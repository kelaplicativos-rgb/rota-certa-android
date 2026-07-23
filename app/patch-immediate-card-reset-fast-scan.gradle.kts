val immediateCardResetFastScan by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val dollar = "$"
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        text = text.replace(
"""        if (analyzing) {
            traceEvent("accessibility.schedule skipped analyzing=true")
            return
        }
        if (analyzeJob?.isActive == true) {
            traceEvent("accessibility.schedule skipped active_job=true")
            return
        }
""",
"""        if (analyzing) {
            traceEvent("accessibility.schedule allowed while_analyzing=true")
        }
        if (analyzeJob?.isActive == true) {
            traceEvent("accessibility.schedule supersede active_job=true")
            analyzeJob?.cancel()
        }
""",
        )

        text = text.replace(
"""        if (snapshotText.isBlank()) {
            traceEvent("process.empty_text source=${dollar}source")
            if (source == TextSource.Accessibility && lastSaveableRideText.isNotBlank()) {
                traceEvent("process.empty_accessibility keep_saveable_card=true package=${dollar}{lastSaveableRidePackageName.orEmpty()}")
                if (!screenshotInProgress.get()) requestScreenshotAnalysis(allowPopupCandidate = allowPopupCandidate)
                return
            }
            if (allowPopupCandidate) return
            registeredCardGate.clear()
            resetToDefault(reason = "Texto visivel vazio; nenhum card lido neste momento.", record = !isPassiveDiagnosticPackage(activePackageName))
            return
        }
""",
"""        if (snapshotText.isBlank()) {
            traceEvent("process.empty_text source=${dollar}source reset_bubble=true")
            if (allowPopupCandidate) return
            pendingAnalysis = null
            registeredCardGate.clear()
            currentDistanceKm = null
            currentBubbleLabel = null
            lastSnapshotHash = null
            lastAnalyzedHash = null
            showOverlay(RadarColor.Default, distanceKm = null)
            recordDiagnostic(
                stage = "card_disappeared",
                reason = "Texto visivel vazio; card saiu da tela e limpei a bolinha imediatamente.",
            )
            return
        }
""",
        )

        text = text.replace(
"""        val diagnosticColor = color ?: currentRadarColor
        val fallbackText = lastSaveableRideText.takeIf { cached ->
            cached.isNotBlank() && System.currentTimeMillis() - lastSaveableRideCapturedAtMillis <= SAVEABLE_RIDE_CACHE_TTL_MS
        }
        val diagnosticText = text ?: fallbackText
        val diagnosticFields = fields ?: diagnosticText?.let { parser.parse(it, diagnosticPackageName) }
""",
"""        val diagnosticColor = color ?: currentRadarColor
        val diagnosticText = text
        val diagnosticFields = fields ?: diagnosticText?.let { parser.parse(it, diagnosticPackageName) }
""",
        )

        text = text.replace("scheduleVisibleTextAnalysis(delayMs = 80L", "scheduleVisibleTextAnalysis(delayMs = 0L")
        text = text.replace("scheduleVisibleTextAnalysis(delayMs = 20L", "scheduleVisibleTextAnalysis(delayMs = 0L")
        text = text.replace("traceEvent(\"accessibility.schedule delay=${dollar}{delayMs}ms\")", "traceEvent(\"accessibility.schedule delay=${dollar}{delayMs}ms fast=true\")")

        text = text.replace("const val SCREENSHOT_INTERVAL_MS = 650L", "const val SCREENSHOT_INTERVAL_MS = 120L")
        text = text.replace("const val SCREENSHOT_INTERVAL_MS = 300L", "const val SCREENSHOT_INTERVAL_MS = 120L")
        text = text.replace("const val SCAN_LOOP_MS = 700L", "const val SCAN_LOOP_MS = 180L")
        text = text.replace("const val SCAN_LOOP_MS = 500L", "const val SCAN_LOOP_MS = 180L")
        text = text.replace("const val SCAN_LOOP_MS = 300L", "const val SCAN_LOOP_MS = 180L")

        if ("immediate_card_reset_fast_scan.patch_applied" !in text) {
            text = text.replace(
                "        traceEvent(\"registered_card_package_reading.patch_applied=true\")\n",
                "        traceEvent(\"registered_card_package_reading.patch_applied=true\")\n        traceEvent(\"immediate_card_reset_fast_scan.patch_applied=true\")\n",
            )
        }

        if (text != original) file.writeText(text)
    }
}

immediateCardResetFastScan.configure {
    mustRunAfter(
        "registeredCardPackageReading",
        "sanitizeOcrAndUnlimitedModels",
        "cardLifecycleStrictOverlay",
        "clearBubbleOnScreenChange",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(immediateCardResetFastScan)
}
