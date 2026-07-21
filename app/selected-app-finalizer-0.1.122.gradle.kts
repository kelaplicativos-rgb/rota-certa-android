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

    val eventStart = text.indexOf("    override fun onAccessibilityEvent(event: AccessibilityEvent?) {")
    val eventEnd = if (eventStart >= 0) text.indexOf("    override fun onInterrupt()", eventStart) else -1
    if (eventStart < 0 || eventEnd < 0) throw GradleException("Evento final da acessibilidade nao encontrado.")
    var eventRegion = text.substring(eventStart, eventEnd)
    if ("selected_apps_event_gate_0_1_122" !in eventRegion) {
        val candidateAnchor = "        val candidatePackage = eventPackage ?: rootPackage\n"
        if (candidateAnchor !in eventRegion) throw GradleException("Pacote candidato do evento final nao encontrado.")
        eventRegion = eventRegion.replaceFirst(
            candidateAnchor,
            candidateAnchor +
                "        if (!AccessibilityEventFloodGate.isRelevantEventType(event.eventType)) return\n",
        )
        eventRegion = eventRegion.replace(
            "                traceEvent(\"universal.overlay.event ignored=true type=\" + event.eventType)\n",
            "                Unit // selected_apps_overlay_quiet_0_1_122\n",
        )
        val resolvedAnchor = "        val resolvedPackage = candidatePackage ?: lastExternalWindowPackageName ?: return\n"
        if (resolvedAnchor !in eventRegion) throw GradleException("Pacote resolvido do evento final nao encontrado.")
        eventRegion = eventRegion.replaceFirst(
            resolvedAnchor,
            resolvedAnchor +
                "        if (!shouldScanPackage(resolvedPackage)) {\n" +
                "            if (universalForegroundPackageName != resolvedPackage) universalWindowGeneration += 1L\n" +
                "            universalForegroundPackageName = resolvedPackage\n" +
                "            activePackageName = resolvedPackage\n" +
                "            lastExternalWindowPackageName = resolvedPackage\n" +
                "            hardClearUniversalTwoAddress(\"Aplicativo fora da selecao do usuario.\")\n" +
                "            return\n" +
                "        }\n" +
                "        if (accessibilityEventFloodGate.classify(\n" +
                "                packageName = resolvedPackage,\n" +
                "                eventType = event.eventType,\n" +
                "                monitoredPackage = true,\n" +
                "            ) == AccessibilityEventMode.Ignore\n" +
                "        ) return // selected_apps_event_gate_0_1_122\n",
        )
        text = text.substring(0, eventStart) + eventRegion + text.substring(eventEnd)
    }

    val scanStart = text.indexOf("    private fun startContinuousScan() {")
    val scanEnd = if (scanStart >= 0) text.indexOf("    private fun startProximityAlertMonitor()", scanStart) else -1
    if (scanStart < 0 || scanEnd < 0) throw GradleException("Ciclo final da leitura nao encontrado.")
    var scanRegion = text.substring(scanStart, scanEnd)
    if ("selected_apps_scan_loop_0_1_122" !in scanRegion) {
        val oldCondition = """                        if (!UniversalFastReadPolicy.shouldScanLivePackage(
                                packageName = expectedPackage,
                                ownPackageName = this@LiveRideAccessibilityService.packageName,
                            )
                        ) {
"""
        val newCondition = """                        if (!shouldScanPackage(expectedPackage) ||
                            !UniversalFastReadPolicy.shouldScanLivePackage(
                                packageName = expectedPackage,
                                ownPackageName = this@LiveRideAccessibilityService.packageName,
                            )
                        ) { // selected_apps_scan_loop_0_1_122
"""
        if (oldCondition !in scanRegion) throw GradleException("Portaria do ciclo final nao encontrada.")
        scanRegion = scanRegion.replaceFirst(oldCondition, newCondition)
        text = text.substring(0, scanStart) + scanRegion + text.substring(scanEnd)
    }

    val scheduleStart = text.indexOf("    private fun scheduleVisibleTextAnalysis(")
    val scheduleEnd = if (scheduleStart >= 0) text.indexOf("    private fun requestScreenshotAnalysis(", scheduleStart) else -1
    if (scheduleStart < 0 || scheduleEnd < 0) throw GradleException("Agendamento final da leitura nao encontrado.")
    var scheduleRegion = text.substring(scheduleStart, scheduleEnd)
    if ("selected_apps_schedule_0_1_122" !in scheduleRegion) {
        val expectedAnchor = "        val expectedPackage = universalResolvedForegroundPackage() ?: return\n"
        if (expectedAnchor !in scheduleRegion) throw GradleException("Pacote esperado do agendamento final nao encontrado.")
        scheduleRegion = scheduleRegion.replaceFirst(
            expectedAnchor,
            expectedAnchor + "        if (!shouldScanPackage(expectedPackage)) return // selected_apps_schedule_0_1_122\n",
        )
        text = text.substring(0, scheduleStart) + scheduleRegion + text.substring(scheduleEnd)
    }

    val screenshotStart = text.indexOf("    private fun requestScreenshotAnalysis(")
    val screenshotEnd = if (screenshotStart >= 0) text.indexOf("    private fun collectVisibleText(", screenshotStart) else -1
    if (screenshotStart < 0 || screenshotEnd < 0) throw GradleException("OCR final nao encontrado.")
    var screenshotRegion = text.substring(screenshotStart, screenshotEnd)
    if ("selected_apps_ocr_gate_0_1_122" !in screenshotRegion) {
        val requestedAnchor = "        val requestedPackage = ocrRequestToken.observedPackageName\n"
        if (requestedAnchor !in screenshotRegion) throw GradleException("Pacote observado do OCR final nao encontrado.")
        screenshotRegion = screenshotRegion.replaceFirst(
            requestedAnchor,
            requestedAnchor + "        if (!shouldScanPackage(requestedPackage)) return // selected_apps_ocr_gate_0_1_122\n",
        )
        text = text.substring(0, screenshotStart) + screenshotRegion + text.substring(screenshotEnd)
    }

    val processStart = text.indexOf("    private suspend fun processRideText(")
    val processEnd = if (processStart >= 0) text.indexOf("    private fun resolveRidePackageForText(", processStart) else -1
    if (processStart < 0 || processEnd < 0) throw GradleException("Processamento final da leitura nao encontrado.")
    var processRegion = text.substring(processStart, processEnd)
    if ("selected_apps_process_gate_0_1_122" !in processRegion) {
        val activeWindowBlock = """        if (!isUniversalExternalWindowActive()) {
            hardClearUniversalTwoAddress("Janela atual nao permite leitura universal.")
            return
        }

"""
        if (activeWindowBlock !in processRegion) throw GradleException("Janela ativa do processamento final nao encontrada.")
        processRegion = processRegion.replaceFirst(
            activeWindowBlock,
            activeWindowBlock +
                "        if (!shouldScanCurrentWindow()) {\n" +
                "            hardClearUniversalTwoAddress(\"Leitura recebida de aplicativo nao selecionado.\")\n" +
                "            return // selected_apps_process_gate_0_1_122\n" +
                "        }\n\n",
        )
        text = text.substring(0, processStart) + processRegion + text.substring(processEnd)
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

    val freshnessStart = text.indexOf("    private fun isUniversalResultFresh(")
    val freshnessEnd = if (freshnessStart >= 0) text.indexOf("    private fun hardClearUniversalTwoAddress(", freshnessStart) else -1
    if (freshnessStart < 0 || freshnessEnd < 0) throw GradleException("Validade final da rota nao encontrada.")
    var freshnessRegion = text.substring(freshnessStart, freshnessEnd)
    if ("selected_apps_freshness_gate_0_1_122" !in freshnessRegion) {
        freshnessRegion = freshnessRegion.replaceFirst(
            "            isUniversalExternalWindowActive()",
            "            isUniversalExternalWindowActive() &&\n            shouldScanCurrentWindow() // selected_apps_freshness_gate_0_1_122",
        )
        text = text.substring(0, freshnessStart) + freshnessRegion + text.substring(freshnessEnd)
    }

    if ("radar_spatial_index_0_1_122" !in text) {
        val proximityStart = text.indexOf("    private suspend fun checkProximityAlerts(")
        val proximityEnd = if (proximityStart >= 0) text.indexOf("    private fun scheduleVisibleTextAnalysis(", proximityStart) else -1
        if (proximityStart < 0 || proximityEnd < 0) {
            throw GradleException("Monitor final de proximidade nao encontrado para o indice espacial.")
        }
        var proximityRegion = text.substring(proximityStart, proximityEnd)
        val coordinateAnchor = "        val coordinate = locationService.currentCoordinate() ?: return\n"
        if (coordinateAnchor !in proximityRegion) throw GradleException("Coordenada do monitor de radares nao encontrada.")
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
        "selected_apps_ocr_gate_0_1_122",
        "selected_apps_process_gate_0_1_122",
        "selected_apps_store_0_1_122",
        "selected_apps_freshness_gate_0_1_122",
        "radar_spatial_index_0_1_122",
        "AccessibilityEventFloodGate.isRelevantEventType",
        "SelectedRideAppStore.selectedPackages",
        "ImportedRadarSpatialIndex",
        "radars = nearbyRadars",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Contrato 0.1.122 incompleto no servico final: $marker")
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

    if ("InstalledRideAppsCard()" !in text) throw GradleException("O card de aplicativos instalados nao foi ligado a Configuracoes.")
    listOf(
        "Buscar aplicativos instalados",
        "Fora dessa lista, o Rota Certa nao coleta texto",
        "InstalledRideAppPickerActivity::class.java",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Interface 0.1.122 incompleta: $marker")
    }
    file.writeText(text)
}

tasks.named("radarWorkTracking121").configure {
    doLast {
        patchSelectedAppServiceFinal122(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
        patchSelectedAppMainFinal122(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}

tasks.matching { it.name == "workTrackingCardAnchorCleanup121" }.configureEach {
    doLast {
        patchSelectedAppMainFinal122(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
