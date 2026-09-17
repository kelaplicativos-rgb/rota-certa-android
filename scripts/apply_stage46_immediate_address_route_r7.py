#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'
PATCH_ROOT = Path(__file__).resolve().parents[1]
HELPER = PATCH_ROOT / 'stage46/FarolImmediateAddressRouteStage46R7.kt'

if not HELPER.exists():
    raise SystemExit('Stage46 R7 helper missing')
if not SERVICE.exists():
    raise SystemExit('Stage46 R7 requires materialized R6 service')
if not (PKG / 'FarolSingleDestinationFastPathStage46R6.kt').exists():
    raise SystemExit('Stage46 R7 requires materialized R6 helper')
shutil.copyfile(HELPER, PKG / 'FarolImmediateAddressRouteStage46R7.kt')


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)


s = SERVICE.read_text(encoding='utf-8')
if 'FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7 service integration' in s:
    raise SystemExit('Stage46 R7 appears already applied')
if 'FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6 service integration' not in s:
    raise SystemExit('Stage46 R7 requires R6 service integration')

# Accessibility: preserve the full visual collection first so multiple addresses can still be ordered.
# If that collection does not yield a candidate, use the cheap event-local text ONLY when it contains
# exactly one valid address. This avoids the screenshot/OCR round trip without guessing multi-address order.
old_accessibility = 'FarolSingleDestinationFastPathStage46R6.evaluate(collectionStage26.blocks)'
new_accessibility = '''FarolImmediateAddressRouteStage46R7.evaluate(collectionStage26.blocks)
            ?: FarolImmediateAddressRouteStage46R7.evaluateImmediateText(
                cheapSignalStage26.sourceText,
                eventWindowIdStage20,
                FarolUniversalVisualPipelineStage19.Source.Accessibility,
            )'''
s = once(s, old_accessibility, new_accessibility, 'R7 accessibility immediate-address fallback')

# OCR already has a concrete current screenshot surface. One valid address is sufficient there too;
# multiple valid addresses remain ordered by the R7 helper and the last becomes destination.
remaining_r6_evaluators = s.count('FarolSingleDestinationFastPathStage46R6.evaluate(')
if remaining_r6_evaluators < 1:
    raise SystemExit('Stage46 R7 expected at least one remaining R6 OCR evaluator')
s = s.replace(
    'FarolSingleDestinationFastPathStage46R6.evaluate(',
    'FarolImmediateAddressRouteStage46R7.evaluate(',
)

s = once(
    s,
    'FarolSingleDestinationFastPathStage46R6.validateEvaluation(evaluationStage19)',
    'FarolImmediateAddressRouteStage46R7.validateEvaluation(evaluationStage19)',
    'R7 semantic validator',
)
s = once(
    s,
    'FarolSingleDestinationFastPathStage46R6.isSingleFastPathEvaluation(evaluationStage19)',
    'FarolImmediateAddressRouteStage46R7.isSingleImmediateEvaluation(evaluationStage19)',
    'R7 single immediate classifier',
)
s = s.replace('singleDestinationFastPathStage46R6', 'singleImmediateAddressStage46R7')
s = once(
    s,
    'evaluationStage19.blockId.startsWith(FarolSingleDestinationFastPathStage46R6.AGGREGATE_BLOCK_PREFIX)',
    'FarolImmediateAddressRouteStage46R7.isAggregateLastAddressEvaluation(evaluationStage19)',
    'R7 aggregate classifier',
)

s = s.replace('"S46_R6_SINGLE_DESTINATION_FAST_PATH"', '"S46_R7_IMMEDIATE_SINGLE_ADDRESS"')
s = s.replace('"S46_R6_LAST_VISUAL_DESTINATION"', '"S46_R7_LAST_VISUAL_DESTINATION"')
s = s.replace(
    'noPairWait=true',
    'noPairWait=true; addressDetectedImmediate=true; noDestinationCueWait=true; noOcrWaitWhenEventTextSingle=true',
)
s = s.replace('geometricOrder=true', 'lastAddressAuthority=true')
s = s.replace(
    'Destino único atual confirmado com alta confiança; calculando rota real.',
    'Primeiro endereço válido atual detectado; calculando rota real imediatamente.',
)
s = s.replace(
    'Endereços atuais confirmados; último endereço visual é o destino final.',
    'Múltiplos endereços atuais detectados; o último endereço visual é o destino da rota.',
)

# Keep the R6 historical marker and append an R7 integration marker. Historical helpers/tests remain in
# the materialized source for regression, while runtime authority moves to R7.
marker = '    // FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6 service integration\n'
s = once(
    s,
    marker,
    marker + '    // FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7 service integration\n',
    'R7 service integration marker',
)

# Invariants: Stage21 pair authority stays source-unchanged; R1 pair recovery stays intact; R4/R5
# freshness behavior remains present; no new timer/polling mechanism is introduced here.
required = (
    'FarolCausalCorrectionStage21.evaluate(listOf(blockStage46))',
    'FarolCausalCorrectionStage21::validateEvaluation',
    'S46_R5_ATOMIC_CLEAR_REARM_REQUESTED',
    'S46_R4_FINAL_LATCH',
    'FarolImmediateAddressRouteStage46R7.validateEvaluation(evaluationStage19)',
    'S46_R7_IMMEDIATE_SINGLE_ADDRESS',
    'S46_R7_LAST_VISUAL_DESTINATION',
)
for value in required:
    if value not in s:
        raise SystemExit(f'Stage46 R7 required integration missing: {value}')
if s.count('FarolImmediateAddressRouteStage46R7.evaluate(') < 2:
    raise SystemExit('Stage46 R7 expected Accessibility + OCR evaluators')
if 'FarolSingleDestinationFastPathStage46R6.evaluate(' in s:
    raise SystemExit('Stage46 R7 left an active R6 evaluator call')

SERVICE.write_text(s, encoding='utf-8')
print(
    'stage46_r7_apply=PASS '
    'first_valid_address_immediate=true last_visible_address=true '
    'accessibility_event_single_skips_ocr_wait=true legacy_stage21_first=true '
    'r4_r5_freshness_preserved=true no_polling=true'
)