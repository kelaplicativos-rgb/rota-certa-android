val patchBubbleLiveAppearance by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        text = text.replace(
"""        scope.launch { repository.settings.collect { currentSettings = it } }
""",
"""        scope.launch {
            repository.settings.collect { settings ->
                val previousSettings = currentSettings
                currentSettings = settings
                if (
                    previousSettings.bubbleOpacity != settings.bubbleOpacity ||
                    previousSettings.bubbleDarkMode != settings.bubbleDarkMode
                ) {
                    refreshOverlayAppearance()
                }
            }
        }
""",
        )

        text = text.replace(
"""            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.argb(238, 32, 32, 32))
                setStroke(dp(1), Color.argb(220, 255, 255, 255))
            }
""",
"""            background = actionMenuBackgroundDrawable()
""",
        )

        if ("private fun refreshOverlayAppearance()" !in text) {
            text = text.replace(
"""    private fun formatBubbleDistanceKm(distanceKm: Double?): String = when {
""",
"""    private fun refreshOverlayAppearance() {
        val view = overlayView ?: return
        view.text = currentBubbleLabel ?: formatBubbleDistanceKm(currentDistanceKm)
        view.textSize = bubbleTextSizeSp(view.text.toString())
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(currentRadarColor.argb(currentSettings))
            setStroke(dp(3), Color.argb((currentSettings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt(), 255, 255, 255))
        }
        overlayMenuView?.background = actionMenuBackgroundDrawable()
    }

    private fun actionMenuBackgroundDrawable(): GradientDrawable = GradientDrawable().apply {
        val alpha = (currentSettings.bubbleOpacity.coerceIn(0.35, 1.0) * 238).roundToInt()
        cornerRadius = dp(14).toFloat()
        setColor(Color.argb(alpha, 32, 32, 32))
        setStroke(dp(1), Color.argb(alpha.coerceAtLeast(120), 255, 255, 255))
    }

    private fun formatBubbleDistanceKm(distanceKm: Double?): String = when {
""",
            )
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchBubbleLiveAppearance.configure {
    mustRunAfter(tasks.matching { it.name.startsWith("patch") && it.name != "patchBubbleLiveAppearance" })
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchBubbleLiveAppearance)
}