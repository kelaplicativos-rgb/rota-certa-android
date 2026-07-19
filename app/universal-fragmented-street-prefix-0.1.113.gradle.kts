// Correcao orientada pelo card real e diagnostico 0.1.112 (3105):
// o OCR reconheceu somente "Av. Maria Luiza Americano, 2673" porque o embarque
// foi quebrado em linhas no formato "... (Avenida" / "Mateo Bei - Cidade...".
// A primeira linha ainda nao era um endereco completo e o parser nao iniciava a
// recomposicao, ficando com apenas um endereco e sem calcular a rota.

fun rc113ReplaceOnce(
    source: String,
    oldValue: String,
    newValue: String,
    label: String,
): String {
    if (oldValue !in source) throw GradleException("Ponto ausente para $label")
    return source.replaceFirst(oldValue, newValue)
}

fun rc113InsertBeforeLastBrace(source: String, addition: String, label: String): String {
    val index = source.lastIndexOf("\n}")
    if (index < 0) throw GradleException("Fechamento ausente para $label")
    return source.substring(0, index) + addition + source.substring(index)
}

val universalFragmentedStreetStart113 by tasks.registering {
    val parserFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt",
    )
    val parserTestFile = layout.projectDirectory.file(
        "src/test/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParserTest.kt",
    )

    inputs.files(parserFile, parserTestFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalRideCardEvidence112"))

    doLast {
        val parser = parserFile.asFile
        val parserTest = parserTestFile.asFile
        if (!parser.exists() || !parserTest.exists()) {
            throw GradleException("Fontes do parser 0.1.113 nao encontrados")
        }

        var parserText = parser.readText()
        if ("universal_fragmented_street_prefix_0_1_113" !in parserText) {
            val markerAnchor = "    private val markerPrefix = Regex(\n"
            val danglingRegex = """    private val danglingStreetPrefixRegex = Regex(
        "(?:^|[\\s:(])(?:r\\.|av\\.|rua|avenida|alameda|travessa|estrada|rodovia|praca|praça|largo|via|viela|beco|marginal|servidao|servidão)\\s*[.:;,\\-–—]*${'$'}",
        RegexOption.IGNORE_CASE,
    )
"""
            if (markerAnchor !in parserText) {
                throw GradleException("Ponto dos prefixos de logradouro ausente")
            }
            parserText = parserText.replaceFirst(markerAnchor, danglingRegex + markerAnchor)

            parserText = rc113ReplaceOnce(
                source = parserText,
                oldValue = """            val canStartAddress = isRecognizedAddress(current) || isPotentialNamedPlacePrefix(current)
            if (canStartAddress) {
                val parts = mutableListOf(current)
""",
                newValue = """            val canStartAddress = isRecognizedAddress(current) ||
                isPotentialNamedPlacePrefix(current) ||
                isPotentialStreetPrefix(current)
            if (canStartAddress) {
                val startedFromDanglingStreetPrefix = isPotentialStreetPrefix(current)
                val parts = mutableListOf(current)
""",
                label = "inicio de endereco com prefixo pendente",
            )

            parserText = rc113ReplaceOnce(
                source = parserText,
                oldValue = """                    val preserveNamedPlaceContinuation = isPotentialNamedPlacePrefix(parts.first())
""",
                newValue = """                    val preserveNamedPlaceContinuation = isPotentialNamedPlacePrefix(parts.first()) ||
                        isPotentialStreetPrefix(parts.first())
""",
                label = "preservacao da linha quebrada",
            )

            parserText = rc113ReplaceOnce(
                source = parserText,
                oldValue = """                val joined = parts.joinToString(" ")
                    .replace(Regex("\\s+"), " ")
                    .replace(Regex("\\.{2,}${'$'}"), "")
                    .trim(' ', ',', '-', '–', '—')
                if (joined.length >= 5 && isRecognizedAddress(joined)) candidates += joined
""",
                newValue = """                val joinedRaw = parts.joinToString(" ")
                    .replace(Regex("\\s+"), " ")
                    .replace(Regex("\\.{2,}${'$'}"), "")
                    .trim(' ', ',', '-', '–', '—')
                val joined = if (startedFromDanglingStreetPrefix) {
                    val openingParenthesis = joinedRaw.indexOf('(')
                    if (openingParenthesis >= 0) {
                        joinedRaw.substring(openingParenthesis + 1)
                            .trim(' ', ',', '-', '–', '—', ')')
                    } else {
                        joinedRaw
                    }
                } else {
                    joinedRaw
                }
                if (joined.length >= 5 && isRecognizedAddress(joined)) candidates += joined
""",
                label = "normalizacao do logradouro entre parenteses",
            )

            val helperAnchor = "    private fun isPotentialNamedPlacePrefix(value: String): Boolean {\n"
            val helper = """    private fun isPotentialStreetPrefix(value: String): Boolean {
        if (value.length < 3 || isNoise(value)) return false
        return danglingStreetPrefixRegex.containsMatchIn(value)
    }

    private fun looksLikeStreetPrefixContinuation(value: String, previous: String): Boolean {
        if (!isPotentialStreetPrefix(previous) || value.length < 3 || isNoise(value)) return false
        return value.contains(',') ||
            value.contains('-') ||
            value.contains('–') ||
            value.contains('—') ||
            namedPlaceLocalityRegex.containsMatchIn(value) ||
            stateRegex.containsMatchIn(value) ||
            cepRegex.containsMatchIn(value)
    } // universal_fragmented_street_prefix_0_1_113

"""
            if (helperAnchor !in parserText) {
                throw GradleException("Ponto do auxiliar de prefixo pendente ausente")
            }
            parserText = parserText.replaceFirst(helperAnchor, helper + helperAnchor)

            parserText = rc113ReplaceOnce(
                source = parserText,
                oldValue = """        return previous.endsWith(',') ||
            previous.endsWith('-') ||
""",
                newValue = """        return looksLikeStreetPrefixContinuation(value, previous) ||
            previous.endsWith(',') ||
            previous.endsWith('-') ||
""",
                label = "continuacao apos Avenida ou Rua isolada",
            )

            parserText += "\n// universal_fragmented_pickup_0_1_113\n"
            parser.writeText(parserText)
        }

        var testText = parserTest.readText()
        if ("realInDrivePickupSplitAfterAvenidaIsRecomposed" !in testText) {
            val tripleQuote = "\"\"\""
            val addition = """

    @Test
    fun realInDrivePickupSplitAfterAvenidaIsRecomposed() {
        val fields = UniversalScreenAddressParser.parse(
            ${tripleQuote}
            Pedido de viagem
            R${'$'} 2,3/km  ~1,4 km
            R${'$'} 15
            Comercial Esperanca - Sao
            Paulo Sao Mateus (Avenida
            Mateo Bei - Cidade Sao Mateus,
            Sao Paulo - State of Sao Paulo)
            Av. Maria Luiza Americano, 2673
            (Cidade Lider)
            PIX
            Aceitar por R${'$'} 15
            ${tripleQuote}.trimIndent(),
        )

        assertEquals(
            "Avenida Mateo Bei - Cidade Sao Mateus, Sao Paulo - State of Sao Paulo",
            fields.pickup,
        )
        assertEquals("Av. Maria Luiza Americano, 2673 (Cidade Lider)", fields.destination)
    }

    @Test
    fun isolatedStreetWordWithoutContinuationDoesNotBecomeAddress() {
        val addresses = UniversalScreenAddressParser.findAddresses(
            ${tripleQuote}
            Catalogo de vias
            Avenida
            Produto em promocao
            Rua
            ${tripleQuote}.trimIndent(),
        )

        assertTrue(addresses.isEmpty())
    }
"""
            testText = rc113InsertBeforeLastBrace(
                source = testText,
                addition = addition,
                label = "testes do embarque fragmentado",
            )
            parserTest.writeText(testText)
        }

        listOf(
            "danglingStreetPrefixRegex",
            "isPotentialStreetPrefix",
            "looksLikeStreetPrefixContinuation",
            "startedFromDanglingStreetPrefix",
            "universal_fragmented_street_prefix_0_1_113",
            "universal_fragmented_pickup_0_1_113",
            "realInDrivePickupSplitAfterAvenidaIsRecomposed",
            "isolatedStreetWordWithoutContinuationDoesNotBecomeAddress",
        ).forEach { marker ->
            val present = marker in parser.readText() || marker in parserTest.readText()
            if (!present) throw GradleException("Correcao do embarque fragmentado incompleta: $marker")
        }
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalFragmentedStreetStart113)
}
