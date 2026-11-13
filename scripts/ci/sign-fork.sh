#!/bin/bash
#
# Signs the reproducibility-verified unsigned APK with the fork's release key.
# Runs inside the reproducible-builds Docker image, which ships apksigner.
#
# Expected environment variables:
#   $APK_IN:     unsigned APK to sign
#   $OUT:        directory for the signed APK and checksum
#   $KEYSTORE:   path to the keystore file
#   $KS_PASS:    keystore password
#   $KEY_ALIAS:  signing key alias
#   $KEY_PASS:   signing key password

set -euo pipefail
shopt -s inherit_errexit

function require_env() {
  local name
  for name in "$@"; do
    if [ -z "${!name:-}" ]; then
      echo "The $name environment variable was not set!" >&2
      exit 1
    fi
  done
}

require_env APK_IN OUT KEYSTORE KS_PASS KEY_ALIAS KEY_PASS
mkdir -p "$OUT"

# Older workflow revisions uploaded the verified artifact as a directory tree
# (fork-unsigned-<index>/fork-unsigned.apk) instead of a flat file. Accept both
# layouts so a rerun can sign an artifact produced by either revision.
if [ ! -f "$APK_IN" ]; then
  APK_IN="$(find "$(dirname "$APK_IN")" -type f -name 'fork-unsigned.apk' | sort | head -1)"
fi
if [ -z "$APK_IN" ] || [ ! -f "$APK_IN" ]; then
  echo "No unsigned APK to sign was found." >&2
  exit 1
fi

APKSIGNER="${ANDROID_HOME}/build-tools/${ANDROID_BUILD_TOOLS_VERSION}/apksigner"
if [ ! -x "$APKSIGNER" ]; then
  APKSIGNER="$(find "${ANDROID_HOME}/build-tools" -name apksigner -type f | sort -V | tail -1)"
fi
if [ ! -x "$APKSIGNER" ]; then
  echo "apksigner not found in the build-tools installation." >&2
  exit 1
fi

"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-pass env:KS_PASS \
  --key-pass env:KEY_PASS \
  --ks-key-alias "$KEY_ALIAS" \
  --out "$OUT/fork-release.apk" \
  "$APK_IN"

"$APKSIGNER" verify --print-certs "$OUT/fork-release.apk"

(cd "$OUT" && sha256sum fork-release.apk > fork-release.apk.sha256)

echo "::group::Signed output"
ls -la "$OUT"
cat "$OUT/fork-release.apk.sha256"
echo "::endgroup::"
