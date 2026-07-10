// Prepara a ligacao da cor/km ao CoreBubbleDecisionEngine.
// Nesta etapa, o modulo Core existe e compila. A substituicao visual e aplicada quando o bloco antigo ainda esta presente.
// Se outro patch ja reescreveu o ponto visual, este patch nao bloqueia o build.

val coreBubbleDecisionPatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("core_bubble_decision_0_1_87" !in text) {
            val radarStart = text.indexOf("            val radarColor = when (result.recommendation) {")
            val showOverlayToken = "            showOverlay(color = radarColor, distanceKm = result.nearestConfiguredDistanceKm())\n"
            val showOverlayIndex = if (radarStart >= 0) text.indexOf(showOverlayToken, radarStart) else -1
            if (radarStart >= 0 && showOverlayIndex >= 0) {
                val replaceEnd = showOverlayIndex + showOverlayToken.length
                val newBlock = """            val coreClassificationForBubble = br.com.mapeiaia.rotacerta.core.RotaCertaCore.classifyScreen(
                packageName = currentWindowPackageName(),
                text = text,
                fields = fields,
            )
            val coreBubbleDecision = br.com.mapeiaia.rotacerta.core.CoreBubbleDecisionEngine.fromAnalysis(
                classification = coreClassificationForBubble,
                result = result,
                distanceKm = result.nearestConfiguredDistanceKm(),
            )
            val radarColor = when (coreBubbleDecision.mode) {
                br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Good -> RadarColor.Green
                br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Bad -> RadarColor.Red
                br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Waiting -> RadarColor.Default
                br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Hidden -> RadarColor.Idle
            }
            traceEvent("core.bubble mode=${dollar}{coreBubbleDecision.mode} color=${dollar}{radarColor.diagnosticLabel} distance=${dollar}{coreBubbleDecision.distanceKm?.let(::formatDiagnosticKm) ?: "null"} reason=${dollar}{coreBubbleDecision.reason}") // core_bubble_decision_0_1_87
            showOverlay(color = radarColor, distanceKm = coreBubbleDecision.distanceKm)
"""
                text = text.substring(0, radarStart) + newBlock + text.substring(replaceEnd)
            } else if ("core_bubble_decision_ready_0_1_87" !in text) {
                text = text.replace(
                    "    private fun AnalysisResult.nearestConfiguredDistanceKm(): Double? =\n",
                    "    // core_bubble_decision_ready_0_1_87: CoreBubbleDecisionEngine criado; ligacao visual direta fica para o proximo corte seguro.\n    private fun AnalysisResult.nearestConfiguredDistanceKm(): Double? =\n",
                )
            }
        }

        if ("core_bubble_decision_0_1_87" !in text && "core_bubble_decision_ready_0_1_87" !in text) {
            throw org.gradle.api.GradleException("CoreBubbleDecisionEngine nao ficou rastreavel no servico.")
        }

        if (text != original) file.writeText(text)
    }
}

coreBubbleDecisionPatch.configure {
    mustRunAfter(
        "rotaCertaCoreGate",
        "liveResultFreshnessGuard",
        "inDriveCardContractMatch"
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(coreBubbleDecisionPatch)
}
