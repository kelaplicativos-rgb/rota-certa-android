#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"
BASE_BUILD="$PATCHES/scripts/build_rota_certa_0177.sh"
PATCH_PARTS=(
  "$PATCHES/patches/radar-edit-delete-dismiss-0178.patch.gz.b64.part00"
  "$PATCHES/patches/radar-edit-delete-dismiss-0178.patch.gz.b64.part01"
  "$PATCHES/patches/radar-edit-delete-dismiss-0178.patch.gz.b64.part02"
)
PATCH_SHA256="fe415b2697699db7093238dabca24dbf4687d8a35b0cb605cd78b9c18155251b"
COMPILE_FIX_PATCH="$PATCHES/patches/radar-editor-compile-fix-0178.patch"
COMPILE_FIX_SHA256="b212da9543a64eac22e996d3b15d54ce93ecdf5fb7592fd0144e9a848df2864c"

bash "$BASE_BUILD" "$PATCHES"

PROTECTED_FILES=(
  app/src/main/AndroidManifest.xml
  app/src/main/java/br/com/mapeiaai/rotacerta/DecisionEngine.kt
  app/src/main/java/br/com/mapeiaai/rotacerta/GoogleMapsService.kt
  app/src/main/java/br/com/mapeiaai/rotacerta/RideTextParser.kt
  app/src/main/java/br/com/mapeiaai/rotacerta/BubbleShortcutOverlayController.kt
  app/src/main/java/br/com/mapeiaai/rotacerta/BubbleShortcutModule.kt
  app/src/main/java/br/com/mapeiaai/rotacerta/ShortcutLongPressPolicy0171.kt
  app/src/main/java/br/com/mapeiaai/rotacerta/ShortcutActivityLaunchPolicy0176.kt
)

before_hashes="$(mktemp)"
after_hashes="$(mktemp)"
patch_file="$(mktemp --suffix=.patch)"
patch_b64="$(mktemp --suffix=.b64)"
trap 'rm -f "$before_hashes" "$after_hashes" "$patch_file" "$patch_b64"' EXIT
sha256sum "${PROTECTED_FILES[@]}" > "$before_hashes"

cat "${PATCH_PARTS[@]}" > "$patch_b64"
base64 --decode "$patch_b64" | gzip --decompress > "$patch_file"
echo "$PATCH_SHA256  $patch_file" | sha256sum --check
git apply --check "$patch_file"
git apply "$patch_file"

echo "$COMPILE_FIX_SHA256  $COMPILE_FIX_PATCH" | sha256sum --check
git apply --check "$COMPILE_FIX_PATCH"
git apply "$COMPILE_FIX_PATCH"

sha256sum "${PROTECTED_FILES[@]}" > "$after_hashes"
diff -u "$before_hashes" "$after_hashes"

grep -F 'versionCode = 5390' app/build.gradle.kts
grep -F 'versionName = "0.1.178"' app/build.gradle.kts
grep -F 'radar_edit_delete_dismiss_0_1_178' app/src/main/java/br/com/mapeiaai/rotacerta/LiveRideAccessibilityService.kt
grep -F 'dismissGate0178.isDismissed(key)' app/src/main/java/br/com/mapeiaai/rotacerta/DirectionalProximityAlertEngine.kt
grep -F 'onEditRadar' app/src/main/java/br/com/mapeiaai/rotacerta/DirectionalAlertOverlayController.kt
grep -F 'import androidx.compose.material3.OutlinedTextField' app/src/main/java/br/com/mapeiaai/rotacerta/RadarImport.kt
test -f app/src/test/java/br/com/mapeiaai/rotacerta/DirectionalRadarDismiss0178Test.kt

./gradlew testDebugUnitTest --no-daemon --stacktrace
./gradlew lintDebug --no-daemon --stacktrace
./gradlew clean assembleDebug --no-daemon --stacktrace

APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.178"
APK_NAME="rota-certa-0.1.178-radares-editar-excluir-fechar-validado.apk"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cp "$APK_SOURCE" "$OUTPUT_DIR/$APK_NAME"

unzip -tqq "$OUTPUT_DIR/$APK_NAME"
unzip -l "$OUTPUT_DIR/$APK_NAME" | grep -F 'classes.dex'

APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
AAPT="$(find "$ANDROID_SDK_ROOT/build-tools" -name aapt -type f | sort -V | tail -n 1)"

"$AAPT" dump badging "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/badging.txt"
grep -F "package: name='br.com.mapeiaia.rotacerta' versionCode='5390' versionName='0.1.178'" "$OUTPUT_DIR/badging.txt"
"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_DIR/$APK_NAME" | tee "$OUTPUT_DIR/signature.txt"
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/signature.txt"
grep -qi 'd9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' "$OUTPUT_DIR/signature.txt"

for dex in $(zipinfo -1 "$OUTPUT_DIR/$APK_NAME" | grep -E '^classes([0-9]+)?\.dex$'); do
  unzip -p "$OUTPUT_DIR/$APK_NAME" "$dex"
done | strings > "$OUTPUT_DIR/dex-strings.txt"
grep -F 'ApproachDismissGate0178' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'ImportedRadarEditPolicy0178' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'DirectionalRadarDismiss0178Test' app/src/test/java/br/com/mapeiaai/rotacerta/DirectionalRadarDismiss0178Test.kt

sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$after_hashes" "$OUTPUT_DIR/protected-source-sha256.txt"
{
  echo 'package=br.com.mapeiaai.rotacerta'
  echo 'versionName=0.1.178'
  echo 'versionCode=5390'
  echo 'scope=radar_edit_delete_dismiss'
  echo 'dismissal=current_approach_until_exit'
  echo 'farol_protected=true'
  echo 'manifest_permissions_unchanged=true'
} > "$OUTPUT_DIR/validation.txt"

cat "$OUTPUT_DIR/sha256.txt"
cat "$OUTPUT_DIR/signature.txt"
