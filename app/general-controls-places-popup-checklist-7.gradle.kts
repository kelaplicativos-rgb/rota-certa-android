// Checklist 7 — controles gerais, locais rápidos e popup acessível.

fun replaceFunctionChecklist7(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Função ausente no checklist 7: $signature")
    val open = source.indexOf('{', start)
    if (open < 0) throw GradleException("Corpo ausente no checklist 7: $signature")
    var depth = 0
    var index = open
    while (index < source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return source.substring(0, start) + replacement + source.substring(index + 1)
            }
        }
        index += 1
    }
    throw GradleException("Fim da função ausente no checklist 7: $signature")
}

fun removeBalancedCallChecklist7(source: String, token: String): String {
    val tokenIndex = source.indexOf(token)
    if (tokenIndex < 0) return source
    val callStart = source.lastIndexOf("addView(", tokenIndex)
    if (callStart < 0) return source
    val open = source.indexOf('(', callStart)
    var depth = 0
    var index = open
    while (index < source.length) {
        when (source[index]) {
            '(' -> depth += 1
            ')' -> {
                depth -= 1
                if (depth == 0) {
                    var end = index + 1
                    while (end < source.length && (source[end] == ' ' || source[end] == '\t')) end += 1
                    if (end < source.length && source[end] == '\n') end += 1
                    return source.substring(0, callStart) + source.substring(end)
                }
            }
        }
        index += 1
    }
    return source
}

fun addImportChecklist7(source: String, importLine: String, anchor: String): String {
    if (importLine in source) return source
    if (anchor !in source) throw GradleException("Âncora de importação ausente para $importLine")
    return source.replaceFirst(anchor, anchor + importLine + "\n")
}

fun patchMainUsabilityChecklist7(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt ausente no checklist 7.")
    var main = file.readText()
    if ("controls_places_popup_final_checklist_7" in main) return

    main = addImportChecklist7(main, "import androidx.compose.foundation.text.KeyboardActions", "import androidx.compose.foundation.rememberScrollState\n")
    main = addImportChecklist7(main, "import androidx.compose.foundation.text.KeyboardOptions", "import androidx.compose.foundation.text.KeyboardActions\n")
    main = addImportChecklist7(main, "import androidx.compose.ui.platform.LocalFocusManager", "import androidx.compose.ui.platform.LocalContext\n")
    main = addImportChecklist7(main, "import androidx.compose.ui.text.input.ImeAction", "import androidx.compose.ui.text.font.FontWeight\n")

    val systemControl = """@Composable
private fun SystemControlCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessibilityGranted by remember { mutableStateOf(isLiveAccessibilityEnabled(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityGranted = isLiveAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ExpandableCard(title = "Controles gerais", initiallyExpanded = true) {
        SettingsSwitchRow(
            label = "Rota Certa ligado",
            checked = settings.appEnabled,
            onCheckedChange = { enabled -> onChange(settings.copy(appEnabled = enabled)) },
        )
        SettingsSwitchRow(
            label = "Leitura ao vivo",
            checked = settings.liveReadingEnabled,
            onCheckedChange = { enabled -> onChange(settings.copy(liveReadingEnabled = enabled)) },
        )
        SettingsSwitchRow(
            label = "Falar radares e proximidade",
            checked = settings.proximityAlertsEnabled,
            onCheckedChange = { enabled -> onChange(settings.copy(proximityAlertsEnabled = enabled)) },
        )
        Text(
            if (accessibilityGranted) "Permissão de acessibilidade: concedida" else "Permissão de acessibilidade: pendente",
            fontWeight = FontWeight.Bold,
        )
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (accessibilityGranted) "Revisar permissão de acessibilidade" else "Conceder permissão de acessibilidade")
        }
        Text(
            when {
                !settings.appEnabled -> "Rota Certa pausado: leitura e avisos ficam em espera."
                !settings.liveReadingEnabled -> "Alertas podem continuar ativos, mas a leitura dos cards está pausada."
                else -> "Rota Certa e leitura ao vivo estão ativos."
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
} // general_controls_final_checklist_7
"""
    main = replaceFunctionChecklist7(main, "private fun SystemControlCard(settings: AppSettings, onChange: (AppSettings) -> Unit)", systemControl)

    val bubbleSettings = """@Composable
private fun BubbleSettingsCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val context = LocalContext.current
    val popupStore = remember { PopupAppearanceStore(context) }
    var popupScale by remember { mutableStateOf(popupStore.scale()) }

    ExpandableCard(title = "Bolinha e aparência", initiallyExpanded = false) {
        BubbleOpacitySlider(
            value = settings.bubbleOpacity,
            onValueChange = { onChange(settings.copy(bubbleOpacity = it)) },
            onValueChangeFinished = {},
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Cor mais escura")
            Switch(
                checked = settings.bubbleDarkMode,
                onCheckedChange = { onChange(settings.copy(bubbleDarkMode = it)) },
            )
        }
        Text("Tamanho do pop-up: ${(popupScale * 100).roundToInt()}%", fontWeight = FontWeight.Bold)
        Slider(
            value = popupScale.toFloat(),
            onValueChange = { popupScale = it.toDouble() },
            valueRange = PopupAppearanceStore.MIN_SCALE.toFloat()..PopupAppearanceStore.MAX_SCALE.toFloat(),
            onValueChangeFinished = { popupStore.setScale(popupScale) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Em tamanhos maiores, o pop-up usa duas colunas e aumenta também a fonte das bolinhas.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Toque na bolinha para abrir o pop-up. Arraste para mudar a posição.", style = MaterialTheme.typography.bodySmall)
    }
} // popup_scale_ui_final_checklist_7
"""
    main = replaceFunctionChecklist7(main, "private fun BubbleSettingsCard(settings: AppSettings, onChange: (AppSettings) -> Unit)", bubbleSettings)

    val savedPlacesCard = """@Composable
private fun SavedPlacesCard(
    savedPlaces: List<SavedPlace>,
    highlightedSavedPlaceId: String?,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
) {
    val places = SavedPlaceUiPolicy.sortedByName(savedPlaces.filter { it.type == SavedPlaceType.Place })
    val alerts = SavedPlaceUiPolicy.sortedByName(savedPlaces.filter { it.type == SavedPlaceType.ProximityAlert })
    val highlightedType = savedPlaces.firstOrNull { it.id == highlightedSavedPlaceId }?.type

    if (highlightedType == SavedPlaceType.Place) {
        Text("Digite o nome do local e toque em Concluir no teclado.", style = MaterialTheme.typography.bodySmall)
    } else if (highlightedType == SavedPlaceType.ProximityAlert) {
        Text("Confira ou altere o nome que será falado no alerta.", style = MaterialTheme.typography.bodySmall)
    }

    ExpandableCard(title = "Locais salvos (${places.size})", initiallyExpanded = highlightedType == SavedPlaceType.Place) {
        if (places.isEmpty()) {
            Text("Nenhum local salvo ainda.", style = MaterialTheme.typography.bodySmall)
        } else {
            places.forEach { place ->
                SavedPlaceEditor(place, place.id == highlightedSavedPlaceId, onRenameSavedPlace, onDeleteSavedPlace)
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    ExpandableCard(title = "Alertas de proximidade (${alerts.size})", initiallyExpanded = highlightedType == SavedPlaceType.ProximityAlert) {
        if (alerts.isEmpty()) {
            Text("Nenhum alerta de proximidade criado ainda.", style = MaterialTheme.typography.bodySmall)
        } else {
            alerts.forEach { place ->
                SavedPlaceEditor(place, place.id == highlightedSavedPlaceId, onRenameSavedPlace, onDeleteSavedPlace)
            }
        }
    }
} // saved_places_alphabetical_final_checklist_7
"""
    main = replaceFunctionChecklist7(main, "private fun SavedPlacesCard(", savedPlacesCard)

    val savedPlaceEditor = """@Composable
private fun SavedPlaceEditor(
    place: SavedPlace,
    highlighted: Boolean = false,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var draftName by remember(place.id, place.name, highlighted) {
        mutableStateOf(SavedPlaceUiPolicy.initialDraftName(place, highlighted))
    }

    fun saveName() {
        val cleanName = draftName.trim()
        if (!SavedPlaceUiPolicy.canSave(place, cleanName)) return
        onRenameSavedPlace(place, cleanName)
        focusManager.clearFocus()
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(savedPlaceTypeLabel(place), fontWeight = FontWeight.Bold)
        if (highlighted && place.type == SavedPlaceType.Place) {
            Text("O campo começa vazio para você escrever o nome diretamente.", style = MaterialTheme.typography.bodySmall)
        } else if (highlighted) {
            Text("O nome sugerido pode ser mantido ou alterado.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = draftName,
            onValueChange = { draftName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (place.type == SavedPlaceType.ProximityAlert) "Nome falado no alerta" else "Nome do local") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { saveName() }),
        )
        if (place.type == SavedPlaceType.ProximityAlert) {
            Text(
                "O app vai falar: ${draftName.ifBlank { defaultSavedPlaceName(place.type) }} se aproximando.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(place.address.ifBlank { formatCoordinate(place.coordinate) }, style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openSavedPlaceInGps(context, place) }, modifier = Modifier.weight(1f)) {
                Text("GPS")
            }
            Button(
                enabled = SavedPlaceUiPolicy.canSave(place, draftName) && draftName.trim() != place.name.trim(),
                onClick = { saveName() },
                modifier = Modifier.weight(1f),
            ) {
                Text("Salvar")
            }
            OutlinedButton(onClick = { onDeleteSavedPlace(place) }, modifier = Modifier.weight(1f)) {
                Text("Apagar")
            }
        }
    }
} // enter_saves_place_final_checklist_7
"""
    main = replaceFunctionChecklist7(main, "private fun SavedPlaceEditor(", savedPlaceEditor)

    val analysisStart = main.indexOf("private fun AnalysisScreen(")
    val analysisEnd = if (analysisStart >= 0) main.indexOf("private fun LiveReadingCard(", analysisStart) else -1
    if (analysisStart < 0 || analysisEnd < 0) throw GradleException("Tela de análise ausente para mover leitura.")
    var analysisRegion = main.substring(analysisStart, analysisEnd)
    val readingCall = analysisRegion.indexOf("    LiveReadingCard(")
    if (readingCall >= 0) {
        val open = analysisRegion.indexOf('(', readingCall)
        var depth = 0
        var index = open
        var close = -1
        while (index < analysisRegion.length) {
            when (analysisRegion[index]) {
                '(' -> depth += 1
                ')' -> { depth -= 1; if (depth == 0) { close = index + 1; break } }
            }
            index += 1
        }
        if (close < 0) throw GradleException("Chamada LiveReadingCard sem fechamento.")
        var end = close
        while (end < analysisRegion.length && (analysisRegion[end] == '\n' || analysisRegion[end] == ' ' || analysisRegion[end] == '\t')) end += 1
        val spacer = "Spacer(Modifier.height(10.dp))"
        if (analysisRegion.startsWith(spacer, end)) {
            end += spacer.length
            while (end < analysisRegion.length && (analysisRegion[end] == '\n' || analysisRegion[end] == ' ' || analysisRegion[end] == '\t')) end += 1
        }
        analysisRegion = analysisRegion.substring(0, readingCall) + "    // live_reading_moved_to_general_controls_checklist_7\n" + analysisRegion.substring(end)
        main = main.substring(0, analysisStart) + analysisRegion + main.substring(analysisEnd)
    }

    main = main.lineSequence()
        .filterNot { line ->
            line.contains("AppControlBubble(\"Leitura\"") || line.contains("AppControlBubble(\"Acesso\"")
        }
        .joinToString("\n")

    if ("general_controls_final_checklist_7" !in main ||
        "popup_scale_ui_final_checklist_7" !in main ||
        "saved_places_alphabetical_final_checklist_7" !in main ||
        "enter_saves_place_final_checklist_7" !in main ||
        "live_reading_moved_to_general_controls_checklist_7" !in main
    ) {
        throw GradleException("Interface final do checklist 7 incompleta.")
    }
    main += "\n// controls_places_popup_final_checklist_7\n"
    file.writeText(main)
}

fun patchShortcutCatalogChecklist7(file: java.io.File) {
    if (!file.exists()) throw GradleException("BubbleShortcutModule.kt ausente no checklist 7.")
    var text = file.readText()
    text = text.lineSequence()
        .filterNot { line ->
            line.trim() == "ReadingBubbleShortcutModule," || line.trim() == "PermissionsBubbleShortcutModule,"
        }
        .joinToString("\n")

    val listToken = "    val modules: List<BubbleShortcutModule> = listOf(\n"
    val start = text.indexOf(listToken)
    val end = if (start >= 0) text.indexOf("    )", start + listToken.length) else -1
    if (start < 0 || end < 0) throw GradleException("Catálogo final ausente no checklist 7.")
    val region = text.substring(start, end)
    val count = Regex("(?m)^\\s{8}[A-Za-z0-9_]+,\\s*$").findAll(region).count()
    text = text.replace(
        Regex("require\\(modules\\.size == \\d+\\) \\{ \\"[^\\\"]*\\" \\}"),
        "require(modules.size == $count) { \"O popup deve conter $count módulos.\" }",
    )
    if ("ReadingBubbleShortcutModule," in region || "PermissionsBubbleShortcutModule," in region) {
        throw GradleException("Leitura ou permissão ainda ocupam bolinha no popup.")
    }
    text += "\n// reading_permission_moved_out_of_popup_checklist_7\n"
    file.writeText(text)
}

fun patchServiceSavedPlaceChecklist7(file: java.io.File) {
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
    service = removeBalancedCallChecklist7(service, "quickActionBubbleButton(\"Acesso\")")
    service += "\n// saved_place_blank_name_and_controls_moved_checklist_7\n"
    file.writeText(service)
}

fun verifyChecklist7(
    mainFile: java.io.File,
    catalogFile: java.io.File,
    serviceFile: java.io.File,
    overlayFile: java.io.File,
) {
    val main = mainFile.readText()
    val catalog = catalogFile.readText()
    val service = serviceFile.readText()
    val overlay = overlayFile.readText()

    listOf(
        "general_controls_final_checklist_7",
        "Leitura ao vivo",
        "Permissão de acessibilidade",
        "popup_scale_ui_final_checklist_7",
        "saved_places_alphabetical_final_checklist_7",
        "enter_saves_place_final_checklist_7",
    ).forEach { marker -> if (marker !in main) throw GradleException("Contrato de interface ausente: $marker") }
    if ("AppControlBubble(\"Leitura\"" in main || "AppControlBubble(\"Acesso\"" in main) {
        throw GradleException("Leitura ou acesso continuam duplicados na central de bolinhas.")
    }
    if ("ReadingBubbleShortcutModule," in catalog || "PermissionsBubbleShortcutModule," in catalog) {
        throw GradleException("Leitura ou permissão continuam no popup flutuante.")
    }
    if ("else \"Local salvo\"" in service) throw GradleException("Novo local ainda recebe nome preenchido.")
    listOf("PopupAppearanceStore(context)", "LARGE_SCALE_TWO_COLUMNS", "scaledSp(10f, scale)").forEach { marker ->
        if (marker !in overlay) throw GradleException("Escala acessível do popup ausente: $marker")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        val sourceRoot = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile
        val mainFile = java.io.File(sourceRoot, "MainActivity.kt")
        val catalogFile = java.io.File(sourceRoot, "BubbleShortcutModule.kt")
        val serviceFile = java.io.File(sourceRoot, "LiveRideAccessibilityService.kt")
        val overlayFile = java.io.File(sourceRoot, "BubbleShortcutOverlayController.kt")
        patchMainUsabilityChecklist7(mainFile)
        patchShortcutCatalogChecklist7(catalogFile)
        patchServiceSavedPlaceChecklist7(serviceFile)
        verifyChecklist7(mainFile, catalogFile, serviceFile, overlayFile)
    }
}
