package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * Impede que uma resposta rápida seja inserida em uma janela diferente daquela
 * que estava aberta quando o usuário acionou o atalho da bolinha.
 */
object QuickReplyTargetPolicy {
    fun normalize(packageName: String?): String? = packageName
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotBlank() }

    fun canFill(
        currentPackageName: String?,
        expectedPackageName: String?,
        ownPackageName: String?,
    ): Boolean {
        val current = normalize(currentPackageName) ?: return false
        val own = normalize(ownPackageName)
        if (current == own) return false

        val expected = normalize(expectedPackageName)
        return expected == null || current == expected
    }
}
