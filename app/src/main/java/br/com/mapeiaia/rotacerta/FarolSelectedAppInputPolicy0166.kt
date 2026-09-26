package br.com.mapeiaia.rotacerta

/**
 * Política pura do caminho de entrada do farol.
 *
 * Vale para qualquer pacote escolhido pelo usuário. O nome do aplicativo não participa
 * da autorização: apenas a seleção persistida, a raiz realmente visível e a sessão atual.
 */
object FarolSelectedAppInputPolicy0166 {
    fun resolveStableWindowId(
        eventPackageName: String?,
        rootPackageName: String?,
        selectedPackageName: String,
        eventWindowId: Int,
        rootWindowId: Int?,
        lastStableWindowId: Int?,
    ): Int = when {
        rootPackageName == selectedPackageName && rootWindowId != null -> rootWindowId
        eventPackageName == selectedPackageName -> eventWindowId
        lastStableWindowId != null -> lastStableWindowId
        rootWindowId != null -> rootWindowId
        else -> eventWindowId
    }

    fun shouldAttemptOcr(
        packageName: String,
        selectedPackages: Set<String>,
        strictRootPackageName: String?,
        parserAlreadyActive: Boolean,
    ): Boolean =
        packageName in selectedPackages &&
            strictRootPackageName == packageName &&
            !parserAlreadyActive
}
