// Restaura o estado funcional de modelos de cards removido por patches universais antigos.
fun enforcePopupNavigationCardState120(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado.")
    var text = file.readText()

    val appStart = text.indexOf("@Composable\nfun RotaCertaApp(launchIntent: Intent?) {")
    val appEnd = if (appStart >= 0) text.indexOf("\n@Composable\nprivate fun AnalysisScreen(", appStart) else -1
    if (appStart < 0 || appEnd <= appStart) throw GradleException("RotaCertaApp nao encontrada.")
    var app = text.substring(appStart, appEnd)

    if ("val cardTemplates by repository.cardTemplates.collectAsState" !in app) {
        val anchor = "    val diagnostic by repository.diagnostic.collectAsState(initial = null)\n"
        if (anchor !in app) throw GradleException("Estado diagnostic nao encontrado.")
        app = app.replaceFirst(
            anchor,
            anchor + "    val cardTemplates by repository.cardTemplates.collectAsState(initial = emptyList())\n",
        )
    }

    if ("val ocrService = remember { OcrService(context) }" !in app) {
        val anchor = "    val radarImportSummary by repository.radarImportSummary.collectAsState(initial = RadarImportSummary())\n"
        if (anchor !in app) throw GradleException("Estado radarImportSummary nao encontrado.")
        app = app.replaceFirst(anchor, anchor + "    val ocrService = remember { OcrService(context) }\n")
    }

    if ("var templateStatus by remember" !in app) {
        val anchor = "    var liveEnabled by remember { mutableStateOf(isLiveAccessibilityEnabled(context)) }\n"
        if (anchor !in app) throw GradleException("Estado liveEnabled nao encontrado.")
        app = app.replaceFirst(
            anchor,
            anchor + """    var templateStatus by remember { mutableStateOf("Modelos cadastrados: ${'$'}{cardTemplates.size}") }
    var unreadTemplatePrints by remember { mutableStateOf(0) }
""",
        )
    }

    if ("fun registerRideCard(packageName: String?, text: String)" !in app) {
        val anchor = "    fun renameSavedPlace(place: SavedPlace, name: String) {\n"
        if (anchor !in app) throw GradleException("Funcao renameSavedPlace nao encontrada.")
        val functions = """    fun registerRideCard(packageName: String?, text: String) {
        if (text.isBlank()) {
            Toast.makeText(context, "Nao ha texto lido para cadastrar", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val inferredPackageName = packageName ?: RideCardTemplateMatcher.inferPackageName(text)
            val template = RideCardTemplateMatcher.createTemplate(inferredPackageName, text)
            repository.addCardTemplate(template)
            templateStatus = "Modelo cadastrado: ${'$'}{template.name}"
            Toast.makeText(context, templateStatus, Toast.LENGTH_LONG).show()
        }
    }

    fun deleteCardModel(template: RideCardTemplate) {
        scope.launch {
            repository.removeCardTemplate(template.id)
            templateStatus = "Modelo removido: ${'$'}{template.name}"
            Toast.makeText(context, templateStatus, Toast.LENGTH_LONG).show()
        }
    }

"""
        app = app.replaceFirst(anchor, functions + anchor)
    } else if ("fun deleteCardModel(template: RideCardTemplate)" !in app) {
        val anchor = "    fun renameSavedPlace(place: SavedPlace, name: String) {\n"
        if (anchor !in app) throw GradleException("Funcao renameSavedPlace nao encontrada para deleteCardModel.")
        app = app.replaceFirst(
            anchor,
            """    fun deleteCardModel(template: RideCardTemplate) {
        scope.launch {
            repository.removeCardTemplate(template.id)
            templateStatus = "Modelo removido: ${'$'}{template.name}"
            Toast.makeText(context, templateStatus, Toast.LENGTH_LONG).show()
        }
    }

""" + anchor,
        )
    }

    if ("val cardModelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents())" !in app) {
        val anchor = "    val radarFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->\n"
        if (anchor !in app) throw GradleException("radarFilePicker nao encontrado.")
        val picker = """    val cardModelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            unreadTemplatePrints = 0
            templateStatus = "Lendo ${'$'}{uris.size} print(s)..."
            var failures = 0
            var imported = 0
            uris.forEach { uri ->
                val extractedText = runCatching { ocrService.extractText(uri) }.getOrDefault("")
                val packageName = RideCardTemplateMatcher.inferPackageName(extractedText)
                if (extractedText.isBlank() || packageName == null) {
                    failures += 1
                } else {
                    val template = RideCardTemplateMatcher.createTemplate(packageName, extractedText)
                    repository.addCardTemplate(template)
                    imported += 1
                }
            }
            unreadTemplatePrints = failures
            templateStatus = when {
                failures == 0 -> "Leitura concluida: ${'$'}imported modelo(s) importado(s)."
                imported == 0 -> "Nenhum modelo importado. Confira se os prints sao cards de corrida."
                else -> "Leitura concluida: ${'$'}imported modelo(s), ${'$'}failures print(s) sem leitura."
            }
            Toast.makeText(context, templateStatus, Toast.LENGTH_LONG).show()
        }
    }

"""
        app = app.replaceFirst(anchor, picker + anchor)
    }

    if ("LaunchedEffect(cardTemplates.size)" !in app) {
        val anchor = "    LaunchedEffect(launchIntent) {\n"
        if (anchor !in app) throw GradleException("LaunchedEffect launchIntent nao encontrado.")
        app = app.replaceFirst(
            anchor,
            """    LaunchedEffect(cardTemplates.size) {
        if (!templateStatus.startsWith("Lendo ")) {
            templateStatus = "Modelos cadastrados: ${'$'}{cardTemplates.size}"
        }
    }

""" + anchor,
        )
    }

    text = text.substring(0, appStart) + app + text.substring(appEnd)
    if ("popup_navigation_card_state_0_1_120" !in text) {
        text += "\n// popup_navigation_card_state_0_1_120\n"
    }
    listOf(
        "val cardTemplates by repository.cardTemplates.collectAsState",
        "var templateStatus by remember",
        "fun registerRideCard(packageName: String?, text: String)",
        "fun deleteCardModel(template: RideCardTemplate)",
        "val cardModelPicker = rememberLauncherForActivityResult",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Estado de Cards incompleto: $marker")
    }
    file.writeText(text)
}

val popupNavigationCardState120 by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn("popupNavigationCompileRepair120")
    doLast { enforcePopupNavigationCardState120(mainFile.asFile) }
}

popupNavigationCardState120.configure {
    mustRunAfter("popupNavigationCompileRepair120")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(popupNavigationCardState120)
}
