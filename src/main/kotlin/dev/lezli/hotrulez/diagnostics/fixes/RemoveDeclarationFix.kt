package dev.lezli.hotrulez.diagnostics.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement

/**
 * Removes an unused declaration (a `function` or a `let` binding) together with the blank
 * line that preceded it. Only offered when the symbol inspection has already established the
 * declaration has no references, so the delete is safe without a usage search. [label] is
 * the human-readable thing being removed, e.g. `function 'isOwner'` or `'let' binding`.
 */
class RemoveDeclarationFix(
    declaration: PsiElement,
    private val label: String,
) : PsiUpdateModCommandAction<PsiElement>(declaration) {

    override fun getFamilyName(): String = "Remove $label"

    override fun invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater) {
        element.deleteWithLeadingWhitespace()
    }
}
