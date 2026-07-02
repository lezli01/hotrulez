# hotrulez Project Spec — v3 (Assisted Authoring)

Status: draft
Last updated: 2026-07-02
Supersedes: `docs/v2/spec.md` (archived). The v2 spec and task list cover
symbol intelligence (shipped 0.5.0) and Cloud Storage support (shipped 0.6.0)
and remain the historical record under `docs/v2/`. The v1 milestone lives under
`docs/v1/`.

## Context

`hotrulez` is a JetBrains IDE plugin for Firebase Security Rules — both Cloud
Firestore (`service cloud.firestore`) and Cloud Storage (`service
firebase.storage`) `.rules` files. Through 0.6.1 it makes `.rules` a
first-class language:

- **v1 (→ 0.4.0)** — passive language: file recognition, syntax highlighting,
  a Grammar-Kit/JFlex parser and typed PSI, a PSI-aware formatter, structural
  diagnostics (an always-on annotator plus two configurable inspections, 18
  checks in total), and editor polish (icon, color settings page, brace
  matcher, quote handler, commenter).
- **v2 (0.5.0)** — symbol intelligence: a PSI reference/resolve layer that
  honors Firebase Rules scoping and path-variable shadowing, and the four
  features that ride on it — go-to-definition, find-usages, rename, and
  scope-aware completion — for functions, parameters, `let` bindings, and path
  variables.
- **v2 (0.6.0)** — Cloud Storage as a sibling dialect of the same language,
  detected from the `service` declaration and modeled as data in
  `references/RulesService` (service name, root-match shape,
  `request`/`resource` member tables, path helpers).

The plugin now **understands** a `.rules` file. What it does not yet do is
**act on that understanding**. Its diagnostics point at problems but never
offer to fix them: `FirebaseRulesStructureInspection` registers every problem
with `LocalQuickFix.EMPTY_ARRAY`, and the annotator only calls `.range().create()`.
And although the resolver knows exactly which names resolve and which do not, no
diagnostic yet reports an *undefined* reference or an *unused* declaration —
the two checks the resolver most directly makes possible.

v3 closes that gap. It is the arc from *"the IDE reads your rules"* to *"the
IDE helps you write them."*

## Thesis

**v3 turns the plugin's existing understanding into active assistance,
delivered as three sequenced releases — one milestone per release, matching the
project's established cadence.** Each release stands on infrastructure that is
already shipped; none of them relaxes a single v1/v2 non-goal.

1. **0.7 — Actionable Diagnostics.** Make the diagnostics the plugin already
   raises *fixable*, and add the two new checks the 0.5 resolver unlocks
   (undefined references, unused declarations). This is the highest value per
   unit of effort: the detection layer mostly exists, quick-fixes are the most
   visible "the IDE is smart" win after completion and navigation, and the new
   checks are the direct payoff of having built the resolver.
2. **0.8 — Authoring Polish.** Reveal and navigate structure: a structure view
   (the `service` / `match` / `function` outline), code folding for braced
   blocks, quick-documentation (hover) for built-ins and helpers (the docs
   deferred out of v2), and parameter info on function and helper calls.
3. **0.9 — Toward Semantics.** Begin type/dataflow-aware expression analysis —
   flagging *obvious*, doc-grounded member and type mistakes — staying strictly
   short of runtime evaluation.

This spec describes **0.7 in full implementation detail**, **0.8 in enough
detail to commit to it**, and **0.9 as a lightly-sketched direction** — the
later a milestone is, the less we should over-specify it before the earlier
work teaches us what we actually need.

## Anchors

- **First-principles parity with mature JetBrains language plugins.** The bar
  is "what the Kotlin, Go, and Rust language plugins do." There is no usage
  telemetry; scope is chosen because platform convention makes `.rules` feel
  like a first-class language. Quick-fixes, an undefined-symbol inspection, a
  structure view, folding, quick-docs, and parameter info are all table stakes
  for a first-class language plugin — and all are still missing here.
- **Hold every v1/v2 non-goal.** v3 adds IDE assistance *within the same
  conservative, structural scope*. It does not evaluate authorization, connect
  to Firebase, or model runtime behavior. See Non-Goals.
- **Every fix is safe and obvious.** A quick-fix either makes one unambiguous
  correction (insert the missing `rules_version`, change `'1'` to `'2'`) or
  inserts a clearly-marked placeholder and parks the caret on it. A fix never
  guesses program *meaning* — it never invents a condition, a return value, or
  a type.
- **Emulator / in-IDE rules testing remains out of scope, not deferred.**
  Running the emulator or the rules-test SDK would break the "no project
  connection" principle and is a test-runner concern that `firebase-tools`
  already serves. It is explicitly dropped.

## Non-Goals

v3 inherits every v1/v2 non-goal unchanged. The plugin must not:

- Evaluate whether a request is allowed or denied, or otherwise infer
  authorization or security quality. Diagnostics — including the new ones —
  stay structural.
- Connect to Firebase projects, emulators, the rules-test SDK, credentials, or
  live data, or run rules tests in-IDE.
- Hard-code Firebase project IDs or environment-specific paths.
- Model the language as JavaScript, JSON, or generic configuration.
- Replace official Firebase tooling for deployment or authorization testing.
- Add web-app frameworks or unrelated UI dependencies.

Additionally, v3-specific non-goals:

- **No type inference (still).** The new undefined-reference check is *name
  resolution* — it reports an identifier that resolves to no declaration and is
  not a known built-in. It is **not** type analysis: members after `.`
  (`request.foo`, `resource.data.bar`) are **never** flagged, because that would
  require a type model, and custom `request.auth.token` claims are never
  invented. (0.9 revisits type-shaped checks, and even there stays doc-grounded
  and short of runtime evaluation.)
- **Path / wildcard variables are never "unused."** A match segment such as
  `/cities/{city}` is routinely declared without being referenced — it is a
  structural URL capture, not a dead local. The unused-symbol check deliberately
  excludes path/wildcard variables.
- **No fix that silently changes meaning.** Where the correct repair is
  genuinely ambiguous (which operation an empty `allow` intended, what
  expression a bare `return;` wanted, which of several `service` blocks to keep),
  the diagnostic is reported **without** an automatic fix rather than guessing.
  These gaps are listed explicitly below — never left silent.

## Documentation Sources

Per the standing ground rule, official Firebase docs are authoritative for
language semantics and current IntelliJ Platform SDK docs (via Context7) are
authoritative for extension points. Before implementing each milestone,
re-check the relevant pages and do not encode a member, scoping, or fix
behavior the docs do not confirm; tag anything uncertain `UNCONFIRMED`
(matching the existing `.bnf` convention) with a TODO tied to the source.

Firebase semantics load-bearing for v3 (already confirmed for v2 on 2026-06-28;
re-confirm the specific facts each milestone relies on before coding):

- Rules structure, path/wildcard variables, and the per-service root match:
  `https://firebase.google.com/docs/firestore/security/rules-structure`
- Rules language (functions, `let`, scoping, forward references):
  `https://firebase.google.com/docs/rules/rules-language`
- Conditions, `request`/`resource`, helper calls:
  `https://firebase.google.com/docs/firestore/security/rules-conditions`
- `request`/`resource` member reference (member-table source):
  `https://firebase.google.com/docs/reference/rules/rules.firestore.Request`

IntelliJ Platform SDK topics to re-check before 0.7: attaching a
`LocalQuickFix` to an inspection `ProblemDescriptor`; attaching a quick-fix /
intention to an annotation (`AnnotationBuilder.withFix` and the current
adapter for turning a `LocalQuickFix` into an `IntentionAction`, e.g.
`LocalQuickFixAndIntentionActionOnPsiElement`); `ProblemHighlightType`
values (`LIKE_UNKNOWN_SYMBOL`, `LIKE_UNUSED_SYMBOL`); and
`localInspection` registration. Prefer extension points over startup code.

## v3 Milestone Detail

### 0.7 — Actionable Diagnostics (specified in full)

Two halves that share one new package of fix classes.

#### Half A — Quick-fixes for existing diagnostics

Attach a fix to each existing diagnostic **where a single unambiguous or
clearly-placeholder repair exists**. Fixes are implemented once (as
`LocalQuickFix`) and attached to inspection problems directly and to annotator
errors via the platform's quick-fix/intention adapter.

Structure-inspection warnings (replace the current `LocalQuickFix.EMPTY_ARRAY`):

| Diagnostic | Quick-fix | Notes |
|---|---|---|
| Missing `rules_version` | Insert `rules_version = '2';` at the top of the file | Flagship fix. |
| Non-v2 `rules_version` | Change the version literal to `'2'` | |
| `rules_version` after `service` | Move the `rules_version` line above the `service` block | |
| Unknown service name | Two fixes: change to `cloud.firestore`; change to `firebase.storage` | |
| `service` missing its block | Add a `{ … }` block, scaffolding the conventional root match for the detected service | Caret parked inside the block. |
| Missing root match | Insert `match /databases/{database}/documents { }` (Firestore) or `match /b/{bucket}/o { }` (Storage), per detected service | Service-aware via `RulesService.rootMatchHint`. |

Annotator errors (extend the annotator's `error(...)` helper to accept fixes):

| Diagnostic | Quick-fix | Notes |
|---|---|---|
| Unknown operation (`allow read, fetch`) | Replace with the closest known operation, offered only within a small edit distance of a real op | Typo repair; no fix when nothing is close. |
| Duplicate parameter name | Remove the duplicate parameter | |
| Function missing its `return` | Add `return <expr>;` with a `false` placeholder, caret on the placeholder | Placeholder, not a guess. |
| Misplaced recursive wildcard (v1) | Two fixes: move `{name=**}` to the last segment; upgrade to `rules_version = '2';` | Shares the version-upgrade fix. |

Usage-inspection warnings:

| Diagnostic | Quick-fix | Notes |
|---|---|---|
| Recursive wildcard without v2 | Add / upgrade to `rules_version = '2';` | Shared version fix. |
| Condition-less `allow` | Append `: if <condition>;` placeholder, caret on the placeholder | Nudge; placeholder, not a guess. |

**Deliberately no automatic fix** (reported as before, documented here so the
gap is never silent): empty operation list (intended operation is ambiguous);
bare `return;` (no expression to synthesize); more than one recursive wildcard
(which to drop is ambiguous); multiple `service` blocks (cannot choose or
merge); helper-call arity (the missing/extra argument is unknown).

#### Half B — New resolver-enabled checks

A new configurable inspection, `FirebaseRulesSymbolInspection` (displayName
"Firebase Rules symbol resolution", group "Firebase Rules", `WARNING`, on by
default). It lives in an inspection rather than the always-on annotator because
unresolved and unused names are constantly transient while typing — the same
severity philosophy that puts file-shape checks in an inspection.

- **Undefined reference.** A `reference_expression` whose `FirebaseRulesReference`
  resolves to nothing (`multiResolve` is empty) **and** whose name is not a
  built-in (`FirebaseRulesBuiltins.isBuiltinName`) is reported as `Cannot
  resolve symbol 'x'` with `ProblemHighlightType.LIKE_UNKNOWN_SYMBOL`. The
  reference is `soft`, so the platform does not flag it on its own — this
  inspection is the explicit surfacing. Members are `member_expression` nodes,
  not `reference_expression`s, so `request.foo` is structurally excluded — no
  type model, no non-goal violation. Optional secondary fix when the name is in
  callee position: *Create function 'x'*, scaffolding
  `function x(<args>) { return false; }` in the nearest enclosing block.
- **Unused function.** A `function` declaration with no resolving reference in
  the file, reported with `ProblemHighlightType.LIKE_UNUSED_SYMBOL` as `Function
  'x' is never used`. Fix: *Remove function 'x'* (safe delete of the
  declaration). Usages are found with the shipped find-usages layer
  (`ReferencesSearch` over the single file).
- **Unused `let` binding.** A `let x = …;` never referenced in the remainder of
  its function body: `Variable 'x' is never used`. Fix: *Remove 'let' binding*.
- **Unused parameter.** A parameter never referenced in its function body:
  `Parameter 'x' is never used`, grayed. **Reported only, no removal fix in
  0.7** — removing a parameter must update every call site's argument list,
  which is refactoring-grade and deferred. Documented, not silent.
- **Not flagged:** path / wildcard variables (see Non-Goals), and any name that
  resolves or is a built-in/helper.

#### Confirmed semantics the new checks must honor

- Resolution is **scope-based, not declaration-order-based**: a forward
  reference to a function declared later in an enclosing scope resolves and is
  therefore *not* undefined. `FirebaseRulesScopes` already implements this; the
  inspection must consult it, never a textual/top-to-bottom heuristic.
- A `let` is visible only *after* its declaration, and shadowing (nested path
  variable / same-named function) follows the v2 rules. "Unused" and
  "undefined" must be computed through the resolver so these all hold.
- Built-ins (`request`, `resource`) and helpers (`get`/`getAfter`/`exists`/
  `existsAfter`, and Storage's cross-service `firestore.get`/`firestore.exists`)
  are recognized, non-navigable, and never "undefined."

#### Implementation components (0.7)

- New package `dev.lezli.hotrulez.diagnostics.fixes` — one `LocalQuickFix` per
  repair above, each mutating the PSI (insert/replace/delete) via the document
  or PSI factory; placeholder fixes position the caret.
- Extend `FirebaseRulesAnnotator.error(...)` to accept optional fixes and attach
  them through `AnnotationBuilder.withFix` (via the current SDK adapter).
- Populate `FirebaseRulesStructureInspection` / `FirebaseRulesUsageInspection`
  problem descriptors with their fixes (drop `LocalQuickFix.EMPTY_ARRAY`).
- New `FirebaseRulesSymbolInspection` for Half B, registered as a
  `localInspection` in `plugin.xml`.
- Reuse the shipped resolver (`FirebaseRulesScopes`, `FirebaseRulesReference`,
  `FirebaseRulesBuiltins`) and find-usages layer; regenerate the grammar only if
  a missing PSI accessor forces a narrow `.bnf` change.

#### Tests (0.7)

- New `FirebaseRulesQuickFixTest`: for each fix, apply it to a before-fixture
  and assert the after-text (using the existing `testData` fixture style).
- Extend `FirebaseRulesInspectionTest` for `FirebaseRulesSymbolInspection`:
  undefined reference flagged; forward reference and enclosing-scope call *not*
  flagged; built-ins/helpers not flagged; members never flagged; unused
  function / `let` / parameter flagged; path variables never flagged.
- Recovery: fixes and the symbol inspection degrade gracefully in a partially
  malformed file (no exceptions; unrelated blocks unaffected).
- `./gradlew test` green after the milestone.

#### Acceptance (0.7)

- Every diagnostic in the Half-A table offers its fix; the "no automatic fix"
  set is reported without one, as designed.
- The symbol inspection flags undefined references and unused
  functions/`let`s/parameters with correct, resolver-based scoping, and never
  flags members, built-ins, helpers, or path variables.
- All v1/v2 non-goals still hold; nothing connects to Firebase, evaluates
  authorization, or infers a type.
- Tests cover fixes, the symbol inspection (positives and scoping negatives),
  and recovery; implementation follows current JetBrains SDK and Firebase docs.

### 0.8 — Authoring Polish (committed; specified enough to start)

Structure and navigation features, each riding on the existing PSI and
`RulesService` profile. All are read-only views over the PSI — no new
semantics.

- **Structure view** — `lang.psiStructureViewFactory`: an outline of the
  `service` → `match` (by path) → `function` / `allow` tree, so a large rules
  file is navigable from the Structure tool window.
- **Code folding** — `lang.foldingBuilder`: fold `service`, `match`, and
  `function` braced blocks (and optionally block comments), with sensible
  placeholder text (e.g. the match path).
- **Quick documentation** — `lang.documentationProvider`: hover / Ctrl-Q docs
  for built-ins (`request`, `resource` and their members), helpers, and
  `allow` operations, sourced from the same static, doc-sourced `RulesService`
  and `FirebaseRulesBuiltins` tables that drive completion. This is the hover
  docs deferred out of v2. Still no type inference.
- **Parameter info** — `codeInsight.parameterInfo`: on a call to a user
  `function` (parameter names from the declaration) and to the fixed-arity path
  helpers (`get`/`exists`/…), show the signature while typing arguments.

Non-goals unchanged; docs strings stay structural and doc-grounded. Detailed
task breakdown deferred until 0.7 ships.

### 0.9 — Toward Semantics (direction, not commitment)

Begin *doc-grounded* expression analysis, still short of runtime evaluation:
flag *obvious* member and type mistakes that the static Firebase docs make
unambiguous — e.g. a member that cannot exist on a known built-in in the
detected dialect, or an operator applied to plainly incompatible literal types
— while never asserting authorization, never inventing types for user data, and
never evaluating a rule. The exact check set will be shaped by what 0.7/0.8
reveal about false-positive risk; this milestone is intentionally left
under-specified until then, and may itself split across releases.

## Future (explicitly not planned)

Emulator / rules-test-SDK integration and any in-IDE authorization evaluation
remain out of scope — they would require revisiting the no-connection,
no-evaluation core principles that define the product.

## Overall Acceptance Criteria (v3 program)

v3 is successful when a developer editing a `.rules` file can:

- **0.7** — accept a one-click fix for the structural problems the plugin flags,
  and see undefined references and unused functions/`let`s/parameters surfaced
  with correct scoping — without the plugin inventing meaning, types, or
  authorization judgments.
- **0.8** — navigate a large file via a structure view, fold blocks, read
  built-in/helper docs on hover, and see parameter info while calling functions
  and helpers.
- **0.9** — receive conservative, doc-grounded warnings about obvious
  expression mistakes, still with nothing connected to Firebase and no runtime
  evaluation.
- Trust, throughout, that every check and fix is structural: the plugin never
  connects to Firebase and never claims a rule is secure.
