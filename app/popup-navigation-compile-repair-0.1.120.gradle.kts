// Reparos finais de compilacao para a navegacao separada 0.1.120.
// Executa depois de todos os patches visuais anteriores.

fun replaceInside120(source: String, start: Int, end: Int, old: String, new: String): String {
    val region = source.substring(start, end)
    if (old !in region) return source
    return source.substring(0, start) + region.replaceFirst(old, new) + source.substring(end)
}

fun enforcePopupNavigationCompileRepair120(serviceFile: java.io.File, mainFile: java.io.File) {
    if (!serviceFile.exists() || !mainFile.exists()) throw GradleException("Fontes 0.1.120 nao encontradas.")

    var service = serviceFile.readText()
    val serviceInsert = service.indexOf("    private fun toggleLiveReadingFromBubble() {")
    if (serviceInsert < 0) throw GradleException("Ponto de reparo do servico nao encontrado.")
    val missingHelpers = buildString {
        if ("private fun openCollectorFromBubble()" !in service) {
            append(
                """    private fun openCollectorFromBubble() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        runCatching {
            startActivity(
                Intent(this@LiveRideAccessibilityService, BlaBlaCarCollectorActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { toast("Nao foi possivel abrir o Coletor.") }
    }

""",
            )
        }
        if ("private fun exportDiagnosticFromBubble()" !in service) {
            append(
                """    private fun exportDiagnosticFromBubble() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        DiagnosticLogStore.record("bubble_action", "diagnostic export requested")
        runCatching {
            startActivity(
                Intent(this@LiveRideAccessibilityService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_HISTORY)
                    .putExtra(EXTRA_OPEN_BUBBLE_GROUP, "reports")
                    .putExtra("auto_export_report", true),
            )
        }.onFailure { toast("Nao foi possivel abrir a exportacao do relatorio.") }
    }

""",
            )
        }
    }
    if (missingHelpers.isNotEmpty()) {
        service = service.substring(0, serviceInsert) + missingHelpers + service.substring(serviceInsert)
    }
    if ("popup_navigation_compile_service_0_1_120" !in service) {
        service += "\n// popup_navigation_compile_service_0_1_120\n"
    }
    listOf(
        "private fun openCollectorFromBubble()",
        "private fun exportDiagnosticFromBubble()",
        "BubbleShortcutAction.OpenCollector -> openCollectorFromBubble()",
        "BubbleShortcutAction.ExportDiagnostic -> exportDiagnosticFromBubble()",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Reparo do servico incompleto: $marker")
    }
    serviceFile.writeText(service)

    var main = mainFile.readText()

    // Garante os argumentos na chamada principal de SettingsScreen.
    val callStart = main.indexOf("                TAB_CONFIG -> SettingsScreen(")
    val callEnd = if (callStart >= 0) main.indexOf("                TAB_TOOLS ->", callStart) else -1
    if (callStart < 0 || callEnd <= callStart) throw GradleException("Chamada SettingsScreen nao encontrada.")
    var call = main.substring(callStart, callEnd)
    if ("templateStatus = templateStatus" !in call) {
        val anchor = "                    cardTemplates = cardTemplates,\n"
        if (anchor !in call) throw GradleException("Argumento cardTemplates nao encontrado na chamada.")
        call = call.replaceFirst(
            anchor,
            anchor + """                    templateStatus = templateStatus,
                    unreadTemplatePrints = unreadTemplatePrints,
                    onPickCardModels = { cardModelPicker.launch("image/*") },
                    onDeleteCardModel = ::deleteCardModel,
""",
        )
    }
    if ("onCreateSavedPlace =" !in call) {
        val anchor = "                    onRegisterRideCard = ::registerRideCard,\n"
        if (anchor !in call) throw GradleException("Argumento onRegisterRideCard nao encontrado na chamada.")
        call = call.replaceFirst(
            anchor,
            anchor + """                    onCreateSavedPlace = { createSavedPlaceFromHome(SavedPlaceType.Place) },
                    onCreateProximityAlert = { createSavedPlaceFromHome(SavedPlaceType.ProximityAlert) },
""",
        )
    }
    main = main.substring(0, callStart) + call + main.substring(callEnd)

    // Garante os parametros na assinatura de SettingsScreen.
    val settingsStart = main.indexOf("@Composable\nprivate fun SettingsScreen(")
    val settingsBody = if (settingsStart >= 0) main.indexOf(") {", settingsStart) else -1
    if (settingsStart < 0 || settingsBody <= settingsStart) throw GradleException("Assinatura SettingsScreen nao encontrada.")
    var signature = main.substring(settingsStart, settingsBody)
    if ("templateStatus: String" !in signature) {
        val anchor = "    cardTemplates: List<RideCardTemplate>,\n"
        if (anchor !in signature) throw GradleException("Parametro cardTemplates nao encontrado.")
        signature = signature.replaceFirst(
            anchor,
            anchor + """    templateStatus: String,
    unreadTemplatePrints: Int,
    onPickCardModels: () -> Unit,
    onDeleteCardModel: (RideCardTemplate) -> Unit,
""",
        )
    }
    if ("onCreateSavedPlace: () -> Unit" !in signature) {
        val anchor = "    onRegisterRideCard: (String?, String) -> Unit,\n"
        if (anchor !in signature) throw GradleException("Parametro onRegisterRideCard nao encontrado.")
        signature = signature.replaceFirst(
            anchor,
            anchor + """    onCreateSavedPlace: () -> Unit,
    onCreateProximityAlert: () -> Unit,
""",
        )
    }
    main = main.substring(0, settingsStart) + signature + main.substring(settingsBody)

    // Usa um modulo de Cards proprio, sem depender da funcao antiga removida de Analise.
    main = main.replace(
        "            BUBBLE_GROUP_CARDS -> CardModelsCard(\n",
        "            BUBBLE_GROUP_CARDS -> RegisteredCardsModuleCard(\n",
    )
    if ("private fun RegisteredCardsModuleCard(" !in main) {
        val insertion = main.indexOf("@Composable\nprivate fun SavedPlacesModuleCard(")
        if (insertion < 0) throw GradleException("Ponto de insercao do modulo Cards nao encontrado.")
        val cards = """@Composable
private fun RegisteredCardsModuleCard(
    cardTemplates: List<RideCardTemplate>,
    templateStatus: String,
    unreadTemplatePrints: Int,
    onPickCardModels: () -> Unit,
    onDeleteCardModel: (RideCardTemplate) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cards cadastrados", fontWeight = FontWeight.Bold)
            Text("Modelos cadastrados: ${'$'}{cardTemplates.size}")
            Button(onClick = onPickCardModels, modifier = Modifier.fillMaxWidth()) {
                Text("Anexar modelos de cards (prints)")
            }
            Text(templateStatus, style = MaterialTheme.typography.bodySmall)
            if (unreadTemplatePrints > 0) {
                Text("Prints sem leitura: ${'$'}unreadTemplatePrints", style = MaterialTheme.typography.bodySmall)
            }
            if (cardTemplates.isEmpty()) {
                Text("Nenhum modelo cadastrado ainda.", style = MaterialTheme.typography.bodySmall)
            } else {
                cardTemplates.forEach { template ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(template.name, fontWeight = FontWeight.Bold)
                            Text(template.packageName ?: "app nao identificado", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { onDeleteCardModel(template) }) {
                            Text("Apagar")
                        }
                    }
                }
            }
        }
    }
} // registered_cards_module_0_1_120

"""
        main = main.substring(0, insertion) + cards + main.substring(insertion)
    }

    if ("popup_navigation_compile_main_0_1_120" !in main) {
        main += "\n// popup_navigation_compile_main_0_1_120\n"
    }
    listOf(
        "templateStatus: String",
        "onCreateSavedPlace: () -> Unit",
        "onCreateProximityAlert: () -> Unit",
        "BUBBLE_GROUP_CARDS -> RegisteredCardsModuleCard(",
        "registered_cards_module_0_1_120",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Reparo MainActivity incompleto: $marker")
    }
    mainFile.writeText(main)
}

val popupNavigationCompileRepair120 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }
    dependsOn("popupNavigationProfessionalCompat120")
    doLast { enforcePopupNavigationCompileRepair120(serviceFile.asFile, mainFile.asFile) }
}

popupNavigationCompileRepair120.configure {
    mustRunAfter("popupNavigationProfessionalCompat120")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(popupNavigationCompileRepair120)
}
