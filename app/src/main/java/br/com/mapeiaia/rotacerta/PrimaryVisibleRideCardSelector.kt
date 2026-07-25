package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

data class PrimaryVisibleRideCardSelection(
    val selectedText: String,
    val cardCount: Int,
    val selectedIndex: Int,
    val passengerName: String?,
    val reason: String,
    val cardSignature: String = "",
)

/** Isola cards completos sem misturar passageiro, embarque e destino. */
object PrimaryVisibleRideCardSelector {
    private val ratingRegex = Regex("^[0-5](?:[.,]\\d{1,2})?$")
    private val reviewCountRegex = Regex("^\\(?\\d{1,6}\\)?(?:\\s+avalia(?:c|ç)(?:ao|ão|oes|ões))?$")
    private val nameTokenRegex = Regex("^[\\p{L}][\\p{L}'’-]{0,24}$")
    private val forbiddenNameRegex = Regex(
        "(?:\\d|r\\$|km|min|rua|avenida|av\\.|travessa|estrada|rodovia|alameda|pra[cç]a|bairro|jardim|aceitar|ofere[cç]a|tarifa|pix|dinheiro|fechar|pedido|viagem|corrida|mapa|destino|embarque|perfil|premium|comfort|uberx|99pop|notifica[cç][aã]o|demanda|desempenho)",
        RegexOption.IGNORE_CASE,
    )

    fun select(text: String): PrimaryVisibleRideCardSelection =
        completeCards(text).firstOrNull() ?: unchanged(text.trim(), if (text.isBlank()) "texto_vazio" else "card_individual_ou_layout_sem_lista")

    fun completeCards(text: String): List<PrimaryVisibleRideCardSelection> {
        val original = text.trim()
        if (original.isBlank()) return emptyList()
        val lines = normalizedLines(original)
        val anchors = lines.indices.filter { index -> isPassengerAnchor(lines, index) }

        if (anchors.size < 2) {
            val trigger = UniversalAddressTrigger.evaluate(original)
            val passenger = RidePassengerIdentityPolicy.evaluate(original).candidates.singleOrNull()
            return if (trigger.addresses.size >= 2 && !trigger.destination.isNullOrBlank()) {
                listOf(
                    PrimaryVisibleRideCardSelection(
                        selectedText = original,
                        cardCount = 1,
                        selectedIndex = 0,
                        passengerName = passenger,
                        reason = "card_individual_ou_layout_sem_lista",
                        cardSignature = signature(passenger, trigger.addressSignature),
                    ),
                )
            } else {
                emptyList()
            }
        }

        val complete = mutableListOf<PrimaryVisibleRideCardSelection>()
        anchors.forEachIndexed { cardIndex, startIndex ->
            val endIndex = anchors.getOrNull(cardIndex + 1) ?: lines.size
            val blockText = lines.subList(startIndex, endIndex).joinToString("\n").trim()
            val trigger = UniversalAddressTrigger.evaluate(blockText)
            if (trigger.addresses.size >= 2 && !trigger.destination.isNullOrBlank()) {
                val passenger = lines[startIndex]
                complete += PrimaryVisibleRideCardSelection(
                    selectedText = blockText,
                    cardCount = anchors.size,
                    selectedIndex = cardIndex,
                    passengerName = passenger,
                    reason = if (cardIndex == 0) "primeiro_card_completo_visivel" else "card_completo_visivel",
                    cardSignature = signature(passenger, trigger.addressSignature),
                )
            }
        }
        return complete
    }

    private fun normalizedLines(text: String): List<String> = text
        .replace('\u00A0', ' ')
        .replace('\u202F', ' ')
        .lines()
        .map { line -> Normalizer.normalize(line, Normalizer.Form.NFC).trim() }
        .filter(String::isNotBlank)

    private fun isPassengerAnchor(lines: List<String>, index: Int): Boolean {
        val name = lines.getOrNull(index) ?: return false
        if (!looksLikeHumanName(name)) return false
        val immediate = lines.getOrNull(index + 1).orEmpty()
        val normalizedRating = immediate.replace(',', '.')
        val hasImmediateRating = ratingRegex.matches(normalizedRating)
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

    private fun signature(passenger: String?, addressSignature: String): String =
        canonical(passenger.orEmpty()) + "|" + canonical(addressSignature)

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9|]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun unchanged(text: String, reason: String) = PrimaryVisibleRideCardSelection(
        selectedText = text,
        cardCount = if (text.isBlank()) 0 else 1,
        selectedIndex = 0,
        passengerName = null,
        reason = reason,
        cardSignature = "",
    )
}
