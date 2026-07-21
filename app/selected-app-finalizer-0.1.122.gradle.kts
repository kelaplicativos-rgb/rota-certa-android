fun replaceSelectedAppRegion122(
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

fun patchSelectedAppServiceFinal122(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para o seletor final.")
    var text = file.readText()

    if ("private val accessibilityEventFloodGate = AccessibilityEventFloodGate()" !in text) {
        val anchor = "    private var universalActiveAddressSignature: String? = null // universal_two_address_fields_0_1_98\n"
        if (anchor !in text) throw GradleException("Campos finais do leitor universal nao encontrados.")
        text = text.replaceFirst(
            anchor,
            anchor +
                "    private val accessibilityEventFloodGate = AccessibilityEventFloodGate()\n" +
                "    private val importedRadarSpatialIndex = ImportedRadarSpatialIndex()\n",
        )
    }

    text = replaceSelectedAppRegion122(
        text,
        "    override fun onAccessibilityEvent(event: AccessibilityEvent?) {",
        "    override fun onInterrupt()",
        """    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!serviceReady || event == null) return
        if (!currentSettings.appEnabled || !currentSettings.liveReadingEnabled) {
            hardClearUniversalTwoAddress("Leitura ao vivo desligada.")
            return
        }
        if (!AccessibilityEventFloodGate.isRelevantEventType(event.eventType)) return

        val packageName = normalizePackageName(event.packageName?.toString()) ?: currentRootPackageName()
        activePackageName = packageName
        if (!shouldScanPackage(packageName)) {
            if (currentRadarColor != RadarColor.Idle || currentDistanceKm != null || lastSnapshotHash != null) {
                hardClearUniversalTwoAddress("Aplicativo fora da selecao do usuario.")
            }
            return
        }

        val eventMode = accessibilityEventFloodGate.classify(
            packageName = packageName,
            eventType = event.eventType,
            monitoredPackage = true,
        )
        if (eventMode == AccessibilityEventMode.Ignore) return

        traceEvent("selected.app.event package=${'$'}{packageName.orEmpty()} type=${'$'}{event.eventType}") // selected_apps_event_gate_0_1_122
        scheduleVisibleTextAnalysis(delayMs = 0L, allowPopupCandidate = true)
        requestScreenshotAnalysis(allowPopupCandidate = true)
    }

""",
        "evento filtrado pelos aplicativos selecionados",
    )

    text = replaceSelectedAppRegion122(
        text,
        "    private fun startContinuousScan() {",
        "    private fun startProximityAlertMonitor()",
        """    private fun startContinuousScan() {
        if (continuousScanStarted || !serviceReady) return
        continuousScanStarted = true
        traceEvent("selected.app.scan.loop interval=${'$'}SCAN_LOOP_MS")
        scope.launch {
            while (serviceReady) {
                val packageName = currentWindowPackageName()
                if (!currentSettings.appEnabled || !currentSettings.liveReadingEnabled) {
                    if (currentRadarColor != RadarColor.Idle || currentDistanceKm != null || lastSnapshotHash != null) {
                        hardClearUniversalTwoAddress("Leitura ao vivo desligada.")
                    }
                } else if (!shouldScanPackage(packageName)) {
                    if (currentRadarColor != RadarColor.Idle || currentDistanceKm != null || lastSnapshotHash != null) {
                        hardClearUniversalTwoAddress("Aplicativo fora da selecao; leitura pausada.")
                    }
                } else {
                    val visibleText = collectVisibleText(allowPopupCandidate = true)
                    processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
                    requestScreenshotAnalysis(allowPopupCandidate = true)
                }
                delay(SCAN_LOOP_MS)
            }
        }
    } // selected_apps_scan_loop_0_1_122

""",
        "ciclo limitado aos aplicativos selecionados",
    )

    text = replaceSelectedAppRegion122(
        text,
        "    private fun scheduleVisibleTextAnalysis(",
        "    private fun requestScreenshotAnalysis(",
        """    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return
        if (!shouldScanCurrentWindow()) return
        analyzeJob?.cancel()
        analyzeJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            if (!shouldScanCurrentWindow()) return@launch
            val visibleText = collectVisibleText(allowPopupCandidate = true)
            processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
        }
    } // selected_apps_schedule_0_1_122

""",
        "agendamento limitado aos aplicativos selecionados",
    )

    text = text.replace(
        "        if (!serviceReady || currentWindowPackageName() == this.packageName) return \"\"",
        "        if (!serviceReady || !shouldScanCurrentWindow()) return \"\"",
    )

    val processOld = """    ) {
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return
        if (currentWindowPackageName() == this.packageName) {
            hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
            return
        }

        val snapshotText = text.trim()
"""
    val processNew = """    ) {
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return
        if (!shouldScanCurrentWindow()) {
            if (currentRadarColor != RadarColor.Idle || currentDistanceKm != null || lastSnapshotHash != null) {
                hardClearUniversalTwoAddress("Leitura recebida de aplicativo nao selecionado.")
            }
            return
        }

        val snapshotText = text.trim()
"""
    val processStart = text.indexOf("    private suspend fun processRideText(")
    val processEnd = if (processStart >= 0) text.indexOf("    private fun resolveRidePackageForText(", processStart) else -1
    if (processStart < 0 || processEnd < 0) throw GradleException("Processamento final da leitura nao encontrado.")
    val processRegion = text.substring(processStart, processEnd)
    if (processOld in processRegion) {
        val patchedRegion = processRegion.replaceFirst(processOld, processNew)
        text = text.substring(0, processStart) + patchedRegion + text.substring(processEnd)
    } else if ("Leitura recebida de aplicativo nao selecionado." !in processRegion) {
        throw GradleException("Nao consegui proteger processRideText contra apps nao selecionados.")
    }

    text = replaceSelectedAppRegion122(
        text,
        "    private fun shouldScanPackage(packageName: String?): Boolean {",
        "    private fun selectedRidePackages(",
        """    private fun shouldScanPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        val selectedPackages = SelectedRideAppStore.selectedPackages(applicationContext, currentSettings)
        return serviceReady &&
            currentSettings.appEnabled &&
            currentSettings.liveReadingEnabled &&
            normalized != this.packageName &&
            normalized in selectedPackages
    } // selected_apps_store_0_1_122

""",
        "portaria final dos aplicativos selecionados",
    )

    text = text.replace(
        "            addressSignature == universalActiveAddressSignature &&\n            currentWindowPackageName() != this.packageName",
        "            addressSignature == universalActiveAddressSignature &&\n            shouldScanCurrentWindow()",
    )

    if ("radar_spatial_index_0_1_122" !in text) {
        val proximityStart = text.indexOf("    private suspend fun checkProximityAlerts(")
        val proximityEnd = if (proximityStart >= 0) text.indexOf("    private fun scheduleVisibleTextAnalysis(", proximityStart) else -1
        if (proximityStart < 0 || proximityEnd < 0) {
            throw GradleException("Monitor final de proximidade nao encontrado para o indice espacial.")
        }
        var proximityRegion = text.substring(proximityStart, proximityEnd)
        val coordinateAnchor = "        val coordinate = locationService.currentCoordinate() ?: return\n"
        if (coordinateAnchor !in proximityRegion) {
            throw GradleException("Coordenada do monitor de radares nao encontrada.")
        }
        proximityRegion = proximityRegion.replaceFirst(
            coordinateAnchor,
            coordinateAnchor +
                "        val radarSearchRadiusMeters = currentSettings.proximityAlertDistanceMeters.coerceAtLeast(200).toDouble() + 1_000.0\n" +
                "        val nearbyRadarQuery = importedRadarSpatialIndex.query(\n" +
                "            source = radars,\n" +
                "            center = coordinate,\n" +
                "            radiusMeters = radarSearchRadiusMeters,\n" +
                "        ) // radar_spatial_index_0_1_122\n" +
                "        val nearbyRadars = nearbyRadarQuery.radars\n",
        )
        proximityRegion = proximityRegion.replaceFirst("            radars = radars,", "            radars = nearbyRadars,")
        text = text.substring(0, proximityStart) + proximityRegion + text.substring(proximityEnd)
    }

    listOf(
        "selected_apps_event_gate_0_1_122",
        "selected_apps_scan_loop_0_1_122",
        "selected_apps_schedule_0_1_122",
        "selected_apps_store_0_1_122",
        "radar_spatial_index_0_1_122",
        "AccessibilityEventFloodGate.isRelevantEventType",
        "SelectedRideAppStore.selectedPackages",
        "ImportedRadarSpatialIndex",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Contrato 0.1.122 incompleto no servico: $marker")
    }

    file.writeText(text)
}

fun patchSelectedAppMainFinal122(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado para o seletor final.")
    var text = file.readText()

    text = text.replace(
        "        MonitoredAppsCard(settings = draft, onChange = ::saveDraft)\n",
        "        InstalledRideAppsCard()\n",
    )
    text = text.replace(
        "        MonitoredAppsCard(cardTemplates = cardTemplates)\n",
        "        InstalledRideAppsCard()\n",
    )

    if ("private fun InstalledRideAppsCard()" !in text) {
        val anchor = "@Composable\nprivate fun ExpandableCard(\n"
        if (anchor !in text) throw GradleException("Ponto da interface para o seletor de apps nao encontrado.")
        val block = """@Composable
private fun InstalledRideAppsCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedPackages by remember { mutableStateOf(SelectedRideAppStore.read(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                selectedPackages = SelectedRideAppStore.read(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ExpandableCard(title = "Aplicativos de corrida", initiallyExpanded = true) {
        Text(
            "Escolha os aplicativos instalados que a bolinha pode ler. Fora dessa lista, o Rota Certa nao coleta texto, nao executa OCR e nao solicita captura de tela.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { context.startActivity(Intent(context, InstalledRideAppPickerActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Buscar aplicativos instalados")
        }
        if (selectedPackages.isEmpty()) {
            Text(
                "Nenhum aplicativo selecionado. A leitura ao vivo fica pausada ate voce escolher pelo menos um aplicativo de corrida.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text("Aplicativos selecionados: ${'$'}{selectedPackages.size}", fontWeight = FontWeight.Bold)
            selectedPackages.forEach { packageName ->
                Text(packageName, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

"""
        text = text.replace(anchor, block + anchor)
    }

    if ("InstalledRideAppsCard()" !in text) {
        throw GradleException("O card de aplicativos instalados nao foi ligado a tela Configuracoes.")
    }
    listOf(
        "Buscar aplicativos instalados",
        "Fora dessa lista, o Rota Certa nao coleta texto",
        "InstalledRideAppPickerActivity::class.java",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Interface 0.1.122 incompleta: $marker")
    }

    file.writeText(text)
}

tasks.named("universalTwoAddressRuntimeFinal").configure {
    doLast {
        patchSelectedAppServiceFinal122(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
        patchSelectedAppMainFinal122(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
