package dev.lezli.hotrulez.diagnostics.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.util.PsiTreeUtil
import dev.lezli.hotrulez.psi.FirebaseRulesMatchPath
import dev.lezli.hotrulez.psi.FirebaseRulesRecursiveWildcard
import dev.lezli.hotrulez.references.FirebaseRulesElementFactory

/**
 * Moves a recursive wildcard `{name=**}` to the last segment of its match path — the only
 * placement Firebase permits under `rules_version = '1'`. `match /{rest=**}/songs` becomes
 * `match /songs/{rest=**}`, preserving every segment (and the wildcard's name); the other
 * offered fix upgrades to v2, where any placement is legal.
 */
class MoveRecursiveWildcardToLastFix(wildcard: FirebaseRulesRecursiveWildcard) :
    PsiUpdateModCommandAction<FirebaseRulesRecursiveWildcard>(wildcard) {

    override fun getFamilyName(): String = "Move recursive wildcard to the last segment"

    override fun invoke(context: ActionContext, wildcard: FirebaseRulesRecursiveWildcard, updater: ModPsiUpdater) {
        val matchPath = PsiTreeUtil.getParentOfType(wildcard, FirebaseRulesMatchPath::class.java) ?: return
        // getChildren() returns the composite segment nodes in order (the '/' separators are
        // leaf tokens and are excluded), so reordering their text rebuilds the path.
        val segments = matchPath.children.toList()
        if (segments.size < 2 || segments.last() === wildcard) return

        val reordered = segments.filter { it !== wildcard } + wildcard
        val newPath = reordered.joinToString(separator = "/", prefix = "/") { it.text }
        matchPath.replace(FirebaseRulesElementFactory.matchPath(context.project, newPath))
    }
}
