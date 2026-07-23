package br.com.mapeiaia.rotacerta

/**
 * Porta unica dos diagnosticos detalhados.
 *
 * O uso normal do Rota Certa nunca habilita esta porta. O metodo setEnabled foi
 * mantido somente para compatibilidade com codigo historico e ignora pedidos de
 * ativacao vindos de configuracoes antigas ou backups. Uma coleta temporaria so
 * pode ser aberta explicitamente por beginManualCapture.
 */
object DiagnosticRuntimeGate {
    @Volatile
    private var manualCaptureUntilMillis: Long = 0L

    /** Compatibilidade: false encerra a coleta; true nao ativa diagnostico continuo. */
    fun setEnabled(value: Boolean) {
        if (!value) manualCaptureUntilMillis = 0L
    }

    fun beginManualCapture(
        durationMillis: Long = DEFAULT_MANUAL_CAPTURE_MILLIS,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        manualCaptureUntilMillis = nowMillis + durationMillis.coerceIn(250L, MAX_MANUAL_CAPTURE_MILLIS)
    }

    fun endManualCapture() {
        manualCaptureUntilMillis = 0L
    }

    fun isEnabled(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val deadline = manualCaptureUntilMillis
        if (deadline <= 0L) return false
        if (nowMillis > deadline) {
            manualCaptureUntilMillis = 0L
            return false
        }
        return true
    }

    inline fun whenEnabled(block: () -> Unit) {
        if (isEnabled()) block()
    }

    private const val DEFAULT_MANUAL_CAPTURE_MILLIS = 2_000L
    private const val MAX_MANUAL_CAPTURE_MILLIS = 10_000L
}
