package br.com.mapeiaia.rotacerta

class RideTextParser {
    private val fareRegex = Regex("""R\$\s*\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?""", RegexOption.IGNORE_CASE)
    private val primaryFareRegex = Regex("""^R\$\s*\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?(?:\s|$)""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""\b\d+(?:[,.]\d+)?\s*km\b""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""\b\d{1,3}\s*(?:minutos|minuto|min)\b""", RegexOption.IGNORE_CASE)
    private val routeStepRegex = Regex(
        """\b\d{1,3}\s*(?:minutos|minuto|min)\s*\(\s*(?:\d+(?:[,.]\d+)?\s*)?(?:km|m)\s*\)""",
        RegexOption.IGNORE_CASE,
    )
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
        "estacao",
        "estação",
        "comercial",
    )
    private val pickupMarkers = listOf("embarque", "partida", "origem", "buscar", "coleta", "pickup")
    private val destinationMarkers = listOf("destino final", "destino", "chegada", "final", "desembarque", "dropoff", "para onde", "ir para")
    private val streetTypeSuffixes = listOf(" rua", " r.", " avenida", " av.", " travessa", " estrada", " rodovia", " alameda")

    fun parse(text: String, packageName: String? = null): RideFields =
        parseWithMetadata(text, packageName).fields

    fun parseWithMetadata(text: String, packageName: String? = null): RideParseResult {
        val rawLines = text
            .lines()
            .map { it.normalizeOcrWhitespace().trim() }
            .filter { it.length >= 2 || markerOnlyRegex.matches(it) }

        val appKind = RideAppKind.fromPackage(packageName) ?: inferRideAppKind(text)
        val appScopedLines = appKind?.let { isolateAppPrimaryRideLines(rawLines, it) } ?: rawLines
        val lines = isolatePrimaryRideLines(appScopedLines)
        val scopedText = lines.joinToString("\n")

        val appLayout = appKind?.let { parseAppSpecificLayout(it, lines) }
        if (appLayout != null) {
            return resultWithCommonFields(appLayout, lines, scopedText, appKind.parserName)
        }

        val knownLayout = parseKnownRideAppLayout(lines)
        if (knownLayout != null) {
            return resultWithCommonFields(knownLayout, lines, scopedText, "generic-known-layout")
        }

        val addresses = findAddressCandidates(lines)
        val pickup = findAddressAfterMarker(lines, pickupMarkers) ?: addresses.firstOrNull()
        val destination = findAddressAfterMarker(lines, destinationMarkers) ?: addresses.asReversed().firstOrNull {
            !it.equals(pickup, ignoreCase = true)
        }

        return RideParseResult(
            fields = RideFields(
                pickup = pickup,
                destination = destination,
                fare = findFare(lines, scopedText),
                distance = distanceRegex.find(scopedText)?.value?.trim(),
                time = timeRegex.find(scopedText)?.value?.trim(),
            ),
            parserName = appKind?.parserName ?: "generic-address-candidates",
        )
    }

    private fun resultWithCommonFields(fields: RideFields, lines: List<String>, scopedText: String, parserName: String): RideParseResult =
        RideParseResult(
            fields = fields.copy(
                fare = fields.fare ?: findFare(lines, scopedText),
                distance = fields.distance ?: distanceRegex.find(scopedText)?.value?.trim(),
                time = fields.time ?: timeRegex.find(scopedText)?.value?.trim(),
            ),
            parserName = parserName,
        )

    private fun parseAppSpecificLayout(appKind: RideAppKind, lines: List<String>): RideFields? = when (appKind) {
        RideAppKind.NinetyNine -> parseNinetyNineLayout(lines)
        RideAppKind.Uber -> parseUberLayout(lines)
        RideAppKind.InDrive -> parseInDriveLayout(lines)
    }

    private fun parseNinetyNineLayout(lines: List<String>): RideFields? {
        val routeLayout = parseRouteStepLayout(lines)
        if (!routeLayout?.destination.isNullOrBlank()) return routeLayout

        val addresses = findAddressCandidates(linesBeforeActions(lines))
        if (addresses.size < 2) return null
        return RideFields(
            pickup = addresses.firstOrNull(),
            destination = addresses.lastOrNull(),
        )
    }

    private fun parseUberLayout(lines: List<String>): RideFields? =
        parseRouteStepLayout(linesBeforeActions(lines))
            ?: parseMapPointLayout(linesBeforeActions(lines))
            ?: parseStackedAddressLayout(linesBeforeActions(lines))

    private fun parseInDriveLayout(lines: List<String>): RideFields? {
        val rideLines = linesBeforeActions(lines)
        return parseInDriveFirstOfferAddressBlock(rideLines)
            ?: parseMapPointLayout(rideLines)
            ?: parseStackedAddressLayout(rideLines)
            ?: parseRouteStepLayout(rideLines)
    }

    private fun parseInDriveFirstOfferAddressBlock(lines: List<String>): RideFields? {
        val fareIndex = lines.indexOfFirst { isPrimaryFareLine(it) }
        if (fareIndex < 0) return null

        val addresses = mutableListOf<String>()
        var started = false
        for (index in fareIndex + 1 until lines.size) {
            val line = lines[index]
            if (isActionLine(line) || isInDriveOfferBoundary(line)) {
                if (started) break
                continue
            }

            val address = buildAddressBlock(lines, index, line)
            if (address != null) {
                started = true
                if (addresses.none { it.equals(address, ignoreCase = true) }) addresses += address
                continue
            }

            if (started) break
        }

        if (addresses.size < 2) return null
        return RideFields(
            pickup = addresses.first(),
            destination = addresses.last { !it.equals(addresses.first(), ignoreCase = true) },
        )
    }

    private fun isInDriveOfferBoundary(line: String): Boolean {
        val normalized = line.lowercase().trim()
        return normalized == "pix" ||
            normalized == "dinheiro" ||
            normalized.contains("preço justo") ||
            normalized.contains("preco justo") ||
            normalized.contains("reclamar") ||
            normalized.contains("ocultar") ||
            normalized.contains("escolher no mapa") ||
            normalized.contains("pedido de viagem")
    }

    private fun isolateAppPrimaryRideLines(lines: List<String>, appKind: RideAppKind): List<String> = when (appKind) {
        RideAppKind.InDrive -> isolateInDrivePrimaryOffer(lines)
        RideAppKind.NinetyNine -> isolateNinetyNinePrimaryOffer(lines)
        RideAppKind.Uber -> linesBeforeActions(lines)
    }

    private fun isolateInDrivePrimaryOffer(lines: List<String>): List<String> {
        val mapPointIndexes = findMapPointAddressIndexes(lines)
        if (mapPointIndexes.size >= 2) {
            return lines.subList(0, addressBlockEndExclusive(lines, mapPointIndexes.last()))
        }
        return linesBeforeActions(lines)
    }

    private fun isolateNinetyNinePrimaryOffer(lines: List<String>): List<String> {
        val actionIndex = lines.indexOfFirst { isActionLine(it) }
        if (actionIndex > 0) return lines.take(actionIndex)
        return lines
    }

    private fun linesBeforeActions(lines: List<String>): List<String> {
        val actionIndex = lines.indexOfFirst { isActionLine(it) }
        return if (actionIndex > 0) lines.take(actionIndex) else lines
    }

    private fun isActionLine(line: String): Boolean {
        val normalized = line.lowercase().trim()
        return normalized.startsWith("aceitar") ||
            normalized.startsWith("selecionar") ||
            normalized.startsWith("ofereça") ||
            normalized.startsWith("ofereca") ||
            normalized == "fechar" ||
            normalized.contains("sua tarifa")
    }

    private fun inferRideAppKind(text: String): RideAppKind? {
        val normalized = text.lowercase()
        return when {
            normalized.contains("uberx") || normalized.contains("viagem longa") || normalized.contains("exclusivo") -> RideAppKind.Uber
            normalized.contains("negocia") || normalized.contains("perfil premium") || normalized.contains("perfil essencial") -> RideAppKind.NinetyNine
            normalized.contains("ofereça sua tarifa") || normalized.contains("ofereca sua tarifa") || normalized.contains("aceitar por") -> RideAppKind.InDrive
            normalized.contains("pedido de viagem") -> RideAppKind.InDrive
            else -> null
        }
    }

    private fun isolatePrimaryRideLines(lines: List<String>): List<String> {
        if (lines.size < 4) return lines

        val mapPointAddressIndexes = findMapPointAddressIndexes(lines)
        if (mapPointAddressIndexes.size >= 2) {
            val end = addressBlockEndExclusive(lines, mapPointAddressIndexes.last())
            return lines.subList(0, end)
        }

        val primaryFareIndexes = lines.indices.filter { isPrimaryFareLine(lines[it]) }
        if (primaryFareIndexes.size >= 2) {
            val start = 0
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

    private fun findMapPointAddressIndexes(lines: List<String>): List<Int> =
        lines.indices.filter { index ->
            val address = mapPointRegex.find(lines[index])?.groupValues?.getOrNull(1)?.trim()
            !address.isNullOrBlank() && looksLikeAddress(address)
        }

    private fun addressBlockEndExclusive(lines: List<String>, startIndex: Int): Int {
        if (startIndex !in lines.indices) return lines.size
        var end = startIndex + 1
        var previous = cleanAddressLine(lines[startIndex])
        while (end < lines.size) {
            val next = cleanAddressLine(lines[end])
            if (!isAddressContinuation(next, previous)) break
            previous = next
            end += 1
        }
        return end.coerceAtMost(lines.size)
    }

    private fun isPrimaryFareLine(line: String): Boolean {
        val normalized = line.lowercase()
        return primaryFareRegex.containsMatchIn(line) &&
            !normalized.contains("/km") &&
            !normalized.contains("tarifa") &&
            !normalized.contains("inclu") &&
            !normalized.contains("ofere")
    }

    private fun findFare(lines: List<String>, scopedText: String): String? {
        val firstMapPointAddressIndex = findMapPointAddressIndexes(lines).firstOrNull()
        val fareBeforeMapPoint = firstMapPointAddressIndex?.let { index ->
            lines.take(index).asReversed().firstOrNull { isPrimaryFareLine(it) }
        }
        val primaryFareLine = fareBeforeMapPoint ?: lines.firstOrNull { isPrimaryFareLine(it) }
        return primaryFareLine?.let { primaryFareRegex.find(it)?.value?.trim() }
            ?: fareRegex.findAll(scopedText)
                .firstOrNull { match ->
                    val tail = scopedText.substring(match.range.last + 1).trimStart().take(3)
                    !tail.startsWith("/km", ignoreCase = true)
                }
                ?.value
                ?.trim()
    }

    private fun parseKnownRideAppLayout(lines: List<String>): RideFields? =
        parseMapPointLayout(lines) ?: parseRouteStepLayout(lines) ?: parseStackedAddressLayout(lines)

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

        if (candidates.size == 1) {
            val addresses = findAddressCandidates(lines)
            return RideFields(
                pickup = addresses.firstOrNull(),
                destination = addresses.asReversed().firstOrNull { !it.equals(addresses.firstOrNull(), ignoreCase = true) },
            )
        }

        return RideFields(
            pickup = candidates.firstOrNull(),
            destination = candidates.lastOrNull(),
        )
    }

    private fun parseStackedAddressLayout(lines: List<String>): RideFields? {
        val candidates = findStandaloneAddressCandidates(lines)
        if (candidates.size < 2) return null

        return RideFields(
            pickup = candidates.firstOrNull(),
            destination = candidates.lastOrNull(),
        )
    }

    private fun findStandaloneAddressCandidates(lines: List<String>): List<String> =
        lines
            .asSequence()
            .map { cleanAddressLine(it) }
            .filter { it.length >= 8 && !isRideMarker(it) && !markerOnlyRegex.matches(it) && looksLikeAddress(it) }
            .distinct()
            .toList()

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
            candidate.isNotBlank() && !isNoise(candidate) && !isRideMarker(candidate) && !roadCodeRegex.matches(candidate) && !markerOnlyRegex.matches(candidate)
        }

    private fun cleanAddressLine(value: String): String =
        mapPointRegex.find(value)?.groupValues?.getOrNull(1)?.trim() ?: value.trim()

    private fun isAddressContinuation(value: String, previousLine: String): Boolean {
        if (value.length < 2 || isNoise(value) || isRideMarker(value) || roadCodeRegex.matches(value)) return false
        if (value.equals("A", ignoreCase = true) || value.equals("B", ignoreCase = true)) return false
        if (mapPointRegex.find(value) != null) return false

        val normalized = value.lowercase()
        val previous = previousLine.trim()
        val previousNormalized = previous.lowercase()
        val previousHasOpenParenthesis = previous.count { it == '(' } > previous.count { it == ')' }
        val previousEndsWithStreetType = streetTypeSuffixes.any { previousNormalized.endsWith(it) }
        val previousEndsWithConnector = listOf(" da", " de", " do", " das", " dos").any { previousNormalized.endsWith(it) }

        if (looksLikeAddress(value) && !previousEndsWithStreetType) return false

        return previous.endsWith(",") ||
            previousEndsWithStreetType ||
            previousEndsWithConnector ||
            previousHasOpenParenthesis ||
            normalized.startsWith("(") ||
            normalized.startsWith("da ") ||
            normalized.startsWith("de ") ||
            normalized.startsWith("do ") ||
            normalized.startsWith("das ") ||
            normalized.startsWith("dos ") ||
            normalized.firstOrNull()?.isLowerCase() == true
    }

    private fun looksLikeAddress(value: String): Boolean {
        if (isNoise(value) || isRideMarker(value) || roadCodeRegex.matches(value)) return false
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
            normalized.contains("selecionar") ||
            normalized.contains("ofereça") ||
            normalized.contains("ofereca") ||
            normalized.contains("fechar") ||
            normalized.contains("pedido de viagem") ||
            normalized.contains("preço") ||
            normalized.contains("preco") ||
            normalized.contains("tarifa")
    }

    private fun isRideMarker(value: String): Boolean {
        val normalized = value.lowercase().trim().trimEnd(':', '-', '—')
        return pickupMarkers.any { normalized == it } || destinationMarkers.any { normalized == it }
    }

    private fun String.normalizeOcrWhitespace(): String =
        replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace(Regex("""\s+"""), " ")

    private enum class RideAppKind(val parserName: String) {
        NinetyNine("99-card-template"),
        Uber("uber-trip-card"),
        InDrive("indrive-order-card");

        companion object {
            fun fromPackage(packageName: String?): RideAppKind? = when (packageName?.lowercase()) {
                "com.app99.driver" -> NinetyNine
                "com.ubercab.driver" -> Uber
                "sinet.startup.indriver" -> InDrive
                else -> null
            }
        }
    }

    private data class AddressCandidate(
        val label: Char,
        val address: String,
    )
}

data class RideParseResult(
    val fields: RideFields,
    val parserName: String,
)
