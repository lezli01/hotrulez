package dev.lezli.hotrulez.references

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import dev.lezli.hotrulez.psi.FirebaseRulesReferenceExpression
import dev.lezli.hotrulez.psi.impl.FirebaseRulesExpressionImpl

/**
 * Base impl for `reference_expression`, wired in via the `mixin` attribute in
 * `FirebaseRules.bnf`. Attaches the [FirebaseRulesReference] directly on the
 * element's `getReference()` / `getReferences()` — an `ASTWrapperPsiElement` does
 * not consult a `psi.referenceContributor`'s providers, so the reference is hung
 * on the PSI instead.
 */
abstract class FirebaseRulesReferenceExpressionMixin(node: ASTNode) :
    FirebaseRulesExpressionImpl(node), FirebaseRulesReferenceExpression {

    override fun getReference(): PsiReference = FirebaseRulesReference(this)

    override fun getReferences(): Array<PsiReference> = arrayOf(getReference())
}
