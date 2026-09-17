#!/usr/bin/env bash
set -euo pipefail

PATCH_REPOSITORY="${1:?Informe o repositório cumulativo de patches}"
PATCH_REPOSITORY="$(git -C "$PATCH_REPOSITORY" rev-parse --show-toplevel)"
SOURCE_REPOSITORY="$(git rev-parse --show-toplevel)"
PATCH_ARCHIVE="$PATCH_REPOSITORY/patches/farol-real-device-0188.patch.gz.b64"
PATCH_FILE="$(mktemp --suffix=.farol-real-device-0188.patch)"
BEFORE_HASHES="$(mktemp)"
AFTER_HASHES="$(mktemp)"
TEST_COUNT_STAGING="$(mktemp)"

cleanup() {
  rm -f "$PATCH_FILE" "$BEFORE_HASHES" "$AFTER_HASHES" "$TEST_COUNT_STAGING"
}
trap cleanup EXIT

# 1) Reproduce the exact cumulative 0.1.187 line before applying the real-device fix.
bash "$PATCH_REPOSITORY/scripts/build_rota_certa_0187.sh" "$PATCH_REPOSITORY"

grep -Fq 'versionCode = 5471' app/build.gradle.kts
grep -Fq 'versionName = "0.1.187"' app/build.gradle.kts

# 2) Decode the reviewed 0.1.188 patch from the exact PR head.
test -s "$PATCH_ARCHIVE"
base64 --decode "$PATCH_ARCHIVE" | gzip --decompress > "$PATCH_FILE"
test -s "$PATCH_FILE"
echo "patch_archive_sha256=$(sha256sum "$PATCH_ARCHIVE" | awk '{print $1}')"
echo "patch_sha256=$(sha256sum "$PATCH_FILE" | awk '{print $1}')"

# 3) The real-device fix may change observation/segmentation/OCR, but it may not
#    change route math, decision math, targets, alerts/radars, or permissions.
PROTECTED_FILES=(
  app/src/main/AndroidManifest.xml
  app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GpsAddressResolver.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RadarImport.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
  app/src/main/java/br/com/mapeiaai/rotacerta/DirectionalAlertOverlayController.kt
)
# Correct the package typo above fail-closed before hashing.
PROTECTED_FILES[8]='app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt'
for file in "${PROTECTED_FILES[@]}"; do
  test -f "$file" || { echo "Arquivo protegido ausente: $file" >&2; exit 1; }
done
sha256sum "${PROTECTED_FILES[@]}" > "$BEFORE_HASHES"

for forbidden in \
  'app/src/main/AndroidManifest.xml' \
  'DecisionEngine.kt' \
  'GoogleMapsService.kt' \
  'GpsAddressResolver.kt' \
  'RideTextParser.kt' \
  'RadarImport.kt' \
  'DirectionalAlertPolicy.kt' \
  'DirectionalProximityAlertEngine.kt' \
  'DirectionalAlertOverlayController.kt'; do
  if grep -Fq "$forbidden" "$PATCH_FILE"; then
    echo "Patch 0.1.188 tentou alterar fronteira protegida: $forbidden" >&2
    exit 1
  fi
done

git apply --check "$PATCH_FILE"
git apply "$PATCH_FILE"

# 4) Final hardening discovered from the real-device report. This marker is
#    intentionally present so the canonical wrapper does not inject it twice.
python3 "$PATCH_REPOSITORY/scripts/harden_farol_real_device_0188.py" "$SOURCE_REPOSITORY"
echo 'farol_real_device_0188_hardening=applied'

sha256sum "${PROTECTED_FILES[@]}" > "$AFTER_HASHES"
diff -u "$BEFORE_HASHES" "$AFTER_HASHES"

grep -Fq 'versionCode = 5472' app/build.gradle.kts
grep -Fq 'versionName = "0.1.188"' app/build.gradle.kts
grep -Fq 'fun authorizeRoute0188(' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -Fq 'BUBBLE_ROUTE_GATE_REJECTED_0188' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -Fq 'BUBBLE_FAILED_CARD_EVIDENCE_ONLY_0188' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
grep -Fq 'UniversalAddressTrigger.MINIMUM_VISIBLE_ADDRESSES' app/src/main/java/br/com/mapeiaia/rotacerta/FarolRealDeviceGate0188.kt
grep -Fq 'flagRetrieveInteractiveWindows' app/src/main/res/xml/rota_certa_accessibility.xml
test -f app/src/test/java/br/com/mapeiaia/rotacerta/FarolRealDevice0188Test.kt
echo 'farol_real_device_0188_post_patch_contracts=passed'

# 5) Validate the final 0.1.188 tree. No candidate can be emitted before all
#    unit contracts, Android Lint and a clean APK build succeed.
./gradlew testDebugUnitTest --no-daemon --max-workers=1 --no-parallel --stacktrace
python3 - <<'PY' > "$TEST_COUNT_STAGING"
import glob
import xml.etree.ElementTree as ET
count = 0
failures = 0
for report in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    root = ET.parse(report).getroot()
    count += int(root.attrib.get('tests', 0))
    failures += int(root.attrib.get('failures', 0)) + int(root.attrib.get('errors', 0))
print(f'tests={count}')
print(f'failures={failures}')
if count <= 0:
    raise SystemExit('Nenhum teste foi descoberto na 0.1.188')
if failures:
    raise SystemExit('Há testes com falha na 0.1.188')
PY

./gradlew lintDebug --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew clean assembleDebug --no-daemon --max-workers=1 --no-parallel --stacktrace

# 6) Validate APK identity, signature and compiled 0.1.188 contracts.
APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.188"
APK_NAME="rota-certa-0.1.188-farol-real-device-validado-em-ci.apk"
test -s "$APK_SOURCE"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cp "$APK_SOURCE" "$OUTPUT_DIR/$APK_NAME"
unzip -tqq "$OUTPUT_DIR/$APK_NAME"
unzip -l "$OUTPUT_DIR/$APK_NAME" | grep -F 'classes.dex'

APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
AAPT="$(find "$ANDROID_SDK_ROOT/build-tools" -name aapt -type f | sort -V | tail -n 1)"
test -x "$APKSIGNER"
test -x "$AAPT"
"$AAPT" dump badging "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/badging.txt"
grep -F "package: name='br.com.mapeiaia.rotacerta' versionCode='5472' versionName='0.1.188'" "$OUTPUT_DIR/badging.txt"
printf '%s\n' 'br.com.mapeiaia.rotacerta' > "$OUTPUT_DIR/package.txt"
printf '%s\n' 'versionName=0.1.188' 'versionCode=5472' > "$OUTPUT_DIR/version.txt"

"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_DIR/$APK_NAME" | tee "$OUTPUT_DIR/signature.txt"
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/signature.txt"
grep -qi 'd9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' "$OUTPUT_DIR/signature.txt"

for dex in $(zipinfo -1 "$OUTPUT_DIR/$APK_NAME" | grep -E '^classes([0-9]+)?\.dex$'); do
  unzip -p "$OUTPUT_DIR/$APK_NAME" "$dex"
done | strings > "$OUTPUT_DIR/dex-strings.txt"
for marker in \
  'BUBBLE_ROUTE_GATE_REJECTED_0188' \
  'BUBBLE_FAILED_CARD_EVIDENCE_ONLY_0188' \
  'authorizeRoute0188'; do
  grep -Fq "$marker" "$OUTPUT_DIR/dex-strings.txt" || {
    echo "Contrato 0.1.188 ausente no DEX: $marker" >&2
    exit 1
  }
done

cp "$TEST_COUNT_STAGING" "$OUTPUT_DIR/test-count.txt"
grep -Fxq 'failures=0' "$OUTPUT_DIR/test-count.txt"
sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$AFTER_HASHES" "$OUTPUT_DIR/protected-source-sha256.txt"
cat > "$OUTPUT_DIR/validation.txt" <<'VALIDATION'
package=br.com.mapeiaia.rotacerta
versionName=0.1.188
versionCode=5472
status=ci_candidate_pending_real_device
selected_package_observation_only=true
coherent_current_card_required=true
same_window_block_required=true
cross_card_address_mixing=false
failed_card_recovery_can_paint=false
passive_security_status_screens_fail_closed=true
decision_engine_unchanged=true
route_engine_unchanged=true
manifest_permissions_unchanged=true
VALIDATION

cat "$OUTPUT_DIR/test-count.txt"
cat "$OUTPUT_DIR/sha256.txt"
echo 'rota_certa_0188_build=passed'
