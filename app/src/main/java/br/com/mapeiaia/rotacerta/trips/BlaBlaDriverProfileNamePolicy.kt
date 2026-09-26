package br.com.mapeiaia.rotacerta.trips

internal object BlaBlaDriverProfileNamePolicy {
    private val tripLikePatterns = listOf(
        Regex("""\bviagem\s+(?:de|para)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:domingo|segunda(?:-feira)?|terça(?:-feira)?|quarta(?:-feira)?|quinta(?:-feira)?|sexta(?:-feira)?|sábado)\b.*\b\d{1,2}\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:ride|trip)\s+(?:from|to)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b\d{1,2}\s+de\s+(?:janeiro|fevereiro|março|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)\b""", RegexOption.IGNORE_CASE),
    )

    fun normalize(candidate: String?): String? {
        val value = candidate
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        if (value.length > 80) return null
        if (value.count { it == '→' } > 0 || value.contains("->")) return null
        if (tripLikePatterns.any { it.containsMatchIn(value) }) return null
        return value
    }

    fun isValid(candidate: String?): Boolean = normalize(candidate) != null
}
