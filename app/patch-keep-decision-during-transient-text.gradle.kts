val keepDecisionDuringTransientText by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        serviceFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            if ("screen_changed.keep_active_decision" !in text) {
                text = text.replace(
                    Regex(
                        """        if \(snapshotHash != lastSnapshotHash\) \{\n            lastSnapshotHash = snapshotHash\n            lastAnalyzedHash = null\n            showOverlay\(RadarColor\.Default\)\n            recordDiagnostic\(\n                stage = "screen_changed",\n                reason = "[^"]*",\n                text = snapshotText,\n            \)\n        \}\n""",
                    ),
                    """        if (snapshotHash != lastSnapshotHash) {
            val keepActiveDecisionDuringTransientText = hasActiveRegisteredDecision() &&
                shouldScanCurrentWindow() &&
                (source == TextSource.Accessibility || lastOcrText.isNotBlank())
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            if (keepActiveDecisionDuringTransientText) {
                traceEvent("screen_changed.keep_active_decision source=${'$'}source hash=${'$'}snapshotHash")
            } else {
                showOverlay(RadarColor.Default)
            }
            recordDiagnostic(
                stage = "screen_changed",
                reason = if (keepActiveDecisionDuringTransientText) {
                    "Tela mudou, mas mantive a decisao atual ate confirmar novo card cadastrado."
                } else {
                    "Tela mudou; aguardando confirmar card cadastrado sem manter km antigo."
                },
                text = snapshotText,
            )
        }
""",
                )
            }

            if (text != original) file.writeText(text)
        }
    }
}

keepDecisionDuringTransientText.configure {
    mustRunAfter("patchBubbleStateReport", "patchLiveRideOverlayStability", "liveRideWindowEventGuard")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(keepDecisionDuringTransientText)
}
