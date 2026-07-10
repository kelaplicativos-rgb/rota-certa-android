val noStickyDecisionCleanup by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        serviceFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text
            val dollar = "$"

            text = text.replace(
"""        if ((color == RadarColor.Default || color == RadarColor.Idle) &&
            hasActiveRegisteredDecision() &&
            shouldScanCurrentWindow() &&
            now - lastDecisionOverlayAtMillis < DECISION_OVERLAY_STICKY_MS
        ) {
            traceEvent("overlay.keep_decision color=${dollar}{currentRadarColor.diagnosticLabel} requested=${dollar}{color.diagnosticLabel}")
            return
        }
""",
"""        if ((color == RadarColor.Default || color == RadarColor.Idle) &&
            hasActiveRegisteredDecision() &&
            shouldScanCurrentWindow() &&
            now - lastDecisionOverlayAtMillis < DECISION_OVERLAY_STICKY_MS
        ) {
            traceEvent("overlay.keep_valid_decision_transient requested=${dollar}{color.diagnosticLabel} previous=${dollar}{currentRadarColor.diagnosticLabel}") // preserve_valid_decision_0_1_84
            return
        }
""",
            )
            text = text.replace(
"""        if ((color == RadarColor.Default || color == RadarColor.Idle) &&
            hasActiveRegisteredDecision() &&
            shouldScanCurrentWindow() &&
            now - lastDecisionOverlayAtMillis < DECISION_OVERLAY_STICKY_MS
        ) {
            traceEvent("overlay.clear_previous_decision requested=${dollar}{color.diagnosticLabel} previous=${dollar}{currentRadarColor.diagnosticLabel}") // no_sticky_decision_cleanup_0_1_79
            registeredCardGate.clear()
            lastDecisionOverlayAtMillis = 0L
        }
""",
"""        if ((color == RadarColor.Default || color == RadarColor.Idle) &&
            hasActiveRegisteredDecision() &&
            shouldScanCurrentWindow() &&
            now - lastDecisionOverlayAtMillis < DECISION_OVERLAY_STICKY_MS
        ) {
            traceEvent("overlay.keep_valid_decision_transient requested=${dollar}{color.diagnosticLabel} previous=${dollar}{currentRadarColor.diagnosticLabel}") // preserve_valid_decision_0_1_84
            return
        }
""",
            )

            if ("forceMissingCardOverlayDefault" !in text) {
                text = text.replace(
"""        if (color == RadarColor.Green || color == RadarColor.Red) lastDecisionOverlayAtMillis = now
        val nextText = formatBubbleDistanceKm(distanceKm)
        if (currentRadarColor == color && currentDistanceKm == distanceKm && overlayView?.text?.toString() == nextText) return
        currentRadarColor = color
        currentDistanceKm = distanceKm
""",
"""        val forceMissingCardOverlayDefault = lastBubbleStateReason.contains("ainda nao bate com nenhum card cadastrado", ignoreCase = true) ||
            lastBubbleStateReason.contains("cadastre o modelo para liberar o farol", ignoreCase = true) ||
            lastBubbleStateReason.contains("tela nao confirmada por card cadastrado", ignoreCase = true)
        val safeColor = if (forceMissingCardOverlayDefault) RadarColor.Default else color
        val safeDistanceKm = if (forceMissingCardOverlayDefault) null else distanceKm
        if (forceMissingCardOverlayDefault) {
            traceEvent("overlay.force_missing_card_default requested=${dollar}{color.diagnosticLabel} previous=${dollar}{currentRadarColor.diagnosticLabel}") // force_missing_card_overlay_default_0_1_80
            registeredCardGate.clear()
            lastDecisionOverlayAtMillis = 0L
        }
        if (safeColor == RadarColor.Green || safeColor == RadarColor.Red) lastDecisionOverlayAtMillis = now
        val nextText = formatBubbleDistanceKm(safeDistanceKm)
        if (currentRadarColor == safeColor && currentDistanceKm == safeDistanceKm && overlayView?.text?.toString() == nextText) return
        currentRadarColor = safeColor
        currentDistanceKm = safeDistanceKm
""",
                )
            }

            text = text.replace(
                "val keepActiveDecisionDuringTransientText = false // no_sticky_decision_cleanup_0_1_79",
                "val keepActiveDecisionDuringTransientText = hasActiveRegisteredDecision() && shouldScanCurrentWindow() // preserve_valid_decision_0_1_84",
            )
            text = text.replace(
                Regex("""val keepActiveDecisionForTransientInsufficient = false // no_sticky_decision_cleanup_0_1_79"""),
                "val keepActiveDecisionForTransientInsufficient = computedRadarColor == RadarColor.Default && !missingRegisteredCardDecision && hasActiveRegisteredDecision() && shouldScanCurrentWindow() // preserve_valid_decision_0_1_84",
            )

            text = text.replace(
"""            registeredCardGate.clear()
            lastDecisionOverlayAtMillis = 0L // no_sticky_decision_cleanup_0_1_79
            resetToDefault(reason = "Texto visivel vazio; nenhum card lido neste momento.", record = true)
""",
"""            if (hasActiveRegisteredDecision() && shouldScanCurrentWindow()) {
                traceEvent("process.empty_text keep_valid_decision_transient=true") // preserve_valid_decision_0_1_84
                recordDiagnostic(
                    stage = "screen_changed",
                    reason = "Texto visivel ficou vazio por instantes; mantive a decisao atual ate confirmar saida real do card.",
                    text = null,
                )
                return
            }
            registeredCardGate.clear()
            lastDecisionOverlayAtMillis = 0L
            resetToDefault(reason = "Texto visivel vazio; nenhum card lido neste momento.", record = true)
""",
            )

            text = text.replace(
"""        if (shouldScanCurrentWindow() && hasActiveRegisteredDecision()) {
            traceEvent("resetToIdle clear_active_ride_window reason=${dollar}reason") // no_sticky_decision_cleanup_0_1_79
        }
""",
"""        if (shouldScanCurrentWindow() && hasActiveRegisteredDecision()) {
            traceEvent("resetToIdle keep_valid_decision_guard reason=${dollar}reason") // preserve_valid_decision_0_1_84
            return
        }
""",
            )

            text = text.replace(
                "Pacote passivo ignorado sem apagar a ultima decisao:",
                "Pacote passivo ignorado; bolinha limpa:",
            )

            if ("force_missing_card_overlay_default_0_1_80" !in text) {
                throw org.gradle.api.GradleException("Nao consegui instalar a trava final para card nao cadastrado no overlay.")
            }
            if ("preserve_valid_decision_0_1_84" !in text) {
                throw org.gradle.api.GradleException("Nao consegui reativar a preservacao curta de decisao valida.")
            }
            if ("overlay.clear_previous_decision" in text) {
                throw org.gradle.api.GradleException("A bolinha ainda contem limpeza agressiva de decisao valida.")
            }

            if (text != original) file.writeText(text)
        }
    }
}

noStickyDecisionCleanup.configure {
    mustRunAfter(
        "patchLiveRideOverlayStability",
        "patchBubbleStateReport",
        "liveRideWindowEventGuard",
        "keepDecisionDuringTransientText",
        "hardClearUnregisteredCardDecision",
        "modularLiveBubbleCore",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(noStickyDecisionCleanup)
}
