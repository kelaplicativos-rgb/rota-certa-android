// Contrato final da etapa 15.

fun verifyUniversalManualPackageChecklist15(root: java.io.File) {
    val service = java.io.File(root, "LiveRideAccessibilityService.kt").readText()
    val strict = java.io.File(root, "StrictSelectedAppReadPolicy.kt").readText()
    val stability = java.io.File(root, "FarolDisplayStabilityPolicy.kt").readText()
    val parser = java.io.File(root, "UniversalScreenAddressParser.kt").readText()
    val selectedStore = java.io.File(root, "SelectedRideAppStore.kt").readText()
    val repository = java.io.File(root, "SettingsRepository.kt").readText()
    val models = java.io.File(root, "Models.kt").readText()
    val main = java.io.File(root, "MainActivity.kt").readText()

    listOf(
        "manual_package_overrides_legacy_classification_checklist_15",
        "destination_only_stability_checklist_15",
        "fixed_absence_window_checklist_15",
        "no_via_app_false_address_checklist_15",
        "selected_package_add_remove_checklist_15",
        "card_adds_package_checklist_15",
        "last_card_removes_package_checklist_15",
        "no_predefined_card_contract_checklist_15",
        "fixed_absence_confirmation_job_checklist_15",
        "valid_read_cancels_absence_checklist_15",
        "same_destination_no_ocr_no_repaint_checklist_15",
        "overlay_idempotent_same_value_checklist_15",
        "no_duplicate_overlay_render_checklist_15",
        "clear_captures_prunes_packages_checklist_15",
        "delete_capture_prunes_package_checklist_15",
    ).forEach { marker ->
        val combined = listOf(service, strict, stability, parser, selectedStore, repository, models, main).joinToString("\n")
        if (marker !in combined) throw GradleException("Contrato 15 ausente: $marker")
    }

    if ("if (!appEnabled || !liveReadingEnabled || !packageAllowedByPlatformPolicy)" in strict) {
        throw GradleException("Classificacao predefinida ainda bloqueia pacote capturado manualmente.")
    }
    if ("PARTIAL_ABSENCE_CONFIRM_MILLIS = 90L" in stability) {
        throw GradleException("Janela antiga de 90 ms ainda pode causar pisca-pisca.")
    }
    if ("scrolled" in stability || "definitiveWindowEvent" in stability) {
        throw GradleException("Mapa/rolagem ainda participa da limpeza da decisao.")
    }
    if ("requireRegisteredRideCard: Boolean = true" in models ||
        "prefs[requireRegisteredRideCard] ?: true" in repository
    ) {
        throw GradleException("Modelo predefinido voltou a nascer obrigatorio.")
    }
    if ("Via app" in parser && "no_via_app_false_address_checklist_15" !in parser) {
        throw GradleException("Falso destino Via app nao foi bloqueado.")
    }

    val showStart = service.indexOf("    private fun showOverlay(")
    val formatStart = service.indexOf("    private fun formatBubbleDistanceKm(", showStart)
    if (showStart < 0 || formatStart <= showStart) throw GradleException("showOverlay final nao localizado.")
    val showRegion = service.substring(showStart, formatStart)
    if ("existingViewChecklist15.text.toString() == nextTextChecklist15" !in showRegion) {
        throw GradleException("Bolinha ainda redesenha o mesmo valor.")
    }

    val keepCurrent = """FarolDisplayStabilityPolicy.Action.KeepCurrent -> {
                return // same_destination_no_ocr_no_repaint_checklist_15
            }"""
    if (keepCurrent !in service) throw GradleException("Mesmo destino ainda inicia OCR/repintura.")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        verifyUniversalManualPackageChecklist15(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}
