package dev.lezli.hotrulez.diagnostics

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import dev.lezli.hotrulez.psi.FirebaseRulesFile
import dev.lezli.hotrulez.psi.FirebaseRulesMatchPath
import dev.lezli.hotrulez.psi.FirebaseRulesParenPathSegment
import dev.lezli.hotrulez.psi.FirebaseRulesPathNameSegment
import dev.lezli.hotrulez.psi.FirebaseRulesPathWildcard
import dev.lezli.hotrulez.psi.FirebaseRulesRulesVersionStatement
import dev.lezli.hotrulez.psi.FirebaseRulesTypes as T

/**
 * Shared facts and PSI helpers used by the Firestore Rules diagnostics.
 *
 * Diagnostics are split by *severity*, which also answers the
 * "annotator vs inspection" question for each check:
 *
 *  - [FirebaseRulesAnnotator] reports the unambiguous, grammar-inexpressible
 *    ERRORS that are always wrong in any Firestore Rules file (always on,
 *    not user-configurable).
 *  - [FirebaseRulesStructureInspection] and [FirebaseRulesUsageInspection]
 *    report configurable WARNINGS for file shape and suspicious usage, so a
 *    partial or snippet file does not flash hard errors and users can tune or
 *    suppress them.
 *
 * All wording stays structural/syntactic: diagnostics describe Firestore Rules
 * *syntax and structure*, never whether a rule is secure or correctly
 * authorizes a request.
 */
internal object FirebaseRulesDiagnostics {
    /**
     * Operation names accepted inside an `allow` rule. Identical for Cloud Firestore
     * and Cloud Storage: `read`/`write` plus the granular methods they expand to.
     */
    val ALLOW_OPERATIONS = setOf("read", "write", "get", "list", "create", "update", "delete")

    /** PSI element types that make up a match path, in source order. */
    private val PATH_SEGMENT_TYPES = setOf(
        T.PATH_NAME_SEGMENT,
        T.PATH_WILDCARD,
        T.RECURSIVE_WILDCARD,
        T.PAREN_PATH_SEGMENT,
    )

    /**
     * The `rules_version` value (quotes stripped) declared by the file that
     * contains [element], or `null` when no `rules_version` statement is present.
     */
    fun rulesVersion(element: PsiElement): String? {
        val file = element.containingFile as? FirebaseRulesFile ?: return null
        // Memoized per file: the annotator and usage inspection each call this once
        // per recursive wildcard on every keystroke, and the value only changes when
        // the file's PSI changes (which invalidates the cache).
        return CachedValuesManager.getCachedValue(file) {
            val statement = PsiTreeUtil.getChildrenOfType(file, FirebaseRulesRulesVersionStatement::class.java)
                ?.firstOrNull()
            CachedValueProvider.Result.create(statement?.string?.text?.let(StringUtil::unquoteString), file)
        }
    }

    /** The path segments of [path] in source order, excluding the `/` separators. */
    fun pathSegments(path: FirebaseRulesMatchPath): List<PsiElement> =
        path.node.getChildren(null)
            .filter { it.elementType in PATH_SEGMENT_TYPES }
            .map { it.psi }

    /**
     * Whether [path] is the conventional root `/databases/{database}/documents`.
     * The database segment may be a wildcard (`{database}`) or the documented
     * `(default)` literal (parsed as a paren path segment).
     */
    fun isRootDocumentsPath(path: FirebaseRulesMatchPath?): Boolean {
        if (path == null) return false
        val segments = pathSegments(path)
        if (segments.size != 3) return false
        val first = segments[0] as? FirebaseRulesPathNameSegment ?: return false
        val third = segments[2] as? FirebaseRulesPathNameSegment ?: return false
        val database = segments[1]
        return first.pathText() == "databases" &&
            (database is FirebaseRulesPathWildcard ||
                (database as? FirebaseRulesParenPathSegment)?.identifier?.text == "default") &&
            third.pathText() == "documents"
    }

    /**
     * Whether [path] is the conventional Cloud Storage root `/b/{bucket}/o`. The
     * bucket segment is normally the `{bucket}` wildcard but may be a literal bucket
     * name, so only the surrounding `b` and `o` segments are required.
     */
    fun isRootBucketPath(path: FirebaseRulesMatchPath?): Boolean {
        if (path == null) return false
        val segments = pathSegments(path)
        if (segments.size != 3) return false
        val first = segments[0] as? FirebaseRulesPathNameSegment ?: return false
        val third = segments[2] as? FirebaseRulesPathNameSegment ?: return false
        return first.pathText() == "b" && third.pathText() == "o"
    }

    private fun FirebaseRulesPathNameSegment.pathText(): String =
        text.filterNot { it.isWhitespace() }

}
