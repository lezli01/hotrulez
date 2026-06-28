# Security Policy

## Scope

`hotrulez` is a static, structural JetBrains IDE plugin for Firebase Cloud
Firestore Security Rules. By design it **never connects to Firebase, runs the
emulator, reads credentials, or evaluates whether a rule authorizes a
request** — it only parses, highlights, formats, and analyzes the structure of
`.rules` files locally in the IDE. Its attack surface is therefore small, but
we still take security reports seriously.

> [!IMPORTANT]
> hotrulez does not assess whether your Firestore Rules are *secure*. It makes
> no authorization decisions. Continue to use Firebase's official tooling to
> test and deploy your rules. A diagnostic from this plugin is never a security
> guarantee.

## Supported Versions

Security fixes are applied to the latest released version. Please upgrade to the
most recent release before reporting an issue.

| Version | Supported |
| --- | --- |
| 0.5.x (latest) | ✅ |
| < 0.5 | ❌ |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues,
discussions, or pull requests.**

Instead, use GitHub's private vulnerability reporting:

1. Go to the [**Security** tab](https://github.com/lezli01/hotrulez/security) of
   the repository.
2. Click **Report a vulnerability**, or use this direct link:
   <https://github.com/lezli01/hotrulez/security/advisories/new>.
3. Describe the issue, including the affected version, reproduction steps, and a
   minimal `.rules` snippet if relevant.

Reports submitted this way are private and visible only to the maintainers.

## What to expect

- We aim to acknowledge a report within a few days.
- We will keep you informed as we investigate and work on a fix.
- Once a fix is released, we are happy to credit you in the advisory unless you
  prefer to remain anonymous.

Thank you for helping keep hotrulez and its users safe.
