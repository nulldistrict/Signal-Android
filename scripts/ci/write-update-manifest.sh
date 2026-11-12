#!/bin/bash
#
# Writes the APK update manifest consumed by the in-app self-updater.
# Runs inside Signal's reproducible-builds Docker image, which ships aapt2.
#
# Expected environment variables:
#   $APK:      signed APK to describe
#   $OUT:      directory for latest.json
#   $APK_URL:  public download URL of the APK
#
# Optional:
#   $SOURCE_DATE_EPOCH: upload timestamp in seconds (defaults to now)

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

require_env APK OUT APK_URL
mkdir -p "$OUT"

AAPT2="$(find "${ANDROID_HOME}/build-tools" -name aapt2 -type f | sort -V | tail -1)"
if [ ! -x "$AAPT2" ]; then
  echo "aapt2 not found in the build-tools installation." >&2
  exit 1
fi

# sed reads the whole stream, unlike head, which would make aapt2 die on SIGPIPE
# and trip `set -o pipefail`.
BADGING="$("$AAPT2" dump badging "$APK" | sed -n '1p')"
VERSION_CODE="$(printf '%s\n' "$BADGING" | sed -n "s/.*versionCode='\\([0-9]*\\)'.*/\\1/p")"
VERSION_NAME="$(printf '%s\n' "$BADGING" | sed -n "s/.*versionName='\\([^']*\\)'.*/\\1/p")"
DIGEST="$(sha256sum "$APK" | awk '{print $1}')"
UPLOAD_TIMESTAMP="${SOURCE_DATE_EPOCH:-$(date -u +%s)}"

if [ -z "$VERSION_CODE" ] || [ -z "$VERSION_NAME" ]; then
  echo "Could not read version information from $APK" >&2
  exit 1
fi

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

cat > "$OUT/latest.json" <<EOF
{
  "versionCode": $VERSION_CODE,
  "versionName": "$(json_escape "$VERSION_NAME")",
  "url": "$(json_escape "$APK_URL")",
  "sha256sum": "$DIGEST",
  "uploadTimestamp": $UPLOAD_TIMESTAMP
}
EOF

echo "::group::Update manifest"
cat "$OUT/latest.json"
echo "::endgroup::"
