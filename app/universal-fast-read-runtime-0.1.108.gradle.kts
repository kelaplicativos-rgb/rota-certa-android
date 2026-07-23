// Caminho rapido orientado pelo diagnostico 0.1.107 (3077):
// - a acessibilidade encontrou dois enderecos em 2 ms;
// - 178 ms depois, a raiz temporaria da propria bolinha devolveu texto vazio;
// - o vazio cancelou a rota antes da geocodificacao;
// - o OCR continuou repetindo sem necessidade enquanto o card ja estava legivel.
//
// Contrato 0.1.108:
// - raiz vazia da TYPE_ACCESSIBILITY_OVERLAY nao apaga o card externo;
// - raiz vazia real do app externo continua limpando imediatamente;
// - OCR fica pausado enquanto a acessibilidade possui um card ativo;
// - OCR volta a funcionar imediatamente apos limpeza ou quando for o fallback.

fun fastReadReplaceOnce(
    source: String,
    oldValue: String,
    newValue: String,
    label: String,
): String {
    if (oldValue !in source) throw GradleException("Ponto ausente para $label")
    return source.replaceFirst(oldValue, newValue)
}

val universalFastReadRuntime108 by tasks.registering {
    val serviceFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt",
    )
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalPoiDestinationBoundary"))
    dependsOn(tasks.named("universalOverlayWindowResolver"))

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado")
        var text = file.readText()

        if ("universal_fast_read_runtime_0_1_108" !in text) {
            text = fastReadReplaceOnce(
                source = text,
                oldValue = "    private var lastExternalWindowPackageName: String? = null\n",
                newValue = """    private var lastExternalWindowPackageName: String? = null
    private var universalAccessibilityOwnsCard: Boolean = false // universal_fast_read_field_0_1_108
""",
                label = "campo do proprietario da leitura rapida",
            )

            text = fastReadReplaceOnce(
                source = text,
                oldValue = """                        val expectedPackage = universalResolvedForegroundPackage()
                        val visibleText = collectVisibleText(allowPopupCandidate = true)
                        if (expectedPackage == universalResolvedForegroundPackage() && isUniversalExternalWindowActive()) {
                            processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
                            requestScreenshotAnalysis(allowPopupCandidate = true)
                        }
""",
                newValue = """                        val expectedPackage = universalResolvedForegroundPackage()
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
                label = "ciclo continuo sem falso vazio",
            )

            text = fastReadReplaceOnce(
                source = text,
                oldValue = """            val visibleText = collectVisibleText(allowPopupCandidate = true)
            if (!isUniversalExternalWindowActive() || expectedPackage != universalResolvedForegroundPackage()) return@launch
            processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
""",
                newValue = """            val visibleText = collectVisibleText(allowPopupCandidate = true)
            if (!isUniversalExternalWindowActive() || expectedPackage != universalResolvedForegroundPackage()) return@launch
            val ignoreTransientOverlayEmpty = UniversalFastReadPolicy.shouldIgnoreTransientEmptyAccessibilityRead(
                text = visibleText,
                rootPackageName = currentRootPackageName(),
                effectivePackageName = expectedPackage,
                ownPackageName = this@LiveRideAccessibilityService.packageName,
            )
            if (ignoreTransientOverlayEmpty) {
                traceEvent("universal.accessibility transient_overlay_empty_ignored=true")
                return@launch
            }
            processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
""",
                label = "agendamento sem falso vazio",
            )

            text = fastReadReplaceOnce(
                source = text,
                oldValue = """    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {
        if (!serviceReady || !isUniversalExternalWindowActive() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val requestedPackage = universalResolvedForegroundPackage() ?: return
""",
                newValue = """    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {
        if (!serviceReady || !isUniversalExternalWindowActive() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!UniversalFastReadPolicy.shouldRequestOcr(
                accessibilityOwnsCard = universalAccessibilityOwnsCard,
                hasActiveAddressSignature = universalActiveAddressSignature != null,
            )
        ) return
        val requestedPackage = universalResolvedForegroundPackage() ?: return
""",
                label = "pausa do OCR redundante",
            )

            text = fastReadReplaceOnce(
                source = text,
                oldValue = """            UniversalLiveReadAction.Analyze -> Unit
""",
                newValue = """            UniversalLiveReadAction.Analyze -> {
                universalAccessibilityOwnsCard = liveSource == UniversalLiveReadSource.Accessibility
            }
""",
                label = "proprietario da fonte ativa",
            )

            text = fastReadReplaceOnce(
                source = text,
                oldValue = """        universalLiveReadGate.reset()
""",
                newValue = """        universalAccessibilityOwnsCard = false
        universalLiveReadGate.reset()
""",
                label = "liberacao do OCR na limpeza",
            )

            text += "\n// universal_fast_read_runtime_0_1_108\n"
        }

        listOf(
            "universal_fast_read_field_0_1_108",
            "UniversalFastReadPolicy.shouldIgnoreTransientEmptyAccessibilityRead(",
            "universal.accessibility transient_overlay_empty_ignored=true",
            "UniversalFastReadPolicy.shouldRequestOcr(",
            "universalAccessibilityOwnsCard = liveSource == UniversalLiveReadSource.Accessibility",
            "universalAccessibilityOwnsCard = false",
            "universal_fast_read_runtime_0_1_108",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Caminho rapido incompleto: $marker")
        }

        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalFastReadRuntime108)
}
