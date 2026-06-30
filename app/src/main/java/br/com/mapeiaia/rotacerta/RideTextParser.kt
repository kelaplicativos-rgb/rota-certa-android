package br.com.mapeiaia.rotacerta

class RideTextParser {
    private val fareRegex = Regex("""R\$\s*\d{1,3}(?:\.\d{3})*(?:,\d{2})?""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""\b\d+(?:[,.]\d+)?\s*km\b""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""\b\d{1,3}\s*(?:min|minuto|minutos)\b""", RegexOption.IGNORE_CASE)
    private val addressWords = listOf("rua", "avenida", "av.", "rodovia", "estrada", "praça", "travessa", "alameda", "bairro")
    private val pickupMarkers = listOf("embarque", "partida", "origem", "buscar", "coleta", "pickup")
    private val destinationMarkers = listOf("destino", "chegada", "final", "desembarque", "dropoff")

    fun parse(text: String): RideFields {
        val lines = text
            .lines()
            .map { it.trim() }
            .filter { it.length >= 3 }

        return RideFields(
            pickup = findAddressAfterMarker(lines, pickupMarkers) ?: findFirstAddress(lines),
            destination = findAddressAfterMarker(lines, destinationMarkers),
            fare = fareRegex.find(text)?.value?.trim(),
            distance = distanceRegex.find(text)?.value?.trim(),
            time = timeRegex.find(text)?.value?.trim(),
        )
    }

    private fun findAddressAfterMarker(lines: List<String>, markers: List<String>): String? {
        lines.forEachIndexed { index, line ->
            val normalized = line.lowercase()
            if (markers.any { normalized.contains(it) }) {
                val sameLineValue = line.substringAfter(":", missingDelimiterValue = "").trim()
                if (sameLineValue.length >= 5 && !markers.any { sameLineValue.lowercase() == it }) {
                    return sameLineValue
                }
                lines.drop(index + 1).take(3).firstOrNull { looksLikeAddress(it) }?.let { return it }
                lines.getOrNull(index + 1)?.takeIf { it.length >= 5 }?.let { return it }
            }
        }
        return null
    }

    private fun findFirstAddress(lines: List<String>): String? =
        lines.firstOrNull { looksLikeAddress(it) }

    private fun looksLikeAddress(value: String): Boolean {
        val normalized = value.lowercase()
        return addressWords.any { normalized.contains(it) } || Regex("""\d{1,5}""").containsMatchIn(value)
    }
}
