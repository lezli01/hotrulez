package dev.lezli.hotrulez.diagnostics.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import dev.lezli.hotrulez.psi.FirebaseRulesArgumentList
import dev.lezli.hotrulez.psi.FirebaseRulesBlock
import dev.lezli.hotrulez.psi.FirebaseRulesCallExpression
import dev.lezli.hotrulez.psi.FirebaseRulesFunctionDeclaration
import dev.lezli.hotrulez.psi.FirebaseRulesReferenceExpression
import dev.lezli.hotrulez.psi.FirebaseRulesRulesVersionStatement
import dev.lezli.hotrulez.references.FirebaseRulesElementFactory

/**
 * Scaffolds a missing function for an unresolved call `x(a, b)` as
 * `function x(arg1, arg2) { return false; }`, placed in the nearest enclosing block (or the
 * file top level) so the call resolves to it, with the same arity as the call site. `false`
 * is the safe default body and is left selected for the user to replace with a real
 * condition.
 */
class CreateFunctionFix(callee: FirebaseRulesReferenceExpression) :
    PsiUpdateModCommandAction<FirebaseRulesReferenceExpression>(callee) {

    private val functionName = callee.identifier.text

    override fun getFamilyName(): String = "Create function '$functionName'"

    override fun invoke(context: ActionContext, callee: FirebaseRulesReferenceExpression, updater: ModPsiUpdater) {
        val project = context.project
        val call = callee.parent as? FirebaseRulesCallExpression ?: return
        val parameters = (1..argumentCount(call.argumentList)).map { "arg$it" }
        val function = FirebaseRulesElementFactory.functionDeclaration(project, functionName, parameters)

        val block = PsiTreeUtil.getParentOfType(callee, FirebaseRulesBlock::class.java)
        val file = callee.containingFile
        val inserted: PsiElement = if (block != null) {
            insertAfterOpeningBrace(block, function)
        } else {
            // No enclosing service/match block: the unresolved call sits in a top-level function
            // body, so the scaffolded helper is itself a top-level declaration. Anchor it after
            // any `rules_version` statement — inserting before the raw firstChild would demote
            // rules_version from the first statement, a shape the Firebase compiler rejects.
            val rulesVersion = PsiTreeUtil.getChildrenOfType(file, FirebaseRulesRulesVersionStatement::class.java)?.firstOrNull()
            if (rulesVersion != null) {
                file.addAfter(function, rulesVersion)
            } else {
                val anchor = file.firstChild
                if (anchor != null) file.addBefore(function, anchor) else file.add(function)
            }
        }

        // Reformat via the whole file: reformatting only the enclosing block mis-indents its
        // existing statements when the block is deeply nested.
        CodeStyleManager.getInstance(project).reformat(file)
        val body = (inserted as? FirebaseRulesFunctionDeclaration)?.functionBody
        body?.returnStatementList?.firstOrNull()?.expression?.let { updater.select(it) }
    }

    private fun argumentCount(argumentList: FirebaseRulesArgumentList?): Int {
        if (argumentList == null) return 0
        return argumentList.expressionList.size + argumentList.pathArgumentList.size
    }
}
