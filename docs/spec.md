# hotrulez Project Spec — v2 (Symbol Intelligence)

Status: draft
Last updated: 2026-06-27
Supersedes: `docs/v1/spec.md` (archived). The v1 spec and task list cover the
road to the shipped 0.4.0 release and remain the historical record under
`docs/v1/`.

## Context

`hotrulez` is a JetBrains IDE plugin for Firebase Cloud Firestore Security
Rules. v1 (shipped through 0.4.0) made `.rules` a complete **passive** language:
file recognition, syntax highlighting, a generated Grammar-Kit/JFlex parser and
typed PSI, a PSI-aware formatter, structural and usage diagnostics (an annotator
plus two inspections), and editor polish (icon, color settings page, brace
matcher, quote handler, commenter).

What v1 deliberately does **not** have is anything *interactive* that depends on
understanding which symbol is which. There is no code completion, no
go-to-definition, no find-usages, no rename refactoring. The PSI is a tree of
syntax, not a graph of meaning: a call to `isOwner(...)` is not linked to the
`function isOwner(...)` that defines it, and a use of the path variable `city`
is not linked to the `{city}` that binds it.

v2 closes exactly that gap.

## Thesis

**v2 builds the one thing v1 lacks — a PSI reference/resolve layer for Firestore
Rules — and uses it to deliver the four features that ride on it together:
go-to-definition, find-usages, rename refactoring, and code completion.**

These four are not independent milestones. They all sit on a single piece of
infrastructure: the ability to resolve a *use* of a name to its *declaration*
(and to enumerate the declarations visible at a point). Build that resolver once
and all four features become reachable from it. Splitting them across releases
would mean building the same resolver twice, so they ship as one milestone.

## Decisions

These decisions were made deliberately and constrain the rest of this document.

- **Release model: incremental 0.5.x, one milestone per release.** v2 is the
  0.5 line. Later milestones ship as 0.6, 0.7, … This spec describes the v2
  milestone in implementation detail and sketches the sequenced roadmap after
  it; it does not try to fully specify 0.6+.
- **Anchor: first-principles parity with mature JetBrains language plugins.**
  The bar is "what Kotlin, Go, and Rust language plugins do for symbols." There
  is no external usage telemetry; scope is chosen from platform convention and
  what makes `.rules` feel like a first-class language, not from a feature
  request backlog.
- **Hold every v1 non-goal.** v2 adds IDE intelligence *within the same
  conservative, structural scope*. It does not evaluate authorization, connect
  to Firebase, or model runtime behavior. See Non-Goals.
- **Emulator / in-IDE rules testing is out of scope and is not on the roadmap.**
  Running rules against the emulator or the rules-test SDK would break v1's
  "no project connection" principle and is not part of language-plugin parity —
  it is a test-runner concern that `firebase-tools` and the emulator suite
  already serve. It is explicitly dropped, not deferred.
- **Quick documentation (hover docs) is deferred to the authoring-polish
  milestone, not v2.** Built-ins and helpers appear in completion in v2 but do
  not carry doc payloads yet.

## Non-Goals

v2 inherits every v1 non-goal unchanged. The plugin must not:

- Evaluate whether a request is allowed or denied, or otherwise infer
  authorization or security quality.
- Connect to Firebase projects, emulators, the rules-test SDK, credentials, or
  live Firestore data, or run rules tests in-IDE.
- Hard-code Firebase project IDs or environment-specific paths.
- Support Cloud Storage Rules in this release (it is a sequenced future
  milestone, not v2 scope).
- Model the language as JavaScript, JSON, or generic configuration.
- Replace official Firebase tools for deployment or authorization testing.
- Add web app frameworks or unrelated UI dependencies.

Additionally, v2-specific non-goals:

- **No type inference.** Completion offers documented members from a static
  table; it does not infer the type of an arbitrary expression. `request.` and
  `resource.` member completion is a fixed, doc-sourced list, never the result
  of evaluating field types. Deep, type-aware analysis is a later milestone.
- **No semantic correctness claims.** Resolve/rename only links names to
  declarations; it does not assert a rule is correct or secure.

## Documentation Sources

Firebase Rules semantics below were re-checked against official docs on
2026-06-27, per the standing ground rule that Firebase docs are authoritative
for language semantics. The load-bearing facts (the `cloud.firestore` service
name, the `get/list/read/create/update/delete/write` operations, the
`request`/`resource` built-ins and `request.auth.uid`/`resource.data`, the
`get/exists/getAfter` helpers, `{city}`/`{name=**}` path wildcards, and `let`
being valid only inside function bodies) were re-confirmed against the live docs
on 2026-06-28:

- Firestore Rules structure and path/wildcard variables:
  `https://firebase.google.com/docs/firestore/security/rules-structure`
- Firebase Security Rules language (functions, `let`, scoping):
  `https://firebase.google.com/docs/rules/rules-language`
- Conditions, `request`/`resource`, and helper calls:
  `https://firebase.google.com/docs/firestore/security/rules-conditions`
- `request` object reference (member table source):
  `https://firebase.google.com/docs/reference/rules/rules.firestore.Request`

Before implementing the resolver, completion table, or rename scoping, re-check
these pages and the current IntelliJ Platform SDK docs for the relevant
extension points. Do not encode a member or scoping rule this spec does not
confirm; tag anything uncertain `UNCONFIRMED` (matching the existing `.bnf`
convention) and leave a TODO tied to the official source.

## Confirmed Firebase Semantics (the resolver must honor these)

The resolver and rename processor must implement Firestore's actual scoping, not
a textual approximation. The following are confirmed from the sources above.

### Functions

- Syntax: `function name(arg0, arg1, ...) { /* let bindings */ return expr; }`.
  Zero or more parameters. The body contains **a single `return` statement** and
  (in `rules_version = '2'`) **up to 10 `let` bindings**; it must end with the
  `return`. No loops or other logic.
- Functions "automatically access functions and variables from the scope in
  which they are defined." A function declared inside a `match` block can
  therefore reference that block's path variables and enclosing built-ins; a
  function declared at `service` scope cannot see match-local path variables.
- Functions "may call other functions but may not recurse"; total call-stack
  depth is limited to 20.
- **Resolution is scope-based, not declaration-order-based.** A call resolves to
  a function of that name visible in the current or an enclosing scope,
  regardless of whether the declaration appears before or after the call in the
  file. The resolver must not assume top-to-bottom visibility.

### `let` bindings

- Syntax: `let name = expression;`, function-local, up to 10 per function.
- A `let` name is visible within the remainder of the function body after its
  declaration. Uses resolve to that binding.

### Path / wildcard variables

- A single-segment wildcard `{city}` binds the name `city` (a string); a
  recursive wildcard `{document=**}` binds the name `document` (a path).
- A bound path variable is "visible within the `match` scope or any nested scope
  where the `path` is declared." A use resolves to the nearest enclosing binding
  of that name.
- **Shadowing:** if a nested `match` re-declares the same name, the inner
  binding shadows the outer one within the nested subtree. Rename and
  find-usages must respect this — renaming an outer `{city}` must not touch a
  shadowed inner `{city}`'s uses, and vice versa.

### Built-in variables and helpers (completion targets, not navigable)

- `request` top-level members: `auth`, `method`, `path`, `params`, `resource`,
  `time`.
- `request.auth` members: `uid`, `token` (and `request.auth` may be null when
  unauthenticated).
- `request.auth.token`: standard JWT claims (e.g. `email`, `email_verified`,
  `phone_number`, `name`, `sub`, `firebase`) plus arbitrary custom claims.
  Custom claims cannot be statically enumerated; the table lists only the
  documented standard claims and notes that custom claims exist.
- `resource` and `request.resource` members: `data` (the field map), `id`,
  `__name__`.
- Helper calls and their arity (already encoded in v1's diagnostics, reused
  here): `exists(path)`, `existsAfter(path)`, `get(path)`, `getAfter(path)` each
  take exactly one path argument.

The member table is **illustrative** here; the implementer must build the
authoritative version from the reference docs and tag anything uncertain
`UNCONFIRMED`.

## v2 Scope: Symbol Intelligence

### Symbols the resolver covers

Full treatment — resolve (go-to-definition), find-usages, and rename — for all
four user-defined symbol kinds:

- **Functions** (and their resolution across service/match scope, forward
  references, and inter-function calls).
- **Function parameters** (function-local).
- **`let` bindings** (function-local, post-declaration scope).
- **Path / wildcard variables** (match-subtree scope, with shadowing).

Built-in variables (`request`, `resource`) and helper functions
(`exists`/`get`/`getAfter`/`existsAfter`) are **recognized by the resolver** so
they are never mistaken for undefined symbols, and they **appear in completion**,
but they are **not navigable** — Ctrl+click on a built-in is a no-op (there is no
declaration to jump to). Quick-docs for them is deferred.

### Go to definition

- Ctrl+click / "Go to Declaration" on a function call jumps to its `function`
  declaration.
- On a path-variable use, jumps to the binding wildcard in the match path.
- On a parameter or `let` use, jumps to its binding.
- On a built-in or helper, does nothing (documented behavior, not a bug).
- Delivered through `PsiReference.resolve()` on the relevant PSI elements;
  the platform's go-to-declaration uses it automatically.

### Find usages

- Find Usages on any covered declaration lists every resolving use, scoped
  correctly (path-variable usages stop at a shadowing redeclaration).
- Requires a `FindUsagesProvider` and a word scanner over the lexer, plus
  `PsiNamedElement` declarations; the standard usage search consumes the same
  references that power go-to-definition.

### Rename refactoring

- Rename a function → updates the declaration and every call site.
- Rename a parameter or `let` → updates the binding and its in-body uses.
- Rename a path variable → updates the binding wildcard and every use within
  that match subtree, **stopping at a shadowing redeclaration**, and leaving
  unrelated same-named bindings untouched.
- New names are validated by a `NamesValidator` (must be a legal identifier;
  reserved keywords rejected).
- Works largely through references + `PsiNamedElement.setName()`; the
  path-variable scoping edge cases are handled by a dedicated
  `RenamePsiElementProcessor` so shadowing is correct.

### Code completion

A `CompletionContributor` with position-keyed providers. Completion offers:

- **After `allow `**: operation names `get, list, read, create, update, delete,
  write`.
- **Statement / structural position**: keywords `match`, `allow`, `function`,
  `return`, `let`, `if`, plus `rules_version` and `service` at file top level.
- **After `service `**: `cloud.firestore`.
- **Expression position**: in-scope user symbols (functions, parameters, `let`
  bindings, and path variables visible at the caret per the scoping rules),
  built-in variables (`request`, `resource`), helper functions
  (`exists`/`get`/`getAfter`/`existsAfter`), and literals (`true`, `false`,
  `null`).
- **Shallow member completion** after `request.`, `request.auth.`,
  `request.resource.`, and `resource.`: the documented members from the static
  table (one to two levels deep). This is a fixed list sourced from Firebase
  docs — **not type inference**. Unknown/custom claims are not invented.

Completion must respect scoping: a function's parameters are only offered inside
that function; a path variable is only offered within its match subtree; a `let`
is only offered after its declaration.

## Implementation Components

New package: `dev.lezli.hotrulez.references` (resolve/declarations) plus feature
packages as below. Reuse the existing generated PSI; do not regenerate the
grammar for this milestone unless a missing PSI accessor forces a narrow `.bnf`
change (e.g. exposing a named-element interface on declarations).

- **Declarations as named elements.** Make function declarations, parameters,
  `let` bindings, and path wildcards implement `PsiNamedElement` (and expose a
  name identifier), via Grammar-Kit `implements`/`mixin` hooks or a
  `*PsiImplUtil`. This is what find-usages and rename key off.
- **References and resolution.** Implement `PsiReference` (poly-variant where a
  name could be ambiguous) on function-call names and on identifier uses that
  can denote a parameter / `let` / path variable. Resolution walks enclosing
  scopes per the confirmed semantics. Register a
  `com.intellij.psi.referenceContributor` if references are attached by pattern
  rather than directly on PSI.
- **Find usages:** `com.intellij.lang.findUsagesProvider` with a
  `DefaultWordsScanner` over the highlighting/parsing lexer.
- **Rename:** a `com.intellij.lang.refactoring.namesValidator` and a
  `com.intellij.refactoring.renamePsiElementProcessor` for path-variable
  scoping.
- **Completion:** `com.intellij.completion.contributor` with
  `PlatformPatterns`-keyed `CompletionProvider`s and a static, doc-sourced
  member/keyword/operation table.
- **Go to definition:** no dedicated EP needed — provided by `PsiReference`
  resolution. Add a `GotoDeclarationHandler` only if a case is not expressible
  as a reference.

All registration goes through `plugin.xml` extension points. Re-check the
current IntelliJ Platform SDK docs for the exact tag names and signatures before
implementing; prefer extension points over startup code.

## Tests

Add focused tests with each piece, using the existing test fixtures style under
`src/test/testData`. Recommended fixtures and checks:

- **Resolve:** call → function declaration (incl. a forward reference and a
  call to a function declared in an enclosing scope); parameter use → parameter;
  `let` use → `let`; path-variable use → binding wildcard.
- **Scoping negatives:** a service-scope function does not resolve a match-local
  path variable; a `let` is not visible before its declaration.
- **Shadowing:** nested `match` reusing a path-variable name resolves each use
  to the nearest binding; find-usages and rename respect the boundary.
- **Find usages:** counts and locations for each symbol kind.
- **Rename:** function rename updates all call sites; path-variable rename
  updates only the correct subtree and leaves a shadowed same-name binding
  untouched; rename to a reserved keyword is rejected.
- **Completion:** operations after `allow`; in-scope symbols in expression
  position; scoping (parameter not offered outside its function; `let` not
  offered before declaration); shallow members after `request.` / `resource.` /
  `request.auth.`; built-ins/helpers present but non-navigable.
- **Recovery:** completion and resolve degrade gracefully in a partially
  malformed file (no exceptions; unrelated blocks still work).

## Milestone Definition

### v2 / 0.5: Symbol Intelligence

Done when:

- A PSI reference/resolve layer links uses to declarations for functions,
  parameters, `let` bindings, and path variables, honoring Firestore scoping
  and path-variable shadowing.
- Go-to-definition, find-usages, and rename work for all four symbol kinds, with
  correct scoping; built-ins are recognized but non-navigable.
- Code completion offers in-scope symbols, keywords, operations, helpers, and
  shallow `request.`/`resource.` members from a static doc-sourced table, with
  scoping respected and no type inference.
- All v1 non-goals still hold; nothing connects to Firebase or evaluates
  authorization.
- Tests cover resolve, scoping negatives, shadowing, find-usages, rename, and
  completion as listed above.
- Implementation choices follow current official JetBrains SDK and Firebase
  docs.

## Future Milestones (sequenced, not v2 scope)

Written here so the direction is explicit, but **not** part of the 0.5 release.
One milestone per release.

1. **0.6 — Actionable diagnostics.** Quick-fixes / intentions for the
   diagnostics v1 already raises (e.g. insert missing `rules_version = '2';`,
   add a missing `if`), plus new semantic checks the resolver now makes possible
   — **unused functions** and **undefined references** (a name that resolves to
   nothing). Highest value per unit of effort because detection mostly exists
   and the resolver is built. Still no runtime/authorization claims.
2. **Cloud Storage Rules.** Add `firebase.storage` as a sibling language/service
   shape, reusing lexer, formatter, and highlighting infrastructure; differs in
   service name, root match shape, and allowed methods.
3. **Authoring polish.** Structure view, code folding for braced blocks,
   quick-docs (the deferred hover docs for built-ins/helpers), and parameter
   info on calls.
4. **Toward semantics.** Type/dataflow-aware expression analysis (field/member
   validation against a model, obvious type mismatches), staying short of
   runtime evaluation.

Explicitly **not planned:** emulator or rules-test-SDK integration and any
in-IDE authorization evaluation. These would require revisiting v1's
no-connection, no-evaluation core principles and are out of the product's scope.

## Acceptance Criteria

v2 is successful when a developer editing a `.rules` file can:

- Ctrl+click a function call, parameter, `let`, or path variable and land on its
  declaration; Find Usages from any of them; and Rename any of them with the
  rest of the file updated correctly (including correct path-variable
  shadowing).
- Get useful, scope-aware completion for symbols, keywords, operations, helpers,
  and `request.`/`resource.` members — without the plugin inventing types or
  evaluating authorization.
- Trust that nothing connects to Firebase or claims a rule is secure.
- See tests covering resolve, scoping, shadowing, find-usages, rename, and
  completion for representative Firestore Rules examples.
