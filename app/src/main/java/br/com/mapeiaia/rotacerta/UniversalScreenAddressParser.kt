package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Detecta enderecos de rua no texto visivel, com ou sem numero de imovel.
 *
 * O numero melhora a precisao, mas nao e obrigatorio porque alguns cards exibem
 * somente o nome do logradouro. Preco, distancia, horario, telefone, CEP e
 * controles da interface continuam bloqueados como falsos enderecos.
 */
object UniversalScreenAddressParser {
    private val streetStartRegex = Regex(
        "(?:^|[\\s:;])((?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|passagem|servidao|servidão)(?:\\b|(?=\\s)))",
        RegexOption.IGNORE_CASE,
    )
    private val parenthesizedStreetRegex = Regex(
        "\\(((?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|servidao|servidão)(?:\\b|(?=\\s)))",
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
    private val namedPlaceStartRegex = Regex(
        "^(?:condominio|condomínio|conjunto\\s+residencial|residencial|loteamento|shopping|terminal|estacao|estação|aeroporto|rodoviaria|rodoviária|hospital|mercado|restaurante|hotel|pousada|escola|faculdade|universidade|posto|parque|poupatempo|igreja|cemiterio|cemitério|loja|lojas)(?:\\b|(?=\\s))",
        RegexOption.IGNORE_CASE,
    )
    private val namedPlaceLocalityRegex = Regex(
        "\\b(?:cidade|bairro|jardim|vila|distrito|municipio|município|satelite|satélite|centro)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val danglingStreetPrefixRegex = Regex(
        "(?:^|[\\s:(])(?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|servidao|servidão)\\s*[.:;,\\-–—]*$",
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
    private val houseNumberTokenRegex = Regex(
        "\\b\\d{1,6}(?:[-/][\\p{L}\\d]+|[\\p{L}])?\\b",
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
    private val invalidStreetNameWords = setOf("de", "da", "do", "das", "dos", "n", "no", "numero", "número", "sn", "app", "aplicativo", "web", "google", "maps") // no_via_app_false_address_checklist_15

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
            .flatMap(::splitAddressSegments)
            .filter { it.length >= 4 }

        val candidates = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val current = cleanAddressSegment(lines[index])
            val canStartAddress = isRecognizedAddress(current) ||
                isPotentialNamedPlacePrefix(current) ||
                isPotentialStreetPrefix(current)
            if (canStartAddress) {
                val startedFromDanglingStreetPrefix = isPotentialStreetPrefix(current)
                val parts = mutableListOf(current)
                var nextIndex = index + 1
                while (nextIndex < lines.size && parts.size < 3) {
                    val preserveNamedPlaceContinuation = isPotentialNamedPlacePrefix(parts.first()) ||
                        isPotentialStreetPrefix(parts.first())
                    val next = if (preserveNamedPlaceContinuation) {
                        normalizeLine(lines[nextIndex])
                    } else {
                        cleanAddressSegment(lines[nextIndex])
                    }
                    if (!looksLikeContinuation(next, parts.last())) break
                    parts += next
                    nextIndex += 1
                }
                val joinedRaw = parts.joinToString(" ")
                    .replace(Regex("\\s+"), " ")
                    .replace(Regex("\\.{2,}$"), "")
                    .trim(' ', ',', '-', '–', '—')
                val joined = if (startedFromDanglingStreetPrefix) {
                    val openingParenthesis = joinedRaw.indexOf('(')
                    if (openingParenthesis >= 0) {
                        joinedRaw.substring(openingParenthesis + 1)
                            .trim(' ', ',', '-', '–', '—', ')')
                    } else {
                        joinedRaw
                    }
                } else {
                    joinedRaw
                }
                if (joined.length >= 5 && isRecognizedAddress(joined)) candidates += joined
                index = nextIndex
            } else {
                index += 1
            }
        }

        return candidates.distinctBy(::canonical)
    } // universal_99_join_before_confirm_0_1_111

    /** Aceita logradouro reconhecivel mesmo quando o card omite o numero. */
    fun isRecognizedAddress(value: String): Boolean {
        if (value.length < 5 || isNoise(value)) return false
        val streetGroup = streetStartRegex.find(value)?.groups?.get(1)
            ?: parenthesizedStreetRegex.find(value)?.groups?.get(1)
        if (streetGroup != null) {
            return hasMeaningfulStreetName(value, streetGroup.range.last + 1)
        }
        return isRecognizedNamedPlace(value)
    }

    private fun isPotentialStreetPrefix(value: String): Boolean {
        if (value.length < 3 || isNoise(value)) return false
        return danglingStreetPrefixRegex.containsMatchIn(value)
    }

    private fun looksLikeStreetPrefixContinuation(value: String, previous: String): Boolean {
        if (!isPotentialStreetPrefix(previous) || value.length < 3 || isNoise(value)) return false
        return value.contains(',') ||
            value.contains('-') ||
            value.contains('–') ||
            value.contains('—') ||
            namedPlaceLocalityRegex.containsMatchIn(value) ||
            stateRegex.containsMatchIn(value) ||
            cepRegex.containsMatchIn(value)
    } // universal_fragmented_street_prefix_0_1_113

    private fun isPotentialNamedPlacePrefix(value: String): Boolean {
        if (!namedPlaceStartRegex.containsMatchIn(value) || isNoise(value)) return false
        val normalized = canonical(value)
        val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < 2) return false
        return normalized.endsWith(" residencial") ||
            normalized.endsWith(" condominio") ||
            normalized.endsWith(" loteamento") ||
            normalized.endsWith(" parque")
    }

    private fun isRecognizedNamedPlace(value: String): Boolean {
        if (!namedPlaceStartRegex.containsMatchIn(value)) return false
        val hasLocalitySignal = value.contains(',') ||
            stateRegex.containsMatchIn(value) ||
            cepRegex.containsMatchIn(value) ||
            namedPlaceLocalityRegex.containsMatchIn(value)
        if (!hasLocalitySignal) return false

        val genericWords = setOf(
            "condominio", "conjunto", "residencial", "loteamento", "shopping",
            "terminal", "estacao", "aeroporto", "rodoviaria", "hospital",
            "mercado", "restaurante", "hotel", "pousada", "escola", "faculdade",
            "universidade", "posto", "parque", "poupatempo", "igreja", "cemiterio",
            "loja", "lojas", "cidade", "bairro", "jardim", "vila", "distrito",
            "municipio", "satelite", "centro", "sao", "santo", "santa",
        )
        val meaningfulWords = canonical(value)
            .split(Regex("\\s+"))
            .filter { token -> token.length >= 3 && token !in genericWords }
        return meaningfulWords.size >= 2 // universal_99_named_destination_0_1_111
    }

    /** Mantido para validar e recompor numeros quebrados pelo OCR. */
    fun isCompleteNumberedAddress(value: String): Boolean {
        if (!isRecognizedAddress(value)) return false
        val streetGroup = streetStartRegex.find(value)?.groups?.get(1)
            ?: parenthesizedStreetRegex.find(value)?.groups?.get(1)
            ?: return false
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

    private fun hasMeaningfulStreetName(value: String, streetTypeEnd: Int): Boolean {
        val streetName = value.substring(streetTypeEnd)
            .substringBefore('(')
            .substringBefore(',')
            .substringBefore(" - ")
            .replace(noNumberRegex, " ")
            .replace(houseNumberMarkerRegex, " ")
            .replace(houseNumberTokenRegex, " ")
            .replace(Regex("[;:\\-–—]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return streetName
            .split(Regex("\\s+"))
            .map(::canonical)
            .any { token -> token.any(Char::isLetter) && token !in invalidStreetNameWords }
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
        if (streetStartRegex.containsMatchIn(value) || parenthesizedStreetRegex.containsMatchIn(value)) return false
        val normalized = canonical(value)
        val previousCanonical = canonical(previous)
        val danglingAddressPrefix = previousCanonical.endsWith(" sao") ||
            previousCanonical.endsWith(" santo") ||
            previousCanonical.endsWith(" santa") ||
            previousCanonical.endsWith(" jardim") ||
            previousCanonical.endsWith(" vila") ||
            previousCanonical.endsWith(" cidade") ||
            previousCanonical.endsWith(" parque") ||
            previousCanonical.endsWith(" bairro") ||
            previousCanonical.endsWith(" residencial") ||
            previousCanonical.endsWith(" condominio") ||
            previousCanonical.endsWith(" loteamento")
        val wrappedLocalityContinuation = danglingAddressPrefix &&
            (value.contains(',') || value.contains('(')) &&
            normalized.length in 3..100
        val wrappedStreetNumberContinuation = !isCompleteNumberedAddress(previous) &&
            explicitHouseNumberRegex.containsMatchIn(value) &&
            (stateRegex.containsMatchIn(value) ||
                cepRegex.containsMatchIn(value) ||
                namedPlaceLocalityRegex.containsMatchIn(value))
        val previousOpenParenthesis = previous.count { it == '(' } > previous.count { it == ')' }
        return looksLikeStreetPrefixContinuation(value, previous) ||
            previous.endsWith(',') ||
            previous.endsWith('-') ||
            previousOpenParenthesis ||
            wrappedLocalityContinuation ||
            wrappedStreetNumberContinuation ||
            value.startsWith("(") ||
            normalized.startsWith("bairro ") ||
            normalized.startsWith("centro") ||
            normalized.startsWith("jardim ") ||
            normalized.startsWith("vila ") ||
            normalized.startsWith("cidade ") ||
            normalized.startsWith("sao ") ||
            stateRegex.containsMatchIn(value) ||
            cepRegex.containsMatchIn(value)
    } // universal_99_wrapped_address_0_1_111

    private fun splitAddressSegments(value: String): List<String> {
        val normalized = normalizeLine(value)
        if (normalized.length < 4) return emptyList()
        val starts = streetStartRegex.findAll(normalized)
            .mapNotNull { match -> match.groups[1]?.range?.first }
            .distinct()
            .toList()
        if (starts.size <= 1) return listOf(normalized)

        return starts.mapIndexedNotNull { index, start ->
            val end = starts.getOrNull(index + 1) ?: normalized.length
            trimFlattenedAddressSegment(normalized.substring(start, end))
                .takeIf { it.length >= 4 }
        }
    }

    private fun trimFlattenedAddressSegment(value: String): String {
        val cleaned = value.trim(' ', ',', '-', '–', '—')
        var depth = 0
        var sawOpeningParenthesis = false
        for (index in cleaned.indices) {
            when (cleaned[index]) {
                '(' -> {
                    depth += 1
                    sawOpeningParenthesis = true
                }
                ')' -> {
                    if (depth > 0) depth -= 1
                    if (sawOpeningParenthesis && depth == 0) {
                        val suffix = cleaned.substring(index + 1).trim()
                        val startsNewContent = streetStartRegex.containsMatchIn(suffix) ||
                            poiStartRegex.containsMatchIn(suffix) ||
                            Regex("^(?:emei|emef|emeb|ubs|upa)\\b", RegexOption.IGNORE_CASE).containsMatchIn(suffix)
                        val localitySuffix = !startsNewContent && (
                            suffix.startsWith(",") ||
                                stateRegex.containsMatchIn(suffix) ||
                                cepRegex.containsMatchIn(suffix)
                            )
                        if (suffix.isNotBlank() && !localitySuffix) {
                            return cleaned.substring(0, index + 1).trim(' ', ',', '-', '–', '—')
                        }
                    }
                }
            }
        }
        return cleaned.trimEnd(' ', ',', '-', '–', '—', '(')
    } // universal_flattened_line_split_0_1_101

    private fun cleanAddressSegment(value: String): String {
        val withoutMarker = DestinationAddressIdentityPolicy.cleanParserSegment(value.replace(markerPrefix, "")) // clean_unmatched_address_wrappers_checklist_16
        if (parenthesizedStreetRegex.containsMatchIn(withoutMarker)) {
            return withoutMarker // universal_poi_parenthesized_address_0_1_107
        }
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

// universal_flattened_foreign_suffix_0_1_101

// universal_poi_destination_boundary_0_1_107

// universal_99_card_addresses_0_1_111

// universal_99_named_continuation_0_1_111

// universal_fragmented_pickup_0_1_113
