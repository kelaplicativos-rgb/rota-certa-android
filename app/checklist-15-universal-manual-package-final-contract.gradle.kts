// Contrato final da etapa 15.

fun readGeneratedClassChecklist15(root: java.io.File, preferredName: String, classMarker: String): String {
    val preferred = java.io.File(root, preferredName)
    if (preferred.exists()) return preferred.readText()
    return root.listFiles()
        ?.firstOrNull { it.isFile && it.extension == "kt" && classMarker in runCatching { it.readText() }.getOrDefault("") }
        ?.readText()
        ?: throw GradleException("Classe gerada ausente no contrato 15: $classMarker")
}

fun verifyUniversalManualPackageChecklist15(root: java.io.File) {
    val service = readGeneratedClassChecklist15(root, "LiveRideAccessibilityService.kt", "class LiveRideAccessibilityService")
    val strict = readGeneratedClassChecklist15(root, "StrictSelectedAppReadPolicy.kt", "object StrictSelectedAppReadPolicy")
    val stability = readGeneratedClassChecklist15(root, "FarolDisplayStabilityPolicy.kt", "object FarolDisplayStabilityPolicy")
    val parser = readGeneratedClassChecklist15(root, "UniversalScreenAddressParser.kt", "object UniversalScreenAddressParser")
    val selectedStore = readGeneratedClassChecklist15(root, "SelectedRideAppStore.kt", "object SelectedRideAppStore")
    val repository = readGeneratedClassChecklist15(root, "SettingsRepository.kt", "class SettingsRepository")
    val models = readGeneratedClassChecklist15(root, "Models.kt", "data class AppSettings")
    val main = readGeneratedClassChecklist15(root, "MainActivity.kt", "class MainActivity")
    val combined = listOf(service, strict, stability, parser, selectedStore, repository, models, main).joinToString("\n")

    listOf(
        "manual_package_overrides_legacy_classification_checklist_15",
        "destination_only_stability_checklist_15",
        "fixed_absence_window_checklist_15",
        "no_via_app_false_address_checklist_15",
        "selected_package_add_remove_checklist_15",
        "card_adds_package_checklist_15",
        "last_card_removes_package_checklist_15",
        "fixed_absence_confirmation_job_checklist_15",
        "valid_read_cancels_absence_checklist_15",
        "same_destination_no_ocr_no_repaint_checklist_15",
        "overlay_idempotent_same_value_checklist_15",
        "no_duplicate_overlay_render_checklist_15",
        "clear_captures_prunes_packages_checklist_15",
        "delete_capture_prunes_package_checklist_15",
    ).forEach { marker ->
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
