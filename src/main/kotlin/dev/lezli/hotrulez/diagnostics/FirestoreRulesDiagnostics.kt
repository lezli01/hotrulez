package dev.lezli.hotrulez.diagnostics

import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import dev.lezli.hotrulez.psi.FirestoreRulesFile
import dev.lezli.hotrulez.psi.FirestoreRulesMatchPath
import dev.lezli.hotrulez.psi.FirestoreRulesParenPathSegment
import dev.lezli.hotrulez.psi.FirestoreRulesPathNameSegment
import dev.lezli.hotrulez.psi.FirestoreRulesPathWildcard
import dev.lezli.hotrulez.psi.FirestoreRulesRulesVersionStatement
import dev.lezli.hotrulez.psi.FirestoreRulesTypes as T

/**
 * Shared facts and PSI helpers used by the Firestore Rules diagnostics.
 *
 * Diagnostics are split by *severity*, which also answers the
 * "annotator vs inspection" question for each check:
 *
 *  - [FirestoreRulesAnnotator] reports the unambiguous, grammar-inexpressible
 *    ERRORS that are always wrong in any Firestore Rules file (always on,
 *    not user-configurable).
 *  - [FirestoreRulesStructureInspection] and [FirestoreRulesUsageInspection]
 *    report configurable WARNINGS for file shape and suspicious usage, so a
 *    partial or snippet file does not flash hard errors and users can tune or
 *    suppress them.
 *
 * All wording stays structural/syntactic: diagnostics describe Firestore Rules
 * *syntax and structure*, never whether a rule is secure or correctly
 * authorizes a request.
 */
internal object FirestoreRulesDiagnostics {
    /**
     * Operation names accepted inside an `allow` rule for Cloud Firestore
     * (`read`/`write` plus the granular methods they expand to).
     */
    val ALLOW_OPERATIONS = setOf("read", "write", "get", "list", "create", "update", "delete")

    /**
     * Path-resolving helper calls documented to take exactly one path argument:
     * `get`, `getAfter`, `exists`, `existsAfter` (bare or as `firestore.*`).
     */
    val SINGLE_PATH_HELPERS = setOf("get", "getAfter", "exists", "existsAfter")

    /** The only service a Firestore Rules file targets. */
    const val SERVICE_FIRESTORE = "cloud.firestore"

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
        val file = element.containingFile as? FirestoreRulesFile ?: return null
        // Memoized per file: the annotator and usage inspection each call this once
        // per recursive wildcard on every keystroke, and the value only changes when
        // the file's PSI changes (which invalidates the cache).
        return CachedValuesManager.getCachedValue(file) {
            val statement = PsiTreeUtil.getChildrenOfType(file, FirestoreRulesRulesVersionStatement::class.java)
                ?.firstOrNull()
            CachedValueProvider.Result.create(statement?.string?.text?.let(::unquote), file)
        }
    }

    /** The path segments of [path] in source order, excluding the `/` separators. */
    fun pathSegments(path: FirestoreRulesMatchPath): List<PsiElement> =
        path.node.getChildren(null)
            .filter { it.elementType in PATH_SEGMENT_TYPES }
            .map { it.psi }

    /**
     * Whether [path] is the conventional root `/databases/{database}/documents`.
     * The database segment may be a wildcard (`{database}`) or the documented
     * `(default)` literal (parsed as a paren path segment).
     */
    fun isRootDocumentsPath(path: FirestoreRulesMatchPath?): Boolean {
        if (path == null) return false
        val segments = pathSegments(path)
        if (segments.size != 3) return false
        val first = segments[0] as? FirestoreRulesPathNameSegment ?: return false
        val third = segments[2] as? FirestoreRulesPathNameSegment ?: return false
        return first.identifier.text == "databases" &&
            (segments[1] is FirestoreRulesPathWildcard || segments[1] is FirestoreRulesParenPathSegment) &&
            third.identifier.text == "documents"
    }

    /**
     * Removes a single matching pair of surrounding quote characters, leaving any
     * inner quotes intact. Unlike [String.trim] with quote chars, it does not strip
     * quote characters that are part of the value itself.
     */
    private fun unquote(text: String): String {
        if (text.length >= 2) {
            val quote = text.first()
            if ((quote == '\'' || quote == '"') && text.last() == quote) {
                return text.substring(1, text.length - 1)
            }
        }
        return text
    }
}
