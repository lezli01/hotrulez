package dev.lezli.hotrulez.diagnostics.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import dev.lezli.hotrulez.psi.FirebaseRulesBlock

/**
 * Fixes are written once as [ModCommandAction]s (see the concrete `*Fix` classes in this
 * package) and attached to both diagnostic surfaces:
 *
 *  - the annotator, via `AnnotationBuilder.withFix(action.asIntention())`, and
 *  - the inspections, via [asQuickFix] below, which wraps the same action as a
 *    [LocalQuickFix] for `ProblemsHolder` / `InspectionManager.createProblemDescriptor`.
 *
 * ModCommand is the platform-preferred quick-fix model on 2025.2: fixes mutate a
 * non-physical copy of the file (so intention preview and undo come for free) and
 * position the caret/selection declaratively through [com.intellij.modcommand.ModPsiUpdater].
 */
fun ModCommandAction.asQuickFix(): LocalQuickFix =
    LocalQuickFix.from(this)
        ?: error("LocalQuickFix.from returned null for ${this::class.simpleName}")

/**
 * Deletes [this] element along with the whitespace immediately before it, so removing a
 * statement does not leave behind the blank line that used to separate it from its
 * predecessor.
 */
internal fun PsiElement.deleteWithLeadingWhitespace() {
    (prevSibling as? PsiWhiteSpace)?.delete()
    delete()
}

/**
 * Positions the caret just inside the opening brace of [block], i.e. where a user would
 * start typing rules. Used by the scaffolding fixes that insert an empty `match` block so
 * the caret lands in the freshly-created block rather than at the edit site.
 */
internal fun moveCaretInsideBlock(updater: ModPsiUpdater, block: FirebaseRulesBlock?) {
    val lbrace = block?.firstChild ?: return
    updater.moveCaretTo(lbrace.textRange.endOffset)
}
