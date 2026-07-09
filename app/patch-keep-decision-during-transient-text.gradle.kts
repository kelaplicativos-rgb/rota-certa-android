val keepDecisionDuringTransientText by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        serviceFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            if ("screen_changed.keep_active_decision" !in text) {
                val screenStart = text.indexOf("        if (snapshotHash != lastSnapshotHash) {\n")
                val screenEnd = if (screenStart >= 0) {
                    text.indexOf("\n\n        val parseResult = parser.parseWithMetadata", screenStart)
                } else {
                    -1
                }
                if (screenStart >= 0 && screenEnd > screenStart) {
                    val replacement = """        if (snapshotHash != lastSnapshotHash) {
            val keepActiveDecisionDuringTransientText = hasActiveRegisteredDecision() && shouldScanCurrentWindow()
            lastSnapshotHash = snapshotHash
            if (!keepActiveDecisionDuringTransientText) {
                lastAnalyzedHash = null
                showOverlay(RadarColor.Default)
            } else {
                traceEvent("screen_changed.keep_active_decision source=${'$'}source hash=${'$'}snapshotHash accLen=${'$'}{lastAccessibilityText.length} ocrLen=${'$'}{lastOcrText.length}")
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
"""
                    text = text.substring(0, screenStart) + replacement + text.substring(screenEnd)
                }
            }

            if ("process.empty_text keep_active_decision=true" !in text) {
                val emptyStart = text.indexOf("        if (snapshotText.isBlank()) {\n")
                val emptyEnd = if (emptyStart >= 0) {
                    text.indexOf("\n\n        val snapshotHash = snapshotText.snapshotHash()", emptyStart)
                } else {
                    -1
                }
                if (emptyStart >= 0 && emptyEnd > emptyStart) {
                    val replacement = """        if (snapshotText.isBlank()) {
            traceEvent("process.empty_text source=${'$'}source")
            if (allowPopupCandidate) return
            if (hasActiveRegisteredDecision() && shouldScanCurrentWindow()) {
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
            return
        }
"""
                    text = text.substring(0, emptyStart) + replacement + text.substring(emptyEnd)
                }
            }

            if ("screen_changed.keep_active_decision" !in text) {
                throw org.gradle.api.GradleException("Nao consegui instalar a protecao de decisao em screen_changed.")
            }
            if ("process.empty_text keep_active_decision=true" !in text) {
                throw org.gradle.api.GradleException("Nao consegui instalar a protecao de texto vazio transitorio.")
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
