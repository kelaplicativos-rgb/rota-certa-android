// Checklist 7 — catálogo do popup e criação de locais sem nome preenchido.

fun patchPopupCatalogChecklist7(file: java.io.File) {
    if (!file.exists()) throw GradleException("BubbleShortcutModule.kt ausente no checklist 7.")
    var text = file.readText()

    text = text.lineSequence()
        .filterNot { line ->
            line.trim() == "ReadingBubbleShortcutModule," ||
                line.trim() == "PermissionsBubbleShortcutModule,"
        }
        .joinToString("\n")

    val listToken = "    val modules: List<BubbleShortcutModule> = listOf(\n"
    val start = text.indexOf(listToken)
    val end = if (start >= 0) text.indexOf("    )", start + listToken.length) else -1
    if (start < 0 || end < 0) throw GradleException("Catálogo final ausente no checklist 7.")
    val region = text.substring(start, end)
    val count = Regex("(?m)^\\s{8}[A-Za-z0-9_]+,\\s*$").findAll(region).count()
    text = text.replace(
        Regex("""require\(modules\.size == \d+\) \{ "[^"]*" \}"""),
        "require(modules.size == $count) { \"O popup deve conter $count módulos.\" }",
    )

    if ("ReadingBubbleShortcutModule," in region || "PermissionsBubbleShortcutModule," in region) {
        throw GradleException("Leitura ou permissão continuam no popup flutuante.")
    }
    if ("reading_permission_moved_out_of_popup_checklist_7" !in text) {
        text += "\n// reading_permission_moved_out_of_popup_checklist_7\n"
    }
    file.writeText(text)
}

fun patchSavedPlaceCreationChecklist7(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no checklist 7.")
    var service = file.readText()

    service = service
        .replace(
            "defaultName: String = if (type == SavedPlaceType.ProximityAlert) \"Alerta\" else \"Local salvo\"",
            "defaultName: String = if (type == SavedPlaceType.ProximityAlert) \"Alerta\" else \"\"",
        )
        .replace(
            "name = if (isAlert) \"Alerta de proximidade\" else \"Local salvo\"",
            "name = if (isAlert) \"Alerta de proximidade\" else \"\"",
        )
        .replace(
            "addView(quickToggleBubbleButton(\"Leitura\", QuickBubbleToggle.LiveReading, currentSettings.liveReadingEnabled))\n",
            "",
        )

    if ("saved_place_blank_name_checklist_7" !in service) {
        service += "\n// saved_place_blank_name_checklist_7\n"
    }
    file.writeText(service)
}

fun verifyPopupAndPlacesChecklist7(
    catalogFile: java.io.File,
    serviceFile: java.io.File,
    overlayFile: java.io.File,
) {
    val catalog = catalogFile.readText()
    val service = serviceFile.readText()
    val overlay = overlayFile.readText()

    if ("ReadingBubbleShortcutModule," in catalog || "PermissionsBubbleShortcutModule," in catalog) {
        throw GradleException("Leitura ou permissão reapareceram no catálogo.")
    }
    if ("else \"Local salvo\"" in service) {
        throw GradleException("Novo local ainda recebe nome preenchido.")
    }
    listOf(
        "PopupAppearanceStore(context)",
        "LARGE_SCALE_TWO_COLUMNS",
        "scaledSp(10f, scale)",
    ).forEach { marker ->
        if (marker !in overlay) throw GradleException("Escala acessível do popup ausente: $marker")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        val root = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile
        val catalog = java.io.File(root, "BubbleShortcutModule.kt")
        val service = java.io.File(root, "LiveRideAccessibilityService.kt")
        val overlay = java.io.File(root, "BubbleShortcutOverlayController.kt")
        patchPopupCatalogChecklist7(catalog)
        patchSavedPlaceCreationChecklist7(service)
        verifyPopupAndPlacesChecklist7(catalog, service, overlay)
    }
}

apply(from = "general-controls-group-routing-final-checklist-7.gradle.kts")
