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

    // Garante os argumentos na chamada principal de SettingsScreen usando
    // savedPlaces/onRename como ancoras estaveis, mesmo sem cardTemplates antigo.
    val callStart = main.indexOf("                TAB_CONFIG -> SettingsScreen(")
    val callEnd = if (callStart >= 0) main.indexOf("                TAB_TOOLS ->", callStart) else -1
    if (callStart < 0 || callEnd <= callStart) throw GradleException("Chamada SettingsScreen nao encontrada.")
    var call = main.substring(callStart, callEnd)
    if ("templateStatus = templateStatus" !in call) {
        val anchor = Regex("(?m)^\\s*savedPlaces\\s*=\\s*savedPlaces,\\s*$").find(call)
            ?: throw GradleException("Argumento savedPlaces nao encontrado na chamada.")
        val indent = anchor.value.takeWhile(Char::isWhitespace).substringAfterLast('\n')
        val addition = buildString {
            if ("cardTemplates = cardTemplates" !in call) append(indent + "cardTemplates = cardTemplates,\n")
            append(indent + "templateStatus = templateStatus,\n")
            append(indent + "unreadTemplatePrints = unreadTemplatePrints,\n")
            append(indent + "onPickCardModels = { cardModelPicker.launch(\"image/*\") },\n")
            append(indent + "onDeleteCardModel = ::deleteCardModel,\n")
        }
        call = call.substring(0, anchor.range.first) + addition + call.substring(anchor.range.first)
    }
    if ("onCreateSavedPlace =" !in call) {
        val anchor = Regex("(?m)^\\s*onRenameSavedPlace\\s*=.*$").find(call)
            ?: throw GradleException("Argumento onRenameSavedPlace nao encontrado na chamada.")
        val indent = anchor.value.takeWhile(Char::isWhitespace).substringAfterLast('\n')
        val addition = buildString {
            if ("onRegisterRideCard =" !in call) append(indent + "onRegisterRideCard = ::registerRideCard,\n")
            append(indent + "onCreateSavedPlace = { createSavedPlaceFromHome(SavedPlaceType.Place) },\n")
            append(indent + "onCreateProximityAlert = { createSavedPlaceFromHome(SavedPlaceType.ProximityAlert) },\n")
        }
        call = call.substring(0, anchor.range.first) + addition + call.substring(anchor.range.first)
    }
    main = main.substring(0, callStart) + call + main.substring(callEnd)

    // Garante os parametros na assinatura de SettingsScreen pelas mesmas ancoras.
    val settingsStart = main.indexOf("@Composable\nprivate fun SettingsScreen(")
    val settingsBody = if (settingsStart >= 0) main.indexOf(") {", settingsStart) else -1
    if (settingsStart < 0 || settingsBody <= settingsStart) throw GradleException("Assinatura SettingsScreen nao encontrada.")
    var signature = main.substring(settingsStart, settingsBody)
    if ("templateStatus: String" !in signature) {
        val anchor = "    savedPlaces: List<SavedPlace>,\n"
        if (anchor !in signature) throw GradleException("Parametro savedPlaces nao encontrado.")
        val addition = buildString {
            if ("cardTemplates: List<RideCardTemplate>" !in signature) append("    cardTemplates: List<RideCardTemplate>,\n")
            append("    templateStatus: String,\n")
            append("    unreadTemplatePrints: Int,\n")
            append("    onPickCardModels: () -> Unit,\n")
            append("    onDeleteCardModel: (RideCardTemplate) -> Unit,\n")
        }
        signature = signature.replaceFirst(anchor, addition + anchor)
    }
    if ("onCreateSavedPlace: () -> Unit" !in signature) {
        val anchor = "    onRenameSavedPlace: (SavedPlace, String) -> Unit,\n"
        if (anchor !in signature) throw GradleException("Parametro onRenameSavedPlace nao encontrado.")
        val addition = buildString {
            if ("onRegisterRideCard: (String?, String) -> Unit" !in signature) append("    onRegisterRideCard: (String?, String) -> Unit,\n")
            append("    onCreateSavedPlace: () -> Unit,\n")
            append("    onCreateProximityAlert: () -> Unit,\n")
        }
        signature = signature.replaceFirst(anchor, addition + anchor)
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
