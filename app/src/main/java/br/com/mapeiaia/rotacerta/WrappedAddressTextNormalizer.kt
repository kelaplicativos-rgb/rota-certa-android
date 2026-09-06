package br.com.mapeiaia.rotacerta

/**
 * Reconstitui apenas quebras de linha tipicas do OCR de prints.
 *
 * Exemplo:
 * Rua das Flores,
 * 120 - Centro
 *
 * A uniao so acontece quando a primeira linha contem um logradouro reconhecido,
 * ainda nao possui numero valido, e a linha seguinte comeca com um numero de
 * imovel seguro. Preco, horario, telefone, CEP e medidas nunca sao emprestados.
 */
object WrappedAddressTextNormalizer {
    private val streetRegex = Regex(
        "(?:^|[\\s:;])(?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|servidao|servidão)(?:\\b|(?=\\s))",
        RegexOption.IGNORE_CASE,
    )
    private val wrappedHouseNumberRegex = Regex(
        "^(?:n\\s*(?:[º°o]\\.?|\\.|[uú]mero)\\s*[:\\-]?\\s*)?\\d{1,6}(?:[-/][\\p{L}\\d]+|[\\p{L}])?\\b",
        RegexOption.IGNORE_CASE,
    )
    private val cepRegex = Regex("^\\d{5}-?\\d{3}\\b")
    private val timeRegex = Regex("^(?:[01]?\\d|2[0-3])[:h]\\d{2}\\b", RegexOption.IGNORE_CASE)
    private val moneyRegex = Regex("^(?:R\\$|\\d+(?:[,.]\\d{2})?\\s*(?:R\\$|reais?))", RegexOption.IGNORE_CASE)
    private val measurementRegex = Regex(
        "^\\d+(?:[,.]\\d+)?\\s*(?:km|quil[oô]metros?|m|min|minutos?|h|horas?)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val phoneRegex = Regex(
        "^(?:\\+?55\\s*)?(?:\\(?\\d{2}\\)?\\s*)?(?:9\\s*)?\\d{4}[\\s\\-]?\\d{4}\\b",
    )
    private val noNumberRegex = Regex("^(?:s\\s*/\\s*n|s\\s*n|sem\\s+n[uú]mero)\\b", RegexOption.IGNORE_CASE)

    fun normalize(text: String): String {
        if (text.isBlank()) return text
        val lines = text.lines().map(::normalizeLine)
        val normalized = mutableListOf<String>()
        var index = 0

        while (index < lines.size) {
            val current = lines[index]
            val next = lines.getOrNull(index + 1)
            if (next != null && shouldMerge(current, next)) {
                normalized += "$current $next".replace(Regex("\\s+"), " ").trim()
                index += 2
            } else {
                normalized += current
                index += 1
            }
        }

        return normalized.joinToString("\n")
    }

    private fun shouldMerge(streetLine: String, numberLine: String): Boolean {
        if (streetLine.isBlank() || numberLine.isBlank()) return false
        if (!streetRegex.containsMatchIn(streetLine)) return false
        if (UniversalScreenAddressParser.isCompleteNumberedAddress(streetLine)) return false
        if (!wrappedHouseNumberRegex.containsMatchIn(numberLine)) return false
        if (
            cepRegex.containsMatchIn(numberLine) ||
            timeRegex.containsMatchIn(numberLine) ||
            moneyRegex.containsMatchIn(numberLine) ||
            measurementRegex.containsMatchIn(numberLine) ||
            phoneRegex.containsMatchIn(numberLine) ||
            noNumberRegex.containsMatchIn(numberLine)
        ) return false

        return UniversalScreenAddressParser.isCompleteNumberedAddress("$streetLine $numberLine")
    }

    private fun normalizeLine(value: String): String = value
        .replace('\u00A0', ' ')
        .replace('\u202F', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}
