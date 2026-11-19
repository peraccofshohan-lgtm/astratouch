#!/usr/bin/env bash
#
# One-time release-signing bootstrap for AstraTouch.
#
# Generates a release keystore (if you do not already have one), base64-encodes
# it, and uploads the four required GitHub Actions secrets with `gh`.
#
# Requires: JDK 17+ (for keytool), gh CLI authenticated as a repo admin, base64.
#
# Usage:
#   ./scripts/setup-signing.sh                       # interactive: prompt + upload secrets
#   ./scripts/setup-signing.sh --keystore my.jks     # reuse an existing keystore
#   ./scripts/setup-signing.sh --dry-run             # generate + print, do not upload
#   ./scripts/setup-signing.sh --print-values        # print the 4 values to paste into the UI
#   ./scripts/setup-signing.sh --non-interactive     # read passwords from env, no prompts
#
# Non-interactive env vars (so this works in CI or a headless shell):
#   ASTRA_STORE_PASSWORD   required when --non-interactive
#   ASTRA_KEY_PASSWORD     optional, defaults to ASTRA_STORE_PASSWORD
#
# If your token cannot write Actions secrets (403 "Resource not accessible by
# integration"), use --print-values and paste each value into
# Settings -> Secrets and variables -> Actions -> New repository secret.
#
# The keystore is written OUTSIDE the repository by default (~/.astratouch-signing)
# and is never committed. Back it up somewhere safe: losing it means you can never
# update the app for existing users.

set -euo pipefail

REPO="${ASTRATOUCH_REPO:-peraccofshohan-lgtm/astratouch}"
ALIAS="astratouch"
OUT_DIR="${HOME}/.astratouch-signing"
KEYSTORE=""
DRY_RUN=0
PRINT_VALUES=0
NON_INTERACTIVE=0

while [ $# -gt 0 ]; do
  case "$1" in
    --keystore) KEYSTORE="$2"; shift 2 ;;
    --alias) ALIAS="$2"; shift 2 ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    --print-values) PRINT_VALUES=1; shift ;;
    --non-interactive) NON_INTERACTIVE=1; shift ;;
    -h|--help) sed -n '2,26p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

command -v keytool >/dev/null || { echo "keytool not found. Install a JDK 17+." >&2; exit 1; }
command -v base64 >/dev/null || { echo "base64 not found." >&2; exit 1; }

mkdir -p "$OUT_DIR"
chmod 700 "$OUT_DIR"

if [ -z "$KEYSTORE" ]; then
  KEYSTORE="$OUT_DIR/astratouch-release.jks"
  if [ -f "$KEYSTORE" ]; then
    echo "Keystore already exists at $KEYSTORE — reusing it."
  else
    echo "Generating a new 4096-bit RSA release keystore at $KEYSTORE"
    if [ "$NON_INTERACTIVE" -eq 1 ]; then
      STORE_PASS="${ASTRA_STORE_PASSWORD:-}"
      [ -n "$STORE_PASS" ] || { echo "ASTRA_STORE_PASSWORD must be set with --non-interactive." >&2; exit 1; }
    else
      read -r -s -p "Choose a keystore password (min 12 chars): " STORE_PASS; echo
      read -r -s -p "Repeat password: " STORE_PASS2; echo
      [ "$STORE_PASS" = "$STORE_PASS2" ] || { echo "Passwords do not match." >&2; exit 1; }
    fi
    [ ${#STORE_PASS} -ge 12 ] || { echo "Password must be at least 12 characters." >&2; exit 1; }
    keytool -genkeypair -v \
      -keystore "$KEYSTORE" \
      -alias "$ALIAS" \
      -keyalg RSA -keysize 4096 -validity 10000 \
      -storetype JKS \
      -storepass "$STORE_PASS" \
      -keypass "$STORE_PASS" \
      -dname "CN=AstraTouch Releases, O=AstraTouch, C=BD"
    printf '%s' "$STORE_PASS" > "$OUT_DIR/store-password.txt"
    chmod 600 "$OUT_DIR/store-password.txt"
  fi
fi

[ -f "$KEYSTORE" ] || { echo "Keystore not found: $KEYSTORE" >&2; exit 1; }

if [ -f "$OUT_DIR/store-password.txt" ]; then
  STORE_PASS="$(cat "$OUT_DIR/store-password.txt")"
else
  read -r -s -p "Keystore password: " STORE_PASS; echo
fi
if [ "$NON_INTERACTIVE" -eq 1 ]; then
  KEY_PASS="${ASTRA_KEY_PASSWORD:-$STORE_PASS}"
else
  read -r -s -p "Key password (press Enter to reuse the keystore password): " KEY_PASS; echo
  [ -n "$KEY_PASS" ] || KEY_PASS="$STORE_PASS"
fi

echo "Encoding keystore…"
case "$(uname -s)" in
  Darwin) B64="$(base64 -i "$KEYSTORE" | tr -d '\n')" ;;
  *)      B64="$(base64 -w 0 "$KEYSTORE")" ;;
esac

echo
echo "Keystore:  $KEYSTORE"
echo "Alias:     $ALIAS"
echo "Bytes:     $(wc -c < "$KEYSTORE")"
echo "Base64 len:${#B64}"
echo

if [ "$DRY_RUN" -eq 1 ]; then
  echo "--dry-run: not uploading."
  echo "ANDROID_KEY_ALIAS=$ALIAS"
  echo "ANDROID_KEYSTORE_BASE64=<${#B64} chars, withheld>"
  exit 0
fi

if [ "$PRINT_VALUES" -eq 1 ]; then
  echo "=============================================================="
  echo " Paste each value into GitHub:"
  echo "   Settings -> Secrets and variables -> Actions -> New repository secret"
  echo " Repo: $REPO"
  echo "=============================================================="
  echo
  echo "--- Secret 1 of 4: ANDROID_KEY_ALIAS ---"
  printf '%s\n' "$ALIAS"
  echo
  echo "--- Secret 2 of 4: ANDROID_KEYSTORE_PASSWORD ---"
  printf '%s\n' "$STORE_PASS"
  echo
  echo "--- Secret 3 of 4: ANDROID_KEY_PASSWORD ---"
  printf '%s\n' "$KEY_PASS"
  echo
  echo "--- Secret 4 of 4: ANDROID_KEYSTORE_BASE64 (${#B64} chars, one line) ---"
  printf '%s\n' "$B64"
  echo
  cat <<'EOT'
 To set them from a shell instead of the web UI:

   gh secret set ANDROID_KEYSTORE_BASE64 --repo <owner>/<repo> --body "$(cat keystore.b64)"
   gh secret set ANDROID_KEYSTORE_PASSWORD --repo <owner>/<repo>
   gh secret set ANDROID_KEY_ALIAS --repo <owner>/<repo>
   gh secret set ANDROID_KEY_PASSWORD --repo <owner>/<repo>

 BACK UP the .jks file and both passwords now. If you lose them you can never
 publish an update that existing installs will accept.
EOT
  exit 0
fi

command -v gh >/dev/null || { echo "gh CLI not found; install it to upload secrets." >&2; exit 1; }

gh secret set ANDROID_KEYSTORE_BASE64 --repo "$REPO" --body "$B64"
gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$REPO" --body "$STORE_PASS"
gh secret set ANDROID_KEY_ALIAS --repo "$REPO" --body "$ALIAS"
gh secret set ANDROID_KEY_PASSWORD --repo "$REPO" --body "$KEY_PASS"

echo
echo "Secrets uploaded to $REPO:"
gh secret list --repo "$REPO"
echo
echo "Back up $KEYSTORE and its passwords now (password manager + offline copy)."
echo "Then push a tag to publish: git tag v1.0.0 && git push origin v1.0.0"
