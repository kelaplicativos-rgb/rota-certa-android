package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Contrato unico que decide quando a bolinha pode agir.
 *
 * - zero ou um endereco: inativa e sem dado antigo;
 * - dois ou mais enderecos distintos: ativa imediatamente;
 * - o ultimo endereco visivel sempre e o destino;
 * - qualquer alteracao no texto visivel muda o hash da tela e invalida o resultado anterior.
 */
data class UniversalAddressTriggerDecision(
    val addresses: List<String>,
    val active: Boolean,
    val pickup: String?,
    val destination: String?,
    val addressSignature: String,
    val screenHash: Int,
)

object UniversalAddressTrigger {
    const val MINIMUM_VISIBLE_ADDRESSES = 2

    fun evaluate(text: String): UniversalAddressTriggerDecision {
        val addresses = UniversalScreenAddressParser.findAddresses(text)
        val active = addresses.size >= MINIMUM_VISIBLE_ADDRESSES
        return UniversalAddressTriggerDecision(
            addresses = addresses,
            active = active,
            pickup = addresses.firstOrNull()?.takeIf { active },
            destination = addresses.lastOrNull()?.takeIf { active },
            addressSignature = if (active) addresses.joinToString("\u001F", transform = ::canonical) else "",
            screenHash = normalizeScreen(text).hashCode(),
        )
    }

    private fun normalizeScreen(value: String): String = value
        .replace('\u00A0', ' ')
        .replace('\u202F', ' ')
        .lines()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString("\n")

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
