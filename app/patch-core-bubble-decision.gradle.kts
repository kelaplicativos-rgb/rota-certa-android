// Direciona a cor/km da bolinha pelo CoreBubbleDecisionEngine.
// A bolinha deixa de decidir verde/vermelho/amarelo diretamente pelo AnalysisResult.

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
            val oldBlock = """            lastAnalyzedHash = lastSnapshotHash ?: snapshotHash
            val radarColor = when (result.recommendation) {
                Recommendation.GoodRide -> RadarColor.Green
                Recommendation.OutsideRadius -> RadarColor.Red
                Recommendation.InsufficientData -> RadarColor.Default
            }
            traceEvent("overlay.apply color=${dollar}{radarColor.diagnosticLabel} distance=${dollar}{result.nearestConfiguredDistanceKm()?.let(::formatDiagnosticKm) ?: "null"}")
            showOverlay(color = radarColor, distanceKm = result.nearestConfiguredDistanceKm())
"""
            val newBlock = """            lastAnalyzedHash = lastSnapshotHash ?: snapshotHash
            val coreClassificationForBubble = br.com.mapeiaia.rotacerta.core.RotaCertaCore.classifyScreen(
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
            if (oldBlock !in text) {
                throw org.gradle.api.GradleException("Nao encontrei o bloco de decisao visual antigo da bolinha.")
            }
            text = text.replace(oldBlock, newBlock)
        }

        if ("core_bubble_decision_0_1_87" !in text) {
            throw org.gradle.api.GradleException("CoreBubbleDecisionEngine nao foi conectado na bolinha.")
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
