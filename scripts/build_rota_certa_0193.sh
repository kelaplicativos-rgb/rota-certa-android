#!/usr/bin/env bash
set -euo pipefail

PATCH_REPOSITORY="${1:?Informe o repositório cumulativo de patches}"
PATCH_REPOSITORY="$(git -C "$PATCH_REPOSITORY" rev-parse --show-toplevel)"
SOURCE_REPOSITORY="$(git rev-parse --show-toplevel)"
BEFORE_HASHES="$(mktemp)"
AFTER_HASHES="$(mktemp)"
TEST_COUNT_STAGING="$(mktemp)"
cleanup() { rm -f "$BEFORE_HASHES" "$AFTER_HASHES" "$TEST_COUNT_STAGING"; }
trap cleanup EXIT

# 1) Reproduz exatamente a 0.1.192 já aprovada.
bash "$PATCH_REPOSITORY/scripts/build_rota_certa_0192.sh" "$PATCH_REPOSITORY"
grep -Fq 'versionCode = 5476' app/build.gradle.kts
grep -Fq 'versionName = "0.1.192"' app/build.gradle.kts
grep -Fq 'object FarolFlightRecorder0163' app/src/main/java/br/com/mapeiaia/rotacerta/FarolFlightRecorder0163.kt
grep -Fq 'FarolFlightRecorder0163.exportReport' app/src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt
grep -Fq 'fun hideFromEngineIdle()' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt
grep -Fq 'const val PASSED_CLOSE_DELAY_MILLIS = 3_000L' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt

# 2) A 0.1.193 é somente diagnóstico/telemetria; núcleo funcional fica protegido byte a byte.
PROTECTED_FILES=(
  app/src/main/AndroidManifest.xml
  app/src/main/java/br/com/mapeiaia/rotacerta/DecisionEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/GpsAddressResolver.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/RadarImport.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertPolicy.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt
  app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
)
for file in "${PROTECTED_FILES[@]}"; do test -f "$file" || { echo "Arquivo protegido ausente: $file" >&2; exit 1; }; done
sha256sum "${PROTECTED_FILES[@]}" > "$BEFORE_HASHES"

python3 "$PATCH_REPOSITORY/scripts/apply_forensic_incident_monitor_0193.py" "$SOURCE_REPOSITORY"
python3 "$PATCH_REPOSITORY/scripts/enhance_forensic_popup_timing_0193.py" "$SOURCE_REPOSITORY"

sha256sum "${PROTECTED_FILES[@]}" > "$AFTER_HASHES"
diff -u "$BEFORE_HASHES" "$AFTER_HASHES"

grep -Fq 'versionCode = 5477' app/build.gradle.kts
grep -Fq 'versionName = "0.1.193"' app/build.gradle.kts
grep -Fq 'ForensicIncidentMonitor0193.observe(stage, packageName, details)' app/src/main/java/br/com/mapeiaia/rotacerta/FarolFlightRecorder0163.kt
grep -Fq 'ForensicIncidentMonitor0193.markManualReport()' app/src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt
grep -Fq 'ALERT_OVERLAY_POST_PASS_SCHEDULED_0193' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt
grep -Fq 'ALERT_OVERLAY_POST_PASS_TIMEOUT_FIRED_0193' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt
grep -Fq 'ALERT_OVERLAY_PENDING_CLOSE_CANCELLED_0193' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt
grep -Fq 'FORENSIC_ALERT_POPUP_EARLY_TIMEOUT_0193' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt
grep -Fq 'ALERT_OVERLAY_ENGINE_IDLE_PRESERVED_0193' app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt
grep -Fq 'FORENSIC_EVENT_STORM_0193' app/src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt
grep -Fq 'FORENSIC_STALE_GENERATION_RESULT_0193' app/src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt
grep -Fq 'FORENSIC_MANUAL_INCIDENT_MARK_0193' app/src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt
! grep -E -n 'Timer\(|scheduleAtFixedRate|while \(true\)|takeScreenshot|delay\(' app/src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt

echo 'forensic_incident_monitor_0193_contracts=passed'

# 3) Testes, Lint e build final.
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
if count < 388:
    raise SystemExit(f'Esperados pelo menos 388 testes após 0.1.193, encontrados {count}')
if failures:
    raise SystemExit('Há testes com falha na 0.1.193')
PY
./gradlew lintDebug --no-daemon --max-workers=1 --no-parallel --stacktrace
./gradlew clean assembleDebug --no-daemon --max-workers=1 --no-parallel --stacktrace

# 4) Validação do APK e evidências.
APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="artifact-0.1.193"
APK_NAME="rota-certa-0.1.193-diagnostico-forense-validado-em-ci.apk"
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
grep -F "package: name='br.com.mapeiaia.rotacerta' versionCode='5477' versionName='0.1.193'" "$OUTPUT_DIR/badging.txt"
printf '%s\n' 'br.com.mapeiaia.rotacerta' > "$OUTPUT_DIR/package.txt"
printf '%s\n' 'versionName=0.1.193' 'versionCode=5477' > "$OUTPUT_DIR/version.txt"

"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_DIR/$APK_NAME" | tee "$OUTPUT_DIR/signature.txt"
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' "$OUTPUT_DIR/signature.txt"
grep -qi 'd9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd' "$OUTPUT_DIR/signature.txt"

for dex in $(zipinfo -1 "$OUTPUT_DIR/$APK_NAME" | grep -E '^classes([0-9]+)?\.dex$'); do unzip -p "$OUTPUT_DIR/$APK_NAME" "$dex"; done | strings > "$OUTPUT_DIR/dex-strings.txt"
grep -Fq 'ForensicIncidentMonitor0193' "$OUTPUT_DIR/dex-strings.txt"
grep -Fq 'FORENSIC_MANUAL_INCIDENT_MARK_0193' "$OUTPUT_DIR/dex-strings.txt"
grep -Fq 'ALERT_OVERLAY_POST_PASS_SCHEDULED_0193' "$OUTPUT_DIR/dex-strings.txt"
grep -Fq 'ALERT_OVERLAY_POST_PASS_TIMEOUT_FIRED_0193' "$OUTPUT_DIR/dex-strings.txt"
grep -Fq 'ALERT_OVERLAY_PENDING_CLOSE_CANCELLED_0193' "$OUTPUT_DIR/dex-strings.txt"
grep -Fq 'FORENSIC_ALERT_POPUP_EARLY_TIMEOUT_0193' "$OUTPUT_DIR/dex-strings.txt"
grep -Fq 'FarolFlightRecorder0163' "$OUTPUT_DIR/dex-strings.txt"

cp "$TEST_COUNT_STAGING" "$OUTPUT_DIR/test-count.txt"
grep -Fxq 'failures=0' "$OUTPUT_DIR/test-count.txt"
sha256sum "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/sha256.txt"
stat --printf='%s\n' "$OUTPUT_DIR/$APK_NAME" > "$OUTPUT_DIR/size-bytes.txt"
cp "$AFTER_HASHES" "$OUTPUT_DIR/protected-source-sha256.txt"
cat > "$OUTPUT_DIR/validation.txt" <<'VALIDATION'
package=br.com.mapeiaia.rotacerta
versionName=0.1.193
versionCode=5477
status=ci_candidate_pending_real_device
existing_flight_recorder_reused=true
manual_report_marks_incident=true
automatic_event_storm_detection=true
automatic_ocr_storm_detection=true
stale_generation_detection=true
final_color_without_distance_detection=true
alert_popup_post_pass_telemetry=true
alert_popup_monotonic_elapsed_measurement=true
alert_popup_early_cancel_trace=true
alert_popup_early_timeout_anomaly=true
extra_polling=false
extra_screenshot=false
extra_continuous_disk_log=false
decision_engine_unchanged=true
route_engine_unchanged=true
parser_unchanged=true
live_service_unchanged=true
manifest_permissions_unchanged=true
VALIDATION
cat "$OUTPUT_DIR/test-count.txt"
cat "$OUTPUT_DIR/sha256.txt"
echo 'rota_certa_0193_build=passed'
