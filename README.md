# AstraTouch

> **Your Phone. Your Control.**

AstraTouch is a native Android floating command and accessibility control app: a draggable bubble
that opens a radial command center, drives real system actions, runs choreographed macros, and adapts
to your context through profiles — entirely offline.

- **Package:** `com.astratouch.app`
- **Version:** `1.0.0` (versionCode `10000`)
- **Android 10 → Android 16** (`minSdk 29`, `targetSdk 36`, `compileSdk 36`)
- **Kotlin + Jetpack Compose + Material 3**
- **No ads. No analytics. No account. No `INTERNET` permission.**

---

## Highlights

| Subsystem | What it does |
| --- | --- |
| Floating Overlay Engine | Draggable bubble with edge snapping, docking, position persistence, shapes, gradients, auto-dim, haptics and rotation-safe geometry |
| Radial Command Center | Eight layouts (full circle, semi, quarter, grid, compact, expanded, nested, multi-page) with paging, favourites and recents |
| System Action Engine | A real `ActionRegistry`: capability detection, per-action permissions, timeouts, and honest user-facing failure explanations |
| Accessibility Integration | `AccessibilityService` used strictly for global actions and window-change awareness — never text scraping |
| Gesture Engine | Single/double tap, long press, four swipes, sensitivity, debounce, cancellation, live test pad |
| Macro Automation | Ordered actions, delays, conditions, timeout guards, loop prevention, execution history, six default macros |
| App Launcher | PackageManager discovery, search, favourites, groups, launch counter |
| Smart Profiles | Six profiles with independent bubble, menu, gestures, theme and (optionally) app-triggered switching |
| Customization Studio | Five themes plus full custom colours, radius, typography, animation and haptics |
| Screenshot Studio | Accessibility-API capture (11+), MediaProjection recording, crop, annotate, FileProvider share |
| Productivity | Clipboard history with sensitive-content filtering, notes, calculator, timers |
| Permission & Privacy Center | Per-access purpose, dependents, fallbacks, settings deep links, full local wipe, JSON backup/restore |

## Install

1. Download `AstraTouch-v1.0.0.apk` from the [latest GitHub Release](https://github.com/peraccofshohan-lgtm/astratouch/releases/latest).
2. Verify the download against `SHA256SUMS.txt` (see [docs/INSTALLATION.md](docs/INSTALLATION.md)).
3. Allow install from your browser/file manager when Android prompts (the APK is signed with a
   dedicated release key, not the Android debug key).
4. Open AstraTouch → **Onboarding** → grant *Display over other apps* → enable the
   **AstraTouch Actions** accessibility service → start the bubble.

## Permissions at a glance

| Access | Required? | Used for |
| --- | --- | --- |
| Display over other apps | **Required** | The floating bubble and radial menu |
| Accessibility service | **Required** for global actions | Home/Back/Recents, shade, Quick Settings, screenshots |
| Modify system settings | Optional | Brightness, auto-rotation |
| Notifications | Optional | Ongoing status, timer/macro alerts |
| Camera | Optional | Torch only (no preview, no photos) |
| Battery optimisation exemption | Optional | Keeps the bubble alive on aggressive OEM builds |

AstraTouch requests **no** `INTERNET` permission. Details: [docs/PERMISSIONS.md](docs/PERMISSIONS.md).

## Build from source

Requires **JDK 17+** (CI uses 21) and Android SDK platform 36.

```bash
./gradlew clean
./gradlew assembleDebug
./gradlew test
./gradlew lint
./gradlew assembleRelease   # signed only when signing env vars/key.properties are supplied
./gradlew check
```

Full instructions, including R8 and signing setup: [docs/BUILDING.md](docs/BUILDING.md).

## Releases

Signed APKs are built and published by GitHub Actions when a `v*` tag is pushed — see
[docs/RELEASE_PROCESS.md](docs/RELEASE_PROCESS.md) and [docs/GITHUB_ACTIONS.md](docs/GITHUB_ACTIONS.md).
The APK is never committed to the repository; it lives only as a release asset.

## Known limitations

- **Global gestures outside AstraTouch** cannot be intercepted; Android owns those. AstraTouch
  recognises gestures that begin on its own bubble. See [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md).
- **Screenshots on Android 10** are unavailable through the accessibility API (API 30+); Android 10
  users can use MediaProjection recording.
- **Secure screens** (banking apps, DRM video) block capture at the OS level.
- **OEM battery managers** may still kill the overlay; the app detects this and offers recovery.

## Privacy statement

AstraTouch stores everything locally: profiles, macros, notes, clipboard history, screenshots and
recordings. There is no network code in the app — not even an opt-in telemetry switch. Clipboard
entries that look like passwords, one-time codes, private keys or payment data are refused, and
clipboard history is excluded from backups. Full statement: [docs/PRIVACY.md](docs/PRIVACY.md).

## Documentation

- [Architecture](docs/ARCHITECTURE.md) · [Features](docs/FEATURES.md) · [Compatibility](docs/COMPATIBILITY.md)
- [Permissions](docs/PERMISSIONS.md) · [Accessibility](docs/ACCESSIBILITY.md) · [Privacy](docs/PRIVACY.md)
- [Database](docs/DATABASE.md) · [Testing](docs/TESTING.md) · [Building](docs/BUILDING.md)
- [Release process](docs/RELEASE_PROCESS.md) · [Release signing](docs/RELEASE_SIGNING.md) · [GitHub Actions](docs/GITHUB_ACTIONS.md)
- [Installation](docs/INSTALLATION.md) · [Troubleshooting](docs/TROUBLESHOOTING.md) · [Known limitations](docs/KNOWN_LIMITATIONS.md) · [Roadmap](docs/ROADMAP.md)
- [Completion matrix](docs/COMPLETION_MATRIX.md)

## License

MIT — see [LICENSE](LICENSE).
