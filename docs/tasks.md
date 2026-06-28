# hotrulez Tasks — v2 (Symbol Intelligence)

Status: draft
Last updated: 2026-06-28
Source: `docs/spec.md`
Predecessor: `docs/v1/tasks.md` (archived; covers the road to 0.4.0).

This list covers the v2 / 0.5 milestone only. Later milestones are sketched in
the roadmap section and in `docs/spec.md`; they are not v2 scope and are not
broken into checkable items here.

Coverage status (audited 2026-06-28 against branch `feat/v2-symbol-intelligence`,
commit `fe04f41`): the v2 implementation is essentially complete — go-to-
definition, find-usages, rename, and scope-aware completion all ship with tests
(all green in the last `./gradlew test` run; see `build/test-results/`). A few
items remain open and are flagged inline below with a _(Partial: …)_ note — the
`UNCONFIRMED`-tag `TODO` markers under Ground Rules, the `let`-rename test under
Rename refactoring, the multi-pattern completion routing under Code completion,
and the two "follows current docs" editorial checks (under Ground Rules and
Release-Quality Acceptance).

## Ground Rules

- [ ] Re-check current IntelliJ Platform SDK docs before changing or registering
  IntelliJ APIs / extension points, generated parser tooling, or before
  encoding Firebase Rules semantics. (Firebase scoping + `request`/`resource`
  semantics were verified 2026-06-27; see spec Documentation Sources.)
  _(Standing process rule, not a discrete deliverable; the doc-check workflow is
  recorded in `AGENTS.md` and `docs/spec.md`.)_
- [x] Hold every v1 non-goal: no authorization evaluation, no Firebase/emulator/
  rules-test-SDK connection, no Storage rules in v2, no project IDs, structural
  not JavaScript.
- [x] No type inference: `request.`/`resource.` member completion comes from a
  static, doc-sourced table, never from evaluating expression types.
- [x] Add focused tests with each resolve, navigation, rename, or completion
  change.
- [ ] Tag any construct or member not confirmed by official docs `UNCONFIRMED`
  (matching the existing `.bnf` convention) with a TODO tied to the source.
  _(Partial: `UNCONFIRMED` tags are present in `.bnf` and
  `FirestoreRulesBuiltins.kt`, but no literal `TODO` markers tied to the source
  exist — `grep TODO src/main` finds none.)_
- [x] Update `README.md` and `AGENTS.md` with the new symbol-intelligence
  features once they ship.

## Milestone v2 / 0.5: Symbol Intelligence

### Reference / resolve infrastructure

- [x] Add `dev.lezli.hotrulez.references` package.
- [x] Make `function` declarations implement `PsiNamedElement` with a name
  identifier (via Grammar-Kit `implements`/`mixin` or a `*PsiImplUtil`).
- [x] Make function parameters named elements.
- [x] Make `let` bindings named elements.
- [x] Make path / wildcard variables (`{city}`, `{document=**}`) named elements.
- [x] If a PSI accessor is missing, make the narrowest possible `.bnf` change to
  expose a named-element interface; keep generated and handwritten code
  separated and re-run the generate tasks.
- [x] Implement `PsiReference` (poly-variant where a name is ambiguous) on
  function-call names.
- [x] Implement references on identifier uses that can denote a parameter,
  `let` binding, or path variable.
- [x] Implement function resolution as scope-based, not declaration-order-based:
  resolve calls to a function visible in the current or an enclosing
  service/match scope, including forward references.
- [x] Implement `let` visibility: function-local, only after its declaration.
- [x] Implement path-variable scope: visible within the binding `match` subtree
  and nested scopes, with a nested redeclaration shadowing the outer name.
- [x] Recognize built-in variables (`request`, `resource`) and helper calls
  (`exists`/`existsAfter`/`get`/`getAfter`) in the resolver so they are not
  treated as undefined; mark them non-navigable.
- [x] Register a `com.intellij.psi.referenceContributor` if references are
  attached by pattern rather than directly on PSI.
  _(N/A — references are attached directly on the PSI via
  `FirestoreRulesReferenceExpressionMixin.getReference()`, so no
  `referenceContributor` is needed; the choice is documented in `plugin.xml`.)_

### Go to definition

- [x] Confirm "Go to Declaration" resolves a function call to its declaration
  (including a forward reference and an enclosing-scope call).
- [x] Resolve a path-variable use to its binding wildcard.
- [x] Resolve a parameter or `let` use to its binding.
- [x] Built-ins and helpers are a no-op on Ctrl+click (documented behavior).
- [x] Add a `GotoDeclarationHandler` only if a case is not expressible as a
  `PsiReference`.
  _(Done by omission — every case resolves via `PsiReference`, so no handler was
  added.)_

### Find usages

- [x] Add a `com.intellij.lang.findUsagesProvider`.
- [x] Add a `DefaultWordsScanner` over the lexer.
- [x] Find Usages works for functions, parameters, `let` bindings, and path
  variables, stopping at a path-variable shadowing boundary.

### Rename refactoring

- [x] Add a `com.intellij.lang.namesValidator` (legal identifier; reserved
  keywords rejected).
- [x] Implement `setName()` on named elements (add an `ElementManipulator` if
  needed).
- [x] Add a `com.intellij.refactoring.renamePsiElementProcessor` for
  path-variable scoping/shadowing.
- [x] Function rename updates the declaration and every call site.
- [ ] Parameter / `let` rename updates the binding and its in-body uses.
  _(Partial: parameter rename is implemented and tested
  (`testRenameParameterUpdatesUsesButNotSameNamedMember`); `let` rename uses the
  same named-element / manipulator path but has no test, so its behavior is
  unverified.)_
- [x] Path-variable rename updates only the correct match subtree and leaves a
  shadowed same-name binding untouched.

### Code completion

- [ ] Add a `com.intellij.completion.contributor` with `PlatformPatterns`-keyed
  providers.
  _(Partial: one `completion.contributor` is registered, but it uses a single
  universal `PlatformPatterns.psiElement()` pattern with internal `when`-based
  routing rather than multiple pattern-keyed providers.)_
- [x] Operation completion after `allow `:
  `get, list, read, create, update, delete, write`.
- [x] Keyword completion in statement / structural position
  (`match`, `allow`, `function`, `return`, `let`, `if`), with `rules_version`
  and `service` at file top level.
- [x] `cloud.firestore` completion after `service `.
- [x] Expression-position completion: in-scope user symbols (functions,
  parameters, `let`, path variables visible at the caret), built-ins
  (`request`, `resource`), helpers, and literals (`true`, `false`, `null`).
- [x] Shallow member completion after `request.`, `request.auth.`,
  `request.resource.`, and `resource.` from the static table (one to two levels;
  no type inference; custom claims not invented).
- [x] Build the static member / keyword / operation table from the official
  Firebase reference docs; tag uncertain entries `UNCONFIRMED`.
  _(Table built and `UNCONFIRMED`-tagged in `FirestoreRulesBuiltins.kt`; live
  doc-fidelity is an editorial check — see the doc re-check rule under Ground
  Rules and the "follows current docs" item under Release-Quality Acceptance.)_
- [x] Completion respects scoping: parameters only inside their function, path
  variables only within their subtree, `let` only after its declaration.

### Tests

- [x] Resolve: call → declaration, including a forward reference and a call to a
  function declared in an enclosing scope.
- [x] Resolve: parameter, `let`, and path-variable uses → their bindings.
- [x] Scoping negatives: a service-scope function does not resolve a match-local
  path variable; a `let` is not visible before its declaration.
- [x] Shadowing: nested `match` reusing a name resolves each use to the nearest
  binding; find-usages and rename respect the boundary.
- [x] Find usages: counts and locations for each symbol kind.
- [x] Rename: function updates all call sites; path variable updates only the
  correct subtree and leaves a shadowed binding untouched; rename to a reserved
  keyword is rejected.
- [x] Completion: operations after `allow`; in-scope symbols in expression
  position; scoping respected; shallow members after `request.`/`resource.`/
  `request.auth.`; built-ins and helpers present but non-navigable.
- [x] Recovery: completion and resolve degrade gracefully in a partially
  malformed file (no exceptions; unrelated blocks still work).
- [x] Run `./gradlew test` after the milestone.
  _(Last run green per `build/test-results/test/*.xml` — 0 failures; these are
  local, git-ignored build artifacts, so re-run before release to confirm.)_

### Registration

- [x] Register all new extension points in `plugin.xml`.
- [x] Verify the exact EP tag names and signatures against current SDK docs
  before implementation.

## Release-Quality Acceptance (v2 / 0.5)

- [x] Go-to-definition, find-usages, and rename work for functions, parameters,
  `let` bindings, and path variables, with correct Firestore scoping and
  path-variable shadowing.
- [x] Built-ins are recognized (not flagged undefined) but non-navigable.
- [x] Completion offers scope-aware symbols, keywords, operations, helpers, and
  shallow `request.`/`resource.` members from a static doc-sourced table, with
  no type inference.
- [x] All v1 non-goals still hold; nothing connects to Firebase or evaluates
  authorization.
- [x] Tests cover resolve, scoping negatives, shadowing, find-usages, rename,
  and completion.
- [ ] Implementation choices follow current official JetBrains SDK and Firebase
  docs.
  _(Verifiable parts hold — current EPs registered, IntelliJ Platform 2025.2,
  provenance comments dated 2026-06-27; live doc-currency is an editorial check
  not verifiable from the repo.)_

## Future Milestones (roadmap — not v2 scope)

Sequenced, one milestone per release. See `docs/spec.md` for detail.

- **0.6 — Actionable diagnostics:** quick-fixes/intentions for existing
  diagnostics + resolver-enabled semantic checks (unused functions, undefined
  references).
- **Cloud Storage Rules:** `firebase.storage` as a sibling service shape.
- **Authoring polish:** structure view, folding, quick-docs, parameter info.
- **Toward semantics:** type/dataflow-aware expression analysis (no runtime
  evaluation).

Explicitly **not planned:** emulator / rules-test-SDK integration and any in-IDE
authorization evaluation.
