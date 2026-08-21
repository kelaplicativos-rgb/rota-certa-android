package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Contrato unico que decide quando a bolinha pode agir.
 *
 * - enderecos com logradouro reconhecivel entram na contagem, com ou sem numero;
 * - zero ou um endereco reconhecido: cinza, sem geocodificacao e sem dado antigo;
 * - dois ou mais enderecos reconhecidos: amarela e inicia a analise;
 * - o ultimo endereco reconhecido sempre e o destino;
 * - qualquer alteracao no texto visivel muda o hash e invalida o resultado anterior.
 */
data class UniversalAddressTriggerDecision(
    val addresses: List<String>,
    val active: Boolean,
    val pickup: String?,
    val destination: String?,
    val addressSignature: String,
    val screenHash: Int,
) {
    val shouldClearPreviousResult: Boolean
        get() = !active
}

object UniversalAddressTrigger {
    const val MINIMUM_VISIBLE_ADDRESSES = 2
    const val MINIMUM_RECOGNIZED_ADDRESSES = MINIMUM_VISIBLE_ADDRESSES
    const val MINIMUM_COMPLETE_NUMBERED_ADDRESSES = MINIMUM_VISIBLE_ADDRESSES // compatibilidade legada

    fun evaluate(text: String): UniversalAddressTriggerDecision {
        val addressText = WrappedAddressTextNormalizer.normalize(text)
        val addresses = UniversalScreenAddressParser.findAddresses(addressText)
        val active = addresses.size >= MINIMUM_RECOGNIZED_ADDRESSES
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
