# hotrulez Tasks — v3 (Assisted Authoring)

Status: draft
Last updated: 2026-07-02
Source: `docs/spec.md`
Predecessor: `docs/v2/tasks.md` (archived; covers 0.5 symbol intelligence and
0.6 Cloud Storage).

This list breaks out the **0.7 — Actionable Diagnostics** milestone in full.
The later milestones (0.8 Authoring Polish, 0.9 Toward Semantics) are sketched
in the roadmap section and in `docs/spec.md`; they are not 0.7 scope and are not
broken into checkable items yet — one milestone per release.

Coverage status (as of 2026-07-02): nothing below is started. The detection
layer for Half A already exists (18 diagnostics ship through 0.6.1, but with
`LocalQuickFix.EMPTY_ARRAY` / no annotator fixes), and the resolver Half B
depends on shipped in 0.5. 0.7 adds the *action* layer on top.

## Ground Rules

- [ ] Re-check current IntelliJ Platform SDK docs (via Context7) before
  registering or changing extension points or quick-fix/intention APIs:
  attaching a `LocalQuickFix` to an inspection `ProblemDescriptor`; attaching a
  fix to an annotation (`AnnotationBuilder.withFix` + the current
  `LocalQuickFix`→`IntentionAction` adapter); `ProblemHighlightType`
  (`LIKE_UNKNOWN_SYMBOL`, `LIKE_UNUSED_SYMBOL`); `localInspection` registration.
- [ ] Re-confirm the load-bearing Firebase semantics against the live docs
  before coding the new checks: scope-based (not order-based) function
  resolution with forward references, `let` post-declaration visibility,
  path-variable shadowing, and the built-in/helper vocabulary. (Last confirmed
  2026-06-28 for v2 — see spec Documentation Sources.)
- [ ] Hold every v1/v2 non-goal: no authorization evaluation, no
  Firebase/emulator/rules-test-SDK connection, no project IDs, structural not
  JavaScript.
- [ ] No type inference: the undefined-reference check is name resolution only.
  Members after `.` (`request.foo`) are never flagged; custom claims never
  invented.
- [ ] Every quick-fix is safe: one unambiguous correction, or a clearly-marked
  placeholder with the caret parked on it. Never guess a condition, a return
  value, or a type.
- [ ] Add focused tests with each fix and each new check.
- [ ] Tag any construct or member not confirmed by official docs `UNCONFIRMED`
  with a TODO tied to the source.
- [ ] Update `README.md` and `AGENTS.md` once the features ship.

## Milestone 0.7: Actionable Diagnostics

### Fix infrastructure

- [ ] Add package `dev.lezli.hotrulez.diagnostics.fixes`.
- [ ] Implement each repair once as a `LocalQuickFix` that mutates the PSI
  (insert / replace / delete); placeholder fixes position the caret on the
  placeholder.
- [ ] Extend `FirebaseRulesAnnotator.error(...)` to accept optional fixes and
  attach them via `AnnotationBuilder.withFix` (through the current SDK adapter).
- [ ] Replace `LocalQuickFix.EMPTY_ARRAY` in `FirebaseRulesStructureInspection`
  (and add fixes to `FirebaseRulesUsageInspection`) with the real fixes.

### Half A — quick-fixes for existing diagnostics (structure warnings)

- [ ] Missing `rules_version` → insert `rules_version = '2';` at the top.
- [ ] Non-v2 `rules_version` → change the version literal to `'2'`.
- [ ] `rules_version` after `service` → move the line above the `service` block.
- [ ] Unknown service name → two fixes: change to `cloud.firestore`; change to
  `firebase.storage`.
- [ ] `service` missing its block → add a `{ … }` block scaffolding the
  detected service's root match; caret inside.
- [ ] Missing root match → insert `match /databases/{database}/documents { }`
  (Firestore) or `match /b/{bucket}/o { }` (Storage), keyed on
  `RulesService.rootMatchHint`.

### Half A — quick-fixes for existing diagnostics (annotator errors)

- [ ] Unknown operation → replace with the closest known operation, offered
  only within a small edit distance; no fix when nothing is close.
- [ ] Duplicate parameter name → remove the duplicate parameter.
- [ ] Function missing its `return` → add `return false;` placeholder, caret on
  the placeholder.
- [ ] Misplaced recursive wildcard (v1) → two fixes: move `{name=**}` to the
  last segment; upgrade to `rules_version = '2';`.

### Half A — quick-fixes for existing diagnostics (usage warnings)

- [ ] Recursive wildcard without v2 → add / upgrade to `rules_version = '2';`
  (share the version-upgrade fix).
- [ ] Condition-less `allow` → append `: if <condition>;` placeholder, caret on
  the placeholder.

### Half A — no automatic fix (report only; verify each still reports cleanly)

- [ ] Confirm these keep reporting **without** a fix (ambiguous repair): empty
  operation list; bare `return;`; more than one recursive wildcard; multiple
  `service` blocks; helper-call arity.

### Half B — new symbol inspection (`FirebaseRulesSymbolInspection`)

- [ ] Add `FirebaseRulesSymbolInspection` (`LocalInspectionTool`, group
  "Firebase Rules", `WARNING`, enabled by default) and register it as a
  `localInspection` in `plugin.xml`.
- [ ] Undefined reference: flag a `reference_expression` whose
  `FirebaseRulesReference.multiResolve` is empty and whose name is not
  `FirebaseRulesBuiltins.isBuiltinName`, as `Cannot resolve symbol 'x'`
  (`LIKE_UNKNOWN_SYMBOL`). Members are structurally excluded (they are
  `member_expression`, not `reference_expression`).
- [ ] Optional fix on an undefined callee: *Create function 'x'* scaffolding
  `function x(<args>) { return false; }` in the nearest enclosing block.
- [ ] Unused function: `function` with no resolving reference (via
  `ReferencesSearch` over the file) → `Function 'x' is never used`
  (`LIKE_UNUSED_SYMBOL`); fix *Remove function 'x'* (safe delete).
- [ ] Unused `let` binding: never referenced later in its body →
  `Variable 'x' is never used`; fix *Remove 'let' binding*.
- [ ] Unused parameter: never referenced in its body →
  `Parameter 'x' is never used`, grayed. **Report only** — no removal fix in 0.7
  (deferred; removal must update call-site arity).
- [ ] Do **not** flag path / wildcard variables, resolved names, or
  built-ins/helpers.

### Scoping correctness (must hold for the new checks)

- [ ] Compute "undefined" and "unused" through `FirebaseRulesScopes` — never a
  textual/top-to-bottom heuristic.
- [ ] A forward reference and an enclosing-scope function call resolve, so they
  are **not** reported undefined.
- [ ] `let` post-declaration visibility and path-variable / function shadowing
  are respected by both checks.

### Tests

- [ ] New `FirebaseRulesQuickFixTest`: for each Half-A fix, apply to a
  before-fixture and assert the after-text (existing `testData` style).
- [ ] Extend `FirebaseRulesInspectionTest` for `FirebaseRulesSymbolInspection`:
  undefined reference flagged; forward reference and enclosing-scope call not
  flagged; built-ins/helpers/members not flagged; unused function/`let`/
  parameter flagged; path variables not flagged.
- [ ] Recovery: fixes and the symbol inspection degrade gracefully in a
  partially malformed file (no exceptions; unrelated blocks unaffected).
- [ ] Run `./gradlew test` after the milestone (green; artifacts are
  git-ignored, so re-run before release).

### Registration & docs

- [ ] Register `FirebaseRulesSymbolInspection` in `plugin.xml`; verify EP tag
  names and quick-fix/intention APIs against current SDK docs first.
- [ ] Update `README.md` (Diagnostics + a new "Quick-fixes" note) and
  `AGENTS.md` (diagnostics section) to describe fixes and the symbol inspection.

## Release-Quality Acceptance (0.7)

- [ ] Every diagnostic in the Half-A tables offers its fix; the "no automatic
  fix" set reports without one, by design.
- [ ] The symbol inspection flags undefined references and unused
  functions/`let`s/parameters with correct resolver-based scoping, and never
  flags members, built-ins, helpers, or path variables.
- [ ] All v1/v2 non-goals still hold; nothing connects to Firebase, evaluates
  authorization, or infers a type.
- [ ] Tests cover fixes, the symbol inspection (positives + scoping negatives),
  and recovery.
- [ ] Implementation choices follow current official JetBrains SDK and Firebase
  docs.

## Future Milestones (roadmap — not 0.7 scope)

Sequenced, one milestone per release. See `docs/spec.md` for detail.

- **0.8 — Authoring polish:** structure view (`psiStructureViewFactory`), code
  folding (`foldingBuilder`), quick-docs (`documentationProvider`, the hover
  docs deferred from v2), and parameter info (`codeInsight.parameterInfo`) — all
  read-only PSI views, no new semantics. Task breakdown deferred until 0.7 ships.
- **0.9 — Toward semantics:** conservative, doc-grounded expression analysis
  (obvious member/type mistakes) — never runtime evaluation. Intentionally
  under-specified until 0.7/0.8 reveal the false-positive risk.

Explicitly **not planned:** emulator / rules-test-SDK integration and any
in-IDE authorization evaluation.
