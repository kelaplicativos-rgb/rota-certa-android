package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Aplica a regra de endereco completo somente aos extremos isolados pelo parser
 * do card atual. Assim uma lista com varias corridas nao mistura o embarque de
 * uma oferta com o destino de outra.
 */
data class RegisteredCardAddressDecision(
    val addresses: List<String>,
    val active: Boolean,
    val pickup: String?,
    val destination: String?,
    val addressSignature: String,
    val screenHash: Int,
)

object RegisteredCardAddressGate {
    fun evaluate(fields: RideFields): RegisteredCardAddressDecision {
        val addresses = listOfNotNull(fields.pickup, fields.destination)
            .map(String::trim)
            .filter(String::isNotBlank)
            .filter(UniversalScreenAddressParser::isCompleteNumberedAddress)
            .distinctBy(::canonical)
        val active = addresses.size >= UniversalAddressTrigger.MINIMUM_COMPLETE_NUMBERED_ADDRESSES
        val signature = if (active) addresses.joinToString("\u001F", transform = ::canonical) else ""
        return RegisteredCardAddressDecision(
            addresses = addresses,
            active = active,
            pickup = addresses.firstOrNull()?.takeIf { active },
            destination = addresses.lastOrNull()?.takeIf { active },
            addressSignature = signature,
            screenHash = signature.hashCode(),
        )
    }

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
