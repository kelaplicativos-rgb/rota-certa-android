package br.com.mapeiaia.rotacerta

import android.content.Context
import java.util.ArrayDeque

/**
 * Trilha global temporaria. No uso normal, record retorna antes de limpar ou
 * montar qualquer texto. A fila so pode receber eventos durante uma coleta
 * manual explicitamente aberta por DiagnosticRuntimeGate.
 */
object DiagnosticLogStore {
    private const val MaxEvents = 1_500
    private const val MaxSourceLength = 48
    private const val MaxMessageLength = 700

    private val lock = Any()
    private val events = ArrayDeque<String>(MaxEvents)

    /** Mantido por compatibilidade com os pontos existentes do app. */
    fun attach(@Suppress("UNUSED_PARAMETER") context: Context) = Unit

    fun record(source: String, message: String, nowMillis: Long = System.currentTimeMillis()) {
        if (!DiagnosticRuntimeGate.isEnabled(nowMillis)) return
        val cleanSource = source
            .trim()
            .ifBlank { "unknown" }
            .replace(Regex("\\s+"), "_")
            .take(MaxSourceLength)
        val cleanMessage = message
            .replace(Regex("[\\r\\n]+"), " ")
            .trim()
            .ifBlank { "empty" }
            .take(MaxMessageLength)
        synchronized(lock) {
            events.addLast("$nowMillis $cleanSource $cleanMessage")
            while (events.size > MaxEvents) events.removeFirst()
        }
    }

    fun dump(maxEvents: Int = MaxEvents): String = synchronized(lock) {
        events
            .toList()
            .takeLast(maxEvents.coerceIn(1, MaxEvents))
            .joinToString("\n")
    }

    fun clear() {
        synchronized(lock) {
            events.clear()
        }
    }
}
