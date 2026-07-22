package br.com.mapeiaia.rotacerta

import java.util.Locale

data class BubbleAnalysisToken(
    val packageName: String,
    val snapshotHash: Int,
    val templateId: String,
) {
    companion object {
        fun from(packageName: String?, snapshotHash: Int, match: RideCardTemplateMatch): BubbleAnalysisToken? {
            val normalizedPackage = packageName
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it.isNotBlank() }
                ?: return null
            return BubbleAnalysisToken(
                packageName = normalizedPackage,
                snapshotHash = snapshotHash,
                templateId = match.template.id,
            )
        }
    }
}
