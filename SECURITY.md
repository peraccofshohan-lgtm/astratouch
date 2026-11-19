# Security policy

## Supported versions

| Version | Supported |
| --- | --- |
| 1.x | Yes |
| < 1.0 | Not released |

Only the latest 1.x release receives fixes. There is no long-term-support branch.

## Reporting a vulnerability

**Do not open a public issue.**

Use GitHub's private vulnerability reporting:
[Security → Report a vulnerability](https://github.com/peraccofshohan-lgtm/astratouch/security/advisories/new).

Include:

- Affected version (`Settings → About`) and Android version.
- Steps to reproduce, or a minimal proof of concept.
- Impact: what an attacker gains, what preconditions exist (physical access,
  malicious app installed, accessibility already granted, …).

You can expect an acknowledgement within 72 hours and an assessment within
7 days. Fixes for high-severity issues are prioritised and released as a patch
version with a signed, verified APK.

If you must use email, send to the address in the repository's `SECURITY.md`
commit history — but prefer the GitHub advisory flow, which supports encrypted
disclosure and coordinated release.

## What is in scope

| Area | Examples |
| --- | --- |
| Overlay windows | Tapjacking, spoofing another app's UI, focus stealing |
| Services | Foreground-service abuse, notification spoofing, wake locks |
| Intents and receivers | Unauthorised broadcast triggering an action, exported component misuse |
| FileProvider | Path traversal, sharing a file outside the intended scope |
| Data handling | Data readable by another app, clipboard/notes leaking |
| Backup import | Malicious JSON causing code execution, path write or resource exhaustion |
| Macro engine | Infinite loops, unbounded delay, running privileged actions without consent |
| Accessibility surface | Anything that reads content or automates another app |

## Out of scope

- Missing `INTERNET` permission — the absence is intentional and documented.
- Actions that require permissions the user has not granted.
- Physical-access attacks requiring an unlocked bootloader.
- Rooted-device scenarios.
- Denial of service that requires the user to enable a feature against warnings.
- Play Store policy opinions (report those as discussions, not vulnerabilities).
- Known limitations listed in [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md).

## Security design properties

Documented so you know what "secure" means here — see
[docs/PRIVACY.md](docs/PRIVACY.md) and [docs/ACCESSIBILITY.md](docs/ACCESSIBILITY.md).

1. **No network surface.** `INTERNET` is not declared, so the app cannot open a
   socket. An exploit cannot exfiltrate data over the network by itself.
2. **Minimal exported surface.** Only `MainActivity` (launcher) and the
   accessibility service (required to be exported by the platform) are exported.
   `OverlayActionReceiver` accepts exactly three internal, non-exported actions.
3. **Immutable PendingIntents.** All `PendingIntent`s use `FLAG_IMMUTABLE`.
4. **No `allowBackup`.** Data cannot leave via adb backup or cloud backup;
   data-extraction rules exclude databases, prefs and files.
5. **Private storage only.** Screenshots and recordings live in `cacheDir` /
   `filesDir`, shared only through explicit FileProvider URIs with a declared path
   allowlist.
6. **Bounded execution.** Every action has a 12 s timeout; macros clamp delays to
   10 s and block recursion, so a hostile macro cannot wedge the service.
7. **Sensitive input rejected.** The clipboard filter refuses OTPs, passwords,
   private keys and card patterns before storage.
8. **Signed releases only.** The release workflow refuses to publish without a
   real signing key, verifies the signature and re-checks the published hash.

## Supply chain

- Dependencies are pinned in `gradle/libs.versions.toml`; no dynamic versions.
- The Gradle wrapper is validated by `gradle/actions/wrapper-validation` in CI.
- The release pipeline builds from a tag, in CI, with a reproducible Gradle cache —
  no locally-built binaries are published.
- Adding a dependency requires justification in the PR: the privacy guarantee
  depends on keeping the dependency graph small.

## Disclosure timeline

- Day 0 — report received, acknowledged within 72 h.
- Day 0–7 — triage and severity assessment, reproduction confirmed.
- Day 7–30 — fix developed, tested, and a patch release prepared.
- Day 30 or patch publication (whichever is first) — advisory published with
  credit, unless you ask to remain anonymous.

Reporters who prefer coordinated disclosure with a longer embargo can request one;
we will agree on a date provided the issue is not being actively exploited.
