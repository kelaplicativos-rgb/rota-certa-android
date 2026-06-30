package br.com.mapeiaia.rotacerta

class RideTextParser {
    private val fareRegex = Regex("""R\$\s*\d{1,3}(?:\.\d{3})*(?:,\d{2})?""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""\b\d+(?:[,.]\d+)?\s*km\b""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""\b\d{1,3}\s*(?:min|minuto|minutos)\b""", RegexOption.IGNORE_CASE)
    private val addressWords = listOf("rua", "avenida", "av.", "rodovia", "estrada", "praca", "praça", "travessa", "alameda", "bairro", "condominio", "condomínio", "shopping", "terminal")
    private val pickupMarkers = listOf("embarque", "partida", "origem", "buscar", "coleta", "pickup")
    private val destinationMarkers = listOf("destino final", "destino", "chegada", "final", "desembarque", "dropoff", "para onde", "ir para")

    fun parse(text: String): RideFields {
        val lines = text
            .lines()
            .map { it.trim() }
            .filter { it.length >= 3 }

        val pickup = findAddressAfterMarker(lines, pickupMarkers) ?: findFirstAddress(lines)
        val destination = findAddressAfterMarker(lines, destinationMarkers) ?: findLastAddress(lines, pickup)

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
                lines.drop(index + 1).take(4).firstOrNull { looksLikeAddress(it) }?.let { return it }
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

    private fun findFirstAddress(lines: List<String>): String? =
        lines.firstOrNull { looksLikeAddress(it) }

    private fun findLastAddress(lines: List<String>, exclude: String?): String? =
        lines.asReversed().firstOrNull { candidate ->
            looksLikeAddress(candidate) && !candidate.equals(exclude, ignoreCase = true)
        }

    private fun looksLikeAddress(value: String): Boolean {
        if (isNoise(value)) return false
        val normalized = value.lowercase()
        return addressWords.any { normalized.contains(it) } || Regex("""\b\d{1,5}\b""").containsMatchIn(value)
    }

    private fun isNoise(value: String): Boolean {
        val normalized = value.lowercase()
        return fareRegex.containsMatchIn(value) ||
            distanceRegex.containsMatchIn(value) ||
            timeRegex.containsMatchIn(value) ||
            normalized.contains("pix") ||
            normalized.contains("cartao") ||
            normalized.contains("cartão") ||
            normalized.contains("dinheiro")
    }
}
