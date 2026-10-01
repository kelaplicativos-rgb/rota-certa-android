#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"
BASE_BUILD="$PATCHES/scripts/build_rota_certa_0182.sh"
TRANSFORM="$PATCHES/scripts/fix_contextual_shortcuts_0183.py"

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
  app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
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

grep -F 'versionCode = 5440' app/build.gradle.kts
grep -F 'versionName = "0.1.183"' app/build.gradle.kts
grep -F 'showShortcutActionMenu0183(entry0180.spec)' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'SHORTCUT_CONTEXT_MENU_0183' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'Criar alerta aqui' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutContextMenuPolicy0183.kt
grep -F 'Criar radar neste local' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutContextMenuPolicy0183.kt
grep -F 'Usar localização atual como destino' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutContextMenuPolicy0183.kt
grep -F 'Capturar aplicativo e tela agora' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutContextMenuPolicy0183.kt
grep -F 'Limpar área de transferência' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'Limpar cache do Rota Certa' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'scope.launch(Dispatchers.IO)' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
! grep -F 'ShortcutDirectTapPolicy0182.actionForTap(entry0180.quickAction0180)' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt

test -f app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutContextMenuPolicy0183.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutContextMenuPolicy0183Test.kt

./gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew lintDebug --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew clean assembleDebug --no-daemon --max-workers=1 --no-parallel --stacktrace

APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.183"
APK_NAME="rota-certa-0.1.183-menu-contextual-atalhos-validado.apk"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cp "$APK_SOURCE" "$OUTPUT_DIR/$APK_NAME"

unzip -tqq "$OUTPUT_DIR/$APK_NAME"
unzip -l "$OUTPUT_DIR/$APK_NAME" | grep -F 'classes.dex'

APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
AAPT="$(find "$ANDROID_SDK_ROOT/build-tools" -name aapt -type f | sort -V | tail -n 1)"

"$AAPT" dump badging "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/badging.txt"
grep -F "package: name='br.com.mapeiaia.rotacerta' versionCode='5440' versionName='0.1.183'" "$OUTPUT_DIR/badging.txt"
"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_DIR/$APK_NAME" | tee "$OUTPUT_DIR/signature.txt"
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/signature.txt"
grep -qi 'd9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' "$OUTPUT_DIR/signature.txt"

for dex in $(zipinfo -1 "$OUTPUT_DIR/$APK_NAME" | grep -E '^classes([0-9]+)?\.dex$'); do
  unzip -p "$OUTPUT_DIR/$APK_NAME" "$dex"
done | strings > "$OUTPUT_DIR/dex-strings.txt"
grep -F 'ShortcutContextMenuPolicy0183' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'SHORTCUT_CONTEXT_MENU_0183' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'Criar alerta aqui' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'Limpar cache do Rota Certa' "$OUTPUT_DIR/dex-strings.txt"

sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$after_hashes" "$OUTPUT_DIR/protected-source-sha256.txt"
{
  echo 'package=br.com.mapeiaia.rotacerta'
  echo 'versionName=0.1.183'
  echo 'versionCode=5440'
  echo 'scope=contextual_shortcut_action_menu'
  echo 'main_bubble_single_tap=opens_grid'
  echo 'shortcut_single_tap=opens_context_menu'
  echo 'alert_quick_action=create_alert_here'
  echo 'radar_quick_action=create_radar_here'
  echo 'destination_quick_action=use_current_location'
  echo 'capture_quick_action=capture_now'
  echo 'module_open_action=available'
  echo 'clear_clipboard_action=available'
  echo 'clear_own_cache_action=available'
  echo 'cache_clear_scope=own_app_only'
  echo 'drag_cancels_shortcut_tap=true'
  echo 'manifest_permissions_unchanged=true'
  echo 'farol_route_ocr_protected=true'
} > "$OUTPUT_DIR/validation.txt"

cat "$OUTPUT_DIR/sha256.txt"
cat "$OUTPUT_DIR/signature.txt"
