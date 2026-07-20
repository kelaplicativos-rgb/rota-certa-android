// Corrige o bloco `when (tab)` depois que patches antigos unificam Analise e
// Ferramentas. A correcao roda no fim da propria tarefa da Home agrupada para
// evitar ciclos de dependencias entre os muitos patches Gradle legados.

fun enforceGroupedBubbleNavigation115(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado.")
    var text = file.readText()
    if ("grouped_navigation_compat_0_1_115" in text) return

    val startToken = "            when (tab) {"
    val endToken = "\n        }\n    }\n}\n\n// in_app_bubble_home_visible_0_1_97"
    val start = text.indexOf(startToken)
    val end = if (start >= 0) text.indexOf(endToken, start) else -1
    if (start < 0 || end <= start) {
        throw GradleException("Bloco when(tab) nao encontrado para navegacao agrupada.")
    }

    val replacement = """            when (tab) {
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

    text = text.substring(0, start) + replacement + text.substring(end)

    listOf(
        "TAB_ANALYSIS -> AnalysisScreen(",
        "TAB_CONFIG -> SettingsScreen(",
        "TAB_TOOLS -> ToolsScreen(",
        "TAB_HISTORY -> ReportsGroupScreen(",
        "grouped_navigation_compat_0_1_115",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Navegacao agrupada incompleta: $marker")
    }
    if ("TAB_ANALYSIS, TAB_TOOLS -> ToolsScreen(" in text) {
        throw GradleException("Regressao: Analise e Ferramentas ainda estao unificadas.")
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
