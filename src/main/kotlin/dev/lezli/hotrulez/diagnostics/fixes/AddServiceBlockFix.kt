package dev.lezli.hotrulez.diagnostics.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import dev.lezli.hotrulez.psi.FirebaseRulesServiceDeclaration
import dev.lezli.hotrulez.references.FirebaseRulesElementFactory
import dev.lezli.hotrulez.references.RulesService

/**
 * Scaffolds the missing rule block of a block-less `service` declaration, seeding it with
 * the service's conventional root match (`match /databases/{database}/documents { }` for
 * Firestore, `match /b/{bucket}/o { }` for Storage). Only offered for a recognised service
 * (the missing-block warning never fires for an unknown one), so the root shape is known.
 * The caret lands inside the root match, ready for a first rule.
 */
class AddServiceBlockFix(service: FirebaseRulesServiceDeclaration) :
    PsiUpdateModCommandAction<FirebaseRulesServiceDeclaration>(service) {

    override fun getFamilyName(): String = "Add rule block"

    override fun invoke(context: ActionContext, service: FirebaseRulesServiceDeclaration, updater: ModPsiUpdater) {
        val project = context.project
        val name = service.serviceName?.text?.filterNot { it.isWhitespace() }
        val rootMatchHint = RulesService.fromServiceName(name)?.rootMatchHint ?: return

        service.add(FirebaseRulesElementFactory.serviceBlock(project, rootMatchHint))
        val reformatted = CodeStyleManager.getInstance(project).reformat(service) as FirebaseRulesServiceDeclaration
        moveCaretInsideBlock(updater, reformatted.block?.matchDeclarationList?.firstOrNull()?.block)
    }
}
