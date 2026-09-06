package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Produz uma identidade estável para o destino final.
 *
 * A acessibilidade e o OCR podem entregar o mesmo endereço em etapas, com
 * parênteses soltos, bairro, cidade ou número aparecendo alguns milissegundos
 * depois. Essas variações não podem transformar o mesmo destino em outro card.
 */
object DestinationAddressIdentityPolicy {
    private val leadingWrapper = Regex("^[\\s\\p{Ps}\\p{Pi}\\\"'“”‘’<>|•·:;,\\-–—]+")
    private val trailingWrapper = Regex("[\\s\\p{Pe}\\p{Pf}\\\"'“”‘’<>|•·:;,\\-–—]+$")
    private val wrappedStreetStart = Regex(
        "^[\\s\\p{Ps}\\p{Pi}\\\"'“”‘’<>|•·:;,\\-–—]+(?=(?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|servidao|servidão)(?:\\b|(?=\\s)))",
        RegexOption.IGNORE_CASE,
    )
    private val markerPrefix = Regex(
        "^(?:[ab]|origem|partida|embarque|destino(?:\\s+final)?|chegada|desembarque)\\s*[:\\-–—]?\\s+",
        RegexOption.IGNORE_CASE,
    )
    private val explicitHouseNumber = Regex(
        "(?:,\\s*|\\bn(?:[º°o]\\.?|\\.|[uú]mero)\\s*[:\\-]?\\s*)(\\d{1,6}[a-z]?)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val streetAliases = mapOf(
        "r" to "rua",
        "rua" to "rua",
        "av" to "avenida",
        "avenida" to "avenida",
        "rod" to "rodovia",
        "rodovia" to "rodovia",
        "estr" to "estrada",
        "estrada" to "estrada",
        "tv" to "travessa",
        "travessa" to "travessa",
        "al" to "alameda",
        "alameda" to "alameda",
        "praca" to "praca",
        "via" to "via",
        "viela" to "viela",
        "beco" to "beco",
        "marginal" to "marginal",
        "servidao" to "servidao",
    )

    data class Identity(
        val canonical: String,
        val streetType: String?,
        val streetNameTokens: List<String>,
        val explicitNumber: String?,
    )

    /**
     * Limpeza usada durante a montagem de linhas quebradas. Remove um invólucro
     * inicial somente quando ele antecede diretamente um logradouro. Assim,
     * "(Avenida Lucas Nogueira" é corrigido, mas "(Cidade Líder)" continua
     * disponível como complemento da linha anterior.
     */
    fun cleanParserSegment(value: String): String {
        var cleaned = value
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        cleaned = cleaned.replace(markerPrefix, "").trim()
        cleaned = cleaned.replace(wrappedStreetStart, "")
        return cleaned.replace(Regex("\\s+"), " ").trim()
    }

    /** Limpeza final para geocodificação, cache, relatório e identidade. */
    fun cleanDisplayAddress(value: String): String = cleanParserSegment(value)
        .replace(leadingWrapper, "")
        .replace(trailingWrapper, "")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun signature(packageName: String?, destination: String?): String {
        val normalizedPackage = packageName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val identity = identity(destination)
        return "$normalizedPackage|${identity.canonical}"
    }

    fun sameDestinationSignatures(previous: String?, current: String?): Boolean {
        if (previous.isNullOrBlank() || current.isNullOrBlank()) return false
        if (previous == current) return true
        val previousSeparator = previous.indexOf('|')
        val currentSeparator = current.indexOf('|')
        if (previousSeparator <= 0 || currentSeparator <= 0) return false
        val previousPackage = previous.substring(0, previousSeparator)
        val currentPackage = current.substring(0, currentSeparator)
        if (previousPackage != currentPackage) return false
        return areCompatible(
            identity(previous.substring(previousSeparator + 1)),
            identity(current.substring(currentSeparator + 1)),
        )
    }

    fun areCompatible(previous: Identity, current: Identity): Boolean {
        if (previous.canonical.isBlank() || current.canonical.isBlank()) return false
        if (previous.canonical == current.canonical) return true
        if (previous.streetType != null && current.streetType != null && previous.streetType != current.streetType) {
            return false
        }
        if (previous.explicitNumber != null && current.explicitNumber != null &&
            previous.explicitNumber != current.explicitNumber
        ) {
            return false
        }
        val first = previous.streetNameTokens
        val second = current.streetNameTokens
        val minimum = minOf(first.size, second.size)
        if (minimum < MINIMUM_STREET_NAME_TOKENS) return false
        val commonPrefix = (0 until minimum).takeWhile { index -> first[index] == second[index] }.size
        if (commonPrefix < MINIMUM_STREET_NAME_TOKENS) return false
        return first.take(minimum) == second.take(minimum)
    }

    fun identity(value: String?): Identity {
        val display = cleanDisplayAddress(value.orEmpty())
        val canonical = canonical(display)
        val tokens = canonical.split(' ').filter(String::isNotBlank)
        val firstToken = tokens.firstOrNull()
        val streetType = firstToken?.let(streetAliases::get)
        val nameStart = if (streetType != null) 1 else 0
        val explicitNumberValue = explicitHouseNumber.find(display)
            ?.groups
            ?.get(1)
            ?.value
            ?.lowercase(Locale.ROOT)
        val streetNameTokens = tokens
            .drop(nameStart)
            .takeWhile { token -> token != explicitNumberValue }
            .filterNot { token -> token.length == 8 && token.all(Char::isDigit) }
        return Identity(
            canonical = canonical,
            streetType = streetType,
            streetNameTokens = streetNameTokens,
            explicitNumber = explicitNumberValue,
        )
    }

    private fun canonical(value: String): String = Normalizer
        .normalize(cleanDisplayAddress(value).lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private const val MINIMUM_STREET_NAME_TOKENS = 2
}
