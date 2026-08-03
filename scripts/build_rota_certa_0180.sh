#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"
BASE_BUILD="$PATCHES/scripts/build_rota_certa_0179.sh"
TRANSFORM="$PATCHES/scripts/fix_per_shortcut_menu_0180.py"
TRANSFORM_PARTS=(
  "$PATCHES/scripts/fix_per_shortcut_menu_0180.py.part00"
  "$PATCHES/scripts/fix_per_shortcut_menu_0180.py.part01"
  "$PATCHES/scripts/fix_per_shortcut_menu_0180.py.part02"
  "$PATCHES/scripts/fix_per_shortcut_menu_0180.py.part03"
  "$PATCHES/scripts/fix_per_shortcut_menu_0180.py.part04"
  "$PATCHES/scripts/fix_per_shortcut_menu_0180.py.part05"
)
TRANSFORM_PART_SHA256=(
  "0bb824a846857007de17b204cab6598f17a235a5c81bd4e30ff0ad3005ab8e5e"
  "de6e31eec2e38a3f0c41ca38b65f60f0e778ec84990e6fd8e1c55a25cd44a9af"
  "70eddf978841781a1d35948a703933ab97e0779ab8405d23d84561549d31831a"
  "05c6c09490396cb7f60d184ad987a4612dc4072b94e1d2081b62825da589df9d"
  "a4fef1d0be984639ebd2db0566957d6229d3fd2874b8f987449e86624df93760"
  "0e207aa05180c831edf42903e882dcec4d0101ec181c9866947c6d82a61ccf9d"
)
TRANSFORM_COMBINED_SHA256="edba90aff4a103e9b1a1c2dde8fc549d25b32bd8ba79251f48e8bf6e93a03caf"

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
combined_transform="$(mktemp --suffix=.py)"
cleanup() {
  rm -f "$before_hashes" "$after_hashes" "$combined_transform"
  if [[ "$cleanup_gradle_home" == "true" ]]; then
    rm -rf "$GRADLE_USER_HOME"
  fi
}
trap cleanup EXIT

for index in "${!TRANSFORM_PARTS[@]}"; do
  echo "${TRANSFORM_PART_SHA256[$index]}  ${TRANSFORM_PARTS[$index]}" | sha256sum --check
  cat "${TRANSFORM_PARTS[$index]}" >> "$combined_transform"
done
echo "$TRANSFORM_COMBINED_SHA256  $combined_transform" | sha256sum --check
python -m py_compile "$combined_transform"

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

python "$TRANSFORM"

sha256sum "${PROTECTED_FILES[@]}" > "$after_hashes"
diff -u "$before_hashes" "$after_hashes"

grep -F 'versionCode = 5410' app/build.gradle.kts
grep -F 'versionName = "0.1.180"' app/build.gradle.kts
grep -F 'per_shortcut_menu_0_1_180' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'SHORTCUT_LONG_PRESS_MILLIS: Long = 1_500L' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'SHORTCUT_CUSTOMIZATION_HOLD_MILLIS: Long = 5_000L' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'onShortcutCustomize(entry0179)' app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
grep -F 'executeShortcutQuickTap0180' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'executeShortcutHold0180' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'openShortcutEntryCustomization0180' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'Toque rápido:' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
grep -F 'Segurar 1,5 s:' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
grep -F 'Não fazer nada' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
grep -F 'Excluir da grade' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt

test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutPerEntryMenu0180Test.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutPerEntryMenuContract0180Test.kt

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
grep -F 'ShortcutPerEntryMenu0180Test' "$OUTPUT_DIR/dex-strings.txt" || true
grep -F 'SHORTCUT_HOLD_OPEN_EDITOR_0180' "$OUTPUT_DIR/dex-strings.txt"

sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$after_hashes" "$OUTPUT_DIR/protected-source-sha256.txt"
{
  echo 'package=br.com.mapeiaia.rotacerta'
  echo 'versionName=0.1.180'
  echo 'versionCode=5410'
  echo 'scope=per_shortcut_configurable_gestures'
  echo 'quick_tap=configurable_primary_module_none'
  echo 'hold_1500=configurable_primary_module_none'
  echo 'hold_5000=individual_shortcut_editor_reserved'
  echo 'five_seconds_does_not_fire_1500_action_first=true'
  echo 'individual_menu=quick_hold_none_delete'
  echo 'farol_protected=true'
  echo 'radars_protected=true'
  echo 'manifest_permissions_unchanged=true'
} > "$OUTPUT_DIR/validation.txt"

cat "$OUTPUT_DIR/sha256.txt"
cat "$OUTPUT_DIR/signature.txt"
