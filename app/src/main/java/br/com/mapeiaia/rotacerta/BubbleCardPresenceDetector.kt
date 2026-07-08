package br.com.mapeiaia.rotacerta

object BubbleCardPresenceDetector {
    fun createToken(
        packageName: String?,
        snapshotHash: Int,
        match: RideCardTemplateMatch,
    ): BubbleAnalysisToken? = BubbleAnalysisToken.from(packageName, snapshotHash, match)

    fun hasRegisteredPackage(packageName: String?, templates: List<RideCardTemplate>): Boolean {
        val normalized = RegisteredRidePackagePolicy.normalizePackageName(packageName) ?: return false
        return normalized in RegisteredRidePackagePolicy.packagesFromTemplates(templates)
    }

    fun matchRegisteredCard(
        text: String,
        packageName: String?,
        templates: List<RideCardTemplate>,
    ): RideCardTemplateMatch? {
        val normalized = RegisteredRidePackagePolicy.normalizePackageName(packageName) ?: return null
        if (text.isBlank()) return null
        return RideCardTemplateMatcher.match(text, normalized, templates)
    }

    fun sameRegisteredCard(
        token: BubbleAnalysisToken,
        text: String,
        packageName: String?,
        templates: List<RideCardTemplate>,
        snapshotHash: Int = stableSnapshotHash(text),
    ): Boolean {
        val normalized = RegisteredRidePackagePolicy.normalizePackageName(packageName) ?: return false
        if (normalized != token.packageName) return false
        if (snapshotHash != token.snapshotHash) return false
        val match = matchRegisteredCard(text, normalized, templates) ?: return false
        return match.template.id == token.templateId
    }

    fun stableSnapshotHash(text: String): Int = text
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .hashCode()
}
