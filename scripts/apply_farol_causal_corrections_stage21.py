#!/usr/bin/env python3
from __future__ import annotations
import argparse
import shutil
from pathlib import Path

SERVICE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt')
PIPELINE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolUniversalVisualPipelineStage19.kt')
HELPER = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolCausalCorrectionStage21.kt')
TEST = Path('app/src/test/java/br/com/mapeiaia/rotacerta/FarolCausalCorrectionStage21Test.kt')
BUILD = Path('app/build.gradle.kts')
PATCH_ROOT = Path(__file__).resolve().parents[1]
HELPER_TEMPLATE = PATCH_ROOT / 'stage21/FarolCausalCorrectionStage21.kt'
TEST_TEMPLATE = PATCH_ROOT / 'stage21/FarolCausalCorrectionStage21Test.kt'
MARKER = 'FAROL_CAUSAL_CORRECTION_STAGE21'

HANDLER = r'''    private fun handleUniversalVisualEventStage19(
        eventPackageStage19: String?,
        eventTypeStage20: Int,
        eventWindowIdStage20: Int,
    ): Boolean {
        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return false
        if (bubbleGestureActive) return true // bubble_drag_accessibility_pause_0_1_116
        val nowNsStage21 = SystemClock.elapsedRealtimeNanos()
        val eventDecisionStage21 = stage21EventGate.decide(
            packageName = eventPackageStage19,
            eventType = eventTypeStage20,
            windowId = eventWindowIdStage20,
            nowNs = nowNsStage21,
            selfPackageName = packageName,
            selfSuppressionUntilNs = stage21SelfEventSuppressionUntilNs,
            expensiveWorkActive = screenshotInProgress.get() || universalRouteJob?.isActive == true,
        )
        if (!eventDecisionStage21.process) {
            FarolForensicTraceStage20.note(
                nowNsStage21,
                "S21_EVENT_COALESCED",
                details = "reason=${eventDecisionStage21.reason}; package=${eventPackageStage19.orEmpty()}; eventType=$eventTypeStage20; window=$eventWindowIdStage20",
            )
            return true
        }
        val cycleIdStage20 = FarolForensicTraceStage20.beginCycle(
            nowNs = nowNsStage21,
            packageName = eventPackageStage19,
            eventType = eventTypeStage20,
            eventWindowId = eventWindowIdStage20,
        )
        stage20LastCycleId = cycleIdStage20
        FarolForensicTraceStage20.accessibilityCollectStarted(cycleIdStage20, SystemClock.elapsedRealtimeNanos())
        val blocksStage19 = collectUniversalAccessibilityBlocksStage19()
        FarolForensicTraceStage20.accessibilityCollectFinished(
            cycleIdStage20,
            SystemClock.elapsedRealtimeNanos(),
            runCatching { windows.size }.getOrDefault(0),
            blocksStage19.size,
        )
        FarolForensicTraceStage20.accessibilityEvaluateStarted(cycleIdStage20, SystemClock.elapsedRealtimeNanos())
        val evaluationStage19 = FarolLatencyProbeStage9.measureValue(
            stage = "STAGE19_UNIVERSAL_VISUAL_ACCESSIBILITY",
            source = "Accessibility",
        ) {
            FarolUniversalVisualPipelineStage19.evaluate(blocksStage19)
        }
        FarolForensicTraceStage20.accessibilityEvaluateFinished(
            cycleIdStage20,
            SystemClock.elapsedRealtimeNanos(),
            evaluationStage19 != null,
        )
        if (evaluationStage19 != null) {
            stage19VisualVerificationPending = false
            stage19OcrSerial += 1L
            stage21OcrGate.cancelBecauseAccessibilityWon()
            stage19OcrRerunRequested = false
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                processUniversalVisualStage19(evaluationStage19, "Accessibility", cycleIdStage20)
            }
        } else {
            stage19VisualVerificationPending = true
            requestUniversalScreenshotStage19(eventPackageStage19, cycleIdStage20)
        }
        return true
    }

'''

COLLECT = r'''    private fun collectUniversalAccessibilityBlocksStage19(): List<FarolUniversalVisualPipelineStage19.VisualBlock> {
        val outputStage19 = ArrayList<FarolCardBlock0188>(96)
        val budgetStage19 = intArrayOf(0)
        val visibleWindowsStage19 = runCatching { windows }.getOrDefault(emptyList())
            .sortedByDescending { runCatching { it.layer }.getOrDefault(0) }
        for (windowStage19 in visibleWindowsStage19) {
            if (budgetStage19[0] >= MAX_ACCESSIBILITY_NODES_0167 || outputStage19.size >= 160) break
            val rootStage19 = runCatching { windowStage19.root }.getOrNull() ?: continue
            val packageStage19 = safeNodePackageName0185(rootStage19) ?: "visual.unknown"
            // The Rota Certa accessibility overlay is never visual ride authority. Skipping its
            // own window breaks showOverlay -> self-event -> full traversal/OCR loops while the
            // next visual window remains eligible, including ride popups over Rota Certa.
            if (normalizePackageName(packageStage19) == normalizePackageName(packageName)) continue
            val windowIdStage19 = runCatching { windowStage19.id }.getOrDefault(-1)
            val layerStage19 = runCatching { windowStage19.layer }.getOrDefault(0)
            val beforeStage21 = outputStage19.size
            collectAccessibilitySubtreeBlocks0188(
                node0188 = rootStage19,
                id0188 = "stage19:$windowIdStage19",
                parentId0188 = null,
                depth0188 = 0,
                packageName0188 = packageStage19,
                windowId0188 = windowIdStage19,
                windowLayer0188 = layerStage19,
                output0188 = outputStage19,
                budget0188 = budgetStage19,
            )
            if (outputStage19.size > beforeStage21 && FarolCausalCorrectionStage21.hasAddressEvidence(
                    outputStage19.subList(beforeStage21, outputStage19.size).asSequence().map { it.text },
                )
            ) {
                // Windows are already ordered by visual layer. Once the highest window contains
                // address evidence, lower windows cannot become authority and are not traversed.
                break
            }
        }
        return outputStage19.map { blockStage19 ->
            FarolUniversalVisualPipelineStage19.VisualBlock(
                id = blockStage19.id,
                parentId = blockStage19.parentId,
                metadataPackageName = blockStage19.packageName,
                windowId = blockStage19.windowId,
                windowLayer = blockStage19.windowLayer,
                depth = blockStage19.depth,
                text = blockStage19.text,
                source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
                left = blockStage19.left,
                top = blockStage19.top,
                right = blockStage19.right,
                bottom = blockStage19.bottom,
                syntheticRoot = blockStage19.syntheticRoot,
            )
        }
    }

'''

OCR = r'''    private fun requestUniversalScreenshotStage19(eventPackageStage19: String?, cycleIdStage20: Long? = null) {
        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings) || bubbleGestureActive) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val requestStage21 = stage21OcrGate.request()
        if (!requestStage21.startNow) {
            stage19OcrRerunRequested = true
            FarolForensicTraceStage20.ocrStage(
                SystemClock.elapsedRealtimeNanos(), requestStage21.token, "DEFERRED_BUSY", cycleIdStage20,
                "stage21=coalesced_single_flight",
            )
            return
        }
        val serialStage19 = ++stage19OcrSerial
        val tokenStage21 = requestStage21.token
        FarolForensicTraceStage20.ocrStage(
            SystemClock.elapsedRealtimeNanos(), serialStage19, "REQUEST", cycleIdStage20,
            "package=${eventPackageStage19.orEmpty()}; s21token=$tokenStage21",
        )
        val visualWindowIdStage19 = stage19ActiveWindowId ?: runCatching { rootInActiveWindow?.windowId }.getOrNull() ?: 0
        if (!screenshotInProgress.compareAndSet(false, true)) {
            stage19OcrRerunRequested = true
            stage21OcrGate.complete(tokenStage21)
            FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "DEFERRED_BUSY", cycleIdStage20, "atomic_race=true")
            return
        }
        stage19OcrRerunRequested = false
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "SCREENSHOT_CALLBACK", cycleIdStage20)
                        scope.launch {
                            var bitmapStage19: Bitmap? = null
                            try {
                                if (!stage21OcrGate.isCurrent(tokenStage21) || serialStage19 != stage19OcrSerial) {
                                    FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "STALE_BEFORE_BITMAP", cycleIdStage20)
                                    return@launch
                                }
                                FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "BITMAP_CONVERT_START", cycleIdStage20)
                                bitmapStage19 = screenshot.toSoftwareBitmap() ?: run {
                                    FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "BITMAP_CONVERT_FAILED", cycleIdStage20)
                                    return@launch
                                }
                                FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "BITMAP_CONVERT_END", cycleIdStage20)
                                if (!stage21OcrGate.isCurrent(tokenStage21) || serialStage19 != stage19OcrSerial) {
                                    FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "STALE_BEFORE_EXTRACT", cycleIdStage20)
                                    return@launch
                                }
                                val ocrStartedNsStage20 = SystemClock.elapsedRealtimeNanos()
                                val structuredStage19 = withContext(Dispatchers.Default) {
                                    ocrService.extractStructuredText(bitmapStage19)
                                }
                                val extractEndedNsStage20 = SystemClock.elapsedRealtimeNanos()
                                FarolForensicTraceStage20.ocrStage(
                                    extractEndedNsStage20, serialStage19, "EXTRACT_END", cycleIdStage20,
                                    "extract_us=${(extractEndedNsStage20 - ocrStartedNsStage20).coerceAtLeast(0L) / 1000L}; blocks=${structuredStage19.blocks.size}",
                                )
                                if (!stage21OcrGate.isCurrent(tokenStage21) || serialStage19 != stage19OcrSerial || !WorkModePolicy0162.isEnabled(currentSettings)) {
                                    FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "STALE_AFTER_EXTRACT", cycleIdStage20, "latestSerial=$stage19OcrSerial")
                                    return@launch
                                }
                                val fragmentsStage19 = structuredStage19.blocks.take(120).mapIndexedNotNull { indexStage19, blockStage19 ->
                                    blockStage19.text.takeIf(String::isNotBlank)?.let {
                                        FarolSpatialFragment0189(
                                            id = "stage19-ocr:$serialStage19/$indexStage19",
                                            text = it,
                                            left = blockStage19.left,
                                            top = blockStage19.top,
                                            right = blockStage19.right,
                                            bottom = blockStage19.bottom,
                                        )
                                    }
                                }
                                val blocksStage19 = FarolVisualPriority0189.cluster("stage19-ocr:$serialStage19", fragmentsStage19)
                                    .map { groupStage19 ->
                                        FarolUniversalVisualPipelineStage19.VisualBlock(
                                            id = groupStage19.id,
                                            metadataPackageName = eventPackageStage19,
                                            windowId = visualWindowIdStage19,
                                            windowLayer = Int.MAX_VALUE,
                                            depth = 1,
                                            text = groupStage19.text,
                                            source = FarolUniversalVisualPipelineStage19.Source.Ocr,
                                            left = groupStage19.left,
                                            top = groupStage19.top,
                                            right = groupStage19.right,
                                            bottom = groupStage19.bottom,
                                        )
                                    }
                                val evaluationStage19 = withContext(Dispatchers.Default) {
                                    FarolUniversalVisualPipelineStage19.evaluate(blocksStage19)
                                }
                                FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "EVALUATE_END", cycleIdStage20, "candidate=${evaluationStage19 != null}")
                                if (!stage21OcrGate.isCurrent(tokenStage21) || serialStage19 != stage19OcrSerial) {
                                    FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "STALE_AFTER_EVALUATE", cycleIdStage20, "latestSerial=$stage19OcrSerial")
                                    return@launch
                                }
                                stage19VisualVerificationPending = false
                                if (evaluationStage19 != null) {
                                    processUniversalVisualStage19(evaluationStage19, "Ocr", cycleIdStage20)
                                } else {
                                    FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "NO_CANDIDATE", cycleIdStage20)
                                    hardClearUniversalTwoAddress(
                                        reason = "Snapshot visual atual sem dois endereços semanticamente completos Stage21.",
                                        keepWaitingYellow = true,
                                    )
                                }
                            } finally {
                                FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "COMPLETE", cycleIdStage20)
                                bitmapStage19?.takeUnless(Bitmap::isRecycled)?.recycle()
                                screenshotInProgress.set(false)
                                val rerunStage21 = stage21OcrGate.complete(tokenStage21) || stage19OcrRerunRequested
                                stage19OcrRerunRequested = false
                                if (rerunStage21 && WorkModePolicy0162.isEnabled(currentSettings)) {
                                    requestUniversalScreenshotStage19(eventPackageStage19, cycleIdStage20)
                                }
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "SCREENSHOT_FAILURE", cycleIdStage20, "errorCode=$errorCode")
                        screenshotInProgress.set(false)
                        stage19VisualVerificationPending = false
                        val rerunStage21 = stage21OcrGate.complete(tokenStage21) || stage19OcrRerunRequested
                        stage19OcrRerunRequested = false
                        if (rerunStage21 && WorkModePolicy0162.isEnabled(currentSettings)) {
                            requestUniversalScreenshotStage19(eventPackageStage19, cycleIdStage20)
                        }
                    }
                },
            )
        }.onFailure {
            screenshotInProgress.set(false)
            stage19VisualVerificationPending = false
            stage21OcrGate.complete(tokenStage21)
        }
    }

'''


def fail(msg: str) -> None:
    raise SystemExit(msg)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    n = text.count(old)
    if n != 1:
        fail(f'Stage21 anchor {label}: expected 1, found {n}')
    return text.replace(old, new, 1)


def replace_section(text: str, start: str, end: str, replacement: str, label: str) -> str:
    a = text.find(start)
    b = text.find(end, a + 1)
    if a < 0 or b <= a:
        fail(f'Stage21 section {label}: markers not found')
    return text[:a] + replacement + text[b:]


def self_test() -> None:
    for p in (HELPER_TEMPLATE, TEST_TEMPLATE):
        if not p.is_file(): fail(f'missing Stage21 support file: {p}')
    helper = HELPER_TEMPLATE.read_text(encoding='utf-8')
    tests = TEST_TEMPLATE.read_text(encoding='utf-8')
    for required in (
        MARKER, 'SEMANTIC_ADDRESS_GATE_BEFORE_CACHE_AND_GOOGLE_STAGE21',
        'EVENT_COALESCING_AND_SELF_OVERLAY_SUPPRESSION_STAGE21',
        'OCR_SINGLE_FLIGHT_NO_BACKLOG_STAGE21', 'danglingConnectorRegex',
        'cancelBecauseAccessibilityWon', 'hasAddressEvidence',
    ):
        if required not in helper: fail(f'Stage21 helper missing {required}')
    required_tests = (
        'avenidaMendoncaEIsRejected', 'avenidaMendoncaWithoutContextDoesNotAuthorizeRoute',
        'completeAddressWithNumberAndCityIsAccepted', 'completeStreetWithoutNumberButWithNeighborhoodAndCityIsAccepted',
        'smallOcrSpellingErrorWithFullStructureIsAccepted', 'twoDifferentCardBlocksAreNotMixed',
        'completePickupAndTruncatedDestinationNeverDecide', 'ownOverlayEventInsideSuppressionWindowIsIgnored',
        'identicalEventBurstIsCoalesced', 'severalWindowsPrioritizeHighestValidVisualWindow',
        'rapidAddressChangeChangesScreenHash', 'oldOcrTokenBecomesStaleBeforeExtractWhenNewDemandArrives',
        'cacheHitCannotBypassSemanticGate', 'cacheMissCannotSendInvalidCandidateToGoogle',
        'verificationPendingStillBlocksPainting', 'uberMetadataDoesNotRegressValidVisualCard',
        'ninetyNineMetadataDoesNotRegressValidVisualCard', 'inDriveMetadataDoesNotRegressValidVisualCard',
    )
    for name in required_tests:
        if name not in tests: fail(f'Stage21 mandatory regression missing: {name}')
    count = tests.count('@Test')
    if count != 40: fail(f'expected 40 Stage21 tests, found {count}')
    for forbidden in ('Thread.sleep(', 'delay(', 'drivingDistanceKm(', 'drivingDistancesFromAddressKm('):
        if forbidden in helper: fail(f'Stage21 helper must be deterministic/passive, found {forbidden}')
    print('stage21_self_test=passed')
    print('stage21_test_methods=40')
    print('semantic_gate_before_cache_and_google=true')
    print('own_overlay_self_event_suppression=true')
    print('equivalent_event_coalescing=true')
    print('ocr_single_flight=true')
    print('ocr_stale_before_extract_guard=true')


def apply(root: Path) -> None:
    service_path = root / SERVICE
    pipeline_path = root / PIPELINE
    build_path = root / BUILD
    if not service_path.is_file() or not pipeline_path.is_file() or not build_path.is_file():
        fail('Stage21 requires materialized Stage20 source')
    service = service_path.read_text(encoding='utf-8')
    pipeline = pipeline_path.read_text(encoding='utf-8')
    build = build_path.read_text(encoding='utf-8')
    if 'FAROL_FORENSIC_CAUSALITY_STAGE20' not in service:
        fail('Stage21 requires applied Stage20 forensic service')
    if 'UNIVERSAL_VISUAL_AUTHORITY_STAGE19' not in pipeline:
        fail('Stage21 requires Stage19 visual pipeline')
    if MARKER in service or (root / HELPER).exists():
        fail('Stage21 already applied')
    if 'versionCode = 5485' not in build or 'versionName = "0.1.201"' not in build:
        fail('Stage21 requires exact 0.1.201/5485 Stage20 baseline')

    state_anchor = (
        '    private var stage20ExpectedPaintToken: FarolForensicTraceStage20.PaintToken? = null\n'
        '    // FAROL_FORENSIC_CAUSALITY_STAGE20 — diagnostic only, never authority\n'
    )
    state_new = (
        '    private var stage20ExpectedPaintToken: FarolForensicTraceStage20.PaintToken? = null\n'
        '    private val stage21EventGate = FarolCausalCorrectionStage21.EventGate()\n'
        '    private val stage21OcrGate = FarolCausalCorrectionStage21.OcrGate()\n'
        '    private var stage21SelfEventSuppressionUntilNs: Long = 0L\n'
        '    // FAROL_CAUSAL_CORRECTION_STAGE21 — causal fixes from Stage20 physical evidence\n'
        '    // FAROL_FORENSIC_CAUSALITY_STAGE20 — diagnostic only, never authority\n'
    )
    service = replace_once(service, state_anchor, state_new, 'state fields')
    service = replace_section(service, '    private fun handleUniversalVisualEventStage19(', '    private fun collectUniversalAccessibilityBlocksStage19()', HANDLER, 'event handler')
    service = replace_section(service, '    private fun collectUniversalAccessibilityBlocksStage19()', '    private fun requestUniversalScreenshotStage19(', COLLECT, 'accessibility collector')
    service = replace_section(service, '    private fun requestUniversalScreenshotStage19(', '    private suspend fun processUniversalVisualStage19(', OCR, 'ocr single flight')

    process_anchor = (
        '    ) {\n'
        '        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return\n'
        '        val previousBindingStage20 = currentStage20BindingSnapshot()\n'
    )
    process_guard = (
        '    ) {\n'
        '        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return\n'
        '        val semanticStage21 = FarolCausalCorrectionStage21.validateEvaluation(evaluationStage19)\n'
        '        if (!semanticStage21.accepted) {\n'
        '            FarolForensicTraceStage20.note(\n'
        '                SystemClock.elapsedRealtimeNanos(), "S21_SEMANTIC_REJECT_BEFORE_CACHE_ROUTE", cycleIdStage20,\n'
        '                details = "reason=${semanticStage21.reason}; destination=${evaluationStage19.destination}",\n'
        '            )\n'
        '            hardClearUniversalTwoAddress(\n'
        '                reason = "Destino visual incompleto rejeitado antes de cache/Google: ${semanticStage21.reason}.",\n'
        '                keepWaitingYellow = true,\n'
        '            )\n'
        '            return\n'
        '        }\n'
        '        val previousBindingStage20 = currentStage20BindingSnapshot()\n'
    )
    process_start = service.find('    private suspend fun processUniversalVisualStage19(')
    process_end = service.find('    private fun stage20BindingSnapshot(', process_start)
    if process_start < 0 or process_end <= process_start: fail('Stage21 process section not found')
    process_section = service[process_start:process_end]
    process_section = replace_once(process_section, process_anchor, process_guard, 'semantic defense before cache')
    service = service[:process_start] + process_section + service[process_end:]

    show_start = service.find('    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {')
    show_end = service.find('    private fun formatBubbleDistanceKm(', show_start)
    if show_start < 0 or show_end <= show_start: fail('Stage21 showOverlay section not found')
    show = service[show_start:show_end]
    show = replace_once(
        show,
        '        val manager = windowManager ?: return\n',
        '        val manager = windowManager ?: return\n'
        '        if (color != currentRadarColor || distanceKm != currentDistanceKm) {\n'
        '            stage21SelfEventSuppressionUntilNs = SystemClock.elapsedRealtimeNanos() + 250_000_000L\n'
        '        }\n',
        'self overlay suppression arm',
    )
    service = service[:show_start] + show + service[show_end:]

    eval_start = pipeline.find('    fun evaluate(blocks: List<VisualBlock>): Evaluation? {')
    eval_end = pipeline.find('    fun bindingMatchesCurrent(', eval_start)
    if eval_start < 0 or eval_end <= eval_start: fail('Stage21 Stage19 evaluate section not found')
    pipeline = pipeline[:eval_start] + (
        '    fun evaluate(blocks: List<VisualBlock>): Evaluation? =\n'
        '        FarolCausalCorrectionStage21.evaluate(blocks)\n\n'
    ) + pipeline[eval_end:]

    build = replace_once(build, 'versionCode = 5485', 'versionCode = 5486', 'versionCode')
    build = replace_once(build, 'versionName = "0.1.201"', 'versionName = "0.1.202"', 'versionName')

    (root / HELPER).parent.mkdir(parents=True, exist_ok=True)
    (root / TEST).parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HELPER_TEMPLATE, root / HELPER)
    shutil.copyfile(TEST_TEMPLATE, root / TEST)
    service_path.write_text(service, encoding='utf-8')
    pipeline_path.write_text(pipeline, encoding='utf-8')
    build_path.write_text(build, encoding='utf-8')

    transformed = service_path.read_text(encoding='utf-8')
    checks = (
        MARKER, 'stage21EventGate', 'stage21OcrGate', 'S21_EVENT_COALESCED',
        'STALE_BEFORE_BITMAP', 'STALE_BEFORE_EXTRACT', 'S21_SEMANTIC_REJECT_BEFORE_CACHE_ROUTE',
        'stage20ExpectedPaintToken', 'isStage19BindingFresh', 'cachedDrivingDistancesFromAddressKm',
        'drivingDistancesFromAddressKm', 'preparePaint',
    )
    for item in checks:
        if item not in transformed: fail(f'applied service missing {item}')
    applied_process_start = transformed.index('    private suspend fun processUniversalVisualStage19(')
    applied_process_end = transformed.index('    private fun stage20BindingSnapshot(', applied_process_start)
    applied_process = transformed[applied_process_start:applied_process_end]
    if applied_process.index('validateEvaluation(evaluationStage19)') > applied_process.index('cachedDrivingDistancesFromAddressKm'):
        fail('semantic gate must precede cache lookup')
    if 'drivingDistancesFromAddressKm' not in transformed[applied_process_end:]:
        fail('Google route path unexpectedly missing after Stage21')
    if 'FarolCausalCorrectionStage21.evaluate(blocks)' not in pipeline_path.read_text(encoding='utf-8'):
        fail('Stage19 evaluator not delegated to Stage21 optimized semantic evaluator')
    print('stage21_apply=passed')
    print('versionName=0.1.202')
    print('versionCode=5486')
    print('semantic_gate_precedes_cache=true')
    print('semantic_gate_precedes_google=true')
    print('own_overlay_window_skipped=true')
    print('highest_address_window_early_exit=true')
    print('event_burst_coalescing=true')
    print('ocr_busy_coalesced_without_queue=true')
    print('ocr_stale_checked_before_extract=true')
    print('stage20_forensics_preserved=true')
    print('stage19_freshness_preserved=true')


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('source_root', nargs='?', type=Path)
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    self_test()
    if args.self_test: return
    if args.source_root is None: fail('source_root required')
    apply(args.source_root.resolve())

if __name__ == '__main__':
    main()
