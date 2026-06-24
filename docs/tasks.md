# hotrulez Tasks

Status: draft
Last updated: 2026-06-23
Source: `docs/spec.md`

## Ground Rules

- [x] Re-check official JetBrains or Firebase docs through Context7 before
  changing IntelliJ Platform APIs, Gradle setup, generated parser tooling, or
  Firebase Rules semantics.
- [x] Keep implementation scoped to a JetBrains plugin for Cloud Firestore
  Security Rules.
- [x] Do not add Firebase credentials, project IDs, emulator state, web app
  frameworks, or runtime authorization evaluation.
- [x] Add focused tests with each lexer, parser, formatter, or diagnostics
  change.
- [x] Update `AGENTS.md` with concrete commands only after those Gradle tasks or
  scripts exist.

## Milestone 0: Project Scaffold

- [x] Create `settings.gradle.kts`.
- [x] Create `build.gradle.kts` using Gradle Kotlin DSL.
- [x] Configure the IntelliJ Platform Gradle Plugin 2.x.
- [x] Configure Kotlin or Java source compatibility for the selected IDE target.
- [x] Add IntelliJ Platform test framework dependencies.
- [x] Create `src/main/kotlin/dev/lezli/hotrulez`.
- [x] Create `src/main/resources/META-INF/plugin.xml`.
- [x] Create `src/test/kotlin/dev/lezli/hotrulez`.
- [x] Create `src/test/testData`.
- [x] Verify the scaffold with the narrowest available Gradle check once the
  wrapper or Gradle invocation exists.

## Milestone 1: File Recognition and Highlighting

- [x] Add `FirestoreRulesLanguage`.
- [x] Add `FirestoreRulesFileType`.
- [x] Register `.rules` files through the `com.intellij.fileType` extension
  point.
- [x] Add a lexer for initial highlighting.
- [x] Tokenize whitespace, comments, keywords, identifiers, strings, numbers,
  path punctuation, structural punctuation, operators, and invalid characters.
- [x] Recognize Firestore Rules keywords:
  `rules_version`, `service`, `match`, `allow`, `if`, `function`, `return`,
  `true`, `false`, and `null`.
- [x] Recognize operations:
  `get`, `list`, `read`, `create`, `update`, `delete`, and `write`.
- [x] Recognize Firestore built-ins and helpers:
  `request`, `resource`, `exists`, `existsAfter`, `get`, and `getAfter`.
- [x] Add syntax highlighter classes under a highlighting package.
- [x] Register the syntax highlighter through
  `com.intellij.lang.syntaxHighlighterFactory`.
- [x] Add highlighting coverage for a minimal valid Firestore Rules v2 file.
- [x] Add highlighting coverage for a nested `service` and `match` example.
- [x] Add highlighting coverage for invalid tokens.

## Milestone 2: Parser and PSI

- [x] Choose and document parser tooling, with Grammar-Kit plus JFlex as the
  preferred default unless current docs indicate otherwise.
- [x] Identify the PSI and token boundaries the formatter needs before starting
  formatter implementation.
- [ ] Add grammar source for Firestore Rules files.
- [ ] Add or generate PSI nodes for rules files, version declarations, service
  blocks, match blocks, match paths, path segments, wildcards, recursive
  wildcards, allow statements, operation lists, functions, parameters, return
  statements, expressions, calls, and member access.
- [x] Ensure PSI exposes braced block boundaries for `service`, `match`, and
  `function` constructs.
- [x] Ensure PSI exposes semicolon-terminated statements, including
  `rules_version`, `allow`, and `return`.
- [x] Ensure PSI exposes comments so the formatter can preserve and indent them
  without moving them across code.
- [x] Register parser support through `com.intellij.lang.parserDefinition`.
- [x] Ensure malformed statements recover without preventing the rest of the file
  from parsing.
- [x] Add parser tests for a minimal valid file.
- [x] Add parser tests for nested match blocks.
- [x] Add parser tests for every supported allow operation.
- [x] Add parser tests for function declarations and helper calls.
- [x] Add parser tests for recursive wildcard paths.
- [x] Add parser tests for representative malformed input.

## Milestone 3: Formatter

- [x] Re-check current JetBrains formatter docs through Context7 before changing
  formatter APIs or `plugin.xml` registration.
- [x] Add `dev.lezli.hotrulez.formatting` package.
- [x] Add `FirestoreRulesFormattingModelBuilder`.
- [x] Add a recursive `FirestoreRulesBlock` implementation backed by PSI/AST
  nodes.
- [x] Add formatter token sets for braces, semicolon-terminated statements,
  comma-separated lists, path punctuation, expression operators, comments, and
  whitespace-sensitive error recovery.
- [x] Add spacing rules for `rules_version = '2';`.
- [x] Add spacing rules for `service cloud.firestore {`.
- [x] Add spacing rules for `match /path/{wildcard} {`, including no spaces
  around path separators or inside path wildcards.
- [x] Add spacing rules for recursive wildcards such as `{document=**}`.
- [x] Add spacing rules for `allow read, write: if condition;`.
- [x] Add spacing rules for function declarations, calls, and comma-separated
  parameter or argument lists.
- [x] Add spacing rules for member access such as `request.auth.uid` without
  spaces around dots.
- [x] Add expression operator spacing for parsed binary, logical, equality,
  relational, and membership operators.
- [x] Keep unary operators attached to their operands.
- [x] Indent nested `service`, `match`, and `function` blocks by one level.
- [x] Indent `allow` and `return` statements relative to their containing block.
- [x] Align closing braces with their opening declaration.
- [x] Implement child attributes so pressing Enter inside braced blocks chooses
  the expected indentation.
- [x] Preserve semicolon-terminated statements.
- [x] Keep short `allow read, write: if condition;` statements on one line when
  appropriate.
- [x] Preserve user-intended multiline conditions.
- [x] Preserve line and block comments and indent them with their containing
  block.
- [x] Preserve intentional blank lines subject to normal IDE code style settings.
- [x] Avoid reordering operations or rewriting expressions.
- [x] Avoid formatting across unrecoverable syntax errors; leave unknown text
  unchanged while still formatting known surrounding blocks.
- [x] Register formatter support through `com.intellij.lang.formatter`.
- [x] Add formatter test infrastructure using `CodeStyleManager.reformatText`.
- [x] Add formatter fixtures under `src/test/testData/formatter`.
- [x] Add formatter tests for compact input to expected formatted output.
- [x] Add formatter tests for nested blocks.
- [x] Add formatter tests for multiline conditions.
- [x] Add formatter tests for comments inside and between blocks.
- [x] Add formatter tests for recursive wildcards and path variables.
- [x] Add formatter recovery tests for malformed but partially parseable input.
- [x] Run `./gradlew test` after formatter implementation.

## Milestone 4: Diagnostics

- [ ] Decide whether each diagnostic belongs in an annotator or inspection.
- [ ] Add diagnostic for missing `rules_version = '2';`.
- [ ] Add diagnostic for `rules_version = '2';` appearing after the service
  declaration.
- [ ] Add diagnostic for missing `service cloud.firestore`.
- [ ] Add diagnostic for unsupported service names in Firestore Rules files.
- [ ] Add diagnostic for missing root
  `match /databases/{database}/documents`.
- [ ] Add diagnostic for invalid allow operation names.
- [ ] Add diagnostic for empty allow operation lists.
- [ ] Add diagnostic for missing required `if` conditions.
- [ ] Add diagnostic for malformed match paths.
- [ ] Add diagnostic for recursive wildcard usage inconsistent with
  `rules_version = '2';`.
- [ ] Add diagnostic for function declarations missing return expressions.
- [ ] Add diagnostic for duplicate function parameter names.
- [ ] Add diagnostic for documented helper-call arity errors after confirming
  arity in official Firebase docs.
- [ ] Add tests for every diagnostic.
- [ ] Review diagnostic wording to ensure it does not claim runtime security or
  authorization correctness.

## Milestone 5: IDE Polish and Docs

- [ ] Add a file icon if it improves IDE polish.
- [x] Add a color settings page if the highlighter exposes meaningful categories.
- [ ] Check brace, quote, and comment handling against JetBrains conventions.
- [ ] Replace the repeated-title `README.md` with a concise project README.
- [ ] Document implemented features and current limitations in `README.md`.
- [x] Update `AGENTS.md` with real build and test commands once they exist.

## Release-Quality Acceptance

- [ ] `.rules` file recognition works.
- [ ] Syntax highlighting is stable and useful.
- [ ] Parser errors surface in the editor.
- [ ] Automatic formatting handles common Firestore Rules structure through
  IntelliJ formatter APIs.
- [ ] Formatting preserves comments, multiline conditions, path wildcard syntax,
  and Firestore Rules semantics.
- [ ] Diagnostics catch documented invalid constructs.
- [ ] Tests cover lexer, parser, formatter, and diagnostics fixtures.
- [ ] Implementation choices follow current official JetBrains and Firebase docs.
