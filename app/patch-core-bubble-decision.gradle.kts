// Liga efetivamente a cor/km final da bolinha ao CoreBubbleDecisionEngine.
// Este corte mira o formato real gerado por keepDecisionDuringTransientText:
// computedRadarColor + protecao de analise insuficiente transitoria.

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

        if ("core_bubble_decision_0_1_88" !in text) {
            val computedStart = text.indexOf("            val computedRadarColor = when (result.recommendation) {\n")
            val catchStart = if (computedStart >= 0) text.indexOf("\n        } catch (error: Exception) {", computedStart) else -1
            if (computedStart >= 0 && catchStart > computedStart) {
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
            val computedRadarColor = when (coreBubbleDecision.mode) {
                br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Good -> RadarColor.Green
                br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Bad -> RadarColor.Red
                br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Waiting -> RadarColor.Default
                br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Hidden -> RadarColor.Idle
            }
            val keepActiveDecisionForTransientInsufficient = computedRadarColor == RadarColor.Default &&
                hasActiveRegisteredDecision() &&
                shouldScanCurrentWindow()
            if (keepActiveDecisionForTransientInsufficient) {
                traceEvent("core.bubble transient_keep mode=${dollar}{coreBubbleDecision.mode} hash=${dollar}snapshotHash reason=${dollar}{coreBubbleDecision.reason}") // core_bubble_decision_0_1_88
                recordDiagnostic(
                    stage = "analysis_result",
                    reason = "Core classificou leitura transitoria/insuficiente dentro do app monitorado; mantive a decisao verde/vermelha anterior ate confirmar novo card real.",
                    text = text,
                    fields = fields,
                    result = result,
                    cardTemplateMatch = cardMatch,
                )
            } else {
                val radarColor = computedRadarColor
                traceEvent("core.bubble apply mode=${dollar}{coreBubbleDecision.mode} color=${dollar}{radarColor.diagnosticLabel} distance=${dollar}{coreBubbleDecision.distanceKm?.toString() ?: "null"} reason=${dollar}{coreBubbleDecision.reason}") // core_bubble_decision_0_1_88
                showOverlay(color = radarColor, distanceKm = coreBubbleDecision.distanceKm)
                recordDiagnostic(
                    stage = "analysis_result",
                    color = radarColor,
                    reason = coreBubbleDecision.reason,
                    text = text,
                    fields = fields,
                    result = result,
                    cardTemplateMatch = cardMatch,
                )
            }
"""
                text = text.substring(0, computedStart) + newBlock + text.substring(catchStart)
            } else {
                val radarStart = text.indexOf("            val radarColor = when (result.recommendation) {\n")
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
            traceEvent("core.bubble apply mode=${dollar}{coreBubbleDecision.mode} color=${dollar}{radarColor.diagnosticLabel} distance=${dollar}{coreBubbleDecision.distanceKm?.toString() ?: "null"} reason=${dollar}{coreBubbleDecision.reason}") // core_bubble_decision_0_1_88
            showOverlay(color = radarColor, distanceKm = coreBubbleDecision.distanceKm)
"""
                    text = text.substring(0, radarStart) + newBlock + text.substring(replaceEnd)
                }
            }
        }

        if ("core_bubble_decision_0_1_88" !in text) {
            throw org.gradle.api.GradleException("CoreBubbleDecisionEngine nao assumiu a aplicacao final de cor/km.")
        }

        if (text != original) file.writeText(text)
    }
}

coreBubbleDecisionPatch.configure {
    mustRunAfter(
        "keepDecisionDuringTransientText",
        "noStickyDecisionCleanup",
        "rotaCertaCoreGate",
        "liveResultFreshnessGuard",
        "inDriveCardContractMatch"
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(coreBubbleDecisionPatch)
}
