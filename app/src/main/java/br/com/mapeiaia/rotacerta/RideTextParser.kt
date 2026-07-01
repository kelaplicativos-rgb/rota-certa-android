package br.com.mapeiaia.rotacerta

class RideTextParser {
    private val fareRegex = Regex("""R\$\s*\d{1,3}(?:\.\d{3})*(?:,\d{2})?""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""\b\d+(?:[,.]\d+)?\s*km\b""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""\b\d{1,3}\s*(?:min|minuto|minutos)\b""", RegexOption.IGNORE_CASE)
    private val roadCodeRegex = Regex("""^[A-Z]{2}-\d{3}$""")
    private val mapPointRegex = Regex("""^[AB]\s+(.+)""", RegexOption.IGNORE_CASE)
    private val addressWords = listOf(
        "rua",
        "avenida",
        "av.",
        "rodovia",
        "estrada",
        "praca",
        "praça",
        "travessa",
        "alameda",
        "bairro",
        "condominio",
        "condomínio",
        "shopping",
        "terminal",
    )
    private val pickupMarkers = listOf("embarque", "partida", "origem", "buscar", "coleta", "pickup")
    private val destinationMarkers = listOf("destino final", "destino", "chegada", "final", "desembarque", "dropoff", "para onde", "ir para")
    private val streetTypeSuffixes = listOf(" rua", " avenida", " av.", " travessa", " estrada", " rodovia", " alameda")

    fun parse(text: String): RideFields {
        val lines = text
            .lines()
            .map { it.trim() }
            .filter { it.length >= 2 }

        val addresses = findAddressCandidates(lines)
        val pickup = findAddressAfterMarker(lines, pickupMarkers) ?: addresses.firstOrNull()
        val destination = findAddressAfterMarker(lines, destinationMarkers) ?: addresses.asReversed().firstOrNull {
            !it.equals(pickup, ignoreCase = true)
        }

        return RideFields(
            pickup = pickup,
            destination = destination,
            fare = fareRegex.find(text)?.value?.trim(),
            distance = distanceRegex.find(text)?.value?.trim(),
            time = timeRegex.find(text)?.value?.trim(),
        )
    }

    private fun findAddressAfterMarker(lines: List<String>, markers: List<String>): String? {
        lines.forEachIndexed { index, line ->
            val normalized = line.lowercase()
            val marker = markers.firstOrNull { normalized.contains(it) }
            if (marker != null) {
                val sameLineValue = valueAfterMarker(line, normalized, marker)
                if (sameLineValue.length >= 5 && looksLikeAddress(sameLineValue)) {
                    return sameLineValue
                }
                findAddressCandidates(lines.drop(index + 1).take(6)).firstOrNull()?.let { return it }
                lines.getOrNull(index + 1)?.takeIf { it.length >= 5 && !isNoise(it) }?.let { return it }
            }
        }
        return null
    }

    private fun valueAfterMarker(originalLine: String, normalizedLine: String, marker: String): String {
        val markerIndex = normalizedLine.indexOf(marker)
        if (markerIndex < 0) return ""
        return originalLine
            .drop(markerIndex + marker.length)
            .trim()
            .trimStart(':', '-', '—', ' ')
            .trim()
    }

    private fun findAddressCandidates(lines: List<String>): List<String> {
        val candidates = mutableListOf<String>()

        lines.forEachIndexed { index, rawLine ->
            val firstLine = cleanAddressLine(rawLine)
            if (!looksLikeAddress(firstLine)) return@forEachIndexed

            val parts = mutableListOf(firstLine)
            var nextIndex = index + 1
            while (nextIndex < lines.size && parts.size < 4) {
                val next = cleanAddressLine(lines[nextIndex])
                if (!isAddressContinuation(next, parts.last())) break
                parts += next
                nextIndex += 1
            }

            candidates += parts.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
        }

        return candidates.distinct()
    }

    private fun cleanAddressLine(value: String): String =
        mapPointRegex.find(value)?.groupValues?.getOrNull(1)?.trim() ?: value.trim()

    private fun isAddressContinuation(value: String, previousLine: String): Boolean {
        if (value.length < 2 || isNoise(value) || roadCodeRegex.matches(value)) return false
        if (value.equals("A", ignoreCase = true) || value.equals("B", ignoreCase = true)) return false

        val normalized = value.lowercase()
        val previous = previousLine.trim()
        val previousNormalized = previous.lowercase()
        val previousHasOpenParenthesis = previous.count { it == '(' } > previous.count { it == ')' }
        val previousEndsWithStreetType = streetTypeSuffixes.any { previousNormalized.endsWith(it) }

        if (looksLikeAddress(value) && !previousEndsWithStreetType) return false

        return previous.endsWith(",") ||
            previousEndsWithStreetType ||
            previousHasOpenParenthesis ||
            normalized.startsWith("da ") ||
            normalized.startsWith("de ") ||
            normalized.startsWith("do ") ||
            normalized.startsWith("das ") ||
            normalized.startsWith("dos ") ||
            normalized.firstOrNull()?.isLowerCase() == true
    }

    private fun looksLikeAddress(value: String): Boolean {
        if (isNoise(value) || roadCodeRegex.matches(value)) return false
        val normalized = value.lowercase()
        val hasAddressWord = addressWords.any { normalized.contains(it) }
        val hasAddressNumber = Regex("""\b\d{1,5}\b""").containsMatchIn(value) &&
            listOf(",", "-", "(", ")").any { value.contains(it) }
        return hasAddressWord || hasAddressNumber
    }

    private fun isNoise(value: String): Boolean {
        val normalized = value.lowercase()
        return fareRegex.containsMatchIn(value) ||
            distanceRegex.containsMatchIn(value) ||
            timeRegex.containsMatchIn(value) ||
            normalized.contains("pix") ||
            normalized.contains("cartao") ||
            normalized.contains("cartão") ||
            normalized.contains("dinheiro") ||
            normalized.contains("aceitar") ||
            normalized.contains("ofereça") ||
            normalized.contains("ofereca") ||
            normalized.contains("fechar") ||
            normalized.contains("pedido de viagem") ||
            normalized.contains("preço") ||
            normalized.contains("preco") ||
            normalized.contains("tarifa")
    }
}
