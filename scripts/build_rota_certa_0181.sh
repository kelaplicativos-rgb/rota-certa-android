#!/usr/bin/env bash
set -euo pipefail
# validation_trigger=shortcut_in_place_0181

PATCHES="${1:-../patches}"
BASE_BUILD="$PATCHES/scripts/build_rota_certa_0180.sh"
TRANSFORM_POPUP="$PATCHES/scripts/fix_saved_place_popup_0181.py"
TRANSFORM_IN_PLACE="$PATCHES/scripts/fix_shortcut_in_place_0181.py"
TRANSFORM_TESTS="$PATCHES/scripts/fix_shortcut_in_place_tests_0181.py"

cleanup_gradle_home=false
if [[ -z "${GRADLE_USER_HOME:-}" ]]; then
  GRADLE_USER_HOME="$(mktemp -d)"
  cleanup_gradle_home=true
fi
export GRADLE_USER_HOME
mkdir -p "$GRADLE_USER_HOME"
cat > "$GRADLE_USER_HOME/gradle.properties" <<'EOF'
org.gradle.daemon=false
org.gradle.parallel=false
org.gradle.workers.max=1
org.gradle.vfs.watch=false
org.gradle.jvmargs=-Xmx2560m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8
kotlin.compiler.execution.strategy=in-process
kotlin.incremental=false
EOF

before_hashes="$(mktemp)"
after_hashes="$(mktemp)"
cleanup() {
  rm -f "$before_hashes" "$after_hashes"
  if [[ "$cleanup_gradle_home" == "true" ]]; then
    rm -rf "$GRADLE_USER_HOME"
  fi
}
trap cleanup EXIT

python -m py_compile "$TRANSFORM_POPUP" "$TRANSFORM_IN_PLACE" "$TRANSFORM_TESTS"
bash "$BASE_BUILD" "$PATCHES"

PROTECTED_FILES=(
  app/src/main/AndroidManifest.xml
  app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GpsAddressResolver.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RadarImport.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/Repositories.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/LiveSpeechEngine.kt
)
sha256sum "${PROTECTED_FILES[@]}" > "$before_hashes"

python "$TRANSFORM_POPUP"
python "$TRANSFORM_IN_PLACE"
python "$TRANSFORM_TESTS"

sha256sum "${PROTECTED_FILES[@]}" > "$after_hashes"
diff -u "$before_hashes" "$after_hashes"

grep -F 'versionCode = 5420' app/build.gradle.kts
grep -F 'versionName = "0.1.181"' app/build.gradle.kts
grep -F 'private fun showSavePlacePopup' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'showSavePlacePopup(coordinate, resolved.addressLine, type)' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'Endereco completo' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'Nome do alerta' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'OPEN_MODULE -> showShortcutModulePopup0181(entry0180.spec)' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'dispatchShortcutPrimaryInPlace0181' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'SHORTCUT_IN_PLACE_POPUP_0181' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'Voce continua no aplicativo e na tela que estava usando.' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutInPlacePolicy0181.kt
grep -F 'text = "Cancelar"' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'text = "Salvar"' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'Local salvo pela bolinha sem sair da tela atual.' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'Alerta de proximidade salvo pela bolinha sem sair da tela atual.' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt

test -f app/src/main/java/br/com/mapeiaia/rotacerta/SavedPlacePopupPolicy0181.kt
test -f app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutInPlacePolicy0181.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/SavedPlacePopupPolicy0181Test.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/SavedPlacePopupContract0181Test.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutInPlacePolicy0181Test.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutInPlaceContract0181Test.kt

./gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew lintDebug --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew clean assembleDebug --no-daemon --max-workers=1 --no-parallel --stacktrace

APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.181"
APK_NAME="rota-certa-0.1.181-atalhos-em-popup-endereco-completo-validado.apk"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cp "$APK_SOURCE" "$OUTPUT_DIR/$APK_NAME"

unzip -tqq "$OUTPUT_DIR/$APK_NAME"
unzip -l "$OUTPUT_DIR/$APK_NAME" | grep -F 'classes.dex'

APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
AAPT="$(find "$ANDROID_SDK_ROOT/build-tools" -name aapt -type f | sort -V | tail -n 1)"

"$AAPT" dump badging "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/badging.txt"
grep -F "package: name='br.com.mapeiaia.rotacerta' versionCode='5420' versionName='0.1.181'" "$OUTPUT_DIR/badging.txt"
"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_DIR/$APK_NAME" | tee "$OUTPUT_DIR/signature.txt"
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/signature.txt"
grep -qi 'd9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' "$OUTPUT_DIR/signature.txt"

for dex in $(zipinfo -1 "$OUTPUT_DIR/$APK_NAME" | grep -E '^classes([0-9]+)?\.dex$'); do
  unzip -p "$OUTPUT_DIR/$APK_NAME" "$dex"
done | strings > "$OUTPUT_DIR/dex-strings.txt"
grep -F 'SavedPlacePopupPolicy0181' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'ShortcutInPlacePolicy0181' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'SHORTCUT_IN_PLACE_POPUP_0181' "$OUTPUT_DIR/dex-strings.txt"

sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$after_hashes" "$OUTPUT_DIR/protected-source-sha256.txt"
{
  echo 'package=br.com.mapeiaia.rotacerta'
  echo 'versionName=0.1.181'
  echo 'versionCode=5420'
  echo 'scope=shortcut_grid_in_place_overlays_and_full_address'
  echo 'open_module_gesture_switches_app=false'
  echo 'internal_primary_actions_overlay_first=true'
  echo 'explicit_external_actions_keep_original_purpose=true'
  echo 'save_place_keeps_external_app_visible=true'
  echo 'save_alert_keeps_external_app_visible=true'
  echo 'save_popup_focusable=true'
  echo 'save_popup_fields=title_instruction_full_address_name_cancel_save'
  echo 'blank_place_name_fallback=Local salvo'
  echo 'blank_alert_name_fallback=Alerta de proximidade'
  echo 'gps_resolver_unchanged=true'
  echo 'manifest_permissions_unchanged=true'
  echo 'farol_route_ocr_protected=true'
} > "$OUTPUT_DIR/validation.txt"

cat "$OUTPUT_DIR/sha256.txt"
cat "$OUTPUT_DIR/signature.txt"
