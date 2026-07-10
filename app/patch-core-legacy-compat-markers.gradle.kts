// Compatibilidade entre patches legados e o Rota Certa Core.
// Alguns patches antigos exigem marcadores textuais para nao tentar reinstalar
// travas que agora foram assumidas pelo CoreBubbleStateController/CoreBubbleDecisionEngine.

val coreLegacyCompatMarkers by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text

        if ("core_legacy_compat_markers_0_1_91" !in text) {
            val anchor = "    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {\n"
            if (anchor !in text) {
                throw org.gradle.api.GradleException("Nao encontrei showOverlay para instalar compatibilidade legada do Core.")
            }
            val marker = """    // core_legacy_compat_markers_0_1_91
    // force_missing_card_overlay_default_0_1_80 preservado pelo CoreBubbleStateController.
    // preserve_valid_decision_0_1_84 preservado pelo CoreBubbleDecisionEngine.
"""
            text = text.replace(anchor, marker + anchor)
        }

        if ("force_missing_card_overlay_default_0_1_80" !in text) {
            throw org.gradle.api.GradleException("Marcador de compatibilidade de card ausente.")
        }
        if ("preserve_valid_decision_0_1_84" !in text) {
            throw org.gradle.api.GradleException("Marcador de preservacao transitoria ausente.")
        }

        if (text != original) file.writeText(text)
    }
}

coreLegacyCompatMarkers.configure {
    mustRunAfter(
        "patchLiveRideOverlayStability",
        "patchBubbleStateReport",
        "liveRideWindowEventGuard",
        "keepDecisionDuringTransientText",
        "hardClearUnregisteredCardDecision",
        "modularLiveBubbleCore",
    )
}

tasks.named("noStickyDecisionCleanup").configure {
    dependsOn(coreLegacyCompatMarkers)
}
