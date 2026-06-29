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
import dev.lezli.hotrulez.parser.FirebaseRulesTokenSets
import dev.lezli.hotrulez.psi.FirebaseRulesAllowStatement
import dev.lezli.hotrulez.psi.FirebaseRulesBlock
import dev.lezli.hotrulez.psi.FirebaseRulesExpression
import dev.lezli.hotrulez.psi.FirebaseRulesFile
import dev.lezli.hotrulez.psi.FirebaseRulesFunctionBody
import dev.lezli.hotrulez.psi.FirebaseRulesFunctionDeclaration
import dev.lezli.hotrulez.psi.FirebaseRulesLetStatement
import dev.lezli.hotrulez.psi.FirebaseRulesMemberExpression
import dev.lezli.hotrulez.psi.FirebaseRulesMethodList
import dev.lezli.hotrulez.psi.FirebaseRulesParameter
import dev.lezli.hotrulez.psi.FirebaseRulesParameterList
import dev.lezli.hotrulez.psi.FirebaseRulesPathWildcard
import dev.lezli.hotrulez.psi.FirebaseRulesRecursiveWildcard
import dev.lezli.hotrulez.psi.FirebaseRulesTypes as T
import dev.lezli.hotrulez.references.FirebaseRulesBuiltins
import dev.lezli.hotrulez.references.FirebaseRulesNamedElement
import dev.lezli.hotrulez.references.FirebaseRulesScopes

/**
 * Position-aware completion for Firestore Rules. Routing is keyed by
 * [PlatformPatterns]: each leaf-anchored context gets its own provider, and a
 * contextual provider handles the position-sensitive cases:
 *
 *  - after a `.` ([MemberProvider]): shallow members from the static [FirebaseRulesBuiltins] table;
 *  - after `service ` ([ServiceProvider]): `cloud.firestore`;
 *  - everything else ([ContextualProvider]):
 *      - after `allow ` (and within the method list): operation names;
 *      - after an `allow ... :`: the `if` keyword;
 *      - in expression position: scope-visible user symbols, built-ins, helpers, and literals;
 *      - in structural position: the keywords valid for that block.
 *
 * The contextual provider's pattern excludes the `.`/`service` leaves, so each
 * completion site is served by exactly one provider. It still carries the
 * `.`/`service` branches as a recovery net in case a comment-separated leaf slips
 * past the pattern.
 *
 * Scoping is delegated to [FirebaseRulesScopes], so a parameter is offered only
 * inside its function, a path variable only within its match subtree, and a
 * `let` only after its declaration. There is no type inference — members come
 * from the fixed doc-sourced table. All lookups are null-safe so a partially
 * malformed file degrades gracefully.
 */
class FirebaseRulesCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().afterLeaf("."), MemberProvider)
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().afterLeaf("service"), ServiceProvider)
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().andNot(PlatformPatterns.psiElement().afterLeaf(".", "service")),
            ContextualProvider,
        )
    }

    /** Shallow members after a `.`: `request.`, `request.auth.`, `resource.`, … */
    private object MemberProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) = addMembers(parameters.position, result)
    }

    /** The single service literal after the `service` keyword. */
    private object ServiceProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) = addKeywords(result, listOf(FirebaseRulesBuiltins.SERVICE_FIRESTORE), "service")
    }

    /** Operations, the allow-colon `if`, expression symbols, and structural keywords. */
    private object ContextualProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val position = parameters.position
            // A parameter declaration is a fresh name, not a reference or an expression;
            // offer nothing there rather than leaking expression symbols.
            if (PsiTreeUtil.getParentOfType(position, FirebaseRulesParameterList::class.java) != null) return

            val previous = previousMeaningfulLeaf(position)
            val previousType = previous?.node?.elementType

            when {
                // Recovery net: normally MemberProvider/ServiceProvider own these leaves.
                previousType == T.DOT -> addMembers(position, result)
                previousType == T.SERVICE_KEYWORD ->
                    addKeywords(result, listOf(FirebaseRulesBuiltins.SERVICE_FIRESTORE), "service")
                inMethodList(position, previousType, previous) ->
                    addKeywords(result, FirebaseRulesBuiltins.OPERATIONS, "operation")
                // The allow rule's own ':' (a direct child of the allow statement), not a
                // map-entry or ternary ':' nested inside the condition.
                previousType == T.COLON && previous?.parent is FirebaseRulesAllowStatement ->
                    addKeywords(result, listOf("if"), "keyword")
                isExpressionPosition(position, previousType) -> addExpressionSymbols(position, result)
                else -> addStructuralKeywords(position, previous, result)
            }
        }
    }
}

private fun addMembers(position: PsiElement, result: CompletionResultSet) {
    val member = PsiTreeUtil.getParentOfType(position, FirebaseRulesMemberExpression::class.java) ?: return
    FirebaseRulesBuiltins.MEMBERS[receiverKey(member.expression)]?.let { addKeywords(result, it, "member") }
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
    for (symbol in FirebaseRulesScopes.visibleSymbols(position)) {
        val name = symbol.name ?: continue
        result.addElement(LookupElementBuilder.create(name).withTypeText(symbolType(symbol)))
    }
    addKeywords(result, FirebaseRulesBuiltins.GLOBALS, "built-in")
    for (helper in FirebaseRulesBuiltins.HELPERS) {
        result.addElement(LookupElementBuilder.create(helper).withTailText("()", true).withTypeText("helper"))
    }
    addKeywords(result, FirebaseRulesBuiltins.LITERALS, "literal")
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
            is FirebaseRulesFunctionBody -> {
                addKeywords(result, FirebaseRulesBuiltins.FUNCTION_BODY_KEYWORDS, "keyword"); return
            }
            is FirebaseRulesBlock -> {
                addKeywords(result, FirebaseRulesBuiltins.BLOCK_KEYWORDS, "keyword"); return
            }
            is FirebaseRulesFile -> {
                addKeywords(result, FirebaseRulesBuiltins.TOP_LEVEL_KEYWORDS, "keyword"); return
            }
        }
        context = context.parent
    }
}

private fun isExpressionPosition(position: PsiElement, previousType: IElementType?): Boolean =
    PsiTreeUtil.getParentOfType(position, FirebaseRulesExpression::class.java) != null ||
        previousType in EXPRESSION_STARTERS

private fun inMethodList(position: PsiElement, previousType: IElementType?, previous: PsiElement?): Boolean {
    if (previousType == T.ALLOW_KEYWORD) return true
    if (PsiTreeUtil.getParentOfType(position, FirebaseRulesMethodList::class.java) != null) return true
    return previousType == T.COMMA &&
        PsiTreeUtil.getParentOfType(previous, FirebaseRulesMethodList::class.java) != null
}

private fun addKeywords(result: CompletionResultSet, names: List<String>, typeText: String) {
    for (name in names) {
        result.addElement(LookupElementBuilder.create(name).withTypeText(typeText))
    }
}

private fun symbolType(symbol: FirebaseRulesNamedElement): String = when (symbol) {
    is FirebaseRulesFunctionDeclaration -> "function"
    is FirebaseRulesParameter -> "parameter"
    is FirebaseRulesLetStatement -> "let"
    is FirebaseRulesPathWildcard, is FirebaseRulesRecursiveWildcard -> "path variable"
    else -> "symbol"
}

private fun previousMeaningfulLeaf(element: PsiElement): PsiElement? {
    var leaf = PsiTreeUtil.prevLeaf(element)
    while (leaf != null && (leaf is PsiWhiteSpace || leaf.node.elementType in FirebaseRulesTokenSets.COMMENTS)) {
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
