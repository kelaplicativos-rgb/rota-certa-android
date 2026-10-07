package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * 0.1.752 — shared visual authority used by manual screen reading and FAROL.
 *
 * It keeps the visual order of OCR blocks, deduplicates repeated recovery passes
 * and always treats the final positive address on the same visible surface as
 * the destination candidate.
 */
object ScreenAddressVisualAuthority0752 {
    const val CONTRACT_MARKER = "SCREEN_ADDRESS_VISUAL_AUTHORITY_0752"
    const val LAST_ADDRESS_MARKER = "LAST_VISIBLE_ADDRESS_IS_DESTINATION_0752"

    fun orderedAddresses(
        accessibilityText: String,
        passes: List<OcrStructuredText0188>,
    ): List<String> {
        val seen = linkedSetOf<String>()
        val output = mutableListOf<String>()

        fun add(value: String) {
            val cleaned = DestinationAddressIdentityPolicy.cleanDisplayAddress(value)
            val key = canonical(cleaned)
            if (key.isNotBlank() && seen.add(key)) output += cleaned
        }

        deduplicatedBlocks(passes)
            .sortedWith(
                compareBy<OcrTextBlock0188> { it.top }
                    .thenBy { it.left }
                    .thenBy { it.bottom }
                    .thenBy { it.right },
            )
            .forEach { block ->
                UniversalScreenAddressParser.findAddresses(block.text).forEach(::add)
            }

        if (output.size < 2) {
            passes.forEach { pass ->
                UniversalScreenAddressParser.findAddresses(pass.text).forEach(::add)
            }
        }

        if (output.size < 2 && accessibilityText.isNotBlank()) {
            UniversalScreenAddressParser.findAddresses(accessibilityText).forEach(::add)
        }

        return output
    }

    fun deduplicatedBlocks(passes: List<OcrStructuredText0188>): List<OcrTextBlock0188> {
        val seen = linkedSetOf<String>()
        return passes.asSequence()
            .flatMap { it.blocks.asSequence() }
            .filter { it.text.isNotBlank() }
            .filter { block ->
                val key = canonical(block.text) + "|" +
                    (block.left / 12) + "|" + (block.top / 12) + "|" +
                    (block.right / 12) + "|" + (block.bottom / 12)
                seen.add(key)
            }
            .take(180)
            .toList()
    }

    fun destination(addresses: List<String>): String? = addresses.lastOrNull()

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
