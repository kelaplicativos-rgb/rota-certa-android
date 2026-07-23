// Checklist 7 — organização da interface, nomes rápidos e tamanho do popup.

fun replaceUiFunctionChecklist7(source: String, signature: String, replacement: String): String {
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

fun addUiImportChecklist7(source: String, importLine: String, anchor: String): String {
    if (importLine in source) return source
    if (anchor !in source) throw GradleException("Âncora de importação ausente: $importLine")
    return source.replaceFirst(anchor, anchor + importLine + "\n")
}

fun removeAnalysisReadingCallChecklist7(source: String): String {
    val regionStart = source.indexOf("private fun AnalysisScreen(")
    val regionEnd = if (regionStart >= 0) source.indexOf("private fun LiveReadingCard(", regionStart) else -1
    if (regionStart < 0 || regionEnd < 0) throw GradleException("Tela de análise ausente no checklist 7.")
    var region = source.substring(regionStart, regionEnd)
    val callStart = region.indexOf("    LiveReadingCard(")
    if (callStart < 0) return source
    val open = region.indexOf('(', callStart)
    var depth = 0
    var index = open
    var callEnd = -1
    while (index < region.length) {
        when (region[index]) {
            '(' -> depth += 1
            ')' -> {
                depth -= 1
                if (depth == 0) { callEnd = index + 1; break }
            }
        }
        index += 1
    }
    if (callEnd < 0) throw GradleException("Chamada de leitura sem fechamento.")
    while (callEnd < region.length && region[callEnd].isWhitespace()) callEnd += 1
    val spacer = "Spacer(Modifier.height(10.dp))"
    if (region.startsWith(spacer, callEnd)) {
        callEnd += spacer.length
        while (callEnd < region.length && region[callEnd].isWhitespace()) callEnd += 1
    }
    region = region.substring(0, callStart) +
        "    // live_reading_moved_to_general_controls_checklist_7\n" +
        region.substring(callEnd)
    return source.substring(0, regionStart) + region + source.substring(regionEnd)
}

fun patchGeneralControlsUiChecklist7(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt ausente no checklist 7.")
    var main = file.readText()
    if ("general_controls_ui_complete_checklist_7" in main) return

    main = addUiImportChecklist7(main, "import androidx.compose.foundation.text.KeyboardActions", "import androidx.compose.foundation.rememberScrollState\n")
    main = addUiImportChecklist7(main, "import androidx.compose.foundation.text.KeyboardOptions", "import androidx.compose.foundation.text.KeyboardActions\n")
    main = addUiImportChecklist7(main, "import androidx.compose.ui.platform.LocalFocusManager", "import androidx.compose.ui.platform.LocalContext\n")
    main = addUiImportChecklist7(main, "import androidx.compose.ui.text.input.ImeAction", "import androidx.compose.ui.text.font.FontWeight\n")

    main = replaceUiFunctionChecklist7(
        main,
        "private fun SystemControlCard(settings: AppSettings, onChange: (AppSettings) -> Unit)",
        """private fun SystemControlCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessibilityGranted by remember { mutableStateOf(isLiveAccessibilityEnabled(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accessibilityGranted = isLiveAccessibilityEnabled(context)
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
""",
    )

    main = replaceUiFunctionChecklist7(
        main,
        "private fun BubbleSettingsCard(settings: AppSettings, onChange: (AppSettings) -> Unit)",
        """private fun BubbleSettingsCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {
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
        Text("Tamanho do pop-up: " + (popupScale * 100).roundToInt() + "%", fontWeight = FontWeight.Bold)
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
""",
    )

    main = replaceUiFunctionChecklist7(
        main,
        "private fun SavedPlacesCard(",
        """private fun SavedPlacesCard(
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

    ExpandableCard(
        title = "Locais salvos (" + places.size + ")",
        initiallyExpanded = highlightedType == SavedPlaceType.Place,
    ) {
        if (places.isEmpty()) Text("Nenhum local salvo ainda.", style = MaterialTheme.typography.bodySmall)
        else places.forEach { place ->
            SavedPlaceEditor(place, place.id == highlightedSavedPlaceId, onRenameSavedPlace, onDeleteSavedPlace)
        }
    }

    Spacer(Modifier.height(10.dp))
    ExpandableCard(
        title = "Alertas de proximidade (" + alerts.size + ")",
        initiallyExpanded = highlightedType == SavedPlaceType.ProximityAlert,
    ) {
        if (alerts.isEmpty()) Text("Nenhum alerta de proximidade criado ainda.", style = MaterialTheme.typography.bodySmall)
        else alerts.forEach { place ->
            SavedPlaceEditor(place, place.id == highlightedSavedPlaceId, onRenameSavedPlace, onDeleteSavedPlace)
        }
    }
} // saved_places_alphabetical_final_checklist_7
""",
    )

    main = replaceUiFunctionChecklist7(
        main,
        "private fun SavedPlaceEditor(",
        """private fun SavedPlaceEditor(
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
                "O app vai falar: " + draftName.ifBlank { defaultSavedPlaceName(place.type) } + " se aproximando.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(place.address.ifBlank { formatCoordinate(place.coordinate) }, style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openSavedPlaceInGps(context, place) }, modifier = Modifier.weight(1f)) { Text("GPS") }
            Button(
                enabled = SavedPlaceUiPolicy.canSave(place, draftName) && draftName.trim() != place.name.trim(),
                onClick = { saveName() },
                modifier = Modifier.weight(1f),
            ) { Text("Salvar") }
            OutlinedButton(onClick = { onDeleteSavedPlace(place) }, modifier = Modifier.weight(1f)) { Text("Apagar") }
        }
    }
} // enter_saves_place_final_checklist_7
""",
    )

    main = removeAnalysisReadingCallChecklist7(main)
    main = main.lineSequence()
        .filterNot { it.contains("AppControlBubble(\"Leitura\"") || it.contains("AppControlBubble(\"Acesso\"") }
        .joinToString("\n")

    listOf(
        "general_controls_final_checklist_7",
        "popup_scale_ui_final_checklist_7",
        "saved_places_alphabetical_final_checklist_7",
        "enter_saves_place_final_checklist_7",
        "live_reading_moved_to_general_controls_checklist_7",
    ).forEach { marker -> if (marker !in main) throw GradleException("Contrato de interface ausente: $marker") }
    if ("AppControlBubble(\"Leitura\"" in main || "AppControlBubble(\"Acesso\"" in main) {
        throw GradleException("Leitura ou acesso continuam duplicados na central de bolinhas.")
    }

    main += "\n// general_controls_ui_complete_checklist_7\n"
    file.writeText(main)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchGeneralControlsUiChecklist7(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
