// Ajuste orientado pelo diagnostico 0.1.109 (3089):
// - a decisao verde foi aplicada em 496 ms;
// - o OCR entregou "Rua Pedro da Lomba, 188, Sao" e a linha seguinte
//   "Rafael, Sao Paulo" ficou fora do destino;
// - enquanto o OCR possuia o card, a acessibilidade vazia ainda era consultada
//   varias vezes por segundo sem contribuir para a decisao.
//
// Contrato 0.1.110:
// - recompor localidade quebrada pelo OCR quando a primeira linha termina em
//   prefixo incompleto como Sao/Santo/Santa/Jardim/Vila/Cidade/Parque/Bairro;
// - manter o primeiro ciclo rapido de 120 ms;
// - quando o OCR ja possui um card ativo, reduzir o polling redundante da
//   acessibilidade para 650 ms, sem bloquear eventos reais de troca de janela.

fun fastRead110ReplaceOnce(
    source: String,
    oldValue: String,
    newValue: String,
    label: String,
): String {
    if (oldValue !in source) throw GradleException("Ponto ausente para $label")
    return source.replaceFirst(oldValue, newValue)
}

val universalFastReadRuntime110 by tasks.registering {
    val serviceFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt",
    )
    val parserFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt",
    )
    val guardFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/UniversalRuntimeGuards.kt",
    )
    val parserTestFile = layout.projectDirectory.file(
        "src/test/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParserTest.kt",
    )
    val guardTestFile = layout.projectDirectory.file(
        "src/test/java/br/com/mapeiaia/rotacerta/UniversalRuntimeGuardsTest.kt",
    )

    inputs.files(serviceFile, parserFile, guardFile, parserTestFile, guardTestFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalFastReadRuntime109"))

    doLast {
        val service = serviceFile.asFile
        val parser = parserFile.asFile
        val guard = guardFile.asFile
        val parserTest = parserTestFile.asFile
        val guardTest = guardTestFile.asFile
        listOf(service, parser, guard, parserTest, guardTest).forEach { file ->
            if (!file.exists()) throw GradleException("Arquivo nao encontrado: ${file.path}")
        }

        var guardText = guard.readText()
        if ("universal_accessibility_scan_watchdog_0_1_110" !in guardText) {
            guardText = fastRead110ReplaceOnce(
                source = guardText,
                oldValue = """    fun minimumOcrIntervalMillis(hasActiveAddressSignature: Boolean): Long =
        if (hasActiveAddressSignature) 650L else 300L

    private fun normalize(value: String?): String? =
""",
                newValue = """    fun minimumOcrIntervalMillis(hasActiveAddressSignature: Boolean): Long =
        if (hasActiveAddressSignature) 650L else 300L

    fun minimumAccessibilityScanIntervalMillis(
        accessibilityOwnsCard: Boolean,
        hasActiveAddressSignature: Boolean,
    ): Long = if (hasActiveAddressSignature && !accessibilityOwnsCard) {
        650L // universal_accessibility_scan_watchdog_0_1_110
    } else {
        120L
    }

    private fun normalize(value: String?): String? =
""",
                label = "politica de polling da acessibilidade",
            )
            guard.writeText(guardText)
        }

        var parserText = parser.readText()
        if ("universal_wrapped_locality_0_1_110" !in parserText) {
            parserText = fastRead110ReplaceOnce(
                source = parserText,
                oldValue = """        val normalized = canonical(value)
        val previousOpenParenthesis = previous.count { it == '(' } > previous.count { it == ')' }
        return previous.endsWith(',') ||
""",
                newValue = """        val normalized = canonical(value)
        val previousCanonical = canonical(previous)
        val danglingLocalityPrefix = previousCanonical.endsWith(" sao") ||
            previousCanonical.endsWith(" santo") ||
            previousCanonical.endsWith(" santa") ||
            previousCanonical.endsWith(" jardim") ||
            previousCanonical.endsWith(" vila") ||
            previousCanonical.endsWith(" cidade") ||
            previousCanonical.endsWith(" parque") ||
            previousCanonical.endsWith(" bairro")
        val wrappedLocalityContinuation = danglingLocalityPrefix &&
            value.contains(',') &&
            normalized.length in 3..80 // universal_wrapped_locality_0_1_110
        val previousOpenParenthesis = previous.count { it == '(' } > previous.count { it == ')' }
        return previous.endsWith(',') ||
""",
                label = "recomposicao de localidade quebrada",
            )
            parserText = fastRead110ReplaceOnce(
                source = parserText,
                oldValue = """            previousOpenParenthesis ||
            value.startsWith("(") ||
""",
                newValue = """            previousOpenParenthesis ||
            wrappedLocalityContinuation ||
            value.startsWith("(") ||
""",
                label = "ativacao da continuacao de localidade",
            )
            parser.writeText(parserText)
        }

        var serviceText = service.readText()
        if ("universal_fast_read_runtime_0_1_110" !in serviceText) {
            serviceText = fastRead110ReplaceOnce(
                source = serviceText,
                oldValue = """                delay(SCAN_LOOP_MS)
            }
        }
    }

    private fun startProximityAlertMonitor() {
""",
                newValue = """                val accessibilityScanDelayMillis = UniversalFastReadPolicy.minimumAccessibilityScanIntervalMillis(
                    accessibilityOwnsCard = universalAccessibilityOwnsCard,
                    hasActiveAddressSignature = universalActiveAddressSignature != null,
                )
                delay(accessibilityScanDelayMillis)
            }
        }
    }

    private fun startProximityAlertMonitor() {
""",
                label = "polling adaptativo da acessibilidade",
            )
            serviceText += "\n// universal_fast_read_runtime_0_1_110\n"
            service.writeText(serviceText)
        }

        var parserTestText = parserTest.readText()
        if ("wrappedNeighborhoodAfterDanglingSaoIsJoined" !in parserTestText) {
            parserTestText = fastRead110ReplaceOnce(
                source = parserTestText,
                oldValue = """    @Test
    fun realAddressStillWinsAfterNoisyLines() {
        val fields = UniversalScreenAddressParser.parse(
            """
            qua., 15 de jul.
            Documento em PDF
            R$ 42,00
            Rua Doutor Paulo - Centro, Tres Coracoes - MG
            """.trimIndent(),
        )

        assertEquals("Rua Doutor Paulo - Centro, Tres Coracoes - MG", fields.destination)
    }
}
""",
                newValue = """    @Test
    fun realAddressStillWinsAfterNoisyLines() {
        val fields = UniversalScreenAddressParser.parse(
            """
            qua., 15 de jul.
            Documento em PDF
            R$ 42,00
            Rua Doutor Paulo - Centro, Tres Coracoes - MG
            """.trimIndent(),
        )

        assertEquals("Rua Doutor Paulo - Centro, Tres Coracoes - MG", fields.destination)
    }

    @Test
    fun wrappedNeighborhoodAfterDanglingSaoIsJoined() {
        val fields = UniversalScreenAddressParser.parse(
            """
            Av. Mateo Bei, Sao Mateus, Sao
            12 minutos (3.4 km)
            Rua Pedro da Lomba, 188, Sao
            Rafael, Sao Paulo
            """.trimIndent(),
        )

        assertEquals("Av. Mateo Bei, Sao Mateus, Sao", fields.pickup)
        assertEquals("Rua Pedro da Lomba, 188, Sao Rafael, Sao Paulo", fields.destination)
    }
}
""",
                label = "teste da localidade quebrada",
            )
            parserTest.writeText(parserTestText)
        }

        var guardTestText = guardTest.readText()
        if ("ocrOwnedCardThrottlesOnlyRedundantAccessibilityPolling" !in guardTestText) {
            guardTestText = fastRead110ReplaceOnce(
                source = guardTestText,
                oldValue = """    @Test
    fun activeOcrUsesSlowerWatchdogWithoutDelayingFirstRead() {
        assertEquals(300L, UniversalFastReadPolicy.minimumOcrIntervalMillis(false))
        assertEquals(650L, UniversalFastReadPolicy.minimumOcrIntervalMillis(true))
    }

    @Test
    fun duplicateHistoryIsBlockedInsideWindow() {
""",
                newValue = """    @Test
    fun activeOcrUsesSlowerWatchdogWithoutDelayingFirstRead() {
        assertEquals(300L, UniversalFastReadPolicy.minimumOcrIntervalMillis(false))
        assertEquals(650L, UniversalFastReadPolicy.minimumOcrIntervalMillis(true))
    }

    @Test
    fun ocrOwnedCardThrottlesOnlyRedundantAccessibilityPolling() {
        assertEquals(
            120L,
            UniversalFastReadPolicy.minimumAccessibilityScanIntervalMillis(
                accessibilityOwnsCard = false,
                hasActiveAddressSignature = false,
            ),
        )
        assertEquals(
            120L,
            UniversalFastReadPolicy.minimumAccessibilityScanIntervalMillis(
                accessibilityOwnsCard = true,
                hasActiveAddressSignature = true,
            ),
        )
        assertEquals(
            650L,
            UniversalFastReadPolicy.minimumAccessibilityScanIntervalMillis(
                accessibilityOwnsCard = false,
                hasActiveAddressSignature = true,
            ),
        )
    }

    @Test
    fun duplicateHistoryIsBlockedInsideWindow() {
""",
                label = "teste do polling adaptativo",
            )
            guardTest.writeText(guardTestText)
        }

        listOf(
            "universal_accessibility_scan_watchdog_0_1_110",
            "universal_wrapped_locality_0_1_110",
            "UniversalFastReadPolicy.minimumAccessibilityScanIntervalMillis(",
            "wrappedNeighborhoodAfterDanglingSaoIsJoined",
            "ocrOwnedCardThrottlesOnlyRedundantAccessibilityPolling",
            "universal_fast_read_runtime_0_1_110",
        ).forEach { marker ->
            val present = marker in guard.readText() ||
                marker in parser.readText() ||
                marker in service.readText() ||
                marker in parserTest.readText() ||
                marker in guardTest.readText()
            if (!present) throw GradleException("Caminho rapido 0.1.110 incompleto: $marker")
        }
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalFastReadRuntime110)
}
