#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"
BASE_BUILD="$PATCHES/scripts/build_rota_certa_0183.sh"
TRANSFORM_B64="$PATCHES/scripts/fix_home_action_shortcuts_0184.py.gz.b64"
TRANSFORM="$(mktemp --suffix=.py)"

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
  rm -f "$before_hashes" "$after_hashes" "$TRANSFORM"
  if [[ "$cleanup_gradle_home" == "true" ]]; then
    rm -rf "$GRADLE_USER_HOME"
  fi
}
trap cleanup EXIT

base64 --decode "$TRANSFORM_B64" | gzip --decompress > "$TRANSFORM"
echo "bae0df391e9fee86b36914c1d1e4def6b9d3bb4ef70c3fb2ed95518e7b7e2c1f  $TRANSFORM" | sha256sum --check
python -m py_compile "$TRANSFORM"
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

python "$TRANSFORM"

sha256sum "${PROTECTED_FILES[@]}" > "$after_hashes"
diff -u "$before_hashes" "$after_hashes"

grep -F 'versionCode = 5450' app/build.gradle.kts
grep -F 'versionName = "0.1.184"' app/build.gradle.kts
grep -F 'SHORTCUT_ACTION_CATALOG_0184' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutActionCatalog0184.kt
grep -F 'SHORTCUT_GRID_EMPTY_FRESH_INSTALL_0184' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'executeShortcutAction0184(entry0180.shortcutId)' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
! grep -F 'showShortcutActionMenu0183(entry0180.spec)' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
! grep -F 'shortcut_add_0179' app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
grep -F 'ShortcutActionControls0184(spec.id)' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
grep -F 'const val REQUIRED_APPROACHING_SAMPLES = 2' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
grep -F 'const val TARGET_AHEAD_TOLERANCE_DEGREES = 40.0' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt
grep -F 'const val RADAR_DIRECTION_TOLERANCE_DEGREES = 28.0' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt
grep -F 'savedAlertDirectionMatches' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt

test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutActionCatalog0184Test.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutGridActionMigration0184Test.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/DirectionalOppositeTraffic0184Test.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutActionContract0184Test.kt

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

for dex in $(zipinfo -1 "$OUTPUT_DIR/$APK_NAME" | grep -E '^classes([0-9]+)?\.dex$'); do
  unzip -p "$OUTPUT_DIR/$APK_NAME" "$dex"
done | strings > "$OUTPUT_DIR/dex-strings.txt"
grep -F 'SHORTCUT_ACTION_CATALOG_0184' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'SHORTCUT_ACTION_EXECUTED_0184' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'SHORTCUT_GRID_EMPTY_FRESH_INSTALL_0184' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'Criar backup' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'Restaurar backup' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'Capturar WhatsApp' "$OUTPUT_DIR/dex-strings.txt"

sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$after_hashes" "$OUTPUT_DIR/protected-source-sha256.txt"
{
  echo 'package=br.com.mapeiaia.rotacerta'
  echo 'versionName=0.1.184'
  echo 'versionCode=5450'
  echo 'scope=home_action_catalog_empty_grid_strict_direction'
  echo 'home_catalog_complete=true'
  echo 'grid_unit=typed_action'
  echo 'fresh_install_grid=empty'
  echo 'existing_grid_migration=preserved'
  echo 'maximum_active_actions=32'
  echo 'main_bubble_empty_grid=opens_home'
  echo 'shortcut_single_tap=executes_action_directly'
  echo 'generic_context_menu_in_tap_path=false'
  echo 'direction_requires_recent_accurate_heading=true'
  echo 'direction_requires_two_progress_samples=true'
  echo 'manual_alert_radar_capture_heading_when_available=true'
  echo 'arbitrary_intents_or_code=false'
  echo 'manifest_permissions_unchanged=true'
  echo 'farol_route_ocr_protected=true'
} > "$OUTPUT_DIR/validation.txt"

cat "$OUTPUT_DIR/sha256.txt"
cat "$OUTPUT_DIR/signature.txt"
