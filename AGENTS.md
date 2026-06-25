# AGENTS.md

## Project Identity

`hotrulez` is a JetBrains IDE plugin for Firebase Cloud Firestore Security Rules.

The plugin should provide:

- Syntax highlighting for Firestore Rules files.
- Formatting for Firestore Rules syntax.
- Validation and IDE diagnostics for Firestore Rules structure and expressions.

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
  and file type code separated by responsibility.

## Firestore Rules Language Notes

The implementation should treat Firestore Rules as their own language, not as
JavaScript or JSON.

Core syntax to preserve and validate includes:

- `rules_version = '2';` at the top of modern rules files.
- `service cloud.firestore { ... }`.
- `match /databases/{database}/documents { ... }`.
- Nested `match` blocks with path variables and recursive wildcards.
- `allow` statements for operations such as `read`, `write`, `get`, `list`,
  `create`, `update`, and `delete`.
- `function` declarations and `return` expressions.
- Built-in variables including `request`, `resource`, and path wildcard names.
- Firestore rules helper calls including `exists()`, `existsAfter()`, `get()`,
  and `getAfter()`.

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
- Annotation or inspection tests for invalid Firestore Rules constructs.
- Gradle plugin verification tasks before release-oriented changes.

Update this file when the project gains concrete build commands, test commands,
or a different implementation stack.

## Current Commands

The project now has a Gradle wrapper. Use the narrowest useful check for the
work being changed:

- `./gradlew test` for the current lexer, syntax highlighting, file type,
  parser, and formatter tests.
- `./gradlew generateFirestoreParser generateFirestoreLexer` to regenerate the
  parser/PSI and lexer from the grammar. These run automatically before
  `compileKotlin`/`compileJava`, so a normal build/test already regenerates.

The Firestore Rules grammar source lives in `src/main/grammar/`:

- `FirestoreRules.bnf` — Grammar-Kit grammar (parser + typed PSI). It uses the
  operator-precedence engine for expressions.
- `FirestoreRules.flex` — JFlex lexer used by the generated parser (separate from
  the coarse highlighting lexer in `lexer/FirestoreRulesLexer.kt`).

Generated sources are written to `build/generated/sources/grammarkit` (not
committed). The grammar was synthesised from the nicbytes and grimsteel
tree-sitter Firestore grammars and reconciled against official Firebase docs.
