# Privacy

AstraTouch is offline by construction. This document states the guarantees, the
mechanism that enforces each one, and what data exists on your device.

## Guarantees

1. **No network access.** The app does not declare `android.permission.INTERNET`
   or `ACCESS_NETWORK_STATE`. Without the permission the Linux kernel refuses
   socket creation for the app's UID, so no library, SDK or future code change
   can exfiltrate data without also changing the manifest — a visible change in
   every diff and every release.
2. **No analytics, crash reporting or advertising SDK.** Dependencies are listed
   in `gradle/libs.versions.toml`: AndroidX, Compose, Hilt, Room, DataStore,
   Kotlin Serialization, and test-only libraries. Nothing else.
3. **No account, no identifier, no telemetry.** There is no login, no device id,
   no installation id, no usage counter that leaves the process.
4. **Cloud backup is disabled.** `android:allowBackup="false"` plus
   `res/xml/data_extraction_rules.xml` excluding `database`, `sharedpref` and
   `file` from both cloud backup and device-to-device transfer.
5. **Everything is local.** Profiles, macros, notes, clipboard history, timers,
   screenshots and recordings live in app-private storage and are removed when you
   uninstall the app.
6. **Clipboard history never leaves the device and can refuse secrets.**
   `SensitiveContentFilter` rejects OTP-shaped strings, password/secret
   assignments, PEM private keys, card patterns and high-entropy tokens before
   they are stored.
7. **Screenshots and recordings are yours.** They are written to app-private
   storage and only shared through an explicit `FileProvider` URI you initiate.

## What is stored, and where

| Data | Location | Retention |
| --- | --- | --- |
| Profiles, bubble config, radial config, gesture mappings | Room `profiles`, `bubble_configs`, `radial_configs`, `radial_items`, `gesture_mappings` | Until deleted or uninstalled |
| Themes | Room `themes` | Until deleted or uninstalled |
| Macros and steps | Room `macros`, `macro_steps` | Until deleted or uninstalled |
| App favourites, groups, launch counters | Room `app_entries` | Until deleted or uninstalled |
| Notes | Room `notes` | Until deleted or uninstalled |
| Clipboard history | Room `clipboard_entries` | Capped by your retention setting; excluded from backups |
| Timers | Room `timers` | Until deleted or uninstalled |
| User preferences (theme, haptics, defaults) | DataStore `astra_preferences` | Until cleared |
| Bubble position, last profile | DataStore | Until cleared |
| Screenshots | `cacheDir/screenshots` via `ScreenshotSaver` | Until you delete them or Android trims the cache |
| Recordings | `filesDir/recordings` | Until you delete them |
| Logs | None persisted — `Log.d` only in debug builds | — |

Nothing is written to shared/external storage, so no other app can read it and no
media-scanner index is created.

## Permissions and their privacy impact

Full detail is in [PERMISSIONS.md](PERMISSIONS.md). Summary:

- `SYSTEM_ALERT_WINDOW` — draw the bubble. Sees only what you can see; AstraTouch
  does not observe the screen through it.
- `BIND_ACCESSIBILITY_SERVICE` — global actions + screenshot. `canRetrieveWindowContent="false"`,
  so no content is read.
- `FOREGROUND_SERVICE` / `specialUse` / `mediaProjection` — keep the bubble and a
  recording alive while you use another app; the notification tells you it is on.
- `WRITE_SETTINGS` — brightness and auto-rotate actions only.
- `POST_NOTIFICATIONS` — the required foreground-service notification.
- `VIBRATE` — haptic feedback, disableable.
- `RECEIVE_BOOT_COMPLETED` — (declared) restore your enabled state after reboot.

There is no `CAMERA`, no `RECORD_AUDIO`, no `READ_CONTACTS`, no `READ_SMS`, no
`READ_PHONE_STATE`, no `ACCESS_FINE_LOCATION`, no `QUERY_ALL_PACKAGES` (the
launcher uses a `<queries>` launcher-intent filter), and no `INTERNET`.

## Your controls

- **Permission Center** — every special access, what needs it, and where to revoke it.
- **Privacy Center** — export all data as JSON, import a previously exported JSON
  (transactional; invalid files change nothing), and delete all local data.
- **Clipboard** — turn history off, set retention, and enable/disable the
  sensitive-content filter.
- **Recording** — stop at any time from the notification; the file is finalised
  immediately.
- **Accessibility** — revoke in system settings; AstraTouch detects it and degrades
  gracefully.

## Backup files

Exports are plain JSON (`BackupEnvelope`, `CURRENT_SCHEMA = 1`) containing
profiles, bubble and radial configs, radial items, gesture mappings, macros, macro
steps and themes, plus `appVersion`, `appVersionCode`, `exportedAt`, `deviceSdk`.

Exports **exclude** clipboard history, notes, screenshots, recordings and the
current bubble position. Treat an export as personal configuration data: it is not
encrypted, so store it accordingly.

## Children's data

AstraTouch collects nothing from anyone, of any age. There is nothing to disclose
under COPPA/GDPR-K beyond "no collection occurs".

## GDPR / CCPA position

No personal data is processed by the developer. There is no controller/processor
relationship because no data is transmitted. Data-subject requests (access,
erasure, portability) are satisfiable entirely on-device via Privacy Center export
and delete.

## Contact

Security and privacy reports: see [SECURITY.md](../SECURITY.md). Do not open a
public issue for a vulnerability.
