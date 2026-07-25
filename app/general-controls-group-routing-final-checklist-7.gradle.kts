// Checklist 7 — compatibilidade dos grupos antigos e ordem alfabética modular.

fun patchGeneralControlGroupRoutingChecklist7(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt ausente para roteamento do checklist 7.")
    var main = file.readText()
    if ("general_group_routing_complete_checklist_7" in main) return

    main = main
        .replace("mutableStateOf(BUBBLE_GROUP_ACCESS)", "mutableStateOf(BUBBLE_GROUP_GENERAL)")
        .replace("TAB_CONFIG -> BUBBLE_GROUP_ACCESS", "TAB_CONFIG -> BUBBLE_GROUP_GENERAL")
        .replace("else -> BUBBLE_GROUP_ACCESS", "else -> BUBBLE_GROUP_GENERAL")
        .replace(
            "name = if (isAlert) \"Alerta\" else \"Local salvo\"",
            "name = if (isAlert) \"Alerta\" else \"\" // blank_saved_place_name_checklist_7",
        )
        .replace(
            "    val items = savedPlaces.filter { it.type == type }",
            "    val items = SavedPlaceUiPolicy.sortedByName(savedPlaces.filter { it.type == type }) // alphabetical_module_checklist_7",
        )

    val whenStart = main.indexOf("        when (selectedGroup) {")
    val alertsStart = if (whenStart >= 0) main.indexOf("            BUBBLE_GROUP_ALERTS ->", whenStart) else -1
    if (whenStart < 0 || alertsStart < 0) throw GradleException("Roteamento agrupado ausente no checklist 7.")

    val generalPrefix = """        when (selectedGroup) {
            BUBBLE_GROUP_GENERAL,
            BUBBLE_GROUP_READING,
            BUBBLE_GROUP_ACCESS,
            -> {
                SystemControlCard(settings = draft, onChange = ::saveDraft)
                Spacer(Modifier.height(10.dp))
                AlwaysLocationPermissionCard(
                    hasAlwaysPermission = hasAlwaysLocationPermission(context),
                    onOpenLocationSettings = { openAppLocationSettings(context) },
                )
            } // legacy_access_groups_to_general_checklist_7
"""
    main = main.substring(0, whenStart) + generalPrefix + main.substring(alertsStart)

    listOf(
        "legacy_access_groups_to_general_checklist_7",
        "blank_saved_place_name_checklist_7",
        "alphabetical_module_checklist_7",
        "mutableStateOf(BUBBLE_GROUP_GENERAL)",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Roteamento final ausente: $marker")
    }

    val routedStart = main.indexOf("legacy_access_groups_to_general_checklist_7")
    val alertsAfter = if (routedStart >= 0) main.indexOf("BUBBLE_GROUP_ALERTS ->", routedStart) else -1
    if (routedStart < 0 || alertsAfter <= routedStart) {
        throw GradleException("Não foi possível delimitar o grupo geral final.")
    }
    val routedRegion = main.substring(routedStart, alertsAfter)
    if ("LiveReadingCard(" in routedRegion) {
        throw GradleException("Grupo geral ainda usa cartão antigo de leitura.")
    }

    main += "\n// general_group_routing_complete_checklist_7\n"
    file.writeText(main)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchGeneralControlGroupRoutingChecklist7(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}

apply(from = "checklist-7-final-contract.gradle.kts")
