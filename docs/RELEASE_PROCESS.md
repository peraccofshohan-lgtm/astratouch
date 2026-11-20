# Release process

AstraTouch releases are produced only by `.github/workflows/android-release.yml`.
There is no manual APK upload and no release without a verified signature.

Release artifacts are signed with a **stable personal-use debug identity**. That is
deliberate, not a shortcut: the same certificate is reused for every build so that
one release can update the previous one. See
[RELEASE_SIGNING.md](RELEASE_SIGNING.md).

## One-time setup

**There is none.** No keystore to generate, no secret to store, no backup to keep.

The signing identity lives in two committed files:

| File | Contents |
| --- | --- |
| `signing/astratouch-debug-cert.pem` | the public certificate (no private key) |
| `.github/signing-identity.txt` | the pinned fingerprint the pipeline enforces |

The matching private key is re-derived at build time by
`scripts/derive-debug-keystore.py` and written only to the runner's temporary
directory. Nothing needs to be provisioned, and nothing can be lost.

If you want the derivation itself to be private, set the optional
`ASTRA_DEBUG_SEED` secret **once, before the first release**, and never change it
afterwards — changing it rotates the identity and strands every existing install.

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
| 3 | Signing identity derived and fingerprint matches `.github/signing-identity.txt` | Abort; an unexpected signer would strand existing installs |
| 4 | Unit tests pass | Abort |
| 5 | Lint clean | Abort |
| 6 | `assembleDebugPersonal` produces an APK | Abort |
| 7 | APK exists and is > 1 MB | Abort |
| 8 | `apksigner verify` → `Verifies` with v2/v3, signer == pinned identity | Abort |
| 9 | `aapt2 dump badging` → package, versionName, versionCode, minSdk 29, targetSdk 36 | Abort |
| 10 | `SHA256SUMS.txt` written and `sha256sum -c` passes | Abort |
| 11 | `gh release create` (fails if the release already exists) | Abort |
| 12 | Re-download the published APK and re-check its SHA-256 | Abort + report |

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
everything up to the build and then stops:

| Gate | Dry run |
| --- | --- |
| 1. Tag is semver | ✅ runs |
| 2. versionName / versionCode consistency | ✅ runs |
| 3. Signing identity + fingerprint | ✅ runs |
| 4. Unit tests | ✅ runs |
| 5. Lint | ✅ runs |
| 6–12. Build, signature, badging, checksums, publish, re-verify | ⏭ skipped |

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
