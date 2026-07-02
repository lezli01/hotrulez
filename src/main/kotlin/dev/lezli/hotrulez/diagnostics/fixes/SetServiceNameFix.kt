package dev.lezli.hotrulez.diagnostics.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import dev.lezli.hotrulez.psi.FirebaseRulesServiceName
import dev.lezli.hotrulez.references.FirebaseRulesElementFactory

/**
 * Replaces an unrecognised `service` name with a known one ([target], e.g.
 * `cloud.firestore` or `firebase.storage`). Offered once per known service, so an unknown
 * name surfaces both alternatives.
 */
class SetServiceNameFix(
    serviceName: FirebaseRulesServiceName,
    private val target: String,
) : PsiUpdateModCommandAction<FirebaseRulesServiceName>(serviceName) {

    override fun getFamilyName(): String = "Change service to '$target'"

    override fun invoke(context: ActionContext, element: FirebaseRulesServiceName, updater: ModPsiUpdater) {
        element.replace(FirebaseRulesElementFactory.serviceName(context.project, target))
    }
}
