package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Stage46 R7 universal visible-location route.
 *
 * Runtime contract:
 * - visual package/app identity never authorizes a location;
 * - card/template/ride semantics never authorize a location;
 * - one current visible location is enough to start the real-route pipeline immediately;
 * - multiple current visible locations use physical Y order; deterministic encounter order is fallback;
 * - a POI/named place does not need house number, street type, bairro, city/state or destination cue;
 * - Stage21 may remain as a legacy extractor/fallback for already-proven pairs, but it is never a veto
 *   for an evaluation constructed by R7;
 * - only negative technical hygiene removes obvious non-location UI/fare/time/distance/percentage noise;
 * - this helper performs no Google request, cache lookup, painting, timer or polling. Existing visual
 *   epoch, route freshness and paint-token barriers remain downstream authority.
 */
object FarolImmediateAddressRouteStage46R7 {
    const val CONTRACT_MARKER = "FAROL_IMMEDIATE_ADDRESS_ROUTE_STAGE46_R7"
    const val FIRST_VALID_ADDRESS_MARKER = "FIRST_VALID_ADDRESS_STARTS_ROUTE_IMMEDIATELY_STAGE46_R7"
    const val LAST_ADDRESS_MARKER = "LAST_VISIBLE_ADDRESS_REPLACES_DESTINATION_STAGE46_R7"
    const val CHEAP_SINGLE_MARKER = "SINGLE_ADDRESS_EVENT_TEXT_AVOIDS_OCR_WAIT_STAGE46_R7"
    // Historical compatibility marker retained so older R7 inventory remains readable. It is NOT a veto.
    const val STAGE21_STRUCTURE_MARKER = "STAGE21_ADDRESS_STRUCTURE_RETAINED_STAGE46_R7"
    const val PACKAGE_NEUTRAL_MARKER = "PACKAGE_IDENTITY_NEVER_AUTHORIZES_ROUTE_STAGE46_R7"
    const val FRESHNESS_MARKER = "EXISTING_VISUAL_EPOCH_ROUTE_AND_PAINT_FRESHNESS_RETAINED_STAGE46_R7"
    const val NO_POLLING_MARKER = "EVENT_DRIVEN_IMMEDIATE_ADDRESS_NO_POLLING_STAGE46_R7"
    const val OCR_TRAILING_FARE_MARKER = "OCR_TRAILING_FARE_CANNOT_ENTER_ROUTE_IDENTITY_STAGE46_R7"
    const val UNIVERSAL_VISIBLE_LOCATION_MARKER = "ANY_VISIBLE_LOCATION_CAN_START_REAL_ROUTE_STAGE46_R7"
    const val NO_STAGE21_VETO_MARKER = "NO_STAGE21_SEMANTIC_VETO_FOR_R7_LOCATION_STAGE46_R7"
    const val NAMED_PLACE_MARKER = "NAMED_PLACE_WITHOUT_HOUSE_NUMBER_IS_ROUTEABLE_STAGE46_R7"
    const val NEGATIVE_HYGIENE_ONLY_MARKER = "ONLY_NEGATIVE_TECHNICAL_HYGIENE_BEFORE_ROUTE_STAGE46_R7"
    const val CASE_001170_MARKER = "CASE_001170_ESTACAO_LUZ_HOSPITAL_CLINICAS_REGRESSION_STAGE46_R7"

    const val SINGLE_BLOCK_PREFIX = "r7-single:"
    const val AGGREGATE_BLOCK_PREFIX = "r7-aggregate:"
    const val EVENT_TEXT_BLOCK_PREFIX = "r7-event-single:"

    private val trailingFareLikeToken = Regex(
        pattern = """,\s*(?:R\$\s*)?\d{1,3},\d{2}\s*$""",
        option = RegexOption.IGNORE_CASE,
    )
    private val pureFare = Regex("""^\s*(?:R\$\s*)?\d{1,4}(?:[.,]\d{2})\s*$""", RegexOption.IGNORE_CASE)
    private val pureTime = Regex("""^\s*\d{1,3}\s*(?:min|minuto|minutos|h|hora|horas)\s*$""", RegexOption.IGNORE_CASE)
    private val pureDistance = Regex("""^\s*\d+(?:[.,]\d+)?\s*(?:m|km)\s*$""", RegexOption.IGNORE_CASE)
    private val purePercent = Regex("""^\s*\d{1,3}\s*%\s*$""")
    private val pureRating = Regex("""^\s*\d(?:[.,]\d)?\s*(?:★|estrelas?)?\s*$""", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("""\s+""")
    private val trailingBrokenConnector = Regex("""\b(?:e|de|da|do|das|dos|para|em|na|no)\s*$""", RegexOption.IGNORE_CASE)

    private val exactTechnicalNoise = setOf(
        "origem", "destino", "destino final", "embarque", "partida", "chegada", "desembarque",
        "pickup", "dropoff", "aceitar", "aceitar corrida", "recusar", "rejeitar", "cancelar",
        "fechar", "continuar", "confirmar", "ok", "voltar", "oferta recebida", "pedido de viagem",
        "sua tarifa", "ofereça sua tarifa", "ofereca sua tarifa", "pix", "dinheiro",
        "rota certa", "calculando rota", "aguardando", "carregando",
    )
    private val canonicalTechnicalNoise by lazy { exactTechnicalNoise.map(::canonical).toSet() }

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
            locationQueries(block.text).mapIndexed { index, address ->
                Occurrence(block, address, canonical(address), blockIndex, index)
            }
        }.filter { occurrence ->
            occurrence.canonical.isNotBlank() && isUsableLocationQuery(occurrence.address)
        }
        if (parsed.isEmpty()) return Decision(null, "no_current_visible_location", 0, false)

        // Window/layer ownership is technical freshness/integrity, not semantic authorization.
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

        return Decision(
            evaluation,
            if (ordered.all { hasConcreteGeometry(it.block) }) "aggregate_last_visual_address_geometry"
            else "aggregate_last_detected_address_order",
            uniqueCount,
            false,
        )
    }

    /**
     * Cheap event-local shortcut. The full current visual collector is called first by the service.
     * This path only avoids an OCR round-trip when that event already exposes exactly one usable
     * location. Multi-location event text yields to the full collector so physical order is not guessed.
     */
    fun evaluateImmediateText(
        text: String,
        windowId: Int,
        source: FarolUniversalVisualPipelineStage19.Source,
    ): FarolUniversalVisualPipelineStage19.Evaluation? {
        if (text.isBlank()) return null
        val locations = locationQueries(text).distinctBy(::canonical)
        if (locations.size != 1) return null
        val location = locations.single()
        return buildEvaluation(
            windowId = windowId,
            source = source,
            analysisText = text.take(2400),
            orderedAddresses = listOf(location),
            pickup = location,
            destination = location,
            blockIdPrefix = EVENT_TEXT_BLOCK_PREFIX,
        )
    }

    /**
     * Stage21 is allowed only as validation for foreign legacy pair evaluations that were not built
     * by R7. Any R7 single/aggregate evaluation is authorized by current visual ownership + negative
     * technical hygiene; Stage21 address semantics cannot veto it.
     */
    fun validateEvaluation(
        evaluation: FarolUniversalVisualPipelineStage19.Evaluation,
    ): FarolCausalCorrectionStage21.Validation {
        val r7Single = evaluation.addresses.size == 1 && (
            evaluation.blockId.startsWith(SINGLE_BLOCK_PREFIX) ||
                evaluation.blockId.startsWith(EVENT_TEXT_BLOCK_PREFIX)
            )
        val r7Aggregate = evaluation.addresses.size >= 2 &&
            evaluation.blockId.startsWith(AGGREGATE_BLOCK_PREFIX)

        if (!r7Single && !r7Aggregate) {
            return if (evaluation.addresses.size >= 2) {
                FarolCausalCorrectionStage21.validateEvaluation(evaluation)
            } else {
                FarolCausalCorrectionStage21.Validation(false, "single_not_r7_authorized")
            }
        }

        if (evaluation.addresses.isEmpty()) {
            return FarolCausalCorrectionStage21.Validation(false, "r7_empty_location")
        }
        if (!isUsableLocationQuery(evaluation.destination)) {
            return FarolCausalCorrectionStage21.Validation(false, "r7_destination_technical_noise_or_truncated")
        }
        val expectedDestination = evaluation.addresses.last()
        if (canonical(expectedDestination) != canonical(evaluation.destination)) {
            return FarolCausalCorrectionStage21.Validation(false, "r7_destination_identity_mismatch")
        }
        if (routeSignature(evaluation.destination).isBlank()) {
            return FarolCausalCorrectionStage21.Validation(false, "r7_blank_signature")
        }
        return FarolCausalCorrectionStage21.Validation(
            true,
            if (r7Single) "single_valid_address_immediate" else "aggregate_last_visual_location_immediate",
        )
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

    /** Structured parsing is an extractor, never an authorization barrier. */
    private fun locationQueries(text: String): List<String> {
        val normalized = WrappedAddressTextNormalizer.normalize(text)
        val structured = UniversalScreenAddressParser.findAddresses(normalized)
            .map(::cleanParsedAddress)
            .filter(::isUsableLocationQuery)
        val raw = rawLocationQueries(text)
        return (structured + raw)
            .map(::cleanParsedAddress)
            .filter(::isUsableLocationQuery)
            .distinctBy(::canonical)
    }

    private fun rawLocationQueries(text: String): List<String> {
        val lines = text.lines()
            .map(::cleanParsedAddress)
            .map { whitespace.replace(it, " ").trim() }
            .filter(String::isNotBlank)
        if (lines.isEmpty()) return emptyList()

        val groups = mutableListOf<String>()
        lines.forEach { line ->
            if (isTechnicalNonLocation(line)) return@forEach
            val append = groups.isNotEmpty() && (
                line.startsWith("(") ||
                    parenthesisBalance(groups.last()) > 0 ||
                    groups.last().trimEnd().endsWith("-") ||
                    trailingBrokenConnector.containsMatchIn(groups.last())
                )
            if (append) {
                groups[groups.lastIndex] = cleanParsedAddress(groups.last() + " " + line)
            } else {
                groups += line
            }
        }
        return groups.filter(::isUsableLocationQuery)
    }

    private fun parenthesisBalance(value: String): Int =
        value.count { it == '(' } - value.count { it == ')' }

    private fun cleanParsedAddress(value: String): String {
        val policyCleaned = DestinationAddressIdentityPolicy.cleanDisplayAddress(value)
        val cleaned = policyCleaned.ifBlank { value.trim() }
        if (cleaned.isBlank()) return cleaned
        return cleaned
            .replace(trailingFareLikeToken, "")
            .trim()
            .trimEnd(',')
            .trim()
    }

    private fun isUsableLocationQuery(value: String): Boolean {
        val cleaned = cleanParsedAddress(value)
        if (cleaned.length < 3 || isTechnicalNonLocation(cleaned)) return false
        return canonical(cleaned).any(Char::isLetter)
    }

    private fun isTechnicalNonLocation(value: String): Boolean {
        val cleaned = whitespace.replace(value, " ").trim().trim('(', ')')
        if (cleaned.isBlank()) return true
        val lower = canonical(cleaned)
        if (lower in canonicalTechnicalNoise) return true
        if (pureFare.matches(cleaned) || pureTime.matches(cleaned) || pureDistance.matches(cleaned) ||
            purePercent.matches(cleaned) || pureRating.matches(cleaned)
        ) return true
        if (trailingBrokenConnector.containsMatchIn(cleaned)) return true

        val c = canonical(cleaned)
        if (c.startsWith("aceitar ") || c.startsWith("recusar ") || c.startsWith("rejeitar ") ||
            c.startsWith("cancelar ") || c.startsWith("ofereca ") || c.startsWith("selecionar ")
        ) return true
        if (!c.any(Char::isLetter)) return true
        return false
    }

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
        val signature = routeSignature(destination)
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

    private fun routeSignature(destination: String): String {
        val policy = DestinationAddressIdentityPolicy.signature("visual", destination)
        if (policy.isNotBlank()) return policy
        val c = canonical(destination)
        return if (c.isBlank()) "" else "visual:$c"
    }

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
