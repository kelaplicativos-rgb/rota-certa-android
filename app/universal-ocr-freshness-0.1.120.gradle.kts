// Correcao orientada pelo relatorio real 0.1.120 (build 3377):
// apos uma limpeza por troca de janela, um OCR iniciado na janela anterior podia
// terminar quando o inDrive reaparecesse e reaplicar o card antigo.
//
// O token abaixo vincula cada captura ao pacote observado, a geracao da tela e
// a geracao da janela. Qualquer troca real invalida a captura, mesmo quando o
// pacote anterior volta antes do fim do OCR. Eventos da propria bolinha nao
// alteram a geracao, preservando a leitura por baixo do overlay.

fun ocrFresh120ReplaceOnce(
    source: String,
    oldValue: String,
    newValue: String,
    label: String,
): String {
    if (oldValue !in source) throw GradleException("Ponto ausente para $label")
    return source.replaceFirst(oldValue, newValue)
}

fun ocrFresh120InsertBeforeLastBrace(source: String, addition: String, label: String): String {
    val index = source.lastIndexOf("\n}")
    if (index < 0) throw GradleException("Fechamento ausente para $label")
    return source.substring(0, index) + addition + source.substring(index)
}

fun applyUniversalOcrFreshness120(
    serviceFile: java.io.File,
    guardFile: java.io.File,
    testFile: java.io.File,
) {
    listOf(serviceFile, guardFile, testFile).forEach { file ->
        if (!file.exists()) throw GradleException("Arquivo nao encontrado: ${file.path}")
    }

    var guardText = guardFile.readText()
    if ("universal_ocr_freshness_policy_0_1_120" !in guardText) {
        val anchor = "    private fun normalize(value: String?): String? =\n"
        val policy = """    data class OcrRequestToken(
        val observedPackageName: String,
        val screenGeneration: Long,
        val windowGeneration: Long,
    )

    fun createOcrRequestToken(
        observedPackageName: String?,
        resolvedPackageName: String?,
        ownPackageName: String,
        screenGeneration: Long,
        windowGeneration: Long,
    ): OcrRequestToken? {
        val observed = normalize(observedPackageName) ?: return null
        val resolved = normalize(resolvedPackageName) ?: return null
        if (!shouldScanLivePackage(observed, ownPackageName)) return null
        if (observed != resolved) return null
        return OcrRequestToken(
            observedPackageName = observed,
            screenGeneration = screenGeneration,
            windowGeneration = windowGeneration,
        ) // universal_ocr_freshness_policy_0_1_120
    }

    fun isOcrRequestFresh(
        token: OcrRequestToken,
        observedPackageName: String?,
        resolvedPackageName: String?,
        ownPackageName: String,
        screenGeneration: Long,
        windowGeneration: Long,
    ): Boolean {
        val observed = normalize(observedPackageName) ?: return false
        val resolved = normalize(resolvedPackageName) ?: return false
        return shouldScanLivePackage(observed, ownPackageName) &&
            token.observedPackageName == observed &&
            token.observedPackageName == resolved &&
            token.screenGeneration == screenGeneration &&
            token.windowGeneration == windowGeneration
    }

"""
        guardText = ocrFresh120ReplaceOnce(
            source = guardText,
            oldValue = anchor,
            newValue = policy + anchor,
            label = "politica de frescor do OCR",
        )
        guardFile.writeText(guardText)
    }

    var serviceText = serviceFile.readText()
    if ("universal_ocr_window_generation_0_1_120" !in serviceText) {
        serviceText = ocrFresh120ReplaceOnce(
            source = serviceText,
            oldValue = "    private var universalScreenGeneration: Long = 0L\n",
            newValue = """    private var universalScreenGeneration: Long = 0L
    private var universalWindowGeneration: Long = 0L // universal_ocr_window_generation_0_1_120
""",
            label = "geracao da janela",
        )

        serviceText = ocrFresh120ReplaceOnce(
            source = serviceText,
            oldValue = """            if (ownMainActivityEvent) {
                universalForegroundPackageName = this.packageName
""",
            newValue = """            if (ownMainActivityEvent) {
                if (universalForegroundPackageName != this.packageName) universalWindowGeneration += 1L
                universalForegroundPackageName = this.packageName
""",
            label = "invalidacao ao abrir a MainActivity",
        )

        serviceText = ocrFresh120ReplaceOnce(
            source = serviceText,
            oldValue = """        val resolvedPackage = candidatePackage ?: lastExternalWindowPackageName ?: return
        val previousExternalPackage = universalForegroundPackageName?.takeUnless { it == this.packageName }
        universalForegroundPackageName = resolvedPackage
""",
            newValue = """        val resolvedPackage = candidatePackage ?: lastExternalWindowPackageName ?: return
        val previousObservedPackage = universalForegroundPackageName
        val previousExternalPackage = previousObservedPackage?.takeUnless { it == this.packageName }
        if (previousObservedPackage != resolvedPackage) universalWindowGeneration += 1L
        universalForegroundPackageName = resolvedPackage
""",
            label = "invalidacao por troca real de pacote",
        )

        serviceText = ocrFresh120ReplaceOnce(
            source = serviceText,
            oldValue = """        val requestedPackage = universalResolvedForegroundPackage() ?: return
        if (!UniversalFastReadPolicy.shouldScanLivePackage(
""",
            newValue = """        val resolvedOcrPackage = universalResolvedForegroundPackage() ?: return
        val ocrRequestToken = UniversalFastReadPolicy.createOcrRequestToken(
            observedPackageName = universalForegroundPackageName ?: activePackageName,
            resolvedPackageName = resolvedOcrPackage,
            ownPackageName = this.packageName,
            screenGeneration = universalScreenGeneration,
            windowGeneration = universalWindowGeneration,
        ) ?: run {
            traceEvent("universal.ocr request_blocked_observed_window=true")
            return
        }
        val requestedPackage = ocrRequestToken.observedPackageName
        if (!UniversalFastReadPolicy.shouldScanLivePackage(
""",
            label = "token da captura OCR",
        )

        serviceText = ocrFresh120ReplaceOnce(
            source = serviceText,
            oldValue = """                                if (!isUniversalExternalWindowActive() || requestedPackage != universalResolvedForegroundPackage()) {
                                    traceEvent("universal.ocr discarded_stale_window=true")
                                    return@runCatching
                                }
""",
            newValue = """                                if (!UniversalFastReadPolicy.isOcrRequestFresh(
                                        token = ocrRequestToken,
                                        observedPackageName = universalForegroundPackageName ?: activePackageName,
                                        resolvedPackageName = universalResolvedForegroundPackage(),
                                        ownPackageName = this@LiveRideAccessibilityService.packageName,
                                        screenGeneration = universalScreenGeneration,
                                        windowGeneration = universalWindowGeneration,
                                    )
                                ) {
                                    traceEvent("universal.ocr discarded_stale_window=true")
                                    return@runCatching
                                }
""",
            label = "frescor antes da extracao",
        )

        serviceText = ocrFresh120ReplaceOnce(
            source = serviceText,
            oldValue = """                                if (!isUniversalExternalWindowActive() || requestedPackage != universalResolvedForegroundPackage()) {
                                    traceEvent("universal.ocr discarded_after_extract=true")
                                    return@runCatching
                                }
""",
            newValue = """                                if (!UniversalFastReadPolicy.isOcrRequestFresh(
                                        token = ocrRequestToken,
                                        observedPackageName = universalForegroundPackageName ?: activePackageName,
                                        resolvedPackageName = universalResolvedForegroundPackage(),
                                        ownPackageName = this@LiveRideAccessibilityService.packageName,
                                        screenGeneration = universalScreenGeneration,
                                        windowGeneration = universalWindowGeneration,
                                    )
                                ) {
                                    traceEvent("universal.ocr discarded_after_extract=true generation_or_window_changed=true")
                                    return@runCatching
                                }
""",
            label = "frescor depois da extracao",
        )

        serviceText += "\n// universal_ocr_freshness_runtime_0_1_120\n"
        serviceFile.writeText(serviceText)
    }

    var testText = testFile.readText()
    if ("windowRoundTripInvalidatesOcrRequest" !in testText) {
        val addition = """

    @Test
    fun passiveObservedWindowBlocksOcrEvenWhenResolvedRootStillShowsRideApp() {
        val token = UniversalFastReadPolicy.createOcrRequestToken(
            observedPackageName = "com.android.systemui",
            resolvedPackageName = "sinet.startup.indriver",
            ownPackageName = "br.com.mapeiaia.rotacerta",
            screenGeneration = 10L,
            windowGeneration = 4L,
        )
        assertEquals(null, token)
    }

    @Test
    fun windowRoundTripInvalidatesOcrRequest() {
        val token = requireNotNull(
            UniversalFastReadPolicy.createOcrRequestToken(
                observedPackageName = "sinet.startup.indriver",
                resolvedPackageName = "sinet.startup.indriver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                screenGeneration = 10L,
                windowGeneration = 4L,
            ),
        )
        assertFalse(
            UniversalFastReadPolicy.isOcrRequestFresh(
                token = token,
                observedPackageName = "sinet.startup.indriver",
                resolvedPackageName = "sinet.startup.indriver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                screenGeneration = 10L,
                windowGeneration = 6L,
            ),
        )
    }

    @Test
    fun sameWindowAndScreenGenerationKeepsOcrFresh() {
        val token = requireNotNull(
            UniversalFastReadPolicy.createOcrRequestToken(
                observedPackageName = "sinet.startup.indriver",
                resolvedPackageName = "sinet.startup.indriver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                screenGeneration = 10L,
                windowGeneration = 4L,
            ),
        )
        assertTrue(
            UniversalFastReadPolicy.isOcrRequestFresh(
                token = token,
                observedPackageName = "sinet.startup.indriver",
                resolvedPackageName = "sinet.startup.indriver",
                ownPackageName = "br.com.mapeiaia.rotacerta",
                screenGeneration = 10L,
                windowGeneration = 4L,
            ),
        )
    }
"""
        testText = ocrFresh120InsertBeforeLastBrace(
            source = testText,
            addition = addition,
            label = "testes de frescor do OCR",
        )
        testFile.writeText(testText)
    }

    listOf(
        "universal_ocr_freshness_policy_0_1_120",
        "universal_ocr_window_generation_0_1_120",
        "universal_ocr_freshness_runtime_0_1_120",
        "request_blocked_observed_window=true",
        "generation_or_window_changed=true",
        "windowRoundTripInvalidatesOcrRequest",
    ).forEach { marker ->
        val present = marker in guardFile.readText() || marker in serviceFile.readText() || marker in testFile.readText()
        if (!present) throw GradleException("Correcao de OCR obsoleto incompleta: $marker")
    }
}

val universalOcrFreshness120 by tasks.registering {
    val serviceFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt",
    )
    val guardFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/UniversalRuntimeGuards.kt",
    )
    val testFile = layout.projectDirectory.file(
        "src/test/java/br/com/mapeiaia/rotacerta/UniversalRuntimeGuardsTest.kt",
    )
    inputs.files(serviceFile, guardFile, testFile)
    outputs.upToDateWhen { false }
    dependsOn("popupGestureValidatorCompat120")
    doLast { applyUniversalOcrFreshness120(serviceFile.asFile, guardFile.asFile, testFile.asFile) }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalOcrFreshness120)
}
