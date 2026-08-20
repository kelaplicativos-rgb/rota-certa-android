package br.com.mapeiaia.rotacerta

/** Regras únicas para chave de rota: valor digitado vence; chave do build é fallback. */
object GoogleMapsApiKeyPolicy {
    fun effective(userValue: String?, bundledValue: String?): String =
        bundledValue.orEmpty().trim().ifBlank { userValue.orEmpty().trim() }

    fun isConfigured(userValue: String?, bundledValue: String?): Boolean =
        effective(userValue, bundledValue).isNotBlank()

    /**
     * Backups antigos não carregam a chave embutida por segurança. Restaurá-los
     * jamais pode apagar uma chave que já funciona no aparelho ou no build.
     */
    fun valueAfterRestore(
        currentValue: String?,
        restoredValue: String?,
        bundledValue: String?,
    ): String = restoredValue.orEmpty().trim()
        .ifBlank { currentValue.orEmpty().trim() }
        .ifBlank { bundledValue.orEmpty().trim() }
}
