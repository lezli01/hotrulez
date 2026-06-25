package dev.lezli.hotrulez.diagnostics

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import dev.lezli.hotrulez.diagnostics.FirestoreRulesDiagnostics.ALLOW_OPERATIONS
import dev.lezli.hotrulez.psi.FirestoreRulesAllowStatement
import dev.lezli.hotrulez.psi.FirestoreRulesFunctionDeclaration
import dev.lezli.hotrulez.psi.FirestoreRulesMatchPath
import dev.lezli.hotrulez.psi.FirestoreRulesMethodList
import dev.lezli.hotrulez.psi.FirestoreRulesParameterList
import dev.lezli.hotrulez.psi.FirestoreRulesRecursiveWildcard
import dev.lezli.hotrulez.psi.FirestoreRulesReturnStatement
import dev.lezli.hotrulez.psi.FirestoreRulesTypes as T

/**
 * Reports the unambiguous structural/semantic ERRORS that the grammar is too
 * permissive to reject on its own. These are always wrong in a Firestore Rules
 * file, so they live in an always-on annotator rather than a configurable
 * inspection. Configurable WARNINGS live in [FirestoreRulesStructureInspection]
 * and [FirestoreRulesUsageInspection].
 */
class FirestoreRulesAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is FirestoreRulesAllowStatement -> checkAllowStructure(element, holder)
            is FirestoreRulesMethodList -> checkOperations(element, holder)
            is FirestoreRulesParameterList -> checkDuplicateParameters(element, holder)
            is FirestoreRulesFunctionDeclaration -> checkFunctionReturns(element, holder)
            is FirestoreRulesReturnStatement -> checkReturnExpression(element, holder)
            is FirestoreRulesMatchPath -> checkRecursiveWildcardPlacement(element, holder)
        }
    }

    /**
     * `allow` must list at least one operation.
     *
     * A condition-less `allow` (e.g. `allow read;`) is *legal* Firestore syntax —
     * it unconditionally grants the operation — so the missing-condition case is a
     * configurable warning in [FirestoreRulesUsageInspection], not an error here.
     * An empty operation list never forms an empty [FirestoreRulesMethodList]
     * because the grammar requires at least one identifier; it surfaces instead as
     * a null method list, which is what this check looks for.
     */
    private fun checkAllowStructure(statement: FirestoreRulesAllowStatement, holder: AnnotationHolder) {
        if (statement.methodList == null) {
            error(
                holder,
                statement.childToken(T.ALLOW_KEYWORD) ?: statement,
                "'allow' requires at least one operation, for example 'allow read'.",
            )
        }
    }

    /** Every operation name in an `allow` rule must be a known Firestore method. */
    private fun checkOperations(methodList: FirestoreRulesMethodList, holder: AnnotationHolder) {
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
    private fun checkDuplicateParameters(parameterList: FirestoreRulesParameterList, holder: AnnotationHolder) {
        val seen = HashSet<String>()
        for (identifier in parameterList.identifierLeaves()) {
            if (!seen.add(identifier.text)) {
                error(holder, identifier, "Duplicate parameter name '${identifier.text}'.")
            }
        }
    }

    /** A function body must end with a `return` statement. */
    private fun checkFunctionReturns(function: FirestoreRulesFunctionDeclaration, holder: AnnotationHolder) {
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
    private fun checkReturnExpression(statement: FirestoreRulesReturnStatement, holder: AnnotationHolder) {
        if (statement.expression == null) {
            error(holder, statement, "'return' requires an expression.")
        }
    }

    /**
     * Match-path recursive wildcard rules. A match path may contain at most one
     * `{name=**}` (both rules versions). Under `rules_version = '1'` it must also
     * be the last segment; under rules_version '2' — the modern default, and what
     * we assume when no version is declared — a recursive wildcard may appear
     * anywhere in the match path.
     */
    private fun checkRecursiveWildcardPlacement(path: FirestoreRulesMatchPath, holder: AnnotationHolder) {
        val segments = FirestoreRulesDiagnostics.pathSegments(path)
        val recursive = segments.withIndex().filter { it.value is FirestoreRulesRecursiveWildcard }
        if (recursive.isEmpty()) return

        // At most one recursive wildcard per match path, regardless of version.
        for ((_, segment) in recursive.drop(1)) {
            error(holder, segment, "A match path may contain at most one recursive wildcard '{name=**}'.")
        }

        // rules_version '1' additionally requires the (single allowed) recursive
        // wildcard to be last. Only the first one is placement-checked here; any
        // extras are already reported above as "at most one", so a stray wildcard
        // never collects both an "at most one" and a "must be last" error.
        if (FirestoreRulesDiagnostics.rulesVersion(path) == "1") {
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
