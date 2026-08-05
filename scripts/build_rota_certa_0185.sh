#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"
BASE_BUILD="$PATCHES/scripts/build_rota_certa_0184.sh"
PATCH_ARCHIVE="$PATCHES/patches/fix-live-card-isolation-0185.patch.gz.b64"
PATCH_ARCHIVE_SHA256="bc00670d72a17f1165777cb592f2d6bcb2fe4f239aba32c2af39b8e2dab481c5"
PATCH_SHA256="6d2c1530c71d92a49266f671b6dca53b5d4f0d144ea8e45b389cc520182eb8b6"
PATCH_FILE="$(mktemp --suffix=.patch)"

cleanup_gradle_home=false
if [[ -z "${GRADLE_USER_HOME:-}" ]]; then
  GRADLE_USER_HOME="$(mktemp -d)"
  cleanup_gradle_home=true
fi
export GRADLE_USER_HOME
mkdir -p "$GRADLE_USER_HOME"
cat > "$GRADLE_USER_HOME/gradle.properties" <<'GRADLE'
org.gradle.daemon=false
org.gradle.parallel=false
org.gradle.workers.max=1
org.gradle.vfs.watch=false
org.gradle.jvmargs=-Xmx2560m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8
kotlin.compiler.execution.strategy=in-process
kotlin.incremental=false
GRADLE

before_hashes="$(mktemp)"
after_hashes="$(mktemp)"
cleanup() {
  rm -f "$before_hashes" "$after_hashes" "$PATCH_FILE"
  if [[ "$cleanup_gradle_home" == "true" ]]; then rm -rf "$GRADLE_USER_HOME"; fi
}
trap cleanup EXIT

echo "$PATCH_ARCHIVE_SHA256  $PATCH_ARCHIVE" | sha256sum --check
base64 --decode "$PATCH_ARCHIVE" | gzip -dc > "$PATCH_FILE"
echo "$PATCH_SHA256  $PATCH_FILE" | sha256sum --check
bash "$BASE_BUILD" "$PATCHES"

PROTECTED_FILES=(
  app/src/main/AndroidManifest.xml
  app/src/main/java/br/com/mapeiaia/rotacerta/GpsAddressResolver.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RadarImport.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/Repositories.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/LiveSpeechEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutActionCatalog0184.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
)
sha256sum "${PROTECTED_FILES[@]}" > "$before_hashes"
git apply --check "$PATCH_FILE"
git apply "$PATCH_FILE"
sha256sum "${PROTECTED_FILES[@]}" > "$after_hashes"
diff -u "$before_hashes" "$after_hashes"

grep -F 'versionCode = 5460' app/build.gradle.kts
grep -F 'versionName = "0.1.185"' app/build.gradle.kts
grep -F 'CONFIRMED_INDIVIDUAL_CARD_0185' app/src/main/java/br/com/mapeiaia/rotacerta/RideCardConfirmationPolicy0185.kt
grep -F 'EXPLICIT_EXTERNAL_PACKAGE_REJECTION_0185' app/src/main/java/br/com/mapeiaia/rotacerta/ExplicitPackageTransitionPolicy0185.kt
grep -F 'BUBBLE_UNCONFIRMED_CARD_REJECTED_0185' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'EXPLICIT_EXTERNAL_PACKAGE_REJECTED_0185' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'runCatching { node0167.text }.getOrNull()' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'hardClearUniversalTwoAddress(failureReason0185, keepWaitingYellow = false)' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/RideCardConfirmationPolicy0185Test.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/ExplicitPackageTransitionPolicy0185Test.kt

./gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew lintDebug --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew clean assembleDebug --no-daemon --max-workers=1 --no-parallel --stacktrace

APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.185"
APK_NAME="rota-certa-0.1.185-card-individual-indrive-validado.apk"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cp "$APK_SOURCE" "$OUTPUT_DIR/$APK_NAME"
unzip -tqq "$OUTPUT_DIR/$APK_NAME"
unzip -l "$OUTPUT_DIR/$APK_NAME" | grep -F 'classes.dex'
APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
AAPT="$(find "$ANDROID_SDK_ROOT/build-tools" -name aapt -type f | sort -V | tail -n 1)"
"$AAPT" dump badging "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/badging.txt"
grep -F "package: name='br.com.mapeiaia.rotacerta' versionCode='5460' versionName='0.1.185'" "$OUTPUT_DIR/badging.txt"
"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_DIR/$APK_NAME" | tee "$OUTPUT_DIR/signature.txt"
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/signature.txt"
grep -qi 'd9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' "$OUTPUT_DIR/signature.txt"
for dex in $(zipinfo -1 "$OUTPUT_DIR/$APK_NAME" | grep -E '^classes([0-9]+)?\\.dex$'); do unzip -p "$OUTPUT_DIR/$APK_NAME" "$dex"; done | strings > "$OUTPUT_DIR/dex-strings.txt"
grep -F 'CONFIRMED_INDIVIDUAL_CARD_0185' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'EXPLICIT_EXTERNAL_PACKAGE_REJECTION_0185' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'BUBBLE_UNCONFIRMED_CARD_REJECTED_0185' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'EXPLICIT_EXTERNAL_PACKAGE_REJECTED_0185' "$OUTPUT_DIR/dex-strings.txt"
sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$after_hashes" "$OUTPUT_DIR/protected-source-sha256.txt"
cat > "$OUTPUT_DIR/validation.txt" <<'VALIDATION'
package=br.com.mapeiaia.rotacerta
versionName=0.1.185
versionCode=5460
scope=indrive_confirmed_individual_card_external_transition_accessibility_node_safety
indrive_feed_authorizes_decision=false
indrive_individual_modal_required=true
indrive_background_offers_excluded=true
explicit_external_package_rejected_before_stale_root=true
accessibility_node_reads_contained=true
failure_containment_paints_idle=true
route_and_decision_engine_unchanged=true
manifest_permissions_unchanged=true
VALIDATION
cat "$OUTPUT_DIR/sha256.txt"
cat "$OUTPUT_DIR/signature.txt"
