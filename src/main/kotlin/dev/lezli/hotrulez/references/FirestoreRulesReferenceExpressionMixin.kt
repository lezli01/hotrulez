package dev.lezli.hotrulez.references

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import dev.lezli.hotrulez.psi.FirestoreRulesReferenceExpression
import dev.lezli.hotrulez.psi.impl.FirestoreRulesExpressionImpl

/**
 * Base impl for `reference_expression`, wired in via the `mixin` attribute in
 * `FirestoreRules.bnf`. Attaches the [FirestoreRulesReference] directly on the
 * element's `getReference()` / `getReferences()` — an `ASTWrapperPsiElement` does
 * not consult a `psi.referenceContributor`'s providers, so the reference is hung
 * on the PSI instead.
 */
abstract class FirestoreRulesReferenceExpressionMixin(node: ASTNode) :
    FirestoreRulesExpressionImpl(node), FirestoreRulesReferenceExpression {

    override fun getReference(): PsiReference = FirestoreRulesReference(this)

    override fun getReferences(): Array<PsiReference> = arrayOf(getReference())
}
