package dev.lezli.hotrulez.diagnostics.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import dev.lezli.hotrulez.psi.FirebaseRulesParameter
import dev.lezli.hotrulez.psi.FirebaseRulesTypes

/**
 * Removes a duplicate function parameter along with the comma that separates it from a
 * neighbour, so `function f(a, a)` becomes `function f(a)` rather than `function f(a, )`.
 * The enclosing parameter list is reformatted to tidy any leftover spacing.
 */
class RemoveParameterFix(parameter: FirebaseRulesParameter) :
    PsiUpdateModCommandAction<FirebaseRulesParameter>(parameter) {

    override fun getFamilyName(): String = "Remove duplicate parameter"

    override fun invoke(context: ActionContext, parameter: FirebaseRulesParameter, updater: ModPsiUpdater) {
        val list = parameter.parent
        parameter.adjacentComma()?.delete()
        parameter.delete()
        CodeStyleManager.getInstance(context.project).reformat(list)
    }

    /** The comma separating this parameter from a neighbour: the preceding one if any, else the following. */
    private fun FirebaseRulesParameter.adjacentComma(): PsiElement? {
        var prev = prevSibling
        while (prev is PsiWhiteSpace) prev = prev.prevSibling
        if (prev?.node?.elementType == FirebaseRulesTypes.COMMA) return prev

        var next = nextSibling
        while (next is PsiWhiteSpace) next = next.nextSibling
        if (next?.node?.elementType == FirebaseRulesTypes.COMMA) return next

        return null
    }
}
