val stableBubbleNoFlicker by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("event self_overlay ignored" !in text) {
            text = text.replace(
"""        if (!shouldScanPackage(packageName)) {
            val reason = scanBlockReason(packageName)
""",
"""        if (packageName == this.packageName && shouldScanPackage(currentRootPackageName())) {
            traceEvent("event self_overlay ignored ride_root=${dollar}{currentRootPackageName().orEmpty()}")
            return
        }
        if (!shouldScanPackage(packageName)) {
            val reason = scanBlockReason(packageName)
""",
            )
        }

        if ("force_idle skipped_active_ride_root" !in text) {
            text = text.replace(
"""    private fun forceIdleOverlay(reason: String) {
        lastSnapshotHash = null
""",
"""    private fun forceIdleOverlay(reason: String) {
        val activeRideRootPackage = currentRootPackageName()?.takeIf { shouldScanPackage(it) }
        if (activeRideRootPackage != null && reason.contains("Rota Certa esta em primeiro plano")) {
            traceEvent("overlay.force_idle skipped_active_ride_root=${dollar}activeRideRootPackage reason=${dollar}reason")
            return
        }
        lastSnapshotHash = null
""",
            )
        }

        if ("overlay.skip unchanged=true" !in text) {
            text = text.replace(
"""        val manager = windowManager ?: return
        currentRadarColor = color
        if (distanceKm != null) {
""",
"""        val manager = windowManager ?: return
        val targetText = labelText ?: formatBubbleDistanceKm(distanceKm)
        val unchanged = overlayView != null &&
            currentRadarColor == color &&
            currentDistanceKm == distanceKm &&
            currentBubbleLabel == labelText &&
            overlayView?.text?.toString().orEmpty() == targetText
        if (unchanged) {
            traceEvent("overlay.skip unchanged=true color=${dollar}{color.diagnosticLabel} text=${dollar}targetText")
            return
        }
        currentRadarColor = color
        if (distanceKm != null) {
""",
            )
        }

        if ("stable_bubble_no_flicker.patch_applied" !in text) {
            text = text.replace(
                "        traceEvent(\"precise_bubble_route_km.patch_applied=true\")\n",
                "        traceEvent(\"precise_bubble_route_km.patch_applied=true\")\n        traceEvent(\"stable_bubble_no_flicker.patch_applied=true\")\n",
            )
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

stableBubbleNoFlicker.configure {
    mustRunAfter(
        "preciseBubbleRouteKm",
        "shortcutNavigationIdleReset",
        "diagnosticJsonToolsActions",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(stableBubbleNoFlicker)
}
