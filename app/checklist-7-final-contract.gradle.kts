// Inspeção final do código efetivamente compilado após a organização da etapa 7.

fun verifyChecklist7FinalContract(
    mainFile: java.io.File,
    catalogFile: java.io.File,
    serviceFile: java.io.File,
    overlayFile: java.io.File,
    savedPlaceModuleFile: java.io.File,
) {
    val main = mainFile.readText()
    val catalog = catalogFile.readText()
    val service = serviceFile.readText()
    val overlay = overlayFile.readText()
    val savedPlaceModule = savedPlaceModuleFile.readText()

    listOf(
        "general_controls_final_checklist_7",
        "label = \"Leitura ao vivo\"",
        "Permissão de acessibilidade",
        "popup_scale_ui_final_checklist_7",
        "KeyboardActions(onDone = { saveName() })",
        "SavedPlaceUiPolicy.sortedByName",
        "legacy_access_groups_to_general_checklist_7",
        "alphabetical_module_checklist_7",
        "blank_saved_place_name_checklist_7",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Contrato final da interface 7 ausente: $marker")
    }

    if ("AppControlBubble(\"Leitura\"" in main || "AppControlBubble(\"Acesso\"" in main) {
        throw GradleException("Controles gerais continuam duplicados em bolinhas internas.")
    }
    if ("ReadingBubbleShortcutModule," in catalog || "PermissionsBubbleShortcutModule," in catalog) {
        throw GradleException("Leitura ou permissão reapareceram no popup flutuante.")
    }
    val validCatalogSize = "require(modules.size == 14)" in catalog ||
        "require(modules.size == 15)" in catalog
    if (!validCatalogSize) {
        throw GradleException("Catálogo final não contém a grade operacional esperada.")
    }
    if ("defaultName = \"\"" !in savedPlaceModule) {
        throw GradleException("Atalho de Local voltou a preencher um nome automaticamente.")
    }
    if ("else \"Local salvo\"" in service) {
        throw GradleException("Serviço voltou a preencher Local salvo automaticamente.")
    }
    listOf(
        "PopupAppearanceStore(context)",
        "LARGE_SCALE_TWO_COLUMNS",
        "appearanceStore.scale()",
    ).forEach { marker ->
        if (marker !in overlay) throw GradleException("Popup acessível incompleto: $marker")
    }

    // A reorganização visual não pode retirar proteções do caminho crítico anterior.
    listOf(
        "overlay_before_storage_final_checklist_6",
        "ocr_outside_critical_path_final_checklist_6",
        "low_priority_capture_final_checklist_6",
        "strict_selected_app_policy_checklist_1",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Contrato operacional anterior perdido: $marker")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        val root = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile
        verifyChecklist7FinalContract(
            mainFile = java.io.File(root, "MainActivity.kt"),
            catalogFile = java.io.File(root, "BubbleShortcutModule.kt"),
            serviceFile = java.io.File(root, "LiveRideAccessibilityService.kt"),
            overlayFile = java.io.File(root, "BubbleShortcutOverlayController.kt"),
            savedPlaceModuleFile = java.io.File(root, "SavedPlaceBubbleShortcutModule.kt"),
        )
    }
}
