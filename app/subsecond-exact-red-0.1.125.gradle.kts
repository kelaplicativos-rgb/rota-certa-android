// Rota Certa 0.1.125
// Aplica vermelho imediatamente quando a distancia geodesica minima ja supera
// todos os raios configurados. A regra e conservadora: nunca libera verde por
// linha reta e nunca exibe quilometro estimado.

fun patchSubsecondExactRed125(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para o vermelho instantaneo 0.1.125.")
    var text = file.readText()
    if ("subsecond_exact_red_lower_bound_0_1_125" in text) return

    val anchor = """        ) // session_diagnostic_targets_v2

        val homeRouteStartedAt = System.currentTimeMillis()
"""
    if (anchor !in text) {
        throw GradleException("Ponto de insercao do limite geometrico exato nao encontrado.")
    }

    val replacement = """        ) // session_diagnostic_targets_v2

        val exactLowerBound = ExactRadiusLowerBoundPolicy.evaluate(
            destinationCoordinate = destinationCoordinate,
            settings = settings,
            homeCoordinate = homeCoordinate,
            alternativeCoordinate = alternativeCoordinate,
        )
        if (exactLowerBound.definitelyOutside) {
            val fastOutsideResult = AnalysisResult(
                createdAtMillis = System.currentTimeMillis(),
                extractedText = snapshotText,
                fields = fields,
                recommendation = Recommendation.OutsideRadius,
                reason = "Destino certamente fora dos raios: a distancia minima possivel ja ultrapassa o limite configurado.",
            )
            traceEvent(
                "universal.fast_red exact_lower_bound=true lower_km=${'$'}{exactLowerBound.nearestLowerBoundKm} targets=${'$'}{exactLowerBound.evaluatedTargets}",
            )
            LiveFailureTraceStore.recordStep(
                stage = "route.fast_red",
                details = "exact_lower_bound=true; lower_km=${'$'}{exactLowerBound.nearestLowerBoundKm}; targets=${'$'}{exactLowerBound.evaluatedTargets}",
                packageName = currentWindowPackageName(),
                generation = generation,
                screenHash = screenHash,
            )
            applyUniversalTwoAddressResult(
                fastOutsideResult,
                screenHash,
                addressSignature,
                generation,
            )
            return
        } // subsecond_exact_red_lower_bound_0_1_125

        val homeRouteStartedAt = System.currentTimeMillis()
"""

    text = text.replaceFirst(anchor, replacement)
    listOf(
        "subsecond_exact_red_lower_bound_0_1_125",
        "ExactRadiusLowerBoundPolicy.evaluate(",
        "universal.fast_red exact_lower_bound=true",
        "stage = \"route.fast_red\"",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Contrato do vermelho instantaneo incompleto: ${'$'}marker")
    }
    file.writeText(text)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchSubsecondExactRed125(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
