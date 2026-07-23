val preciseBubbleRouteKm by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        text = text.replace(
"""            val homeDistanceKm: Double? = null
            val alternativeDistanceKm: Double? = null
            traceEvent("route.distance skipped live_fast=true home=approx alternative=approx")
""",
"""            val homeDistanceKm = routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
            val alternativeDistanceKm = routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings)
            traceEvent("route.distance precise home=${dollar}{homeDistanceKm?.let(::formatDiagnosticKm) ?: "null"} alternative=${dollar}{alternativeDistanceKm?.let(::formatDiagnosticKm) ?: "null"}")
""",
        )

        text = text.replace(
"""    private suspend fun geocodeFast(query: String, region: DeviceRegion, settings: AppSettings): Coordinate? {
        if (query.isBlank()) return null
        return geocodingService.geocode(query, region)
    }
""",
"""    private suspend fun geocodeFast(query: String, region: DeviceRegion, settings: AppSettings): Coordinate? {
        if (query.isBlank()) return null
        return geocodingService.geocode(query, region)
            ?: googleMapsService.geocode(query, region, settings.googleMapsApiKey)
    }
""",
        )

        text = text.replace(
"""        if (distanceKm != null) {
            traceEvent("bubble.distance_label hidden_by_policy km=" + formatDiagnosticKm(distanceKm))
        }
        currentDistanceKm = null
        currentBubbleLabel = labelText
""",
"""        if (distanceKm != null) {
            traceEvent("bubble.distance_label shown_route km=" + formatDiagnosticKm(distanceKm))
        }
        currentDistanceKm = distanceKm
        currentBubbleLabel = labelText
""",
        )

        text = text.replace(
"""        showOverlay(color = radarColor, distanceKm = null)
        traceEvent("cache.instant_apply distance=hidden_cached_source_unknown")
        recordDiagnostic(
""",
"""        val cachedBubbleDistanceKm = result.trustedBubbleDistanceKm()
        showOverlay(color = radarColor, distanceKm = cachedBubbleDistanceKm)
        traceEvent("cache.instant_apply distance=" + (cachedBubbleDistanceKm?.let(::formatDiagnosticKm) ?: "hidden_no_route"))
        recordDiagnostic(
""",
        )

        if ("private fun AnalysisResult.trustedBubbleDistanceKm()" !in text) {
            text = text.replace(
"""
    private fun AnalysisResult.nearestRoutedConfiguredDistanceKm(
""",
"""
    private fun AnalysisResult.trustedBubbleDistanceKm(): Double? =
        if (reason.contains("Google Maps", ignoreCase = true)) nearestConfiguredDistanceKm() else null

    private fun AnalysisResult.nearestRoutedConfiguredDistanceKm(
""",
            )
        }

        if ("precise_bubble_route_km.patch_applied" !in text) {
            text = text.replace(
                "        traceEvent(\"shortcut.navigation.patch_applied=true\")\n",
                "        traceEvent(\"shortcut.navigation.patch_applied=true\")\n        traceEvent(\"precise_bubble_route_km.patch_applied=true\")\n",
            )
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

preciseBubbleRouteKm.configure {
    mustRunAfter(
        "diagnosticJsonToolsActions",
        "shortcutNavigationIdleReset",
        "bubbleRouteDistanceOnly",
        "patchLiveFastColorPriority",
        "patchLiveAnalysisSupersede",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(preciseBubbleRouteKm)
}
