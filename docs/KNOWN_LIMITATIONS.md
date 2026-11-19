# Known limitations

This file exists because a utility app that claims to do everything is usually
lying. Everything below is either an Android platform constraint AstraTouch
cannot escape, or a scope decision made deliberately and left visible in the UI.

## Platform constraints (impossible, not "not implemented")

### 1. No global gesture interception

AstraTouch cannot intercept swipes, taps or key presses that happen on another
app's UI. Android provides no API for that. What it can do:

- Recognise gestures **inside the bubble and radial menu windows** — taps, double
  taps, long presses and four-direction swipes on AstraTouch's own surface.
- Trigger global actions (back/home/recents) **after** a gesture, via the
  accessibility service.
- The only true global-gesture mechanism on Android is an accessibility service
  calling `dispatchGesture` over the whole screen, which is exactly the
  "invisible full-screen gesture-capture overlay" this project refuses to build.
  It breaks other apps' gesture handling, trips Play policy, and enables
  click-fraud automation.

### 2. No background clipboard reading (Android 10+)

Since Android 10, only the app with input focus may read the clipboard. AstraTouch
captures clipboard history when its own UI is focused (opening the Clipboard
screen or the bubble's clipboard action), not passively in the background. The
alternative — an accessibility service scraping the clipboard — was rejected as
privacy-hostile.

### 3. No screenshots below Android 11 via accessibility

`AccessibilityService.takeScreenshot` arrived in API 30. On Android 10 the
screenshot action is reported `UNSUPPORTED_OS` and the MediaProjection path (with
its per-session consent dialog) is offered instead.

### 4. MediaProjection consent cannot be remembered

Android requires a fresh user consent dialog for every recording session. No
app-level setting can skip it. The app does not attempt the known OEM-specific
tricks to bypass this.

### 5. FLAG_SECURE windows cannot be captured

Banking apps, DRM video and password fields set `FLAG_SECURE`. Both the
accessibility screenshot API and MediaProjection return black or refuse. AstraTouch
reports "This app blocks screenshots" rather than producing a broken file.

### 6. True blur requires Android 12+

`RenderEffect` blur is API 31+. Below that, AstraTouch substitutes a translucent
gradient and says so in the blur control's description — it does not pretend to
blur.

### 7. OEM task killers

Samsung's, Xiaomi's, Oppo's and Vivo's background managers can stop any
foreground service. AstraTouch shows the required notification, offers the battery
exemption, and documents the per-OEM steps, but cannot prevent a kill.

### 8. Some actions need a window that no longer exists

"Toggle split screen", "Picture-in-picture" and "Move to other app" depend on the
current foreground activity's `ActivityManager`/`PictureInPictureParams` state.
If the running app does not support it, the action returns a documented
`UNSUPPORTED_OS`/failure result with an explanation.

## Deliberate scope decisions

### 9. Text recognition (OCR) is not shipped

`Experimental lab` shows an "On-screen text recognition" switch that is disabled
and labelled "Reserved… stays off and is labelled honestly rather than pretending
to work". OCR needs either a network service (impossible: no INTERNET permission)
or a ~20 MB on-device model that would triple the APK and could not be verified in
this environment. It is listed in the roadmap, not faked.

### 10. Macro nesting is depth-limited to 1

A macro may open another macro's screen or run a *non-recursive* chain, but
`RunMacroUseCase` blocks `OPEN_MACROS`/macro-invoking steps from recursing
(`MAX_DEPTH = 1`) and delay steps are clamped to 10 s. This prevents an
unbreakable loop that the user cannot cancel on a background service. Documented in
FEATURES.md; the error is reported in macro history.

### 11. No plugin/scripting surface

There is no Lua/JS engine. Executing user-supplied scripts on a device with
accessibility privileges is a malware vector and would fail any store review.

### 12. No cloud sync

Consequence of "no INTERNET permission". Backup/restore is manual JSON via
Privacy Center.

### 13. Timer and stopwatch do not persist across reboot

Timers are stored in the `timers` table so they survive process death and app
restart, but there is no alarm-style exact alarm scheduling; a timer that should
have fired while the device was off reports as finished on next launch instead of
waking the device.

### 14. No widget, no Quick Settings tile

Both were considered. A QS tile needs no special permission and would be a good
addition; it is on the roadmap. A home-screen widget duplicating the bubble was
judged redundant.

## Verification gaps (stated openly)

- **No physical-device testing.** All verification is on GitHub-hosted emulators
  (API 29, 33, 36). Real OEM skins, real battery behaviour and real TalkBack have
  not been exercised.
- **No TalkBack audit** with a human screen-reader user. Semantic labels and 48 dp
  targets are implemented and reviewed, but an accessibility audit is outstanding.
- **No performance profiling** on low-end hardware. The bubble uses a single
  hardware-accelerated surface and no per-frame layout work, but jank on a 2 GB
  device is unmeasured.
- **Release pipeline has not been executed end-to-end with real signing
  secrets** in this repository: the automation token cannot write repository
  secrets (HTTP 403). The gates are written and reviewed; the first real run
  requires the owner to run `scripts/setup-signing.sh`.
- **Lint is clean for `lintDebug`;** `lintRelease` runs in the release workflow and
  aborts on errors, but has not yet been exercised with a signed build here.

## Things that are not limitations

For clarity, these are implemented, not deferred: multi-profile isolation, custom
profiles, macro conditions and history, radial paging, gesture sensitivity, theme
customisation, screenshot annotate/crop/share, recording, clipboard sensitive
filtering, notes, calculator, permission center, privacy center, JSON backup,
24 screens, 50+ system actions, and the migration from schema v1 to v2.
