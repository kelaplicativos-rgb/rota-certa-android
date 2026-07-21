package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * Portaria estrita para snapshots do inDrive.
 *
 * Um card valido apresenta exatamente dois enderecos: embarque e destino.
 * Durante a troca de cards, a arvore de acessibilidade pode misturar o card
 * anterior e o novo, expondo 4 ou 6 enderecos. Esses snapshots ambiguos nunca
 * podem conservar a cor anterior nem iniciar uma rota incorreta.
 */
class RideCardSnapshotStabilizer {
    fun shouldIgnore(
        packageName: String?,
        addressCount: Int,
        active: Boolean,
        nowMillis: Long,
    ): Boolean {
        @Suppress("UNUSED_VARIABLE")
        val observationTime = nowMillis
        val normalizedPackage = packageName
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf(String::isNotBlank)

        return normalizedPackage == PACKAGE_INDRIVE_DRIVER &&
            active &&
            addressCount != EXPECTED_INDRIVE_ADDRESS_COUNT
    }

    fun reset() = Unit

    companion object {
        private const val EXPECTED_INDRIVE_ADDRESS_COUNT = 2
        private const val PACKAGE_INDRIVE_DRIVER = "sinet.startup.indriver"
    }
}
