// Compatibilidade final da navegacao agrupada.
//
// A ordem dos muitos patches legados pode variar quando um novo modulo e ligado.
// Por isso este passo nao confia apenas no marcador 0.1.115: ele normaliza sempre
// as chamadas e assinaturas da Home para a mesma forma que ja foi aprovada no APK.

fun groupedNavigationReplaceRegion115(
    source: String,
    startToken: String,
    endToken: String,
    replacement: String,
    label: String,
): String {
    val start = source.indexOf(startToken)
    val end = if (start >= 0) source.indexOf(endToken, start + startToken.length) else -1
    if (start < 0 || end <= start) throw GradleException("Regiao ausente para $label")
    return source.substring(0, start) + replacement + source.substring(end)
}

fun enforceGroupedBubbleNavigation115(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado.")
    var text = file.readText()

    val startToken = "            when (tab) {"
    val endToken = "\n        }\n    }\n}\n\n// in_app_bubble_home_visible_0_1_97"
    val start = text.indexOf(startToken)
    val end = if (start >= 0) text.indexOf(endToken, start) else -1
    if (start < 0 || end <= start) {
        throw GradleException("Bloco when(tab) nao encontrado para navegacao agrupada.")
    }

    val navigation = """            when (tab) {
                TAB_ANALYSIS -> AnalysisScreen(
                    settings = settings,
                    latestResult = history.firstOrNull(),
                    liveEnabled = liveEnabled,
                    onSaveSettings = { updated -> scope.launch { repository.saveSettings(updated) } },
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRefreshLiveState = { liveEnabled = isLiveAccessibilityEnabled(context) },
                )
                TAB_CONFIG -> SettingsScreen(
                    selectedGroup = selectedBubbleGroup,
                    settings = settings,
                    liveEnabled = liveEnabled,
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRefreshLiveState = { liveEnabled = isLiveAccessibilityEnabled(context) },
                    diagnostic = null,
                    savedPlaces = savedPlaces,
                    backupStatus = backupStatus,
                    highlightedSavedPlaceId = highlightedSavedPlaceId,
                    radarImportSummary = radarImportSummary,
                    radarImportStatus = radarImportStatus,
                    onSave = { updated -> scope.launch { repository.saveSettings(updated) } },
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
                    onOpenWhatsApp = { openWhatsAppApp(context) },
                    onOpenBlaBlaCarCollector = {
                        context.startActivity(Intent(context, BlaBlaCarCollectorActivity::class.java))
                    },
                    onClearClipboard = { clearClipboard(context) },
                )
                TAB_HISTORY -> ReportsGroupScreen(
                    diagnostic = null,
                    history = history,
                )
                else -> Unit
            } // grouped_navigation_compat_0_1_115"""
    text = text.substring(0, start) + navigation + text.substring(end)

    val settingsSignature = """@Composable
private fun SettingsScreen(
    selectedGroup: String,
    settings: AppSettings,
    liveEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onRefreshLiveState: () -> Unit,
    diagnostic: LiveDiagnostic?,
    savedPlaces: List<SavedPlace>,
    backupStatus: String,
    highlightedSavedPlaceId: String?,
    radarImportSummary: RadarImportSummary,
    radarImportStatus: String,
    onSave: (AppSettings) -> Unit,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
    onRegionDetected: (DeviceRegion) -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onImportRadarFile: () -> Unit,
    onOpenMapaRadar: () -> Unit,
    onClearImportedRadars: () -> Unit,
) {"""
    val settingsStart = text.indexOf("@Composable\nprivate fun SettingsScreen(")
    val settingsBody = if (settingsStart >= 0) text.indexOf("\n) {", settingsStart) else -1
    if (settingsStart < 0 || settingsBody <= settingsStart) {
        throw GradleException("Assinatura SettingsScreen nao encontrada.")
    }
    text = text.substring(0, settingsStart) + settingsSignature + text.substring(settingsBody + "\n) {".length)

    val reportsReplacement = """@Composable
private fun ReportsGroupScreen(
    diagnostic: LiveDiagnostic?,
    history: List<AnalysisResult>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Relatorios e historico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        DiagnosticExpander(
            diagnostic = diagnostic,
        )
        Text("Historico de decisoes", fontWeight = FontWeight.Bold)
        HistoryScreen(history)
    }
} // grouped_reports_tools_0_1_115

"""
    text = groupedNavigationReplaceRegion115(
        source = text,
        startToken = "@Composable\nprivate fun ReportsGroupScreen(",
        endToken = "@Composable\nprivate fun HistoryScreen(",
        replacement = reportsReplacement,
        label = "ReportsGroupScreen final",
    )

    listOf(
        "TAB_ANALYSIS -> AnalysisScreen(",
        "TAB_CONFIG -> SettingsScreen(",
        "TAB_TOOLS -> ToolsScreen(",
        "TAB_HISTORY -> ReportsGroupScreen(",
        "grouped_navigation_compat_0_1_115",
        "private fun SettingsScreen(",
        "private fun ReportsGroupScreen(",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Navegacao agrupada incompleta: $marker")
    }
    if ("TAB_ANALYSIS, TAB_TOOLS -> ToolsScreen(" in text) {
        throw GradleException("Regressao: Analise e Ferramentas ainda estao unificadas.")
    }
    if ("cardTemplates: List<RideCardTemplate>" in text.substring(
            text.indexOf("private fun SettingsScreen("),
            text.indexOf(") {", text.indexOf("private fun SettingsScreen(")),
        )
    ) {
        throw GradleException("SettingsScreen voltou a exigir modelos sem usar o parametro.")
    }

    file.writeText(text)
}

tasks.named("inAppGroupedBubbleHome115").configure {
    doLast {
        enforceGroupedBubbleNavigation115(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
