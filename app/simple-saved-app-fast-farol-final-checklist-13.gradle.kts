// Checklist 13 — contrato simples definitivo:
// aplicativo ensinado + dois endereços = último endereço e rota imediata.

fun replaceFunctionSimpleFarol13(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Função ausente no checklist 13: $signature")
    val open = source.indexOf('{', start)
    if (open < 0) throw GradleException("Corpo ausente no checklist 13: $signature")
    var depth = 0
    var index = open
    while (index < source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return source.substring(0, start) + replacement + source.substring(index + 1)
            }
        }
        index += 1
    }
    throw GradleException("Fim da função ausente no checklist 13: $signature")
}

fun patchSimpleFarolGoogleMaps13(file: java.io.File) {
    if (!file.exists()) throw GradleException("GoogleMapsService.kt ausente no checklist 13.")
    var text = file.readText()
    if ("simple_cached_route_peek_checklist_13" !in text) {
        val anchor = "    private fun requestDrivingDistance(body: String, apiKey: String): Double? {\n"
        if (anchor !in text) throw GradleException("Ponto do cache rápido Google Maps ausente.")
        val helper = """    fun cachedDrivingDistancesFromAddressKm(
        originAddress: String,
        destinations: List<Coordinate>,
    ): List<Double?>? {
        if (originAddress.isBlank() || destinations.isEmpty()) return null
        val normalizedOrigin = normalizeAddress(originAddress)
        val result = MutableList<Double?>(destinations.size) { null }
        destinations.forEachIndexed { index, destination ->
            val cacheKey = addressRouteKey(normalizedOrigin, destination)
            val cached = addressRouteCache[cacheKey]
                ?: readPersistentDistance(PERSISTENT_ADDRESS_ROUTE_PREFIX, cacheKey, ROUTE_CACHE_TTL_MS)
                ?: return null
            addressRouteCache[cacheKey] = cached
            result[index] = cached
        }
        return result
    } // simple_cached_route_peek_checklist_13

"""
        text = text.replaceFirst(anchor, helper + anchor)
    }
    text = text
        .replace("const val CONNECT_TIMEOUT_MS = 1_200", "const val CONNECT_TIMEOUT_MS = 450 // fast_network_connect_budget_checklist_13")
        .replace("const val READ_TIMEOUT_MS = 2_600", "const val READ_TIMEOUT_MS = 900 // fast_network_read_budget_checklist_13")
    file.writeText(text)
}

fun patchSimpleFarolModels13(file: java.io.File) {
    if (!file.exists()) throw GradleException("Models.kt ausente no checklist 13.")
    val text = file.readText().replace(
        "val requireRegisteredRideCard: Boolean = true,",
        "val requireRegisteredRideCard: Boolean = false, // simple_saved_app_default_checklist_13",
    )
    file.writeText(text)
}

fun patchSimpleFarolReport13(file: java.io.File) {
    if (!file.exists()) throw GradleException("ManualTechnicalReportBuilder.kt ausente no checklist 13.")
    var text = file.readText()
    text = text.replace(
        "appendLine(\"Modelo de card obrigatorio: ${'$'}{settings.requireRegisteredRideCard}\")",
        "appendLine(\"Modelo visual bloqueia o farol: false; modelos são apenas apoio\")\n            appendLine(\"Politica do farol: aplicativo salvo + dois ou mais enderecos; o ultimo e o destino\")",
    )
    if ("Tempo da ultima decisao" !in text) {
        val anchor = "            appendLine(\"Texto do OCR: tamanho=${'$'}{bubble.getInt(KEY_STATE_OCR_TEXT_LENGTH, 0)} hash=${'$'}{bubble.text(KEY_STATE_OCR_TEXT_HASH)}\")\n"
        if (anchor !in text) throw GradleException("Estado do OCR ausente no relatório 13.")
        text = text.replaceFirst(
            anchor,
            anchor + """            appendLine("Tempo da ultima decisao: ${'$'}{bubble.getLong(\"fast_farol_last_elapsed_ms\", -1L).takeIf { it >= 0L }?.toString()?.plus(\" ms\") ?: \"nao registrado\"}")
            appendLine("Caminho da ultima decisao: ${'$'}{bubble.getString(\"fast_farol_last_path\", null) ?: \"nao registrado\"}")
            appendLine("Ultimo destino calculado: ${'$'}{bubble.getString(\"fast_farol_last_destination\", null) ?: \"nao registrado\"}")
""",
        )
    }
    file.writeText(text)
}

fun patchSimpleFarolService13(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no checklist 13.")
    var service = file.readText()

    if ("import kotlinx.coroutines.CoroutineStart" !in service) {
        service = service.replaceFirst(
            "import kotlinx.coroutines.CoroutineScope\n",
            "import kotlinx.coroutines.CoroutineScope\nimport kotlinx.coroutines.CoroutineStart\n",
        )
    }

    if ("simple_saved_app_fields_checklist_13" !in service) {
        val anchor = "    private var universalActiveAddressSignature: String? = null // universal_two_address_fields_0_1_98\n"
        if (anchor !in service) throw GradleException("Campos universais ausentes no checklist 13.")
        service = service.replaceFirst(
            anchor,
            anchor + """    private var lastImmediateScreenFingerprintChecklist13: Int? = null
    private var lastImmediateScreenPackageChecklist13: String? = null
    private var fastFarolStartedAtChecklist13: Long = 0L // simple_saved_app_fields_checklist_13
""",
        )
    }

    val onEvent = """    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!serviceReady || event == null) return
        if (!currentSettings.appEnabled || !currentSettings.liveReadingEnabled) {
            hardClearUniversalTwoAddress("Leitura universal desligada.")
            return
        }
        if (!AccessibilityEventFloodGate.isRelevantEventType(event.eventType)) return

        val eventPackage = normalizePackageName(event.packageName?.toString())
        val rootPackage = currentRootPackageName()
        val candidatePackage = eventPackage ?: rootPackage
        val ownMainActivityEvent = UniversalWindowPackageResolver.isOwnMainActivityEvent(
            eventPackageName = candidatePackage,
            eventClassName = event.className?.toString(),
            eventType = event.eventType,
            ownPackageName = this.packageName,
            mainActivityClassName = MainActivity::class.java.name,
            windowStateChangedType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        )
        if (candidatePackage == this.packageName) {
            if (ownMainActivityEvent) {
                universalForegroundPackageName = this.packageName
                activePackageName = this.packageName
                hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
            }
            return
        }

        val savedPackages = SelectedRideAppStore.read(applicationContext)
        var resolvedPackage = candidatePackage ?: lastExternalWindowPackageName ?: return
        SelectedRideOverlayWindowPolicy.resolve(
            rootPackageName = resolvedPackage,
            lastSelectedPackageName = recentSelectedRidePackageChecklist11,
            lastSelectedAtMillis = recentSelectedRidePackageAtMillisChecklist11,
            selectedPackages = savedPackages,
            nowMillis = System.currentTimeMillis(),
        )?.let { resolvedPackage = it }

        if (resolvedPackage !in savedPackages || !shouldScanPackage(resolvedPackage)) {
            universalForegroundPackageName = resolvedPackage
            activePackageName = resolvedPackage
            lastExternalWindowPackageName = resolvedPackage
            lastImmediateScreenFingerprintChecklist13 = null
            lastImmediateScreenPackageChecklist13 = null
            hardClearUniversalTwoAddress(scanBlockReason(resolvedPackage))
            return
        }

        recentSelectedRidePackageChecklist11 = resolvedPackage
        recentSelectedRidePackageAtMillisChecklist11 = System.currentTimeMillis()
        universalForegroundPackageName = resolvedPackage
        activePackageName = resolvedPackage
        lastExternalWindowPackageName = resolvedPackage

        val immediateTextChecklist13 = collectImmediateVisibleTextChecklist13()
        val fingerprintChecklist13 = SimpleSavedAppFarolPolicy.screenFingerprint(
            packageName = resolvedPackage,
            text = immediateTextChecklist13,
            windowId = event.windowId,
        )
        val screenChangedChecklist13 = lastImmediateScreenPackageChecklist13 != null &&
            (lastImmediateScreenPackageChecklist13 != resolvedPackage ||
                SimpleSavedAppFarolPolicy.changed(lastImmediateScreenFingerprintChecklist13, fingerprintChecklist13))
        if (screenChangedChecklist13) {
            hardClearUniversalTwoAddress(
                reason = "A tela mudou; cor e quilometros anteriores removidos imediatamente.",
                keepWaitingYellow = true,
            ) // immediate_screen_change_clear_checklist_13
            universalForegroundPackageName = resolvedPackage
            activePackageName = resolvedPackage
            lastExternalWindowPackageName = resolvedPackage
        }
        lastImmediateScreenPackageChecklist13 = resolvedPackage
        lastImmediateScreenFingerprintChecklist13 = fingerprintChecklist13

        if (immediateTextChecklist13.isBlank()) {
            hardClearUniversalTwoAddress(
                reason = "Tela alterada sem dois enderecos visiveis; resultado removido imediatamente.",
                keepWaitingYellow = true,
            )
            scheduleScreenshotFallback127(resolvedPackage)
            return
        }

        val quickEvaluationChecklist13 = SimpleSavedAppFarolPolicy.evaluate(
            packageName = resolvedPackage,
            savedPackages = savedPackages,
            text = immediateTextChecklist13,
        )
        analyzeJob?.cancel()
        analyzeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            processRideText(immediateTextChecklist13, TextSource.Accessibility, allowPopupCandidate = true)
        } // immediate_accessibility_process_checklist_13
        if (quickEvaluationChecklist13.active) {
            screenshotFallbackJob127?.cancel()
            screenshotFallbackJob127 = null
        } else {
            scheduleScreenshotFallback127(resolvedPackage)
        }
    } // simple_saved_app_event_contract_checklist_13
"""
    service = replaceFunctionSimpleFarol13(service, "    override fun onAccessibilityEvent(event: AccessibilityEvent?)", onEvent)

    if ("simple_saved_app_helpers_checklist_13" !in service) {
        val processAnchor = "    private suspend fun processRideText(\n"
        val processIndex = service.indexOf(processAnchor)
        if (processIndex < 0) throw GradleException("processRideText ausente para helpers 13.")
        val helpers = """    private data class FastWorkRegionTargetsChecklist13(
        val homeCoordinate: Coordinate?,
        val pins: List<WorkRegionPin>,
    ) {
        val destinations: List<Coordinate>
            get() = buildList {
                homeCoordinate?.let(::add)
                pins.mapNotNull(WorkRegionPin::coordinate).forEach(::add)
            }
    }

    private fun collectImmediateVisibleTextChecklist13(): String {
        val root = rootInActiveWindow ?: return ""
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        return lines.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n")
    }

    private fun fastWorkRegionTargetsChecklist13(settings: AppSettings): FastWorkRegionTargetsChecklist13 {
        val home = settings.homeCoordinate.takeIf { settings.homeTargetEnabled }
        val pins = if (settings.alternativeTargetEnabled) {
            WorkRegionTargetPolicy.editablePins(settings)
                .filter { it.enabled && it.coordinate != null }
        } else {
            emptyList()
        }
        return FastWorkRegionTargetsChecklist13(homeCoordinate = home, pins = pins)
    }

    private fun decideFastWorkRegionChecklist13(
        snapshotText: String,
        fields: RideFields,
        settings: AppSettings,
        targets: FastWorkRegionTargetsChecklist13,
        routeDistances: List<Double?>,
    ): AnalysisResult {
        var routeIndex = 0
        val homeDistanceKm = if (targets.homeCoordinate != null) routeDistances.getOrNull(routeIndex++) else null
        val pinRoutes = targets.pins.map { pin ->
            WorkRegionPinRoute(
                pin = pin,
                distanceKm = if (pin.coordinate != null) routeDistances.getOrNull(routeIndex++) else null,
            )
        }
        return decisionEngine.decideWorkRegion(
            fields = fields,
            settings = settings,
            fullText = snapshotText,
            homeTargetActive = targets.homeCoordinate != null,
            homeDistanceKm = homeDistanceKm,
            pinRoutes = pinRoutes,
        )
    }

    // simple_saved_app_helpers_checklist_13

"""
        service = service.substring(0, processIndex) + helpers + service.substring(processIndex)
    }

    val processReplacement = """    private suspend fun processRideText(
        text: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
    ) {
        @Suppress("UNUSED_VARIABLE") val ignoredPopupCandidateChecklist13 = allowPopupCandidate
        if (bubbleGestureActive || !serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return
        val savedPackagesChecklist13 = SelectedRideAppStore.read(applicationContext)
        val selectedPackageChecklist13 = strictSelectedRootPackageChecklist1()
            ?: normalizePackageName(universalResolvedForegroundPackage())
                ?.takeIf { it in savedPackagesChecklist13 }
            ?: run {
                hardClearUniversalTwoAddress("Aplicativo nao ensinado; leitura e rota bloqueadas.")
                return
            }

        val snapshotTextChecklist13 = text.trim()
        val evaluationChecklist13 = withContext(Dispatchers.Default) {
            SimpleSavedAppFarolPolicy.evaluate(
                packageName = selectedPackageChecklist13,
                savedPackages = savedPackagesChecklist13,
                text = snapshotTextChecklist13,
            )
        }
        if (!evaluationChecklist13.active) {
            hardClearUniversalTwoAddress(
                reason = "Tela sem dois enderecos validos; cor e quilometros removidos imediatamente.",
                keepWaitingYellow = true,
            ) // simple_two_address_clear_checklist_13
            return
        }

        if (source == TextSource.Accessibility) {
            lastAccessibilityAcceptedAtMillis127 = System.currentTimeMillis()
            screenshotFallbackJob127?.cancel()
            screenshotFallbackJob127 = null
        }
        universalLastActiveReadAtMillis = System.currentTimeMillis()
        val fieldsChecklist13 = RideFields(
            pickup = evaluationChecklist13.pickup,
            destination = evaluationChecklist13.destination,
        )
        val cardChangedChecklist13 = universalActiveAddressSignature != evaluationChecklist13.addressSignature ||
            lastSnapshotHash != evaluationChecklist13.screenHash
        if (cardChangedChecklist13 && (
                universalActiveAddressSignature != null ||
                    currentDistanceKm != null ||
                    currentRadarColor == RadarColor.Green ||
                    currentRadarColor == RadarColor.Red
            )
        ) {
            hardClearUniversalTwoAddress(
                reason = "Novo endereco detectado; resultado anterior removido imediatamente.",
                keepWaitingYellow = true,
            )
        }

        universalActiveRidePackageName = selectedPackageChecklist13
        universalActiveAddressSignature = evaluationChecklist13.addressSignature
        lastSnapshotHash = evaluationChecklist13.screenHash
        manualActiveCardTemplateId127 = null
        registeredCardGate.markSeen()

        if (cardChangedChecklist13) {
            universalScreenGeneration += 1L
            universalRouteJob?.cancel()
            universalRouteJob = null
            lastAnalyzedHash = null
            currentDistanceKm = null
            fastFarolStartedAtChecklist13 = System.currentTimeMillis()
            bubblePrefs.edit()
                .putLong("fast_farol_started_at", fastFarolStartedAtChecklist13)
                .putString("fast_farol_last_destination", fieldsChecklist13.destination.orEmpty())
                .apply()
        } else if (lastAnalyzedHash == evaluationChecklist13.screenHash || universalRouteJob?.isActive == true) {
            return
        }

        val settingsChecklist13 = currentSettings
        val targetsChecklist13 = fastWorkRegionTargetsChecklist13(settingsChecklist13)
        if (targetsChecklist13.destinations.isEmpty()) {
            rememberBubbleReason("work_region_missing", "Configure Casa ou pelo menos um alfinete com coordenada validada.")
            showOverlay(RadarColor.Default, distanceKm = null)
            return
        }

        val cachedDistancesChecklist13 = googleMapsService.cachedDrivingDistancesFromAddressKm(
            originAddress = fieldsChecklist13.destination.orEmpty(),
            destinations = targetsChecklist13.destinations,
        )
        val generationChecklist13 = universalScreenGeneration
        if (cachedDistancesChecklist13 != null) {
            val cachedResultChecklist13 = decideFastWorkRegionChecklist13(
                snapshotText = snapshotTextChecklist13,
                fields = fieldsChecklist13,
                settings = settingsChecklist13,
                targets = targetsChecklist13,
                routeDistances = cachedDistancesChecklist13,
            )
            bubblePrefs.edit().putString("fast_farol_last_path", "cache_exato").apply()
            applyUniversalTwoAddressResult(
                cachedResultChecklist13,
                evaluationChecklist13.screenHash,
                evaluationChecklist13.addressSignature,
                generationChecklist13,
            ) // exact_cache_before_yellow_checklist_13
            return
        }

        rememberBubbleReason("universal_waiting", "Dois enderecos identificados; calculando o ultimo destino.")
        showOverlay(RadarColor.Default, distanceKm = null)
        bubblePrefs.edit().putString("fast_farol_last_path", "rota_google").apply()
        universalRouteJob = scope.launch {
            analyzeUniversalTwoAddress(
                snapshotText = snapshotTextChecklist13,
                fields = fieldsChecklist13,
                screenHash = evaluationChecklist13.screenHash,
                addressSignature = evaluationChecklist13.addressSignature,
                generation = generationChecklist13,
            )
        }
    } // simple_saved_app_process_checklist_13
"""
    service = replaceFunctionSimpleFarol13(service, "    private suspend fun processRideText(", processReplacement)

    val analyzeReplacement = """    private suspend fun analyzeUniversalTwoAddress(
        snapshotText: String,
        fields: RideFields,
        screenHash: Int,
        addressSignature: String,
        generation: Long,
    ) {
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return
        val settingsChecklist13 = currentSettings
        val apiKeyChecklist13 = GoogleMapsApiKeyPolicy.effective(
            settingsChecklist13.googleMapsApiKey,
            BuildConfig.GOOGLE_MAPS_API_KEY,
        )
        if (apiKeyChecklist13.isBlank()) {
            rememberBubbleReason("google_maps_api_required", "Configure a Chave Google Maps API para calcular a rota.")
            showOverlay(RadarColor.Default, distanceKm = null)
            return
        }
        val targetsChecklist13 = fastWorkRegionTargetsChecklist13(settingsChecklist13)
        if (targetsChecklist13.destinations.isEmpty()) {
            rememberBubbleReason("work_region_missing", "Configure Casa ou pelo menos um alfinete com coordenada validada.")
            showOverlay(RadarColor.Default, distanceKm = null)
            return
        }
        val routeDistancesChecklist13 = googleMapsService.drivingDistancesFromAddressKm(
            originAddress = fields.destination.orEmpty(),
            destinations = targetsChecklist13.destinations,
            apiKey = apiKeyChecklist13,
        ) // single_exact_route_matrix_checklist_13
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return
        val resultChecklist13 = decideFastWorkRegionChecklist13(
            snapshotText = snapshotText,
            fields = fields,
            settings = settingsChecklist13,
            targets = targetsChecklist13,
            routeDistances = routeDistancesChecklist13,
        )
        applyUniversalTwoAddressResult(resultChecklist13, screenHash, addressSignature, generation)
    } // simple_saved_app_route_checklist_13
"""
    service = replaceFunctionSimpleFarol13(service, "    private suspend fun analyzeUniversalTwoAddress(", analyzeReplacement)

    val applyReplacement = """    private suspend fun applyUniversalTwoAddressResult(
        result: AnalysisResult,
        screenHash: Int,
        addressSignature: String,
        generation: Long,
    ) {
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return
        val colorChecklist13 = when (result.recommendation) {
            Recommendation.GoodRide -> RadarColor.Green
            Recommendation.OutsideRadius -> RadarColor.Red
            Recommendation.InsufficientData -> RadarColor.Default
        }
        val distanceChecklist13 = result.nearestConfiguredDistanceKm()
        lastAnalyzedHash = screenHash
        rememberBubbleReason("universal_result", result.reason)
        showOverlay(colorChecklist13, distanceChecklist13)
        val finishedAtChecklist13 = System.currentTimeMillis()
        val elapsedChecklist13 = if (fastFarolStartedAtChecklist13 > 0L) {
            (finishedAtChecklist13 - fastFarolStartedAtChecklist13).coerceAtLeast(0L)
        } else {
            0L
        }
        bubblePrefs.edit()
            .putLong("fast_farol_last_elapsed_ms", elapsedChecklist13)
            .putLong("fast_farol_last_finished_at", finishedAtChecklist13)
            .putString("fast_farol_last_destination", result.fields.destination.orEmpty())
            .apply() // measured_end_to_end_farol_checklist_13
        val persistenceSignatureChecklist13 = listOf(
            addressSignature,
            result.recommendation.name,
            distanceChecklist13?.let { String.format(Locale.US, "%.3f", it) }.orEmpty(),
        ).joinToString("|")
        if (universalAnalysisDeduper.shouldPersist(persistenceSignatureChecklist13)) {
            scope.launch(Dispatchers.IO) { runCatching { repository.addAnalysis(result) } }
        }
    } // simple_saved_app_apply_checklist_13
"""
    service = replaceFunctionSimpleFarol13(service, "    private suspend fun applyUniversalTwoAddressResult(", applyReplacement)

    val freshReplacement = """    private fun isUniversalResultFresh(
        generation: Long,
        screenHash: Int,
        addressSignature: String,
    ): Boolean {
        val activePackageChecklist13 = normalizePackageName(universalActiveRidePackageName)
            ?: normalizePackageName(universalResolvedForegroundPackage())
        return serviceReady &&
            currentSettings.appEnabled &&
            currentSettings.liveReadingEnabled &&
            generation == universalScreenGeneration &&
            screenHash == lastSnapshotHash &&
            addressSignature == universalActiveAddressSignature &&
            activePackageChecklist13 != null &&
            activePackageChecklist13 in SelectedRideAppStore.read(applicationContext) &&
            shouldScanPackage(activePackageChecklist13)
    } // simple_saved_app_freshness_checklist_13
"""
    service = replaceFunctionSimpleFarol13(service, "    private fun isUniversalResultFresh(", freshReplacement)

    val manualReplacement = """    private fun requestManualRideCardScreenshotChecklist12(attempt: Int) {
        val taughtPackageChecklist13 = SimpleSavedAppFarolPolicy.teachablePackage(
            packageName = currentRootPackageName() ?: recentSelectedRidePackageChecklist11,
            ownPackageName = this.packageName,
        )
        if (taughtPackageChecklist13 == null) {
            manualCardCaptureInProgressChecklist12.set(false)
            toast("Abra o aplicativo de corrida com o card na tela e tente novamente.")
            return
        }
        val selectedPackagesChecklist12 = SelectedRideAppStore.read(applicationContext).toMutableSet()
        if (selectedPackagesChecklist12.add(taughtPackageChecklist13)) {
            SelectedRideAppStore.save(applicationContext, selectedPackagesChecklist12)
        }
        recentSelectedRidePackageChecklist11 = taughtPackageChecklist13
        recentSelectedRidePackageAtMillisChecklist11 = System.currentTimeMillis()
        universalForegroundPackageName = taughtPackageChecklist13
        activePackageName = taughtPackageChecklist13
        lastExternalWindowPackageName = taughtPackageChecklist13
        val packageNameChecklist12 = taughtPackageChecklist13 // capture_teaches_package_checklist_13

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            manualCardCaptureInProgressChecklist12.set(false)
            toast("A captura completa exige Android 11 ou superior.")
            return
        }
        if (!screenshotInProgress.compareAndSet(false, true)) {
            if (attempt < 5) {
                scope.launch {
                    delay(100L)
                    requestManualRideCardScreenshotChecklist12(attempt + 1)
                }
            } else {
                manualCardCaptureInProgressChecklist12.set(false)
                toast("A tela está sendo lida. Tente novamente em um instante.")
            }
            return
        }

        toast("Aplicativo salvo. Capturando o card completo...")
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            var bitmapChecklist12: Bitmap? = null
                            try {
                                bitmapChecklist12 = screenshot.toSoftwareBitmap()
                                val bitmap = bitmapChecklist12 ?: run {
                                    toast("O Android não entregou a imagem da tela.")
                                    return@launch
                                }
                                val textChecklist12 = ocrService.extractText(bitmap)
                                val parsedChecklist12 = parser.parseWithMetadata(textChecklist12, packageNameChecklist12)
                                val simpleEvaluationChecklist13 = withContext(Dispatchers.Default) {
                                    SimpleSavedAppFarolPolicy.evaluate(
                                        packageName = packageNameChecklist12,
                                        savedPackages = selectedPackagesChecklist12,
                                        text = textChecklist12,
                                    )
                                }
                                val fieldsChecklist12 = RideFields(
                                    pickup = simpleEvaluationChecklist13.pickup ?: parsedChecklist12.fields.pickup,
                                    destination = simpleEvaluationChecklist13.destination ?: parsedChecklist12.fields.destination,
                                    fare = parsedChecklist12.fields.fare,
                                )
                                val looksLikeCardChecklist12 = RideCardTemplateMatcher.looksLikeLearnableRideCard(textChecklist12)
                                val evaluationChecklist12 = ManualRideCardCapturePolicy.evaluate(
                                    packageSelected = true,
                                    text = textChecklist12,
                                    bitmapWidth = bitmap.width,
                                    bitmapHeight = bitmap.height,
                                    looksLikeRideCard = looksLikeCardChecklist12,
                                )
                                if (!evaluationChecklist12.canStoreImage) {
                                    toast(evaluationChecklist12.reason)
                                    return@launch
                                }
                                val optionalTemplateChecklist13 = if (evaluationChecklist12.canCreateTemplate) {
                                    RideCardTemplateMatcher.createTemplate(packageNameChecklist12, textChecklist12)
                                } else {
                                    null
                                }
                                val captureChecklist12 = automaticRideCaptureStore129.saveCard(
                                    bitmap = bitmap,
                                    packageName = packageNameChecklist12,
                                    text = textChecklist12,
                                    fields = fieldsChecklist12,
                                    kind = AutomaticRideCaptureKind.Candidate,
                                    matchedTemplateId = optionalTemplateChecklist13?.id,
                                    matchedTemplateName = optionalTemplateChecklist13?.name,
                                    allowIncompleteManual = true,
                                )
                                if (captureChecklist12 == null) {
                                    toast("Não consegui armazenar o print completo.")
                                    return@launch
                                }
                                optionalTemplateChecklist13?.let { repository.addCardTemplate(it) }
                                repository.addCapturedScreen(
                                    CapturedRideScreen(
                                        createdAtMillis = System.currentTimeMillis(),
                                        packageName = packageNameChecklist12,
                                        textHash = textChecklist12.hashCode(),
                                        textPreview = textChecklist12.trim().take(500),
                                        parserName = parsedChecklist12.parserName,
                                        pickup = fieldsChecklist12.pickup,
                                        destination = fieldsChecklist12.destination,
                                        fare = fieldsChecklist12.fare,
                                    ),
                                )
                                toast("Aplicativo e print salvos. O farol usará sempre o último de dois endereços.")
                                processRideText(textChecklist12, TextSource.Ocr, allowPopupCandidate = true)
                            } catch (_: Throwable) {
                                toast("Não consegui concluir a captura manual do card.")
                            } finally {
                                bitmapChecklist12?.recycle()
                                screenshotInProgress.set(false)
                                manualCardCaptureInProgressChecklist12.set(false)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotInProgress.set(false)
                        manualCardCaptureInProgressChecklist12.set(false)
                        toast("O Android bloqueou o print desta tela. Mantenha o card aberto e tente novamente.")
                    }
                },
            )
        }.onFailure {
            screenshotInProgress.set(false)
            manualCardCaptureInProgressChecklist12.set(false)
            toast("Não consegui solicitar o print completo da tela.")
        }
    } // capture_teaches_app_and_triggers_farol_checklist_13
"""
    service = replaceFunctionSimpleFarol13(service, "    private fun requestManualRideCardScreenshotChecklist12(", manualReplacement)

    service = service.replace(
        "Aplicativo selecionado ativo; aguardando um card cadastrado correspondente.",
        "Aplicativo salvo ativo; aguardando dois enderecos na tela.",
    )

    listOf(
        "simple_saved_app_event_contract_checklist_13",
        "immediate_screen_change_clear_checklist_13",
        "simple_saved_app_process_checklist_13",
        "exact_cache_before_yellow_checklist_13",
        "single_exact_route_matrix_checklist_13",
        "simple_saved_app_freshness_checklist_13",
        "capture_teaches_app_and_triggers_farol_checklist_13",
        "measured_end_to_end_farol_checklist_13",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Contrato simples do farol ausente: $marker")
    }
    file.writeText(service)
}

fun patchSimpleSavedAppFastFarol13(root: java.io.File) {
    patchSimpleFarolService13(java.io.File(root, "LiveRideAccessibilityService.kt"))
    patchSimpleFarolGoogleMaps13(java.io.File(root, "GoogleMapsService.kt"))
    patchSimpleFarolModels13(java.io.File(root, "Models.kt"))
    patchSimpleFarolReport13(java.io.File(root, "ManualTechnicalReportBuilder.kt"))
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchSimpleSavedAppFastFarol13(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    doFirst {
        patchSimpleSavedAppFastFarol13(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}
