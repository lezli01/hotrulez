package dev.lezli.hotrulez.diagnostics.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import dev.lezli.hotrulez.references.FirebaseRulesElementFactory

/**
 * Replaces an unknown `allow` operation with the closest known one ([target]). The
 * annotator offers this only when [target] is within a small edit distance of the typo
 * (see FirebaseRulesAnnotator), so an unrecognisable token gets no misleading suggestion.
 */
class ChangeOperationFix(
    operation: PsiElement,
    private val target: String,
) : PsiUpdateModCommandAction<PsiElement>(operation) {

    override fun getFamilyName(): String = "Change operation to '$target'"

    override fun invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater) {
        element.replace(FirebaseRulesElementFactory.identifier(context.project, target))
    }
}
