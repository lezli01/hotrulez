# Contributing to hotrulez

Thanks for your interest in improving **hotrulez** — a JetBrains IDE plugin for
Firebase Security Rules (both Cloud Firestore and Cloud Storage). Contributions
of every size are welcome: bug reports, documentation fixes, new diagnostics,
test cases, and features.

By participating in this project you agree to abide by our
[Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to contribute

- **Report a bug** — open a [bug report](https://github.com/lezli01/hotrulez/issues/new?template=bug_report.yml).
  A minimal `.rules` snippet that reproduces the problem is the single most
  helpful thing you can include.
- **Request a feature** — open a [feature request](https://github.com/lezli01/hotrulez/issues/new?template=feature_request.yml).
- **Ask a question or share an idea** — start a
  [Discussion](https://github.com/lezli01/hotrulez/discussions).
- **Send a pull request** — see below.

## Development setup

Requirements:

- **JDK 21** (the build's Gradle toolchain targets language version 21).
- The bundled **Gradle wrapper** (`./gradlew`) — no separate Gradle install
  needed.
- Any JetBrains IDE compatible with build `252` or newer for manual testing.

Clone your fork and verify the toolchain builds:

```bash
git clone https://github.com/<your-username>/hotrulez.git
cd hotrulez
./gradlew test
```

The parser, typed PSI, and parsing lexer are generated from
`src/main/grammar/` by Grammar-Kit and JFlex into
`build/generated/sources/grammarkit` (not committed). Generation runs
automatically before compilation; to regenerate explicitly:

```bash
./gradlew generateFirebaseParser generateFirebaseLexer
```

## Building and running

| Task | Command |
| --- | --- |
| Run the test suite | `./gradlew test` |
| Build the installable plugin ZIP (`build/distributions/`) | `./gradlew buildPlugin` |
| Launch a sandbox IDE with the plugin loaded | `./gradlew runIde` |

`./gradlew runIde` boots a throwaway JetBrains IDE with hotrulez installed —
the fastest way to try a change against real `.rules` files.

## Making changes

- Keep edits scoped to plugin behavior and project setup. Don't introduce
  unrelated app frameworks or web UI dependencies.
- Keep code separated by responsibility (lexer, parser, PSI, highlighting,
  formatting, diagnostics, editor, references, find-usages, refactoring,
  completion) as described in the [README](README.md#how-it-is-built) and
  [AGENTS.md](AGENTS.md).
- **Add focused tests** when changing lexer, parser, formatter, diagnostics, or
  symbol-intelligence logic. Match the existing test files
  (`FirebaseRulesAnnotatorTest`, `FirebaseRulesResolveTest`, etc.).
- Keep diagnostic wording **structural** — describe syntax and structure; never
  assert that a rule is "secure" or that a request would be authorized.
- Confirm Firebase Rules semantics (for the relevant service — Cloud Firestore or
  Cloud Storage) against the official Firebase documentation before adding or
  changing a check.
- Match the style of the surrounding Kotlin. A `.editorconfig` is provided.

## Commit messages — Conventional Commits

Releases are automated with
[release-please](https://github.com/googleapis/release-please), which derives
the next version and the [CHANGELOG](CHANGELOG.md) from commit messages. Pull
requests are squash-merged, so **your PR title must follow the
[Conventional Commits](https://www.conventionalcommits.org/) format** — it
becomes the commit on `master`.

```
<type>[optional scope]: <description>
```

Common types:

| Type | Use for | Version bump |
| --- | --- | --- |
| `feat` | a new user-facing capability | minor |
| `fix` | a bug fix | patch |
| `docs` | documentation only | none |
| `refactor` | code change that neither fixes a bug nor adds a feature | none |
| `test` | adding or correcting tests | none |
| `build` | build system, Gradle, or packaging changes | none |
| `ci` | CI workflow and automation changes | none |
| `chore` | other maintenance and dependency bumps | none |

This is the subset used in this project; see the
[Conventional Commits specification](https://www.conventionalcommits.org/) for
the full set. A breaking change is marked with `!` (e.g. `feat!: ...`) or a
`BREAKING CHANGE:` footer, and bumps the major version.

Examples:

```
feat(completion): offer request.auth.token members
fix(formatter): keep multiline conditions split
docs: document the recursive-wildcard diagnostic
```

## Pull request process

1. Fork the repository and create a branch from `master`.
2. Make your change and add tests.
3. Make sure `./gradlew test` passes locally.
4. Push and open a pull request. Fill in the PR template.
5. CI must pass before a PR can be merged. A maintainer will review and merge.

`master` is protected: changes land through pull requests and the CI test job
must be green.

## Licensing

By contributing, you agree that your contributions will be licensed under the
project's [MIT License](LICENSE).
