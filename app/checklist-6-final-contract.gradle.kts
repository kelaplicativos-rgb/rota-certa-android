// Inspeção final do código que será efetivamente compilado na etapa 6.
fun verifyChecklist6FinalContract(
    serviceFile: java.io.File,
    mainFile: java.io.File,
    mapsFile: java.io.File,
) {
    val service = serviceFile.readText()
    val main = mainFile.readText()
    val maps = mapsFile.readText()

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
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        verifyChecklist6FinalContract(
            serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
            mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
            mapsFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt").asFile,
        )
    }
}
