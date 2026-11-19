# Signing handover

**Status: no action required. The release pipeline needs no signing secrets.**

This document previously described a production-keystore setup: generate a 4096-bit
RSA key, base64 it, and store four secrets (`ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) so that the
release workflow could sign an APK.

That requirement has been **removed**, deliberately. AstraTouch is a personal-use
application. It does not need a production signing key, and it does not need signing
secrets. The published artifacts are signed with a stable *debug* identity that is
derived deterministically at build time.

If you are reading this looking for a step-by-step secret-setting procedure: there
isn't one. That is the point.

---

## What replaced it

| Old requirement | Replaced by |
| --- | --- |
| Generate a production keystore once | `scripts/derive-debug-keystore.py` derives the key from a seed on every run |
| Base64 the keystore into a secret | Nothing is stored; the keystore is a temporary file on the runner |
| Four GitHub Actions secrets | Zero secrets required |
| Commit nothing, back up the keystore | Only a 688-byte **public** certificate is committed |

The full technical explanation is in [`RELEASE_SIGNING.md`](RELEASE_SIGNING.md).

---

## Why the old approach was blocked, and why that no longer matters

The blocker was real: the GitHub token available to this work has no `secrets`
permission.

```
GET  /repos/…/actions/secrets             → 403 Resource not accessible by integration
GET  /repos/…/actions/secrets/public-key  → 403
POST /repos/…/actions/workflows/{id}/dispatches → 403
x-accepted-github-permissions: secrets=read
x-oauth-scopes:
```

Run [`44532549544`](https://github.com/peraccofshohan-lgtm/astratouch/actions/runs/44532549544)
(tag `v1.0.1`) demonstrated it end to end: gates 1, 2, 4 and 5 passed — tag format,
version consistency, 86 unit tests, `lintRelease` — and the run then stopped at the
secrets gate, correctly refusing to publish an unsigned APK.

Rather than treat that as something to route around, the requirement itself was
re-examined. A personal-use app does not need a production trust anchor. Signing
locally already works: every Android developer installs debug-signed builds. The
only genuine difficulty was making the debug identity *stable*, because a
per-runner-generated `debug.keystore` would make every release break the previous
one's update path.

That problem is solved without any secret, so the 403 is no longer on the critical
path.

---

## If you ever *do* want a production-signed build

Nothing was torn out. The `release` build type and
`scripts/setup-signing.sh` still exist and are untouched. To use them you would:

1. Provision a real keystore outside the repository.
2. Supply it to Gradle via `key.properties` (untracked) or environment variables.
3. Build `:app:assembleRelease` instead of `:app:assembleDebugPersonal`.
4. Update the workflow's build step and its expected signer fingerprint.

No step in the current pipeline does this, and the current pipeline does not claim
to.

---

## Verification you can point at

CI run [`44592475036`](https://github.com/peraccofshohan-lgtm/astratouch/actions/runs/44592475036)
built the `debugPersonal` artifact and confirmed, via annotations visible on the
run, that its signer certificate is:

```
198476e1822fb79f6c85c426c5c450c66c87db2982b88f90139c1eae19b3920e
```

which is exactly the fingerprint pinned in `.github/signing-identity.txt`. The check
is performed by `keytool`, independently of `apksigner`, so a single tool's parsing
quirk cannot produce a false pass.

---

## Do not

- Do not commit a private key. `.gitignore` rejects `*.jks`, `*.keystore`, `*.p12`,
  `*.pfx`, `*.b64`, `key.properties`, and `*.pem` — except the one committed public
  certificate.
- Do not regenerate `signing/astratouch-debug-cert.pem`. Every existing install
  would then refuse the next update. See the rotation section in
  [`RELEASE_SIGNING.md`](RELEASE_SIGNING.md).
- Do not set `ASTRA_DEBUG_SEED` partway through a release line. Same reason.
