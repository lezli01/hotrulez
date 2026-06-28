package dev.lezli.hotrulez.references

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import dev.lezli.hotrulez.psi.FirestoreRulesTypes

/**
 * Shared implementation for every [FirestoreRulesNamedElement]. All covered
 * declarations have exactly one `IDENTIFIER` child that is their name, so name
 * lookup, the navigation offset, and rename are all derived from that single
 * leaf.
 *
 * Wired in as the `mixin` for the relevant rules in `FirestoreRules.bnf`, so the
 * Grammar-Kit-generated `*Impl` classes extend this instead of
 * [ASTWrapperPsiElement].
 */
abstract class FirestoreRulesNamedElementBase(node: ASTNode) : ASTWrapperPsiElement(node), FirestoreRulesNamedElement {

    override fun getNameIdentifier(): PsiElement? =
        node.findChildByType(FirestoreRulesTypes.IDENTIFIER)?.psi

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement {
        nameIdentifier?.replace(FirestoreRulesElementFactory.identifier(project, name))
        return this
    }

    /** Navigate to / select the name itself, not the whole declaration. */
    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()
}
