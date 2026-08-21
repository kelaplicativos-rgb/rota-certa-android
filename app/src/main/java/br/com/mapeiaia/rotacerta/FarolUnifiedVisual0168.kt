package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Núcleo visual puro da 0.1.168.
 *
 * Ele não autoriza verde/vermelho sozinho. Sua função é entregar ao parser apenas
 * um bloco coerente de card, estabilizar assinaturas visuais e impedir que um
 * fragmento de rua seja enviado ao serviço de rotas.
 */
object FarolUnifiedVisual0168 {
    const val CONTRACT_MARKER: String = "farol_unified_visual_0_1_168"

    private val whitespace = Regex("\\s+")
    private val volatileCountdown = Regex("(?i)\\b\\d{1,3}\\s*(?:seg(?:undos?)?|s|min(?:utos?)?|h)\\b")
    private val volatileMoney = Regex("(?i)R\\$\\s*\\d+(?:[.,]\\d{1,2})?")
    private val volatileDistance = Regex("(?i)~?\\s*\\d+(?:[.,]\\d+)?\\s*km\\b")
    private val resourceId = Regex("(?i)\\b[A-Za-z0-9_.]+:id/[A-Za-z0-9_\\-]+\\b")
    private val streetStart = Regex(
        "(?i)^(?:rua|r\\.?|avenida|av\\.?|alameda|travessa|estrada|rodovia|praça|praca|largo|viela|marginal)\\b",
    )
    private val cardStart = Regex(
        "(?i)^(?:pedido de viagem|nova (?:solicitação|solicitacao)|solicitação de viagem|solicitacao de viagem|corrida disponível|corrida disponivel|oferta de corrida)\\b",
    )
    private val cardAction = Regex(
        "(?i)\\b(?:aceitar(?: por)?|recusar|pular|ofereça sua tarifa|ofereca sua tarifa|confirmar corrida|iniciar viagem)\\b",
    )
    private val rideSignal = Regex(
        "(?i)\\b(?:pedido de viagem|corrida|viagem|embarque|destino|passageiro|uberx|99|preço justo|preco justo)\\b",
    )
    private val locationSignal = Regex(
        "(?i)\\b(?:cidade|jardim|vila|centro|bairro|parque|residencial|industrial|são paulo|sao paulo|santo andré|santo andre|sp|mg|rj|brasil)\\b",
    )
    private val namedPlaceSignal = Regex(
        "(?i)\\b(?:hotel|shopping|hospital|aeroporto|terminal|estação|estacao|condomínio|condominio|mercado|atacadista|restaurante|escola|faculdade|igreja|casa|empresa|posto)\\b",
    )

    fun fromVisionText(result: com.google.mlkit.vision.text.Text): String {
        val blocks = result.textBlocks.sortedWith(
            compareBy(
                { it.boundingBox?.top ?: Int.MAX_VALUE },
                { it.boundingBox?.left ?: Int.MAX_VALUE },
            ),
        )
        if (blocks.isEmpty()) return result.text
        return blocks.joinToString("\n\n") { block ->
            block.lines
                .sortedWith(
                    compareBy(
                        { it.boundingBox?.top ?: Int.MAX_VALUE },
                        { it.boundingBox?.left ?: Int.MAX_VALUE },
                    ),
                )
                .joinToString("\n") { it.text.trim() }
        }
    }

    fun normalizeForAnalysis(raw: String): String {
        if (raw.isBlank()) return ""
        val cleaned = raw
            .replace('\u00A0', ' ')
            .replace(resourceId, " ")
            .replace(Regex("(?i)[ \\t]+(Pedido de viagem|Nova solicitação|Nova solicitacao|Solicitação de viagem|Solicitacao de viagem|Corrida disponível|Corrida disponivel)[ \\t]+"), "\n$1 ")
            .replace(Regex("(?i)[ \\t]+(Aceitar por|Aceitar|Pular|Recusar|Ofereça sua tarifa|Ofereca sua tarifa)[ \\t]+"), "\n$1 ")
            .lines()
            .joinToString("\n") { whitespace.replace(it.trim(), " ") }
            .trim()
        if (cleaned.isBlank()) return ""

        val cards = splitCards(cleaned)
        val selected = if (cards.size <= 1) cleaned else cards.maxWithOrNull(
            compareBy<String> { scoreCard(it) }.thenByDescending { -it.length },
        ) ?: cleaned

        return selected
            .lines()
            .filterNot { line -> isClearlyTruncatedStreet(line.trim()) }
            .joinToString("\n")
            .trim()
    }

    fun semanticHash(raw: String): Int = semanticSignature(raw).hashCode()

    fun semanticSignature(raw: String): String = fold(
        normalizeForAnalysis(raw)
            .replace(volatileCountdown, " ")
            .replace(volatileMoney, " ")
            .replace(volatileDistance, " ")
            .replace(whitespace, " ")
            .trim(),
    )

    fun isClearlyTruncatedStreet(value: String): Boolean {
        val text = whitespace.replace(value.trim(), " ")
        if (!streetStart.containsMatchIn(text)) return false
        if (Regex("\\d").containsMatchIn(text)) return false
        val commaCount = text.count { it == ',' }
        val locationCount = locationSignal.findAll(text).count()
        val hasStateSuffix = Regex("(?i)(?:-|,)\\s*[A-Z]{2}\\b").containsMatchIn(text)
        return commaCount < 2 && locationCount < 2 && !hasStateSuffix
    }

    fun isNamedPlaceWithLocation(value: String): Boolean {
        val text = whitespace.replace(value.trim(), " ")
        if (text.length !in 8..220 || streetStart.containsMatchIn(text)) return false
        val opening = text.indexOf('(')
        val closing = text.lastIndexOf(')')
        val parentheticalLocation = opening > 1 && closing > opening + 3 &&
            locationSignal.containsMatchIn(text.substring(opening + 1, closing))
        val namedWithLocation = namedPlaceSignal.containsMatchIn(text) && locationSignal.containsMatchIn(text)
        val wordCount = Regex("[\\p{L}]{2,}").findAll(text).count()
        return wordCount >= 3 && (parentheticalLocation || namedWithLocation)
    }

    private fun splitCards(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = mutableListOf<String>()
        text.lines().forEach { line ->
            if (cardStart.containsMatchIn(line.trim()) && current.isNotEmpty()) {
                result += current.joinToString("\n").trim()
                current.clear()
            }
            current += line
        }
        if (current.isNotEmpty()) result += current.joinToString("\n").trim()
        return result.filter { it.isNotBlank() }
    }

    private fun scoreCard(card: String): Int {
        var score = 0
        if (cardAction.containsMatchIn(card)) score += 5
        if (rideSignal.containsMatchIn(card)) score += 4
        score += Regex("(?i)\\b(?:rua|avenida|av\\.?|alameda|travessa|estrada|rodovia)\\b").findAll(card).count() * 3
        score += Regex("\\d{1,5}").findAll(card).count().coerceAtMost(4)
        score += locationSignal.findAll(card).count().coerceAtMost(4)
        if (card.length > 2_000) score -= 5
        return score
    }

    private fun fold(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
}
