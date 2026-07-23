val patchLiveRideBubbleActions by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val service = serviceFile.asFile
        var text = service.readText()
        val original = text

        if ("openDecisionAddressSettingsFromBubble" !in text) {
            text = text.replace(
"""            addView(actionMenuItem("📍  Salvar este local") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            })
            addView(actionMenuItem("🔔  Criar alerta de proximidade") {
""",
"""            addView(actionMenuItem("📍  Salvar este local") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            })
            addView(actionMenuItem("🎯  Definir região de destino") {
                hideActionMenu()
                openDecisionAddressSettingsFromBubble()
            })
            addView(actionMenuItem("🔔  Criar alerta de proximidade") {
""",
            )

            text = text.replace(
"""    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType) {
""",
"""    private fun openDecisionAddressSettingsFromBubble() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_TOOLS),
            )
        }.onFailure {
            toast("Nao consegui abrir a regiao do farol agora.")
        }
    }

    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType) {
""",
            )
        }

        text = text.replace(
            ".putExtra(EXTRA_OPEN_TAB, TAB_ANALYSIS)",
            ".putExtra(EXTRA_OPEN_TAB, TAB_TOOLS)",
        )

        text = text.replace(
"""            x = bubbleParams.x + dp(76)
            y = bubbleParams.y
""",
"""            x = overlayMenuX(bubbleParams)
            y = overlayMenuY(bubbleParams)
""",
        )

        text = text.replace(
"""        params.x = bubbleParams.x + dp(76)
        params.y = bubbleParams.y
""",
"""        params.x = overlayMenuX(bubbleParams)
        params.y = overlayMenuY(bubbleParams)
""",
        )

        if ("private fun overlayMenuX(" !in text) {
            text = text.replace(
"""    private fun actionMenuItem(label: String, action: () -> Unit): TextView =
""",
"""    private fun overlayMenuX(bubbleParams: WindowManager.LayoutParams): Int {
        val menuWidth = dp(260)
        val gap = dp(10)
        val rightX = bubbleParams.x + dp(66) + gap
        val screenWidth = resources.displayMetrics.widthPixels
        return if (rightX + menuWidth <= screenWidth) {
            rightX
        } else {
            (bubbleParams.x - menuWidth - gap).coerceAtLeast(0)
        }
    }

    private fun overlayMenuY(bubbleParams: WindowManager.LayoutParams): Int =
        bubbleParams.y.coerceAtLeast(0)

    private fun actionMenuItem(label: String, action: () -> Unit): TextView =
""",
            )
        }

        if (text != original) {
            service.writeText(text)
        }

        val main = mainFile.asFile
        var mainText = main.readText()
        val originalMain = mainText

        if ("ToolsScreenResourceGroups" !in mainText) {
            mainText = mainText.replace(
                "    var tab by remember { mutableStateOf(TAB_ANALYSIS) }\n",
                "    var tab by remember { mutableStateOf(TAB_TOOLS) }\n",
            )

            mainText = mainText.replace(
"""        if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_TOOLS || requestedTab == TAB_HISTORY) {
            tab = requestedTab
        }
""",
"""        if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_TOOLS || requestedTab == TAB_HISTORY || requestedTab == TAB_DIAGNOSTIC) {
            tab = when (requestedTab) {
                TAB_ANALYSIS -> TAB_TOOLS
                TAB_HISTORY -> TAB_DIAGNOSTIC
                else -> requestedTab
            }
        }
""",
            )

            mainText = mainText.replace(
"""            NavigationBar {
                NavigationBarItem(selected = tab == TAB_ANALYSIS, onClick = { tab = TAB_ANALYSIS }, label = { Text("Analise") }, icon = {})
                NavigationBarItem(selected = tab == TAB_CONFIG, onClick = { tab = TAB_CONFIG }, label = { Text("Config") }, icon = {})
                NavigationBarItem(selected = tab == TAB_TOOLS, onClick = { tab = TAB_TOOLS }, label = { Text("Ferramentas") }, icon = {})
                NavigationBarItem(selected = tab == TAB_HISTORY, onClick = { tab = TAB_HISTORY }, label = { Text("Historico") }, icon = {})
            }
""",
"""            NavigationBar {
                NavigationBarItem(selected = tab == TAB_TOOLS, onClick = { tab = TAB_TOOLS }, label = { Text("Ferramentas") }, icon = {})
                NavigationBarItem(selected = tab == TAB_CONFIG, onClick = { tab = TAB_CONFIG }, label = { Text("Config") }, icon = {})
                NavigationBarItem(selected = tab == TAB_DIAGNOSTIC, onClick = { tab = TAB_DIAGNOSTIC }, label = { Text("Diagnostico") }, icon = {})
            }
""",
            )

            mainText = mainText.replace(
"""                TAB_ANALYSIS -> AnalysisScreen(
                    settings = settings,
                    latestResult = history.firstOrNull(),
                    cardTemplates = cardTemplates,
                    templateStatus = templateStatus,
                    unreadTemplatePrints = unreadTemplatePrints,
                    liveEnabled = liveEnabled,
                    onSaveSettings = { scope.launch { repository.saveSettings(it) } },
                    onDeleteCardModel = ::deleteCardModel,
                    onPickCardModels = { cardModelPicker.launch("image/*") },
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRefreshLiveState = { liveEnabled = isLiveAccessibilityEnabled(context) },
                )
                TAB_CONFIG -> SettingsScreen(
                    settings = settings,
                    diagnostic = diagnostic,
                    cardTemplates = cardTemplates,
                    savedPlaces = savedPlaces,
                    backupStatus = backupStatus,
                    highlightedSavedPlaceId = highlightedSavedPlaceId,
                    radarImportSummary = radarImportSummary,
                    radarImportStatus = radarImportStatus,
                    onSave = { scope.launch { repository.saveSettings(it) } },
                    onRegisterRideCard = ::registerRideCard,
                    onRenameSavedPlace = ::renameSavedPlace,
                    onDeleteSavedPlace = { place -> scope.launch { repository.removeSavedPlace(place.id) } },
                    onRegionDetected = { detectedRegion -> region = detectedRegion },
                    onCreateBackup = { backupFileCreator.launch("rota-certa-backup.json") },
                    onRestoreBackup = { backupFilePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onImportRadarFile = { radarFilePicker.launch(arrayOf("text/*", "text/comma-separated-values", "application/octet-stream", "*/*")) },
                    onOpenMapaRadar = { openMapaRadarSite(context) },
                    onClearImportedRadars = {
                        scope.launch {
                            repository.clearImportedRadars()
                            radarImportStatus = "Radares importados removidos."
                        }
                    },
                )
                TAB_TOOLS -> ToolsScreen(
                    onOpenBlaBlaCarCollector = {
                        context.startActivity(Intent(context, BlaBlaCarCollectorActivity::class.java))
                    },
                    onClearClipboard = { clearClipboard(context) },
                )
                TAB_HISTORY -> HistoryScreen(history)
""",
"""                TAB_ANALYSIS, TAB_TOOLS -> ToolsScreen(
                    settings = settings,
                    latestResult = history.firstOrNull(),
                    cardTemplates = cardTemplates,
                    templateStatus = templateStatus,
                    unreadTemplatePrints = unreadTemplatePrints,
                    liveEnabled = liveEnabled,
                    savedPlaces = savedPlaces,
                    highlightedSavedPlaceId = highlightedSavedPlaceId,
                    radarImportSummary = radarImportSummary,
                    radarImportStatus = radarImportStatus,
                    onSaveSettings = { scope.launch { repository.saveSettings(it) } },
                    onDeleteCardModel = ::deleteCardModel,
                    onPickCardModels = { cardModelPicker.launch("image/*") },
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRefreshLiveState = { liveEnabled = isLiveAccessibilityEnabled(context) },
                    onRenameSavedPlace = ::renameSavedPlace,
                    onDeleteSavedPlace = { place -> scope.launch { repository.removeSavedPlace(place.id) } },
                    onImportRadarFile = { radarFilePicker.launch(arrayOf("text/*", "text/comma-separated-values", "application/octet-stream", "*/*")) },
                    onOpenMapaRadar = { openMapaRadarSite(context) },
                    onClearImportedRadars = {
                        scope.launch {
                            repository.clearImportedRadars()
                            radarImportStatus = "Radares importados removidos."
                        }
                    },
                    onOpenBlaBlaCarCollector = {
                        context.startActivity(Intent(context, BlaBlaCarCollectorActivity::class.java))
                    },
                    onClearClipboard = { clearClipboard(context) },
                )
                TAB_CONFIG -> SettingsScreen(
                    settings = settings,
                    diagnostic = diagnostic,
                    cardTemplates = cardTemplates,
                    savedPlaces = savedPlaces,
                    backupStatus = backupStatus,
                    highlightedSavedPlaceId = highlightedSavedPlaceId,
                    radarImportSummary = radarImportSummary,
                    radarImportStatus = radarImportStatus,
                    onSave = { scope.launch { repository.saveSettings(it) } },
                    onRegisterRideCard = ::registerRideCard,
                    onRenameSavedPlace = ::renameSavedPlace,
                    onDeleteSavedPlace = { place -> scope.launch { repository.removeSavedPlace(place.id) } },
                    onRegionDetected = { detectedRegion -> region = detectedRegion },
                    onCreateBackup = { backupFileCreator.launch("rota-certa-backup.json") },
                    onRestoreBackup = { backupFilePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onImportRadarFile = { radarFilePicker.launch(arrayOf("text/*", "text/comma-separated-values", "application/octet-stream", "*/*")) },
                    onOpenMapaRadar = { openMapaRadarSite(context) },
                    onClearImportedRadars = {
                        scope.launch {
                            repository.clearImportedRadars()
                            radarImportStatus = "Radares importados removidos."
                        }
                    },
                )
                TAB_HISTORY, TAB_DIAGNOSTIC -> DiagnosticScreen(
                    diagnostic = diagnostic,
                    cardTemplates = cardTemplates,
                    history = history,
                    onRegisterRideCard = ::registerRideCard,
                )
""",
            )

            mainText = mainText.replace(
"""        DiagnosticExpander(
            diagnostic = diagnostic,
            cardTemplates = cardTemplates,
            onRegisterRideCard = onRegisterRideCard,
        )
        BubbleSettingsCard(settings = draft, onChange = ::saveDraft)
        SavedPlacesCard(
            savedPlaces = savedPlaces,
            highlightedSavedPlaceId = highlightedSavedPlaceId,
            onRenameSavedPlace = onRenameSavedPlace,
            onDeleteSavedPlace = onDeleteSavedPlace,
        )
        RadarImportCard(
            summary = radarImportSummary,
            importStatus = radarImportStatus,
            onPickFile = onImportRadarFile,
            onOpenMapaRadar = onOpenMapaRadar,
            onClearRadars = onClearImportedRadars,
        )
        MonitoredAppsCard(settings = draft, onChange = ::saveDraft)
        SettingsLocationCard(
            draft = draft,
            gpsStatus = gpsStatus,
            onDraftChange = { draft = it },
            onRequestGps = ::requestGps,
            onSave = { onSave(draft) },
        )
        MapsAndAdvancedCard(draft = draft, onDraftChange = { draft = it }, onSave = { onSave(draft) })
""",
"""        BubbleSettingsCard(settings = draft, onChange = ::saveDraft)
        MonitoredAppsCard(settings = draft, onChange = ::saveDraft)
        MapsAndAdvancedCard(draft = draft, onDraftChange = { draft = it }, onSave = { onSave(draft) })
""",
            )

            mainText = mainText.replace(
"""private fun ToolsScreen(
    onOpenBlaBlaCarCollector: () -> Unit,
    onClearClipboard: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ferramentas", fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Coletor BlaBlaCar", fontWeight = FontWeight.Bold)
                Text(
                    "Registro manual de viagem logada: passageiros, telefones, WhatsApp, rotas, faturamento, despesas e lucro.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onOpenBlaBlaCarCollector, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir coletor")
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Area de transferencia", fontWeight = FontWeight.Bold)
                Text(
                    "Limpeza manual para remover o texto copiado quando o copiar/colar do celular travar ou ficar preso em conteudo antigo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onClearClipboard, modifier = Modifier.fillMaxWidth()) {
                    Text("Limpar area de transferencia")
                }
            }
        }
    }
}
""",
"""private fun ToolsScreen(
    settings: AppSettings,
    latestResult: AnalysisResult?,
    cardTemplates: List<RideCardTemplate>,
    templateStatus: String,
    unreadTemplatePrints: Int,
    liveEnabled: Boolean,
    savedPlaces: List<SavedPlace>,
    highlightedSavedPlaceId: String?,
    radarImportSummary: RadarImportSummary,
    radarImportStatus: String,
    onSaveSettings: (AppSettings) -> Unit,
    onDeleteCardModel: (RideCardTemplate) -> Unit,
    onPickCardModels: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRefreshLiveState: () -> Unit,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
    onImportRadarFile: () -> Unit,
    onOpenMapaRadar: () -> Unit,
    onClearImportedRadars: () -> Unit,
    onOpenBlaBlaCarCollector: () -> Unit,
    onClearClipboard: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ferramentas", fontWeight = FontWeight.Bold)
        // ToolsScreenResourceGroups
        ExpandableCard(title = "Definir regiao de destino", initiallyExpanded = true) {
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
        ExpandableCard(title = "Alertas de proximidade", initiallyExpanded = false) {
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
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Coletor BlaBlaCar", fontWeight = FontWeight.Bold)
                Text(
                    "Registro manual de viagem logada: passageiros, telefones, WhatsApp, rotas, faturamento, despesas e lucro.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onOpenBlaBlaCarCollector, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir coletor")
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Area de transferencia", fontWeight = FontWeight.Bold)
                Text(
                    "Limpeza manual para remover o texto copiado quando o copiar/colar do celular travar ou ficar preso em conteudo antigo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onClearClipboard, modifier = Modifier.fillMaxWidth()) {
                    Text("Limpar area de transferencia")
                }
            }
        }
    }
}
""",
            )

            mainText = mainText.replace(
"""@Composable
private fun HistoryScreen(history: List<AnalysisResult>) {
    if (history.isEmpty()) {
        Text("Nenhuma analise salva ainda.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        history.forEach { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(recommendationLabel(result.recommendation), fontWeight = FontWeight.Bold)
                    Text(formatDate(result.createdAtMillis))
                    Text(result.fields.destination ?: "Destino final nao identificado")
                    Text(result.reason)
                }
            }
        }
    }
}
""",
"""@Composable
private fun DiagnosticScreen(
    diagnostic: LiveDiagnostic?,
    cardTemplates: List<RideCardTemplate>,
    history: List<AnalysisResult>,
    onRegisterRideCard: (String?, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Diagnostico", fontWeight = FontWeight.Bold)
        DiagnosticExpander(
            diagnostic = diagnostic,
            cardTemplates = cardTemplates,
            onRegisterRideCard = onRegisterRideCard,
        )
        HistoryDiagnosticCard(history = history)
    }
}

@Composable
private fun HistoryDiagnosticCard(history: List<AnalysisResult>) {
    val context = LocalContext.current
    ExpandableCard(title = "Historico de analises", initiallyExpanded = false) {
        Button(
            onClick = { copyHistory(context, history) },
            enabled = history.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Copiar historico")
        }
        if (history.isEmpty()) {
            Text("Nenhuma analise salva ainda.")
            return@ExpandableCard
        }
        history.forEach { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(recommendationLabel(result.recommendation), fontWeight = FontWeight.Bold)
                    Text(formatDate(result.createdAtMillis))
                    Text(result.fields.destination ?: "Destino final nao identificado")
                    Text(result.reason)
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(history: List<AnalysisResult>) {
    HistoryDiagnosticCard(history = history)
}
""",
            )

            mainText = mainText.replace(
"""private fun clearClipboard(context: Context) {
""",
"""private fun copyHistory(context: Context, history: List<AnalysisResult>) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Rota Certa historico", history.toHistoryShareText()))
    Toast.makeText(context, "Historico copiado.", Toast.LENGTH_SHORT).show()
}

private fun List<AnalysisResult>.toHistoryShareText(): String = buildString {
    appendLine("ROTA CERTA HISTORICO")
    if (isEmpty()) {
        appendLine("Nenhuma analise salva.")
        return@buildString
    }
    forEachIndexed { index, result ->
        appendLine((index + 1).toString() + ". " + recommendationLabel(result.recommendation))
        appendLine("Data: " + formatDate(result.createdAtMillis))
        appendLine("Destino: " + (result.fields.destination ?: "nao identificado"))
        appendLine("Motivo: " + result.reason)
        appendLine()
    }
}

private fun clearClipboard(context: Context) {
""",
            )

            mainText = mainText.replace(
"""private enum class LocationTarget { Home, Alternative }
""",
"""private const val TAB_DIAGNOSTIC = "diagnostico"

private enum class LocationTarget { Home, Alternative }
""",
            )

            mainText = mainText.replace(
                "            Text(\"Endereco para aceitar corridas\", fontWeight = FontWeight.Bold)\n",
                "            Text(\"Salvar Casa/Alfinete\", fontWeight = FontWeight.Bold)\n",
            )
            mainText = mainText.replace(
                "            Text(\"Raio de aceite\", fontWeight = FontWeight.Bold)\n",
                "            Text(\"KM da regiao de destino\", fontWeight = FontWeight.Bold)\n",
            )
        }

        if (mainText != originalMain) {
            main.writeText(mainText)
        }
    }
}

patchLiveRideBubbleActions.configure {
    mustRunAfter("patchLiveRideOverlayStability")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchLiveRideBubbleActions)
}
