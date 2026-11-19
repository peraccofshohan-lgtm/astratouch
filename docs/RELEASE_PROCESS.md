# Release process

AstraTouch releases are produced only by `.github/workflows/android-release.yml`.
There is no manual APK upload, no debug-signed artifact, and no release without a
verified signature.

## One-time setup

1. **Create the signing key** (once, ever):
   ```bash
   ./scripts/setup-signing.sh
   ```
   This generates `~/.astratouch-signing/astratouch-release.jks` (4096-bit RSA,
   10 000-day validity), base64-encodes it, and uploads the four secrets.
2. **Back the keystore up** to a password manager and an offline copy. Losing it
   permanently prevents updates for existing installs.
3. **Protect the `release` environment** (Settings → Environments → `release`) with
   required reviewers if you want a human gate on publishing.

If your automation token cannot write repository secrets (as is the case for the
Arena-managed token in this repo — it returns HTTP 403 on
`/actions/secrets`), run `setup-signing.sh` yourself once with an admin-authenticated
`gh`, or paste the four values into Settings → Secrets and variables → Actions.

## Cutting a release

```bash
# 1. Bump the version — both values must agree
#    gradle.properties:
#      astratouch.versionName=1.1.0
#      astratouch.versionCode=10100
$EDITOR gradle.properties

# 2. Update the changelog
$EDITOR CHANGELOG.md
$EDITOR docs/releases/v1.1.0.md    # copy docs/releases/v1.0.0.md as a template

# 3. Verify locally (or let CI do it)
./gradlew :app:testDebugUnitTest :app:lintDebug

# 4. Merge to main
git commit -am "release: v1.1.0"
git push origin main

# 5. Tag and push — this starts the release workflow
git tag -a v1.1.0 -m "AstraTouch 1.1.0"
git push origin v1.1.0
```

The workflow validates that the tag matches `versionName` and that
`versionCode == major*10000 + minor*100 + patch` **before** it builds anything.

## Version numbering

`versionCode = major × 10000 + minor × 100 + patch`

| versionName | versionCode |
| --- | --- |
| 1.0.0 | 10000 |
| 1.0.1 | 10001 |
| 1.1.0 | 10100 |
| 2.3.4 | 20304 |

This leaves room for 99 patches and 99 minors per major without ever colliding.

## Pipeline gates

| # | Gate | Failure mode |
| --- | --- | --- |
| 1 | Tag matches `v\d+\.\d+\.\d+` | Abort, no artifacts |
| 2 | `versionName` == tag, `versionCode` == formula | Abort |
| 3 | All four signing secrets present | Abort; never falls back to a debug key |
| 4 | Unit tests pass | Abort |
| 5 | `lintRelease` clean | Abort |
| 6 | `assembleRelease` produces an APK | Abort |
| 7 | `apksigner verify` → not `CN=Android Debug`, v2+v3 present | Abort |
| 8 | `aapt2 dump badging` → package, versionName, versionCode, minSdk 29, targetSdk 36 | Abort |
| 9 | `SHA256SUMS.txt` written and `sha256sum -c` passes | Abort |
| 10 | `gh release create` (skips if the release already exists) | Abort |
| 11 | Re-download the published APK and re-check its SHA-256 | Abort + report |

## Release assets

| File | Contents |
| --- | --- |
| `AstraTouch-vX.Y.Z.apk` | Signed, minified, resource-shrunk release APK |
| `AstraTouch-vX.Y.Z.apk.sha256` | Single-line checksum |
| `SHA256SUMS.txt` | `sha256sum -c` compatible manifest |
| `release-metadata.json` | tag, commit SHA, version, versionCode, minSdk, targetSdk, builtAt, workflow run URL, sha256 |
| `RELEASE_NOTES.md` | The body of the GitHub Release |

## After publishing

- Download the APK and install it on a device (the post-publish job only proves
  the hash; installability should be spot-checked).
- Announce with the checksum so users can verify.
- If a release is broken, do **not** delete the tag. Ship a patch release:
  Android users cannot reinstall a lower `versionCode`.

## Hotfix path

```bash
git checkout -b hotfix/1.0.1 v1.0.0
# …fix…
git commit -am "fix: …"
git checkout main && git merge --no-ff hotfix/1.0.1
# bump to 1.0.1 / 10001, tag v1.0.1, push tag
```

## Dry runs

The workflow takes a `dry_run` boolean input. With `dry_run: true` it verifies
everything that does not need signing material and then stops:

| Gate | Dry run |
| --- | --- |
| 1. Tag is semver | ✅ runs |
| 2. versionName / versionCode consistency | ✅ runs |
| 3. Signing secrets present | ⏭ skipped |
| 4. Unit tests | ✅ runs |
| 5. `lintRelease` | ✅ runs |
| 6. Signed build | ⏭ skipped |
| 7–11. Signature, badging, checksums, publish, re-verify | ⏭ skipped |

A summary table is written to the job's step summary. This is the supported way to
validate the pipeline before the signing secrets exist, and it proves the release
build still compiles and lints even when nothing is published.

Running without `dry_run` and without secrets stops at gate 3 with an explicit
error — that is the gate working, not a bug.

```bash
gh workflow run android-release.yml -f release_tag=v1.0.0 -f dry_run=true
```

## Registry distribution

AstraTouch is not published to Google Play. If you choose to, note that the
accessibility-service declaration requires a Play Console accessibility
declaration and a video demonstration; see [ACCESSIBILITY.md](ACCESSIBILITY.md).
