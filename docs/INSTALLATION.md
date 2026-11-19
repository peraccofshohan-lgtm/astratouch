# Installation

AstraTouch ships as a single signed APK attached to each GitHub Release. There is
no Play Store listing and no account.

## Requirements

- Android 10 (API 29) or newer, up to Android 16 (API 36).
- ~40 MB free storage.
- Both gesture navigation and three-button navigation are supported.

## 1. Download

Open the [latest release](https://github.com/peraccofshohan-lgtm/astratouch/releases/latest)
and download:

- `AstraTouch-vX.Y.Z.apk` — the signed application
- `SHA256SUMS.txt` — the checksum to verify your download
- `release-metadata.json` — build provenance

## 2. Verify the download (recommended)

```bash
sha256sum -c SHA256SUMS.txt      # Linux
shasum -a 256 -c SHA256SUMS.txt  # macOS
```

On Windows PowerShell:

```powershell
Get-FileHash .\AstraTouch-v1.0.0.apk -Algorithm SHA256
# compare with the value in SHA256SUMS.txt
```

Verify the signature. Either tool works — `keytool` needs only a JDK:

```bash
keytool -printcert -jarfile AstraTouch-v1.0.2-personal-debug.apk
# or, with Android build-tools:
apksigner verify --verbose --print-certs AstraTouch-v1.0.2-personal-debug.apk
```

You should see **`CN=AstraTouch Debug, O=AstraTouch Personal, C=BD`** and a valid v2
signature. AstraTouch ships debug-signed on purpose: it uses one stable identity for
every release so that updates install cleanly over each other. See
[RELEASE_SIGNING.md](RELEASE_SIGNING.md) for what that does and does not guarantee.

## 3. Install

1. Tap the downloaded APK in your notification shade or file manager.
2. Android will warn that the app is from an unknown source — that is expected for
   a self-published release. Allow your browser or file manager to install
   unknown apps when prompted.
3. Tap **Install**, then **Open**.

Minimum supported Android is 10; the installer refuses anything lower.

## 4. First run

1. **Onboarding** — four pages explaining the bubble, actions, customization and
   privacy model.
2. **Permission Setup**
   - Tap *Display over other apps* → enable **Allow display over other apps**.
   - Tap *AstraTouch accessibility service* → enable **AstraTouch Actions**.
3. **Dashboard** → tap **Start bubble**. The bubble appears near the right edge.
4. Tap the bubble to open the radial menu.

## 5. Optional permissions

Only needed for specific features, and only requested when you use them:

| Feature | Permission |
| --- | --- |
| Brightness, auto-rotation | Modify system settings |
| Flashlight | Camera |
| Lock screen (without accessibility) | AstraTouch screen-lock device admin |
| Ongoing notification on Android 13+ | Notifications |
| Bubble survival on aggressive OEM builds | Battery optimisation exemption |

## 6. Updating

Install the newer APK over the existing app — profiles, macros, notes and themes
are preserved. AstraTouch only destroys local data during a database downgrade or
when you explicitly use **Privacy Center → Delete all local data**.

Because every release is signed with the same certificate, updates install
cleanly. If Android reports a signature mismatch, you are installing an APK built
by someone else — delete it and download from the official release page.

## Uninstall

Uninstalling removes app-private data (profiles, macros, notes, clipboard history,
screenshots, recordings) as Android always does. Export a backup first from
**Privacy Center** if you want to keep your configuration.

## Troubleshooting

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) if the bubble does not appear, if the
accessibility service keeps turning itself off, or if Android blocks the install.
