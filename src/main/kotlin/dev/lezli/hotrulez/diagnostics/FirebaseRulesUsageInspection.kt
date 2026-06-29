package dev.lezli.hotrulez.diagnostics

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import dev.lezli.hotrulez.diagnostics.FirebaseRulesDiagnostics.SINGLE_PATH_HELPERS
import dev.lezli.hotrulez.psi.FirebaseRulesAllowStatement
import dev.lezli.hotrulez.psi.FirebaseRulesBlock
import dev.lezli.hotrulez.psi.FirebaseRulesCallExpression
import dev.lezli.hotrulez.psi.FirebaseRulesFunctionDeclaration
import dev.lezli.hotrulez.psi.FirebaseRulesMatchPath
import dev.lezli.hotrulez.psi.FirebaseRulesMemberExpression
import dev.lezli.hotrulez.psi.FirebaseRulesRecursiveWildcard
import dev.lezli.hotrulez.psi.FirebaseRulesReferenceExpression
import dev.lezli.hotrulez.psi.FirebaseRulesVisitor
import dev.lezli.hotrulez.psi.FirebaseRulesTypes as T

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
class FirebaseRulesUsageInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : FirebaseRulesVisitor() {
            override fun visitAllowStatement(o: FirebaseRulesAllowStatement) {
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

            override fun visitMatchPath(o: FirebaseRulesMatchPath) {
                // The version-dependent matching semantics only apply to recursive
                // wildcards in a match path; a wildcard inside a get()/exists() path
                // argument is not a match path, so this visitor never sees it.
                val segments = FirebaseRulesDiagnostics.pathSegments(o)
                // The annotator raises a hard ERROR on any recursive wildcard that is
                // misplaced (not the final segment) or redundant (a second `**`). Only
                // nudge about the version when there is exactly one wildcard and it is
                // the last segment — the single case the annotator leaves error-free —
                // so the two diagnostics never stack on the same element.
                val wildcard = (
                    segments.singleOrNull { it is FirebaseRulesRecursiveWildcard }
                        ?.takeIf { it == segments.lastOrNull() }
                        as? FirebaseRulesRecursiveWildcard
                    ) ?: return
                // Only rules_version '2' has the modern recursive-wildcard semantics;
                // an omitted version uses the v1 behavior.
                if (FirebaseRulesDiagnostics.rulesVersion(o) != "2") {
                    holder.registerProblem(
                        wildcard,
                        "Recursive wildcard '{${wildcard.identifier.text}=**}' should be used with rules_version = '2'; " +
                            "its match semantics differ between rules versions.",
                    )
                }
            }

            override fun visitCallExpression(o: FirebaseRulesCallExpression) {
                val name = calleeName(o) ?: return
                if (name !in SINGLE_PATH_HELPERS) return
                // A bare `get(...)` may be a user-defined function that shadows the
                // builtin path helper; only the `firestore.*` member form is
                // unambiguous. Skip the arity check when a same-named function exists.
                if (o.expression is FirebaseRulesReferenceExpression && declaresFunction(o, name)) return
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
            private fun declaresFunction(call: FirebaseRulesCallExpression, name: String): Boolean {
                // Walk the enclosing scopes (match/service blocks up to the file): a
                // function declared in an unrelated sibling block must not shadow, so
                // only declarations directly visible from the call site count.
                var scope: PsiElement? = call.parent
                while (scope != null) {
                    if (scope is FirebaseRulesBlock &&
                        PsiTreeUtil.getChildrenOfType(scope, FirebaseRulesFunctionDeclaration::class.java)
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
            private fun calleeName(call: FirebaseRulesCallExpression): String? =
                when (val callee = call.expression) {
                    is FirebaseRulesReferenceExpression -> callee.identifier.text
                    is FirebaseRulesMemberExpression -> {
                        val receiver = callee.expression
                        if (receiver is FirebaseRulesReferenceExpression && receiver.identifier.text == "firestore") {
                            callee.identifier.text
                        } else {
                            null
                        }
                    }
                    else -> null
                }
        }
}
