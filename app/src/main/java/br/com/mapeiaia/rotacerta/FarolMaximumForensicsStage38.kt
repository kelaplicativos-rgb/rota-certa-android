package br.com.mapeiaia.rotacerta

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Stage38 — caixa-preta forense máxima, estritamente diagnóstica.
 *
 * Não agenda timers, não faz OCR, não chama rede e não participa de nenhuma decisão funcional.
 * Cada chamada representa um acontecimento real já ocorrido no runtime e recebe do chamador o
 * timestamp monotônico Android (SystemClock.elapsedRealtimeNanos()).
 */
object FarolMaximumForensicsStage38 {
    const val CONTRACT_MARKER = "FAROL_MAXIMUM_FORENSICS_STAGE38"
    const val CLOCK_MARKER = "ELAPSED_REALTIME_NANOS_STAGE38"
    const val EVENT_DRIVEN_MARKER = "EVENT_DRIVEN_NO_POLLING_STAGE38"
    const val DIAGNOSTIC_ONLY_MARKER = "DIAGNOSTIC_ONLY_NO_BEHAVIOR_AUTHORITY_STAGE38"
    const val CAUSAL_CHAIN_MARKER = "EVENT_TO_PIXEL_TO_OCR_TO_ADDRESS_TO_ROUTE_TO_PAINT_STAGE38"
    const val MAX_EVENTS = 32_768
    const val MAX_DETAILS = 1_600
    private const val MAX_OVERHEAD_SAMPLES = 4_096

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
    )

    private val lock = Any()
    private val sequence = AtomicLong(0L)
    private val events = ArrayDeque<Event>(MAX_EVENTS)
    private val stageCounts = LinkedHashMap<String, Long>()
    private val overheadSamples = ArrayDeque<Long>(MAX_OVERHEAD_SAMPLES)

    private var droppedEvents = 0L
    private var sessionStartNs = 0L
    private var sessionStartWallMs = 0L
    private var lastNs = 0L
    private var recordCalls = 0L
    private var recordOverheadTotalNs = 0L
    private var recordOverheadMaxNs = 0L

    /**
     * Registra um acontecimento já ocorrido. O relógio causal é fornecido explicitamente pelo
     * chamador para que o Stage38 nunca precise criar polling, sleep ou agendamento próprio.
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
        val overheadStartNs = System.nanoTime()
        val seq: Long
        synchronized(lock) {
            if (sessionStartNs == 0L) {
                sessionStartNs = atNs
                sessionStartWallMs = wallMs
            }
            lastNs = maxOf(lastNs, atNs)
            seq = sequence.incrementAndGet()
            while (events.size >= MAX_EVENTS) {
                events.removeFirst()
                droppedEvents += 1L
            }
            val safeStage = sanitize(stage).ifBlank { "EVENT" }.take(140)
            events.addLast(
                Event(
                    seq = seq,
                    atNs = atNs,
                    wallMs = wallMs,
                    stage = safeStage,
                    packageName = sanitize(packageName.orEmpty()).ifBlank { null },
                    cycleId = cycleId,
                    traceId = sanitize(traceId.orEmpty()).ifBlank { null },
                    operationId = sanitize(operationId.orEmpty()).ifBlank { null },
                    threadName = sanitize(threadName).ifBlank { "unknown" }.take(100),
                    details = sanitizeDetails(details).take(MAX_DETAILS),
                ),
            )
            stageCounts[safeStage] = (stageCounts[safeStage] ?: 0L) + 1L
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
        Snapshot(
            events = events.toList(),
            dropped = droppedEvents,
            sessionStartNs = sessionStartNs,
            sessionStartWallMs = sessionStartWallMs,
            lastNs = lastNs,
            recordCalls = recordCalls,
            recordOverheadTotalNs = recordOverheadTotalNs,
            recordOverheadMaxNs = recordOverheadMaxNs,
        )
    }

    fun exportReport(): String = synchronized(lock) {
        val sortedOverhead = overheadSamples.sorted()
        val overheadMedian = percentile(sortedOverhead, 50)
        val overheadP95 = percentile(sortedOverhead, 95)
        val firstAvailableNs = events.firstOrNull()?.atNs ?: sessionStartNs
        var previousNs: Long? = null
        buildString {
            appendLine("ROTA CERTA — STAGE38 FORENSE MÁXIMO NANOSSEGUNDO A NANOSSEGUNDO")
            appendLine("marker=$CONTRACT_MARKER")
            appendLine("clock=$CLOCK_MARKER")
            appendLine("mode=$EVENT_DRIVEN_MARKER")
            appendLine("authority=$DIAGNOSTIC_ONLY_MARKER")
            appendLine("causalChain=$CAUSAL_CHAIN_MARKER")
            appendLine("events=${events.size}; dropped=$droppedEvents; capacity=$MAX_EVENTS")
            appendLine("sessionStartNs=$sessionStartNs; firstAvailableNs=$firstAvailableNs; lastNs=$lastNs")
            appendLine("recordCalls=$recordCalls; observerOverheadTotal_ns=$recordOverheadTotalNs; observerOverheadTotal_us=${recordOverheadTotalNs / 1_000L}; observerOverheadMedian_ns=$overheadMedian; observerOverheadP95_ns=$overheadP95; observerOverheadMax_ns=$recordOverheadMaxNs")
            appendLine("note=nenhum timer/polling/screenshot/OCR/rede e criado por este gravador; ele somente observa call-sites reais")
            appendLine()
            appendLine("--- CONTADORES POR ESTÁGIO ---")
            stageCounts.entries.sortedByDescending { it.value }.forEach { (stage, count) ->
                appendLine("$stage=$count")
            }
            appendLine()
            appendLine("--- CRONOLOGIA CAUSAL COMPLETA ---")
            if (events.isEmpty()) appendLine("(sem eventos Stage38)")
            events.forEach { event ->
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
                event.packageName?.let { append(" | package=").append(it) }
                event.cycleId?.let { append(" | cycle=").append(it) }
                event.traceId?.let { append(" | trace=").append(it) }
                event.operationId?.let { append(" | op=").append(it) }
                if (event.details.isNotBlank()) append(" | ").append(sanitizeDetailsForExport(event.details))
                appendLine()
                previousNs = event.atNs
            }
        }.trimEnd()
    }

    internal fun resetForTests() = synchronized(lock) {
        sequence.set(0L)
        events.clear()
        stageCounts.clear()
        overheadSamples.clear()
        droppedEvents = 0L
        sessionStartNs = 0L
        sessionStartWallMs = 0L
        lastNs = 0L
        recordCalls = 0L
        recordOverheadTotalNs = 0L
        recordOverheadMaxNs = 0L
    }

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

    /**
     * Export-only privacy barrier. Stage38 recording and every FAROL decision
     * remain untouched; only the text representation written to a manual report
     * is redacted.
     */
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

    private fun percentile(sorted: List<Long>, percentile: Int): Long {
        if (sorted.isEmpty()) return -1L
        val index = (((sorted.size - 1) * percentile) / 100.0).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun formatWall(wallMs: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(wallMs))
}
