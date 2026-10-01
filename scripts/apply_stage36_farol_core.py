#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'
HELPER = Path(__file__).resolve().parents[1] / 'stage36/FarolRuntimeAuthorityStage36.kt'

if not HELPER.exists():
    raise SystemExit('missing Stage36 helper')
(PKG / 'FarolRuntimeAuthorityStage36.kt').write_text(HELPER.read_text())


def once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)


def between(text, start, end, replacement, label):
    try:
        a = text.index(start)
        b = text.index(end, a)
    except ValueError as exc:
        raise SystemExit(f'{label}: anchor missing: {exc}')
    return text[:a] + replacement + text[b:]


s = SERVICE.read_text()

s = once(
    s,
    '    private lateinit var stage30PresenceAuthority: FarolPresenceAuthorityStage30.Authority\n',
    '    private lateinit var stage30PresenceAuthority: FarolPresenceAuthorityStage30.Authority\n'
    '    private lateinit var stage36RuntimeAuthority: FarolRuntimeAuthorityStage36.Authority\n',
    'stage36 authority field',
)
s = once(
    s,
    '    private val stage26BindingActivationGeneration = LinkedHashMap<String, Long>()\n',
    '    private val stage36BindingWorkToken = LinkedHashMap<String, FarolRuntimeAuthorityStage36.WorkToken>()\n',
    'binding token map',
)
s = once(
    s,
    '        stage30PresenceAuthority = FarolPresenceAuthorityStage30.Authority(stage30PresenceState.sessionStartWallMillis)\n',
    '        stage30PresenceAuthority = FarolPresenceAuthorityStage30.Authority(stage30PresenceState.sessionStartWallMillis)\n'
    '        stage36RuntimeAuthority = FarolRuntimeAuthorityStage36.Authority(stage30PresenceState.sessionStartWallMillis)\n',
    'stage36 authority init',
)

refresh = r'''    private fun refreshReadingActivationStage26(
        eventPackageStage26: String?,
        eventTypeStage26: Int,
    ): FarolReadingActivationStage26.ActivationSnapshot {
        val startedStage36 = SystemClock.elapsedRealtimeNanos()
        val nowWallStage36 = System.currentTimeMillis()
        val selectedStage36 = SelectedRideAppStore.read(applicationContext)
        val selectedNormalizedStage36 = selectedStage36.mapNotNull(FarolRuntimeAuthorityStage36::normalizePackage).toSet()

        // Stage30 is preserved as forensic shadow. Stage36 alone drives functional ON/OFF.
        stage30PresenceAuthority.updateSelection(selectedStage36)
        stage36RuntimeAuthority.updateSelection(selectedStage36)
        stage26ReadingActivation.updateSelection(selectedStage36)
        val usageAccessStage36 = stage30PresenceState.hasUsageAccess()
        stage30PresenceAuthority.setUsageAccess(usageAccessStage36)
        stage36RuntimeAuthority.setUsageAccess(usageAccessStage36)

        val eventPackageNormalizedStage36 = FarolRuntimeAuthorityStage36.normalizePackage(eventPackageStage26)
        val selectedEventStage36 = eventPackageNormalizedStage36 != null && eventPackageNormalizedStage36 in selectedNormalizedStage36
        val windowBoundaryStage36 = eventTypeStage26 == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val windowsChangedStage36 = eventTypeStage26 == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        val wasEnabledStage36 = stage36RuntimeAuthority.snapshot().enabled

        if (windowBoundaryStage36) {
            stage30PresenceAuthority.observeWindowBoundary(eventPackageNormalizedStage36)
            stage36RuntimeAuthority.observeWindowBoundary(eventPackageNormalizedStage36)
        }
        if (selectedEventStage36) {
            stage30PresenceAuthority.observeAccessibility(eventPackageNormalizedStage36, eventTypeStage26, nowWallStage36)
            stage36RuntimeAuthority.observeAccessibility(eventPackageNormalizedStage36)
        }

        val refreshUsageStage36 = windowBoundaryStage36 || windowsChangedStage36 || (selectedEventStage36 && !wasEnabledStage36)
        if (usageAccessStage36 && refreshUsageStage36) {
            val evidenceStage36 = stage30PresenceState.readIncrementalUsage(selectedStage36, nowWallStage36)
            stage30PresenceAuthority.applyUsageEvidence(evidenceStage36)
            stage36RuntimeAuthority.applyUsageEvidence(evidenceStage36)
        }

        val runtimeStage36 = stage36RuntimeAuthority.snapshot()
        stage26ReadingActivation.setUsageAccess(runtimeStage36.usageAccessGranted)
        stage26ReadingActivation.replaceUsageState(
            FarolCausalLatencyStage28.currentExecutionEvents(runtimeStage36.authoritativeActivePackages),
        )
        stage26UsageInitialized = true
        val snapshotStage36 = stage26ReadingActivation.snapshot()

        if (snapshotStage36.enabled != stage28LastActivationEnabled) {
            FarolCausalLatencyStage28.Metrics.increment(if (snapshotStage36.enabled) "activationOn" else "activationOff")
            stage28LastActivationEnabled = snapshotStage36.enabled
        }
        FarolCausalLatencyStage28.Metrics.setGauge("selectedAppsActiveCount", snapshotStage36.selectedAppsActiveCount.toLong())
        FarolCausalLatencyStage28.Metrics.setGauge("activationGeneration", runtimeStage36.readingEpoch)
        FarolCausalLatencyStage28.Metrics.sample(
            "eventToActivationState",
            SystemClock.elapsedRealtimeNanos() - startedStage36,
        )

        if (selectedEventStage36 || windowBoundaryStage36 || windowsChangedStage36) {
            // Stage30 runningAppProcesses stays shadow-only exactly as physically proved on Android 16.
            stage30PresenceAuthority.updateProcessShadow(stage30PresenceState.readProcessShadow(selectedStage36))
        }
        return snapshotStage36
    }

'''
s = between(
    s,
    '    private fun refreshReadingActivationStage26(',
    '    private fun applyReadingOffStage26(',
    refresh,
    'refresh reading activation',
)

s = once(
    s,
    '        stage32SemanticGate.markReadingOff()\n',
    '        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.markExplicitOff("stage26_apply_reading_off")\n'
    '        stage32SemanticGate.markReadingOff()\n',
    'true reading off hook',
)

fresh_activation = r'''    private fun isReadingActivationGenerationFreshStage26(expectedGenerationStage26: Long): Boolean {
        @Suppress("UNUSED_VARIABLE") val legacyGenerationStage36 = expectedGenerationStage26
        if (!::stage36RuntimeAuthority.isInitialized) return false
        val runtimeStage36 = stage36RuntimeAuthority.snapshot()
        return runtimeStage36.enabled && runtimeStage36.usageAccessGranted && WorkModePolicy0162.isEnabled(currentSettings)
    }

'''
s = between(
    s,
    '    private fun isReadingActivationGenerationFreshStage26(',
    '    private fun buildSemanticSignalStage32(',
    fresh_activation,
    'legacy activation generation demotion',
)

s = once(
    s,
    '        if (bubbleGestureActive) {\n',
    '        stage36RuntimeAuthority.observeVisualEvidence()\n'
    '        if (bubbleGestureActive) {\n',
    'raw evidence lease observe',
)

invalidate = r'''    private fun invalidateOldVisualBeforeCollectStage26(newGenerationStage26: Long, eventStartedNsStage26: Long) {
        // Stage36: raw visual mutation revokes the visible old paint immediately, but it is not
        // semantic proof that the current card/destination changed. OCR/route survive until a new
        // final destination, explicit visual disappearance, or true reading OFF proves staleness.
        universalScreenGeneration += 1L
        universalWindowGeneration += 1L
        analyzeJob?.cancel(); analyzeJob = null
        screenshotFallbackJob127?.cancel(); screenshotFallbackJob127 = null
        lastAnalyzedHash = null
        currentDistanceKm = null
        stage19VisualVerificationPending = true
        if (screenshotInProgress.get()) FarolSemanticCardStage32.Metrics.increment("ocrPreservedAcrossRawMutation")
        if (universalRouteJob?.isActive == true) FarolRuntimeAuthorityStage36.Metrics.increment("routePreservedAcrossRawMutation")
        fastFarolStartedAtChecklist13 = System.currentTimeMillis()
        rememberBubbleReason("stage36_visual_verification", "Mudança visual detectada; preservando trabalho do mesmo card até prova semântica.")
        showOverlay(RadarColor.Orange, distanceKm = null)
        FarolCausalLatencyStage28.Metrics.increment("oldPaintInvalidated")
        FarolCausalLatencyStage28.Metrics.sample(
            "eventToOldPaintInvalidated",
            SystemClock.elapsedRealtimeNanos() - eventStartedNsStage26,
        )
        FarolReadingActivationStage26.Metrics.sample(
            "eventToOldPaintInvalidated",
            SystemClock.elapsedRealtimeNanos() - eventStartedNsStage26,
        )
        stage26CurrentVisualGeneration = newGenerationStage26
    }

'''
s = between(
    s,
    '    private fun invalidateOldVisualBeforeCollectStage26(',
    '    private fun collectUniversalAccessibilityBlocksStage19()',
    invalidate,
    'raw visual invalidation',
)

ocr_fresh = r'''    private fun isStage36WorkFresh(tokenStage36: FarolRuntimeAuthorityStage36.WorkToken): Boolean =
        serviceReady && WorkModePolicy0162.isEnabled(currentSettings) &&
            ::stage36RuntimeAuthority.isInitialized && stage36RuntimeAuthority.isFresh(tokenStage36)

'''
s = between(
    s,
    '    private fun isStage23OcrDemandFresh(',
    '    private fun requestUniversalScreenshotStage19(',
    ocr_fresh,
    'ocr freshness authority',
)

s = once(
    s,
    '        val tokenStage23 = requestStage23.token\n',
    '        val tokenStage23 = requestStage23.token\n'
    '        val workTokenStage36 = stage36RuntimeAuthority.captureWorkToken() ?: run {\n'
    '            stage23OcrGate.complete(tokenStage23)\n'
    '            stage32ScreenshotRateGate.complete(semanticStage32.generation, false)\n'
    '            return\n'
    '        }\n',
    'capture ocr work token',
)
s = s.replace(
    'isStage23OcrDemandFresh(tokenStage23, demandStage23, serialStage19)',
    'isStage36WorkFresh(workTokenStage36)',
)
if 'isStage23OcrDemandFresh(' in s:
    raise SystemExit('legacy Stage23 OCR freshness call remains')

s = once(
    s,
    '        if (!isReadingActivationGenerationFreshStage26(stage26CandidateActivationGeneration)) return\n'
    '        stage26RouteResponseNs = 0L\n',
    '        if (!stage36RuntimeAuthority.snapshot().enabled) return\n'
    '        stage26RouteResponseNs = 0L\n',
    'candidate entry runtime authority',
)
s = once(
    s,
    '        val semanticStage21 = FarolCausalCorrectionStage21.validateEvaluation(evaluationStage19)\n'
    '        stage32SemanticGate.observeCandidate(evaluationStage19.addressSignature)\n',
    '        val semanticStage21 = FarolCausalCorrectionStage21.validateEvaluation(evaluationStage19)\n'
    '        stage36RuntimeAuthority.bindDestination(evaluationStage19.addressSignature)\n'
    '        stage32SemanticGate.observeCandidate(evaluationStage19.addressSignature)\n',
    'bind stage36 final destination',
)
s = once(
    s,
    '        val visualChangedStage19 = universalActiveAddressSignature != evaluationStage19.addressSignature ||\n'
    '            lastSnapshotHash != evaluationStage19.screenHash\n',
    '        val visualChangedStage19 = universalActiveAddressSignature != evaluationStage19.addressSignature\n',
    'semantic destination change only',
)

binding = r'''    private fun stage26BindingKey(bindingStage26: FarolUniversalVisualPipelineStage19.Binding): String =
        "${bindingStage26.screenGeneration}|${bindingStage26.windowGeneration}|${bindingStage26.screenHash}|${bindingStage26.addressSignature}"

    private fun bindReadingActivationStage26(
        bindingStage26: FarolUniversalVisualPipelineStage19.Binding,
        activationGenerationStage26: Long,
    ) {
        @Suppress("UNUSED_VARIABLE") val legacyActivationStage36 = activationGenerationStage26
        if (stage36BindingWorkToken.size >= 12) {
            val firstStage36 = stage36BindingWorkToken.keys.firstOrNull()
            if (firstStage36 != null) stage36BindingWorkToken.remove(firstStage36)
        }
        val tokenStage36 = stage36RuntimeAuthority.captureDestinationToken(bindingStage26.addressSignature) ?: return
        stage36BindingWorkToken[stage26BindingKey(bindingStage26)] = tokenStage36
    }

    private fun isReadingBindingFreshStage26(bindingStage26: FarolUniversalVisualPipelineStage19.Binding): Boolean {
        val tokenStage36 = stage36BindingWorkToken[stage26BindingKey(bindingStage26)] ?: return false
        return stage36RuntimeAuthority.isFresh(tokenStage36)
    }

'''
s = between(
    s,
    '    private fun stage26BindingKey(',
    '    private suspend fun analyzeUniversalTwoAddressStage19(',
    binding,
    'route/paint work token binding',
)

stage19_fresh = r'''    private fun isStage19BindingFresh(bindingStage19: FarolUniversalVisualPipelineStage19.Binding): Boolean =
        serviceReady && WorkModePolicy0162.isEnabled(currentSettings) &&
            isReadingBindingFreshStage26(bindingStage19) &&
            bindingStage19.addressSignature == universalActiveAddressSignature


'''
s = between(
    s,
    '    private fun isStage19BindingFresh(',
    '    private fun stage26BindingKey(',
    stage19_fresh,
    'single route freshness authority',
)

s = once(
    s,
    '        val paintFreshStage20 = isStage19BindingFresh(bindingStage19)\n',
    '        val paintFreshStage20 = isStage19BindingFresh(bindingStage19) && !stage19VisualVerificationPending\n',
    'paint waits for current visual verification',
)

s = once(
    s,
    '        universalActiveAddressSignature = null\n'
    '        lastSnapshotHash = null\n',
    '        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.clearVisualLease(reason)\n'
    '        universalActiveAddressSignature = null\n'
    '        lastSnapshotHash = null\n',
    'hard clear card lease',
)

SERVICE.write_text(s)

report = PKG / 'ManualTechnicalReportBuilder.kt'
r = report.read_text()
r = once(
    r,
    '            appendLine(FarolCardLeaseStage34.Metrics.exportReport())\n            appendLine()\n',
    '            appendLine(FarolCardLeaseStage34.Metrics.exportReport())\n'
    '            appendLine()\n'
    '            appendLine(FarolRuntimeAuthorityStage36.Metrics.exportReport(if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority else null))\n'
    '            appendLine()\n',
    'Stage36 report export',
)
report.write_text(r)

build = ROOT / 'app/build.gradle.kts'
b = build.read_text()
b = once(b, 'versionCode = 5492', 'versionCode = 5493', 'versionCode')
b = once(b, 'versionName = "0.1.208"', 'versionName = "0.1.209"', 'versionName')
build.write_text(b)

print('stage36_farol_core=PASS')
