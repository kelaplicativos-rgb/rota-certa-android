package br.com.mapeiaia.rotacerta

class RideTextParser {
    private val fareRegex = Regex("""R\$\s*\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?""", RegexOption.IGNORE_CASE)
    private val primaryFareRegex = Regex("""^R\$\s*\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?(?:\s|$)""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""\b\d+(?:[,.]\d+)?\s*km\b""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""\b\d{1,3}\s*(?:min|minuto|minutos)\b""", RegexOption.IGNORE_CASE)
    private val routeStepRegex = Regex("""\b\d{1,3}\s*min\s*\(\s*\d+(?:[,.]\d+)?\s*(?:m|km)\s*\)""", RegexOption.IGNORE_CASE)
    private val roadCodeRegex = Regex("""^[A-Z]{2}-\d{3}$""")
    private val mapPointRegex = Regex("""^[AB]\s+(.+)""", RegexOption.IGNORE_CASE)
    private val markerOnlyRegex = Regex("""^[AB]$""", RegexOption.IGNORE_CASE)
    private val addressWords = listOf(
        "rua",
        "r.",
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
    private val streetTypeSuffixes = listOf(" rua", " r.", " avenida", " av.", " travessa", " estrada", " rodovia", " alameda")

    fun parse(text: String): RideFields {
        val rawLines = text
            .lines()
            .map { it.trim() }
            .filter { it.length >= 2 || markerOnlyRegex.matches(it) }

        val lines = isolatePrimaryRideLines(rawLines)
        val scopedText = lines.joinToString("\n")

        val knownLayout = parseKnownRideAppLayout(lines)
        if (knownLayout != null) {
            return knownLayout.copy(
                fare = fareRegex.find(scopedText)?.value?.trim(),
                distance = distanceRegex.find(scopedText)?.value?.trim(),
                time = timeRegex.find(scopedText)?.value?.trim(),
            )
        }

        val addresses = findAddressCandidates(lines)
        val pickup = findAddressAfterMarker(lines, pickupMarkers) ?: addresses.firstOrNull()
        val destination = findAddressAfterMarker(lines, destinationMarkers) ?: addresses.asReversed().firstOrNull {
            !it.equals(pickup, ignoreCase = true)
        }

        return RideFields(
            pickup = pickup,
            destination = destination,
            fare = fareRegex.find(scopedText)?.value?.trim(),
            distance = distanceRegex.find(scopedText)?.value?.trim(),
            time = timeRegex.find(scopedText)?.value?.trim(),
        )
    }

    private fun isolatePrimaryRideLines(lines: List<String>): List<String> {
        if (lines.size < 4) return lines

        val primaryFareIndexes = lines.indices.filter { isPrimaryFareLine(lines[it]) }
        if (primaryFareIndexes.size >= 2) {
            val start = primaryFareIndexes.first()
            val end = primaryFareIndexes[1]
            return lines.subList(start, end)
        }

        val routeStepIndexes = lines.indices.filter { routeStepRegex.containsMatchIn(lines[it]) }
        if (routeStepIndexes.size > 2) {
            val start = primaryFareIndexes.firstOrNull()?.takeIf { it < routeStepIndexes[0] } ?: routeStepIndexes[0]
            val end = routeStepIndexes[2]
            return lines.subList(start, end)
        }

        return lines
    }

    private fun isPrimaryFareLine(line: String): Boolean {
        val normalized = line.lowercase()
        return primaryFareRegex.containsMatchIn(line) &&
            !normalized.contains("/km") &&
            !normalized.contains("tarifa") &&
            !normalized.contains("inclu") &&
            !normalized.contains("ofere")
    }

    private fun parseKnownRideAppLayout(lines: List<String>): RideFields? =
        parseMapPointLayout(lines) ?: parseRouteStepLayout(lines)

    private fun parseMapPointLayout(lines: List<String>): RideFields? {
        val candidates = mutableListOf<AddressCandidate>()

        lines.forEachIndexed { index, line ->
            val inlineMarker = mapPointRegex.find(line)
            if (inlineMarker != null) {
                val address = buildAddressBlock(lines, index, line)
                if (address != null) candidates += AddressCandidate(line.first().uppercaseChar(), address)
                return@forEachIndexed
            }

            if (markerOnlyRegex.matches(line)) {
                val address = buildAddressBlock(lines, index + 1, lines.getOrNull(index + 1).orEmpty())
                if (address != null) candidates += AddressCandidate(line.uppercase().first(), address)
            }
        }

        if (candidates.isEmpty()) return null

        return RideFields(
            pickup = candidates.firstOrNull { it.label == 'A' }?.address ?: candidates.firstOrNull()?.address,
            destination = candidates.lastOrNull { it.label == 'B' }?.address ?: candidates.lastOrNull()?.address,
        )
    }

    private fun parseRouteStepLayout(lines: List<String>): RideFields? {
        val candidates = mutableListOf<String>()

        lines.forEachIndexed { index, line ->
            if (!routeStepRegex.containsMatchIn(line)) return@forEachIndexed
            val startIndex = nextAddressLineIndex(lines, index + 1) ?: return@forEachIndexed
            buildAddressBlock(lines, startIndex, lines[startIndex])?.let { candidates += it }
        }

        if (candidates.isEmpty()) return null

        return RideFields(
            pickup = candidates.firstOrNull(),
            destination = candidates.lastOrNull(),
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
            buildAddressBlock(lines, index, rawLine)?.let { candidates += it }
        }

        return candidates.distinct()
    }

    private fun buildAddressBlock(lines: List<String>, startIndex: Int, rawFirstLine: String): String? {
        if (startIndex !in lines.indices) return null

        val firstLine = cleanAddressLine(rawFirstLine)
        if (!looksLikeAddress(firstLine)) return null

        val parts = mutableListOf(firstLine)
        var nextIndex = startIndex + 1
        while (nextIndex < lines.size && parts.size < 4) {
            val next = cleanAddressLine(lines[nextIndex])
            if (!isAddressContinuation(next, parts.last())) break
            parts += next
            nextIndex += 1
        }

        return parts.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
    }

    private fun nextAddressLineIndex(lines: List<String>, startIndex: Int): Int? =
        (startIndex until lines.size).firstOrNull { index ->
            val candidate = cleanAddressLine(lines[index])
            candidate.isNotBlank() && !isNoise(candidate) && !roadCodeRegex.matches(candidate) && !markerOnlyRegex.matches(candidate)
        }

    private fun cleanAddressLine(value: String): String =
        mapPointRegex.find(value)?.groupValues?.getOrNull(1)?.trim() ?: value.trim()

    private fun isAddressContinuation(value: String, previousLine: String): Boolean {
        if (value.length < 2 || isNoise(value) || roadCodeRegex.matches(value)) return false
        if (value.equals("A", ignoreCase = true) || value.equals("B", ignoreCase = true)) return false
        if (mapPointRegex.find(value) != null) return false

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

    private data class AddressCandidate(
        val label: Char,
        val address: String,
    )
}
