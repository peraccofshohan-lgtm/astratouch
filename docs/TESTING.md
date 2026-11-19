# Testing

All tests run on GitHub Actions inside this repository. Verified status is
recorded in [COMPLETION_MATRIX.md](COMPLETION_MATRIX.md) — nothing is marked
TESTED without a run to point at.

## Test layers

| Layer | Command | Where it runs |
| --- | --- | --- |
| JVM unit tests | `./gradlew :app:testDebugUnitTest` | CI (`android-ci.yml`), locally |
| Android Lint | `./gradlew :app:lintDebug` | CI |
| Instrumentation / UI | `./gradlew :app:connectedDebugAndroidTest` | CI emulator matrix (`android-instrumentation.yml`) |
| Release gates | `./gradlew :app:lintRelease :app:assembleRelease` | CI release workflow |

## Unit tests (JVM)

Located under `app/src/test/java/…`.

### `gesture/GestureEngineTest`
Tap vs. double tap vs. long press; all four swipe directions; diagonal
classification; threshold and sensitivity maths; clamping; debounce; unbound
gestures; cancellation; haptics gating. 12 cases.

### `ui/overlay/RadialLayoutMathTest`
Every layout returns one position per slot; empty menus; full-circle radius
invariance; semi-circle upper half; quarter-circle sweep; grid symmetry and
bounds; compact vs. expanded radius ordering; single-item placement; angle
helper. 9 cases.

### `service/overlay/OverlayGeometryTest`
Edge snapping both sides; clamping to screen bounds; status/nav-bar insets; grid
quantisation; dock-side derivation; rotation rescaling; unknown previous bounds
fallback; menu centring near a corner; rectangle collision. 9 cases. Uses a pure
Kotlin rectangle type, so no Android graphics dependency is needed.

### `util/SensitiveContentFilterTest`
Ordinary text accepted; blank rejected; OTP shapes rejected; password
assignments rejected; PEM private keys rejected; card numbers and CVV labels
rejected; over-long content rejected; random-secret heuristic; user-facing
rejection reasons. 9 cases.

### `service/action/ActionModelTest`
API window checks (min and max), action-id uniqueness and shape, every failure
reason carries a message, capability labels, result explanation integrity.
6 cases.

### `ui/screens/ExpressionEvaluatorTest`
Arithmetic, precedence, right-associative exponentiation, unary minus, decimals,
Unicode operators, division by zero, malformed input. 8 cases.

### `domain/MacroEngineTest` (MockK)
Step ordering; disabled macro; empty macro; recursion guard; loop prevention for
macro-manager steps; partial failures continue; all-failed marked FAILED;
conditional step skipping; save-use-case normalisation and validation; gesture
coverage. 12 cases.

### `data/DatabaseTest` (Robolectric + Room)
Real SQLite at API 29/33/34: seeding creates all profiles with dependent rows;
idempotent seeding; macro seeding with six named defaults and ordered steps;
duplication; cascade delete; notes round trip; bubble position persistence
without touching other columns; step ordering normalisation; radial item foreign
keys. 9 cases.

### `data/BackupSerializationTest`
Exact round trip of the full backup envelope; human-readable JSON with schema
version; forward-compatible unknown fields; minimal payload defaults; malformed
JSON failure; theme preset identity. 6 cases.

## Instrumented tests

Located under `app/src/androidTest/java/…`, executed on real emulators.

### `MigrationTest`
Creates a v1 database by hand, runs `MIGRATION_1_2`, then `runMigrationsAndValidate`
against the current schema. Asserts preserved rows, new columns with correct
defaults, and the new `timers` table.

### `NavigationInstrumentedTest`
Boots the real graph with the real Hilt graph and Room database, asserts the
dashboard renders, asserts the navigation table declares exactly 24 unique
destinations, and navigates Settings → Permission Center.

### `CalculatorInstrumentedTest`
Types `2 + 3 =` through the actual Compose UI and asserts `5` is displayed; also
verifies `C` resets the display.

## Emulator matrix

`android-instrumentation.yml` runs instrumentation tests on **API 29** (minimum),
**API 33** (notification permission era) and **API 36** (current target), using
`reactivecircus/android-emulator-runner` with KVM acceleration.

⚠️ **Honest reporting:** local sandbox execution of Android builds is impossible
(no JDK/Android SDK, `dl.google.com` blocked). Everything above is executed on
GitHub-hosted runners; links to the runs are recorded in the completion matrix.
No physical-device testing has been performed, so OEM-specific behaviour
(Samsung/Xiaomi background managers, unique bubble rendering quirks) remains
untested and is documented in [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md).

## Writing new tests

- Pure logic goes in a plain JUnit test — no Robolectric unless Android APIs are
  genuinely required.
- Anything touching Room uses `AstraDatabase.buildInMemory(context)` under
  Robolectric or `MigrationTestHelper` on a device.
- ViewModels are tested with `runTest` and MockK fakes; do not assert on Compose
  internals.
- Compose UI tests use `createAndroidComposeRule<HiltTestActivity>()` and semantic
  matchers (`onNodeWithText`, `testTag`), never pixel comparisons.
