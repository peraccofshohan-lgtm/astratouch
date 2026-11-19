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

./gradlew :app:assembleDebug          # development APK (com.astratouch.app.debug)
./gradlew :app:assembleDebugPersonal  # the published, signed APK
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:lintDebug              # Android Lint
./gradlew :app:assembleRelease        # store-style APK (unsigned without a release key)
./gradlew clean                       # clean
```

Outputs land in `app/build/outputs/apk/{debug,debugPersonal,release}/`.
Test reports: `app/build/reports/tests/`. Lint report:
`app/build/reports/lint-results-debug.html`.

## Version metadata

Version numbers are centralised in `gradle.properties` and consumed by
`app/build.gradle.kts`:

```properties
astratouch.versionName=1.0.2
astratouch.versionCode=10002
astratouch.minSdk=29
astratouch.compileSdk=36
astratouch.targetSdk=36
```

The release workflow refuses to publish unless the pushed tag matches
`versionName` and `versionCode` equals `major*10000 + minor*100 + patch`.

## Signing the published build locally

AstraTouch is a personal-use app signed with a **stable debug identity**, not a
production release key. The identity is derived, not stored:

```bash
python3 -m venv .venv && .venv/bin/pip install cryptography
.venv/bin/python scripts/derive-debug-keystore.py \
  --out /tmp/debug.p12 \
  --cert-in signing/astratouch-debug-cert.pem

export ASTRA_DEBUG_KEYSTORE_FILE=/tmp/debug.p12
export ASTRA_DEBUG_KEYSTORE_PASSWORD=astratouchdebug
export ASTRA_DEBUG_KEY_ALIAS=astratouchdebug
export ASTRA_DEBUG_KEY_PASSWORD=astratouchdebug
export ASTRA_REQUIRE_SIGNING=1     # fail instead of quietly building unsigned

./gradlew :app:assembleDebugPersonal
```

With `ASTRA_REQUIRE_SIGNING=1` the `verifyDebugSigning` task fails the build if no
keystore resolved, so a misconfigured environment produces an error rather than an
unsigned APK. No secret and no committed private key is involved — see
[RELEASE_SIGNING.md](RELEASE_SIGNING.md).

`assembleRelease` is a separate, store-style path. It stays **unsigned** unless you
supply your own release keystore; that is intentional, so a local release build
never silently borrows a debug key.

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
