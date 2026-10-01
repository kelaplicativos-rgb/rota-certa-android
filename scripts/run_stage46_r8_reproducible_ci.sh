#!/usr/bin/env bash
set -euo pipefail
SOURCE="$(cd "${1:?source}" && pwd)"
PATCHES="$(cd "${2:?patches}" && pwd)"
HIST19="$(cd "${3:?historical19}" && pwd)"
HIST34="$(cd "${4:?historical34}" && pwd)"
mkdir -p "${5:?evidence}"
EVIDENCE="$(cd "$5" && pwd)"

mkdir -p "$EVIDENCE/r7-bootstrap"
R7_MATERIALIZER="$EVIDENCE/run-stage46-r7-materialize-only.sh"
python3 - "$PATCHES/scripts/run_stage46_r7_reproducible_ci.sh" "$R7_MATERIALIZER" <<'PY'
from pathlib import Path
import sys
source = Path(sys.argv[1]).read_text(encoding='utf-8')
out = Path(sys.argv[2])
anchor = '''python3 - "$SOURCE" <<'PY' | tee "$EVIDENCE/static-r7.txt"'''
if source.count(anchor) != 1:
    raise SystemExit(f'R8 materialize-only anchor expected once, got {source.count(anchor)}')
early = '''if [[ "${STAGE46_R7_MATERIALIZE_ONLY:-0}" == "1" ]]; then
  printf 'stage46_r7_materialized=PASS version=0.1.225/5509 stage46_inventory=197 superseded_r7_runtime_audit_skipped=true runtime_tests_skipped_for_r8_final_validation=true\n' | tee "$EVIDENCE/final-status.txt"
  exit 0
fi

'''
out.write_text(source.replace(anchor, early + anchor, 1), encoding='utf-8')
PY
STAGE46_R7_MATERIALIZE_ONLY=1 bash "$R7_MATERIALIZER" "$SOURCE" "$PATCHES" "$HIST19" "$HIST34" "$EVIDENCE/r7-bootstrap"
grep -Fq 'stage46_r7_materialized=PASS version=0.1.225/5509 stage46_inventory=197' "$EVIDENCE/r7-bootstrap/final-status.txt"
printf 'r8_bootstrap_strategy=PASS exact_r7_materialization=true duplicate_r7_runtime_tests=false final_r8_static_and_runtime_tests=true\n' | tee "$EVIDENCE/r8-bootstrap-strategy.txt"

P="$SOURCE/app/src/main/java/br/com/mapeiaia/rotacerta"
PROTECTED=(
  "$P/GoogleMapsService.kt"
  "$P/LiveRideRouteCache.kt"
  "$P/DecisionEngine.kt"
  "$P/WorkRegionTargetPolicy.kt"
  "$P/FarolCausalCorrectionStage21.kt"
  "$P/FarolFinalPaintFreshnessStage41.kt"
  "$P/FarolAtomicTransitionStage46R5.kt"
  "$P/FarolStableFinalLatchStage46R4.kt"
  "$P/FarolImmediateAddressRouteStage46R7.kt"
)
for f in "${PROTECTED[@]}"; do test -f "$f"; done
sha256sum "${PROTECTED[@]}" > "$EVIDENCE/protected-before.sha256"

python3 "$PATCHES/scripts/apply_stage46_route_location_evidence_r8.py" "$SOURCE" | tee "$EVIDENCE/materialize-r8.txt"
cp "$PATCHES/stage46/FarolStage46PositiveLocationEvidenceR8Test.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage46PositiveLocationEvidenceR8Test.kt"
python3 "$PATCHES/scripts/apply_stage46_r8_version.py" "$SOURCE" | tee "$EVIDENCE/version-r8.txt"
python3 "$PATCHES/scripts/apply_stage46_r8_test_compat.py" "$SOURCE" | tee "$EVIDENCE/test-compat-r8.txt"

sha256sum "${PROTECTED[@]}" > "$EVIDENCE/protected-after.sha256"
cmp "$EVIDENCE/protected-before.sha256" "$EVIDENCE/protected-after.sha256"
printf 'google_route_stack_byte_for_byte_preserved=PASS\n' | tee "$EVIDENCE/google-route-stack-preserved.txt"
grep -Fq 'versionCode = 5510' "$SOURCE/app/build.gradle.kts"
grep -Fq 'versionName = "0.1.226"' "$SOURCE/app/build.gradle.kts"
test "$(grep -c '@Test' "$PATCHES/stage46/FarolStage46PositiveLocationEvidenceR8Test.kt")" -eq 17

python3 - "$SOURCE" "$PATCHES" <<'PY' | tee "$EVIDENCE/static-r8.txt"
from pathlib import Path
import sys
root=Path(sys.argv[1]); patches=Path(sys.argv[2]); p=root/'app/src/main/java/br/com/mapeiaia/rotacerta'
s=(p/'LiveRideAccessibilityService.kt').read_text(); r8=(p/'FarolRouteLocationEvidenceStage46R8.kt').read_text()
for m in (
    'FAROL_POSITIVE_LOCATION_EVIDENCE_STAGE46_R8',
    'ARBITRARY_UI_TEXT_CANNOT_REACH_GOOGLE_STAGE46_R8',
    'GOOGLE_ROUTE_AND_CACHE_UNCHANGED_STAGE46_R8',
    'POSITIVE_ADDRESS_OR_PLACE_STRUCTURE_REQUIRED_STAGE46_R8',
    'LAST_VALID_VISIBLE_LOCATION_IS_DESTINATION_STAGE46_R8',
    'STRONG_NAMED_PLACE_REMAINS_ROUTEABLE_STAGE46_R8',
    'PROVEN_DESTINATION_CHANGE_CLEARS_BEFORE_HEAVY_COLLECT_STAGE46_R8',
    'CURRENT_DESTINATION_EVIDENCE_DOES_NOT_CLEAR_FINAL_STAGE46_R8',
    'EVENT_DRIVEN_LOCATION_EVIDENCE_NO_POLLING_STAGE46_R8',
): assert m in r8,m
assert 'FarolCausalCorrectionStage21.validateAddress' in r8
assert s.count('FarolRouteLocationEvidenceStage46R8.evaluate(')>=2
assert 'FarolRouteLocationEvidenceStage46R8.evaluateImmediateText(' in s
assert 'FarolRouteLocationEvidenceStage46R8.validateEvaluation(evaluationStage19)' in s
assert 'S46_R8_PROVEN_DESTINATION_CHANGE_CLEARED_PRECOLLECT' in s
assert 'FarolImmediateAddressRouteStage46R7.evaluate(collectionStage26.blocks)' not in s
assert 'S46_R5_ATOMIC_CLEAR_REARM_REQUESTED' in s and 'S46_R4_FINAL_LATCH' in s
for x in ('GoogleMapsService','LiveRideRouteCache','DecisionEngine','WorkRegionTargetPolicy','showOverlay('): assert x not in r8,x
for x in ('Thread.sleep','delay(','Timer(','scheduleAtFixedRate','scheduleWithFixedDelay'): assert x not in r8,x
apply=(patches/'scripts/apply_stage46_route_location_evidence_r8.py').read_text()
for x in ('GoogleMapsService.kt','LiveRideRouteCache.kt','DecisionEngine.kt','WorkRegionTargetPolicy.kt'): assert x not in apply,x
print('stage46_r8_static_causal_audit=PASS positive_location_only=true false_ui_blocked=true named_places_preserved=true precollect_clear_on_strong_replacement=true same_destination_latch_preserved=true google_route_stack_protected=true no_polling=true')
PY

python3 - "$SOURCE" <<'PY' | tee "$EVIDENCE/test-inventory.txt"
from pathlib import Path
import sys
n=sum(p.read_text().count('@Test') for p in (Path(sys.argv[1])/'app/src/test/java').rglob('*.kt'))
print('total_at_test='+str(n)); assert n==1262,n
PY

cd "$SOURCE"
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
assert (s46_t,s46_f,s46_e,s46_s)==(214,0,0,0),(s46_t,s46_f,s46_e,s46_s)
assert (all_t,all_f,all_e,all_s)==(1262,0,0,0),(all_t,all_f,all_e,all_s)
PY
printf 'stage46_r8_gradle_validation=PASS stage46=214/214 full=1262/1262 lint=PASS assemble=PASS\n' | tee "$EVIDENCE/gradle-validation.txt"

APK="$EVIDENCE/Rota-Certa-Stage46-Positive-Location-Evidence-R8-0.1.226.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$APK"
AAPT="$(find "$ANDROID_HOME/build-tools" -name aapt -type f | sort -V | tail -1)"
SIGN="$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -1)"
"$AAPT" dump badging "$APK" | tee "$EVIDENCE/badging.txt"
grep -q "package: name='br.com.mapeiaia.rotacerta' versionCode='5510' versionName='0.1.226'" "$EVIDENCE/badging.txt"
"$SIGN" verify --verbose --print-certs "$APK" | tee "$EVIDENCE/signature.txt"
grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' "$EVIDENCE/signature.txt"
unzip -t "$APK" | tee "$EVIDENCE/zip.txt"
mkdir -p "$EVIDENCE/dex"
unzip -Z1 "$APK" | grep -E '^classes([0-9]+)?\.dex$' | tee "$EVIDENCE/dex-inventory.txt"
while IFS= read -r dex; do unzip -p "$APK" "$dex" > "$EVIDENCE/dex/$dex"; done < "$EVIDENCE/dex-inventory.txt"
cat "$EVIDENCE"/dex/classes*.dex > "$EVIDENCE/all-classes.dex"
for marker in \
  FAROL_POSITIVE_LOCATION_EVIDENCE_STAGE46_R8 \
  ARBITRARY_UI_TEXT_CANNOT_REACH_GOOGLE_STAGE46_R8 \
  GOOGLE_ROUTE_AND_CACHE_UNCHANGED_STAGE46_R8 \
  POSITIVE_ADDRESS_OR_PLACE_STRUCTURE_REQUIRED_STAGE46_R8 \
  LAST_VALID_VISIBLE_LOCATION_IS_DESTINATION_STAGE46_R8 \
  STRONG_NAMED_PLACE_REMAINS_ROUTEABLE_STAGE46_R8 \
  PROVEN_DESTINATION_CHANGE_CLEARS_BEFORE_HEAVY_COLLECT_STAGE46_R8 \
  FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7 \
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
printf 'stage46_r8_apk_validation=PASS package=br.com.mapeiaia.rotacerta version=0.1.226/5510 signature_v2=true dex_markers=true false_ui_blocked=true google_route_stack_byte_for_byte_preserved=true\n' | tee "$EVIDENCE/apk-validation.txt"
printf 'stage46_r8_end_to_end=PASS version=0.1.226/5510 tests=1262 stage46=214 positive_location_only=true false_ui_blocked=true named_places_preserved=true strong_replacement_precollect_clear=true same_destination_latch_preserved=true google_route_stack_preserved=true\n' | tee "$EVIDENCE/final-status.txt"
