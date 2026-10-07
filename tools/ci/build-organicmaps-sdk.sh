#!/usr/bin/env bash
set -Eeuo pipefail

: "${ANDROID_HOME:?ANDROID_HOME must be set}"
OM_TAG="${OM_TAG:-2026.08.27-18-android}"
OM_VERSION="${OM_VERSION:-2026.08.27-18}"
OM_SOURCE_SHA="${OM_SOURCE_SHA:-3ef379196cca434cf17f4ee684f16aa99fd8985e}"

M2_BASE="$HOME/.m2/repository/app/organicmaps/sdk"
LOCATION_POM="$M2_BASE/location-core/$OM_VERSION/location-core-$OM_VERSION.pom"
SDK_POM="$M2_BASE/sdk/$OM_VERSION/sdk-$OM_VERSION.pom"
MAPS_POM="$M2_BASE/maps-world/$OM_VERSION/maps-world-$OM_VERSION.pom"

verify_outputs() {
  test -s "$LOCATION_POM"
  test -s "$SDK_POM"
  test -s "$MAPS_POM"
}

retry_net() {
  local attempts=3 delay=4 n=1
  until "$@"; do
    if [ "$n" -ge "$attempts" ]; then
      echo "::error title=Network bootstrap failed::Command failed after $attempts attempts: $*"
      return 1
    fi
    echo "::warning title=Transient network retry::Attempt $n failed: $*"
    sleep "$delay"
    n=$((n + 1))
    delay=$((delay * 2))
  done
}

if verify_outputs; then
  echo "Organic Maps SDK already present in local Maven cache: $OM_VERSION"
  exit 0
fi

retry_net sudo apt-get update -y
retry_net sudo apt-get install -y ninja-build
retry_net "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"   "platforms;android-36"   "ndk;29.0.14206865"   "cmake;3.22.1"

rm -rf /tmp/organicmaps
retry_net git clone --depth 128 --branch "$OM_TAG" https://github.com/organicmaps/organicmaps.git /tmp/organicmaps

cd /tmp/organicmaps
ACTUAL_SHA="$(git rev-parse HEAD)"
if [ "$ACTUAL_SHA" != "$OM_SOURCE_SHA" ]; then
  echo "::error title=Organic Maps source mismatch::Expected $OM_SOURCE_SHA but got $ACTUAL_SHA"
  exit 1
fi

retry_net git submodule update --init --recursive --depth 1 --jobs 8

ACTUAL_VERSION="$(tools/unix/version.sh android_name)"
if [ "$ACTUAL_VERSION" != "$OM_VERSION" ]; then
  echo "::error title=Organic Maps version mismatch::Expected $OM_VERSION but got $ACTUAL_VERSION"
  exit 1
fi

echo "sdk.dir=$ANDROID_HOME" > android/local.properties
cd android
chmod +x ./gradlew
./gradlew   :sdk:location:core:publishToMavenLocal   :sdk:publishToMavenLocal   :sdk:maps:world:publishToMavenLocal   -Parm64 -Pnjobs=2   --no-daemon --max-workers=2 --stacktrace   | tee /tmp/organicmaps-sdk-build.log

verify_outputs
printf '%s\n' "$OM_SOURCE_SHA" > /tmp/organicmaps-source-sha.txt
printf '%s\n' "$OM_VERSION" > /tmp/organicmaps-version.txt
echo "Organic Maps SDK verified: $OM_VERSION @ $OM_SOURCE_SHA"
