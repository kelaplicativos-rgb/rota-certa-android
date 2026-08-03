#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"
BASE_BUILD="$PATCHES/scripts/build_rota_certa_0179.sh"
PATCH_PARTS=(
  "$PATCHES/patches/per-shortcut-gesture-config-0180.patch.gz.b64.chunk00"
  "$PATCHES/patches/per-shortcut-gesture-config-0180.patch.gz.b64.chunk01"
  "$PATCHES/patches/per-shortcut-gesture-config-0180.patch.gz.b64.chunk02"
  "$PATCHES/patches/per-shortcut-gesture-config-0180.patch.gz.b64.chunk03"
  "$PATCHES/patches/per-shortcut-gesture-config-0180.patch.gz.b64.chunk04"
  "$PATCHES/patches/per-shortcut-gesture-config-0180.patch.gz.b64.chunk05"
  "$PATCHES/patches/per-shortcut-gesture-config-0180.patch.gz.b64.chunk06"
)
PATCH_SHA256="0f379a76ddcd90f23e5308b958d138ad1c3b09c5ece321c80f97c6883ca713f9"

cleanup_gradle_home=false
if [[ -z "${GRADLE_USER_HOME:-}" ]]; then
  GRADLE_USER_HOME="$(mktemp -d)"
  cleanup_gradle_home=true
fi
export GRADLE_USER_HOME
mkdir -p "$GRADLE_USER_HOME"
cat > "$GRADLE_USER_HOME/gradle.properties" <<'PROPERTIES'
org.gradle.daemon=false
org.gradle.parallel=false
org.gradle.workers.max=1
org.gradle.vfs.watch=false
org.gradle.jvmargs=-Xmx2560m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8
kotlin.compiler.execution.strategy=in-process
kotlin.incremental=false
PROPERTIES

before_hashes="$(mktemp)"
after_hashes="$(mktemp)"
patch_b64="$(mktemp --suffix=.b64)"
patch_file="$(mktemp --suffix=.patch)"
cleanup() {
  rm -f "$before_hashes" "$after_hashes" "$patch_b64" "$patch_file"
  if [[ "$cleanup_gradle_home" == "true" ]]; then
    rm -rf "$GRADLE_USER_HOME"
  fi
}
trap cleanup EXIT

bash "$BASE_BUILD" "$PATCHES"

PROTECTED_FILES=(
  app/src/main/AndroidManifest.xml
  app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutLongPressPolicy0171.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutActivityLaunchPolicy0176.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RadarImport.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/Repositories.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/LiveSpeechEngine.kt
)

sha256sum "${PROTECTED_FILES[@]}" > "$before_hashes"
cat "${PATCH_PARTS[@]}" > "$patch_b64"
base64 --decode "$patch_b64" | gzip --decompress > "$patch_file"
echo "$PATCH_SHA256  $patch_file" | sha256sum --check
git apply --check "$patch_file"
git apply "$patch_file"
sha256sum "${PROTECTED_FILES[@]}" > "$after_hashes"
diff -u "$before_hashes" "$after_hashes"

grep -F 'versionCode = 5410' app/build.gradle.kts
grep -F 'versionName = "0.1.180"' app/build.gradle.kts
grep -F 'shortcut_gesture_configuration_0_1_180' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'SHORTCUT_LONG_PRESS_MILLIS: Long = 1_500L' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'SHORTCUT_CUSTOMIZATION_HOLD_MILLIS: Long = 5_000L' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'ShortcutGestureAction0180.DoNothing -> Unit' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'EXTRA_EDIT_SHORTCUT_ENTRY_0180' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
grep -F 'Configurar esta bolinha' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
grep -F 'onShortcutGesture(entry0179, entry0179.quickTapAction)' app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
grep -F 'onShortcutGesture(entry0179, entry0179.holdAction)' app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
grep -F 'ShortcutGesturePolicy0179.resolveRelease' app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt

test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179Test.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomizationContract0179Test.kt
grep -F 'holdingToFiveSecondsNeverDispatchesTheIntermediateAction' app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179Test.kt
grep -F 'configurationShowsBothGestureButtonsNoOpAndDelete' app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomizationContract0179Test.kt

./gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew lintDebug --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew clean assembleDebug --no-daemon --max-workers=1 --no-parallel --stacktrace

APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.180"
APK_NAME="rota-certa-0.1.180-gestos-atalhos-configuraveis-validado.apk"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cp "$APK_SOURCE" "$OUTPUT_DIR/$APK_NAME"

unzip -tqq "$OUTPUT_DIR/$APK_NAME"
unzip -l "$OUTPUT_DIR/$APK_NAME" | grep -F 'classes.dex'

APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
AAPT="$(find "$ANDROID_SDK_ROOT/build-tools" -name aapt -type f | sort -V | tail -n 1)"

"$AAPT" dump badging "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/badging.txt"
grep -F "package: name='br.com.mapeiaia.rotacerta' versionCode='5410' versionName='0.1.180'" "$OUTPUT_DIR/badging.txt"
"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_DIR/$APK_NAME" | tee "$OUTPUT_DIR/signature.txt"
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/signature.txt"
grep -qi 'd9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' "$OUTPUT_DIR/signature.txt"

for dex in $(zipinfo -1 "$OUTPUT_DIR/$APK_NAME" | grep -E '^classes([0-9]+)?\.dex$'); do
  unzip -p "$OUTPUT_DIR/$APK_NAME" "$dex"
done | strings > "$OUTPUT_DIR/dex-strings.txt"
grep -F 'ShortcutGestureAction0180' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'ShortcutGestureResolution0180' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'SHORTCUT_GESTURE_DISPATCH_0180' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'edit_shortcut_entry_0180' "$OUTPUT_DIR/dex-strings.txt"

sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$after_hashes" "$OUTPUT_DIR/protected-source-sha256.txt"
{
  echo 'package=br.com.mapeiaia.rotacerta'
  echo 'versionName=0.1.180'
  echo 'versionCode=5410'
  echo 'scope=per_shortcut_gesture_configuration'
  echo 'quick_tap=configurable_execute_open_module_or_none'
  echo 'hold_1500=configurable_execute_open_module_or_none'
  echo 'hold_5000=opens_specific_shortcut_configuration'
  echo 'intermediate_action_deferred_until_release=true'
  echo 'delete_from_grid=true'
  echo 'farol_protected=true'
  echo 'radars_protected=true'
  echo 'manifest_permissions_unchanged=true'
} > "$OUTPUT_DIR/validation.txt"

cat "$OUTPUT_DIR/sha256.txt"
cat "$OUTPUT_DIR/signature.txt"
