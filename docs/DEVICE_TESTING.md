# Real-device testing guide

Everything in this repository has been verified on GitHub-hosted emulators
(API 29, 33, 36). **No physical device has been used.** This guide is the manual
test pass you should run on real hardware before treating a release as shippable,
with the specific things emulators cannot catch.

Budget about 25 minutes for the core pass.

---

## 0. Prerequisites

- A device on Android 10 (API 29) or newer. Ideally test **two**: one modern
  (Android 13+) and one with an OEM skin (Samsung, Xiaomi, OnePlus, Oppo, Vivo).
- The published APK and `SHA256SUMS.txt` from the GitHub Release.
- Optional: `adb` on a computer, for logcat.

## 1. Verify the download before installing

This is the only step that proves you have the artifact the project published.

```bash
sha256sum AstraTouch-v1.0.2-personal-debug.apk
cat SHA256SUMS.txt          # the two hashes must match exactly
```

Check the signature. Either tool works; `keytool` ships with the JDK and needs no
Android SDK:

```bash
keytool -printcert -jarfile AstraTouch-v1.0.2-personal-debug.apk
# or, with Android build-tools:
apksigner verify --verbose --print-certs AstraTouch-v1.0.2-personal-debug.apk
```

Expected:

- `Verifies`, with `Verified using v2 scheme … : true`
- certificate DN **`CN=AstraTouch Debug, O=AstraTouch Personal, C=BD`**

  This is **not** `CN=Android Debug`. AstraTouch uses a purpose-built, *stable*
  debug identity rather than the throwaway key Android generates per machine. It is
  a debug certificate by intent, but a specific and reproducible one.
- certificate SHA-256
  `198476e1822fb79f6c85c426c5c450c66c87db2982b88f90139c1eae19b3920e`,
  matching `signingCertificateSha256` in `release-metadata.json` and the fingerprint
  pinned in `.github/signing-identity.txt`

Then check the package metadata:

```bash
aapt2 dump badging AstraTouch-v1.0.2-personal-debug.apk | head -3
```

Expect `package: name='com.astratouch.app' versionCode='10002' versionName='1.0.2'`,
`sdkVersion:'29'`, `targetSdkVersion:'36'`. The application id must be exactly
`com.astratouch.app` — **not** `com.astratouch.app.debug`. The `.debug` variant is a
development build that installs as a separate app and cannot update this one.

## 2. Install

1. Copy the APK to the device (USB, or
   `adb install -r AstraTouch-v1.0.2-personal-debug.apk`).
2. Tap it in Files/Downloads. Allow your file manager to install unknown apps when
   prompted.
3. Upgrading from an earlier AstraTouch release should work **in place**, keeping
   your data — every release is signed with the same certificate.
4. You will need to uninstall first only if you installed a build signed with a
   *different* key: a development `debug` build, or anything from before the signing
   strategy changed in v1.0.2.

**Watch for:** a `Parse error` means a truncated download (re-verify the hash).
A signature-mismatch error means you are installing a build signed by someone else.

## 3. First-run and permissions

| Step | Expected | What to watch for |
| --- | --- | --- |
| Launch | Splash, then onboarding (4 pages) | No crash on a cold start; splash does not hang |
| Complete onboarding | Dashboard | Onboarding is not shown a second time |
| Grant *Display over other apps* | System toggle enables, app updates to "granted" | Some OEMs hide this setting two levels deep |
| Enable the accessibility service | Warning dialog describing global actions | The description must say it does not read screen content |
| Tap **Start bubble** | Bubble appears at the right edge within ~1 s | No flicker; notification appears in the shade |

**OEM check:** on Samsung/Xiaomi, background the app and return after 30 s. The
bubble must still be there. If it vanished, grant the battery-optimisation
exemption the app offers and retest.

## 4. Bubble behaviour (things emulators get wrong)

| Test | Expected |
| --- | --- |
| Drag the bubble around | Follows the finger exactly; no lag or jump |
| Release near an edge | Snaps to the nearest edge |
| Rotate the device | Bubble stays on screen, position preserved, menu still opens |
| Enable auto-hide, wait | Bubble dims/hides, returns on interaction |
| Change bubble shape, opacity, colour | Applied live without recreating the service |
| Toggle the device between light/dark system theme | App theme follows only where designed |

**Watch for:** a bubble that drifts after rotation (inset maths), a bubble that
becomes unreachable behind the nav bar, or visible jank while dragging on a
low-end device.

## 5. Radial menu

| Test | Expected |
| --- | --- |
| Tap the bubble | Menu opens centred on the bubble; items are separated |
| Tap near a screen corner | Menu stays fully on screen (clamped) |
| Switch layouts (all 8) | Each looks **different** — `NESTED` is two rings, `MULTI_PAGE` sits higher |
| Page to a second page | Paging indicator changes, items swap |
| Long-press / edit an item | Label and icon can be changed and persist |

## 6. Actions — on a real device this is where OEM differences appear

Trigger each of these from the radial menu or the action library:

- **Home, Back, Recents** — must act on the *foreground* app, not AstraTouch
- **Notifications, Quick Settings** — shade opens
- **Screenshot** — needs Android 11+; a toast/notification confirms the save
- **Lock screen** — device locks (device-admin or accessibility path)
- **Volume up/down, Brightness up/down** — the physical/system value changes
- **Flashlight** — torch turns on, then off
- **Wi-Fi / Bluetooth / Internet / Sound panels** — correct settings panel opens
- **Open an app from the launcher** — the app starts

**Watch for:** an action that reports success but does nothing. That is the exact
failure this project refuses to hide — report it with the action id.

While you test, have logcat running and filtered:

```bash
adb logcat -s AstraTouch AstraOverlay AstraAction AstraAccessibility
```

## 7. Screenshots and recordings — the v1.0.1 fixes

This is the highest-value part of the manual pass, because v1.0.0 shipped these
broken.

1. Take a screenshot → open **Screenshot studio** → the capture is listed.
2. Tap **Share** on the capture → the Android share sheet opens with an image.
   *(v1.0.0 threw here.)*
3. Tap **Edit**, crop, save → a new file appears.
4. Tap **Record screen**, approve the capture dialog → record for ~5 s → **Stop**.
5. In **Recordings**, the MP4 is listed with its size.
6. Tap **Play** → a video app opens and plays it. *(v1.0.0 had no recordings UI.)*
7. Tap **Delete** → the row disappears.
8. Open a banking app (or any `FLAG_SECURE` screen) and try to screenshot →
   AstraTouch must say the app blocks screenshots, not produce a black file.

## 8. Macros, including the condition fix

1. Create a macro: Home → delay 1 s → Recents. Run it; both happen in order.
2. Add a step using the **Battery saver is on** condition. With the saver off, the
   step is *skipped* and the summary says so; turn the saver on and it should pass.
   *(v1.0.0 always failed this condition.)*
3. Set a delay of 60 s → it is clamped to 10 s, and the saved value shows 10 s.
4. Try to add a step that opens the macro manager → it is blocked (loop
   prevention).
5. Run a macro with a missing permission → the run reports the failing step and the
   reason, and history records it.

## 9. Clipboard and notes

1. Turn clipboard history on, copy some ordinary text, open the clipboard screen →
   the text is captured.
2. Copy a 6-digit code and a string like `password=hunter2` → both are **refused**
   with an explanation.
3. Copy from a password manager that marks the clip sensitive → also refused.
4. Add a note, pin it, edit it, delete it.

## 10. Privacy Center — verify the promise

1. **Export** your data → a JSON file is produced; open it and confirm it contains
   profiles/macros/themes and **no** clipboard history and no notes.
2. **Import** that file → data is restored.
3. Record a short screen capture, then use **Delete all data**.
4. Verify with `adb` that the recording is actually gone:

```bash
adb shell run-as com.astratouch.app ls files/recordings
# expect: empty  (v1.0.0 left the MP4s here)
adb shell run-as com.astratouch.app ls files/captures
# expect: empty
```

*(`run-as` requires a debuggable build. The published `debugPersonal` artifact is
deliberately **not** debuggable, so this step applies only to development builds; if it refuses, confirm
from the app — the Recordings section must be empty and show "No recordings yet".)*

## 11. Reboot behaviour — the Android 12+ fix

1. Enable **start at boot** in bubble settings.
2. Reboot the device.
3. **Expected on Android 12+:** a "Bubble is ready" notification. Tap it → the app
   opens and the bubble appears.
   *(v1.0.0 tried to start the service from the boot broadcast, which Android
   forbids — you would have seen no bubble and an exception in logcat.)*
4. **Expected on Android 10/11:** the bubble may come back by itself.
5. Also test updating the app (install the same APK over itself) — the bubble
   should restore without any tap, because `MY_PACKAGE_REPLACED` is exempt.

## 12. Accessibility review

- Enable TalkBack. Navigate the dashboard, the radial menu and the calculator.
  Every control must announce a meaningful label; the radial items must be
  reachable one at a time.
- Set the system font size to the largest setting → text must not clip or overlap.
- Set animator duration scale to 0 (Developer options) → animations collapse
  rather than half-finishing.
- Turn haptics off in AstraTouch → no vibration on bubble interaction.

**Not yet verified by anyone:** a full TalkBack audit with a screen-reader user.
Treat any finding here as a genuine bug report.

## 13. Battery and stability

- Leave the bubble running for an hour with auto-hide on → check
  Settings → Battery → AstraTouch usage is low and the app is not holding the
  device awake.
- Run for a day. Watch for: the bubble disappearing without a crash log (OEM task
  killer), the service being killed and not restarting, or duplicate bubbles.

## 14. What to report

For any failure, include:

1. Device model, Android version, OEM skin version.
2. App version from **Settings → About**.
3. The exact action or screen, and what you expected vs. what happened.
4. `adb logcat -s AstraTouch AstraOverlay AstraAction AstraAccessibility` output
   around the failure.
5. **About → Copy diagnostics** — it contains version, SDK level, granted accesses
   and service state, and deliberately contains no personal data.

## Known-failing-by-design (do not report)

These are documented platform limits, not bugs. See
[KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md).

- Gestures performed on *other* apps are not intercepted — Android offers no API.
- Clipboard is only captured while AstraTouch has focus (Android 10+ rule).
- Screenshots require Android 11+ through accessibility.
- `FLAG_SECURE` windows cannot be captured.
- True blur requires Android 12+; earlier versions use a translucent gradient and
  say so.
- OCR is not shipped; its switch is disabled and labelled reserved.
- No cloud sync, ever — there is no `INTERNET` permission.
