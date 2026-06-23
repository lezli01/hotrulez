# hotrulez Project Spec

Status: draft
Last updated: 2026-06-23

## Purpose

`hotrulez` is a JetBrains IDE plugin for Firebase Cloud Firestore Security
Rules. Its job is to make `.rules` files behave like a first-class language in
JetBrains IDEs: recognized file type, syntax highlighting, parsing, formatting,
and explicit IDE diagnostics for Firestore Rules structure and expressions.

The plugin must treat Firestore Rules as their own language. It must not model
them as JavaScript, JSON, or a generic configuration format.

## Decisions

The initial project direction is:

- Full target support is expected: syntax highlighting, parser, formatter,
  diagnostics, and focused tests.
- This document describes both the current repository state and the target
  implementation.
- The spec should be detailed enough for another agent or engineer to scaffold
  and implement from it with minimal follow-up.
- Firebase documentation is authoritative for language semantics. The plugin can
  provide conservative static diagnostics, but it must not invent behavior that
  differs from official Firebase Rules semantics.
- The first user-facing milestone is file recognition plus syntax highlighting
  when opening a `.rules` file.

## Documentation Sources

Current documentation was checked through Context7 on 2026-06-23:

- `/websites/plugins_jetbrains_intellij` for IntelliJ Platform Plugin SDK
  guidance on file type registration, syntax highlighter registration, custom
  language support, formatter registration, Gradle plugin 2.x setup, and tests.
- `/llmstxt/firebase_google_llms_txt` for official Firebase Cloud Firestore
  Security Rules syntax and semantics including `rules_version = '2';`,
  `service cloud.firestore`, `match` blocks, recursive wildcards, `allow`
  methods, `request`, `resource`, and helper calls such as `exists()`,
  `existsAfter()`, `get()`, and `getAfter()`.

Before changing JetBrains Platform APIs, Gradle plugin setup, generated parser
tooling, or Firebase Rules semantics, re-check the relevant official docs
through Context7.

## Current Repository State

The repository is currently a very small scaffold:

- `AGENTS.md` defines the project identity, desired plugin shape, Firebase Rules
  notes, development rules, and verification expectations.
- `README.md` only contains repeated project title lines.
- There is no Gradle project yet.
- There is no `src/main` or `src/test` tree yet.
- There is no `plugin.xml`, lexer, parser, PSI, syntax highlighter, formatter,
  annotator, inspection, or test suite yet.
- CodeGraph is configured for the workspace but currently has no indexed source
  files for this repo.

This means the next implementation step is project scaffolding, not refactoring.

## Product Goals

The plugin should support these user workflows:

1. A user opens a `.rules` file and the IDE recognizes it as Firestore Security
   Rules.
2. The file is syntax highlighted with meaningful colors for keywords, paths,
   wildcards, operations, comments, strings, numbers, operators, function names,
   variables, built-ins, and invalid tokens.
3. The IDE parses the file and reports syntax errors in the editor.
4. The formatter produces stable indentation for nested `service`, `match`,
   `allow`, and `function` blocks.
5. Diagnostics explain invalid or suspicious Firestore Rules constructs without
   pretending to fully evaluate authorization behavior.

## Non-Goals

The first implementation must not:

- Evaluate whether a request is allowed or denied.
- Connect to Firebase projects, emulators, credentials, or live Firestore data.
- Hard-code Firebase project IDs or environment-specific paths.
- Support Cloud Storage Rules as if they were Firestore Rules.
- Replace official Firebase tools for deployment or authorization testing.
- Add web app frameworks or unrelated UI dependencies.

## Supported Language Scope

The target language is Cloud Firestore Security Rules, centered on modern rules
files that start with:

```rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /cities/{city} {
      allow read: if true;
    }
  }
}
```

Core syntax to support:

- `rules_version = '2';` at the top of modern rules files.
- `service cloud.firestore { ... }`.
- `match` blocks with absolute path patterns.
- Nested `match` blocks.
- Path variables such as `{database}` and `{city}`.
- Version 2 recursive wildcards such as `{document=**}`, including placement
  anywhere in a match path where Firebase Rules v2 permits it.
- `allow` statements using one or more operations.
- Operations: `get`, `list`, `read`, `create`, `update`, `delete`, and `write`.
- `if` conditions after `allow` declarations.
- `function` declarations with parameters and return expressions.
- Built-in variables including `request`, `resource`, and path wildcard names.
- Firestore helper calls including `exists()`, `existsAfter()`, `get()`, and
  `getAfter()`.

Expression support should follow official Firebase Rules language semantics. The
parser should be designed to grow without treating the language as JavaScript.

Important expression categories include:

- Boolean, string, number, null, list, and map literals where officially
  supported.
- Field access and chained member access, such as `request.auth.uid`.
- Method and function calls.
- Path expressions and interpolated path variables where officially supported.
- Unary, binary, logical, equality, relational, and membership operators where
  officially supported.
- Parenthesized expressions.

If a language construct is not confirmed in Firebase docs, do not add it as a
diagnostic rule or formatter assumption. Parse cautiously and leave TODOs tied to
the official source that needs verification.

## IntelliJ Plugin Shape

Use normal IntelliJ Platform plugin conventions.

Preferred project layout once scaffolded:

```text
settings.gradle.kts
build.gradle.kts
src/main/kotlin/dev/lezli/hotrulez/
  FirestoreRulesLanguage.kt
  FirestoreRulesFileType.kt
  FirestoreRulesIcons.kt
  lexer/
  parser/
  psi/
  highlighting/
  formatting/
  diagnostics/
src/main/resources/META-INF/plugin.xml
src/main/resources/icons/
src/test/kotlin/dev/lezli/hotrulez/
src/test/testData/
```

The package name `dev.lezli.hotrulez` is the working default. Change it only
deliberately and consistently across source, tests, and plugin metadata.

### Build Setup

Use Gradle Kotlin DSL and the IntelliJ Platform Gradle Plugin 2.x. The official
JetBrains docs currently show plugin setup through:

- `org.jetbrains.intellij.platform`
- `org.jetbrains.kotlin.jvm` if Kotlin is used
- IntelliJ Platform repositories through the Gradle plugin
- an `intellijPlatform` dependency block selecting the target IDE
- IntelliJ Platform test framework dependencies
- Java/Kotlin JVM compatibility aligned with the selected IDE target

Do not document concrete build commands in this spec until the Gradle wrapper and
tasks exist in the repository.

### Plugin Metadata

Keep plugin metadata in:

```text
src/main/resources/META-INF/plugin.xml
```

Expected extension points:

- `com.intellij.fileType` for registering the Firestore Rules file type.
- `com.intellij.lang.syntaxHighlighterFactory` for syntax highlighting.
- `com.intellij.lang.parserDefinition` after parser/PSI support exists.
- `com.intellij.lang.formatter` after formatter support exists.
- Annotator or inspection extension points for diagnostics that cannot be
  expressed by grammar alone.

Use extension points instead of startup code wherever the IntelliJ Platform
provides a direct registration mechanism.

## Implementation Components

### Language and File Type

Create a singleton language object for Firestore Rules and a language file type
for `.rules` files.

File type behavior:

- Name: `Firestore Rules`.
- Description: `Firebase Cloud Firestore Security Rules`.
- Default extension: `rules`.
- File type recognition must support the first milestone without parser support.
- A dedicated icon is optional for the first milestone but should be added before
  release polish.

### Lexer

The lexer should recognize:

- Whitespace and line terminators.
- Line comments and block comments if officially supported.
- Keywords: `rules_version`, `service`, `match`, `allow`, `if`, `function`,
  `return`, `true`, `false`, `null`.
- Firestore service identifier pieces, including `cloud` and `firestore`.
- Operations: `get`, `list`, `read`, `create`, `update`, `delete`, `write`.
- Identifiers.
- String literals.
- Number literals.
- Path punctuation: `/`, `{`, `}`, `=`, `**`.
- Structural punctuation: `{`, `}`, `(`, `)`, `[`, `]`, `,`, `.`, `:`, `;`.
- Operators used by Firebase Rules expressions.
- Bad characters as explicit invalid tokens.

Prefer JFlex-compatible lexer generation if the project adopts Grammar-Kit for
parser generation. If generated files are committed, document the generation
source and command next to the generated output.

### Parser and PSI

Parser support should introduce clear PSI nodes for:

- Rules file.
- Rules version declaration.
- Service declaration.
- Match block.
- Match path.
- Path segment.
- Path wildcard.
- Recursive wildcard.
- Allow statement.
- Operation list.
- Function declaration.
- Parameter list.
- Return statement.
- Expressions.
- Function or helper calls.
- Member access.

The grammar should prioritize useful IDE recovery. A malformed `allow` statement
inside a `match` block should not prevent the rest of the file from parsing.

Parser implementation options:

- Prefer Grammar-Kit plus JFlex if that matches current JetBrains guidance and
  keeps generated PSI maintainable.
- Keep generated parser/PSI separate from handwritten support classes.
- Do not hand-roll a large parser unless generated tooling proves unsuitable.

### Syntax Highlighting

The first milestone is complete when syntax highlighting works for representative
Firestore Rules files.

Highlight categories:

- Keywords.
- Service names.
- Match paths.
- Path variables.
- Recursive wildcards.
- Allow operations.
- Built-in variables.
- Firestore helper functions.
- Function declarations and calls.
- Strings.
- Numbers.
- Booleans and null.
- Operators and punctuation.
- Comments.
- Invalid tokens.

Register the highlighter through `plugin.xml` using the IntelliJ syntax
highlighter factory extension point.

### Formatter

Formatter support should produce stable output for common Firestore Rules files.

Formatting rules:

- Indent nested blocks by one level.
- Put block bodies on their own indentation level.
- Preserve semicolon-terminated statements.
- Keep `allow read, write: if condition;` on one line when it fits.
- Allow multiline conditions without collapsing user-intended line breaks.
- Do not aggressively reorder operations or rewrite expressions.
- Do not change semantics.

Formatter implementation should use IntelliJ formatting APIs and be registered
with the language formatter extension point.

### Diagnostics

Diagnostics should be explicit, user-facing, and conservative. They should point
to the smallest useful source range and explain what rule is being violated.

Initial diagnostics:

- Missing `rules_version = '2';` in a modern Firestore rules file.
- `rules_version = '2';` not placed before the service declaration.
- Missing `service cloud.firestore` block.
- Unsupported service name in a Firestore Rules file.
- Missing root `match /databases/{database}/documents` block.
- Invalid `allow` operation name.
- Empty operation list in an `allow` statement.
- Missing `if` condition in an `allow` statement when Firebase syntax requires
  one.
- Malformed match path.
- Recursive wildcard usage inconsistent with `rules_version = '2';`.
- Function declaration missing a return expression.
- Duplicate function parameter names.
- Obvious wrong arity for documented Firestore helper calls, if the docs confirm
  the arity.

Diagnostics must not claim that a rule is secure or insecure based on incomplete
static evaluation. For example, the plugin may flag invalid syntax, unknown
operations, or unsupported service shape, but it should not declare that a
particular authorization policy is correct without runtime context.

### Tests

Add focused tests whenever implementing lexer, parser, formatter, or diagnostics.

Recommended test fixtures:

- Minimal valid Firestore Rules v2 file.
- Nested match blocks.
- Allow statements with every supported operation.
- Function declaration and call.
- Helper calls: `exists`, `existsAfter`, `get`, `getAfter`.
- Recursive wildcard path examples.
- Invalid operation name.
- Missing rules version.
- Malformed match path.
- Formatter fixture for nested service, match, allow, and function blocks.

Use `src/test/testData` for fixtures and keep tests narrow. Do not add broad
release verification until the project has a real Gradle setup.

## Milestones

### Milestone 0: Project Scaffold

Create the Gradle Kotlin DSL project structure and plugin metadata.

Done when:

- The repository has a valid Gradle project.
- Plugin metadata exists under `src/main/resources/META-INF/plugin.xml`.
- Kotlin or Java source lives under the expected `src/main` tree.
- The project has a minimal test source tree.
- No unrelated app frameworks or dependencies were added.

### Milestone 1: File Recognition and Highlighting

This is the first user-facing workflow.

Done when:

- `.rules` files are recognized as Firestore Rules files.
- The language and file type are registered through extension points.
- Representative Firestore Rules files receive syntax highlighting.
- Bad characters receive an invalid-token highlight.
- Highlighting tests cover a minimal valid file and a representative nested file.

### Milestone 2: Parser and PSI

Done when:

- The plugin parses valid Firestore Rules files into PSI.
- Syntax errors appear without breaking the rest of the file.
- PSI nodes exist for version declarations, service blocks, match blocks, allow
  statements, functions, paths, wildcards, and expressions.
- Parser tests cover valid and invalid representative files.

### Milestone 3: Formatter

Done when:

- The formatter handles nested `service`, `match`, `allow`, and `function`
  blocks.
- Formatting preserves semantics and avoids expression rewrites.
- Formatter tests cover compact, nested, and multiline examples.

### Milestone 4: Diagnostics

Done when:

- Structural and semantic checks from the diagnostics section are implemented.
- Diagnostics use official Firebase semantics.
- Inspection or annotator tests cover each diagnostic.
- Diagnostics avoid runtime authorization claims.

### Milestone 5: IDE Polish

Done when:

- File icon and color settings page are present if appropriate.
- Comments, braces, and quote handling feel consistent with JetBrains language
  support conventions.
- README explains what the plugin does and what is currently implemented.
- AGENTS.md is updated with concrete build and test commands only after those
  commands exist.

## Acceptance Criteria

The project is successful when a developer can open a Firestore `.rules` file in
a JetBrains IDE and get meaningful language support without leaving the editor.

For the first release-quality target:

- File recognition works for `.rules`.
- Syntax highlighting is stable and useful.
- Parser errors are surfaced in the editor.
- Formatting handles common Firestore Rules structure.
- Diagnostics catch documented invalid constructs.
- Tests cover lexer, parser, formatter, and diagnostics for representative
  Firestore Rules examples.
- The implementation follows official JetBrains and Firebase documentation.
