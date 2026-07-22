// Correcao orientada pelo relatorio 0.1.100 (2931):
// - impede leitura antiga de outro app quando o Rota Certa esta em primeiro plano;
// - acessibilidade e OCR deixam de apagar um ao outro;
// - a identidade do card passa a ser a assinatura dos enderecos, nao textos dinamicos;
// - evita dezenas de entradas iguais no historico;
// - separa enderecos quando a acessibilidade achata varios cards em uma linha.

fun replaceUniversalGuardRegion(
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

fun applyUniversalRuntimeStabilityGuard(serviceFile: java.io.File, parserFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado")
    if (!parserFile.exists()) throw GradleException("UniversalScreenAddressParser.kt nao encontrado")

    var service = serviceFile.readText()
    if ("universal_runtime_stability_guard_0_1_101" !in service) {
        val fieldAnchor = "    private var universalActiveAddressSignature: String? = null // universal_two_address_fields_0_1_98\n"
        if (fieldAnchor !in service) throw GradleException("Campos do runtime universal nao encontrados")
        service = service.replaceFirst(
            fieldAnchor,
            fieldAnchor + """    private val universalLiveReadGate = UniversalLiveReadGate()
    private val universalAnalysisDeduper = UniversalAnalysisDeduper()
    private var universalForegroundPackageName: String? = null
    private var universalLastTriggerTraceSignature: String? = null
    private var universalLastTriggerTraceAtMillis: Long = 0L // universal_runtime_stability_fields_0_1_101
""",
        )

        val connectedStart = service.indexOf("    override fun onServiceConnected() {")
        val connectedEnd = if (connectedStart >= 0) service.indexOf("    override fun onAccessibilityEvent", connectedStart) else -1
        if (connectedStart < 0 || connectedEnd <= connectedStart) throw GradleException("onServiceConnected nao encontrado")
        var connectedBlock = service.substring(connectedStart, connectedEnd)
        val settingsAnchor = "            currentSettings = repository.settings.first()\n            currentCardTemplates = repository.cardTemplates.first()\n"
        if (settingsAnchor !in connectedBlock) throw GradleException("Carregamento inicial de configuracoes nao encontrado")
        connectedBlock = connectedBlock.replaceFirst(
            settingsAnchor,
            """            currentSettings = repository.settings.first()
            if (currentSettings.requireRegisteredRideCard) {
                currentSettings = currentSettings.copy(requireRegisteredRideCard = false)
                repository.saveSettings(currentSettings)
            }
            currentCardTemplates = repository.cardTemplates.first() // universal_optional_card_model_migration_0_1_101
""",
        )
        service = service.substring(0, connectedStart) + connectedBlock + service.substring(connectedEnd)

        service = replaceUniversalGuardRegion(
            service,
            "    override fun onAccessibilityEvent(event: AccessibilityEvent?) {",
            "    override fun onInterrupt()",
            """    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!serviceReady || event == null) return
        if (!currentSettings.appEnabled || !currentSettings.liveReadingEnabled) {
            hardClearUniversalTwoAddress("Leitura universal desligada.")
            return
        }

        val eventPackage = normalizePackageName(event.packageName?.toString())
        val rootPackage = currentRootPackageName()
        if (eventPackage == this.packageName || rootPackage == this.packageName) {
            universalForegroundPackageName = this.packageName
            activePackageName = this.packageName
            analyzeJob?.cancel()
            analyzeJob = null
            hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
            return
        }

        val isWindowEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        val resolvedPackage = when {
            rootPackage != null -> rootPackage
            isWindowEvent && eventPackage != null -> eventPackage
            universalForegroundPackageName != null -> universalForegroundPackageName
            else -> eventPackage
        } ?: return

        val previousPackage = universalForegroundPackageName
        universalForegroundPackageName = resolvedPackage
        activePackageName = resolvedPackage
        if (previousPackage != null && previousPackage != resolvedPackage) {
            hardClearUniversalTwoAddress("Aplicativo ou janela alterada; resultado anterior removido.")
        }

        traceEvent("universal.event package=${'$'}resolvedPackage type=${'$'}{event.eventType}") // universal_stable_foreground_event_0_1_101
        scheduleVisibleTextAnalysis(delayMs = 0L, allowPopupCandidate = true)
        requestScreenshotAnalysis(allowPopupCandidate = true)
    }

""",
            "evento com pacote de primeiro plano estavel",
        )

        service = replaceUniversalGuardRegion(
            service,
            "    private fun startContinuousScan() {",
            "    private fun startProximityAlertMonitor()",
            """    private fun startContinuousScan() {
        if (continuousScanStarted || !serviceReady) return
        continuousScanStarted = true
        traceEvent("universal.scan.loop interval=${'$'}SCAN_LOOP_MS")
        scope.launch {
            while (serviceReady) {
                when {
                    !currentSettings.appEnabled || !currentSettings.liveReadingEnabled ->
                        hardClearUniversalTwoAddress("Leitura universal desligada.")
                    !isUniversalExternalWindowActive() ->
                        hardClearUniversalTwoAddress("Tela do proprio Rota Certa ou janela sem leitura valida.")
                    else -> {
                        val expectedPackage = universalResolvedForegroundPackage()
                        val visibleText = collectVisibleText(allowPopupCandidate = true)
                        if (expectedPackage == universalResolvedForegroundPackage() && isUniversalExternalWindowActive()) {
                            processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
                            requestScreenshotAnalysis(allowPopupCandidate = true)
                        }
                    }
                }
                delay(SCAN_LOOP_MS)
            }
        }
    } // universal_stable_scan_0_1_101

""",
            "ciclo universal estavel",
        )

        service = replaceUniversalGuardRegion(
            service,
            "    private fun scheduleVisibleTextAnalysis(",
            "    private fun requestScreenshotAnalysis(",
            """    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return
        if (!isUniversalExternalWindowActive()) return
        val expectedPackage = universalResolvedForegroundPackage() ?: return
        analyzeJob?.cancel()
        analyzeJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            if (!isUniversalExternalWindowActive() || expectedPackage != universalResolvedForegroundPackage()) return@launch
            val visibleText = collectVisibleText(allowPopupCandidate = true)
            if (!isUniversalExternalWindowActive() || expectedPackage != universalResolvedForegroundPackage()) return@launch
            processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
        }
    } // universal_stable_schedule_0_1_101

""",
            "agendamento universal estavel",
        )

        service = replaceUniversalGuardRegion(
            service,
            "    private fun requestScreenshotAnalysis(",
            "    private fun collectVisibleText(",
            """    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {
        if (!serviceReady || !isUniversalExternalWindowActive() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val requestedPackage = universalResolvedForegroundPackage() ?: return
        val now = System.currentTimeMillis()
        if (now - lastScreenshotMillis < SCREENSHOT_INTERVAL_MS) return
        if (!screenshotInProgress.compareAndSet(false, true)) return
        lastScreenshotMillis = now
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            runCatching {
                                if (!isUniversalExternalWindowActive() || requestedPackage != universalResolvedForegroundPackage()) {
                                    traceEvent("universal.ocr discarded_stale_window=true")
                                    return@runCatching
                                }
                                val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching
                                val ocrText = ocrService.extractText(bitmap)
                                if (!isUniversalExternalWindowActive() || requestedPackage != universalResolvedForegroundPackage()) {
                                    traceEvent("universal.ocr discarded_after_extract=true")
                                    return@runCatching
                                }
                                processRideText(ocrText, TextSource.Ocr, allowPopupCandidate = true)
                            }.onFailure { error ->
                                recordDiagnostic(
                                    stage = "screenshot_ocr_error",
                                    reason = "Falha ao ler texto do print da tela.",
                                    error = error,
                                )
                            }
                            screenshotInProgress.set(false)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotInProgress.set(false)
                    }
                },
            )
        }.onFailure {
            screenshotInProgress.set(false)
        }
    } // universal_stable_screenshot_0_1_101

""",
            "OCR vinculado a janela solicitante",
        )

        service = replaceUniversalGuardRegion(
            service,
            "    private fun collectVisibleText(",
            "    private fun collectNodeText(",
            """    private fun collectVisibleText(allowPopupCandidate: Boolean = false): String {
        if (!serviceReady || !isUniversalExternalWindowActive()) return ""
        val root = rootInActiveWindow ?: return ""
        val rootPackage = normalizePackageName(root.packageName?.toString())
        val expectedPackage = universalForegroundPackageName
        if (rootPackage == this.packageName || expectedPackage == this.packageName) return ""
        if (rootPackage != null && expectedPackage != null && rootPackage != expectedPackage) return ""
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        return lines.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")
    } // universal_stable_collect_0_1_101

""",
            "coleta vinculada a janela",
        )

        service = replaceUniversalGuardRegion(
            service,
            "    private suspend fun processRideText(",
            "    private suspend fun analyzeUniversalTwoAddress(",
            """    private suspend fun processRideText(
        text: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
    ) {
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return
        if (!isUniversalExternalWindowActive()) {
            hardClearUniversalTwoAddress("Janela atual nao permite leitura universal.")
            return
        }

        val snapshotText = text.trim()
        val trigger = UniversalAddressTrigger.evaluate(snapshotText)
        traceUniversalTrigger(source, trigger)
        val liveSource = when (source) {
            TextSource.Accessibility -> UniversalLiveReadSource.Accessibility
            TextSource.Ocr -> UniversalLiveReadSource.Ocr
        }
        val activeTrigger = trigger.active && !trigger.destination.isNullOrBlank()
        when (universalLiveReadGate.submit(liveSource, activeTrigger)) {
            UniversalLiveReadAction.Ignore -> {
                traceEvent("universal.source ignored=${'$'}source active=${'$'}activeTrigger")
                return
            }
            UniversalLiveReadAction.Clear -> {
                hardClearUniversalTwoAddress("Menos de dois enderecos numerados na fonte ativa; dado anterior removido.")
                return
            }
            UniversalLiveReadAction.Analyze -> Unit
        }

        val analysisHash = listOf(trigger.addressSignature, trigger.destination.orEmpty()).joinToString("|").hashCode()
        val screenChanged = lastSnapshotHash != analysisHash ||
            universalActiveAddressSignature != trigger.addressSignature
        if (screenChanged) {
            universalScreenGeneration += 1L
            universalRouteJob?.cancel()
            universalActiveAddressSignature = trigger.addressSignature
            lastSnapshotHash = analysisHash
            lastAnalyzedHash = null
            pendingAnalysis = null
            analyzing = false
            currentDistanceKm = null
            rememberBubbleReason("universal_waiting", "Dois enderecos numerados encontrados; calculando o ultimo destino.")
            publishRuntimeValidationTrigger(trigger)
            showOverlay(RadarColor.Default, distanceKm = null)
            traceEvent("universal.screen.changed hash=${'$'}analysisHash yellow=true signature=${'$'}{trigger.addressSignature.hashCode()}")
        } else if (lastAnalyzedHash == analysisHash || universalRouteJob?.isActive == true) {
            return
        }

        val generation = universalScreenGeneration
        val fields = RideFields(
            pickup = trigger.pickup,
            destination = trigger.destination,
        )
        universalRouteJob = scope.launch {
            analyzeUniversalTwoAddress(
                snapshotText = snapshotText,
                fields = fields,
                screenHash = analysisHash,
                addressSignature = trigger.addressSignature,
                generation = generation,
            )
        }
    } // universal_stable_process_0_1_101

""",
            "processamento universal com arbitragem",
        )

        service = replaceUniversalGuardRegion(
            service,
            "    private suspend fun applyUniversalTwoAddressResult(",
            "    private fun isUniversalResultFresh(",
            """    private suspend fun applyUniversalTwoAddressResult(
        result: AnalysisResult,
        screenHash: Int,
        addressSignature: String,
        generation: Long,
    ) {
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) {
            traceEvent("universal.result discarded_stale=true")
            return
        }
        val color = when (result.recommendation) {
            Recommendation.GoodRide -> RadarColor.Green
            Recommendation.OutsideRadius -> RadarColor.Red
            Recommendation.InsufficientData -> RadarColor.Default
        }
        val distanceKm = result.nearestConfiguredDistanceKm()
        lastAnalyzedHash = screenHash
        val persistenceSignature = listOf(
            addressSignature,
            result.recommendation.name,
            distanceKm?.let { String.format(Locale.US, "%.3f", it) }.orEmpty(),
        ).joinToString("|")
        if (universalAnalysisDeduper.shouldPersist(persistenceSignature)) {
            repository.addAnalysis(result)
        } else {
            traceEvent("universal.history duplicate_skipped=true")
        }
        rememberBubbleReason("universal_result", result.reason)
        showOverlay(color, distanceKm)
        traceEvent("universal.result applied color=${'$'}{color.diagnosticLabel} km=${'$'}{distanceKm?.toString().orEmpty()}")
    }

""",
            "resultado universal sem duplicacao",
        )

        service = replaceUniversalGuardRegion(
            service,
            "    private fun isUniversalResultFresh(",
            "    private fun hardClearUniversalTwoAddress(",
            """    private fun isUniversalResultFresh(
        generation: Long,
        screenHash: Int,
        addressSignature: String,
    ): Boolean =
        serviceReady &&
            currentSettings.appEnabled &&
            currentSettings.liveReadingEnabled &&
            generation == universalScreenGeneration &&
            screenHash == lastSnapshotHash &&
            addressSignature == universalActiveAddressSignature &&
            isUniversalExternalWindowActive()

""",
            "frescor universal por janela",
        )

        service = replaceUniversalGuardRegion(
            service,
            "    private fun hardClearUniversalTwoAddress(",
            "    private fun looksLikeRegisteredPopupCandidate(",
            """    private fun hardClearUniversalTwoAddress(reason: String) {
        val hadData = currentRadarColor != RadarColor.Idle ||
            currentDistanceKm != null ||
            lastSnapshotHash != null ||
            universalActiveAddressSignature != null
        val stateChanged = hadData || lastBubbleStateStage != "universal_idle" || lastBubbleStateReason != reason
        universalScreenGeneration += 1L
        universalRouteJob?.cancel()
        universalRouteJob = null
        analyzeJob?.cancel()
        analyzeJob = null
        universalActiveAddressSignature = null
        lastSnapshotHash = null
        lastAnalyzedHash = null
        pendingAnalysis = null
        analyzing = false
        currentDistanceKm = null
        lastAccessibilityText = ""
        lastOcrText = ""
        universalLiveReadGate.reset()
        registeredCardGate.clear()
        if (stateChanged) {
            clearRuntimeValidationTrigger()
            rememberBubbleReason("universal_idle", reason)
            showOverlay(RadarColor.Idle, distanceKm = null)
            currentRadarColor = RadarColor.Idle
            currentDistanceKm = null
            overlayView?.let { view ->
                view.text = ""
                view.textSize = bubbleTextSizeSp("")
                view.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(RadarColor.Idle.argb(currentSettings))
                    setStroke(
                        dp(3),
                        Color.argb(
                            (currentSettings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt(),
                            255,
                            255,
                            255,
                        ),
                    )
                }
                view.contentDescription = "Rota Certa ${'$'}{RadarColor.Idle.diagnosticLabel}"
            }
            if (BuildConfig.DEBUG) {
                bubblePrefs.edit()
                    .putString("runtime_validation_state", "cinza|")
                    .putLong("runtime_validation_state_at", System.currentTimeMillis())
                    .apply()
            }
        }
        if (hadData) traceEvent("universal.clear immediate=true reason=${'$'}reason")
    } // universal_stable_clear_0_1_101

    private fun universalResolvedForegroundPackage(): String? =
        universalForegroundPackageName ?: currentRootPackageName() ?: activePackageName

    private fun isUniversalExternalWindowActive(): Boolean {
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return false
        val rootPackage = currentRootPackageName()
        if (rootPackage == this.packageName || universalForegroundPackageName == this.packageName) return false
        return universalResolvedForegroundPackage()?.let { it != this.packageName } == true
    }

    private fun traceUniversalTrigger(source: TextSource, trigger: UniversalAddressTriggerDecision) {
        val now = System.currentTimeMillis()
        val signature = listOf(source.name, trigger.active.toString(), trigger.addressSignature).joinToString("|")
        if (signature == universalLastTriggerTraceSignature && now - universalLastTriggerTraceAtMillis < 1_000L) return
        universalLastTriggerTraceSignature = signature
        universalLastTriggerTraceAtMillis = now
        traceEvent(
            "universal.trigger source=${'$'}source addresses=${'$'}{trigger.addresses.size} active=${'$'}{trigger.active} destination=${'$'}{trigger.destination?.take(100).orEmpty()}",
        )
    } // universal_runtime_stability_guard_0_1_101

""",
            "limpeza e auxiliares universais",
        )

        service = service.replace(
            "            registeredCardRequired = true,",
            "            registeredCardRequired = false,",
        )
    }

    listOf(
        "universal_runtime_stability_fields_0_1_101",
        "universal_optional_card_model_migration_0_1_101",
        "universal_stable_foreground_event_0_1_101",
        "universal_stable_scan_0_1_101",
        "universal_stable_schedule_0_1_101",
        "universal_stable_screenshot_0_1_101",
        "universal_stable_collect_0_1_101",
        "universal_stable_process_0_1_101",
        "universal.history duplicate_skipped=true",
        "universal_stable_clear_0_1_101",
        "universal_runtime_stability_guard_0_1_101",
        "UniversalLiveReadGate()",
        "UniversalAnalysisDeduper()",
        "registeredCardRequired = false",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Guarda universal incompleta: $marker")
    }
    serviceFile.writeText(service)

    var parser = parserFile.readText()
    if ("universal_flattened_line_split_0_1_101" !in parser) {
        val oldLines = """        val lines = text.lines()
            .map(::normalizeLine)
            .filter { it.length >= 4 }
"""
        val newLines = """        val lines = text.lines()
            .flatMap(::splitAddressSegments)
            .filter { it.length >= 4 }
"""
        if (oldLines !in parser) throw GradleException("Preparacao de linhas do parser nao encontrada")
        parser = parser.replaceFirst(oldLines, newLines)

        val helperAnchor = "    private fun cleanAddressSegment(value: String): String {\n"
        if (helperAnchor !in parser) throw GradleException("Ponto dos auxiliares do parser nao encontrado")
        val helpers = """    private fun splitAddressSegments(value: String): List<String> {
        val normalized = normalizeLine(value)
        if (normalized.length < 4) return emptyList()
        val starts = streetStartRegex.findAll(normalized)
            .mapNotNull { match -> match.groups[1]?.range?.first }
            .distinct()
            .toList()
        if (starts.size <= 1) return listOf(normalized)

        return starts.mapIndexedNotNull { index, start ->
            val end = starts.getOrNull(index + 1) ?: normalized.length
            trimFlattenedAddressSegment(normalized.substring(start, end))
                .takeIf { it.length >= 4 }
        }
    }

    private fun trimFlattenedAddressSegment(value: String): String {
        val cleaned = value.trim(' ', ',', '-', '–', '—')
        var depth = 0
        var sawOpeningParenthesis = false
        for (index in cleaned.indices) {
            when (cleaned[index]) {
                '(' -> {
                    depth += 1
                    sawOpeningParenthesis = true
                }
                ')' -> {
                    if (depth > 0) depth -= 1
                    if (sawOpeningParenthesis && depth == 0) {
                        val suffix = cleaned.substring(index + 1).trim()
                        val localitySuffix = suffix.startsWith(",") ||
                            stateRegex.containsMatchIn(suffix) ||
                            cepRegex.containsMatchIn(suffix)
                        if (suffix.isNotBlank() && !localitySuffix) {
                            return cleaned.substring(0, index + 1).trim(' ', ',', '-', '–', '—')
                        }
                    }
                }
            }
        }
        return cleaned.trimEnd(' ', ',', '-', '–', '—', '(')
    } // universal_flattened_line_split_0_1_101

"""
        parser = parser.replaceFirst(helperAnchor, helpers + helperAnchor)
    }

    listOf(
        ".flatMap(::splitAddressSegments)",
        "trimFlattenedAddressSegment",
        "universal_flattened_line_split_0_1_101",
    ).forEach { marker ->
        if (marker !in parser) throw GradleException("Parser achatado incompleto: $marker")
    }
    parserFile.writeText(parser)
}

val universalRuntimeStabilityGuard by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val parserFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt")
    inputs.files(serviceFile, parserFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalImmediateGrayClear"))
    doLast { applyUniversalRuntimeStabilityGuard(serviceFile.asFile, parserFile.asFile) }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalRuntimeStabilityGuard)
}
