val hardClearUnregisteredCardDecision by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        serviceFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            if ("hard_clear_missing_card_0_1_77" !in text) {
                text = text.replace(
"""            registeredCardGate.clear()
            saveCapturedCardScreen(snapshotText, fields, snapshotHash, parseResult.parserName, packageName)
""",
"""            registeredCardGate.clear()
            lastDecisionOverlayAtMillis = 0L // hard_clear_missing_card_0_1_77
            saveCapturedCardScreen(snapshotText, fields, snapshotHash, parseResult.parserName, packageName)
""",
                )
            }

            if ("hardClearUnregisteredCardDefault" !in text) {
                text = text.replace(
"""        rememberBubbleReason("default", reason)
        showOverlay(RadarColor.Default)
""",
"""        val hardClearUnregisteredCardDefault = reason.contains("ainda nao bate com nenhum card cadastrado", ignoreCase = true) ||
            reason.contains("cadastre o modelo para liberar o farol", ignoreCase = true)
        if (hardClearUnregisteredCardDefault) {
            lastDecisionOverlayAtMillis = 0L
            registeredCardGate.clear()
        }
        rememberBubbleReason("default", reason)
        showOverlay(RadarColor.Default)
""",
                )
            }

            if ("hard_clear_overlay_now_0_1_77" !in text) {
                text = text.replace(
"""        val now = System.currentTimeMillis()
""",
"""        val now = System.currentTimeMillis()
        if (color == RadarColor.Default && (
                lastBubbleStateReason.contains("ainda nao bate com nenhum card cadastrado", ignoreCase = true) ||
                    lastBubbleStateReason.contains("cadastre o modelo para liberar o farol", ignoreCase = true)
            )
        ) {
            lastDecisionOverlayAtMillis = 0L // hard_clear_overlay_now_0_1_77
            registeredCardGate.clear()
        }
""",
                )
            }

            if ("missingRegisteredCardDecision" !in text) {
                text = text.replace(
"""            val keepActiveDecisionForTransientInsufficient = computedRadarColor == RadarColor.Default &&
                hasActiveRegisteredDecision() &&
                shouldScanCurrentWindow()
""",
"""            val missingRegisteredCardDecision = cardMatch == null ||
                result.reason.contains("ainda nao bate com nenhum card cadastrado", ignoreCase = true) ||
                result.reason.contains("cadastre o modelo para liberar o farol", ignoreCase = true)
            if (missingRegisteredCardDecision) {
                registeredCardGate.clear()
                lastDecisionOverlayAtMillis = 0L
            }
            val keepActiveDecisionForTransientInsufficient = computedRadarColor == RadarColor.Default &&
                !missingRegisteredCardDecision &&
                hasActiveRegisteredDecision() &&
                shouldScanCurrentWindow()
""",
                )
                text = text.replace(
"""                val radarColor = computedRadarColor
                traceEvent("overlay.apply color=${'$'}{radarColor.diagnosticLabel} distance=${'$'}{result.nearestConfiguredDistanceKm()?.toString() ?: "null"}")
                showOverlay(color = radarColor, distanceKm = result.nearestConfiguredDistanceKm())
""",
"""                val radarColor = if (missingRegisteredCardDecision) RadarColor.Default else computedRadarColor
                val displayedDistanceKm = if (missingRegisteredCardDecision) null else result.nearestConfiguredDistanceKm()
                traceEvent("overlay.apply color=${'$'}{radarColor.diagnosticLabel} distance=${'$'}{displayedDistanceKm?.toString() ?: "null"}")
                showOverlay(color = radarColor, distanceKm = displayedDistanceKm)
""",
                )
            }

            if ("hard_clear_missing_card_0_1_77" !in text) {
                throw org.gradle.api.GradleException("Nao consegui instalar limpeza forte para card nao cadastrado.")
            }
            if ("hard_clear_overlay_now_0_1_77" !in text) {
                throw org.gradle.api.GradleException("Nao consegui instalar limpeza da protecao sticky para card nao cadastrado.")
            }

            if (text != original) file.writeText(text)
        }
    }
}

hardClearUnregisteredCardDecision.configure {
    mustRunAfter("keepDecisionDuringTransientText")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(hardClearUnregisteredCardDecision)
}
