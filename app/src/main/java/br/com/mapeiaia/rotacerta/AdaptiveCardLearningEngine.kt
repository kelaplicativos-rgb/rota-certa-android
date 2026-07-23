package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object AdaptiveCardLearningEngine {
    private val moneyRegex = Regex("""R\$\s*\d""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""\b\d+(?:[,.]\d+)?\s*km\b""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""\b\d{1,3}\s*(?:seg|min|minuto|minutos)\b""", RegexOption.IGNORE_CASE)
    private val ratingRegex = Regex("""^\d(?:[,.]\d{1,2})$""")
    private val addressRegex = Regex(
        """(?:\b(?:rua|avenida|rodovia|estrada|travessa|alameda|praca|praça|bairro|jardim|cidade|parque|terminal|estacao|estação|shopping|hospital|mercado)\b|\b(?:r|av)\.)""",
        RegexOption.IGNORE_CASE,
    )
    private val addressNumberRegex = Regex("""\b\d{1,5}\b.*(?:,|-|\(|\bsp\b|sao paulo|são paulo)""", RegexOption.IGNORE_CASE)
    private val mapMarkerRegex = Regex("""^(?:a|b)\b""", RegexOption.IGNORE_CASE)

    private val actionPhrases = listOf(
        "aceitar",
        "aceitar por",
        "selecionar",
        "ofereca sua tarifa",
        "ofereça sua tarifa",
        "reclamar",
        "ocultar",
        "escolher no mapa",
    )
    private val paymentPhrases = listOf("pix", "dinheiro", "cartao", "cartão")
    private val appPhrases = listOf(
        "pedido de viagem",
        "pedidos de viagem",
        "preco justo",
        "preço justo",
        "perfil premium",
        "perfil essencial",
        "negocia",
        "uberx",
        "exclusivo",
        "viagem longa",
        "radar de viagens",
        "pop expresso",
    )

    fun adaptiveFeaturesFor(text: String): Set<String> {
        val lines = normalizedLines(text)
        if (lines.isEmpty()) return emptySet()

        val roles = lines.map(::lineRole)
        val features = linkedSetOf<String>()
        val roleCounts = roles.groupingBy { it }.eachCount()

        roleCounts.forEach { (role, count) ->
            features += "adaptive.role.$role"
            features += "adaptive.role.$role.count.${count.bucketedCount()}"
        }

        compactRoleSequence(roles).takeIf { it.isNotBlank() }?.let { sequence ->
            features += "adaptive.sequence.$sequence"
            sequence.windowedRoles(2).forEach { features += "adaptive.pair.$it" }
            sequence.windowedRoles(3).forEach { features += "adaptive.triple.$it" }
        }

        val joined = lines.joinToString(" ")
        appPhrases.forEach { phrase ->
            val normalizedPhrase = phrase.normalizedForAdaptive()
            if (joined.contains(normalizedPhrase)) features += "adaptive.phrase.$normalizedPhrase"
        }
        actionPhrases.forEach { phrase ->
            val normalizedPhrase = phrase.normalizedForAdaptive()
            if (joined.contains(normalizedPhrase)) features += "adaptive.action.$normalizedPhrase"
        }
        paymentPhrases.forEach { phrase ->
            val normalizedPhrase = phrase.normalizedForAdaptive()
            if (joined.contains(normalizedPhrase)) features += "adaptive.payment.$normalizedPhrase"
        }

        val moneyIndex = roles.indexOf("money")
        val firstAddressIndex = roles.indexOf("address")
        val lastAddressIndex = roles.indexOfLast { it == "address" }
        if (moneyIndex >= 0 && firstAddressIndex >= 0) {
            features += if (moneyIndex < firstAddressIndex) "adaptive.order.money_before_address" else "adaptive.order.address_before_money"
        }
        if (firstAddressIndex >= 0 && lastAddressIndex > firstAddressIndex) {
            features += "adaptive.route.two_addresses"
        }
        if (roles.contains("money") && roles.contains("distance") && roles.contains("address")) {
            features += "adaptive.structure.fare_distance_address"
        }
        if (roles.contains("map_marker") && roles.contains("address")) {
            features += "adaptive.structure.map_marked_addresses"
        }
        if (roles.contains("action") && roles.contains("money")) {
            features += "adaptive.structure.action_with_fare"
        }

        return features
    }

    fun bestMatch(
        text: String,
        normalizedPackage: String?,
        candidates: List<RideCardTemplateMatch>,
        currentFeatures: Set<String>,
    ): RideCardTemplateMatch? {
        if (candidates.isEmpty()) return null
        val textAdaptiveFeatures = currentFeatures.filterTo(linkedSetOf()) { it.startsWith("adaptive.") }
            .ifEmpty { adaptiveFeaturesFor(text) }
        if (textAdaptiveFeatures.size < MIN_ADAPTIVE_FEATURES) return null

        return candidates
            .asSequence()
            .filter { match -> match.template.packageName?.equals(normalizedPackage, ignoreCase = true) == true }
            .mapNotNull { match ->
                val requiredAdaptive = match.template.requiredFeatures.filterTo(linkedSetOf()) { it.startsWith("adaptive.") }
                if (requiredAdaptive.size < MIN_ADAPTIVE_FEATURES) return@mapNotNull null
                val matchedAdaptive = requiredAdaptive.intersect(textAdaptiveFeatures)
                val semanticRequired = requiredAdaptive.filter { it.startsWith("adaptive.phrase.") || it.startsWith("adaptive.action.") || it.startsWith("adaptive.structure.") }
                val semanticMatched = semanticRequired.count { it in matchedAdaptive }
                val adaptiveScore = weightedScore(matchedAdaptive, requiredAdaptive)
                if (adaptiveScore < MIN_ADAPTIVE_SCORE) return@mapNotNull null
                if (semanticRequired.isNotEmpty() && semanticMatched == 0) return@mapNotNull null
                match.copy(
                    score = max(match.score, adaptiveScore),
                    matchedFeatures = (match.matchedFeatures + matchedAdaptive + ADAPTIVE_MATCH_MARKER).distinct().sorted(),
                )
            }
            .maxWithOrNull(compareBy<RideCardTemplateMatch> { it.score }.thenBy { it.matchedFeatures.size })
    }

    private fun weightedScore(matched: Set<String>, required: Set<String>): Double {
        if (required.isEmpty()) return 0.0
        val matchedWeight = matched.sumOf(::featureWeight)
        val requiredWeight = required.sumOf(::featureWeight).coerceAtLeast(1.0)
        return min(1.0, matchedWeight / requiredWeight)
    }

    private fun featureWeight(feature: String): Double = when {
        feature.startsWith("adaptive.structure.") -> 2.0
        feature.startsWith("adaptive.phrase.") -> 1.8
        feature.startsWith("adaptive.action.") -> 1.6
        feature.startsWith("adaptive.sequence.") -> 1.4
        feature.startsWith("adaptive.triple.") -> 1.2
        else -> 1.0
    }

    private fun normalizedLines(text: String): List<String> = text
        .lines()
        .map { it.normalizedForAdaptive() }
        .filter { it.length >= 2 }
        .distinct()

    private fun lineRole(line: String): String = when {
        moneyRegex.containsMatchIn(line) -> "money"
        distanceRegex.containsMatchIn(line) -> "distance"
        timeRegex.containsMatchIn(line) -> "time"
        mapMarkerRegex.containsMatchIn(line) -> "map_marker"
        actionPhrases.any { line.contains(it.normalizedForAdaptive()) } -> "action"
        paymentPhrases.any { line == it.normalizedForAdaptive() || line.contains(it.normalizedForAdaptive()) } -> "payment"
        addressRegex.containsMatchIn(line) || addressNumberRegex.containsMatchIn(line) -> "address"
        ratingRegex.matches(line) -> "rating"
        line.any { it.isDigit() } -> "numeric"
        else -> "text"
    }

    private fun compactRoleSequence(roles: List<String>): String = roles
        .fold(mutableListOf<String>()) { acc, role ->
            if (acc.lastOrNull() != role) acc += role
            acc
        }
        .take(10)
        .joinToString("-")

    private fun String.windowedRoles(size: Int): List<String> {
        val parts = split('-').filter { it.isNotBlank() }
        if (parts.size < size) return emptyList()
        return parts.windowed(size).map { it.joinToString("-") }
    }

    private fun Int.bucketedCount(): String = when {
        this <= 1 -> "1"
        this == 2 -> "2"
        this <= 4 -> "3_4"
        else -> "5_plus"
    }

    private fun String.normalizedForAdaptive(): String =
        Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()

    private const val MIN_ADAPTIVE_FEATURES = 5
    private const val MIN_ADAPTIVE_SCORE = 0.72
    private const val ADAPTIVE_MATCH_MARKER = "adaptive.card_template_match"
}
