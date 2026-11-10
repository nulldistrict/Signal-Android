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

APKSIGNER="${ANDROID_HOME}/build-tools/${ANDROID_BUILD_TOOLS_VERSION}/apksigner"
if [ ! -x "$APKSIGNER" ]; then
  APKSIGNER="$(find "${ANDROID_HOME}/build-tools" -name apksigner -type f | sort -V | tail -1)"
fi
if [ ! -x "$APKSIGNER" ]; then
  echo "apksigner not found in the build-tools installation." >&2
  exit 1
fi

apksigner sign \
  --ks "$KEYSTORE" \
  --ks-pass env:KS_PASS \
  --key-pass env:KEY_PASS \
  --ks-key-alias "$KEY_ALIAS" \
  --out "$OUT/fork-release.apk" \
  "$APK_IN"

apksigner verify --print-certs "$OUT/fork-release.apk"

(cd "$OUT" && sha256sum fork-release.apk > fork-release.apk.sha256)

echo "::group::Signed output"
ls -la "$OUT"
cat "$OUT/fork-release.apk.sha256"
echo "::endgroup::"
