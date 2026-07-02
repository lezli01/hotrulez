# AGENTS.md

## Project Identity

`hotrulez` is a JetBrains IDE plugin for **Firebase Security Rules** — both Cloud
Firestore (`service cloud.firestore`) and Cloud Storage (`service firebase.storage`)
`.rules` files. The two share one rules language; the dialect is detected from the
file's `service` declaration and modeled as data in `references/RulesService`.

The plugin should provide:

- Syntax highlighting for Firebase Rules files.
- Formatting for Firebase Rules syntax.
- Validation and IDE diagnostics for Firebase Rules structure and expressions.
- Symbol intelligence: go-to-definition, find usages, rename refactoring, and
  scope-aware code completion for functions, parameters, `let` bindings, and path
  variables (shipped in v2 / 0.5).

## Documentation Workflow

Use Context7 for current documentation before changing JetBrains Platform APIs,
Gradle plugin setup, or Firebase Rules semantics. Do not rely on memory for these
APIs because both JetBrains plugin tooling and Firebase documentation change over
time.

Documentation checked for this file on 2026-06-23:

- `/websites/plugins_jetbrains_intellij` for IntelliJ Platform Plugin SDK topics
  including custom language support, syntax highlighters, formatter registration,
  annotators/inspections, `plugin.xml` extension points, and the IntelliJ Platform
  Gradle Plugin 2.x conventions.
- `/llmstxt/firebase_google_llms_txt` for Firebase Cloud Firestore Security Rules
  syntax, `rules_version = '2';`, `service cloud.firestore`, `match` blocks,
  wildcards, functions, request/resource variables, and Firestore rules APIs such
  as `exists`, `existsAfter`, `get`, and `getAfter`.

When adding or changing implementation, prefer official JetBrains and Firebase
documentation retrieved through Context7 over third-party examples.

## Expected Plugin Shape

This project should follow normal IntelliJ Platform plugin conventions:

- Prefer Gradle Kotlin DSL and the IntelliJ Platform Gradle Plugin 2.x when the
  project is scaffolded.
- Keep plugin metadata in `src/main/resources/META-INF/plugin.xml`.
- Keep Kotlin or Java source under `src/main/kotlin` or `src/main/java`.
- Register language support through IntelliJ extension points rather than custom
  startup code where an extension point exists.
- Keep lexer, parser, PSI, syntax highlighter, formatter, annotator, inspection,
  editor support (brace matcher, quote handler, commenter), and file type code
  separated by responsibility.

## Firebase Rules Language Notes

The implementation should treat Firebase Rules as their own language, not as
JavaScript or JSON.

Core syntax to preserve and validate includes:

- `rules_version = '2';` at the top of modern rules files.
- `service cloud.firestore { ... }` (Cloud Firestore) or
  `service firebase.storage { ... }` (Cloud Storage); detected from this declaration.
- The conventional root match per service: `match /databases/{database}/documents`
  (Firestore) or `match /b/{bucket}/o` (Storage).
- Nested `match` blocks with path variables and recursive wildcards.
- `allow` statements for operations such as `read`, `write`, `get`, `list`,
  `create`, `update`, and `delete` (the same operation set in both services).
- `function` declarations and `return` expressions.
- Built-in variables including `request`, `resource`, and path wildcard names. The
  `request`/`resource` member tables are **service-aware**: Firestore exposes
  `resource.data`/`id`/`__name__`; Cloud Storage exposes object metadata
  (`resource.size`, `contentType`, `metadata`, …).
- Path helpers: Cloud Firestore's bare `exists()`/`existsAfter()`/`get()`/
  `getAfter()`, and Cloud Storage's cross-service `firestore.get()`/
  `firestore.exists()`. New service-specific vocabulary belongs in `RulesService`.

Validation should be explicit and user-facing through IDE diagnostics. Prefer
annotators or inspections for semantic checks that cannot be represented by the
grammar alone.

## Development Rules

- Keep edits scoped to plugin behavior and project setup.
- Do not introduce unrelated app frameworks or web UI dependencies.
- Do not hard-code Firebase project IDs, credentials, or emulator state in source.
- Add focused tests when changing lexer, parser, formatter, or validation logic.
- Do not document commands in this file until the corresponding Gradle tasks or
  scripts exist in the repository.
- If adding generated parser or lexer files, document the generation command and
  source grammar next to the generated output.

## Verification Expectations

When the project has been scaffolded, validate changes with the narrowest useful
checks available, such as:

- Lexer/parser tests for representative `.rules` files.
- Formatter tests for nested `service`, `match`, `allow`, and `function` blocks.
- Annotation or inspection tests for invalid Firebase Rules constructs.

## OpenWiki

This repository has documentation located in the `/openwiki` directory.

Start here:
- [OpenWiki quickstart](openwiki/quickstart.md)

OpenWiki includes repository overview, architecture notes, workflows, domain concepts, operations, integrations, testing guidance, and source maps.

When working in this repository, read the OpenWiki quickstart first, then follow its links to the relevant architecture, workflow, domain, operation, and testing notes.

## OpenWiki

This repository has documentation located in the `/openwiki` directory.

Start here:
- [OpenWiki quickstart](openwiki/quickstart.md)

OpenWiki includes repository overview, architecture notes, workflows, domain concepts, operations, integrations, testing guidance, and source maps.

When working in this repository, read the OpenWiki quickstart first, then follow its links to the relevant architecture, workflow, domain, operation, and testing notes.
- Gradle plugin verification tasks before release-oriented changes.

Update this file when the project gains concrete build commands, test commands,
or a different implementation stack.

## Current Commands

The project now has a Gradle wrapper. Use the narrowest useful check for the
work being changed:

- `./gradlew test` for the current lexer, syntax highlighting, file type,
  parser, and formatter tests.
- `./gradlew generateFirebaseParser generateFirebaseLexer` to regenerate the
  parser/PSI and lexer from the grammar. These run automatically before
  `compileKotlin`/`compileJava`, so a normal build/test already regenerates.

The Firebase Rules grammar source lives in `src/main/grammar/`:

- `FirebaseRules.bnf` — Grammar-Kit grammar (parser + typed PSI). It uses the
  operator-precedence engine for expressions.
- `FirebaseRules.flex` — JFlex lexer used by the generated parser (separate from
  the coarse highlighting lexer in `lexer/FirebaseRulesLexer.kt`).

Generated sources are written to `build/generated/sources/grammarkit` (not
committed). The grammar was synthesised from the nicbytes and grimsteel
tree-sitter Firestore grammars and reconciled against official Firebase docs.

Editor conveniences live in `dev.lezli.hotrulez.editor`: `FirebaseRulesBraceMatcher`
(a `PairedBraceMatcher` for `{}`/`()`/`[]`), `FirebaseRulesQuoteHandler` (a
`SimpleTokenSetQuoteHandler` over the `STRING` token), and `FirebaseRulesCommenter`
(`//` line and `/* */` block comments). They key off the highlighting lexer's
`FirebaseRulesTokenTypes`, not the generated PSI tokens, because they run against
the editor highlighter. The `.rules` file icon is an SVG in
`src/main/resources/icons`, loaded through `FirebaseRulesIcons`.

Diagnostics live in `dev.lezli.hotrulez.diagnostics`. Severity decides the home:
always-wrong, grammar-inexpressible ERRORS go in `FirebaseRulesAnnotator`
(always on); configurable WARNINGS go in `FirebaseRulesStructureInspection`
(file shape), `FirebaseRulesUsageInspection` (element-local usage), and
`FirebaseRulesSymbolInspection` (unresolved references and unused
functions/`let`s/parameters, resolved through `FirebaseRulesScopes` so it agrees
with go-to-definition and rename). Quick-fixes live in the `diagnostics.fixes`
subpackage: each is a single `ModCommandAction` (`PsiUpdateModCommandAction`)
attached to the annotator via `AnnotationBuilder.withFix(action.asIntention())`
and to the inspections via `LocalQuickFix.from(action)` (see `asQuickFix`). Keep
diagnostic wording structural — describe syntax/structure, never assert that a
rule is secure or authorizes a request. Confirm Firebase Rules semantics against
official Firebase docs before adding or changing a check (e.g. a condition-less
`allow` is legal, and a recursive wildcard may appear anywhere in a v2 match
path), and add a focused test for each diagnostic in `FirebaseRulesAnnotatorTest`,
`FirebaseRulesInspectionTest`, `FirebaseRulesSymbolInspectionTest`, or
`FirebaseRulesQuickFixTest`.

Symbol intelligence (v2 / 0.5) lives in four packages and rides on a single PSI
resolve layer:

- `references/` is the core. Function declarations, parameters, `let` bindings,
  and path/wildcard variables are `PsiNameIdentifierOwner`s
  (`FirebaseRulesNamedElement`), wired onto the generated PSI through the
  `implements`/`mixin` attributes in `FirebaseRules.bnf`, with shared behavior in
  `FirebaseRulesNamedElementBase`. `FirebaseRulesReferenceExpressionMixin`
  attaches a poly-variant, soft `FirebaseRulesReference` directly on each
  `reference_expression` — an `ASTWrapperPsiElement` does not consult a
  `psi.referenceContributor`, so the reference is hung on the PSI via `mixin`
  rather than a contributor. `FirebaseRulesScopes` implements Firestore's actual
  visibility rules: scope-based function resolution with forward references,
  function-local parameters and post-declaration `let`s, and match-subtree path
  variables with nested shadowing. `FirebaseRulesBuiltins` holds the static,
  doc-sourced member/keyword/operation tables (no type inference; custom claims
  not invented).
- `findusages/` registers a `FindUsagesProvider` with a `DefaultWordsScanner` over
  the parsing lexer.
- `refactoring/` registers a `NamesValidator` (reserved keywords rejected) and a
  `RenamePsiElementProcessor` that scopes path-variable rename to the binding's
  match subtree so shadowing holds.
- `completion/` registers a position-aware `CompletionContributor`.

When a grammar change is needed to expose a PSI accessor or a named-element
interface, make the narrowest `.bnf` change, re-run
`./gradlew generateFirebaseParser generateFirebaseLexer` (a normal build already
does), and keep generated and handwritten code separated. Add focused tests in the
matching `FirebaseRulesResolveTest` / `FirebaseRulesFindUsagesTest` /
`FirebaseRulesRenameTest` / `FirebaseRulesCompletionTest`.
