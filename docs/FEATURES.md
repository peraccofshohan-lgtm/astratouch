# Features

Everything listed here is implemented in this repository and reachable from the
app. Capability-gated features report their real state rather than pretending to
work.

## 1. Floating overlay engine
- Draggable bubble drawn in a `TYPE_APPLICATION_OVERLAY` window.
- Edge snapping (left/right/top/bottom docking), free mode and grid snapping.
- Position persisted per profile and re-clamped on rotation, display-size change
  and multi-window resize.
- Persisted insets so the bubble never hides under the status or navigation bar.
- Seven shapes: circle, squircle, rounded square, hexagon, diamond, shield, star.
- Gradient start/end colours, border width, shadow radius, blur radius, opacity,
  icon size and icon choice (42 built-in vector icons).
- Optional edge handle and expandable edge panel flags.
- Auto-dim while the menu is open, temporary hide ("Hide bubble") and quick show
  ("Show bubble") without destroying the window.
- Haptics on interaction, configurable animation style and speed.
- Start-on-boot option, honoured only when the user enabled the bubble and the
  overlay permission is still granted.
- Event-driven: no polling, no screen scraping, no wake locks.

## 2. Radial command center
- Eight layouts: full circle, semi circle, quarter circle, grid, compact,
  expanded, nested, multi page.
- Per-layout capacity (4–12 items), paging where it makes sense.
- Configurable radius, spacing, item size, background gradient, opacity, blur,
  accent colour, animation style/speed, haptics and labels.
- Per-item custom label and icon; favourites and recently-used actions.
- Boundary protection: the panel centre is clamped so items stay on screen.

## 3. System action engine
A single `ActionRegistry` with 50+ actions across navigation, device, capture,
apps, productivity and AstraTouch routing. Each action exposes id, name,
description, icon, category, required access, minimum API, capability state,
timeout handling and a user-facing failure explanation.

Navigation: Home, Back, Recents, Notifications, Quick Settings, Lock screen,
Open radial menu.

Device: Volume up/down/panel, Brightness up/down, Flashlight, Auto-rotation,
Sound/Display/Wi-Fi/Bluetooth/Airplane/Battery settings, Internet panel, Sound
panel (12+), Bluetooth panel (13+), Do Not Disturb.

Capture: Screenshot (11+ accessibility API), Screen recording (MediaProjection),
open Screenshot Studio, crop and annotate shortcuts.

Apps: Open app, App info, AstraTouch app info, app shortcuts, app picker.

Productivity: Calculator, Notes, Timer, Clipboard.

## 4. Accessibility integration
- Explicit activation; the service never enables itself.
- Global actions and screenshot capture only.
- Window-state awareness for profile auto-switching (package name only, never
  persisted).
- Live connection state surfaced in the Permission Center and Experimental Lab.
- Graceful degradation: revoking access immediately turns dependent actions into
  `NeedsPermission` results with explanations.

## 5. Gesture engine
Single tap, double tap, long press, swipe up/down/left/right; per-gesture
sensitivity (0.2×–2.0×), swipe threshold, debounce, cancellation and haptics.
Includes a live test pad that runs the real recognition code.

## 6. Macro automation
Create, edit, duplicate, delete, reorder; action steps, delay steps and
conditional steps; sequential execution; timeout guard; loop prevention; skip
reporting; execution history; import/export through the backup system. Six
default macros ship on first run: Work Mode, Reading Mode, Gaming Mode, Media
Mode, Quick Screenshot, Productivity Mode.

## 7. App launcher
PackageManager discovery of launcher activities, search, favourites, custom
groups, launch counters and "last used" ordering, open app info, launch apps.

## 8. Smart profiles
Default, Work, Gaming, Reading, Media and Custom, each with its own bubble
config, radial config, gesture bindings and theme. Profiles can auto-activate
for a package (opt-in per profile).

## 9. Customization studio
Five presets (Midnight Obsidian, Cyber Cyan, Violet Nebula, Arctic Glass, Custom)
plus per-element colour editing, corner radius, text scale, animation speed,
haptic intensity, monospace typography and dark/light direction. Changes apply
live to the overlay.

## 10. Screenshot studio & productivity
- Screenshot capture through the accessibility API, saved to app-private storage.
- MediaProjection screen recording to MP4 with a foreground-service notification.
- Crop and freehand annotation, then share through a FileProvider URI.
- Clipboard history with sensitive-content filtering, pinning, search, delete,
  clear-unpinned and retention settings.
- Notes with pin/delete/search-by-title.
- Calculator with a safe recursive-descent parser (no `eval`, no network).
- Countdown timers.

## 11. Permission & privacy center
Per access: purpose, dependent features, current state, deep link to the exact
Android settings screen, denial/revocation handling and a documented fallback.
Plus a privacy dashboard, JSON export/import and a full local wipe.

## 12. Backup and restore
Versioned JSON envelope containing profiles, bubble/radial configs, radial items,
gesture mappings, macros + steps, themes, favourite apps and notes. Clipboard
history is deliberately excluded. Import is transactional and rolls back on
failure.
