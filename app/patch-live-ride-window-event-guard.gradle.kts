val liveRideWindowEventGuard by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        serviceFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            text = text.replace(
"""        val rootPackageName = currentRootPackageName()
        val rootIsRideWindow = shouldScanPackage(rootPackageName)
        val rootPackageName = currentRootPackageName()
        val rootIsRideWindow = shouldScanPackage(rootPackageName)
""",
"""        val rootPackageName = currentRootPackageName()
        val rootIsRideWindow = shouldScanPackage(rootPackageName)
""",
            )

            if ("event self ignored active_ride_root" !in text) {
                text = text.replace(
"""        if (packageName == this.packageName) {
            rememberBubbleReason("self_app", "Rota Certa em primeiro plano; bolinha limpa e leitura de corrida pausada.")
            resetToIdle("Rota Certa em primeiro plano; bolinha limpa e leitura de corrida pausada.", record = false)
            return
        }
        if (!shouldScanPackage(packageName)) {
            val reason = scanBlockReason(packageName)
""",
"""        val activeRideRootPackage = currentRootPackageName()
        val hasActiveRideRoot = shouldScanPackage(activeRideRootPackage)
        if (packageName == this.packageName) {
            if (hasActiveRideRoot) {
                traceEvent("event self ignored active_ride_root=${'$'}{activeRideRootPackage.orEmpty()}")
                return
            }
            rememberBubbleReason("self_app", "Rota Certa em primeiro plano; bolinha limpa e leitura de corrida pausada.")
            resetToIdle("Rota Certa em primeiro plano; bolinha limpa e leitura de corrida pausada.", record = false)
            return
        }
        if (!shouldScanPackage(packageName)) {
            val reason = scanBlockReason(packageName)
            if (hasActiveRideRoot) {
                traceEvent("event blocked ignored active_ride_root=${'$'}{activeRideRootPackage.orEmpty()} event_package=${'$'}packageName reason=${'$'}reason")
                return
            }
""",
                )
            }

            if ("resetToIdle guarded active_ride_window" !in text) {
                text = text.replace(
"""    private fun resetToIdle(
        reason: String,
        record: Boolean = false,
    ) {
        lastSnapshotHash = null
        lastAnalyzedHash = null
        registeredCardGate.clear()
        clearRememberedRideText()
        rememberBubbleReason("idle", reason)
        showOverlay(RadarColor.Idle)
""",
"""    private fun resetToIdle(
        reason: String,
        record: Boolean = false,
    ) {
        if (shouldScanCurrentWindow() && (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red)) {
            traceEvent("resetToIdle guarded active_ride_window reason=${'$'}reason")
            return
        }
        lastSnapshotHash = null
        lastAnalyzedHash = null
        registeredCardGate.clear()
        clearRememberedRideText()
        rememberBubbleReason("idle", reason)
        showOverlay(RadarColor.Idle)
""",
                )
            }

            if (text != original) file.writeText(text)
        }
    }
}

liveRideWindowEventGuard.configure {
    mustRunAfter("patchBubbleStateReport", "patchLiveRideOverlayStability")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(liveRideWindowEventGuard)
}
