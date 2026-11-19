# Changelog

All notable changes to AstraTouch are documented here.
This project adheres to [Semantic Versioning](https://semver.org/) and
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0] — 2026-11-19

First public release. AstraTouch is a complete, offline, native Android floating
command and accessibility control app.

### Added

**Floating overlay engine**
- Draggable bubble in a `TYPE_APPLICATION_OVERLAY` window with edge snapping,
  left/right/top/bottom docking, free and grid modes, and position persistence.
- Seven bubble shapes, gradient colours, border, shadow, blur, opacity, icon and
  icon-size controls.
- Auto-dim while the menu is open, temporary hide and quick show, optional edge
  handle and expandable edge panel.
- Rotation, inset and display-change aware geometry; haptics and animation styles.

**Radial command center**
- Eight layouts: full circle, semi circle, quarter circle, grid, compact,
  expanded, nested, multi page.
- Configurable radius, item size, background gradient, opacity, blur, accent,
  labels, animation and haptics.
- Per-item custom label and icon, favourites, recently used actions, paging and
  on-screen boundary protection.

**System action engine**
- Central `ActionRegistry` with 50+ actions across navigation, device, capture,
  apps, productivity and AstraTouch routing.
- Every action reports capability state, required access, API availability,
  timeouts and human-readable failure explanations.

**Accessibility integration**
- Documented `AccessibilityService` limited to global actions and window-state
  awareness (`canRetrieveWindowContent="false"`).
- Optional device-admin `force-lock` for the Lock Screen action.

**Gesture engine**
- Single tap, double tap, long press and four-direction swipes with sensitivity,
  debounce, cancellation, haptics and a live test pad.

**Macro automation**
- Action, delay and conditional steps; sequential execution, timeout guard, loop
  prevention, execution history and six default macros.

**Apps and profiles**
- PackageManager app discovery with search, favourites, groups and launch
  counters.
- Six profiles with independent bubble, radial menu, gestures and theme.

**Customization studio**
- Five theme presets (Midnight Obsidian, Cyber Cyan, Violet Nebula, Arctic Glass,
  Amber Ember) plus an editable Custom slot, with per-element colours, radius,
  typography, motion and haptics.

**Productivity**
- Screenshot capture via the accessibility API, MediaProjection recording, crop,
  annotate and FileProvider sharing.
- Clipboard history with sensitive-content filtering and retention controls.
- Notes, calculator (safe expression parser) and timers.

**Privacy and data**
- Permission Center with per-access purpose, dependents, fallbacks and settings
  deep links.
- Privacy Center with JSON export/import, and a full local data wipe.
- Room database with exported schemas and a tested 1 → 2 migration.

**Engineering**
- Kotlin + Compose + Material 3, Clean Architecture, MVVM, Hilt, Room, DataStore,
  Kotlin Serialization.
- Unit tests (gesture recognition, radial math, overlay geometry, sensitive
  filtering, action model, calculator, macro engine, database, serialization).
- Instrumented tests (navigation smoke, calculator UI, migration).
- GitHub Actions: CI, PR verification, emulator matrix, and a gated signed-release
  pipeline that verifies signature, applicationId, versionName, versionCode,
  minSdk, targetSdk and SHA-256 before publishing.

### Security
- No `INTERNET` permission, no analytics SDK, no advertising SDK.
- `allowBackup=false` with data-extraction exclusions.
- All PendingIntents use `FLAG_IMMUTABLE`; no exported components beyond those
  Android requires.
- Release signing material supplied only through GitHub Actions secrets.
