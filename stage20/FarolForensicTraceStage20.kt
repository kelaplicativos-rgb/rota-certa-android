package br.com.mapeiaia.rotacerta

import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/** Stage20: gravador forense causal, somente memoria e sem timers. */
object FarolForensicTraceStage20 {
    const val CONTRACT_MARKER = "FAROL_FORENSIC_CAUSALITY_STAGE20"
    const val CLOCK_MARKER = "ELAPSED_REALTIME_NANOS_STAGE20"
    const val MAX_EVENTS = 8_192
    const val MAX_ATTEMPTS = 256
    const val MAX_DETAILS = 900
    const val ROUTE_TO_PAINT_WARN_US = 100_000L
    const val CACHE_TO_PAINT_WARN_US = 100_000L
    const val PAINT_APPLY_WARN_US = 50_000L
    const val VISUAL_DETECTION_WARN_US = 150_000L
    const val ACCESSIBILITY_SEGMENT_WARN_US = 100_000L

    data class BindingSnapshot(
        val screenGeneration: Long,
        val windowGeneration: Long,
        val screenHash: Int?,
        val addressSignature: String?,
    ) {
        fun stableKey(): String = listOf(screenGeneration, windowGeneration, screenHash ?: 0, addressSignature.orEmpty()).joinToString("|")
    }

    data class PaintToken(
        val traceId: String,
        val operationId: String,
        val binding: BindingSnapshot,
        val expectedColor: String,
        val expectedDistanceKm: Double?,
        val preparedNs: Long,
    )

    data class TraceEvent(
        val seq: Long,
        val atNs: Long,
        val cycleId: Long?,
        val traceId: String?,
        val operationId: String?,
        val stage: String,
        val binding: BindingSnapshot?,
        val details: String,
        val critical: Boolean,
    )

    private data class CycleState(
        val id: Long,
        val startedNs: Long,
        var collectStartedNs: Long? = null,
        var collectEndedNs: Long? = null,
        var evaluateStartedNs: Long? = null,
        var evaluateEndedNs: Long? = null,
    )

    private data class AttemptState(
        val traceId: String,
        var binding: BindingSnapshot,
        var source: String,
        var destination: String,
        var cycleId: Long?,
        var candidateNs: Long,
        var cacheStartNs: Long? = null,
        var cacheEndNs: Long? = null,
        var cacheHit: Boolean? = null,
        var routeJobId: String? = null,
        var routeStartNs: Long? = null,
        var routeEndNs: Long? = null,
        var decisionStartNs: Long? = null,
        var decisionEndNs: Long? = null,
        var paintPreparedNs: Long? = null,
        var paintRequestedNs: Long? = null,
        var paintAppliedNs: Long? = null,
        var expectedColor: String? = null,
        var expectedDistanceKm: Double? = null,
        var actualColor: String? = null,
        var actualDistanceKm: Double? = null,
        var paintOrigin: String? = null,
        var invalidatedNs: Long? = null,
        var staleBlockedCount: Int = 0,
        var anomalyCount: Int = 0,
    )

    private val lock = Any()
    private val eventSeq = AtomicLong(0L)
    private val cycleSeq = AtomicLong(0L)
    private val traceSeq = AtomicLong(0L)
    private val routeSeq = AtomicLong(0L)
    private val events = ArrayDeque<TraceEvent>(MAX_EVENTS)
    private val cycles = LinkedHashMap<Long, CycleState>()
    private val attempts = LinkedHashMap<String, AttemptState>()
    private val traceByBinding = HashMap<String, String>()
    private var droppedEvents: Long = 0L
    private var criticalCount: Long = 0L

    fun beginCycle(nowNs: Long, packageName: String?, eventType: Int, eventWindowId: Int): Long = synchronized(lock) {
        val id = cycleSeq.incrementAndGet()
        cycles[id] = CycleState(id, nowNs)
        trimCycles()
        addEvent(nowNs, cycleId = id, stage = "S20_EVENT_RECEIVED", details = "package=${safe(packageName)}; eventType=$eventType; eventWindow=$eventWindowId")
        id
    }

    fun accessibilityCollectStarted(cycleId: Long, nowNs: Long) = synchronized(lock) {
        cycles[cycleId]?.collectStartedNs = nowNs
        addEvent(nowNs, cycleId, stage = "S20_ACCESSIBILITY_COLLECT_START")
    }

    fun accessibilityCollectFinished(cycleId: Long, nowNs: Long, windowCount: Int, blockCount: Int) = synchronized(lock) {
        val cycle = cycles[cycleId]
        cycle?.collectEndedNs = nowNs
        val durationUs = cycle?.collectStartedNs?.let { us(it, nowNs) }
        addEvent(nowNs, cycleId, stage = "S20_ACCESSIBILITY_COLLECT_END", details = "windows=$windowCount; blocks=$blockCount; duration_us=${durationUs ?: -1}", critical = durationUs != null && durationUs > ACCESSIBILITY_SEGMENT_WARN_US)
    }

    fun accessibilityEvaluateStarted(cycleId: Long, nowNs: Long) = synchronized(lock) {
        cycles[cycleId]?.evaluateStartedNs = nowNs
        addEvent(nowNs, cycleId, stage = "S20_ACCESSIBILITY_EVALUATE_START")
    }

    fun accessibilityEvaluateFinished(cycleId: Long, nowNs: Long, candidateFound: Boolean) = synchronized(lock) {
        val cycle = cycles[cycleId]
        cycle?.evaluateEndedNs = nowNs
        val durationUs = cycle?.evaluateStartedNs?.let { us(it, nowNs) }
        addEvent(nowNs, cycleId, stage = "S20_ACCESSIBILITY_EVALUATE_END", details = "candidate=$candidateFound; duration_us=${durationUs ?: -1}", critical = durationUs != null && durationUs > ACCESSIBILITY_SEGMENT_WARN_US)
    }

    fun ocrStage(nowNs: Long, ocrSerial: Long, stage: String, cycleId: Long? = null, details: String = "") = synchronized(lock) {
        addEvent(nowNs, cycleId, operationId = "ocr-$ocrSerial", stage = "S20_OCR_${safeStage(stage)}", details = details)
    }

    fun bindCandidate(nowNs: Long, cycleId: Long?, binding: BindingSnapshot, source: String, destination: String, blockId: String): String = synchronized(lock) {
        val key = binding.stableKey()
        val traceId = traceByBinding[key] ?: "T20-${traceSeq.incrementAndGet().toString().padStart(6, '0')}"
        val attempt = attempts[traceId]
        if (attempt == null) {
            attempts[traceId] = AttemptState(traceId, binding, source, destination, cycleId, nowNs)
            traceByBinding[key] = traceId
            trimAttempts()
        } else {
            attempt.binding = binding
            attempt.source = source
            attempt.destination = destination
            attempt.cycleId = cycleId ?: attempt.cycleId
        }
        val detectionUs = cycleId?.let { cycles[it]?.startedNs }?.let { us(it, nowNs) }
        val critical = detectionUs != null && detectionUs > VISUAL_DETECTION_WARN_US
        if (critical) attempts[traceId]?.anomalyCount = (attempts[traceId]?.anomalyCount ?: 0) + 1
        addEvent(nowNs, cycleId, traceId, stage = "S20_VISUAL_CANDIDATE_BOUND", binding = binding, details = "source=${safe(source)}; destination=${safe(destination)}; block=${safe(blockId)}; event_to_candidate_us=${detectionUs ?: -1}", critical = critical)
        traceId
    }

    fun traceFor(binding: BindingSnapshot): String? = synchronized(lock) { traceByBinding[binding.stableKey()] }

    fun note(nowNs: Long, stage: String, cycleId: Long? = null, traceId: String? = null, operationId: String? = null, binding: BindingSnapshot? = null, details: String = "", critical: Boolean = false) = synchronized(lock) {
        traceId?.let { if (critical) attempts[it]?.anomalyCount = (attempts[it]?.anomalyCount ?: 0) + 1 }
        addEvent(nowNs, cycleId, traceId, operationId, stage, binding, details, critical)
    }

    fun visualInvalidated(nowNs: Long, previous: BindingSnapshot?, next: BindingSnapshot?, reason: String) = synchronized(lock) {
        val previousTrace = previous?.let { traceByBinding[it.stableKey()] }
        previousTrace?.let { attempts[it]?.invalidatedNs = nowNs }
        addEvent(nowNs, traceId = previousTrace, stage = "S20_VISUAL_INVALIDATED", binding = previous, details = "reason=${safe(reason)}; next=${bindingText(next)}")
    }

    fun cacheLookupStarted(traceId: String?, nowNs: Long) = synchronized(lock) {
        traceId?.let { attempts[it]?.cacheStartNs = nowNs }
        addEvent(nowNs, traceId = traceId, stage = "S20_CACHE_LOOKUP_START")
    }

    fun cacheLookupFinished(traceId: String?, nowNs: Long, hit: Boolean) = synchronized(lock) {
        val attempt = traceId?.let(attempts::get)
        attempt?.cacheEndNs = nowNs
        attempt?.cacheHit = hit
        val durationUs = attempt?.cacheStartNs?.let { us(it, nowNs) }
        addEvent(nowNs, traceId = traceId, stage = if (hit) "S20_CACHE_HIT" else "S20_CACHE_MISS", binding = attempt?.binding, details = "duration_us=${durationUs ?: -1}")
    }

    fun routeJobStarted(traceId: String?, nowNs: Long): String = synchronized(lock) {
        val jobId = "R20-${routeSeq.incrementAndGet().toString().padStart(6, '0')}"
        traceId?.let { attempts[it]?.apply { routeJobId = jobId; routeStartNs = nowNs } }
        addEvent(nowNs, traceId = traceId, operationId = jobId, stage = "S20_ROUTE_JOB_START", binding = traceId?.let { attempts[it]?.binding })
        jobId
    }

    fun routeCallStarted(traceId: String?, jobId: String?, nowNs: Long, destination: String) = synchronized(lock) {
        traceId?.let { attempts[it]?.routeStartNs = nowNs }
        addEvent(nowNs, traceId = traceId, operationId = jobId, stage = "S20_ROUTE_CALL_START", binding = traceId?.let { attempts[it]?.binding }, details = "destination=${safe(destination)}")
    }

    fun routeCallFinished(traceId: String?, jobId: String?, nowNs: Long, distances: String) = synchronized(lock) {
        val attempt = traceId?.let(attempts::get)
        attempt?.routeEndNs = nowNs
        addEvent(nowNs, traceId = traceId, operationId = jobId, stage = "S20_ROUTE_CALL_END", binding = attempt?.binding, details = "route_us=${attempt?.routeStartNs?.let { us(it, nowNs) } ?: -1}; distances=${safe(distances)}; invalidated=${attempt?.invalidatedNs != null}")
    }

    fun routeCancelled(traceId: String?, jobId: String?, nowNs: Long, reason: String) = synchronized(lock) {
        addEvent(nowNs, traceId = traceId, operationId = jobId, stage = "S20_ROUTE_JOB_CANCELLED", binding = traceId?.let { attempts[it]?.binding }, details = "reason=${safe(reason)}")
    }

    fun bindingCheck(traceId: String?, jobId: String?, nowNs: Long, stage: String, expected: BindingSnapshot, current: BindingSnapshot, fresh: Boolean, verificationPending: Boolean) = synchronized(lock) {
        if (!fresh) traceId?.let { attempts[it]?.staleBlockedCount = (attempts[it]?.staleBlockedCount ?: 0) + 1 }
        addEvent(nowNs, traceId = traceId, operationId = jobId, stage = if (fresh) "S20_BINDING_FRESH_${safeStage(stage)}" else "S20_STALE_RESULT_BLOCKED_${safeStage(stage)}", binding = expected, details = "current=${bindingText(current)}; verificationPending=$verificationPending")
    }

    fun decisionStarted(traceId: String?, jobId: String?, nowNs: Long) = synchronized(lock) {
        traceId?.let { attempts[it]?.decisionStartNs = nowNs }
        addEvent(nowNs, traceId = traceId, operationId = jobId, stage = "S20_DECISION_START", binding = traceId?.let { attempts[it]?.binding })
    }

    fun decisionFinished(traceId: String?, jobId: String?, nowNs: Long, recommendation: String, distanceKm: Double?) = synchronized(lock) {
        val attempt = traceId?.let(attempts::get)
        attempt?.decisionEndNs = nowNs
        addEvent(nowNs, traceId = traceId, operationId = jobId, stage = "S20_DECISION_END", binding = attempt?.binding, details = "decision_us=${attempt?.decisionStartNs?.let { us(it, nowNs) } ?: -1}; recommendation=${safe(recommendation)}; distance=$distanceKm")
    }

    fun preparePaint(traceId: String, operationId: String, binding: BindingSnapshot, expectedColor: String, expectedDistanceKm: Double?, nowNs: Long): PaintToken = synchronized(lock) {
        val attempt = attempts[traceId]
        attempt?.paintPreparedNs = nowNs
        attempt?.expectedColor = expectedColor
        attempt?.expectedDistanceKm = expectedDistanceKm
        addEvent(nowNs, traceId = traceId, operationId = operationId, stage = "S20_FINAL_PAINT_PREPARED", binding = binding, details = "expectedColor=${safe(expectedColor)}; expectedDistance=$expectedDistanceKm")
        PaintToken(traceId, operationId, binding, expectedColor, expectedDistanceKm, nowNs)
    }

    fun overlayRequested(token: PaintToken?, nowNs: Long, color: String, distanceKm: Double?, currentBinding: BindingSnapshot, origin: String) = synchronized(lock) {
        val finalColor = isFinalColor(color)
        val traceId = token?.traceId
        val attempt = traceId?.let(attempts::get)
        attempt?.paintRequestedNs = nowNs
        attempt?.paintOrigin = safe(origin)
        var critical = false
        val reasons = ArrayList<String>(4)
        if (finalColor && token == null) { critical = true; reasons += "unscoped_final_paint" }
        if (token != null && token.binding != currentBinding) { critical = true; reasons += "binding_mismatch" }
        if (token != null && !token.expectedColor.equals(color, ignoreCase = true)) { critical = true; reasons += "color_mismatch" }
        if (token != null && !distanceEquals(token.expectedDistanceKm, distanceKm)) { critical = true; reasons += "distance_mismatch" }
        val routeToPaintUs = attempt?.routeEndNs?.let { us(it, nowNs) }
        val cacheToPaintUs = if (attempt?.cacheHit == true) attempt.cacheEndNs?.let { us(it, nowNs) } else null
        if (routeToPaintUs != null && routeToPaintUs > ROUTE_TO_PAINT_WARN_US) { critical = true; reasons += "route_to_paint_delay" }
        if (cacheToPaintUs != null && cacheToPaintUs > CACHE_TO_PAINT_WARN_US) { critical = true; reasons += "cache_to_paint_delay" }
        if (critical) attempt?.anomalyCount = (attempt?.anomalyCount ?: 0) + 1
        addEvent(nowNs, traceId = traceId, operationId = token?.operationId, stage = if (critical) "FORENSIC_STAGE20_FINAL_PAINT_REQUEST_ANOMALY" else "S20_OVERLAY_RENDER_REQUEST", binding = token?.binding, details = "color=${safe(color)}; distance=$distanceKm; route_to_paint_us=${routeToPaintUs ?: -1}; cache_to_paint_us=${cacheToPaintUs ?: -1}; reasons=${reasons.joinToString(",").ifBlank { "none" }}; current=${bindingText(currentBinding)}; origin=${safe(origin)}", critical = critical)
    }

    fun overlayApplied(token: PaintToken?, nowNs: Long, color: String, distanceKm: Double?, currentBinding: BindingSnapshot, origin: String) = synchronized(lock) {
        val traceId = token?.traceId
        val attempt = traceId?.let(attempts::get)
        attempt?.paintAppliedNs = nowNs
        attempt?.actualColor = color
        attempt?.actualDistanceKm = distanceKm
        attempt?.paintOrigin = safe(origin)
        val applyUs = attempt?.paintRequestedNs?.let { us(it, nowNs) }
        var critical = applyUs != null && applyUs > PAINT_APPLY_WARN_US
        if (token != null && token.binding != currentBinding) critical = true
        if (critical) attempt?.anomalyCount = (attempt?.anomalyCount ?: 0) + 1
        addEvent(nowNs, traceId = traceId, operationId = token?.operationId, stage = if (critical) "FORENSIC_STAGE20_OVERLAY_APPLY_ANOMALY" else "S20_OVERLAY_RENDER_APPLIED", binding = token?.binding, details = "color=${safe(color)}; distance=$distanceKm; request_to_apply_us=${applyUs ?: -1}; current=${bindingText(currentBinding)}; origin=${safe(origin)}", critical = critical)
    }

    fun overlayIdempotentSkipped(token: PaintToken?, nowNs: Long, color: String, distanceKm: Double?, currentBinding: BindingSnapshot, origin: String) = synchronized(lock) {
        addEvent(nowNs, traceId = token?.traceId, operationId = token?.operationId, stage = "S20_OVERLAY_IDEMPOTENT_SKIP", binding = token?.binding, details = "color=${safe(color)}; distance=$distanceKm; current=${bindingText(currentBinding)}; origin=${safe(origin)}")
    }

    fun exportReport(): String = synchronized(lock) {
        val eventSnapshot = events.toList()
        val attemptSnapshot = attempts.values.toList()
        val criticalSnapshot = eventSnapshot.filter { it.critical }
        val verdict = when {
            criticalSnapshot.any { it.stage.contains("FINAL_PAINT") || it.details.contains("binding_mismatch") || it.details.contains("unscoped_final_paint") } -> "FAIL_CRITICAL_PAINT_CAUSALITY"
            criticalSnapshot.any { it.details.contains("route_to_paint_delay") } -> "FAIL_INTERNAL_POST_ROUTE_DELAY"
            criticalSnapshot.any { it.stage.contains("ACCESSIBILITY") || it.stage.contains("VISUAL_CANDIDATE") } -> "FAIL_VISUAL_DETECTION_LATENCY"
            attemptSnapshot.any { it.paintAppliedNs != null } -> "TRACEABLE_NO_CRITICAL_CAUSALITY_ANOMALY"
            else -> "NO_FINAL_DECISION_CAPTURED"
        }
        buildString {
            appendLine("--- FAROL FORENSIC CAUSALITY STAGE20 ---")
            appendLine("marker=$CONTRACT_MARKER")
            appendLine("clock=$CLOCK_MARKER")
            appendLine("precision=monotonic nanoseconds; durations exported in microseconds")
            appendLine("behavioral_authority=false; diagnostic_only=true; timers=false; network_added=false; ocr_added=false")
            appendLine("events=${eventSnapshot.size}; dropped=$droppedEvents; attempts=${attemptSnapshot.size}; critical=$criticalCount")
            appendLine("verdict=$verdict")
            appendLine()
            appendLine("--- ATTEMPT SUMMARY ---")
            if (attemptSnapshot.isEmpty()) appendLine("no_attempts") else attemptSnapshot.forEach { attempt ->
                append("trace=").append(attempt.traceId)
                append(" | sg=").append(attempt.binding.screenGeneration)
                append(" | wg=").append(attempt.binding.windowGeneration)
                append(" | source=").append(safe(attempt.source))
                append(" | destination=").append(safe(attempt.destination))
                append(" | cache=").append(attempt.cacheHit ?: "unknown")
                append(" | routeJob=").append(attempt.routeJobId ?: "none")
                append(" | route_us=").append(duration(attempt.routeStartNs, attempt.routeEndNs))
                append(" | route_to_paint_us=").append(duration(attempt.routeEndNs, attempt.paintRequestedNs))
                append(" | cache_to_paint_us=").append(if (attempt.cacheHit == true) duration(attempt.cacheEndNs, attempt.paintRequestedNs) else -1)
                append(" | decision_us=").append(duration(attempt.decisionStartNs, attempt.decisionEndNs))
                append(" | paint_apply_us=").append(duration(attempt.paintRequestedNs, attempt.paintAppliedNs))
                append(" | final=").append(attempt.actualColor ?: "none").append('/').append(attempt.actualDistanceKm ?: "none")
                append(" | staleBlocked=").append(attempt.staleBlockedCount)
                append(" | anomalies=").append(attempt.anomalyCount)
                append(" | origin=").append(attempt.paintOrigin ?: "none")
                appendLine()
            }
            appendLine()
            appendLine("--- CRITICAL ANOMALIES ---")
            if (criticalSnapshot.isEmpty()) appendLine("none") else criticalSnapshot.forEach { appendLine(it.toLine()) }
            appendLine()
            appendLine("--- FULL CAUSAL CHRONOLOGY ---")
            if (eventSnapshot.isEmpty()) appendLine("none") else eventSnapshot.forEach { appendLine(it.toLine()) }
        }.trimEnd()
    }

    fun callSite(stack: Array<StackTraceElement>): String = stack.asSequence()
        .filterNot { it.className.contains("FarolForensicTraceStage20") }
        .filterNot { it.methodName == "getStackTrace" }
        .take(6)
        .joinToString(" > ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        .take(600)

    internal fun resetForTest() = synchronized(lock) {
        eventSeq.set(0L); cycleSeq.set(0L); traceSeq.set(0L); routeSeq.set(0L)
        events.clear(); cycles.clear(); attempts.clear(); traceByBinding.clear()
        droppedEvents = 0L; criticalCount = 0L
    }
    internal fun eventCountForTest(): Int = synchronized(lock) { events.size }
    internal fun droppedForTest(): Long = synchronized(lock) { droppedEvents }
    internal fun criticalForTest(): Long = synchronized(lock) { criticalCount }

    private fun addEvent(nowNs: Long, cycleId: Long? = null, traceId: String? = null, operationId: String? = null, stage: String, binding: BindingSnapshot? = null, details: String = "", critical: Boolean = false) {
        val event = TraceEvent(eventSeq.incrementAndGet(), nowNs, cycleId, traceId, operationId, safeStage(stage), binding, safe(details).take(MAX_DETAILS), critical)
        while (events.size >= MAX_EVENTS) { events.removeFirst(); droppedEvents += 1L }
        events.addLast(event)
        if (critical) criticalCount += 1L
    }

    private fun TraceEvent.toLine(): String = buildString {
        append("s20seq=").append(seq)
        append(" | mono_ns=").append(atNs)
        append(" | cycle=").append(cycleId ?: -1)
        append(" | trace=").append(traceId ?: "none")
        append(" | op=").append(operationId ?: "none")
        append(" | stage=").append(stage)
        binding?.let {
            append(" | sg=").append(it.screenGeneration)
            append(" | wg=").append(it.windowGeneration)
            append(" | hash=").append(it.screenHash ?: 0)
            append(" | sigHash=").append(it.addressSignature?.hashCode() ?: 0)
        }
        if (details.isNotBlank()) append(" | ").append(details)
    }

    private fun trimCycles() { while (cycles.size > MAX_ATTEMPTS * 4) cycles.remove(cycles.keys.first()) }
    private fun trimAttempts() {
        while (attempts.size > MAX_ATTEMPTS) {
            val first = attempts.entries.first()
            attempts.remove(first.key)
            traceByBinding.entries.removeAll { it.value == first.key }
        }
    }
    private fun duration(start: Long?, end: Long?): Long = if (start == null || end == null) -1L else us(start, end)
    private fun us(startNs: Long, endNs: Long): Long = ((endNs - startNs).coerceAtLeast(0L)) / 1_000L
    private fun distanceEquals(a: Double?, b: Double?): Boolean = when {
        a == null && b == null -> true
        a == null || b == null -> false
        else -> abs(a - b) <= 0.005
    }
    private fun isFinalColor(color: String): Boolean {
        val normalized = color.lowercase(Locale.ROOT)
        return normalized.contains("green") || normalized.contains("verde") || normalized.contains("red") || normalized.contains("vermelh")
    }
    private fun bindingText(binding: BindingSnapshot?): String = binding?.let { "sg=${it.screenGeneration},wg=${it.windowGeneration},hash=${it.screenHash ?: 0},sigHash=${it.addressSignature?.hashCode() ?: 0}" } ?: "none"
    private fun safeStage(value: String): String = buildString(minOf(value.length, 120)) { value.take(120).forEach { ch -> append(if (ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == '-') ch else '_') } }.ifBlank { "EVENT" }
    private fun safe(value: String?): String = value.orEmpty().replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').replace(Regex("\\s{2,}"), " ").trim()
}
