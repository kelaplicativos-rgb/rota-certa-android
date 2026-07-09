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
            traceEvent("overlay.clear_previous_decision requested=${dollar}{color.diagnosticLabel} previous=${dollar}{currentRadarColor.diagnosticLabel}") // no_sticky_decision_cleanup_0_1_79
            registeredCardGate.clear()
            lastDecisionOverlayAtMillis = 0L
        }
""",
            )

            text = text.replace(
                "val keepActiveDecisionDuringTransientText = hasActiveRegisteredDecision() && shouldScanCurrentWindow()",
                "val keepActiveDecisionDuringTransientText = false // no_sticky_decision_cleanup_0_1_79",
            )
            text = text.replace(
                Regex("""val keepActiveDecisionForTransientInsufficient = computedRadarColor == RadarColor\.Default &&\s+!missingRegisteredCardDecision &&\s+hasActiveRegisteredDecision\(\) &&\s+shouldScanCurrentWindow\(\)"""),
                "val keepActiveDecisionForTransientInsufficient = false // no_sticky_decision_cleanup_0_1_79",
            )
            text = text.replace(
                Regex("""val keepActiveDecisionForTransientInsufficient = computedRadarColor == RadarColor\.Default &&\s+hasActiveRegisteredDecision\(\) &&\s+shouldScanCurrentWindow\(\)"""),
                "val keepActiveDecisionForTransientInsufficient = false // no_sticky_decision_cleanup_0_1_79",
            )

            text = text.replace(
"""            if (hasActiveRegisteredDecision() && shouldScanCurrentWindow()) {
                traceEvent("process.empty_text keep_active_decision=true")
                recordDiagnostic(
                    stage = "screen_changed",
                    reason = "Texto visivel ficou vazio por instantes; mantive a decisao atual ate confirmar saida real do card.",
                    text = null,
                )
                return
            }
            registeredCardGate.clear()
            resetToDefault(reason = "Texto visivel vazio; nenhum card lido neste momento.", record = true)
""",
"""            registeredCardGate.clear()
            lastDecisionOverlayAtMillis = 0L // no_sticky_decision_cleanup_0_1_79
            resetToDefault(reason = "Texto visivel vazio; nenhum card lido neste momento.", record = true)
""",
            )

            text = text.replace(
"""        if (shouldScanCurrentWindow() && hasActiveRegisteredDecision()) {
            traceEvent("resetToIdle guarded active_ride_window reason=${dollar}reason")
            return
        }
""",
"""        if (shouldScanCurrentWindow() && hasActiveRegisteredDecision()) {
            traceEvent("resetToIdle clear_active_ride_window reason=${dollar}reason") // no_sticky_decision_cleanup_0_1_79
        }
""",
            )

            if ("overlay.keep_decision" in text) {
                throw org.gradle.api.GradleException("A bolinha ainda contem preservacao sticky de decisao antiga.")
            }
            if ("process.empty_text keep_active_decision=true" in text) {
                throw org.gradle.api.GradleException("Texto vazio ainda preserva decisao antiga.")
            }
            if ("no_sticky_decision_cleanup_0_1_79" !in text) {
                throw org.gradle.api.GradleException("Limpeza anti-sticky nao foi aplicada.")
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
