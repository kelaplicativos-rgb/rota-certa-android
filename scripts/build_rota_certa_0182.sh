#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"
BASE_BUILD="$PATCHES/scripts/build_rota_certa_0181.sh"
TRANSFORM="$PATCHES/scripts/fix_direct_shortcuts_0182.py"

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

python -m py_compile "$TRANSFORM"
bash "$BASE_BUILD" "$PATCHES"

PROTECTED_FILES=(
  app/src/main/AndroidManifest.xml
  app/src/main/java/br/com/mapeiaia/rotacerta/GpsAddressResolver.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RadarImport.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/Repositories.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/LiveSpeechEngine.kt
)
sha256sum "${PROTECTED_FILES[@]}" > "$before_hashes"

python "$TRANSFORM"

sha256sum "${PROTECTED_FILES[@]}" > "$after_hashes"
diff -u "$before_hashes" "$after_hashes"

grep -F 'versionCode = 5430' app/build.gradle.kts
grep -F 'versionName = "0.1.182"' app/build.gradle.kts
grep -F 'SHORTCUT_DIRECT_TAP_0182' app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
grep -F 'ShortcutDirectTapPolicy0182.actionForTap(entry0180.quickAction0180)' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'PRIMARY_ACTION -> dispatchShortcutPrimaryDirect0182(entry0180.spec)' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'else -> executeShortcutModule(spec)' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'Um toque na bolinha principal abre a grade; o toque seguinte executa o atalho imediatamente.' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
grep -F 'Ação: um toque executa imediatamente' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
! grep -F 'tapCount0180' app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
! grep -F 'SHORTCUT_TRIPLE_TAP_WINDOW_MILLIS' app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
! grep -F 'in ShortcutInPlacePolicy0181.overlayFirstIds -> showShortcutModulePopup0181(spec)' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt

test -f app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutDirectTapPolicy0182.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutDirectTapPolicy0182Test.kt

./gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew lintDebug --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew clean assembleDebug --no-daemon --max-workers=1 --no-parallel --stacktrace

APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.182"
APK_NAME="rota-certa-0.1.182-grade-atalhos-dois-toques-validado.apk"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cp "$APK_SOURCE" "$OUTPUT_DIR/$APK_NAME"

unzip -tqq "$OUTPUT_DIR/$APK_NAME"
unzip -l "$OUTPUT_DIR/$APK_NAME" | grep -F 'classes.dex'

APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
AAPT="$(find "$ANDROID_SDK_ROOT/build-tools" -name aapt -type f | sort -V | tail -n 1)"

"$AAPT" dump badging "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/badging.txt"
grep -F "package: name='br.com.mapeiaia.rotacerta' versionCode='5430' versionName='0.1.182'" "$OUTPUT_DIR/badging.txt"
"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_DIR/$APK_NAME" | tee "$OUTPUT_DIR/signature.txt"
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/signature.txt"
grep -qi 'd9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' "$OUTPUT_DIR/signature.txt"

for dex in $(zipinfo -1 "$OUTPUT_DIR/$APK_NAME" | grep -E '^classes([0-9]+)?\.dex$'); do
  unzip -p "$OUTPUT_DIR/$APK_NAME" "$dex"
done | strings > "$OUTPUT_DIR/dex-strings.txt"
grep -F 'ShortcutDirectTapPolicy0182' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'SHORTCUT_DIRECT_TAP_0182' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'single_tap_direct_0182' "$OUTPUT_DIR/dex-strings.txt"

sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$after_hashes" "$OUTPUT_DIR/protected-source-sha256.txt"
{
  echo 'package=br.com.mapeiaia.rotacerta'
  echo 'versionName=0.1.182'
  echo 'versionCode=5430'
  echo 'scope=direct_shortcut_grid_two_taps_maximum'
  echo 'main_bubble_single_tap=opens_grid'
  echo 'shortcut_single_tap=executes_primary_action_immediately'
  echo 'maximum_taps_main_to_action=2'
  echo 'triple_tap_window_removed=true'
  echo 'shortcut_hold_classification_removed=true'
  echo 'configuration_route=permanent_plus_shortcut'
  echo 'generic_intermediate_popup_on_primary=false'
  echo 'saved_place_and_alert_action_popup_preserved=true'
  echo 'name_icon_order_visibility_resource_preserved=true'
  echo 'manifest_permissions_unchanged=true'
  echo 'farol_route_ocr_protected=true'
} > "$OUTPUT_DIR/validation.txt"

cat "$OUTPUT_DIR/sha256.txt"
cat "$OUTPUT_DIR/signature.txt"
