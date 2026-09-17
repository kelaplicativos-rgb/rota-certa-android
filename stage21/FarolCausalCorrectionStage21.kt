package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/** Stage21: causal corrections proved by Stage20 physical forensics. */
object FarolCausalCorrectionStage21 {
    const val CONTRACT_MARKER = "FAROL_CAUSAL_CORRECTION_STAGE21"
    const val SEMANTIC_GATE_MARKER = "SEMANTIC_ADDRESS_GATE_BEFORE_CACHE_AND_GOOGLE_STAGE21"
    const val EVENT_COALESCING_MARKER = "EVENT_COALESCING_AND_SELF_OVERLAY_SUPPRESSION_STAGE21"
    const val OCR_COALESCING_MARKER = "OCR_SINGLE_FLIGHT_NO_BACKLOG_STAGE21"

    private const val NORMAL_BURST_NS = 75_000_000L
    private const val BUSY_BURST_NS = 180_000_000L

    data class Validation(val accepted: Boolean, val reason: String)

    data class EventDecision(val process: Boolean, val reason: String)

    class EventGate {
        private var lastFingerprint: String? = null
        private var lastAcceptedNs: Long = Long.MIN_VALUE

        @Synchronized
        fun decide(
            packageName: String?,
            eventType: Int,
            windowId: Int,
            nowNs: Long,
            selfPackageName: String,
            selfSuppressionUntilNs: Long,
            expensiveWorkActive: Boolean,
        ): EventDecision {
            val normalizedPackage = packageName.orEmpty().trim().lowercase(Locale.ROOT)
            if (normalizedPackage == selfPackageName.lowercase(Locale.ROOT) && nowNs <= selfSuppressionUntilNs) {
                return EventDecision(false, "self_overlay_event")
            }
            val fingerprint = "$normalizedPackage|$eventType|$windowId"
            val delta = if (lastAcceptedNs == Long.MIN_VALUE) Long.MAX_VALUE else (nowNs - lastAcceptedNs).coerceAtLeast(0L)
            val burstNs = if (expensiveWorkActive) BUSY_BURST_NS else NORMAL_BURST_NS
            if (fingerprint == lastFingerprint && delta < burstNs) {
                return EventDecision(false, if (expensiveWorkActive) "equivalent_event_busy" else "equivalent_event_burst")
            }
            lastFingerprint = fingerprint
            lastAcceptedNs = nowNs
            return EventDecision(true, "accepted")
        }
    }

    data class OcrRequest(val startNow: Boolean, val token: Long, val reason: String)

    class OcrGate {
        private var generation = 0L
        private var activeToken: Long? = null
        private var rerunWanted = false

        @Synchronized
        fun request(): OcrRequest {
            generation += 1L
            if (activeToken != null) {
                rerunWanted = true
                return OcrRequest(false, generation, "coalesced_busy")
            }
            activeToken = generation
            rerunWanted = false
            return OcrRequest(true, generation, "start")
        }

        @Synchronized
        fun cancelBecauseAccessibilityWon(): Long {
            generation += 1L
            rerunWanted = false
            return generation
        }

        @Synchronized
        fun isCurrent(token: Long): Boolean = activeToken == token && generation == token

        @Synchronized
        fun complete(token: Long): Boolean {
            if (activeToken == token) activeToken = null
            val rerun = rerunWanted
            rerunWanted = false
            return rerun
        }
    }

    fun hasAddressEvidence(texts: Sequence<String>): Boolean = texts
        .take(96)
        .any { text -> addressLeadRegex.containsMatchIn(normalizeWhitespace(text)) }

    fun validateEvaluation(evaluation: FarolUniversalVisualPipelineStage19.Evaluation): Validation {
        if (evaluation.addresses.size < 2) return Validation(false, "less_than_two_addresses")
        if (canonical(evaluation.pickup) == canonical(evaluation.destination)) return Validation(false, "same_pickup_destination")
        val pickup = validateAddress(evaluation.pickup)
        if (!pickup.accepted) return Validation(false, "pickup_${pickup.reason}")
        val destination = validateAddress(evaluation.destination)
        if (!destination.accepted) return Validation(false, "destination_${destination.reason}")
        return Validation(true, "structurally_complete_pair")
    }

    fun validateAddress(address: String): Validation {
        val value = normalizeWhitespace(address).trim(' ', ',', ';', '-', '–', '—')
        if (value.length < 6) return Validation(false, "too_short")
        if (danglingConnectorRegex.containsMatchIn(value)) return Validation(false, "dangling_connector")
        if (danglingPunctuationRegex.containsMatchIn(value)) return Validation(false, "dangling_punctuation")

        val streetMatch = streetLeadRegex.find(value)
        val namedPlace = namedPlaceLeadRegex.containsMatchIn(value)
        if (streetMatch == null && !namedPlace) return Validation(false, "no_address_structure")

        val hasHouseNumber = houseNumberRegex.containsMatchIn(value) || noNumberRegex.containsMatchIn(value)
        val hasStateOrCep = stateOrCepRegex.containsMatchIn(value)
        val commaParts = value.split(',').map(String::trim).filter(String::isNotBlank)
        val hasLocalityStructure = commaParts.size >= 2 || hasStateOrCep || localityWordRegex.containsMatchIn(value)

        if (streetMatch != null) {
            val tail = value.substring(streetMatch.range.last + 1)
            val meaningful = canonical(tail).split(' ')
                .filter { it.length >= 2 && it !in connectorWords && it !in streetTypeWords }
            if (meaningful.isEmpty()) return Validation(false, "missing_street_name")
            if (!hasHouseNumber && !hasLocalityStructure) return Validation(false, "street_without_context")
        } else if (!hasHouseNumber && !hasLocalityStructure) {
            return Validation(false, "place_without_context")
        }
        return Validation(true, "complete")
    }

    /**
     * Fast Stage21 evaluator: only the highest visual layer containing address evidence is parsed.
     * It preserves Stage19 visual authority and binding identity, while avoiding parsing every
     * lower window/block and enforcing the semantic gate before any cache/Google path exists.
     */
    fun evaluate(
        blocks: List<FarolUniversalVisualPipelineStage19.VisualBlock>,
    ): FarolUniversalVisualPipelineStage19.Evaluation? {
        if (blocks.isEmpty()) return null
        data class Parsed(val block: FarolUniversalVisualPipelineStage19.VisualBlock, val addresses: List<String>)
        fun visualTop(block: FarolUniversalVisualPipelineStage19.VisualBlock): Int =
            block.top.takeUnless { it == Int.MAX_VALUE } ?: Int.MAX_VALUE

        val layers = blocks.asSequence()
            .filter { it.text.isNotBlank() && !it.syntheticRoot }
            .map { it.windowLayer }
            .distinct()
            .sortedDescending()
            .toList()

        for (layer in layers) {
            val layerBlocks = blocks.asSequence()
                .filter { it.windowLayer == layer && it.text.isNotBlank() && !it.syntheticRoot }
                .take(160)
                .toList()
            if (layerBlocks.isEmpty()) continue
            val parsed = layerBlocks.map { block ->
                Parsed(
                    block,
                    UniversalScreenAddressParser.findAddresses(
                        WrappedAddressTextNormalizer.normalize(block.text),
                    ).map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
                        .filter(String::isNotBlank)
                        .distinctBy(::canonical),
                )
            }
            val addressBearing = parsed.filter { it.addresses.isNotEmpty() }
            if (addressBearing.isEmpty()) continue

            val anchor = if (addressBearing.any { visualTop(it.block) != Int.MAX_VALUE }) {
                addressBearing.minWithOrNull(
                    compareBy<Parsed> { visualTop(it.block) }
                        .thenByDescending { it.block.depth }
                        .thenBy { it.block.text.length },
                )
            } else {
                val deepest = addressBearing.maxOf { it.block.depth }
                val specific = addressBearing.filter { it.block.depth == deepest }
                if (specific.map { canonical(it.addresses.last()) }.distinct().size > 1) return null
                specific.minByOrNull { it.block.text.length }
            } ?: return null

            val candidates = parsed.asSequence()
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
            val canonicalAddresses = winner.addresses.joinToString("|") { canonical(it) }
            val stableBlockIdentity = "${winner.block.windowId}:$canonicalAddresses"
            val evaluation = FarolUniversalVisualPipelineStage19.Evaluation(
                windowId = winner.block.windowId,
                blockId = stableBlockIdentity,
                source = winner.block.source,
                analysisText = winner.block.text.trim(),
                addresses = winner.addresses,
                pickup = pickup,
                destination = destination,
                addressSignature = addressSignature,
                screenHash = "$stableBlockIdentity|$addressSignature".hashCode(),
            )
            return evaluation.takeIf { validateEvaluation(it).accepted }
        }
        return null
    }

    private fun contains(
        container: FarolUniversalVisualPipelineStage19.VisualBlock,
        child: FarolUniversalVisualPipelineStage19.VisualBlock,
    ): Boolean {
        if (container.top == Int.MAX_VALUE || child.top == Int.MAX_VALUE) return false
        if (container.right <= container.left || child.right <= child.left) return false
        return child.left >= container.left && child.right <= container.right &&
            child.top >= container.top && child.bottom <= container.bottom
    }

    private fun normalizeWhitespace(value: String): String = value.replace(Regex("\\s+"), " ").trim()

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val addressLeadRegex = Regex(
        "(?:^|[\\s:(])(?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|passagem|servidao|servidão|shopping|terminal|estacao|estação|aeroporto|rodoviaria|rodoviária|hospital|mercado|restaurante|hotel|pousada|escola|faculdade|universidade|posto|parque|condominio|condomínio|residencial)(?:\\b|(?=\\s))",
        RegexOption.IGNORE_CASE,
    )
    private val streetLeadRegex = Regex(
        "(?:^|[\\s:(])(?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|passagem|servidao|servidão)(?:\\b|(?=\\s))",
        RegexOption.IGNORE_CASE,
    )
    private val namedPlaceLeadRegex = Regex(
        "^(?:shopping|terminal|estacao|estação|aeroporto|rodoviaria|rodoviária|hospital|mercado|restaurante|hotel|pousada|escola|faculdade|universidade|posto|parque|condominio|condomínio|residencial)(?:\\b|(?=\\s))",
        RegexOption.IGNORE_CASE,
    )
    private val danglingConnectorRegex = Regex("\\b(?:e|de|da|do|das|dos|em|na|no|nas|nos|com|para|por)\\s*$", RegexOption.IGNORE_CASE)
    private val danglingPunctuationRegex = Regex("[,;:/\\-–—]\\s*$")
    private val houseNumberRegex = Regex("(?:,\\s*|\\bn(?:[º°o]\\.?|\\.|[uú]mero)\\s*[:\\-]?\\s*)\\d{1,6}(?:[-/][\\p{L}\\d]+|[\\p{L}])?\\b", RegexOption.IGNORE_CASE)
    private val noNumberRegex = Regex("\\b(?:s\\s*/\\s*n|s\\s*n|sem\\s+n[uú]mero)\\b", RegexOption.IGNORE_CASE)
    private val stateOrCepRegex = Regex("(?:[,\\-]\\s*(?:AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)\\b|\\b\\d{5}-?\\d{3}\\b)", RegexOption.IGNORE_CASE)
    private val localityWordRegex = Regex("\\b(?:bairro|jardim|vila|distrito|municipio|município|residencial|condominio|condomínio|loteamento|centro|cidade)\\b", RegexOption.IGNORE_CASE)
    private val connectorWords = setOf("e", "de", "da", "do", "das", "dos", "em", "na", "no", "nas", "nos", "com", "para", "por")
    private val streetTypeWords = setOf("r", "av", "rua", "avenida", "alameda", "travessa", "estrada", "rodovia", "praca", "praça", "largo", "via", "viela", "beco", "marginal", "passagem", "servidao", "servidão")
}
