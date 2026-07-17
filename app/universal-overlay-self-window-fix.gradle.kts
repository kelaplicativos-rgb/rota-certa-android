// Corrige a falha observada no diagnostico 0.1.105: a janela da propria
// TYPE_ACCESSIBILITY_OVERLAY era confundida com a MainActivity e cancelava a
// geocodificacao/rota imediatamente depois de encontrar os dois enderecos.

fun overlayResolverReplaceRegion(
    source: String,
    startToken: String,
    endToken: String,
    replacement: String,
    label: String,
): String {
    val start = source.indexOf(startToken)
    val end = if (start >= 0) source.indexOf(endToken, start + startToken.length) else -1
    if (start < 0 || end <= start) throw GradleException("Regiao ausente para $label")
    return source.substring(0, start) + replacement + source.substring(end)
}

val universalOverlayWindowResolver by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("sessionDiagnosticRetention"))

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado")
        var text = file.readText()

        if ("universal_overlay_self_window_fix_0_1_106" !in text) {
            if ("lastExternalWindowPackageName" !in text) {
                val fieldRegex = Regex("    private var universalActiveAddressSignature: String\\? = null[^\\n]*\\n")
                val match = fieldRegex.find(text)
                    ?: throw GradleException("Campo de assinatura universal nao encontrado")
                text = text.replaceRange(
                    match.range,
                    match.value + "    private var lastExternalWindowPackageName: String? = null\n",
                )
            }

            text = overlayResolverReplaceRegion(
                source = text,
                startToken = "    override fun onAccessibilityEvent(event: AccessibilityEvent?) {",
                endToken = "    override fun onInterrupt()",
                replacement = """    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!serviceReady || event == null) return
        if (!currentSettings.appEnabled || !currentSettings.liveReadingEnabled) {
            hardClearUniversalTwoAddress("Leitura universal desligada.")
            return
        }
        val packageName = normalizePackageName(event.packageName?.toString()) ?: currentRootPackageName()
        val ownMainActivityEvent = UniversalWindowPackageResolver.isOwnMainActivityEvent(
            eventPackageName = packageName,
            eventClassName = event.className?.toString(),
            eventType = event.eventType,
            ownPackageName = this.packageName,
            mainActivityClassName = MainActivity::class.java.name,
            windowStateChangedType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        )
        if (packageName != this.packageName) {
            activePackageName = packageName
            lastExternalWindowPackageName = packageName
        } else if (ownMainActivityEvent) {
            activePackageName = packageName
        }
        if (packageName == this.packageName) {
            if (ownMainActivityEvent) {
                hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
            } else {
                traceEvent("universal.overlay.event ignored=true type=" + event.eventType)
            }
            return
        }
        traceEvent("universal.event package=" + packageName.orEmpty() + " type=" + event.eventType) // universal_two_address_event_0_1_98
        scheduleVisibleTextAnalysis(delayMs = 0L, allowPopupCandidate = true)
        requestScreenshotAnalysis(allowPopupCandidate = true)
    } // universal_overlay_event_guard_0_1_106

""",
                label = "evento de acessibilidade universal",
            )

            text = overlayResolverReplaceRegion(
                source = text,
                startToken = "    private fun currentWindowPackageName()",
                endToken = "    private fun currentRootPackageName()",
                replacement = """    private fun currentWindowPackageName(): String? {
        val resolution = UniversalWindowPackageResolver.resolve(
            rootPackageName = currentRootPackageName(),
            activePackageName = activePackageName,
            lastExternalPackageName = lastExternalWindowPackageName,
            ownPackageName = this.packageName,
        )
        lastExternalWindowPackageName = resolution.lastExternalPackageName
        return resolution.effectivePackageName
    } // universal_overlay_window_resolver_0_1_106

""",
                label = "resolvedor da janela atual",
            )

            val clearStart = text.indexOf("    private fun hardClearUniversalTwoAddress(reason: String) {")
            val clearEnd = if (clearStart >= 0) text.indexOf("\n    private fun ", clearStart + 10) else -1
            if (clearStart < 0 || clearEnd <= clearStart) {
                throw GradleException("Limpeza universal nao encontrada")
            }
            var clearBlock = text.substring(clearStart, clearEnd)
            val hadDataEndToken = "            universalActiveAddressSignature != null\n"
            if (hadDataEndToken !in clearBlock) {
                throw GradleException("Estado da limpeza universal nao encontrado")
            }
            clearBlock = clearBlock.replaceFirst(
                hadDataEndToken,
                hadDataEndToken + "        if (!hadData && currentRadarColor == RadarColor.Idle) return // universal_clear_idempotent_0_1_106\n",
            )
            text = text.substring(0, clearStart) + clearBlock + text.substring(clearEnd)

            text += "\n// universal_overlay_self_window_fix_0_1_106\n"
        }

        listOf(
            "UniversalWindowPackageResolver.resolve(",
            "UniversalWindowPackageResolver.isOwnMainActivityEvent(",
            "universal.overlay.event ignored=true",
            "universal_overlay_event_guard_0_1_106",
            "universal_overlay_window_resolver_0_1_106",
            "universal_clear_idempotent_0_1_106",
            "universal_overlay_self_window_fix_0_1_106",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Correcao de overlay incompleta: $marker")
        }

        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalOverlayWindowResolver)
}
