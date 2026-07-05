# HotRulez Tasks — 0.9 (Toward Semantics)

Status: **sketch — no implementation breakdown yet.** 0.9 is deliberately kept at
sketch altitude (see `docs/spec.md`): its correctness is an empirical property (the
false-positive rate of a semantic check on real rules files), so it is not broken
into checkable feature items until that risk is measured and the check set is
committed. This list is therefore a **promotion checklist** — the spikes and
decisions that turn the 0.9 *sketch* into a full, implementable plan — not a build
list. One milestone per release; the feature checklist appears only once 0.9 is
promoted.
Last updated: 2026-07-05.
Source: `docs/spec.md` (0.9 — Toward Semantics, sketch).
Predecessor: `docs/v4/tasks.md` (archived; the 0.8 Authoring Polish breakdown —
shipped in PR #31 / released in the 0.7.0 line).

## Ground Rules (apply the moment coding starts)

- [ ] Re-check current IntelliJ Platform SDK docs (via Context7) before adding an
  inspection or fix: `com.intellij.codeInspection.LocalInspectionTool` (as
  `FirebaseRulesSymbolInspection` uses) and the `ModCommand`
  `PsiUpdateModCommandAction` fix pattern from 0.7. Platform target: IntelliJ IDEA
  2025.2, `sinceBuild = 252`, Java 21.
- [ ] Re-confirm the load-bearing Firebase docs before encoding any check: the
  `request`/`resource` member reference for **both** dialects (which members are
  fixed vs. open), and that custom auth claims (`request.auth.token.*`) are
  arbitrary.
- [ ] Hold every v1/v2/0.7/0.8 non-goal: no authorization evaluation, no
  Firebase/emulator/rules-test-SDK connection, no project IDs, structural-not-JS.
- [ ] **No type inference** — no type derived for any variable, member, call result,
  or user value; a literal-operand check (Candidate B) stays literals-only.
- [ ] Any check degrades gracefully on a partially-malformed file (no exceptions;
  unrelated blocks unaffected) — the standing bar.
- [ ] Tag any member/rule not confirmed by official docs `UNCONFIRMED` with a TODO
  tied to its source.

## Promotion checklist (sketch → plan)

Resolve these before writing the first check. Each maps to an Open Question in
`docs/spec.md`; the leaning (where one exists) is noted.

- [ ] **Measure the false-positive risk.** Run Candidate A's logic (mentally or as a
  throwaway spike) over a corpus of real, idiomatic `.rules` files — the plugin's own
  test fixtures plus public examples — and record where it would wrongly flag. This is
  the gate; everything else depends on it.
- [ ] **Decide the check set** (spec Q1): A only *(leaning)*, A + B, or A split across
  releases. Only promote what the risk measurement supports.
- [ ] **Fix the closed/open receiver model** (spec Q3/Q4): confirm the exhaustive
  built-in receivers (`request`, `request.auth`, `resource`, `request.resource` +
  Storage equivalents) and the open ones never to flag (`request.auth.token.*`,
  `*.data.*`, Storage `resource.metadata.*`) against the docs; decide where the
  explicit `closed` marker lives (`RulesService` vs. a derived set) — a table key is
  **not** "closed."
- [ ] **Choose the extension shape** (spec Q2): new `FirebaseRulesMemberInspection`
  *(leaning)* vs. extending `FirebaseRulesSymbolInspection`; independent toggle.
- [ ] **Choose severity + default** (spec Q5): `WEAK_WARNING`, on-by-default
  *(leaning)* vs. `WARNING` / opt-in.
- [ ] **Decide neutral-file behavior** (spec Q7): suppress when no dialect is detected
  *(leaning)* vs. union like completion.
- [ ] **Decide on a quick-fix** (spec Q6): "did you mean `<closest valid member>`"
  rename via the 0.7 `ModCommand` pattern *only* on an unambiguous near match, or defer
  fixes for the first cut.
- [ ] **Decide the release split** (spec Q8): one 0.9 (semver **0.8.0**) vs. 0.9.x
  increments.

## When promoted, the plan will need (outline — not yet checkable)

Once the checklist above resolves, promote this file to a full 0.8-style breakdown
covering, at minimum:

- The chosen inspection class, its registration in `plugin.xml`, and its toggle.
- The closed-receiver model change (and any `FirebaseRulesDocs` participation).
- Dialect gating via `RulesService.forElement`; neutral-file behavior as decided.
- Reuse of `FirebaseRulesMemberPath.receiverKey`, the resolver, and the `ModCommand`
  fix pattern — no duplication.
- Tests: true positives on closed receivers, **guaranteed negatives** on every open
  namespace (`*.data.*`, `request.auth.token.*`, Storage `metadata.*`), dialect
  correctness, forward references, and malformed-file recovery — plus a corpus
  regression asserting no false positive on the existing valid fixtures.
- `README.md`, `AGENTS.md`, and the `plugin.xml` `<description>` updated (text-only).
- `./gradlew test` and `verifyPlugin` green.

## Reference — the shipped inputs 0.9 builds on

Verified against the current tree; these are the intended, already-shipped inputs:

- `references/FirebaseRulesService.kt` — `RulesService` (per-dialect `members`,
  `globals`, `bareHelpers`; `forElement` / `membersFor` dialect gating).
- `references/FirebaseRulesMemberPath.kt` — `receiverKey(...)` (shared member-path
  logic, extracted in 0.8).
- `references/FirebaseRulesBuiltins.kt` — `isBuiltinName`, `OPERATIONS`, type/global
  namespaces.
- `documentation/FirebaseRulesDocs.kt` — 0.8 doc-prose table `(title, summaryHtml,
  docUrl)`, keyed off the same member paths (source of "see docs" prose).
- `diagnostics/FirebaseRulesSymbolInspection.kt` — the 0.7 resolver-based inspection
  (the model to mirror or extend; note it *never* flags members today).
- `diagnostics/fixes/` — the 0.7 `ModCommand` `PsiUpdateModCommandAction` quick-fix
  pattern (`CreateFunctionFix`, `RemoveDeclarationFix`, `asQuickFix`).

## Future Milestones (roadmap — not 0.9 scope)

Explicitly **not planned:** emulator / rules-test-SDK integration and any in-IDE
authorization *evaluation*. A broader semantic-analysis push (type-aware checks over
user data, cross-rule reasoning) would be a new arc beyond Assisted Authoring.
