#!/usr/bin/env bash
set -euo pipefail

SOURCE="${1:?source checkout}"
PATCHES="${2:?current patches checkout}"
HIST19="${3:?historical Stage19 checkout}"
HIST34="${4:?historical Stage34 checkout}"
EVIDENCE="${5:?evidence directory}"

SOURCE="$(cd "$SOURCE" && pwd)"
PATCHES="$(cd "$PATCHES" && pwd)"
HIST19="$(cd "$HIST19" && pwd)"
HIST34="$(cd "$HIST34" && pwd)"
mkdir -p "$EVIDENCE"
EVIDENCE="$(cd "$EVIDENCE" && pwd)"

REPO="kelaplicativos-rgb/rota-certa-android"
CANONICAL_BASE="32da54cd112c8ecb8b43b40c5cdb87ef13c4ec42"
STAGE18_RECIPE="aa4bd10847d41a3abd5d8a9cb186d6cfa8acc88c"
STAGE32_RECIPE="a4443bb6c287fcf31a56f2a3c4ba8fc211fe1f79"
ORIGINAL_0186_BLOB="d84be03702ff5c75e753b448cde4ccd4b66a3222"

printf 'canonical_base=%s\nstage18_recipe=%s\nstage32_recipe=%s\noriginal_0186_blob=%s\n' \
  "$CANONICAL_BASE" "$STAGE18_RECIPE" "$STAGE32_RECIPE" "$ORIGINAL_0186_BLOB" \
  | tee "$EVIDENCE/bootstrap-pins.txt"

# -----------------------------------------------------------------------------
# Reproduce the historical 0.1.186 original blob explicitly.
# The optimized historical wrapper references this Git object by SHA. It may no
# longer be reachable from a shallow checkout, but GitHub still preserves the blob.
# Fetch it through the Git database API, verify Git object identity byte-for-byte,
# and substitute only the wrapper's retrieval mechanism. Functional source output
# remains the exact historical script content.
# -----------------------------------------------------------------------------
ORIGINAL_0186="$HIST19/.bootstrap-original-0186.sh"
gh api "repos/$REPO/git/blobs/$ORIGINAL_0186_BLOB" --jq '.content' \
  | tr -d '\n' | base64 --decode > "$ORIGINAL_0186"
test "$(git hash-object "$ORIGINAL_0186")" = "$ORIGINAL_0186_BLOB"
python3 - "$HIST19/scripts/build_rota_certa_0186.sh" "$ORIGINAL_0186_BLOB" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
blob = sys.argv[2]
text = path.read_text(encoding='utf-8')
old = f'git -C "$PATCH_REPOSITORY" cat-file blob {blob} > "$ORIGINAL_SCRIPT"'
new = 'cp "$PATCH_REPOSITORY/.bootstrap-original-0186.sh" "$ORIGINAL_SCRIPT"'
if text.count(old) != 1:
    raise SystemExit(f'historical 0186 blob retrieval anchor expected once, got {text.count(old)}')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
PY
grep -Fq 'cp "$PATCH_REPOSITORY/.bootstrap-original-0186.sh" "$ORIGINAL_SCRIPT"' "$HIST19/scripts/build_rota_certa_0186.sh"
printf 'historical_0186_blob_recovery=PASS git_object_identity=%s\n' "$ORIGINAL_0186_BLOB" \
  | tee "$EVIDENCE/historical-0186-blob-recovery.txt"

# -----------------------------------------------------------------------------
# Stage18: same historical cumulative recipe, applied to the protected canonical
# source checkout. This reconstructs 0.1.194, then Stage9/12/14/16/18.
# -----------------------------------------------------------------------------
pushd "$SOURCE" >/dev/null
bash "$HIST19/scripts/build_rota_certa_0194.sh" "$HIST19" 2>&1 | tee "$EVIDENCE/rebuild-0.1.194.log"
python3 "$HIST19/scripts/apply_farol_latency_instrumentation_stage9.py" "$SOURCE"
python3 "$HIST19/scripts/apply_farol_latency_manual_export_stage12.py" "$SOURCE"
python3 "$HIST19/scripts/apply_visible_offer_activation_stage14.py" "$SOURCE"
python3 "$HIST19/scripts/apply_visible_card_priority_latency_stage16.py" "$SOURCE"
python3 "$HIST19/scripts/apply_app_identity_isolation_stage18.py" "$SOURCE"
grep -Fq 'versionCode = 5482' app/build.gradle.kts
grep -Fq 'versionName = "0.1.198"' app/build.gradle.kts
test -f app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt
test -f app/src/main/java/br/com/mapeiaia/rotacerta/FarolAppIdentityIsolationStage18.kt
find app/src/main/java app/src/test/java -type f -print0 | sort -z | xargs -0 sha256sum > "$EVIDENCE/stage18-content-manifest.sha256"
printf 'rebuild_stage18=PASS version=0.1.198/5482\n' | tee "$EVIDENCE/rebuild-stage18.txt"
popd >/dev/null

# -----------------------------------------------------------------------------
# Stage32: pinned historical recipe previously used to create the now-expired
# Stage34 bootstrap artifact. No temporary artifact is required anymore.
# -----------------------------------------------------------------------------
cat > "$SOURCE/app/src/main/res/xml/rota_certa_accessibility.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android" android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewTextChanged|typeWindowsChanged" android:accessibilityFeedbackType="feedbackGeneric" android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows" android:canRetrieveWindowContent="true" android:canTakeScreenshot="true" android:description="@string/accessibility_service_description" android:notificationTimeout="100" />
XML
for script in \
  apply_universal_visual_pipeline_stage19.py \
  apply_farol_forensic_causality_stage20.py \
  apply_farol_causal_corrections_stage21.py \
  apply_farol_visual_identity_stage23.py \
  apply_farol_reading_activation_stage26_materialized.py \
  apply_farol_causal_latency_stage28_v2.py \
  apply_farol_presence_authority_stage30.py; do
  python3 "$HIST34/scripts/$script" "$SOURCE"
done
python3 "$HIST34/scripts/apply_farol_semantic_ocr_blackbox_print_stage32.py" "$SOURCE"
grep -Fq 'versionCode = 5491' "$SOURCE/app/build.gradle.kts"
grep -Fq 'FAROL_SEMANTIC_CARD_GENERATION_STAGE32' "$SOURCE/app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
find "$SOURCE/app/src/main/java" "$SOURCE/app/src/test/java" -type f -print0 | sort -z | xargs -0 sha256sum > "$EVIDENCE/stage32-content-manifest.sha256"
sha256sum \
  "$HIST19/scripts/build_rota_certa_0194.sh" \
  "$HIST19/scripts/apply_farol_latency_instrumentation_stage9.py" \
  "$HIST19/scripts/apply_farol_latency_manual_export_stage12.py" \
  "$HIST19/scripts/apply_visible_offer_activation_stage14.py" \
  "$HIST19/scripts/apply_visible_card_priority_latency_stage16.py" \
  "$HIST19/scripts/apply_app_identity_isolation_stage18.py" \
  "$HIST34/scripts/apply_universal_visual_pipeline_stage19.py" \
  "$HIST34/scripts/apply_farol_forensic_causality_stage20.py" \
  "$HIST34/scripts/apply_farol_causal_corrections_stage21.py" \
  "$HIST34/scripts/apply_farol_visual_identity_stage23.py" \
  "$HIST34/scripts/apply_farol_reading_activation_stage26_materialized.py" \
  "$HIST34/scripts/apply_farol_causal_latency_stage28_v2.py" \
  "$HIST34/scripts/apply_farol_presence_authority_stage30.py" \
  "$HIST34/scripts/apply_farol_semantic_ocr_blackbox_print_stage32.py" \
  > "$EVIDENCE/bootstrap-script-sha256.txt"
printf 'rebuild_stage32=PASS versionCode=5491 pinned_recipes=true expired_artifact_dependency=false\n' \
  | tee "$EVIDENCE/rebuild-stage32.txt"

# -----------------------------------------------------------------------------
# Materialize the currently reviewed chain Stage34 -> Stage45.
# -----------------------------------------------------------------------------
python3 "$PATCHES/scripts/apply_stage34_semantic.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage34_precollect.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage34_binding.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage34_test_semantic_contract.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage34_test_candidate_contract.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage34_test_frame_contract.py" "$SOURCE"
cp "$PATCHES/stage34/FarolStage34Test.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage34Test.kt"
python3 "$PATCHES/scripts/apply_stage34_test_root_fix.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage36_prepare.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage36_farol_core.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage36_report_compile_fix.py" "$SOURCE"
cp "$PATCHES/stage36/FarolStage36Test.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage36Test.kt"
cp "$PATCHES/stage36/FarolStage36FreshnessTest.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage36FreshnessTest.kt"
cp "$PATCHES/stage36/FarolStage36RuntimeTest.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage36RuntimeTest.kt"
python3 "$PATCHES/scripts/apply_stage36_test_runtime_fix.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage38_parser.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage38_evaluation.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage38_acquisition.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage38_ocr_request.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage38_ocr_result.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage38_downstream.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage38_report.py" "$SOURCE"
cp "$PATCHES/stage38/FarolStage38Test.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage38Test.kt"

python3 "$PATCHES/scripts/apply_stage40_presence.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage40_visual.py" "$SOURCE"
cp "$PATCHES/stage40/FarolStage40AuthorityRedContractTest.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage40AuthorityRedContractTest.kt"
python3 "$PATCHES/scripts/apply_stage40_version.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage40_precollect_bounded.py" "$SOURCE"
cp "$PATCHES/stage40/FarolStage40PreCollectBootstrapTest.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage40PreCollectBootstrapTest.kt"
python3 "$PATCHES/scripts/apply_stage40_precollect_physical_version.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage41_subsecond_final_paint.py" "$SOURCE"
cp "$PATCHES/stage41/FarolStage41SubsecondFinalPaintTest.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage41SubsecondFinalPaintTest.kt"
python3 "$PATCHES/scripts/apply_stage41_version.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage42_manual_universal_reading.py" "$SOURCE"
cp "$PATCHES/stage42/FarolStage42ManualUniversalReadingTest.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage42ManualUniversalReadingTest.kt"
python3 "$PATCHES/scripts/apply_stage42_version.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage43_manual_toggle_runtime_sync.py" "$SOURCE"
cp "$PATCHES/stage43/FarolStage43ManualToggleRuntimeSyncTest.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage43ManualToggleRuntimeSyncTest.kt"
python3 "$PATCHES/scripts/apply_stage43_version.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage44_semantic_final_lease.py" "$SOURCE"
cp "$PATCHES/stage44/FarolStage44SemanticFinalLeaseTest.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage44SemanticFinalLeaseTest.kt"
python3 "$PATCHES/scripts/apply_stage44_version.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage45_ocr_multiline_address.py" "$SOURCE"
cp "$PATCHES/stage45/FarolStage45OcrMultilineAddressTest.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage45OcrMultilineAddressTest.kt"
python3 "$PATCHES/scripts/apply_stage45_version.py" "$SOURCE"
grep -Fq 'versionCode = 5502' "$SOURCE/app/build.gradle.kts"
grep -Fq 'versionName = "0.1.218"' "$SOURCE/app/build.gradle.kts"

# -----------------------------------------------------------------------------
# Stage46 R1 -> R6. Protect semantic/freshness predecessors byte-for-byte.
# -----------------------------------------------------------------------------
P="$SOURCE/app/src/main/java/br/com/mapeiaia/rotacerta"
for file in FarolCausalCorrectionStage21.kt FarolFinalPaintFreshnessStage41.kt FarolManualOffVisualCommitStage43.kt FarolSemanticFinalLeaseStage44.kt FarolOcrMultilineAddressStage45.kt; do
  sha256sum "$P/$file" >> "$EVIDENCE/protected-before.sha256"
done

python3 "$PATCHES/scripts/apply_stage46_visual_epoch_no_result.py" "$SOURCE" | tee "$EVIDENCE/materialize-r1.txt"
cp "$PATCHES/stage46/FarolStage46VisualEpochNoResultTest.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage46VisualEpochNoResultTest.kt"
python3 "$PATCHES/scripts/apply_stage46_version.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage46_target_surface_r2.py" "$SOURCE" | tee "$EVIDENCE/materialize-r2.txt"
python3 "$PATCHES/scripts/apply_stage46_target_surface_r2_order_fix.py" "$SOURCE"
cp "$PATCHES/stage46/FarolStage46TargetSurfaceR2Test.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage46TargetSurfaceR2Test.kt"
python3 "$PATCHES/scripts/apply_stage46_r2_version.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage46_r2_test_compat.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage46_acquisition_surface_r3.py" "$SOURCE" | tee "$EVIDENCE/materialize-r3.txt"
python3 "$PATCHES/scripts/apply_stage46_r3_semantic_promotion_fix.py" "$SOURCE"
cp "$PATCHES/stage46/FarolStage46AcquisitionSurfaceR3Test.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage46AcquisitionSurfaceR3Test.kt"
python3 "$PATCHES/scripts/apply_stage46_r3_version.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage46_r3_test_compat.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage46_stable_final_latch_r4.py" "$SOURCE" | tee "$EVIDENCE/materialize-r4.txt"
cp "$PATCHES/stage46/FarolStage46StableFinalLatchR4Test.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage46StableFinalLatchR4Test.kt"
python3 "$PATCHES/scripts/apply_stage46_r4_version.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage46_r4_test_compat.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage46_atomic_transition_r5.py" "$SOURCE" | tee "$EVIDENCE/materialize-r5.txt"
cp "$PATCHES/stage46/FarolStage46AtomicTransitionR5Test.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage46AtomicTransitionR5Test.kt"
python3 "$PATCHES/scripts/apply_stage46_r5_version.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage46_r5_test_compat.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage46_single_destination_fast_path_r6.py" "$SOURCE" | tee "$EVIDENCE/materialize-r6.txt"
cp "$PATCHES/stage46/FarolStage46SingleDestinationFastPathR6Test.kt" "$SOURCE/app/src/test/java/br/com/mapeiaia/rotacerta/FarolStage46SingleDestinationFastPathR6Test.kt"
python3 "$PATCHES/scripts/apply_stage46_r6_version.py" "$SOURCE"
python3 "$PATCHES/scripts/apply_stage46_r6_test_compat.py" "$SOURCE"

for file in FarolCausalCorrectionStage21.kt FarolFinalPaintFreshnessStage41.kt FarolManualOffVisualCommitStage43.kt FarolSemanticFinalLeaseStage44.kt FarolOcrMultilineAddressStage45.kt; do
  sha256sum "$P/$file" >> "$EVIDENCE/protected-after.sha256"
done
sed 's#  .*#  FILE#' "$EVIDENCE/protected-before.sha256" > /tmp/r6-protected-before
sed 's#  .*#  FILE#' "$EVIDENCE/protected-after.sha256" > /tmp/r6-protected-after
cmp /tmp/r6-protected-before /tmp/r6-protected-after
grep -Fq 'versionCode = 5508' "$SOURCE/app/build.gradle.kts"
grep -Fq 'versionName = "0.1.224"' "$SOURCE/app/build.gradle.kts"

# Exact Stage46 test inventory.
test "$(grep -c '@Test' "$PATCHES/stage46/FarolStage46VisualEpochNoResultTest.kt")" -eq 28
test "$(grep -c '@Test' "$PATCHES/stage46/FarolStage46TargetSurfaceR2Test.kt")" -eq 24
test "$(grep -c '@Test' "$PATCHES/stage46/FarolStage46AcquisitionSurfaceR3Test.kt")" -eq 26
test "$(grep -c '@Test' "$PATCHES/stage46/FarolStage46StableFinalLatchR4Test.kt")" -eq 25
test "$(grep -c '@Test' "$PATCHES/stage46/FarolStage46AtomicTransitionR5Test.kt")" -eq 24
test "$(grep -c '@Test' "$PATCHES/stage46/FarolStage46SingleDestinationFastPathR6Test.kt")" -eq 33

# Static causal audit before any Kotlin execution.
python3 - "$SOURCE" <<'PY' | tee "$EVIDENCE/static-r6.txt"
from pathlib import Path
import sys
root=Path(sys.argv[1])
p=root/'app/src/main/java/br/com/mapeiaia/rotacerta'
s=(p/'LiveRideAccessibilityService.kt').read_text()
stage21=(p/'FarolCausalCorrectionStage21.kt').read_text()
r4=(p/'FarolStableFinalLatchStage46R4.kt').read_text()
r5=(p/'FarolAtomicTransitionStage46R5.kt').read_text()
r6=(p/'FarolSingleDestinationFastPathStage46R6.kt').read_text()
markers=(
 'FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6',
 'TWO_ADDRESSES_NOT_MANDATORY_WHEN_SINGLE_DESTINATION_HIGH_CONFIDENCE_STAGE46_R6',
 'LAST_GEOMETRIC_VISIBLE_ADDRESS_IS_DESTINATION_STAGE46_R6',
 'SPLIT_ADDRESS_BLOCKS_CAN_FORM_CURRENT_VISUAL_DESTINATION_STAGE46_R6',
 'SINGLE_DESTINATION_STILL_USES_STAGE21_ADDRESS_VALIDATION_STAGE46_R6',
 'SINGLE_PICKUP_OR_ORIGIN_CUE_CANNOT_AUTHORIZE_ROUTE_STAGE46_R6',
 'AMBIGUOUS_SINGLE_ADDRESS_FALLS_BACK_TO_LEGACY_ACQUISITION_STAGE46_R6',
 'PACKAGE_IDENTITY_NEVER_AUTHORIZES_SINGLE_DESTINATION_STAGE46_R6',
 'EVENT_DRIVEN_SINGLE_DESTINATION_NO_POLLING_STAGE46_R6')
for m in markers: assert m in r6,m
assert s.count('FarolSingleDestinationFastPathStage46R6.evaluate(') >= 2
assert 'FarolSingleDestinationFastPathStage46R6.validateEvaluation(evaluationStage19)' in s
assert 'S46_R6_SINGLE_DESTINATION_FAST_PATH' in s and 'S46_R6_LAST_VISUAL_DESTINATION' in s
assert 'FarolCausalCorrectionStage21.evaluate(listOf(blockStage46))' in s
assert 'FarolCausalCorrectionStage21::validateEvaluation' in s
assert 'less_than_two_addresses' in stage21
assert 'FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6' not in stage21
assert 'FINAL_COLOR_STAYS_LIT_UNTIL_PROVEN_CHANGE_STAGE46_R4' in r4
assert 'FAROL_ATOMIC_TRANSITION_STAGE46_R5' in r5
assert 'S46_R5_ATOMIC_CLEAR_REARM_REQUESTED' in s
assert 'S46_R4_FINAL_LATCH_PRESERVED_FOREIGN' in s
assert 'S46_R3_TARGET_PROMOTED_AFTER_STAGE21' in s
assert 'FarolVisualIdentityStage23.hasTwoAddressLeads' in s
assert 'hasOneAddressLead' not in s and 'hasSingleAddressLead' not in s
for forbidden in ('Thread.sleep','delay(','Timer(','scheduleAtFixedRate','scheduleWithFixedDelay'):
    assert forbidden not in r6,forbidden
print('stage46_r6_static_causal_audit=PASS legacy_first=true single_high_confidence=true split_geometry=true stage21_protected=true r4_latch=true r5_atomic=true no_polling=true bootstrap_reproducible=true')
PY

python3 - "$SOURCE" <<'PY' | tee "$EVIDENCE/test-inventory.txt"
from pathlib import Path
import sys
root=Path(sys.argv[1])
n=sum(p.read_text(encoding='utf-8').count('@Test') for p in (root/'app/src/test/java').rglob('*.kt'))
print('total_at_test='+str(n))
assert n==1208,n
PY

# -----------------------------------------------------------------------------
# Execute Stage46 regressions, critical inherited regressions and complete suite.
# -----------------------------------------------------------------------------
pushd "$SOURCE" >/dev/null
./gradlew --no-daemon testDebugUnitTest \
  --tests br.com.mapeiaia.rotacerta.FarolStage46VisualEpochNoResultTest \
  --tests br.com.mapeiaia.rotacerta.FarolStage46TargetSurfaceR2Test \
  --tests br.com.mapeiaia.rotacerta.FarolStage46AcquisitionSurfaceR3Test \
  --tests br.com.mapeiaia.rotacerta.FarolStage46StableFinalLatchR4Test \
  --tests br.com.mapeiaia.rotacerta.FarolStage46AtomicTransitionR5Test \
  --tests br.com.mapeiaia.rotacerta.FarolStage46SingleDestinationFastPathR6Test \
  | tee "$EVIDENCE/stage46-r6-tests.log"
python3 - <<'PY' | tee "$EVIDENCE/stage46-r6-count.txt"
from pathlib import Path
import xml.etree.ElementTree as E
t=f=e=s=0
for p in Path('app/build/test-results/testDebugUnitTest').glob('TEST-br.com.mapeiaia.rotacerta.FarolStage46*.xml'):
    r=E.parse(p).getroot(); t+=int(r.attrib.get('tests',0)); f+=int(r.attrib.get('failures',0)); e+=int(r.attrib.get('errors',0)); s+=int(r.attrib.get('skipped',0))
print(t,f,e,s); assert (t,f,e,s)==(160,0,0,0),(t,f,e,s)
PY

./gradlew --no-daemon testDebugUnitTest \
  --tests br.com.mapeiaia.rotacerta.FarolStage45OcrMultilineAddressTest \
  --tests br.com.mapeiaia.rotacerta.FarolStage44SemanticFinalLeaseTest \
  --tests br.com.mapeiaia.rotacerta.FarolStage43ManualToggleRuntimeSyncTest \
  --tests br.com.mapeiaia.rotacerta.FarolStage42ManualUniversalReadingTest \
  --tests br.com.mapeiaia.rotacerta.FarolStage41SubsecondFinalPaintTest \
  --tests br.com.mapeiaia.rotacerta.FarolStage40PreCollectBootstrapTest \
  --tests br.com.mapeiaia.rotacerta.FarolStage40AuthorityRedContractTest \
  --tests br.com.mapeiaia.rotacerta.FarolStage38Test \
  --tests br.com.mapeiaia.rotacerta.FarolStage36FreshnessTest \
  | tee "$EVIDENCE/inherited-regressions.log"

./gradlew --no-daemon --rerun-tasks testDebugUnitTest | tee "$EVIDENCE/full-tests.log"
python3 - <<'PY' | tee "$EVIDENCE/full-count.txt"
from pathlib import Path
import xml.etree.ElementTree as E
t=f=e=s=0
for p in Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    r=E.parse(p).getroot(); t+=int(r.attrib.get('tests',0)); f+=int(r.attrib.get('failures',0)); e+=int(r.attrib.get('errors',0)); s+=int(r.attrib.get('skipped',0))
print(t,f,e,s); assert (t,f,e,s)==(1208,0,0,0),(t,f,e,s)
PY

./gradlew --no-daemon lintDebug | tee "$EVIDENCE/lint.log"
./gradlew --no-daemon clean assembleDebug | tee "$EVIDENCE/assemble.log"
popd >/dev/null

# -----------------------------------------------------------------------------
# Validate exactly the APK produced by this tested materialization.
# -----------------------------------------------------------------------------
APK_SOURCE="$SOURCE/app/build/outputs/apk/debug/app-debug.apk"
APK_OUT="$EVIDENCE/Rota-Certa-Stage46-Single-Destination-Fast-Path-R6-0.1.224.apk"
cp "$APK_SOURCE" "$APK_OUT"
AAPT="$(find "$ANDROID_HOME/build-tools" -name aapt -type f | sort -V | tail -1)"
SIGN="$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -1)"
"$AAPT" dump badging "$APK_OUT" | tee "$EVIDENCE/badging.txt"
grep -q "package: name='br.com.mapeiaia.rotacerta' versionCode='5508' versionName='0.1.224'" "$EVIDENCE/badging.txt"
"$SIGN" verify --verbose --print-certs "$APK_OUT" | tee "$EVIDENCE/signature.txt"
grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' "$EVIDENCE/signature.txt"
unzip -t "$APK_OUT" | tee "$EVIDENCE/zip.txt"
mkdir -p "$EVIDENCE/dex"
unzip -Z1 "$APK_OUT" | grep -E '^classes([0-9]+)?\.dex$' | tee "$EVIDENCE/dex-inventory.txt"
while IFS= read -r dex; do unzip -p "$APK_OUT" "$dex" > "$EVIDENCE/dex/$dex"; done < "$EVIDENCE/dex-inventory.txt"
cat "$EVIDENCE"/dex/classes*.dex > "$EVIDENCE/all-classes.dex"
for marker in \
  FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6 \
  TWO_ADDRESSES_NOT_MANDATORY_WHEN_SINGLE_DESTINATION_HIGH_CONFIDENCE_STAGE46_R6 \
  LAST_GEOMETRIC_VISIBLE_ADDRESS_IS_DESTINATION_STAGE46_R6 \
  SINGLE_PICKUP_OR_ORIGIN_CUE_CANNOT_AUTHORIZE_ROUTE_STAGE46_R6 \
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
sha256sum "$APK_OUT" | tee "$EVIDENCE/apk-sha256.txt"
sha512sum "$APK_OUT" | tee "$EVIDENCE/apk-sha512.txt"
stat -c '%s' "$APK_OUT" | tee "$EVIDENCE/apk-size.txt"
printf 'stage46_r6_apk_validation=PASS\n' | tee "$EVIDENCE/apk-validation.txt"
printf 'stage46_r6_end_to_end=PASS version=0.1.224/5508 tests=1208 stage46=160\n' | tee "$EVIDENCE/final-status.txt"
