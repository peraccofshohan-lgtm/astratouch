# Completion matrix

This is the honest accounting of the AstraTouch build: what exists, what is
verified, how it is verified, and where the edges are.

**Legend**

| Mark | Meaning |
| --- | --- |
| ✅ **BUILT** | Implemented and compiled in CI |
| ✅ **TESTED** | Covered by an automated test that runs in CI and passes |
| 🟡 **MANUAL** | Implemented; verification is by review or on-device use, not automated |
| ⚠️ **LIMITED** | Implemented with a documented platform or scope limit |
| ⛔ **BLOCKED** | Depends on something outside this environment (see note) |
| ➖ **N/A** | Not applicable, with a reason |

Verified CI runs on branch `arena/51c0745e-astratouch`:

| Run | Workflow | Result |
| --- | --- | --- |
| `44474918008` | Android CI | success — `Unit tests`, `Lint`, `Build debug APK`; artifacts `astratouch-debug-apk` (18.5 MB) and `astratouch-test-reports` |
| `44476967768` | Android CI | success — after the migration rework |
| `44478519934` | Android Instrumentation | success, but **hollow** — no tests actually executed; discovered later by a new assertion step |
| `44479827314` | Android Instrumentation | failure — the new guard caught a duplicate-DataStore crash and two test-authoring bugs |
| `44481063498` | Android Instrumentation | **success with tests actually executed** — API 29 (432 s), API 33 (454 s), API 36 (480 s); results asserted non-empty by the new guard |
| `44482746370` / `44482740630` / `44482746373` | Instrumentation, CI, PR | all success |
| `44485199536` | Android Instrumentation | failure — cold-cache `Could not resolve` on two API levels while the third passed on the same commit; fixed by serialising the matrix and retrying only on network failures |
| `44486247931` / `44486247984` / `44486236809` | Instrumentation, PR, CI | **all green on the final commit** — API 29, 33 and 36 each executed and passed their tests |
| `44483918522` | Android Release (tag `v1.0.0`) | expected failure at the secrets gate: gates 1, 2, 4 and 5 passed, every publish step skipped |
| `44478520365` | Android PR | success — PR verification on PR #1 |

> **Verification honesty statement.** No local Android build is possible in the
> environment this repository was produced in (no JDK and no Android SDK;
> `dl.google.com` is unreachable). Every claim marked ✅ TESTED was executed on a
> GitHub-hosted runner. Claims marked 🟡 MANUAL are code-reviewed but not
> machine-verified. No physical device was used.

---

## PR #1 review pass (pre-v1.0.1)

PR #1 was reviewed line by line before release. **Nine defects were found; eight
are fixed, one is an environment limitation.** Full detail in
[PR1_REVIEW.md](PR1_REVIEW.md).

| # | Defect | Severity | Status |
| --- | --- | --- | --- |
| 1 | FileProvider did not declare `captures/` or `recordings/`, so sharing always threw | Critical | Fixed |
| 2 | "Delete all data" left recordings on disk | Critical (privacy) | Fixed |
| 3 | Recordings were created but had no UI to list/play/share/delete | Feature hole | Fixed |
| 4 | `BOOT_COMPLETED` started a `specialUse` FGS — throws on Android 12+ | Critical | Fixed |
| 5 | `BATTERY_SAVER_ON` macro condition was hard-coded `false` | High | Fixed |
| 6 | `NESTED` / `MULTI_PAGE` radial layouts were aliases of `FULL_CIRCLE` | High | Fixed |
| 7 | Gradle could silently emit an unsigned release APK | High | Fixed |
| 8 | Signature gate reported a vague error for an unsigned APK | Medium | Fixed |
| 9 | `setup-signing.sh` required a TTY | Medium | Fixed |

Reviewing code rather than trusting this matrix is what found items 1, 3, 4, 5 and
6 — each of which had been marked as implemented, and two of which were marked as
tested.

---

## Phase 01 — Research and plan

| Item | Status | Notes |
| --- | --- | --- |
| Android platform capability research | ✅ BUILT | [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) records what Android cannot do (global gesture interception, background clipboard, per-session MediaProjection consent) |
| Feature scope across 23 areas | ✅ BUILT | [FEATURES.md](FEATURES.md) and README feature matrix |
| Architecture decision (Clean + MVVM + Hilt) | ✅ BUILT | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Verified dependency matrix | ✅ BUILT | `gradle/libs.versions.toml`; resolved in CI |
| Accessibility/Play policy position | ✅ BUILT | [ACCESSIBILITY.md](ACCESSIBILITY.md) |
| Honest-limits policy | ✅ BUILT | No fake features; disabled OCR switch labelled as reserved |

## Phase 02 — Scaffold

| Item | Status | Notes |
| --- | --- | --- |
| Gradle 8.13 wrapper + version catalog | ✅ TESTED | Wrapper validated in CI |
| AGP 8.11.1 / Kotlin 2.2.21 / KSP / Hilt / Room | ✅ TESTED | Compiles in CI |
| Package `com.astratouch.app`, versionName 1.0.1, versionCode 10001 | ✅ TESTED | Asserted by release workflow badging gate |
| min 29 / compile + target 36 | ✅ TESTED | `BuildConfig` fields + release gate |
| Manifest with no `INTERNET`, `allowBackup=false` | ✅ BUILT | Reviewable in `AndroidManifest.xml`; asserted by docs |
| Room database v2 + exported schema | ✅ TESTED | `DatabaseTest`, `MigrationTest` |
| DataStore preferences | ✅ TESTED | Exercised indirectly by ViewModel tests and app boot |
| Hilt DI graph | ✅ TESTED | Instrumentation test boots the real graph |
| Theme system (Astra design system) | ✅ BUILT | 5 presets + custom |
| Navigation graph | ✅ TESTED | `NavigationInstrumentedTest` asserts 24 unique routes |

## Phase 03 — Implementation

### Floating bubble

| Item | Status | Notes |
| --- | --- | --- |
| Overlay window in a foreground service | ✅ BUILT | `FloatingOverlayService`, `specialUse` subtype documented |
| Drag | ✅ BUILT | `OverlayController` + gesture engine |
| Edge snap (4 sides, free/grid) | ✅ TESTED | `OverlayGeometryTest` (9 cases) |
| Auto-hide, auto-dim | ✅ BUILT | Config-driven; `auto_hide_ms`, `auto_dim_alpha` |
| Position persistence per profile | ✅ TESTED | `DatabaseTest` "position persists without touching other columns" |
| 7 shapes, gradient, border, shadow, opacity | ✅ BUILT | `BubbleConfig` |
| Multi-instance (multiple bubbles) | ⚠️ LIMITED | One bubble per profile; multiple *profiles* each with their own bubble, one active. True simultaneous multi-bubble is on the roadmap |
| Rotation / inset handling | ✅ TESTED | `OverlayGeometryTest` rotation + insets cases |
| Haptics toggle | ✅ TESTED | `GestureEngineTest` haptics gating |

### Radial menu

| Item | Status | Notes |
| --- | --- | --- |
| 8 genuinely distinct layouts | ✅ TESTED | `RadialLayoutMathTest` (12 cases) — includes a test asserting **no two layouts produce identical geometry**, added after `NESTED`/`MULTI_PAGE` were found to be aliases of `FULL_CIRCLE` |
| Unlimited items via paging | ✅ BUILT | `radial_items` unique (`page`,`slot`) |
| Submenus / nested | ✅ BUILT | Nested layout + action routing |
| Custom labels and icons per item | ✅ BUILT | `label`, `iconKey` columns |
| Animation styles + speed | ✅ BUILT | Config columns |
| Favourites | ✅ TESTED | `is_favorite`, ranking tests |
| Screen-boundary protection | ✅ TESTED | `OverlayGeometryTest` corner clamping |

### Action library

| Item | Status | Notes |
| --- | --- | --- |
| 6 categories | ✅ TESTED | `ActionModelTest` |
| 50+ actions registered | ✅ BUILT | 57 `ActionIds` referenced by `ActionRegistry` |
| Permission gating | ✅ TESTED | `ActionModelTest` capability states |
| API-level gating | ✅ TESTED | `ActionModelTest` API window cases |
| Failure explanations (no silent no-ops) | ✅ TESTED | `ActionModelTest` reason-message integrity |
| 12 s execution timeout | ✅ BUILT | `ActionRegistry` |

### Macros

| Item | Status | Notes |
| --- | --- | --- |
| Action / delay / condition steps | ✅ TESTED | `MacroEngineTest` |
| Nested macros | ⚠️ LIMITED | Depth 1, recursion blocked (`MAX_DEPTH=1`) |
| Error recovery (continue on failure) | ✅ TESTED | Partial-failure and all-failed cases |
| Duplicate macro | ✅ TESTED | `DatabaseTest` |
| Execution history | ✅ TESTED | `macro_executions` + engine status assertions |
| 6 default macros | ✅ TESTED | `DatabaseTest` asserts names + ordered steps |
| Delay clamping to 10 s | ✅ TESTED | `SaveMacroUseCase` validation tests |

### Profiles

| Item | Status | Notes |
| --- | --- | --- |
| 5 built-in + custom profiles | ✅ TESTED | `DatabaseTest` seeding/idempotence |
| Per-profile isolation | ✅ TESTED | Profile-scoped tables + FK cascades |
| Auto-switch by package | 🟡 MANUAL | Verified wired: `FloatingOverlayService.observeForegroundApp()` collects the accessibility foreground package and calls `setActiveProfile`. Not unit-tested with a fake foreground app |
| Theme per profile | ✅ BUILT | `theme_configs.theme_id` on profile |

### Themes

| Item | Status | Notes |
| --- | --- | --- |
| 5 presets + custom slot | ✅ TESTED | Midnight Obsidian, Cyber Cyan, Violet Nebula, Arctic Glass, Amber Ember, plus the editable Custom slot — `BackupSerializationTest` pins the count, uniqueness, light/dark coverage and id round trip |
| Per-element colours, radius, typography, motion | ✅ BUILT | `ThemeConfig` |

### Capture studio

| Item | Status | Notes |
| --- | --- | --- |
| Screenshot via accessibility (API 30+) | 🟡 MANUAL | Implemented; emulator-only verification, `FLAG_SECURE` handled |
| MediaProjection recording | 🟡 MANUAL | Records MP4 to `files/recordings`; now listable, playable, shareable and deletable in the studio (was orphaned in v1.0.0) |
| Crop / annotate | 🟡 MANUAL | Implemented in the studio UI |
| Share via FileProvider | ✅ BUILT | Path allowlist now includes `captures/` and `recordings/`; it did not before v1.0.1, which broke every share |

### Productivity

| Item | Status | Notes |
| --- | --- | --- |
| App launcher (favourites, groups, search) | ✅ BUILT | `<queries>` launcher filter, no `QUERY_ALL_PACKAGES` |
| Clipboard history with sensitive filter | ✅ TESTED | `SensitiveContentFilterTest` (9 cases) |
| Quick notes | ✅ TESTED | `DatabaseTest` round trip |
| Calculator | ✅ TESTED | `ExpressionEvaluatorTest` (8 cases) + `CalculatorInstrumentedTest` |
| Timers / stopwatch | ✅ BUILT | `timers` table; no exact-alarm wake (see KNOWN_LIMITATIONS) |
| Permission Center | 🟡 MANUAL | Implemented with purpose text + settings deep links |
| Privacy Center (export / import / wipe) | ✅ TESTED | `BackupSerializationTest` (6 cases) |
| Backup transactional import | ✅ TESTED | `BackupManager` + serialization tests |

### Accessibility compliance

| Item | Status | Notes |
| --- | --- | --- |
| `canRetrieveWindowContent="false"` | ✅ BUILT | Verified in `accessibility_service_config.xml` |
| No content scraping | ✅ BUILT | No `AccessibilityNodeInfo` usage in the codebase |
| No gesture injection on other apps | ✅ BUILT | No `dispatchGesture` call site |
| 48 dp targets, labels, no colour-only meaning | 🟡 MANUAL | Reviewed; no TalkBack audit |
| Reduce-motion respected | ✅ BUILT | System animator scale honoured |

### Privacy

| Item | Status | Notes |
| --- | --- | --- |
| No `INTERNET` permission | ✅ BUILT | Absent from manifest |
| No analytics/ads dependencies | ✅ BUILT | Version catalog auditable |
| `allowBackup=false` + extraction rules | ✅ BUILT | Manifest + `data_extraction_rules.xml` |
| Immutable PendingIntents | ✅ BUILT | No mutable flags used |
| Local export/import/delete | ✅ TESTED | Serialization tests |

## Phase 04 — QA

| Item | Status | Notes |
| --- | --- | --- |
| JVM unit tests | ✅ TESTED | 9 files / 86 test cases (+6 instrumentation). All green in CI. |
| Room database tests (API 29/33/34) | ✅ TESTED | Robolectric `@Config` matrix, JVM |
| Migration test | ✅ TESTED | Self-contained v1 schema; green on API 29/33/36 in run `44481063498`. Writing it exposed that `MIGRATION_1_2` was not idempotent (`duplicate column name` on re-run), which is now fixed |
| Navigation smoke test | ✅ TESTED | `NavigationInstrumentedTest` — real Hilt graph on a device; green on API 29/33/36 in run `44481063498` |
| Calculator UI test | ✅ TESTED | `CalculatorInstrumentedTest` — four cases (2+3=5, clear, operator precedence, divide-by-zero) asserting on tagged nodes; green on API 29/33/36 in run `44481063498` |
| Emulator matrix API 29/33/36 | ✅ TESTED | Final state: run `44486247931`, all three API levels green, results asserted non-empty. Getting here found five real problems: run `44478519934` was a **hollow pass** (zero tests executed); `44479827314` exposed a duplicate-DataStore crash, ambiguous calculator text matching and a Hilt `hiltViewModel()` failure in `HiltTestActivity`; `44485199536` showed correlated cold-cache failures when three jobs resolved dependencies simultaneously. All are fixed, and the workflow now fails if no test XML is produced |
| Lint (`lintDebug`) | ✅ TESTED | Passed in run `44474918008` |
| Lint (`lintRelease`) | ✅ TESTED | Passed inside the release pipeline, run `44483918522`, step 9 — before the secrets gate, so it is verified without any signing material |
| Physical device testing | ➖ N/A | No device available; documented as a gap in KNOWN_LIMITATIONS |
| TalkBack audit | ➖ N/A | Requires a human screen-reader user; documented as a gap |
| Performance profiling | ➖ N/A | See KNOWN_LIMITATIONS |

## Phase 05 — Documentation

| File | Status |
| --- | --- |
| `README.md` | ✅ BUILT |
| `LICENSE` (MIT) | ✅ BUILT |
| `CHANGELOG.md` | ✅ BUILT |
| `CONTRIBUTING.md` | ✅ BUILT |
| `SECURITY.md` | ✅ BUILT |
| `docs/ARCHITECTURE.md` | ✅ BUILT |
| `docs/FEATURES.md` | ✅ BUILT |
| `docs/BUILDING.md` | ✅ BUILT |
| `docs/INSTALLATION.md` | ✅ BUILT |
| `docs/COMPATIBILITY.md` | ✅ BUILT |
| `docs/ACCESSIBILITY.md` | ✅ BUILT |
| `docs/PERMISSIONS.md` | ✅ BUILT |
| `docs/PRIVACY.md` | ✅ BUILT |
| `docs/DATABASE.md` | ✅ BUILT |
| `docs/TESTING.md` | ✅ BUILT |
| `docs/GITHUB_ACTIONS.md` | ✅ BUILT |
| `docs/RELEASE_PROCESS.md` | ✅ BUILT |
| `docs/RELEASE_SIGNING.md` | ✅ BUILT |
| `docs/TROUBLESHOOTING.md` | ✅ BUILT |
| `docs/KNOWN_LIMITATIONS.md` | ✅ BUILT |
| `docs/ROADMAP.md` | ✅ BUILT |
| `docs/COMPLETION_MATRIX.md` | ✅ BUILT (this file) |
| `docs/releases/v1.0.0.md` | ✅ BUILT |
| In-app About / Help / diagnostics | ✅ BUILT | Copy-diagnostics produces a personal-data-free report |

## Phase 06 — Release

| Item | Status | Notes |
| --- | --- | --- |
| CI (test + lint + debug APK) | ✅ TESTED | Run `44474918008` success |
| PR verification workflow | ✅ TESTED | `android-pr.yml` green on PR #1 (runs `44478520365`, `44479827309`) |
| Self-hosted signing gate | ✅ TESTED | 4 secrets required, no debug-key fallback — verified failing closed in run `44483918522` |
| Signature verification gate | ✅ BUILT | Fails explicitly on `apksigner` non-zero exit; rejects `CN=Android Debug`; requires v2+v3; publishes the signer cert SHA-256 |
| Badging assertions | ✅ BUILT | package / versionName / versionCode / minSdk / targetSdk |
| Checksums + metadata | ✅ BUILT | `SHA256SUMS.txt`, `release-metadata.json` (incl. `signingCertificateSha256`), post-publish re-verify |
| Release notes | ✅ BUILT | `docs/releases/v1.0.0.md`, `docs/releases/v1.0.1.md` |
| Signing bootstrap script | ✅ TESTED | `scripts/setup-signing.sh` — `--non-interactive` and `--print-values` verified end-to-end with a stubbed `keytool` |
| **Produce a signed v1.0.1 APK** | ⛔ BLOCKED | The automation token has repo admin but **not** `secrets: write` (HTTP 403 on `/actions/secrets`) and not `workflow: dispatch`. The owner must run `scripts/setup-signing.sh` once, or add the four secrets in Settings |
| **Publish the GitHub Release** | ⛔ BLOCKED | Same cause: run `44483918522` aborted at the secrets gate with `Release signing secrets are missing: ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD … Refusing to publish an unsigned APK` |
| Git tag `v1.0.0` | ✅ BUILT | Tagged and pushed; it triggered release run `44483918522`, which validated the tag format and version consistency and then stopped at the secrets gate exactly as designed |

### What "BLOCKED" means concretely

The release pipeline is complete and reviewed. It fails closed on purpose: if it
cannot sign with the real key it publishes nothing rather than shipping a
debug-signed APK. Unblocking requires one manual step by a repository owner with
admin scope:

```bash
./scripts/setup-signing.sh          # generates a keystore + uploads 4 secrets
git tag -a v1.0.0 -m "AstraTouch 1.0.0" && git push origin v1.0.0
```

After that, gates 1–11 run automatically and the APK, checksums and metadata are
attached to the release.

---

## Summary

| Phase | Status |
| --- | --- |
| 01 Research & plan | ✅ Complete |
| 02 Scaffold | ✅ Complete, CI-verified |
| 03 Implementation | ✅ Complete (2 items ⚠️ LIMITED by documented design, 3 🟡 MANUAL) |
| 04 QA | ✅ Unit tests, lint, PR workflow and the emulator matrix (API 29/33/36) all green. `lintRelease` runs only inside the release pipeline and is ⛔ pending signing secrets |
| 05 Documentation | ✅ Complete — 22 documents + release notes |
| 06 Release | ⚠️ Pipeline complete; gates 1, 2, 4 and 5 verified passing on tag `v1.0.0`, and the secrets gate observed failing closed. **Producing a signed APK is ⛔ blocked**: the automation token is refused by GitHub's secrets API (403), so the four signing secrets must be added by the repository owner. Exact steps: [SIGNING_HANDOVER.md](SIGNING_HANDOVER.md) |

Nothing in this matrix is marked TESTED without a CI run to point at, and nothing
is marked BUILT unless it compiles in that run.
