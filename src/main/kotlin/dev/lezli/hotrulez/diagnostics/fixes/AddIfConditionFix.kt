package dev.lezli.hotrulez.diagnostics.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import dev.lezli.hotrulez.psi.FirebaseRulesAllowStatement
import dev.lezli.hotrulez.references.FirebaseRulesElementFactory

/**
 * Adds a missing condition to an unconditional `allow` rule, turning `allow read;` into
 * `allow read: if false;`. `false` is a deliberately restrictive placeholder (an
 * unconditional grant is the risk being flagged) and is left selected so the user can type
 * the real condition straight over it.
 */
class AddIfConditionFix(allow: FirebaseRulesAllowStatement) :
    PsiUpdateModCommandAction<FirebaseRulesAllowStatement>(allow) {

    override fun getFamilyName(): String = "Add ': if <condition>'"

    override fun invoke(context: ActionContext, allow: FirebaseRulesAllowStatement, updater: ModPsiUpdater) {
        val methodsText = allow.methodList?.text ?: return
        val replacement = FirebaseRulesElementFactory.allowStatement(context.project, methodsText, "false")
        val inserted = allow.replace(replacement) as FirebaseRulesAllowStatement
        inserted.expression?.let { updater.select(it) }
    }
}
