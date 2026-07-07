val patchSetupWarningForceOverlay by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        text = text.replace(
"""        showOverlay(RadarColor.Default, labelText = WARNING_BUBBLE_LABEL)
        recordDiagnostic(stage = "needs_setup", color = RadarColor.Default, reason = reason, text = text, fields = fields)
""",
"""        showSetupWarningOverlay()
        recordDiagnostic(stage = "needs_setup", color = RadarColor.Default, reason = reason, text = text, fields = fields)
""",
        )

        text = text.replace(
"""            showOverlay(RadarColor.Default, labelText = WARNING_BUBBLE_LABEL)
            toast("Card salvo. Aguarde o proximo card para validar.")
""",
"""            showSetupWarningOverlay()
            toast("Card salvo. Aguarde o proximo card para validar.")
""",
        )

        if ("private fun showSetupWarningOverlay()" !in text) {
            val helper = """
    private fun showSetupWarningOverlay() {
        if (!serviceReady) return
        traceEvent("setup_warning.force color=${'$'}{RadarColor.Default.diagnosticLabel}")
        val manager = windowManager ?: return
        currentRadarColor = RadarColor.Default
        currentDistanceKm = null
        currentBubbleLabel = WARNING_BUBBLE_LABEL
        val view = overlayView ?: TextView(this).also { newView ->
            val params = overlayLayoutParams()
            newView.contentDescription = "Rota Certa"
            newView.gravity = Gravity.CENTER
            newView.includeFontPadding = false
            newView.setTextColor(Color.BLACK)
            newView.setTypeface(Typeface.DEFAULT_BOLD)
            newView.setOnClickListener { toggleActionMenu() }
            newView.setOnTouchListener(BubbleTouchListener())
            if (!runCatching { manager.addView(newView, params) }.isSuccess) return
            overlayView = newView
            overlayParams = params
        }
        view.text = WARNING_BUBBLE_LABEL
        view.textSize = bubbleTextSizeSp(WARNING_BUBBLE_LABEL)
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(RadarColor.Default.argb(currentSettings))
            setStroke(dp(3), Color.argb((currentSettings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt(), 255, 255, 255))
        }
    }

"""
            val refreshAnchor = "    private fun refreshOverlayAppearance() {"
            val formatAnchor = "    private fun formatBubbleDistanceKm(distanceKm: Double?): String = when {"
            text = when {
                refreshAnchor in text -> text.replace(refreshAnchor, helper + refreshAnchor)
                formatAnchor in text -> text.replace(formatAnchor, helper + formatAnchor)
                else -> text
            }
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchSetupWarningForceOverlay.configure {
    mustRunAfter(tasks.matching { it.name.startsWith("patch") && it.name != "patchSetupWarningForceOverlay" })
    mustRunAfter("bubbleRouteDistanceOnly", "patchFullDiagnosticExport")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchSetupWarningForceOverlay)
}
