# HotRulez vs. other JetBrains Firebase Rules plugins

A feature comparison of **HotRulez** against the other JetBrains Marketplace
plugins that support the Firebase Security Rules language, scoped strictly to
**Firebase Security Rules** authoring.

> **As of 2026-07-05.** Competitor data is drawn from each plugin's JetBrains
> Marketplace listing (description, versions, and the public
> `plugins.jetbrains.com/api/plugins/<id>` metadata) and, where noted, the
> vendor's own README/articles. HotRulez data is drawn from this repository
> (`README.md`, `plugin.xml`). Marketplace listings change; re-verify before
> quoting.

## Scope of this comparison

This table covers **only** what helps you *author the Firebase Security Rules
language* — the `.rules` DSL used by `service cloud.firestore` (Cloud Firestore)
and `service firebase.storage` (Cloud Storage). Everything a "Firebase" plugin
might also do that is **not** the rules language — browsing Firestore *data*,
Realtime Database JSON rules, `firebase.json`/indexes config, Remote Config, Data
Connect, hosting, analytics, and so on — is **out of scope** and excluded, except
for two capabilities that directly touch rules and are therefore kept as rows:
**emulator / rules-testing** and **rules deploy**.

## The three plugins at a glance

| | **HotRulez** | Firebase Rules | Firebase Pro |
|---|:---:|:---:|:---:|
| Marketplace ID | [32552](https://plugins.jetbrains.com/plugin/32552) | [15189](https://plugins.jetbrains.com/plugin/15189-firebase-rules) | [28937](https://plugins.jetbrains.com/plugin/28937-firebase-pro) |
| Vendor | lezli01 | Anbora Labs | JHTech Labs |
| Price | **Free** | Paid (7-day trial) | Paid (30-day trial) |
| Open source | ✅ MIT | ✅ | ❌ |
| **Handles the `.rules` language** | ✅ | ✅ | ✅ |
| Cloud Firestore rules | ✅ | ✅ | ✅ |
| Cloud Storage rules | ✅ | 🟡 *(undocumented)* | ✅ |
| Latest version | 0.7.x | 2026.1.2 (May 2026) | 262.1.15 (Jun 2026) |
| Downloads | — *(newest entrant)* | ~124,500 | ~11,500 |

## Feature comparison — Firebase Rules scope

Legend: ✅ documented / supported · 🟡 partial or basic · ❔ not documented (unknown) ·
❌ not supported / out of scope. See [Notes & caveats](#notes--caveats) — a ❔ for
a competitor means *the listing doesn't mention it*, not that it is proven absent.

| Capability | **HotRulez** | Firebase&nbsp;Rules<br>(15189) | Firebase&nbsp;Pro<br>(28937) |
|---|:---:|:---:|:---:|
| **Editing & display** | | | |
| File recognition (`*.rules` type + icon) | ✅ | ✅ | ✅ |
| Syntax highlighting (configurable colors) | ✅ *(24 categories)* | ✅ | ✅ |
| Code formatting / reformat | ✅ | ✅ | ✅ |
| Code folding | ✅ | ❔ | ❔ |
| Brace matching | ✅ | ❔ | ❔ |
| Comment toggling (line / block) | ✅ | ❔ | ❔ |
| Quote auto-closing | ✅ | ❔ | ❔ |
| **Completion** | | | |
| Keywords / `allow` operations / service names | ✅ | 🟡 *("basic")* | ✅ |
| Members after `request.` / `resource.` | ✅ *(service-aware)* | ❔ | ✅ |
| Scope-aware symbols (functions, params, `let`, path vars) | ✅ | ❔ | ✅ |
| **Navigation & refactoring** | | | |
| Structure view / file outline | ✅ | ✅ | ✅ |
| Go to declaration / definition | ✅ | 🟡 *("navigation")* | ✅ |
| Find usages | ✅ | ❔ | ✅ |
| Rename refactoring | ✅ *(scope-aware)* | ❔ | ❔ |
| **Diagnostics** | | | |
| Syntax / parse-error diagnostics | ✅ | ✅ | 🟡 |
| Semantic inspections (unresolved / unused symbols, structure) | ✅ *(3 inspections)* | 🟡 *("linter")* | ✅ |
| Quick-fixes / intentions (Alt+Enter) | ✅ | ❔ | ✅ |
| **Docs & signature help** | | | |
| Quick documentation (hover / Ctrl+Q) | ✅ *(doc-grounded)* | ❔ | ✅ |
| Parameter info (Ctrl+P) | ✅ | ❔ | ❔ |
| Live templates / snippets | ❌ | ❔ | ❔ |
| **Rules dialects & tooling** | | | |
| Cloud Firestore rules (`service cloud.firestore`) | ✅ | ✅ | ✅ |
| Cloud Storage rules (`service firebase.storage`) | ✅ | 🟡 *(inferred)* | ✅ |
| Emulator / rules-testing integration | ❌ *(by design)* | ❌ | 🟡 *(start/manage)* |
| Rules deploy integration | ❌ *(by design)* | ❌ | ❔ |

## What sets each apart

- **HotRulez (this project)** — the only **free, open-source** plugin in the
  group, and the one with the deepest *documented and verifiable* rules-authoring
  feature set: a PSI resolve layer that powers go-to-declaration, find-usages,
  scope-aware rename, and scope-/service-aware completion; three tunable
  inspections plus an always-on error annotator, most with Alt+Enter quick-fixes;
  doc-grounded quick documentation and parameter info; and a full set of authoring
  aids (structure view, folding). It is deliberately **structural and static** —
  it never connects to Firebase, runs the emulator, or evaluates authorization —
  so it has no emulator, deploy, or data features by design. Both Firestore and
  Cloud Storage rules are first-class, with the dialect detected from the
  `service` declaration.

- **Firebase Rules — Anbora Labs (15189)** — the established, most-downloaded
  dedicated rules plugin (~124.5k). It is a genuine rules-language editor (not a
  data/emulator tool), and its listing documents seven features: syntax
  highlighting with configurable colors, syntax checking, *basic* code completion,
  code formatting, code navigation, structure view, and a rules linter. Depth
  beyond those bullets — folding, rename, quick documentation, parameter info,
  member/semantic completion — is **not documented**, and neither Cloud Storage
  nor `service firebase.storage` is named anywhere in the listing (Storage support
  is only inferred from the shared DSL). It is a **paid** plugin (7-day trial).

- **Firebase Pro — JHTech Labs (28937)** — the closest competitor on rules-language
  depth. It gives the Security Rules file type genuine first-class language
  support: rules-aware highlighting and formatting, context-aware completion
  (language constructs, local variables, and modeled `request`/`resource`
  members), a hierarchical structure view, reference navigation and find-usages,
  documentation-on-hover, and semantic inspections with quick-fixes (recursive
  calls, invalid methods/service names, bad type checks, undefined functions) —
  for **both** Firestore and Storage rules. Rules is one part of a broader **paid**
  Firebase suite (also `firebase.json` config, indexes, Realtime Database JSON
  rules, Remote Config, Data Connect, Extensions, and emulator start/manage).
  Undocumented for the rules language: folding, brace matching, rename, parameter
  info, and live templates; its advertised "schema validation" targets the JSON
  config files, not the rules DSL. It is **not open source** and has no rules
  deploy action.

## Notes & caveats

- **❔ means "undocumented," not "absent."** For the paid competitors we can only
  score what their Marketplace listing (and, where available, the vendor's own
  docs) actually claims. Several ❔ rows — brace matching, comment toggling,
  folding — are behaviors the IntelliJ Platform often provides "for free" once a
  plugin registers a real custom language, so a full language plugin like
  *Firebase Pro* may well have them without advertising them. Treat ❔ as *"not
  verifiable from public information,"* not as a demerit.

- **HotRulez's ✅ are repository-verified.** Every ✅ in the HotRulez column maps
  to a registered extension in [`plugin.xml`](../src/main/resources/META-INF/plugin.xml)
  and is described in the [README](../README.md) — e.g. the three
  `localInspection`s, the folding/structure-view/documentation/parameter-info
  builders, and the reference/rename/completion stack.

- **"Free" is a real differentiator.** HotRulez is MIT-licensed and free;
  *Firebase Rules* and *Firebase Pro* are both commercial (trial-then-pay). Among
  plugins that support the rules **language**, HotRulez is the only free option.

- **HotRulez is intentionally scope-limited.** It performs no authorization
  evaluation and no Firebase/emulator/deploy integration — those ❌ cells are a
  deliberate design boundary, not a gap. For deploying or emulator-testing rules,
  use Firebase's official CLI/tooling alongside any of these plugins.

- **Storage-rules parity.** HotRulez and *Firebase Pro* both explicitly support
  `service firebase.storage`; *Firebase Rules* (15189) does not name Storage in
  its listing, so its Storage support is only inferred from the shared DSL.
