package br.com.mapeiaia.rotacerta

/**
 * Chave global barata para retirar a instrumentacao detalhada do caminho
 * critico da bolinha. O valor nasce desligado e e atualizado pelo DataStore.
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
