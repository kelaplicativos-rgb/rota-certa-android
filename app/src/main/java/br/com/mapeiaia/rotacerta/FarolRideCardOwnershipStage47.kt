package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Stage47 — ownership barrier between "visible location" and "ride card".
 *
 * Address text is evidence of a place, never evidence that a ride offer owns the screen.
 * A route may start only when the current selected driver app also exposes ride-card semantics.
 * This keeps Maps, chats, search fields and navigation instructions out of the Farol critical path.
 */
object FarolRideCardOwnershipStage47 {
    const val CONTRACT_MARKER = "FAROL_RIDE_CARD_OWNERSHIP_STAGE47"
    const val ADDRESS_NOT_CARD_MARKER = "VISIBLE_ADDRESS_NEVER_EQUALS_RIDE_CARD_STAGE47"
    const val SELECTED_SURFACE_MARKER = "SELECTED_DRIVER_SURFACE_REQUIRED_STAGE47"
    const val NEGATIVE_NAV_MARKER = "MAP_SEARCH_NAV_TEXT_REJECTED_STAGE47"
    const val LOCAL_FIRST_MARKER = "CARD_OWNERSHIP_BEFORE_ROUTE_STAGE47"

    data class Decision(
        val owned: Boolean,
        val reason: String,
        val semanticSignature: String = "",
    )

    fun evaluate(
        packageName: String?,
        selectedPackages: Set<String>,
        text: String,
        locationCount: Int,
        structuralSignature: String = "",
    ): Decision {
        val pkg = normalizePackage(packageName)
            ?: return Decision(false, "package_missing")
        val selected = selectedPackages.mapNotNull(::normalizePackage).toSet()
        if (pkg !in selected) return Decision(false, "foreign_or_unselected_surface")
        if (text.isBlank() || locationCount <= 0) return Decision(false, "no_location_evidence")

        val canonicalText = canonical(text)
        val actionCue = ACTION_CUES.any(canonicalText::contains)
        val strongRideCue = STRONG_RIDE_CUES.any(canonicalText::contains)
        val fareCue = FARE_REGEX.containsMatchIn(text)
        val etaCue = ETA_REGEX.containsMatchIn(canonicalText)
        val ratingCue = RATING_REGEX.containsMatchIn(text)
        val navigationNoise = NAVIGATION_NOISE.any(canonicalText::contains)

        if (navigationNoise && !actionCue && !strongRideCue) {
            return Decision(false, "navigation_or_search_surface")
        }

        val owned = when {
            actionCue && locationCount >= 1 -> true
            strongRideCue && locationCount >= 1 && (fareCue || etaCue || ratingCue) -> true
            locationCount >= 2 && fareCue && etaCue -> true
            else -> false
        }
        if (!owned) return Decision(false, "location_without_ride_card_semantics")

        val stableText = buildList {
            if (actionCue) add("action")
            if (strongRideCue) add("ride")
            if (fareCue) add("fare")
            if (etaCue) add("eta")
            if (ratingCue) add("rating")
            add("loc=$locationCount")
            if (structuralSignature.isNotBlank()) add("struct=${canonical(structuralSignature).take(180)}")
        }.joinToString("|")
        return Decision(true, "ride_card_owned", "$pkg|$stableText")
    }

    private fun normalizePackage(value: String?): String? =
        value?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val ACTION_CUES = listOf(
        "aceitar", "aceitar corrida", "aceitar viagem", "recusar", "rejeitar",
        "ofereca sua tarifa", "ofereça sua tarifa", "confirmar corrida", "buscar passageiro",
    ).map(::canonical)

    private val STRONG_RIDE_CUES = listOf(
        "pedido de viagem", "pedido de corrida", "oferta recebida", "nova corrida",
        "nova viagem", "corrida solicitada", "viagem solicitada", "passageiro",
        "perfil essencial", "perfil comfort", "perfil confort", "uberx", "uber comfort",
    ).map(::canonical)

    private val NAVIGATION_NOISE = listOf(
        "barra de pesquisa", "pesquisa por voz", "street view", "view street",
        "ver imagens", "continue por", "vire a esquerda", "vire a direita",
        "trajeto alternativo", "rotas", "iniciar navegacao", "explorar",
        "sua localizacao", "mapa", "google maps",
    ).map(::canonical)

    private val FARE_REGEX = Regex("(?iu)(?:R\\$\\s*\\d|\\d+(?:[.,]\\d+)?\\s*/\\s*km)")
    private val ETA_REGEX = Regex("(?iu)\\b\\d{1,3}\\s*(?:min|minutos?|km|m)\\b")
    private val RATING_REGEX = Regex("(?u)\\b[1-5][.,]\\d\\s*(?:★|estrelas?)?\\b")
}
