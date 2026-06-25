package dev.lezli.hotrulez.diagnostics

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import dev.lezli.hotrulez.diagnostics.FirestoreRulesDiagnostics.SINGLE_PATH_HELPERS
import dev.lezli.hotrulez.psi.FirestoreRulesAllowStatement
import dev.lezli.hotrulez.psi.FirestoreRulesBlock
import dev.lezli.hotrulez.psi.FirestoreRulesCallExpression
import dev.lezli.hotrulez.psi.FirestoreRulesFunctionDeclaration
import dev.lezli.hotrulez.psi.FirestoreRulesMatchPath
import dev.lezli.hotrulez.psi.FirestoreRulesMemberExpression
import dev.lezli.hotrulez.psi.FirestoreRulesRecursiveWildcard
import dev.lezli.hotrulez.psi.FirestoreRulesReferenceExpression
import dev.lezli.hotrulez.psi.FirestoreRulesVisitor
import dev.lezli.hotrulez.psi.FirestoreRulesTypes as T

/**
 * Suspicious-usage warnings tied to a single statement, expression, or path
 * element:
 *
 *  - An `allow` rule with no `if` condition, which unconditionally grants the
 *    operation. This is legal syntax, so it is a configurable nudge, never an error.
 *  - A recursive wildcard `{name=**}` in a match path used without
 *    `rules_version = '2';`, whose matching semantics differ between rules versions.
 *  - A documented single-path helper (`get`/`getAfter`/`exists`/`existsAfter`)
 *    invoked with an argument count other than one.
 *
 * These are configurable warnings rather than annotator errors because each can
 * be a deliberate choice. As with the other diagnostics, the wording stays
 * structural and never asserts that a rule is secure or authorizes a request.
 */
class FirestoreRulesUsageInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : FirestoreRulesVisitor() {
            override fun visitAllowStatement(o: FirestoreRulesAllowStatement) {
                // Only a complete, well-formed `allow <ops>;` with no condition; skip
                // anything still being typed (no terminator yet, or a parse error)
                // so this does not pile a warning onto the parser's own diagnostics.
                if (o.methodList == null || o.expression != null) return
                if (o.node.findChildByType(T.SEMICOLON) == null) return
                if (PsiTreeUtil.findChildOfType(o, PsiErrorElement::class.java) != null) return
                holder.registerProblem(
                    o,
                    "'allow' rule has no 'if' condition. Add ': if <condition>' to restrict when the operation is permitted.",
                )
            }

            override fun visitRecursiveWildcard(o: FirestoreRulesRecursiveWildcard) {
                // The version-dependent matching semantics only apply to match paths,
                // not to recursive wildcards that appear inside a get()/exists() path argument.
                if (o.parent !is FirestoreRulesMatchPath) return
                // Only warn under an explicit rules_version '1', whose recursive-wildcard
                // semantics differ. An omitted version already defaults to '2' (see
                // FirestoreRulesAnnotator.checkRecursiveWildcardPlacement), so a null
                // version must not be treated the same as v1.
                if (FirestoreRulesDiagnostics.rulesVersion(o) == "1") {
                    holder.registerProblem(
                        o,
                        "Recursive wildcard '{${o.identifier.text}=**}' should be used with rules_version = '2'; " +
                            "its match semantics differ between rules versions.",
                    )
                }
            }

            override fun visitCallExpression(o: FirestoreRulesCallExpression) {
                val name = calleeName(o) ?: return
                if (name !in SINGLE_PATH_HELPERS) return
                // A bare `get(...)` may be a user-defined function that shadows the
                // builtin path helper; only the `firestore.*` member form is
                // unambiguous. Skip the arity check when a same-named function exists.
                if (o.expression is FirestoreRulesReferenceExpression && declaresFunction(o, name)) return
                val arguments = o.argumentList
                val count = arguments.expressionList.size + arguments.pathArgumentList.size
                if (count != 1) {
                    holder.registerProblem(
                        arguments,
                        "'$name()' takes exactly one argument but found $count.",
                    )
                }
            }

            /**
             * Whether the containing file declares a function named [name], which would
             * shadow a bare call to the built-in path helper of the same name.
             */
            private fun declaresFunction(call: FirestoreRulesCallExpression, name: String): Boolean {
                // Walk the enclosing scopes (match/service blocks up to the file): a
                // function declared in an unrelated sibling block must not shadow, so
                // only declarations directly visible from the call site count.
                var scope: PsiElement? = call.parent
                while (scope != null) {
                    if (scope is FirestoreRulesBlock &&
                        PsiTreeUtil.getChildrenOfType(scope, FirestoreRulesFunctionDeclaration::class.java)
                            ?.any { it.identifier?.text == name } == true
                    ) {
                        return true
                    }
                    scope = scope.parent
                }
                return false
            }

            /**
             * The helper name only for forms that resolve to a path helper: a bare
             * `get(/...)` reference, or the cross-service `firestore.get(/...)` form.
             * A member call on any other receiver (e.g. `resource.data.get('k', d)`,
             * the two-argument Firestore `Map.get`) is not a path helper and is left alone.
             */
            private fun calleeName(call: FirestoreRulesCallExpression): String? =
                when (val callee = call.expression) {
                    is FirestoreRulesReferenceExpression -> callee.identifier.text
                    is FirestoreRulesMemberExpression -> {
                        val receiver = callee.expression
                        if (receiver is FirestoreRulesReferenceExpression && receiver.identifier.text == "firestore") {
                            callee.identifier.text
                        } else {
                            null
                        }
                    }
                    else -> null
                }
        }
}
