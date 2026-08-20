package br.com.mapeiaia.rotacerta

import java.text.Normalizer

data class ScreenPhoneTarget(
    val nationalDigits: String,
    val internationalDigits: String,
    val displayNumber: String,
    val url: String,
    val score: Int,
)

object ScreenPhoneLink {
    private val validBrazilianAreaCodes = setOf(
        11, 12, 13, 14, 15, 16, 17, 18, 19,
        21, 22, 24, 27, 28,
        31, 32, 33, 34, 35, 37, 38,
        41, 42, 43, 44, 45, 46, 47, 48, 49,
        51, 53, 54, 55,
        61, 62, 63, 64, 65, 66, 67, 68, 69,
        71, 73, 74, 75, 77, 79,
        81, 82, 83, 84, 85, 86, 87, 88, 89,
        91, 92, 93, 94, 95, 96, 97, 98, 99,
    )

    private val candidateRegex = Regex(
        pattern = """(?<!\d)(?:\+?55[\s().-]*)?(?:\(?[1-9]\d\)?[\s.-]*)?(?:9[\s.-]?\d{4}|[2-5]\d{3})[\s.-]?\d{4}(?!\d)""",
    )

    fun findBest(text: String): ScreenPhoneTarget? {
        if (text.isBlank()) return null

        val bestByNumber = linkedMapOf<String, ScreenPhoneTarget>()
        text.lines().forEachIndexed { lineIndex, line ->
            candidateRegex.findAll(line).forEach { match ->
                createTarget(match.value, line, lineIndex)?.let { target ->
                    val previous = bestByNumber[target.nationalDigits]
                    if (previous == null || target.score > previous.score) {
                        bestByNumber[target.nationalDigits] = target
                    }
                }
            }
        }

        if (bestByNumber.isEmpty()) {
            candidateRegex.findAll(text).forEach { match ->
                createTarget(match.value, match.value, 0)?.let { target ->
                    bestByNumber.putIfAbsent(target.nationalDigits, target)
                }
            }
        }

        return bestByNumber.values.maxWithOrNull(
            compareBy<ScreenPhoneTarget> { it.score }
                .thenBy { it.nationalDigits.length }
                .thenByDescending { it.nationalDigits },
        )
    }

    private fun createTarget(raw: String, contextLine: String, lineIndex: Int): ScreenPhoneTarget? {
        val nationalDigits = normalizeBrazilianNumber(raw) ?: return null
        val normalizedContext = normalizeText(contextLine)
        if (normalizedContext.contains("cpf") || normalizedContext.contains("cnpj")) return null

        var score = 0
        if (normalizedContext.contains("whatsapp") || normalizedContext.contains("whats app")) score += 120
        if (
            normalizedContext.contains("telefone") ||
            normalizedContext.contains("celular") ||
            normalizedContext.contains("contato") ||
            normalizedContext.contains("fone") ||
            normalizedContext.contains("phone")
        ) {
            score += 80
        }
        if (raw.contains("+55")) score += 25
        if (raw.contains('(') || raw.contains(')')) score += 12
        if (raw.contains('-')) score += 8
        if (nationalDigits.length == 11) score += 10
        score += (30 - lineIndex.coerceAtMost(30))

        val internationalDigits = "55$nationalDigits"
        return ScreenPhoneTarget(
            nationalDigits = nationalDigits,
            internationalDigits = internationalDigits,
            displayNumber = formatNationalNumber(nationalDigits),
            url = "https://wa.me/$internationalDigits",
            score = score,
        )
    }

    private fun normalizeBrazilianNumber(raw: String): String? {
        var digits = raw.filter(Char::isDigit)
        if (digits.startsWith("00")) digits = digits.drop(2)
        if ((digits.length == 12 || digits.length == 13) && digits.startsWith("55")) {
            digits = digits.drop(2)
        }
        if (digits.length != 10 && digits.length != 11) return null

        val areaCode = digits.take(2).toIntOrNull() ?: return null
        if (areaCode !in validBrazilianAreaCodes) return null

        val subscriber = digits.drop(2)
        val validSubscriber = when (digits.length) {
            11 -> subscriber.length == 9 && subscriber.firstOrNull() == '9'
            10 -> subscriber.length == 8 && subscriber.firstOrNull() in '2'..'5'
            else -> false
        }
        if (!validSubscriber || subscriber.toSet().size == 1) return null
        return digits
    }

    private fun formatNationalNumber(digits: String): String = when (digits.length) {
        11 -> "(${digits.substring(0, 2)}) ${digits.substring(2, 7)}-${digits.substring(7)}"
        10 -> "(${digits.substring(0, 2)}) ${digits.substring(2, 6)}-${digits.substring(6)}"
        else -> digits
    }

    private fun normalizeText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
}
