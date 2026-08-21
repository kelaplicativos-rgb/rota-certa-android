package br.com.mapeiaia.rotacerta

/**
 * Stage45: reconstruction of OCR-wrapped addresses inside ONE already spatially-clustered visual group.
 *
 * The helper never combines different FarolVisualGroup0189 groups. Geometry/bounds remain the card
 * isolation authority; this class only repairs line wrapping inside that bounded group before Stage21.
 */
object FarolOcrMultilineAddressStage45 {
    const val CONTRACT_MARKER = "FAROL_OCR_MULTILINE_ADDRESS_STAGE45"
    const val SAME_SPATIAL_CLUSTER_ONLY_MARKER = "OCR_RECONSTRUCTION_SAME_SPATIAL_CLUSTER_ONLY_STAGE45"
    const val DIRECT_HOUSE_NUMBER_MARKER = "OCR_SAFE_DIRECT_HOUSE_NUMBER_NORMALIZATION_STAGE45"
    const val PARENTHESIS_CONTINUATION_MARKER = "OCR_PARENTHESES_CONTINUATION_RECONSTRUCTION_STAGE45"
    const val SEMANTIC_GATE_UNCHANGED_MARKER = "STAGE21_SEMANTIC_GATE_REMAINS_AUTHORITY_STAGE45"
    const val NO_POLLING_MARKER = "NO_POLLING_NO_CONTINUOUS_OCR_STAGE45"

    private val streetLeadRegex = Regex(
        "(?:^|[\\s:(])(?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|passagem|servidao|servidão)(?:\\b|(?=\\s))",
        RegexOption.IGNORE_CASE,
    )
    private val parenthesizedStreetRegex = Regex(
        "\\((?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|passagem|servidao|servidão)(?:\\b|(?=\\s))",
        RegexOption.IGNORE_CASE,
    )
    private val independentAddressStartRegex = Regex(
        "^(?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|passagem|servidao|servidão)(?:\\b|(?=\\s))",
        RegexOption.IGNORE_CASE,
    )
    private val unsafeContinuationRegex = Regex(
        "^(?:r\\$|aceitar\\b|ofere[cç]a\\b|pedido\\s+de\\s+viagem\\b|reclamar\\b|ocultar\\b|escolher\\s+no\\s+mapa\\b|\\d+(?:[,.]\\d+)?\\s*(?:km|m|min|min\\.|minutos?|seg|seg\\.|segundos?|h|horas?)\\b|(?:[01]?\\d|2[0-3]):\\d{2}\\b)",
        RegexOption.IGNORE_CASE,
    )
    private val numberTokenRegex = Regex("\\b\\d{1,6}(?:[-/][\\p{L}\\d]+|[\\p{L}])?\\b")
    private val wordRegex = Regex("[\\p{L}]{2,}")

    data class Reconstruction(
        val text: String,
        val changed: Boolean,
        val mergedStreetLines: Int,
        val mergedParenthesisLines: Int,
        val normalizedDirectNumbers: Int,
    )

    fun reconstructClusterText(text: String): String = reconstruct(text).text

    fun reconstruct(text: String): Reconstruction {
        if (text.isBlank()) return Reconstruction(text, false, 0, 0, 0)
        val lines = text.lines().map(::normalizeLine).filter(String::isNotBlank)
        if (lines.isEmpty()) return Reconstruction("", text.isNotEmpty(), 0, 0, 0)

        val output = ArrayList<String>(lines.size)
        var index = 0
        var mergedStreet = 0
        var mergedParen = 0
        var directNumbers = 0

        while (index < lines.size) {
            val streetMerge = mergeWrappedStreet(lines, index)
            if (streetMerge != null) {
                val normalized = normalizeSafeDirectHouseNumber(streetMerge.text)
                output += normalized.first
                mergedStreet += streetMerge.endIndex - index
                if (normalized.second) directNumbers += 1
                index = streetMerge.endIndex + 1
                continue
            }

            val parenthesisMerge = mergeParenthesizedAddress(lines, index)
            if (parenthesisMerge != null) {
                output += parenthesisMerge.text
                mergedParen += parenthesisMerge.endIndex - index
                index = parenthesisMerge.endIndex + 1
                continue
            }

            val normalized = normalizeSafeDirectHouseNumber(lines[index])
            output += normalized.first
            if (normalized.second) directNumbers += 1
            index += 1
        }

        val rebuilt = output.joinToString("\n")
        return Reconstruction(
            text = rebuilt,
            changed = rebuilt != lines.joinToString("\n"),
            mergedStreetLines = mergedStreet,
            mergedParenthesisLines = mergedParen,
            normalizedDirectNumbers = directNumbers,
        )
    }

    private data class Merge(val text: String, val endIndex: Int)

    private fun mergeWrappedStreet(lines: List<String>, start: Int): Merge? {
        val first = lines[start]
        if (!streetLeadRegex.containsMatchIn(first)) return null
        if (hasSafeDirectHouseNumber(first)) return null

        var joined = first
        var end = start
        val limit = minOf(lines.lastIndex, start + 3)
        var cursor = start + 1
        while (cursor <= limit) {
            val next = lines[cursor]
            if (isUnsafeContinuation(next)) break
            if (independentAddressStartRegex.containsMatchIn(next)) break
            joined = "$joined $next".replace(Regex("\\s+"), " ").trim()
            end = cursor

            val hasNumber = hasSafeDirectHouseNumber(joined)
            val parensOpen = parenthesisDepth(joined) > 0
            if (hasNumber && !parensOpen) break
            cursor += 1
        }

        if (end == start || !hasSafeDirectHouseNumber(joined)) return null
        // If OCR opened locality parentheses, keep at most one extra continuation until it closes.
        if (parenthesisDepth(joined) > 0 && end < lines.lastIndex && end < start + 4) {
            val next = lines[end + 1]
            if (!isUnsafeContinuation(next) && !independentAddressStartRegex.containsMatchIn(next)) {
                val closed = "$joined $next".replace(Regex("\\s+"), " ").trim()
                if (parenthesisDepth(closed) <= 0) return Merge(closed, end + 1)
            }
        }
        return Merge(joined, end)
    }

    private fun mergeParenthesizedAddress(lines: List<String>, start: Int): Merge? {
        val first = lines[start]
        if (!parenthesizedStreetRegex.containsMatchIn(first) || parenthesisDepth(first) <= 0) return null
        var joined = first
        var end = start
        val limit = minOf(lines.lastIndex, start + 4)
        var cursor = start + 1
        while (cursor <= limit) {
            val next = lines[cursor]
            if (isUnsafeContinuation(next)) break
            if (independentAddressStartRegex.containsMatchIn(next)) break
            joined = "$joined $next".replace(Regex("\\s+"), " ").trim()
            end = cursor
            if (parenthesisDepth(joined) <= 0) return Merge(joined, end)
            cursor += 1
        }
        return null
    }

    private fun normalizeSafeDirectHouseNumber(value: String): Pair<String, Boolean> {
        val street = streetLeadRegex.find(value) ?: return value to false
        val tailStart = street.range.last + 1
        val candidates = numberTokenRegex.findAll(value, tailStart).toList().asReversed()
        for (candidate in candidates) {
            val range = candidate.range
            if (!safeNumberContext(value, tailStart, range)) continue
            val before = value.substring(0, range.first).trimEnd()
            if (before.endsWith(',')) return value to false
            val numberAndSuffix = value.substring(range.first)
            val normalized = "$before, $numberAndSuffix".replace(Regex("\\s+"), " ").trim()
            return normalized to true
        }
        return value to false
    }

    private fun hasSafeDirectHouseNumber(value: String): Boolean {
        val street = streetLeadRegex.find(value) ?: return false
        val tailStart = street.range.last + 1
        return numberTokenRegex.findAll(value, tailStart).any { safeNumberContext(value, tailStart, it.range) }
    }

    private fun safeNumberContext(value: String, streetTailStart: Int, numberRange: IntRange): Boolean {
        val prefix = value.substring(streetTailStart, numberRange.first)
        if (wordRegex.findAll(prefix).count() < 1) return false
        val beforeIndex = numberRange.first - 1
        if (beforeIndex > 0 && value[beforeIndex] in charArrayOf('.', ',') && value[beforeIndex - 1].isDigit()) return false
        val suffix = value.substring(numberRange.last + 1).trimStart()
        if (suffix.isEmpty()) return true
        if (suffix.first() in charArrayOf('(', ',', '-', '–', '—')) return true
        return Regex("^(?:bloco|casa|loja|sala|ap(?:to)?\\.?|apartamento|fundos|andar|lote|quadra)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(suffix)
    }

    private fun isUnsafeContinuation(value: String): Boolean =
        value.isBlank() || unsafeContinuationRegex.containsMatchIn(value)

    private fun parenthesisDepth(value: String): Int =
        value.count { it == '(' } - value.count { it == ')' }

    private fun normalizeLine(value: String): String = value
        .replace('\u00A0', ' ')
        .replace('\u202F', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}
