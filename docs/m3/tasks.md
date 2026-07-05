# hotrulez Tasks — m3 (Actionable Diagnostics)

Status: **archived — m3 complete.** This is the m3 (Actionable Diagnostics)
task breakdown; every item below shipped in PR #29 (12 ModCommand quick-fixes in
`diagnostics/fixes/` plus `FirebaseRulesSymbolInspection`). Kept as the historical
record for the m3 phase. The m4 (Authoring Polish) breakdown lives in
`docs/m4/tasks.md`; the m5 (Toward Semantics) plan is the active `docs/tasks.md`.
Last updated: 2026-07-02 (archived 2026-07-03).
Source: `docs/m3/spec.md` (archived).
Predecessor: `docs/m2/tasks.md` (archived; covers m2 symbol intelligence and
Cloud Storage).

This list breaks out the **m3 — Actionable Diagnostics** milestone in full.
The later milestones (m4 Authoring Polish, m5 Toward Semantics) are sketched
in the roadmap section and in `docs/spec.md`; they are not m3 scope and are not
broken into checkable items yet — one milestone per release. Together, m3, m4
and m5 make up the Assisted Authoring arc.

Coverage status: everything below shipped in PR #29. The detection
layer for Half A already existed (18 diagnostics shipped through m2, but with
`LocalQuickFix.EMPTY_ARRAY` / no annotator fixes), and the resolver Half B
depends on shipped in m2. m3 added the *action* layer on top.

## Ground Rules

- [x] Re-check current IntelliJ Platform SDK docs (via Context7) before
  registering or changing extension points or quick-fix/intention APIs:
  attaching a `LocalQuickFix` to an inspection `ProblemDescriptor`; attaching a
  fix to an annotation (`AnnotationBuilder.withFix` + the current
  `LocalQuickFix`→`IntentionAction` adapter); `ProblemHighlightType`
  (`LIKE_UNKNOWN_SYMBOL`, `LIKE_UNUSED_SYMBOL`); `localInspection` registration.
- [x] Re-confirm the load-bearing Firebase semantics against the live docs
  before coding the new checks: scope-based (not order-based) function
  resolution with forward references, `let` post-declaration visibility,
  path-variable shadowing, and the built-in/helper vocabulary. (Last confirmed
  2026-06-28 for m2 — see spec Documentation Sources.)
- [x] Hold every m1/m2 non-goal: no authorization evaluation, no
  Firebase/emulator/rules-test-SDK connection, no project IDs, structural not
  JavaScript.
- [x] No type inference: the undefined-reference check is name resolution only.
  Members after `.` (`request.foo`) are never flagged; custom claims never
  invented.
- [x] Every quick-fix is safe: one unambiguous correction, or a clearly-marked
  placeholder with the caret parked on it. Never guess a condition, a return
  value, or a type.
- [x] Add focused tests with each fix and each new check.
- [x] Tag any construct or member not confirmed by official docs `UNCONFIRMED`
  with a TODO tied to the source.
- [x] Update `README.md` and `AGENTS.md` once the features ship.

## Milestone m3: Actionable Diagnostics

### Fix infrastructure

- [x] Add package `dev.lezli.hotrulez.diagnostics.fixes`.
- [x] Implement each repair once as a `LocalQuickFix` that mutates the PSI
  (insert / replace / delete); placeholder fixes position the caret on the
  placeholder.
- [x] Extend `FirebaseRulesAnnotator.error(...)` to accept optional fixes and
  attach them via `AnnotationBuilder.withFix` (through the current SDK adapter).
- [x] Replace `LocalQuickFix.EMPTY_ARRAY` in `FirebaseRulesStructureInspection`
  (and add fixes to `FirebaseRulesUsageInspection`) with the real fixes.

### Half A — quick-fixes for existing diagnostics (structure warnings)

- [x] Missing `rules_version` → insert `rules_version = '2';` at the top.
- [x] Non-v2 `rules_version` → change the version literal to `'2'`.
- [x] `rules_version` after `service` → move the line above the `service` block.
- [x] Unknown service name → two fixes: change to `cloud.firestore`; change to
  `firebase.storage`.
- [x] `service` missing its block → add a `{ … }` block scaffolding the
  detected service's root match; caret inside.
- [x] Missing root match → insert `match /databases/{database}/documents { }`
  (Firestore) or `match /b/{bucket}/o { }` (Storage), keyed on
  `RulesService.rootMatchHint`.

### Half A — quick-fixes for existing diagnostics (annotator errors)

- [x] Unknown operation → replace with the closest known operation, offered
  only within a small edit distance; no fix when nothing is close.
- [x] Duplicate parameter name → remove the duplicate parameter.
- [x] Function missing its `return` → add `return false;` placeholder, caret on
  the placeholder.
- [x] Misplaced recursive wildcard (v1) → two fixes: move `{name=**}` to the
  last segment; upgrade to `rules_version = '2';`.

### Half A — quick-fixes for existing diagnostics (usage warnings)

- [x] Recursive wildcard without v2 → add / upgrade to `rules_version = '2';`
  (share the version-upgrade fix).
- [x] Condition-less `allow` → append `: if <condition>;` placeholder, caret on
  the placeholder.

### Half A — no automatic fix (report only; verify each still reports cleanly)

- [x] Confirm these keep reporting **without** a fix (ambiguous repair): empty
  operation list; bare `return;`; more than one recursive wildcard; multiple
  `service` blocks; helper-call arity.

### Half B — new symbol inspection (`FirebaseRulesSymbolInspection`)

- [x] Add `FirebaseRulesSymbolInspection` (`LocalInspectionTool`, group
  "Firebase Rules", `WARNING`, enabled by default) and register it as a
  `localInspection` in `plugin.xml`.
- [x] Undefined reference: flag a `reference_expression` whose
  `FirebaseRulesReference.multiResolve` is empty and whose name is not
  `FirebaseRulesBuiltins.isBuiltinName`, as `Cannot resolve symbol 'x'`
  (`LIKE_UNKNOWN_SYMBOL`). Members are structurally excluded (they are
  `member_expression`, not `reference_expression`).
- [x] Optional fix on an undefined callee: *Create function 'x'* scaffolding
  `function x(<args>) { return false; }` in the nearest enclosing block.
- [x] Unused function: `function` with no resolving reference (via
  `ReferencesSearch` over the file) → `Function 'x' is never used`
  (`LIKE_UNUSED_SYMBOL`); fix *Remove function 'x'* (safe delete).
- [x] Unused `let` binding: never referenced later in its body →
  `Variable 'x' is never used`; fix *Remove 'let' binding*.
- [x] Unused parameter: never referenced in its body →
  `Parameter 'x' is never used`, grayed. **Report only** — no removal fix in m3
  (deferred; removal must update call-site arity).
- [x] Do **not** flag path / wildcard variables, resolved names, or
  built-ins/helpers.

### Scoping correctness (must hold for the new checks)

- [x] Compute "undefined" and "unused" through `FirebaseRulesScopes` — never a
  textual/top-to-bottom heuristic.
- [x] A forward reference and an enclosing-scope function call resolve, so they
  are **not** reported undefined.
- [x] `let` post-declaration visibility and path-variable / function shadowing
  are respected by both checks.

### Tests

- [x] New `FirebaseRulesQuickFixTest`: for each Half-A fix, apply to a
  before-fixture and assert the after-text (existing `testData` style).
- [x] Extend `FirebaseRulesInspectionTest` for `FirebaseRulesSymbolInspection`:
  undefined reference flagged; forward reference and enclosing-scope call not
  flagged; built-ins/helpers/members not flagged; unused function/`let`/
  parameter flagged; path variables not flagged.
- [x] Recovery: fixes and the symbol inspection degrade gracefully in a
  partially malformed file (no exceptions; unrelated blocks unaffected).
- [x] Run `./gradlew test` after the milestone (green; artifacts are
  git-ignored, so re-run before release).

### Registration & docs

- [x] Register `FirebaseRulesSymbolInspection` in `plugin.xml`; verify EP tag
  names and quick-fix/intention APIs against current SDK docs first.
- [x] Update `README.md` (Diagnostics + a new "Quick-fixes" note) and
  `AGENTS.md` (diagnostics section) to describe fixes and the symbol inspection.

## Release-Quality Acceptance (m3)

- [x] Every diagnostic in the Half-A tables offers its fix; the "no automatic
  fix" set reports without one, by design.
- [x] The symbol inspection flags undefined references and unused
  functions/`let`s/parameters with correct resolver-based scoping, and never
  flags members, built-ins, helpers, or path variables.
- [x] All m1/m2 non-goals still hold; nothing connects to Firebase, evaluates
  authorization, or infers a type.
- [x] Tests cover fixes, the symbol inspection (positives + scoping negatives),
  and recovery.
- [x] Implementation choices follow current official JetBrains SDK and Firebase
  docs.

## Future Milestones (roadmap — not m3 scope)

Sequenced, one milestone per release. See `docs/spec.md` for detail.

- **m4 — Authoring polish:** structure view (`psiStructureViewFactory`), code
  folding (`foldingBuilder`), quick-docs (`documentationProvider`, the hover
  docs deferred from m2), and parameter info (`codeInsight.parameterInfo`) — all
  read-only PSI views, no new semantics. Task breakdown deferred until m3 ships.
- **m5 — Toward semantics:** conservative, doc-grounded expression analysis
  (obvious member/type mistakes) — never runtime evaluation. Intentionally
  under-specified until m3/m4 reveal the false-positive risk.

Explicitly **not planned:** emulator / rules-test-SDK integration and any
in-IDE authorization evaluation.
