// Integracao resiliente do contrato universal do ultimo endereco.
// Desativa a primeira tentativa e aplica somente os pontos essenciais depois de todos os patches legados.

tasks.named("universalLastAddressFinalPatch").configure { enabled = false }

val universalLastAddressFinalV2 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    val modelsFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/Models.kt")
    inputs.files(serviceFile, mainFile, modelsFile)
    outputs.upToDateWhen { false }

    fun replaceRegion(
        file: java.io.File,
        startToken: String,
        endToken: String,
        replacement: String,
        marker: String,
        required: Boolean = true,
    ) {
        val text = file.readText()
        if (marker in text) return
        val start = text.indexOf(startToken)
        val end = if (start >= 0) text.indexOf(endToken, start + startToken.length) else -1
        if (start < 0 || end < 0) {
            if (required) throw GradleException("Nao encontrei a regiao $marker em ${file.name}.")
            return
        }
        file.writeText(text.substring(0, start) + replacement + text.substring(end))
    }

    fun replaceIfPresent(file: java.io.File, old: String, replacement: String) {
        val text = file.readText()
        if (old in text) file.writeText(text.replace(old, replacement))
    }

    doLast {
        val service = serviceFile.asFile

        replaceRegion(
            service,
            "    private suspend fun processRideText(",
            "    private fun resolveRidePackageForText(",
            """    private suspend fun processRideText(
        text: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
    ) {
        if (!serviceReady || !currentSettings.appEnabled) return

        val packageName = normalizePackageName(currentWindowPackageName()) ?: "android.visible.screen"
        val snapshotText = text.trim()
        traceEvent("universal.process source=${'$'}source package=${'$'}packageName length=${'$'}{snapshotText.length}")

        if (snapshotText.isBlank()) {
            if (!allowPopupCandidate) resetToIdle("Tela sem endereco; resultado anterior removido.", false)
            return
        }

        val fields = UniversalScreenAddressParser.parse(snapshotText)
        val destination = fields.destination?.trim()
        if (destination.isNullOrBlank()) {
            traceEvent("universal.address none package=${'$'}packageName")
            if (!allowPopupCandidate) resetToIdle("Nenhum endereco visivel; resultado anterior removido.", false)
            return
        }

        registeredCardGate.markSeen()
        val analysisSignature = "last-address|" + destination.lowercase(Locale.ROOT)
        val snapshotHash = analysisSignature.hashCode()
        traceEvent("universal.address destination=${'$'}{destination.diagnosticValue()} hash=${'$'}snapshotHash")

        if (snapshotHash == lastAnalyzedHash &&
            (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red)
        ) return

        lastSnapshotHash = snapshotHash
        showOverlay(RadarColor.Default)

        val latestAnalysisToken = ++analysisSerial
        liveAnalysisJob?.cancel()
        pendingAnalysis = null
        analyzing = false
        coreCardAnalysisCoalescer.beforeStart(analysisSignature, false, false)
        liveAnalysisJob = scope.launch {
            val completed = kotlinx.coroutines.withTimeoutOrNull(LIVE_ANALYSIS_TIMEOUT_MS) {
                analyzeLiveText(
                    text = snapshotText,
                    fields = fields,
                    snapshotHash = snapshotHash,
                    cardMatch = null,
                    allowPopupCandidate = allowPopupCandidate,
                    analysisToken = latestAnalysisToken,
                    analysisCardSignature = analysisSignature,
                )
                true
            } ?: false
            if (!completed && latestAnalysisToken == analysisSerial) {
                analyzing = false
                liveAnalysisJob = null
                coreCardAnalysisCoalescer.finish(analysisSignature)
                showOverlay(RadarColor.Default)
            }
        }
    } // universal_last_address_process_v2_0_1_95

""",
            "universal_last_address_process_v2_0_1_95",
        )

        replaceRegion(
            service,
            "    private fun shouldScanPackage(packageName: String?): Boolean {",
            "    private fun selectedRidePackages(",
            """    private fun shouldScanPackage(packageName: String?): Boolean =
        serviceReady && currentSettings.appEnabled && !packageName.isNullOrBlank() // universal_all_packages_v2_0_1_95

""",
            "universal_all_packages_v2_0_1_95",
        )

        replaceRegion(
            service,
            "    private fun selectedRidePackages(",
            "    private fun scanBlockReason(",
            """    private fun selectedRidePackages(settings: AppSettings): Set<String> = emptySet() // universal_no_packages_v2_0_1_95

""",
            "universal_no_packages_v2_0_1_95",
            required = false,
        )

        replaceRegion(
            service,
            "    private fun isPassiveDiagnosticPackage(packageName: String?): Boolean {",
            "    private fun normalizePackageName(",
            """    private fun isPassiveDiagnosticPackage(packageName: String?): Boolean = false // universal_no_passive_v2_0_1_95

""",
            "universal_no_passive_v2_0_1_95",
            required = false,
        )

        replaceRegion(
            service,
            "    private suspend fun geocodeBest(",
            "    private suspend fun routeDistanceKm(",
            """    private suspend fun geocodeBest(query: String, region: DeviceRegion, settings: AppSettings): Coordinate? {
        val apiKey = settings.googleMapsApiKey.ifBlank { BuildConfig.GOOGLE_MAPS_API_KEY }
        return googleMapsService.geocode(query, region, apiKey) ?: geocodingService.geocode(query, region)
    } // universal_geocode_v2_0_1_95

""",
            "universal_geocode_v2_0_1_95",
        )

        replaceRegion(
            service,
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
    } // universal_route_v2_0_1_95

""",
            "universal_route_v2_0_1_95",
        )

        replaceIfPresent(
            service,
            "            addView(actionMenuItem(\"💾  Salvar card de corrida\") {\n                hideActionMenu()\n                saveCurrentRideCardFromBubble()\n            })\n",
            "",
        )
        replaceIfPresent(service, "const val SCAN_LOOP_MS = 850L", "const val SCAN_LOOP_MS = 350L")
        replaceIfPresent(service, "const val SCREENSHOT_INTERVAL_MS = 650L", "const val SCREENSHOT_INTERVAL_MS = 300L")
        replaceIfPresent(service, "else -> distanceKm.roundToInt().coerceAtMost(99).toString()", "else -> String.format(Locale(\"pt\", \"BR\"), \"%.1f\", distanceKm).removeSuffix(\",0\")")

        var main = mainFile.asFile.readText()
        if ("universal_models_removed_v2_0_1_95" !in main) {
            val start = main.indexOf("    CardModelsCard(\n")
            val end = if (start >= 0) main.indexOf("\n\n    latestResult?.let", start) else -1
            if (start >= 0 && end > start) {
                main = main.substring(0, start) +
                    "    // Modelos removidos; o gatilho e o ultimo endereco. // universal_models_removed_v2_0_1_95\n\n" +
                    main.substring(end + 2)
            } else {
                main += "\n// universal_models_removed_v2_0_1_95\n"
            }
            main = main.replace(
                "        MonitoredAppsCard(settings = draft, onChange = ::saveDraft)\n",
                "        // Apps e pacotes predefinidos removidos. // universal_apps_removed_v2_0_1_95\n",
            )
            main = main.replace(
                "Operando. Verde/vermelho aparecem quando o app reconhece um card de corrida cadastrado.",
                "Operando em qualquer tela. Encontrou endereco, usa sempre o ultimo e calcula ate o ponto definido pelo motorista.",
            )
            main = main.replace(
                "Aceite corridas cujo destino final fique dentro do raio definido por voce.",
                "Leitura universal: o ultimo endereco visivel e calculado ate o ponto definido por voce.",
            )
            main = main.replace(
                "Salva configuracoes, modelos de cards, locais e alertas de proximidade em um arquivo do celular.",
                "Salva configuracoes, locais e alertas de proximidade em um arquivo do celular.",
            )
            mainFile.asFile.writeText(main)
        }

        var models = modelsFile.asFile.readText()
        models = models.replace("val restrictToSelectedRideApps: Boolean = true", "val restrictToSelectedRideApps: Boolean = false")
        models = models.replace("val requireRegisteredRideCard: Boolean = true", "val requireRegisteredRideCard: Boolean = false")
        if ("universal_defaults_v2_0_1_95" !in models) models += "\n// universal_defaults_v2_0_1_95\n"
        modelsFile.asFile.writeText(models)

        val finalText = service.readText()
        listOf(
            "universal_last_address_process_v2_0_1_95",
            "universal_all_packages_v2_0_1_95",
            "universal_geocode_v2_0_1_95",
            "universal_route_v2_0_1_95",
        ).forEach { marker -> if (marker !in finalText) throw GradleException("Integracao universal incompleta: $marker") }
    }
}

universalLastAddressFinalV2.configure {
    mustRunAfter(
        tasks.matching { task ->
            task.name != name &&
                task.name != "universalLastAddressFinalPatch" &&
                !task.name.startsWith("compile") &&
                !task.name.startsWith("test") &&
                task.name !in setOf("preBuild", "assemble", "assembleDebug") &&
                (task.name.contains("patch", true) || task.name.contains("fix", true) || task.name.startsWith("enforce", true))
        },
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalLastAddressFinalV2)
}
