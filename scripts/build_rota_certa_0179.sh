#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"
BASE_BUILD="$PATCHES/scripts/build_rota_certa_0178.sh"
PATCH_PARTS=(
  "$PATCHES/patches/customizable-shortcut-grid-0179.patch.part00"
  "$PATCHES/patches/customizable-shortcut-grid-0179.patch.part01"
  "$PATCHES/patches/customizable-shortcut-grid-0179.patch.part02"
  "$PATCHES/patches/customizable-shortcut-grid-0179.patch.part03"
)
PATCH_SHA256="4167d17dc9cde54d2ae3962c3480bdc8211d43f3b7e969ef9b252cb829a3aa8c"
CONTRACT_PATCH="$PATCHES/patches/customizable-shortcut-grid-0179-contracts.patch"
CONTRACT_PATCH_SHA256="181bb7a9036db57a95d56234cf0859bbb2f8c9cf45a79d0fe5570fa800f4d721"

# Toda a cadeia cumulativa (0.1.177 -> 0.1.178 -> 0.1.179) herda estes
# limites. Isso impede daemons Kotlin separados e compilação paralela de
# excederem a memória do runner sem remover testes, lint ou assemble.
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
patch_file="$(mktemp --suffix=.patch)"
cleanup() {
  rm -f "$before_hashes" "$after_hashes" "$patch_file"
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
cat "${PATCH_PARTS[@]}" > "$patch_file"
echo "$PATCH_SHA256  $patch_file" | sha256sum --check
git apply --check "$patch_file"
git apply "$patch_file"

echo "$CONTRACT_PATCH_SHA256  $CONTRACT_PATCH" | sha256sum --check
git apply --check "$CONTRACT_PATCH"
git apply "$CONTRACT_PATCH"

sha256sum "${PROTECTED_FILES[@]}" > "$after_hashes"
diff -u "$before_hashes" "$after_hashes"

grep -F 'versionCode = 5400' app/build.gradle.kts
grep -F 'versionName = "0.1.179"' app/build.gradle.kts
grep -F 'shortcut_grid_customization_0_1_179' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'SHORTCUT_LONG_PRESS_MILLIS: Long = 2_000L' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'MAIN_CUSTOMIZATION_HOLD_MILLIS: Long = 5_000L' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'shortcut_add_0179' app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
grep -F 'ShortcutGridCustomizationScreen0179' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
grep -F 'onShortcutLongPress = ::executeShortcutLongPress0179' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -F 'newView.setOnClickListener { toggleResourceShortcuts() }' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt

test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179Test.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomizationContract0179Test.kt
grep -F 'ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS' app/src/test/java/br/com/mapeiaia/rotacerta/AuthorizedAppsCards146ContractTest.kt
grep -F 'onShortcut(entry0179.spec)' app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutActivityLaunchContract0176Test.kt

./gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew lintDebug --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew clean assembleDebug --no-daemon --max-workers=1 --no-parallel --stacktrace

APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.179"
APK_NAME="rota-certa-0.1.179-grade-atalhos-personalizavel-validado.apk"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cp "$APK_SOURCE" "$OUTPUT_DIR/$APK_NAME"

unzip -tqq "$OUTPUT_DIR/$APK_NAME"
unzip -l "$OUTPUT_DIR/$APK_NAME" | grep -F 'classes.dex'

APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
AAPT="$(find "$ANDROID_SDK_ROOT/build-tools" -name aapt -type f | sort -V | tail -n 1)"

"$AAPT" dump badging "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/badging.txt"
grep -F "package: name='br.com.mapeiaia.rotacerta' versionCode='5400' versionName='0.1.179'" "$OUTPUT_DIR/badging.txt"
"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_DIR/$APK_NAME" | tee "$OUTPUT_DIR/signature.txt"
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/signature.txt"
grep -qi 'd9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' "$OUTPUT_DIR/signature.txt"

for dex in $(zipinfo -1 "$OUTPUT_DIR/$APK_NAME" | grep -E '^classes([0-9]+)?\.dex$'); do
  unzip -p "$OUTPUT_DIR/$APK_NAME" "$dex"
done | strings > "$OUTPUT_DIR/dex-strings.txt"
grep -F 'ShortcutGridPreferenceStore0179' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'ShortcutGridCustomizationPolicy0179' "$OUTPUT_DIR/dex-strings.txt"
grep -F 'ShortcutGesturePolicy0179' "$OUTPUT_DIR/dex-strings.txt"

sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$after_hashes" "$OUTPUT_DIR/protected-source-sha256.txt"
{
  echo 'package=br.com.mapeiaia.rotacerta'
  echo 'versionName=0.1.179'
  echo 'versionCode=5400'
  echo 'scope=customizable_shortcut_grid'
  echo 'default_migration=preserve_current_catalog'
  echo 'shortcut_short_tap=selected_primary_action'
  echo 'shortcut_hold_seconds=2'
  echo 'main_bubble_hold_seconds=5'
  echo 'main_bubble_single_tap=toggle_grid_preserved'
  echo 'main_bubble_double_tap=create_alert_preserved'
  echo 'farol_protected=true'
  echo 'radars_protected=true'
  echo 'manifest_permissions_unchanged=true'
} > "$OUTPUT_DIR/validation.txt"

cat "$OUTPUT_DIR/sha256.txt"
cat "$OUTPUT_DIR/signature.txt"
