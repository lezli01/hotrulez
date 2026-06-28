package dev.lezli.hotrulez.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import dev.lezli.hotrulez.psi.FirestoreRulesReferenceExpression

/**
 * Lets the rename refactoring rewrite an identifier *use* in place: replaces the
 * `reference_expression`'s identifier leaf with the new name. Required so
 * `PsiReferenceBase.handleElementRename` can edit each reference.
 */
class FirestoreRulesReferenceManipulator : AbstractElementManipulator<FirestoreRulesReferenceExpression>() {
    override fun handleContentChange(
        element: FirestoreRulesReferenceExpression,
        range: TextRange,
        newContent: String,
    ): FirestoreRulesReferenceExpression {
        element.identifier.replace(FirestoreRulesElementFactory.identifier(element.project, newContent))
        return element
    }
}
