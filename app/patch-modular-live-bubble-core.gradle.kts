val modularLiveBubbleCore by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        serviceFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            if ("private val bubbleDecisionPolicy = LiveRideBubbleDecisionPolicy()" !in text) {
                text = text.replace(
                    "    private lateinit var decisionEngine: DecisionEngine\n",
                    "    private lateinit var decisionEngine: DecisionEngine\n    private val bubbleDecisionPolicy = LiveRideBubbleDecisionPolicy()\n",
                )
            }

            if ("modular_bubble_policy_0_1_78" !in text && "core_bubble_decision_0_1_88" !in text) {
                text = text.replace(
"""                val radarColor = if (missingRegisteredCardDecision) RadarColor.Default else computedRadarColor
                val displayedDistanceKm = if (missingRegisteredCardDecision) null else result.nearestConfiguredDistanceKm()
                traceEvent("overlay.apply color=${'$'}{radarColor.diagnosticLabel} distance=${'$'}{displayedDistanceKm?.toString() ?: "null"}")
                showOverlay(color = radarColor, distanceKm = displayedDistanceKm)
""",
"""                val bubbleDecision = bubbleDecisionPolicy.decide(
                    LiveRideBubbleDecisionInput(
                        monitoredPackageActive = allowPopupCandidate || shouldScanCurrentWindow(),
                        registeredCardMatched = !missingRegisteredCardDecision,
                        destinationIdentified = !fields.destination.isNullOrBlank(),
                        result = result,
                        nearestConfiguredDistanceKm = result.nearestConfiguredDistanceKm(),
                    ),
                )
                if (bubbleDecision.shouldClearActiveDecision) {
                    registeredCardGate.clear()
                    lastDecisionOverlayAtMillis = 0L
                }
                val radarColor = when (bubbleDecision.signal) {
                    LiveRideBubbleSignal.Accept -> RadarColor.Green
                    LiveRideBubbleSignal.Reject -> RadarColor.Red
                    LiveRideBubbleSignal.Idle -> RadarColor.Idle
                    LiveRideBubbleSignal.WaitingForRegisteredCard,
                    LiveRideBubbleSignal.WaitingForData -> RadarColor.Default
                }
                val displayedDistanceKm = bubbleDecision.distanceKm // modular_bubble_policy_0_1_78
                traceEvent("overlay.apply policy=${'$'}{bubbleDecision.signal} color=${'$'}{radarColor.diagnosticLabel} distance=${'$'}{displayedDistanceKm?.toString() ?: "null"}")
                showOverlay(color = radarColor, distanceKm = displayedDistanceKm)
""",
                )
            }

            if ("private val bubbleDecisionPolicy = LiveRideBubbleDecisionPolicy()" !in text) {
                throw org.gradle.api.GradleException("Nao consegui instalar o modulo central da bolinha no servico.")
            }
            if ("modular_bubble_policy_0_1_78" !in text && "core_bubble_decision_0_1_88" !in text) {
                throw org.gradle.api.GradleException("Nao consegui trocar a aplicacao de cor/km para nenhum modulo central da bolinha.")
            }

            if (text != original) file.writeText(text)
        }
    }
}

modularLiveBubbleCore.configure {
    mustRunAfter("hardClearUnregisteredCardDecision")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(modularLiveBubbleCore)
}
