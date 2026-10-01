#!/usr/bin/env bash
set -euo pipefail

PATCH_REPOSITORY="${1:?Informe o repositório cumulativo de patches}"
PATCH_REPOSITORY="$(git -C "$PATCH_REPOSITORY" rev-parse --show-toplevel)"
SOURCE_REPOSITORY="$(git rev-parse --show-toplevel)"
BEFORE_HASHES="$(mktemp)"
AFTER_HASHES="$(mktemp)"
TEST_COUNT_STAGING="$(mktemp)"

cleanup() {
  rm -f "$BEFORE_HASHES" "$AFTER_HASHES" "$TEST_COUNT_STAGING"
}
trap cleanup EXIT

# 1) Reproduz exatamente a 0.1.190, incluindo o pop-up de 3 segundos já aprovado em CI.
bash "$PATCH_REPOSITORY/scripts/build_rota_certa_0190.sh" "$PATCH_REPOSITORY"
grep -Fq 'versionCode = 5474' app/build.gradle.kts
grep -Fq 'versionName = "0.1.190"' app/build.gradle.kts
grep -Fq 'const val PASSED_CLOSE_DELAY_MILLIS = 3_000L' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt

# 2) A 0.1.191 só pode alterar versão, política/motor de proximidade e teste novo.
#    Farol, rota, OCR, Manifest, importação de radar, overlay de 3 s e serviço ficam protegidos.
PROTECTED_FILES=(
  app/src/main/AndroidManifest.xml
  app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GpsAddressResolver.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RadarImport.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
)
for file in "${PROTECTED_FILES[@]}"; do
  test -f "$file" || { echo "Arquivo protegido ausente: $file" >&2; exit 1; }
done
sha256sum "${PROTECTED_FILES[@]}" > "$BEFORE_HASHES"

python3 "$PATCH_REPOSITORY/scripts/apply_proximity_alerts_no_direction_0191.py" "$SOURCE_REPOSITORY"

sha256sum "${PROTECTED_FILES[@]}" > "$AFTER_HASHES"
diff -u "$BEFORE_HASHES" "$AFTER_HASHES"

grep -Fq 'versionCode = 5475' app/build.gradle.kts
grep -Fq 'versionName = "0.1.191"' app/build.gradle.kts
! grep -Fq 'fix.headingDegrees != null' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt
! grep -Fq 'DirectionalAlertPolicy.isTargetAhead(' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
! grep -Fq 'DirectionalAlertPolicy.radarDirectionMatches(' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
! grep -Fq 'sentido confirmado' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
! grep -Fq 'direção confirmada' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
grep -Fq 'runtime.hasPassed(distance)' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
grep -Fq 'status = "Aproximando"' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
grep -Fq 'const val PASSED_CLOSE_DELAY_MILLIS = 3_000L' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt
grep -Fq 'onDismiss = { directionalAlertEngineChecklist5.dismissUntilExit(visual.targetId) }' app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
test -f app/src/test/java/br/com/mapeiaia/rotacerta/ProximityAlertsNoDirection0191ContractTest.kt

echo 'proximity_alerts_no_direction_0191_contracts=passed'

# 3) Revalida toda a árvore final.
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
    raise SystemExit('Nenhum teste foi descoberto na 0.1.191')
if failures:
    raise SystemExit('Há testes com falha na 0.1.191')
PY
./gradlew lintDebug --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew clean assembleDebug --no-daemon --max-workers=1 --no-parallel --stacktrace

# 4) Emite candidato somente depois de pacote, versão, assinatura e integridade.
APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.191"
APK_NAME="rota-certa-0.1.191-alertas-sem-filtro-sentido-validado-em-ci.apk"
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
grep -F "package: name='br.com.mapeiaia.rotacerta' versionCode='5475' versionName='0.1.191'" "$OUTPUT_DIR/badging.txt"
printf '%s\n' 'br.com.mapeiaia.rotacerta' > "$OUTPUT_DIR/package.txt"
printf '%s\n' 'versionName=0.1.191' 'versionCode=5475' > "$OUTPUT_DIR/version.txt"

"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_DIR/$APK_NAME" | tee "$OUTPUT_DIR/signature.txt"
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/signature.txt"
grep -qi 'd9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' "$OUTPUT_DIR/signature.txt"

cp "$TEST_COUNT_STAGING" "$OUTPUT_DIR/test-count.txt"
grep -Fxq 'failures=0' "$OUTPUT_DIR/test-count.txt"
sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$AFTER_HASHES" "$OUTPUT_DIR/protected-source-sha256.txt"
cat > "$OUTPUT_DIR/validation.txt" <<'VALIDATION'
package=br.com.mapeiaia.rotacerta
versionName=0.1.191
versionCode=5475
status=ci_candidate_pending_real_device
proximity_alerts_direction_gate=false
gps_heading_required_for_alert=false
imported_radar_direction_required=false
approach_basis=distance_trend
passed_basis=distance_increase_after_minimum
popup_after_pass_auto_close_ms=3000
manual_close_suppresses_same_target_until_exit=true
future_reapproach_reenabled_after_exit=true
farol_unchanged=true
route_engine_unchanged=true
ocr_unchanged=true
manifest_permissions_unchanged=true
VALIDATION

cat "$OUTPUT_DIR/test-count.txt"
cat "$OUTPUT_DIR/sha256.txt"
echo 'rota_certa_0191_build=passed'
