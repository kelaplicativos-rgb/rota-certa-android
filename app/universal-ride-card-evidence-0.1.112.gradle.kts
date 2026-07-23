// Correcao orientada pelo diagnostico 0.1.111 (3101):
// uma imagem comum no app Arquivos continha varios enderecos, o OCR formou
// "Rua.luli" e o runtime calculou 191,992 km sem existir card de corrida.
//
// Contrato 0.1.112:
// - dois enderecos continuam necessarios, mas nao sao mais suficientes;
// - fora de um app de motorista, o texto precisa conter evidencias fortes de
//   oferta de corrida (tempo+distancia e tarifa/marcador de corrida);
// - dentro de 99/Uber/inDrive, basta ao menos um sinal real de oferta junto dos
//   dois enderecos, preservando rapidez e tolerancia ao OCR;
// - logradouros deformados como "Rua.luli" nunca liberam rota.

fun rc112InsertBeforeLastBrace(source: String, addition: String, label: String): String {
    val index = source.lastIndexOf("\n}")
    if (index < 0) throw GradleException("Fechamento ausente para $label")
    return source.substring(0, index) + addition + source.substring(index)
}

val universalRideCardEvidence112 by tasks.registering {
    val guardFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/UniversalRuntimeGuards.kt",
    )
    val serviceFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt",
    )
    val testFile = layout.projectDirectory.file(
        "src/test/java/br/com/mapeiaia/rotacerta/UniversalRuntimeGuardsTest.kt",
    )

    inputs.files(guardFile, serviceFile, testFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universal99CardContinuation111"))

    doLast {
        val guard = guardFile.asFile
        val service = serviceFile.asFile
        val test = testFile.asFile
        listOf(guard, service, test).forEach { file ->
            if (!file.exists()) throw GradleException("Arquivo ausente: ${file.path}")
        }

        var guardText = guard.readText()
        if ("universal_ride_card_evidence_0_1_112" !in guardText) {
            val anchor = "/** Mantem o historico com uma entrada util por decisao, sem dezenas de copias. */"
            if (anchor !in guardText) throw GradleException("Ponto da politica de evidencia ausente")
            val policy = """data class UniversalRideCardEvidenceDecision(
    val accepted: Boolean,
    val score: Int,
    val reason: String,
)

/**
 * Impede que listas de enderecos, mapas, documentos e fotos de produtos sejam
 * tratadas como ofertas de corrida. A verificacao acontece antes de qualquer
 * geocodificacao ou chamada de rota.
 */
object UniversalRideCardEvidencePolicy {
    private val knownRidePackages = setOf(
        "com.app99.driver",
        "com.ubercab.driver",
        "sinet.startup.indriver",
    )
    private val timeTokenRegex = Regex(
        "\\b\\d{1,3}\\s*(?:min|minutos?)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val tripDistanceRegex = Regex(
        "\\b\\d+(?:[,.]\\d+)?\\s*(?:km|m)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val moneyRegex = Regex(
        "R\\$\\s*\\d+(?:[,.]\\d{1,2})?",
        RegexOption.IGNORE_CASE,
    )
    private val perKmRegex = Regex(
        "R\\$\\s*\\d+(?:[,.]\\d{1,2})?\\s*/\\s*km",
        RegexOption.IGNORE_CASE,
    )
    private val rideMarkerRegex = Regex(
        "\\b(?:corridas?|perfil\\s+(?:essencial|premium)|[aá]rea\\s+de\\s+risco|tarifa(?:\\s+base)?|aceitar|ofere[cç]a|pedido\\s+de\\s+viagem|uberx|comfort|99pop|din[aâ]mic[ao])\\b",
        RegexOption.IGNORE_CASE,
    )
    private val malformedStreetRegex = Regex(
        "\\b(?:rua|avenida)[.:;,]+\\s*\\p{L}",
        RegexOption.IGNORE_CASE,
    )

    fun evaluate(
        text: String,
        addresses: List<String>,
        destination: String?,
        packageName: String?,
    ): UniversalRideCardEvidenceDecision {
        val normalizedAddresses = addresses
            .map { address -> address.trim() }
            .filter { address -> address.isNotBlank() }
        if (normalizedAddresses.size < 2 || destination.isNullOrBlank()) {
            return UniversalRideCardEvidenceDecision(false, 0, "menos_de_dois_enderecos")
        }
        if (normalizedAddresses.any(malformedStreetRegex::containsMatchIn) ||
            malformedStreetRegex.containsMatchIn(destination)
        ) {
            return UniversalRideCardEvidenceDecision(false, 0, "logradouro_deformado")
        }

        val hasTime = timeTokenRegex.containsMatchIn(text)
        val hasTripDistance = tripDistanceRegex.containsMatchIn(text)
        val hasMoney = moneyRegex.containsMatchIn(text)
        val hasPerKm = perKmRegex.containsMatchIn(text)
        val markerCount = rideMarkerRegex.findAll(text).map { it.value.lowercase() }.distinct().count()
        val hasTripMetrics = hasTime && hasTripDistance
        val normalizedPackage = packageName?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val knownRideApp = normalizedPackage != null && normalizedPackage in knownRidePackages

        val score = (if (hasTripMetrics) 2 else 0) +
            (if (hasMoney) 1 else 0) +
            (if (hasPerKm) 1 else 0) +
            (if (markerCount > 0) 1 else 0)

        val acceptedInRideApp = knownRideApp &&
            (hasTripMetrics || hasMoney || hasPerKm || markerCount > 0)
        val acceptedInExternalViewer =
            (hasTripMetrics && (hasMoney || markerCount > 0)) ||
                (hasPerKm && (hasTime || markerCount > 0)) ||
                (hasMoney && markerCount >= 2)
        val accepted = acceptedInRideApp || acceptedInExternalViewer
        return UniversalRideCardEvidenceDecision(
            accepted = accepted,
            score = score,
            reason = if (accepted) "card_de_corrida_confirmado" else "sem_evidencia_de_corrida",
        )
    }
} // universal_ride_card_evidence_0_1_112

"""
            guardText = guardText.replaceFirst(anchor, policy + anchor)
            guard.writeText(guardText)
        }

        var serviceText = service.readText()
        if ("universal_ride_evidence_gate_0_1_112" !in serviceText) {
            val triggerStart = serviceText.indexOf(
                "        val trigger = UniversalAddressTrigger.evaluate(snapshotText)",
            )
            val liveSourceStart = if (triggerStart >= 0) {
                serviceText.indexOf("        val liveSource = when (source)", triggerStart)
            } else {
                -1
            }
            if (triggerStart < 0 || liveSourceStart <= triggerStart) {
                throw GradleException("Intervalo do gatilho universal nao encontrado")
            }
            val evidenceBlock = """        val rideEvidence = UniversalRideCardEvidencePolicy.evaluate(
            text = snapshotText,
            addresses = trigger.addresses,
            destination = trigger.destination,
            packageName = universalResolvedForegroundPackage(),
        )
        if (trigger.addresses.size >= 2 && !rideEvidence.accepted) {
            traceEvent(
                "universal.card.evidence accepted=false score=${'$'}{rideEvidence.score} reason=${'$'}{rideEvidence.reason}",
            )
        }
"""
            serviceText = serviceText.substring(0, liveSourceStart) +
                evidenceBlock +
                serviceText.substring(liveSourceStart)

            val activeRegex = Regex(
                "(?m)^ {8}val activeTrigger = trigger\\.active && !trigger\\.destination\\.isNullOrBlank\\(\\)\\s*$",
            )
            val activeMatch = activeRegex.find(serviceText)
                ?: throw GradleException("Ativacao do gatilho universal nao encontrada")
            val activeReplacement =
                "        val activeTrigger = trigger.active && !trigger.destination.isNullOrBlank() && rideEvidence.accepted // universal_ride_evidence_gate_0_1_112"
            serviceText = serviceText.replaceRange(activeMatch.range, activeReplacement)
            service.writeText(serviceText)
        }

        var testText = test.readText()
        if ("ordinaryProductPhotoWithAddressesNeverBecomesRideCard" !in testText) {
            val addition = """

    @Test
    fun ordinaryProductPhotoWithAddressesNeverBecomesRideCard() {
        val decision = UniversalRideCardEvidencePolicy.evaluate(
            text = ${'"'}${'"'}${'"'}
                MPR-2012
                SISTEMA OPERACIONAL: ANDROID
                PROJETOR MULTIMIDIA 4000 LUMENS
                POTENCIA TOTAL: 110W
                R${'$'}620.00
            ${'"'}${'"'}${'"'}.trimIndent(),
            addresses = listOf(
                "Rua Baltazar Vidal 95",
                "Rua Coelho Lisboa, 419",
                "Rua Agave Dragao 81",
                "Rua Azevedo Soares, 1500",
                "Rua.luli",
            ),
            destination = "Rua.luli",
            packageName = "com.google.android.apps.nbu.files",
        )

        assertFalse(decision.accepted)
        assertEquals("logradouro_deformado", decision.reason)
    }

    @Test
    fun addressListWithoutRideOfferEvidenceIsRejected() {
        val decision = UniversalRideCardEvidencePolicy.evaluate(
            text = ${'"'}${'"'}${'"'}
                Rua Baltazar Vidal 95
                Rua Coelho Lisboa, 419
                Rua Agave Dragao 81
                Rua Azevedo Soares, 1500
            ${'"'}${'"'}${'"'}.trimIndent(),
            addresses = listOf("Rua Baltazar Vidal 95", "Rua Azevedo Soares, 1500"),
            destination = "Rua Azevedo Soares, 1500",
            packageName = "com.google.android.apps.nbu.files",
        )

        assertFalse(decision.accepted)
        assertEquals("sem_evidencia_de_corrida", decision.reason)
    }

    @Test
    fun real99ScreenshotInImageViewerHasStrongRideEvidence() {
        val decision = UniversalRideCardEvidencePolicy.evaluate(
            text = ${'"'}${'"'}${'"'}
                Dinheiro
                R${'$'}8,76
                R${'$'}2,08/km
                4,76 493 corridas
                Perfil Essencial
                9min (1,3km) Area de risco
                Yogui Stilo e Sports, Avenida Mateo Bei, 2651 - Cidade Sao Mateus
                9min (2,9km)
                Condominio Parque Residencial Santa Barbara, Cidade Satelite San
            ${'"'}${'"'}${'"'}.trimIndent(),
            addresses = listOf(
                "Avenida Mateo Bei, 2651 - Cidade Sao Mateus",
                "Condominio Parque Residencial Santa Barbara, Cidade Satelite San",
            ),
            destination = "Condominio Parque Residencial Santa Barbara, Cidade Satelite San",
            packageName = "com.google.android.apps.nbu.files",
        )

        assertTrue(decision.accepted)
        assertTrue(decision.score >= 3)
    }

    @Test
    fun knownRideAppKeepsFastFallbackWithPartialOcr() {
        val decision = UniversalRideCardEvidencePolicy.evaluate(
            text = ${'"'}${'"'}${'"'}
                R${'$'}18,50
                Rua A, 10
                Rua B, 20
            ${'"'}${'"'}${'"'}.trimIndent(),
            addresses = listOf("Rua A, 10", "Rua B, 20"),
            destination = "Rua B, 20",
            packageName = "com.app99.driver",
        )

        assertTrue(decision.accepted)
    }

    @Test
    fun malformedStreetIsRejectedEvenWithRideMetrics() {
        val decision = UniversalRideCardEvidencePolicy.evaluate(
            text = "R${'$'}20,00 8min 3,2km Perfil Premium Rua A, 10 Rua.luli",
            addresses = listOf("Rua A, 10", "Rua.luli"),
            destination = "Rua.luli",
            packageName = "com.app99.driver",
        )

        assertFalse(decision.accepted)
        assertEquals("logradouro_deformado", decision.reason)
    }
"""
            testText = rc112InsertBeforeLastBrace(
                source = testText,
                addition = addition,
                label = "testes da evidencia de corrida",
            )
            test.writeText(testText)
        }

        listOf(
            "universal_ride_card_evidence_0_1_112",
            "universal_ride_evidence_gate_0_1_112",
            "ordinaryProductPhotoWithAddressesNeverBecomesRideCard",
            "real99ScreenshotInImageViewerHasStrongRideEvidence",
            "malformedStreetIsRejectedEvenWithRideMetrics",
        ).forEach { marker ->
            val present = marker in guard.readText() || marker in service.readText() || marker in test.readText()
            if (!present) throw GradleException("Protecao contra falso card incompleta: $marker")
        }
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalRideCardEvidence112)
}
