#!/usr/bin/env bash
set -euo pipefail

PATCHES="${1:-../patches}"
BASE_BUILD="$PATCHES/scripts/build_rota_certa_0185.sh"
PATCH_ARCHIVE="$(mktemp --suffix=.patch.gz.b64)"
PATCH_ARCHIVE_SHA256="96eeb390e29798a407f6963936dc963b79385e7a2c4bb0c6796c9b90a76dccb9"
PATCH_SHA256="107497299518e76b43b8fd9469dbf3d51aa21142953418f5946f32f8cd414c27"
HARDENING_ARCHIVE_SOURCE="$PATCHES/patches/shortcut-hardening-0186.patch.gz.b64"
HARDENING_ARCHIVE_SHA256="71ac9605e1b56f4276f1a5443af85ae11fee023db1b71a8db8de96ad21ece274"
HARDENING_PATCH_SHA256="03d102222b81ca80ce7e6ea7f9c18f8e9f94fc8fea0828ecd465094e847e6ca0"
WHITESPACE_FIX_PATCH="$PATCHES/patches/shortcut-whitespace-fix-0186.patch"
PATCH_PARTS=(
  "$PATCHES/patches/shortcut-audio-links-text-correction-0186.patch.gz.b64.part00"
  "$PATCHES/patches/shortcut-audio-links-text-correction-0186.patch.gz.b64.part01"
  "$PATCHES/patches/shortcut-audio-links-text-correction-0186.patch.gz.b64.part02"
  "$PATCHES/patches/shortcut-audio-links-text-correction-0186.patch.gz.b64.part03"
  "$PATCHES/patches/shortcut-audio-links-text-correction-0186.patch.gz.b64.part04"
  "$PATCHES/patches/shortcut-audio-links-text-correction-0186.patch.gz.b64.part05"
  "$PATCHES/patches/shortcut-audio-links-text-correction-0186.patch.gz.b64.part06"
  "$PATCHES/patches/shortcut-audio-links-text-correction-0186.patch.gz.b64.part07"
)
PATCH_FILE="$(mktemp --suffix=.patch)"
HARDENING_ARCHIVE="$(mktemp --suffix=.patch.gz.b64)"
HARDENING_PATCH_FILE="$(mktemp --suffix=.patch)"

cleanup_gradle_home=false
if [[ -z "${GRADLE_USER_HOME:-}" ]]; then
  GRADLE_USER_HOME="$(mktemp -d)"
  cleanup_gradle_home=true
fi
export GRADLE_USER_HOME
mkdir -p "$GRADLE_USER_HOME"
cat > "$GRADLE_USER_HOME/gradle.properties" <<'GRADLE'
org.gradle.daemon=false
org.gradle.parallel=false
org.gradle.workers.max=1
org.gradle.vfs.watch=false
org.gradle.jvmargs=-Xmx2560m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8
kotlin.compiler.execution.strategy=in-process
kotlin.incremental=false
GRADLE

before_hashes="$(mktemp)"
after_hashes="$(mktemp)"
cleanup() {
  rm -f "$before_hashes" "$after_hashes" "$PATCH_FILE" "$PATCH_ARCHIVE" "$HARDENING_ARCHIVE" "$HARDENING_PATCH_FILE"
  if [[ "$cleanup_gradle_home" == "true" ]]; then rm -rf "$GRADLE_USER_HOME"; fi
}
trap cleanup EXIT

cat "${PATCH_PARTS[@]}" > "$PATCH_ARCHIVE"
printf '\n' >> "$PATCH_ARCHIVE"
echo "$PATCH_ARCHIVE_SHA256  $PATCH_ARCHIVE" | sha256sum --check
base64 --decode "$PATCH_ARCHIVE" | gzip -dc > "$PATCH_FILE"
echo "$PATCH_SHA256  $PATCH_FILE" | sha256sum --check
cp "$HARDENING_ARCHIVE_SOURCE" "$HARDENING_ARCHIVE"
printf '\n' >> "$HARDENING_ARCHIVE"
echo "$HARDENING_ARCHIVE_SHA256  $HARDENING_ARCHIVE" | sha256sum --check
base64 --decode "$HARDENING_ARCHIVE" | gzip -dc > "$HARDENING_PATCH_FILE"
echo "$HARDENING_PATCH_SHA256  $HARDENING_PATCH_FILE" | sha256sum --check

bash "$BASE_BUILD" "$PATCHES"

PROTECTED_FILES=(
  app/src/main/AndroidManifest.xml
  app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GpsAddressResolver.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RideCardConfirmationPolicy0185.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/ExplicitPackageTransitionPolicy0185.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/FarolRealtimeEventGate0167.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/FarolSelectedAppInputPolicy0166.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/FarolUnifiedVisual0168.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/AndroidServices.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
)
for protected_file in "${PROTECTED_FILES[@]}"; do
  test -f "$protected_file" || {
    echo "Arquivo protegido ausente: $protected_file" >&2
    exit 1
  }
done
sha256sum "${PROTECTED_FILES[@]}" > "$before_hashes"
git apply --check "$PATCH_FILE"
git apply "$PATCH_FILE"
git apply --check "$HARDENING_PATCH_FILE"
git apply "$HARDENING_PATCH_FILE"
git apply --check "$WHITESPACE_FIX_PATCH"
git apply "$WHITESPACE_FIX_PATCH"
sha256sum "${PROTECTED_FILES[@]}" > "$after_hashes"
diff -u "$before_hashes" "$after_hashes"

REQUIRED_FILES=(
  app/build.gradle.kts
  app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/SpeechOutputMode0186.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/QuickLinkSearchPolicy0186.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/TextCorrectionEngine0186.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/TextReplacementSession0186.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
)
for required_file in "${REQUIRED_FILES[@]}"; do
  test -f "$required_file" || {
    echo "Arquivo obrigatório ausente: $required_file" >&2
    exit 1
  }
done

grep -F 'versionCode = 5470' app/build.gradle.kts
grep -F 'versionName = "0.1.186"' app/build.gradle.kts
grep -F 'SHORTCUT_DIRECT_TAP_AND_HOLD_0186' app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
grep -F 'PERSISTED_HOLD_ACTION_0186' app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomization0179.kt
grep -F 'CONFIGURABLE_SPEECH_OUTPUT_0186' app/src/main/java/br/com/mapeiaia/rotacerta/SpeechOutputMode0186.kt
grep -F 'LOCAL_LINK_SEARCH_0186' app/src/main/java/br/com/mapeiaia/rotacerta/QuickLinkSearchPolicy0186.kt
grep -F 'OFFLINE_TEXT_CORRECTION_0186' app/src/main/java/br/com/mapeiaia/rotacerta/TextCorrectionEngine0186.kt
grep -F 'protectSpans' app/src/main/java/br/com/mapeiaia/rotacerta/TextCorrectionEngine0186.kt
grep -F 'allowedFinalLength' app/src/main/java/br/com/mapeiaia/rotacerta/TextReplacementSession0186.kt
grep -F 'removeExtra(EXTRA_TEXT_CORRECTION_INITIAL_0186)' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
grep -F 'QuickLinkCapacityPolicy0186.canCreate' app/src/main/java/br/com/mapeiaia/rotacerta/QuickLinksActivity.kt
grep -F 'cancelShortcutGestures0186' app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt
grep -F 'SAFE_TEXT_REPLACEMENT_0186' app/src/main/java/br/com/mapeiaia/rotacerta/TextReplacementSession0186.kt
grep -F 'EXTRA_HOME_LAUNCH_MODE_0186' app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt
! grep -F 'SHORTCUT_TRIPLE_TAP_OPEN_EDITOR_0180' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
! grep -F 'tapCount0180' app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt

git diff --check
./gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew lintDebug --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew clean assembleDebug --no-daemon --max-workers=1 --no-parallel --stacktrace

APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.186"
APK_NAME="rota-certa-0.1.186-grade-audio-links-corretor-validado.apk"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cp "$APK_SOURCE" "$OUTPUT_DIR/$APK_NAME"
unzip -tqq "$OUTPUT_DIR/$APK_NAME"
unzip -l "$OUTPUT_DIR/$APK_NAME" | grep -F 'classes.dex'
APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
AAPT="$(find "$ANDROID_SDK_ROOT/build-tools" -name aapt -type f | sort -V | tail -n 1)"
"$AAPT" dump badging "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/badging.txt"
grep -F "package: name='br.com.mapeiaia.rotacerta' versionCode='5470' versionName='0.1.186'" "$OUTPUT_DIR/badging.txt"
"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_DIR/$APK_NAME" | tee "$OUTPUT_DIR/signature.txt"
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/signature.txt"
grep -qi 'd9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' "$OUTPUT_DIR/signature.txt"
for dex in $(zipinfo -1 "$OUTPUT_DIR/$APK_NAME" | grep -E '^classes([0-9]+)?\.dex$'); do unzip -p "$OUTPUT_DIR/$APK_NAME" "$dex"; done | strings > "$OUTPUT_DIR/dex-strings.txt"
COMPILED_CONTRACTS=(
  ShortcutInteractionPolicy0186
  SpeechOutputMode0186
  QuickLinkSearchPolicy0186
  PortugueseTextCorrectionEngine0186
  TextReplacementSession0186
  TextCorrectionModule0186
  EXPLICIT_HOME_LAUNCH_0186
)
for contract in "${COMPILED_CONTRACTS[@]}"; do
  grep -F "$contract" "$OUTPUT_DIR/dex-strings.txt" || {
    echo "Contrato compilado ausente no DEX: $contract" >&2
    exit 1
  }
done
python3 - <<'PY' > "$OUTPUT_DIR/test-count.txt"
import glob, xml.etree.ElementTree as ET
count = 0
failures = 0
for path in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    root = ET.parse(path).getroot()
    count += int(root.attrib.get('tests', 0))
    failures += int(root.attrib.get('failures', 0)) + int(root.attrib.get('errors', 0))
print(f'tests={count}')
print(f'failures={failures}')
if count <= 0:
    raise SystemExit("Nenhum teste foi descoberto")
if failures:
    raise SystemExit(1)
PY
sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$after_hashes" "$OUTPUT_DIR/protected-source-sha256.txt"
cat > "$OUTPUT_DIR/validation.txt" <<'VALIDATION'
package=br.com.mapeiaia.rotacerta
versionName=0.1.186
versionCode=5470
scope=grid_close_collapsed_home_configurable_hold_audio_links_offline_text_correction
outside_grid_touch_consumed=true
main_bubble_closes_open_grid=true
farol_state_unchanged_by_grid_close=true
quick_tap_no_900ms_window=true
hold_threshold_millis=1500
hold_release_does_not_run_quick=true
drag_cancels_both_gestures=true
stale_hold_callbacks_cancelled=true
generic_home_collapsed=true
deliberate_module_launch_expands_only_requested=true
hold_action_persisted_and_typed=true
speech_modes=muted,alarm,media
single_tts_engine=true
links_search_local=true
links_buttons=open,copy,edit,delete
text_correction=offline_conservative_reviewable
url_email_spans_preserved=true
text_replacement=explicit_exact_context_only
text_replacement_overflow=fail_closed_without_truncation
quick_links_capacity=40_block_new_before_data_loss
text_correction_intent_extras_cleared=true
manifest_permissions_unchanged=true
farol_core_protected_by_sha256=true
protected_source_commit=32da54cd112c8ecb8b43b40c5cdb87ef13c4ec42
tests_discovered_positive=true
VALIDATION
cat "$OUTPUT_DIR/test-count.txt"
cat "$OUTPUT_DIR/sha256.txt"
cat "$OUTPUT_DIR/signature.txt"
