package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Stage46 R8 — positive location evidence before Google/cache authority.
 * R7 remains the fast extractor/order engine, but arbitrary visible labels cannot become routes.
 * Google Maps, route cache, Casa/Alfinete, radius decision and downstream freshness are untouched.
 */
object FarolRouteLocationEvidenceStage46R8 {
    const val CONTRACT_MARKER = "FAROL_POSITIVE_LOCATION_EVIDENCE_STAGE46_R8"
    const val UI_NOISE_MARKER = "ARBITRARY_UI_TEXT_CANNOT_REACH_GOOGLE_STAGE46_R8"
    const val GOOGLE_UNCHANGED_MARKER = "GOOGLE_ROUTE_AND_CACHE_UNCHANGED_STAGE46_R8"
    const val POSITIVE_ONLY_MARKER = "POSITIVE_ADDRESS_OR_PLACE_STRUCTURE_REQUIRED_STAGE46_R8"
    const val LAST_VALID_MARKER = "LAST_VALID_VISIBLE_LOCATION_IS_DESTINATION_STAGE46_R8"
    const val NAMED_PLACE_MARKER = "STRONG_NAMED_PLACE_REMAINS_ROUTEABLE_STAGE46_R8"
    const val IMMEDIATE_CLEAR_MARKER = "PROVEN_DESTINATION_CHANGE_CLEARS_BEFORE_HEAVY_COLLECT_STAGE46_R8"
    const val SAME_DESTINATION_MARKER = "CURRENT_DESTINATION_EVIDENCE_DOES_NOT_CLEAR_FINAL_STAGE46_R8"
    const val NO_POLLING_MARKER = "EVENT_DRIVEN_LOCATION_EVIDENCE_NO_POLLING_STAGE46_R8"

    data class ReplacementProof(
        val proven: Boolean,
        val reason: String,
        val candidateSignature: String? = null,
        val positiveLocationCount: Int = 0,
    )

    fun evaluate(blocks: List<FarolUniversalVisualPipelineStage19.VisualBlock>): FarolUniversalVisualPipelineStage19.Evaluation? {
        if (blocks.isEmpty()) return null
        val filtered = blocks.mapNotNull { block ->
            if (block.text.isBlank() || block.syntheticRoot) return@mapNotNull null
            val locations = positiveLocations(block)
            if (locations.isEmpty()) null else block.copy(text = locations.joinToString("\n"))
        }
        if (filtered.isEmpty()) return null
        return FarolImmediateAddressRouteStage46R7.evaluate(filtered)?.takeIf { validateEvaluation(it).accepted }
    }

    fun evaluateImmediateText(
        text: String,
        windowId: Int,
        source: FarolUniversalVisualPipelineStage19.Source,
    ): FarolUniversalVisualPipelineStage19.Evaluation? {
        if (text.isBlank()) return null
        val block = FarolUniversalVisualPipelineStage19.VisualBlock(
            id = "r8-event:$windowId",
            windowId = windowId,
            windowLayer = Int.MAX_VALUE,
            depth = 1,
            text = text,
            source = source,
        )
        val locations = positiveLocations(block)
        if (locations.size != 1) return null
        return FarolImmediateAddressRouteStage46R7.evaluateImmediateText(locations.single(), windowId, source)
            ?.takeIf { validateEvaluation(it).accepted }
    }

    fun validateEvaluation(evaluation: FarolUniversalVisualPipelineStage19.Evaluation): FarolCausalCorrectionStage21.Validation {
        val r7Owned = evaluation.blockId.startsWith(FarolImmediateAddressRouteStage46R7.SINGLE_BLOCK_PREFIX) ||
            evaluation.blockId.startsWith(FarolImmediateAddressRouteStage46R7.EVENT_TEXT_BLOCK_PREFIX) ||
            evaluation.blockId.startsWith(FarolImmediateAddressRouteStage46R7.AGGREGATE_BLOCK_PREFIX)
        if (!r7Owned) {
            return if (evaluation.addresses.size >= 2) FarolCausalCorrectionStage21.validateEvaluation(evaluation)
            else FarolCausalCorrectionStage21.Validation(false, "single_not_r8_authorized")
        }
        if (evaluation.addresses.isEmpty()) return FarolCausalCorrectionStage21.Validation(false, "r8_empty_location")
        evaluation.addresses.forEachIndexed { index, address ->
            if (!isPositiveLocation(address)) {
                return FarolCausalCorrectionStage21.Validation(false, "r8_location_${index}_no_positive_structure")
            }
        }
        val expected = evaluation.addresses.last()
        if (DestinationAddressIdentityPolicy.signature("visual", expected) != evaluation.addressSignature) {
            return FarolCausalCorrectionStage21.Validation(false, "r8_destination_signature_mismatch")
        }
        return FarolCausalCorrectionStage21.Validation(
            true,
            if (evaluation.addresses.size == 1) "r8_positive_single_location" else "r8_positive_last_location",
        )
    }

    fun isSingleImmediateEvaluation(evaluation: FarolUniversalVisualPipelineStage19.Evaluation): Boolean =
        FarolImmediateAddressRouteStage46R7.isSingleImmediateEvaluation(evaluation)

    fun isAggregateLastAddressEvaluation(evaluation: FarolUniversalVisualPipelineStage19.Evaluation): Boolean =
        FarolImmediateAddressRouteStage46R7.isAggregateLastAddressEvaluation(evaluation)

    /**
     * Cheap pre-collect proof used only to clear an already-final result earlier. A lone unlabeled
     * address remains non-destructive because partial Accessibility events are common. Two positive
     * locations, or one explicit destination cue, are strong enough. If the current destination is
     * still visible, the final stays latched and no blink is introduced.
     */
    fun proveDestinationReplacement(sourceText: String, currentAddressSignature: String?): ReplacementProof {
        val current = currentAddressSignature?.trim()?.takeIf(String::isNotEmpty)
            ?: return ReplacementProof(false, "no_current_signature")
        if (sourceText.isBlank()) return ReplacementProof(false, "no_source_text")
        val block = FarolUniversalVisualPipelineStage19.VisualBlock(
            id = "r8-proof",
            windowId = 1,
            windowLayer = 1,
            depth = 1,
            text = sourceText,
            source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
        )
        val locations = positiveLocations(block)
        if (locations.isEmpty()) return ReplacementProof(false, "no_positive_location")
        val signatures = locations.map { DestinationAddressIdentityPolicy.signature("visual", it) }.filter(String::isNotBlank)
        if (current in signatures) return ReplacementProof(false, "current_destination_still_visible", positiveLocationCount = locations.size)
        val explicitDestination = Regex(
            "(?iu)\\b(destino|destino final|chegada|desembarque|dropoff|drop-off)\\b",
        ).containsMatchIn(sourceText)
        if (locations.size < 2 && !explicitDestination) {
            return ReplacementProof(false, "single_unlabeled_location_not_destructive", positiveLocationCount = locations.size)
        }
        val candidateSignature = signatures.lastOrNull()
            ?: return ReplacementProof(false, "blank_candidate_signature", positiveLocationCount = locations.size)
        return ReplacementProof(
            true,
            if (locations.size >= 2) "different_last_location_pair" else "different_explicit_destination",
            candidateSignature,
            locations.size,
        )
    }

    private fun positiveLocations(block: FarolUniversalVisualPipelineStage19.VisualBlock): List<String> =
        FarolImmediateAddressRouteStage46R7.evaluate(listOf(block))?.addresses.orEmpty()
            .map(::sanitizeLocation)
            .filter(String::isNotBlank)
            .filter(::isPositiveLocation)
            .distinctBy { DestinationAddressIdentityPolicy.signature("visual", it) }

    private fun sanitizeLocation(raw: String): String {
        var value = DestinationAddressIdentityPolicy.cleanDisplayAddress(raw).trim()
        repeat(8) {
            val cleaned = value
                .replace(trailingOperationalToken, "")
                .trim().trimEnd(',', ';', '-', '–', '—').trim()
            if (cleaned == value) return value
            value = cleaned
        }
        return value
    }

    private fun isPositiveLocation(value: String): Boolean {
        if (FarolCausalCorrectionStage21.validateAddress(value).accepted) return true
        return isStrongNamedPlace(value)
    }

    private fun isStrongNamedPlace(value: String): Boolean {
        val normalized = canonical(value)
        val match = strongNamedPlaceLead.find(normalized) ?: return false
        if (match.range.first != 0) return false
        val tail = normalized.substring(match.range.last + 1).trim()
        if (tail.isBlank()) return false
        return tail.split(' ').any { it.length >= 3 && it !in connectorWords }
    }

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val strongNamedPlaceLead = Regex(
        "^(?:shopping|terminal|estacao|aeroporto|rodoviaria|hospital|mercado|restaurante|hotel|pousada|escola|faculdade|universidade|posto|parque|condominio|residencial)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val connectorWords = setOf("e", "de", "da", "do", "das", "dos", "em", "na", "no", "nas", "nos")
    private val trailingOperationalToken = Regex(
        "(?iu)(?:\\s+|,\\s*)(?:R\\$\\s*\\d+(?:[.,]\\d+)?(?:\\s*/\\s*km)?|~?\\d+(?:[.,]\\d+)?\\s*km|\\d+\\s*(?:s|seg|segs|segundos|min|mins|minutos|h|hora|horas)\\.?|PIX|Reclamar|Ocultar|Escolher no mapa)\\s*$",
    )
}
