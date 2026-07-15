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

    /**
     * Nunca permite que o chamador amplie a janela natural de validade do card.
     * A bolinha antiga passava 2,8 segundos aqui e, por isso, mantinha verde/vermelho e KM
     * do card anterior depois que a tela ja tinha mudado. Uma solicitacao maior que o prazo
     * real do gate agora e rejeitada imediatamente.
     */
    fun hasSeenRecently(maxAgeMillis: Long = staleResetMillis): Boolean {
        if (lastSeenAtMillis <= 0L || maxAgeMillis <= 0L) return false
        if (maxAgeMillis > staleResetMillis) return false
        return nowProvider() - lastSeenAtMillis < maxAgeMillis
    }

    fun shouldResetStale(hasDecisionColor: Boolean): Boolean {
        if (!hasDecisionColor || lastSeenAtMillis <= 0L) return false
        return nowProvider() - lastSeenAtMillis >= staleResetMillis
    }
}
