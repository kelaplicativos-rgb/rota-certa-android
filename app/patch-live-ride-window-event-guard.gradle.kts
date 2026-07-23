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

            val passivePackageListCode = """setOf(
            "android",
            "com.android.launcher",
            "com.android.settings",
            "com.android.systemui",
            "com.google.android.apps.maps",
            "com.google.android.apps.nbu.files",
            "com.google.android.inputmethod.latin",
            "com.google.android.packageinstaller",
            "com.openai.chatgpt",
            "com.sec.android.app.launcher",
            "com.samsung.android.app.settings",
            "com.samsung.android.app.smartcapture",
            "com.samsung.android.capture",
            "com.samsung.android.honeyboard",
            "com.waze",
        )"""

            if ("smart_capture_passive_overlay_0_1_84" !in text) {
                text = text.replace(
                    """            "com.samsung.android.app.settings",
            "com.samsung.android.honeyboard",
""",
                    """            "com.samsung.android.app.settings",
            "com.samsung.android.app.smartcapture", // smart_capture_passive_overlay_0_1_84
            "com.samsung.android.capture",
            "com.samsung.android.honeyboard",
""",
                )
            }

            if ("active_non_passive_package_priority_0_1_82" !in text) {
                text = text.replace(
"""    private fun currentWindowPackageName(): String? =
        currentRootPackageName() ?: activePackageName
""",
"""    private fun currentWindowPackageName(): String? {
        val rootPackage = currentRootPackageName()
        val activePackage = normalizePackageName(activePackageName)
        val passivePackages = $passivePackageListCode
        val activeIsPassive = activePackage == null || activePackage == this.packageName || activePackage in passivePackages
        return when {
            activePackage != null && !activeIsPassive && activePackage != rootPackage -> activePackage // active_non_passive_package_priority_0_1_82
            rootPackage != null -> rootPackage
            else -> activePackage
        }
    }
""",
                )
            }

            if ("passive_event_preserves_monitored_root_0_1_88" !in text) {
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
            val passivePackagesForEvent = $passivePackageListCode
            val eventIsPassiveOverlay = packageName == this.packageName || packageName in passivePackagesForEvent
            val monitoredRootPackage = currentRootPackageName()
            val eventAction = br.com.mapeiaia.rotacerta.core.RideWindowEventPolicy.decide(
                eventPackageIsMonitored = false,
                rootPackageIsMonitored = shouldScanPackage(monitoredRootPackage),
                eventPackageIsPassive = eventIsPassiveOverlay,
                hasActiveRegisteredDecision = hasActiveRegisteredDecision(),
            )
            when (eventAction) {
                br.com.mapeiaia.rotacerta.core.RideWindowEventAction.PreserveMonitoredRoot -> {
                    traceEvent("event overlay preserved monitored_root=${'$'}{monitoredRootPackage.orEmpty()} event_package=${'$'}packageName reason=${'$'}reason") // passive_event_preserves_monitored_root_0_1_88
                    return
                }
                br.com.mapeiaia.rotacerta.core.RideWindowEventAction.ResetIdle -> {
                    traceEvent("event passive ignored clean_stale_root=${'$'}{monitoredRootPackage.orEmpty()} event_package=${'$'}packageName reason=${'$'}reason") // passive_event_no_popup_scan_0_1_82
                    resetToIdle(reason, record = false)
                    return
                }
                else -> Unit
            }
            if (shouldScanPackage(monitoredRootPackage)) {
                traceEvent("event blocked ignored monitored_root=${'$'}{monitoredRootPackage.orEmpty()} event_package=${'$'}packageName reason=${'$'}reason")
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
            if ("passive_event_preserves_monitored_root_0_1_88" !in text) {
                throw org.gradle.api.GradleException("Evento passivo ainda apaga a janela monitorada antes da decisao.")
            }
            if ("active_non_passive_package_priority_0_1_82" !in text) {
                throw org.gradle.api.GradleException("Nao consegui priorizar pacote ativo real contra raiz obsoleta.")
            }
            if ("smart_capture_passive_overlay_0_1_84" !in text) {
                throw org.gradle.api.GradleException("Nao consegui tratar Samsung Smart Capture como overlay passivo.")
            }
            if ("PASSIVE_DIAGNOSTIC_PACKAGES" in text.substringAfter("active_non_passive_package_priority_0_1_82")) {
                throw org.gradle.api.GradleException("Patch anti-pisca ainda depende de constante passiva instavel.")
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
