// Contrato operacional final da bolinha:
// - le qualquer tela, sem pacote/app/modelo cadastrado;
// - somente dois ou mais enderecos distintos acionam a analise;
// - fica amarela imediatamente e usa o ultimo endereco;
// - qualquer mudanca de tela invalida e limpa o resultado anterior;
// - cache de rota independe de pacote e modelo de card.

fun replaceUniversalRuntimeRegion(
    source: String,
    startToken: String,
    endToken: String,
    replacement: String,
    label: String,
): String {
    val start = source.indexOf(startToken)
    val end = if (start >= 0) source.indexOf(endToken, start + startToken.length) else -1
    if (start < 0 || end < 0) {
        throw GradleException("Nao encontrei a regiao para $label.")
    }
    return source.substring(0, start) + replacement + source.substring(end)
}

fun enforceUniversalTwoAddressRuntime(serviceFile: java.io.File, mainFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
    var text = serviceFile.readText()

    if ("private val universalRouteCache = LiveRideRouteCache()" !in text) {
        val anchor = "    private val registeredCardGate = RegisteredCardDecisionGate()\n"
        if (anchor !in text) throw GradleException("Ponto dos campos universais nao encontrado.")
        text = text.replaceFirst(
            anchor,
            anchor + """    private val universalRouteCache = LiveRideRouteCache()
    private var universalRouteJob: Job? = null
    private var universalScreenGeneration: Long = 0L
    private var universalActiveAddressSignature: String? = null // universal_two_address_fields_0_1_98
""",
        )
    }

    text = replaceUniversalRuntimeRegion(
        text,
        "    override fun onAccessibilityEvent(event: AccessibilityEvent?) {",
        "    override fun onInterrupt()",
        """    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!serviceReady || event == null) return
        if (!currentSettings.appEnabled || !currentSettings.liveReadingEnabled) {
            hardClearUniversalTwoAddress("Leitura universal desligada.")
            return
        }
        val packageName = normalizePackageName(event.packageName?.toString()) ?: currentRootPackageName()
        activePackageName = packageName
        if (packageName == this.packageName) {
            hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
            return
        }
        traceEvent("universal.event package=${'$'}{packageName.orEmpty()} type=${'$'}{event.eventType}") // universal_two_address_event_0_1_98
        scheduleVisibleTextAnalysis(delayMs = 0L, allowPopupCandidate = true)
        requestScreenshotAnalysis(allowPopupCandidate = true)
    }

""",
        "evento universal",
    )

    text = replaceUniversalRuntimeRegion(
        text,
        "    private fun startContinuousScan() {",
        "    private fun startProximityAlertMonitor()",
        """    private fun startContinuousScan() {
        if (continuousScanStarted || !serviceReady) return
        continuousScanStarted = true
        traceEvent("universal.scan.loop interval=${'$'}SCAN_LOOP_MS")
        scope.launch {
            while (serviceReady) {
                if (!currentSettings.appEnabled || !currentSettings.liveReadingEnabled) {
                    hardClearUniversalTwoAddress("Leitura universal desligada.")
                } else if (currentWindowPackageName() == this@LiveRideAccessibilityService.packageName) {
                    hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
                } else {
                    val visibleText = collectVisibleText(allowPopupCandidate = true)
                    processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
                    requestScreenshotAnalysis(allowPopupCandidate = true)
                }
                delay(SCAN_LOOP_MS)
            }
        }
    } // universal_two_address_scan_0_1_98

""",
        "ciclo universal",
    )

    text = replaceUniversalRuntimeRegion(
        text,
        "    private fun scheduleVisibleTextAnalysis(",
        "    private fun requestScreenshotAnalysis(",
        """    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return
        if (currentWindowPackageName() == this.packageName) {
            hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
            return
        }
        analyzeJob?.cancel()
        analyzeJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            val visibleText = collectVisibleText(allowPopupCandidate = true)
            processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
        }
    } // universal_two_address_schedule_0_1_98

""",
        "agendamento universal",
    )

    text = text.replace(
        "        if (!allowPopupCandidate && !shouldScanCurrentWindow()) return \"\"",
        "        if (!serviceReady || currentWindowPackageName() == this.packageName) return \"\"",
    )

    text = replaceUniversalRuntimeRegion(
        text,
        "    private suspend fun processRideText(",
        "    private fun resolveRidePackageForText(",
        """    private suspend fun processRideText(
        text: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
    ) {
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return
        if (currentWindowPackageName() == this.packageName) {
            hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
            return
        }

        val snapshotText = text.trim()
        val trigger = UniversalAddressTrigger.evaluate(snapshotText)
        traceEvent(
            "universal.trigger source=${'$'}source addresses=${'$'}{trigger.addresses.size} active=${'$'}{trigger.active} destination=${'$'}{trigger.destination?.take(100).orEmpty()}",
        ) // universal_two_address_trigger_0_1_98

        if (!trigger.active || trigger.destination.isNullOrBlank()) {
            hardClearUniversalTwoAddress("Menos de dois enderecos visiveis; dado anterior removido.")
            return
        }

        val screenChanged = lastSnapshotHash != trigger.screenHash
        if (screenChanged) {
            universalScreenGeneration += 1L
            universalRouteJob?.cancel()
            universalActiveAddressSignature = trigger.addressSignature
            lastSnapshotHash = trigger.screenHash
            lastAnalyzedHash = null
            pendingAnalysis = null
            analyzing = false
            currentDistanceKm = null
            showOverlay(RadarColor.Default, distanceKm = null)
            traceEvent("universal.screen.changed hash=${'$'}{trigger.screenHash} yellow=true")
        } else if (lastAnalyzedHash == trigger.screenHash || universalRouteJob?.isActive == true) {
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
                screenHash = trigger.screenHash,
                addressSignature = trigger.addressSignature,
                generation = generation,
            )
        }
    } // universal_two_address_process_0_1_98

    private suspend fun analyzeUniversalTwoAddress(
        snapshotText: String,
        fields: RideFields,
        screenHash: Int,
        addressSignature: String,
        generation: Long,
    ) {
        currentSettings = repository.settings.first()
        val settings = currentSettings
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

        val region = DeviceRegion(country = "Brasil")
        val cacheKey = LiveRideRouteCache.keyFor(
            fields = fields,
            settings = settings,
            packageName = null,
            cardSignature = null,
        )
        val cached = universalRouteCache.get(cacheKey)
        if (cached != null) {
            traceEvent("universal.route.cache hit=true age=${'$'}{cached.ageMillis}ms")
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
            applyUniversalTwoAddressResult(cachedResult, screenHash, addressSignature, generation)
            return
        }

        traceEvent("universal.route.cache hit=false")
        val destinationCoordinate = fields.destination?.let { geocodeBest(it, region, settings) }
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

        val homeCoordinate = if (settings.homeTargetEnabled) {
            settings.homeCoordinate ?: settings.homeAddress.takeIf(String::isNotBlank)?.let { geocodeBest(it, region, settings) }
        } else null
        val alternativeCoordinate = if (settings.alternativeTargetEnabled) {
            settings.alternativeCoordinate ?: settings.alternativeAddress.takeIf(String::isNotBlank)?.let { geocodeBest(it, region, settings) }
        } else null
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

        val homeDistanceKm = routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return
        val alternativeDistanceKm = routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings)
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

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
            traceEvent("universal.route.cache stored=true")
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
        applyUniversalTwoAddressResult(result, screenHash, addressSignature, generation)
    }

    private suspend fun applyUniversalTwoAddressResult(
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
        lastAnalyzedHash = screenHash
        repository.addAnalysis(result)
        showOverlay(color, result.nearestConfiguredDistanceKm())
        traceEvent(
            "universal.result applied color=${'$'}{color.diagnosticLabel} km=${'$'}{result.nearestConfiguredDistanceKm()?.toString().orEmpty()}",
        )
    }

    private fun isUniversalResultFresh(
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
            currentWindowPackageName() != this.packageName

    private fun hardClearUniversalTwoAddress(reason: String) {
        val hadData = currentRadarColor != RadarColor.Idle ||
            currentDistanceKm != null ||
            lastSnapshotHash != null ||
            universalActiveAddressSignature != null
        universalScreenGeneration += 1L
        universalRouteJob?.cancel()
        universalRouteJob = null
        universalActiveAddressSignature = null
        lastSnapshotHash = null
        lastAnalyzedHash = null
        pendingAnalysis = null
        analyzing = false
        currentDistanceKm = null
        lastAccessibilityText = ""
        lastOcrText = ""
        showOverlay(RadarColor.Idle, distanceKm = null)
        if (hadData) traceEvent("universal.clear immediate=true reason=${'$'}reason")
    }

""",
        "processamento universal de dois enderecos",
    )

    text = replaceUniversalRuntimeRegion(
        text,
        "    private fun looksLikeRegisteredPopupCandidate(text: String): Boolean {",
        "    private fun rememberSourceText(",
        """    private fun looksLikeRegisteredPopupCandidate(text: String): Boolean =
        UniversalAddressTrigger.evaluate(text).active // universal_two_address_candidate_0_1_98

""",
        "candidato universal",
    )

    text = replaceUniversalRuntimeRegion(
        text,
        "    private fun shouldScanPackage(packageName: String?): Boolean {",
        "    private fun selectedRidePackages(",
        """    private fun shouldScanPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        return serviceReady &&
            currentSettings.appEnabled &&
            currentSettings.liveReadingEnabled &&
            normalized != this.packageName
    } // universal_two_address_all_packages_0_1_98

""",
        "liberacao universal de pacotes",
    )

    text = replaceUniversalRuntimeRegion(
        text,
        "    private suspend fun geocodeBest(",
        "    private suspend fun routeDistanceKm(",
        """    private suspend fun geocodeBest(query: String, region: DeviceRegion, settings: AppSettings): Coordinate? {
        val apiKey = settings.googleMapsApiKey.ifBlank { BuildConfig.GOOGLE_MAPS_API_KEY }
        return googleMapsService.geocode(query, region, apiKey) ?: geocodingService.geocode(query, region)
    } // universal_two_address_geocode_0_1_98

""",
        "geocodificacao universal",
    )

    text = replaceUniversalRuntimeRegion(
        text,
        "    private suspend fun routeDistanceKm(",
        "    private fun AnalysisResult.nearestConfiguredDistanceKm()",
        """    private suspend fun routeDistanceKm(
        origin: Coordinate?,
        destination: Coordinate?,
        settings: AppSettings,
    ): Double? {
        val apiKey = settings.googleMapsApiKey.ifBlank { BuildConfig.GOOGLE_MAPS_API_KEY }
        return if (origin != null && destination != null && apiKey.isNotBlank()) {
            googleMapsService.drivingDistanceKm(origin, destination, apiKey)
        } else null
    } // universal_two_address_route_0_1_98

""",
        "rota universal",
    )

    text = Regex("const val SCAN_LOOP_MS = \\d+L").replace(text, "const val SCAN_LOOP_MS = 120L")
    text = Regex("const val SCREENSHOT_INTERVAL_MS = \\d+L").replace(text, "const val SCREENSHOT_INTERVAL_MS = 300L")

    listOf(
        "universal_two_address_fields_0_1_98",
        "universal_two_address_event_0_1_98",
        "universal_two_address_scan_0_1_98",
        "universal_two_address_schedule_0_1_98",
        "universal_two_address_trigger_0_1_98",
        "universal_two_address_process_0_1_98",
        "universal_two_address_candidate_0_1_98",
        "universal_two_address_all_packages_0_1_98",
        "universal_two_address_geocode_0_1_98",
        "universal_two_address_route_0_1_98",
        "UniversalAddressTrigger.evaluate(snapshotText)",
        "trigger.addresses.size",
        "trigger.destination",
        "showOverlay(RadarColor.Default, distanceKm = null)",
        "hardClearUniversalTwoAddress",
        "universalRouteCache.get",
        "universalRouteCache.put",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Contrato universal incompleto: $marker")
    }
    if ("RideCardTemplateMatcher.match(snapshotText" in text.substring(
            text.indexOf("    private suspend fun processRideText("),
            text.indexOf("    private fun resolveRidePackageForText("),
        )
    ) {
        throw GradleException("Regressao: processo universal ainda exige modelo de card.")
    }
    serviceFile.writeText(text)

    if (mainFile.exists()) {
        var main = mainFile.readText()
        main = main
            .replace(
                "Leitura universal: o ultimo endereco visivel e calculado ate o ponto definido por voce.",
                "Gatilho universal: ao encontrar dois enderecos, usa o ultimo e calcula imediatamente ate o ponto definido por voce.",
            )
            .replace(
                "Operando em qualquer tela. Encontrou endereco, usa sempre o ultimo e calcula ate o ponto definido pelo motorista.",
                "Operando em qualquer tela. Dois enderecos deixam a bolinha amarela; o ultimo vira o destino e inicia o calculo.",
            )
            .replace(
                "Aceite corridas cujo destino final fique dentro do raio definido por voce.",
                "Dois enderecos visiveis acionam a bolinha; o ultimo e calculado ate o ponto definido por voce.",
            )
        if ("universal_two_address_ui_0_1_98" !in main) main += "\n// universal_two_address_ui_0_1_98\n"
        mainFile.writeText(main)
    }
}

val universalTwoAddressRuntimeFinal by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }
    doLast { enforceUniversalTwoAddressRuntime(serviceFile.asFile, mainFile.asFile) }
}

universalTwoAddressRuntimeFinal.configure {
    mustRunAfter(
        tasks.matching { task ->
            task.name != name &&
                !task.name.startsWith("compile") &&
                !task.name.startsWith("test") &&
                task.name !in setOf("preBuild", "assemble", "assembleDebug") &&
                (task.name.contains("patch", true) ||
                    task.name.contains("fix", true) ||
                    task.name.startsWith("enforce", true) ||
                    task.name.contains("final", true))
        },
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalTwoAddressRuntimeFinal)
}

// Em uma segunda chamada do Gradle no mesmo checkout, os arquivos ja contem o contrato final.
// Patches legados que tentariam reescrever o leitor sao ignorados; o contrato final continua sendo validado.
tasks.configureEach {
    val isLegacyMutation = name != "universalTwoAddressRuntimeFinal" &&
        !name.startsWith("compile") &&
        !name.startsWith("test") &&
        name !in setOf("preBuild", "assemble", "assembleDebug") &&
        (name.contains("patch", true) || name.contains("fix", true) || name.startsWith("enforce", true))
    if (isLegacyMutation) {
        onlyIf {
            val source = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile
            !source.exists() || "universal_two_address_process_0_1_98" !in source.readText()
        }
    }
}
