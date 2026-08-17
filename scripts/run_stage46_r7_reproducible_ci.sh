#!/usr/bin/env bash
set -euo pipefail
SOURCE="$(cd "${1:?source}" && pwd)"
PATCHES="$(cd "${2:?patches}" && pwd)"
HIST19="$(cd "${3:?historical19}" && pwd)"
HIST34="$(cd "${4:?historical34}" && pwd)"
mkdir -p "${5:?evidence}"
EVIDENCE="$(cd "$5" && pwd)"

# R6 v4 is independently green. R7 still reconstructs/materializes the exact R6 predecessor, but
# stops the R6 orchestrator before its superseded static runtime-integration audit and before the
# expensive Gradle/lint/APK pass. R7 then replaces that integration and receives one authoritative
# static + Gradle pass over the final materialized state.
mkdir -p "$EVIDENCE/r6-bootstrap"
R6_MATERIALIZER="$EVIDENCE/run-stage46-r6-materialize-only.sh"
python3 - "$PATCHES/scripts/run_stage46_r6_reproducible_ci.sh" "$R6_MATERIALIZER" <<'PY'
from pathlib import Path
import sys
source = Path(sys.argv[1]).read_text(encoding='utf-8')
out = Path(sys.argv[2])
anchor = '''# Static causal audit before any Kotlin execution.
'''
if source.count(anchor) != 1:
    raise SystemExit(f'R7 materialize-only anchor expected once, got {source.count(anchor)}')
early = '''if [[ "${STAGE46_R6_MATERIALIZE_ONLY:-0}" == "1" ]]; then
  printf 'stage46_r6_materialized=PASS version=0.1.224/5508 stage46_inventory=160 superseded_r6_runtime_audit_skipped=true runtime_tests_skipped_for_r7_final_validation=true\\n' | tee "$EVIDENCE/final-status.txt"
  exit 0
fi

'''
out.write_text(source.replace(anchor, early + anchor, 1), encoding='utf-8')
PY
STAGE46_R6_MATERIALIZE_ONLY=1 bash "$R6_MATERIALIZER" "$SOURCE" "$PATCHES" "$HIST19" "$HIST34" "$EVIDENCE/r6-bootstrap"
grep -Fq 'stage46_r6_materialized=PASS version=0.1.224/5508 stage46_inventory=160 superseded_r6_runtime_audit_skipped=true runtime_tests_skipped_for_r7_final_validation=true' "$EVIDENCE/r6-bootstrap/final-status.txt"
printf 'r7_bootstrap_strategy=PASS exact_r6_materialization=true superseded_r6_runtime_audit_skipped=true duplicate_r6_runtime_tests=false final_r7_static_and_runtime_tests=true\n' | tee "$EVIDENCE/r7-bootstrap-strategy.txt"

P="$SOURCE/app/src/main/java/br/com/mapeiaia/rotacerta"
sha256sum "$P/FarolCausalCorrectionStage21.kt" "$P/FarolFinalPaintFreshnessStage41.kt" "$P/FarolAtomicTransitionStage46R5.kt" "$P/FarolSingleDestinationFastPathStage46R6.kt" > "$EVIDENCE/protected-before.sha256"
python3 "$PATCHES/scripts/apply_stage46_immediate_address_route_r7.py" "$SOURCE" | tee "$EVIDENCE/materialize-r7.txt"
cp "$PATCHES/stage46/FarolStage46ImmediateAddressRouteR7Test.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage46ImmediateAddressRouteR7Test.kt"
python3 "$PATCHES/scripts/apply_stage46_r7_version.py" "$SOURCE" | tee "$EVIDENCE/version-r7.txt"
python3 "$PATCHES/scripts/apply_stage46_r7_test_compat.py" "$SOURCE" | tee "$EVIDENCE/test-compat-r7.txt"
sha256sum "$P/FarolCausalCorrectionStage21.kt" "$P/FarolFinalPaintFreshnessStage41.kt" "$P/FarolAtomicTransitionStage46R5.kt" "$P/FarolSingleDestinationFastPathStage46R6.kt" > "$EVIDENCE/protected-after.sha256"
cmp "$EVIDENCE/protected-before.sha256" "$EVIDENCE/protected-after.sha256"
grep -Fq 'versionCode = 5509' "$SOURCE/app/build.gradle.kts"
grep -Fq 'versionName = "0.1.225"' "$SOURCE/app/build.gradle.kts"
test "$(grep -c '@Test' "$PATCHES/stage46/FarolStage46ImmediateAddressRouteR7Test.kt")" -eq 25

python3 - "$SOURCE" <<'PY' | tee "$EVIDENCE/static-r7.txt"
from pathlib import Path
import sys
root=Path(sys.argv[1]); p=root/'app/src/main/java/br/com/mapeiaia/rotacerta'
s=(p/'LiveRideAccessibilityService.kt').read_text(); r7=(p/'FarolImmediateAddressRouteStage46R7.kt').read_text(); stage21=(p/'FarolCausalCorrectionStage21.kt').read_text()
for m in ('FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7','FIRST_VALID_ADDRESS_STARTS_ROUTE_IMMEDIATELY_STAGE46_R7','LAST_VISIBLE_ADDRESS_REPLACES_DESTINATION_STAGE46_R7','SINGLE_ADDRESS_EVENT_TEXT_AVOIDS_OCR_WAIT_STAGE46_R7','EVENT_DRIVEN_IMMEDIATE_ADDRESS_NO_POLLING_STAGE46_R7'): assert m in r7,m
assert s.count('FarolImmediateAddressRouteStage46R7.evaluate(')>=2
assert 'FarolImmediateAddressRouteStage46R7.evaluateImmediateText(' in s
assert 'cheapSignalStage26.sourceText' in s
assert 'FarolImmediateAddressRouteStage46R7.validateEvaluation(evaluationStage19)' in s
assert 'FarolSingleDestinationFastPathStage46R6.evaluate(' not in s
assert 'S46_R7_IMMEDIATE_SINGLE_ADDRESS' in s and 'S46_R7_LAST_VISUAL_DESTINATION' in s
assert 'S46_R5_ATOMIC_CLEAR_REARM_REQUESTED' in s and 'S46_R4_FINAL_LATCH' in s
assert 'less_than_two_addresses' in stage21
for x in ('Thread.sleep','delay(','Timer(','scheduleAtFixedRate','scheduleWithFixedDelay'): assert x not in r7,x
print('stage46_r7_static_causal_audit=PASS first_address_immediate=true last_address_authority=true event_single_no_ocr_wait=true freshness_preserved=true no_polling=true')
PY

python3 - "$SOURCE" <<'PY' | tee "$EVIDENCE/test-inventory.txt"
from pathlib import Path
import sys
n=sum(p.read_text().count('@Test') for p in (Path(sys.argv[1])/'app/src/test/java').rglob('*.kt'))
print('total_at_test='+str(n)); assert n==1233,n
PY

cd "$SOURCE"
# One authoritative pass. --rerun-tasks guarantees execution even though historical reconstruction may
# have touched Gradle outputs. The same materialized source produces tests, lint result and physical APK.
./gradlew --no-daemon --rerun-tasks testDebugUnitTest lintDebug assembleDebug | tee "$EVIDENCE/final-gradle-validation.log"
python3 - <<'PY' | tee "$EVIDENCE/test-counts.txt"
from pathlib import Path
import xml.etree.ElementTree as E
all_t=all_f=all_e=all_s=0
s46_t=s46_f=s46_e=s46_s=0
for p in Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    r=E.parse(p).getroot()
    vals=(int(r.attrib.get('tests',0)),int(r.attrib.get('failures',0)),int(r.attrib.get('errors',0)),int(r.attrib.get('skipped',0)))
    all_t+=vals[0]; all_f+=vals[1]; all_e+=vals[2]; all_s+=vals[3]
    if p.name.startswith('TEST-br.com.mapeiaia.rotacerta.FarolStage46'):
        s46_t+=vals[0]; s46_f+=vals[1]; s46_e+=vals[2]; s46_s+=vals[3]
print('stage46',s46_t,s46_f,s46_e,s46_s)
print('full',all_t,all_f,all_e,all_s)
assert (s46_t,s46_f,s46_e,s46_s)==(185,0,0,0),(s46_t,s46_f,s46_e,s46_s)
assert (all_t,all_f,all_e,all_s)==(1233,0,0,0),(all_t,all_f,all_e,all_s)
PY
printf 'stage46_r7_gradle_validation=PASS stage46=185/185 full=1233/1233 lint=PASS assemble=PASS single_gradle_pass=true\n' | tee "$EVIDENCE/gradle-validation.txt"

APK="$EVIDENCE/Rota-Certa-Stage46-Immediate-Address-Route-R7-0.1.225.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$APK"
AAPT="$(find "$ANDROID_HOME/build-tools" -name aapt -type f | sort -V | tail -1)"
SIGN="$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -1)"
"$AAPT" dump badging "$APK" | tee "$EVIDENCE/badging.txt"
grep -q "package: name='br.com.mapeiaia.rotacerta' versionCode='5509' versionName='0.1.225'" "$EVIDENCE/badging.txt"
"$SIGN" verify --verbose --print-certs "$APK" | tee "$EVIDENCE/signature.txt"
grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' "$EVIDENCE/signature.txt"
unzip -t "$APK" | tee "$EVIDENCE/zip.txt"
mkdir -p "$EVIDENCE/dex"
unzip -Z1 "$APK" | grep -E '^classes([0-9]+)?\.dex$' | tee "$EVIDENCE/dex-inventory.txt"
while IFS= read -r dex; do unzip -p "$APK" "$dex" > "$EVIDENCE/dex/$dex"; done < "$EVIDENCE/dex-inventory.txt"
cat "$EVIDENCE"/dex/classes*.dex > "$EVIDENCE/all-classes.dex"
for marker in \
  FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7 \
  FIRST_VALID_ADDRESS_STARTS_ROUTE_IMMEDIATELY_STAGE46_R7 \
  LAST_VISIBLE_ADDRESS_REPLACES_DESTINATION_STAGE46_R7 \
  SINGLE_ADDRESS_EVENT_TEXT_AVOIDS_OCR_WAIT_STAGE46_R7 \
  FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6 \
  FAROL_ATOMIC_TRANSITION_STAGE46_R5 \
  FAROL_STABLE_FINAL_LATCH_STAGE46_R4 \
  FAROL_ACQUISITION_SURFACE_STAGE46_R3 \
  FAROL_TARGET_SURFACE_AUTHORITY_STAGE46_R2 \
  FAROL_VISUAL_SURFACE_EPOCH_STAGE46 \
  FAROL_OCR_MULTILINE_ADDRESS_STAGE45 \
  FAROL_SEMANTIC_FINAL_LEASE_STAGE44 \
  MANUAL_OFF_PHYSICAL_VIEW_COMMIT_STAGE43 \
  FAROL_SUBSECOND_SAME_FRAME_FINAL_PAINT_STAGE41; do
  grep -a -q "$marker" "$EVIDENCE/all-classes.dex"
done
sha256sum "$APK" | tee "$EVIDENCE/apk-sha256.txt"
sha512sum "$APK" | tee "$EVIDENCE/apk-sha512.txt"
stat -c '%s' "$APK" | tee "$EVIDENCE/apk-size.txt"
printf 'stage46_r7_apk_validation=PASS package=br.com.mapeiaia.rotacerta version=0.1.225/5509 signature_v2=true dex_markers=true\n' | tee "$EVIDENCE/apk-validation.txt"
printf 'stage46_r7_end_to_end=PASS version=0.1.225/5509 tests=1233 stage46=185 first_address_immediate=true last_address_authority=true bootstrap_exact=true single_gradle_pass=true\n' | tee "$EVIDENCE/final-status.txt"
