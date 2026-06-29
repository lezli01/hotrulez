package dev.lezli.hotrulez.references

import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import dev.lezli.hotrulez.psi.FirebaseRulesFile
import dev.lezli.hotrulez.psi.FirebaseRulesServiceDeclaration

/**
 * A Firebase Security Rules dialect. Cloud Firestore and Cloud Storage rules are
 * written in the *same* rules language (same lexer, grammar, formatter, and
 * symbol scoping); they differ only in:
 *
 *  - the `service` name (`cloud.firestore` vs `firebase.storage`),
 *  - the conventional root `match` path (`/databases/{database}/documents` vs
 *    `/b/{bucket}/o`),
 *  - the documented `request`/`resource` member tables, and
 *  - the built-in path helpers (`get`/`exists`/`getAfter`/`existsAfter` are
 *    Firestore document lookups; Storage instead reaches Firestore through the
 *    cross-service `firestore.get`/`firestore.exists` member calls).
 *
 * Those differences are captured here as **data** so the single language
 * implementation can adapt to whichever service a file targets. The dialect is
 * detected from the file content (the `service` declaration — see [forFile]),
 * because both dialects share the `.rules` extension and Firebase itself keys on
 * the declared service. A file with no recognised service is in a *neutral*
 * state (`null`): service-specific checks are suppressed and completion offers
 * the union of both dialects.
 *
 * Member tables are doc-sourced (Firebase reference docs, re-checked 2026-06-29:
 * the Storage `resource`/`request.resource` metadata fields, the
 * `service firebase.storage` / `match /b/{bucket}/o` shape, and the cross-service
 * `firestore.get`/`firestore.exists` calls). As with Firestore, this is **not**
 * type inference — members are a fixed list and custom metadata/claims are not
 * invented.
 */
enum class RulesService(
    /** The exact `service` identifier this dialect targets, e.g. `cloud.firestore`. */
    val serviceName: String,
    /** The conventional root match path, used only in diagnostic wording. */
    val rootMatchHint: String,
    /** Top-level built-in variables offered in expression position for this dialect. */
    val globals: List<String>,
    /** Bare path-helper calls valid in this dialect (Firestore-only; empty for Storage). */
    val bareHelpers: List<String>,
    /** Shallow member table keyed by whitespace-stripped receiver text, one to two levels deep. */
    val members: Map<String, List<String>>,
) {
    FIRESTORE(
        serviceName = "cloud.firestore",
        rootMatchHint = "/databases/{database}/documents",
        globals = listOf("request", "resource"),
        bareHelpers = listOf("get", "exists", "getAfter", "existsAfter"),
        members = mapOf(
            // `query` is a Request member used for `list` operations; the rest are
            // confirmed in the Firestore conditions/structure guides.
            "request" to listOf("auth", "method", "path", "params", "resource", "time", "query"),
            "request.auth" to listOf("uid", "token"),
            "request.auth.token" to listOf("email", "email_verified", "phone_number", "name", "sub", "firebase"),
            "resource" to listOf("data", "id", "__name__"),
            "request.resource" to listOf("data", "id", "__name__"),
        ),
    ),
    STORAGE(
        serviceName = "firebase.storage",
        rootMatchHint = "/b/{bucket}/o",
        // `firestore` is the namespace for the cross-service firestore.get/exists calls.
        globals = listOf("request", "resource", "firestore"),
        // Storage has no bare path helpers; cross-service access is firestore.get/exists.
        bareHelpers = emptyList(),
        members = mapOf(
            // Storage `request` has no `method`/`query`; it carries `params`/`path`.
            "request" to listOf("auth", "params", "path", "resource", "time"),
            "request.auth" to listOf("uid", "token"),
            "request.auth.token" to listOf("email", "email_verified", "phone_number", "name", "sub", "firebase"),
            // The existing object's full metadata (rules.storage Resource reference).
            "resource" to listOf(
                "name", "bucket", "generation", "metageneration", "size", "timeCreated",
                "updated", "md5Hash", "crc32c", "etag", "contentDisposition", "contentEncoding",
                "contentLanguage", "contentType", "metadata",
            ),
            // The incoming upload: the writable subset (no generation/metageneration/etag/
            // timeCreated/updated, which the server assigns).
            "request.resource" to listOf(
                "name", "bucket", "size", "md5Hash", "crc32c", "contentDisposition",
                "contentEncoding", "contentLanguage", "contentType", "metadata",
            ),
            // Cross-service Firestore access available from Storage rules.
            "firestore" to listOf("get", "exists"),
        ),
    ),
    ;

    companion object {
        /** Every recognised service name, offered by `service ` completion. */
        val SERVICE_NAMES: List<String> = entries.map { it.serviceName }

        /**
         * Cross-service path helpers callable from Storage rules as
         * `firestore.get(path)` / `firestore.exists(path)`, each taking one path argument.
         */
        val CROSS_SERVICE_HELPERS: Set<String> = setOf("get", "exists")

        /**
         * Every name the resolver treats as a non-navigable built-in across all
         * dialects (so it is never reported as an undefined reference). Service-agnostic
         * on purpose: recognising `firestore` or `getAfter` in the "wrong" dialect is
         * harmless, whereas missing one would risk a false "undefined" later.
         */
        val ALL_BUILTIN_NAMES: Set<String> =
            (entries.flatMap { it.globals } + entries.flatMap { it.bareHelpers } + CROSS_SERVICE_HELPERS).toSet()

        /** Union of every dialect's member table, used in neutral mode (no service declared). */
        val UNION_MEMBERS: Map<String, List<String>> =
            entries.flatMap { it.members.keys }.toSet().associateWith { key ->
                entries.flatMap { it.members[key].orEmpty() }.distinct()
            }

        /** The dialect for [name] (whitespace already stripped), or null if unrecognised. */
        fun fromServiceName(name: String?): RulesService? =
            entries.firstOrNull { it.serviceName == name }

        /**
         * The dialect declared by [file], or null when no recognised `service` block is
         * present. Memoised per file (the value only changes when the file's PSI does),
         * since diagnostics and completion query it repeatedly while editing.
         */
        fun forFile(file: FirebaseRulesFile): RulesService? =
            CachedValuesManager.getCachedValue(file) {
                val service = PsiTreeUtil.getChildrenOfType(file, FirebaseRulesServiceDeclaration::class.java)
                    ?.firstOrNull()
                val name = service?.serviceName?.text?.filterNot { it.isWhitespace() }
                CachedValueProvider.Result.create(fromServiceName(name), file)
            }

        /** The dialect for the file containing [element], or null when neutral. */
        fun forElement(element: PsiElement): RulesService? =
            (element.containingFile as? FirebaseRulesFile)?.let { forFile(it) }

        /** Member table for [service], falling back to the union of all dialects when neutral. */
        fun membersFor(service: RulesService?): Map<String, List<String>> =
            service?.members ?: UNION_MEMBERS

        /** Expression-position globals for [service], falling back to the union when neutral. */
        fun globalsFor(service: RulesService?): List<String> =
            service?.globals ?: entries.flatMap { it.globals }.distinct()

        /**
         * Bare path helpers for [service]. In neutral mode we offer the Firestore set:
         * it is the only dialect with bare helpers, and offering them on a service-less
         * snippet is harmless.
         */
        fun bareHelpersFor(service: RulesService?): List<String> =
            service?.bareHelpers ?: FIRESTORE.bareHelpers
    }
}
