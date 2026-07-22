// Contrato final da leitura universal.
//
// Regras:
// 1. Ler qualquer aplicativo e qualquer tela, inclusive fotos, prints e web.
// 2. O gatilho e encontrar um ou mais enderecos visiveis.
// 3. Usar sempre o ultimo endereco distinto como destino final.
// 4. Calcular desse destino ate Casa/Alfinete definidos pelo motorista.
// 5. Nao exigir pacote, app de corrida, modelo de card, preco ou botao.

val universalLastAddressFinalPatch by tasks.registering {
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
    ) {
        val text = file.readText()
        if (marker in text) return
        val start = text.indexOf(startToken)
        val end = if (start >= 0) text.indexOf(endToken, start + startToken.length) else -1
        if (start < 0 || end < 0) {
            throw GradleException("Nao encontrei a regiao $marker em ${file.name}.")
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
            if (!allowPopupCandidate) {
                resetToIdle(
                    reason = "Tela sem texto/endereco visivel; informacao anterior removida.",
                    record = false,
                )
            }
            return
        }

        val fields = UniversalScreenAddressParser.parse(snapshotText)
        val destination = fields.destination?.trim()
        if (destination.isNullOrBlank()) {
            traceEvent("universal.address none package=${'$'}packageName")
            if (!allowPopupCandidate) {
                resetToIdle(
                    reason = "Nenhum endereco visivel; informacao anterior removida.",
                    record = false,
                )
            }
            return
        }

        registeredCardGate.markSeen()
        val analysisSignature = "last-address|" + destination.lowercase(Locale.ROOT)
        val snapshotHash = analysisSignature.hashCode()
        traceEvent("universal.address destination=${'$'}{destination.diagnosticValue()} hash=${'$'}snapshotHash")

        if (
            snapshotHash == lastAnalyzedHash &&
            (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red)
        ) {
            return
        }

        lastSnapshotHash = snapshotHash
        showOverlay(RadarColor.Default)

        val latestAnalysisToken = ++analysisSerial
        liveAnalysisJob?.cancel()
        pendingAnalysis = null
        analyzing = false
        coreCardAnalysisCoalescer.beforeStart(
            signature = analysisSignature,
            activeJob = false,
            hasAppliedDecision = false,
        )
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
                recordDiagnostic(
                    stage = "universal_route_timeout",
                    reason = "O ultimo endereco foi detectado, mas a rota excedeu o tempo limite.",
                    text = snapshotText,
                    fields = fields,
                )
            }
        }
    } // universal_last_address_process_0_1_95

""",
            "universal_last_address_process_0_1_95",
        )

        replaceRegion(
            service,
            "    private fun shouldScanPackage(packageName: String?): Boolean {",
            "    private fun selectedRidePackages(",
            """    private fun shouldScanPackage(packageName: String?): Boolean =
        serviceReady && currentSettings.appEnabled && !packageName.isNullOrBlank() // universal_all_packages_0_1_95

""",
            "universal_all_packages_0_1_95",
        )

        replaceRegion(
            service,
            "    private fun selectedRidePackages(",
            "    private fun scanBlockReason(",
            """    private fun selectedRidePackages(settings: AppSettings): Set<String> = emptySet()

""",
            "universal_no_selected_packages_0_1_95",
        )

        replaceRegion(
            service,
            "    private fun scanBlockReason(packageName: String?): String {",
            "    private fun recordDiagnostic(",
            """    private fun scanBlockReason(packageName: String?): String =
        if (currentSettings.appEnabled) {
            "Leitura universal liberada: " + (normalizePackageName(packageName) ?: "tela sem pacote") + "."
        } else {
            "Rota Certa desligado pelo motorista."
        } // universal_no_package_block_0_1_95

""",
            "universal_no_package_block_0_1_95",
        )

        replaceRegion(
            service,
            "    private fun isPassiveDiagnosticPackage(packageName: String?): Boolean {",
            "    private fun normalizePackageName(",
            """    private fun isPassiveDiagnosticPackage(packageName: String?): Boolean = false // universal_no_passive_block_0_1_95

""",
            "universal_no_passive_block_0_1_95",
        )

        replaceRegion(
            service,
            "    private suspend fun geocodeBest(",
            "    private suspend fun routeDistanceKm(",
            """    private suspend fun geocodeBest(query: String, region: DeviceRegion, settings: AppSettings): Coordinate? {
        val apiKey = settings.googleMapsApiKey.ifBlank { BuildConfig.GOOGLE_MAPS_API_KEY }
        return googleMapsService.geocode(query, region, apiKey)
            ?: geocodingService.geocode(query, region)
    } // universal_bundled_geocode_key_0_1_95

""",
            "universal_bundled_geocode_key_0_1_95",
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
        } else {
            null
        }
    } // universal_bundled_route_key_0_1_95

""",
            "universal_bundled_route_key_0_1_95",
        )

        replaceIfPresent(
            service,
            "            addView(actionMenuItem(\"💾  Salvar card de corrida\") {\n                hideActionMenu()\n                saveCurrentRideCardFromBubble()\n            })\n",
            "",
        )
        replaceIfPresent(service, "const val SCAN_LOOP_MS = 850L", "const val SCAN_LOOP_MS = 350L")
        replaceIfPresent(service, "const val SCREENSHOT_INTERVAL_MS = 650L", "const val SCREENSHOT_INTERVAL_MS = 300L")
        replaceIfPresent(
            service,
            "else -> distanceKm.roundToInt().coerceAtMost(99).toString()",
            "else -> String.format(Locale(\"pt\", \"BR\"), \"%.1f\", distanceKm).removeSuffix(\",0\")",
        )

        var main = mainFile.asFile.readText()
        if ("universal_models_removed_0_1_95" !in main) {
            val modelsCallStart = main.indexOf("    CardModelsCard(\n")
            if (modelsCallStart >= 0) {
                val modelsCallEnd = main.indexOf("\n\n    latestResult?.let", modelsCallStart)
                if (modelsCallEnd < 0) throw GradleException("Nao encontrei o fim do bloco de modelos.")
                main = main.substring(0, modelsCallStart) +
                    "    // Modelos removidos: leitura universal usa o ultimo endereco visivel. // universal_models_removed_0_1_95\n" +
                    main.substring(modelsCallEnd + 2)
            }

            main = main.replace(
                "        MonitoredAppsCard(settings = draft, onChange = ::saveDraft)\n",
                "        // Apps/pacotes predefinidos removidos. // universal_apps_removed_0_1_95\n",
            )
            main = main.replace(
                "                    \"Operando. Verde/vermelho aparecem quando o app reconhece um card de corrida cadastrado.\"",
                "                    \"Operando em qualquer tela. Detectou um ou mais enderecos, usa o ultimo e calcula ate o ponto definido pelo motorista.\"",
            )
            main = main.replace(
                "            Text(\"Aceite corridas cujo destino final fique dentro do raio definido por voce.\", style = MaterialTheme.typography.bodySmall)",
                "            Text(\"Leitura universal: o ultimo endereco visivel e calculado ate o ponto definido por voce.\", style = MaterialTheme.typography.bodySmall)",
            )
            main = main.replace(
                "            \"Salva configuracoes, modelos de cards, locais e alertas de proximidade em um arquivo do celular.\"",
                "            \"Salva configuracoes, locais e alertas de proximidade em um arquivo do celular.\"",
            )
            main = main.replace(
                "        Text(\"Cards cadastrados: ${'$'}{cardTemplates.size}\", style = MaterialTheme.typography.bodySmall)\n",
                "        Text(\"Leitura universal ativa: nenhum modelo de card e necessario.\", style = MaterialTheme.typography.bodySmall)\n",
            )
            val registerButtonStart = main.indexOf("            OutlinedButton(\n                enabled = diagnostic.textPreview.isNotBlank(),")
            if (registerButtonStart >= 0) {
                val registerButtonEnd = main.indexOf("            }\n", registerButtonStart)
                if (registerButtonEnd > registerButtonStart) {
                    main = main.removeRange(registerButtonStart, registerButtonEnd + "            }\n".length)
                }
            }
            mainFile.asFile.writeText(main)
        }

        var models = modelsFile.asFile.readText()
        models = models.replace("val restrictToSelectedRideApps: Boolean = true", "val restrictToSelectedRideApps: Boolean = false")
        models = models.replace("val requireRegisteredRideCard: Boolean = true", "val requireRegisteredRideCard: Boolean = false")
        if ("universal_settings_defaults_0_1_95" !in models) {
            models = models.replace(
                "    val proximityAlertDistanceMeters: Int = 200,\n)",
                "    val proximityAlertDistanceMeters: Int = 200,\n) // universal_settings_defaults_0_1_95",
            )
        }
        modelsFile.asFile.writeText(models)

        val finalService = service.readText()
        listOf(
            "universal_last_address_process_0_1_95",
            "universal_all_packages_0_1_95",
            "universal_no_package_block_0_1_95",
            "universal_no_passive_block_0_1_95",
            "universal_bundled_geocode_key_0_1_95",
            "universal_bundled_route_key_0_1_95",
        ).forEach { marker ->
            if (marker !in finalService) throw GradleException("Leitura universal incompleta: ${'$'}marker")
        }
    }
}

universalLastAddressFinalPatch.configure {
    mustRunAfter(
        tasks.matching { task ->
            task.name != name &&
                !task.name.startsWith("compile") &&
                !task.name.startsWith("test") &&
                task.name !in setOf("preBuild", "assemble", "assembleDebug") &&
                (
                    task.name.contains("patch", ignoreCase = true) ||
                        task.name.contains("fix", ignoreCase = true) ||
                        task.name.startsWith("enforce", ignoreCase = true)
                    )
        },
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalLastAddressFinalPatch)
}
