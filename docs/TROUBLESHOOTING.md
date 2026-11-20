# Troubleshooting

Every entry states the symptom, the cause, and the fix. If a step here does not
work, open an issue with your Android version, device model and the exact message
you saw.

## The bubble does not appear

1. **Overlay permission** — Settings → Apps → AstraTouch → *Display over other
   apps*. If it says "Not allowed", AstraTouch shows a red banner on the
   dashboard; tap it.
2. **Bubble turned off** — Dashboard → *Start bubble* toggles it on. Check that
   *Auto-hide* has not moved it off-screen; **Settings → Bubble → Reset position**.
3. **Aggressive battery management** — Samsung, Xiaomi, OnePlus, Oppo and Vivo
   kill foreground services. Grant the battery-optimisation exemption that
   AstraTouch offers, and in the OEM's own settings mark the app as
   *Unrestricted* / *Autostart on*.
4. **Do Not Disturb / work profile** — a managed profile can block overlays
   entirely. Test in the personal profile.

## "Accessibility service keeps turning off"

Android disables a service it considers misbehaving, and some OEMs disable it
after a reboot or an app update.

- Re-enable it in Settings → Accessibility → AstraTouch Actions.
- Exclude AstraTouch from the OEM's auto-clean list.
- After an app **update** the toggle usually survives; after a **force stop** it
  does not — that is an Android rule, not an AstraTouch bug.
- AstraTouch detects the state and shows a banner instead of failing silently.

## The Back / Home action does nothing

Those need the accessibility service. If it is off, `ActionRegistry` reports the
action as `NEEDS_PERMISSION`; the action sheet shows a lock icon and a "Grant"
button. Grant it, then retry.

## Screenshot action fails

- Requires **Android 11 (API 30)** or newer. On Android 10 the action reports
  `UNSUPPORTED_OS` and offers the MediaProjection path instead.
- Requires a window that allows capture. Some banking and DRM apps set
  `FLAG_SECURE`; Android refuses to capture them and AstraTouch shows
  "This app blocks screenshots".
- If saving fails, check free space — screenshots go to app-private cache.

## Screen recording stops immediately

- MediaProjection requires a fresh consent dialog per session; the system will not
  let AstraTouch reuse an old token.
- The recording service must run in the foreground — if you revoked the
  notification permission on Android 13+, the service cannot start. Re-grant
  notifications.
- Starting a call, or another app capturing the screen, can pre-empt the projection.

## A macro stops halfway

Open the macro → *History*. Each run lists the failing step and a reason.

- **Timeout** — the macro exceeded its `timeoutMs` (default 12 s per action).
  Reduce delays.
- **NEEDS_PERMISSION** — a step needs access you have not granted.
- **Recursion blocked** — a macro cannot call itself or another macro that calls
  it (depth limit 1).
- Delays are clamped to 10 s; a longer value is silently reduced and logged.

## Clipboard entries are missing

`SensitiveContentFilter` rejects: OTP-shaped strings, `password=`/`token=`/`secret=`
assignments, PEM private keys, card-number patterns, very long content and
high-entropy tokens. This is intentional. Turn the filter off in
**Settings → Privacy → Clipboard**, but understand the trade-off.

On Android 10+ Android itself restricts background clipboard access: AstraTouch can
only read the clipboard while its own window is focused, so history is captured
when you open the clipboard screen.

## The app launcher shows no apps

Package visibility. AstraTouch declares only a `<queries>` filter for the launcher
intent, so an app with no launcher activity cannot appear. Tap *Refresh* on the
Apps screen after installing something new.

## Build fails: `SDK location not found`

Create `local.properties` with `sdk.dir=/path/to/Android/Sdk`, or export
`ANDROID_HOME`. See [BUILDING.md](BUILDING.md).

## Build fails: `Unsupported class file major version`

You are not on JDK 21. `java -version` must report 21; AGP 8.11.1 will not run on
17 or 24.

## Release workflow fails with "signing secrets are missing"

By design. The pipeline refuses to publish an unsigned or debug-signed APK. Run
`./scripts/setup-signing.sh` with an admin-authenticated `gh`, or add the four
secrets in repository settings. See [RELEASE_SIGNING.md](RELEASE_SIGNING.md).

## Release workflow fails on `aapt2 dump badging` assertions

`versionName` / `versionCode` in `gradle.properties` do not match the tag.
`versionCode` must equal `major*10000 + minor*100 + patch`.

## "There was a problem parsing the package"

The download was truncated, or you are on an Android version below 10. Verify the
SHA-256 against `SHA256SUMS.txt` and re-download.

## "App not installed / signature mismatch"

An existing AstraTouch build with a different signing certificate is installed
(for example a `debug` build from a fork). Uninstall it, then install the official
release.

## Battery drain

- Increase *auto-hide* so the bubble is off-screen longer.
- Disable blur (it adds a GPU read-back each frame).
- Reduce animation speed to 0.
- Check that no macro is looping — History shows every run.

## Still stuck

Run **Settings → About → Copy diagnostics**, paste into the issue along with
`adb logcat -s AstraTouch`. Diagnostics contains version, SDK level, granted
permissions and enabled features — **never** personal data.
