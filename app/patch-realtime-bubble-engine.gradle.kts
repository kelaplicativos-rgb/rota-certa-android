val patchRealtimeBubbleEngine by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        text = text.replace("const val SCAN_LOOP_MS = 850L", "const val SCAN_LOOP_MS = 180L")
        text = text.replace("const val SCREENSHOT_INTERVAL_MS = 650L", "const val SCREENSHOT_INTERVAL_MS = 420L")

        text = text.replace(
"""    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {
        if (!serviceReady || (!allowPopupCandidate && !shouldScanCurrentWindow())) return
        if (analyzing) {
            traceEvent("accessibility.schedule skipped analyzing=true")
            return
        }
        if (analyzeJob?.isActive == true) {
            traceEvent("accessibility.schedule skipped active_job=true")
            return
        }
        traceEvent("accessibility.schedule delay=${'$'}{delayMs}ms")
        analyzeJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            val visibleText = collectVisibleText(allowPopupCandidate)
            traceEvent("accessibility.collect length=${'$'}{visibleText.length}")
            processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate)
        }
    }
""",
"""    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {
        if (!serviceReady || (!allowPopupCandidate && !shouldScanCurrentWindow())) return
        analyzeJob?.cancel()
        traceEvent("accessibility.schedule delay=${'$'}{delayMs}ms supersede=true")
        analyzeJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            val visibleText = collectVisibleText(allowPopupCandidate)
            traceEvent("accessibility.collect length=${'$'}{visibleText.length}")
            processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate)
        }
    }
""",
        )

        text = text.replace("scheduleVisibleTextAnalysis(delayMs = 80L", "scheduleVisibleTextAnalysis(delayMs = 0L")

        text = text.replace(
"""        if (snapshotHash != lastSnapshotHash) {
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            showOverlay(RadarColor.Default)
            recordDiagnostic(
                stage = "screen_changed",
                reason = "A imagem/texto da tela mudou; aguardando confirmar o card cadastrado.",
                text = snapshotText,
            )
        }
""",
"""        if (snapshotHash != lastSnapshotHash) {
            val previousSnapshotHash = lastSnapshotHash
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            pendingAnalysis = null
            registeredCardGate.clear()
            if (previousSnapshotHash != null) {
                traceEvent("screen.supersede previous=${'$'}previousSnapshotHash current=${'$'}snapshotHash")
            }
            showOverlay(RadarColor.Default)
            recordDiagnostic(
                stage = "screen_changed",
                reason = "A imagem/texto da tela mudou; limpei a bolinha antes de confirmar o proximo card cadastrado.",
                text = snapshotText,
            )
        }
""",
        )

        text = text.replace(
"""            repository.addAnalysis(result)
            lastSavedReadHash = snapshotHash
            if (!allowPopupCandidate && !shouldScanCurrentWindow()) {
""",
"""            repository.addAnalysis(result)
            lastSavedReadHash = snapshotHash
            if (!allowPopupCandidate && lastSnapshotHash != snapshotHash) {
                registeredCardGate.clear()
                resetToDefault(
                    reason = "A tela mudou durante o calculo; descartei a decisao antiga antes de mostrar cor/km.",
                    record = false,
                )
                recordDiagnostic(
                    stage = "analysis_superseded",
                    reason = "A tela mudou durante o calculo; resultado antigo nao foi aplicado na bolinha.",
                    text = text,
                    fields = fields,
                    result = result,
                    cardTemplateMatch = cardMatch,
                )
                return
            }
            if (!allowPopupCandidate && !shouldScanCurrentWindow()) {
""",
        )

        text = text.replace(
"""            if (pending != null && pending.snapshotHash != lastAnalyzedHash && shouldScanCurrentWindow()) {
""",
"""            if (pending != null && pending.snapshotHash != lastAnalyzedHash && pending.snapshotHash == lastSnapshotHash && shouldScanCurrentWindow()) {
""",
        )

        if (text != original) {
            file.writeText(text)
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchRealtimeBubbleEngine)
}
