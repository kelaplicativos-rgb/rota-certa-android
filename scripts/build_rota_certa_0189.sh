#!/usr/bin/env bash
set -euo pipefail

PATCH_REPOSITORY="${1:?Informe o repositório cumulativo de patches}"
PATCH_REPOSITORY="$(git -C "$PATCH_REPOSITORY" rev-parse --show-toplevel)"
SOURCE_REPOSITORY="$(git rev-parse --show-toplevel)"
PATCH_ARCHIVE="$PATCH_REPOSITORY/patches/farol-priority-latency-0189.patch.gz.b64"
PATCH_FILE="$(mktemp --suffix=.farol-priority-latency-0189.patch)"
BEFORE_HASHES="$(mktemp)"
AFTER_HASHES="$(mktemp)"
TEST_COUNT_STAGING="$(mktemp)"
EXPECTED_PATCH_SHA="a64ae94d050499efcba6bc1b8231fe111fcb38b57030327cde911f0a46a06493"

cleanup() {
  rm -f "$PATCH_FILE" "$BEFORE_HASHES" "$AFTER_HASHES" "$TEST_COUNT_STAGING"
}
trap cleanup EXIT

# 1) Reproduz exatamente a candidata 0.1.188 que foi testada no aparelho.
bash "$PATCH_REPOSITORY/scripts/build_rota_certa_0188.sh" "$PATCH_REPOSITORY"
grep -Fq 'versionCode = 5472' app/build.gradle.kts
grep -Fq 'versionName = "0.1.188"' app/build.gradle.kts

# 2) O payload 0.1.189 precisa ser byte-exato. Nenhum patch parcial/corrompido entra.
test -s "$PATCH_ARCHIVE"
base64 --decode "$PATCH_ARCHIVE" | gzip --decompress > "$PATCH_FILE"
test -s "$PATCH_FILE"
ACTUAL_PATCH_SHA="$(sha256sum "$PATCH_FILE" | awk '{print $1}')"
echo "patch_0189_sha256=$ACTUAL_PATCH_SHA"
test "$ACTUAL_PATCH_SHA" = "$EXPECTED_PATCH_SHA" || {
  echo "Patch 0.1.189 divergente/corrompido: esperado=$EXPECTED_PATCH_SHA atual=$ACTUAL_PATCH_SHA" >&2
  exit 1
}

# 3) A correção pode alterar observação, segmentação, estados visuais e OCR,
#    mas não pode mudar decisão matemática, rota, alvo, Manifest, radares ou alertas.
PROTECTED_FILES=(
  app/src/main/AndroidManifest.xml
  app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GpsAddressResolver.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RadarImport.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt
)
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
    echo "Patch 0.1.189 tentou alterar fronteira protegida: $forbidden" >&2
    exit 1
  fi
done

git apply --check "$PATCH_FILE"
git apply "$PATCH_FILE"
python3 "$PATCH_REPOSITORY/scripts/harden_farol_priority_latency_0189.py" "$SOURCE_REPOSITORY"
sha256sum "${PROTECTED_FILES[@]}" > "$AFTER_HASHES"
diff -u "$BEFORE_HASHES" "$AFTER_HASHES"

grep -Fq 'versionCode = 5473' app/build.gradle.kts
grep -Fq 'versionName = "0.1.189"' app/build.gradle.kts

# 4) Toda a árvore final precisa passar novamente por testes, Lint e build limpo.
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
    raise SystemExit('Nenhum teste foi descoberto na 0.1.189')
if failures:
    raise SystemExit('Há testes com falha na 0.1.189')
PY
./gradlew lintDebug --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew clean assembleDebug --no-daemon --max-workers=1 --no-parallel --stacktrace

# 5) APK só existe como candidato depois de todos os controles anteriores.
APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.189"
APK_NAME="rota-certa-0.1.189-priority-latency-validado-em-ci.apk"
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
grep -F "package: name='br.com.mapeiaia.rotacerta' versionCode='5473' versionName='0.1.189'" "$OUTPUT_DIR/badging.txt"
printf '%s\n' 'br.com.mapeiaia.rotacerta' > "$OUTPUT_DIR/package.txt"
printf '%s\n' 'versionName=0.1.189' 'versionCode=5473' > "$OUTPUT_DIR/version.txt"

"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_DIR/$APK_NAME" | tee "$OUTPUT_DIR/signature.txt"
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/signature.txt"
grep -qi 'd9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' "$OUTPUT_DIR/signature.txt"

for dex in $(zipinfo -1 "$OUTPUT_DIR/$APK_NAME" | grep -E '^classes([0-9]+)?\.dex$'); do
  unzip -p "$OUTPUT_DIR/$APK_NAME" "$dex"
done | strings > "$OUTPUT_DIR/dex-strings.txt"
for marker in \
  'FAROL_TOP_BLOCK_AUTHORITY_0189' \
  'BUBBLE_DESTINATION_CONFIRMED_ORANGE_0189' \
  'OCR_FALLBACK_DEDUPED_0189' \
  'laranja'; do
  grep -Fq "$marker" "$OUTPUT_DIR/dex-strings.txt" || {
    echo "Contrato 0.1.189 ausente no DEX: $marker" >&2
    exit 1
  }
done

cp "$TEST_COUNT_STAGING" "$OUTPUT_DIR/test-count.txt"
grep -Fxq 'failures=0' "$OUTPUT_DIR/test-count.txt"
sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$AFTER_HASHES" "$OUTPUT_DIR/protected-source-sha256.txt"
printf '%s\n' "$EXPECTED_PATCH_SHA" > "$OUTPUT_DIR/patch-sha256.txt"
cat > "$OUTPUT_DIR/validation.txt" <<'VALIDATION'
package=br.com.mapeiaia.rotacerta
versionName=0.1.189
versionCode=5473
status=ci_candidate_pending_real_device
gray=inactive_or_external
yellow=selected_package_active_without_confirmed_destination
orange=confirmed_final_destination_route_pending
green_red=require_real_route_decision=true
top_window_block_has_authority=true
new_visual_authority_invalidates_previous=true
same_block_two_or_more_addresses_last_is_destination=true
cross_card_address_mixing=false
ocr_single_flight_per_generation_block=true
decision_engine_unchanged=true
route_engine_unchanged=true
manifest_permissions_unchanged=true
VALIDATION

cat "$OUTPUT_DIR/test-count.txt"
cat "$OUTPUT_DIR/sha256.txt"
echo 'rota_certa_0189_build=passed'
