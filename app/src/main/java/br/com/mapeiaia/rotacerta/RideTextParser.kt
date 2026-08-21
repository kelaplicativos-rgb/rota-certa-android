package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

class RideTextParser {
    private val fareRegex = Regex("""R\$\s*\d{1,4}(?:[.,]\d{1,2})?""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""\b\d+(?:[,.]\d+)?\s*km\b""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""\b\d{1,3}\s*(?:minutos?|min)\b""", RegexOption.IGNORE_CASE)
    private val markerAddress = Regex("""^\s*[AB]\s+(.{5,})$""", RegexOption.IGNORE_CASE)
    private val streetStart = Regex("""^(?:rua|r\.|avenida|av\.|travessa|alameda|estrada|rodovia|praça|praca|largo|via)\b""", RegexOption.IGNORE_CASE)
    private val addressWords = setOf(
        "rua", "avenida", "travessa", "alameda", "estrada", "rodovia", "praça", "praca", "bairro",
        "condomínio", "condominio", "shopping", "terminal", "estação", "estacao", "hospital", "mercado",
        "restaurante", "lanchonete", "escola", "aeroporto", "rodoviária", "rodoviaria", "hotel", "parque",
    )
    private val noise = setOf(
        "aceitar", "cancelar", "voltar", "selecionar", "configurações", "configuracoes", "permissões", "permissoes",
        "pix", "dinheiro", "reclamar", "ocultar", "copiar", "compartilhar",
    )

    fun parse(text: String, packageName: String? = null): RideFields = parseWithMetadata(text, packageName).fields

    fun parseWithMetadata(text: String, packageName: String? = null): RideParseResult {
        @Suppress("UNUSED_VARIABLE") val ignoredPackage = packageName
        val lines = text.lines()
            .map { it.replace('\u00A0', ' ').replace('\u202F', ' ').trim().replace(Regex("""\s+"""), " ") }
            .filter { it.length >= 3 }
            .distinctBy(::canonical)
        val addresses = extractAddresses(lines)
        val pickup = addresses.firstOrNull()
        val destination = addresses.drop(1).lastOrNull { !it.equals(pickup, ignoreCase = true) }
        val scoped = lines.joinToString("\n")
        return RideParseResult(
            fields = RideFields(
                pickup = pickup,
                destination = destination,
                fare = fareRegex.find(scoped)?.value,
                distance = distanceRegex.find(scoped)?.value,
                time = timeRegex.find(scoped)?.value,
            ),
            parserName = "manual-universal-last-address",
        )
    }

    private fun extractAddresses(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val marker = markerAddress.matchEntire(line)?.groupValues?.getOrNull(1)?.trim()
            val candidate = marker ?: line
            if (looksLikeAddress(candidate)) {
                val parts = mutableListOf(clean(candidate))
                var next = index + 1
                while (next < lines.size && parts.size < 3 && !looksLikeAddress(lines[next]) && looksLikeContinuation(lines[next])) {
                    parts += clean(lines[next])
                    next += 1
                }
                val joined = parts.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
                if (joined.length >= 6 && result.none { canonical(it) == canonical(joined) }) result += joined
                index = next
            } else index += 1
        }
        return result
    }

    private fun looksLikeAddress(value: String): Boolean {
        if (FarolUnifiedVisual0168.isClearlyTruncatedStreet(value)) return false // farol_unified_visual_0_1_168
        if (FarolUnifiedVisual0168.isNamedPlaceWithLocation(value)) return true

        val normalized = canonicalWords(value)
        if (value.length < 6 || normalized in noise) return false
        if (fareRegex.matches(value) || distanceRegex.matches(value) || timeRegex.matches(value)) return false
        val hasStreet = streetStart.containsMatchIn(value)
        val hasAddressWord = addressWords.any { normalized.contains(it) }
        val hasNumber = Regex("""\b\d{1,6}[A-Za-z]?\b""").containsMatchIn(value)
        val hasLocality = value.contains(',') || Regex("""\b[A-Z]{2}\b""").containsMatchIn(value) || value.contains(" - ")
        return hasStreet || (hasAddressWord && (hasNumber || hasLocality)) || (hasNumber && hasLocality)
    }

    private fun looksLikeContinuation(value: String): Boolean {
        val normalized = canonicalWords(value)
        if (value.length < 3 || normalized in noise) return false
        if (fareRegex.containsMatchIn(value) || distanceRegex.containsMatchIn(value) || timeRegex.containsMatchIn(value)) return false
        return value.contains(',') || value.contains(" - ") || Regex("""\b[A-Z]{2}\b""").containsMatchIn(value) ||
            listOf("bairro", "jardim", "centro", "cidade", "state of", "district").any { normalized.contains(it) }
    }

    private fun clean(value: String): String = value
        .replace(Regex("""^\s*[AB]\s+""", RegexOption.IGNORE_CASE), "")
        .trim(' ', '-', '•', '|')

    private fun canonical(value: String): String = canonicalWords(value).replace(Regex("""[^\p{L}\p{N}]+"""), "")
    private fun canonicalWords(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
}

data class RideParseResult(val fields: RideFields, val parserName: String)
