package br.com.mapeiaia.rotacerta

/**
 * Circuito fail-closed para impedir que uma exceção no despertar por notificação
 * derrube repetidamente o serviço de acessibilidade.
 */
class FarolNotificationFailureCircuit0170(
    private val cooldownMillis: Long = 60_000L,
) {
    private var blockedUntilElapsedMillis: Long = Long.MIN_VALUE

    fun canAttempt(nowElapsedMillis: Long): Boolean = nowElapsedMillis >= blockedUntilElapsedMillis

    fun onFailure(nowElapsedMillis: Long) {
        blockedUntilElapsedMillis = if (nowElapsedMillis > Long.MAX_VALUE - cooldownMillis) {
            Long.MAX_VALUE
        } else {
            nowElapsedMillis + cooldownMillis
        }
    }

    fun reset() {
        blockedUntilElapsedMillis = Long.MIN_VALUE
    }
}
