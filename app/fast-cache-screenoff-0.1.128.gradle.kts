// Rota Certa 0.1.128
// Caminho rapido real para cards curtos e tela bloqueada:
// - rota por endereco direto no Routes API (uma matriz para Casa + Alfinete);
// - geocodificacao deixa de bloquear a primeira cor quando a rota exata ja voltou;
// - cache persistente e aquecimento em segundo plano;
// - systemui/launcher nao apagam um card/resultado valido durante uma janela curta;
// - variante markerless do inDrive continua exigindo modelo manual do mesmo pacote.

fun replaceKotlinFunction128(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Funcao nao encontrada no patch 0.1.128: $signature")
    val braceStart = source.indexOf('{', start)
    if (braceStart < 0) throw GradleException("Corpo nao encontrado no patch 0.1.128: $signature")
    var depth = 0
    var index = braceStart
    while (index < source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) {
                    return source.substring(0, start) + replacement + source.substring(index + 1)
                }
            }
        }
        index += 1
    }
    throw GradleException("Fim da funcao nao encontrado no patch 0.1.128: $signature")
}

fun patchFastCacheScreenOff128(
    serviceFile: java.io.File,
    matcherFile: java.io.File,
) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no patch 0.1.128.")
    if (!matcherFile.exists()) throw GradleException("RideCardTemplateMatcher.kt ausente no patch 0.1.128.")

    var service = serviceFile.readText()
    val dollar = "$"

    service = service.replace(
        "        googleMapsService = GoogleMapsService()\n",
        "        googleMapsService = GoogleMapsService(applicationContext) // persistent_maps_cache_context_0_1_128\n",
    )

    if ("locked_popup_session_guard_0_1_128" !in service) {
        val helperAnchor = "    private fun universalResolvedForegroundPackage(): String? {"
        val helperIndex = service.indexOf(helperAnchor)
        if (helperIndex < 0) throw GradleException("Resolucao de pacote nao encontrada para o guard de tela bloqueada.")
        val helper = """    private fun shouldProtectLockedPopupSession128(incomingPackageName: String?): Boolean {
        val incoming = normalizePackageName(incomingPackageName)
        val transientSystemWindow = incoming == "android" ||
            incoming == "com.android.systemui" ||
            incoming == "com.samsung.android.systemui" ||
            incoming?.contains("launcher") == true ||
            incoming?.contains("keyguard") == true
        if (!transientSystemWindow) return false
        val activeRidePackage = normalizePackageName(universalActiveRidePackageName) ?: return false
        if (!shouldScanPackage(activeRidePackage)) return false
        if (universalActiveAddressSignature == null && currentRadarColor != RadarColor.Green && currentRadarColor != RadarColor.Red) return false
        val now = System.currentTimeMillis()
        return universalLastActiveReadAtMillis > 0L &&
            now >= universalLastActiveReadAtMillis &&
            now - universalLastActiveReadAtMillis <= 10_000L
    } // locked_popup_session_guard_0_1_128

"""
        service = service.substring(0, helperIndex) + helper + service.substring(helperIndex)
    }

    if ("blocked_systemui_preserves_card_0_1_128" !in service) {
        val eventStart = service.indexOf("    override fun onAccessibilityEvent(event: AccessibilityEvent?) {")
        val blockedStart = if (eventStart >= 0) service.indexOf("        if (!shouldScanPackage(resolvedPackage)) {", eventStart) else -1
        val blockedEnd = if (blockedStart >= 0) service.indexOf("        if (accessibilityEventFloodGate.classify(", blockedStart) else -1
        if (blockedStart < 0 || blockedEnd < 0) throw GradleException("Portao de pacote bloqueado nao encontrado para 0.1.128.")
        val newBlock = """        if (!shouldScanPackage(resolvedPackage)) {
            if (shouldProtectLockedPopupSession128(resolvedPackage)) {
                traceEvent("universal.foreground protected_locked_popup=true incoming=${dollar}resolvedPackage active=${dollar}{universalActiveRidePackageName.orEmpty()}")
                return // blocked_systemui_preserves_card_0_1_128
            }
            if (universalForegroundPackageName != resolvedPackage) universalWindowGeneration += 1L
            universalForegroundPackageName = resolvedPackage
            activePackageName = resolvedPackage
            lastExternalWindowPackageName = resolvedPackage
            hardClearUniversalTwoAddress(scanBlockReason(resolvedPackage)) // universal_package_block_reason_0_1_126
            return
        }
"""
        service = service.substring(0, blockedStart) + newBlock + service.substring(blockedEnd)
    }

    if ("transient_empty_locked_popup_ignored_0_1_128" !in service) {
        val anchor = """        if (!shouldScanCurrentWindow()) {
            hardClearUniversalTwoAddress(scanBlockReason(currentWindowPackageName())) // universal_process_block_reason_0_1_126
            return // selected_apps_process_gate_0_1_122
        }

"""
        if (anchor !in service) throw GradleException("Portao do processRideText nao encontrado para proteger leitura vazia.")
        val replacement = anchor + """        if (text.isBlank() && shouldProtectLockedPopupSession128(currentRootPackageName())) {
            traceEvent("universal.accessibility locked_popup_empty_ignored=true")
            return // transient_empty_locked_popup_ignored_0_1_128
        }

"""
        service = service.replaceFirst(anchor, replacement)
    }

    val resolverReplacement = """    private fun universalResolvedForegroundPackage(): String? {
        val resolution = UniversalWindowPackageResolver.resolve(
            rootPackageName = currentRootPackageName(),
            activePackageName = universalForegroundPackageName ?: activePackageName,
            lastExternalPackageName = lastExternalWindowPackageName,
            ownPackageName = this.packageName,
        )
        val resolvedPackage128 = resolution.effectivePackageName
        if (shouldProtectLockedPopupSession128(resolvedPackage128)) {
            val protectedPackage128 = normalizePackageName(universalActiveRidePackageName)
            if (protectedPackage128 != null) {
                lastExternalWindowPackageName = protectedPackage128
                return protectedPackage128 // locked_popup_resolver_preserves_ride_package_0_1_128
            }
        }
        lastExternalWindowPackageName = resolution.lastExternalPackageName
        return resolvedPackage128
    }
"""
    service = replaceKotlinFunction128(
        service,
        "    private fun universalResolvedForegroundPackage(): String?",
        resolverReplacement,
    )

    val freshnessReplacement = """    private fun isUniversalResultFresh(
        generation: Long,
        screenHash: Int,
        addressSignature: String,
    ): Boolean {
        val effectiveRidePackage128 = normalizePackageName(universalActiveRidePackageName)
            ?: normalizePackageName(universalResolvedForegroundPackage())
        return serviceReady &&
            currentSettings.appEnabled &&
            currentSettings.liveReadingEnabled &&
            generation == universalScreenGeneration &&
            screenHash == lastSnapshotHash &&
            addressSignature == universalActiveAddressSignature &&
            manualActiveCardTemplateId127 != null &&
            currentCardTemplates.any { template ->
                template.id == manualActiveCardTemplateId127 &&
                    normalizePackageName(template.packageName) == effectiveRidePackage128
            } &&
            effectiveRidePackage128 != null &&
            shouldScanPackage(effectiveRidePackage128) // locked_popup_result_freshness_0_1_128
    }
"""
    service = replaceKotlinFunction128(
        service,
        "    private fun isUniversalResultFresh(",
        freshnessReplacement,
    )

    val analyzeReplacement = """    private suspend fun analyzeUniversalTwoAddress(
        snapshotText: String,
        fields: RideFields,
        screenHash: Int,
        addressSignature: String,
        generation: Long,
    ) {
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
            traceEvent("universal.route.cache hit=true age=${dollar}{cached.ageMillis}ms")
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
        val destinationQuery = fields.destination.orEmpty().trim()
        val (homeCoordinate, alternativeCoordinate) = coroutineScope {
            val homeDeferred128 = async {
                if (settings.homeTargetEnabled) {
                    settings.homeCoordinate ?: settings.homeAddress.takeIf(String::isNotBlank)?.let { geocodeBest(it, region, settings) }
                } else null
            }
            val alternativeDeferred128 = async {
                if (settings.alternativeTargetEnabled) {
                    settings.alternativeCoordinate ?: settings.alternativeAddress.takeIf(String::isNotBlank)?.let { geocodeBest(it, region, settings) }
                } else null
            }
            homeDeferred128.await() to alternativeDeferred128.await()
        }
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

        val targets128 = mutableListOf<Coordinate>()
        val homeIndex128 = homeCoordinate?.let { coordinate -> targets128.add(coordinate); targets128.lastIndex }
        val alternativeIndex128 = alternativeCoordinate?.let { coordinate -> targets128.add(coordinate); targets128.lastIndex }
        val matrixStarted128 = System.currentTimeMillis()
        val directDistances128 = routeDistancesFromAddressKm(
            originAddress = destinationQuery,
            destinations = targets128,
            settings = settings,
        )
        val matrixElapsed128 = System.currentTimeMillis() - matrixStarted128
        val directHomeDistance128 = homeIndex128?.let { directDistances128.getOrNull(it) }
        val directAlternativeDistance128 = alternativeIndex128?.let { directDistances128.getOrNull(it) }
        val directMatrixComplete128 = targets128.isNotEmpty() &&
            (homeCoordinate == null || directHomeDistance128 != null) &&
            (alternativeCoordinate == null || directAlternativeDistance128 != null)

        if (directMatrixComplete128 && isUniversalResultFresh(generation, screenHash, addressSignature)) {
            LiveFailureTraceStore.recordRoute(
                label = "home_address_matrix",
                distanceKm = directHomeDistance128,
                elapsedMillis = matrixElapsed128,
                packageName = universalActiveRidePackageName,
                generation = generation,
                screenHash = screenHash,
            )
            LiveFailureTraceStore.recordRoute(
                label = "alternative_address_matrix",
                distanceKm = directAlternativeDistance128,
                elapsedMillis = matrixElapsed128,
                packageName = universalActiveRidePackageName,
                generation = generation,
                screenHash = screenHash,
            )
            val directResult128 = decisionEngine.decide(
                fields = fields,
                settings = settings,
                destinationCoordinate = null,
                homeCoordinate = homeCoordinate,
                alternativeCoordinate = alternativeCoordinate,
                fullText = snapshotText,
                homeDistanceKm = directHomeDistance128,
                alternativeDistanceKm = directAlternativeDistance128,
            )
            traceEvent("universal.route.address_matrix success=true elapsed=${dollar}{matrixElapsed128}ms targets=${dollar}{targets128.size}") // direct_address_route_matrix_runtime_0_1_128
            applyUniversalTwoAddressResult(directResult128, screenHash, addressSignature, generation)

            scope.launch {
                val warmedDestination128 = fields.destination?.let { geocodeBest(it, region, settings) }
                if (warmedDestination128 != null) {
                    universalRouteCache.put(
                        cacheKey,
                        LiveRideRouteCache.CachedRoute(
                            destinationCoordinate = warmedDestination128,
                            homeCoordinate = homeCoordinate,
                            alternativeCoordinate = alternativeCoordinate,
                            homeDistanceKm = directHomeDistance128,
                            alternativeDistanceKm = directAlternativeDistance128,
                        ),
                    )
                    val snapshot128 = universalRouteCache.exportSnapshot()
                    bubblePrefs.edit().putString("persistent_exact_route_cache_v1", snapshot128).apply()
                    traceEvent("universal.geocode.background_warm success=true") // background_geocode_warm_0_1_128
                }
            }
            return
        }

        traceEvent("universal.route.address_matrix success=false elapsed=${dollar}{matrixElapsed128}ms fallback=coordinate")
        val destinationGeocodeStartedAt = System.currentTimeMillis()
        val destinationCoordinate = fields.destination?.let { geocodeBest(it, region, settings) }
        LiveFailureTraceStore.recordGeocode(
            label = "destination_fallback",
            query = destinationQuery,
            coordinate = destinationCoordinate?.let { coordinate -> "${dollar}{coordinate.latitude},${dollar}{coordinate.longitude}" },
            elapsedMillis = System.currentTimeMillis() - destinationGeocodeStartedAt,
            packageName = universalActiveRidePackageName,
            generation = generation,
            screenHash = screenHash,
        )
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

        val (fallbackHomeResult128, fallbackAlternativeResult128) = coroutineScope {
            val homeDeferred128 = async {
                if (directHomeDistance128 != null) directHomeDistance128 else routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
            }
            val alternativeDeferred128 = async {
                if (directAlternativeDistance128 != null) directAlternativeDistance128 else routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings)
            }
            homeDeferred128.await() to alternativeDeferred128.await()
        }
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

        if (destinationCoordinate != null && (fallbackHomeResult128 != null || fallbackAlternativeResult128 != null)) {
            universalRouteCache.put(
                cacheKey,
                LiveRideRouteCache.CachedRoute(
                    destinationCoordinate = destinationCoordinate,
                    homeCoordinate = homeCoordinate,
                    alternativeCoordinate = alternativeCoordinate,
                    homeDistanceKm = fallbackHomeResult128,
                    alternativeDistanceKm = fallbackAlternativeResult128,
                ),
            )
            scope.launch(Dispatchers.IO) {
                bubblePrefs.edit()
                    .putString("persistent_exact_route_cache_v1", universalRouteCache.exportSnapshot())
                    .apply()
            }
        }

        val fallbackResult128 = decisionEngine.decide(
            fields = fields,
            settings = settings,
            destinationCoordinate = destinationCoordinate,
            homeCoordinate = homeCoordinate,
            alternativeCoordinate = alternativeCoordinate,
            fullText = snapshotText,
            homeDistanceKm = fallbackHomeResult128,
            alternativeDistanceKm = fallbackAlternativeResult128,
        )
        applyUniversalTwoAddressResult(fallbackResult128, screenHash, addressSignature, generation)
    }
"""
    service = replaceKotlinFunction128(
        service,
        "    private suspend fun analyzeUniversalTwoAddress(",
        analyzeReplacement,
    )

    if ("direct_address_route_helper_0_1_128" !in service) {
        val helperAnchor = "    private suspend fun routeDistanceKm(\n"
        val index = service.indexOf(helperAnchor)
        if (index < 0) throw GradleException("Helper de rota por coordenada nao encontrado.")
        val helper = """    private suspend fun routeDistancesFromAddressKm(
        originAddress: String,
        destinations: List<Coordinate>,
        settings: AppSettings,
    ): List<Double?> {
        val apiKey = settings.googleMapsApiKey.ifBlank { BuildConfig.GOOGLE_MAPS_API_KEY }
        return if (originAddress.isNotBlank() && destinations.isNotEmpty() && apiKey.isNotBlank()) {
            googleMapsService.drivingDistancesFromAddressKm(originAddress, destinations, apiKey)
        } else {
            List(destinations.size) { null }
        }
    } // direct_address_route_helper_0_1_128

"""
        service = service.substring(0, index) + helper + service.substring(index)
    }

    var matcher = matcherFile.readText()
    if ("indrive_markerless_offer_crop_0_1_128" !in matcher) {
        val cropAnchor = """        if (timeCount >= 2 && distanceCount >= 2 && addressCount >= 1) return true
        return false
"""
        if (cropAnchor !in matcher) throw GradleException("Regra final de recorte de card nao encontrada.")
        val cropReplacement = """        if (timeCount >= 2 && distanceCount >= 2 && addressCount >= 1) return true
        val inDriveMarkerlessOffer128 =
            ("pedido de viagem" in normalized || "pedidos de viagem" in normalized) &&
                ("aceitar por" in normalized || "ofereca sua tarifa" in normalized || "preco justo" in normalized) &&
                (addressCount >= 2 || endpointTextLines >= 2)
        if (inDriveMarkerlessOffer128) return true // indrive_markerless_offer_crop_0_1_128
        return false
"""
        matcher = matcher.replaceFirst(cropAnchor, cropReplacement)
    }

    if ("indrive_same_package_family_match_0_1_128" !in matcher) {
        val matchAnchor = """                } else {
                    samePackage &&
                        cropOk &&
                        (structuralOk || "card.route.marked_stops" in match.matchedFeatures) &&
                        match.score >= MIN_SCORE &&
                        match.matchedFeatures.size >= MIN_FEATURES
                }
"""
        if (matchAnchor !in matcher) throw GradleException("Filtro padrao de match nao encontrado.")
        val matchReplacement = """                } else {
                    val standardSamePackageMatch128 = samePackage &&
                        cropOk &&
                        (structuralOk || "card.route.marked_stops" in match.matchedFeatures) &&
                        match.score >= MIN_SCORE &&
                        match.matchedFeatures.size >= MIN_FEATURES
                    val inDriveFamilySignals128 = listOf(
                        "pedido de viagem",
                        "pedidos de viagem",
                        "aceitar por",
                        "ofereca sua tarifa",
                        "preco justo",
                    ).count { signal -> signal in liveFeatures }
                    val inDriveSamePackageFamily128 = samePackage &&
                        normalizedPackage == INDRIVE_PACKAGE &&
                        "card.crop.route_block" in liveFeatures &&
                        "card.route.two_addresses" in liveFeatures &&
                        inDriveFamilySignals128 >= 2 &&
                        match.matchedFeatures.size >= 3
                    standardSamePackageMatch128 || inDriveSamePackageFamily128 // indrive_same_package_family_match_0_1_128
                }
"""
        matcher = matcher.replaceFirst(matchAnchor, matchReplacement)
    }

    listOf(
        "persistent_maps_cache_context_0_1_128",
        "locked_popup_session_guard_0_1_128",
        "blocked_systemui_preserves_card_0_1_128",
        "transient_empty_locked_popup_ignored_0_1_128",
        "locked_popup_resolver_preserves_ride_package_0_1_128",
        "locked_popup_result_freshness_0_1_128",
        "direct_address_route_matrix_runtime_0_1_128",
        "background_geocode_warm_0_1_128",
        "direct_address_route_helper_0_1_128",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Marcador 0.1.128 ausente no servico: $marker")
    }
    listOf(
        "indrive_markerless_offer_crop_0_1_128",
        "indrive_same_package_family_match_0_1_128",
    ).forEach { marker ->
        if (marker !in matcher) throw GradleException("Marcador 0.1.128 ausente no matcher: $marker")
    }

    serviceFile.writeText(service)
    matcherFile.writeText(matcher)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchFastCacheScreenOff128(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcher.kt").asFile,
        )
    }
}
