fun replacePrivateFunctionBlockRegisteredPackageReading(
    source: String,
    functionName: String,
    transform: (String) -> String,
): String {
    val start = source.indexOf("    private fun $functionName")
    if (start < 0) return source
    val next = source.indexOf("\n    private fun ", start + 1)
    val block = if (next < 0) source.substring(start) else source.substring(start, next + 1)
    val replacement = transform(block)
    return if (next < 0) {
        source.substring(0, start) + replacement
    } else {
        source.substring(0, start) + replacement + source.substring(next + 1)
    }
}

val registeredCardPackageReading by tasks.registering {
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
"""        val cardMatch = RideCardTemplateMatcher.match(snapshotText, packageName, currentCardTemplates)
        if (cardMatch == null) {
""",
"""        val exactCardMatch = RideCardTemplateMatcher.match(snapshotText, packageName, currentCardTemplates)
        val cardMatch = exactCardMatch ?: registeredPackageCardMatch(packageName)
        if (exactCardMatch == null && cardMatch != null) {
            traceEvent("card_model.package_match package=${dollar}{packageName.orEmpty()} template=${dollar}{cardMatch.template.name}")
        }
        if (cardMatch == null) {
""",
        )

        if ("private fun registeredPackageCardMatch(" !in text) {
            text = text.replace(
"""    private suspend fun analyzeLiveText(
""",
"""    private fun registeredPackageCardMatch(packageName: String?): RideCardTemplateMatch? {
        val normalizedPackage = normalizePackageName(packageName) ?: return null
        val template = currentCardTemplates.firstOrNull { candidate ->
            candidate.packageName?.equals(normalizedPackage, ignoreCase = true) == true
        } ?: return null
        return RideCardTemplateMatch(
            template = template,
            score = 0.75,
            matchedFeatures = listOf("registered_app_card_match"),
        )
    }

    private suspend fun analyzeLiveText(
""",
            )
        }

        text = text.replace(
"""        if (snapshotText.isBlank()) {
            traceEvent("process.empty_text source=${dollar}source")
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
        val hash = text?.snapshotHash()
        val signature = listOf(stage, diagnosticColor.diagnosticLabel, reason, diagnosticPackageName.orEmpty(), hash?.toString().orEmpty()).joinToString("|")
""",
"""        val diagnosticColor = color ?: currentRadarColor
        val diagnosticText = text
        val diagnosticFields = fields ?: diagnosticText?.let { parser.parse(it, diagnosticPackageName) }
        val hash = diagnosticText?.snapshotHash()
        val signature = listOf(stage, diagnosticColor.diagnosticLabel, reason, diagnosticPackageName.orEmpty(), hash?.toString().orEmpty()).joinToString("|")
""",
        )

        text = text.replace(
"""        val diagnosticColor = color ?: currentRadarColor
        val fallbackText = lastSaveableRideText.takeIf { cached ->
            cached.isNotBlank() && System.currentTimeMillis() - lastSaveableRideCapturedAtMillis <= SAVEABLE_RIDE_CACHE_TTL_MS
        }
        val diagnosticText = text ?: fallbackText
        val diagnosticFields = fields ?: diagnosticText?.let { parser.parse(it, diagnosticPackageName) }
        val hash = diagnosticText?.snapshotHash()
        val signature = listOf(stage, diagnosticColor.diagnosticLabel, reason, diagnosticPackageName.orEmpty(), hash?.toString().orEmpty()).joinToString("|")
""",
"""        val diagnosticColor = color ?: currentRadarColor
        val diagnosticText = text
        val diagnosticFields = fields ?: diagnosticText?.let { parser.parse(it, diagnosticPackageName) }
        val hash = diagnosticText?.snapshotHash()
        val signature = listOf(stage, diagnosticColor.diagnosticLabel, reason, diagnosticPackageName.orEmpty(), hash?.toString().orEmpty()).joinToString("|")
""",
        )

        text = text.replace("textLength = text?.length ?: 0,", "textLength = diagnosticText?.length ?: 0,")
        text = text.replace("textPreview = text?.trim().orEmpty().take(DIAGNOSTIC_TEXT_LIMIT),", "textPreview = diagnosticText?.trim().orEmpty().take(DIAGNOSTIC_TEXT_LIMIT),")
        text = text.replace("pickup = fields?.pickup ?: result?.fields?.pickup,", "pickup = diagnosticFields?.pickup ?: result?.fields?.pickup,")
        text = text.replace("destination = fields?.destination ?: result?.fields?.destination,", "destination = diagnosticFields?.destination ?: result?.fields?.destination,")

        text = text.replace("scheduleVisibleTextAnalysis(delayMs = 80L", "scheduleVisibleTextAnalysis(delayMs = 0L")
        text = text.replace("scheduleVisibleTextAnalysis(delayMs = 20L", "scheduleVisibleTextAnalysis(delayMs = 0L")
        text = text.replace("const val SCREENSHOT_INTERVAL_MS = 650L", "const val SCREENSHOT_INTERVAL_MS = 120L")
        text = text.replace("const val SCREENSHOT_INTERVAL_MS = 300L", "const val SCREENSHOT_INTERVAL_MS = 120L")
        text = text.replace("const val SCAN_LOOP_MS = 700L", "const val SCAN_LOOP_MS = 180L")
        text = text.replace("const val SCAN_LOOP_MS = 500L", "const val SCAN_LOOP_MS = 180L")
        text = text.replace("const val SCAN_LOOP_MS = 300L", "const val SCAN_LOOP_MS = 180L")

        if ("registered_card_package_reading.patch_applied" !in text) {
            text = text.replace(
                "        traceEvent(\"sanitize_ocr_and_unlimited_models.patch_applied=true\")\n",
                "        traceEvent(\"sanitize_ocr_and_unlimited_models.patch_applied=true\")\n        traceEvent(\"registered_card_package_reading.patch_applied=true\")\n",
            )
        }
        if ("immediate_card_reset_fast_scan.patch_applied" !in text) {
            text = text.replace(
                "        traceEvent(\"registered_card_package_reading.patch_applied=true\")\n",
                "        traceEvent(\"registered_card_package_reading.patch_applied=true\")\n        traceEvent(\"immediate_card_reset_fast_scan.patch_applied=true\")\n",
            )
        }

        if (text != original) file.writeText(text)
    }
}

registeredCardPackageReading.configure {
    mustRunAfter(
        "manualCardLearningMode",
        "sanitizeOcrAndUnlimitedModels",
        "finalKmAndStrictRideCard",
        "cardLifecycleStrictOverlay",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(registeredCardPackageReading)
}
