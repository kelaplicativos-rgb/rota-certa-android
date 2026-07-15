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
    private val addressStartRegex = Regex(
        listOf(streetStartRegex.pattern, localityStartRegex.pattern, poiStartRegex.pattern)
            .joinToString("|") { "(?:$it)" },
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
        if (value.length < 5 || isNoise(value)) return false
        if (coordinateRegex.matches(value)) return true

        val words = value.split(Regex("\\s+")).filter { token -> token.any(Char::isLetter) }
        val hasLetters = value.count { it.isLetter() } >= 4
        if (!hasLetters || words.size < 2) return false

        val hasStreetStart = streetStartRegex.containsMatchIn(value)
        val hasLocalityStart = localityStartRegex.containsMatchIn(value)
        val hasPoiStart = poiStartRegex.containsMatchIn(value)
        val hasNumber = numberRegex.containsMatchIn(value)
        val hasCep = cepRegex.containsMatchIn(value)
        val hasState = stateRegex.containsMatchIn(value)
        val hasLocalityPunctuation = value.contains(',') || value.contains(" - ") || value.contains('(')

        return when {
            hasStreetStart -> true
            hasPoiStart -> words.size >= 2
            hasLocalityStart -> words.size >= 3 || hasNumber || hasState || hasLocalityPunctuation
            hasCep -> true
            hasState && hasLocalityPunctuation && words.size >= 2 -> true
            else -> false
        }
    }

    private fun looksLikeContinuation(value: String, previous: String): Boolean {
        if (value.length < 2 || isNoise(value)) return false
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
}
