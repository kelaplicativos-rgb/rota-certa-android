#!/usr/bin/env bash
set -euo pipefail
SOURCE="$(cd "${1:?source}" && pwd)"
PATCHES="$(cd "${2:?patches}" && pwd)"
HIST19="$(cd "${3:?historical19}" && pwd)"
HIST34="$(cd "${4:?historical34}" && pwd)"
mkdir -p "${5:?evidence}"
EVIDENCE="$(cd "$5" && pwd)"

mkdir -p "$EVIDENCE/r8-baseline"
bash "$PATCHES/scripts/run_stage46_r8_reproducible_ci.sh" \
  "$SOURCE" "$PATCHES" "$HIST19" "$HIST34" "$EVIDENCE/r8-baseline"
grep -Fq 'stage46_r8_end_to_end=PASS version=0.1.226/5510 tests=1262' "$EVIDENCE/r8-baseline/final-status.txt"
printf 'error1_r8_authoritative_baseline=PASS head_runtime=0.1.226/5510 tests=1262\n' | tee "$EVIDENCE/r8-baseline-proof.txt"

P="$SOURCE/app/src/main/java/br/com/mapeiaia/rotacerta"
PROTECTED=(
  "$P/GoogleMapsService.kt"
  "$P/LiveRideRouteCache.kt"
  "$P/DecisionEngine.kt"
  "$P/WorkRegionTargetPolicy.kt"
  "$P/FarolCausalCorrectionStage21.kt"
  "$P/FarolFinalPaintFreshnessStage41.kt"
  "$P/FarolSemanticFinalLeaseStage44.kt"
  "$P/FarolOcrMultilineAddressStage45.kt"
  "$P/FarolVisualEpochNoResultStage46.kt"
  "$P/FarolTargetSurfaceStage46R2.kt"
  "$P/FarolAcquisitionSurfaceStage46R3.kt"
  "$P/FarolStableFinalLatchStage46R4.kt"
  "$P/FarolAtomicTransitionStage46R5.kt"
  "$P/FarolSingleDestinationFastPathStage46R6.kt"
  "$P/FarolImmediateAddressRouteStage46R7.kt"
  "$P/FarolRouteLocationEvidenceStage46R8.kt"
)
for f in "${PROTECTED[@]}"; do test -f "$f"; done
sha256sum "${PROTECTED[@]}" > "$EVIDENCE/protected-before.sha256"

python3 "$PATCHES/scripts/apply_error1_card_visual_episode_reentry.py" "$SOURCE" | tee "$EVIDENCE/materialize-error1.txt"
cp "$PATCHES/error1/FarolCardVisualEpisodeReentryError1Test.kt" \
  "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolCardVisualEpisodeReentryError1Test.kt"
python3 "$PATCHES/scripts/apply_error1_version.py" "$SOURCE" | tee "$EVIDENCE/version-error1.txt"

sha256sum "${PROTECTED[@]}" > "$EVIDENCE/protected-after.sha256"
cmp "$EVIDENCE/protected-before.sha256" "$EVIDENCE/protected-after.sha256"
printf 'error1_protected_stack_byte_for_byte=PASS stage21_stage41_stage44_stage45_stage46_r1_r8_google_route_cache_decision_unchanged=true\n' | tee "$EVIDENCE/protected-stack.txt"

grep -Fq 'versionCode = 5521' "$SOURCE/app/build.gradle.kts"
grep -Fq 'versionName = "0.1.228"' "$SOURCE/app/build.gradle.kts"
test "$(grep -c '@Test' "$PATCHES/error1/FarolCardVisualEpisodeReentryError1Test.kt")" -eq 11

python3 - "$SOURCE" "$PATCHES" <<'PY' | tee "$EVIDENCE/static-error1.txt"
from pathlib import Path
import sys
root=Path(sys.argv[1]); patches=Path(sys.argv[2])
p=root/'app/src/main/java/br/com/mapeiaia/rotacerta'
activation=(p/'FarolReadingActivationStage26.kt').read_text()
service=(p/'LiveRideAccessibilityService.kt').read_text()
fix=(patches/'scripts/apply_error1_card_visual_episode_reentry.py').read_text()
assert activation.count('fun endVisualEpisode()') == 1
start=activation.index('fun endVisualEpisode()')
end=activation.index('fun invalidate()', start)
reset=activation[start:end]
for token in ('lastWindowSignature = null','lastRelevantValue = null','bootstrapValueByStructure.clear()'):
    assert token in reset, token
assert 'generation +=' not in reset
assert service.count('stage26PreCollectGate.endVisualEpisode()') == 1
site=service.index('private fun revokeEmptyTargetStage46(')
reset_site=service.index('stage26PreCollectGate.endVisualEpisode()', site)
assert reset_site > site
for earlier in ('stage46VisualEpoch += 1L','clearVisualLease("stage46_r2_target_empty")','universalRouteJob?.cancel()','stage46BindingSurfaceToken.clear()','releaseConfirmedTargetStage46R3("target_empty"'):
    assert service.index(earlier, site) < reset_site, earlier
assert 'S46_VISUAL_EPISODE_PRECOLLECT_RESET' in service
assert 'stage40_bootstrap_duplicate_coalesced' in activation
assert 'stage40_same_address_evidence' in activation
for forbidden in ('Thread.sleep','SystemClock.sleep','Timer(','scheduleAtFixedRate','scheduleWithFixedDelay','debounce','cooldown'):
    assert forbidden.lower() not in reset.lower(), forbidden
for protected_name in ('GoogleMapsService.kt','LiveRideRouteCache.kt','DecisionEngine.kt','WorkRegionTargetPolicy.kt','FarolCausalCorrectionStage21.kt','FarolFinalPaintFreshnessStage41.kt'):
    assert protected_name not in fix, protected_name
print('error1_static_causal_audit=PASS episode_memory_reset_only_on_proven_target_empty=true same_episode_coalescing_preserved=true generation_not_relaxed=true stale_stack_unchanged=true no_polling_debounce_cooldown=true')
PY

python3 - "$SOURCE" <<'PY' | tee "$EVIDENCE/test-inventory.txt"
from pathlib import Path
import sys
n=sum(p.read_text(encoding='utf-8').count('@Test') for p in (Path(sys.argv[1])/'app/src/test/java').rglob('*.kt'))
print('total_at_test='+str(n)); assert n==1273,n
PY

cd "$SOURCE"
./gradlew --no-daemon --rerun-tasks testDebugUnitTest \
  --tests br.com.mapeiaia.rotacerta.FarolCardVisualEpisodeReentryError1Test \
  --tests br.com.mapeiaia.rotacerta.FarolStage40PreCollectBootstrapTest \
  --tests br.com.mapeiaia.rotacerta.FarolStage46AtomicTransitionR5Test \
  | tee "$EVIDENCE/targeted-tests.log"
printf 'error1_targeted_regression=PASS new=11 stage40=22 r5_preserved=true\n' | tee "$EVIDENCE/targeted-tests-status.txt"

./gradlew --no-daemon --rerun-tasks testDebugUnitTest lintDebug assembleDebug | tee "$EVIDENCE/final-gradle-validation.log"
python3 - <<'PY' | tee "$EVIDENCE/test-counts.txt"
from pathlib import Path
import xml.etree.ElementTree as E
all_t=all_f=all_e=all_s=0
new_t=new_f=new_e=new_s=0
for p in Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    r=E.parse(p).getroot()
    vals=(int(r.attrib.get('tests',0)),int(r.attrib.get('failures',0)),int(r.attrib.get('errors',0)),int(r.attrib.get('skipped',0)))
    all_t+=vals[0]; all_f+=vals[1]; all_e+=vals[2]; all_s+=vals[3]
    if p.name == 'TEST-br.com.mapeiaia.rotacerta.FarolCardVisualEpisodeReentryError1Test.xml':
        new_t,new_f,new_e,new_s=vals
print('error1',new_t,new_f,new_e,new_s)
print('full',all_t,all_f,all_e,all_s)
assert (new_t,new_f,new_e,new_s)==(11,0,0,0),(new_t,new_f,new_e,new_s)
assert (all_t,all_f,all_e,all_s)==(1273,0,0,0),(all_t,all_f,all_e,all_s)
PY
printf 'error1_gradle_validation=PASS new=11/11 full=1273/1273 lint=PASS assemble=PASS\n' | tee "$EVIDENCE/gradle-validation.txt"

APK="$EVIDENCE/Rota-Certa-Error1-Card-Visual-Episode-Reentry-0.1.228.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$APK"
AAPT="$(find "$ANDROID_HOME/build-tools" -name aapt -type f | sort -V | tail -1)"
SIGN="$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -1)"
"$AAPT" dump badging "$APK" | tee "$EVIDENCE/badging.txt"
grep -q "package: name='br.com.mapeiaia.rotacerta' versionCode='5521' versionName='0.1.228'" "$EVIDENCE/badging.txt"
"$SIGN" verify --verbose --print-certs "$APK" | tee "$EVIDENCE/signature.txt"
grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' "$EVIDENCE/signature.txt"
unzip -t "$APK" | tee "$EVIDENCE/zip.txt"
mkdir -p "$EVIDENCE/dex"
unzip -Z1 "$APK" | grep -E '^classes([0-9]+)?\.dex$' | tee "$EVIDENCE/dex-inventory.txt"
while IFS= read -r dex; do unzip -p "$APK" "$dex" > "$EVIDENCE/dex/$dex"; done < "$EVIDENCE/dex-inventory.txt"
cat "$EVIDENCE"/dex/classes*.dex > "$EVIDENCE/all-classes.dex"
for marker in \
  S46_VISUAL_EPISODE_PRECOLLECT_RESET \
  FAROL_POSITIVE_LOCATION_EVIDENCE_STAGE46_R8 \
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
printf 'error1_apk_validation=PASS package=br.com.mapeiaia.rotacerta version=0.1.228/5521 signature_v2=true episode_reentry_marker=true protected_stack=true\n' | tee "$EVIDENCE/apk-validation.txt"
printf 'error1_end_to_end=PASS version=0.1.228/5521 tests=1273 new_regression=11 same_episode_coalescing=true reentry_after_proven_exit=true stale_protection_preserved=true protected_stack_byte_for_byte=true\n' | tee "$EVIDENCE/final-status.txt"
