# Release signing

AstraTouch release APKs are signed with a dedicated release keystore that never
enters the repository. GitHub Actions decodes it from repository secrets at build
time and deletes it with the runner.

> **Losing the keystore means you can never update the app for existing users.**
> Android identifies an app's updates by signing certificate. Back it up in at
> least two offline locations.

## 1. Create the release keystore

Run this once, on a trusted machine, with JDK 17+ installed:

```bash
keytool -genkeypair \
  -v \
  -keystore astratouch-release.jks \
  -alias astratouch \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storetype JKS
```

You will be prompted for:

- a **keystore password** (store password),
- your name/org details (they appear in the certificate),
- a **key password** (you may press Enter to reuse the store password).

Recommended: use a long, randomly generated password from a password manager
(32+ characters) and store it there, not in a shell history.

Verify the keystore:

```bash
keytool -list -v -keystore astratouch-release.jks -alias astratouch
```

## 2. Preserve it safely

- Copy `astratouch-release.jks` plus the passwords into a password manager's
  secure file store or an encrypted offline drive.
- **Never** commit it. `.gitignore` already excludes `*.jks`, `*.keystore`,
  `key.properties` and `*.p12`.
- Never paste the base64 blob or passwords into an issue, PR, chat or CI log.

## 3. Encode as base64

```bash
# macOS
base64 -i astratouch-release.jks | tr -d '\n' > keystore.b64

# Linux
base64 -w 0 astratouch-release.jks > keystore.b64

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("astratouch-release.jks")) | Set-Content keystore.b64
```

Sanity check the round trip before uploading:

```bash
base64 --decode keystore.b64 > roundtrip.jks
cmp astratouch-release.jks roundtrip.jks && echo "identical"
```

## 4. Configure the four repository secrets

Repository → **Settings → Secrets and variables → Actions → New repository secret**:

| Secret name | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | contents of `keystore.b64` (single line) |
| `ANDROID_KEYSTORE_PASSWORD` | store password |
| `ANDROID_KEY_ALIAS` | `astratouch` |
| `ANDROID_KEY_PASSWORD` | key password |

Then delete the local `.b64` file and clear your shell history entry if you
pasted anything.

## 5. Validate the configuration

The release workflow fails fast with an explicit message when any secret is
missing:

```
::error::Release signing secrets are missing: ANDROID_KEY_ALIAS. See docs/RELEASE_SIGNING.md.
```

To validate without publishing, run the workflow manually
(**Actions → Android Release → Run workflow**) with the `release_tag` input left
empty only if a matching tag already exists; otherwise push a throwaway tag such
as `v9.9.9` and delete it afterwards. The **Verify signature and manifest
metadata** step prints:

```
Verified using v2 scheme (APK Signature Scheme v2): true
Signer #1 certificate DN: CN=..., O=...
Signer #1 certificate SHA-256 digest: <hex>
```

A build signed with the Android debug key (`CN=Android Debug`) is rejected by the
pipeline.

## 6. Rotate credentials safely

Two different operations are often confused:

**Changing the password or alias (same key).** Just re-encode the *same*
keystore after changing its password with `keytool -storepasswd` /
`keytool -keypasswd`, then update the three password/alias secrets. The signing
identity — and therefore update compatibility — is unchanged.

**Replacing the key (new identity).** This breaks updates: Android will refuse to
install the new APK over the old one. Only do this deliberately, for example
after a suspected key compromise. Then:

1. Generate a new keystore with a **new alias**.
2. Update all four secrets.
3. Publish the next release under a **new applicationId** (e.g.
   `com.astratouch.app.next`) or instruct users to uninstall and reinstall.
4. Document the event in `CHANGELOG.md` and `SECURITY.md`.

Never rotate keys silently.

## 7. Verify a signed APK yourself

```bash
# Using Android build-tools
apksigner verify --verbose --print-certs AstraTouch-v1.0.0.apk

# Check the manifest values
aapt2 dump badging AstraTouch-v1.0.0.apk | head -5

# Check the hash against the release
sha256sum AstraTouch-v1.0.0.apk
cat SHA256SUMS.txt
```

Expected output includes a non-debug certificate, `applicationId='com.astratouch.app'`,
`versionName='1.0.0'`, `versionCode='10000'`, `sdkVersion:'29'`,
`targetSdkVersion:'36'`.

## 8. Local release builds (maintainers only)

The Gradle build resolves signing material in this order:

1. `ASTRA_KEYSTORE_FILE` + `ASTRA_KEYSTORE_PASSWORD` + `ASTRA_KEY_ALIAS` +
   `ASTRA_KEY_PASSWORD` environment variables (what CI uses).
2. An untracked `key.properties` in the repository root:

   ```properties
   storeFile=/absolute/path/astratouch-release.jks
   storePassword=...
   keyAlias=astratouch
   keyPassword=...
   ```

If neither is present, `assembleRelease` produces an **unsigned** APK. That is
intentional: the release pipeline's gate refuses to publish it.

## Verified automation limits (observed, not assumed)

The token used to build this repository has `admin: true` on the repository but is
still refused by two endpoints. Both were tested, not guessed:

| Call | Result |
| --- | --- |
| `GET /repos/{owner}/{repo}/actions/secrets` | `403 Resource not accessible by integration` |
| `GET /repos/{owner}/{repo}/actions/secrets/public-key` | `403 Resource not accessible by integration` |
| `POST /repos/{owner}/{repo}/actions/workflows/{id}/dispatches` | `403 Resource not accessible by integration` |

Consequences:

- Secrets must be added by a person with admin scope, either through
  `scripts/setup-signing.sh` or Settings → Secrets and variables → Actions.
- A manual dispatch (`gh workflow run`) has to be triggered by a human; a pushed
  tag works from automation because tag pushes are ordinary git writes.

## What the pipeline does when secrets are absent (verified)

Pushing tag `v1.0.0` produced release run **`44483918522`**:

| Step | Outcome |
| --- | --- |
| Resolve and validate version tag | passed |
| Validate version consistency with `gradle.properties` | passed |
| Run unit tests | passed |
| Lint (release blocking) | passed |
| Fail clearly when signing secrets are missing | **failed**, with an explicit message naming all four secrets |
| Decode keystore, signed build, signature check, badging, checksums, publish, post-publish re-verify | all skipped |
| Upload workflow artifacts | no APK to upload — logged as a warning, not a fake success |

This is the intended behaviour: no debug-signed APK is ever published, and the run
is red so the gap cannot go unnoticed.
