val patchUxPlacesAlertsRadars by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.files(mainFile, serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val main = mainFile.asFile
        var text = main.readText()
        val original = text
        val dollar = "$"

        text = text.replace(
            "if (initiallyExpanded || requestedExpander == title) expanded = true",
            "if (initiallyExpanded || requestedExpander == title || (requestedExpander != null && title.startsWith(requestedExpander))) expanded = true",
        )

        text = text.replace(
"""    LaunchedEffect(highlightedSavedPlaceId, savedPlaces) {
        val id = highlightedSavedPlaceId ?: return@LaunchedEffect
        val place = savedPlaces.firstOrNull { it.id == id && it.type == SavedPlaceType.Place }
        if (place != null && handledSavedPlaceNameDialogId != id) {
            savedPlaceNameDialogId = id
        }
    }

    savedPlaces.firstOrNull { it.id == savedPlaceNameDialogId && it.type == SavedPlaceType.Place }?.let { place ->
""",
"""    LaunchedEffect(highlightedSavedPlaceId, savedPlaces) {
        val id = highlightedSavedPlaceId ?: return@LaunchedEffect
        val place = savedPlaces.firstOrNull { it.id == id }
        if (place != null && handledSavedPlaceNameDialogId != id) {
            savedPlaceNameDialogId = id
        }
    }

    savedPlaces.firstOrNull { it.id == savedPlaceNameDialogId }?.let { place ->
""",
        )

        text = text.replace(
"""@Composable
private fun SavedPlaceNameDialog(
    place: SavedPlace,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftName by remember(place.id, place.name) {
        mutableStateOf(place.name.takeUnless { it == defaultSavedPlaceName(place.type) }.orEmpty())
    }
    val focusRequester = remember { FocusRequester() }

    fun confirm() {
        onSave(draftName.trim().ifBlank { defaultSavedPlaceName(place.type) })
    }

    LaunchedEffect(place.id) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nome do local") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(place.address.ifBlank { formatCoordinate(place.coordinate) }, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    label = { Text("Digite o nome") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                )
            }
        },
        confirmButton = {
            Button(onClick = { confirm() }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
""",
"""@Composable
private fun SavedPlaceNameDialog(
    place: SavedPlace,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftName by remember(place.id, place.name) {
        mutableStateOf(place.name.takeUnless { it == defaultSavedPlaceName(place.type) }.orEmpty())
    }
    val focusRequester = remember { FocusRequester() }
    val isAlert = place.type == SavedPlaceType.ProximityAlert

    fun confirm() {
        onSave(draftName.trim().ifBlank { defaultSavedPlaceName(place.type) })
    }

    LaunchedEffect(place.id) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAlert) "Nome do alerta" else "Nome do local") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(place.address.ifBlank { formatCoordinate(place.coordinate) }, style = MaterialTheme.typography.bodySmall)
                if (isAlert) {
                    Text("Esse nome sera usado no aviso falado quando voce se aproximar deste ponto.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    label = { Text(if (isAlert) "Digite o alerta" else "Digite o nome") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                )
            }
        },
        confirmButton = {
            Button(onClick = { confirm() }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
""",
        )

        text = text.replace(
"""        ExpandableCard(title = "Definir regiao de trabalho", initiallyExpanded = false) {
            AnalysisScreen(
                settings = settings,
                latestResult = latestResult,
                cardTemplates = cardTemplates,
                templateStatus = templateStatus,
                unreadTemplatePrints = unreadTemplatePrints,
                liveEnabled = liveEnabled,
                onSaveSettings = onSaveSettings,
                onDeleteCardModel = onDeleteCardModel,
                onPickCardModels = onPickCardModels,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onRefreshLiveState = onRefreshLiveState,
            )
        }
""",
"""        WorkRegionCard(settings = settings, onSaveSettings = onSaveSettings)
        latestResult?.let { result ->
            ResultCard(result, settings)
        }
        CardModelsCard(
            cardTemplates = cardTemplates,
            templateStatus = templateStatus,
            unreadTemplatePrints = unreadTemplatePrints,
            onPickCardModels = onPickCardModels,
            onDeleteCardModel = onDeleteCardModel,
        )
""",
        )

        text = text.replace(
"""        ExpandableCard(title = "Alertas de proximidade", initiallyExpanded = false) {
            RadarImportCard(
                summary = radarImportSummary,
                importStatus = radarImportStatus,
                onPickFile = onImportRadarFile,
                onOpenMapaRadar = onOpenMapaRadar,
                onClearRadars = onClearImportedRadars,
            )
            Spacer(Modifier.height(8.dp))
            SavedPlacesCard(
                savedPlaces = savedPlaces,
                highlightedSavedPlaceId = highlightedSavedPlaceId,
                onRenameSavedPlace = onRenameSavedPlace,
                onDeleteSavedPlace = onDeleteSavedPlace,
            )
        }
""",
"""        SavedPlacesCard(
            savedPlaces = savedPlaces,
            highlightedSavedPlaceId = highlightedSavedPlaceId,
            onRenameSavedPlace = onRenameSavedPlace,
            onDeleteSavedPlace = onDeleteSavedPlace,
        )
        ExpandableCard(title = "Radares importados (" + radarImportSummary.count + ")", initiallyExpanded = false) {
            RadarImportCard(
                summary = radarImportSummary,
                importStatus = radarImportStatus,
                onPickFile = onImportRadarFile,
                onOpenMapaRadar = onOpenMapaRadar,
                onClearRadars = onClearImportedRadars,
            )
        }
""",
        )

        if ("private fun WorkRegionCard(" !in text) {
            text = text.replace(
"""@Composable
private fun ToolsScreen(
""",
"""@Composable
private fun WorkRegionCard(
    settings: AppSettings,
    onSaveSettings: (AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val locationService = remember { DeviceLocationService(context) }
    val gpsAddressResolver = remember { GpsAddressResolver(context) }
    val scope = rememberCoroutineScope()
    var draft by remember(settings) { mutableStateOf(settings) }
    var status by remember { mutableStateOf("") }
    var pendingGps by remember { mutableStateOf(false) }

    fun saveRegion(updated: AppSettings) {
        draft = updated
        onSaveSettings(updated)
    }

    fun captureGps() {
        scope.launch {
            status = "Buscando sinal de GPS..."
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                status = "Nao consegui captar o GPS. Autorize a localizacao e tente novamente."
                return@launch
            }
            val resolved = gpsAddressResolver.resolve(coordinate)
            val address = resolved.addressLine.ifBlank { formatCoordinate(coordinate) }
            val updated = draft.copy(homeAddress = address, homeCoordinate = coordinate)
            saveRegion(updated)
            status = "Regiao salva pelo GPS: ${dollar}{formatCoordinate(coordinate)}"
        }
    }

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (!pendingGps) return@rememberLauncherForActivityResult
        pendingGps = false
        if (permissions.values.any { it }) {
            captureGps()
        } else {
            status = "Localizacao negada. Autorize o GPS para salvar a regiao."
        }
    }

    ExpandableCard(title = "Minha regiao de corridas", initiallyExpanded = false) {
        Text(
            "Defina onde voce quer receber corridas pelo destino final. O app aceita quando o destino fica dentro do raio salvo.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = draft.homeAddress,
            onValueChange = { draft = draft.copy(homeAddress = it, homeCoordinate = null) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Endereco da regiao de trabalho") },
        )
        Button(
            onClick = {
                pendingGps = true
                gpsPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Definir pelo GPS atual")
        }
        draft.homeCoordinate?.let {
            Text("GPS salvo: ${dollar}{formatCoordinate(it)}", style = MaterialTheme.typography.bodySmall)
        }
        RadiusSlider(
            label = "Raio da regiao de trabalho",
            value = draft.homeRadiusKm,
            onValueChange = { draft = draft.copy(homeRadiusKm = it) },
            onValueChangeFinished = { saveRegion(draft) },
        )
        Button(
            onClick = {
                saveRegion(draft)
                status = "Regiao de trabalho salva."
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Salvar regiao de trabalho")
        }
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ToolsScreen(
""",
            )
        }

        text = text.replace("Salvar Casa/Alfinete", "Minha regiao de corridas")
        text = text.replace("Definir regiao de trabalho", "Minha regiao de corridas")
        text = text.replace("Definir região de trabalho", "Minha região de corridas")
        text = text.replace(
            "Configure o ponto que o destino final precisa ficar perto. O Rota Certa usa este endereco e o raio em km para decidir aceitar ou recusar.",
            "Defina rapidamente o ponto onde o destino final precisa ficar perto. Use o GPS atual, ajuste o raio e salve.",
        )
        text = text.replace("Usar GPS atual", "Definir pelo GPS atual")
        text = text.replace("Salvar endereco", "Salvar regiao de trabalho")
        text = text.replace("KM da regiao de destino", "Raio da regiao de trabalho")

        if (text != original) {
            main.writeText(text)
        }

        val service = serviceFile.asFile
        var serviceText = service.readText()
        val originalService = serviceText

        serviceText = serviceText.replace(
"""    private fun openSavedPlaceEditor(place: SavedPlace) {
        hideActionMenu()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_TOOLS)
                    .putExtra("br.com.mapeiaia.rotacerta.extra.OPEN_EXPANDER", "Alertas de proximidade")
                    .putExtra(EXTRA_SAVED_PLACE_ID, place.id),
            )
        }
    }
""",
"""    private fun openSavedPlaceEditor(place: SavedPlace) {
        hideActionMenu()
        val expander = if (place.type == SavedPlaceType.ProximityAlert) "Alertas de proximidade" else "Locais salvos"
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_TOOLS)
                    .putExtra("br.com.mapeiaia.rotacerta.extra.OPEN_EXPANDER", expander)
                    .putExtra(EXTRA_SAVED_PLACE_ID, place.id),
            )
        }
    }
""",
        )

        serviceText = serviceText.replace(
"""            addView(actionMenuItem(
                label = "📍  Salvar este local",
                action = {
                    hideActionMenu()
                    saveCurrentPlaceFromBubble(SavedPlaceType.Place)
                },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Alertas de proximidade") },
            ))
""",
"""            addView(actionMenuItem(
                label = "📍  Salvar este local",
                action = {
                    hideActionMenu()
                    saveCurrentPlaceFromBubble(SavedPlaceType.Place)
                },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Locais salvos") },
            ))
""",
        )
        serviceText = serviceText.replace("Definir regiao de trabalho", "Minha regiao de corridas")
        serviceText = serviceText.replace("Definir região de trabalho", "Minha região de corridas")

        if (serviceText != originalService) {
            service.writeText(serviceText)
        }
    }
}

patchUxPlacesAlertsRadars.configure {
    mustRunAfter("patchBubbleShortcutClipboard")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchUxPlacesAlertsRadars)
}
