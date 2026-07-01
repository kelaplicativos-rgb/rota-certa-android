package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

object RideScreenTextClassifier {
    private val moneyRegex = Regex("""R\$\s*\d""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""\b\d+(?:[,.]\d+)?\s*km\b""", RegexOption.IGNORE_CASE)
    private val routeStepRegex = Regex(
        """\b\d{1,3}\s*(?:min|minuto|minutos)\.?\s*(?:\(\s*(?:\d+(?:[,.]\d+)?\s*)?(?:m|km)\s*\))?""",
        RegexOption.IGNORE_CASE,
    )
    private val addressRegex = Regex(
        """\b(?:rua|r\.|avenida|av\.|rodovia|estrada|travessa|alameda|praca|bairro)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val rideKeywords = listOf(
        "aceitar",
        "corrida",
        "corridas",
        "pedido de viagem",
        "pedidos de viagem",
        "viagem longa",
        "uber",
        "uberx",
        "dinheiro",
        "pix",
        "parada(s)",
        "ofereca sua tarifa",
        "negocia",
        "exclusivo",
        "preco justo",
    )
    private val viewerShellKeywords = listOf(
        "files by google",
        "editar",
        "pesquisar na imagem",
        "navegar para cima",
        "compartilhar",
        "adicionar a pasta",
        "com estrela",
        "mais opcoes",
        "google fotos",
        "galeria",
    )

    fun shouldIgnore(text: String): Boolean {
        val normalized = text.normalizedForMatch()
        val viewerHits = viewerShellKeywords.count { normalized.contains(it) }
        return viewerHits >= 2 && !hasRideEvidence(text)
    }

    fun looksLikeRideCard(text: String): Boolean {
        if (shouldIgnore(text)) return false
        return hasRideEvidence(text)
    }

    private fun hasRideEvidence(text: String): Boolean {
        val normalized = text.normalizedForMatch()
        val hasMoney = moneyRegex.containsMatchIn(text)
        val hasRideKeyword = rideKeywords.any { normalized.contains(it) }
        val hasRouteSignal = routeStepRegex.containsMatchIn(text) || distanceRegex.containsMatchIn(text)
        val hasAddressSignal = addressRegex.containsMatchIn(normalized) ||
            Regex("""(?m)^\s*[ab]\s+.+""", RegexOption.IGNORE_CASE).containsMatchIn(text)

        return hasMoney && (
            hasRideKeyword && (hasRouteSignal || hasAddressSignal) ||
                hasRouteSignal && hasAddressSignal
            )
    }

    private fun String.normalizedForMatch(): String =
        Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
