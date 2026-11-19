# Architecture

AstraTouch follows Clean Architecture with MVVM on top, wired together by Hilt.

```
┌──────────────────────────────────────────────────────────────┐
│ UI (Compose)                                                 │
│   ui/screens/*        full-screen destinations (24 screens)  │
│   ui/navigation       single NavHost graph                   │
│   ui/overlay          bubble + radial menu composables       │
│   ui/components       design-system primitives               │
│   ui/theme            AstraTheme + design tokens             │
└───────────────▲──────────────────────────────────────────────┘
                │ StateFlow / events
┌───────────────┴──────────────────────────────────────────────┐
│ Presentation (MVVM)                                          │
│   *ViewModel per screen (HiltViewModel), SavedStateHandle    │
│   immutable UI state, no Android view references             │
└───────────────▲──────────────────────────────────────────────┘
                │ use cases
┌───────────────┴──────────────────────────────────────────────┐
│ Domain                                                       │
│   domain/model      pure Kotlin, @Serializable               │
│   domain/usecase    SwitchProfile, SaveMacro, RunMacro, …    │
│   gesture/          GestureEngine (pure recognition)         │
└───────────────▲──────────────────────────────────────────────┘
                │ repository interfaces (concrete classes here)
┌───────────────┴──────────────────────────────────────────────┐
│ Data + Platform                                              │
│   data/local        Room database, DAOs, entities, migrations│
│   data/repository   Profile, Macro, Notes, Clipboard, Apps…  │
│   data/prefs        DataStore-backed PreferencesStore        │
│   data/backup       JSON export/import, wipe                 │
│   data/mapper       entity ⇄ domain mapping                  │
│   service/          overlay service, capture service         │
│   service/action    ActionRegistry + handlers                │
│   accessibility/    AccessibilityService                     │
│   permission/       PermissionManager                        │
│   screenshot/       capture storage                          │
└──────────────────────────────────────────────────────────────┘
```

## Key design decisions

### 1. The overlay is a foreground service, not an Activity overlay
`FloatingOverlayService` is a `LifecycleService` that adds exactly one
`WindowManager` view (`TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE`). Compose
draws the bubble and the radial menu inside that view. The window is created once
and re-used; showing/hiding toggles visibility instead of add/remove, so "quick
show" costs nothing.

`OverlayController` is a process-wide interface implemented by the service and
registered in a companion `@Volatile` field. Everything else (actions, the app
UI, macros) talks to that interface — no service binding, no leaked references.

### 2. Actions are data, execution is a handler
`AstraAction` is immutable metadata (id, names, permissions, API window,
keywords). `ActionHandler` is a suspend function returning a sealed
`ActionResult`. `ActionRegistry` owns the catalogue, computes
`CapabilityState` on demand (never cached stale) and wraps every execution in a
timeout, converting exceptions into user-readable failures.

Because capability is queried before UI is rendered, AstraTouch never shows a
button that cannot work on the current device (H20/H21).

### 3. Geometry and gesture recognition are pure Kotlin
`OverlayGeometry` (snapping, insets, rotation rescaling, collision) and
`GestureEngine` (tap/double-tap/long-press/swipes, debounce, sensitivity) have no
Android dependencies, which is why they are covered by fast JVM tests and can be
reasoned about without an emulator.

### 4. Persistence is split by shape of data
- **Room** for structured, relational, queryable data: profiles, bubble/radial
  configuration, gesture mappings, macros + steps + history, themes, notes,
  clipboard, recent actions, timers.
- **DataStore Preferences** for a small set of behavioural flags (onboarding
  complete, overlay enabled, active profile id, haptics, experiments).
- **JSON (Kotlin Serialization)** for portable backup/restore with an explicit
  `schemaVersion`.

### 5. Profiles are a bundle of foreign keys
A `Profile` row references `bubble_config_id`, `radial_config_id` and a
`theme_id`. Switching profiles switches an entire configuration atomically, and
the overlay observes the active profile flow so the bubble restyles instantly.

### 6. Accessibility is minimal by construction
The service config declares `canRetrieveWindowContent="false"` and subscribes to
window-state events only. It supplies global actions
(`performGlobalAction`) and screenshot capture (`takeScreenshot`, API 30+). No
view text is read, stored or transmitted.

### 7. Threading
- UI: main thread only.
- Database and file IO: repositories suspend on `Dispatchers.IO`.
- Overlay geometry: computed on the main thread (cheap integer maths).
- Macro execution: coroutine on the ViewModel scope; delays are cancellable.

### 8. Release engineering
Single `app` module, version catalog, R8 with resource shrinking, exported Room
schemas committed under `app/schemas`, and three GitHub Actions workflows that
carry the build/test/lint/release burden on hosted runners.

## Package map

| Package | Responsibility |
| --- | --- |
| `com.astratouch.app` | Application class, MainActivity, root composable |
| `.accessibility` | AccessibilityService |
| `.data.backup` | Backup envelope, export/import, wipe |
| `.data.local` | Room database, entities, DAOs, converters, migrations |
| `.data.mapper` | entity → domain mapping |
| `.data.prefs` | DataStore preferences |
| `.data.repository` | Domain-oriented repositories |
| `.di` | Hilt modules |
| `.domain.model` | Immutable models and constants |
| `.domain.usecase` | Cross-cutting operations |
| `.gesture` | Gesture recognition |
| `.permission` | Special-access state and settings deep links |
| `.receiver` | Boot restore, device admin, overlay action receiver |
| `.screenshot` | Capture storage and sharing |
| `.service` | Overlay + screen capture services |
| `.service.action` | Action engine |
| `.service.overlay` | Controller contract, state, geometry |
| `.ui.*` | Compose UI |
| `.util` | Navigation routes, sensitive-content filter |
