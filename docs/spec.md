# HotRulez Project Spec — m5 (Toward Semantics)

Status: **committed plan.** Promoted from sketch on 2026-07-05 after a doc-grounded
false-positive-risk assessment (see Evidence). m5 is the third and final milestone
of the Assisted Authoring arc (m3 / m4 / m5).
Last updated: 2026-07-05.
Supersedes: `docs/m4/spec.md` (the m4 "Authoring Polish" plan; **m4 shipped**).
Earlier milestones live under `docs/m3/` (Actionable Diagnostics), `docs/m2/`
(Symbol Intelligence + Cloud Storage), and `docs/m1/` (Passive Language).

## Context

HotRulez is a JetBrains IDE plugin for Firebase Security Rules — both Cloud
Firestore (`service cloud.firestore`) and Cloud Storage (`service
firebase.storage`) `.rules` files. Through the Assisted Authoring arc it has grown
to **read**, **understand**, **act on**, and **explain** a `.rules` file:

- **m1** — passive language (file type, highlighting, parser/PSI,
  formatter, structural diagnostics, editor polish).
- **m2** — symbol intelligence (resolve, go-to-definition, find-usages,
  rename, scope-aware completion).
- **m2** — Cloud Storage as a sibling dialect, modeled as data in
  `RulesService`.
- **m3** — Actionable Diagnostics: 12 `ModCommand` quick-fixes plus
  `FirebaseRulesSymbolInspection` (undefined references, unused
  functions/`let`s/parameters through the shipped resolver).
- **m4** — Authoring Polish: structure view, code folding, quick
  documentation, parameter info, and the `FirebaseRulesDocs` doc-prose table.

Every diagnostic the plugin ships today is **structural** — does this brace close,
does this symbol resolve, is this declaration used. None is **semantic** — none
asks whether a member can exist on a receiver. m5 takes the first careful step
across that line, and *only* the first.

## Thesis

**m5 adds one conservative, doc-grounded semantic check — a service-aware
inspection that flags a member access on a *closed* built-in receiver when the
member is not part of that receiver's fixed, documented set — extending the
`FirebaseRulesSymbolInspection` philosophy (resolve against a fixed model, never
invent) from undefined *references* to unknown *members*, while relaxing no prior
non-goal.** It infers no types, evaluates no authorization, and flags nothing whose
receiver is an open (user-data / custom-claim / metadata) namespace.

This scope was chosen empirically: a simulation of the check over the repo's entire
member-access corpus produced **0 false positives and 3 true positives**, and an
adversarial doc review pinned down exactly which receivers are safe to close (see
Evidence). The one candidate that could not be grounded in the docs — an
operator/literal-type check — is **deferred** (see Candidate B).

## The check — `FirebaseRulesMemberInspection`

A new `LocalInspectionTool` (`dev.lezli.hotrulez.diagnostics.FirebaseRulesMemberInspection`),
registered as a `localInspection` with its own toggle, `enabledByDefault="true"`,
`level="WEAK WARNING"`. It is a distinct concern from the resolver-based
`FirebaseRulesSymbolInspection` (member validity is table-based, not scope-based),
so it is a separate inspection, not an extension of that one.

### What it flags

A `member_expression` `<receiver>.<member>` is flagged **iff all** of:

1. the file's dialect is **known** (`RulesService.forFile` / `forElement` returns a
   service — see Neutral files);
2. `receiverKey(receiver)` (the shared `FirebaseRulesMemberPath.receiverKey` logic)
   is a **closed receiver** in that dialect's flag-authority model;
3. `member` is **not** in that closed receiver's complete member set;
4. **no hop** in the receiver chain is an **open** receiver (short-circuit — see
   below).

Anything else is silent. In particular a member on an unknown/user receiver, a call
result, a slice, or any open namespace is never flagged.

### The closed-receiver model (flag authority)

This is a **new, explicit, per-dialect table** — the *flag authority* — kept
separate from `RulesService.members` (the *completion* source), because the two
genuinely diverge: `request.auth.token` belongs in completion (standard claims are
useful hints) but must be **open** for flagging (custom claims are unbounded); and
`request.query` is a valid closed receiver to flag against but is not a completion
member today. A drift-guard test keeps the flag model doc-consistent, mirroring the
m4 `FirebaseRulesDocsTableTest` pattern.

**Cloud Firestore — closed receivers (safe to flag unknown members):**

| Receiver | Complete member set | Source |
| --- | --- | --- |
| `request` | `auth, method, path, query, resource, time` | `rules.firestore.Request` (exhaustive; **no `params`**) |
| `request.auth` | `uid, token` | `rules.firestore.Request` / rules-and-auth |
| `resource` | `data, id, __name__` | `rules.firestore.Resource` |
| `request.resource` | `data, id, __name__` | same `Resource` type |
| `request.query` | `limit, offset, orderBy` | `rules.firestore.Request` (docs use "e.g." phrasing → treat as closed but this receiver only, tag `UNCONFIRMED` on exhaustiveness) |

**Cloud Storage — closed receivers:**

| Receiver | Complete member set | Source |
| --- | --- | --- |
| `request` | `auth, params, path, resource, time` | storage rules-conditions (**no `method`, no `query`**) |
| `request.auth` | `uid, token` | rules-and-auth |
| `resource` | `name, bucket, generation, metageneration, size, timeCreated, updated, md5Hash, crc32c, etag, contentDisposition, contentEncoding, contentLanguage, contentType, metadata` | storage rules-conditions / reference |
| `request.resource` | `name, bucket, size, md5Hash, crc32c, contentDisposition, contentEncoding, contentLanguage, contentType, metadata` | `resource` minus `{generation, metageneration, etag, timeCreated, updated}` (deliberately the *larger* of two conflicting doc sources, so the check never false-flags `md5Hash`/`crc32c`/`content*`) |

### Open receivers (NEVER flag — the short-circuit)

The moment any hop in the receiver chain matches an open receiver, the inspection
returns without flagging. These are app / JWT / query schema the engine cannot know:

- `request.auth.token` **(both dialects)** — developer-defined custom claims via
  `setCustomUserClaims` are unbounded (`request.auth.token.admin`, `.reader`).
- `request.auth.token.firebase` **(both dialects)** — **not** exhaustive: the
  Firebase `DecodedIdToken.firebase` reserved claim carries `sign_in_second_factor`,
  `second_factor_identifier` (MFA), SAML's `sign_in_attributes`, and a literal open
  index signature `[key: string]: any`. Idiomatic MFA/SAML rules
  (`request.auth.token.firebase.sign_in_second_factor == 'phone'`) must not be
  flagged. *(This was the decisive catch from the adversarial pass.)*
- `request.auth.token.firebase.identities` — keyed by sign-in provider (unbounded).
- Firestore `resource.data`, `request.resource.data` — user document fields.
- Cloud Storage `resource.metadata`, `request.resource.metadata` — custom object
  metadata.
- Cloud Storage `request.params` — request/API-dependent query-parameter keys.

The closed member sets stop exactly one level above these; the shallow `members`
table (m2) already stops there, and the new flag model keeps the same discipline.

### Service-scoping is load-bearing

Closed sets are **per service**. This is not an optimization — it is where the
check's value comes from: `resource.size` in a Firestore file and `request.method`
in a Storage file are **true positives** *because* the sets are dialect-scoped
(`size` is Storage-only; `method` is Firestore-only). The inspection MUST resolve
`RulesService.forFile` and use that dialect's set — never a global merge.

### Severity, quick-fix, neutral files

- **Severity: `WEAK WARNING`, on by default.** The docs document which members
  *exist* but never state that accessing an undefined member is a hard deploy-time
  error (it surfaces as a runtime evaluation error that denies the request), and two
  receivers carry mild residual uncertainty (`request.auth` is typed as a generic
  `Map`; `request.query` membership uses "e.g." phrasing). Weak-warning is the
  reputation-safe altitude for the plugin's first semantic check. Leave headroom to
  escalate the four fully-documented interfaces (`request`, `resource`,
  `request.resource`, `request.auth`) to `WARNING` in a later release; keep
  `request.query` at weak-warning regardless.
- **Quick-fix (continues the m3 Actionable Diagnostics theme):** on a flagged
  member, compute edit distance to the receiver's known members; when a close match
  exists (Levenshtein ≤ 2) offer a `ModCommand` rename fix — *"Did you mean 'X'?"*
  (`request.resourse` → `resource`, `resource.dta` → `data`,
  `request.query.limitt` → `limit`). For cross-dialect true positives with no close
  in-dialect match, prefer a **dialect-aware message** over a rename — e.g. Storage
  `request.method` → *"'method' is not a member of request in Cloud Storage rules;
  the operation is expressed by the match (get/list/create/update/delete)"*;
  Firestore `resource.size` → *"'size' is a Cloud Storage member; Firestore
  `resource` exposes {data, id, __name__}"*. No fix when there is no near match —
  just the warning.
- **Neutral files: suppress.** When `RulesService.forFile` returns null (no
  recognized `service` — an incomplete/fragment/parse-broken file), the flag does
  **not** run. Unioning both dialects' member sets would accept a Storage-only
  member in a would-be-Firestore file, destroying the cross-dialect true positives.
  Completion continues to union (harmless for suggestions), unchanged.

## Prerequisite table fixes (correctness — must land before the check ships)

These are not preferences; the check is unsafe without them. All were identified by
the grounding assessment.

1. **Introduce the explicit closed-receiver flag authority** (above), separate from
   `members`. The inspection flags only against this allowlist — never against
   "every key present in `members`."
2. **Mark `request.auth.token` open** in both dialects. It is a `members` key today
   with six standard claims; keep those as completion hints, but the flag model must
   never treat it as closed — custom claims are unbounded. *(This is the single
   largest false-positive risk in the current table, and the 0-FP simulation already
   assumed this fix.)*
3. **Never close `request.auth.token.firebase`.** It is absent from the table today
   (already open); keep it that way. Optionally add `{identities, sign_in_provider,
   sign_in_second_factor, second_factor_identifier, sign_in_attributes, tenant}` as
   completion hints only.
4. **Remove `params` from the Firestore `request` set** in `RulesService.members`.
   Firestore has no `request.params` (path wildcards bind as named variables:
   `match /users/{userId}` → `userId`). Keeping it both mis-suggests in Firestore
   completion and would make the flag silently accept `request.params.*`. Leave the
   **Storage** `request.params` entry intact (valid there, and itself open).

## Evidence (why this scope is safe to ship)

A background grounding workflow (doc research → table cross-check → corpus
simulation → adversarial verification → synthesis) established:

- **0 false positives / 3 true positives** simulating the service-aware check over
  the 203 concrete member-access sites in the repo. The three flags: `request.foo`
  (the intentional bad-member fixture), `resource.size` in a Firestore file, and
  `request.method` in a Storage file — all genuine. The "short-circuit on any open
  receiver" rule neutralized every trap (`resource.data.*`, custom claims, slices,
  `.diff(...).addedKeys()...` chains).
- **Adversarial refutation** found the `request.auth.token.firebase` MFA/SAML
  members and its open index signature, which is why that receiver is hard-forbidden
  from ever being closed. No valid-but-unlisted member survived on `request`,
  `resource`, `request.resource`, `request.auth`, or `request.query`.
- **Caveat honestly recorded:** the corpus is synthetic (there are no real
  application `.rules` files in the repo — every `.rules` file is a formatter
  fixture). The 0-FP result is a floor, not proof; weak-warning severity and the
  conservative closed set are the hedge against real-world receivers the corpus
  under-exercises.

## Candidate B — deferred (operator / literal-type check)

A check for an operator applied to plainly incompatible **literal** operands
(`"x" / 2`, `true && 1`) was considered and **deferred**. It is technically
feasible literals-only with zero type inference, but:

- its grounding is **inference-by-omission** — the docs enumerate which operators
  each type *supports* but never state that a bad combination is an *error*, which
  conflicts with the plugin's standing "doc-grounded — never assert what the docs
  don't confirm" anchor (every check today can cite a doc sentence; this one cannot);
- its **real-world hit rate is near-zero** — nobody writes `true + 1`; every genuine
  operator bug is on a variable/member/call whose type must be **inferred** (out of
  scope).

Revisit B when a real type-inference pass exists (a later Toward Semantics
increment), where the same operator rules become genuinely useful *and* each flag
can cite an inferred type.

## Non-Goals

m5 inherits **every** m1/m2/m3/m4 non-goal unchanged (no authorization
evaluation; no Firebase/emulator/rules-test-SDK connection; no project IDs; not
modeled as JavaScript/JSON; no unrelated UI deps). Additionally, m5-specific:

- **No type inference.** The member check is a fixed, doc-sourced closed-set lookup;
  it never derives the type of a variable, member, call result, or user value.
- **No flagging of open namespaces.** `request.auth.token.*` (incl. `.firebase.*`
  and `.identities.*`), `*.data.*`, Storage `resource.metadata.*` /
  `request.resource.metadata.*`, and Storage `request.params.*` are never flagged,
  by construction (the short-circuit).
- **No global (dialect-blind) member set.** Neutral files suppress the flag; there
  is no union fallback for flagging.
- **No security or authorization judgment.** The wording stays structural
  ("'x' is not a member of `request`"), never "insecure" / "authorizes".

## Anchors

- **Conservative to a fault** — a false positive on valid, idiomatic rules is worse
  than a missed true positive; when the docs don't make a member unambiguously
  absent, stay silent.
- **Doc-grounded, no inference** — every flag traces to a fixed, doc-sourced closed
  set; anything the docs leave uncertain (`request.query` exhaustiveness) is tagged
  `UNCONFIRMED` and kept at weak-warning.
- **Reuse, don't rebuild** — `FirebaseRulesMemberPath.receiverKey`,
  `RulesService.forFile`/`forElement`, the `member_expression` PSI, and the m3
  `ModCommand` (`PsiUpdateModCommandAction`) quick-fix pattern already ship.
- **Dialect-correct or silent** — service-scoped closed sets; neutral files suppress.

## Implementation components

- `references/FirebaseRulesService.kt` — add the explicit per-dialect
  **closed-receiver flag model** (e.g. `closedReceivers: Map<String, Set<String>>`),
  distinct from `members`; apply the four prerequisite table fixes.
- `diagnostics/FirebaseRulesMemberInspection.kt` — the new `LocalInspectionTool`:
  walk `member_expression`s, resolve dialect via `RulesService.forElement`, apply the
  flag rule + open-receiver short-circuit, register `WEAK WARNING` problems with the
  quick-fix.
- `diagnostics/fixes/` — a `RenameMemberFix` (`PsiUpdateModCommandAction`) for the
  did-you-mean case (reuse `asQuickFix`); dialect-aware messages are inspection
  message text, not fixes.
- `plugin.xml` — register the `localInspection` (shortName `FirebaseRulesMember`,
  groupName "Firebase Rules", `enabledByDefault="true"`, `level="WEAK WARNING"`).
- Reuse — do not duplicate — `receiverKey`, dialect detection, and the fix plumbing.
  A drift-guard test keeps the closed model doc-consistent.

## Tests

- **`FirebaseRulesMemberInspectionTest`** — true positives: `request.foo`
  (Firestore), `resource.size` in a Firestore file, `request.method` in a Storage
  file, `request.resourse` (typo → did-you-mean `resource`). **Guaranteed
  negatives** (the critical set): `resource.data.<field>`,
  `request.resource.data.<field>`, `request.auth.token.<customClaim>`,
  `request.auth.token.firebase.sign_in_second_factor`,
  `request.auth.token.firebase.<x>`, Storage `resource.metadata.<key>`,
  Storage `request.params.<key>`; a valid member on every closed receiver in both
  dialects; a neutral (no-`service`) file flags nothing; a malformed file throws
  nothing.
- **Quick-fix test** — did-you-mean rename applies and produces valid text;
  cross-dialect message present with no rename when no near match.
- **Drift-guard test** — the closed-receiver flag model stays consistent with the
  documented sets and with `members` where they overlap; `request.auth.token` and
  `request.auth.token.firebase` are asserted **open**.
- `./gradlew test` green; `verifyPlugin` green (a new stable `localInspection` EP).

## Acceptance

- A member access on a closed, service-correct built-in receiver whose member is
  unknown is flagged at weak-warning, with a did-you-mean fix on near matches and a
  dialect-aware message on cross-dialect mistakes.
- Nothing under an open receiver is ever flagged (user data, custom claims,
  `.firebase.*`, metadata, Storage query params), in either dialect.
- Neutral files flag nothing; malformed files throw nothing; completion is unchanged
  except that Firestore no longer suggests `request.params`.
- All prior non-goals hold; no type is inferred; no authorization is judged.
- Tests cover positives, the full guaranteed-negative set, dialect-awareness, the
  quick-fix, and drift; implementation follows current JetBrains SDK and Firebase
  docs.
- `README.md`, `AGENTS.md`, and the `plugin.xml` `<description>` feature list are
  updated (text-only).

## Decisions taken (grilled 2026-07-05 — please confirm)

Resolved during the `/grill-me` session; the first three are your explicit choices,
the rest are recommended defaults grounded in the evidence.

1. **Theme = the roadmap** — m5 is the Toward Semantics milestone (your choice).
2. **Scope = Candidate A only; defer B** — you initially chose A+B, then, once the
   inference-by-omission conflict with the doc-grounded anchor was surfaced, chose to
   defer B and keep m5 doc-grounded-pure.
3. **Severity = `WEAK WARNING`, on by default** (your choice).
4. **Quick-fix = did-you-mean + dialect-aware messages** (your choice).
5. **New `FirebaseRulesMemberInspection`**, not an extension of the symbol
   inspection (table-based vs resolver-based concern; own toggle).
6. **Explicit `closedReceivers` flag model** separate from `members`, with a
   drift-guard test.
7. **Neutral files suppress** the flag (no union fallback).
8. **Include `request.query`** as a closed Firestore sub-receiver at weak-warning,
   tagged `UNCONFIRMED` on exhaustiveness (cheaply reversible; omit if you'd rather
   ship the four fully-documented interfaces only).
9. **Single release** — m5 ships as one milestone.

## Future (explicitly not planned)

Emulator / rules-test-SDK integration and any in-IDE authorization *evaluation*
remain out of scope. A type-aware semantic pass (which would revive Candidate B and
enable member checks on inferred types) would be a new arc beyond Assisted
Authoring, not part of m5.
