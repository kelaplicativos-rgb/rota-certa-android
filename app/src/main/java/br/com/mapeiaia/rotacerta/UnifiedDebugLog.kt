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
    private const val MAX_DETAILS = 1_200
    private const val MAX_OVERHEAD_SAMPLES = 2_048

    data class SnapshotEvent(
        val atMillis: Long,
        val monotonicNs: Long,
        val stage: String,
        val packageName: String,
        val details: String,
        val threadName: String,
        val diagnosticContext: DiagnosticEventContext0507? = null,
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
        diagnosticContext: DiagnosticEventContext0507? = null,
    ) {
        // Diagnostics are observational only. A sanitizer/recorder failure must
        // never escape into the business path being observed.
        runCatching {
            val safeDetails = sanitizeForRecord(details)
            recordFlight(stage, packageName, safeDetails, nowMillis)
            if (!runCatching { DiagnosticRuntimeGate.isEnabled(nowMillis) }.getOrDefault(false)) return@runCatching
            recordInMemory(
                stage = stage,
                packageName = packageName,
                details = safeDetails,
                nowMillis = nowMillis,
                monotonicNs = SystemClock.elapsedRealtimeNanos(),
                diagnosticContext = diagnosticContext,
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
        diagnosticContext: DiagnosticEventContext0507? = null,
    ) {
        // Always-on evidence must also be fail-open. If the export sanitizer is
        // the failing component, preserve a non-sensitive fallback marker and
        // keep the publication pipeline alive.
        runCatching {
            val safeDetails = sanitizeForRecord(details)
            recordFlight(stage, packageName, safeDetails, nowMillis)
            recordInMemory(
                stage = stage,
                packageName = packageName,
                details = safeDetails,
                nowMillis = nowMillis,
                monotonicNs = monotonicNs,
                diagnosticContext = diagnosticContext,
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
                event.diagnosticContext?.let { diagnostic ->
                    append(" | parentModule=").append(diagnostic.parentModule.name)
                    append(" | originModule=").append(diagnostic.originModule.name)
                    append(" | executorModule=").append(diagnostic.executorModule.name)
                    if (diagnostic.correlationId.isNotBlank()) append(" | correlationId=").append(diagnostic.correlationId)
                    if (diagnostic.operationId.isNotBlank()) append(" | operationId=").append(diagnostic.operationId)
                }
                if (event.details.isNotBlank()) append(" | ").append(event.details)
            }
        }
    }

    /** Export-only sanitizer shared by report builders and regression tests. */
    fun sanitizeForExport(value: String): String = maskSensitive(sanitize(value))

    private fun sanitizeForRecord(value: String): String =
        runCatching { sanitizeForExport(value) }
            .getOrElse { error ->
                "[sanitization failed chars=${value.length} error=${error.javaClass.simpleName.ifBlank { "Throwable" }}]"
            }

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
        diagnosticContext: DiagnosticEventContext0507?,
    ) {
        val overheadStartNs = System.nanoTime()
        val event = SnapshotEvent(
            atMillis = nowMillis,
            monotonicNs = monotonicNs,
            stage = sanitize(stage).ifBlank { "EVENT" }.take(140),
            packageName = sanitize(packageName.orEmpty()).ifBlank { "nao informado" }.take(140),
            // [details] is already sanitized by the fail-open entrypoints.
            // Do not invoke the regex sanitizer a second time here.
            details = details.take(MAX_DETAILS),
            threadName = sanitize(Thread.currentThread().name).ifBlank { "unknown" }.take(100),
            diagnosticContext = diagnosticContext?.sanitized0507(),
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

    private fun maskSensitive(value: String): String {
        val protectedTechnicalUuids = mutableListOf<String>()
        var masked = value.replace(TECHNICAL_UUID_0510) { match ->
            val placeholder = "__RC_TECH_UUID_${protectedTechnicalUuids.size}__"
            protectedTechnicalUuids += match.value
            placeholder
        }
        masked = masked
        .replace(
            Regex("(?is)-----BEGIN(?: [A-Z0-9]+)* PRIVATE KEY-----.*?-----END(?: [A-Z0-9]+)* PRIVATE KEY-----"),
            "[chave privada mascarada]",
        )
        .replace(
            Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+\\-/]+=*"),
            "Bearer [segredo mascarado]",
        )
        .replace(
            Regex("(?i)([\"']?(?:authorization|proxy-authorization|cookie|set-cookie|token|access[_-]?token|refresh[_-]?token|password|senha|secret|client[_-]?secret|api[_-]?key|private[_-]?key|jwt|session[_-]?(?:token|id)|view[_-]?token|x-rota-certa-driver-token)[\"']?\\s*[:=]\\s*)(?:\\[[^\\]]*\\]|\"[^\"]*\"|'[^']*'|[^,|;\\s}]+)"),
        ) { match -> "${match.groupValues[1]}[segredo mascarado]" }
        .replace(
            Regex("(?i)([\"']?(?:address|home[_-]?address|residential[_-]?address|boarding[_-]?address|dropoff[_-]?address|latitude|longitude|coordinates|lat|lng)[\"']?\\s*[:=]\\s*)(?:\\[[^\\]]*\\]|\"[^\"]*\"|'[^']*'|[^,|;\\s}]+)"),
        ) { match -> "${match.groupValues[1]}[local privado mascarado]" }
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
            Regex("(?i)\\b(eventText|accessibilityText|rawText|messageText)\\s*[:=]\\s*([^|;]+)"),
        ) { match -> "${match.groupValues[1]}=[texto mascarado]" }
        protectedTechnicalUuids.forEachIndexed { index, uuid ->
            masked = masked.replace("__RC_TECH_UUID_${index}__", uuid)
        }
        return masked
    }

    private val TECHNICAL_UUID_0510 = Regex(
        "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b",
    )

    private fun percentile(sorted: List<Long>, percentile: Int): Long {
        if (sorted.isEmpty()) return -1L
        val index = (((sorted.size - 1) * percentile) / 100.0).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun format(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(millis))
}
