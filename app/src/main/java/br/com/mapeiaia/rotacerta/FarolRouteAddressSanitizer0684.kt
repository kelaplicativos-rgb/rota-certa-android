package br.com.mapeiaia.rotacerta

/**
 * 0.1.684 — final route-address barrier.
 *
 * No card address may reach route/geocode/cache authority before this sanitizer accepts it.
 * The sanitizer is intentionally conservative: a recoverable UI suffix is cut, equivalent repeated
 * addresses are collapsed, and unresolved ambiguity is rejected so the FAROL stays yellow.
 */
object FarolRouteAddressSanitizer0684 {
    const val CONTRACT_MARKER = "FAROL_ROUTE_ADDRESS_SANITIZER_0684"
    const val NO_DIRTY_ROUTE_MARKER = "NO_UNSANITIZED_CARD_ADDRESS_REACHES_ROUTE_0684"
    const val AMBIGUITY_YELLOW_MARKER = "AMBIGUOUS_ROUTE_ADDRESS_STAYS_YELLOW_0684"
    const val ACCESSIBILITY_PRIORITY_MARKER = "ACCESSIBILITY_ROUTE_ADDRESS_PRIORITY_OVER_OCR_0684"

    data class Result(
        val accepted: Boolean,
        val raw: String,
        val sanitized: String?,
        val reason: String,
        val cuts: List<String> = emptyList(),
    ) {
        val changed: Boolean get() = sanitized != null && FarolRouteAddressSanitizer0684.normalizeForCompare(raw) != FarolRouteAddressSanitizer0684.normalizeForCompare(sanitized)
    }

    fun sanitize(raw: String): Result {
        val original = normalize(raw)
        if (original.isBlank()) return Result(false, raw, null, "blank")

        val cuts = ArrayList<String>(4)
        var value = original

        val boundary = operationalBoundary.find(value)
        if (boundary != null) {
            value = value.substring(0, boundary.range.first).trimEnd()
            cuts += "operational_boundary:${boundary.value.trim().take(80)}"
        }

        value = trimDanglingParenthetical(value).also { trimmed ->
            if (trimmed != value) cuts += "dangling_parenthetical"
        }
        value = normalizeRoutePunctuation(value)
        if (value.isBlank()) return Result(false, raw, null, "empty_after_cleanup", cuts)

        val repeated = collapseRepeatedStreetAddress(value)
        if (!repeated.accepted) return Result(false, raw, null, repeated.reason, cuts + repeated.cuts)
        value = repeated.value
        cuts += repeated.cuts

        // Preserve balanced locality parentheses in the route text. cleanDisplayAddress() is
        // identity-oriented and strips trailing wrapper punctuation, which would turn "(Bairro)"
        // into an unbalanced route query. Parser-segment cleanup removes role wrappers without
        // damaging a balanced geographic complement.
        value = DestinationAddressIdentityPolicy.cleanParserSegment(value)
        value = normalizeRoutePunctuation(value)

        val parsed = UniversalScreenAddressParser.findAddresses(value)
            .map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
            .filter(String::isNotBlank)
            .distinctBy { DestinationAddressIdentityPolicy.identity(it).canonical }

        if (parsed.size > 1) {
            val first = DestinationAddressIdentityPolicy.identity(parsed.first())
            val allEquivalent = parsed.drop(1).all { candidate ->
                DestinationAddressIdentityPolicy.areCompatible(first, DestinationAddressIdentityPolicy.identity(candidate))
            }
            if (!allEquivalent) return Result(false, raw, null, "multiple_distinct_addresses", cuts + "parser_multiple_distinct")
            value = parsed.first()
            cuts += "parser_equivalent_duplicates"
        } else if (parsed.size == 1) {
            val parsedValue = parsed.single()
            if (DestinationAddressIdentityPolicy.areCompatible(
                    DestinationAddressIdentityPolicy.identity(value),
                    DestinationAddressIdentityPolicy.identity(parsedValue),
                )
            ) {
                value = parsedValue
            }
        }

        if (operationalBoundary.containsMatchIn(value)) {
            return Result(false, raw, null, "operational_text_survived", cuts)
        }
        if (!UniversalScreenAddressParser.isRecognizedAddress(value)) {
            return Result(false, raw, null, "no_positive_route_structure", cuts)
        }

        return Result(true, raw, value, if (cuts.isEmpty()) "clean" else "sanitized", cuts)
    }

    fun compatibleRouteAddress(first: String?, second: String?): Boolean {
        if (first.isNullOrBlank() || second.isNullOrBlank()) return false
        val firstIdentity = DestinationAddressIdentityPolicy.identity(first)
        val secondIdentity = DestinationAddressIdentityPolicy.identity(second)
        if (DestinationAddressIdentityPolicy.areCompatible(firstIdentity, secondIdentity)) return true
        if (firstIdentity.streetType == null || firstIdentity.streetType != secondIdentity.streetType) return false
        if (firstIdentity.explicitNumber == null || firstIdentity.explicitNumber != secondIdentity.explicitNumber) return false
        val firstStreet = firstIdentity.streetNameTokens
        val secondStreet = secondIdentity.streetNameTokens
        if (firstStreet.isEmpty() || secondStreet.isEmpty()) return false
        return firstStreet.first() == secondStreet.first()
    }

    private data class CollapseResult(
        val accepted: Boolean,
        val value: String,
        val reason: String = "ok",
        val cuts: List<String> = emptyList(),
    )

    private fun collapseRepeatedStreetAddress(value: String): CollapseResult {
        val matches = streetLead.findAll(value).mapNotNull { it.groups[1]?.range?.first }.distinct().toList()
        if (matches.size <= 1) return CollapseResult(true, value)

        val segments = matches.mapIndexed { index, start ->
            val end = matches.getOrNull(index + 1) ?: value.length
            normalizeRoutePunctuation(value.substring(start, end))
        }.filter { UniversalScreenAddressParser.isRecognizedAddress(it) }

        if (segments.size <= 1) return CollapseResult(true, value)
        val anchor = DestinationAddressIdentityPolicy.identity(segments.first())
        val equivalent = segments.drop(1).all { segment ->
            DestinationAddressIdentityPolicy.areCompatible(anchor, DestinationAddressIdentityPolicy.identity(segment))
        }
        if (!equivalent) return CollapseResult(false, value, "multiple_distinct_street_addresses", listOf("street_repeat_ambiguous"))

        val preferred = segments.maxWithOrNull(
            compareBy<String> { if (UniversalScreenAddressParser.isCompleteNumberedAddress(it)) 1 else 0 }
                .thenBy { localityEvidenceScore(it) }
                .thenBy { it.length },
        ) ?: segments.first()
        return CollapseResult(true, preferred, cuts = listOf("equivalent_repeated_street_collapsed"))
    }

    private fun trimDanglingParenthetical(value: String): String {
        var depth = 0
        var firstUnmatchedOpen = -1
        value.forEachIndexed { index, ch ->
            when (ch) {
                '(' -> {
                    if (depth == 0) firstUnmatchedOpen = index
                    depth += 1
                }
                ')' -> {
                    if (depth > 0) {
                        depth -= 1
                        if (depth == 0) firstUnmatchedOpen = -1
                    }
                }
            }
        }
        return if (depth > 0 && firstUnmatchedOpen >= 0) value.substring(0, firstUnmatchedOpen).trimEnd() else value
    }

    private fun normalizeRoutePunctuation(value: String): String = normalize(value)
        .trim(' ', ',', ';', ':', '-', '–', '—', '|')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun localityEvidenceScore(value: String): Int = listOf(
        Regex("(?iu)\\b(?:bairro|jardim|vila|parque|centro|cidade|condominio|condomínio|residencial)\\b"),
        Regex("(?iu)(?:-|,)\\s*(?:AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)\\b"),
        Regex("\\b\\d{5}-?\\d{3}\\b"),
    ).count { it.containsMatchIn(value) }

    private fun normalize(value: String): String = value
        .replace('\u00A0', ' ')
        .replace('\u202F', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalizeForCompare(value: String): String = normalize(value).lowercase()

    private val streetLead = Regex(
        "(?iu)(?:^|[\\s(])((?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|passagem|servidao|servidão)(?:\\b|(?=\\s)))",
    )

    private val operationalBoundary = Regex(
        "(?iu)(?:\\s*[-–—|,:;]?\\s*)\\b(?:" +
            "solicita[cç][aã]o\\s+j[aá]\\s+aceita|" +
            "solicita[cç][aã]o\\s+aceita|" +
            "pedidos?\\s+de\\s+viagem|" +
            "pedido\\s+de\\s+viagem|" +
            "detalhes?\\s+da\\s+viagem|" +
            "demanda|" +
            "desempenho|" +
            "aceitar|" +
            "recusar|" +
            "ofere[cç]a|" +
            "oferta|" +
            "reclamar|" +
            "ocultar|" +
            "escolher\\s+no\\s+mapa|" +
            "cancelar(?:\\s+(?:viagem|pedido))?|" +
            "pagamento|" +
            "fechar" +
        ")\\b",
    )
}
