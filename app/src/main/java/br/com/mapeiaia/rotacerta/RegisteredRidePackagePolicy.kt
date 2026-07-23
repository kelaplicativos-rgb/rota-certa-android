package br.com.mapeiaia.rotacerta

import java.util.Locale

object RegisteredRidePackagePolicy {
    fun packagesFromTemplates(templates: List<RideCardTemplate>): Set<String> = templates
        .asSequence()
        .mapNotNull { template -> normalizePackageName(template.packageName) }
        .filter { it.isNotBlank() }
        .toSortedSet()

    fun hasUniversalTemplate(templates: List<RideCardTemplate>): Boolean = templates.any { template ->
        RideCardTemplateMatcher.isUniversalLearnedPackage(template.packageName)
    }

    fun acceptsPackageFromTemplates(packageName: String?, templates: List<RideCardTemplate>): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        return normalized in packagesFromTemplates(templates) || hasUniversalTemplate(templates)
    }

    fun normalizePackageName(packageName: String?): String? = packageName
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotBlank() }
}
