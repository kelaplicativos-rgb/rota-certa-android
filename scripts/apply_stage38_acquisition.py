#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
PATCH_ROOT = Path(__file__).resolve().parents[1]

def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)

def insert_before(text: str, anchor: str, addition: str, label: str) -> str:
    count = text.count(anchor)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 anchor, got {count}')
    return text.replace(anchor, addition + anchor, 1)

# ---------------------------------------------------------------------------
# Runtime call-sites.
# ---------------------------------------------------------------------------
service = PKG / 'LiveRideAccessibilityService.kt'
s = service.read_text()

s = once(
    s,
    '        FarolFlightRecorder0163.initialize(applicationContext) // farol_flight_recorder_init_0_1_163\n',
    '        FarolFlightRecorder0163.initialize(applicationContext) // farol_flight_recorder_init_0_1_163\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            atNs = SystemClock.elapsedRealtimeNanos(), wallMs = System.currentTimeMillis(),\n'
    '            stage = "S38_SERVICE_INITIALIZE", packageName = packageName,\n'
    '            details = "version=${BuildConfig.VERSION_NAME}; code=${BuildConfig.VERSION_CODE}; diagnostic_only=true; no_timer=true",\n'
    '        )\n',
    'service initialize Stage38',
)

s = once(
    s,
    '    private fun handleAccessibilityEvent0172(event: AccessibilityEvent) {\n        if (!serviceReady) return\n',
    '''    private fun handleAccessibilityEvent0172(event: AccessibilityEvent) {\n        val stage38EventNs = SystemClock.elapsedRealtimeNanos()\n        val stage38EventPackage = normalizePackageName(runCatching { event.packageName?.toString() }.getOrNull())\n        val stage38EventText = runCatching { event.text.joinToString(" || ") }.getOrDefault("")\n        val stage38Source = runCatching { event.source }.getOrNull()\n        FarolMaximumForensicsStage38.record(\n            atNs = stage38EventNs, wallMs = System.currentTimeMillis(),\n            stage = "S38_ACCESSIBILITY_EVENT_RECEIVED", packageName = stage38EventPackage,\n            details = "type=${runCatching { event.eventType }.getOrDefault(0)}; window=${runCatching { event.windowId }.getOrDefault(0)}; contentChangeTypes=${runCatching { event.contentChangeTypes }.getOrDefault(0)}; action=${runCatching { event.action }.getOrDefault(0)}; eventTimeMs=${runCatching { event.eventTime }.getOrDefault(0L)}; class=${runCatching { event.className?.toString() }.getOrNull().orEmpty()}; sourcePackage=${runCatching { stage38Source?.packageName?.toString() }.getOrNull().orEmpty()}; sourceViewId=${runCatching { stage38Source?.viewIdResourceName }.getOrNull().orEmpty()}; eventText=${stage38EventText.take(900)}",\n        )\n        if (!serviceReady) {\n            FarolMaximumForensicsStage38.record(SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_EVENT_REJECT", stage38EventPackage, details = "reason=service_not_ready")\n            return\n        }\n''',
    'accessibility event entry Stage38',
)

s = once(
    s,
    '        val activationStage26 = refreshReadingActivationStage26(eventPackageStage19, eventTypeStage20)\n',
    '        val activationStage26 = refreshReadingActivationStage26(eventPackageStage19, eventTypeStage20)\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_ACTIVATION_STATE", eventPackageStage19,\n'
    '            details = "enabled=${activationStage26.enabled}; generation=${activationStage26.generation}; usageAccess=${activationStage26.usageAccessGranted}; selectedActive=${activationStage26.activeSelectedPackages.sorted().joinToString(",")}",\n'
    '        )\n',
    'activation Stage38',
)

s = once(
    s,
    '        val semanticDecisionStage32 = stage32SemanticGate.observe(semanticSignalStage32)\n',
    '        val semanticDecisionStage32 = stage32SemanticGate.observe(semanticSignalStage32)\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_SEMANTIC_GATE", eventPackageStage19,\n'
    '            details = "mutation=${semanticDecisionStage32.mutation}; generation=${semanticDecisionStage32.snapshot.generation}; fingerprint=${semanticDecisionStage32.snapshot.fingerprint}; sourcePackage=${semanticSignalStage32.sourcePackage.orEmpty()}; windowPackage=${semanticSignalStage32.windowPackage.orEmpty()}; sourceSlot=${semanticSignalStage32.sourceSlot}; sourceText=${semanticSignalStage32.sourceText.take(900)}",\n'
    '        )\n',
    'semantic gate Stage38',
)

# The semantic decision type changed fields across stages; use only stable admission fields below.
s = once(
    s,
    '        val admissionStage26 = stage26PreCollectGate.admit(true, cheapSignalStage26)\n',
    '        val admissionStage26 = stage26PreCollectGate.admit(true, cheapSignalStage26)\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_PRECOLLECT_ADMISSION", eventPackageStage19,\n'
    '            details = "heavyCollect=${admissionStage26.heavyCollect}; visualGeneration=${admissionStage26.visualGeneration}; ownOverlay=${cheapSignalStage26.ownOverlay}; windowSignature=${cheapSignalStage26.windowSignature}; sourceSlot=${cheapSignalStage26.sourceSlot}; contentChangeTypes=${cheapSignalStage26.contentChangeTypes}; sourceText=${cheapSignalStage26.sourceText.take(900)}",\n'
    '        )\n',
    'precollect Stage38',
)

s = once(
    s,
    '        stage20LastCycleId = cycleIdStage20\n\n        val collectStartedNsStage26 = SystemClock.elapsedRealtimeNanos()\n',
    '        stage20LastCycleId = cycleIdStage20\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_CYCLE_LINK", eventPackageStage19, cycleId = cycleIdStage20,\n'
    '            details = "eventStartedNs=$eventStartedNsStage26; eventType=$eventTypeStage20; eventWindow=$eventWindowIdStage20",\n'
    '        )\n\n'
    '        val collectStartedNsStage26 = SystemClock.elapsedRealtimeNanos()\n',
    'cycle link Stage38',
)

s = once(
    s,
    '        FarolForensicCardBlackBoxStage32.recordCollection(\n            collectEndedNsStage26, collectionStage26.snapshot.hash, collectionStage26.stats.blocksEmitted,\n            collectionStage26.stats.windowsTraversed, collectionStage26.stats.blocksVisited, collectionStage26.stats.earlyExitReason,\n        )\n',
    '        FarolForensicCardBlackBoxStage32.recordCollection(\n            collectEndedNsStage26, collectionStage26.snapshot.hash, collectionStage26.stats.blocksEmitted,\n            collectionStage26.stats.windowsTraversed, collectionStage26.stats.blocksVisited, collectionStage26.stats.earlyExitReason,\n        )\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            collectEndedNsStage26, System.currentTimeMillis(), "S38_ACCESSIBILITY_COLLECTION_RESULT", eventPackageStage19, cycleId = cycleIdStage20,\n'
    '            details = "duration_ns=${(collectEndedNsStage26 - collectStartedNsStage26).coerceAtLeast(0L)}; snapshotHash=${collectionStage26.snapshot.hash}; blocks=${collectionStage26.stats.blocksEmitted}; windowsTotal=${collectionStage26.stats.visibleWindowsTotal}; windowsTraversed=${collectionStage26.stats.windowsTraversed}; nodesVisited=${collectionStage26.stats.blocksVisited}; parserInvocations=${collectionStage26.addressParserInvocations}; duplicatesAvoided=${collectionStage26.duplicateSubtreesAvoided}; reason=${collectionStage26.stats.earlyExitReason}",\n'
    '        )\n'
    '        collectionStage26.blocks.forEachIndexed { stage38Index, stage38Block ->\n'
    '            FarolMaximumForensicsStage38.record(\n'
    '                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_ACCESSIBILITY_BLOCK", eventPackageStage19, cycleId = cycleIdStage20,\n'
    '                details = "index=$stage38Index; id=${stage38Block.id}; parent=${stage38Block.parentId.orEmpty()}; window=${stage38Block.windowId}; layer=${stage38Block.windowLayer}; depth=${stage38Block.depth}; bounds=${stage38Block.left},${stage38Block.top},${stage38Block.right},${stage38Block.bottom}; text=${stage38Block.text.take(1200)}",\n'
    '            )\n'
    '        }\n',
    'collection result Stage38',
)

s = once(
    s,
    '        FarolForensicCardBlackBoxStage32.recordAccessibilityEvaluation(evaluateEndedNsStage26, evaluationStage19 != null)\n',
    '        FarolForensicCardBlackBoxStage32.recordAccessibilityEvaluation(evaluateEndedNsStage26, evaluationStage19 != null)\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            evaluateEndedNsStage26, System.currentTimeMillis(), "S38_ACCESSIBILITY_EVALUATION_RESULT", eventPackageStage19, cycleId = cycleIdStage20,\n'
    '            details = "candidate=${evaluationStage19 != null}; duration_ns=${(evaluateEndedNsStage26 - evaluateStartedNsStage26).coerceAtLeast(0L)}; pickup=${evaluationStage19?.pickup.orEmpty()}; destination=${evaluationStage19?.destination.orEmpty()}; signature=${evaluationStage19?.addressSignature.orEmpty()}",\n'
    '        )\n'
    '        if (evaluationStage19 == null) {\n'
    '            FarolCausalCorrectionStage21.forensicExplainEvaluationStage38(collectionStage26.blocks).take(320).forEachIndexed { index38, step38 ->\n'
    '                FarolMaximumForensicsStage38.record(\n'
    '                    SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_ACCESSIBILITY_EVALUATION_RULE", eventPackageStage19, cycleId = cycleIdStage20,\n'
    '                    details = "step=$index38; $step38",\n'
    '                )\n'
    '            }\n'
    '        }\n',
    'accessibility evaluation Stage38',
)

# Node-level capture: reuse the exact existing lead check instead of evaluating it twice.
s = once(
    s,
    '        // Fast local check before descending: an ancestor that already exposes the complete card\n        // is not expanded into dozens of child/parent copies.\n        parserStage26 += 1\n        if (depthStage26 > 0 && FarolVisualIdentityStage23.hasTwoAddressLeads(linesStage26.asSequence())) {\n',
    '        // Fast local check before descending: an ancestor that already exposes the complete card\n'
    '        // is not expanded into dozens of child/parent copies.\n'
    '        parserStage26 += 1\n'
    '        val stage38EarlyTwoAddressLeads = depthStage26 > 0 && FarolVisualIdentityStage23.hasTwoAddressLeads(linesStage26.asSequence())\n'
    '        val stage38NodeBounds = Rect()\n'
    '        runCatching { nodeStage26.getBoundsInScreen(stage38NodeBounds) }\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_ACCESSIBILITY_NODE_VISITED", packageNameStage26,\n'
    '            details = "id=$idStage26; parent=${parentIdStage26.orEmpty()}; window=$windowIdStage26; layer=$windowLayerStage26; depth=$depthStage26; budget=${budgetStage26[0]}; bounds=${stage38NodeBounds.left},${stage38NodeBounds.top},${stage38NodeBounds.right},${stage38NodeBounds.bottom}; earlyTwoAddressLeads=$stage38EarlyTwoAddressLeads; lines=${linesStage26.joinToString(" || ").take(1000)}",\n'
    '        )\n'
    '        if (stage38EarlyTwoAddressLeads) {\n',
    'node capture Stage38',
)


service.write_text(s)
print('stage38_acquisition=PASS')
