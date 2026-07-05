# HotRulez Tasks — m5 (Toward Semantics)

Status: **committed plan.** Promoted from sketch on 2026-07-05 after doc-grounded
FP-risk grounding. This breaks out milestone m5 in full: one conservative,
service-aware semantic check — `FirebaseRulesMemberInspection` — that flags an
unknown member on a *closed* built-in receiver, plus the prerequisite table fixes it
depends on. Candidate B (operator/literal-type) is deferred (see `docs/spec.md`).
Last updated: 2026-07-05.
Source: `docs/spec.md` (m5 — Toward Semantics).
Predecessor: `docs/m4/tasks.md` (the m4 Authoring Polish breakdown, shipped in
PR #31 and released alongside m3).

## Ground Rules

- [ ] Re-confirm the load-bearing Firebase docs before coding the closed sets: the
  `request`/`resource` member reference for **both** dialects
  (`rules.firestore.Request`, `rules.firestore.Resource`, storage rules-conditions /
  reference), and that custom auth claims (`request.auth.token.*`), the `firebase`
  reserved claim's MFA/SAML members + open index signature, `*.data.*`,
  Storage `resource.metadata.*`, and Storage `request.params.*` are all **open**.
- [ ] Re-check current IntelliJ Platform SDK docs (via Context7) before registering
  the inspection: `com.intellij.codeInspection.LocalInspectionTool` /
  `localInspection` EP and the `ModCommand` `PsiUpdateModCommandAction` fix pattern
  (both already used by m3). Platform target: IntelliJ IDEA 2025.2,
  `sinceBuild = 252`, Java 21.
- [ ] Hold every m1/m2/m3/m4 non-goal: no authorization evaluation, no
  Firebase/emulator/rules-test-SDK connection, no project IDs, structural-not-JS.
- [ ] **No type inference** — the member check is a fixed doc-sourced closed-set
  lookup; derive no type of any variable/member/call/user value.
- [ ] Degrade gracefully on a partially-malformed file (no exceptions; unrelated
  blocks unaffected).
- [ ] Tag anything the docs don't unambiguously confirm `UNCONFIRMED` with a TODO
  tied to its source (notably `request.query` exhaustiveness).
- [ ] Keep diagnostic wording **structural** — "'x' is not a member of `request`" —
  never "insecure" / "authorizes".

## Milestone m5: `FirebaseRulesMemberInspection`

### Prerequisite table fixes (correctness — land FIRST, in `RulesService`)

- [ ] Add an explicit **closed-receiver flag model** to
  `references/FirebaseRulesService.kt` (e.g. `closedReceivers: Map<String,
  Set<String>>` per dialect), **separate** from `members` (which stays the
  completion source). This is the *only* authority the inspection flags against.
- [ ] Populate Firestore closed receivers: `request` → {auth, method, path, query,
  resource, time}; `request.auth` → {uid, token}; `resource` → {data, id,
  __name__}; `request.resource` → {data, id, __name__}; `request.query` → {limit,
  offset, orderBy} (tag `UNCONFIRMED` on exhaustiveness).
- [ ] Populate Storage closed receivers: `request` → {auth, params, path, resource,
  time}; `request.auth` → {uid, token}; `resource` → {name, bucket, generation,
  metageneration, size, timeCreated, updated, md5Hash, crc32c, etag,
  contentDisposition, contentEncoding, contentLanguage, contentType, metadata};
  `request.resource` → {name, bucket, size, md5Hash, crc32c, contentDisposition,
  contentEncoding, contentLanguage, contentType, metadata}.
- [ ] **Mark `request.auth.token` OPEN** (both dialects) — keep its standard claims
  in `members` for completion, but exclude it from the closed flag model (custom
  claims are unbounded).
- [ ] **Never close `request.auth.token.firebase`** — keep it out of the closed flag
  model (MFA/SAML members + open index signature). Optionally add its known members
  to completion hints only.
- [ ] **Remove `params` from the Firestore `request` `members`** entry (Firestore has
  no `request.params`); leave the Storage `request.params` entry intact (open).

### The inspection

- [ ] Add `diagnostics/FirebaseRulesMemberInspection.kt` (`LocalInspectionTool`):
  visit `FirebaseRulesMemberExpression`s; skip anything inside a `PsiErrorElement`
  (don't stack on parser diagnostics).
- [ ] Resolve dialect via `RulesService.forElement`; if null (**neutral file**),
  flag nothing and return.
- [ ] Compute `receiverKey` via `FirebaseRulesMemberPath.receiverKey` (shared, no
  duplication).
- [ ] **Short-circuit:** if any hop in the receiver chain is an OPEN receiver
  (`request.auth.token`, `request.auth.token.firebase`, `…firebase.identities`,
  Firestore `resource.data` / `request.resource.data`, Storage `resource.metadata` /
  `request.resource.metadata`, Storage `request.params`), return without flagging.
- [ ] Flag `<receiver>.<member>` only when `receiverKey` is a closed receiver in the
  file's dialect and `member` ∉ that receiver's complete set; register a
  `ProblemHighlightType`/`WEAK WARNING` problem.
- [ ] Message stays structural; cross-dialect members get a dialect-aware message
  (Storage `request.method`; Firestore `resource.size`).

### Quick-fix

- [ ] Add `diagnostics/fixes/RenameMemberFix.kt` (`PsiUpdateModCommandAction`,
  `asQuickFix`): when a known member of the receiver is within Levenshtein ≤ 2 of the
  typo, offer *"Did you mean 'X'?"* and rewrite the identifier.
- [ ] Offer the rename only on a close in-dialect match; otherwise (esp. cross-dialect
  true positives) emit the dialect-aware message with **no** fix.

### Registration & docs

- [ ] Register the `localInspection` in `plugin.xml` (shortName `FirebaseRulesMember`,
  displayName "Firebase Rules unknown member", groupName "Firebase Rules",
  `enabledByDefault="true"`, `level="WEAK WARNING"`), verified against current SDK docs.
- [ ] Update `README.md`, `AGENTS.md`, and the `plugin.xml` `<description>` feature
  list (text-only) to describe the unknown-member check and its dialect-awareness.

### Tests

- [ ] `FirebaseRulesMemberInspectionTest` — **true positives:** `request.foo`
  (Firestore); `resource.size` in a Firestore file; `request.method` in a Storage
  file; `request.resourse` (typo). **Guaranteed negatives (critical):**
  `resource.data.<field>`, `request.resource.data.<field>`,
  `request.auth.token.<customClaim>`,
  `request.auth.token.firebase.sign_in_second_factor`,
  `request.auth.token.firebase.<x>`, Storage `resource.metadata.<key>`, Storage
  `request.params.<key>`; a valid member on every closed receiver in both dialects;
  a neutral (no-`service`) file; a malformed file (no exception).
- [ ] Quick-fix test — did-you-mean rename applies and yields valid text; a
  cross-dialect case shows the message with no rename.
- [ ] Drift-guard test — closed flag model stays consistent with the documented sets
  and with `members` where they overlap; assert `request.auth.token` and
  `request.auth.token.firebase` are **open**; assert Firestore `request` has no
  `params`.
- [ ] Run `./gradlew test` (green); keep `verifyPlugin` green.

## Release-Quality Acceptance (m5)

- [ ] Unknown member on a closed, service-correct receiver is flagged at
  weak-warning with a did-you-mean fix (near match) or dialect-aware message
  (cross-dialect); nothing under any open receiver is ever flagged, in either
  dialect.
- [ ] Neutral files flag nothing; malformed files throw nothing; Firestore
  completion no longer offers `request.params`.
- [ ] All prior non-goals hold; no type inferred; no authorization judged; wording
  stays structural.
- [ ] Tests cover positives, the full guaranteed-negative set, dialect-awareness,
  the quick-fix, and drift.
- [ ] Implementation follows current official JetBrains SDK and Firebase docs;
  `UNCONFIRMED` tags carry source TODOs.

## Implementation Decisions (grilled 2026-07-05 — please confirm)

Mirror the eight decisions in `docs/spec.md` "Decisions taken"; nothing here
reopens them. Load-bearing implementation calls:

- The inspection flags ONLY against the explicit `closedReceivers` model, NEVER
  against every `members` key (the two diverge — `request.auth.token` in-completion
  but flag-open; `request.query` flag-closed but not in completion).
- Service-scoping is mandatory: `RulesService.forElement` selects the dialect set;
  a global merge would destroy the cross-dialect true positives.
- The open-receiver short-circuit is the correctness core — it must run on **every
  hop** of the receiver chain, not just the immediate receiver.
- `request.auth.token.firebase` is hard-forbidden from the closed model (adversarial
  finding: MFA/SAML members + open index signature).

## Deferred / roadmap (not m5 scope)

- **Candidate B** (operator on incompatible literal operands) — deferred; revisit
  with a real type-inference pass, where each flag can cite an inferred type. See
  `docs/spec.md` "Candidate B".
- Escalating the four fully-documented Firestore/Storage interfaces from
  weak-warning to `WARNING` once real-world FP data confirms low risk.

Explicitly **not planned:** emulator / rules-test-SDK integration and any in-IDE
authorization evaluation.
