#!/bin/bash
#
# Builds the fork's signed-release candidate APK inside Signal's
# reproducible-builds Docker image. The output is intentionally unsigned:
# reproducibility is verified on the unsigned artifact, then the signing job
# signs the verified bytes.
#
# Expected environment variables:
#   $OUT:            directory to collect outputs into
#   $BUILD_VARIANT:  Gradle variant to assemble (default: githubProdRelease)
#   $GRADLE_ARGS:    extra Gradle arguments (default: --no-configuration-cache)

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

require_env OUT
mkdir -p "$OUT"

BUILD_VARIANT="${BUILD_VARIANT:-githubProdRelease}"
GRADLE_ARGS="${GRADLE_ARGS:---no-configuration-cache}"

# GitHub-hosted ubuntu-latest runners have 16 GB of RAM. The upstream
# gradle.properties values (-Xmx12g Gradle daemon plus -Xmx4g Kotlin daemon)
# leave no room for metaspace, native memory and the Docker runtime, so the
# kernel killed the Gradle daemon during R8. Apply a CI-only memory budget
# below that limit. Heap size does not affect the produced bytes, but the
# determinism flags from gradle.properties must be preserved verbatim.
GRADLE_MEMORY_ARGS=(
  "-Dorg.gradle.jvmargs=-Xmx8g -Xms256m -XX:MaxMetaspaceSize=1g -Dcom.android.tools.r8.deterministicdebugging=true -XX:+UnlockExperimentalVMOptions -XX:hashCode=3"
  "-Dkotlin.daemon.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=512m"
)

# githubProd -> githubProdRelease, etc.
VARIANT_DIR="${BUILD_VARIANT%Release}"

echo "::group::Environment"
echo "commit:  $(git rev-parse HEAD)"
echo "variant: $BUILD_VARIANT"
echo "out:     $OUT"
nproc
free -g | head -2
java -version 2>&1 | head -1
echo "::endgroup::"

./gradlew --console=plain clean "assemble${BUILD_VARIANT^}" "${GRADLE_MEMORY_ARGS[@]}" $GRADLE_ARGS

APK_DIR="app/build/outputs/apk/${VARIANT_DIR}/release"
if [ ! -d "$APK_DIR" ]; then
  echo "Expected APK output directory $APK_DIR does not exist." >&2
  exit 1
fi

# Prefer the universal APK: it is the one published for self-updates.
UNIVERSAL_APK="$(find "$APK_DIR" -maxdepth 1 -name '*universal*.apk' -print -quit)"
if [ -z "$UNIVERSAL_APK" ]; then
  echo "No universal APK found in $APK_DIR; refusing to publish an ABI-specific build." >&2
  exit 1
fi

cp "$UNIVERSAL_APK" "$OUT/fork-unsigned.apk"
(cd "$OUT" && sha256sum fork-unsigned.apk > fork-unsigned.apk.sha256)

echo "::group::Outputs"
ls -la "$OUT"
cat "$OUT/fork-unsigned.apk.sha256"
echo "::endgroup::"
echo "Done!"
