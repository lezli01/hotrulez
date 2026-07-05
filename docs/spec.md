# HotRulez Project Spec — 0.9 (Toward Semantics)

Status: **sketch (intentionally under-specified).** This document carries **0.9
(Toward Semantics)** forward as a *direction*, not a committed implementation. It
is deliberately kept at sketch altitude: 0.9 is the point where the plugin first
reasons about *meaning*, and the concrete check set is only decided once the
false-positive risk is assessed against real `.rules` files. What this document
*does* commit to is the boundary, the anchors, the non-goals, and an explicit list
of open questions to resolve at implementation time.
Last updated: 2026-07-05.
Program: the third and final milestone of the **v3 "Assisted Authoring"** arc
(0.7 / 0.8 / 0.9). A full semantic-analysis or evaluation push would be a later arc
(1.0+), not this milestone — see Future.
Supersedes: `docs/v4/spec.md` (archived — the 0.8 "Authoring Polish" plan; **0.8
shipped**). The v3 Assisted Authoring plan as it stood while 0.7 was active is under
`docs/v3/`; the v2 spec (symbol intelligence 0.5.0, Cloud Storage 0.6.0) under
`docs/v2/`; the v1 milestone under `docs/v1/`.

Note on numbering: `docs/vN/` folders are a monotonic archive counter, not the arc
number (this working spec, when archived, becomes `docs/v5/`, even though 0.9 is the
third milestone of the "v3" arc). And **the milestone number runs one ahead of the
semver tag**: release-please cut **0.7.0** (2026-07-04) bundling milestone 0.7
(Actionable Diagnostics) *and* milestone 0.8 (Authoring Polish), so milestone 0.9
will most likely release as **0.8.0**.

## Context

HotRulez is a JetBrains IDE plugin for Firebase Security Rules — both Cloud
Firestore (`service cloud.firestore`) and Cloud Storage (`service
firebase.storage`) `.rules` files. Through the Assisted Authoring arc it has grown
from a passive language into an active assistant that **reads**, **understands**,
**acts on**, and now **explains** a `.rules` file:

- **v1 (→ 0.4.0)** — passive language: file recognition, syntax highlighting, a
  Grammar-Kit/JFlex parser and typed PSI, a PSI-aware formatter, structural
  diagnostics (an always-on annotator plus two configurable inspections), and
  editor polish (icon, color settings page, brace matcher, quote handler,
  commenter).
- **v2 (0.5.0)** — symbol intelligence: a PSI reference/resolve layer honoring
  Firebase Rules scoping and path-variable shadowing, plus go-to-definition,
  find-usages, rename, and scope-aware completion.
- **v2 (0.6.0)** — Cloud Storage as a sibling dialect, detected from the `service`
  declaration and modeled as data in `RulesService`.
- **v3 (0.7)** — **Actionable Diagnostics**: 12 quick-fixes (`ModCommand`
  `PsiUpdateModCommandAction`s serving both inspections and the annotator) for the
  structural diagnostics, plus `FirebaseRulesSymbolInspection`, which flags
  undefined references and unused functions / `let`s / parameters through the
  shipped resolver.
- **v3 (0.8)** — **Authoring Polish** *(shipped)*: four read-only authoring
  surfaces — structure view, code folding, quick documentation, and parameter
  info — each a projection of PSI/data the plugin already owned, plus one new
  artifact, the `FirebaseRulesDocs` doc-*prose* table (a per-entity `(title,
  summaryHtml, docUrl)` map keyed off the same paths `RulesService` uses).

What the plugin still does not do is reason about the *meaning* of an expression.
Its diagnostics to date are **structural** (does this brace close, does this
symbol resolve, is this declaration used) — never **semantic** (can this member
exist on this receiver, can this operator apply to these operands). 0.9 takes the
first careful step across that line, and only the first.

## Thesis (direction, not commitment)

**0.9 begins doc-grounded expression analysis — flagging *only* mistakes the
static Firebase reference makes unambiguous — while never asserting authorization,
never inventing types for user data, and never evaluating a rule.** It stays firmly
short of runtime semantics. The 0.8 member/doc tables (now carrying prose) are the
obvious input: they already enumerate the *known* vocabulary per dialect, so the
conservative question "is this member known on this built-in?" is answerable
without any new inference.

The reason this document is a sketch and not a plan: the value of such checks is
entirely determined by their **false-positive rate**, and that rate can only be
judged against real rules files. A single wrong squiggle on valid, idiomatic rules
would do more damage to trust than the check's warnings are worth. So 0.9 commits
to the *shape* of the analysis and the *boundary* it must respect, and defers the
exact check set, the severities, and the fixes until that risk is measured.

## Candidate checks (enumerated, NOT yet committed)

Two checks are on the table. Neither is committed; both are described so the
implementing session starts with a map rather than a blank page.

### Candidate A — unknown member on a *closed* built-in receiver

Flag `receiver.member` when the receiver is a built-in whose member set is
*exhaustively known* and `member` is not in it — e.g. `request.foo`,
`request.auth.bar`. This reuses `RulesService.members` and the shared
`FirebaseRulesMemberPath.receiverKey` logic directly; `FirebaseRulesDocs` supplies
the "did you mean / see docs" prose. This is the *safest* candidate and the natural
0.9 headline — but it lives on a knife's edge (see The Boundary).

### Candidate B — operator on incompatible *literal* types

Flag an operator applied to plainly incompatible **literal** operands the docs make
unambiguous (e.g. a string literal on the arithmetic side of a numeric operator).
This is materially riskier than A: it requires modeling operand *types*, which
brushes directly against the standing **no type inference** non-goal. It would have
to be scoped to *literals only* (never a variable, member, or call result, whose
type the plugin must not infer) to stay on the right side of that line — which
makes its real-world hit rate low. **Leaning: defer B beyond 0.9** unless A proves
so clean that appetite grows; recorded here only so it is not silently dropped.

## The Boundary (the central false-positive risk)

This is the load-bearing insight that shapes every 0.9 decision, verified against
the shipped code:

- Members are **structurally distinct** from references: a `member_expression` is
  not a `reference_expression`. `FirebaseRulesSymbolInspection` (0.7) relies on
  exactly this and **deliberately never flags members** —
  `FirebaseRulesSymbolInspectionTest.testBuiltinsAndMembersAreNotFlagged` asserts
  that `resource.data.customField` passes clean. Candidate A would be the first
  check to *ever* flag a member, flipping a currently-guaranteed non-flag into a
  flag. That is precisely why it must be conservative.
- `RulesService.members` is a **deliberately shallow** `Map<receiverKey,
  List<String>>`. It stops exactly where **user data / open namespaces** begin. So
  being *present as a key* in that table is **not** the same as being **closed**:
  - **Closed** (safe to flag an unknown member) — the fixed built-in objects:
    `request` (`auth`, `method`, `path`, `params`, `resource`, `time`, `query`),
    `request.auth` (`uid`, `token`), `resource` and `request.resource` (`data`,
    `id`, `__name__`), and their Cloud Storage equivalents.
  - **Open** (must **never** flag past this point) — `request.auth.token.*` (custom
    auth claims are arbitrary, even though the table lists the *standard* claims),
    `resource.data.*` / `request.resource.data.*` (user document fields), and Cloud
    Storage `resource.metadata.*` (custom object metadata).
- Therefore a member check cannot key off "is this a table key." It needs an
  explicit **closed-receiver** concept: only receivers marked exhaustive may
  produce a flag; everything below `.data` / `.token` / `.metadata` is open by
  construction and silent. Getting that marker right — and confirming the Storage
  split against the docs — is the core of the work, and an open question below.

## Anchors (carried forward)

- **Conservative to a fault.** A false positive on valid, idiomatic rules is worse
  than a missed true positive. When the docs do not make a mistake *unambiguous*,
  0.9 stays silent.
- **Doc-grounded, no inference.** Every flag traces to the fixed, doc-sourced
  vocabulary (`RulesService` / `FirebaseRulesDocs`). 0.9 derives no type of a user
  value and evaluates no expression.
- **Reuse, don't rebuild.** The member tables, `receiverKey`, `RulesService.forElement`
  dialect gating, the resolver (`FirebaseRulesScopes` / `FirebaseRulesReference`),
  and the 0.7 `ModCommand` quick-fix pattern are all already shipped and are the
  intended inputs.
- **Dialect-correct or silent.** Checks key on `RulesService.forElement`; a neutral
  file (no recognized `service`) leans toward silence rather than guessing.

## Non-Goals

0.9 inherits **every** v1/v2/0.7/0.8 non-goal unchanged. The plugin must not:

- Evaluate whether a request is allowed or denied, or infer authorization or
  security quality.
- Connect to Firebase projects, emulators, the rules-test SDK, credentials, or live
  data, or run rules tests in-IDE.
- Hard-code Firebase project IDs or environment-specific paths.
- Model the language as JavaScript, JSON, or generic configuration.
- Replace official Firebase tooling for deployment or authorization testing.
- Add web-app frameworks or unrelated UI dependencies.

Additionally, 0.9-specific non-goals:

- **Still no type inference.** 0.9 never derives the type of a variable, member,
  call result, or any user value. Candidate B, if ever taken, is restricted to
  *literal* operands only.
- **No flagging of open namespaces.** `request.auth.token.*`, `*.data.*`, and
  Storage `resource.metadata.*` are user/arbitrary space and are never flagged, by
  construction.
- **No new evaluation surface.** 0.9 adds analysis, not connection or execution;
  the no-connection / no-evaluation core principles are untouched.

## Open Questions (resolve at implementation time)

These are the decisions deliberately deferred out of this sketch. Each should be
answered against real rules files and the current SDK/Firebase docs when 0.9 is
promoted to a full plan.

1. **Which candidate(s) ship?** A only (leaning), A + B, or A split across releases.
2. **New inspection vs. extend `FirebaseRulesSymbolInspection`?** Leaning **new**
   (`FirebaseRulesMemberInspection` or similar) — member validity is a table-based
   concern, distinct from the resolver-based symbol inspection, and deserves its own
   independent toggle. To confirm.
3. **Does the member model gain an explicit `closed` marker?** Almost certainly yes
   (a table key is not "closed" — see The Boundary). Where does it live —
   `RulesService`, or a derived set beside it? Does `FirebaseRulesDocs` need to
   participate?
4. **Confirm the Cloud Storage closed/open split against the docs** (which `resource`
   members are fixed; `resource.metadata.*` is open).
5. **Severity and default.** `WEAK_WARNING` vs `WARNING`; on-by-default vs opt-in.
   Leaning weak-warning, on-by-default given the conservative scope — to confirm.
6. **Quick-fix?** A "did you mean `<closest valid member>`" rename fix (0.7's
   `ModCommand` pattern) only when there is an unambiguous near match, else no fix —
   or defer fixes entirely for the first cut.
7. **Neutral-file behavior.** Suppress the member check entirely when no dialect is
   detected (safest), or use the union like completion does? Leaning suppress.
8. **Release split.** One 0.9 (semver 0.8.0), or 0.9.x increments if A and B land
   separately.

## Documentation Sources

Per the standing ground rule, official Firebase docs are authoritative for language
semantics and current IntelliJ Platform SDK docs (via Context7) are authoritative
for extension points. Re-check the relevant pages before implementing; do not
encode a member, signature, or rule the docs do not confirm; tag anything uncertain
`UNCONFIRMED` with a TODO tied to its source.

Firebase semantics load-bearing for 0.9 (re-confirm before coding):

- `request`/`resource` member reference (Firestore + Storage — the closed/open
  determination): `https://firebase.google.com/docs/reference/rules/rules.firestore.Request`
  and `https://firebase.google.com/docs/reference/rules/rules.storage`
- Rules language and conditions:
  `https://firebase.google.com/docs/rules/rules-language` and
  `https://firebase.google.com/docs/firestore/security/rules-conditions`
- Custom auth claims (why `request.auth.token.*` is open):
  `https://firebase.google.com/docs/rules/rules-and-auth`

IntelliJ Platform SDK for 0.9 (platform target: **IntelliJ IDEA 2025.2**,
`sinceBuild = 252`, Java 21): `com.intellij.codeInspection` / `LocalInspectionTool`
for a new inspection (as `FirebaseRulesSymbolInspection` uses today), and the 0.7
`ModCommand` `PsiUpdateModCommandAction` pattern for any fix. Confirm class/method
signatures against current SDK docs before coding. Prefer extension points over
startup code, as every prior milestone did.

## Why this stays a sketch

The 0.7→0.8 transition promoted 0.8 from a forward sketch to a full, implementable
plan *because* 0.8 was pure read-only projection of data the plugin already owned —
low risk, fully knowable up front. 0.9 is the opposite: its correctness is an
empirical property (the false-positive rate on real files), not a structural one.
Promoting it to a full plan before that rate is measured would be committing to
detail the evidence does not yet support. This sketch fixes the boundary and the
principles so the eventual plan starts sound, and defers everything whose right
answer depends on data we do not have yet.

## Future (explicitly not planned)

Emulator / rules-test-SDK integration and any in-IDE authorization *evaluation*
remain out of scope — they would require revisiting the no-connection,
no-evaluation core principles that define the product. A broader semantic-analysis
push (type-aware checks over user data, cross-rule reasoning) would be a new arc
beyond the Assisted Authoring program, not part of 0.9.
