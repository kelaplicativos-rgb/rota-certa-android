package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Detecta somente enderecos completos e numerados no texto visivel.
 *
 * Um candidato so e aceito quando a mesma linha que contem o logradouro tambem
 * contem um numero de imovel estruturalmente associado. Bairro, cidade, ponto de
 * referencia, estabelecimento, CEP, coordenada e numeros soltos nunca completam
 * artificialmente um endereco.
 */
object UniversalScreenAddressParser {
    private val streetStartRegex = Regex(
        "(?:^|[\\s:;])((?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|servidao|servidão)(?:\\b|(?=\\s)))",
        RegexOption.IGNORE_CASE,
    )
    private val localityStartRegex = Regex(
        "(?:^|[\\s:;])((?:bairro|jardim|vila|residencial|condominio|condomínio|loteamento|centro|sitio|sítio|fazenda)(?:\\b|(?=\\s)))",
        RegexOption.IGNORE_CASE,
    )
    private val poiStartRegex = Regex(
        "(?:^|[\\s:;])((?:shopping|terminal|estacao|estação|aeroporto|rodoviaria|rodoviária|hospital|mercado|restaurante|hotel|pousada|escola|faculdade|universidade|posto|parque|poupatempo|igreja|cemiterio|cemitério)(?:\\b|(?=\\s)))",
        RegexOption.IGNORE_CASE,
    )
    private val markerPrefix = Regex(
        "^(?:[ab]|origem|partida|embarque|destino(?:\\s+final)?|chegada|desembarque)\\s*[:\\-–—]?\\s+",
        RegexOption.IGNORE_CASE,
    )
    private val cepRegex = Regex("\\b\\d{5}-?\\d{3}\\b")
    private val noNumberRegex = Regex(
        "\\b(?:s\\s*/\\s*n|s\\s*n|sem\\s+n[uú]mero)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val houseNumberMarkerRegex = Regex(
        "n\\s*(?:[º°o]\\.?|\\.|[uú]mero)",
        RegexOption.IGNORE_CASE,
    )
    private val explicitHouseNumberRegex = Regex(
        "(?:,\\s*|\\bn(?:[º°o]\\.?|\\.|[uú]mero)\\s*[:\\-]?\\s*)(\\d{1,6}(?:[-/][\\p{L}\\d]+|[\\p{L}])?)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val directHouseNumberRegex = Regex(
        "\\b(\\d{1,6}(?:[-/][\\p{L}\\d]+|[\\p{L}])?)\\b(?=\\s*(?:$|[,;\\-–—]|\\b(?:bloco|casa|loja|sala|ap(?:to)?\\.?|apartamento|fundos|andar|lote|quadra)\\b))",
        RegexOption.IGNORE_CASE,
    )
    private val phoneRegex = Regex(
        "(?<!\\d)(?:\\+?55\\s*)?(?:\\(?\\d{2}\\)?\\s*)?(?:9\\s*)?\\d{4}[\\s\\-]?\\d{4}(?!\\d)",
    )
    private val timeRegex = Regex("\\b(?:[01]?\\d|2[0-3])[:h]\\d{2}\\b", RegexOption.IGNORE_CASE)
    private val moneyRegex = Regex(
        "(?:R\\$\\s*\\d+(?:[,.]\\d{2})?|\\b\\d+(?:[,.]\\d{2})?\\s*(?:R\\$|reais?)\\b)",
        RegexOption.IGNORE_CASE,
    )
    private val measurementRegex = Regex(
        "\\b\\d+(?:[,.]\\d+)?\\s*(?:km|quil[oô]metros?|m|min|minutos?|h|horas?)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val stateRegex = Regex(
        "(?:\\-|,|\\()\\s*(?:AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)(?:\\b|\\))",
        RegexOption.IGNORE_CASE,
    )
    private val fileRegex = Regex(
        "(?:\\.(?:txt|pdf|json|csv|zip|apk|jpg|jpeg|png|webp|doc|docx|xls|xlsx|mht|mp4)\\b|\\b\\d+(?:[,.]\\d+)?\\s*(?:kb|mb|gb)\\b|documento\\s+em\\s+pdf)",
        RegexOption.IGNORE_CASE,
    )
    private val calendarRegex = Regex(
        "(?:\\b(?:seg|ter|qua|qui|sex|sab|sáb|dom)\\.?\\b|\\b(?:jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)\\.?\\b|\\b\\d{1,2}\\s+de\\s+(?:janeiro|fevereiro|marco|março|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)\\b)",
        RegexOption.IGNORE_CASE,
    )
    private val uiNoiseRegex = Regex(
        "(?:radares?\\s+importados?|gps\\s+salvo|configura[cç][aã]o|apar[eê]ncia|leitura\\s+ao\\s+vivo|relat[oó]rio|backup|acessibilidade|bluetooth|wi-?fi|modo\\s+offline|lanterna|planos?\\s+de\\s+fundo|dados\\s+m[oó]veis|n[aã]o\\s+perturbar|quick\\s+share|multicontrole|transmiss[aã]o\\s+de\\s+[aá]udio|abrir\\s+rota\\s+certa)",
        RegexOption.IGNORE_CASE,
    )
    private val transactionNoiseRegex = Regex(
        "(?:r\\$|\\b\\d+(?:[,.]\\d+)?\\s*(?:km|m|min|minutos?)\\b|aceitar|ofere[cç]a|tarifa|pre[cç]o|pix|dinheiro|cart[aã]o|fechar|cancelar)",
        RegexOption.IGNORE_CASE,
    )
    private val invalidStreetNameWords = setOf("de", "da", "do", "das", "dos", "n", "no", "numero", "número")

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
            if (isCompleteNumberedAddress(current)) {
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

    fun isCompleteNumberedAddress(value: String): Boolean {
        if (value.length < 5 || isNoise(value)) return false
        val streetMatch = streetStartRegex.find(value) ?: return false
        val streetGroup = streetMatch.groups[1] ?: return false
        val streetTypeEnd = streetGroup.range.last + 1
        val addressTail = value.substring(streetTypeEnd)
        if (noNumberRegex.containsMatchIn(addressTail)) return false

        val excludedRanges = sequenceOf(cepRegex, phoneRegex, timeRegex, moneyRegex, measurementRegex)
            .flatMap { regex -> regex.findAll(value) }
            .map { match -> match.range }
            .toList()

        val explicitCandidates = explicitHouseNumberRegex.findAll(value, streetTypeEnd)
            .mapNotNull { match -> match.groups[1]?.range }
            .toList()
        if (explicitCandidates.any { range -> isValidHouseNumberRange(value, streetTypeEnd, range, excludedRanges) }) {
            return true
        }

        return directHouseNumberRegex.findAll(value, streetTypeEnd)
            .mapNotNull { match -> match.groups[1]?.range }
            .toList()
            .asReversed()
            .any { range -> isValidHouseNumberRange(value, streetTypeEnd, range, excludedRanges) }
    }

    private fun isValidHouseNumberRange(
        value: String,
        streetTypeEnd: Int,
        numberRange: IntRange,
        excludedRanges: List<IntRange>,
    ): Boolean {
        if (excludedRanges.any { excluded -> numberRange.overlaps(excluded) }) return false
        if (isDecimalFragment(value, numberRange)) return false

        val streetName = value.substring(streetTypeEnd, numberRange.first)
            .replace(houseNumberMarkerRegex, " ")
            .replace(Regex("[,;:\\-–—]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val meaningfulStreetWords = streetName
            .split(Regex("\\s+"))
            .map(::canonical)
            .filter { token -> token.any(Char::isLetter) && token !in invalidStreetNameWords }
        return meaningfulStreetWords.isNotEmpty()
    }

    private fun isDecimalFragment(value: String, range: IntRange): Boolean {
        val before = range.first - 1
        val after = range.last + 1
        val decimalBefore = before > 0 && value[before] in charArrayOf(',', '.') && value[before - 1].isDigit()
        val decimalAfter = after + 1 < value.length && value[after] in charArrayOf(',', '.') && value[after + 1].isDigit()
        return decimalBefore || decimalAfter
    }

    private fun looksLikeContinuation(value: String, previous: String): Boolean {
        if (value.length < 2 || isNoise(value)) return false
        if (streetStartRegex.containsMatchIn(value)) return false
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
            stateRegex.containsMatchIn(value) ||
            cepRegex.containsMatchIn(value)
    }

    private fun cleanAddressSegment(value: String): String {
        val withoutMarker = value.replace(markerPrefix, "").trim()
        val starts = listOfNotNull(
            streetStartRegex.find(withoutMarker)?.groups?.get(1)?.range?.first,
            localityStartRegex.find(withoutMarker)?.groups?.get(1)?.range?.first,
            poiStartRegex.find(withoutMarker)?.groups?.get(1)?.range?.first,
        )
        val start = starts.minOrNull()
        return if (start != null) withoutMarker.substring(start).trim() else withoutMarker
    }

    private fun isNoise(value: String): Boolean =
        fileRegex.containsMatchIn(value) ||
            calendarRegex.containsMatchIn(value) ||
            uiNoiseRegex.containsMatchIn(value) ||
            transactionNoiseRegex.containsMatchIn(value)

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

    private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last
}
