package dev.lezli.hotrulez.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.ResolveResult
import dev.lezli.hotrulez.psi.FirebaseRulesReferenceExpression

/**
 * Reference from an identifier use (a `reference_expression`) to the
 * declaration(s) it denotes: a function (in callee position) or a parameter /
 * `let` / path variable (in value position). Resolution honours Firestore
 * scoping and path-variable shadowing via [FirebaseRulesScopes].
 *
 * Poly-variant because a name can match more than one visible function
 * declaration. Built-ins and helpers resolve to nothing (empty result) — they
 * are recognised but non-navigable — and the reference is `soft` so an
 * unresolved built-in is never flagged.
 */
class FirebaseRulesReference(element: FirebaseRulesReferenceExpression) :
    PsiReferenceBase.Poly<FirebaseRulesReferenceExpression>(
        element,
        TextRange(0, element.textLength),
        /* soft = */ true,
    ) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        FirebaseRulesScopes.resolve(element)
            .map(::PsiElementResolveResult)
            .toTypedArray()
}
