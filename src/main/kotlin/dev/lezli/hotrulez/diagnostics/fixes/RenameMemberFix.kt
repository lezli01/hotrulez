package dev.lezli.hotrulez.diagnostics.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import dev.lezli.hotrulez.psi.FirebaseRulesMemberExpression
import dev.lezli.hotrulez.references.FirebaseRulesElementFactory

/**
 * Rewrites the accessed member of a [FirebaseRulesMemberExpression] to [target] — the
 * "did you mean 'X'?" fix for an unknown member that is within a small edit distance of a
 * known member of its receiver (see `FirebaseRulesMemberInspection`). One fix is offered
 * per near match, so several close candidates each surface their own rename, mirroring the
 * per-alternative shape of [SetServiceNameFix].
 */
class RenameMemberFix(
    member: FirebaseRulesMemberExpression,
    private val target: String,
) : PsiUpdateModCommandAction<FirebaseRulesMemberExpression>(member) {

    override fun getFamilyName(): String = "Change to '$target'"

    override fun invoke(context: ActionContext, element: FirebaseRulesMemberExpression, updater: ModPsiUpdater) {
        element.identifier.replace(FirebaseRulesElementFactory.identifier(context.project, target))
    }
}
