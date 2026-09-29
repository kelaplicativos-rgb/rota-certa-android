package br.com.mapeiaia.rotacerta

object FarolTemporalUiNoise0697 {
    const val CONTRACT_MARKER = "FAROL_TEMPORAL_UI_NOISE_0697"
    const val REMOVED_MARKER = "FAROL_TEMPORAL_UI_NOISE_REMOVED_0697"

    data class Result(
        val cleaned: String,
        val changed: Boolean,
        val removed: List<String>,
    )

    fun clean(raw: String): Result {
        if (raw.isBlank()) return Result(raw, false, emptyList())
        var value = raw
        val removed = ArrayList<String>(3)

        value = beforeHouseNumber.replace(value) { match ->
            removed += match.groupValues[2]
            match.groupValues[1] + match.groupValues[3]
        }
        value = afterHouseNumberBeforeLocality.replace(value) { match ->
            removed += match.groupValues[2]
            match.groupValues[1] + match.groupValues[3]
        }
        value = trailingTemporalLabel.replace(value) { match ->
            removed += match.groupValues[2]
            match.groupValues[1]
        }

        val normalized = value
            .replace(Regex("""\s+,\s*"""), ", ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val baseline = raw.trim().replace(Regex("""\s+"""), " ")
        return Result(normalized, normalized != baseline, removed.distinct())
    }

    private const val TEMPORAL =
        """(?:Agora mesmo|agora mesmo|AGORA MESMO|há\s+\d{1,3}\s*(?:s|seg(?:undos?)?|min(?:utos?)?|h|horas?)|\d{1,3}\s*(?:s|seg(?:undos?)?|min(?:utos?)?)\s+atrás)"""

    private val beforeHouseNumber = Regex(
        """(\p{L}[\p{L}\p{M}.']*)\s+($TEMPORAL)(\s*[,;:]?\s*)(?=\d+[A-Za-z]?\b)""",
    )

    private val afterHouseNumberBeforeLocality = Regex(
        """(\b\d+[A-Za-z]?)(?:\s+|\s*[,;:]\s*)($TEMPORAL)(\s*(?=\(|,|-))""",
    )

    private val trailingTemporalLabel = Regex(
        """(.+?)(?:\s+|\s*[-–—|,:;]\s*)($TEMPORAL)\s*$""",
    )
}
