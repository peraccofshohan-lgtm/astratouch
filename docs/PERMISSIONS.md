# Permissions

AstraTouch requests the smallest set of Android capabilities that makes the
documented features work, and it explains every one of them inside the app
(**Permission Center**).

## Manifest permissions

| Permission | Type | Required | Used by |
| --- | --- | --- | --- |
| `SYSTEM_ALERT_WINDOW` | Special access | **Yes** for the overlay | Floating bubble, radial menu |
| `BIND_ACCESSIBILITY_SERVICE` | Service binding (user-enabled) | **Yes** for global actions | Home/Back/Recents, shade, Quick Settings, lock, screenshots |
| `WRITE_SETTINGS` | Special access | No | Brightness, auto-rotation |
| `POST_NOTIFICATIONS` | Runtime (13+) | No | Ongoing overlay notification, timer/macro alerts |
| `VIBRATE` | Normal | No | Haptics |
| `FOREGROUND_SERVICE` (+ `SPECIAL_USE`, `MEDIA_PROJECTION`) | Normal | n/a | Overlay service, screen recorder |
| `RECEIVE_BOOT_COMPLETED` | Normal | No | Restoring the bubble after reboot (opt-in) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Normal | No | Guiding the user to the exemption screen |
| `CAMERA` (declared at runtime only) | Runtime | No | Torch toggle |

### Deliberately absent

- **`INTERNET`** — never declared. AstraTouch has no networking code path at all.
- `QUERY_ALL_PACKAGES` — the app uses a launcher-`<queries>` intent filter instead.
- `READ_/WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE` — screenshots stay in
  app-private storage and are shared through a `FileProvider` URI.
- `READ_SMS`, `READ_CONTACTS`, `RECORD_AUDIO`, location, `READ_PHONE_STATE`.
- Accessibility capabilities beyond what is listed in
  [`accessibility_service_config.xml`](../app/src/main/res/xml/accessibility_service_config.xml):
  `canRetrieveWindowContent="false"`.

## Requesting flow

1. **Onboarding** explains the product without triggering a single system dialog.
2. **Permission Setup** lists every access with purpose, dependents and current
   state; each row opens the exact Android settings screen.
3. **Progressive**: nothing else is requested until the user reaches the feature
   that needs it (e.g. the torch asks for camera on first use).
4. **Denial handling**: every dependent action returns a `NeedsPermission` result
   with an explanation instead of failing silently.
5. **Revocation**: state is recomputed on each screen resume; a revoked access
   immediately downgrades capability and the UI shows the new state.

## Per-permission details

### Display over other apps (`SYSTEM_ALERT_WINDOW`)
Opens via `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` with the package URI.
Without it: the bubble cannot exist; everything else in the app still works and
the Quick Control Center can run actions from within AstraTouch.

### Accessibility service
Enabled only from Android Settings (`ACTION_ACCESSIBILITY_SETTINGS`). The service
is bound by the system, not started by AstraTouch. Configuration requests:
window-state events only, no window content, gestures allowed.
Without it: navigation actions, panels, lock screen and screenshots report
"needs the accessibility service" and the rest of the app remains usable.

### Modify system settings (`WRITE_SETTINGS`)
Opens via `Settings.ACTION_MANAGE_WRITE_SETTINGS`. Used for exactly two values:
`SCREEN_BRIGHTNESS` and `ACCELEROMETER_ROTATION`.
Without it: those two actions explain the limitation and offer the system
settings page instead.

### Notifications (Android 13+)
Requested through the normal runtime dialog. AstraTouch works without it: the
foreground service still runs, only its notification is hidden by the system.

### Camera
Requested only when the user first taps the Flashlight action. AstraTouch never
opens a camera preview and never stores imagery; it only calls
`CameraManager.setTorchMode`.

### Battery optimisation exemption
Optional. Opens `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`. Helps on OEM
builds that aggressively kill background services.

### Device admin (optional, `force-lock` only)
The Lock Screen action can use a device-admin receiver whose policy XML requests
a single capability: `force-lock`. No wipe, no password policy, no camera disable.
If admin is not activated, the action falls back to the accessibility
`GLOBAL_ACTION_LOCK_SCREEN`.

## Security posture

- All manifest components are non-exported except `MainActivity` (launcher) and
  the accessibility service / boot receiver, which Android requires to be
  exported.
- PendingIntents use `FLAG_IMMUTABLE`.
- `android:allowBackup="false"` and data-extraction rules exclude databases,
  preferences and files, so local configuration is not copied into cloud backups.
- No secrets, keys or credentials exist in the repository (see
  [RELEASE_SIGNING.md](RELEASE_SIGNING.md)).
