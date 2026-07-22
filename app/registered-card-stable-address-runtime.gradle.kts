// Contrato corretivo sobre o leitor universal 0.1.98:
// - somente apps monitorados;
// - somente card cadastrado e reconhecido;
// - somente os extremos do primeiro card isolado pelo parser;
// - dois enderecos completos e numerados;
// - Acessibilidade e OCR nao disputam o estado visual;
// - resultado identico nao e salvo repetidamente.

fun replaceRegisteredStableRegion(
    source: String,
    startToken: String,
    endToken: String,
    replacement: String,
    label: String,
): String {
    val start = source.indexOf(startToken)
    val end = if (start >= 0) source.indexOf(endToken, start + startToken.length) else -1
    if (start < 0 || end < 0) {
        throw GradleException("Nao encontrei a regiao final para $label.")
    }
    return source.substring(0, start) + replacement + source.substring(end)
}

fun applyRegisteredCardStableAddressRuntime(serviceFile: java.io.File, mainFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
    var text = serviceFile.readText()

    if ("private val stableLiveReadSourceGate = StableLiveReadSourceGate()" !in text) {
        val anchor = "    private var universalActiveAddressSignature: String? = null // universal_two_address_fields_0_1_98\n"
        if (anchor !in text) throw GradleException("Campos do leitor universal nao encontrados.")
        text = text.replaceFirst(
            anchor,
            anchor + """    private val stableLiveReadSourceGate = StableLiveReadSourceGate()
    private var universalActivePackageName: String? = null
    private var universalActiveCardTemplateId: String? = null
    private var universalLastPersistedSignature: String? = null
    private var universalLastPersistedAtMillis: Long = 0L // registered_stable_fields_0_1_99
""",
        )
    }

    text = replaceRegisteredStableRegion(
        text,
        "    override fun onAccessibilityEvent(event: AccessibilityEvent?) {",
        "    override fun onInterrupt()",
        """    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!serviceReady || event == null) return
        if (!currentSettings.appEnabled || !currentSettings.liveReadingEnabled) {
            hardClearUniversalTwoAddress("Leitura ao vivo desligada.")
            return
        }
        val packageName = normalizePackageName(event.packageName?.toString()) ?: currentRootPackageName()
        activePackageName = packageName
        if (!shouldScanPackage(packageName)) {
            hardClearUniversalTwoAddress(scanBlockReason(packageName))
            return
        }
        traceEvent("registered.event package=${'$'}{packageName.orEmpty()} type=${'$'}{event.eventType}") // registered_stable_event_0_1_99
        scheduleVisibleTextAnalysis(delayMs = 0L)
        requestScreenshotAnalysis()
    }

""",
        "evento restrito a apps monitorados",
    )

    text = replaceRegisteredStableRegion(
        text,
        "    private fun startContinuousScan() {",
        "    private fun startProximityAlertMonitor()",
        """    private fun startContinuousScan() {
        if (continuousScanStarted || !serviceReady) return
        continuousScanStarted = true
        traceEvent("registered.scan.loop interval=${'$'}SCAN_LOOP_MS")
        scope.launch {
            while (serviceReady) {
                val packageName = currentWindowPackageName()
                when {
                    !currentSettings.appEnabled || !currentSettings.liveReadingEnabled ->
                        hardClearUniversalTwoAddress("Leitura ao vivo desligada.")
                    !shouldScanPackage(packageName) ->
                        hardClearUniversalTwoAddress(scanBlockReason(packageName))
                    else -> {
                        val visibleText = collectVisibleText()
                        processRideText(visibleText, TextSource.Accessibility)
                        requestScreenshotAnalysis()
                    }
                }
                delay(SCAN_LOOP_MS)
            }
        }
    } // registered_stable_scan_0_1_99

""",
        "ciclo restrito e estavel",
    )

    text = replaceRegisteredStableRegion(
        text,
        "    private fun scheduleVisibleTextAnalysis(",
        "    private fun requestScreenshotAnalysis(",
        """    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return
        if (!shouldScanCurrentWindow()) {
            hardClearUniversalTwoAddress(scanBlockReason(currentWindowPackageName()))
            return
        }
        analyzeJob?.cancel()
        analyzeJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            val visibleText = collectVisibleText()
            processRideText(visibleText, TextSource.Accessibility)
        }
    } // registered_stable_schedule_0_1_99

""",
        "agendamento restrito",
    )

    text = text.replace(
        "        if (!serviceReady || currentWindowPackageName() == this.packageName) return \"\"",
        "        if (!serviceReady || !shouldScanCurrentWindow()) return \"\"",
    )

    text = replaceRegisteredStableRegion(
        text,
        "    private suspend fun processRideText(",
        "    private fun resolveRidePackageForText(",
        """    private suspend fun processRideText(
        text: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
    ) {
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return
        val packageName = currentWindowPackageName()
        if (!shouldScanPackage(packageName)) {
            hardClearUniversalTwoAddress(scanBlockReason(packageName))
            return
        }

        val rawText = text.trim()
        val cardMatch = rawText.takeIf(String::isNotBlank)?.let {
            RideCardTemplateMatcher.match(it, packageName, currentCardTemplates)
        }
        val parsed = parser.parseWithMetadata(rawText, packageName)
        val addressDecision = RegisteredCardAddressGate.evaluate(parsed.fields)
        val validRegisteredCard = cardMatch != null && addressDecision.active && !addressDecision.destination.isNullOrBlank()
        val stableSource = when (source) {
            TextSource.Accessibility -> StableLiveReadSource.Accessibility
            TextSource.Ocr -> StableLiveReadSource.Ocr
        }
        val sourceAction = stableLiveReadSourceGate.submit(stableSource, validRegisteredCard)
        traceEvent(
            "registered.trigger source=${'$'}source action=${'$'}sourceAction model=${'$'}{cardMatch?.template?.name.orEmpty()} addresses=${'$'}{addressDecision.addresses.size} destination=${'$'}{addressDecision.destination?.take(100).orEmpty()}",
        ) // registered_stable_trigger_0_1_99

        when (sourceAction) {
            StableLiveReadAction.Ignore -> return
            StableLiveReadAction.Clear -> {
                val reason = if (cardMatch == null) {
                    "Card atual nao corresponde a nenhum modelo cadastrado; resultado anterior removido."
                } else {
                    "Card perdeu um dos dois enderecos completos e numerados; resultado anterior removido."
                }
                hardClearUniversalTwoAddress(reason, resetSourceGate = false)
                return
            }
            StableLiveReadAction.Analyze -> Unit
        }

        val matchedCard = cardMatch ?: return
        val destination = addressDecision.destination ?: return
        val fields = parsed.fields.copy(
            pickup = addressDecision.pickup,
            destination = destination,
        )
        val snapshotText = listOfNotNull(
            fields.fare?.let { "Valor: ${'$'}it" },
            fields.pickup?.let { "Embarque: ${'$'}it" },
            fields.destination?.let { "Destino: ${'$'}it" },
        ).joinToString("\n")
        val analysisKey = listOf(packageName.orEmpty(), matchedCard.template.id, addressDecision.addressSignature).joinToString("|")
        val screenHash = analysisKey.hashCode()
        val screenChanged = lastSnapshotHash != screenHash ||
            universalActiveAddressSignature != addressDecision.addressSignature ||
            universalActivePackageName != packageName ||
            universalActiveCardTemplateId != matchedCard.template.id

        if (screenChanged) {
            universalScreenGeneration += 1L
            universalRouteJob?.cancel()
            universalActiveAddressSignature = addressDecision.addressSignature
            universalActivePackageName = packageName
            universalActiveCardTemplateId = matchedCard.template.id
            lastSnapshotHash = screenHash
            lastAnalyzedHash = null
            pendingAnalysis = null
            analyzing = false
            currentDistanceKm = null
            registeredCardGate.markSeen()
            showOverlay(RadarColor.Default, distanceKm = null)
            traceEvent("registered.screen.changed hash=${'$'}screenHash yellow=true template=${'$'}{matchedCard.template.id}")
        } else if (lastAnalyzedHash == screenHash || universalRouteJob?.isActive == true) {
            return
        }

        val generation = universalScreenGeneration
        universalRouteJob = scope.launch {
            analyzeRegisteredCardAddress(
                snapshotText = snapshotText,
                fields = fields,
                screenHash = screenHash,
                addressSignature = addressDecision.addressSignature,
                packageName = packageName.orEmpty(),
                cardTemplateId = matchedCard.template.id,
                generation = generation,
            )
        }
    } // registered_stable_process_0_1_99

    private suspend fun analyzeRegisteredCardAddress(
        snapshotText: String,
        fields: RideFields,
        screenHash: Int,
        addressSignature: String,
        packageName: String,
        cardTemplateId: String,
        generation: Long,
    ) {
        currentSettings = repository.settings.first()
        val settings = currentSettings
        if (!isRegisteredResultFresh(generation, screenHash, addressSignature, packageName, cardTemplateId)) return

        val region = DeviceRegion(country = "Brasil")
        val cacheKey = LiveRideRouteCache.keyFor(
            fields = fields,
            settings = settings,
            packageName = packageName,
            cardSignature = cardTemplateId,
        )
        val cached = universalRouteCache.get(cacheKey)
        if (cached != null) {
            traceEvent("registered.route.cache hit=true age=${'$'}{cached.ageMillis}ms")
            val cachedResult = decisionEngine.decide(
                fields = fields,
                settings = settings,
                destinationCoordinate = cached.destinationCoordinate,
                homeCoordinate = cached.homeCoordinate,
                alternativeCoordinate = cached.alternativeCoordinate,
                fullText = snapshotText,
                homeDistanceKm = cached.homeDistanceKm,
                alternativeDistanceKm = cached.alternativeDistanceKm,
            )
            applyRegisteredCardResult(cachedResult, screenHash, addressSignature, packageName, cardTemplateId, generation)
            return
        }

        traceEvent("registered.route.cache hit=false")
        val destinationCoordinate = fields.destination?.let { geocodeBest(it, region, settings) }
        if (!isRegisteredResultFresh(generation, screenHash, addressSignature, packageName, cardTemplateId)) return

        val homeCoordinate = if (settings.homeTargetEnabled) {
            settings.homeCoordinate ?: settings.homeAddress.takeIf(String::isNotBlank)?.let { geocodeBest(it, region, settings) }
        } else null
        val alternativeCoordinate = if (settings.alternativeTargetEnabled) {
            settings.alternativeCoordinate ?: settings.alternativeAddress.takeIf(String::isNotBlank)?.let { geocodeBest(it, region, settings) }
        } else null
        if (!isRegisteredResultFresh(generation, screenHash, addressSignature, packageName, cardTemplateId)) return

        val homeDistanceKm = routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
        if (!isRegisteredResultFresh(generation, screenHash, addressSignature, packageName, cardTemplateId)) return
        val alternativeDistanceKm = routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings)
        if (!isRegisteredResultFresh(generation, screenHash, addressSignature, packageName, cardTemplateId)) return

        if (destinationCoordinate != null) {
            universalRouteCache.put(
                cacheKey,
                LiveRideRouteCache.CachedRoute(
                    destinationCoordinate = destinationCoordinate,
                    homeCoordinate = homeCoordinate,
                    alternativeCoordinate = alternativeCoordinate,
                    homeDistanceKm = homeDistanceKm,
                    alternativeDistanceKm = alternativeDistanceKm,
                ),
            )
            traceEvent("registered.route.cache stored=true")
        }

        val result = decisionEngine.decide(
            fields = fields,
            settings = settings,
            destinationCoordinate = destinationCoordinate,
            homeCoordinate = homeCoordinate,
            alternativeCoordinate = alternativeCoordinate,
            fullText = snapshotText,
            homeDistanceKm = homeDistanceKm,
            alternativeDistanceKm = alternativeDistanceKm,
        )
        applyRegisteredCardResult(result, screenHash, addressSignature, packageName, cardTemplateId, generation)
    }

    private suspend fun applyRegisteredCardResult(
        result: AnalysisResult,
        screenHash: Int,
        addressSignature: String,
        packageName: String,
        cardTemplateId: String,
        generation: Long,
    ) {
        if (!isRegisteredResultFresh(generation, screenHash, addressSignature, packageName, cardTemplateId)) {
            traceEvent("registered.result discarded_stale=true")
            return
        }
        val color = when (result.recommendation) {
            Recommendation.GoodRide -> RadarColor.Green
            Recommendation.OutsideRadius -> RadarColor.Red
            Recommendation.InsufficientData -> RadarColor.Default
        }
        lastAnalyzedHash = screenHash
        lastSavedReadHash = screenHash
        val distanceSignature = result.nearestConfiguredDistanceKm()?.let { String.format(Locale.US, "%.3f", it) }.orEmpty()
        val persistedSignature = listOf(addressSignature, result.recommendation.name, distanceSignature).joinToString("|")
        val now = System.currentTimeMillis()
        if (persistedSignature != universalLastPersistedSignature || now - universalLastPersistedAtMillis >= 30_000L) {
            repository.addAnalysis(result)
            universalLastPersistedSignature = persistedSignature
            universalLastPersistedAtMillis = now
        } else {
            traceEvent("registered.history duplicate_skipped=true")
        }
        showOverlay(color, result.nearestConfiguredDistanceKm())
        traceEvent(
            "registered.result applied color=${'$'}{color.diagnosticLabel} km=${'$'}{result.nearestConfiguredDistanceKm()?.toString().orEmpty()}",
        )
    }

    private fun isRegisteredResultFresh(
        generation: Long,
        screenHash: Int,
        addressSignature: String,
        packageName: String,
        cardTemplateId: String,
    ): Boolean =
        serviceReady &&
            currentSettings.appEnabled &&
            currentSettings.liveReadingEnabled &&
            generation == universalScreenGeneration &&
            screenHash == lastSnapshotHash &&
            addressSignature == universalActiveAddressSignature &&
            packageName == universalActivePackageName &&
            cardTemplateId == universalActiveCardTemplateId &&
            shouldScanPackage(currentWindowPackageName())

    private fun hardClearUniversalTwoAddress(reason: String, resetSourceGate: Boolean = true) {
        val hadData = currentRadarColor != RadarColor.Idle ||
            currentDistanceKm != null ||
            lastSnapshotHash != null ||
            universalActiveAddressSignature != null ||
            universalActivePackageName != null ||
            universalActiveCardTemplateId != null
        universalScreenGeneration += 1L
        universalRouteJob?.cancel()
        universalRouteJob = null
        universalActiveAddressSignature = null
        universalActivePackageName = null
        universalActiveCardTemplateId = null
        lastSnapshotHash = null
        lastAnalyzedHash = null
        pendingAnalysis = null
        analyzing = false
        currentDistanceKm = null
        lastAccessibilityText = ""
        lastOcrText = ""
        registeredCardGate.clear()
        if (resetSourceGate) stableLiveReadSourceGate.reset()
        if (hadData) {
            showOverlay(RadarColor.Idle, distanceKm = null)
            traceEvent("registered.clear immediate=true reason=${'$'}reason")
        }
    }

""",
        "processamento estavel do card cadastrado",
    )

    text = replaceRegisteredStableRegion(
        text,
        "    private fun looksLikeRegisteredPopupCandidate(text: String): Boolean",
        "    private fun rememberSourceText(",
        """    private fun looksLikeRegisteredPopupCandidate(text: String): Boolean {
        val packageName = currentWindowPackageName()
        if (!shouldScanPackage(packageName) || text.isBlank()) return false
        val cardMatch = RideCardTemplateMatcher.match(text, packageName, currentCardTemplates) ?: return false
        val parsed = parser.parseWithMetadata(text, packageName)
        return cardMatch.score > 0.0 && RegisteredCardAddressGate.evaluate(parsed.fields).active
    } // registered_stable_candidate_0_1_99

""",
        "candidato cadastrado",
    )

    text = replaceRegisteredStableRegion(
        text,
        "    private fun shouldScanPackage(packageName: String?): Boolean {",
        "    private fun selectedRidePackages(",
        """    private fun shouldScanPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return false
        if (normalized == this.packageName) return false
        if (normalized in PASSIVE_DIAGNOSTIC_PACKAGES) return false
        if (normalized in IGNORED_PACKAGES) return false
        return normalized in selectedRidePackages(currentSettings)
    } // registered_stable_package_filter_0_1_99

""",
        "filtro de pacotes monitorados",
    )

    text = Regex("const val SCAN_LOOP_MS = \\d+L").replace(text, "const val SCAN_LOOP_MS = 250L")
    text = Regex("const val SCREENSHOT_INTERVAL_MS = \\d+L").replace(text, "const val SCREENSHOT_INTERVAL_MS = 500L")

    listOf(
        "registered_stable_fields_0_1_99",
        "registered_stable_event_0_1_99",
        "registered_stable_scan_0_1_99",
        "registered_stable_schedule_0_1_99",
        "registered_stable_trigger_0_1_99",
        "registered_stable_process_0_1_99",
        "registered_stable_candidate_0_1_99",
        "registered_stable_package_filter_0_1_99",
        "RideCardTemplateMatcher.match(it, packageName, currentCardTemplates)",
        "RegisteredCardAddressGate.evaluate(parsed.fields)",
        "StableLiveReadSourceGate()",
        "registered.history duplicate_skipped=true",
        "normalized in selectedRidePackages(currentSettings)",
        "const val SCAN_LOOP_MS = 250L",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Contrato cadastrado estavel incompleto: $marker")
    }
    serviceFile.writeText(text)

    if (mainFile.exists()) {
        var main = mainFile.readText()
        main = main
            .replace(
                "Gatilho universal: ao encontrar dois enderecos, usa o ultimo e calcula imediatamente ate o ponto definido por voce.",
                "Somente um card cadastrado com dois enderecos completos e numerados libera o calculo.",
            )
            .replace(
                "Operando em qualquer tela. Dois enderecos deixam a bolinha amarela; o ultimo vira o destino e inicia o calculo.",
                "Lendo apenas apps monitorados. O card cadastrado precisa ter embarque e destino completos e numerados.",
            )
            .replace(
                "Dois enderecos visiveis acionam a bolinha; o ultimo e calculado ate o ponto definido por voce.",
                "Card cadastrado com dois enderecos numerados aciona a bolinha; o destino isolado do card e calculado.",
            )
        if ("registered_stable_ui_0_1_99" !in main) main += "\n// registered_stable_ui_0_1_99\n"
        mainFile.writeText(main)
    }
}

val registeredCardStableAddressRuntime by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalTwoAddressRuntimeFinal"))
    doLast { applyRegisteredCardStableAddressRuntime(serviceFile.asFile, mainFile.asFile) }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(registeredCardStableAddressRuntime)
}
