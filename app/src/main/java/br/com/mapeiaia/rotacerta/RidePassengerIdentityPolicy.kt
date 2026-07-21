package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Distingue um card individual de uma tela com varias ofertas.
 *
 * Uma identidade valida e uma linha curta com nome humano cujo proximo dado
 * visivel e imediatamente uma nota ou quantidade de avaliacoes. Esse requisito
 * impede que titulos como "Nova notificacao" sejam confundidos com passageiro.
 */
data class RidePassengerIdentityDecision(
    val accepted: Boolean,
    val candidates: List<String>,
    val reason: String,
)

object RidePassengerIdentityPolicy {
    private val ratingRegex = Regex("^[0-5](?:[.,]\\d{1,2})?$")
    private val reviewCountRegex = Regex("^\\(?\\d{1,6}\\)?(?:\\s+avalia(?:c|ç)(?:ao|ão|oes|ões))?$")
    private val nameTokenRegex = Regex("^[\\p{L}][\\p{L}'’-]{0,24}$")
    private val forbiddenContentRegex = Regex(
        "(?:\\d|r\\$|km|min|rua|avenida|av\\.|travessa|estrada|rodovia|alameda|pra[cç]a|bairro|jardim|aceitar|ofere[cç]a|tarifa|pix|dinheiro|fechar|pedido|viagem|corrida|mapa|destino|embarque|perfil|premium|comfort|uberx|99pop)",
        RegexOption.IGNORE_CASE,
    )
    private val uiOnlyLines = setOf(
        "aceitar",
        "aceitar por",
        "ofereca sua tarifa",
        "ofereça sua tarifa",
        "fechar",
        "pix",
        "dinheiro",
        "pedido de viagem",
        "pedidos de viagem",
        "nova corrida",
        "nova notificação",
        "nova notificacao",
        "mostrar novos pedidos",
        "corrida",
        "passageiro",
        "demanda",
        "desempenho",
    )

    fun evaluate(text: String): RidePassengerIdentityDecision {
        val lines = text
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .lines()
            .map(String::trim)
            .filter(String::isNotBlank)

        val candidates = buildList {
            lines.forEachIndexed { index, line ->
                if (!looksLikeHumanName(line)) return@forEachIndexed
                val immediate = lines.getOrNull(index + 1).orEmpty()
                val hasIdentityContext =
                    ratingRegex.matches(immediate.replace(',', '.')) || reviewCountRegex.matches(immediate)
                if (hasIdentityContext) add(line)
            }
        }.distinctBy(::canonical)

        return when (candidates.size) {
            1 -> RidePassengerIdentityDecision(
                accepted = true,
                candidates = candidates,
                reason = "um_passageiro_identificado",
            )
            0 -> RidePassengerIdentityDecision(
                accepted = false,
                candidates = emptyList(),
                reason = "passageiro_nao_identificado",
            )
            else -> RidePassengerIdentityDecision(
                accepted = false,
                candidates = candidates,
                reason = "varios_passageiros_lista_de_pedidos",
            )
        }
    }

    private fun looksLikeHumanName(value: String): Boolean {
        val normalized = value.trim().replace(Regex("\\s+"), " ")
        val canonical = canonical(normalized)
        if (canonical in uiOnlyLines.map(::canonical)) return false
        if (normalized.length !in MIN_NAME_LENGTH..MAX_NAME_LENGTH) return false
        if (forbiddenContentRegex.containsMatchIn(normalized)) return false

        val words = normalized.split(' ')
        if (words.size !in MIN_NAME_WORDS..MAX_NAME_WORDS) return false
        if (words.any { word -> !nameTokenRegex.matches(word) }) return false

        return words.any { word -> word.length >= 2 }
    }

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private const val MIN_NAME_WORDS = 1
    private const val MAX_NAME_WORDS = 4
    private const val MIN_NAME_LENGTH = 2
    private const val MAX_NAME_LENGTH = 52
}
