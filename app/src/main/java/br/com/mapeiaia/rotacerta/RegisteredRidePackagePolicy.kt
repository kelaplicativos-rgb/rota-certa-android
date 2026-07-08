package br.com.mapeiaia.rotacerta

import java.util.Locale

object RegisteredRidePackagePolicy {
    fun packagesFromTemplates(templates: List<RideCardTemplate>): Set<String> = templates
        .asSequence()
        .mapNotNull { template -> normalizePackageName(template.packageName) }
        .filter { it.isNotBlank() }
        .toSortedSet()

    fun normalizePackageName(packageName: String?): String? = packageName
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotBlank() }
}
