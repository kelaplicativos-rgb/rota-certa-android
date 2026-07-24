package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * Mantem a lista de pacotes permitidos sincronizada com os cards existentes.
 * Um pacote so deve ser removido de Aplicativos quando nao restar nenhum modelo
 * nem nenhuma captura desse mesmo pacote.
 */
object CardPackageLifecyclePolicy {
    fun shouldRemoveSelectedPackage(
        packageName: String?,
        templates: List<RideCardTemplate>,
        captures: List<AutomaticRideCapture>,
    ): Boolean {
        val normalized = normalize(packageName) ?: return false
        val hasTemplate = templates.any { normalize(it.packageName) == normalized }
        val hasCapture = captures.any { normalize(it.packageName) == normalized }
        return !hasTemplate && !hasCapture
    }

    fun removePackageIfOrphaned(
        selectedPackages: Set<String>,
        packageName: String?,
        templates: List<RideCardTemplate>,
        captures: List<AutomaticRideCapture>,
    ): Set<String> {
        val normalized = normalize(packageName) ?: return selectedPackages
        if (!shouldRemoveSelectedPackage(normalized, templates, captures)) return selectedPackages
        return selectedPackages.mapNotNull(::normalize).filterNot { it == normalized }.toSortedSet()
    }

    private fun normalize(value: String?): String? = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
}
