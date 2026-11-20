# Compatibility

## Android versions

| | Version | API | Status |
| --- | --- | --- | --- |
| Minimum | Android 10 | 29 | Supported, primary test matrix target |
| | Android 11 | 30 | Supported |
| | Android 12 / 12L | 31 / 32 | Supported |
| | Android 13 | 33 | Supported, test matrix target |
| | Android 14 | 34 | Supported |
| | Android 15 | 35 | Supported |
| | Android 16 | 36 | Supported, compile + target |
| Below minimum | Android 9 and older | ≤ 28 | **Not supported** |

Why API 29 is the floor: `TYPE_APPLICATION_OVERLAY` behaviour, scoped storage for
screenshot/recording saves, and `MediaProjection` foreground-service rules are all
stable from Android 10 onward. Supporting 26–28 would require a parallel
`SYSTEM_ALERT_WINDOW`/`TYPE_PHONE` path and legacy storage code that cannot be
validated here.

## Feature availability by API level

AstraTouch never hides a failure silently: `ActionRegistry` reports each action as
`AVAILABLE`, `NEEDS_PERMISSION` or `UNSUPPORTED_OS`.

| Feature | Since | Notes |
| --- | --- | --- |
| Floating bubble, radial menu | 29 | Core |
| Global back/home/recents (accessibility) | 29 | Requires the service |
| Screenshot via accessibility | 30 | Android 11 added `takeScreenshot` |
| Screen recording (MediaProjection) | 29 | Consent dialog each session |
| Notification permission | 33 | Prompted on first use |
| Volume/brightness/rotation control | 29 | Brightness/rotation need write-settings |
| Flashlight | 29 | Camera permission |
| Lock screen | 29 | Device admin or accessibility |
| Split screen | 24 | `unsupported_os` below 29 is moot |
| Picture-in-picture hand-off | 26 | Delegates to the system |
| Dark mode reaction | 29 | Follows `uiMode` configuration |

## Screen and input

- Phone portrait and landscape, tablets, foldables (configuration changes are
  handled without losing the overlay).
- Both gesture navigation and 3-button navigation; nav-bar insets are respected.
- Display cutouts handled through window insets.
- Density-independent sizing throughout; no fixed pixel constants.
- Right-to-left layouts: Compose mirrors automatically; bubble docking sides are
  user-selectable rather than locale-driven.

## Honour-roll and OEM caveats

Some manufacturers aggressively kill background services. AstraTouch provides the
documented mitigations (notification foreground service, battery-optimisation
exemption prompt) but cannot defeat a vendor task-killer. Known behaviours are
listed in [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md).

## Not supported

- Android Go editions below 2 GB RAM — the Compose UI and overlay are too heavy.
- ChromeOS, Android Auto, Wear OS, Android TV — not tested, not targeted.
- Rooted/Xposed-only integrations — no root path exists in the codebase.
