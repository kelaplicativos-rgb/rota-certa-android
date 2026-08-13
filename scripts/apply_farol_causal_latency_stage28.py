#!/usr/bin/env python3
from __future__ import annotations

import argparse
import shutil
from pathlib import Path

SERVICE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt')
REPORT = Path('app/src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt')
BUILD = Path('app/build.gradle.kts')
PIPELINE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolUniversalVisualPipelineStage19.kt')
HELPER = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolCausalLatencyStage28.kt')
USAGE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/SelectedAppUsageStateStage28.kt')
TEST = Path('app/src/test/java/br/com/mapeiaia/rotacerta/FarolCausalLatencyStage28Test.kt')
PATCH_ROOT = Path(__file__).resolve().parents[1]
HELPER_TEMPLATE = PATCH_ROOT / 'stage28/FarolCausalLatencyStage28.kt'
USAGE_TEMPLATE = PATCH_ROOT / 'stage28/SelectedAppUsageStateStage28.kt'
TEST_TEMPLATE = PATCH_ROOT / 'stage28/FarolCausalLatencyStage28Test.kt'
MARKER = 'FAROL_CAUSAL_LATENCY_STALE_ACTIVATION_STAGE28'


def fail(message: str) -> None:
    raise SystemExit(message)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        fail(f'Stage28 anchor {label}: expected 1, found {count}')
    return text.replace(old, new, 1)


def replace_section(text: str, start: str, end: str, replacement: str, label: str) -> str:
    a = text.find(start)
    b = text.find(end, a + len(start))
    if a < 0 or b <= a:
        fail(f'Stage28 section {label}: markers not found')
    return text[:a] + replacement + text[b:]


def self_test() -> None:
    for p in (HELPER_TEMPLATE, USAGE_TEMPLATE, TEST_TEMPLATE):
        if not p.is_file(): fail(f'missing Stage28 support file: {p}')
    helper = HELPER_TEMPLATE.read_text(encoding='utf-8')
    usage = USAGE_TEMPLATE.read_text(encoding='utf-8')
    tests = TEST_TEMPLATE.read_text(encoding='utf-8')
    required = (
        MARKER, 'OLD_PAINT_UI_REVOKED_BEFORE_HEAVY_WORK_STAGE28',
        'TWO_CURRENT_ADDRESSES_ANY_VISIBLE_PACKAGE_STAGE28', 'EVENT_SOURCE_SUBTREE_BEFORE_ALL_WINDOWS_STAGE28',
        'ONE_OCR_PER_VISUAL_GENERATION_STAGE28', 'ONE_ROUTE_PER_CURRENT_DESTINATION_STAGE28',
        'O1_GENERATION_TOKEN_STALE_GUARD_STAGE28', 'REAL_GOOGLE_ROUTE_PRESERVED_STAGE28',
        'trimNarrativeSuffix', 'VisualGate', 'RouteGate', 'WorkCoordinator',
    )
    for item in required:
        if item not in helper: fail(f'Stage28 helper missing {item}')
    for item in ('runningAppProcesses', 'IMPORTANCE_CACHED', 'USAGE_HISTORY_NEVER_KEEPS_READING_ON_STAGE28'):
        if item not in usage: fail(f'Stage28 usage witness missing {item}')
    if 'UsageStatsManager' in usage or 'queryEvents' in usage or 'lastTimeUsed' in usage:
        fail('Stage28 current activation authority cannot depend on UsageStats history')
    if tests.count('@Test') != 50:
        fail(f'expected exactly 50 Stage28 tests, found {tests.count("@Test")}')
    mandatory = (
        'noSelectedAppActiveMeansOff','offMeansIdleGrayContract','offMeansKmNull','oneSelectedActiveMeansOn',
        'twoSelectedActiveMeansOn','closeOneOfTwoRemainsOn','closeLastMeansOff','usageHistoryCannotKeepCachedProcessActive',
        'reopenSelectedMeansOn','activationGenerationChangesOnOff','oldWorkCannotPaintAfterOff','oldOcrCannotPaintAfterOff',
        'oldCacheCannotPaintAfterOff','oldGoogleCannotPaintAfterOff','redAThenBRevokesAImmediately','greenAThenContentGoneClears',
        'sameContentNoiseDoesNotFlicker','repeatedEventDoesNotHeavyCollect','sameGenerationDoesNotRelaunchOcr',
        'sameDestinationDoesNotRelaunchGoogle','newDestinationGetsNewIdentity','googleAFinishesDuringBIsStale',
        'ocrAFinishesDuringBIsStale','cacheAArrivesDuringBIsStale','scheduledAIsDiscardedDuringB','ownOverlayIsIgnored',
        'eventStormIsCoalescedBeforeHeavyPath','twoAddressesOnWhatsAppAreProcessable','twoAddressesOnChatGptAreProcessable',
        'twoAddressesOnHomeOverlayAreProcessable','addressesInDifferentCitiesAreNotBlocked','veryLongRouteIsNotBlockedByPolicy',
        'narrativeSuffixIsTrimmedFromDestination','trailingNarrativeDoesNotBecomeDestination','cardModelIsNotRequired',
        'visualPackageUber99IndriveIsNotRequired','selectedAppOnlyControlsOnOff','inDrivePackageCanActivate','app99PackageCanActivate',
        'uberPackageCanActivate','realGoogleContractRemainsMandatory','noGoogleRouteMeansNoInventedKmInCoordinator',
        'exactCurrentCacheCanPaintImmediately','oldCacheIsForbiddenAfterVisualChange','readingOffMeansZeroHeavyCollection',
        'duplicateEventMeansZeroHeavyCollection','ownOverlayMeansZeroHeavyCollection','staleProtectionIsO1GenerationTokenCheck',
    )
    for name in mandatory:
        if name not in tests: fail(f'Stage28 mandatory test missing {name}')
    forbidden = ('Thread.sleep(', 'SystemClock.sleep(', 'delay(250', 'delay(500', 'MAX_DISTANCE', 'MAX_ROUTE_KM')
    for value in forbidden:
        if value in helper or value in usage: fail(f'Stage28 forbidden critical-path authority: {value}')
    print('stage28_self_test=passed')
    print('stage28_test_methods=50')
    print('activation_authority=current_non_cached_process')
    print('usage_history_authority=false')
    print('universal_visual_package_authority=false')
    print('stale_guard=o1_generation_token')


def apply(root: Path) -> None:
    for p in (SERVICE, REPORT, BUILD, PIPELINE):
        if not (root / p).is_file(): fail(f'Stage28 requires materialized Stage26 source: {p}')
    service = (root / SERVICE).read_text(encoding='utf-8')
    report = (root / REPORT).read_text(encoding='utf-8')
    build = (root / BUILD).read_text(encoding='utf-8')
    pipeline = (root / PIPELINE).read_text(encoding='utf-8')
    if 'FAROL_READING_ACTIVATION_STAGE26' not in service:
        fail('Stage28 must be applied after exact Stage26 materialization')
    if MARKER in service or (root / HELPER).exists(): fail('Stage28 already appears applied')
    if 'versionCode = 5488' not in build or 'versionName = "0.1.204"' not in build:
        fail('Stage28 requires exact Stage26 0.1.204/5488 baseline')

    shutil.copyfile(HELPER_TEMPLATE, root / HELPER)
    shutil.copyfile(USAGE_TEMPLATE, root / USAGE)
    shutil.copyfile(TEST_TEMPLATE, root / TEST)

    # Delimit obvious prose after an otherwise plausible address. This is intentionally permissive:
    # it does not check package, city, state, model, geography or distance.
    pipeline = replace_once(
        pipeline,
        '.map(DestinationAddressIdentityPolicy::cleanDisplayAddress)\n                    .filter(String::isNotBlank)',
        '.map(DestinationAddressIdentityPolicy::cleanDisplayAddress)\n                    .map(FarolCausalLatencyStage28::trimNarrativeSuffix)\n                    .filter(String::isNotBlank)',
        'narrative address delimitation',
    )

    service = replace_once(
        service,
        '    private lateinit var stage26UsageState: SelectedAppUsageStateStage26\n',
        '    private lateinit var stage28UsageState: SelectedAppUsageStateStage28\n'
        '    private val stage28RouteGate = FarolCausalLatencyStage28.RouteGate()\n'
        '    private var stage28LastActivationEnabled = false\n'
        '    // FAROL_CAUSAL_LATENCY_STALE_ACTIVATION_STAGE28 — current execution + cheap visual mutation authority\n',
        'Stage28 service state',
    )
    service = replace_once(
        service,
        '        stage26UsageState = SelectedAppUsageStateStage26(applicationContext)\n',
        '        stage28UsageState = SelectedAppUsageStateStage28(applicationContext)\n',
        'current execution witness init',
    )

    refresh = r'''    private fun refreshReadingActivationStage26(
        eventPackageStage26: String?,
        eventTypeStage26: Int,
    ): FarolReadingActivationStage26.ActivationSnapshot {
        val startedStage28 = SystemClock.elapsedRealtimeNanos()
        val selectedStage28 = SelectedRideAppStore.read(applicationContext)
        stage26ReadingActivation.updateSelection(selectedStage28)
        val executionStage28 = stage28UsageState.readCurrentExecution(selectedStage28)
        stage26ReadingActivation.setUsageAccess(executionStage28.usageAccessGranted)
        stage26ReadingActivation.replaceUsageState(
            FarolCausalLatencyStage28.currentExecutionEvents(executionStage28.activeSelectedPackages),
        )
        stage26UsageInitialized = true
        val snapshotStage28 = stage26ReadingActivation.snapshot()
        if (snapshotStage28.enabled != stage28LastActivationEnabled) {
            FarolCausalLatencyStage28.Metrics.increment(if (snapshotStage28.enabled) "activationOn" else "activationOff")
            stage28LastActivationEnabled = snapshotStage28.enabled
        }
        FarolCausalLatencyStage28.Metrics.setGauge("selectedAppsActiveCount", snapshotStage28.selectedAppsActiveCount.toLong())
        FarolCausalLatencyStage28.Metrics.setGauge("activationGeneration", snapshotStage28.generation)
        FarolCausalLatencyStage28.Metrics.sample(
            "eventToActivationState",
            SystemClock.elapsedRealtimeNanos() - startedStage28,
        )
        return snapshotStage28
    }

'''
    service = replace_section(
        service,
        '    private fun refreshReadingActivationStage26(',
        '    private fun applyReadingOffStage26(',
        refresh,
        'live activation refresh',
    )

    off_start = service.index('    private fun applyReadingOffStage26(')
    off_end = service.index('    private fun isReadingActivationGenerationFreshStage26(', off_start)
    off = service[off_start:off_end]
    off = off.replace('currentRadarColor == RadarColor.Orange', 'currentRadarColor == RadarColor.Idle')
    off = off.replace('currentRadarColor != RadarColor.Orange', 'currentRadarColor != RadarColor.Idle')
    off = off.replace('showOverlay(RadarColor.Orange, distanceKm = null)', 'showOverlay(RadarColor.Idle, distanceKm = null)')
    off = off.replace(
        '        stage26PreCollectGate.invalidate()\n',
        '        stage26PreCollectGate.invalidate()\n'
        '        stage28RouteGate.invalidateExcept(-1L, -1L)\n'
        '        FarolCausalLatencyStage28.Metrics.increment("workCancelledOnReadingOff")\n'
        '        FarolCausalLatencyStage28.Metrics.setGauge("selectedAppsActiveCount", 0L)\n',
        1,
    )
    service = service[:off_start] + off + service[off_end:]

    cheap = r'''    private fun buildCheapVisualSignalStage26(
        eventPackageStage26: String?,
        eventTypeStage26: Int,
        eventWindowIdStage26: Int,
        eventStage26: AccessibilityEvent,
    ): FarolReadingActivationStage26.CheapVisualSignal {
        val sourceStage28 = runCatching { eventStage26.source }.getOrNull()
        val sourcePackageStage28 = normalizePackageName(runCatching { sourceStage28?.packageName?.toString() }.getOrNull())
        val eventPackageNormalizedStage28 = normalizePackageName(eventPackageStage26)
        val ownPackageStage28 = normalizePackageName(packageName)
        val boundsStage28 = Rect()
        runCatching { sourceStage28?.getBoundsInScreen(boundsStage28) }
        val sourceSlotStage28 = buildString {
            append(eventWindowIdStage26); append(':')
            append(runCatching { sourceStage28?.viewIdResourceName }.getOrNull().orEmpty()); append(':')
            append(boundsStage28.left); append(':'); append(boundsStage28.top); append(':')
            append(boundsStage28.right); append(':'); append(boundsStage28.bottom)
        }
        val relevantStage28 = LinkedHashSet<String>(6)
        fun addStage28(valueStage28: CharSequence?) {
            val textStage28 = valueStage28?.toString()?.trim()?.takeIf(String::isNotBlank) ?: return
            if (FarolVisualIdentityStage23.countAddressLeads(textStage28) > 0) relevantStage28 += textStage28
        }
        addStage28(runCatching { sourceStage28?.text }.getOrNull())
        addStage28(runCatching { sourceStage28?.contentDescription }.getOrNull())
        runCatching { eventStage26.text }.getOrDefault(emptyList()).take(6).forEach(::addStage28)
        // One local parent only. The source fast-path below gets first chance before any all-window fallback.
        if (relevantStage28.size < 2) {
            val parentStage28 = runCatching { sourceStage28?.parent }.getOrNull()
            addStage28(runCatching { parentStage28?.text }.getOrNull())
            addStage28(runCatching { parentStage28?.contentDescription }.getOrNull())
            val childrenStage28 = runCatching { parentStage28?.childCount }.getOrDefault(0).coerceIn(0, 8)
            for (indexStage28 in 0 until childrenStage28) {
                val childStage28 = runCatching { parentStage28?.getChild(indexStage28) }.getOrNull() ?: continue
                addStage28(runCatching { childStage28.text }.getOrNull())
                addStage28(runCatching { childStage28.contentDescription }.getOrNull())
                if (relevantStage28.size >= 4) break
            }
        }
        val ownEventStage28 = eventPackageNormalizedStage28 == ownPackageStage28 && sourcePackageStage28 == ownPackageStage28
        val ownOverlayStage28 = ownEventStage28 && relevantStage28.isEmpty()
        return FarolReadingActivationStage26.CheapVisualSignal(
            ownOverlay = ownOverlayStage28,
            windowSignature = "$eventWindowIdStage26:${sourcePackageStage28.orEmpty()}",
            sourceText = relevantStage28.sorted().joinToString("\n"),
            sourceSlot = sourceSlotStage28,
            contentChangeTypes = runCatching { eventStage26.contentChangeTypes }.getOrDefault(0),
        )
    }

    private fun collectUniversalAccessibilitySnapshotStage28(eventStage28: AccessibilityEvent): Stage26AccessibilitySnapshot {
        val sourceStage28 = runCatching { eventStage28.source }.getOrNull()
        val candidatesStage28 = ArrayList<AccessibilityNodeInfo>(3)
        var cursorStage28 = sourceStage28
        repeat(3) {
            val currentStage28 = cursorStage28 ?: return@repeat
            if (candidatesStage28.none { it === currentStage28 }) candidatesStage28 += currentStage28
            cursorStage28 = runCatching { currentStage28.parent }.getOrNull()
        }
        for ((indexStage28, rootStage28) in candidatesStage28.withIndex()) {
            val packageStage28 = safeNodePackageName0185(rootStage28) ?: "visual.unknown"
            if (normalizePackageName(packageStage28) == normalizePackageName(packageName)) continue
            val budgetStage28 = intArrayOf(0)
            val windowStage28 = runCatching { eventStage28.windowId }.getOrDefault(-1)
            val treeStage28 = collectCompactSubtreeStage26(
                rootStage28,
                "stage28-source:$windowStage28:$indexStage28",
                null,
                1,
                packageStage28,
                windowStage28,
                Int.MAX_VALUE - 1,
                budgetStage28,
            )
            val completeStage28 = treeStage28.completeBlock ?: continue
            val blockStage28 = FarolUniversalVisualPipelineStage19.VisualBlock(
                id = completeStage28.id,
                parentId = completeStage28.parentId,
                metadataPackageName = completeStage28.packageName,
                windowId = completeStage28.windowId,
                windowLayer = completeStage28.windowLayer,
                depth = completeStage28.depth,
                text = completeStage28.text,
                source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
                left = completeStage28.left,
                top = completeStage28.top,
                right = completeStage28.right,
                bottom = completeStage28.bottom,
                syntheticRoot = false,
            )
            val seedStage28 = FarolVisualIdentityStage23.VisualSeed(
                windowId = blockStage28.windowId,
                windowLayer = blockStage28.windowLayer,
                text = blockStage28.text,
                left = blockStage28.left,
                top = blockStage28.top,
                right = blockStage28.right,
                bottom = blockStage28.bottom,
                syntheticRoot = false,
            )
            val snapshotStage28 = FarolVisualIdentityStage23.snapshot(sequenceOf(seedStage28))
            val statsStage28 = FarolVisualIdentityStage23.CollectionStats(
                visibleWindowsTotal = 1,
                windowsTraversed = 1,
                windowsSkippedSelf = 0,
                windowsSkippedLowerLayer = 0,
                blocksVisited = budgetStage28[0],
                blocksEmitted = 1,
                earlyExitWindow = windowStage28,
                earlyExitReason = "stage28_event_source_subtree_fast_path",
                visualSnapshotHash = snapshotStage28.hash,
            )
            return Stage26AccessibilitySnapshot(
                listOf(blockStage28), snapshotStage28, statsStage28, 1, treeStage28.duplicateSubtreesAvoided,
            )
        }
        return collectUniversalAccessibilitySnapshotStage26()
    }

'''
    service = replace_section(
        service,
        '    private fun buildCheapVisualSignalStage26(',
        '    private fun invalidateOldVisualBeforeCollectStage26(',
        cheap,
        'cheap signal and source fast path',
    )

    handler_start = service.index('    private fun handleUniversalVisualEventStage19(')
    handler_end = service.index('    private fun refreshReadingActivationStage26(', handler_start)
    handler = service[handler_start:handler_end]
    handler = replace_once(
        handler,
        '        val collectionStage26 = collectUniversalAccessibilitySnapshotStage26()\n',
        '        val collectionStage26 = collectUniversalAccessibilitySnapshotStage28(eventStage26)\n',
        'direct event source fast collector',
    )
    handler = handler.replace(
        '        if (!activationStage26.enabled) {\n',
        '        if (!activationStage26.enabled) {\n            FarolCausalLatencyStage28.Metrics.increment("eventsReceived")\n            FarolCausalLatencyStage28.Metrics.increment("eventsRejectedReadingOff")\n            FarolCausalLatencyStage28.Metrics.increment("heavyCollectionsAvoided")\n',
        1,
    )
    handler = handler.replace(
        '        if (bubbleGestureActive) {\n',
        '        if (bubbleGestureActive) {\n            FarolCausalLatencyStage28.Metrics.increment("eventsReceived")\n            FarolCausalLatencyStage28.Metrics.increment("ownOverlayEventsIgnored")\n            FarolCausalLatencyStage28.Metrics.increment("heavyCollectionsAvoided")\n',
        1,
    )
    handler = handler.replace(
        '        if (!admissionStage26.heavyCollect) return true\n',
        '        if (!admissionStage26.heavyCollect) {\n'
        '            FarolCausalLatencyStage28.Metrics.increment("eventsCoalesced")\n'
        '            FarolCausalLatencyStage28.Metrics.increment("visualIdentityRepeated")\n'
        '            FarolCausalLatencyStage28.Metrics.increment("heavyCollectionsAvoided")\n'
        '            return true\n'
        '        }\n'
        '        FarolCausalLatencyStage28.Metrics.increment("visualIdentityChanged")\n'
        '        FarolCausalLatencyStage28.Metrics.increment("heavyCollectionsStarted")\n',
        1,
    )
    service = service[:handler_start] + handler + service[handler_end:]

    invalidate_start = service.index('    private fun invalidateOldVisualBeforeCollectStage26(')
    invalidate_end = service.index('    private fun collectUniversalAccessibilityBlocksStage19()', invalidate_start)
    invalidate = service[invalidate_start:invalidate_end]
    invalidate = invalidate.replace(
        '        showOverlay(RadarColor.Orange, distanceKm = null)\n',
        '        showOverlay(RadarColor.Orange, distanceKm = null)\n'
        '        stage28RouteGate.invalidateExcept(stage26ReadingActivation.snapshot().generation, newGenerationStage26)\n'
        '        FarolCausalLatencyStage28.Metrics.increment("oldPaintInvalidated")\n'
        '        FarolCausalLatencyStage28.Metrics.sample("eventToOldPaintInvalidated", SystemClock.elapsedRealtimeNanos() - eventStartedNsStage26)\n',
        1,
    )
    service = service[:invalidate_start] + invalidate + service[invalidate_end:]

    # Exact-cache path remains first and receives a Stage28 metric; Google remains the real route authority.
    process_start = service.index('    private suspend fun processUniversalVisualStage19(')
    process_end = service.index('    private fun stage20BindingSnapshot(', process_start)
    process = service[process_start:process_end]
    process = process.replace(
        '        if (cachedStage19 != null) {\n',
        '        if (cachedStage19 != null) {\n            FarolCausalLatencyStage28.Metrics.increment("routeCacheHits")\n',
        1,
    )
    service = service[:process_start] + process + service[process_end:]

    route_start_anchor = '        val routeStartedNsStage26 = SystemClock.elapsedRealtimeNanos()\n'
    route_start_new = (
        '        val routeKeyStage28 = FarolCausalLatencyStage28.RouteKey(\n'
        '            stage26CandidateActivationGeneration, stage26CurrentVisualGeneration, fieldsStage19.destination.orEmpty(),\n'
        '        )\n'
        '        if (!stage28RouteGate.begin(routeKeyStage28)) return\n'
        '        val routeStartedNsStage26 = SystemClock.elapsedRealtimeNanos()\n'
    )
    service = replace_once(service, route_start_anchor, route_start_new, 'route dedup begin')
    service = replace_once(
        service,
        '        stage26RouteResponseNs = routeEndedNsStage26\n',
        '        stage26RouteResponseNs = routeEndedNsStage26\n'
        '        stage28RouteGate.finish(routeKeyStage28)\n'
        '        FarolCausalLatencyStage28.Metrics.increment("routeRequests")\n',
        'route dedup finish',
    )

    report = replace_once(
        report,
        '            appendLine(FarolReadingActivationStage26.Metrics.exportReport())\n',
        '            appendLine(FarolReadingActivationStage26.Metrics.exportReport())\n'
        '            appendLine()\n'
        '            appendLine(FarolCausalLatencyStage28.Metrics.exportReport())\n',
        'Stage28 manual metrics',
    )

    build = replace_once(build, 'versionCode = 5488', 'versionCode = 5489', 'versionCode')
    build = replace_once(build, 'versionName = "0.1.204"', 'versionName = "0.1.205"', 'versionName')

    (root / SERVICE).write_text(service, encoding='utf-8')
    (root / REPORT).write_text(report, encoding='utf-8')
    (root / BUILD).write_text(build, encoding='utf-8')
    (root / PIPELINE).write_text(pipeline, encoding='utf-8')

    transformed = (root / SERVICE).read_text(encoding='utf-8')
    checks = (
        MARKER, 'SelectedAppUsageStateStage28', 'readCurrentExecution', 'RadarColor.Idle',
        'collectUniversalAccessibilitySnapshotStage28(eventStage26)', 'stage28_event_source_subtree_fast_path',
        'stage28RouteGate.begin', 'stage28RouteGate.finish', 'isReadingActivationGenerationFreshStage26',
        'S21_SEMANTIC_REJECT_BEFORE_CACHE_ROUTE', 'FarolForensicTraceStage20.preparePaint',
        'cachedDrivingDistancesFromAddressKm', 'drivingDistancesFromAddressKm',
    )
    for item in checks:
        if item not in transformed: fail(f'applied Stage28 service missing {item}')
    if 'SelectedAppUsageStateStage26(applicationContext)' in transformed:
        fail('Stage28 runtime still instantiates historical Stage26 usage authority')
    refresh_slice = transformed[transformed.index('    private fun refreshReadingActivationStage26('):transformed.index('    private fun applyReadingOffStage26(')]
    if 'readSelectedActivity' in refresh_slice or 'UsageStats' in refresh_slice:
        fail('Stage28 activation refresh still depends on usage history')
    off_slice = transformed[transformed.index('    private fun applyReadingOffStage26('):transformed.index('    private fun isReadingActivationGenerationFreshStage26(')]
    if 'showOverlay(RadarColor.Idle, distanceKm = null)' not in off_slice:
        fail('Stage28 OFF must paint true Idle/gray')
    if 'showOverlay(RadarColor.Orange, distanceKm = null)' in off_slice:
        fail('Stage28 OFF cannot paint waiting/orange')
    direct_slice = transformed[transformed.index('    private fun handleUniversalVisualEventStage19('):transformed.index('    private fun refreshReadingActivationStage26(')]
    if direct_slice.index('invalidateOldVisualBeforeCollectStage26') > direct_slice.index('collectUniversalAccessibilitySnapshotStage28'):
        fail('Stage28 old paint must be revoked before source/global collection')
    if 'windows.sortedByDescending' in transformed[transformed.index('    private fun buildCheapVisualSignalStage26('):transformed.index('    private fun collectUniversalAccessibilitySnapshotStage28(')]:
        fail('Stage28 cheap visual mutation detector cannot enumerate all windows')
    if 'versionCode = 5489' not in (root / BUILD).read_text(encoding='utf-8') or 'versionName = "0.1.205"' not in (root / BUILD).read_text(encoding='utf-8'):
        fail('Stage28 version mismatch')
    print('stage28_apply=passed')
    print('versionName=0.1.205')
    print('versionCode=5489')
    print('activation_authority=current_non_cached_process')
    print('off_visual_state=RadarColor.Idle')
    print('event_source_fast_path=true')
    print('all_windows_before_mutation=false')
    print('route_deduplicated=true')
    print('google_real_preserved=true')
    print('package_visual_authority=false')
    print('narrative_address_delimitation=true')


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
