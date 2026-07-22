package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

data class PrimaryVisibleRideCardSelection(
    val selectedText: String,
    val cardCount: Int,
    val selectedIndex: Int,
    val passengerName: String?,
    val reason: String,
)

/**
 * Isola o primeiro pedido completo visivel quando a acessibilidade entrega uma
 * lista inteira de corridas como um unico texto.
 *
 * A selecao e conservadora: so recorta quando existem pelo menos dois blocos
 * inequivocos de passageiro (nome seguido imediatamente de nota/avaliacoes).
 * Dentro da lista, escolhe o primeiro bloco que contem pelo menos dois enderecos.
 * Se nao houver lista comprovada, preserva o texto original para nao alterar os
 * layouts de Uber, 99 ou cards individuais.
 */
object PrimaryVisibleRideCardSelector {
    private val ratingRegex = Regex("^[0-5](?:[.,]\\d{1,2})?$")
    private val reviewCountRegex = Regex("^\\(?\\d{1,6}\\)?(?:\\s+avalia(?:c|ç)(?:ao|ão|oes|ões))?$")
    private val nameTokenRegex = Regex("^[\\p{L}][\\p{L}'’-]{0,24}$")
    private val forbiddenNameRegex = Regex(
        "(?:\\d|r\\$|km|min|rua|avenida|av\\.|travessa|estrada|rodovia|alameda|pra[cç]a|bairro|jardim|aceitar|ofere[cç]a|tarifa|pix|dinheiro|fechar|pedido|viagem|corrida|mapa|destino|embarque|perfil|premium|comfort|uberx|99pop|notifica[cç][aã]o|demanda|desempenho)",
        RegexOption.IGNORE_CASE,
    )

    fun select(text: String): PrimaryVisibleRideCardSelection {
        val original = text.trim()
        if (original.isBlank()) return unchanged(original, "texto_vazio")

        val lines = original
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .lines()
            .map { line -> Normalizer.normalize(line, Normalizer.Form.NFC).trim() }
            .filter(String::isNotBlank)

        val anchors = lines.indices.filter { index -> isPassengerAnchor(lines, index) }
        if (anchors.size < 2) return unchanged(original, "card_individual_ou_layout_sem_lista")

        anchors.forEachIndexed { cardIndex, startIndex ->
            val endIndex = anchors.getOrNull(cardIndex + 1) ?: lines.size
            val blockLines = lines.subList(startIndex, endIndex)
            val blockText = blockLines.joinToString("\n").trim()
            val trigger = UniversalAddressTrigger.evaluate(blockText)
            if (trigger.addresses.size >= 2 && !trigger.destination.isNullOrBlank()) {
                return PrimaryVisibleRideCardSelection(
                    selectedText = blockText,
                    cardCount = anchors.size,
                    selectedIndex = cardIndex,
                    passengerName = lines[startIndex],
                    reason = "primeiro_card_completo_visivel",
                )
            }
        }

        return PrimaryVisibleRideCardSelection(
            selectedText = original,
            cardCount = anchors.size,
            selectedIndex = -1,
            passengerName = null,
            reason = "lista_detectada_sem_card_completo",
        )
    }

    private fun isPassengerAnchor(lines: List<String>, index: Int): Boolean {
        val name = lines.getOrNull(index) ?: return false
        if (!looksLikeHumanName(name)) return false

        val immediate = lines.getOrNull(index + 1).orEmpty()
        val hasImmediateRating = ratingRegex.matches(immediate.replace(',', '.'))
        val hasImmediateReviewCount = reviewCountRegex.matches(immediate)
        if (!hasImmediateRating && !hasImmediateReviewCount) return false

        if (hasImmediateRating) {
            val reviewContext = lines.drop(index + 2).take(2)
            if (reviewContext.any(reviewCountRegex::matches)) return true
        }
        return hasImmediateReviewCount || hasImmediateRating
    }

    private fun looksLikeHumanName(value: String): Boolean {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
            .trim()
            .replace(Regex("\\s+"), " ")
        if (normalized.length !in 2..52) return false
        if (forbiddenNameRegex.containsMatchIn(normalized)) return false
        val words = normalized.split(' ')
        if (words.size !in 1..4) return false
        return words.all(nameTokenRegex::matches) && words.any { it.length >= 2 }
    }

    private fun unchanged(text: String, reason: String) = PrimaryVisibleRideCardSelection(
        selectedText = text,
        cardCount = if (text.isBlank()) 0 else 1,
        selectedIndex = 0,
        passengerName = null,
        reason = reason,
    )
}
