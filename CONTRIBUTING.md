# Contributing to AstraTouch

Thanks for wanting to help. AstraTouch is a small, opinionated codebase; the rules
below exist to keep it small and honest.

## Before you start

- Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — Clean Architecture, MVVM,
  Hilt, Room, DataStore.
- Read [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md). Some "missing"
  features are deliberate.
- For anything larger than a bug fix, open an issue first describing the
  **workflow** you want, not the implementation.

## Getting set up

```bash
git clone https://github.com/<your-fork>/astratouch.git
cd astratouch
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Requirements: JDK 21, Android SDK 36, build-tools 36.0.0. See
[docs/BUILDING.md](docs/BUILDING.md).

## Non-negotiable project rules

These are the reason the project exists; a PR that breaks one will be declined
regardless of how good it is otherwise.

1. **No `INTERNET` permission.** No analytics, crash reporting, ads, remote config
   or cloud sync. Ever.
2. **No invisible full-screen gesture-capture overlay.** No `dispatchGesture`
   automation of other apps' UI. No click automation.
3. **No content scraping.** `canRetrieveWindowContent` stays `false`. Do not add
   `AccessibilityNodeInfo` walking.
4. **No fake features.** If something is not implemented, the UI must say so
   plainly (see the disabled OCR switch in Experimental lab).
5. **No silent failures.** Actions report `NEEDS_PERMISSION` / `UNSUPPORTED_OS`
   with a human-readable explanation.
6. **No placeholder versions.** `gradle/libs.versions.toml` only contains versions
   that resolve; CI must be green.
7. **Documented limits stay documented.** Do not remove a "known limitation"
   because it is inconvenient; remove it when it is fixed.

## Code style

- Kotlin official style (`kotlin.code.style=official`); 4-space indent, 120-column
  soft limit.
- Entities never leave the repository layer — map to domain models.
- ViewModels expose immutable `StateFlow`, never mutable state.
- Composables are stateless where possible; hoist state.
- New user-visible strings go in `res/values/strings.xml`.
- No wildcard imports; keep import lists explicit (the codebase already does).
- Coroutines: `viewModelScope` for UI work, `Dispatchers.IO` for disk, structured
  concurrency everywhere, no `GlobalScope`.

## Tests

- Bug fixes need a failing test first.
- Pure logic → plain JUnit in `app/src/test`.
- Room → Robolectric (JVM) or `MigrationTestHelper` in `app/src/androidTest`.
- UI flows → Compose test in `androidTest` using `HiltTestActivity`.
- Run `./gradlew :app:testDebugUnitTest` before pushing. CI runs it too, but a red
  push wastes 5 minutes of everyone's time.

## Database changes

Follow the checklist in [docs/DATABASE.md](docs/DATABASE.md#schema-changes-checklist):
bump the version, write the migration, register it, commit the exported schema
JSON, add a migration test.

## Commits and pull requests

Conventional commits, imperative mood, scoped where useful:

```
feat(radial): support paging beyond four pages
fix(overlay): keep bubble inside bounds after rotation
docs(privacy): clarify clipboard capture restriction
test(macro): cover conditional step skipping
ci(release): assert targetSdk from badging
```

A PR should contain:

- A one-paragraph description of the user-visible change.
- The test that proves it.
- Screenshots or a short screen recording for any UI change.
- A `CHANGELOG.md` entry under `## [Unreleased]` for user-visible changes.
- Documentation updates if behaviour, permissions or limits changed.

Keep PRs focused. A 40-file refactor is fine if it is *one* refactor; two features
in one PR will be asked to split.

## Reviews

Maintainers look for: correctness, tests, no new permissions, no dishonest UI,
and that the change does not grow the APK gratuitously. Expect questions about
platform constraints you may not have hit yet — that is usually the discussion
worth having.

## Security

Do not open a public issue for a vulnerability. See [SECURITY.md](SECURITY.md).

## Licence

Contributions are accepted under the MIT licence. By opening a PR you confirm you
have the right to submit the code under that licence.
