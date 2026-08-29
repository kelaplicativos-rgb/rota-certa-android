package br.com.mapeiaia.rotacerta

import android.content.Context
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object DebugLogPreferenceStore {
    private const val PREFS = "rota_certa_debug_log"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        DiagnosticRuntimeGate.setEnabled(enabled)
    }
}

/**
 * Existing unified debug store, evolved into a bounded in-memory flight recorder.
 *
 * Legacy callers keep their gated [record] behavior. Agenda instrumentation uses
 * [recordAlways] so events that happen before the user notices a problem are
 * already available when "Gerar relatório para depuração" is pressed.
 *
 * No method below creates polling, network, screenshots, OCR, disk I/O or work
 * on a timer. Heavy string assembly happens only in [dump].
 */
object UnifiedDebugEventStore {
    const val MAX_EVENTS = 6_000
    private const val MAX_DETAILS = 1_000
    private const val MAX_OVERHEAD_SAMPLES = 2_048

    data class SnapshotEvent(
        val atMillis: Long,
        val monotonicNs: Long,
        val stage: String,
        val packageName: String,
        val details: String,
        val threadName: String,
    )

    data class Snapshot(
        val events: List<SnapshotEvent>,
        val droppedEvents: Long,
        val bufferCapacity: Int,
        val recordCalls: Long,
        val recordOverheadTotalNs: Long,
        val recordMedianNs: Long,
        val recordP95Ns: Long,
        val recordMaxNs: Long,
    )

    private val lock = Any()
    private val events = ArrayDeque<SnapshotEvent>(MAX_EVENTS)
    private val overheadSamples = ArrayDeque<Long>(MAX_OVERHEAD_SAMPLES)
    private var droppedEvents = 0L
    private var recordCalls = 0L
    private var recordOverheadTotalNs = 0L
    private var recordOverheadMaxNs = 0L

    /**
     * Compatibility path: detailed legacy collection still obeys
     * DiagnosticRuntimeGate exactly as before.
     */
    fun record(
        stage: String,
        packageName: String?,
        details: String = "",
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        recordFlight(stage, packageName, details, nowMillis)
        if (!runCatching { DiagnosticRuntimeGate.isEnabled(nowMillis) }.getOrDefault(false)) return
        runCatching {
            recordInMemory(
                stage = stage,
                packageName = packageName,
                details = details,
                nowMillis = nowMillis,
                monotonicNs = SystemClock.elapsedRealtimeNanos(),
            )
        }
    }

    /**
     * Agenda-only normal-path recorder. It is intentionally always on, bounded
     * and memory-only so the report button snapshots history instead of starting
     * the investigation after the incident.
     */
    fun recordAlways(
        stage: String,
        packageName: String?,
        details: String = "",
        nowMillis: Long = System.currentTimeMillis(),
        monotonicNs: Long = SystemClock.elapsedRealtimeNanos(),
    ) {
        recordFlight(stage, packageName, details, nowMillis)
        runCatching {
            recordInMemory(
                stage = stage,
                packageName = packageName,
                details = details,
                nowMillis = nowMillis,
                monotonicNs = monotonicNs,
            )
        }
    }

    fun clear() = synchronized(lock) {
        events.clear()
        overheadSamples.clear()
        droppedEvents = 0L
        recordCalls = 0L
        recordOverheadTotalNs = 0L
        recordOverheadMaxNs = 0L
    }

    fun size(): Int = synchronized(lock) { events.size }

    fun snapshot(): Snapshot = synchronized(lock) {
        val sorted = overheadSamples.sorted()
        Snapshot(
            events = events.toList(),
            droppedEvents = droppedEvents,
            bufferCapacity = MAX_EVENTS,
            recordCalls = recordCalls,
            recordOverheadTotalNs = recordOverheadTotalNs,
            recordMedianNs = percentile(sorted, 50),
            recordP95Ns = percentile(sorted, 95),
            recordMaxNs = recordOverheadMaxNs,
        )
    }

    fun dump(): String = dump(snapshot())

    fun dump(snapshot: Snapshot): String {
        if (snapshot.events.isEmpty()) return "sem eventos na trilha unificada"
        return snapshot.events.joinToString("\n") { event ->
            buildString {
                append(format(event.atMillis))
                append(" | mono_ns=").append(event.monotonicNs)
                append(" | thread=").append(event.threadName)
                append(" | ").append(event.stage)
                append(" | pacote=").append(event.packageName)
                if (event.details.isNotBlank()) append(" | ").append(event.details)
            }
        }
    }

    /** Export-only sanitizer shared by report builders and regression tests. */
    fun sanitizeForExport(value: String): String = maskSensitive(sanitize(value))

    private fun recordFlight(stage: String, packageName: String?, details: String, nowMillis: Long) {
        runCatching {
            FarolFlightRecorder0163.record(
                stage = stage,
                packageName = packageName,
                details = details,
                wallTimeMillis = nowMillis,
            )
        }
    }

    private fun recordInMemory(
        stage: String,
        packageName: String?,
        details: String,
        nowMillis: Long,
        monotonicNs: Long,
    ) {
        val overheadStartNs = System.nanoTime()
        val event = SnapshotEvent(
            atMillis = nowMillis,
            monotonicNs = monotonicNs,
            stage = sanitize(stage).ifBlank { "EVENT" }.take(140),
            packageName = sanitize(packageName.orEmpty()).ifBlank { "nao informado" }.take(140),
            details = sanitizeForExport(details).take(MAX_DETAILS),
            threadName = sanitize(Thread.currentThread().name).ifBlank { "unknown" }.take(100),
        )
        synchronized(lock) {
            while (events.size >= MAX_EVENTS) {
                events.removeFirst()
                droppedEvents += 1L
            }
            events.addLast(event)
        }

        val cost = (System.nanoTime() - overheadStartNs).coerceAtLeast(0L)
        synchronized(lock) {
            recordCalls += 1L
            recordOverheadTotalNs += cost
            recordOverheadMaxNs = maxOf(recordOverheadMaxNs, cost)
            if (overheadSamples.size >= MAX_OVERHEAD_SAMPLES) overheadSamples.removeFirst()
            overheadSamples.addLast(cost)
        }
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

    private fun maskSensitive(value: String): String = value
        .replace(
            Regex("(?<!\\d)(?:\\+?55\\s*)?(?:\\(?\\d{2}\\)?\\s*)?9?\\d{4}[-\\s]?\\d{4}(?!\\d)"),
            "[telefone mascarado]",
        )
        .replace(
            Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
            "[email mascarado]",
        )
        .replace(
            Regex("(?i)https?://[^\\s|;]+"),
            "[url mascarada]",
        )
        .replace(
            Regex("(?i)\\b(token|cookie|authorization|password|senha|secret|jwt|sessionToken|accessToken|viewToken)\\s*[:=]\\s*(?:\"[^\"]*\"|'[^']*'|[^|;\\s]+)"),
        ) { match -> "${match.groupValues[1]}=[segredo mascarado]" }
        .replace(
            Regex("(?i)\\b(eventText|accessibilityText|rawText|messageText)\\s*[:=]\\s*([^|;]+)"),
        ) { match -> "${match.groupValues[1]}=[texto mascarado]" }

    private fun percentile(sorted: List<Long>, percentile: Int): Long {
        if (sorted.isEmpty()) return -1L
        val index = (((sorted.size - 1) * percentile) / 100.0).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun format(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(millis))
}
