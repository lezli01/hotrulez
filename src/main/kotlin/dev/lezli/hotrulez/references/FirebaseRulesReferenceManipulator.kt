package dev.lezli.hotrulez.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import dev.lezli.hotrulez.psi.FirebaseRulesReferenceExpression

/**
 * Lets the rename refactoring rewrite an identifier *use* in place: replaces the
 * `reference_expression`'s identifier leaf with the new name. Required so
 * `PsiReferenceBase.handleElementRename` can edit each reference.
 */
class FirebaseRulesReferenceManipulator : AbstractElementManipulator<FirebaseRulesReferenceExpression>() {
    override fun handleContentChange(
        element: FirebaseRulesReferenceExpression,
        range: TextRange,
        newContent: String,
    ): FirebaseRulesReferenceExpression {
        element.identifier.replace(FirebaseRulesElementFactory.identifier(element.project, newContent))
        return element
    }
}
