package br.com.mapeiaia.rotacerta

import android.content.Context

/**
 * Trilha global complementar, mantida somente em memoria.
 *
 * O formato anterior gravava SharedPreferences a cada evento da acessibilidade,
 * incluindo o ciclo de 120 ms. Isso aumentava I/O justamente no caminho critico
 * da bolinha. O arquivo de suporte continua sendo criado apenas por acao manual.
 */
object DiagnosticLogStore {
    private const val MaxEvents = 1_500
    private const val MaxSourceLength = 48
    private const val MaxMessageLength = 700

    private val lock = Any()
    private val events = mutableListOf<String>()

    /** Mantido por compatibilidade com os pontos existentes do app. */
    fun attach(@Suppress("UNUSED_PARAMETER") context: Context) = Unit

    fun record(source: String, message: String, nowMillis: Long = System.currentTimeMillis()) {
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
            events += "$nowMillis $cleanSource $cleanMessage"
            while (events.size > MaxEvents) events.removeAt(0)
        }
    }

    fun dump(maxEvents: Int = MaxEvents): String = synchronized(lock) {
        events
            .takeLast(maxEvents.coerceIn(1, MaxEvents))
            .joinToString("\n")
    }

    fun clear() {
        synchronized(lock) {
            events.clear()
        }
    }
}
