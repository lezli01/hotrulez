# hotrulez Tasks — v2 (Symbol Intelligence)

Status: draft
Last updated: 2026-06-27
Source: `docs/spec.md`
Predecessor: `docs/v1/tasks.md` (archived; covers the road to 0.4.0).

This list covers the v2 / 0.5 milestone only. Later milestones are sketched in
the roadmap section and in `docs/spec.md`; they are not v2 scope and are not
broken into checkable items here.

## Ground Rules

- [ ] Re-check current IntelliJ Platform SDK docs before changing or registering
  IntelliJ APIs / extension points, generated parser tooling, or before
  encoding Firebase Rules semantics. (Firebase scoping + `request`/`resource`
  semantics were verified 2026-06-27; see spec Documentation Sources.)
- [ ] Hold every v1 non-goal: no authorization evaluation, no Firebase/emulator/
  rules-test-SDK connection, no Storage rules in v2, no project IDs, structural
  not JavaScript.
- [ ] No type inference: `request.`/`resource.` member completion comes from a
  static, doc-sourced table, never from evaluating expression types.
- [ ] Add focused tests with each resolve, navigation, rename, or completion
  change.
- [ ] Tag any construct or member not confirmed by official docs `UNCONFIRMED`
  (matching the existing `.bnf` convention) with a TODO tied to the source.
- [ ] Update `README.md` and `AGENTS.md` with the new symbol-intelligence
  features once they ship.

## Milestone v2 / 0.5: Symbol Intelligence

### Reference / resolve infrastructure

- [ ] Add `dev.lezli.hotrulez.references` package.
- [ ] Make `function` declarations implement `PsiNamedElement` with a name
  identifier (via Grammar-Kit `implements`/`mixin` or a `*PsiImplUtil`).
- [ ] Make function parameters named elements.
- [ ] Make `let` bindings named elements.
- [ ] Make path / wildcard variables (`{city}`, `{document=**}`) named elements.
- [ ] If a PSI accessor is missing, make the narrowest possible `.bnf` change to
  expose a named-element interface; keep generated and handwritten code
  separated and re-run the generate tasks.
- [ ] Implement `PsiReference` (poly-variant where a name is ambiguous) on
  function-call names.
- [ ] Implement references on identifier uses that can denote a parameter,
  `let` binding, or path variable.
- [ ] Implement function resolution as scope-based, not declaration-order-based:
  resolve calls to a function visible in the current or an enclosing
  service/match scope, including forward references.
- [ ] Implement `let` visibility: function-local, only after its declaration.
- [ ] Implement path-variable scope: visible within the binding `match` subtree
  and nested scopes, with a nested redeclaration shadowing the outer name.
- [ ] Recognize built-in variables (`request`, `resource`) and helper calls
  (`exists`/`existsAfter`/`get`/`getAfter`) in the resolver so they are not
  treated as undefined; mark them non-navigable.
- [ ] Register a `com.intellij.psi.referenceContributor` if references are
  attached by pattern rather than directly on PSI.

### Go to definition

- [ ] Confirm "Go to Declaration" resolves a function call to its declaration
  (including a forward reference and an enclosing-scope call).
- [ ] Resolve a path-variable use to its binding wildcard.
- [ ] Resolve a parameter or `let` use to its binding.
- [ ] Built-ins and helpers are a no-op on Ctrl+click (documented behavior).
- [ ] Add a `GotoDeclarationHandler` only if a case is not expressible as a
  `PsiReference`.

### Find usages

- [ ] Add a `com.intellij.lang.findUsagesProvider`.
- [ ] Add a `DefaultWordsScanner` over the lexer.
- [ ] Find Usages works for functions, parameters, `let` bindings, and path
  variables, stopping at a path-variable shadowing boundary.

### Rename refactoring

- [ ] Add a `com.intellij.lang.namesValidator` (legal identifier; reserved
  keywords rejected).
- [ ] Implement `setName()` on named elements (add an `ElementManipulator` if
  needed).
- [ ] Add a `com.intellij.refactoring.renamePsiElementProcessor` for
  path-variable scoping/shadowing.
- [ ] Function rename updates the declaration and every call site.
- [ ] Parameter / `let` rename updates the binding and its in-body uses.
- [ ] Path-variable rename updates only the correct match subtree and leaves a
  shadowed same-name binding untouched.

### Code completion

- [ ] Add a `com.intellij.completion.contributor` with `PlatformPatterns`-keyed
  providers.
- [ ] Operation completion after `allow `:
  `get, list, read, create, update, delete, write`.
- [ ] Keyword completion in statement / structural position
  (`match`, `allow`, `function`, `return`, `let`, `if`), with `rules_version`
  and `service` at file top level.
- [ ] `cloud.firestore` completion after `service `.
- [ ] Expression-position completion: in-scope user symbols (functions,
  parameters, `let`, path variables visible at the caret), built-ins
  (`request`, `resource`), helpers, and literals (`true`, `false`, `null`).
- [ ] Shallow member completion after `request.`, `request.auth.`,
  `request.resource.`, and `resource.` from the static table (one to two levels;
  no type inference; custom claims not invented).
- [ ] Build the static member / keyword / operation table from the official
  Firebase reference docs; tag uncertain entries `UNCONFIRMED`.
- [ ] Completion respects scoping: parameters only inside their function, path
  variables only within their subtree, `let` only after its declaration.

### Tests

- [ ] Resolve: call → declaration, including a forward reference and a call to a
  function declared in an enclosing scope.
- [ ] Resolve: parameter, `let`, and path-variable uses → their bindings.
- [ ] Scoping negatives: a service-scope function does not resolve a match-local
  path variable; a `let` is not visible before its declaration.
- [ ] Shadowing: nested `match` reusing a name resolves each use to the nearest
  binding; find-usages and rename respect the boundary.
- [ ] Find usages: counts and locations for each symbol kind.
- [ ] Rename: function updates all call sites; path variable updates only the
  correct subtree and leaves a shadowed binding untouched; rename to a reserved
  keyword is rejected.
- [ ] Completion: operations after `allow`; in-scope symbols in expression
  position; scoping respected; shallow members after `request.`/`resource.`/
  `request.auth.`; built-ins and helpers present but non-navigable.
- [ ] Recovery: completion and resolve degrade gracefully in a partially
  malformed file (no exceptions; unrelated blocks still work).
- [ ] Run `./gradlew test` after the milestone.

### Registration

- [ ] Register all new extension points in `plugin.xml`.
- [ ] Verify the exact EP tag names and signatures against current SDK docs
  before implementation.

## Release-Quality Acceptance (v2 / 0.5)

- [ ] Go-to-definition, find-usages, and rename work for functions, parameters,
  `let` bindings, and path variables, with correct Firestore scoping and
  path-variable shadowing.
- [ ] Built-ins are recognized (not flagged undefined) but non-navigable.
- [ ] Completion offers scope-aware symbols, keywords, operations, helpers, and
  shallow `request.`/`resource.` members from a static doc-sourced table, with
  no type inference.
- [ ] All v1 non-goals still hold; nothing connects to Firebase or evaluates
  authorization.
- [ ] Tests cover resolve, scoping negatives, shadowing, find-usages, rename,
  and completion.
- [ ] Implementation choices follow current official JetBrains SDK and Firebase
  docs.

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
