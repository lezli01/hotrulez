# HotRulez Tasks — m5 (Toward Semantics)

Status: **IMPLEMENTED** (2026-07-05). One conservative, service-aware semantic check —
`FirebaseRulesMemberInspection` — flags an unknown member on a *closed* built-in
receiver, plus the prerequisite table fixes it depends on. Candidate B
(operator/literal-type) is deferred (see `docs/spec.md`). Every task below is done;
`./gradlew test` (338 tests) and `verifyPlugin` (4 target IDEs) are green. The plan was
implementation-grilled and then adversarially reviewed by a workflow, which caught one
false-positive class — documented `rules.Map` methods on `request.auth`
(`request.auth.get(...)`) — now fixed by skipping members in call position.
Last updated: 2026-07-05.
Source: `docs/spec.md` (m5 — Toward Semantics).
Predecessor: `docs/m4/tasks.md` (the m4 Authoring Polish breakdown, shipped in
PR #31 and released alongside m3).

## Ground Rules

- [x] Re-confirm the load-bearing Firebase docs before coding the closed sets: the
  `request`/`resource` member reference for **both** dialects
  (`rules.firestore.Request`, `rules.firestore.Resource`, storage rules-conditions /
  reference), and that custom auth claims (`request.auth.token.*`), the `firebase`
  reserved claim's MFA/SAML members + open index signature, `*.data.*`,
  Storage `resource.metadata.*`, and Storage `request.params.*` are all **open**.
- [x] Re-check current IntelliJ Platform SDK docs (via Context7) before registering
  the inspection: `com.intellij.codeInspection.LocalInspectionTool` /
  `localInspection` EP and the `ModCommand` `PsiUpdateModCommandAction` fix pattern
  (both already used by m3). Platform target: IntelliJ IDEA 2025.2,
  `sinceBuild = 252`, Java 21.
- [x] Hold every m1/m2/m3/m4 non-goal: no authorization evaluation, no
  Firebase/emulator/rules-test-SDK connection, no project IDs, structural-not-JS.
- [x] **No type inference** — the member check is a fixed doc-sourced closed-set
  lookup; derive no type of any variable/member/call/user value.
- [x] Degrade gracefully on a partially-malformed file (no exceptions; unrelated
  blocks unaffected).
- [x] Tag anything the docs don't unambiguously confirm `UNCONFIRMED` with a TODO
  tied to its source (notably `request.query` exhaustiveness).
- [x] Keep diagnostic wording **structural** — "'x' is not a member of `request`" —
  never "insecure" / "authorizes".

## Milestone m5: `FirebaseRulesMemberInspection`

### Prerequisite table fixes (correctness — land FIRST, in `RulesService`)

- [x] Add an explicit **closed-receiver flag model** to
  `references/FirebaseRulesService.kt` — `closedReceivers: Map<String, Set<String>>`
  as a per-dialect enum ctor param, **separate** from `members` (which stays the
  completion source). This is the *only* authority the inspection flags against.
- [x] Add a human-readable `label` enum ctor param (`"Cloud Firestore"` /
  `"Cloud Storage"`) for dialect-aware messages, plus a companion
  `OPEN_RECEIVERS: Set<String>` holding the open-receiver keys —
  **documentation + drift-test authority only, never read at runtime** (see the
  single-lookup decision below).
- [x] Populate Firestore closed receivers: `request` → {auth, method, path, query,
  resource, time}; `request.auth` → {uid, token}; `resource` → {data, id,
  __name__}; `request.resource` → {data, id, __name__}; `request.query` → {limit,
  offset, orderBy} (tag `UNCONFIRMED` on exhaustiveness).
- [x] Populate Storage closed receivers: `request` → {auth, params, path, resource,
  time}; `request.auth` → {uid, token}; `resource` → {name, bucket, generation,
  metageneration, size, timeCreated, updated, md5Hash, crc32c, etag,
  contentDisposition, contentEncoding, contentLanguage, contentType, metadata};
  `request.resource` → {name, bucket, size, md5Hash, crc32c, contentDisposition,
  contentEncoding, contentLanguage, contentType, metadata}.
- [x] **Mark `request.auth.token` OPEN** (both dialects) — keep its standard claims
  in `members` for completion, but exclude it from the closed flag model (custom
  claims are unbounded).
- [x] **Never close `request.auth.token.firebase`** — keep it out of the closed flag
  model (MFA/SAML members + open index signature). Optionally add its known members
  to completion hints only.
- [x] **Remove `params` from the Firestore `request` `members`** entry (Firestore has
  no `request.params`); leave the Storage `request.params` entry intact (open).

### The inspection

- [x] Add `diagnostics/FirebaseRulesMemberInspection.kt` (`LocalInspectionTool`)
  using `buildVisitor` + `ProblemsHolder.registerProblem` over the generated
  `FirebaseRulesVisitor.visitMemberExpression` (single node type, independent
  verdicts — no `checkFile` walk). Skip any member inside a `PsiErrorElement`.
- [x] Resolve dialect via `RulesService.forElement`; if null (**neutral file**),
  flag nothing and return.
- [x] Compute `receiverKey` via `FirebaseRulesMemberPath.receiverKey` (shared, no
  duplication).
- [x] **`chainRoot` guard (purity + shadowing):** walk `member.expression` down; if
  every hop is a `member_expression` bottoming out in a `reference_expression`,
  return that root — otherwise (a call / index / paren / literal anywhere in the
  receiver, e.g. `resource().foo`, `resource[0].foo`) return null and **skip**. Then,
  when `receiverKey` is a closed key, resolve the root via
  `FirebaseRulesScopes.resolveVariable`; if it resolves to a user binding
  (`let`/param/path-var shadowing `request`/`resource`), **skip**.
- [x] **Single-lookup flag:** flag `<receiver>.<member>` iff
  `closedReceivers[receiverKey]` exists in the file's dialect and `member` ∉ that set.
  The open-receiver short-circuit is **not** a runtime per-hop walk — it is provably
  redundant with this gate (no closed receiver sits beneath an open one) and is
  enforced statically by the drift-guard test instead. Members in **call position**
  (`request.auth.get(...)`) are **skipped** — the closed sets document *properties*,
  and `request.auth` is a `rules.Map` whose methods can't be enumerated without type
  inference (adversarial-review correction to the earlier "flag callees" call).
- [x] Anchor the problem on `member.identifier` (squiggle under the member name only)
  with `ProblemHighlightType.GENERIC_ERROR_OR_WARNING`, so displayed severity follows
  the profile `level` (a later escalation to `WARNING` is a plugin.xml-only change).
- [x] **Message taxonomy (3 cases), all structural:** (1) a near in-dialect match
  (Levenshtein ≤ 2) → base message `'x' is not a member of `<receiver>`` + rename
  fix(es); (2) else a **cross-dialect** member (valid on the *other* dialect's same
  receiver — Storage `request.method`, Firestore `resource.size`) → a generic
  dialect-aware message computed from the other dialect's model, **no** fix; (3) else
  plain unknown → base message, no fix. The no-fix cases (2, 3) enumerate the
  receiver's valid set; never "insecure"/"authorizes".

### Quick-fix

- [x] Add `diagnostics/fixes/RenameMemberFix.kt`
  (`PsiUpdateModCommandAction<FirebaseRulesMemberExpression>`, `asQuickFix`): `invoke`
  replaces `element.identifier` with `FirebaseRulesElementFactory.identifier(project,
  target)`; family name `Change to 'X'`.
- [x] In the inspection, offer **one rename fix per in-dialect member within
  Levenshtein ≤ 2** (platform `com.intellij.util.text.EditDistance.levenshtein` —
  confirm the signature against the SDK), ordered closest-first (mirrors
  `SetServiceNameFix` surfacing all alternatives).
- [x] Precedence: a near in-dialect match → rename fix(es); otherwise (esp.
  cross-dialect true positives) the dialect-aware message with **no** fix.

### Registration & docs

- [x] Register the `localInspection` in `plugin.xml` (shortName `FirebaseRulesMember`,
  displayName "Firebase Rules unknown member", groupName "Firebase Rules",
  `enabledByDefault="true"`, `level="WEAK WARNING"`), verified against current SDK docs.
- [x] Update `README.md`, `AGENTS.md`, and the `plugin.xml` `<description>` feature
  list (text-only) to describe the unknown-member check and its dialect-awareness.

### Tests

- [x] `FirebaseRulesMemberInspectionTest` — **true positives:** `request.foo`
  (Firestore); `resource.size` in a Firestore file; `request.method` in a Storage
  file; `request.resourse` (typo). **Guaranteed negatives (critical):**
  `resource.data.<field>`, `request.resource.data.<field>`,
  `request.auth.token.<customClaim>`,
  `request.auth.token.firebase.sign_in_second_factor`,
  `request.auth.token.firebase.<x>`, Storage `resource.metadata.<key>`, Storage
  `request.params.<key>`; a valid member on every closed receiver in both dialects;
  a neutral (no-`service`) file; a malformed file (no exception).
- [x] Guard negatives (FP classes surfaced while grilling + in adversarial review): a
  `let` / param / path-var named `request` or `resource` (**shadowing**) flags nothing;
  a call / index / paren receiver collapsing to a closed key (`resource().foo`,
  `resource[0].foo`) flags nothing; a member in **call position** — a method call like
  `request.auth.get(...)` / `request.auth.keys()` — flags nothing.
- [x] Registration test — assert the profile registers `FirebaseRulesMember` at
  `WEAK WARNING`, enabled by default (the behavioral tests force-enable, so they can't
  catch a `plugin.xml` regression).
- [x] Quick-fix test — did-you-mean rename applies and yields valid text; a
  cross-dialect case shows the message with no rename.
- [x] Drift-guard test — closed flag model stays consistent with the documented sets
  and with `members` where they overlap; assert **no** `OPEN_RECEIVERS` key appears in
  any dialect's `closedReceivers` and **no** closed key has an open key as a strict
  prefix (the short-circuit invariant, now enforced here rather than at runtime);
  assert `request.auth.token` and `request.auth.token.firebase` are **open**; assert
  Firestore `request` has no `params`.
- [x] Run `./gradlew test` (green); keep `verifyPlugin` green.

## Release-Quality Acceptance (m5)

- [x] Unknown member on a closed, service-correct receiver is flagged at
  weak-warning with a did-you-mean fix (near match) or dialect-aware message
  (cross-dialect); nothing under any open receiver is ever flagged, in either
  dialect.
- [x] Neutral files flag nothing; malformed files throw nothing; Firestore
  completion no longer offers `request.params`.
- [x] All prior non-goals hold; no type inferred; no authorization judged; wording
  stays structural.
- [x] Tests cover positives, the full guaranteed-negative set, dialect-awareness,
  the quick-fix, and drift.
- [x] Implementation follows current official JetBrains SDK and Firebase docs;
  `UNCONFIRMED` tags carry source TODOs.

## Implementation Decisions (grilled 2026-07-05 — CONFIRMED)

The eight product decisions in `docs/spec.md` "Decisions taken" stand unchanged. A
second `/grill-me` pass (same day, against the actual code) resolved the
implementation-level branches the tasks left open; all confirmed:

- **Standing invariants (unchanged):** flag ONLY against the explicit
  `closedReceivers` model, never every `members` key (the two diverge —
  `request.auth.token` in-completion but flag-open; `request.query` flag-closed but
  not in completion). Service-scoping is mandatory (`RulesService.forElement`; no
  global merge). `request.auth.token.firebase` is hard-forbidden from the closed model
  (MFA/SAML members + open index signature).
- **Short-circuit → single lookup.** The open-receiver short-circuit is provably
  redundant with the closed-key gate (no closed receiver sits beneath an open one), so
  the hot path is a single `closedReceivers[receiverKey]` lookup; the "no open hop"
  invariant is enforced **statically by the drift-guard test** (via `OPEN_RECEIVERS`),
  not re-walked at runtime.
- **Shadowing guard (new).** A `let`/param/path-var can legally be named
  `request`/`resource` (the names validator only blocks keywords, and the resolver
  binds it). When `receiverKey` is a closed key, resolve the chain root via
  `FirebaseRulesScopes.resolveVariable`; a user binding there ⇒ **skip** (kills that
  false-positive class — "conservative to a fault").
- **`chainRoot` guard (new).** One helper returns the root `reference_expression`
  only for a pure `member_expression* → reference_expression` chain; a call / index /
  paren receiver ⇒ null ⇒ skip. Does double duty: chain-purity + shadow-root.
- **Call-position members skipped (adversarial-review correction).** A member in call
  position (`request.auth.get(...)`) is a method call and is skipped. The earlier
  "flag callees" call assumed closed receivers document no methods, but `request.auth`
  is a `rules.Map` with documented methods (get/keys/values/size/diff) — flagging them
  is a false positive. The closed sets authorize only *property* membership; method
  existence would need type inference (out of scope).
- **API shape.** `buildVisitor` + `ProblemsHolder.registerProblem` over the generated
  `FirebaseRulesVisitor` (not `checkFile`; this check has no cross-node pass).
- **Anchor + severity.** Anchor on `member.identifier`;
  `ProblemHighlightType.GENERIC_ERROR_OR_WARNING` so severity tracks the profile
  `level` (escalation = plugin.xml only).
- **Quick-fix.** `PsiUpdateModCommandAction<FirebaseRulesMemberExpression>` replacing
  `.identifier` via `FirebaseRulesElementFactory.identifier`; one fix per in-dialect
  member within Levenshtein ≤ 2 (platform `EditDistance.levenshtein`), closest-first.
- **Message taxonomy.** Three cases (near-match+fix / cross-dialect message / plain);
  generic cross-dialect template from the other dialect's model; no-fix cases
  enumerate the valid set. Needs the new `RulesService.label`.
- **Registration.** shortName `FirebaseRulesMember`, displayName
  "Firebase Rules unknown member", groupName "Firebase Rules",
  `enabledByDefault="true"`, `level="WEAK WARNING"`.

## Deferred / roadmap (not m5 scope)

- **Candidate B** (operator on incompatible literal operands) — deferred; revisit
  with a real type-inference pass, where each flag can cite an inferred type. See
  `docs/spec.md` "Candidate B".
- Escalating the four fully-documented Firestore/Storage interfaces from
  weak-warning to `WARNING` once real-world FP data confirms low risk.

Explicitly **not planned:** emulator / rules-test-SDK integration and any in-IDE
authorization evaluation.
