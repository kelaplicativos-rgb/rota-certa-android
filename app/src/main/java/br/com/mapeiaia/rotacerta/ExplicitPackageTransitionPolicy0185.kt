package br.com.mapeiaia.rotacerta

/** Rejeita um pacote externo explícito antes que uma raiz antiga do app selecionado o autorize. */
object ExplicitPackageTransitionPolicy0185 {
    const val CONTRACT_MARKER = "EXPLICIT_EXTERNAL_PACKAGE_REJECTION_0185"

    fun shouldReject(
        eventPackageName: String?,
        selectedPackages: Set<String>,
        ownPackageName: String,
        isTransientOverlay: (String) -> Boolean,
    ): Boolean {
        val eventPackage = eventPackageName?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            ?: return false
        val selected = selectedPackages.asSequence().map { it.trim().lowercase() }.toSet()
        val own = ownPackageName.trim().lowercase()
        return eventPackage !in selected &&
            eventPackage != own &&
            !isTransientOverlay(eventPackage)
    }
}
