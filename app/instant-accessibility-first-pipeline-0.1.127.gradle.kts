// Rota Certa 0.1.127
// Caminho rapido seguro:
// - acessibilidade processa primeiro;
// - OCR entra somente como fallback 90 ms depois;
// - as duas rotas configuradas sao consultadas em paralelo;
// - nenhuma aproximacao libera verde.

fun patchInstantAccessibilityFirstPipeline127(serviceFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para o caminho rapido 0.1.127.")

    var service = serviceFile.readText()

    if ("import kotlinx.coroutines.async" !in service) {
        val importAnchor = "import kotlinx.coroutines.cancel\n"
        if (importAnchor !in service) throw GradleException("Import de corrotinas nao encontrado para paralelizar rotas.")
        service = service.replaceFirst(
            importAnchor,
            importAnchor + "import kotlinx.coroutines.async\nimport kotlinx.coroutines.coroutineScope\n",
        )
    }

    if ("deferred_ocr_job_0_1_127" !in service) {
        val fieldAnchor = "    private var analyzeJob: Job? = null\n"
        if (fieldAnchor !in service) throw GradleException("Campo analyzeJob nao encontrado para o fallback de OCR.")
        service = service.replaceFirst(
            fieldAnchor,
            fieldAnchor +
                "    private var screenshotFallbackJob127: Job? = null // deferred_ocr_job_0_1_127\n" +
                "    private var lastAccessibilityAcceptedAtMillis127: Long = 0L // accessibility_first_timestamp_0_1_127\n",
        )
    }

    if ("accessibility_first_ocr_fallback_0_1_127" !in service) {
        val eventStart = service.indexOf("    override fun onAccessibilityEvent(")
        val eventEnd = if (eventStart >= 0) service.indexOf("    override fun onInterrupt()", eventStart) else -1
        if (eventStart < 0 || eventEnd < 0) throw GradleException("Evento de acessibilidade nao encontrado para priorizacao.")
        var eventRegion = service.substring(eventStart, eventEnd)
        val eventAnchor = "        scheduleVisibleTextAnalysis(delayMs = 0L, allowPopupCandidate = true)\n        requestScreenshotAnalysis(allowPopupCandidate = true)\n"
        if (eventAnchor !in eventRegion) throw GradleException("Disparo simultaneo de acessibilidade/OCR nao encontrado.")
        eventRegion = eventRegion.replaceFirst(
            eventAnchor,
            "        scheduleVisibleTextAnalysis(delayMs = 0L, allowPopupCandidate = true)\n" +
                "        scheduleScreenshotFallback127(resolvedPackage) // accessibility_first_ocr_fallback_0_1_127\n",
        )
        service = service.substring(0, eventStart) + eventRegion + service.substring(eventEnd)
    }

    if ("accessibility_confirmed_cancel_ocr_0_1_127" !in service) {
        val analyzeAnchor = """            UniversalLiveReadAction.Analyze -> {
                universalAccessibilityOwnsCard = liveSource == UniversalLiveReadSource.Accessibility
            }
"""
        if (analyzeAnchor !in service) throw GradleException("Confirmacao da fonte ativa nao encontrada.")
        val analyzeReplacement = """            UniversalLiveReadAction.Analyze -> {
                universalAccessibilityOwnsCard = liveSource == UniversalLiveReadSource.Accessibility
                if (universalAccessibilityOwnsCard) {
                    lastAccessibilityAcceptedAtMillis127 = System.currentTimeMillis()
                    screenshotFallbackJob127?.cancel()
                    screenshotFallbackJob127 = null
                } // accessibility_confirmed_cancel_ocr_0_1_127
            }
"""
        service = service.replaceFirst(analyzeAnchor, analyzeReplacement)
    }

    if ("deferred_ocr_fallback_90ms_0_1_127" !in service) {
        val screenshotAnchor = "    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {\n"
        if (screenshotAnchor !in service) throw GradleException("Funcao de OCR nao encontrada para inserir fallback.")
        val fallbackFunction = """    private fun scheduleScreenshotFallback127(expectedPackage: String) {
        screenshotFallbackJob127?.cancel()
        val scheduledAt127 = System.currentTimeMillis()
        screenshotFallbackJob127 = scope.launch {
            delay(90L)
            if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return@launch
            if (expectedPackage != universalResolvedForegroundPackage()) return@launch
            if (!shouldScanPackage(expectedPackage)) return@launch
            if (lastAccessibilityAcceptedAtMillis127 >= scheduledAt127) return@launch
            requestScreenshotAnalysis(allowPopupCandidate = true)
        }
    } // deferred_ocr_fallback_90ms_0_1_127

"""
        service = service.replaceFirst(screenshotAnchor, fallbackFunction + screenshotAnchor)
    }

    if ("deferred_ocr_destroy_cancel_0_1_127" !in service) {
        val destroyAnchor = "        analyzeJob?.cancel()\n        liveAnalysisJob?.cancel()"
        if (destroyAnchor !in service) throw GradleException("Cancelamento de jobs nao encontrado no encerramento do servico.")
        service = service.replaceFirst(
            destroyAnchor,
            "        analyzeJob?.cancel()\n" +
                "        screenshotFallbackJob127?.cancel()\n" +
                "        screenshotFallbackJob127 = null // deferred_ocr_destroy_cancel_0_1_127\n" +
                "        liveAnalysisJob?.cancel()",
        )
    }

    if ("parallel_exact_routes_0_1_127" !in service) {
        val oldRoutes = """        val homeRouteStartedAt = System.currentTimeMillis()
        val homeDistanceKm = routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
        LiveFailureTraceStore.recordRoute(
            label = "home",
            distanceKm = homeDistanceKm,
            elapsedMillis = System.currentTimeMillis() - homeRouteStartedAt,
            packageName = currentWindowPackageName(),
            generation = generation,
            screenHash = screenHash,
        )
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return
        val alternativeRouteStartedAt = System.currentTimeMillis()
        val alternativeDistanceKm = routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings)
        LiveFailureTraceStore.recordRoute(
            label = "alternative",
            distanceKm = alternativeDistanceKm,
            elapsedMillis = System.currentTimeMillis() - alternativeRouteStartedAt,
            packageName = currentWindowPackageName(),
            generation = generation,
            screenHash = screenHash,
        ) // session_diagnostic_alternative_route_v2
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

"""
        if (oldRoutes !in service) throw GradleException("Bloco sequencial de rotas nao encontrado para paralelizacao.")
        val parallelRoutes = """        val (homeRouteResult127, alternativeRouteResult127) = coroutineScope {
            val homeRouteDeferred127 = async {
                val startedAt127 = System.currentTimeMillis()
                val distance127 = routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
                distance127 to (System.currentTimeMillis() - startedAt127)
            }
            val alternativeRouteDeferred127 = async {
                val startedAt127 = System.currentTimeMillis()
                val distance127 = routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings)
                distance127 to (System.currentTimeMillis() - startedAt127)
            }
            homeRouteDeferred127.await() to alternativeRouteDeferred127.await()
        } // parallel_exact_routes_0_1_127
        val homeDistanceKm = homeRouteResult127.first
        val alternativeDistanceKm = alternativeRouteResult127.first
        LiveFailureTraceStore.recordRoute(
            label = "home",
            distanceKm = homeDistanceKm,
            elapsedMillis = homeRouteResult127.second,
            packageName = currentWindowPackageName(),
            generation = generation,
            screenHash = screenHash,
        )
        LiveFailureTraceStore.recordRoute(
            label = "alternative",
            distanceKm = alternativeDistanceKm,
            elapsedMillis = alternativeRouteResult127.second,
            packageName = currentWindowPackageName(),
            generation = generation,
            screenHash = screenHash,
        ) // session_diagnostic_alternative_route_v2
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

"""
        service = service.replaceFirst(oldRoutes, parallelRoutes)
    }

    listOf(
        "deferred_ocr_job_0_1_127",
        "accessibility_first_ocr_fallback_0_1_127",
        "accessibility_confirmed_cancel_ocr_0_1_127",
        "deferred_ocr_fallback_90ms_0_1_127",
        "deferred_ocr_destroy_cancel_0_1_127",
        "parallel_exact_routes_0_1_127",
        "manual_selected_apps_gate_0_1_127",
        "manual_registered_card_gate_0_1_127",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Marcador ausente no caminho rapido 0.1.127: $marker")
    }
    if ("fastInsideResult" in service) {
        throw GradleException("Caminho rapido nao pode liberar verde por aproximacao geometrica.")
    }

    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchInstantAccessibilityFirstPipeline127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
