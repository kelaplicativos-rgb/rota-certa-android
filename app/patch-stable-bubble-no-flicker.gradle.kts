fun replacePrivateFunctionBlockStableBubble(
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
            text = replacePrivateFunctionBlockStableBubble(text, "showOverlay") { block ->
                block.replace(
"""        val manager = windowManager ?: return
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
""",
                )
            }
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
