package dev.lezli.hotrulez.diagnostics.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import dev.lezli.hotrulez.psi.FirebaseRulesBlock
import dev.lezli.hotrulez.references.FirebaseRulesElementFactory

/**
 * Inserts the conventional root match into a `service` block that is missing it — keyed on
 * the detected dialect's [rootMatchPath] (`/databases/{database}/documents` for Firestore,
 * `/b/{bucket}/o` for Storage). The match is added as the block's first entry and the caret
 * lands inside it.
 */
class InsertRootMatchFix(
    serviceBlock: FirebaseRulesBlock,
    private val rootMatchPath: String,
) : PsiUpdateModCommandAction<FirebaseRulesBlock>(serviceBlock) {

    override fun getFamilyName(): String = "Insert root 'match $rootMatchPath' block"

    override fun invoke(context: ActionContext, block: FirebaseRulesBlock, updater: ModPsiUpdater) {
        val project = context.project
        val match = FirebaseRulesElementFactory.matchDeclaration(project, rootMatchPath)

        insertAfterOpeningBrace(block, match)
        val reformatted = CodeStyleManager.getInstance(project).reformat(block) as FirebaseRulesBlock
        moveCaretInsideBlock(updater, reformatted.matchDeclarationList.firstOrNull()?.block)
    }
}
