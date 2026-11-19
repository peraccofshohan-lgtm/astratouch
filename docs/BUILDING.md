# Building from source

## Toolchain

| Component | Version |
| --- | --- |
| JDK | 21 (Temurin recommended) |
| Gradle | 8.13 (wrapper committed — always use `./gradlew`) |
| Android Gradle Plugin | 8.11.1 |
| Kotlin | 2.2.21 |
| KSP | 2.2.21-2.0.4 |
| Hilt | 2.57.2 |
| Compose BOM | 2025.11.00 |
| compileSdk / targetSdk | 36 |
| minSdk | 29 |
| Build tools | 36.0.0 |

## Prerequisites

1. **JDK 21** — `java -version` must report 21.
2. **Android SDK** with platform 36 and build-tools 36.0.0:
   ```bash
   sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
   ```
3. **`local.properties`** (not committed):
   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```
   Or export `ANDROID_HOME`.

## Build commands

```bash
git clone https://github.com/peraccofshohan-lgtm/astratouch.git
cd astratouch

./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:lintDebug              # Android Lint
./gradlew :app:assembleRelease        # release APK (needs signing config, see below)
./gradlew clean                       # clean
```

Outputs land in `app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/`.
Test reports: `app/build/reports/tests/`. Lint report:
`app/build/reports/lint-results-debug.html`.

## Version metadata

Version numbers are centralised in `gradle.properties` and consumed by
`app/build.gradle.kts`:

```properties
astratouch.versionName=1.0.0
astratouch.versionCode=10000
astratouch.minSdk=29
astratouch.compileSdk=36
astratouch.targetSdk=36
```

The release workflow refuses to publish unless the pushed tag matches
`versionName` and `versionCode` equals `major*10000 + minor*100 + patch`.

## Release signing locally

`app/build.gradle.kts` reads four optional Gradle properties. When any is
missing, `assembleRelease` still succeeds but produces an **unsigned** APK, so a
local `./gradlew assembleRelease` never leaks a debug key into a release build.

```properties
# ~/.gradle/gradle.properties  (never commit)
ASTRATOUCH_STORE_FILE=/absolute/path/astratouch-release.jks
ASTRATOUCH_STORE_PASSWORD=…
ASTRATOUCH_KEY_ALIAS=astratouch
ASTRATOUCH_KEY_PASSWORD=…
```

In CI these values come from GitHub Actions secrets — see
[RELEASE_SIGNING.md](RELEASE_SIGNING.md) and `scripts/setup-signing.sh`.

## Dependencies

All versions live in `gradle/libs.versions.toml` (version catalog). No dynamic
versions, no SNAPSHOTs. Dependency verification is handled by Gradle's own
checksum validation for the wrapper plus `gradle/actions/wrapper-validation` in CI.

## Room schemas

`app/schemas/` is committed. Any change to an `@Entity` requires:

1. Bump `AstraDatabase.VERSION`.
2. Add a `Migration` object in `data/local/Migrations.kt`.
3. Register it in `.addMigrations(...)`.
4. Build once so KSP writes the new `app/schemas/…/<version>.json`.
5. Add a `MigrationTest` case asserting the previous version still upgrades.

## Build troubleshooting

| Symptom | Fix |
| --- | --- |
| `SDK location not found` | Create `local.properties` or export `ANDROID_HOME`. |
| `Unsupported class file major version` | You are on JDK 17 or older; use JDK 21. |
| Hilt `MissingBinding` | Run `./gradlew clean` after adding a `@Binds`/`@Provides`. |
| KSP errors after renaming a class | `./gradlew clean`; KSP caches generated sources. |
| Gradle daemon OOM | `org.gradle.jvmargs=-Xmx4096m` in `gradle.properties`. |
| `dl.google.com` unreachable | You are behind a firewall; use a mirror or build in CI. |

## Continuous integration

Every push to `main` or `arena/**` runs unit tests, lint and a debug build.
See [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md).
