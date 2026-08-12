package br.com.mapeiaia.rotacerta

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

    fun evaluate(blocks: List<VisualBlock>): Evaluation? {
        if (blocks.isEmpty()) return null
        data class Parsed(val block: VisualBlock, val addresses: List<String>)

        val parsed = blocks.asSequence()
            .filter { it.text.isNotBlank() && !it.syntheticRoot }
            .take(240)
            .map { block ->
                val addresses = UniversalScreenAddressParser.findAddresses(
                    WrappedAddressTextNormalizer.normalize(block.text),
                ).map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
                    .filter(String::isNotBlank)
                    .distinctBy(::canonical)
                Parsed(block, addresses)
            }
            .toList()
        val addressBearing = parsed.filter { it.addresses.isNotEmpty() }
        if (addressBearing.isEmpty()) return null

        val bestLayer = addressBearing.maxOf { it.block.windowLayer }
        val layerBlocks = addressBearing.filter { it.block.windowLayer == bestLayer }
        fun visualTop(block: VisualBlock): Int = block.top.takeUnless { it == Int.MAX_VALUE } ?: Int.MAX_VALUE

        val anchor = if (layerBlocks.any { visualTop(it.block) != Int.MAX_VALUE }) {
            layerBlocks.minWithOrNull(
                compareBy<Parsed> { visualTop(it.block) }
                    .thenByDescending { it.block.depth }
                    .thenBy { it.block.text.length },
            )
        } else {
            val deepest = layerBlocks.maxOf { it.block.depth }
            val specific = layerBlocks.filter { it.block.depth == deepest }
            if (specific.map { canonical(it.addresses.last()) }.distinct().size > 1) return null
            specific.minByOrNull { it.block.text.length }
        } ?: return null

        val candidates = parsed.asSequence()
            .filter { it.block.windowLayer == bestLayer }
            .filter { it.block.windowId == anchor.block.windowId }
            .filter { it.addresses.size >= UniversalAddressTrigger.MINIMUM_VISIBLE_ADDRESSES }
            .filter { candidate ->
                candidate.block.id == anchor.block.id ||
                    anchor.block.id.startsWith(candidate.block.id + "/") ||
                    contains(candidate.block, anchor.block)
            }
            .toList()
        if (candidates.isEmpty()) return null

        val bestDepth = candidates.maxOf { it.block.depth }
        val winner = candidates.filter { it.block.depth == bestDepth }
            .minWithOrNull(compareBy<Parsed> { visualTop(it.block) }.thenBy { it.block.text.length })
            ?: return null
        val pickup = winner.addresses.first()
        val destination = winner.addresses.last()
        if (canonical(pickup) == canonical(destination)) return null

        val addressSignature = DestinationAddressIdentityPolicy.signature("visual", destination)
        if (addressSignature.isBlank()) return null
        val stableBlockIdentity = listOf(
            winner.block.windowId.toString(),
            winner.block.left.toString(),
            winner.block.top.toString(),
            winner.block.right.toString(),
            winner.block.bottom.toString(),
            canonical(destination),
        ).joinToString(":")
        val authorityIdentity = listOf(
            stableBlockIdentity,
            addressSignature,
            canonical(winner.block.text),
        ).joinToString("|")
        return Evaluation(
            windowId = winner.block.windowId,
            blockId = stableBlockIdentity,
            source = winner.block.source,
            analysisText = winner.block.text.trim(),
            addresses = winner.addresses,
            pickup = pickup,
            destination = destination,
            addressSignature = addressSignature,
            screenHash = authorityIdentity.hashCode(),
        )
    }

    fun bindingMatchesCurrent(
        binding: Binding,
        currentScreenGeneration: Long,
        currentWindowGeneration: Long,
        currentScreenHash: Int?,
        currentAddressSignature: String?,
        visualVerificationPending: Boolean,
    ): Boolean = !visualVerificationPending &&
        binding.screenGeneration == currentScreenGeneration &&
        binding.windowGeneration == currentWindowGeneration &&
        binding.screenHash == currentScreenHash &&
        binding.addressSignature == currentAddressSignature

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
