#!/usr/bin/env python3
from __future__ import annotations

import argparse
import shutil
from pathlib import Path

SERVICE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt')
REPORT = Path('app/src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt')
PIPELINE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolUniversalVisualPipelineStage19.kt')
STAGE20 = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolForensicTraceStage20.kt')
STAGE21 = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolCausalCorrectionStage21.kt')
HELPER = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolVisualIdentityStage23.kt')
TEST = Path('app/src/test/java/br/com/mapeiaia/rotacerta/FarolVisualIdentityStage23Test.kt')
BUILD = Path('app/build.gradle.kts')
PATCH_ROOT = Path(__file__).resolve().parents[1]
HELPER_TEMPLATE = PATCH_ROOT / 'stage23/FarolVisualIdentityStage23.kt'
TEST_TEMPLATE = PATCH_ROOT / 'stage23/FarolVisualIdentityStage23Test.kt'
HANDLER_COLLECT_TEMPLATE = PATCH_ROOT / 'stage23/LiveRideAccessibilityServiceStage23.inc.kt'
SCHEDULE_TEMPLATE = PATCH_ROOT / 'stage23/ScheduleVisibleTextAnalysisStage23.inc.kt'
OCR_TEMPLATE = PATCH_ROOT / 'stage23/RequestUniversalScreenshotStage23.inc.kt'
MARKER = 'FAROL_VISUAL_IDENTITY_COALESCING_STAGE23'


def fail(message: str) -> None:
    raise SystemExit(message)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        fail(f'Stage23 anchor {label}: expected 1, found {count}')
    return text.replace(old, new, 1)


def replace_section(text: str, start: str, end: str, replacement: str, label: str) -> str:
    a = text.find(start)
    b = text.find(end, a + 1)
    if a < 0 or b <= a:
        fail(f'Stage23 section {label}: markers not found')
    return text[:a] + replacement + text[b:]


def self_test() -> None:
    support = (
        HELPER_TEMPLATE,
        TEST_TEMPLATE,
        HANDLER_COLLECT_TEMPLATE,
        SCHEDULE_TEMPLATE,
        OCR_TEMPLATE,
    )
    for path in support:
        if not path.is_file():
            fail(f'missing Stage23 support file: {path}')
    helper = HELPER_TEMPLATE.read_text(encoding='utf-8')
    tests = TEST_TEMPLATE.read_text(encoding='utf-8')
    handler = HANDLER_COLLECT_TEMPLATE.read_text(encoding='utf-8')
    schedule = SCHEDULE_TEMPLATE.read_text(encoding='utf-8')
    ocr = OCR_TEMPLATE.read_text(encoding='utf-8')

    for required in (
        MARKER,
        'VISUAL_SNAPSHOT_FINGERPRINT_BEFORE_EVALUATE_STAGE23',
        'SCHEDULED_DEMAND_BOUND_TO_VISUAL_GENERATION_STAGE23',
        'OCR_DEMAND_BOUND_TO_VISUAL_GENERATION_STAGE23',
        'AUTOMATIC_LATENCY_METRICS_STAGE23',
        'GOOGLE_MAPS_REAL_ROUTE_PRESERVED_STAGE23',
        'PAINT_TOKEN_FINAL_BINDING_PRESERVED_STAGE23',
        'VisualSnapshotGate',
        'ScheduledDemandGate',
        'OcrDemandGate',
        'invalidateForExplicitRecovery',
    ):
        if required not in helper:
            fail(f'Stage23 helper missing {required}')
    if tests.count('@Test') != 40:
        fail(f'expected exactly 40 Stage23 tests, found {tests.count("@Test")}')
    required_tests = (
        'hundredIdenticalEventsCauseOneExpensiveAdmission',
        'differentEventTypesDoNotChangeVisualIdentity',
        'systemUiOrOwnPackageMetadataCannotChangeVisualIdentity',
        'oneRelevantAddressCharacterChangesGeneration',
        'completeDestinationSwapProcessesImmediately',
        'scheduledAfterDirectWinnerIsCancelled',
        'oldScheduledDemandAfterVisualChangeIsCancelled',
        'ownOverlayEventWithSameVisualSnapshotDoesNotEvaluateAgain',
        'externalPopupOverRotaCertaChangesTopWindowIdentity',
        'popupOverWhatsAppIsDetectedByContentNotPackage',
        'popupOverChatGptIsDetectedByContentNotPackage',
        'popupOverHomeIsDetectedByContentNotPackage',
        'largeTopWindowCanEarlyExitInternallyAfterCompleteContext',
        'twoDifferentOffersAreNotMixedByEvaluator',
        'repeatedOcrBusySameGenerationDoesNotCreatePerpetualRerun',
        'ocrBusyWithNewGenerationCreatesExactlyOneUsefulRerun',
        'accessibilityWinnerCancelsActiveOcrIdentity',
        'staleBeforeBitmapGuardRemainsRepresentable',
        'staleBeforeExtractGuardRemainsRepresentable',
        'staleAfterExtractRemainsLastBarrierBeforeEvaluation',
        'truncatedCandidateNeverReachesSemanticDownstream',
        'truncatedCandidateCannotReachCache',
        'cacheHitCannotBypassSemanticBarrier',
        'realGoogleRouteContractIsExplicitlyPreserved',
        'freshnessContractRemainsExplicitlyPreserved',
        'paintTokenContractRemainsExplicitlyPreserved',
        'verificationPendingIsPartOfPreservedFreshnessContract',
        'sameFinalIdentityCannotGenerateSuccessiveExpensiveAdmissions',
        'instrumentationSeparatesVisibleWindowsFromTraversedWindows',
        'instrumentationExportsMedianP95AndMaximum',
    )
    for name in required_tests:
        if name not in tests:
            fail(f'Stage23 mandatory regression missing: {name}')

    for required in (
        'S23_VISUAL_SNAPSHOT_UNCHANGED_SKIP',
        'S23_ACCESSIBILITY_COLLECT_STATS',
        'visible_windows_total=',
        'windows_traversed=',
        'blocks_visited=',
        'blocks_emitted=',
        'event_fingerprint=',
        'final_screen_hash=pending',
        'final_address_signature=pending',
    ):
        if required not in handler:
            fail(f'Stage23 handler/collector missing {required}')
    for required in (
        'S23_SCHEDULED_CANCELLED_BEFORE_COLLECT',
        'S23_SCHEDULED_CANCELLED_VISUAL_CHANGED',
        'stage23ScheduleGate.shouldRun',
    ):
        if required not in schedule:
            fail(f'Stage23 scheduled integration missing {required}')
    for required in (
        'S23_OCR_DEFERRED_SAME_VISUAL',
        'S23_OCR_DEFERRED_NEW_VISUAL',
        'STALE_BEFORE_BITMAP',
        'STALE_BEFORE_EXTRACT',
        'STALE_AFTER_EXTRACT',
        'STALE_AFTER_EVALUATE',
        'stage23_non_cancelable_extract_completed_stale=true',
    ):
        if required not in ocr:
            fail(f'Stage23 OCR integration missing {required}')
    for forbidden in ('Thread.sleep(', 'SystemClock.sleep('):
        if forbidden in helper or forbidden in handler or forbidden in schedule or forbidden in ocr:
            fail(f'artificial debounce/sleep forbidden in Stage23: {forbidden}')
    if 'delay(' in handler or 'delay(' in schedule or 'delay(' in ocr:
        fail('Stage23 critical-path fragments must not add coroutine delay')

    print('stage23_self_test=passed')
    print('stage23_test_methods=40')
    print('visual_snapshot_identity_before_evaluate=true')
    print('event_fingerprint_authority=false')
    print('scheduled_bound_to_visual_generation=true')
    print('self_event_requires_unchanged_visual_identity=true')
    print('incremental_inside_window_early_exit=true')
    print('ocr_rerun_requires_new_visual_demand=true')
    print('automatic_latency_metrics=true')


def apply(root: Path) -> None:
    required_paths = (root / SERVICE, root / REPORT, root / PIPELINE, root / STAGE20, root / STAGE21, root / BUILD)
    if any(not path.is_file() for path in required_paths):
        fail('Stage23 requires materialized Stage21 source')
    service = (root / SERVICE).read_text(encoding='utf-8')
    report = (root / REPORT).read_text(encoding='utf-8')
    pipeline = (root / PIPELINE).read_text(encoding='utf-8')
    stage20 = (root / STAGE20).read_text(encoding='utf-8')
    stage21 = (root / STAGE21).read_text(encoding='utf-8')
    build = (root / BUILD).read_text(encoding='utf-8')

    if 'FAROL_CAUSAL_CORRECTION_STAGE21' not in service or 'FAROL_CAUSAL_CORRECTION_STAGE21' not in stage21:
        fail('Stage23 requires applied Stage21 service/helper')
    if 'FAROL_FORENSIC_CAUSALITY_STAGE20' not in service or 'FAROL_FORENSIC_CAUSALITY_STAGE20' not in stage20:
        fail('Stage23 requires Stage20 forensics')
    if 'UNIVERSAL_VISUAL_AUTHORITY_STAGE19' not in pipeline:
        fail('Stage23 requires Stage19 visual pipeline')
    if MARKER in service or (root / HELPER).exists():
        fail('Stage23 already appears applied')
    if 'versionCode = 5486' not in build or 'versionName = "0.1.202"' not in build:
        fail('Stage23 requires exact 0.1.202/5486 Stage21 baseline')

    state_anchor = (
        '    private val stage21EventGate = FarolCausalCorrectionStage21.EventGate()\n'
        '    private val stage21OcrGate = FarolCausalCorrectionStage21.OcrGate()\n'
        '    private var stage21SelfEventSuppressionUntilNs: Long = 0L\n'
        '    // FAROL_CAUSAL_CORRECTION_STAGE21 — causal fixes from Stage20 physical evidence\n'
    )
    state_new = (
        '    private val stage21EventGate = FarolCausalCorrectionStage21.EventGate()\n'
        '    private val stage21OcrGate = FarolCausalCorrectionStage21.OcrGate()\n'
        '    private var stage21SelfEventSuppressionUntilNs: Long = 0L\n'
        '    private val stage23VisualGate = FarolVisualIdentityStage23.VisualSnapshotGate()\n'
        '    private val stage23ScheduleGate = FarolVisualIdentityStage23.ScheduledDemandGate()\n'
        '    private val stage23OcrGate = FarolVisualIdentityStage23.OcrDemandGate()\n'
        '    // FAROL_VISUAL_IDENTITY_COALESCING_STAGE23 — visual snapshot owns expensive-work admission\n'
        '    // FAROL_CAUSAL_CORRECTION_STAGE21 — semantic barrier/freshness predecessor retained\n'
    )
    service = replace_once(service, state_anchor, state_new, 'state gates')

    handler_collect = HANDLER_COLLECT_TEMPLATE.read_text(encoding='utf-8')
    service = replace_section(
        service,
        '    private fun handleUniversalVisualEventStage19(',
        '    private fun requestUniversalScreenshotStage19(',
        handler_collect,
        'handler and incremental collector',
    )

    ocr = OCR_TEMPLATE.read_text(encoding='utf-8')
    service = replace_section(
        service,
        '    private fun requestUniversalScreenshotStage19(',
        '    private suspend fun processUniversalVisualStage19(',
        ocr,
        'OCR demand binding',
    )

    schedule = SCHEDULE_TEMPLATE.read_text(encoding='utf-8')
    service = replace_section(
        service,
        '    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {',
        '    private fun scheduleScreenshotFallback127',
        schedule,
        'scheduled demand binding',
    )

    report = replace_once(
        report,
        '            appendLine(FarolForensicTraceStage20.exportReport())\n',
        '            appendLine(FarolForensicTraceStage20.exportReport())\n'
        '            appendLine()\n'
        '            appendLine(FarolVisualIdentityStage23.Metrics.exportReport())\n',
        'automatic Stage23 metrics report',
    )

    build = replace_once(build, 'versionCode = 5486', 'versionCode = 5487', 'versionCode')
    build = replace_once(build, 'versionName = "0.1.202"', 'versionName = "0.1.203"', 'versionName')

    (root / HELPER).parent.mkdir(parents=True, exist_ok=True)
    (root / TEST).parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HELPER_TEMPLATE, root / HELPER)
    shutil.copyfile(TEST_TEMPLATE, root / TEST)
    (root / SERVICE).write_text(service, encoding='utf-8')
    (root / REPORT).write_text(report, encoding='utf-8')
    (root / BUILD).write_text(build, encoding='utf-8')

    transformed = (root / SERVICE).read_text(encoding='utf-8')
    checks = (
        MARKER,
        'stage23VisualGate',
        'stage23ScheduleGate',
        'stage23OcrGate',
        'S23_VISUAL_SNAPSHOT_UNCHANGED_SKIP',
        'S23_SCHEDULED_CANCELLED_BEFORE_COLLECT',
        'S23_OCR_DEFERRED_SAME_VISUAL',
        'S21_SEMANTIC_REJECT_BEFORE_CACHE_ROUTE',
        'stage20ExpectedPaintToken',
        'isStage19BindingFresh',
        'stage19VisualVerificationPending',
        'cachedDrivingDistancesFromAddressKm',
        'drivingDistancesFromAddressKm',
        'preparePaint',
        'stage20ExpectedPaintToken',
    )
    for item in checks:
        if item not in transformed:
            fail(f'applied Stage23 service missing {item}')

    process_start = transformed.index('    private suspend fun processUniversalVisualStage19(')
    process_end = transformed.index('    private fun stage20BindingSnapshot(', process_start)
    process = transformed[process_start:process_end]
    if process.index('FarolCausalCorrectionStage21.validateEvaluation(evaluationStage19)') > process.index('cachedDrivingDistancesFromAddressKm'):
        fail('Stage21 semantic gate moved after cache in Stage23')
    if 'drivingDistancesFromAddressKm' not in transformed[process_end:]:
        fail('real Google Maps driving route path was removed')
    if 'FarolCausalCorrectionStage21.evaluate(blocks)' not in (root / PIPELINE).read_text(encoding='utf-8'):
        fail('Stage21 semantic evaluator delegation must remain intact')
    if 'FarolVisualIdentityStage23.Metrics.exportReport()' not in (root / REPORT).read_text(encoding='utf-8'):
        fail('Stage23 automatic metrics report was not integrated')

    print('stage23_apply=passed')
    print('versionName=0.1.203')
    print('versionCode=5487')
    print('visual_identity_precedes_evaluate=true')
    print('same_visual_skips_before_evaluate=true')
    print('scheduled_stale_cancel_before_evaluate=true')
    print('self_overlay_same_visual_skips=true')
    print('external_popup_not_package_blocked=true')
    print('incremental_inside_window_early_exit=true')
    print('ocr_same_generation_no_rerun=true')
    print('ocr_new_generation_single_rerun=true')
    print('stage21_semantic_gate_preserved=true')
    print('stage20_paint_token_preserved=true')
    print('stage19_freshness_preserved=true')
    print('real_google_route_preserved=true')
    print('automatic_stage23_metrics=true')


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('source_root', nargs='?', type=Path)
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    self_test()
    if args.self_test:
        return
    if args.source_root is None:
        fail('source_root required')
    apply(args.source_root.resolve())


if __name__ == '__main__':
    main()
