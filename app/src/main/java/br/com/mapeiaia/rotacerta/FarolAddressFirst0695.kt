package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * 0.1.695 — address-first admission for the FAROL critical path.
 *
 * RideCardConfirmationPolicy0185 remains the primary anti-feed barrier. This helper only opens a
 * second chance when that layout-specific barrier rejects an inDrive snapshot but the same selected
 * visual surface still carries a small, self-contained set of address + ride semantics.
 *
 * It never authorizes route work by package identity alone and never guesses among 3+ addresses.
 * Downstream Stage47/R6/0188 gates remain authoritative before any route or final paint.
 */
object FarolAddressFirst0695 {
    const val CONTRACT_MARKER = "FAROL_ADDRESS_FIRST_0695"
    const val LAYOUT_MISS_NOT_ROUTE_VETO_MARKER = "LAYOUT_SIGNATURE_MISS_CANNOT_VETO_SAFE_ADDRESS_EVIDENCE_0695"
    const val FEED_AMBIGUITY_STILL_BLOCKED_MARKER = "MULTI_CARD_FEED_AMBIGUITY_STILL_BLOCKED_0695"
    const val DOWNSTREAM_GATES_RETAINED_MARKER = "STAGE47_R6_0188_REMAIN_ROUTE_AUTHORITIES_0695"

    private const val INDRIVE_PACKAGE = "sinet.startup.indriver"

    data class Decision(
        val allowPipeline: Boolean,
        val analysisText: String,
        val uniqueAddressCount: Int,
        val reason: String,
    )

    fun evaluate(
        packageName: String?,
        rawText: String,
        rejectedByLayoutGate: Boolean,
    ): Decision {
        if (!rejectedByLayoutGate) {
            return Decision(true, rawText, countAddresses(rawText), "legacy_gate_accepted")
        }

        val normalizedPackage = packageName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (normalizedPackage != INDRIVE_PACKAGE || rawText.isBlank()) {
            return Decision(false, rawText, countAddresses(rawText), "layout_rejection_not_eligible")
        }

        val addresses = normalizedAddresses(rawText)
        if (addresses.isEmpty()) return Decision(false, rawText, 0, "no_address_evidence")
        if (addresses.size >= 3) {
            return Decision(false, rawText, addresses.size, "three_or_more_addresses_are_feed_ambiguous")
        }

        val canonicalText = canonical(rawText)
        val rideStarts = RIDE_START_REGEX.findAll(canonicalText).count()
        val actionCue = ACTION_CUE_REGEX.containsMatchIn(canonicalText)
        val destinationCue = DESTINATION_CUE_REGEX.containsMatchIn(canonicalText)
        val originCue = ORIGIN_CUE_REGEX.containsMatchIn(canonicalText)
        val fareCue = FARE_REGEX.containsMatchIn(rawText)
        val etaCue = ETA_REGEX.containsMatchIn(canonicalText)

        val safeSingle = addresses.size == 1 && (
            actionCue ||
                (rideStarts == 1 && destinationCue && (fareCue || etaCue))
            )

        // Two addresses may represent pickup + destination only when one ride-card context is
        // visible. Multiple "pedido de viagem" anchors remain blocked because they can be a feed.
        val safePair = addresses.size == 2 &&
            rideStarts == 1 &&
            actionCue &&
            (fareCue || etaCue || (originCue && destinationCue))

        val allowed = safeSingle || safePair
        return Decision(
            allowPipeline = allowed,
            analysisText = rawText,
            uniqueAddressCount = addresses.size,
            reason = when {
                safeSingle -> "safe_single_address_second_chance"
                safePair -> "safe_pickup_destination_second_chance"
                rideStarts > 1 -> "multiple_ride_cards_visible"
                addresses.size == 2 -> "two_addresses_without_single_card_proof"
                else -> "single_address_without_ride_semantics"
            },
        )
    }

    internal fun normalizedAddresses(rawText: String): List<String> =
        UniversalScreenAddressParser.findAddresses(WrappedAddressTextNormalizer.normalize(rawText))
            .map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
            .filter(String::isNotBlank)
            .distinctBy(::canonical)

    internal fun countAddresses(rawText: String): Int = normalizedAddresses(rawText).size

    internal fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val RIDE_START_REGEX = Regex("\\bpedido de (?:viagem|corrida)\\b")
    private val ACTION_CUE_REGEX = Regex(
        "\\b(?:aceitar|recusar|rejeitar|ofereca sua tarifa|ofereça sua tarifa|confirmar corrida|buscar passageiro)\\b",
    )
    private val DESTINATION_CUE_REGEX = Regex(
        "\\b(?:destino|destination|chegada|desembarque|drop off|dropoff|entrega|deixar em|ir para|indo para|levar para)\\b",
    )
    private val ORIGIN_CUE_REGEX = Regex(
        "\\b(?:origem|origin|embarque|pickup|pick up|buscar|retirada|coleta|partida|saida|saída|pegar em)\\b",
    )
    private val FARE_REGEX = Regex("(?iu)(?:R\\$\\s*\\d|\\d+(?:[.,]\\d+)?\\s*/\\s*km)")
    private val ETA_REGEX = Regex("(?iu)\\b\\d{1,3}\\s*(?:min|minutos?|km|m)\\b")
}
