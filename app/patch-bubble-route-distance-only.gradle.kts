val bubbleRouteDistanceOnly by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        text = text.replace(
"""            traceEvent("overlay.apply color=${dollar}{radarColor.diagnosticLabel} distance=${dollar}{result.nearestConfiguredDistanceKm()?.let(::formatDiagnosticKm) ?: "null"}")
            showOverlay(color = radarColor, distanceKm = result.nearestConfiguredDistanceKm())
""",
"""            val bubbleDistanceKm = result.nearestRoutedConfiguredDistanceKm(homeDistanceKm, alternativeDistanceKm)
            traceEvent("overlay.apply color=${dollar}{radarColor.diagnosticLabel} distance=${dollar}{bubbleDistanceKm?.let(::formatDiagnosticKm) ?: "hidden_approximate"}")
            showOverlay(color = radarColor, distanceKm = bubbleDistanceKm)
""",
        )

        text = text.replace(
"""            traceEvent("overlay.apply color=${dollar}{radarColor.diagnosticLabel} label=${dollar}{bubbleLabel.orEmpty()} distance=${dollar}{result.nearestConfiguredDistanceKm()?.let(::formatDiagnosticKm) ?: "null"}")
            showOverlay(color = radarColor, distanceKm = result.nearestConfiguredDistanceKm(), labelText = bubbleLabel)
""",
"""            val bubbleDistanceKm = result.nearestRoutedConfiguredDistanceKm(homeDistanceKm, alternativeDistanceKm)
            traceEvent("overlay.apply color=${dollar}{radarColor.diagnosticLabel} label=${dollar}{bubbleLabel.orEmpty()} distance=${dollar}{bubbleDistanceKm?.let(::formatDiagnosticKm) ?: "hidden_approximate"}")
            showOverlay(color = radarColor, distanceKm = bubbleDistanceKm, labelText = bubbleLabel)
""",
        )

        text = text.replace(
"""        showOverlay(color = radarColor, distanceKm = result.nearestConfiguredDistanceKm())
        recordDiagnostic(
            stage = "analysis_cached_result",
""",
"""        showOverlay(color = radarColor, distanceKm = null)
        traceEvent("cache.instant_apply distance=hidden_cached_source_unknown")
        recordDiagnostic(
            stage = "analysis_cached_result",
""",
        )

        if ("private fun AnalysisResult.nearestRoutedConfiguredDistanceKm(" !in text) {
            text = text.replace(
"""
    private fun resetToDefault(
""",
"""
    private fun AnalysisResult.nearestRoutedConfiguredDistanceKm(
        routedHomeDistanceKm: Double?,
        routedAlternativeDistanceKm: Double?,
    ): Double? {
        val nearestConfiguredDistanceKm = nearestConfiguredDistanceKm() ?: return null
        val homeIsNearest = pickupToHomeKm?.let { abs(it - nearestConfiguredDistanceKm) < 0.05 } == true
        val alternativeIsNearest = pickupToAlternativeKm?.let { abs(it - nearestConfiguredDistanceKm) < 0.05 } == true
        return listOfNotNull(
            routedHomeDistanceKm?.takeIf { homeIsNearest },
            routedAlternativeDistanceKm?.takeIf { alternativeIsNearest },
        ).minOrNull()
    }

    private fun resetToDefault(
""",
            )
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

bubbleRouteDistanceOnly.configure {
    mustRunAfter(tasks.matching { it.name.startsWith("patch") })
    mustRunAfter("bubbleLiveAppearance")
    mustRunAfter("unmonitoredScreenshotGuard")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(bubbleRouteDistanceOnly)
}