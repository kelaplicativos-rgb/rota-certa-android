val liveRideWindowEventGuard by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        serviceFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            text = text.replace(
                Regex("""\n        val rootPackageName = currentRootPackageName\(\)\n        val rootIsRideWindow = shouldScanPackage\(rootPackageName\)"""),
                "",
            )
            text = text.replace(
                Regex("""\n        val activeRideRootPackage = currentRootPackageName\(\)\n        val hasActiveRideRoot = shouldScanPackage\(activeRideRootPackage\)"""),
                "",
            )

            if ("active_non_passive_package_priority_0_1_82" !in text) {
                text = text.replace(
"""    private fun currentWindowPackageName(): String? =
        currentRootPackageName() ?: activePackageName
""",
"""    private fun currentWindowPackageName(): String? {
        val rootPackage = currentRootPackageName()
        val activePackage = normalizePackageName(activePackageName)
        return when {
            activePackage != null && !isPassiveDiagnosticPackage(activePackage) && activePackage != rootPackage -> activePackage // active_non_passive_package_priority_0_1_82
            rootPackage != null -> rootPackage
            else -> activePackage
        }
    }
""",
                )
            }

            if ("event passive ignored clean_stale_root" !in text) {
                text = text.replace(
"""        if (packageName == this.packageName) {
            rememberBubbleReason("self_app", "Rota Certa em primeiro plano; bolinha limpa e leitura de corrida pausada.")
            resetToIdle("Rota Certa em primeiro plano; bolinha limpa e leitura de corrida pausada.", record = false)
            return
        }
        if (!shouldScanPackage(packageName)) {
            val reason = scanBlockReason(packageName)
""",
"""        if (packageName == this.packageName) {
            if (shouldScanPackage(currentRootPackageName())) {
                traceEvent("event self ignored monitored_root=${'$'}{currentRootPackageName().orEmpty()}")
                return
            }
            rememberBubbleReason("self_app", "Rota Certa em primeiro plano; bolinha limpa e leitura de corrida pausada.")
            resetToIdle("Rota Certa em primeiro plano; bolinha limpa e leitura de corrida pausada.", record = false)
            return
        }
        if (!shouldScanPackage(packageName)) {
            val reason = scanBlockReason(packageName)
            if (isPassiveDiagnosticPackage(packageName)) {
                traceEvent("event passive ignored clean_stale_root=${'$'}{currentRootPackageName().orEmpty()} event_package=${'$'}packageName reason=${'$'}reason") // passive_event_no_popup_scan_0_1_82
                if (!hasActiveRegisteredDecision()) {
                    resetToIdle(reason, record = false)
                }
                return
            }
            if (shouldScanPackage(currentRootPackageName())) {
                traceEvent("event blocked ignored monitored_root=${'$'}{currentRootPackageName().orEmpty()} event_package=${'$'}packageName reason=${'$'}reason")
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
        if (shouldScanCurrentWindow() && hasActiveRegisteredDecision()) {
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
            } else {
                text = text.replace(
                    "if (shouldScanCurrentWindow() && (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red))",
                    "if (shouldScanCurrentWindow() && hasActiveRegisteredDecision())",
                )
            }

            if ("passive_event_no_popup_scan_0_1_82" !in text) {
                throw org.gradle.api.GradleException("Nao consegui bloquear leitura/print em eventos passivos.")
            }
            if ("active_non_passive_package_priority_0_1_82" !in text) {
                throw org.gradle.api.GradleException("Nao consegui priorizar pacote ativo real contra raiz obsoleta.")
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
