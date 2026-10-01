#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"
BASE_BUILD="$PATCHES/scripts/build_rota_certa_0183.sh"
PATCH_FILE="$(mktemp --suffix=.patch)"
FIX_PATCH="$PATCHES/patches/home-action-shortcuts-0184-tests-fix.patch"
FIX_PATCH_SHA256="703c80b53ae08800318657d9e2feab7c413f0600d7b56dc5532d2ae9d0cab014"
DEX_MARKER_PATCH="$PATCHES/patches/home-action-shortcuts-0184-dex-marker-fix.patch"
DEX_MARKER_PATCH_SHA256="2a4593e9eba23cbff4c97511459d5dcfd6ccd18045cf72c948301e915863a4e9"
PARTS=(
  "$PATCHES/patches/home-action-shortcuts-0184.patch.part00"
  "$PATCHES/patches/home-action-shortcuts-0184.patch.part01"
  "$PATCHES/patches/home-action-shortcuts-0184.patch.part02"
  "$PATCHES/patches/home-action-shortcuts-0184.patch.part03"
  "$PATCHES/patches/home-action-shortcuts-0184.patch.part04"
  "$PATCHES/patches/home-action-shortcuts-0184.patch.part05"
  "$PATCHES/patches/home-action-shortcuts-0184.patch.part06"
  "$PATCHES/patches/home-action-shortcuts-0184.patch.part07"
  "$PATCHES/patches/home-action-shortcuts-0184.patch.part08"
  "$PATCHES/patches/home-action-shortcuts-0184.patch.part09"
  "$PATCHES/patches/home-action-shortcuts-0184.patch.part10"
  "$PATCHES/patches/home-action-shortcuts-0184.patch.part11"
)
PART_HASHES=(
  4a31af392c33d14bff6c09a26c6a07c16c39fe59a18e392fd5e0291240013367
  8ff76014ef335a739c0e6b4ac29615234bb9dd34edca53e56dad60399ea12c3d
  103cff902050e2b28d87a0f9c8dc556f68b4ae4e45d3595ceb56bd539e11adc4
  10f8263111a81aa6b813bac35ec622736e466ee4a20298d227a9f5c6038719a9
  e77085f6701fba6cf24d19a781e670a2ee8c66c917cc27a9b0ce079d6c1924b3
  c899a28d444958180f82d2a9ae0794198e4b54f96aaf0723ca640e5a867db8a9
  fac6e2d5d363fd9145a5d2d5459f11556c980c30eb524b96bd498c3060394fd4
  574e588671522275f206373debc3621a103da3f7c469d32013febb8edeb2ad8c
  8f13533694506ea799d6eab4c061cbdeafb6c03273d8baf699435b9d2b7cc7a9
  55b568f496109123ca4ae3f0e6499c08cd83b053ff3a5a0dfd6400a25dcdc44b
  ad28dc0db70a9cb42352e7627b43962fb9c56cc8778a45090d64e7e43a963db9
  cc21841f87767433120477e6ef86d45064980f9b9ac3793498b77f2eaca2ffac
)
FULL_PATCH_SHA256="56a124919ef18059f1a7ca443a37012d8c8e96b89e84fae0a610a79d3e5f0a32"

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

for index in "${!PARTS[@]}"; do
  echo "${PART_HASHES[$index]}  ${PARTS[$index]}" | sha256sum --check
  cat "${PARTS[$index]}" >> "$PATCH_FILE"
done
echo "$FULL_PATCH_SHA256  $PATCH_FILE" | sha256sum --check

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
)
sha256sum "${PROTECTED_FILES[@]}" > "$before_hashes"
git apply --check "$PATCH_FILE"
git apply "$PATCH_FILE"
echo "$FIX_PATCH_SHA256  $FIX_PATCH" | sha256sum --check
git apply --check "$FIX_PATCH"
git apply "$FIX_PATCH"
echo "$DEX_MARKER_PATCH_SHA256  $DEX_MARKER_PATCH" | sha256sum --check
git apply --check "$DEX_MARKER_PATCH"
git apply "$DEX_MARKER_PATCH"
sha256sum "${PROTECTED_FILES[@]}" > "$after_hashes"
diff -u "$before_hashes" "$after_hashes"

grep -F 'versionCode = 5450' app/build.gradle.kts
grep -F 'versionName = "0.1.184"' app/build.gradle.kts
grep -F 'SHORTCUT_ACTION_CATALOG_0184' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutActionCatalog0184.kt
grep -F 'HOME_ACTION_SHORTCUTS_0184' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'executeShortcutModule(entry0180.spec)' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
! grep -F 'showShortcutActionMenu0183(entry0180.spec)' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
! grep -F 'shortcut_add_0179' app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
grep -F 'HomeShortcutActions0184' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
grep -F 'const val REQUIRED_APPROACHING_SAMPLES = 2' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
grep -F 'const val TARGET_AHEAD_TOLERANCE_DEGREES = 40.0' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt
grep -F 'const val RADAR_DIRECTION_TOLERANCE_DEGREES = 30.0' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt
grep -F 'const val MIN_DISTANCE_PROGRESS_METERS = 1.5' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt
grep -F '"copy_trip_confirmation"' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutActionCatalog0184.kt
grep -F '"stop_app"' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutActionCatalog0184.kt
! grep -F '"trip_confirmation",' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutActionCatalog0184.kt
! grep -F '"stop",' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutActionCatalog0184.kt
grep -F 'modules.size >= 21' app/src/test/java/br/com/mapeiaia/rotacerta/AccessibilityResilienceAndTools0172ContractTest.kt
grep -F 'catálogo completo da Home' app/src/test/java/br/com/mapeiaia/rotacerta/GeneralControlsPlacesPopupChecklist7Test.kt
grep -F 'fix(-0.0018)' app/src/test/java/br/com/mapeiaia/rotacerta/DirectionalRadarDismiss0178Test.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutActionCatalog0184Test.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/HomeActionShortcutsContract0184Test.kt

./gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew lintDebug --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew clean assembleDebug --no-daemon --max-workers=1 --no-parallel --stacktrace

APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.184"
APK_NAME="rota-certa-0.1.184-home-acoes-direcao-validado.apk"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cp "$APK_SOURCE" "$OUTPUT_DIR/$APK_NAME"
unzip -tqq "$OUTPUT_DIR/$APK_NAME"
unzip -l "$OUTPUT_DIR/$APK_NAME" | grep -F 'classes.dex'
APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
AAPT="$(find "$ANDROID_SDK_ROOT/build-tools" -name aapt -type f | sort -V | tail -n 1)"
"$AAPT" dump badging "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/badging.txt"
grep -F "package: name='br.com.mapeiaia.rotacerta' versionCode='5450' versionName='0.1.184'" "$OUTPUT_DIR/badging.txt"
"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_DIR/$APK_NAME" | tee "$OUTPUT_DIR/signature.txt"
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/signature.txt"
grep -qi 'd9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' "$OUTPUT_DIR/signature.txt"
for dex in $(zipinfo -1 "$OUTPUT_DIR/$APK_NAME" | grep -E '^classes([0-9]+)?\.dex$'); do unzip -p "$OUTPUT_DIR/$APK_NAME" "$dex"; done | strings > "$OUTPUT_DIR/dex-strings.txt"
grep -F 'SHORTCUT_ACTION_CATALOG_0184' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'HOME_ACTION_SHORTCUTS_0184' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'STRICT_DIRECTIONAL_APPROACH_0184' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'empty_action_grid_0184' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'action_create_backup' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'action_restore_backup' "$OUTPUT_DIR/dex-strings.txt"
sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$after_hashes" "$OUTPUT_DIR/protected-source-sha256.txt"
cat > "$OUTPUT_DIR/validation.txt" <<'VALIDATION'
package=br.com.mapeiaia.rotacerta
versionName=0.1.184
versionCode=5450
scope=home_action_catalog_empty_grid_strict_direction
home_catalog_complete=true
grid_unit=typed_action
fresh_install_grid=empty
existing_legacy_grid=17_actions
maximum_active_actions=32
main_bubble_empty_grid=opens_home
shortcut_single_tap=executes_action_directly
generic_context_menu_in_tap_path=false
direction_requires_real_progress=true
manual_radar_captures_heading_when_available=true
arbitrary_intents_or_code=false
manifest_permissions_unchanged=true
farol_route_ocr_protected=true
VALIDATION
cat "$OUTPUT_DIR/sha256.txt"
cat "$OUTPUT_DIR/signature.txt"
