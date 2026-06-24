# hotrulez

`hotrulez` is a JetBrains IDE plugin for Firebase Cloud Firestore Security
Rules. It makes `.rules` files behave like a dedicated language in the IDE
instead of treating them as JavaScript, JSON, or plain text.

Created by `lezli01` at [lezli01.is-a.dev](https://lezli01.is-a.dev).

## What It Does

Current support includes:

- `.rules` file type recognition.
- Syntax highlighting for Firestore Rules keywords, allow operations, booleans
  and `null`, built-ins and helpers, service names, function declarations and
  calls, path variables, recursive wildcards, paths, comments, strings, numbers,
  operators, and invalid tokens.
- A color settings page (Settings | Editor | Color Scheme | Firestore Rules)
  exposing every highlight category with a live preview.
- Structural parser/PSI support for common Firestore Rules files.
- Automatic formatting for common `rules_version`, `service`, `match`, `allow`,
  `function`, and `return` structure.
- Formatter handling for path wildcards, recursive wildcards, comments, blank
  lines, multiline conditions, and malformed-but-recoverable input. Block-level
  members are separated by a blank line (around function declarations and between
  sibling match blocks) following the Firebase documentation's layout.

Planned support includes fuller grammar coverage, typed PSI expansion, syntax
error reporting, annotators or inspections, and user-facing diagnostics for
invalid Firestore Rules constructs.

## How It Is Built

The project is a Kotlin-based IntelliJ Platform plugin built with Gradle Kotlin
DSL and the IntelliJ Platform Gradle Plugin 2.x.

Important parts of the repository:

```text
build.gradle.kts
settings.gradle.kts
src/main/kotlin/dev/lezli/hotrulez/
  FirestoreRulesLanguage.kt
  FirestoreRulesFileType.kt
  lexer/
  highlighting/
  parser/
  psi/
  formatting/
src/main/resources/META-INF/plugin.xml
src/test/kotlin/dev/lezli/hotrulez/
src/test/testData/formatter/
docs/spec.md
docs/tasks.md
```

The implementation is split by responsibility:

- `lexer/` tokenizes Firestore Rules syntax.
- `highlighting/` maps tokens to IDE text attributes.
- `parser/` provides a recoverable structural parser for formatter-grade PSI.
- `psi/` defines the Firestore Rules PSI file and wrapper elements.
- `formatting/` provides IntelliJ formatter blocks and spacing rules.
- `plugin.xml` registers the file type, syntax highlighter, parser definition,
  and formatter extension points.

## Requirements

- JDK 21.
- The included Gradle wrapper.
- A JetBrains IDE compatible with build `252` or newer.

The Gradle project currently targets IntelliJ IDEA `2025.2.6.2` for plugin
development and tests.

## Build And Test

Run the test suite:

```bash
./gradlew test
```

Build the installable plugin ZIP:

```bash
./gradlew buildPlugin
```

The plugin ZIP is written under:

```text
build/distributions/
```

For CI-style runs, this repository uses:

```bash
./gradlew test --no-daemon
./gradlew buildPlugin --no-daemon
```

## How To Use

Build the plugin ZIP:

```bash
./gradlew buildPlugin
```

Install it in a JetBrains IDE:

1. Open `Settings` or `Preferences`.
2. Go to `Plugins`.
3. Use the gear menu and choose `Install Plugin from Disk...`.
4. Select the ZIP from `build/distributions/`.
5. Restart the IDE if prompted.

After installation:

- Open a `.rules` file to get Firestore Rules file recognition and highlighting.
- Use the IDE's normal reformat action to format Firestore Rules files.
- Keep using Firebase's official tooling for deployment and authorization
  behavior checks.

## Example Firestore Rules File

```rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /cities/{city} {
      allow read, write: if request.auth != null;

      function ownsCity(uid) {
        return resource.data.owner == uid;
      }
    }
  }
}
```

## Project Status

`hotrulez` is under active development. The current implementation focuses on
file recognition, highlighting, structural parsing, and automatic formatting.
Diagnostics and deeper semantic validation are still planned.

See [docs/spec.md](docs/spec.md) for the product and implementation spec, and
[docs/tasks.md](docs/tasks.md) for the task checklist.
