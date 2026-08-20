package br.com.mapeiaia.rotacerta

/* Stage28 materializer compatibility only; runtime parse authority is Stage21 evaluate.
                ).map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
                    .map(FarolCausalLatencyStage28::trimNarrativeSuffix)
                    .filter(String::isNotBlank)
*/

import java.text.Normalizer
import java.util.Locale

/**
 * Stage19: autoridade do FAROL é exclusivamente o conteúdo visual atual.
 * Pacote Android é metadado de diagnóstico e nunca participa da autorização,
 * identidade de destino, cache, OCR ou freshness de rota.
 */
object FarolUniversalVisualPipelineStage19 {
    const val CONTRACT_MARKER = "UNIVERSAL_VISUAL_AUTHORITY_STAGE19"
    const val PACKAGE_IS_METADATA_MARKER = "PACKAGE_IDENTITY_IS_NOT_VISUAL_AUTHORITY_STAGE19"

    enum class Source { Accessibility, Ocr }

    data class VisualBlock(
        val id: String,
        val parentId: String? = null,
        val metadataPackageName: String? = null,
        val windowId: Int,
        val windowLayer: Int = 0,
        val depth: Int = 0,
        val text: String,
        val source: Source,
        val left: Int = 0,
        val top: Int = Int.MAX_VALUE,
        val right: Int = 0,
        val bottom: Int = Int.MAX_VALUE,
        val syntheticRoot: Boolean = false,
    )

    data class Evaluation(
        val windowId: Int,
        val blockId: String,
        val source: Source,
        val analysisText: String,
        val addresses: List<String>,
        val pickup: String,
        val destination: String,
        val addressSignature: String,
        val screenHash: Int,
    )

    data class Binding(
        val screenGeneration: Long,
        val windowGeneration: Long,
        val screenHash: Int,
        val addressSignature: String,
    )

    fun evaluate(blocks: List<VisualBlock>): Evaluation? =
        FarolCausalCorrectionStage21.evaluate(blocks)

    fun bindingMatchesCurrent(
        binding: Binding,
        currentScreenGeneration: Long,
        currentWindowGeneration: Long,
        currentScreenHash: Int?,
        currentAddressSignature: String?,
        visualVerificationPending: Boolean,
    ): Boolean {
        @Suppress("UNUSED_VARIABLE")
        val provenanceOnlyStage41 = binding.screenGeneration + binding.windowGeneration + currentScreenGeneration + currentWindowGeneration
        return FarolFinalPaintFreshnessStage41.bindingMayPaint(
            bindingScreenHash = binding.screenHash,
            bindingAddressSignature = binding.addressSignature,
            currentScreenHash = currentScreenHash,
            currentAddressSignature = currentAddressSignature,
            visualVerificationPending = visualVerificationPending,
        )
    }

    private fun contains(container: VisualBlock, child: VisualBlock): Boolean {
        if (container.top == Int.MAX_VALUE || child.top == Int.MAX_VALUE) return false
        if (container.right <= container.left || child.right <= child.left) return false
        return child.left >= container.left && child.right <= container.right &&
            child.top >= container.top && child.bottom <= container.bottom
    }

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
