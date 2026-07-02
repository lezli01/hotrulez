package dev.lezli.hotrulez.diagnostics.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import dev.lezli.hotrulez.psi.FirebaseRulesFunctionBody
import dev.lezli.hotrulez.psi.FirebaseRulesReturnStatement
import dev.lezli.hotrulez.psi.FirebaseRulesTypes
import dev.lezli.hotrulez.references.FirebaseRulesElementFactory

/**
 * Appends `return false;` as the last statement of a function body that does not end with a
 * `return`. `false` is the safe default (a rules helper that fell through should deny), and
 * the body is reformatted so the inserted statement is indented like its siblings.
 */
class AddReturnFalseFix(body: FirebaseRulesFunctionBody) :
    PsiUpdateModCommandAction<FirebaseRulesFunctionBody>(body) {

    override fun getFamilyName(): String = "Add 'return false;'"

    override fun invoke(context: ActionContext, body: FirebaseRulesFunctionBody, updater: ModPsiUpdater) {
        val returnStatement = FirebaseRulesElementFactory.returnStatement(context.project, "false")
        val closingBrace = body.node.findChildByType(FirebaseRulesTypes.RBRACE)?.psi
        val inserted = if (closingBrace != null) {
            body.addBefore(returnStatement, closingBrace)
        } else {
            body.add(returnStatement)
        }
        // Reformat via the whole file: reformatting only the body mis-indents the inserted
        // statement when the enclosing function is deeply nested.
        CodeStyleManager.getInstance(context.project).reformat(body.containingFile)
        // Leave the `false` placeholder selected so the user can type the real value over it.
        (inserted as? FirebaseRulesReturnStatement)?.expression?.let { updater.select(it) }
    }
}
