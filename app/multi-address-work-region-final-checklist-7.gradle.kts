// Checklist 7 — Casa + vários alfinetes com uma única matriz de rotas.

fun replaceMultiAddressFunctionChecklist7(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Função ausente para multiendereços: $signature")
    val open = source.indexOf('{', start)
    if (open < 0) throw GradleException("Corpo ausente para multiendereços: $signature")
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
    throw GradleException("Fim da função ausente para multiendereços: $signature")
}

fun insertWorkRegionCardChecklist7(source: String): String {
    if ("multi_address_work_region_ui_checklist_7" in source) return source
    val analysisStart = source.indexOf("private fun AnalysisScreen(")
    val analysisEnd = if (analysisStart >= 0) source.indexOf("private fun LiveReadingCard(", analysisStart) else -1
    if (analysisStart < 0 || analysisEnd < 0) throw GradleException("AnalysisScreen ausente para alfinetes.")
    var region = source.substring(analysisStart, analysisEnd)
    val callStart = region.indexOf("    RadiusQuickCard(")
    if (callStart < 0) throw GradleException("RadiusQuickCard ausente para inserir alfinetes.")
    val open = region.indexOf('(', callStart)
    var depth = 0
    var index = open
    var callEnd = -1
    while (index < region.length) {
        when (region[index]) {
            '(' -> depth += 1
            ')' -> {
                depth -= 1
                if (depth == 0) { callEnd = index + 1; break }
            }
        }
        index += 1
    }
    if (callEnd < 0) throw GradleException("Chamada RadiusQuickCard sem fechamento.")
    val insertion = """

    Spacer(Modifier.height(10.dp))
    WorkRegionPinsCard(
        settings = quickSettings,
        onSettingsChange = ::saveQuickSettings,
    ) // multi_address_work_region_ui_checklist_7
"""
    region = region.substring(0, callEnd) + insertion + region.substring(callEnd)
    return source.substring(0, analysisStart) + region + source.substring(analysisEnd)
}

fun patchMultiAddressMainChecklist7(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt ausente para multiendereços.")
    var main = file.readText()
    main = insertWorkRegionCardChecklist7(main)
    main = main.replace("label = \"Alfinete\"", "label = \"Todos os alfinetes\"")

    val legacyStart = main.indexOf("        OutlinedTextField(\n            value = draft.alternativeAddress,")
    val radiusStart = if (legacyStart >= 0) main.indexOf("        RadiusSlider(\"Raio casa\"", legacyStart) else -1
    if (legacyStart >= 0 && radiusStart > legacyStart) {
        val replacement = """        Text(
            \"Os endereços dos alfinetes são gerenciados em Região de trabalho.\",
            style = MaterialTheme.typography.bodySmall,
        )

"""
        main = main.substring(0, legacyStart) + replacement + main.substring(radiusStart)
    }

    listOf(
        "multi_address_work_region_ui_checklist_7",
        "WorkRegionPinsCard(",
        "Todos os alfinetes",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Interface de multiendereços incompleta: $marker")
    }
    file.writeText(main)
}

fun patchMultiAddressServiceChecklist7(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente para multiendereços.")
    var service = file.readText()
    if ("multi_address_route_matrix_final_checklist_7" in service) return

    val replacement = """    private suspend fun analyzeUniversalTwoAddress(
        snapshotText: String,
        fields: RideFields,
        screenHash: Int,
        addressSignature: String,
        generation: Long,
    ) {
        val settings = currentSettings // instant_farol_cached_settings_0_1_124
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

        val region = DeviceRegion(country = \"Brasil\")
        val homeCoordinate = if (settings.homeTargetEnabled) {
            settings.homeCoordinate
                ?: settings.homeAddress.takeIf(String::isNotBlank)?.let { geocodeBest(it, region, settings) }
        } else {
            null
        }
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

        val configuredPins = if (settings.alternativeTargetEnabled) {
            WorkRegionTargetPolicy.editablePins(settings).filter { it.enabled }
        } else {
            emptyList()
        }
        val resolvedPins = configuredPins.map { pin ->
            if (pin.coordinate != null) {
                pin
            } else if (pin.id == WorkRegionTargetPolicy.LEGACY_PIN_ID && pin.address.isNotBlank()) {
                pin.copy(coordinate = geocodeBest(pin.address, region, settings))
            } else {
                pin
            }
        }
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

        val destinations = buildList {
            homeCoordinate?.let(::add)
            resolvedPins.mapNotNull(WorkRegionPin::coordinate).forEach(::add)
        }
        val routeDistances = googleMapsService.drivingDistancesFromAddressKm(
            originAddress = fields.destination.orEmpty(),
            destinations = destinations,
            apiKey = settings.googleMapsApiKey,
        ) // direct_address_route_matrix_runtime_0_1_128 multi_address_route_matrix_final_checklist_7
        if (!isUniversalResultFresh(generation, screenHash, addressSignature)) return

        var routeIndex = 0
        val homeDistanceKm = if (homeCoordinate != null) routeDistances.getOrNull(routeIndex++) else null
        val pinRoutes = resolvedPins.map { pin ->
            val distance = if (pin.coordinate != null) routeDistances.getOrNull(routeIndex++) else null
            WorkRegionPinRoute(pin = pin, distanceKm = distance)
        }

        val result = decisionEngine.decideWorkRegion(
            fields = fields,
            settings = settings,
            fullText = snapshotText,
            homeTargetActive = settings.homeTargetEnabled,
            homeDistanceKm = homeDistanceKm,
            pinRoutes = pinRoutes,
        )
        applyUniversalTwoAddressResult(result, screenHash, addressSignature, generation)
        // fast_red_continues_exact_route_0_1_127
        // subsecond_exact_red_lower_bound_0_1_125
        // persistent_route_cache_save_0_1_124: o cache persistente por endereço permanece no GoogleMapsService.
    }
"""

    service = replaceMultiAddressFunctionChecklist7(
        service,
        "    private suspend fun analyzeUniversalTwoAddress(",
        replacement,
    )

    listOf(
        "multi_address_route_matrix_final_checklist_7",
        "drivingDistancesFromAddressKm(",
        "decisionEngine.decideWorkRegion(",
        "WorkRegionTargetPolicy.editablePins(settings)",
        "applyUniversalTwoAddressResult(result",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Serviço multiendereço incompleto: $marker")
    }
    file.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        val root = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile
        patchMultiAddressMainChecklist7(java.io.File(root, "MainActivity.kt"))
        patchMultiAddressServiceChecklist7(java.io.File(root, "LiveRideAccessibilityService.kt"))
    }
}
