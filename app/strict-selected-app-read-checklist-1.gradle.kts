// Checklist 1 — leitura estrita somente nos aplicativos escolhidos manualmente.
// Este finalizador roda depois de toda a cadeia historica de patches e fecha
// qualquer atalho que ainda pudesse coletar arvore, screenshot, OCR ou rota
// fora do pacote atualmente selecionado pelo usuario.

fun replaceStrictRegionChecklist1(
    source: String,
    startToken: String,
    endToken: String,
    replacement: String,
    label: String,
): String {
    val start = source.indexOf(startToken)
    val end = if (start >= 0) source.indexOf(endToken, start + startToken.length) else -1
    if (start < 0 || end < 0) throw GradleException("Regiao nao encontrada para $label.")
    return source.substring(0, start) + replacement + source.substring(end)
}

fun insertStrictGuardChecklist1(
    source: String,
    signature: String,
    marker: String,
    guard: String,
    required: Boolean = true,
): String {
    if (marker in source) return source
    val start = source.indexOf(signature)
    if (start < 0) {
        if (required) throw GradleException("Funcao nao encontrada para $marker: $signature")
        return source
    }
    val brace = source.indexOf('{', start)
    if (brace < 0) throw GradleException("Corpo da funcao nao encontrado para $marker.")
    return source.substring(0, brace + 1) + "\n" + guard + source.substring(brace + 1)
}

fun patchStrictSelectedAppReadChecklist1(serviceFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para o checklist 1.")
    var service = serviceFile.readText()

    val shouldScanReplacement = """    private fun shouldScanPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        val passiveProbeSettings = currentSettings.copy(
            restrictToSelectedRideApps = false,
            monitor99 = false,
            monitorUber = false,
            monitorInDrive = false,
            extraMonitoredPackages = "",
        )
        val platformClassification = br.com.mapeiaia.rotacerta.core.CorePackageMonitor.classify(
            packageName = normalized,
            ownPackageName = this.packageName,
            settings = passiveProbeSettings,
        )
        return StrictSelectedAppReadPolicy.canRead(
            packageName = normalized,
            ownPackageName = this.packageName,
            appEnabled = serviceReady && currentSettings.appEnabled,
            liveReadingEnabled = currentSettings.liveReadingEnabled,
            selectedPackages = SelectedRideAppStore.read(applicationContext),
            packageAllowedByPlatformPolicy = platformClassification.canScan,
        )
    } // strict_selected_app_policy_checklist_1

"""
    service = replaceStrictRegionChecklist1(
        source = service,
        startToken = "    private fun shouldScanPackage(packageName: String?): Boolean {",
        endToken = "    private fun selectedRidePackages(",
        replacement = shouldScanReplacement,
        label = "portaria estrita dos aplicativos selecionados",
    )

    val selectedPackagesReplacement = """    private fun selectedRidePackages(settings: AppSettings): Set<String> {
        @Suppress("UNUSED_VARIABLE")
        val ignoredLegacySettings = settings
        return SelectedRideAppStore.read(applicationContext)
    } // selected_packages_manual_only_checklist_1

"""
    service = replaceStrictRegionChecklist1(
        source = service,
        startToken = "    private fun selectedRidePackages(settings: AppSettings): Set<String>",
        endToken = "    private fun scanBlockReason(",
        replacement = selectedPackagesReplacement,
        label = "fonte unica da selecao manual",
    )

    if ("strict_selected_root_helper_checklist_1" !in service) {
        val helperAnchor = "    private fun shouldScanCurrentWindow(): Boolean"
        val helperIndex = service.indexOf(helperAnchor)
        if (helperIndex < 0) throw GradleException("Ponto do pacote raiz nao encontrado para o checklist 1.")
        val helper = """    private fun strictSelectedRootPackageChecklist1(): String? =
        currentRootPackageName()?.takeIf { shouldScanPackage(it) }

    private fun hasStrictSelectedRootChecklist1(): Boolean =
        strictSelectedRootPackageChecklist1() != null // strict_selected_root_helper_checklist_1

"""
        service = service.substring(0, helperIndex) + helper + service.substring(helperIndex)
    }

    service = insertStrictGuardChecklist1(
        service,
        "    private fun scheduleVisibleTextAnalysis(",
        "strict_schedule_gate_checklist_1",
        "        if (!hasStrictSelectedRootChecklist1()) return // strict_schedule_gate_checklist_1\n",
    )
    service = insertStrictGuardChecklist1(
        service,
        "    private fun requestScreenshotAnalysis(",
        "strict_screenshot_gate_checklist_1",
        "        if (!hasStrictSelectedRootChecklist1()) return // strict_screenshot_gate_checklist_1\n",
    )
    service = insertStrictGuardChecklist1(
        service,
        "    private fun collectVisibleText(",
        "strict_tree_gate_checklist_1",
        "        if (!hasStrictSelectedRootChecklist1()) return \"\" // strict_tree_gate_checklist_1\n",
    )
    service = insertStrictGuardChecklist1(
        service,
        "    private suspend fun processRideText(",
        "strict_process_gate_checklist_1",
        "        if (!hasStrictSelectedRootChecklist1()) return // strict_process_gate_checklist_1\n",
    )
    service = insertStrictGuardChecklist1(
        service,
        "    private suspend fun analyzeLiveText(",
        "strict_route_gate_checklist_1",
        "        if (!hasStrictSelectedRootChecklist1()) return // strict_route_gate_checklist_1\n",
    )
    service = insertStrictGuardChecklist1(
        service,
        "    private fun collectVisibleTextForAction(",
        "strict_manual_tree_gate_checklist_1",
        "        if (!hasStrictSelectedRootChecklist1()) return \"\" // strict_manual_tree_gate_checklist_1\n",
    )
    service = insertStrictGuardChecklist1(
        service,
        "    private suspend fun analyzeUniversalTwoAddress(",
        "strict_universal_route_gate_checklist_1",
        "        if (!hasStrictSelectedRootChecklist1()) return // strict_universal_route_gate_checklist_1\n",
        required = false,
    )

    // Um screenshot solicitado enquanto o app selecionado estava aberto nao pode
    // executar OCR se o usuario mudou de janela antes do callback.
    service = service.replace(
        "if (allowPopupCandidate || shouldScanCurrentWindow()) {",
        "if (hasStrictSelectedRootChecklist1()) { // strict_ocr_callback_gate_checklist_1",
    )

    // A captura automatica e paralela, portanto revalida pacote e janela antes
    // de solicitar o screenshot e novamente antes de salvar o bitmap.
    service = insertStrictGuardChecklist1(
        service,
        "    private fun requestAutomaticRideCapture129(",
        "strict_automatic_capture_entry_checklist_1",
        """        if (!shouldScanPackage(packageName) ||
            normalizePackageName(currentRootPackageName()) != normalizePackageName(packageName)
        ) return // strict_automatic_capture_entry_checklist_1
""",
        required = false,
    )

    val automaticStart = service.indexOf("    private fun requestAutomaticRideCapture129(")
    if (automaticStart >= 0) {
        val automaticEnd = service.indexOf("    private fun requestScreenshotAnalysis(", automaticStart)
        if (automaticEnd < 0) throw GradleException("Fim da captura automatica nao encontrado para o checklist 1.")
        var automaticRegion = service.substring(automaticStart, automaticEnd)

        if ("strict_automatic_capture_preflight_checklist_1" !in automaticRegion) {
            val requestAnchor = "            runCatching {\n                takeScreenshot("
            if (requestAnchor !in automaticRegion) throw GradleException("Solicitacao automatica de screenshot nao encontrada.")
            automaticRegion = automaticRegion.replaceFirst(
                requestAnchor,
                """            if (!shouldScanPackage(packageName) ||
                normalizePackageName(currentRootPackageName()) != normalizePackageName(packageName)
            ) {
                automaticCaptureInProgress129.set(false)
                return@launch // strict_automatic_capture_preflight_checklist_1
            }
            runCatching {
                takeScreenshot(""",
            )
        }

        if ("strict_automatic_capture_callback_checklist_1" !in automaticRegion) {
            val callbackAnchor = "                        override fun onSuccess(screenshot: ScreenshotResult) {\n"
            if (callbackAnchor !in automaticRegion) throw GradleException("Callback da captura automatica nao encontrado.")
            automaticRegion = automaticRegion.replaceFirst(
                callbackAnchor,
                callbackAnchor + """                            if (!shouldScanPackage(packageName) ||
                                normalizePackageName(currentRootPackageName()) != normalizePackageName(packageName)
                            ) {
                                automaticCaptureInProgress129.set(false)
                                return // strict_automatic_capture_callback_checklist_1
                            }
""",
            )
        }
        service = service.substring(0, automaticStart) + automaticRegion + service.substring(automaticEnd)
    }

    listOf(
        "strict_selected_app_policy_checklist_1",
        "selected_packages_manual_only_checklist_1",
        "strict_selected_root_helper_checklist_1",
        "strict_schedule_gate_checklist_1",
        "strict_screenshot_gate_checklist_1",
        "strict_tree_gate_checklist_1",
        "strict_process_gate_checklist_1",
        "strict_route_gate_checklist_1",
        "strict_manual_tree_gate_checklist_1",
        "StrictSelectedAppReadPolicy.canRead",
        "SelectedRideAppStore.read(applicationContext)",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Contrato do checklist 1 ausente: $marker")
    }
    if (automaticStart >= 0) {
        listOf(
            "strict_automatic_capture_entry_checklist_1",
            "strict_automatic_capture_preflight_checklist_1",
            "strict_automatic_capture_callback_checklist_1",
        ).forEach { marker ->
            if (marker !in service) throw GradleException("Protecao da captura automatica ausente: $marker")
        }
    }

    serviceFile.writeText(service)
}

val strictSelectedAppReadChecklist1 by tasks.registering {
    outputs.upToDateWhen { false }
    doLast {
        patchStrictSelectedAppReadChecklist1(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(strictSelectedAppReadChecklist1)
    doLast {
        // Reaplica por ultimo, depois dos finalizadores historicos ligados ao preBuild.
        patchStrictSelectedAppReadChecklist1(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
