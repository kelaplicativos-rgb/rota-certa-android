#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"
BASE_BUILD="$PATCHES/scripts/build_rota_certa_0179.sh"
FIXER_PARTS=(
  "$PATCHES/scripts/fix_per_shortcut_gestures_0180.py.part00"
  "$PATCHES/scripts/fix_per_shortcut_gestures_0180.py.part01"
  "$PATCHES/scripts/fix_per_shortcut_gestures_0180.py.part02"
  "$PATCHES/scripts/fix_per_shortcut_gestures_0180.py.part03"
)
FIXER_SHA256="096605354ba54c58ea676f75b1b57fc7904d79708ed72e243b33239c577ef281"
MATERIALIZER="$(mktemp --suffix=.sh)"
FIXER="$(mktemp --suffix=.py)"
BEFORE_HASHES="$(mktemp)"
AFTER_HASHES="$(mktemp)"
cleanup() {
  rm -f "$MATERIALIZER" "$FIXER" "$BEFORE_HASHES" "$AFTER_HASHES"
}
trap cleanup EXIT

python3 - "$BASE_BUILD" "$MATERIALIZER" <<'PY'
from pathlib import Path
import sys
src = Path(sys.argv[1]).read_text(encoding='utf-8')
needle = './gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace'
if src.count(needle) != 1:
    raise SystemExit('Could not isolate 0.1.179 materialization')
src = src.replace(needle, 'exit 0\n\n' + needle, 1)
Path(sys.argv[2]).write_text(src, encoding='utf-8')
PY
chmod +x "$MATERIALIZER"
bash "$MATERIALIZER" "$PATCHES"

PROTECTED_FILES=(
  app/src/main/AndroidManifest.xml
  app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutActivityLaunchPolicy0176.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RadarImport.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/Repositories.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/LiveSpeechEngine.kt
)
sha256sum "${PROTECTED_FILES[@]}" > "$BEFORE_HASHES"
cat "${FIXER_PARTS[@]}" > "$FIXER"
echo "$FIXER_SHA256  $FIXER" | sha256sum --check
python3 "$FIXER"
sha256sum "${PROTECTED_FILES[@]}" > "$AFTER_HASHES"
diff -u "$BEFORE_HASHES" "$AFTER_HASHES"

grep -F 'versionCode = 5410' app/build.gradle.kts
grep -F 'versionName = "0.1.180"' app/build.gradle.kts
grep -F 'SHORTCUT_LONG_PRESS_MILLIS: Long = 1_500L' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'SHORTCUT_CONFIGURATION_HOLD_MILLIS: Long = 5_000L' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'quickAction0180' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'holdAction0180' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'onCustomizeEntry0180' app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
grep -F 'EXTRA_OPEN_SHORTCUT_ENTRY_0180' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
grep -F 'executeShortcutGesture0180' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutGestureMenu0180Test.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutGestureMenuContract0180Test.kt

./gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew lintDebug --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew clean assembleDebug --no-daemon --max-workers=1 --no-parallel --stacktrace

APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.180"
APK_NAME="rota-certa-0.1.180-gestos-por-bolinha-validado.apk"
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
grep -F 'per_shortcut_gesture_menu_0_1_180' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'ShortcutGestureAction0180' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'SHORTCUT_CONFIGURATION_HOLD_MILLIS' "$OUTPUT_DIR/dex-strings.txt"

sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$AFTER_HASHES" "$OUTPUT_DIR/protected-source-sha256.txt"
{
  echo 'package=br.com.mapeiaia.rotacerta'
  echo 'versionName=0.1.180'
  echo 'versionCode=5410'
  echo 'scope=per_shortcut_gesture_menu'
  echo 'quick_tap=user_configurable'
  echo 'hold_1500=user_configurable'
  echo 'hold_5000=open_entry_configuration'
  echo 'actions=execute_immediately,open_module,none'
  echo 'menu_actions=quick_tap,hold_1500,none_both,delete_from_grid'
  echo 'farol_protected=true'
  echo 'radars_protected=true'
  echo 'manifest_permissions_unchanged=true'
} > "$OUTPUT_DIR/validation.txt"
cat "$OUTPUT_DIR/sha256.txt"
cat "$OUTPUT_DIR/signature.txt"
