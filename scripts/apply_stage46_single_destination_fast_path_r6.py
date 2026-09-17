#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'
PATCH_ROOT = Path(__file__).resolve().parents[1]
HELPER = PATCH_ROOT / 'stage46/FarolSingleDestinationFastPathStage46R6.kt'

if not HELPER.exists():
    raise SystemExit('Stage46 R6 helper missing')
if not SERVICE.exists():
    raise SystemExit('Stage46 R6 requires materialized service')
shutil.copyfile(HELPER, PKG / 'FarolSingleDestinationFastPathStage46R6.kt')


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)


def replace_known_evaluators(text: str) -> tuple[str, int, list[str]]:
    replacements = (
        (
            'FarolCausalCorrectionStage21.evaluate(collectionStage26.blocks)',
            'FarolCausalCorrectionStage21.evaluate(collectionStage26.blocks)\n'
            '            ?: FarolSingleDestinationFastPathStage46R6.evaluate(collectionStage26.blocks)',
            'accessibility_collection_stage26',
        ),
        (
            'FarolUniversalVisualPipelineStage19.evaluate(collectionStage26.blocks)',
            'FarolUniversalVisualPipelineStage19.evaluate(collectionStage26.blocks)\n'
            '            ?: FarolSingleDestinationFastPathStage46R6.evaluate(collectionStage26.blocks)',
            'accessibility_collection_stage26_legacy',
        ),
        (
            'FarolUniversalVisualPipelineStage19.evaluate(blocksStage19)',
            'FarolUniversalVisualPipelineStage19.evaluate(blocksStage19)\n'
            '                                        ?: FarolSingleDestinationFastPathStage46R6.evaluate(blocksStage19)',
            'blocks_stage19',
        ),
    )
    applied = 0
    labels: list[str] = []
    for old, new, label in replacements:
        count = text.count(old)
        if count:
            text = text.replace(old, new)
            applied += count
            labels.append(f'{label}:{count}')
    return text, applied, labels


s = SERVICE.read_text(encoding='utf-8')
if 'FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6' in s:
    raise SystemExit('Stage46 R6 appears already applied')

s, evaluator_count, evaluator_labels = replace_known_evaluators(s)
if evaluator_count < 2:
    raise SystemExit(
        'Stage46 R6 expected at least Accessibility + OCR evaluator call-sites; '
        f'found {evaluator_count}: {evaluator_labels}'
    )

# R1 bounded no-result pair recovery deliberately remains pair-only. It is a secondary recovery after
# the normal OCR evaluator (now R6-capable) and changing it would weaken the proven R1 contract.
if 'FarolCausalCorrectionStage21.evaluate(listOf(blockStage46))' not in s:
    raise SystemExit('Stage46 R6 expected preserved R1 bounded-pair recovery')

old_semantic = '        val semanticStage21 = FarolCausalCorrectionStage21.validateEvaluation(evaluationStage19)\n'
new_semantic = '''        val semanticStage21 = FarolSingleDestinationFastPathStage46R6.validateEvaluation(evaluationStage19)
        val singleDestinationFastPathStage46R6 = FarolSingleDestinationFastPathStage46R6.isSingleFastPathEvaluation(evaluationStage19)
        if (singleDestinationFastPathStage46R6) {
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R6_SINGLE_DESTINATION_FAST_PATH", observePackageForWindowIdStage46R3(evaluationStage19.windowId), cycleId = cycleIdStage20,
                details = "window=${evaluationStage19.windowId}; destination=${evaluationStage19.destination}; signature=${evaluationStage19.addressSignature}; semanticAccepted=${semanticStage21.accepted}; reason=${semanticStage21.reason}; source=$sourceStage19; noPairWait=true",
            )
        } else if (evaluationStage19.blockId.startsWith(FarolSingleDestinationFastPathStage46R6.AGGREGATE_BLOCK_PREFIX)) {
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_R6_LAST_VISUAL_DESTINATION", observePackageForWindowIdStage46R3(evaluationStage19.windowId), cycleId = cycleIdStage20,
                details = "window=${evaluationStage19.windowId}; addressCount=${evaluationStage19.addresses.size}; destination=${evaluationStage19.destination}; signature=${evaluationStage19.addressSignature}; semanticAccepted=${semanticStage21.accepted}; source=$sourceStage19; geometricOrder=true",
            )
        }
'''
s = once(s, old_semantic, new_semantic, 'R6 semantic validator')

# Keep diagnostics truthful for both pair and single-destination candidates.
old_reason = '        rememberBubbleReason("stage19_visual_destination", "Dois endereços atuais confirmados; último endereço é o destino final.")\n'
if old_reason in s:
    new_reason = '''        rememberBubbleReason(
            "stage19_visual_destination",
            if (singleDestinationFastPathStage46R6) "Destino único atual confirmado com alta confiança; calculando rota real."
            else "Endereços atuais confirmados; último endereço visual é o destino final.",
        )
'''
    s = once(s, old_reason, new_reason, 'R6 truthful bubble reason')

# Any remaining hard-clear explanation must not imply that two addresses are still mandatory.
s = s.replace(
    'Snapshot visual atual sem dois endereços semanticamente completos Stage21.',
    'Snapshot visual atual sem destino semanticamente confiável para rota.',
)
s = s.replace(
    'Snapshot visual atual confirmado sem dois endereços coerentes.',
    'Snapshot visual atual confirmado sem destino semanticamente confiável.',
)

# The helper markers themselves live in a separate class; leave a service integration marker too.
field_anchor = '    private var stage46TargetWindowId: Int = 0\n'
if field_anchor not in s:
    raise SystemExit('Stage46 R6 service marker anchor missing')
s = s.replace(
    field_anchor,
    field_anchor + '    // FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6 service integration\n',
    1,
)

SERVICE.write_text(s, encoding='utf-8')
print(
    'stage46_r6_apply=PASS '
    f'evaluators={evaluator_count} labels={"|".join(evaluator_labels)} '
    'stage21_source_unchanged=true r1_pair_recovery_preserved=true semantic_single_gate=true'
)
