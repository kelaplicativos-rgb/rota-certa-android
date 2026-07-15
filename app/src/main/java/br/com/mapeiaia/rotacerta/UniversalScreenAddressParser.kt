package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Detecta enderecos em qualquer texto visivel, sem depender de aplicativo,
 * pacote, modelo de card, preco, botao ou layout especifico.
 *
 * A regra operacional e simples: encontrou um ou mais enderecos, o ultimo
 * endereco distinto da tela e o destino usado pelo farol.
 */
object UniversalScreenAddressParser {
    private val addressStartRegex = Regex(
        "(?:^|[\\s:;])((?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|bairro|condominio|condomínio|residencial|shopping|terminal|estacao|estação|aeroporto|rodoviaria|rodoviária|hospital|mercado|restaurante|hotel|pousada|escola|faculdade|universidade|posto|parque|poupatempo|igreja|cemiterio|cemitério)(?:\\b|(?=\\s)))",
        RegexOption.IGNORE_CASE,
    )
    private val markerPrefix = Regex(
        "^(?:[ab]|origem|partida|embarque|destino(?:\\s+final)?|chegada|desembarque)\\s*[:\\-–—]?\\s+",
        RegexOption.IGNORE_CASE,
    )
    private val cepRegex = Regex("\\b\\d{5}-?\\d{3}\\b")
    private val numberRegex = Regex("\\b\\d{1,6}[a-z]?\\b", RegexOption.IGNORE_CASE)
    private val stateRegex = Regex(
        "(?:\\-|,|\\()\\s*(?:AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)(?:\\b|\\))",
        RegexOption.IGNORE_CASE,
    )
    private val coordinateRegex = Regex("^-?\\d{1,3}[,.]\\d{4,}\\s*[,;]\\s*-?\\d{1,3}[,.]\\d{4,}$")
    private val noiseRegex = Regex(
        "(?:r\\$|\\b\\d+(?:[,.]\\d+)?\\s*(?:km|m|min|minutos?)\\b|aceitar|ofere[cç]a|tarifa|pre[cç]o|pix|dinheiro|cart[aã]o|fechar|cancelar|configura[cç][aã]o)",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): RideFields {
        val addresses = findAddresses(text)
        return RideFields(
            pickup = addresses.firstOrNull()?.takeIf { addresses.size > 1 },
            destination = addresses.lastOrNull(),
        )
    }

    fun findAddresses(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = text.lines()
            .map(::normalizeLine)
            .filter { it.length >= 4 }

        val candidates = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val current = cleanAddressSegment(lines[index])
            if (looksLikeAddress(current)) {
                val parts = mutableListOf(current)
                var nextIndex = index + 1
                while (nextIndex < lines.size && parts.size < 3) {
                    val next = cleanAddressSegment(lines[nextIndex])
                    if (!looksLikeContinuation(next, parts.last())) break
                    parts += next
                    nextIndex += 1
                }
                val joined = parts.joinToString(" ")
                    .replace(Regex("\\s+"), " ")
                    .trim(' ', ',', '-', '–', '—')
                if (joined.length >= 5) candidates += joined
                index = nextIndex
            } else {
                index += 1
            }
        }

        return candidates.distinctBy(::canonical)
    }

    private fun looksLikeAddress(value: String): Boolean {
        if (value.length < 5 || noiseRegex.containsMatchIn(value)) return false
        if (coordinateRegex.matches(value)) return true
        val hasAddressWord = addressStartRegex.containsMatchIn(value)
        val hasNumber = numberRegex.containsMatchIn(value)
        val hasCep = cepRegex.containsMatchIn(value)
        val hasState = stateRegex.containsMatchIn(value)
        val hasLocalityPunctuation = value.contains(',') || value.contains(" - ") || value.contains('(')
        val hasLetters = value.count { it.isLetter() } >= 4
        return hasLetters && (
            hasAddressWord ||
                hasCep ||
                (hasNumber && hasLocalityPunctuation) ||
                (hasNumber && hasState) ||
                (hasState && hasLocalityPunctuation)
            )
    }

    private fun looksLikeContinuation(value: String, previous: String): Boolean {
        if (value.length < 2 || noiseRegex.containsMatchIn(value)) return false
        if (looksLikeAddress(value)) return false
        val normalized = canonical(value)
        val previousOpenParenthesis = previous.count { it == '(' } > previous.count { it == ')' }
        return previous.endsWith(',') ||
            previous.endsWith('-') ||
            previousOpenParenthesis ||
            value.startsWith("(") ||
            normalized.startsWith("bairro ") ||
            normalized.startsWith("centro") ||
            normalized.startsWith("jardim ") ||
            normalized.startsWith("vila ") ||
            normalized.startsWith("cidade ") ||
            normalized.startsWith("sao ") ||
            normalized.startsWith("state ") ||
            stateRegex.containsMatchIn(value) ||
            cepRegex.containsMatchIn(value)
    }

    private fun cleanAddressSegment(value: String): String {
        val withoutMarker = value.replace(markerPrefix, "").trim()
        val match = addressStartRegex.find(withoutMarker)
        val start = match?.groups?.get(1)?.range?.first
        return if (start != null) withoutMarker.substring(start).trim() else withoutMarker
    }

    private fun normalizeLine(value: String): String = value
        .replace('\u00A0', ' ')
        .replace('\u202F', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
