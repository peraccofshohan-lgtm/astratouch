# Accessibility

AstraTouch is an assistive control surface. It exists so that a user can trigger
system actions — back, home, recents, screenshots, volume, brightness, lock,
app launches, macros — from a single always-reachable bubble.

This document describes exactly what the accessibility service does, why each
capability is declared, and what it deliberately refuses to do. It is written to
be read by a Play Store reviewer as much as by a user.

## Service declaration

`app/src/main/java/com/astratouch/app/accessibility/AstraAccessibilityService.kt`
is declared in `AndroidManifest.xml` with
`android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"` and exported,
because Android's system settings require that. It is **not** enabled by
installing the app. The user must open **Settings → Accessibility → AstraTouch
Actions** and switch it on, and can revoke it the same way at any time.

`res/xml/accessibility_service_config.xml`:

```xml
android:accessibilityEventTypes="typeWindowStateChanged|typeWindowsChanged"
android:accessibilityFeedbackType="feedbackGeneric"
android:accessibilityFlags="flagDefault|flagReportViewIds"
android:canPerformGestures="true"
android:canRetrieveWindowContent="false"
android:notificationTimeout="100"
```

## What it can do

| Capability | Declaration | Used for |
| --- | --- | --- |
| Perform global actions | `performGlobalAction` | `GLOBAL_ACTION_BACK`, `HOME`, `RECENTS`, `NOTIFICATIONS`, `QUICK_SETTINGS`, `LOCK_SCREEN` |
| Take a screenshot | `takeScreenshot` (API 30+) | Screenshot action, screenshot studio, annotate, share |
| Observe window state | `typeWindowStateChanged`, `typeWindowsChanged` | Knowing when the foreground window changes so the bubble can auto-dim/hide and so the current app can be shown in the launcher context |
| Read view ids | `flagReportViewIds` | Required by the platform for `takeScreenshot` window selection; ids are **not** stored or transmitted |

## What it deliberately does **not** do

- **No gesture injection over other apps.** `dispatchGesture` is available to the
  service but AstraTouch never synthesises taps or swipes on another app's
  content. Overlay gestures are handled by AstraTouch's own window, not by the
  service.
- **No content reading.** `canRetrieveWindowContent="false"`. AstraTouch does not
  walk the node tree, does not read text from other apps, does not scrape
  passwords, and does not use `AccessibilityNodeInfo` for anything at all.
- **No key logging, no form filling, no clipboard reading through accessibility.**
- **No background network.** There is no `INTERNET` permission in the manifest, so
  even a hypothetical leak has nowhere to go.
- **No self-enabling.** The service cannot turn itself back on. If the system or
  the user turns it off, AstraTouch shows a banner and falls back to non-accessibility
  behaviour.

## Why accessibility rather than something simpler

Android does not expose a non-accessibility API for pressing *Back* or taking a
screenshot of another app. The alternatives were:

1. **Accessibility service** — officially documented, user-consented, revocable,
   and the mechanism every launcher-style utility on Play uses.
2. **Root** — unacceptable; the app has no root path.
3. **`MediaProjection` for screenshots** — requires a consent dialog *every*
   capture, cannot press Back/Home at all, and shows a persistent screen-capture
   indicator.

AstraTouch uses accessibility only for the actions that have no other API, and
uses `MediaProjection` separately and only for continuous recording.

## Play Store policy position

AstraTouch is designed to satisfy Google Play's Accessibility API policy:

- **Disclosure & consent.** The onboarding flow explains each capability in plain
  language before the settings toggle is offered; `res/values/strings.xml`
  `accessibility_service_description` restates it in the system dialog.
- **Minimum scope.** Only global actions and window-state observation are used;
  `canRetrieveWindowContent` is off.
- **No deceptive use.** The service never hides UI, never auto-clicks ads, never
  automates another app's interface, and never overlays content without user
  intent.
- **User control.** Every accessibility-backed action has a non-accessibility
  fallback or is clearly marked unavailable.
- **No data collection.** Nothing observed by the service is persisted beyond the
  in-memory current package name, and nothing leaves the device.

Google Play's 2024+ policy additionally requires the "IsAccessibilityTool"
declaration in `accessibility_service_config.xml` for tools whose primary purpose
is assistive. AstraTouch is a control/automation utility rather than an
assistive-technology replacement, so it declares itself as a **non** accessibility
tool and relies on the prominent-disclosure path. If you intend a Play
distribution, keep the disclosure screen, do not add a promotional banner, and
make sure the consent text stays in sync with the manifest.

## Accessibility of AstraTouch's own UI

- Every interactive element has a content description or a visible label; icons
  without text use `contentDescription` from the string resources.
- Minimum touch target 48 dp; Material 3 components used throughout.
- Colour is never the only carrier of meaning (permission states use icon +
  text + colour).
- Text scales with the system font-size setting; layouts scroll rather than clip.
- TalkBack: the radial menu exposes each item as a single focusable node with a
  label, and announces the menu open/close state.
- Reduce-motion is respected: animations collapse to instant transitions when the
  system animator duration scale is 0.
- Haptics can be disabled entirely in Settings.

## Testing accessibility behaviour

Emulator runs in `android-instrumentation.yml` cover navigation and calculator
flows with the default accessibility tree. Manual TalkBack auditing on a physical
device has **not** been performed; this is listed openly in
[KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md).
