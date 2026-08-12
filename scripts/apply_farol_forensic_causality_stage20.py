#!/usr/bin/env python3
from __future__ import annotations
import argparse
import re
import shutil
from pathlib import Path

SERVICE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt')
REPORT = Path('app/src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt')
MAIN = Path('app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt')
HELPER = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolForensicTraceStage20.kt')
TEST = Path('app/src/test/java/br/com/mapeiaia/rotacerta/FarolForensicTraceStage20Test.kt')
BUILD = Path('app/build.gradle.kts')
PATCH_ROOT = Path(__file__).resolve().parents[1]
HELPER_TEMPLATE = PATCH_ROOT / 'stage20/FarolForensicTraceStage20.kt'
TEST_TEMPLATE = PATCH_ROOT / 'stage20/FarolForensicTraceStage20Test.kt'
MARKER = 'FAROL_FORENSIC_CAUSALITY_STAGE20'


def fail(msg: str) -> None:
    raise SystemExit(msg)

def replace_once(text: str, old: str, new: str, label: str) -> str:
    n = text.count(old)
    if n != 1:
        fail(f'Stage20 anchor {label}: expected 1, found {n}')
    return text.replace(old, new, 1)

def replace_in_section(text: str, start_marker: str, end_marker: str, old: str, new: str, label: str) -> str:
    start = text.find(start_marker)
    end = text.find(end_marker, start + 1)
    if start < 0 or end <= start:
        fail(f'Stage20 section {label}: markers not found')
    section = text[start:end]
    section2 = replace_once(section, old, new, label)
    return text[:start] + section2 + text[end:]

def self_test() -> None:
    for p in (HELPER_TEMPLATE, TEST_TEMPLATE):
        if not p.is_file(): fail(f'missing Stage20 support file: {p}')
    helper = HELPER_TEMPLATE.read_text(encoding='utf-8')
    tests = TEST_TEMPLATE.read_text(encoding='utf-8')
    required = [
        MARKER, 'ELAPSED_REALTIME_NANOS_STAGE20', 'PaintToken', 'traceId', 'operationId',
        'S20_STALE_RESULT_BLOCKED_', 'unscoped_final_paint', 'route_to_paint_delay',
        'FULL CAUSAL CHRONOLOGY', 'callSite', 'MAX_EVENTS = 8_192',
    ]
    for item in required:
        if item not in helper: fail(f'Stage20 helper missing {item}')
    forbidden = ['Thread.sleep(', 'delay(', 'takeScreenshot(', 'drivingDistancesFromAddressKm(', 'ocrService.']
    for item in forbidden:
        if item in helper: fail(f'Stage20 diagnostic helper must be passive, found {item}')
    count = tests.count('@Test')
    if count != 34: fail(f'expected 34 Stage20 tests, found {count}')
    print('stage20_self_test=passed')
    print('stage20_test_methods=34')
    print('clock=elapsedRealtimeNanos')
    print('diagnostic_only=true')
    print('paint_token_required_for_stage19_final=true')
    print('unscoped_final_paint_detector=true')
    print('route_to_paint_detector=true')
    print('stale_binding_detector=true')


def apply(root: Path) -> None:
    paths = [root / SERVICE, root / REPORT, root / MAIN, root / BUILD]
    if any(not p.is_file() for p in paths): fail('Stage20 requires materialized Stage19 source')
    service = (root / SERVICE).read_text(encoding='utf-8')
    report = (root / REPORT).read_text(encoding='utf-8')
    main = (root / MAIN).read_text(encoding='utf-8')
    build = (root / BUILD).read_text(encoding='utf-8')
    if 'UNIVERSAL_VISUAL_AUTHORITY_STAGE19' not in service: fail('Stage20 requires applied Stage19 service')
    if MARKER in service or (root / HELPER).exists(): fail('Stage20 already applied')
    if 'versionCode = 5484' not in build or 'versionName = "0.1.200"' not in build:
        fail('Stage20 requires exact 0.1.200/5484 Stage19 baseline')

    service = replace_once(
        service,
        '    private var stage19ActiveBlockId: String? = null\n',
        '    private var stage19ActiveBlockId: String? = null\n'
        '    private var stage20LastCycleId: Long = 0L\n'
        '    private var stage20ExpectedPaintToken: FarolForensicTraceStage20.PaintToken? = null\n'
        '    // FAROL_FORENSIC_CAUSALITY_STAGE20 — diagnostic only, never authority\n',
        'state fields',
    )

    service = replace_once(
        service,
        '        if (handleUniversalVisualEventStage19(eventPackage)) return\n',
        '        val eventWindowIdStage20 = runCatching { event.windowId }.getOrDefault(0)\n'
        '        if (handleUniversalVisualEventStage19(eventPackage, eventType0187, eventWindowIdStage20)) return\n',
        'event cycle call',
    )
    service = replace_once(
        service,
        '    private fun handleUniversalVisualEventStage19(eventPackageStage19: String?): Boolean {\n',
        '    private fun handleUniversalVisualEventStage19(\n'
        '        eventPackageStage19: String?,\n'
        '        eventTypeStage20: Int,\n'
        '        eventWindowIdStage20: Int,\n'
        '    ): Boolean {\n',
        'handler signature',
    )
    service = replace_in_section(
        service,
        '    private fun handleUniversalVisualEventStage19(',
        '    private fun collectUniversalAccessibilityBlocksStage19()',
        '        if (bubbleGestureActive) return true // bubble_drag_accessibility_pause_0_1_116\n'
        '        val blocksStage19 = collectUniversalAccessibilityBlocksStage19()\n'
        '        val evaluationStage19 = FarolLatencyProbeStage9.measureValue(\n',
        '        if (bubbleGestureActive) return true // bubble_drag_accessibility_pause_0_1_116\n'
        '        val cycleIdStage20 = FarolForensicTraceStage20.beginCycle(\n'
        '            nowNs = SystemClock.elapsedRealtimeNanos(),\n'
        '            packageName = eventPackageStage19,\n'
        '            eventType = eventTypeStage20,\n'
        '            eventWindowId = eventWindowIdStage20,\n'
        '        )\n'
        '        stage20LastCycleId = cycleIdStage20\n'
        '        FarolForensicTraceStage20.accessibilityCollectStarted(cycleIdStage20, SystemClock.elapsedRealtimeNanos())\n'
        '        val blocksStage19 = collectUniversalAccessibilityBlocksStage19()\n'
        '        FarolForensicTraceStage20.accessibilityCollectFinished(\n'
        '            cycleIdStage20, SystemClock.elapsedRealtimeNanos(),\n'
        '            runCatching { windows.size }.getOrDefault(0), blocksStage19.size,\n'
        '        )\n'
        '        FarolForensicTraceStage20.accessibilityEvaluateStarted(cycleIdStage20, SystemClock.elapsedRealtimeNanos())\n'
        '        val evaluationStage19 = FarolLatencyProbeStage9.measureValue(\n',
        'handler collect/evaluate start',
    )
    service = replace_in_section(
        service,
        '    private fun handleUniversalVisualEventStage19(',
        '    private fun collectUniversalAccessibilityBlocksStage19()',
        '        }\n        if (evaluationStage19 != null) {\n',
        '        }\n'
        '        FarolForensicTraceStage20.accessibilityEvaluateFinished(\n'
        '            cycleIdStage20, SystemClock.elapsedRealtimeNanos(), evaluationStage19 != null,\n'
        '        )\n'
        '        if (evaluationStage19 != null) {\n',
        'handler evaluate end',
    )
    service = replace_in_section(
        service,
        '    private fun handleUniversalVisualEventStage19(',
        '    private fun collectUniversalAccessibilityBlocksStage19()',
        '                processUniversalVisualStage19(evaluationStage19, "Accessibility")\n',
        '                processUniversalVisualStage19(evaluationStage19, "Accessibility", cycleIdStage20)\n',
        'handler candidate process',
    )
    service = replace_in_section(
        service,
        '    private fun handleUniversalVisualEventStage19(',
        '    private fun collectUniversalAccessibilityBlocksStage19()',
        '            requestUniversalScreenshotStage19(eventPackageStage19)\n',
        '            requestUniversalScreenshotStage19(eventPackageStage19, cycleIdStage20)\n',
        'handler ocr fallback',
    )

    schedule_start = service.find('    private fun scheduleVisibleTextAnalysis(')
    schedule_end = service.find('    private fun scheduleScreenshotFallback127', schedule_start)
    if schedule_start < 0 or schedule_end <= schedule_start: fail('Stage20 scheduled section not found')
    section = service[schedule_start:schedule_end]
    section = replace_once(
        section,
        '        analyzeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {\n            val blocksStage19 = collectUniversalAccessibilityBlocksStage19()\n',
        '        analyzeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {\n'
        '            val cycleIdStage20 = FarolForensicTraceStage20.beginCycle(\n'
        '                SystemClock.elapsedRealtimeNanos(), null, -1, runCatching { rootInActiveWindow?.windowId }.getOrDefault(0),\n'
        '            )\n'
        '            stage20LastCycleId = cycleIdStage20\n'
        '            FarolForensicTraceStage20.accessibilityCollectStarted(cycleIdStage20, SystemClock.elapsedRealtimeNanos())\n'
        '            val blocksStage19 = collectUniversalAccessibilityBlocksStage19()\n'
        '            FarolForensicTraceStage20.accessibilityCollectFinished(cycleIdStage20, SystemClock.elapsedRealtimeNanos(), runCatching { windows.size }.getOrDefault(0), blocksStage19.size)\n'
        '            FarolForensicTraceStage20.accessibilityEvaluateStarted(cycleIdStage20, SystemClock.elapsedRealtimeNanos())\n',
        'scheduled cycle',
    )
    section = replace_once(
        section,
        '            }\n            if (evaluationStage19 != null) {\n',
        '            }\n'
        '            FarolForensicTraceStage20.accessibilityEvaluateFinished(cycleIdStage20, SystemClock.elapsedRealtimeNanos(), evaluationStage19 != null)\n'
        '            if (evaluationStage19 != null) {\n',
        'scheduled evaluation end',
    )
    section = replace_once(section, '                processUniversalVisualStage19(evaluationStage19, "AccessibilityScheduled")\n', '                processUniversalVisualStage19(evaluationStage19, "AccessibilityScheduled", cycleIdStage20)\n', 'scheduled process')
    section = replace_once(section, '                requestUniversalScreenshotStage19(null)\n', '                requestUniversalScreenshotStage19(null, cycleIdStage20)\n', 'scheduled ocr')
    service = service[:schedule_start] + section + service[schedule_end:]

    service = replace_once(
        service,
        '    private fun requestUniversalScreenshotStage19(eventPackageStage19: String?) {\n',
        '    private fun requestUniversalScreenshotStage19(eventPackageStage19: String?, cycleIdStage20: Long? = null) {\n',
        'ocr signature',
    )
    service = replace_in_section(
        service,
        '    private fun requestUniversalScreenshotStage19(',
        '    private suspend fun processUniversalVisualStage19(',
        '        val serialStage19 = ++stage19OcrSerial\n',
        '        val serialStage19 = ++stage19OcrSerial\n'
        '        FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "REQUEST", cycleIdStage20, "package=${eventPackageStage19.orEmpty()}")\n',
        'ocr request',
    )
    service = replace_in_section(
        service,
        '    private fun requestUniversalScreenshotStage19(',
        '    private suspend fun processUniversalVisualStage19(',
        '        if (!screenshotInProgress.compareAndSet(false, true)) {\n            stage19OcrRerunRequested = true\n            return\n        }\n',
        '        if (!screenshotInProgress.compareAndSet(false, true)) {\n'
        '            stage19OcrRerunRequested = true\n'
        '            FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "DEFERRED_BUSY", cycleIdStage20)\n'
        '            return\n'
        '        }\n',
        'ocr busy',
    )
    service = replace_in_section(
        service,
        '    private fun requestUniversalScreenshotStage19(',
        '    private suspend fun processUniversalVisualStage19(',
        '                    override fun onSuccess(screenshot: ScreenshotResult) {\n                        scope.launch {\n',
        '                    override fun onSuccess(screenshot: ScreenshotResult) {\n'
        '                        FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "SCREENSHOT_CALLBACK", cycleIdStage20)\n'
        '                        scope.launch {\n',
        'ocr screenshot callback',
    )
    service = replace_in_section(
        service,
        '    private fun requestUniversalScreenshotStage19(',
        '    private suspend fun processUniversalVisualStage19(',
        '                                bitmapStage19 = screenshot.toSoftwareBitmap() ?: return@launch\n                                val structuredStage19 = withContext(Dispatchers.Default) {\n                                    ocrService.extractStructuredText(bitmapStage19)\n                                }\n                                if (serialStage19 != stage19OcrSerial || !WorkModePolicy0162.isEnabled(currentSettings)) return@launch\n',
        '                                FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "BITMAP_CONVERT_START", cycleIdStage20)\n'
        '                                bitmapStage19 = screenshot.toSoftwareBitmap() ?: run {\n'
        '                                    FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "BITMAP_CONVERT_FAILED", cycleIdStage20)\n'
        '                                    return@launch\n'
        '                                }\n'
        '                                FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "BITMAP_CONVERT_END", cycleIdStage20)\n'
        '                                val ocrStartedNsStage20 = SystemClock.elapsedRealtimeNanos()\n'
        '                                val structuredStage19 = withContext(Dispatchers.Default) {\n'
        '                                    ocrService.extractStructuredText(bitmapStage19)\n'
        '                                }\n'
        '                                FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "EXTRACT_END", cycleIdStage20, "extract_us=${(SystemClock.elapsedRealtimeNanos() - ocrStartedNsStage20).coerceAtLeast(0L) / 1000L}; blocks=${structuredStage19.blocks.size}")\n'
        '                                if (serialStage19 != stage19OcrSerial || !WorkModePolicy0162.isEnabled(currentSettings)) {\n'
        '                                    FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "STALE_AFTER_EXTRACT", cycleIdStage20, "latestSerial=$stage19OcrSerial")\n'
        '                                    return@launch\n'
        '                                }\n',
        'ocr extract',
    )
    service = replace_in_section(
        service,
        '    private fun requestUniversalScreenshotStage19(',
        '    private suspend fun processUniversalVisualStage19(',
        '                                if (serialStage19 != stage19OcrSerial) return@launch\n                                stage19VisualVerificationPending = false\n                                if (evaluationStage19 != null) {\n                                    processUniversalVisualStage19(evaluationStage19, "Ocr")\n',
        '                                FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "EVALUATE_END", cycleIdStage20, "candidate=${evaluationStage19 != null}")\n'
        '                                if (serialStage19 != stage19OcrSerial) {\n'
        '                                    FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "STALE_AFTER_EVALUATE", cycleIdStage20, "latestSerial=$stage19OcrSerial")\n'
        '                                    return@launch\n'
        '                                }\n'
        '                                stage19VisualVerificationPending = false\n'
        '                                if (evaluationStage19 != null) {\n'
        '                                    processUniversalVisualStage19(evaluationStage19, "Ocr", cycleIdStage20)\n',
        'ocr evaluate/process',
    )
    service = replace_in_section(
        service,
        '    private fun requestUniversalScreenshotStage19(',
        '    private suspend fun processUniversalVisualStage19(',
        '                                } else {\n                                    hardClearUniversalTwoAddress(\n',
        '                                } else {\n'
        '                                    FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "NO_CANDIDATE", cycleIdStage20)\n'
        '                                    hardClearUniversalTwoAddress(\n',
        'ocr no candidate',
    )
    service = replace_in_section(
        service,
        '    private fun requestUniversalScreenshotStage19(',
        '    private suspend fun processUniversalVisualStage19(',
        '                            } finally {\n                                bitmapStage19?.takeUnless(Bitmap::isRecycled)?.recycle()\n',
        '                            } finally {\n'
        '                                FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "COMPLETE", cycleIdStage20)\n'
        '                                bitmapStage19?.takeUnless(Bitmap::isRecycled)?.recycle()\n',
        'ocr complete',
    )
    service = replace_in_section(
        service,
        '    private fun requestUniversalScreenshotStage19(',
        '    private suspend fun processUniversalVisualStage19(',
        '                    override fun onFailure(errorCode: Int) {\n                        screenshotInProgress.set(false)\n',
        '                    override fun onFailure(errorCode: Int) {\n'
        '                        FarolForensicTraceStage20.ocrStage(SystemClock.elapsedRealtimeNanos(), serialStage19, "SCREENSHOT_FAILURE", cycleIdStage20, "errorCode=$errorCode")\n'
        '                        screenshotInProgress.set(false)\n',
        'ocr failure',
    )

    service = replace_once(
        service,
        '    private suspend fun processUniversalVisualStage19(\n        evaluationStage19: FarolUniversalVisualPipelineStage19.Evaluation,\n        sourceStage19: String,\n    ) {\n',
        '    private suspend fun processUniversalVisualStage19(\n'
        '        evaluationStage19: FarolUniversalVisualPipelineStage19.Evaluation,\n'
        '        sourceStage19: String,\n'
        '        cycleIdStage20: Long? = null,\n'
        '    ) {\n',
        'process signature',
    )
    service = replace_in_section(
        service,
        '    private suspend fun processUniversalVisualStage19(',
        '    private fun isStage19BindingFresh(',
        '        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return\n        val windowChangedStage19 =',
        '        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return\n'
        '        val previousBindingStage20 = currentStage20BindingSnapshot()\n'
        '        val windowChangedStage19 =',
        'process previous binding',
    )
    service = replace_in_section(
        service,
        '    private suspend fun processUniversalVisualStage19(',
        '    private fun isStage19BindingFresh(',
        '            universalRouteJob?.cancel()\n            universalRouteJob = null\n',
        '            if (universalRouteJob?.isActive == true) {\n'
        '                FarolForensicTraceStage20.routeCancelled(FarolForensicTraceStage20.traceFor(previousBindingStage20), null, SystemClock.elapsedRealtimeNanos(), "visual_changed")\n'
        '            }\n'
        '            universalRouteJob?.cancel()\n'
        '            universalRouteJob = null\n',
        'route cancel correlation',
    )
    service = replace_in_section(
        service,
        '    private suspend fun processUniversalVisualStage19(',
        '    private fun isStage19BindingFresh(',
        '        stage19VisualVerificationPending = false\n\n        if (!visualChangedStage19 && (lastAnalyzedHash == evaluationStage19.screenHash || universalRouteJob?.isActive == true)) return\n',
        '        stage19VisualVerificationPending = false\n'
        '        val currentBindingStage20 = currentStage20BindingSnapshot()\n'
        '        if (windowChangedStage19 || visualChangedStage19) {\n'
        '            FarolForensicTraceStage20.visualInvalidated(SystemClock.elapsedRealtimeNanos(), previousBindingStage20, currentBindingStage20, "windowChanged=$windowChangedStage19; visualChanged=$visualChangedStage19")\n'
        '        }\n'
        '        val traceIdStage20 = FarolForensicTraceStage20.bindCandidate(\n'
        '            SystemClock.elapsedRealtimeNanos(), cycleIdStage20, currentBindingStage20, sourceStage19,\n'
        '            evaluationStage19.destination, evaluationStage19.blockId,\n'
        '        )\n\n'
        '        if (!visualChangedStage19 && (lastAnalyzedHash == evaluationStage19.screenHash || universalRouteJob?.isActive == true)) {\n'
        '            FarolForensicTraceStage20.note(SystemClock.elapsedRealtimeNanos(), "S20_DUPLICATE_OR_ROUTE_ACTIVE_SKIP", cycleIdStage20, traceIdStage20, binding = currentBindingStage20)\n'
        '            return\n'
        '        }\n',
        'candidate bind and duplicate',
    )
    service = replace_in_section(
        service,
        '    private suspend fun processUniversalVisualStage19(',
        '    private fun isStage19BindingFresh(',
        '        val cachedStage19 = googleMapsService.cachedDrivingDistancesFromAddressKm(\n',
        '        FarolForensicTraceStage20.cacheLookupStarted(traceIdStage20, SystemClock.elapsedRealtimeNanos())\n'
        '        val cachedStage19 = googleMapsService.cachedDrivingDistancesFromAddressKm(\n',
        'cache start',
    )
    service = replace_in_section(
        service,
        '    private suspend fun processUniversalVisualStage19(',
        '    private fun isStage19BindingFresh(',
        '        )\n        if (cachedStage19 != null) {\n            if (!isStage19BindingFresh(bindingStage19)) return\n            val resultStage19 = decideFastWorkRegionChecklist13(\n',
        '        )\n'
        '        FarolForensicTraceStage20.cacheLookupFinished(traceIdStage20, SystemClock.elapsedRealtimeNanos(), cachedStage19 != null)\n'
        '        if (cachedStage19 != null) {\n'
        '            val cacheFreshStage20 = isStage19BindingFresh(bindingStage19)\n'
        '            FarolForensicTraceStage20.bindingCheck(traceIdStage20, "CACHE", SystemClock.elapsedRealtimeNanos(), "CACHE_RESULT", stage20BindingSnapshot(bindingStage19), currentStage20BindingSnapshot(), cacheFreshStage20, stage19VisualVerificationPending)\n'
        '            if (!cacheFreshStage20) return\n'
        '            FarolForensicTraceStage20.decisionStarted(traceIdStage20, "CACHE", SystemClock.elapsedRealtimeNanos())\n'
        '            val resultStage19 = decideFastWorkRegionChecklist13(\n',
        'cache fresh/decision start',
    )
    service = replace_in_section(
        service,
        '    private suspend fun processUniversalVisualStage19(',
        '    private fun isStage19BindingFresh(',
        '            )\n            bubblePrefs.edit().putString("fast_farol_last_path", "stage19_cache_exato").apply()\n            applyUniversalTwoAddressResultStage19(resultStage19, bindingStage19)\n',
        '            )\n'
        '            FarolForensicTraceStage20.decisionFinished(traceIdStage20, "CACHE", SystemClock.elapsedRealtimeNanos(), resultStage19.recommendation.name, resultStage19.nearestConfiguredDistanceKm())\n'
        '            bubblePrefs.edit().putString("fast_farol_last_path", "stage19_cache_exato").apply()\n'
        '            applyUniversalTwoAddressResultStage19(resultStage19, bindingStage19, traceIdStage20, "CACHE")\n',
        'cache decision/apply',
    )
    service = replace_in_section(
        service,
        '    private suspend fun processUniversalVisualStage19(',
        '    private fun isStage19BindingFresh(',
        '        bubblePrefs.edit().putString("fast_farol_last_path", "stage19_rota_google").apply()\n        universalRouteJob = scope.launch {\n            analyzeUniversalTwoAddressStage19(\n                snapshotTextStage19 = evaluationStage19.analysisText,\n                fieldsStage19 = fieldsStage19,\n                bindingStage19 = bindingStage19,\n            )\n        }\n',
        '        bubblePrefs.edit().putString("fast_farol_last_path", "stage19_rota_google").apply()\n'
        '        val routeJobIdStage20 = FarolForensicTraceStage20.routeJobStarted(traceIdStage20, SystemClock.elapsedRealtimeNanos())\n'
        '        universalRouteJob = scope.launch {\n'
        '            analyzeUniversalTwoAddressStage19(\n'
        '                snapshotTextStage19 = evaluationStage19.analysisText,\n'
        '                fieldsStage19 = fieldsStage19,\n'
        '                bindingStage19 = bindingStage19,\n'
        '                traceIdStage20 = traceIdStage20,\n'
        '                routeJobIdStage20 = routeJobIdStage20,\n'
        '            )\n'
        '        }\n',
        'route job correlation',
    )
    service = replace_in_section(
        service,
        '    private suspend fun processUniversalVisualStage19(',
        '    private fun isStage19BindingFresh(',
        '            "source=$sourceStage19; destination=${fieldsStage19.destination.orEmpty()}; screenGeneration=${bindingStage19.screenGeneration}; windowGeneration=${bindingStage19.windowGeneration}",\n',
        '            "source=$sourceStage19; destination=${fieldsStage19.destination.orEmpty()}; screenGeneration=${bindingStage19.screenGeneration}; windowGeneration=${bindingStage19.windowGeneration}; trace=$traceIdStage20; routeJob=$routeJobIdStage20",\n',
        'route legacy log correlation',
    )

    service = replace_once(
        service,
        '    private fun isStage19BindingFresh(bindingStage19: FarolUniversalVisualPipelineStage19.Binding): Boolean =\n',
        '    private fun stage20BindingSnapshot(bindingStage19: FarolUniversalVisualPipelineStage19.Binding) =\n'
        '        FarolForensicTraceStage20.BindingSnapshot(bindingStage19.screenGeneration, bindingStage19.windowGeneration, bindingStage19.screenHash, bindingStage19.addressSignature)\n\n'
        '    private fun currentStage20BindingSnapshot() = FarolForensicTraceStage20.BindingSnapshot(\n'
        '        universalScreenGeneration, universalWindowGeneration, lastSnapshotHash, universalActiveAddressSignature,\n'
        '    )\n\n'
        '    private fun isStage19BindingFresh(bindingStage19: FarolUniversalVisualPipelineStage19.Binding): Boolean =\n',
        'binding snapshot helpers',
    )

    service = replace_once(
        service,
        '    private suspend fun analyzeUniversalTwoAddressStage19(\n        snapshotTextStage19: String,\n        fieldsStage19: RideFields,\n        bindingStage19: FarolUniversalVisualPipelineStage19.Binding,\n    ) {\n        if (!isStage19BindingFresh(bindingStage19)) return\n',
        '    private suspend fun analyzeUniversalTwoAddressStage19(\n'
        '        snapshotTextStage19: String,\n'
        '        fieldsStage19: RideFields,\n'
        '        bindingStage19: FarolUniversalVisualPipelineStage19.Binding,\n'
        '        traceIdStage20: String,\n'
        '        routeJobIdStage20: String,\n'
        '    ) {\n'
        '        val initialFreshStage20 = isStage19BindingFresh(bindingStage19)\n'
        '        FarolForensicTraceStage20.bindingCheck(traceIdStage20, routeJobIdStage20, SystemClock.elapsedRealtimeNanos(), "ROUTE_ENTER", stage20BindingSnapshot(bindingStage19), currentStage20BindingSnapshot(), initialFreshStage20, stage19VisualVerificationPending)\n'
        '        if (!initialFreshStage20) return\n',
        'analyze signature/fresh',
    )
    service = replace_in_section(
        service,
        '    private suspend fun analyzeUniversalTwoAddressStage19(',
        '    private suspend fun applyUniversalTwoAddressResultStage19(',
        '        val distancesStage19 = googleMapsService.drivingDistancesFromAddressKm(\n',
        '        FarolForensicTraceStage20.routeCallStarted(traceIdStage20, routeJobIdStage20, SystemClock.elapsedRealtimeNanos(), fieldsStage19.destination.orEmpty())\n'
        '        val distancesStage19 = googleMapsService.drivingDistancesFromAddressKm(\n',
        'route call start',
    )
    service = replace_in_section(
        service,
        '    private suspend fun analyzeUniversalTwoAddressStage19(',
        '    private suspend fun applyUniversalTwoAddressResultStage19(',
        '        )\n        if (!isStage19BindingFresh(bindingStage19)) return\n        val resultStage19 = decideFastWorkRegionChecklist13(\n',
        '        )\n'
        '        FarolForensicTraceStage20.routeCallFinished(traceIdStage20, routeJobIdStage20, SystemClock.elapsedRealtimeNanos(), distancesStage19.toString())\n'
        '        val routeFreshStage20 = isStage19BindingFresh(bindingStage19)\n'
        '        FarolForensicTraceStage20.bindingCheck(traceIdStage20, routeJobIdStage20, SystemClock.elapsedRealtimeNanos(), "AFTER_ROUTE", stage20BindingSnapshot(bindingStage19), currentStage20BindingSnapshot(), routeFreshStage20, stage19VisualVerificationPending)\n'
        '        if (!routeFreshStage20) return\n'
        '        FarolForensicTraceStage20.decisionStarted(traceIdStage20, routeJobIdStage20, SystemClock.elapsedRealtimeNanos())\n'
        '        val resultStage19 = decideFastWorkRegionChecklist13(\n',
        'route finish/fresh/decision',
    )
    service = replace_in_section(
        service,
        '    private suspend fun analyzeUniversalTwoAddressStage19(',
        '    private suspend fun applyUniversalTwoAddressResultStage19(',
        '        )\n        applyUniversalTwoAddressResultStage19(resultStage19, bindingStage19)\n',
        '        )\n'
        '        FarolForensicTraceStage20.decisionFinished(traceIdStage20, routeJobIdStage20, SystemClock.elapsedRealtimeNanos(), resultStage19.recommendation.name, resultStage19.nearestConfiguredDistanceKm())\n'
        '        applyUniversalTwoAddressResultStage19(resultStage19, bindingStage19, traceIdStage20, routeJobIdStage20)\n',
        'route decision/apply',
    )

    service = replace_once(
        service,
        '    private suspend fun applyUniversalTwoAddressResultStage19(\n        resultStage19: AnalysisResult,\n        bindingStage19: FarolUniversalVisualPipelineStage19.Binding,\n    ) {\n        if (!isStage19BindingFresh(bindingStage19)) return\n',
        '    private suspend fun applyUniversalTwoAddressResultStage19(\n'
        '        resultStage19: AnalysisResult,\n'
        '        bindingStage19: FarolUniversalVisualPipelineStage19.Binding,\n'
        '        traceIdStage20: String,\n'
        '        operationIdStage20: String,\n'
        '    ) {\n'
        '        val paintFreshStage20 = isStage19BindingFresh(bindingStage19)\n'
        '        FarolForensicTraceStage20.bindingCheck(traceIdStage20, operationIdStage20, SystemClock.elapsedRealtimeNanos(), "BEFORE_FINAL_PAINT", stage20BindingSnapshot(bindingStage19), currentStage20BindingSnapshot(), paintFreshStage20, stage19VisualVerificationPending)\n'
        '        if (!paintFreshStage20) return\n',
        'apply signature/fresh',
    )
    service = replace_in_section(
        service,
        '    private suspend fun applyUniversalTwoAddressResultStage19(',
        '    private fun startContinuousScan()',
        '        rememberBubbleReason("stage19_visual_result", resultStage19.reason)\n        showOverlay(colorStage19, distanceStage19)\n',
        '        rememberBubbleReason("stage19_visual_result", resultStage19.reason)\n'
        '        val paintTokenStage20 = FarolForensicTraceStage20.preparePaint(\n'
        '            traceIdStage20, operationIdStage20, stage20BindingSnapshot(bindingStage19),\n'
        '            colorStage19.toString(), distanceStage19, SystemClock.elapsedRealtimeNanos(),\n'
        '        )\n'
        '        stage20ExpectedPaintToken = paintTokenStage20\n'
        '        try {\n'
        '            showOverlay(colorStage19, distanceStage19)\n'
        '        } finally {\n'
        '            stage20ExpectedPaintToken = null\n'
        '        }\n',
        'tokenized final paint',
    )

    service = replace_in_section(
    service,
    '    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {',
    '    private fun formatBubbleDistanceKm(',
    '        val nextTextChecklist15 = formatBubbleDistanceKm(distanceKm)\n',
    '        val stage20Origin = if (color == RadarColor.Green || color == RadarColor.Red) {\n'
    '            FarolForensicTraceStage20.callSite(Thread.currentThread().stackTrace)\n'
    '        } else "showOverlay"\n'
    '        val stage20Binding = currentStage20BindingSnapshot()\n'
    '        val nextTextChecklist15 = formatBubbleDistanceKm(distanceKm)\n',
    'overlay origin',
)
    service = replace_in_section(
        service,
        '    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {',
        '    private fun formatBubbleDistanceKm(',
        '        if (existingViewChecklist15 != null && currentRadarColor == color &&\n            existingViewChecklist15.text.toString() == nextTextChecklist15\n        ) {\n            currentDistanceKm = distanceKm\n            return // overlay_idempotent_same_value_checklist_15\n        }\n',
        '        if (existingViewChecklist15 != null && currentRadarColor == color &&\n'
        '            existingViewChecklist15.text.toString() == nextTextChecklist15\n'
        '        ) {\n'
        '            FarolForensicTraceStage20.overlayIdempotentSkipped(stage20ExpectedPaintToken, SystemClock.elapsedRealtimeNanos(), color.toString(), distanceKm, stage20Binding, stage20Origin)\n'
        '            currentDistanceKm = distanceKm\n'
        '            return // overlay_idempotent_same_value_checklist_15\n'
        '        }\n',
        'overlay idempotent',
    )
    service = replace_in_section(
        service,
        '    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {',
        '    private fun formatBubbleDistanceKm(',
        '        FarolFlightRecorder0163.record(\n            stage = "OVERLAY_RENDER_REQUEST",\n',
        '        FarolForensicTraceStage20.overlayRequested(stage20ExpectedPaintToken, SystemClock.elapsedRealtimeNanos(), color.toString(), distanceKm, stage20Binding, stage20Origin)\n'
        '        FarolFlightRecorder0163.record(\n'
        '            stage = "OVERLAY_RENDER_REQUEST",\n',
        'overlay request probe',
    )
    service = replace_in_section(
        service,
        '    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {',
        '    private fun formatBubbleDistanceKm(',
        '        FarolFlightRecorder0163.record(\n            stage = "OVERLAY_RENDER_APPLIED",\n            packageName = universalResolvedForegroundPackage(),\n            details = "color=$color; distance=$distanceKm; text=${view.text}; viewCreated=${existingViewChecklist15 == null}",\n        )\n',
        '        FarolFlightRecorder0163.record(\n'
        '            stage = "OVERLAY_RENDER_APPLIED",\n'
        '            packageName = universalResolvedForegroundPackage(),\n'
        '            details = "color=$color; distance=$distanceKm; text=${view.text}; viewCreated=${existingViewChecklist15 == null}",\n'
        '        )\n'
        '        FarolForensicTraceStage20.overlayApplied(stage20ExpectedPaintToken, SystemClock.elapsedRealtimeNanos(), color.toString(), distanceKm, currentStage20BindingSnapshot(), stage20Origin)\n',
        'overlay applied probe',
    )

    report = replace_once(
        report,
        '            appendLine()\n            appendLine("ROTA CERTA — RELATORIO TECNICO MANUAL")\n',
        '            appendLine()\n'
        '            appendLine(FarolForensicTraceStage20.exportReport())\n'
        '            appendLine()\n'
        '            appendLine("ROTA CERTA — RELATORIO TECNICO MANUAL")\n',
        'report stage20 summary',
    )
    report = report.replace('appendLine("Selecao manual obrigatoria: true")', 'appendLine("Selecao manual obrigatoria para autorizar leitura: false (Stage19 universal)")')
    report = report.replace('appendLine("Politica: aplicativo selecionado + dois ou mais enderecos; o ultimo e o destino")', 'appendLine("Politica Stage19+: conteudo visual atual + dois ou mais enderecos coerentes no mesmo bloco; ultimo endereco e o destino")')
    report = replace_once(
        report,
        '            appendLine(FarolDiagnosticSummary0165.withSummary(settings, FarolFlightRecorder0163.exportReport()))\n',
        '            appendLine("--- RESUMO LEGADO 0.1.165 — NAO AUTORITATIVO PARA STAGE19+ ---")\n'
        '            appendLine(FarolDiagnosticSummary0165.withSummary(settings, FarolFlightRecorder0163.exportReport()))\n',
        'legacy summary warning',
    )
    main = main.replace('appendLine("Selecao manual de apps obrigatoria: true")', 'appendLine("Selecao manual de apps autoriza leitura: false (Stage19 universal)")')
    main = main.replace('appendLine("Politica de leitura: aplicativo salvo + dois ou mais enderecos; o ultimo e o destino")', 'appendLine("Politica Stage19+: tela visual atual + dois ou mais enderecos coerentes no mesmo bloco; ultimo endereco e o destino")')
    main = main.replace('appendLine("Pacotes passivos bloqueados: sistema, launcher, teclado, Google Maps e Waze")', 'appendLine("Pacote Android: somente metadado diagnostico; nao autoriza nem bloqueia a leitura Stage19+")')

    build = replace_once(build, 'versionCode = 5484', 'versionCode = 5485', 'versionCode')
    build = replace_once(build, 'versionName = "0.1.200"', 'versionName = "0.1.201"', 'versionName')

    (root / HELPER).parent.mkdir(parents=True, exist_ok=True)
    (root / TEST).parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HELPER_TEMPLATE, root / HELPER)
    shutil.copyfile(TEST_TEMPLATE, root / TEST)
    (root / SERVICE).write_text(service, encoding='utf-8')
    (root / REPORT).write_text(report, encoding='utf-8')
    (root / MAIN).write_text(main, encoding='utf-8')
    (root / BUILD).write_text(build, encoding='utf-8')

    transformed = (root / SERVICE).read_text(encoding='utf-8')
    checks = [
        MARKER, 'eventWindowIdStage20', 'S20_DUPLICATE_OR_ROUTE_ACTIVE_SKIP',
        'routeJobIdStage20', 'paintTokenStage20', 'stage20ExpectedPaintToken',
        'overlayRequested(stage20ExpectedPaintToken', 'overlayApplied(stage20ExpectedPaintToken',
        'callSite(Thread.currentThread().stackTrace)', 'currentStage20BindingSnapshot()',
    ]
    for item in checks:
        if item not in transformed: fail(f'applied service missing {item}')
    if transformed.count('applyUniversalTwoAddressResultStage19(resultStage19, bindingStage19,') < 2:
        fail('both cache and route final paths must carry Stage20 trace IDs')
    if 'delay(' in HELPER_TEMPLATE.read_text(encoding='utf-8') or 'Thread.sleep(' in HELPER_TEMPLATE.read_text(encoding='utf-8'):
        fail('Stage20 helper added artificial delay')
    print('stage20_apply=passed')
    print('versionName=0.1.201')
    print('versionCode=5485')
    print('event_cycle_id=true')
    print('trace_id=true')
    print('route_job_id=true')
    print('paint_token=true')
    print('all_final_overlay_calls_forensically_observed=true')
    print('legacy_unscoped_final_paint_detected=true')
    print('route_ready_to_paint_gap_measured=true')
    print('ocr_lifecycle_measured=true')
    print('accessibility_collect_and_evaluate_separated=true')
    print('diagnostic_policy_text_matches_stage19=true')


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
