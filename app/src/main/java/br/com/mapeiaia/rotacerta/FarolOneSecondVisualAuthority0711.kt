package br.com.mapeiaia.rotacerta

/**
 * 0.1.711 — a tela visível precisa renovar a autoridade do FAROL continuamente.
 *
 * Verde/vermelho + km pertencem ao último endereço confirmado. Uma leitura vazia é
 * NO_OBSERVATION: não prova que o card/contexto terminou e nunca deve apagar o resultado.
 * Limpeza fica reservada a transições de contexto comprovadas pelo serviço.
 */
object FarolOneSecondVisualAuthority0711 {
    const val CONTRACT_MARKER = "FAROL_ONE_SECOND_VISUAL_AUTHORITY_0711"
    const val RESULT_TTL_MILLIS = 1_000L
    const val HEARTBEAT_MILLIS = 700L
    const val NO_OBSERVATION_PRESERVES_MARKER = "FAROL_NO_OBSERVATION_PRESERVES_0740"
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
        if (observedAddressSignature.isNullOrBlank()) {
            return if (!currentAddressSignature.isNullOrBlank() || hasPublicResult) Action.KEEP else Action.CLEAR_IDLE
        }
        if (currentAddressSignature.isNullOrBlank()) return Action.PROCESS
        if (currentAddressSignature != observedAddressSignature) return Action.CLEAR_THEN_PROCESS
        if (hasPublicResult && !expired(nowElapsedMillis, lastConfirmedElapsedMillis)) return Action.KEEP
        return Action.PROCESS
    }
}
