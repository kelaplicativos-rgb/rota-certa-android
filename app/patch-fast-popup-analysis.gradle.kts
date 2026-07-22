val patchFastPopupAnalysis by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("private var lastRidePackageName: String? = null" !in text) {
            text = text.replace(
                "    private var activePackageName: String? = null\n    private var lastTextPackageName: String? = null\n",
                "    private var activePackageName: String? = null\n    private var lastRidePackageName: String? = null\n    private var lastTextPackageName: String? = null\n",
            )
        }

        text = text.replace(
"""        if (eventPackageName != null) {
            activePackageName = if (isPassiveDiagnosticPackage(eventPackageName)) null else eventPackageName
        }
""",
"""        if (eventPackageName != null) {
            if (shouldScanPackage(eventPackageName)) lastRidePackageName = eventPackageName
            activePackageName = if (isPassiveDiagnosticPackage(eventPackageName)) activePackageName else eventPackageName
        }
""",
        )

        text = text.replace(
            "scheduleVisibleTextAnalysis(delayMs = 80L, allowPopupCandidate = true)",
            "scheduleVisibleTextAnalysis(delayMs = 20L, allowPopupCandidate = true)",
        )
        text = text.replace(
            "scheduleVisibleTextAnalysis(delayMs = 80L)\n        requestScreenshotAnalysis()",
            "scheduleVisibleTextAnalysis(delayMs = 0L)\n        requestScreenshotAnalysis()",
        )

        text = text.replace(
"""        return RideCardTemplateMatcher.inferPackageName(text)
            ?.takeIf { inferred -> shouldScanPackage(inferred) }
""",
"""        val inferredPackage = RideCardTemplateMatcher.inferPackageName(text)
        if (inferredPackage != null && shouldScanPackage(inferredPackage)) return inferredPackage
        return lastRidePackageName?.takeIf { lastPackage ->
            text.isNotBlank() && shouldScanPackage(lastPackage)
        }
""",
        )

        if ("decision.quick" !in text) {
            text = text.replace(
"""            traceEvent("geocode.config home=${dollar}{homeCoordinate != null} alternative=${dollar}{alternativeCoordinate != null}")
            val homeDistanceKm = routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
""",
"""            traceEvent("geocode.config home=${dollar}{homeCoordinate != null} alternative=${dollar}{alternativeCoordinate != null}")
            val quickResult = decisionEngine.decide(
                fields = fields,
                settings = settings,
                destinationCoordinate = destinationCoordinate,
                homeCoordinate = homeCoordinate,
                alternativeCoordinate = alternativeCoordinate,
                fullText = text,
            )
            if (quickResult.recommendation != Recommendation.InsufficientData) {
                val quickColor = when (quickResult.recommendation) {
                    Recommendation.GoodRide -> RadarColor.Green
                    Recommendation.OutsideRadius -> RadarColor.Red
                    Recommendation.InsufficientData -> RadarColor.Default
                }
                traceEvent("decision.quick recommendation=${dollar}{quickResult.recommendation} distance=${dollar}{quickResult.nearestConfiguredDistanceKm()?.let(::formatDiagnosticKm) ?: "null"}")
                showOverlay(color = quickColor, distanceKm = quickResult.nearestConfiguredDistanceKm())
                recordDiagnostic(
                    stage = "analysis_quick_result",
                    color = quickColor,
                    reason = quickResult.reason,
                    text = text,
                    fields = fields,
                    result = quickResult,
                    cardTemplateMatch = cardMatch,
                )
            }
            val homeDistanceKm = routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
""",
            )
        }

        text = text.replace(
            "if (pending != null && pending.snapshotHash != lastAnalyzedHash && shouldScanCurrentWindow()) {",
            "if (pending != null && pending.snapshotHash != lastAnalyzedHash && (pending.allowPopupCandidate || shouldScanCurrentWindow())) {",
        )

        text = text.replace("const val SCAN_LOOP_MS = 850L", "const val SCAN_LOOP_MS = 350L")
        text = text.replace("const val SCREENSHOT_INTERVAL_MS = 650L", "const val SCREENSHOT_INTERVAL_MS = 300L")

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchFastPopupAnalysis.configure {
    mustRunAfter("patchHideInsufficientResultCard")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchFastPopupAnalysis)
}
