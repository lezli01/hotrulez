package dev.lezli.hotrulez.diagnostics.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.util.PsiTreeUtil
import dev.lezli.hotrulez.diagnostics.FirebaseRulesDiagnostics
import dev.lezli.hotrulez.psi.FirebaseRulesFile
import dev.lezli.hotrulez.psi.FirebaseRulesRulesVersionStatement
import dev.lezli.hotrulez.references.FirebaseRulesElementFactory

/**
 * Moves a `rules_version` declared after the `service` block back to the top of the file.
 * Any stray version statements are collapsed into a single one at the top, preserving the
 * first one's value (so a non-'2' value keeps its own value warning, which offers
 * [UseRulesVersion2Fix] separately).
 */
class MoveRulesVersionToTopFix(file: FirebaseRulesFile) :
    PsiUpdateModCommandAction<FirebaseRulesFile>(file) {

    override fun getFamilyName(): String = "Move 'rules_version' before the 'service' block"

    override fun invoke(context: ActionContext, file: FirebaseRulesFile, updater: ModPsiUpdater) {
        val project = context.project
        val versions = PsiTreeUtil.getChildrenOfType(file, FirebaseRulesRulesVersionStatement::class.java)
            ?.toList().orEmpty()
        if (versions.isEmpty()) return

        val value = FirebaseRulesDiagnostics.rulesVersion(versions.first()) ?: "2"
        versions.forEach { it.deleteWithLeadingWhitespace() }

        val statement = FirebaseRulesElementFactory.rulesVersionStatement(project, value)
        prependStatementToFile(file, statement)
    }
}
