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

- [ ] Choose and document parser tooling, with Grammar-Kit plus JFlex as the
  preferred default unless current docs indicate otherwise.
- [ ] Add grammar source for Firestore Rules files.
- [ ] Add or generate PSI nodes for rules files, version declarations, service
  blocks, match blocks, match paths, path segments, wildcards, recursive
  wildcards, allow statements, operation lists, functions, parameters, return
  statements, expressions, calls, and member access.
- [ ] Register parser support through `com.intellij.lang.parserDefinition`.
- [ ] Ensure malformed statements recover without preventing the rest of the file
  from parsing.
- [ ] Add parser tests for a minimal valid file.
- [ ] Add parser tests for nested match blocks.
- [ ] Add parser tests for every supported allow operation.
- [ ] Add parser tests for function declarations and helper calls.
- [ ] Add parser tests for recursive wildcard paths.
- [ ] Add parser tests for representative malformed input.

## Milestone 3: Formatter

- [ ] Implement IntelliJ formatter support for Firestore Rules.
- [ ] Register formatter support through `com.intellij.lang.formatter`.
- [ ] Indent nested `service`, `match`, `allow`, and `function` blocks.
- [ ] Preserve semicolon-terminated statements.
- [ ] Keep short `allow read, write: if condition;` statements on one line when
  appropriate.
- [ ] Preserve user-intended multiline conditions.
- [ ] Avoid reordering operations or rewriting expressions.
- [ ] Add formatter tests for compact input.
- [ ] Add formatter tests for nested blocks.
- [ ] Add formatter tests for multiline conditions.

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
- [ ] Add a color settings page if the highlighter exposes meaningful categories.
- [ ] Check brace, quote, and comment handling against JetBrains conventions.
- [ ] Replace the repeated-title `README.md` with a concise project README.
- [ ] Document implemented features and current limitations in `README.md`.
- [x] Update `AGENTS.md` with real build and test commands once they exist.

## Release-Quality Acceptance

- [ ] `.rules` file recognition works.
- [ ] Syntax highlighting is stable and useful.
- [ ] Parser errors surface in the editor.
- [ ] Formatting handles common Firestore Rules structure.
- [ ] Diagnostics catch documented invalid constructs.
- [ ] Tests cover lexer, parser, formatter, and diagnostics fixtures.
- [ ] Implementation choices follow current official JetBrains and Firebase docs.
