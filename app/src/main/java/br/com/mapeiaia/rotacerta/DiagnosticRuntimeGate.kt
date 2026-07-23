package br.com.mapeiaia.rotacerta

/**
 * Chave global barata para retirar a instrumentação detalhada do caminho
 * crítico da bolinha. O valor nasce desligado e é atualizado pelo DataStore.
 */
object DiagnosticRuntimeGate {
    @Volatile
    private var enabled: Boolean = false

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun isEnabled(): Boolean = enabled

    fun whenEnabled(block: () -> Unit) {
        if (enabled) block()
    }
}
