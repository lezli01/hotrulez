# hotrulez

`hotrulez` is a JetBrains IDE plugin for Firebase Cloud Firestore Security
Rules. It makes `.rules` files behave like a dedicated language in the IDE
instead of treating them as JavaScript, JSON, or plain text.

Created by `lezli01` at [lezli01.is-a.dev](https://lezli01.is-a.dev).

## What It Does

Open any `.rules` file and the IDE treats it as a first-class Firestore Rules
language:

- **File recognition** — every `*.rules` file is recognized as Firestore Rules
  (by extension) and shown with a dedicated file icon.
- **Syntax highlighting** — keywords, allow operations, built-ins, helpers,
  types, service names, function declarations and calls, path variables,
  recursive wildcards, strings, numbers, comments, operators, and invalid
  tokens, all separately colorable.
- **Formatting** — one reformat turns compact or messy rules into clean,
  consistently indented output while preserving your comments, blank lines, and
  multiline conditions.
- **Diagnostics** — 17 checks flag always-wrong constructs as errors and
  surface suspicious-but-legal structure as configurable warnings, with wording
  that stays structural (it never claims a rule is "secure").
- **Editor conveniences** — brace matching, quote auto-closing, and line/block
  comment toggling.

Everything is structural and static. The plugin never evaluates authorization
or talks to Firebase; keep using Firebase's official tooling for deployment and
authorization testing.

## Syntax Highlighting

The highlighter exposes 24 categories, including semantic ones — service name,
function declaration, and path variable — that a fast lexer alone cannot
distinguish and that are resolved by a lightweight annotator. Every category is
customizable with a live preview under **Settings | Editor | Color Scheme |
Firestore Rules**.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Signed-in owners can read and write their own city
    function isOwner(uid) {
      return request.auth != null && resource.data.owner == uid;
    }

    match /cities/{city} {
      allow read, write: if isOwner(request.auth.uid);
    }

    match /scores/{score} {
      allow read: if resource.data.value is int
        ? resource.data.value >= 100
        : resource.data.tier in ['gold', 'silver'];
    }

    match /logs/{document=**} {
      allow read: if false;
    }
  }
}
```

## Formatting

Invoke the IDE's normal **Reformat Code** action on a `.rules` file. The
formatter uses IntelliJ's formatting APIs over the parsed PSI, so it makes
minimal whitespace changes — it never reorders rules or rewrites your
expressions.

**Expanding a compact file.** A whole document collapsed onto one line is
expanded and indented, with a blank line inserted between block members such as
a function declaration:

Before:

```
rules_version='2';service cloud.firestore{match /databases/{database}/documents{match /cities/{city}{allow read,write: if request.auth!=null;function ownsCity(uid){return resource.data.owner==uid;}}}}
```

After:

```
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

**Preserving multiline conditions.** A condition you intentionally split across
lines stays split — the formatter indents the surrounding blocks without
collapsing the `&&` continuation onto one line:

Before:

```
service cloud.firestore{match /databases/{database}/documents{allow read: if request.auth != null
&& request.auth.uid == resource.data.owner;}}
```

After:

```
service cloud.firestore {
  match /databases/{database}/documents {
    allow read: if request.auth != null
    && request.auth.uid == resource.data.owner;
  }
}
```

**Keeping comments in place.** Both line (`//`) and block (`/* */`) comments are
preserved and reindented to align with the block they precede:

Before:

```
service cloud.firestore{/* service body */match /databases/{database}/documents{// city access
match /cities/{city}{allow read: if true;}}}
```

After:

```
service cloud.firestore {
  /* service body */
  match /databases/{database}/documents {
    // city access
    match /cities/{city} {
      allow read: if true;
    }
  }
}
```

Path variables and recursive wildcards (`/cities/{city}/{document=**}`) keep
their tight spacing, and malformed-but-recoverable input is formatted block by
block rather than left untouched.

## Diagnostics

Severity decides the home. Always-wrong, grammar-inexpressible mistakes are
reported as **errors** by an always-on annotator. Configurable **warnings** for
file shape and suspicious usage live in two inspections you can tune under
**Settings | Editor | Inspections | Firestore Rules**. None of the wording
asserts that a rule is secure or that a request would be authorized.

### Errors (always reported)

- **Empty operation list** — `'allow' requires at least one operation, for example 'allow read'.`
- **Unknown operation** — `allow read, fetch: …` → `Unknown Firestore Rules operation 'fetch'. Expected one of: get, list, read, create, update, delete, write.`
- **Duplicate parameter name** — `function f(uid, uid)` → `Duplicate parameter name 'uid'.`
- **Function missing its return** — `Function 'helper' must end with a 'return' statement.`
- **Bare return** — `return;` → `'return' requires an expression.`
- **Too many recursive wildcards** — `A match path may contain at most one recursive wildcard '{name=**}'.`
- **Misplaced recursive wildcard (v1)** — `In rules_version '1', a recursive wildcard '{name=**}' must be the last segment of a match path.`

For example, the unknown operation here is reported on the `fetch` token:

```
match /cities/{city} {
  allow read, fetch: if true;   // error: Unknown Firestore Rules operation 'fetch'.
}
```

### File-structure warnings

- **Missing rules version** — `Missing 'rules_version = '2';' declaration at the top of the file.`
- **Non-v2 rules version** — `rules_version = '1';` → `Expected 'rules_version = '2';' declaration at the top of the file; found version '1'.`
- **Version after service** — `'rules_version' must be declared before the 'service' block.`
- **Missing service** — `Missing 'service cloud.firestore { ... }' block.`
- **Unsupported service name** — `service firebase.storage { … }` → `Firestore Rules files target 'service cloud.firestore'; found 'service firebase.storage'.`
- **Service missing its block** — `'service cloud.firestore' is missing its rule block '{ ... }'.`
- **Missing root match** — `Missing root 'match /databases/{database}/documents' block inside 'service cloud.firestore'.`

### Usage warnings

- **Condition-less allow** — `allow read;` → `'allow' rule has no 'if' condition. Add ': if <condition>' to restrict when the operation is permitted.` A condition-less `allow` is *legal* (it unconditionally grants the operation), so this is an opt-in nudge rather than an error.
- **Recursive wildcard without v2** — `Recursive wildcard '{document=**}' should be used with rules_version = '2'; its match semantics differ between rules versions.`
- **Helper-call arity** — `exists()` → `'exists()' takes exactly one argument but found 0.` Applies to `get`, `getAfter`, `exists`, and `existsAfter`.

## Editor Conveniences

- **Brace matching** for `{}`, `()`, and `[]`. Curly braces are structural, so
  the IDE highlights the matching `}` for a `service`, `match`, or `function`
  block and the **Move Caret to Matching Brace** action jumps between them.
- **Quote handling** — typing `'` or `"` auto-inserts the closing quote and
  types over an existing one.
- **Comment toggling** — **Comment with Line Comment** inserts `//`, and
  **Comment with Block Comment** wraps a selection in `/* */`.

## Current Limitations

- **No authorization evaluation.** The plugin never decides whether a request is
  allowed or denied. All diagnostics are structural and syntactic.
- **No Firebase integration.** It does not connect to Firebase projects,
  emulators, credentials, or live Firestore data, and it hard-codes no project
  IDs.
- **Firestore Rules only.** Cloud Storage rules are out of scope, and the
  supported service is `cloud.firestore`.
- **Conservative diagnostics.** The grammar is intentionally permissive so the
  IDE keeps working while you edit. Constructs not confirmed by official Firebase
  docs are parsed without being flagged.
- **Highlighting is approximate.** Highlighting uses a fast, context-sensitive
  lexer that is separate from the parser. For example, `/` is always highlighted
  as a path separator (it shares the operator color), and an unterminated string
  is highlighted to the end of its line; the parser remains the source of truth
  for precise errors.
- **One fixed formatting style.** There is no custom code-style settings UI, and
  the formatter applies no column alignment.

## Example Firestore Rules File

A complete file that exercises most of what the plugin understands:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function isSignedIn() {
      return request.auth != null;
    }

    match /cities/{city} {
      allow read, write: if isSignedIn() && resource.data.owner == request.auth.uid;

      match /landmarks/{landmark} {
        allow read: if isSignedIn();
        allow create: if isSignedIn()
          && request.resource.data.name is string;
      }
    }

    match /logs/{document=**} {
      allow read: if false;
    }
  }
}
```

## How It Is Built

The project is a Kotlin-based IntelliJ Platform plugin built with Gradle Kotlin
DSL and the IntelliJ Platform Gradle Plugin 2.x.

Important parts of the repository:

```text
build.gradle.kts
settings.gradle.kts
src/main/grammar/                  # FirestoreRules.bnf + FirestoreRules.flex
src/main/kotlin/dev/lezli/hotrulez/
  FirestoreRulesLanguage.kt
  FirestoreRulesFileType.kt
  FirestoreRulesIcons.kt
  lexer/
  highlighting/
  parser/
  psi/
  formatting/
  diagnostics/
  editor/
src/main/resources/icons/
src/main/resources/META-INF/plugin.xml
src/test/kotlin/dev/lezli/hotrulez/
src/test/testData/formatter/
docs/spec.md
docs/tasks.md
```

The implementation is split by responsibility:

- `lexer/` tokenizes Firestore Rules syntax for highlighting.
- `highlighting/` maps tokens to IDE text attributes and exposes the color page.
- `parser/` wires the generated parser/PSI into a recoverable `ParserDefinition`.
- `psi/` defines the Firestore Rules PSI file and wrapper elements.
- `formatting/` provides IntelliJ formatter blocks and spacing rules.
- `diagnostics/` provides the annotator and inspections.
- `editor/` provides the brace matcher, quote handler, and commenter.
- `plugin.xml` registers every extension point above.

The parser, typed PSI, and parsing lexer are generated by Grammar-Kit and JFlex
from `src/main/grammar/` into `build/generated/sources/grammarkit` (not
committed); generation runs automatically before compilation. The grammar is
deliberately permissive and recovery-oriented, so a malformed statement is
isolated to its own declaration instead of breaking the rest of the file. A
second, hand-written lexer (`lexer/FirestoreRulesLexer.kt`) is used only for
highlighting.

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

## Project Status

`hotrulez` covers file recognition, highlighting, structural parsing, automatic
formatting, diagnostics, and editor conveniences (brace, quote, and comment
handling). It performs no runtime authorization evaluation by design.

See [docs/spec.md](docs/spec.md) for the product and implementation spec, and
[docs/tasks.md](docs/tasks.md) for the task checklist.
