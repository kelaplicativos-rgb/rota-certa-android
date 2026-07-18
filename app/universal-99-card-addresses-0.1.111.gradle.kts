// Correcao orientada pelos dois cards reais do 99 e pelo diagnostico 0.1.110:
// - "Avenida Mateo" / "Bei, 2651 - Cidade Sao Mateus" deve formar um endereco;
// - "Condominio Parque Residencial" / "Santa Barbara, Cidade Satelite..." deve
//   ser aceito como destino nomeado mesmo sem Rua/Avenida;
// - um nome incompleto so vira endereco depois que a continuacao o completa.

fun rc111ReplaceRegion(
    source: String,
    startToken: String,
    endToken: String,
    replacement: String,
    label: String,
): String {
    val start = source.indexOf(startToken)
    val end = if (start >= 0) source.indexOf(endToken, start + startToken.length) else -1
    if (start < 0 || end <= start) throw GradleException("Regiao ausente para $label")
    return source.substring(0, start) + replacement + source.substring(end)
}

val universal99CardAddresses111 by tasks.registering {
    val parserFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt",
    )
    inputs.file(parserFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalFastReadRuntime110"))

    doLast {
        val file = parserFile.asFile
        if (!file.exists()) throw GradleException("UniversalScreenAddressParser.kt nao encontrado")
        var text = file.readText()

        if ("universal_99_named_destination_0_1_111" !in text) {
            val markerAnchor = "    private val markerPrefix = Regex(\n"
            if (markerAnchor !in text) throw GradleException("Ponto dos reconhecedores do parser ausente")
            val namedPlaceRegex = """    private val namedPlaceStartRegex = Regex(
        "^(?:condominio|condomínio|conjunto\\s+residencial|residencial|loteamento|shopping|terminal|estacao|estação|aeroporto|rodoviaria|rodoviária|hospital|mercado|restaurante|hotel|pousada|escola|faculdade|universidade|posto|parque|poupatempo|igreja|cemiterio|cemitério|loja|lojas)(?:\\b|(?=\\s))",
        RegexOption.IGNORE_CASE,
    )
    private val namedPlaceLocalityRegex = Regex(
        "\\b(?:cidade|bairro|jardim|vila|distrito|municipio|município|satelite|satélite|centro)\\b",
        RegexOption.IGNORE_CASE,
    )
"""
            text = text.replaceFirst(markerAnchor, namedPlaceRegex + markerAnchor)

            text = rc111ReplaceRegion(
                source = text,
                startToken = "    fun findAddresses(text: String): List<String> {",
                endToken = "    /** Aceita logradouro reconhecivel mesmo quando o card omite o numero. */",
                replacement = """    fun findAddresses(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = text.lines()
            .flatMap(::splitAddressSegments)
            .filter { it.length >= 4 }

        val candidates = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val current = cleanAddressSegment(lines[index])
            val canStartAddress = isRecognizedAddress(current) || isPotentialNamedPlacePrefix(current)
            if (canStartAddress) {
                val parts = mutableListOf(current)
                var nextIndex = index + 1
                while (nextIndex < lines.size && parts.size < 3) {
                    val next = cleanAddressSegment(lines[nextIndex])
                    if (!looksLikeContinuation(next, parts.last())) break
                    parts += next
                    nextIndex += 1
                }
                val joined = parts.joinToString(" ")
                    .replace(Regex("\\s+"), " ")
                    .replace(Regex("\\.{2,}$"), "")
                    .trim(' ', ',', '-', '–', '—')
                if (joined.length >= 5 && isRecognizedAddress(joined)) candidates += joined
                index = nextIndex
            } else {
                index += 1
            }
        }

        return candidates.distinctBy(::canonical)
    } // universal_99_join_before_confirm_0_1_111

""",
                label = "juncao antes da confirmacao do endereco",
            )

            text = rc111ReplaceRegion(
                source = text,
                startToken = "    fun isRecognizedAddress(value: String): Boolean {",
                endToken = "    /** Mantido para validar e recompor numeros quebrados pelo OCR. */",
                replacement = """    fun isRecognizedAddress(value: String): Boolean {
        if (value.length < 5 || isNoise(value)) return false
        val streetGroup = streetStartRegex.find(value)?.groups?.get(1)
            ?: parenthesizedStreetRegex.find(value)?.groups?.get(1)
        if (streetGroup != null) {
            return hasMeaningfulStreetName(value, streetGroup.range.last + 1)
        }
        return isRecognizedNamedPlace(value)
    }

    private fun isPotentialNamedPlacePrefix(value: String): Boolean {
        if (!namedPlaceStartRegex.containsMatchIn(value) || isNoise(value)) return false
        val normalized = canonical(value)
        val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < 2) return false
        return normalized.endsWith(" residencial") ||
            normalized.endsWith(" condominio") ||
            normalized.endsWith(" loteamento") ||
            normalized.endsWith(" parque")
    }

    private fun isRecognizedNamedPlace(value: String): Boolean {
        if (!namedPlaceStartRegex.containsMatchIn(value)) return false
        val hasLocalitySignal = value.contains(',') ||
            stateRegex.containsMatchIn(value) ||
            cepRegex.containsMatchIn(value) ||
            namedPlaceLocalityRegex.containsMatchIn(value)
        if (!hasLocalitySignal) return false

        val genericWords = setOf(
            "condominio", "conjunto", "residencial", "loteamento", "shopping",
            "terminal", "estacao", "aeroporto", "rodoviaria", "hospital",
            "mercado", "restaurante", "hotel", "pousada", "escola", "faculdade",
            "universidade", "posto", "parque", "poupatempo", "igreja", "cemiterio",
            "loja", "lojas", "cidade", "bairro", "jardim", "vila", "distrito",
            "municipio", "satelite", "centro", "sao", "santo", "santa",
        )
        val meaningfulWords = canonical(value)
            .split(Regex("\\s+"))
            .filter { token -> token.length >= 3 && token !in genericWords }
        return meaningfulWords.size >= 2 // universal_99_named_destination_0_1_111
    }

""",
                label = "reconhecimento de destino nomeado",
            )

            text = rc111ReplaceRegion(
                source = text,
                startToken = "    private fun looksLikeContinuation(value: String, previous: String): Boolean {",
                endToken = "    private fun splitAddressSegments(value: String): List<String> {",
                replacement = """    private fun looksLikeContinuation(value: String, previous: String): Boolean {
        if (value.length < 2 || isNoise(value)) return false
        if (streetStartRegex.containsMatchIn(value) || parenthesizedStreetRegex.containsMatchIn(value)) return false
        val normalized = canonical(value)
        val previousCanonical = canonical(previous)
        val danglingAddressPrefix = previousCanonical.endsWith(" sao") ||
            previousCanonical.endsWith(" santo") ||
            previousCanonical.endsWith(" santa") ||
            previousCanonical.endsWith(" jardim") ||
            previousCanonical.endsWith(" vila") ||
            previousCanonical.endsWith(" cidade") ||
            previousCanonical.endsWith(" parque") ||
            previousCanonical.endsWith(" bairro") ||
            previousCanonical.endsWith(" residencial") ||
            previousCanonical.endsWith(" condominio") ||
            previousCanonical.endsWith(" loteamento")
        val wrappedLocalityContinuation = danglingAddressPrefix &&
            value.contains(',') &&
            normalized.length in 3..100
        val wrappedStreetNumberContinuation = !isCompleteNumberedAddress(previous) &&
            explicitHouseNumberRegex.containsMatchIn(value) &&
            (stateRegex.containsMatchIn(value) ||
                cepRegex.containsMatchIn(value) ||
                namedPlaceLocalityRegex.containsMatchIn(value))
        val previousOpenParenthesis = previous.count { it == '(' } > previous.count { it == ')' }
        return previous.endsWith(',') ||
            previous.endsWith('-') ||
            previousOpenParenthesis ||
            wrappedLocalityContinuation ||
            wrappedStreetNumberContinuation ||
            value.startsWith("(") ||
            normalized.startsWith("bairro ") ||
            normalized.startsWith("centro") ||
            normalized.startsWith("jardim ") ||
            normalized.startsWith("vila ") ||
            normalized.startsWith("cidade ") ||
            normalized.startsWith("sao ") ||
            stateRegex.containsMatchIn(value) ||
            cepRegex.containsMatchIn(value)
    } // universal_99_wrapped_address_0_1_111

""",
                label = "continuacao de endereco do 99",
            )

            text += "\n// universal_99_card_addresses_0_1_111\n"
        }

        listOf(
            "namedPlaceStartRegex",
            "namedPlaceLocalityRegex",
            "isPotentialNamedPlacePrefix",
            "universal_99_join_before_confirm_0_1_111",
            "universal_99_named_destination_0_1_111",
            "universal_99_wrapped_address_0_1_111",
            "splitAddressSegments",
            "universal_99_card_addresses_0_1_111",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Contrato dos cards 99 incompleto: $marker")
        }

        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universal99CardAddresses111)
}
