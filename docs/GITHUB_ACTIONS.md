# GitHub Actions

Four workflows live in `.github/workflows/`. All use Gradle 8.13 with JDK 21 on
`ubuntu-latest`.

| Workflow | Trigger | Purpose |
| --- | --- | --- |
| `android-ci.yml` | push to `main`, `arena/**`; manual | Unit tests → lint → debug APK, uploads artifacts |
| `android-pr.yml` | pull requests to `main` | Verifies every commit before merge |
| `android-instrumentation.yml` | push/PR to `main`; manual | Emulator matrix (API 29/33/36) instrumentation tests |
| `android-release.yml` | tag `vX.Y.Z`; manual | Gated, verified, signed release |

## `android-ci.yml`

```
checkout → JDK 21 → gradle/actions/setup-gradle → wrapper validation → toolchain dump
   → :app:testDebugUnitTest  (tee build-logs/unit-tests.log)
   → :app:lintDebug          (tee build-logs/lint.log)
   → :app:assembleDebug      (tee build-logs/assemble-debug.log)
   → [on failure] .github/scripts/report-failure.sh build-logs
   → upload astratouch-debug-apk
   → upload astratouch-test-reports
```

Concurrency group `ci-${{ github.ref }}` cancels superseded runs on the same ref.

### Failure annotations

`.github/scripts/report-failure.sh` is what makes CI failures debuggable without a
local toolchain. It:

- prints a `::group::` block per non-empty log,
- extracts every `e: file:///….kt:line:col: …` Kotlin compile error,
- extracts `Execution failed for task` lines with 12 lines of "What went wrong"
  context,
- greps the lint log for `Error:` entries and the lint HTML/XML report for
  `<issue … severity="Error"`,
- emits each finding as a `::error file=…,line=…,title=…::message` annotation, so
  the error appears inline on the "Files changed" tab and in the Actions overview,
- always prints the last 80 lines of each log.

## `android-pr.yml`

Runs on `pull_request` with `contents: read` and no write permissions. It is the
same pipeline as CI but never uploads long-lived artifacts, keeping fork PRs
cheap and safe.

## `android-instrumentation.yml`

Matrix:

| API | Target | Why |
| --- | --- | --- |
| 29 | `default` | Minimum supported version |
| 33 | `google_apis` | POST_NOTIFICATIONS era |
| 36 | `google_apis` | Current target |

The matrix runs with `max-parallel: 1`. That is deliberate: a cold Gradle
dependency graph downloads ~700 MB from Google's Maven mirror, and three
simultaneous cold jobs intermittently fail together with `Could not resolve`
errors (observed on run `44485199536`, while the third API level passed on the
same commit). Serialising the matrix means only the first level pays for a cold
graph and the rest restore the cache it saves. `fail-fast: false` is kept so one
API failure never hides the others.

Before the emulator boots, a **warm-up** step runs `assembleDebugAndroidTest` up to
five times with backoff (0/30/60/120/120 s). It only retries when the log shows a
network-level resolution failure; a real compile error stops the job immediately
with the `e: file:///…` lines extracted, so a genuine breakage is never masked as
"flaky".

Steps enable KVM (`99-kvm4all.rules` + `udevadm trigger`), then run
`reactivecircus/android-emulator-runner@v2` with
`-no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none`
and execute `:app:connectedDebugAndroidTest`. Reports and logs are uploaded per API
level with `if: always()`.

The job has a 90-minute timeout because a cold emulator boot is slow.

After the tests, an **"Assert tests actually executed"** step parses the JUnit XML
and:

- fails if no result XML was produced at all,
- fails if the XML contains zero tests,
- fails if any failure/error count is non-zero,
- writes a per-API `| Tests | Failures | Errors | Skipped |` table to the step
  summary.

That step exists because run `44478519934` reported green while having executed no
tests — the exact hollow pass a CI badge is supposed to prevent.

## `android-release.yml`

Triggered by a tag matching `v*.*.*` or manually with a `release_tag` input.
Environment: `release`. Permissions: `contents: write` (needed to create the
GitHub Release).

Gates, in order — the workflow fails at the first unmet gate and publishes
nothing:

1. **Tag shape** — must be `vMAJOR.MINOR.PATCH`.
2. **Version consistency** — `gradle.properties` `astratouch.versionName` must
   equal the tag without `v`, and `versionCode` must equal
   `major*10000 + minor*100 + patch`.
3. **Stable signing identity** — `scripts/derive-debug-keystore.py` derives the
   key and pairs it with the committed public certificate. The workflow then asserts
   the certificate fingerprint matches `.github/signing-identity.txt`. **No secret
   is read.**
4. **Unit tests** — `:app:testDebugUnitTest` must pass.
5. **Lint** — `:app:lintDebug` (errors fail the build).
6. **Build** — `:app:assembleDebugPersonal`. The keystore is written to a
   temporary `RUNNER_TEMP` path and dies with the runner.
7. **APK presence** — the artifact must exist and be larger than 1 MB, so a
   truncated or unsigned output cannot slip through.
8. **Signature verification** — `apksigner verify --verbose --print-certs` must
   report `Verifies` with a v2 (or v3) scheme, and the signer's SHA-256 must equal
   the pinned fingerprint. It rejects an APK signed by Android's automatic debug
   key.
9. **Badging assertions** — `aapt2 dump badging` must report
   `package name='com.astratouch.app'`, the expected `versionName` and
   `versionCode`, `sdkVersion:'29'`, `targetSdkVersion:'36'`.
10. **Checksums** — `SHA256SUMS.txt` plus `release-metadata.json`
    (`tag`, commit, version, `versionCode`, `minSdk`, `targetSdk`, `builtAt`,
    `workflowRun`, `sha256`, `signingCertificateSha256`). The checksum is verified
    once more with `sha256sum -c`.
11. **Publish** — `gh release create`. An existing release for the tag is a hard
    error rather than being overwritten, so a published artifact is never replaced
    silently.
12. **Post-publish verification** — the workflow re-downloads the published APK
    asset and re-checks its SHA-256 against the recorded value.

### Required secrets

**None.** Signing is derived, not stored. See
[RELEASE_SIGNING.md](RELEASE_SIGNING.md).

The only optional secret is `ASTRA_DEBUG_SEED`, which selects a different
derivation seed. Leaving it unset is fully supported and produces the same stable
identity every time.

## Local equivalents

| CI step | Local command |
| --- | --- |
| Unit tests | `./gradlew :app:testDebugUnitTest` |
| Lint | `./gradlew :app:lintDebug` |
| Debug APK | `./gradlew :app:assembleDebug` |
| Instrumentation | `./gradlew :app:connectedDebugAndroidTest` (device/emulator attached) |
| Published (signed) APK | `./gradlew :app:assembleDebugPersonal` (see [BUILDING.md](BUILDING.md)) |

## Cost and runtime notes

- CI unit-test + lint + assemble is roughly 150–350 s on a hosted runner with a
  warm Gradle cache (`gradle/actions/setup-gradle` handles caching).
- The emulator matrix is the expensive job (~15–25 min per API level); it runs on
  `main` only, not on every feature branch push.
- `org.gradle.configuration-cache=false` is intentional: Hilt + KSP with the
  current AGP 8.11.1 combination is faster and more reliable without it here.

## Known CI warnings (harmless)

- Node 20 deprecation on `actions/*@v4`.
- `setup-java@v5` upgrade notices.
- Room `fallbackToDestructiveMigrationOnDowngrade` deprecation warning.
- `hiltViewModel` moved-package warning.
