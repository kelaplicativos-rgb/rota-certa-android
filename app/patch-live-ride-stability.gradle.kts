val patchLiveRideOverlayStability by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("private var lastDecisionOverlayAtMillis" !in text) {
            text = text.replace(
                "    private var currentDistanceKm: Double? = null\n",
                "    private var currentDistanceKm: Double? = null\n    private var lastDecisionOverlayAtMillis: Long = 0L\n",
            )
        }

        if ("private fun hasActiveRegisteredDecision()" !in text) {
            text = text.replace(
"""    private fun resetToDefault(
""",
"""    private fun hasActiveRegisteredDecision(): Boolean =
        (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
            registeredCardGate.hasSeenRecently(DECISION_OVERLAY_STICKY_MS)

    private fun resetToDefault(
""",
            )
        } else {
            text = text.replace(
"""    private fun hasActiveRegisteredDecision(): Boolean =
        currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red
""",
"""    private fun hasActiveRegisteredDecision(): Boolean =
        (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
            registeredCardGate.hasSeenRecently(DECISION_OVERLAY_STICKY_MS)
""",
            )
        }

        text = text.replace(
"""        val manager = windowManager ?: return
        currentRadarColor = color
        currentDistanceKm = distanceKm
""",
"""        val manager = windowManager ?: return
        val now = System.currentTimeMillis()
        if (color == RadarColor.Idle && currentRadarColor == RadarColor.Default && shouldScanCurrentWindow()) {
            traceEvent("overlay.keep_waiting color=amarelo reason=monitored_window")
            return
        }
        if ((color == RadarColor.Default || color == RadarColor.Idle) &&
            hasActiveRegisteredDecision() &&
            shouldScanCurrentWindow() &&
            now - lastDecisionOverlayAtMillis < DECISION_OVERLAY_STICKY_MS
        ) {
            traceEvent("overlay.keep_decision color=${dollar}{currentRadarColor.diagnosticLabel} requested=${dollar}{color.diagnosticLabel}")
            return
        }
        if (color == RadarColor.Green || color == RadarColor.Red) lastDecisionOverlayAtMillis = now
        val nextText = formatBubbleDistanceKm(distanceKm)
        if (currentRadarColor == color && currentDistanceKm == distanceKm && overlayView?.text?.toString() == nextText) return
        currentRadarColor = color
        currentDistanceKm = distanceKm
""",
        )

        if ("val nextText = formatBubbleDistanceKm(distanceKm)" in text) {
            text = text.replace(
                "        view.text = formatBubbleDistanceKm(currentDistanceKm)\n",
                "        view.text = nextText\n",
            )
        }

        if ("const val DECISION_OVERLAY_STICKY_MS" !in text) {
            text = text.replace(
                "        const val DIAGNOSTIC_EVENT_LIMIT = 60\n",
                "        const val DIAGNOSTIC_EVENT_LIMIT = 60\n        const val DECISION_OVERLAY_STICKY_MS = 3_500L\n",
            )
        }

        if ("registeredCardGate.hasSeenRecently(DECISION_OVERLAY_STICKY_MS)" !in text) {
            throw org.gradle.api.GradleException("Sticky da bolinha precisa depender de card cadastrado confirmado.")
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchLiveRideOverlayStability.configure {
    mustRunAfter("patchLiveRideAccessibilityService")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchLiveRideOverlayStability)
}
