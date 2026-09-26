package br.com.mapeiaia.rotacerta

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Stage38 — caixa-preta forense máxima, estritamente diagnóstica.
 *
 * A implementação continua sem polling, OCR, screenshot ou rede próprios. Em FORENSIC_MAX ela
 * adiciona prova criptográfica, origem do probe e checkpoints de silêncio causal somente quando
 * um evento real chega; portanto nenhum timer é criado e nenhuma decisão funcional é alterada.
 */
object FarolMaximumForensicsStage38 {
    const val CONTRACT_MARKER = "FAROL_MAXIMUM_FORENSICS_STAGE38"
    const val CLOCK_MARKER = "ELAPSED_REALTIME_NANOS_STAGE38"
    const val EVENT_DRIVEN_MARKER = "EVENT_DRIVEN_NO_POLLING_STAGE38"
    const val DIAGNOSTIC_ONLY_MARKER = "DIAGNOSTIC_ONLY_NO_BEHAVIOR_AUTHORITY_STAGE38"
    const val CAUSAL_CHAIN_MARKER = "EVENT_TO_PIXEL_TO_OCR_TO_ADDRESS_TO_ROUTE_TO_PAINT_STAGE38"
    const val CRYPTO_CHAIN_MARKER = "SHA256_APPEND_ONLY_EVENT_CHAIN_STAGE38_01556"
    const val TRACE_COMPLETENESS_MARKER = "TRACE_INCOMPLETE_ON_ANY_DROP_STAGE38_01556"
    const val MAX_EVENTS = 32_768
    const val MAX_DETAILS = 1_600
    private const val MAX_CRITICAL_EVENTS = 12_288
    private const val MAX_REGULAR_EVENTS = MAX_EVENTS - MAX_CRITICAL_EVENTS
    private const val MAX_OVERHEAD_SAMPLES = 4_096
    private const val HEARTBEAT_TARGET_NS = 100_000_000L
    private const val HEARTBEAT_LAG_NS = 150_000_000L
    private const val CAUSAL_GAP_NS = 500_000_000L
    private const val GENESIS_HASH = "GENESIS"

    enum class Mode { OFF, FORENSIC_BASIC, FORENSIC_MAX }
    enum class Priority { REGULAR, CRITICAL }
    enum class FaultCode {
        SOURCE_NOT_CAPTURED,
        ACCESSIBILITY_EMPTY,
        OCR_NOT_STARTED,
        OCR_BUSY,
        OCR_NO_TEXT,
        PARSER_REJECTED,
        DESTINATION_MISSING,
        ROUTE_NOT_REQUESTED,
        ROUTE_FAILED,
        DECISION_NOT_CREATED,
        PAINT_NOT_REQUESTED,
        PAINT_NOT_APPLIED,
        PIXEL_MISMATCH,
        TIMEOUT,
    }

    data class ByteEnvelope(
        val length: Int,
        val contentType: String,
        val encoding: String,
        val sha256: String,
    )

    data class Event(
        val seq: Long,
        val atNs: Long,
        val wallMs: Long,
        val stage: String,
        val packageName: String?,
        val cycleId: Long?,
        val traceId: String?,
        val operationId: String?,
        val threadName: String,
        val details: String,
        val sessionId: String? = null,
        val attemptId: String? = null,
        val probeId: String? = null,
        val sourceOrigin: String? = null,
        val detailsUtf8Bytes: Int = 0,
        val detailsSha256: String = "",
        val previousHash: String = GENESIS_HASH,
        val eventHash: String = "",
        val priority: Priority = Priority.REGULAR,
        val faultCode: FaultCode? = null,
    )

    data class Snapshot(
        val events: List<Event>,
        val dropped: Long,
        val sessionStartNs: Long,
        val sessionStartWallMs: Long,
        val lastNs: Long,
        val recordCalls: Long,
        val recordOverheadTotalNs: Long,
        val recordOverheadMaxNs: Long,
        val mode: Mode = Mode.FORENSIC_BASIC,
        val sessionId: String? = null,
        val rootHash: String = GENESIS_HASH,
        val droppedCritical: Long = 0L,
        val droppedRegular: Long = 0L,
        val traceComplete: Boolean = true,
        val faultCounts: Map<FaultCode, Long> = emptyMap(),
    )

    private val lock = Any()
    private val sequence = AtomicLong(0L)
    private val attemptSequence = AtomicLong(0L)
    private val criticalEvents = ArrayDeque<Event>(MAX_CRITICAL_EVENTS)
    private val regularEvents = ArrayDeque<Event>(MAX_REGULAR_EVENTS)
    private val stageCounts = LinkedHashMap<String, Long>()
    private val faultCounts = LinkedHashMap<FaultCode, Long>()
    private val overheadSamples = ArrayDeque<Long>(MAX_OVERHEAD_SAMPLES)
    private val activeAttemptByPackage = LinkedHashMap<String, String>()
    private val lastAttemptEventNs = LinkedHashMap<String, Long>()

    @Volatile
    private var configuredMode: Mode = defaultMode()
    private var droppedCriticalEvents = 0L
    private var droppedRegularEvents = 0L
    private var sessionStartNs = 0L
    private var sessionStartWallMs = 0L
    private var sessionId: String? = null
    private var hashHead = GENESIS_HASH
    private var lastNs = 0L
    private var recordCalls = 0L
    private var recordOverheadTotalNs = 0L
    private var recordOverheadMaxNs = 0L

    fun mode(): Mode = configuredMode

    /** Debug/diagnostic control only; it cannot authorize Reading or mutate product state. */
    fun setMode(mode: Mode) {
        synchronized(lock) {
            configuredMode = mode
            if (mode == Mode.OFF) {
                activeAttemptByPackage.clear()
                lastAttemptEventNs.clear()
            }
        }
    }

    /**
     * Registra um acontecimento já ocorrido. O relógio causal é fornecido explicitamente pelo
     * chamador. FORENSIC_MAX captura stack do probe somente dentro de uma tentativa ativa.
     */
    fun record(
        atNs: Long,
        wallMs: Long,
        stage: String,
        packageName: String? = null,
        cycleId: Long? = null,
        traceId: String? = null,
        operationId: String? = null,
        details: String = "",
        threadName: String = Thread.currentThread().name,
    ): Long {
        if (configuredMode == Mode.OFF) return -1L
        if (configuredMode == Mode.FORENSIC_BASIC && !isBasicCheckpoint(stage)) return -1L
        val overheadStartNs = System.nanoTime()
        val seq: Long
        synchronized(lock) {
            ensureSessionLocked(atNs, wallMs)
            lastNs = maxOf(lastNs, atNs)
            val safeStage = sanitize(stage).ifBlank { "EVENT" }.take(140)
            val safePackage = sanitize(packageName.orEmpty()).ifBlank { null }
            val safeTrace = sanitize(traceId.orEmpty()).ifBlank { null }
            val safeOperation = sanitize(operationId.orEmpty()).ifBlank { null }
            val safeThread = sanitize(threadName).ifBlank { "unknown" }.take(100)
            val safeDetails = sanitizeDetails(details).take(if (configuredMode == Mode.FORENSIC_MAX) MAX_DETAILS else 320)
            val attemptId = resolveAttemptLocked(
                safeStage,
                safeDetails,
                safePackage,
                cycleId,
                safeTrace,
                safeOperation,
            )
            val fault = classifyFault(safeStage, safeDetails)
            val priority = if (isCritical(safeStage, attemptId, fault)) Priority.CRITICAL else Priority.REGULAR

            maybeAppendHeartbeatLocked(atNs, wallMs, safePackage, attemptId, safeThread)
            seq = appendEventLocked(
                atNs = atNs,
                wallMs = wallMs,
                stage = safeStage,
                packageName = safePackage,
                cycleId = cycleId,
                traceId = safeTrace,
                operationId = safeOperation,
                threadName = safeThread,
                details = safeDetails,
                attemptId = attemptId,
                priority = priority,
                faultCode = fault,
                sourceOrigin = if (configuredMode == Mode.FORENSIC_MAX && attemptId != null) callerSourceOrigin() else null,
            )
            if (attemptId != null) lastAttemptEventNs[attemptId] = atNs
            if (fault != null) faultCounts[fault] = (faultCounts[fault] ?: 0L) + 1L
            if (attemptId != null && isTerminalStage(safeStage, fault)) {
                activeAttemptByPackage.entries.removeAll { it.value == attemptId }
                lastAttemptEventNs.remove(attemptId)
            }
        }
        val cost = (System.nanoTime() - overheadStartNs).coerceAtLeast(0L)
        synchronized(lock) {
            recordCalls += 1L
            recordOverheadTotalNs += cost
            if (cost > recordOverheadMaxNs) recordOverheadMaxNs = cost
            if (overheadSamples.size >= MAX_OVERHEAD_SAMPLES) overheadSamples.removeFirst()
            overheadSamples.addLast(cost)
        }
        return seq
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        val allEvents = mergedEventsLocked()
        Snapshot(
            events = allEvents,
            dropped = droppedCriticalEvents + droppedRegularEvents,
            sessionStartNs = sessionStartNs,
            sessionStartWallMs = sessionStartWallMs,
            lastNs = lastNs,
            recordCalls = recordCalls,
            recordOverheadTotalNs = recordOverheadTotalNs,
            recordOverheadMaxNs = recordOverheadMaxNs,
            mode = configuredMode,
            sessionId = sessionId,
            rootHash = hashHead,
            droppedCritical = droppedCriticalEvents,
            droppedRegular = droppedRegularEvents,
            traceComplete = droppedCriticalEvents == 0L && droppedRegularEvents == 0L,
            faultCounts = faultCounts.toMap(),
        )
    }

    fun byteEnvelope(
        bytes: ByteArray,
        contentType: String = "application/octet-stream",
        encoding: String = "binary",
    ): ByteEnvelope = ByteEnvelope(
        length = bytes.size,
        contentType = sanitize(contentType).ifBlank { "application/octet-stream" }.take(120),
        encoding = sanitize(encoding).ifBlank { "binary" }.take(40),
        sha256 = sha256(bytes),
    )

    /** Retorna intervalos inclusivos de bytes diferentes, sem persistir o payload bruto. */
    fun diffByteRanges(expected: ByteArray, actual: ByteArray, maxRanges: Int = 64): String {
        if (expected.contentEquals(actual)) return ""
        val limit = maxOf(expected.size, actual.size)
        val ranges = mutableListOf<String>()
        var start = -1
        var index = 0
        while (index < limit) {
            val differs = index >= expected.size || index >= actual.size || expected[index] != actual[index]
            if (differs && start < 0) start = index
            val closes = start >= 0 && (!differs || index == limit - 1)
            if (closes) {
                val end = if (differs && index == limit - 1) index else index - 1
                ranges += if (start == end) "$start" else "$start-$end"
                start = -1
                if (ranges.size >= maxRanges) break
            }
            index += 1
        }
        return ranges.joinToString(",")
    }

    /** Serialização estável para snapshots/provas sem depender da ordem de Map do chamador. */
    fun deterministicSnapshot(fields: Map<String, String?>): String = fields.entries
        .sortedBy { it.key }
        .joinToString("\n") { (key, value) ->
            "${escapeCanonical(key)}=${escapeCanonical(value.orEmpty())}"
        }

    fun deterministicSnapshotHash(fields: Map<String, String?>): String =
        sha256(deterministicSnapshot(fields).toByteArray(Charsets.UTF_8))

    fun exportReport(): String = synchronized(lock) {
        val allEvents = mergedEventsLocked()
        val sortedOverhead = overheadSamples.sorted()
        val overheadMedian = percentile(sortedOverhead, 50)
        val overheadP95 = percentile(sortedOverhead, 95)
        val firstAvailableNs = allEvents.firstOrNull()?.atNs ?: sessionStartNs
        val gaps = detectForensicGaps(allEvents)
        var previousNs: Long? = null
        buildString {
            appendLine("ROTA CERTA — STAGE38 FORENSE MÁXIMO NANOSSEGUNDO A NANOSSEGUNDO")
            appendLine("marker=$CONTRACT_MARKER")
            appendLine("clock=$CLOCK_MARKER")
            appendLine("mode=$EVENT_DRIVEN_MARKER; configuredMode=$configuredMode")
            appendLine("authority=$DIAGNOSTIC_ONLY_MARKER")
            appendLine("causalChain=$CAUSAL_CHAIN_MARKER")
            appendLine("cryptoChain=$CRYPTO_CHAIN_MARKER")
            appendLine("completenessPolicy=$TRACE_COMPLETENESS_MARKER")
            appendLine("sessionId=${sessionId.orEmpty()}; rootHash=$hashHead")
            appendLine("events=${allEvents.size}; dropped=${droppedCriticalEvents + droppedRegularEvents}; droppedCritical=$droppedCriticalEvents; droppedRegular=$droppedRegularEvents; capacity=$MAX_EVENTS")
            appendLine("traceComplete=${droppedCriticalEvents == 0L && droppedRegularEvents == 0L}; retainedChainLinked=${verifyRetainedChain(allEvents)}")
            appendLine("sessionStartNs=$sessionStartNs; firstAvailableNs=$firstAvailableNs; lastNs=$lastNs")
            appendLine("recordCalls=$recordCalls; observerOverheadTotal_ns=$recordOverheadTotalNs; observerOverheadTotal_us=${recordOverheadTotalNs / 1_000L}; observerOverheadMedian_ns=$overheadMedian; observerOverheadP95_ns=$overheadP95; observerOverheadMax_ns=$recordOverheadMaxNs")
            appendLine("note=nenhum timer/polling/screenshot/OCR/rede e criado por este gravador; heartbeat mede silencio causal somente na chegada de evento real")
            appendLine()
            appendLine("--- CONTADORES POR ESTÁGIO ---")
            stageCounts.entries.sortedByDescending { it.value }.forEach { (eventStage, count) ->
                appendLine("$eventStage=$count")
            }
            appendLine()
            appendLine("--- TAXONOMIA DE FALHAS ---")
            if (faultCounts.isEmpty()) appendLine("(nenhuma falha classificada)")
            faultCounts.entries.sortedByDescending { it.value }.forEach { (fault, count) -> appendLine("$fault=$count") }
            appendLine()
            appendLine("--- FORENSIC GAP DETECTOR ---")
            if (gaps.isEmpty()) appendLine("(nenhum gap estrutural detectado no trecho retido)")
            gaps.forEach(::appendLine)
            appendLine()
            appendLine("--- CRONOLOGIA CAUSAL COMPLETA ---")
            if (allEvents.isEmpty()) appendLine("(sem eventos Stage38)")
            allEvents.forEach { event ->
                val deltaNs = previousNs?.let { (event.atNs - it).coerceAtLeast(0L) } ?: 0L
                val fromSessionNs = if (sessionStartNs > 0L) (event.atNs - sessionStartNs).coerceAtLeast(0L) else 0L
                append("s38seq=").append(event.seq)
                append(" | wall=").append(formatWall(event.wallMs))
                append(" | mono_ns=").append(event.atNs)
                append(" | from_start_ns=").append(fromSessionNs)
                append(" | delta_ns=").append(deltaNs)
                append(" | delta_us=").append(deltaNs / 1_000L)
                append(" | delta_ms=").append(String.format(Locale.US, "%.6f", deltaNs / 1_000_000.0))
                append(" | thread=").append(event.threadName)
                append(" | stage=").append(event.stage)
                append(" | priority=").append(event.priority)
                append(" | session=").append(event.sessionId.orEmpty())
                event.attemptId?.let { append(" | attempt=").append(it) }
                event.probeId?.let { append(" | probe=").append(it) }
                event.sourceOrigin?.let { append(" | source=").append(it) }
                event.packageName?.let { append(" | package=").append(it) }
                event.cycleId?.let { append(" | cycle=").append(it) }
                event.traceId?.let { append(" | trace=").append(it) }
                event.operationId?.let { append(" | op=").append(it) }
                event.faultCode?.let { append(" | fault=").append(it) }
                append(" | details_bytes=").append(event.detailsUtf8Bytes)
                append(" | details_sha256=").append(event.detailsSha256)
                append(" | prev_hash=").append(event.previousHash)
                append(" | event_hash=").append(event.eventHash)
                if (event.details.isNotBlank()) append(" | ").append(sanitizeDetailsForExport(event.details))
                appendLine()
                previousNs = event.atNs
            }
        }.trimEnd()
    }

    internal fun detectForensicGaps(input: List<Event>): List<String> {
        val gaps = linkedSetOf<String>()
        val byAttempt = input.filter { it.attemptId != null }.groupBy { it.attemptId!! }
        byAttempt.forEach { (attempt, attemptEvents) ->
            val ordered = attemptEvents.sortedBy { it.seq }
            ordered.zipWithNext().forEach { (before, after) ->
                val gap = (after.atNs - before.atNs).coerceAtLeast(0L)
                if (gap >= CAUSAL_GAP_NS) {
                    gaps += "FORENSIC_GAP attempt=$attempt expected=causal_checkpoint after=${before.stage} before=${after.stage} gap_ms=${gap / 1_000_000L}"
                }
            }
            fun has(predicate: (String) -> Boolean): Boolean = ordered.any { predicate(it.stage.uppercase(Locale.ROOT)) }
            if (has { it.contains("OCR_REQUEST") } &&
                !has { it.contains("SCREENSHOT") || it.contains("OCR_EXTRACT") || it.contains("OCR_RESULT") } &&
                has { it.contains("ROUTE") || it.contains("DECISION") || it.contains("PAINT") }
            ) {
                gaps += "FORENSIC_GAP attempt=$attempt expected=SCREENSHOT_OR_OCR_EXTRACT after=OCR_REQUEST"
            }
            if (has { it.contains("ROUTE_REQUEST") } &&
                !has { it.contains("ROUTE_RESPONSE") || it.contains("ROUTE_RESULT") } &&
                has { it.contains("DECISION") || it.contains("PAINT") }
            ) {
                gaps += "FORENSIC_GAP attempt=$attempt expected=ROUTE_RESPONSE after=ROUTE_REQUEST"
            }
            if (has { it.contains("DECISION") && (it.contains("CREATE") || it.contains("FINAL")) } &&
                !has { it.contains("PAINT_REQUEST") } &&
                has { it.contains("PAINT_APPLIED") || it.contains("OVERLAY_APPLIED") }
            ) {
                gaps += "FORENSIC_GAP attempt=$attempt expected=PAINT_REQUESTED before=PAINT_APPLIED"
            }
        }
        return gaps.toList()
    }

    internal fun verifyRetainedChain(input: List<Event>): Boolean {
        val ordered = input.sortedBy { it.seq }
        if (ordered.size < 2) return true
        return ordered.zipWithNext().all { (before, after) -> after.previousHash == before.eventHash }
    }

    internal fun resetForTests() = synchronized(lock) {
        sequence.set(0L)
        attemptSequence.set(0L)
        criticalEvents.clear()
        regularEvents.clear()
        stageCounts.clear()
        faultCounts.clear()
        overheadSamples.clear()
        activeAttemptByPackage.clear()
        lastAttemptEventNs.clear()
        configuredMode = defaultMode()
        droppedCriticalEvents = 0L
        droppedRegularEvents = 0L
        sessionStartNs = 0L
        sessionStartWallMs = 0L
        sessionId = null
        hashHead = GENESIS_HASH
        lastNs = 0L
        recordCalls = 0L
        recordOverheadTotalNs = 0L
        recordOverheadMaxNs = 0L
    }

    private fun ensureSessionLocked(atNs: Long, wallMs: Long) {
        if (sessionStartNs != 0L) return
        sessionStartNs = atNs
        sessionStartWallMs = wallMs
        sessionId = "s38-${sha256("$wallMs:$atNs".toByteArray(Charsets.UTF_8)).take(20)}"
    }

    private fun resolveAttemptLocked(
        stage: String,
        details: String,
        packageName: String?,
        cycleId: Long?,
        traceId: String?,
        operationId: String?,
    ): String? {
        val key = packageName ?: "_global"
        val explicit = when {
            cycleId != null -> "${sessionId.orEmpty()}-cycle-$cycleId"
            !traceId.isNullOrBlank() -> "${sessionId.orEmpty()}-trace-${stableToken(traceId)}"
            !operationId.isNullOrBlank() -> "${sessionId.orEmpty()}-op-${stableToken(operationId)}"
            else -> null
        }
        if (explicit != null) {
            activeAttemptByPackage[key] = explicit
            return explicit
        }
        activeAttemptByPackage[key]?.let { return it }
        if (!shouldStartImplicitAttempt(stage, details)) return null
        val created = "${sessionId.orEmpty()}-a${attemptSequence.incrementAndGet()}"
        activeAttemptByPackage[key] = created
        return created
    }

    private fun shouldStartImplicitAttempt(stage: String, details: String): Boolean {
        val upper = stage.uppercase(Locale.ROOT)
        if (upper.contains("OCR_REQUEST") || upper.contains("SCREENSHOT_REQUEST") || upper.contains("ROUTE_REQUEST")) return true
        if (upper.contains("CANDIDATE") && !upper.contains("REJECT")) return true
        if (upper.contains("PRECOLLECT_ADMISSION") && details.contains("heavyCollect=true", ignoreCase = true)) return true
        return false
    }

    private fun maybeAppendHeartbeatLocked(
        atNs: Long,
        wallMs: Long,
        packageName: String?,
        attemptId: String?,
        threadName: String,
    ) {
        if (configuredMode != Mode.FORENSIC_MAX || attemptId == null) return
        val previous = lastAttemptEventNs[attemptId] ?: return
        val interval = (atNs - previous).coerceAtLeast(0L)
        if (interval < HEARTBEAT_TARGET_NS) return
        val drift = interval - HEARTBEAT_TARGET_NS
        val lag = interval >= HEARTBEAT_LAG_NS
        appendEventLocked(
            atNs = atNs,
            wallMs = wallMs,
            stage = if (lag) "S38_HEARTBEAT_LAG" else "S38_HEARTBEAT_CHECKPOINT",
            packageName = packageName,
            cycleId = null,
            traceId = null,
            operationId = null,
            threadName = threadName,
            details = "target_ms=100; observed_on_next_real_event=true; interval_ns=$interval; drift_ns=$drift; lag=$lag",
            attemptId = attemptId,
            priority = Priority.CRITICAL,
            faultCode = null,
            sourceOrigin = null,
        )
    }

    private fun appendEventLocked(
        atNs: Long,
        wallMs: Long,
        stage: String,
        packageName: String?,
        cycleId: Long?,
        traceId: String?,
        operationId: String?,
        threadName: String,
        details: String,
        attemptId: String?,
        priority: Priority,
        faultCode: FaultCode?,
        sourceOrigin: String?,
    ): Long {
        val seq = sequence.incrementAndGet()
        val detailsBytes = details.toByteArray(Charsets.UTF_8)
        val detailsHash = sha256(detailsBytes)
        val probeId = stableProbeId(stage)
        val previousHash = hashHead
        val canonical = buildString {
            append(seq).append('|').append(atNs).append('|').append(wallMs).append('|')
            append(stage).append('|').append(packageName.orEmpty()).append('|')
            append(cycleId ?: -1L).append('|').append(traceId.orEmpty()).append('|')
            append(operationId.orEmpty()).append('|').append(threadName).append('|')
            append(sessionId.orEmpty()).append('|').append(attemptId.orEmpty()).append('|')
            append(probeId).append('|').append(sourceOrigin.orEmpty()).append('|')
            append(detailsBytes.size).append('|').append(detailsHash).append('|')
            append(priority.name).append('|').append(faultCode?.name.orEmpty()).append('|')
            append(previousHash)
        }
        val eventHash = sha256(canonical.toByteArray(Charsets.UTF_8))
        hashHead = eventHash
        val event = Event(
            seq = seq,
            atNs = atNs,
            wallMs = wallMs,
            stage = stage,
            packageName = packageName,
            cycleId = cycleId,
            traceId = traceId,
            operationId = operationId,
            threadName = threadName,
            details = details,
            sessionId = sessionId,
            attemptId = attemptId,
            probeId = probeId,
            sourceOrigin = sourceOrigin,
            detailsUtf8Bytes = detailsBytes.size,
            detailsSha256 = detailsHash,
            previousHash = previousHash,
            eventHash = eventHash,
            priority = priority,
            faultCode = faultCode,
        )
        appendToPriorityBufferLocked(event)
        stageCounts[stage] = (stageCounts[stage] ?: 0L) + 1L
        return seq
    }

    private fun appendToPriorityBufferLocked(event: Event) {
        if (event.priority == Priority.CRITICAL) {
            while (criticalEvents.size >= MAX_CRITICAL_EVENTS) {
                criticalEvents.removeFirst()
                droppedCriticalEvents += 1L
            }
            criticalEvents.addLast(event)
        } else {
            while (regularEvents.size >= MAX_REGULAR_EVENTS) {
                regularEvents.removeFirst()
                droppedRegularEvents += 1L
            }
            regularEvents.addLast(event)
        }
    }

    private fun mergedEventsLocked(): List<Event> = (regularEvents.toList() + criticalEvents.toList()).sortedBy { it.seq }

    private fun isCritical(stage: String, attemptId: String?, fault: FaultCode?): Boolean {
        if (attemptId != null || fault != null) return true
        val upper = stage.uppercase(Locale.ROOT)
        return listOf("ERROR", "FAIL", "ANOMALY", "OCR", "ROUTE", "DECISION", "PAINT", "SCREENSHOT").any(upper::contains)
    }

    private fun isTerminalStage(stage: String, fault: FaultCode?): Boolean {
        val upper = stage.uppercase(Locale.ROOT)
        if (upper.contains("ATTEMPT_END") || upper.contains("PAINT_APPLIED") || upper.contains("VISUAL_VERIFY_DONE")) return true
        return fault in setOf(
            FaultCode.ROUTE_FAILED,
            FaultCode.PAINT_NOT_APPLIED,
            FaultCode.DECISION_NOT_CREATED,
            FaultCode.TIMEOUT,
        )
    }

    private fun classifyFault(stage: String, details: String): FaultCode? {
        val upper = stage.uppercase(Locale.ROOT)
        val detailUpper = details.uppercase(Locale.ROOT)
        return when {
            upper.contains("SOURCE") && (upper.contains("MISSING") || detailUpper.contains("SOURCE_NOT_CAPTURED")) -> FaultCode.SOURCE_NOT_CAPTURED
            upper.contains("ACCESSIBILITY") && (upper.contains("EMPTY") || detailUpper.contains("EMPTY=true")) -> FaultCode.ACCESSIBILITY_EMPTY
            upper.contains("OCR") && (upper.contains("NOT_STARTED") || detailUpper.contains("OCR_NOT_STARTED")) -> FaultCode.OCR_NOT_STARTED
            upper.contains("OCR") && (upper.contains("BUSY") || detailUpper.contains("BUSY=true")) -> FaultCode.OCR_BUSY
            upper.contains("OCR") && (upper.contains("NO_TEXT") || detailUpper.contains("NO_TEXT")) -> FaultCode.OCR_NO_TEXT
            (upper.contains("PARSER") || upper.contains("CANDIDATE")) && upper.contains("REJECT") -> FaultCode.PARSER_REJECTED
            upper.contains("DESTINATION") && (upper.contains("MISSING") || detailUpper.contains("DESTINATION_MISSING")) -> FaultCode.DESTINATION_MISSING
            upper.contains("ROUTE") && upper.contains("NOT_REQUEST") -> FaultCode.ROUTE_NOT_REQUESTED
            upper.contains("ROUTE") && (upper.contains("FAIL") || upper.contains("ERROR")) -> FaultCode.ROUTE_FAILED
            upper.contains("DECISION") && upper.contains("NOT_CREATED") -> FaultCode.DECISION_NOT_CREATED
            upper.contains("PAINT") && upper.contains("NOT_REQUEST") -> FaultCode.PAINT_NOT_REQUESTED
            upper.contains("PAINT") && (upper.contains("NOT_APPLIED") || upper.contains("FAIL")) -> FaultCode.PAINT_NOT_APPLIED
            (upper.contains("PIXEL") || upper.contains("VISUAL")) && upper.contains("MISMATCH") -> FaultCode.PIXEL_MISMATCH
            upper.contains("TIMEOUT") || detailUpper.contains("TIMEOUT=true") -> FaultCode.TIMEOUT
            else -> null
        }
    }

    private fun callerSourceOrigin(): String? = runCatching {
        Throwable().stackTrace.firstOrNull { frame ->
            !frame.className.contains("FarolMaximumForensicsStage38") &&
                !frame.className.startsWith("java.lang.Thread")
        }?.let { frame ->
            "${frame.fileName.orEmpty()}:${frame.lineNumber}#${frame.className.substringAfterLast('.')}.${frame.methodName}"
        }
    }.getOrNull()

    private fun stableProbeId(stage: String): String = "probe-${stableToken(stage)}"

    private fun stableToken(value: String): String = sha256(value.toByteArray(Charsets.UTF_8)).take(16)

    private fun sanitize(value: String): String = value
        .replace('\u0000', ' ')
        .replace('\r', ' ')
        .replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun sanitizeDetails(value: String): String = value
        .replace('\u0000', ' ')
        .replace("\r\n", "\\n")
        .replace('\r', '\n')
        .replace("\n", "\\n")
        .trim()

    /** Export-only privacy barrier; runtime evidence remains local and behavior-neutral. */
    private fun sanitizeDetailsForExport(value: String): String = sanitizeDetails(value)
        .replace(
            Regex("(?i)\\b(eventText|accessibilityText|rawText|messageText|typedText)\\s*[:=]\\s*([^|;]+)"),
        ) { match -> "${match.groupValues[1]}=[texto mascarado]" }
        .replace(
            Regex("(?<!\\d)(?:\\+?55\\s*)?(?:\\(?\\d{2}\\)?\\s*)?9?\\d{4}[-\\s]?\\d{4}(?!\\d)"),
            "[telefone mascarado]",
        )
        .replace(
            Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
            "[email mascarado]",
        )
        .replace(Regex("(?i)https?://[^\\s|;]+"), "[url mascarada]")
        .replace(
            Regex("(?i)\\b(token|cookie|authorization|password|senha|secret|jwt|sessionToken|accessToken|viewToken)\\s*[:=]\\s*[^|;\\s]+"),
        ) { match -> "${match.groupValues[1]}=[segredo mascarado]" }

    private fun escapeCanonical(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("=", "\\=")

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun percentile(sorted: List<Long>, percentile: Int): Long {
        if (sorted.isEmpty()) return -1L
        val index = (((sorted.size - 1) * percentile) / 100.0).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun formatWall(wallMs: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(wallMs))

    private fun isBasicCheckpoint(stage: String): Boolean {
        val s = stage.uppercase(Locale.ROOT)
        return BASIC_CHECKPOINT_TOKENS.any(s::contains)
    }

    private val BASIC_CHECKPOINT_TOKENS = setOf(
        "CARD_DETECTED", "LEASE", "SCREENSHOT_CALLBACK", "SCREENSHOT_FAILURE",
        "OCR_EXTRACT_START", "OCR_EXTRACT_END", "OCR_EVALUATION_RESULT",
        "CANDIDATE_SEMANTIC_VALIDATION", "CACHE_RESULT", "DISTANCE_CALCULATED",
        "VISUAL_AUTHORITY_DECISION", "OVERLAY_RENDER_APPLIED", "STALE",
    )

    private fun defaultMode(): Mode = Mode.FORENSIC_BASIC
}
