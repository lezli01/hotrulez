package dev.lezli.hotrulez.references

/**
 * Static, doc-sourced vocabulary shared by **all** Firebase Rules dialects, used
 * by completion and by the resolver to recognise built-ins.
 *
 * The dialect-specific vocabulary — `request`/`resource` member tables, built-in
 * path helpers, top-level globals, the root-match shape, and the service name —
 * lives in [RulesService], because it differs between Cloud Firestore and Cloud
 * Storage. What stays here is the vocabulary that is identical across services:
 * the `allow` operations, expression literals, and structural keywords.
 *
 * This is deliberately **not** type inference — members are a fixed list, custom
 * `request.auth.token` claims are not invented, and entries the guide pages do
 * not directly confirm are marked UNCONFIRMED.
 */
object FirebaseRulesBuiltins {
    /**
     * `allow` operation names. Identical for Cloud Firestore and Cloud Storage:
     * both expose `read`/`write` plus the granular methods they expand to
     * (`list` is rules_version '2' only in both services).
     */
    val OPERATIONS = listOf("get", "list", "read", "create", "update", "delete", "write")

    /** Expression literals. */
    val LITERALS = listOf("true", "false", "null")

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
     * Whether [name] is a built-in variable or path helper in any Firebase Rules
     * dialect (so the resolver does not treat it as an undefined reference). The
     * union is intentionally service-agnostic — see [RulesService.ALL_BUILTIN_NAMES].
     */
    fun isBuiltinName(name: String): Boolean = name in RulesService.ALL_BUILTIN_NAMES
}
