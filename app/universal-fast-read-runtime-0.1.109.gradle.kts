// Ajuste orientado pelo diagnostico 0.1.108:
// - a decisao saiu em menos de 600 ms;
// - um unico quadro OCR vazio apagou uma decisao valida;
// - launcher, System UI e DocumentsUI continuaram disparando OCR sem utilidade.
//
// Contrato 0.1.109:
// - o fim de card por OCR precisa de confirmacao da mesma fonte;
// - pacotes passivos nao percorrem coleta nem OCR;
// - a primeira leitura continua em 300 ms;
// - com card ativo, OCR vira watchdog de 650 ms para reduzir carga.

fun fastRead109ReplaceOnce(
    source: String,
    oldValue: String,
    newValue: String,
    label: String,
): String {
    if (oldValue !in source) throw GradleException("Ponto ausente para $label")
    return source.replaceFirst(oldValue, newValue)
}

val universalFastReadRuntime109 by tasks.registering {
    val serviceFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt",
    )
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalFastReadRuntime108"))

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado")
        var text = file.readText()

        if ("universal_fast_read_runtime_0_1_109" !in text) {
            text = fastRead109ReplaceOnce(
                source = text,
                oldValue = """                        val expectedPackage = universalResolvedForegroundPackage()
                        val visibleText = collectVisibleText(allowPopupCandidate = true)
                        if (expectedPackage == universalResolvedForegroundPackage() && isUniversalExternalWindowActive()) {
                            val ignoreTransientOverlayEmpty = UniversalFastReadPolicy.shouldIgnoreTransientEmptyAccessibilityRead(
                                text = visibleText,
                                rootPackageName = currentRootPackageName(),
                                effectivePackageName = expectedPackage,
                                ownPackageName = this@LiveRideAccessibilityService.packageName,
                            )
                            if (ignoreTransientOverlayEmpty) {
                                traceEvent("universal.accessibility transient_overlay_empty_ignored=true")
                            } else {
                                processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
                            }
                            requestScreenshotAnalysis(allowPopupCandidate = true)
                        }
""",
                newValue = """                        val expectedPackage = universalResolvedForegroundPackage()
                        if (!UniversalFastReadPolicy.shouldScanLivePackage(
                                packageName = expectedPackage,
                                ownPackageName = this@LiveRideAccessibilityService.packageName,
                            )
                        ) {
                            hardClearUniversalTwoAddress("Pacote passivo; leitura e OCR suspensos.")
                        } else {
                            val visibleText = collectVisibleText(allowPopupCandidate = true)
                            if (expectedPackage == universalResolvedForegroundPackage() && isUniversalExternalWindowActive()) {
                                val ignoreTransientOverlayEmpty = UniversalFastReadPolicy.shouldIgnoreTransientEmptyAccessibilityRead(
                                    text = visibleText,
                                    rootPackageName = currentRootPackageName(),
                                    effectivePackageName = expectedPackage,
                                    ownPackageName = this@LiveRideAccessibilityService.packageName,
                                )
                                if (ignoreTransientOverlayEmpty) {
                                    traceEvent("universal.accessibility transient_overlay_empty_ignored=true")
                                } else {
                                    processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
                                }
                                requestScreenshotAnalysis(allowPopupCandidate = true)
                            }
                        }
""",
                label = "ciclo sem pacotes passivos",
            )

            text = fastRead109ReplaceOnce(
                source = text,
                oldValue = """        val expectedPackage = universalResolvedForegroundPackage() ?: return
        analyzeJob?.cancel()
        analyzeJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            if (!isUniversalExternalWindowActive() || expectedPackage != universalResolvedForegroundPackage()) return@launch
            val visibleText = collectVisibleText(allowPopupCandidate = true)
""",
                newValue = """        val expectedPackage = universalResolvedForegroundPackage() ?: return
        if (!UniversalFastReadPolicy.shouldScanLivePackage(
                packageName = expectedPackage,
                ownPackageName = this.packageName,
            )
        ) return
        analyzeJob?.cancel()
        analyzeJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            if (!isUniversalExternalWindowActive() || expectedPackage != universalResolvedForegroundPackage()) return@launch
            val visibleText = collectVisibleText(allowPopupCandidate = true)
""",
                label = "agendamento sem pacote passivo",
            )

            text = fastRead109ReplaceOnce(
                source = text,
                oldValue = """    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {
        if (!serviceReady || !isUniversalExternalWindowActive() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!UniversalFastReadPolicy.shouldRequestOcr(
                accessibilityOwnsCard = universalAccessibilityOwnsCard,
                hasActiveAddressSignature = universalActiveAddressSignature != null,
            )
        ) return
        val requestedPackage = universalResolvedForegroundPackage() ?: return
""",
                newValue = """    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {
        if (!serviceReady || !isUniversalExternalWindowActive() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val requestedPackage = universalResolvedForegroundPackage() ?: return
        if (!UniversalFastReadPolicy.shouldScanLivePackage(
                packageName = requestedPackage,
                ownPackageName = this.packageName,
            )
        ) return
        if (!UniversalFastReadPolicy.shouldRequestOcr(
                accessibilityOwnsCard = universalAccessibilityOwnsCard,
                hasActiveAddressSignature = universalActiveAddressSignature != null,
            )
        ) return
""",
                label = "OCR bloqueado em pacote passivo",
            )

            text = fastRead109ReplaceOnce(
                source = text,
                oldValue = """        val now = System.currentTimeMillis()
        if (now - lastScreenshotMillis < SCREENSHOT_INTERVAL_MS) return
""",
                newValue = """        val now = System.currentTimeMillis()
        val minimumOcrIntervalMillis = UniversalFastReadPolicy.minimumOcrIntervalMillis(
            hasActiveAddressSignature = universalActiveAddressSignature != null,
        )
        if (now - lastScreenshotMillis < minimumOcrIntervalMillis) return
""",
                label = "watchdog OCR adaptativo",
            )

            text += "\n// universal_fast_read_runtime_0_1_109\n"
        }

        listOf(
            "UniversalFastReadPolicy.shouldScanLivePackage(",
            "Pacote passivo; leitura e OCR suspensos.",
            "UniversalFastReadPolicy.minimumOcrIntervalMillis(",
            "universal_fast_read_runtime_0_1_109",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Caminho rapido 0.1.109 incompleto: $marker")
        }

        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalFastReadRuntime109)
}
