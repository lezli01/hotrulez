package dev.lezli.hotrulez.references

/**
 * Static, doc-sourced Firestore Rules vocabulary used by completion and by the
 * resolver to recognise built-ins.
 *
 * Built from the official Firebase reference docs (re-checked 2026-06-27; the
 * `service` name, the `get/list/read/create/update/delete/write` operations, the
 * `request`/`resource` built-ins, and the `get/exists/getAfter` helpers were
 * re-confirmed against the live conditions/structure/rules-language docs on
 * 2026-06-28): the `request`/`resource` member tables, helper calls, operations,
 * and keywords.
 * This is deliberately **not** type inference — members are a fixed list, custom
 * `request.auth.token` claims are not invented, and entries the guide pages do
 * not directly confirm are marked UNCONFIRMED.
 */
object FirestoreRulesBuiltins {
    /** `allow` operation names for Cloud Firestore. */
    val OPERATIONS = listOf("get", "list", "read", "create", "update", "delete", "write")

    /** Built-in top-level variables. Recognised by the resolver but non-navigable. */
    val GLOBALS = listOf("request", "resource")

    /** Path-resolving helper calls; each takes exactly one path argument. Non-navigable. */
    val HELPERS = listOf("get", "exists", "getAfter", "existsAfter")

    /** Expression literals. */
    val LITERALS = listOf("true", "false", "null")

    /** The single service a Firestore Rules file targets. */
    const val SERVICE_FIRESTORE = "cloud.firestore"

    /** Reserved keywords; rejected as renamed identifiers by the names validator. */
    val KEYWORDS = setOf(
        "rules_version", "service", "match", "allow", "if",
        "function", "return", "let", "true", "false", "null", "in", "is",
    )

    /** Structural keywords offered at file top level. */
    val TOP_LEVEL_KEYWORDS = listOf("rules_version", "service", "match", "function")

    /** Structural keywords offered inside a `service` / `match` block. */
    val BLOCK_KEYWORDS = listOf("match", "allow", "function")

    /** Structural keywords offered inside a function body. */
    val FUNCTION_BODY_KEYWORDS = listOf("let", "return")

    /**
     * Shallow member table keyed by the whitespace-stripped receiver expression
     * text. One to two levels deep, sourced from the Firebase reference docs.
     *
     * `request.resource.{id,__name__}` follow from the Resource interface but are
     * not shown on a write example on the consulted guide pages (UNCONFIRMED);
     * `data` is confirmed. `__path__` is intentionally absent — `__name__` is the
     * documented path member.
     *
     * TODO(UNCONFIRMED): confirm `request.resource.{id,__name__}` against the
     * Resource reference before relying on them in diagnostics —
     * https://firebase.google.com/docs/reference/rules/rules.firestore.Resource
     */
    val MEMBERS: Map<String, List<String>> = mapOf(
        // `query` is a Request member used for `list` operations (confirmed via the
        // rules.firestore.Request reference); the others are confirmed in the guides.
        "request" to listOf("auth", "method", "path", "params", "resource", "time", "query"),
        "request.auth" to listOf("uid", "token"),
        // Standard JWT / Firebase claims; arbitrary custom claims also exist and
        // are intentionally not enumerated (free-form member access stays valid).
        "request.auth.token" to listOf("email", "email_verified", "phone_number", "name", "sub", "firebase"),
        "resource" to listOf("data", "id", "__name__"),
        "request.resource" to listOf("data", "id", "__name__"),
    )

    fun isBuiltinName(name: String): Boolean = name in GLOBALS || name in HELPERS
}
