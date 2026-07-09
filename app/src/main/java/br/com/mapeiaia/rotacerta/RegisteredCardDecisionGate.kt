package br.com.mapeiaia.rotacerta

class RegisteredCardDecisionGate(
    private val staleResetMillis: Long = 350L,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    private var lastSeenAtMillis: Long = 0L

    fun markSeen() {
        lastSeenAtMillis = nowProvider()
    }

    fun clear() {
        lastSeenAtMillis = 0L
    }

    fun hasSeenRecently(maxAgeMillis: Long = staleResetMillis): Boolean {
        if (lastSeenAtMillis <= 0L) return false
        return nowProvider() - lastSeenAtMillis < maxAgeMillis
    }

    fun shouldResetStale(hasDecisionColor: Boolean): Boolean {
        if (!hasDecisionColor || lastSeenAtMillis <= 0L) return false
        return nowProvider() - lastSeenAtMillis >= staleResetMillis
    }
}
