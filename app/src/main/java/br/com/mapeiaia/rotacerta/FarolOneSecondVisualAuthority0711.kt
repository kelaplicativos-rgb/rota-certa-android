package br.com.mapeiaia.rotacerta

/**
 * 0.1.711 — a tela visível precisa renovar a autoridade do FAROL continuamente.
 *
 * O resultado não é persistente por ausência de eventos. Verde/vermelho + km possuem
 * um lease curto, revalidado pela tela atual. Se a tela não comprovar um endereço
 * válido dentro do TTL, o conteúdo público é limpo sem destruir/recriar a View.
 */
object FarolOneSecondVisualAuthority0711 {
    const val CONTRACT_MARKER = "FAROL_ONE_SECOND_VISUAL_AUTHORITY_0711"
    const val RESULT_TTL_MILLIS = 1_000L
    const val HEARTBEAT_MILLIS = 700L
    const val NO_ADDRESS_CLEARS_IMMEDIATELY_MARKER = "FAROL_NO_ADDRESS_CLEARS_IMMEDIATELY_0711"
    const val SAME_ADDRESS_RENEWS_LEASE_MARKER = "FAROL_SAME_ADDRESS_RENEWS_LEASE_0711"
    const val CHANGED_ADDRESS_REVOKES_OLD_RESULT_MARKER = "FAROL_CHANGED_ADDRESS_REVOKES_OLD_RESULT_0711"

    enum class Action {
        KEEP,
        CLEAR_IDLE,
        CLEAR_THEN_PROCESS,
        PROCESS,
    }

    fun expired(nowElapsedMillis: Long, lastConfirmedElapsedMillis: Long): Boolean {
        if (lastConfirmedElapsedMillis <= 0L) return true
        return (nowElapsedMillis - lastConfirmedElapsedMillis).coerceAtLeast(0L) >= RESULT_TTL_MILLIS
    }

    fun decide(
        currentAddressSignature: String?,
        observedAddressSignature: String?,
        hasPublicResult: Boolean,
        nowElapsedMillis: Long,
        lastConfirmedElapsedMillis: Long,
    ): Action {
        if (observedAddressSignature.isNullOrBlank()) return Action.CLEAR_IDLE
        if (currentAddressSignature.isNullOrBlank()) return Action.PROCESS
        if (currentAddressSignature != observedAddressSignature) return Action.CLEAR_THEN_PROCESS
        if (hasPublicResult && !expired(nowElapsedMillis, lastConfirmedElapsedMillis)) return Action.KEEP
        return Action.PROCESS
    }
}
