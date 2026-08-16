#!/usr/bin/env bash
set -euo pipefail
SOURCE="$(cd "${1:?source}" && pwd)"
PATCHES="$(cd "${2:?patches}" && pwd)"
HIST19="$(cd "${3:?historical19}" && pwd)"
HIST34="$(cd "${4:?historical34}" && pwd)"
mkdir -p "${5:?evidence}"
EVIDENCE="$(cd "$5" && pwd)"

# R6 v4 is already independently green. For R7 we still reconstruct/materialize R6 from the exact
# reproducible recipe, but deliberately stop its orchestrator before the expensive R6 Gradle/lint/APK
# pass. The final R7 state below runs Stage46 + full-suite + lint + assemble once. This removes duplicate
# validation work without weakening the R7 proof and leaves the canonical R6 workflow unchanged.
mkdir -p "$EVIDENCE/r6-bootstrap"
R6_MATERIALIZER="$EVIDENCE/run-stage46-r6-materialize-only.sh"
python3 - "$PATCHES/scripts/run_stage46_r6_reproducible_ci.sh" "$R6_MATERIALIZER" <<'PY'
from pathlib import Path
import sys
source = Path(sys.argv[1]).read_text(encoding='utf-8')
out = Path(sys.argv[2])
anchor = '''# -----------------------------------------------------------------------------
# Execute Stage46 regressions, critical inherited regressions and complete suite.
# -----------------------------------------------------------------------------
'''
if source.count(anchor) != 1:
    raise SystemExit(f'R7 materialize-only anchor expected once, got {source.count(anchor)}')
early = '''if [[ "${STAGE46_R6_MATERIALIZE_ONLY:-0}" == "1" ]]; then
  printf 'stage46_r6_materialized=PASS version=0.1.224/5508 tests_inventory=1208 stage46_inventory=160 runtime_tests_skipped_for_r7_final_validation=true\\n' | tee "$EVIDENCE/final-status.txt"
  exit 0
fi

'''
out.write_text(source.replace(anchor, early + anchor, 1), encoding='utf-8')
PY
STAGE46_R6_MATERIALIZE_ONLY=1 bash "$R6_MATERIALIZER" "$SOURCE" "$PATCHES" "$HIST19" "$HIST34" "$EVIDENCE/r6-bootstrap"
grep -Fq 'stage46_r6_materialized=PASS version=0.1.224/5508 tests_inventory=1208 stage46_inventory=160 runtime_tests_skipped_for_r7_final_validation=true' "$EVIDENCE/r6-bootstrap/final-status.txt"
printf 'r7_bootstrap_strategy=PASS exact_r6_materialization=true duplicate_r6_runtime_tests=false final_r7_runtime_tests=true\n' | tee "$EVIDENCE/r7-bootstrap-strategy.txt"

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
print(n); assert n==1233,n
PY

cd "$SOURCE"
./gradlew --no-daemon testDebugUnitTest --tests 'br.com.mapeiaia.rotacerta.FarolStage46*' | tee "$EVIDENCE/stage46-r7-tests.log"
python3 - <<'PY' | tee "$EVIDENCE/stage46-r7-count.txt"
from pathlib import Path
import xml.etree.ElementTree as E
t=f=e=s=0
for p in Path('app/build/test-results/testDebugUnitTest').glob('TEST-br.com.mapeiaia.rotacerta.FarolStage46*.xml'):
 r=E.parse(p).getroot(); t+=int(r.attrib.get('tests',0)); f+=int(r.attrib.get('failures',0)); e+=int(r.attrib.get('errors',0)); s+=int(r.attrib.get('skipped',0))
print(t,f,e,s); assert (t,f,e,s)==(185,0,0,0),(t,f,e,s)
PY
./gradlew --no-daemon --rerun-tasks testDebugUnitTest | tee "$EVIDENCE/full-tests.log"
python3 - <<'PY' | tee "$EVIDENCE/full-count.txt"
from pathlib import Path
import xml.etree.ElementTree as E
t=f=e=s=0
for p in Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
 r=E.parse(p).getroot(); t+=int(r.attrib.get('tests',0)); f+=int(r.attrib.get('failures',0)); e+=int(r.attrib.get('errors',0)); s+=int(r.attrib.get('skipped',0))
print(t,f,e,s); assert (t,f,e,s)==(1233,0,0,0),(t,f,e,s)
PY
./gradlew --no-daemon lintDebug | tee "$EVIDENCE/lint.log"
./gradlew --no-daemon clean assembleDebug | tee "$EVIDENCE/assemble.log"

APK="$EVIDENCE/Rota-Certa-Stage46-Immediate-Address-Route-R7-0.1.225.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$APK"
AAPT="$(find "$ANDROID_HOME/build-tools" -name aapt -type f | sort -V | tail -1)"
SIGN="$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -1)"
"$AAPT" dump badging "$APK" | tee "$EVIDENCE/badging.txt"
grep -q "versionCode='5509' versionName='0.1.225'" "$EVIDENCE/badging.txt"
"$SIGN" verify --verbose --print-certs "$APK" | tee "$EVIDENCE/signature.txt"
grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' "$EVIDENCE/signature.txt"
sha256sum "$APK" | tee "$EVIDENCE/apk-sha256.txt"
sha512sum "$APK" | tee "$EVIDENCE/apk-sha512.txt"
stat -c '%s' "$APK" | tee "$EVIDENCE/apk-size.txt"
printf 'stage46_r7_end_to_end=PASS version=0.1.225/5509 tests=1233 stage46=185 first_address_immediate=true last_address_authority=true bootstrap_exact=true duplicate_r6_runtime_tests=false\n' | tee "$EVIDENCE/final-status.txt"