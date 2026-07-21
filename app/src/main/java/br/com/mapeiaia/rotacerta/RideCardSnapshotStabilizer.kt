package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * Evita que leituras intermediarias do inDrive, contendo enderecos antigos e
 * novos ao mesmo tempo, cancelem uma rota valida que acabou de ser iniciada.
 *
 * Um card normal do inDrive apresenta exatamente embarque e destino. Durante a
 * animacao de troca, a arvore de acessibilidade pode expor 4 ou 6 enderecos por
 * alguns instantes. Esses snapshots expandidos sao ignorados por uma janela
 * curta, mas um novo snapshot exato com dois enderecos sempre entra
 * imediatamente.
 */
class RideCardSnapshotStabilizer(
    private val expansionWindowMillis: Long = DEFAULT_EXPANSION_WINDOW_MILLIS,
) {
    private var activePackageName: String? = null
    private var lastExactTwoAtMillis: Long = NO_TIMESTAMP

    fun shouldIgnore(
        packageName: String?,
        addressCount: Int,
        active: Boolean,
        nowMillis: Long,
    ): Boolean {
        val normalizedPackage = packageName
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf(String::isNotBlank)

        if (normalizedPackage != PACKAGE_INDRIVE_DRIVER) {
            reset()
            return false
        }

        if (activePackageName != normalizedPackage) {
            reset()
            activePackageName = normalizedPackage
        }

        if (!active) return false

        if (addressCount == EXPECTED_INDRIVE_ADDRESS_COUNT) {
            lastExactTwoAtMillis = nowMillis
            return false
        }

        if (addressCount <= EXPECTED_INDRIVE_ADDRESS_COUNT || lastExactTwoAtMillis == NO_TIMESTAMP) {
            return false
        }

        val elapsed = nowMillis - lastExactTwoAtMillis
        if (elapsed < 0L) {
            lastExactTwoAtMillis = NO_TIMESTAMP
            return false
        }
        return elapsed <= expansionWindowMillis
    }

    fun reset() {
        activePackageName = null
        lastExactTwoAtMillis = NO_TIMESTAMP
    }

    companion object {
        const val DEFAULT_EXPANSION_WINDOW_MILLIS = 2_800L
        private const val EXPECTED_INDRIVE_ADDRESS_COUNT = 2
        private const val PACKAGE_INDRIVE_DRIVER = "sinet.startup.indriver"
        private const val NO_TIMESTAMP = Long.MIN_VALUE
    }
}
