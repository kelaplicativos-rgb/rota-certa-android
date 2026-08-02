#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"
BASE_BUILD="$PATCHES/scripts/build_rota_certa_0177.sh"
PATCH_B64="$PATCHES/patches/radar-edit-delete-dismiss-0178.patch.gz.b64"

bash "$BASE_BUILD" "$PATCHES"

PROTECTED_FILES=(
  app/src/main/AndroidManifest.xml
  app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutLongPressPolicy0171.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutActivityLaunchPolicy0176.kt
)

before_hashes="$(mktemp)"
after_hashes="$(mktemp)"
patch_file="$(mktemp --suffix=.patch)"
trap 'rm -f "$before_hashes" "$after_hashes" "$patch_file"' EXIT
sha256sum "${PROTECTED_FILES[@]}" > "$before_hashes"

base64 --decode "$PATCH_B64" | gzip --decompress > "$patch_file"
git apply --check "$patch_file"
git apply "$patch_file"

sha256sum "${PROTECTED_FILES[@]}" > "$after_hashes"
diff -u "$before_hashes" "$after_hashes"

grep -F 'versionCode = 5390' app/build.gradle.kts
grep -F 'versionName = "0.1.178"' app/build.gradle.kts
grep -F 'radar_edit_delete_dismiss_0_1_178' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'dismissGate0178.isDismissed(key)' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
grep -F 'onEditRadar' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt

gradle testDebugUnitTest --no-daemon --stacktrace
gradle lintDebug --no-daemon --stacktrace
gradle clean assembleDebug --no-daemon --stacktrace

APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.178"
APK_NAME="rota-certa-0.1.178-radares-editar-excluir-fechar-validado.apk"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cp "$APK_SOURCE" "$OUTPUT_DIR/$APK_NAME"

unzip -tqq "$OUTPUT_DIR/$APK_NAME"
unzip -l "$OUTPUT_DIR/$APK_NAME" | grep -F 'classes.dex'

BUILD_TOOLS_DIR="$(find "${ANDROID_HOME:-$ANDROID_SDK_ROOT}/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
AAPT="$BUILD_TOOLS_DIR/aapt"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"

"$AAPT" dump badging "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/badging.txt"
grep -F "package: name='br.com.mapeiaia.rotacerta' versionCode='5390' versionName='0.1.178'" "$OUTPUT_DIR/badging.txt"
"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/signature.txt"
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/signature.txt"

sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$after_hashes" "$OUTPUT_DIR/protected-source-sha256.txt"
{
  echo 'package=br.com.mapeiaia.rotacerta'
  echo 'versionName=0.1.178'
  echo 'versionCode=5390'
  echo 'scope=radar_edit_delete_dismiss'
  echo 'dismissal=current_approach_until_exit'
  echo 'farol_protected=true'
} > "$OUTPUT_DIR/validation.txt"

cat "$OUTPUT_DIR/sha256.txt"
cat "$OUTPUT_DIR/signature.txt"
