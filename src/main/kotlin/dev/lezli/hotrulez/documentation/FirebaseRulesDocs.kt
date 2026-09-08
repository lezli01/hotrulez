package dev.lezli.hotrulez.documentation

import dev.lezli.hotrulez.references.FirebaseRulesBuiltins
import dev.lezli.hotrulez.references.RulesService

/**
 * The single source of **doc prose** for Firebase Security Rules built-ins.
 *
 * Today's vocabulary tables give the plugin its *names* but not its *descriptions*:
 * [RulesService.members] / `.globals` / `.bareHelpers` and
 * [FirebaseRulesBuiltins.OPERATIONS] / `.TYPE_NAMES` / `.GLOBALS` are lists of bare
 * identifiers. Quick documentation (and, for helpers, parameter info) needs a short,
 * doc-grounded summary per name. Rather than bloat those name tables — which are also
 * consumed by completion, the highlighter, and the resolver — this object carries the
 * prose separately, keyed off the **same** whitespace-stripped keys those tables use.
 * A name present in a vocabulary table but missing here is therefore a visible,
 * test-asserted gap, never a crash.
 *
 * ### Keys
 * - [forMember] is keyed by the full, whitespace-stripped member path exactly as it is
 *   composed from [RulesService.members] (`request.auth`, `request.auth.token.email`,
 *   `resource.size`, …). The documentation provider validates a member against
 *   `RulesService.membersFor(forElement)` **before** consulting this table, so a
 *   Storage-only member (`resource.size`) never surfaces inside a Firestore file even
 *   though the flat map holds both dialects' entries side by side.
 * - [forOperation] / [forGlobal] / [forHelper] / [forNamespace] are keyed by the bare
 *   name from the corresponding vocabulary list.
 *
 * ### Dialect divergence (kept to one shared phrasing)
 * The only full-path keys whose *meaning* differs between Cloud Firestore and Cloud
 * Storage are the umbrella globals `resource` / `request.resource` (existing document
 * vs stored object; document-being-written vs object-being-uploaded). Per the 0.8
 * design decision these get a single summary that names both services rather than a
 * per-dialect override map — every leaf member *below* them is dialect-exclusive
 * (verified against `RulesService.FIRESTORE.members` / `.STORAGE.members`), so no other
 * key collides.
 *
 * ### Content shape
 * Each [Entry] stores CONTENT-ready fragments: [Entry.title] becomes the definition
 * line (plain text so parameter info can reuse a helper signature verbatim) and
 * [Entry.summaryHtml] the body, using only Swing-HTML-safe inline markup (`<code>`,
 * `<b>`). [Entry.docUrl] is the **per-category** Firebase reference page (not a
 * per-member anchor, which the docs do not publish stably).
 *
 * ### Not type inference
 * As with the vocabulary tables, this is deliberately **not** type inference. Members
 * are a fixed, doc-sourced list; custom `request.auth.token` claims and custom object
 * metadata are not invented; and namespace *members* (`math.abs`, `timestamp.date`, …)
 * are intentionally **not** enumerated here (their arity/return shape is unconfirmed as
 * a table) — only the namespace itself is documented. Anything the official Firebase
 * docs do not directly confirm is tagged `UNCONFIRMED` with a TODO tied to its source.
 *
 * Firebase reference pages, re-checked 2026-09-08 (see spec "Documentation Sources"):
 * rules-structure, rules-language, rules-conditions, rules.firestore.Request, and the
 * Cloud Storage storage/security/rules-conditions guide (which replaces the retired
 * rules.storage reference page — see [URL_STORAGE]).
 */
object FirebaseRulesDocs {

    /**
     * A single documentable entity's prose.
     *
     * @property title      the definition line — a bare path/name, or a helper's
     *                       `name(path)` signature (plain text; the provider wraps it,
     *                       and parameter info reuses helper titles verbatim).
     * @property summaryHtml a short description using only `<code>` / `<b>` inline markup.
     * @property docUrl      the per-category Firebase reference page for open-in-browser.
     */
    data class Entry(val title: String, val summaryHtml: String, val docUrl: String)

    // --- Per-category Firebase reference pages (docUrl values; not per-member anchors) ---

    /** Rules structure: `service`/`match`/`allow` shape, `rules_version`, operations. */
    private const val URL_STRUCTURE =
        "https://firebase.google.com/docs/firestore/security/rules-structure"

    /** Rules language: data types, `is`, conversion functions, global namespaces. */
    private const val URL_LANGUAGE =
        "https://firebase.google.com/docs/rules/rules-language"

    /** Conditions: `request`/`resource`, `get`/`exists`/`getAfter`/`existsAfter`. */
    private const val URL_CONDITIONS =
        "https://firebase.google.com/docs/firestore/security/rules-conditions"

    /** The `Request` member reference (Cloud Firestore `request.*` / `resource.*`). */
    private const val URL_REQUEST =
        "https://firebase.google.com/docs/reference/rules/rules.firestore.Request"

    /**
     * The Cloud Storage `resource` / `request.resource` metadata reference.
     *
     * Firebase retired `docs/reference/rules/rules.storage` — it 404s as of 2026-09-08.
     * The live page carrying the object-metadata tables these Storage entries are
     * sourced from is the Cloud Storage rules-conditions guide, so `docUrl` points
     * there. (`docs/reference/security/storage` is also live but publishes a narrower
     * `request.resource` list than the one this plugin models.)
     */
    private const val URL_STORAGE =
        "https://firebase.google.com/docs/storage/security/rules-conditions"

    // ------------------------------------------------------------------------------------
    // Members — keyed by full whitespace-stripped path, as RulesService.members composes.
    // Firestore `request.*` / `resource.*` cite the Request reference; Storage-only object
    // metadata cites the Storage reference. Shared keys (auth/params/path/time, and the
    // divergent `request.resource`) appear once with a service-neutral summary.
    // ------------------------------------------------------------------------------------
    private val MEMBERS: Map<String, Entry> = mapOf(
        // --- request.auth (shared) ---
        "request.auth" to Entry(
            "request.auth",
            "The authentication context of the requester, or <code>null</code> for an " +
                "unauthenticated request. Its <code>uid</code> and <code>token</code> " +
                "identify the signed-in user.",
            URL_REQUEST,
        ),
        "request.auth.uid" to Entry(
            "request.auth.uid",
            "The unique ID of the authenticated user making the request.",
            URL_REQUEST,
        ),
        "request.auth.token" to Entry(
            "request.auth.token",
            "A <code>map</code> of the requester's Firebase Auth token claims (email, " +
                "phone number, and any custom claims).",
            URL_REQUEST,
        ),
        "request.auth.token.email" to Entry(
            "request.auth.token.email",
            "The email address associated with the account, if present on the token.",
            URL_REQUEST,
        ),
        "request.auth.token.email_verified" to Entry(
            "request.auth.token.email_verified",
            "<code>true</code> if the user has verified their email address.",
            URL_REQUEST,
        ),
        "request.auth.token.phone_number" to Entry(
            "request.auth.token.phone_number",
            "The phone number associated with the account, if present on the token.",
            URL_REQUEST,
        ),
        "request.auth.token.name" to Entry(
            "request.auth.token.name",
            "The display name of the user, if set on the token.",
            URL_REQUEST,
        ),
        "request.auth.token.sub" to Entry(
            "request.auth.token.sub",
            "The token subject — the user's <code>uid</code>, identical to " +
                "<code>request.auth.uid</code>.",
            URL_REQUEST,
        ),
        "request.auth.token.firebase" to Entry(
            "request.auth.token.firebase",
            "A <code>map</code> of Firebase-specific claims, including " +
                "<code>sign_in_provider</code> and <code>identities</code>.",
            URL_REQUEST,
        ),
        // --- Firestore request.* leaves ---
        "request.method" to Entry(
            "request.method",
            "The operation being attempted — one of <code>get</code>, <code>list</code>, " +
                "<code>create</code>, <code>update</code>, or <code>delete</code>.",
            URL_REQUEST,
        ),
        "request.path" to Entry(
            "request.path",
            "The <code>path</code> of the resource targeted by the request.",
            URL_REQUEST,
        ),
        "request.params" to Entry(
            "request.params",
            "A <code>map</code> of the parameters supplied with the request.",
            URL_REQUEST,
        ),
        "request.time" to Entry(
            "request.time",
            "A <code>timestamp</code> of the server time at which the request was received.",
            URL_REQUEST,
        ),
        "request.query" to Entry(
            "request.query",
            "The constraints of a <code>list</code> operation: <code>limit</code>, " +
                "<code>offset</code>, and <code>orderBy</code>.",
            URL_REQUEST,
        ),
        // --- request.resource (divergent → one shared phrasing, Decision #2) ---
        "request.resource" to Entry(
            "request.resource",
            "The resource as it would exist <b>after</b> the request: the document being " +
                "written (Cloud Firestore) or the object being uploaded (Cloud Storage). " +
                "Its pending <code>data</code>/metadata is what write rules validate.",
            URL_REQUEST,
        ),
        // --- Firestore request.resource.* leaves ---
        "request.resource.data" to Entry(
            "request.resource.data",
            "A <code>map</code> of the field names and values the write would set on the " +
                "document.",
            URL_REQUEST,
        ),
        "request.resource.id" to Entry(
            "request.resource.id",
            "The document ID of the resource being written.",
            URL_REQUEST,
        ),
        "request.resource.__name__" to Entry(
            "request.resource.__name__",
            "The fully-qualified path of the document being written.",
            URL_REQUEST,
        ),
        // --- Firestore resource.* leaves ---
        "resource.data" to Entry(
            "resource.data",
            "A <code>map</code> of the existing document's field names and values, " +
                "<b>before</b> the request is applied.",
            URL_REQUEST,
        ),
        "resource.id" to Entry(
            "resource.id",
            "The document ID of the existing resource.",
            URL_REQUEST,
        ),
        "resource.__name__" to Entry(
            "resource.__name__",
            "The fully-qualified path of the existing document.",
            URL_REQUEST,
        ),
        // --- Storage request.resource.* leaves (the writable upload subset) ---
        "request.resource.name" to Entry(
            "request.resource.name",
            "The full path name of the object being uploaded.",
            URL_STORAGE,
        ),
        "request.resource.bucket" to Entry(
            "request.resource.bucket",
            "The name of the Cloud Storage bucket the object will reside in.",
            URL_STORAGE,
        ),
        "request.resource.size" to Entry(
            "request.resource.size",
            "The size, in bytes, of the object being uploaded.",
            URL_STORAGE,
        ),
        "request.resource.md5Hash" to Entry(
            "request.resource.md5Hash",
            "The MD5 hash of the object being uploaded.",
            URL_STORAGE,
        ),
        "request.resource.crc32c" to Entry(
            "request.resource.crc32c",
            "The CRC32C checksum of the object being uploaded.",
            URL_STORAGE,
        ),
        "request.resource.contentDisposition" to Entry(
            "request.resource.contentDisposition",
            "The <code>Content-Disposition</code> header of the object being uploaded.",
            URL_STORAGE,
        ),
        "request.resource.contentEncoding" to Entry(
            "request.resource.contentEncoding",
            "The <code>Content-Encoding</code> header of the object being uploaded.",
            URL_STORAGE,
        ),
        "request.resource.contentLanguage" to Entry(
            "request.resource.contentLanguage",
            "The <code>Content-Language</code> header of the object being uploaded.",
            URL_STORAGE,
        ),
        "request.resource.contentType" to Entry(
            "request.resource.contentType",
            "The <code>Content-Type</code> (MIME type) of the object being uploaded.",
            URL_STORAGE,
        ),
        "request.resource.metadata" to Entry(
            "request.resource.metadata",
            "A <code>map</code> of developer-provided custom metadata for the object being " +
                "uploaded.",
            URL_STORAGE,
        ),
        // --- Storage resource.* leaves (the existing object's full metadata) ---
        "resource.name" to Entry(
            "resource.name",
            "The full path name of the existing object.",
            URL_STORAGE,
        ),
        "resource.bucket" to Entry(
            "resource.bucket",
            "The name of the Cloud Storage bucket containing the object.",
            URL_STORAGE,
        ),
        "resource.generation" to Entry(
            "resource.generation",
            "The Google Cloud Storage object generation — its data version identifier.",
            URL_STORAGE,
        ),
        "resource.metageneration" to Entry(
            "resource.metageneration",
            "The Google Cloud Storage object metageneration — the version of its metadata.",
            URL_STORAGE,
        ),
        "resource.size" to Entry(
            "resource.size",
            "The size, in bytes, of the existing object.",
            URL_STORAGE,
        ),
        "resource.timeCreated" to Entry(
            "resource.timeCreated",
            "A <code>timestamp</code> of when the object was created.",
            URL_STORAGE,
        ),
        "resource.updated" to Entry(
            "resource.updated",
            "A <code>timestamp</code> of when the object was last updated.",
            URL_STORAGE,
        ),
        "resource.md5Hash" to Entry(
            "resource.md5Hash",
            "The MD5 hash of the existing object.",
            URL_STORAGE,
        ),
        "resource.crc32c" to Entry(
            "resource.crc32c",
            "The CRC32C checksum of the existing object.",
            URL_STORAGE,
        ),
        "resource.etag" to Entry(
            "resource.etag",
            "The entity tag (ETag) of the existing object.",
            URL_STORAGE,
        ),
        "resource.contentDisposition" to Entry(
            "resource.contentDisposition",
            "The <code>Content-Disposition</code> header of the existing object.",
            URL_STORAGE,
        ),
        "resource.contentEncoding" to Entry(
            "resource.contentEncoding",
            "The <code>Content-Encoding</code> header of the existing object.",
            URL_STORAGE,
        ),
        "resource.contentLanguage" to Entry(
            "resource.contentLanguage",
            "The <code>Content-Language</code> header of the existing object.",
            URL_STORAGE,
        ),
        "resource.contentType" to Entry(
            "resource.contentType",
            "The <code>Content-Type</code> (MIME type) of the existing object.",
            URL_STORAGE,
        ),
        "resource.metadata" to Entry(
            "resource.metadata",
            "A <code>map</code> of developer-provided custom metadata on the existing object.",
            URL_STORAGE,
        ),
    )

    // ------------------------------------------------------------------------------------
    // allow operations — shared by both dialects. `read`/`write` expand to granular ops;
    // `list` requires rules_version '2'. Phrased "document or object" to cover both.
    // ------------------------------------------------------------------------------------
    private val OPERATIONS: Map<String, Entry> = mapOf(
        "get" to Entry(
            "get",
            "Grants read access to a single document or object (a direct lookup). " +
                "Part of the <code>read</code> group.",
            URL_STRUCTURE,
        ),
        "list" to Entry(
            "list",
            "Grants read access to a set of documents or objects via a query or listing. " +
                "Requires <code>rules_version = '2'</code>. Part of the <code>read</code> group.",
            URL_STRUCTURE,
        ),
        "read" to Entry(
            "read",
            "Umbrella operation that expands to <code>get</code> + <code>list</code> — " +
                "grants all read access.",
            URL_STRUCTURE,
        ),
        "create" to Entry(
            "create",
            "Grants permission to create a new document or object that does not yet exist. " +
                "Part of the <code>write</code> group.",
            URL_STRUCTURE,
        ),
        "update" to Entry(
            "update",
            "Grants permission to modify an existing document or object. " +
                "Part of the <code>write</code> group.",
            URL_STRUCTURE,
        ),
        "delete" to Entry(
            "delete",
            "Grants permission to delete a document or object. " +
                "Part of the <code>write</code> group.",
            URL_STRUCTURE,
        ),
        "write" to Entry(
            "write",
            "Umbrella operation that expands to <code>create</code> + <code>update</code> + " +
                "<code>delete</code> — grants all write access.",
            URL_STRUCTURE,
        ),
    )

    // ------------------------------------------------------------------------------------
    // Built-in globals. `resource` gets one shared phrasing (Decision #2); `firestore` is
    // the Storage-only cross-service namespace.
    // ------------------------------------------------------------------------------------
    private val GLOBALS: Map<String, Entry> = mapOf(
        "request" to Entry(
            "request",
            "The context of the incoming request: the operation being attempted plus its " +
                "<code>auth</code>, <code>resource</code>, <code>time</code>, and related " +
                "properties.",
            URL_REQUEST,
        ),
        "resource" to Entry(
            "resource",
            "The resource being accessed as it currently exists: the existing document " +
                "(Cloud Firestore) or the stored object (Cloud Storage). Its " +
                "<code>data</code>/metadata reflects state <b>before</b> the request.",
            URL_CONDITIONS,
        ),
        "firestore" to Entry(
            "firestore",
            "The cross-service namespace available in Cloud Storage rules for reaching " +
                "Cloud Firestore, via <code>firestore.get(path)</code> and " +
                "<code>firestore.exists(path)</code>.",
            URL_STORAGE,
        ),
    )

    // ------------------------------------------------------------------------------------
    // Path helpers. `title` carries the `name(path)` signature (reused verbatim by
    // parameter info); the summary states the one-line purpose. `get`/`exists` double as
    // the cross-service `firestore.get`/`firestore.exists` in Storage rules.
    // ------------------------------------------------------------------------------------
    private val HELPERS: Map<String, Entry> = mapOf(
        "get" to Entry(
            "get(path)",
            "Reads the document at <code>path</code> and returns it as a " +
                "<code>resource</code> (its <code>data</code> and metadata), or " +
                "<code>null</code> if it does not exist. From Cloud Storage rules, call it " +
                "as <code>firestore.get(path)</code>.",
            URL_CONDITIONS,
        ),
        "exists" to Entry(
            "exists(path)",
            "Returns <code>true</code> if a document exists at <code>path</code>. From " +
                "Cloud Storage rules, call it as <code>firestore.exists(path)</code>.",
            URL_CONDITIONS,
        ),
        "getAfter" to Entry(
            "getAfter(path)",
            "Like <code>get</code>, but returns the document at <code>path</code> as it " +
                "will exist <b>after</b> the current operation commits — for validating " +
                "batched writes and transactions.",
            URL_CONDITIONS,
        ),
        "existsAfter" to Entry(
            "existsAfter(path)",
            "Like <code>exists</code>, but reports whether a document will exist at " +
                "<code>path</code> <b>after</b> the current operation commits.",
            URL_CONDITIONS,
        ),
    )

    // ------------------------------------------------------------------------------------
    // Type / global namespaces & conversion functions (FirebaseRulesBuiltins.TYPE_NAMES +
    // "debug"). One "what it is" line each. Namespace MEMBERS (math.abs, timestamp.date, …)
    // are deferred/UNCONFIRMED and intentionally not enumerated here (Decision #4).
    // ------------------------------------------------------------------------------------
    private val NAMESPACES: Map<String, Entry> = mapOf(
        "bool" to Entry(
            "bool",
            "The boolean type. Used as the right-hand side of <code>is</code>, " +
                "e.g. <code>x is bool</code>.",
            URL_LANGUAGE,
        ),
        "bytes" to Entry(
            "bytes",
            "The byte-sequence type for binary data; the right-hand side of " +
                "<code>is bytes</code>.",
            URL_LANGUAGE,
        ),
        "float" to Entry(
            "float",
            "The floating-point number type. Also a conversion function: " +
                "<code>float(x)</code> coerces a value to a float.",
            URL_LANGUAGE,
        ),
        "int" to Entry(
            "int",
            "The integer type. Also a conversion function: <code>int(x)</code> coerces a " +
                "value to an integer.",
            URL_LANGUAGE,
        ),
        "number" to Entry(
            "number",
            "The numeric type covering both <code>int</code> and <code>float</code> values.",
            URL_LANGUAGE,
        ),
        "string" to Entry(
            "string",
            "The string type. Also a conversion function: <code>string(x)</code> coerces a " +
                "value to its string form.",
            URL_LANGUAGE,
        ),
        "list" to Entry(
            "list",
            "The ordered-sequence type.",
            URL_LANGUAGE,
        ),
        "map" to Entry(
            "map",
            "The key/value map type — the type of <code>resource.data</code> and " +
                "<code>request.resource.data</code>.",
            URL_LANGUAGE,
        ),
        "set" to Entry(
            "set",
            "The set type — an unordered collection of unique values.",
            URL_LANGUAGE,
        ),
        "path" to Entry(
            "path",
            "The path type addressing a document or object. Also a conversion function: " +
                "<code>path(string)</code> builds a path from a string.",
            URL_LANGUAGE,
        ),
        "latlng" to Entry(
            "latlng",
            "The geographical-point type representing a latitude/longitude pair.",
            URL_LANGUAGE,
        ),
        "timestamp" to Entry(
            "timestamp",
            "The timestamp type representing a point in time, and the namespace of the " +
                "timestamp helper functions. <code>request.time</code> is a timestamp.",
            URL_LANGUAGE,
        ),
        "duration" to Entry(
            "duration",
            "The duration type representing a length of time, and the namespace of the " +
                "duration helper functions.",
            URL_LANGUAGE,
        ),
        // UNCONFIRMED TODO: precise definition of the `constraint` type is not spelled out
        // on the rules-language reference — https://firebase.google.com/docs/rules/rules-language
        "constraint" to Entry(
            "constraint",
            "The constraint type produced by rules data-validation expressions.",
            URL_LANGUAGE,
        ),
        "map_diff" to Entry(
            "map_diff",
            "The type returned by <code>map.diff(other)</code>, describing how two maps " +
                "differ (affected, added, removed, and changed keys).",
            URL_LANGUAGE,
        ),
        "math" to Entry(
            "math",
            "The namespace of built-in math functions (absolute value, rounding, and more).",
            URL_LANGUAGE,
        ),
        "hashing" to Entry(
            "hashing",
            "The namespace of cryptographic hashing functions (MD5, SHA-256, CRC32).",
            URL_LANGUAGE,
        ),
        "debug" to Entry(
            "debug",
            "A debugging helper: <code>debug(x)</code> logs its argument to the " +
                "rules-evaluation debug log (during emulator/test runs) and returns it " +
                "unchanged.",
            URL_LANGUAGE,
        ),
    )

    /**
     * Prose for a `request`/`resource` member, keyed by its full whitespace-stripped path
     * (`request.auth`, `request.auth.token.email`, `resource.size`, …) — the same key
     * [RulesService.members] composes. Both dialects' entries live in one flat map; the
     * caller is expected to have already validated the path against the file's dialect via
     * `RulesService.membersFor`. Returns `null` for an unknown path (no fabrication).
     */
    fun forMember(path: String): Entry? = MEMBERS[path]

    /**
     * Prose for an `allow` operation ([FirebaseRulesBuiltins.OPERATIONS]:
     * `get`/`list`/`read`/`create`/`update`/`delete`/`write`), or `null` if unknown.
     */
    fun forOperation(name: String): Entry? = OPERATIONS[name]

    /**
     * Prose for a built-in global (`request`, `resource`, or Storage's cross-service
     * `firestore` namespace), or `null` if unknown.
     */
    fun forGlobal(name: String): Entry? = GLOBALS[name]

    /**
     * Prose for a path helper (`get`/`exists`/`getAfter`/`existsAfter`), serving both the
     * bare Firestore helpers and the cross-service `firestore.get`/`firestore.exists`. The
     * [Entry.title] carries the `name(path)` signature, reused verbatim by parameter info.
     * Returns `null` if unknown.
     */
    fun forHelper(name: String): Entry? = HELPERS[name]

    /**
     * Prose for a type / global namespace or conversion function
     * ([FirebaseRulesBuiltins.TYPE_NAMES] plus `debug`), or `null` if unknown. Members
     * *of* these namespaces are intentionally not documented here (deferred/`UNCONFIRMED`).
     */
    fun forNamespace(name: String): Entry? = NAMESPACES[name]

    // --- Test-facing key views (bidirectional drift guard) ----------------------------
    // The forward invariant (every vocabulary name has prose) is asserted by
    // FirebaseRulesDocsTableTest; these views let the same suite assert the *reverse* —
    // that every prose key still maps back to a live vocabulary name — so prose orphaned
    // by a vocabulary removal or rename fails the build instead of lingering as dead text.

    /** The full member paths this table documents (see [forMember]). */
    internal val memberKeys: Set<String> get() = MEMBERS.keys

    /** The `allow` operation names this table documents (see [forOperation]). */
    internal val operationKeys: Set<String> get() = OPERATIONS.keys

    /** The built-in global names this table documents (see [forGlobal]). */
    internal val globalKeys: Set<String> get() = GLOBALS.keys

    /** The path-helper names this table documents (see [forHelper]). */
    internal val helperKeys: Set<String> get() = HELPERS.keys

    /** The type / namespace names this table documents (see [forNamespace]). */
    internal val namespaceKeys: Set<String> get() = NAMESPACES.keys

    /**
     * Every distinct [Entry.docUrl] in this table — the pages `getUrlFor` opens from quick
     * documentation. Firebase retires reference pages (`docs/reference/rules/rules.storage`
     * 404s as of 2026-09-08), so `FirebaseRulesDocsTableTest` pins these to a checked list
     * rather than letting a new entry inherit whatever URL its neighbour happened to carry.
     */
    internal val docUrls: Set<String>
        get() = sequenceOf(MEMBERS, OPERATIONS, GLOBALS, HELPERS, NAMESPACES)
            .flatMap { it.values.asSequence() }
            .mapTo(mutableSetOf()) { it.docUrl }
}
