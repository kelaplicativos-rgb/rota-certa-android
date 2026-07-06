package br.com.mapeiaia.rotacerta

object DiagnosticLogStore {
    private const val MaxEvents = 500
    private const val MaxSourceLength = 48
    private const val MaxMessageLength = 500

    private val lock = Any()
    private val events = mutableListOf<String>()

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
