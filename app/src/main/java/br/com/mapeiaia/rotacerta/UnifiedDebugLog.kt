package br.com.mapeiaia.rotacerta

import android.content.Context
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

object UnifiedDebugEventStore {
    private const val MAX_EVENTS = 2_000
    private const val MAX_DETAILS = 1_000
    private val lock = Any()
    private val events = ArrayDeque<Event>(MAX_EVENTS)

    fun record(stage: String, packageName: String?, details: String = "", nowMillis: Long = System.currentTimeMillis()) {
        FarolFlightRecorder0163.record(
            stage = stage,
            packageName = packageName,
            details = details,
            wallTimeMillis = nowMillis,
        )
        if (!DiagnosticRuntimeGate.isEnabled(nowMillis)) return
        val event = Event(
            atMillis = nowMillis,
            stage = sanitize(stage).ifBlank { "EVENT" },
            packageName = sanitize(packageName.orEmpty()).ifBlank { "nao informado" },
            details = maskSensitive(sanitize(details)).take(MAX_DETAILS),
        )
        synchronized(lock) {
            while (events.size >= MAX_EVENTS) events.removeFirst()
            events.addLast(event)
        }
    }

    fun clear() = synchronized(lock) { events.clear() }

    fun size(): Int = synchronized(lock) { events.size }

    fun dump(): String = synchronized(lock) {
        if (events.isEmpty()) return@synchronized "sem eventos na trilha unificada"
        events.joinToString("\n") { event ->
            "${format(event.atMillis)} | ${event.stage} | pacote=${event.packageName} | ${event.details}".trimEnd(' ', '|')
        }
    }

    private fun sanitize(value: String): String = value.replace(Regex("[\\r\\n\\t]+"), " ").replace(Regex("\\s{2,}"), " ").trim()

    private fun maskSensitive(value: String): String = value
        .replace(Regex("(?<!\\d)(?:\\+?55\\s*)?(?:\\(?\\d{2}\\)?\\s*)?9?\\d{4}[-\\s]?\\d{4}(?!\\d)"), "[telefone mascarado]")
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[email mascarado]")

    private fun format(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(millis))

    private data class Event(
        val atMillis: Long,
        val stage: String,
        val packageName: String,
        val details: String,
    )
}
