// Contrato final da etapa 11 — falhas observadas no vídeo real.

fun verifyRealSessionChecklist11(root: java.io.File) {
    val service = java.io.File(root, "LiveRideAccessibilityService.kt").readText()
    val main = java.io.File(root, "MainActivity.kt").readText()
    val repository = java.io.File(root, "Repositories.kt").readText()
    val matcher = java.io.File(root, "RideCardTemplateMatcher.kt").readText()

    listOf(
        "backup_key_preservation_checklist_11",
        "GoogleMapsApiKeyPolicy.valueAfterRestore",
        "restoredSettingsChecklist11",
    ).forEach { marker ->
        if (marker !in repository) throw GradleException("Restauração ainda pode apagar a chave: $marker")
    }

    listOf(
        "api_key_in_general_controls_checklist_11",
        "Chave Google Maps API: obrigatória para o farol verde/vermelho",
        "Sem a chave, o card pode ser reconhecido e fotografado",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Chave da API não nasceu em Controles gerais: $marker")
    }

    listOf(
        "real_world_match_policy_checklist_11",
        "RealWorldRideCardMatchPolicy.evaluate",
    ).forEach { marker ->
        if (marker !in matcher) throw GradleException("Modelo real de card ainda rígido demais: $marker")
    }

    listOf(
        "selected_overlay_event_bridge_checklist_11",
        "selected_overlay_root_bridge_checklist_11",
        "selected_overlay_window_bridge_checklist_11",
        "selected_overlay_tree_bridge_checklist_11",
        "manual_phone_tree_read_checklist_11",
        "collectPhoneVisibleTextChecklist11()",
        "screenshot.toSoftwareBitmap()",
        "missing_api_stays_yellow_checklist_11",
        "effective_api_key_checklist_11",
        "full_screen_ocr_capture_checklist_11",
        "FullScreenRideCapturePolicy.shouldSaveCandidate",
        "real_session_video_fixes_complete_checklist_11",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Serviço real incompleto: $marker")
    }

    val phoneStart = service.indexOf("private fun capturePhoneAndOpenWhatsApp118()")
    val phoneEnd = service.indexOf("private fun openWhatsAppTarget118", phoneStart)
    if (phoneStart < 0 || phoneEnd <= phoneStart) throw GradleException("Fluxo do telefone não localizado.")
    val phoneRegion = service.substring(phoneStart, phoneEnd)
    if ("collectVisibleTextForAction()" in phoneRegion) {
        throw GradleException("WhatsApp ainda depende da portaria dos aplicativos de corrida.")
    }

    val routeStart = service.indexOf("private suspend fun analyzeUniversalTwoAddress(")
    val routeEnd = service.indexOf("private suspend fun applyUniversalTwoAddressResult(", routeStart)
    if (routeStart < 0 || routeEnd <= routeStart) throw GradleException("Rota final não localizada.")
    val routeRegion = service.substring(routeStart, routeEnd)
    if ("apiKey = settings.googleMapsApiKey" in routeRegion) {
        throw GradleException("Rota ainda ignora a chave incluída no build.")
    }
    if ("showOverlay(RadarColor.Default, distanceKm = null)" !in routeRegion) {
        throw GradleException("Ausência de API não mantém o farol amarelo.")
    }

    val screenshotStart = service.indexOf("private fun requestScreenshotAnalysis(")
    val collectStart = service.indexOf("private fun collectVisibleText(", screenshotStart)
    val screenshotRegion = service.substring(screenshotStart, collectStart)
    if ("saveCard(\n                                        bitmap = bitmap" !in screenshotRegion) {
        throw GradleException("O bitmap completo do OCR não está sendo salvo.")
    }

    listOf(
        "overlay_before_storage_final_checklist_6",
        "ocr_outside_critical_path_final_checklist_6",
        "low_priority_capture_final_checklist_6",
        "strict_selected_app_policy_checklist_1",
        "diagnostics_off_checklist_4",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Proteção anterior perdida: $marker")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        verifyRealSessionChecklist11(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}
