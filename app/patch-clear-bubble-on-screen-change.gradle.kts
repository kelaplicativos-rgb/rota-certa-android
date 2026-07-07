val clearBubbleOnScreenChange by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        text = text.replace(
"""                analysisSerial += 1
                traceEvent("analysis.invalidate_on_screen_change hash=${dollar}snapshotHash")
                showOverlay(RadarColor.Default)
""",
"""                analysisSerial += 1
                clearBubbleForScreenChange(snapshotHash)
""",
        )

        text = text.replace(
"""            analysisSerial += 1
            traceEvent("analysis.invalidate_on_screen_change hash=${dollar}snapshotHash")
            showOverlay(RadarColor.Default)
""",
"""            analysisSerial += 1
            clearBubbleForScreenChange(snapshotHash)
""",
        )

        if ("private fun clearBubbleForScreenChange(" !in text) {
            text = text.replace(
"""
    private fun resetToDefault(
""",
"""
    private fun clearBubbleForScreenChange(snapshotHash: Int) {
        pendingAnalysis = null
        currentDistanceKm = null
        currentBubbleLabel = null
        traceEvent("bubble.clear_on_screen_change hash=${dollar}snapshotHash")
        showOverlay(RadarColor.Default, distanceKm = null)
    }

    private fun resetToDefault(
""",
            )
        }

        if ("clear_bubble_on_screen_change.patch_applied" !in text) {
            text = text.replace(
                "        traceEvent(\"stable_bubble_no_flicker.patch_applied=true\")\n",
                "        traceEvent(\"stable_bubble_no_flicker.patch_applied=true\")\n        traceEvent(\"clear_bubble_on_screen_change.patch_applied=true\")\n",
            )
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

clearBubbleOnScreenChange.configure {
    mustRunAfter(
        "preciseBubbleRouteKm",
        "stableBubbleNoFlicker",
        "patchLiveAnalysisSupersede",
        "patchInstantCardDecisionCache",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(clearBubbleOnScreenChange)
}
