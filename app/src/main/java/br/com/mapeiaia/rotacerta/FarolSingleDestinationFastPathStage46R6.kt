package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Stage46 R6: the first address is no longer a mandatory latency barrier when the current visual
 * surface already proves a final destination with high confidence.
 *
 * Safety contract:
 * - the legacy Stage21 two-address path remains authoritative and is always tried first;
 * - split visible address blocks on one concrete top-layer window may be aggregated and ordered by
 *   physical Y geometry; the visually last address is the destination;
 * - a truly single address is accepted only when Stage21 validates its address structure AND the
 *   current visual context proves destination intent (destination cue) or a strong bottom-most
 *   final-position signal without pickup/origin cues;
 * - package identity never participates in the decision;
 * - this helper never calls cache, Google, paints, timers or polling.
 */
object FarolSingleDestinationFastPathStage46R6 {
    const val CONTRACT_MARKER = "FAROL_SINGLE_DESTINATION_FAST_PATH_STAGE46_R6"
    const val TWO_ADDRESSES_NOT_MANDATORY_MARKER = "TWO_ADDRESSES_NOT_MANDATORY_WHEN_SINGLE_DESTINATION_HIGH_CONFIDENCE_STAGE46_R6"
    const val LAST_VISUAL_ADDRESS_MARKER = "LAST_GEOMETRIC_VISIBLE_ADDRESS_IS_DESTINATION_STAGE46_R6"
    const val SPLIT_BLOCK_MARKER = "SPLIT_ADDRESS_BLOCKS_CAN_FORM_CURRENT_VISUAL_DESTINATION_STAGE46_R6"
    const val SINGLE_SEMANTIC_MARKER = "SINGLE_DESTINATION_STILL_USES_STAGE21_ADDRESS_VALIDATION_STAGE46_R6"
    const val PICKUP_GUARD_MARKER = "SINGLE_PICKUP_OR_ORIGIN_CUE_CANNOT_AUTHORIZE_ROUTE_STAGE46_R6"
    const val AMBIGUITY_MARKER = "AMBIGUOUS_SINGLE_ADDRESS_FALLS_BACK_TO_LEGACY_ACQUISITION_STAGE46_R6"
    const val NO_PACKAGE_MARKER = "PACKAGE_IDENTITY_NEVER_AUTHORIZES_SINGLE_DESTINATION_STAGE46_R6"
    const val NO_POLLING_MARKER = "EVENT_DRIVEN_SINGLE_DESTINATION_NO_POLLING_STAGE46_R6"

    const val SINGLE_BLOCK_PREFIX = "r6-single:"
    const val AGGREGATE_BLOCK_PREFIX = "r6-aggregate:"

    data class Decision(
        val evaluation: FarolUniversalVisualPipelineStage19.Evaluation?,
        val reason: String,
        val uniqueAddressCount: Int,
        val singleFastPath: Boolean,
    )

    private data class Occurrence(
        val block: FarolUniversalVisualPipelineStage19.VisualBlock,
        val address: String,
        val canonical: String,
        val indexInBlock: Int,
    )

    /**
     * This is a fallback after FarolCausalCorrectionStage21.evaluate() returned null.
     */
    fun evaluate(
        blocks: List<FarolUniversalVisualPipelineStage19.VisualBlock>,
    ): FarolUniversalVisualPipelineStage19.Evaluation? = decide(blocks).evaluation

    fun decide(
        blocks: List<FarolUniversalVisualPipelineStage19.VisualBlock>,
    ): Decision {
        if (blocks.isEmpty()) return Decision(null, "no_blocks", 0, false)

        val usable = blocks.asSequence()
            .filter { it.text.isNotBlank() && !it.syntheticRoot }
            .take(200)
            .toList()
        if (usable.isEmpty()) return Decision(null, "no_usable_blocks", 0, false)

        val parsed = usable.flatMap { block ->
            parsedAddresses(block.text).mapIndexed { index, address ->
                Occurrence(block, address, canonical(address), index)
            }
        }.filter { it.canonical.isNotBlank() }
        if (parsed.isEmpty()) return Decision(null, "no_address", 0, false)

        // Visual authority is the highest layer that actually contains address evidence.
        val topLayer = parsed.maxOf { it.block.windowLayer }
        val layerOccurrences = parsed.filter { it.block.windowLayer == topLayer }
        val windows = layerOccurrences.groupBy { it.block.windowId }

        // Two equal-layer windows with different address evidence are visually ambiguous. Do not guess.
        val nonEmptyWindows = windows.filterValues { it.isNotEmpty() }
        if (nonEmptyWindows.size != 1) {
            return Decision(null, "ambiguous_top_layer_windows", layerOccurrences.map { it.canonical }.distinct().size, false)
        }
        val (windowId, windowOccurrences) = nonEmptyWindows.entries.single()

        val representatives = windowOccurrences
            .groupBy { it.canonical }
            .mapNotNull { (_, occurrences) -> chooseRepresentative(occurrences) }
        if (representatives.isEmpty()) return Decision(null, "no_representative", 0, false)

        val uniqueCount = representatives.size
        val allHaveGeometry = representatives.all { hasConcreteGeometry(it.block) }
        val ordered = when {
            allHaveGeometry -> representatives.sortedWith(
                compareBy<Occurrence> { it.block.top }
                    .thenBy { it.block.bottom }
                    .thenBy { it.block.left }
                    .thenBy { it.indexInBlock },
            )
            representatives.size == 1 -> representatives
            else -> return Decision(null, "multiple_addresses_without_reliable_geometry", uniqueCount, false)
        }

        if (ordered.size >= 2) {
            val pickup = ordered.first().address
            val destination = ordered.last().address
            if (canonical(pickup) == canonical(destination)) {
                return Decision(null, "same_first_last_address", uniqueCount, false)
            }
            val evaluation = buildEvaluation(
                windowId = windowId,
                source = ordered.last().block.source,
                analysisText = ordered.joinToString("\n") { it.block.text }.take(2400),
                orderedAddresses = ordered.map { it.address },
                pickup = pickup,
                destination = destination,
                blockIdPrefix = AGGREGATE_BLOCK_PREFIX,
            ) ?: return Decision(null, "aggregate_blank_signature", uniqueCount, false)
            val validation = FarolCausalCorrectionStage21.validateEvaluation(evaluation)
            return if (validation.accepted) {
                Decision(evaluation, "aggregate_last_visual_address", uniqueCount, false)
            } else {
                Decision(null, "aggregate_${validation.reason}", uniqueCount, false)
            }
        }

        val only = ordered.single()
        val validation = FarolCausalCorrectionStage21.validateAddress(only.address)
        if (!validation.accepted) {
            return Decision(null, "single_${validation.reason}", 1, false)
        }
        if (!hasConcreteGeometry(only.block)) {
            return Decision(null, "single_without_geometry", 1, false)
        }

        val sameWindowBlocks = usable.filter { it.windowLayer == topLayer && it.windowId == windowId }
        val localContext = relatedContext(only.block, sameWindowBlocks)
        val hasDestinationCue = destinationCueRegex.containsMatchIn(localContext)
        val hasPickupCue = pickupCueRegex.containsMatchIn(localContext)
        if (hasPickupCue && !hasDestinationCue) {
            return Decision(null, "single_pickup_or_origin_cue", 1, false)
        }
        if (hasPickupCue && hasDestinationCue) {
            return Decision(null, "single_conflicting_role_cues", 1, false)
        }

        val bottomMostStrong = isStrongBottomMostSingle(only, sameWindowBlocks)
        if (!hasDestinationCue && !bottomMostStrong) {
            return Decision(null, "single_not_proven_final", 1, false)
        }

        val evaluation = buildEvaluation(
            windowId = windowId,
            source = only.block.source,
            analysisText = localContext.take(2400),
            orderedAddresses = listOf(only.address),
            pickup = only.address,
            destination = only.address,
            blockIdPrefix = SINGLE_BLOCK_PREFIX,
        ) ?: return Decision(null, "single_blank_signature", 1, false)

        return Decision(
            evaluation,
            if (hasDestinationCue) "single_destination_cue" else "single_strong_bottom_final",
            1,
            true,
        )
    }

    /**
     * Downstream semantic gate. Legacy pair evaluations are unchanged. A single evaluation is
     * accepted only if it was explicitly constructed by this R6 helper and the destination still
     * passes Stage21 structural address validation.
     */
    fun validateEvaluation(
        evaluation: FarolUniversalVisualPipelineStage19.Evaluation,
    ): FarolCausalCorrectionStage21.Validation {
        if (evaluation.addresses.size >= 2) {
            return FarolCausalCorrectionStage21.validateEvaluation(evaluation)
        }
        if (evaluation.addresses.size != 1 || !evaluation.blockId.startsWith(SINGLE_BLOCK_PREFIX)) {
            return FarolCausalCorrectionStage21.Validation(false, "single_not_r6_authorized")
        }
        if (canonical(evaluation.addresses.single()) != canonical(evaluation.destination)) {
            return FarolCausalCorrectionStage21.Validation(false, "single_destination_identity_mismatch")
        }
        val address = FarolCausalCorrectionStage21.validateAddress(evaluation.destination)
        if (!address.accepted) {
            return FarolCausalCorrectionStage21.Validation(false, "single_destination_${address.reason}")
        }
        return FarolCausalCorrectionStage21.Validation(true, "single_destination_high_confidence")
    }

    fun isSingleFastPathEvaluation(
        evaluation: FarolUniversalVisualPipelineStage19.Evaluation,
    ): Boolean = evaluation.addresses.size == 1 && evaluation.blockId.startsWith(SINGLE_BLOCK_PREFIX)

    private fun parsedAddresses(text: String): List<String> =
        UniversalScreenAddressParser.findAddresses(WrappedAddressTextNormalizer.normalize(text))
            .map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
            .filter(String::isNotBlank)
            .distinctBy(::canonical)

    private fun chooseRepresentative(occurrences: List<Occurrence>): Occurrence? = occurrences
        .sortedWith(
            compareByDescending<Occurrence> { hasConcreteGeometry(it.block) }
                .thenByDescending { it.block.depth }
                .thenBy { geometryArea(it.block) }
                .thenBy { it.block.text.length },
        )
        .firstOrNull()

    private fun geometryArea(block: FarolUniversalVisualPipelineStage19.VisualBlock): Long {
        if (!hasConcreteGeometry(block)) return Long.MAX_VALUE
        return (block.right - block.left).toLong() * (block.bottom - block.top).toLong()
    }

    private fun hasConcreteGeometry(block: FarolUniversalVisualPipelineStage19.VisualBlock): Boolean =
        block.top != Int.MAX_VALUE && block.bottom != Int.MAX_VALUE &&
            block.bottom > block.top && block.right > block.left

    private fun relatedContext(
        addressBlock: FarolUniversalVisualPipelineStage19.VisualBlock,
        sameWindowBlocks: List<FarolUniversalVisualPipelineStage19.VisualBlock>,
    ): String {
        val candidates = sameWindowBlocks.asSequence()
            .filter { block ->
                block.id == addressBlock.id ||
                    block.id == addressBlock.parentId ||
                    addressBlock.id.startsWith(block.id + "/") ||
                    block.id.startsWith(addressBlock.id + "/") ||
                    geometricallyNear(block, addressBlock)
            }
            .sortedWith(compareBy<FarolUniversalVisualPipelineStage19.VisualBlock> { it.top }.thenBy { it.left })
            .map { it.text.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .take(12)
            .toList()
        return candidates.joinToString("\n").ifBlank { addressBlock.text }
    }

    private fun geometricallyNear(
        a: FarolUniversalVisualPipelineStage19.VisualBlock,
        b: FarolUniversalVisualPipelineStage19.VisualBlock,
    ): Boolean {
        if (!hasConcreteGeometry(a) || !hasConcreteGeometry(b)) return false
        val verticalGap = when {
            a.bottom < b.top -> b.top - a.bottom
            b.bottom < a.top -> a.top - b.bottom
            else -> 0
        }
        val width = maxOf(a.right, b.right) - minOf(a.left, b.left)
        val horizontalOverlap = minOf(a.right, b.right) - maxOf(a.left, b.left)
        return verticalGap <= maxOf(160, (b.bottom - b.top) * 3) &&
            (horizontalOverlap > 0 || width <= maxOf(a.right - a.left, b.right - b.left) * 2)
    }

    private fun isStrongBottomMostSingle(
        occurrence: Occurrence,
        sameWindowBlocks: List<FarolUniversalVisualPipelineStage19.VisualBlock>,
    ): Boolean {
        if (!strongCompleteAddress(occurrence.address)) return false
        val geometryBlocks = sameWindowBlocks.filter(::hasConcreteGeometry)
        if (geometryBlocks.size < 2) return false
        val minTop = geometryBlocks.minOf { it.top }
        val maxBottom = geometryBlocks.maxOf { it.bottom }
        val span = (maxBottom - minTop).coerceAtLeast(1)
        val center = occurrence.block.top + (occurrence.block.bottom - occurrence.block.top) / 2
        val relativePermille = ((center - minTop).toLong() * 1000L / span.toLong()).toInt()
        val lastMeaningfulBottom = geometryBlocks
            .filter { it.text.isNotBlank() }
            .maxOfOrNull { it.bottom } ?: occurrence.block.bottom
        val nearVisualEnd = occurrence.block.bottom >= lastMeaningfulBottom - maxOf(80, span / 12)
        return relativePermille >= 620 && nearVisualEnd
    }

    private fun strongCompleteAddress(address: String): Boolean {
        val value = address.trim()
        val hasNumber = Regex("(?:,\\s*\\d{1,6}(?:[-/][\\p{L}\\d]+|[\\p{L}])?\\b|\\bs\\s*/?\\s*n\\b)", RegexOption.IGNORE_CASE)
            .containsMatchIn(value)
        val hasLocality = Regex(
            "(?:[,\\-]\\s*(?:AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)\\b|\\b\\d{5}-?\\d{3}\\b|\\b(?:centro|bairro|jardim|vila|cidade|municipio|município|distrito|residencial|condominio|condomínio)\\b)",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(value) || value.count { it == ',' } >= 2
        return hasNumber && hasLocality
    }

    private fun buildEvaluation(
        windowId: Int,
        source: FarolUniversalVisualPipelineStage19.Source,
        analysisText: String,
        orderedAddresses: List<String>,
        pickup: String,
        destination: String,
        blockIdPrefix: String,
    ): FarolUniversalVisualPipelineStage19.Evaluation? {
        val signature = DestinationAddressIdentityPolicy.signature("visual", destination)
        if (signature.isBlank()) return null
        val identityAddresses = orderedAddresses.joinToString("|") { canonical(it) }
        val stableIdentity = "$blockIdPrefix$windowId:$identityAddresses"
        return FarolUniversalVisualPipelineStage19.Evaluation(
            windowId = windowId,
            blockId = stableIdentity,
            source = source,
            analysisText = analysisText.trim(),
            addresses = orderedAddresses,
            pickup = pickup,
            destination = destination,
            addressSignature = signature,
            screenHash = "$stableIdentity|$signature".hashCode(),
        )
    }

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val destinationCueRegex = Regex(
        "\\b(?:destino|destination|chegada|desembarque|drop[ -]?off|entrega|deixar\\s+em|ir\\s+para|indo\\s+para|levar\\s+para)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val pickupCueRegex = Regex(
        "\\b(?:origem|origin|embarque|pickup|pick[ -]?up|buscar|busque|retirada|coleta|partida|sa[ií]da|pegar\\s+em|passageiro\\s+em)\\b",
        RegexOption.IGNORE_CASE,
    )
}
