package dev.lezli.hotrulez.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.ASTNode
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import dev.lezli.hotrulez.parser.FirestoreRulesTokenSets
import dev.lezli.hotrulez.psi.FirestoreRulesAllowStatement
import dev.lezli.hotrulez.psi.FirestoreRulesBlock
import dev.lezli.hotrulez.psi.FirestoreRulesExpression
import dev.lezli.hotrulez.psi.FirestoreRulesFile
import dev.lezli.hotrulez.psi.FirestoreRulesFunctionBody
import dev.lezli.hotrulez.psi.FirestoreRulesFunctionDeclaration
import dev.lezli.hotrulez.psi.FirestoreRulesLetStatement
import dev.lezli.hotrulez.psi.FirestoreRulesMemberExpression
import dev.lezli.hotrulez.psi.FirestoreRulesMethodList
import dev.lezli.hotrulez.psi.FirestoreRulesParameter
import dev.lezli.hotrulez.psi.FirestoreRulesParameterList
import dev.lezli.hotrulez.psi.FirestoreRulesPathWildcard
import dev.lezli.hotrulez.psi.FirestoreRulesRecursiveWildcard
import dev.lezli.hotrulez.psi.FirestoreRulesTypes as T
import dev.lezli.hotrulez.references.FirestoreRulesBuiltins
import dev.lezli.hotrulez.references.FirestoreRulesNamedElement
import dev.lezli.hotrulez.references.FirestoreRulesScopes

/**
 * Position-aware completion for Firestore Rules. The contributor inspects the
 * caret's PSI context and offers exactly one relevant set:
 *
 *  - after `allow ` (and within the method list): operation names;
 *  - after `service `: `cloud.firestore`;
 *  - after a `.`: shallow members from the static [FirestoreRulesBuiltins] table;
 *  - after an `allow ... :`: the `if` keyword;
 *  - in expression position: scope-visible user symbols, built-ins, helpers, and literals;
 *  - in structural position: the keywords valid for that block.
 *
 * Scoping is delegated to [FirestoreRulesScopes], so a parameter is offered only
 * inside its function, a path variable only within its match subtree, and a
 * `let` only after its declaration. There is no type inference — members come
 * from the fixed doc-sourced table. All lookups are null-safe so a partially
 * malformed file degrades gracefully.
 */
class FirestoreRulesCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), Provider)
    }

    private object Provider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val position = parameters.position
            // A parameter declaration is a fresh name, not a reference or an expression;
            // offer nothing there rather than leaking expression symbols.
            if (PsiTreeUtil.getParentOfType(position, FirestoreRulesParameterList::class.java) != null) return

            val previous = previousMeaningfulLeaf(position)
            val previousType = previous?.node?.elementType

            when {
                previousType == T.DOT -> addMembers(position, result)
                inMethodList(position, previousType, previous) ->
                    addKeywords(result, FirestoreRulesBuiltins.OPERATIONS, "operation")
                previousType == T.SERVICE_KEYWORD ->
                    addKeywords(result, listOf(FirestoreRulesBuiltins.SERVICE_FIRESTORE), "service")
                // The allow rule's own ':' (a direct child of the allow statement), not a
                // map-entry or ternary ':' nested inside the condition.
                previousType == T.COLON && previous?.parent is FirestoreRulesAllowStatement ->
                    addKeywords(result, listOf("if"), "keyword")
                isExpressionPosition(position, previousType) -> addExpressionSymbols(position, result)
                else -> addStructuralKeywords(position, previous, result)
            }
        }

        private fun addMembers(position: PsiElement, result: CompletionResultSet) {
            val member = PsiTreeUtil.getParentOfType(position, FirestoreRulesMemberExpression::class.java) ?: return
            FirestoreRulesBuiltins.MEMBERS[receiverKey(member.expression)]?.let { addKeywords(result, it, "member") }
        }

        /**
         * The lookup key for a member receiver, assembled from its identifier and `.` leaves only.
         * Built from tokens (not raw text) so interleaved whitespace or comments — e.g.
         * `request /* x */ .auth` — still map to `request.auth`.
         */
        private fun receiverKey(receiver: PsiElement): String {
            val builder = StringBuilder()
            fun collect(node: ASTNode) {
                val children = node.getChildren(null)
                if (children.isEmpty()) {
                    if (node.elementType == T.IDENTIFIER || node.elementType == T.DOT) builder.append(node.text)
                } else {
                    children.forEach(::collect)
                }
            }
            collect(receiver.node)
            return builder.toString()
        }

        private fun addExpressionSymbols(position: PsiElement, result: CompletionResultSet) {
            for (symbol in FirestoreRulesScopes.visibleSymbols(position)) {
                val name = symbol.name ?: continue
                result.addElement(LookupElementBuilder.create(name).withTypeText(symbolType(symbol)))
            }
            addKeywords(result, FirestoreRulesBuiltins.GLOBALS, "built-in")
            for (helper in FirestoreRulesBuiltins.HELPERS) {
                result.addElement(LookupElementBuilder.create(helper).withTailText("()", true).withTypeText("helper"))
            }
            addKeywords(result, FirestoreRulesBuiltins.LITERALS, "literal")
        }

        private fun addStructuralKeywords(position: PsiElement, previous: PsiElement?, result: CompletionResultSet) {
            // The completion dummy can escape a function body during parse recovery, so the
            // structural context is read from the previous real token, not the dummy. A
            // closing '}' means we are *after* that block, so its own container is skipped.
            var context: PsiElement? = when {
                previous == null -> position.parent
                previous.node.elementType == T.RBRACE -> previous.parent?.parent
                else -> previous.parent
            }
            while (context != null) {
                when (context) {
                    is FirestoreRulesFunctionBody -> {
                        addKeywords(result, FirestoreRulesBuiltins.FUNCTION_BODY_KEYWORDS, "keyword"); return
                    }
                    is FirestoreRulesBlock -> {
                        addKeywords(result, FirestoreRulesBuiltins.BLOCK_KEYWORDS, "keyword"); return
                    }
                    is FirestoreRulesFile -> {
                        addKeywords(result, FirestoreRulesBuiltins.TOP_LEVEL_KEYWORDS, "keyword"); return
                    }
                }
                context = context.parent
            }
        }

        private fun isExpressionPosition(position: PsiElement, previousType: IElementType?): Boolean =
            PsiTreeUtil.getParentOfType(position, FirestoreRulesExpression::class.java) != null ||
                previousType in EXPRESSION_STARTERS

        private fun inMethodList(position: PsiElement, previousType: IElementType?, previous: PsiElement?): Boolean {
            if (previousType == T.ALLOW_KEYWORD) return true
            if (PsiTreeUtil.getParentOfType(position, FirestoreRulesMethodList::class.java) != null) return true
            return previousType == T.COMMA &&
                PsiTreeUtil.getParentOfType(previous, FirestoreRulesMethodList::class.java) != null
        }

        private fun addKeywords(result: CompletionResultSet, names: List<String>, typeText: String) {
            for (name in names) {
                result.addElement(LookupElementBuilder.create(name).withTypeText(typeText))
            }
        }

        private fun symbolType(symbol: FirestoreRulesNamedElement): String = when (symbol) {
            is FirestoreRulesFunctionDeclaration -> "function"
            is FirestoreRulesParameter -> "parameter"
            is FirestoreRulesLetStatement -> "let"
            is FirestoreRulesPathWildcard, is FirestoreRulesRecursiveWildcard -> "path variable"
            else -> "symbol"
        }

        private fun previousMeaningfulLeaf(element: PsiElement): PsiElement? {
            var leaf = PsiTreeUtil.prevLeaf(element)
            while (leaf != null && (leaf is PsiWhiteSpace || leaf.node.elementType in FirestoreRulesTokenSets.COMMENTS)) {
                leaf = PsiTreeUtil.prevLeaf(leaf)
            }
            return leaf
        }

        /** Tokens that can immediately precede the start of an expression. */
        private val EXPRESSION_STARTERS = setOf(
            T.IF_KEYWORD, T.RETURN_KEYWORD, T.ASSIGN, T.LPAREN, T.LBRACKET, T.COMMA,
            T.OR_OR, T.AND_AND, T.EQEQ, T.NE, T.LT, T.LE, T.GT, T.GE,
            T.PLUS, T.MINUS, T.STAR, T.SLASH, T.PERCENT, T.NOT, T.QUESTION, T.IN_KEYWORD,
        )
    }
}
