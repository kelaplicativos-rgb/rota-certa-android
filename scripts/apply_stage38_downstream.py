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

service = PKG / 'LiveRideAccessibilityService.kt'
s = service.read_text()

# Semantic candidate and trace binding.
s = once(
    s,
    '        val semanticStage21 = FarolCausalCorrectionStage21.validateEvaluation(evaluationStage19)\n',
    '        val semanticStage21 = FarolCausalCorrectionStage21.validateEvaluation(evaluationStage19)\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_CANDIDATE_SEMANTIC_VALIDATION", packageName = null, cycleId = cycleIdStage20,\n'
    '            details = "source=$sourceStage19; accepted=${semanticStage21.accepted}; reason=${semanticStage21.reason}; pickup=${evaluationStage19.pickup.take(700)}; destination=${evaluationStage19.destination.take(700)}; addresses=${evaluationStage19.addresses.joinToString(" || ").take(1300)}; signature=${evaluationStage19.addressSignature}",\n'
    '        )\n',
    'candidate semantic Stage38',
)

s = once(
    s,
    '        val traceIdStage20 = FarolForensicTraceStage20.bindCandidate(\n            SystemClock.elapsedRealtimeNanos(), cycleIdStage20, currentBindingStage20, sourceStage19,\n            evaluationStage19.destination, evaluationStage19.blockId,\n        )\n',
    '        val traceIdStage20 = FarolForensicTraceStage20.bindCandidate(\n            SystemClock.elapsedRealtimeNanos(), cycleIdStage20, currentBindingStage20, sourceStage19,\n            evaluationStage19.destination, evaluationStage19.blockId,\n        )\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_CANDIDATE_BOUND", packageName = null, cycleId = cycleIdStage20, traceId = traceIdStage20,\n'
    '            details = "source=$sourceStage19; window=${evaluationStage19.windowId}; block=${evaluationStage19.blockId}; destination=${evaluationStage19.destination.take(900)}; screenGeneration=$universalScreenGeneration; windowGeneration=$universalWindowGeneration; screenHash=${evaluationStage19.screenHash}; addressSignature=${evaluationStage19.addressSignature}",\n'
    '        )\n',
    'candidate bound Stage38',
)

s = once(
    s,
    '        FarolForensicTraceStage20.cacheLookupFinished(traceIdStage20, SystemClock.elapsedRealtimeNanos(), cachedStage19 != null)\n',
    '        FarolForensicTraceStage20.cacheLookupFinished(traceIdStage20, SystemClock.elapsedRealtimeNanos(), cachedStage19 != null)\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_CACHE_RESULT", packageName = null, cycleId = cycleIdStage20, traceId = traceIdStage20, operationId = "CACHE",\n'
    '            details = "hit=${cachedStage19 != null}; destination=${fieldsStage19.destination.orEmpty().take(900)}; targets=${targetsStage19.destinations.size}",\n'
    '        )\n',
    'cache Stage38',
)

s = once(
    s,
    '        val routeStartedNsStage26 = SystemClock.elapsedRealtimeNanos()\n',
    '        val routeStartedNsStage26 = SystemClock.elapsedRealtimeNanos()\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            routeStartedNsStage26, System.currentTimeMillis(), "S38_GOOGLE_ROUTE_START", packageName = null, traceId = traceIdStage20, operationId = routeJobIdStage20,\n'
    '            details = "destination=${fieldsStage19.destination.orEmpty().take(900)}; targets=${targetsStage19.destinations.joinToString(" || ").take(1200)}",\n'
    '        )\n',
    'route start Stage38',
)

s = once(
    s,
    '        val routeEndedNsStage26 = SystemClock.elapsedRealtimeNanos()\n',
    '        val routeEndedNsStage26 = SystemClock.elapsedRealtimeNanos()\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            routeEndedNsStage26, System.currentTimeMillis(), "S38_GOOGLE_ROUTE_END", packageName = null, traceId = traceIdStage20, operationId = routeJobIdStage20,\n'
    '            details = "duration_ns=${(routeEndedNsStage26 - routeStartedNsStage26).coerceAtLeast(0L)}; response=${distancesStage19.toString().take(1200)}",\n'
    '        )\n',
    'route end Stage38',
)

s = once(
    s,
    '        FarolForensicCardBlackBoxStage32.recordPaintRequested(SystemClock.elapsedRealtimeNanos(), colorStage19.toString(), distanceStage19)\n',
    '        FarolForensicCardBlackBoxStage32.recordPaintRequested(SystemClock.elapsedRealtimeNanos(), colorStage19.toString(), distanceStage19)\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_FINAL_PAINT_PREPARE", packageName = null, traceId = traceIdStage20, operationId = operationIdStage20,\n'
    '            details = "recommendation=${resultStage19.recommendation}; color=$colorStage19; distanceKm=${distanceStage19 ?: -1.0}; reason=${resultStage19.reason.take(900)}; binding=${bindingStage19.screenGeneration}|${bindingStage19.windowGeneration}|${bindingStage19.screenHash}|${bindingStage19.addressSignature}",\n'
    '        )\n',
    'paint prepare Stage38',
)

s = once(
    s,
    '            showOverlay(colorStage19, distanceStage19)\n            FarolForensicCardBlackBoxStage32.recordFinal(\n',
    '            showOverlay(colorStage19, distanceStage19)\n'
    '            FarolMaximumForensicsStage38.record(\n'
    '                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_FINAL_PAINT_APPLIED", packageName = null, traceId = traceIdStage20, operationId = operationIdStage20,\n'
    '                details = "color=$colorStage19; distanceKm=${distanceStage19 ?: -1.0}; currentColor=$currentRadarColor; currentDistance=$currentDistanceKm",\n'
    '            )\n'
    '            FarolForensicCardBlackBoxStage32.recordFinal(\n',
    'paint applied Stage38',
)

# Overlay render request/applied, including non-final yellow/gray state changes.
s = once(
    s,
    '        FarolForensicTraceStage20.overlayRequested(stage20ExpectedPaintToken, SystemClock.elapsedRealtimeNanos(), color.toString(), distanceKm, stage20Binding, stage20Origin)\n',
    '        FarolForensicTraceStage20.overlayRequested(stage20ExpectedPaintToken, SystemClock.elapsedRealtimeNanos(), color.toString(), distanceKm, stage20Binding, stage20Origin)\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OVERLAY_RENDER_REQUEST", universalResolvedForegroundPackage(),\n'
    '            traceId = stage20ExpectedPaintToken?.traceId, operationId = stage20ExpectedPaintToken?.operationId,\n'
    '            details = "requestedColor=$color; requestedDistance=${distanceKm ?: -1.0}; currentColor=$currentRadarColor; currentDistance=${currentDistanceKm ?: -1.0}; origin=$stage20Origin; binding=${stage20Binding.stableKey()}",\n'
    '        )\n',
    'overlay request Stage38',
)

s = once(
    s,
    '        FarolForensicTraceStage20.overlayApplied(stage20ExpectedPaintToken, SystemClock.elapsedRealtimeNanos(), color.toString(), distanceKm, currentStage20BindingSnapshot(), stage20Origin)\n',
    '        FarolForensicTraceStage20.overlayApplied(stage20ExpectedPaintToken, SystemClock.elapsedRealtimeNanos(), color.toString(), distanceKm, currentStage20BindingSnapshot(), stage20Origin)\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_OVERLAY_RENDER_APPLIED", universalResolvedForegroundPackage(),\n'
    '            traceId = stage20ExpectedPaintToken?.traceId, operationId = stage20ExpectedPaintToken?.operationId,\n'
    '            details = "color=$color; distance=${distanceKm ?: -1.0}; text=${view.text}; viewCreated=${existingViewChecklist15 == null}; x=${overlayParams?.x ?: -1}; y=${overlayParams?.y ?: -1}",\n'
    '        )\n',
    'overlay applied Stage38',
)

# Every MotionEvent received by the bubble: DOWN/MOVE/UP/CANCEL with coordinates and event timing.
s = once(
    s,
    '        override fun onTouch(view: View, event: MotionEvent): Boolean {\n            val params = overlayParams ?: return false\n',
    '        override fun onTouch(view: View, event: MotionEvent): Boolean {\n'
    '            FarolMaximumForensicsStage38.record(\n'
    '                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S38_BUBBLE_MOTION_EVENT", universalResolvedForegroundPackage(),\n'
    '                details = "action=${event.actionMasked}; actionIndex=${event.actionIndex}; eventTimeMs=${event.eventTime}; downTimeMs=${event.downTime}; rawX=${event.rawX}; rawY=${event.rawY}; x=${event.x}; y=${event.y}; pointers=${event.pointerCount}; pressure=${runCatching { event.getPressure(0) }.getOrDefault(0f)}; size=${runCatching { event.getSize(0) }.getOrDefault(0f)}",\n'
    '            )\n'
    '            val params = overlayParams ?: return false\n',
    'bubble motion Stage38',
)

service.write_text(s)


service.write_text(s)
print('stage38_downstream=PASS')
