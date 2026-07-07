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

        if ("private fun AnalysisResult.nearestRoutedConfiguredDistanceKm(" !in text) {
            text = text.replace(
"""    private fun AnalysisResult.nearestConfiguredDistanceKm(): Double? =
        listOfNotNull(pickupToHomeKm, pickupToAlternativeKm).minOrNull()

    private fun resetToDefault(
""",
"""    private fun AnalysisResult.nearestConfiguredDistanceKm(): Double? =
        listOfNotNull(pickupToHomeKm, pickupToAlternativeKm).minOrNull()

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