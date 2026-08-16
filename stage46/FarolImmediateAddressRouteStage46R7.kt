package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Stage46 R7: a structurally valid address is itself the immediate route trigger.
 *
 * Contract:
 * - the legacy Stage21 pair evaluator is still attempted first by the service;
 * - one valid address must not wait for a destination label, bottom-screen proof, a second address,
 *   OCR, timer, debounce or another AccessibilityEvent before becoming a route candidate;
 * - when the current visual surface exposes multiple valid addresses, the visually last address is
 *   the destination; physical Y geometry wins, with deterministic visual encounter order as fallback;
 * - a cheap event-local text fallback is allowed only when it contains exactly one valid address;
 *   if it contains multiple addresses it yields to the full visual collector so ordering is not guessed;
 * - package identity never authorizes an address;
 * - this helper performs no cache lookup, Google request, painting, timer or polling. Existing visual
 *   epoch, route freshness and paint-token barriers remain downstream authority.
 */
object FarolImmediateAddressRouteStage46R7 {
    const val CONTRACT_MARKER = "FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7"
    const val FIRST_VALID_ADDRESS_MARKER = "FIRST_VALID_ADDRESS_STARTS_ROUTE_IMMEDIATELY_STAGE46_R7"
    const val LAST_ADDRESS_MARKER = "LAST_VISIBLE_ADDRESS_REPLACES_DESTINATION_STAGE46_R7"
    const val CHEAP_SINGLE_MARKER = "SINGLE_ADDRESS_EVENT_TEXT_AVOIDS_OCR_WAIT_STAGE46_R7"
    const val STAGE21_STRUCTURE_MARKER = "STAGE21_ADDRESS_STRUCTURE_RETAINED_STAGE46_R7"
    const val PACKAGE_NEUTRAL_MARKER = "PACKAGE_IDENTITY_NEVER_AUTHORIZES_ROUTE_STAGE46_R7"
    const val FRESHNESS_MARKER = "EXISTING_VISUAL_EPOCH_ROUTE_AND_PAINT_FRESHNESS_RETAINED_STAGE46_R7"
    const val NO_POLLING_MARKER = "EVENT_DRIVEN_IMMEDIATE_ADDRESS_NO_POLLING_STAGE46_R7"

    const val SINGLE_BLOCK_PREFIX = "r7-single:"
    const val AGGREGATE_BLOCK_PREFIX = "r7-aggregate:"
    const val EVENT_TEXT_BLOCK_PREFIX = "r7-event-single:"

    data class Decision(
        val evaluation: FarolUniversalVisualPipelineStage19.Evaluation?,
        val reason: String,
        val uniqueAddressCount: Int,
        val immediateSingle: Boolean,
    )

    private data class Occurrence(
        val block: FarolUniversalVisualPipelineStage19.VisualBlock,
        val address: String,
        val canonical: String,
        val blockIndex: Int,
        val indexInBlock: Int,
    )

    fun evaluate(
        blocks: List<FarolUniversalVisualPipelineStage19.VisualBlock>,
    ): FarolUniversalVisualPipelineStage19.Evaluation? = decide(blocks).evaluation

    fun decide(
        blocks: List<FarolUniversalVisualPipelineStage19.VisualBlock>,
    ): Decision {
        if (blocks.isEmpty()) return Decision(null, "no_blocks", 0, false)

        val usable = blocks.asSequence()
            .filter { it.text.isNotBlank() && !it.syntheticRoot }
            .take(240)
            .toList()
        if (usable.isEmpty()) return Decision(null, "no_usable_blocks", 0, false)

        val parsed = usable.flatMapIndexed { blockIndex, block ->
            parsedAddresses(block.text).mapIndexed { index, address ->
                Occurrence(block, address, canonical(address), blockIndex, index)
            }
        }.filter { occurrence ->
            occurrence.canonical.isNotBlank() && FarolCausalCorrectionStage21.validateAddress(occurrence.address).accepted
        }
        if (parsed.isEmpty()) return Decision(null, "no_valid_address", 0, false)

        // The highest layer that actually contains valid address evidence owns this evaluation.
        val topLayer = parsed.maxOf { it.block.windowLayer }
        val layerOccurrences = parsed.filter { it.block.windowLayer == topLayer }
        val windows = layerOccurrences.groupBy { it.block.windowId }.filterValues { it.isNotEmpty() }
        if (windows.size != 1) {
            return Decision(
                null,
                "ambiguous_top_layer_windows",
                layerOccurrences.map { it.canonical }.distinct().size,
                false,
            )
        }
        val (windowId, windowOccurrences) = windows.entries.single()

        val representatives = windowOccurrences
            .groupBy { it.canonical }
            .mapNotNull { (_, occurrences) -> chooseRepresentative(occurrences) }
        if (representatives.isEmpty()) return Decision(null, "no_representative", 0, false)

        val ordered = orderOccurrences(representatives)
        val uniqueCount = ordered.size
        if (uniqueCount == 1) {
            val only = ordered.single()
            val evaluation = buildEvaluation(
                windowId = windowId,
                source = only.block.source,
                analysisText = only.block.text.take(2400),
                orderedAddresses = listOf(only.address),
                pickup = only.address,
                destination = only.address,
                blockIdPrefix = SINGLE_BLOCK_PREFIX,
            ) ?: return Decision(null, "single_blank_signature", 1, false)
            return Decision(evaluation, "single_valid_address_immediate", 1, true)
        }

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
        if (!validation.accepted) {
            return Decision(null, "aggregate_${validation.reason}", uniqueCount, false)
        }
        return Decision(
            evaluation,
            if (ordered.all { hasConcreteGeometry(it.block) }) "aggregate_last_visual_address_geometry"
            else "aggregate_last_detected_address_order",
            uniqueCount,
            false,
        )
    }

    /**
     * Event-local shortcut used only to avoid an unnecessary screenshot/OCR round-trip when the
     * Accessibility event already exposes exactly one structurally valid address. Multiple addresses
     * deliberately return null here so the complete visual blocks determine their real last order.
     */
    fun evaluateImmediateText(
        text: String,
        windowId: Int,
        source: FarolUniversalVisualPipelineStage19.Source,
    ): FarolUniversalVisualPipelineStage19.Evaluation? {
        if (text.isBlank()) return null
        val valid = parsedAddresses(text)
            .filter { FarolCausalCorrectionStage21.validateAddress(it).accepted }
            .distinctBy(::canonical)
        if (valid.size != 1) return null
        val address = valid.single()
        return buildEvaluation(
            windowId = windowId,
            source = source,
            analysisText = text.take(2400),
            orderedAddresses = listOf(address),
            pickup = address,
            destination = address,
            blockIdPrefix = EVENT_TEXT_BLOCK_PREFIX,
        )
    }

    /**
     * Pair candidates keep Stage21 unchanged. Single candidates are accepted only when they were
     * constructed by R7 and their destination still passes Stage21 structural address validation.
     * Labels such as origem/embarque/destino are intentionally not decision authority in R7.
     */
    fun validateEvaluation(
        evaluation: FarolUniversalVisualPipelineStage19.Evaluation,
    ): FarolCausalCorrectionStage21.Validation {
        if (evaluation.addresses.size >= 2) {
            return FarolCausalCorrectionStage21.validateEvaluation(evaluation)
        }
        val authorizedPrefix = evaluation.blockId.startsWith(SINGLE_BLOCK_PREFIX) ||
            evaluation.blockId.startsWith(EVENT_TEXT_BLOCK_PREFIX)
        if (evaluation.addresses.size != 1 || !authorizedPrefix) {
            return FarolCausalCorrectionStage21.Validation(false, "single_not_r7_authorized")
        }
        val only = evaluation.addresses.single()
        if (canonical(only) != canonical(evaluation.destination)) {
            return FarolCausalCorrectionStage21.Validation(false, "single_destination_identity_mismatch")
        }
        val address = FarolCausalCorrectionStage21.validateAddress(evaluation.destination)
        if (!address.accepted) {
            return FarolCausalCorrectionStage21.Validation(false, "single_destination_${address.reason}")
        }
        return FarolCausalCorrectionStage21.Validation(true, "single_valid_address_immediate")
    }

    fun isSingleImmediateEvaluation(
        evaluation: FarolUniversalVisualPipelineStage19.Evaluation,
    ): Boolean = evaluation.addresses.size == 1 && (
        evaluation.blockId.startsWith(SINGLE_BLOCK_PREFIX) ||
            evaluation.blockId.startsWith(EVENT_TEXT_BLOCK_PREFIX)
        )

    fun isAggregateLastAddressEvaluation(
        evaluation: FarolUniversalVisualPipelineStage19.Evaluation,
    ): Boolean = evaluation.addresses.size >= 2 && evaluation.blockId.startsWith(AGGREGATE_BLOCK_PREFIX)

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
                .thenBy { it.blockIndex }
                .thenBy { it.indexInBlock },
        )
        .firstOrNull()

    private fun orderOccurrences(occurrences: List<Occurrence>): List<Occurrence> {
        if (occurrences.all { hasConcreteGeometry(it.block) }) {
            return occurrences.sortedWith(
                compareBy<Occurrence> { it.block.top }
                    .thenBy { it.block.bottom }
                    .thenBy { it.block.left }
                    .thenBy { it.blockIndex }
                    .thenBy { it.indexInBlock },
            )
        }
        // Accessibility/OCR block encounter order is the deterministic fallback when concrete
        // geometry is unavailable. This avoids waiting for another event while still choosing last.
        return occurrences.sortedWith(compareBy<Occurrence> { it.blockIndex }.thenBy { it.indexInBlock })
    }

    private fun geometryArea(block: FarolUniversalVisualPipelineStage19.VisualBlock): Long {
        if (!hasConcreteGeometry(block)) return Long.MAX_VALUE
        return (block.right - block.left).toLong() * (block.bottom - block.top).toLong()
    }

    private fun hasConcreteGeometry(block: FarolUniversalVisualPipelineStage19.VisualBlock): Boolean =
        block.top != Int.MAX_VALUE && block.bottom != Int.MAX_VALUE &&
            block.bottom > block.top && block.right > block.left

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
}