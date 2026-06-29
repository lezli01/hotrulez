package dev.lezli.hotrulez.references

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import dev.lezli.hotrulez.psi.FirebaseRulesTypes

/**
 * Shared implementation for every [FirebaseRulesNamedElement]. All covered
 * declarations have exactly one `IDENTIFIER` child that is their name, so name
 * lookup, the navigation offset, and rename are all derived from that single
 * leaf.
 *
 * Wired in as the `mixin` for the relevant rules in `FirebaseRules.bnf`, so the
 * Grammar-Kit-generated `*Impl` classes extend this instead of
 * [ASTWrapperPsiElement].
 */
abstract class FirebaseRulesNamedElementBase(node: ASTNode) : ASTWrapperPsiElement(node), FirebaseRulesNamedElement {

    override fun getNameIdentifier(): PsiElement? =
        node.findChildByType(FirebaseRulesTypes.IDENTIFIER)?.psi

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement {
        nameIdentifier?.replace(FirebaseRulesElementFactory.identifier(project, name))
        return this
    }

    /** Navigate to / select the name itself, not the whole declaration. */
    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()
}
