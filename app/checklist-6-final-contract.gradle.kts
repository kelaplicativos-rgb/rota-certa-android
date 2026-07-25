// Inspeção final do código que será efetivamente compilado na etapa 6.
fun verifyChecklist6FinalContract(
    serviceFile: java.io.File,
    mainFile: java.io.File,
    mapsFile: java.io.File,
    captureStoreFile: java.io.File,
) {
    val service = serviceFile.readText()
    val main = mainFile.readText()
    val maps = mapsFile.readText()
    val captureStore = captureStoreFile.readText()

    listOf(
        "automatic_capture_after_farol_final_checklist_6",
        "low_priority_capture_final_checklist_6",
        "overlay_before_storage_final_checklist_6",
        "trigger_default_dispatcher_final_checklist_6",
        "matcher_default_dispatcher_final_checklist_6",
        "ocr_outside_critical_path_final_checklist_6",
        "accessibility_won_skip_ocr_final_checklist_6",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Serviço final perdeu o contrato 6: $marker")
    }
    val applyStart = service.indexOf("private suspend fun applyUniversalTwoAddressResult")
    val overlay = service.indexOf("overlay_before_storage_final_checklist_6", applyStart)
    val history = service.indexOf("repository.addAnalysis(result)", applyStart)
    if (applyStart < 0 || overlay < 0 || history < 0 || overlay > history) {
        throw GradleException("Histórico voltou a bloquear a pintura da bolinha.")
    }
    if ("requestAutomaticRideCapture129(\n                snapshotText" in service) {
        throw GradleException("Captura imediata voltou ao caminho crítico.")
    }

    val captureStart = service.indexOf("private fun requestAutomaticRideCapture129(request:")
    val screenshotStart = service.indexOf("private fun requestScreenshotAnalysis(", captureStart)
    if (captureStart < 0 || screenshotStart < 0) throw GradleException("Captura final não localizada.")
    val captureRegion = service.substring(captureStart, screenshotStart)
    listOf(
        "shouldScanPackage(packageName)",
        "normalizePackageName(currentRootPackageName()) == packageName",
        "universalRouteJob?.isActive == true",
        "scope.launch(Dispatchers.IO)",
    ).forEach { marker ->
        if (marker !in captureRegion) throw GradleException("Portaria da captura final ausente: $marker")
    }
    if ("ocrService.extractText" in captureRegion) {
        throw GradleException("Captura temporária voltou a executar OCR.")
    }

    val scanStart = service.indexOf("private fun startContinuousScan()")
    val scanEnd = service.indexOf("private fun startProximityAlertMonitor()", scanStart)
    if (scanStart < 0 || scanEnd < 0) throw GradleException("Ciclo de segurança final não localizado.")
    val scanRegion = service.substring(scanStart, scanEnd)
    if ("requestScreenshotAnalysis(" in scanRegion) {
        throw GradleException("Ciclo de segurança voltou a solicitar OCR imediatamente.")
    }
    if ("scheduleScreenshotFallback127" !in scanRegion) {
        throw GradleException("Ciclo de segurança perdeu o fallback controlado de OCR.")
    }

    if ("@Composable\n@Composable\nprivate fun AutomaticRideCaptureGallery129" in main) {
        throw GradleException("Anotação Compose duplicada na galeria final.")
    }
    listOf(
        "capture_library_split_final_checklist_6",
        "Candidatas a modelo",
        "Cards já reconhecidos",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Interface final perdeu o contrato 6: $marker")
    }
    listOf(
        "subsecond_connect_budget_checklist_6",
        "subsecond_read_budget_checklist_6",
        "single_route_attempt_checklist_6",
    ).forEach { marker ->
        if (marker !in maps) throw GradleException("Rede final perdeu o contrato 6: $marker")
    }
    if ("capture_tmp_write_safety_checklist_6" !in captureStore) {
        throw GradleException("Armazenamento final perdeu a proteção do JPEG temporário.")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        verifyChecklist6FinalContract(
            serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
            mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
            mapsFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt").asFile,
            captureStoreFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/AutomaticRideCaptureStore.kt").asFile,
        )
    }
}

// Usabilidade é aplicada somente depois de validar que o caminho crítico continua intacto.
apply(from = "general-controls-reading-marker-bridge-checklist-10.gradle.kts")
apply(from = "general-controls-ui-final-checklist-7.gradle.kts")
apply(from = "popup-catalog-locals-final-checklist-7.gradle.kts")
