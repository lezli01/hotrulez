package dev.lezli.hotrulez.diagnostics.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.util.PsiTreeUtil
import dev.lezli.hotrulez.psi.FirebaseRulesFile
import dev.lezli.hotrulez.psi.FirebaseRulesRulesVersionStatement
import dev.lezli.hotrulez.references.FirebaseRulesElementFactory

/**
 * Ensures the file declares `rules_version = '2';`. If a version statement already exists
 * (any value, anywhere in the file), its literal is rewritten to `'2'`; otherwise a new
 * statement is inserted at the very top. One adaptive fix shared by three diagnostics: the
 * missing-version and non-v2 file-structure warnings, and the recursive-wildcard usage
 * warning (whose match semantics only hold under v2).
 */
class UseRulesVersion2Fix(file: FirebaseRulesFile) :
    PsiUpdateModCommandAction<FirebaseRulesFile>(file) {

    override fun getFamilyName(): String = "Set rules_version = '2';"

    override fun invoke(context: ActionContext, file: FirebaseRulesFile, updater: ModPsiUpdater) {
        val project = context.project
        val replacement = FirebaseRulesElementFactory.rulesVersionStatement(project, "2")

        val existing = PsiTreeUtil.getChildrenOfType(file, FirebaseRulesRulesVersionStatement::class.java)?.firstOrNull()
        if (existing != null) {
            existing.replace(replacement)
            return
        }

        val anchor = file.firstChild
        if (anchor == null) {
            file.add(replacement)
        } else {
            val inserted = file.addBefore(replacement, anchor)
            file.addAfter(PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n"), inserted)
        }
    }
}
