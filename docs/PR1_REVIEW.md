# PR #1 review — findings

A line-by-line review of the source, workflows, security surface, tests and
documentation on `arena/51c0745e-astratouch`, performed before the v1.0.1 release.

**Outcome: the review found nine real defects, not documentation problems.** Eight
are fixed in the commits following the original PR; one is a blocked capability
of the automation environment (release secrets) explained at the end.

Every defect below was found by reading code, not by trusting the completion
matrix — which is exactly why three of them had been marked as working.

---

## Critical — would have shipped broken behaviour

### 1. Screenshot and recording sharing always threw

`ScreenshotStore.shareUri()` calls `FileProvider.getUriForFile()` for a file in
`filesDir/captures`, but `res/xml/file_paths.xml` declared only `files/shared`,
`cache/shared` and `cache/screenshots`. FileProvider throws
`IllegalArgumentException: Failed to find configured root that contains…` for any
path outside its declared roots.

*Effect:* every "Share" tap in the Screenshot studio crashed or silently failed.
Recordings had the same problem via `filesDir/recordings`.

*Fixed:* both directories are now declared, with a comment tying the XML to the
code that writes there.

### 2. "Delete all data" did not delete recordings

Privacy Center promises to delete screenshots **and** recordings.
`BackupManager.wipeAll()` removed only `filesDir/captures`. Recorded MP4s survived a
wipe that the UI described as complete.

*Effect:* a broken privacy promise — the worst class of bug in a privacy-first app.

*Fixed:* `wipeAll()` now clears `captures` and `recordings`.

### 3. Recordings were invisible and unmanageable

`ScreenCaptureService` wrote MP4s, but no screen listed, played, shared or deleted
them. Users could record and then never find the file; it accumulated in app
storage until uninstall.

*Effect:* a core feature (screen recording) was half-implemented — capture only.

*Fixed:* added `RecordingStore` plus a recordings section in the Screenshot studio
with play (via `ACTION_VIEW` + read grant), delete, size and count.

### 4. Boot restore crashed on Android 12+

`BootCompletedReceiver` called `startForegroundService()` for a `specialUse`
foreground service from `BOOT_COMPLETED`. Android 12+ throws
`ForegroundServiceStartNotAllowedException`; only a short list of service types is
exempt, and `specialUse` is not among them for boot broadcasts.

*Effect:* the bubble never came back after a reboot on Android 12/13/14, and the
receiver threw each boot.

*Fixed:* `MY_PACKAGE_REPLACED` still starts directly (it is exempt). Otherwise the
receiver posts a "Bubble is ready" notification whose tap opens `MainActivity`,
and `MainActivity` starts the service from the foreground — the only legal path.

### 5. A macro condition was hard-coded to `false`

`RunMacroUseCase.evaluateCondition()` had:

```kotlin
ConditionType.BATTERY_SAVER_ON -> false
```

The condition could never pass, so a macro using it always reported a skipped
step and never took the intended branch.

*Effect:* a shipped condition operator that silently did nothing.

*Fixed:* added `DeviceCapabilities.isPowerSaveMode()` (a plain `PowerManager` read,
no permission) and wired it in. Two regression tests now assert every declared
condition type is evaluated and none is a constant.

---

## Significant — feature claims that did not hold

### 6. Two of eight radial layouts were duplicates

`RadialLayoutMath.positions()` mapped `NESTED` and `MULTI_PAGE` to exactly the same
call as `FULL_CIRCLE`. The UI offered eight layouts; only six drew differently.

*Effect:* an advertised feature that was an alias.

*Fixed:* `NESTED` now renders two concentric rings (inner at 55 % radius);
`MULTI_PAGE` offsets items upward to leave a gutter for the pager. A new test
asserts that no two layouts produce identical geometry, so this cannot regress.

### 7. Gradle could silently emit an unsigned release APK

`app/build.gradle.kts` resolves signing from env vars and, if anything is missing,
falls through to *no signing config* — producing `app-release-unsigned.apk` while
`assembleRelease` still succeeds. The workflow's apksigner gate would catch it much
later, but the failure mode is dangerous and easy to misread.

*Effect:* "release build succeeded" could mean "produced an APK nobody can install
over an update".

*Fixed:* `ASTRA_REQUIRE_SIGNING=1` is set by the release workflow; a
`verifyReleaseSigning` task wired as a dependency of `assembleRelease` throws a
`GradleException` when signing was required but no keystore resolved.

### 8. Signature gate did not assert `apksigner` success

The verification step piped `apksigner verify` into `tee` and then grepped for the
v2/v3 lines. If `apksigner` itself failed (unsigned APK), the grep failed — but the
error surfaced as a vague grep miss rather than "the APK is unsigned".

*Fixed:* the step now fails explicitly on a non-zero `apksigner` exit, greps for
explicit "not signed / DOES NOT VERIFY" markers, and records the signer
certificate's SHA-256 so the published release attests which key signed it.

### 9. Release-signing script was interactive-only

`scripts/setup-signing.sh` required a TTY (`read -s`), so it could not be run by an
automation agent or in CI, and it uploaded secrets with `gh` — which fails with 403
on a token that lacks `secrets: write` (this repository's token does).

*Fixed:* added `--non-interactive` (passwords from env) and `--print-values` (prints
the four exact values to paste into GitHub's settings UI). Verified end-to-end with
a stubbed `keytool`.

---

## Blocked — not fixable from this environment

### Release secrets cannot be written by the automation token

| Endpoint | Result |
| --- | --- |
| `GET /actions/secrets` | `403 Resource not accessible by integration` |
| `GET /actions/secrets/public-key` | `403` |
| `POST /actions/workflows/{id}/dispatches` | `403` |

The token is repository `admin` for git and the API, but the GitHub App backing it
was not granted **Actions secrets: write** or **Actions: write**. The four signing
secrets must therefore be added by hand (once), after which the pipeline is fully
automated. Exact instructions are in the handover section of this document and in
[docs/RELEASE_SIGNING.md](RELEASE_SIGNING.md).

---

## Verified clean during the review

Checked and found correct:

- **All five `PendingIntent`s** use `FLAG_IMMUTABLE`; no `FLAG_MUTABLE` anywhere.
- **Exported components:** `MainActivity` (launcher), `AstraAccessibilityService`
  (guarded by `BIND_ACCESSIBILITY_SERVICE`), `AstraDeviceAdminReceiver` (guarded by
  `BIND_DEVICE_ADMIN`), `BootCompletedReceiver` (boot broadcasts only; does nothing
  privileged). `OverlayActionReceiver` is **not exported** and honours only three
  hard-coded actions — no arbitrary "run this action" payload.
- **`canRetrieveWindowContent="false"`** in the accessibility config; no
  `AccessibilityNodeInfo` traversal and no `dispatchGesture` call anywhere.
- **No `INTERNET` permission** in the manifest and no networking dependency.
- **Clipboard capture** honours `ClipDescription.extras[IS_SENSITIVE]` as well as
  the regex filter (OTP, password assignments, PEM keys, card patterns, entropy).
- **Bubble shapes** are genuinely distinct (`CircleShape`, rounded rects, hexagon,
  diamond, shield, five-point star) — not aliases.
- **Themes:** all five presets are distinct; a unit test pins cardinality, unique
  ids/labels, light+dark coverage and id round-tripping.
- **Profile auto-switch** is wired to the accessibility foreground package and does
  call `setActiveProfile`.
- **Foreground service types** are declared and matched at `startForeground`.
- **FileProvider** is `exported=false` with a path allowlist.
- **`allowBackup=false`** plus `data_extraction_rules.xml` excluding database,
  sharedpref and file.
- **All 24 navigation routes** exist and every screen composable is present.
- **Unit tests** (80 before this pass, now 86) pass in CI; the emulator matrix is
  green on API 29/33/36 and fails closed if no test XML is produced.

## Test counts after this review pass

| Suite | Before | After |
| --- | --- | --- |
| JVM unit tests | 80 | 86 |
| Instrumented tests | 6 | 6 |

New tests: battery-saver condition, all-conditions-evaluated, profile-condition
payload, nested-ring geometry, no-duplicate-layout geometry, multi-page gutter.
