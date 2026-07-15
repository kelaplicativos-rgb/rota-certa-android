package br.com.mapeiaia.rotacerta

class RegisteredCardDecisionGate(
    private val staleResetMillis: Long = 180L,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    private var lastSeenAtMillis: Long = 0L

    fun markSeen() {
        lastSeenAtMillis = nowProvider()
    }

    fun clear() {
        lastSeenAtMillis = 0L
    }

    /**
     * O chamador pode pedir uma janela maior, mas nunca pode ampliar a validade real do card.
     * O APK anterior passava 2,8 segundos aqui e mantinha verde/vermelho e KM antigos. Agora a
     * tolerancia fica limitada a 180 ms: suficiente para absorver OCR do mesmo quadro, mas curta
     * o bastante para limpar no proximo ciclo quando o card fechar ou mudar.
     */
    fun hasSeenRecently(maxAgeMillis: Long = staleResetMillis): Boolean {
        if (lastSeenAtMillis <= 0L || maxAgeMillis <= 0L) return false
        val effectiveMaxAgeMillis = minOf(maxAgeMillis, staleResetMillis)
        return nowProvider() - lastSeenAtMillis < effectiveMaxAgeMillis
    }

    fun shouldResetStale(hasDecisionColor: Boolean): Boolean {
        if (!hasDecisionColor || lastSeenAtMillis <= 0L) return false
        return nowProvider() - lastSeenAtMillis >= staleResetMillis
    }
}
