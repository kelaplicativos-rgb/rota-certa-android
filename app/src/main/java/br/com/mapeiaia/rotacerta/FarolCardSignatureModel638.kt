package br.com.mapeiaia.rotacerta

data class FarolCardSignatureModel638(
    val id: String,
    val packageName: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val sampleCount: Int,
    val anchorTokens: Set<String>,
    val structureTokens: Set<String>,
    val visualHash: String?,
    val confidence: Int,
)

data class FarolCardSignatureProbe638(
    val packageName: String,
    val anchorTokens: Set<String>,
    val structureTokens: Set<String>,
)

data class FarolCardSignatureMatch638(
    val matched: Boolean,
    val modelId: String? = null,
    val score: Double = 0.0,
    val reason: String,
)

object FarolCardSignatureContract638 {
    const val CONTRACT_MARKER = "FAROL_CARD_SIGNATURE_GATE_0638"
    const val MANUAL_TRAINING_ONLY = "SCREENSHOT_ONLY_ON_EXPLICIT_TRAINING_0638"
    const val NO_RUNTIME_SCREENSHOT = "RUNTIME_MATCHER_NEVER_TAKES_SCREENSHOT_0638"
    const val FOREIGN_FAST_RETURN = "FOREIGN_PACKAGE_O1_RETURN_BEFORE_STAGE19_0638"
    const val TRAINED_MATCH_BEFORE_HEAVY = "TRAINED_SIGNATURE_MATCH_BEFORE_HEAVY_COLLECT_0638"
}
