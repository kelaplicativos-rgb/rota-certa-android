package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * Contrato central de autorizacao da leitura ao vivo.
 *
 * A acessibilidade, a arvore de elementos, screenshots, OCR e calculo de rota
 * somente podem ser acionados quando o pacote atual foi escolhido manualmente
 * pelo usuario. Opcoes legadas de monitoramento nao participam desta decisao.
 */
object StrictSelectedAppReadPolicy {
    fun canRead(
        packageName: String?,
        ownPackageName: String,
        appEnabled: Boolean,
        liveReadingEnabled: Boolean,
        selectedPackages: Set<String>,
        packageAllowedByPlatformPolicy: Boolean,
    ): Boolean {
        if (!appEnabled || !liveReadingEnabled || !packageAllowedByPlatformPolicy) return false

        val normalizedPackage = normalize(packageName) ?: return false
        val normalizedOwnPackage = normalize(ownPackageName)
        if (normalizedPackage == normalizedOwnPackage) return false

        val normalizedSelection = selectedPackages.mapNotNull(::normalize).toSet()
        return normalizedPackage in normalizedSelection
    }

    private fun normalize(packageName: String?): String? = packageName
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotBlank() }
}
