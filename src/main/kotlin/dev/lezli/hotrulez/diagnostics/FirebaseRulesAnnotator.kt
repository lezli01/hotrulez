package dev.lezli.hotrulez.diagnostics

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import dev.lezli.hotrulez.diagnostics.FirebaseRulesDiagnostics.ALLOW_OPERATIONS
import dev.lezli.hotrulez.psi.FirebaseRulesAllowStatement
import dev.lezli.hotrulez.psi.FirebaseRulesFunctionDeclaration
import dev.lezli.hotrulez.psi.FirebaseRulesMatchPath
import dev.lezli.hotrulez.psi.FirebaseRulesMethodList
import dev.lezli.hotrulez.psi.FirebaseRulesParameterList
import dev.lezli.hotrulez.psi.FirebaseRulesRecursiveWildcard
import dev.lezli.hotrulez.psi.FirebaseRulesReturnStatement
import dev.lezli.hotrulez.psi.FirebaseRulesTypes as T

/**
 * Reports the unambiguous structural/semantic ERRORS that the grammar is too
 * permissive to reject on its own. These are always wrong in a Firestore Rules
 * file, so they live in an always-on annotator rather than a configurable
 * inspection. Configurable WARNINGS live in [FirebaseRulesStructureInspection]
 * and [FirebaseRulesUsageInspection].
 */
class FirebaseRulesAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is FirebaseRulesAllowStatement -> checkAllowStructure(element, holder)
            is FirebaseRulesMethodList -> checkOperations(element, holder)
            is FirebaseRulesParameterList -> checkDuplicateParameters(element, holder)
            is FirebaseRulesFunctionDeclaration -> checkFunctionReturns(element, holder)
            is FirebaseRulesReturnStatement -> checkReturnExpression(element, holder)
            is FirebaseRulesMatchPath -> checkRecursiveWildcardPlacement(element, holder)
        }
    }

    /**
     * `allow` must list at least one operation.
     *
     * A condition-less `allow` (e.g. `allow read;`) is *legal* Firestore syntax —
     * it unconditionally grants the operation — so the missing-condition case is a
     * configurable warning in [FirebaseRulesUsageInspection], not an error here.
     * The current grammar requires at least one identifier in a method list, so an
     * empty list surfaces as a null node; this check also treats a present-but-empty
     * list as missing so it stays correct if the grammar is relaxed for recovery.
     */
    private fun checkAllowStructure(statement: FirebaseRulesAllowStatement, holder: AnnotationHolder) {
        val methodList = statement.methodList
        if (methodList == null || methodList.identifierLeaves().isEmpty()) {
            error(
                holder,
                statement.childToken(T.ALLOW_KEYWORD) ?: statement,
                "'allow' requires at least one operation, for example 'allow read'.",
            )
        }
    }

    /** Every operation name in an `allow` rule must be a known Firestore method. */
    private fun checkOperations(methodList: FirebaseRulesMethodList, holder: AnnotationHolder) {
        for (identifier in methodList.identifierLeaves()) {
            val name = identifier.text
            if (name !in ALLOW_OPERATIONS) {
                error(
                    holder,
                    identifier,
                    "Unknown Firestore Rules operation '$name'. " +
                        "Expected one of: get, list, read, create, update, delete, write.",
                )
            }
        }
    }

    /** Function parameters must have distinct names. */
    private fun checkDuplicateParameters(parameterList: FirebaseRulesParameterList, holder: AnnotationHolder) {
        val seen = HashSet<String>()
        // Each parameter is now its own PSI node (a named element); anchor the error
        // on the duplicate's name identifier.
        for (parameter in parameterList.parameterList) {
            val identifier = parameter.identifier
            if (!seen.add(identifier.text)) {
                error(holder, identifier, "Duplicate parameter name '${identifier.text}'.")
            }
        }
    }

    /** A function body must end with a `return` statement. */
    private fun checkFunctionReturns(function: FirebaseRulesFunctionDeclaration, holder: AnnotationHolder) {
        val body = function.functionBody ?: return
        // The grammar lets a body interleave `let` bindings and `return`s and allows
        // statements after a `return`, so a body can contain a return yet not *end*
        // with one. Check the last statement, not merely that some return exists.
        val lastStatement = body.node.getChildren(null)
            .lastOrNull { it.elementType == T.RETURN_STATEMENT || it.elementType == T.LET_STATEMENT }
        if (lastStatement == null || lastStatement.elementType != T.RETURN_STATEMENT) {
            val name = function.identifier?.text
            val anchor = function.identifier ?: function.childToken(T.FUNCTION_KEYWORD) ?: function
            val subject = if (name != null) "Function '$name'" else "Function"
            error(holder, anchor, "$subject must end with a 'return' statement.")
        }
    }

    /** `return;` is not allowed; a function must return a value. */
    private fun checkReturnExpression(statement: FirebaseRulesReturnStatement, holder: AnnotationHolder) {
        if (statement.expression == null) {
            error(holder, statement, "'return' requires an expression.")
        }
    }

    /**
     * Match-path recursive wildcard rules. A match path may contain at most one
     * `{name=**}` in either rules version. Under `rules_version = '2'` the wildcard
     * may appear anywhere in the path. Under `rules_version = '1'` — which is also
     * the behavior assumed when no version is declared — it must be the last segment.
     */
    private fun checkRecursiveWildcardPlacement(path: FirebaseRulesMatchPath, holder: AnnotationHolder) {
        val segments = FirebaseRulesDiagnostics.pathSegments(path)
        val recursive = segments.withIndex().filter { it.value is FirebaseRulesRecursiveWildcard }
        if (recursive.isEmpty()) return

        // At most one recursive wildcard per match path, regardless of version.
        for ((_, segment) in recursive.drop(1)) {
            error(holder, segment, "A match path may contain at most one recursive wildcard '{name=**}'.")
        }

        // Any non-v2 ruleset requires the (single allowed) recursive wildcard to
        // be last. Only the first one is placement-checked here; any extras are
        // already reported above as "at most one", so a stray wildcard never
        // collects both an "at most one" and a "must be last" error.
        if (FirebaseRulesDiagnostics.rulesVersion(path) != "2") {
            val (index, segment) = recursive.first()
            if (index != segments.lastIndex) {
                error(
                    holder,
                    segment,
                    "In rules_version '1', a recursive wildcard '{name=**}' must be the last segment of a match path.",
                )
            }
        }
    }

    private fun error(holder: AnnotationHolder, element: PsiElement, message: String) {
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(element).create()
    }

    private fun PsiElement.identifierLeaves(): List<PsiElement> =
        node.getChildren(null)
            .filter { it.elementType == T.IDENTIFIER }
            .map { it.psi }

    private fun PsiElement.childToken(type: IElementType): PsiElement? =
        node.findChildByType(type)?.psi
}
