# Signing handover — exact steps

The release pipeline is complete and verified up to the signing gate. The only
remaining blocker is that **the four signing secrets are not in the repository**,
and the token this work was done with is refused by GitHub's secrets API:

```
GET  /repos/peraccofshohan-lgtm/astratouch/actions/secrets            → 403
GET  /repos/peraccofshohan-lgtm/astratouch/actions/secrets/public-key → 403
POST /repos/peraccofshohan-lgtm/astratouch/actions/workflows/{id}/dispatches → 403
```

You must do this once. Two options; pick either.

---

## Option A — run the script (recommended)

The script generates a 4096-bit RSA release keystore, keeps it outside the
repository, and prints or uploads the four secrets.

**Requirements:** JDK 17+ (for `keytool`), `base64`, and `gh` authenticated as a
repository admin for the upload path.

```bash
git clone https://github.com/peraccofshohan-lgtm/astratouch.git
cd astratouch
git checkout main          # or the merged PR branch

# Generate the keystore and print the four values.
# Pick a password of at least 12 characters.
ASTRA_STORE_PASSWORD='your-strong-password-here' \
  ./scripts/setup-signing.sh --non-interactive --print-values
```

The script writes:

- `~/.astratouch-signing/astratouch-release.jks` — the keystore
- `~/.astratouch-signing/store-password.txt` — the password (mode 0600)

and prints four clearly labelled values.

**Back up the `.jks` file and both passwords now.** If you lose them you can never
publish an update that existing installs will accept; you would have to ship a new
application ID.

### Upload them

If your `gh` can write Actions secrets:

```bash
# Re-run without --print-values and the script uploads for you:
ASTRA_STORE_PASSWORD='your-strong-password-here' \
  ./scripts/setup-signing.sh --non-interactive
gh secret list --repo peraccofshohan-lgtm/astratouch
```

If your token also gets 403, use the web UI (Option B).

---

## Option B — add the four secrets in the GitHub UI

1. Open
   `https://github.com/peraccofshohan-lgtm/astratouch/settings/secrets/actions`
2. Click **New repository secret** four times, once per row below.

| Secret name | Value | Notes |
| --- | --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the base64 blob | **One line, no wrapping, no newlines.** Must decode to a valid `.jks`. |
| `ANDROID_KEYSTORE_PASSWORD` | the store password | Same value you passed to `keytool -storepass`. |
| `ANDROID_KEY_ALIAS` | `astratouch` | Unless you chose a different `--alias`. |
| `ANDROID_KEY_PASSWORD` | the key password | Usually identical to the store password. |

To produce the base64 value yourself from an existing keystore:

```bash
# Linux
base64 -w 0 astratouch-release.jks > keystore.b64
# macOS
base64 -i astratouch-release.jks | tr -d '\n' > keystore.b64
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("astratouch-release.jks")) > keystore.b64
```

Paste the entire contents of `keystore.b64` as the secret value. A trailing newline
is tolerated; internal line breaks are not.

### Verify the secrets registered

```bash
gh secret list --repo peraccofshohan-lgtm/astratouch
```

Expect exactly those four names. (You cannot read the values back — GitHub does not
allow that, by design.)

---

## After the secrets exist

Do these in order. Each step states what you should see.

### 1. Merge the reviewed PR

```bash
gh pr merge 1 --repo peraccofshohan-lgtm/astratouch --merge
```

Wait for `Android CI` on `main` to go green.

### 2. Tag v1.0.1 from the merged `main`

```bash
git checkout main && git pull
git tag -a v1.0.1 -m "AstraTouch 1.0.1"
git push origin v1.0.1
```

The version bump to `1.0.1` / `versionCode 10001` is already committed; the tag must
match it or the pipeline stops at gate 2.

### 3. Watch the release run

```bash
gh run list --workflow=android-release.yml --limit 3
gh run watch <run-id>
```

Expected step outcomes:

| Step | Expected |
| --- | --- |
| Resolve and validate version tag | success |
| Validate version consistency | success — `gradle versionName=1.0.1 tag=1.0.1 versionCode=10001` |
| Run unit tests | success |
| Lint (release blocking) | success |
| Fail clearly when signing secrets are missing | **success** (this is the step that used to fail) |
| Decode signing keystore | success, prints decoded byte count |
| Build signed release APK | success |
| Locate the APK | success, prints size (expect ~8–20 MB) |
| Verify signature and manifest metadata | success — v2/v3 verified, not `CN=Android Debug`, package/version/SDK assertions pass |
| Assemble release assets | success — prints SHA-256 |
| Verify checksum matches a second time | success |
| Publish GitHub Release | success |
| Verify the published release and its assets | success — re-downloads the asset and re-checks the hash |

If any gate fails it names the exact reason and publishes nothing.

### 4. Confirm the release

```bash
gh release view v1.0.1 --repo peraccofshohan-lgtm/astratouch
gh release download v1.0.1 --pattern 'SHA256SUMS.txt' -D /tmp/rel
cat /tmp/rel/SHA256SUMS.txt
```

---

## If something fails

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Release signing secrets are missing` | one or more secrets absent | re-add all four; names are case-sensitive |
| `base64: invalid input` in Decode step | secret value contains line breaks | regenerate with `base64 -w 0` and paste as one line |
| `Failed to read key from keystore` | wrong `ANDROID_KEYSTORE_PASSWORD` or alias | re-add with the exact values used at generation |
| `apksigner verify FAILED` | APK unsigned (Gradle found no keystore) | check `ASTRA_KEYSTORE_FILE` is exported; the `verifyReleaseSigning` task now fails the build first |
| `versionName mismatch` | tag does not match `gradle.properties` | bump both, commit to `main`, tag again |
| Publish step 403 | `GITHUB_TOKEN` lacks `contents: write` | check repo Settings → Actions → Workflow permissions |

The keystore itself must never be committed. `.gitignore` already excludes `*.jks`,
`*.keystore`, `key.properties` and `*.b64`.
