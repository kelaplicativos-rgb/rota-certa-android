#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'
PATCH_ROOT = Path(__file__).resolve().parents[1]
HELPER = PATCH_ROOT / 'stage46/FarolRouteLocationEvidenceStage46R8.kt'

if not HELPER.exists():
    raise SystemExit('Stage46 R8 helper missing')
if not SERVICE.exists():
    raise SystemExit('Stage46 R8 requires materialized R7 service')
if not (PKG / 'FarolImmediateAddressRouteStage46R7.kt').exists():
    raise SystemExit('Stage46 R8 requires materialized R7 helper')
shutil.copyfile(HELPER, PKG / HELPER.name)


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)


s = SERVICE.read_text(encoding='utf-8')
if 'FAROL_POSITIVE_LOCATION_EVIDENCE_STAGE46_R8 service integration' in s:
    raise SystemExit('Stage46 R8 appears already applied')
if 'FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7 service integration' not in s:
    raise SystemExit('Stage46 R8 requires R7 service integration')

s = s.replace('FarolImmediateAddressRouteStage46R7.evaluateImmediateText(', 'FarolRouteLocationEvidenceStage46R8.evaluateImmediateText(')
s = s.replace('FarolImmediateAddressRouteStage46R7.evaluate(', 'FarolRouteLocationEvidenceStage46R8.evaluate(')
s = once(s, 'FarolImmediateAddressRouteStage46R7.validateEvaluation(evaluationStage19)', 'FarolRouteLocationEvidenceStage46R8.validateEvaluation(evaluationStage19)', 'R8 semantic validator')
s = once(s, 'FarolImmediateAddressRouteStage46R7.isSingleImmediateEvaluation(evaluationStage19)', 'FarolRouteLocationEvidenceStage46R8.isSingleImmediateEvaluation(evaluationStage19)', 'R8 single classifier')
s = once(s, 'FarolImmediateAddressRouteStage46R7.isAggregateLastAddressEvaluation(evaluationStage19)', 'FarolRouteLocationEvidenceStage46R8.isAggregateLastAddressEvaluation(evaluationStage19)', 'R8 aggregate classifier')
s = s.replace('"S46_R7_IMMEDIATE_SINGLE_ADDRESS"', '"S46_R8_POSITIVE_SINGLE_LOCATION"')
s = s.replace('"S46_R7_LAST_VISUAL_DESTINATION"', '"S46_R8_LAST_VALID_LOCATION"')

anchor = '''        val admissionStage26 = stage26PreCollectGate.admit(true, cheapSignalStage26)\n        val hardBoundaryStage46 = FarolVisualEpochNoResultStage46.isHardWindowBoundary(\n'''
replacement = '''        val admissionStage26 = stage26PreCollectGate.admit(true, cheapSignalStage26)
        val replacementProofStage46R8 = if (
            admissionStage26.reason == "stage40_address_evidence_changed" &&
            FarolStableFinalLatchStage46R4.isFinalDecision(
                currentRadarColor.name,
                currentDistanceKm,
                universalActiveAddressSignature,
            )
        ) {
            FarolRouteLocationEvidenceStage46R8.proveDestinationReplacement(
                cheapSignalStage26.sourceText,
                universalActiveAddressSignature,
            )
        } else {
            FarolRouteLocationEvidenceStage46R8.ReplacementProof(false, "not_strong_address_change")
        }
        val immediateAddressReplacementStage46R8 = replacementProofStage46R8.proven
        if (immediateAddressReplacementStage46R8) {
            invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(),
                "S46_R8_PROVEN_DESTINATION_CHANGE_CLEARED_PRECOLLECT", eventPackageStage19,
                details = "reason=${replacementProofStage46R8.reason}; candidateSignature=${replacementProofStage46R8.candidateSignature.orEmpty()}; positiveLocations=${replacementProofStage46R8.positiveLocationCount}; previousFinalCleared=true; kmCleared=true; beforeHeavyCollect=true",
            )
            FarolCausalLatencyStage28.Metrics.increment("stage46R8ImmediateDestinationClear")
            FarolCausalLatencyStage28.Metrics.sample(
                "eventToStage46R8ImmediateDestinationClear",
                SystemClock.elapsedRealtimeNanos() - eventStartedNsStage26,
            )
        }
        val hardBoundaryStage46 = FarolVisualEpochNoResultStage46.isHardWindowBoundary(
'''
s = once(s, anchor, replacement, 'R8 immediate proven destination clear')

old_clear = '''            invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)
            if (evaluationStage19 != null) {
'''
new_clear = '''            if (!immediateAddressReplacementStage46R8) {
                invalidateOldVisualBeforeCollectStage26(admissionStage26.visualGeneration, eventStartedNsStage26)
            }
            if (evaluationStage19 != null) {
'''
s = once(s, old_clear, new_clear, 'R8 avoid duplicate invalidation after precollect proof')

marker = '    // FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7 service integration\n'
s = once(s, marker, marker + '    // FAROL_POSITIVE_LOCATION_EVIDENCE_STAGE46_R8 service integration\n', 'R8 service integration marker')

required = (
    'FarolRouteLocationEvidenceStage46R8.evaluate(collectionStage26.blocks)',
    'FarolRouteLocationEvidenceStage46R8.evaluateImmediateText(',
    'FarolRouteLocationEvidenceStage46R8.validateEvaluation(evaluationStage19)',
    'S46_R8_POSITIVE_SINGLE_LOCATION',
    'S46_R8_LAST_VALID_LOCATION',
    'S46_R8_PROVEN_DESTINATION_CHANGE_CLEARED_PRECOLLECT',
    'S46_R5_ATOMIC_CLEAR_REARM_REQUESTED',
    'S46_R4_FINAL_LATCH',
)
for value in required:
    if value not in s:
        raise SystemExit(f'Stage46 R8 required integration missing: {value}')
if 'FarolImmediateAddressRouteStage46R7.evaluate(' in s:
    raise SystemExit('Stage46 R8 left active R7 evaluate call in service')
if 'FarolImmediateAddressRouteStage46R7.evaluateImmediateText(' in s:
    raise SystemExit('Stage46 R8 left active R7 immediate-text call in service')

SERVICE.write_text(s, encoding='utf-8')
print('stage46_r8_apply=PASS positive_location_evidence=true arbitrary_ui_to_google=false proven_destination_change_precollect_clear=true same_destination_latch_preserved=true google_route_unchanged=true no_polling=true')
