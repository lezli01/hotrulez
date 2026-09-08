# HotRulez Project Spec — m4 (Authoring Polish)

Status: **archived — m4 shipped.** This is the **m4 (Authoring Polish)** plan as
it stood while m4 was the active milestone — the second milestone of the
Assisted Authoring arc (m3 / m4 / m5). m4 shipped: merged to `master` as
PR #31, with follow-up fixes (`4dbc6bc` comment attribution / dialect gate, PR #32
brand casing), and released by release-please (2026-07-04). m3 (Actionable
Diagnostics) and m4 (Authoring Polish) shipped in the same release. This
document detailed m4 in full, with **m5 (Toward Semantics)** carried forward as a
lightly-sketched direction. It is kept as the historical record for the m4 phase.
Superseded by `docs/spec.md`, which continues the same Assisted Authoring arc and
carries **m5 (Toward Semantics)** forward as a well-scoped sketch.
Last updated: 2026-07-03 (archived 2026-07-05).
Program: the second milestone of the **Assisted Authoring** arc
(m3 / m4 / m5).
Note on the folder name: the `docs/mN/` folders are the per-milestone archive —
this one holds the m4 (Authoring Polish) milestone.
Supersedes: `docs/m3/spec.md` (archived — the Assisted Authoring plan as it stood
while m3 was active; **m3 shipped**). The m2 spec (symbol intelligence,
Cloud Storage) is under `docs/m2/`; the m1 milestone under `docs/m1/`.

## Context

HotRulez is a JetBrains IDE plugin for Firebase Security Rules — both Cloud
Firestore (`service cloud.firestore`) and Cloud Storage (`service
firebase.storage`) `.rules` files. Through the Assisted Authoring arc it has
grown from a passive language into an active assistant:

- **m1** — passive language: file recognition, syntax highlighting, a
  Grammar-Kit/JFlex parser and typed PSI, a PSI-aware formatter, structural
  diagnostics (an always-on annotator plus two configurable inspections), and
  editor polish (icon, color settings page, brace matcher, quote handler,
  commenter).
- **m2** — symbol intelligence: a PSI reference/resolve layer honoring
  Firebase Rules scoping and path-variable shadowing, plus go-to-definition,
  find-usages, rename, and scope-aware completion.
- **m2** — Cloud Storage as a sibling dialect, detected from the
  `service` declaration and modeled as data in `RulesService`.
- **m3 — Actionable Diagnostics** *(shipped)*: 12 quick-fixes (as
  `ModCommand` `PsiUpdateModCommandAction`s serving both inspections and the
  annotator) for the structural diagnostics, plus a new
  `FirebaseRulesSymbolInspection` that flags undefined references and unused
  functions / `let`s / parameters through the shipped resolver.

The plugin now **reads** a `.rules` file, **understands** its symbols, and
**acts** on structural problems. What it does not yet do is help a developer
**see and move through** a file's structure the way a first-class language plugin
does: there is no structure view, no folding, no hover documentation, and no
parameter hints. Every input for those features already exists — the typed PSI
(service / match / function / allow tree), the `RulesService` dialect profile,
the `FirebaseRulesBuiltins` vocabulary, and the resolver — but none of it is yet
surfaced as a navigable or explanatory view.

m4 closes that gap. Where m3 was *"the IDE fixes your rules,"* m4 is *"the IDE
shows you your rules."*

## Thesis

**m4 adds the read-only authoring surfaces a mature language plugin is expected
to have — structure view, code folding, quick documentation, and parameter
info — each a projection of PSI and data the plugin already owns, adding no new
semantics and relaxing no m1/m2/m3 non-goal.** Three of the four features are
pure views over existing structures; the fourth (quick documentation) requires
one genuinely new artifact — a doc-*prose* table — because today's tables carry
symbol *names* but no descriptions.

## Anchors

- **First-principles parity with mature JetBrains language plugins.** The bar is
  "what the Kotlin, Go, and Rust plugins do." A structure view, code folding,
  quick documentation, and parameter info are all table stakes for a first-class
  language, and all are still missing here. Scope is chosen by platform
  convention, not telemetry.
- **Read-only projections, no new semantics.** Structure view, folding, and
  parameter info render PSI/data the plugin already computes. Quick documentation
  adds *prose* but no *inference*: it explains the fixed, doc-sourced vocabulary
  and shows a user symbol's own signature/comment — it never derives a type or a
  value.
- **Hold every prior non-goal.** m4 adds no connection to Firebase, no
  evaluation of authorization, no runtime model, and no type inference. See
  Non-Goals.
- **Doc strings are doc-grounded and structural.** Every description shown on
  hover comes from official Firebase reference docs (or is the user's own
  comment). Anything not directly confirmed is tagged `UNCONFIRMED` with a TODO
  tied to its source, matching the `.bnf`/`RulesService` convention.

## Non-Goals

m4 inherits every m1/m2/m3 non-goal unchanged. The plugin must not:

- Evaluate whether a request is allowed or denied, or infer authorization or
  security quality.
- Connect to Firebase projects, emulators, the rules-test SDK, credentials, or
  live data, or run rules tests in-IDE.
- Hard-code Firebase project IDs or environment-specific paths.
- Model the language as JavaScript, JSON, or generic configuration.
- Replace official Firebase tooling for deployment or authorization testing.
- Add web-app frameworks or unrelated UI dependencies.

Additionally, m4-specific non-goals:

- **No type inference (still).** Quick documentation for a member (`request.auth`)
  is looked up in the fixed, per-dialect member table — it is *not* computed from
  a type of the receiver. `request.foo` (an unknown member) gets no doc, and the
  feature never invents a custom `request.auth.token` claim. Parameter info shows
  a *declared* or *fixed* signature; it never infers argument types.
- **Parameter info does not validate arity.** It is a display aid only. Whether a
  call has the wrong number of arguments is a diagnostic concern — and helper-call
  arity is already on m3's *deliberately-no-fix* list. m4 must not turn
  parameter info into a silent arity checker.
- **Structure view and folding are non-authoritative navigational aids.** They
  degrade gracefully on malformed files and never suppress or alter diagnostics.
- **Documentation is not a substitute for the Firebase docs.** Hover prose is a
  short, doc-grounded summary plus an external link; it does not reproduce or
  replace the official reference.

## Documentation Sources

Per the standing ground rule, official Firebase docs are authoritative for
language semantics and current IntelliJ Platform SDK docs (via Context7) are
authoritative for extension points. Re-check the relevant pages before
implementing; do not encode a member, signature, or description the docs do not
confirm; tag anything uncertain `UNCONFIRMED` with a TODO tied to the source.

Firebase semantics load-bearing for m4 (already confirmed for m2/m3; re-confirm
the specific facts the doc table relies on before coding):

- Rules structure and the per-service root match:
  `https://firebase.google.com/docs/firestore/security/rules-structure`
- Rules language (functions, `let`, scoping):
  `https://firebase.google.com/docs/rules/rules-language`
- Conditions, `request`/`resource`, helper calls (`get`/`exists`/…):
  `https://firebase.google.com/docs/firestore/security/rules-conditions`
- `request`/`resource` member reference (the member-table + doc-prose source):
  `https://firebase.google.com/docs/reference/rules/rules.firestore.Request`
- Storage `resource`/`request.resource` metadata:
  `https://firebase.google.com/docs/storage/security/rules-conditions`
  (replaces `https://firebase.google.com/docs/reference/rules/rules.storage`, which
  Firebase retired — it 404s as of 2026-09-08)

IntelliJ Platform SDK extension points for m4 (platform target: **IntelliJ IDEA
2025.2**, `sinceBuild = 252`, Java 21). Confirmed against the current SDK docs on
2026-07-03; re-confirm class/method signatures before coding:

- **Structure view** — `lang.psiStructureViewFactory` →
  `PsiStructureViewFactory` returning a `StructureViewModel`
  (`StructureViewModelBase`) built from `StructureViewTreeElement` nodes.
- **Code folding** — `lang.foldingBuilder` → `FoldingBuilderEx` (implement
  `DumbAware`) emitting `FoldingDescriptor`s.
- **Quick documentation** — `lang.documentationProvider` →
  `AbstractDocumentationProvider`. The SDK docs explicitly note that for
  custom-language development, extending `AbstractDocumentationProvider` via the
  language-scoped `lang.documentationProvider` EP is *generally preferred*; the
  newer 2023.1+ Documentation Target API (`DocumentationTargetProvider` /
  `PsiDocumentationTargetProvider`) is the forward-looking alternative but is not
  required on 2025.2 and is heavier for this use. **Decision: use the classic
  `AbstractDocumentationProvider`**, consistent with the plugin's other `lang.*`
  extension points. Re-evaluate only if a platform deprecation forces it.
- **Parameter info** — `codeInsight.parameterInfo` → `ParameterInfoHandler`.

Prefer extension points over startup code (as every prior milestone did).

## m4 Milestone Detail

Four features. Three are read-only projections of existing PSI/data; the fourth
adds a doc-prose table. Each lists the PSI it consumes (all already generated by
the grammar), the extension point, and the behavior.

### Shared: a doc-prose source (`FirebaseRulesDocs`)

Today's tables give the plugin its *vocabulary* but not its *prose*:
`RulesService.members` / `.globals` / `.bareHelpers` and
`FirebaseRulesBuiltins.OPERATIONS` / `.GLOBALS` / `.TYPE_NAMES` are lists of
**names**. Quick documentation needs a short description per name. Rather than
bloat the name-tables (which are also consumed by completion, the highlighter,
and the resolver), m4 adds a dedicated, doc-sourced prose table:

- New `dev.lezli.hotrulez.documentation.FirebaseRulesDocs` — a static object
  mapping each documentable entity to `(title, summaryHtml, docUrl)`:
  - **`allow` operations** keyed by name (`get`, `list`, `read`, `create`,
    `update`, `delete`, `write`), including the "`list` requires
    `rules_version = '2'`" note and what each granular op expands from
    `read`/`write`.
  - **Built-in globals** keyed by name (`request`, `resource`, and Storage's
    `firestore` cross-service namespace).
  - **Members** keyed by the same whitespace-stripped receiver path
    `RulesService.members` uses (`request.auth`, `request.auth.uid`,
    `request.time`, `resource.data`, Storage `resource.size`, …), split by
    dialect where the two differ.
  - **Path helpers** keyed by name (`get`, `exists`, `getAfter`, `existsAfter`,
    and cross-service `firestore.get` / `firestore.exists`) — one-line purpose +
    the `path` signature.
  - **Type/global namespaces & conversion functions** from
    `FirebaseRulesBuiltins.TYPE_NAMES` / `GLOBALS` (`math`, `timestamp`,
    `duration`, `int`, `string`, `debug`, …) — a brief "what it is" line. Members
    *of* these namespaces (`math.abs`, `timestamp.date`) are **not** enumerated in
    m4 (arity/return unconfirmed as a table) and are tagged `UNCONFIRMED` /
    deferred.
- The table is the **single source of doc prose**, reused by quick documentation
  (below) and available to parameter info for helper summaries. Every entry cites
  its Firebase doc page; unconfirmed prose is tagged `UNCONFIRMED` with a TODO.
- Lookups are keyed off the *same* keys `RulesService` already uses, so the
  vocabulary and its prose cannot silently drift: a member present in the name
  table but missing prose is a visible gap (test-asserted), not a crash.

### Feature 1 — Structure view

- **EP:** `lang.psiStructureViewFactory` → `FirebaseRulesStructureViewFactory`
  (package `dev.lezli.hotrulez.structureview`), returning a
  `FirebaseRulesStructureViewModel` (`StructureViewModelBase`, root element the
  `FirebaseRulesFile`) over `FirebaseRulesStructureViewElement`
  (`StructureViewTreeElement` + `NavigatablePsiElement`).
- **Tree shape** (source order by default): `service` → nested `match` (labeled by
  its path) → `function` / `allow`, with `match`/`function` also recognized at
  file top level (the grammar permits both), and `function`s nested inside `match`
  blocks. Children are computed from the typed PSI:
  - `FirebaseRulesServiceDeclaration` → its `FirebaseRulesBlock` children
    (`FirebaseRulesMatchDeclaration`, `FirebaseRulesAllowStatement`,
    `FirebaseRulesFunctionDeclaration`).
  - `FirebaseRulesMatchDeclaration` → its block's matches / allows / functions.
  - `FirebaseRulesFunctionDeclaration` and `FirebaseRulesAllowStatement` are
    leaves.
- **Presentation:**
  - service → the service name text (`FirebaseRulesServiceDeclaration.serviceName`).
  - match → the match path text (`FirebaseRulesMatchDeclaration.matchPath`).
  - function → `name(param, …)` from the declaration's `FirebaseRulesParameter`
    list (reusing the named-element `name`).
  - allow → the operation list (`FirebaseRulesMethodList` text), e.g.
    `allow read, write`.
  - Icons: reuse platform `AllIcons.Nodes.*` (e.g. a class-like icon for
    service/match containers, a method icon for `function`, a property/field icon
    for `allow`) — no new icon assets. `let`/`return`/path variables are **not**
    surfaced (structure view shows navigable *structure*, not every statement).
- **Behavior:** standard `Sorters.ALPHA_SORTER` offered (source order default);
  `getSuitableClasses` wired so the navigation bar and "select in structure view"
  work. Fully null-safe so a partially-parsed file yields a partial tree, never an
  exception.

### Feature 2 — Code folding

- **EP:** `lang.foldingBuilder` → `FirebaseRulesFoldingBuilder`
  (`FoldingBuilderEx`, `DumbAware`; package `dev.lezli.hotrulez.folding`).
- **Foldable regions** (each a `FoldingDescriptor` over the node's braced range):
  - the `FirebaseRulesBlock` of a `service` and of a `match`,
  - the `FirebaseRulesFunctionBody` of a `function`,
  - `BLOCK_COMMENT` tokens (`/* … */`).
  The fold spans only the braces `{ … }` (not the `service …` / `match /path` head),
  so the service name and match path stay visible on the collapsed line — which is
  exactly the "sensible placeholder" the head already provides.
- **Placeholder text:** `{…}` for braced blocks; `/*…*/` for block comments.
- **Collapsed by default:** nothing (`isCollapsedByDefault` returns `false` for all
  regions) — folding is opt-in, matching how most language plugins ship.
- Guarded against unclosed/malformed blocks: a region is emitted only when the
  block has both braces and a non-empty interior, so an in-progress edit never
  produces a bogus or zero-length fold.

### Feature 3 — Quick documentation

- **EP:** `lang.documentationProvider` → `FirebaseRulesDocumentationProvider`
  (`AbstractDocumentationProvider`; package `dev.lezli.hotrulez.documentation`).
- **Targets and content** (assembled with `DocumentationMarkup` DEFINITION /
  CONTENT sections):
  - **User symbols** (resolved via the shipped resolver / `FirebaseRulesNamedElement`):
    - `function` → signature `function name(params)` + any immediately-preceding
      line/block comment as the doc body (the user's own words — doc-grounded by
      definition), like Kotlin/Java show KDoc/JavaDoc.
    - `parameter` → `parameter 'x' of function f`.
    - `let` → `let x = <expr>` (the binding's own text).
    - path / recursive wildcard → `path variable 'x' captured by match /…`.
  - **Built-in vocabulary** (from `FirebaseRulesDocs`, dialect-aware via
    `RulesService.forElement`):
    - `allow` operation on a `FirebaseRulesMethodList` identifier.
    - built-in global (`request` / `resource` / `firestore`) in
      `reference_expression` position.
    - member on a `FirebaseRulesMemberExpression` — keyed by the receiver path
      (reusing `completion`'s `receiverKey` logic) so `request.auth` and
      `resource.data` resolve to their prose; an unknown member yields no doc (no
      type invention).
    - path helper / cross-service helper on a call callee.
    - type/global namespace or conversion function.
  - **External link:** `getUrlFor` returns the entity's Firebase docs URL from
    `FirebaseRulesDocs`, enabling "open in browser" from the doc popup.
- **Doc-target resolution:** `getCustomDocumentationElement` / `getDocumentationElementForLookupItem`
  so hover works on member identifiers and on completion lookup items, not only on
  fully-resolved references.
- No doc is fabricated: if a name is neither a resolvable user symbol nor a
  tabled built-in, the provider returns `null` and the platform shows nothing.

### Feature 4 — Parameter info

- **EP:** `codeInsight.parameterInfo` → `FirebaseRulesParameterInfoHandler`
  (`ParameterInfoHandler<FirebaseRulesArgumentList, ‹SignatureModel›>`; package
  `dev.lezli.hotrulez.parameterinfo`).
- **Where it triggers:** inside the `FirebaseRulesArgumentList` of a
  `FirebaseRulesCallExpression`. The callee is the call's `expression`:
  - a `reference_expression` resolving to a `FirebaseRulesFunctionDeclaration`
    (a user function) → parameter names from its `FirebaseRulesParameter` list;
  - a bare path helper (`get` / `exists` / `getAfter` / `existsAfter` — from
    `RulesService.bareHelpersFor`) → a single `path` parameter;
  - a `member_expression` whose text is `firestore.get` / `firestore.exists`
    (`RulesService.CROSS_SERVICE_HELPERS`) → a single `path` parameter.
- **UI:** show the signature; **highlight the current parameter** by counting
  commas between `(` and the caret against the parameter list (`updateParameterInfo`
  → `updateUI` with the current index). Helper signatures may append the one-line
  purpose from `FirebaseRulesDocs`.
- **Explicitly out of scope for m4:** global-namespace functions (`math.abs`,
  `timestamp.date`, conversion functions) — their arities/returns are not a
  confirmed table; documented as deferred, not silently omitted. **No arity
  validation** (display only; see Non-Goals).

### Confirmed semantics the features must honor

- **Dialect-awareness:** documentation and parameter info for members/helpers must
  key on `RulesService.forElement(...)` so Firestore vs Storage members, and
  Firestore's bare helpers vs Storage's `firestore.*` helpers, are correct; neutral
  files (no recognized `service`) fall back to the union, exactly as completion
  does today.
- **Scope-based resolution:** the doc provider and parameter-info callee lookup
  resolve user symbols through `FirebaseRulesScopes` / `FirebaseRulesReference`,
  never a textual heuristic — so a forward reference to a function declared later
  still documents and still shows parameters.
- **Members are structurally distinct:** a `member_expression` is not a
  `reference_expression`; member docs come from the fixed table, never from type
  inference — the same boundary m3's symbol inspection relies on.

## Implementation components (m4)

- `dev.lezli.hotrulez.documentation.FirebaseRulesDocs` — the doc-prose table
  (shared source of truth; doc-sourced, `UNCONFIRMED`-tagged where needed).
- `dev.lezli.hotrulez.structureview.FirebaseRulesStructureViewFactory` +
  `FirebaseRulesStructureViewModel` + `FirebaseRulesStructureViewElement`.
- `dev.lezli.hotrulez.folding.FirebaseRulesFoldingBuilder`.
- `dev.lezli.hotrulez.documentation.FirebaseRulesDocumentationProvider`.
- `dev.lezli.hotrulez.parameterinfo.FirebaseRulesParameterInfoHandler`.
- Register all four extension points in `plugin.xml`
  (`lang.psiStructureViewFactory`, `lang.foldingBuilder`,
  `lang.documentationProvider`, `codeInsight.parameterInfo`), each
  `language="FirebaseRules"` where applicable.
- Reuse — do not duplicate — `RulesService`, `FirebaseRulesBuiltins`,
  `FirebaseRulesScopes`, `FirebaseRulesReference`, the named-element PSI, and the
  `receiverKey` member-path logic (extract it from the completion contributor to a
  shared helper if both need it). Regenerate the grammar only if a missing PSI
  accessor forces a narrow `.bnf` change (none anticipated — the needed nodes all
  exist).

## Tests (m4)

- **`FirebaseRulesStructureViewTest`** — build the model for a representative
  Firestore file and a Storage file; assert the tree shape (service → matches →
  functions/allows), the presentation strings (match path, `fn(params)`, allow
  ops), and that a nested/malformed file yields a partial tree without throwing.
- **`FirebaseRulesFoldingTest`** — fixture-based `myFixture.testFolding(...)`
  with `<fold text='{…}'>…</fold>` / `/*…*/` markup over service/match/function
  blocks and a block comment; a case with an unclosed block asserts no bogus fold.
- **`FirebaseRulesDocumentationTest`** — for each target category assert the
  generated HTML contains the expected text: an `allow` operation, a built-in
  global, a Firestore member and a Storage-specific member (dialect-awareness), a
  path helper and `firestore.get`, a user function (signature + preceding
  comment), a parameter, a `let`, and a path variable; assert an unknown member
  and an unresolved name return `null`; assert `getUrlFor` returns the Firebase
  URL.
- **`FirebaseRulesParameterInfoTest`** — user-function call (param names +
  highlighted index as the caret moves across commas), a bare `get(` /
  `exists(` helper, a `firestore.get(` cross-service helper; assert no signature
  for a global-namespace call and graceful behavior in a malformed argument list.
- **Recovery** across all four: a partially malformed file produces no exceptions
  and unrelated blocks are unaffected (the standing "degrades gracefully" bar).
- Doc-table integrity: a test asserts every name in `RulesService.members`
  (both dialects) and every `FirebaseRulesBuiltins.OPERATIONS` entry either has a
  `FirebaseRulesDocs` entry or is explicitly on an `UNCONFIRMED`/deferred list —
  so vocabulary and prose cannot drift apart.
- `./gradlew test` green after the milestone (artifacts are git-ignored; re-run
  before release). Keep `verifyPlugin` green (new EPs are all stable).

## Acceptance (m4)

- The **Structure** tool window shows a `.rules` file's service → match →
  function / allow outline with correct labels, navigation, and alpha-sort, for
  both dialects, degrading gracefully on malformed input.
- Service, match, and function braced blocks and block comments **fold**, with
  the service name / match path staying visible on the collapsed line.
- **Quick documentation** (hover / Ctrl-Q) shows doc-grounded prose + an external
  Firebase link for operations, built-ins, dialect-correct members, and helpers,
  and shows signature/comment for user functions, parameters, `let`s, and path
  variables — and shows nothing (no fabrication) for unknown members or
  unresolved names.
- **Parameter info** (Ctrl-P) shows and highlights the signature for user-function
  calls and fixed-arity path helpers, and shows nothing for the deliberately
  out-of-scope calls — never validating arity.
- All m1/m2/m3 non-goals still hold: nothing connects to Firebase, evaluates
  authorization, or infers a type; every doc string is doc-grounded or the user's
  own text.
- Tests cover all four features (positives, dialect-awareness, and recovery) and
  the doc-table integrity check; implementation follows current JetBrains SDK and
  Firebase docs.
- `README.md`, `AGENTS.md`, and the `plugin.xml` `<description>` feature list are
  updated (description stays text-only — the in-IDE renderer is a limited Swing
  HTML kit, no images).

## m5 — Toward Semantics (direction, not commitment)

Carried forward from the m3 plan. Begin *doc-grounded* expression analysis, still
short of runtime evaluation: flag *obvious* member and type mistakes that the
static Firebase docs make unambiguous — e.g. a member that cannot exist on a
known built-in in the detected dialect, or an operator applied to plainly
incompatible literal types — while never asserting authorization, never inventing
types for user data, and never evaluating a rule. The m4 member/doc tables (now
carrying prose) become an obvious input for a conservative "unknown member on a
known built-in" check. The exact check set will be shaped by what m4 reveals
about false-positive risk; this milestone stays under-specified until then and
may split across releases.

## Future (explicitly not planned)

Emulator / rules-test-SDK integration and any in-IDE authorization evaluation
remain out of scope — they would require revisiting the no-connection,
no-evaluation core principles that define the product.

## Decisions taken while you were away (please confirm)

These were resolved with a recommended default during the `/grill-me` session
because you were away from the keyboard; each is cheaply reversible.

1. **Archive model.** Archived the current spec/tasks wholesale to `docs/m3/`
   (matching how `docs/m1/`, `docs/m2/` hold retired programs and your m2→m3
   "retire + plan" commit), rather than rolling the m3 doc forward in place. The
   m3 spec itself said m4's breakdown was "deferred until m3 ships," so a
   roll-forward was the alternative; the wholesale archive matches your literal
   instruction and the existing folder structure.
2. **New-spec identity.** Titled the new working spec by milestone ("m4 —
   Authoring Polish") and framed it as a continuation of the Assisted Authoring
   arc, rather than minting a standalone new-program identity. m4/m5 genuinely
   belong to Assisted Authoring.
3. **Feature set.** Kept exactly the four features the m3 plan committed to
   (structure view, folding, quick docs, parameter info) — no additions
   (breadcrumbs, live templates, go-to-symbol) and no drops.
4. **Doc provider API.** Classic `AbstractDocumentationProvider` /
   `lang.documentationProvider` over the newer Documentation Target API — the
   SDK's stated preference for custom languages and consistent with the plugin's
   other `lang.*` EPs.
5. **Doc prose lives in a new `FirebaseRulesDocs` table**, not inlined into the
   existing name-tables (which completion/highlighter/resolver share).
6. **Structure view surfaces service/match/function/allow only** — not
   `let`/`return`/path variables (structure, not every statement).
7. **Parameter info scope** = user functions + fixed-arity path helpers only;
   global-namespace functions deferred (`UNCONFIRMED` arity), no arity validation.
8. **Folding** = braced blocks (service/match/function) + block comments, nothing
   collapsed by default, folding only the `{…}` so heads stay visible.
