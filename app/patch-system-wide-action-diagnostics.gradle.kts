val systemWideActionDiagnostics by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }

    doLast {
        patchMainActivitySystemWideActions(mainFile.asFile)
    }
}

fun patchMainActivitySystemWideActions(file: java.io.File) {
    var text = file.readText()
    val original = text

    if ("SYSTEM_ACTION_DIAGNOSTIC_PREFS" !in text) {
        text = text.replace(
"""private fun diagnosticJsonFileName(diagnostic: LiveDiagnostic): String =
""",
"""private const val SYSTEM_ACTION_DIAGNOSTIC_PREFS = "rota_certa_system_action_diagnostics"
private const val SYSTEM_ACTION_DIAGNOSTIC_LOG = "events"
private const val SYSTEM_ACTION_DIAGNOSTIC_LIMIT = 260

private fun Context.traceSystemUserAction(action: String, status: String = "started", details: String = "") {
    val cleanAction = action.sanitizeDiagnosticActionValue()
    val cleanStatus = status.sanitizeDiagnosticActionValue()
    val cleanDetails = details.replace(Regex("\\s+"), " ").trim().take(220)
    val line = "${'$'}{System.currentTimeMillis()} ui.action name=${'$'}cleanAction status=${'$'}cleanStatus details=${'$'}cleanDetails"
    val prefs = getSharedPreferences(SYSTEM_ACTION_DIAGNOSTIC_PREFS, Context.MODE_PRIVATE)
    val previous = prefs.getString(SYSTEM_ACTION_DIAGNOSTIC_LOG, "").orEmpty()
        .lines()
        .filter { it.isNotBlank() }
    val next = (previous + line).takeLast(SYSTEM_ACTION_DIAGNOSTIC_LIMIT)
    prefs.edit().putString(SYSTEM_ACTION_DIAGNOSTIC_LOG, next.joinToString("\n")).apply()
}

private fun Context.systemUserActionDiagnosticEvents(): List<String> =
    getSharedPreferences(SYSTEM_ACTION_DIAGNOSTIC_PREFS, Context.MODE_PRIVATE)
        .getString(SYSTEM_ACTION_DIAGNOSTIC_LOG, "")
        .orEmpty()
        .lines()
        .filter { it.isNotBlank() }
        .takeLast(SYSTEM_ACTION_DIAGNOSTIC_LIMIT)

private fun String.sanitizeDiagnosticActionValue(): String =
    lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_.-]+"), "_").trim('_').take(72).ifBlank { "unknown" }

private fun diagnosticJsonFileName(diagnostic: LiveDiagnostic): String =
""",
        )
    }

    if ("fun traceUserAction(action: String" !in text) {
        text = text.replace(
"""    var backupStatus by remember { mutableStateOf("") }
    var radarImportStatus by remember { mutableStateOf("") }
""",
"""    var backupStatus by remember { mutableStateOf("") }
    var radarImportStatus by remember { mutableStateOf("") }

    fun traceUserAction(action: String, status: String = "started", details: String = "") {
        context.traceSystemUserAction(action, status, details)
    }
""",
        )
    }

    if ("ui.action name=app.open" !in text) {
        text = text.replace(
"""    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
""",
"""    LaunchedEffect(Unit) {
        traceUserAction("app.open", "success", "version=${'$'}{BuildConfig.VERSION_NAME} code=${'$'}{BuildConfig.VERSION_CODE}")
        locationPermissionLauncher.launch(
""",
        )
    }

    text = text.replace(
"""        if (text.isBlank()) {
            Toast.makeText(context, "Nao ha texto lido para cadastrar", Toast.LENGTH_SHORT).show()
            return
        }
""",
"""        traceUserAction("card.register_from_diagnostic", "started", "package=${'$'}{packageName.orEmpty()} text_len=${'$'}{text.length}")
        if (text.isBlank()) {
            traceUserAction("card.register_from_diagnostic", "fail", "text_blank")
            Toast.makeText(context, "Nao ha texto lido para cadastrar", Toast.LENGTH_SHORT).show()
            return
        }
""",
    )

    text = text.replace(
"""            repository.addCardTemplate(template)
            Toast.makeText(context, "Modelo cadastrado: ${'$'}{template.name}", Toast.LENGTH_SHORT).show()
""",
"""            repository.addCardTemplate(template)
            traceUserAction("card.register_from_diagnostic", "success", "package=${'$'}{template.packageName.orEmpty()} name=${'$'}{template.name}")
            Toast.makeText(context, "Modelo cadastrado: ${'$'}{template.name}", Toast.LENGTH_SHORT).show()
""",
    )

    text = text.replace(
"""    fun deleteCardModel(template: RideCardTemplate) {
        scope.launch {
            repository.removeCardTemplate(template.id)
            Toast.makeText(context, "Modelo removido: ${'$'}{template.name}", Toast.LENGTH_SHORT).show()
        }
    }
""",
"""    fun deleteCardModel(template: RideCardTemplate) {
        traceUserAction("card.delete_model", "started", "id=${'$'}{template.id} name=${'$'}{template.name}")
        scope.launch {
            repository.removeCardTemplate(template.id)
            traceUserAction("card.delete_model", "success", "id=${'$'}{template.id} name=${'$'}{template.name}")
            Toast.makeText(context, "Modelo removido: ${'$'}{template.name}", Toast.LENGTH_SHORT).show()
        }
    }
""",
    )

    text = text.replace(
"""    fun renameSavedPlace(place: SavedPlace, name: String) {
        val safeName = name.trim().ifBlank { defaultSavedPlaceName(place.type) }
        scope.launch {
            repository.updateSavedPlace(place.copy(name = safeName))
            Toast.makeText(context, "Nome salvo: ${'$'}safeName", Toast.LENGTH_SHORT).show()
        }
    }
""",
"""    fun renameSavedPlace(place: SavedPlace, name: String) {
        val safeName = name.trim().ifBlank { defaultSavedPlaceName(place.type) }
        traceUserAction("place.rename", "started", "id=${'$'}{place.id} type=${'$'}{place.type} name=${'$'}safeName")
        scope.launch {
            repository.updateSavedPlace(place.copy(name = safeName))
            traceUserAction("place.rename", "success", "id=${'$'}{place.id} type=${'$'}{place.type} name=${'$'}safeName")
            Toast.makeText(context, "Nome salvo: ${'$'}safeName", Toast.LENGTH_SHORT).show()
        }
    }
""",
    )

    text = text.replace(
"""    val cardModelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
""",
"""    val cardModelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        traceUserAction("cards.import_prints", if (uris.isEmpty()) "cancelled" else "started", "count=${'$'}{uris.size}")
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
""",
    )

    text = text.replace(
"""            templateStatus = when {
                failures == 0 -> "Leitura concluida: ${'$'}imported modelo(s) importado(s)."
                imported == 0 -> "Nenhum modelo importado. Confira se os prints sao cards de corrida."
                else -> "Leitura concluida: ${'$'}imported modelo(s) importado(s), ${'$'}failures print(s) sem leitura."
            }
""",
"""            templateStatus = when {
                failures == 0 -> "Leitura concluida: ${'$'}imported modelo(s) importado(s)."
                imported == 0 -> "Nenhum modelo importado. Confira se os prints sao cards de corrida."
                else -> "Leitura concluida: ${'$'}imported modelo(s) importado(s), ${'$'}failures print(s) sem leitura."
            }
            traceUserAction("cards.import_prints", if (imported > 0) "success" else "fail", "imported=${'$'}imported failures=${'$'}failures selected=${'$'}{uris.size}")
""",
    )

    text = text.replace(
"""    val radarFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            radarImportStatus = "Importacao cancelada."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            radarImportStatus = "Importando radares..."
""",
"""    val radarFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        traceUserAction("radar.import_file", if (uri == null) "cancelled" else "started")
        if (uri == null) {
            radarImportStatus = "Importacao cancelada."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            radarImportStatus = "Importando radares..."
""",
    )

    text = text.replace(
"""            }.onSuccess { count ->
                radarImportStatus = "Importacao concluida: ${'$'}count radar(es)."
                Toast.makeText(context, "Radares importados: ${'$'}count", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                radarImportStatus = "Falha ao importar radares: ${'$'}{error.message.orEmpty()}"
            }
""",
"""            }.onSuccess { count ->
                radarImportStatus = "Importacao concluida: ${'$'}count radar(es)."
                traceUserAction("radar.import_file", "success", "count=${'$'}count")
                Toast.makeText(context, "Radares importados: ${'$'}count", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                radarImportStatus = "Falha ao importar radares: ${'$'}{error.message.orEmpty()}"
                traceUserAction("radar.import_file", "fail", error.message.orEmpty())
            }
""",
    )

    text = text.replace(
"""                    onSaveSettings = { scope.launch { repository.saveSettings(it) } },
""",
"""                    onSaveSettings = {
                        traceUserAction("analysis.save_settings", "started")
                        scope.launch {
                            repository.saveSettings(it)
                            traceUserAction("analysis.save_settings", "success")
                        }
                    },
""",
    )

    text = text.replace(
"""                    onPickCardModels = { cardModelPicker.launch("image/*") },
""",
"""                    onPickCardModels = {
                        traceUserAction("cards.pick_prints", "started")
                        cardModelPicker.launch("image/*")
                    },
""",
    )

    text = text.replace(
"""                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRefreshLiveState = { liveEnabled = isLiveAccessibilityEnabled(context) },
""",
"""                    onOpenAccessibilitySettings = {
                        traceUserAction("accessibility.open_settings", "started")
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRefreshLiveState = {
                        liveEnabled = isLiveAccessibilityEnabled(context)
                        traceUserAction("accessibility.refresh_state", "success", "enabled=${'$'}liveEnabled")
                    },
""",
    )

    text = text.replace(
"""                    onSave = { scope.launch { repository.saveSettings(it) } },
""",
"""                    onSave = {
                        traceUserAction("config.save_settings", "started")
                        scope.launch {
                            repository.saveSettings(it)
                            traceUserAction("config.save_settings", "success")
                        }
                    },
""",
    )

    text = text.replace(
"""                    onDeleteSavedPlace = { place -> scope.launch { repository.removeSavedPlace(place.id) } },
""",
"""                    onDeleteSavedPlace = { place ->
                        traceUserAction("place.delete", "started", "id=${'$'}{place.id} type=${'$'}{place.type}")
                        scope.launch {
                            repository.removeSavedPlace(place.id)
                            traceUserAction("place.delete", "success", "id=${'$'}{place.id} type=${'$'}{place.type}")
                        }
                    },
""",
    )

    text = text.replace(
"""                    onCreateBackup = { backupFileCreator.launch("rota-certa-backup.json") },
                    onRestoreBackup = { backupFilePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onImportRadarFile = { radarFilePicker.launch(arrayOf("text/*", "text/comma-separated-values", "application/octet-stream", "*/*")) },
                    onOpenMapaRadar = { openMapaRadarSite(context) },
""",
"""                    onCreateBackup = {
                        traceUserAction("backup.create_picker", "started")
                        backupFileCreator.launch("rota-certa-backup.json")
                    },
                    onRestoreBackup = {
                        traceUserAction("backup.restore_picker", "started")
                        backupFilePicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    onImportRadarFile = {
                        traceUserAction("radar.import_picker", "started")
                        radarFilePicker.launch(arrayOf("text/*", "text/comma-separated-values", "application/octet-stream", "*/*"))
                    },
                    onOpenMapaRadar = {
                        traceUserAction("radar.open_mapa_radar", "started")
                        openMapaRadarSite(context)
                    },
""",
    )

    text = text.replace(
"""                    onClearImportedRadars = {
                        scope.launch {
                            repository.clearImportedRadars()
                            radarImportStatus = "Radares importados removidos."
                        }
                    },
""",
"""                    onClearImportedRadars = {
                        traceUserAction("radar.clear_imported", "started")
                        scope.launch {
                            repository.clearImportedRadars()
                            radarImportStatus = "Radares importados removidos."
                            traceUserAction("radar.clear_imported", "success")
                        }
                    },
""",
    )

    text = text.replace(
"""                    onOpenBlaBlaCarCollector = {
                        context.startActivity(Intent(context, BlaBlaCarCollectorActivity::class.java))
                    },
                    onClearClipboard = { clearClipboard(context) },
""",
"""                    onOpenBlaBlaCarCollector = {
                        traceUserAction("tools.open_blablacar_collector", "started")
                        context.startActivity(Intent(context, BlaBlaCarCollectorActivity::class.java))
                    },
                    onClearClipboard = {
                        clearClipboard(context)
                        traceUserAction("tools.clear_clipboard", "success")
                    },
""",
    )

    text = text.replace(
"""                NavigationBarItem(selected = tab == TAB_ANALYSIS, onClick = { tab = TAB_ANALYSIS }, label = { Text("Analise") }, icon = {})
                NavigationBarItem(selected = tab == TAB_CONFIG, onClick = { tab = TAB_CONFIG }, label = { Text("Config") }, icon = {})
                NavigationBarItem(selected = tab == TAB_TOOLS, onClick = { tab = TAB_TOOLS }, label = { Text("Ferramentas") }, icon = {})
                NavigationBarItem(selected = tab == TAB_HISTORY, onClick = { tab = TAB_HISTORY }, label = { Text("Historico") }, icon = {})
""",
"""                NavigationBarItem(selected = tab == TAB_ANALYSIS, onClick = { traceUserAction("navigation.tab", "clicked", TAB_ANALYSIS); tab = TAB_ANALYSIS }, label = { Text("Analise") }, icon = {})
                NavigationBarItem(selected = tab == TAB_CONFIG, onClick = { traceUserAction("navigation.tab", "clicked", TAB_CONFIG); tab = TAB_CONFIG }, label = { Text("Config") }, icon = {})
                NavigationBarItem(selected = tab == TAB_TOOLS, onClick = { traceUserAction("navigation.tab", "clicked", TAB_TOOLS); tab = TAB_TOOLS }, label = { Text("Ferramentas") }, icon = {})
                NavigationBarItem(selected = tab == TAB_HISTORY, onClick = { traceUserAction("navigation.tab", "clicked", TAB_HISTORY); tab = TAB_HISTORY }, label = { Text("Historico") }, icon = {})
""",
    )

    text = text.replace(
"""                    writer.write(selected.toJsonText())
""",
"""                    writer.write(selected.toJsonText(context))
""",
    )

    text = text.replace(
"""private fun LiveDiagnostic.toJsonText(): String =
""",
"""private fun LiveDiagnostic.toJsonText(context: Context): String =
""",
    )

    text = text.replace(
"""        put("logs", org.json.JSONArray().apply {
            diagnosticLog.lines().filter { it.isNotBlank() }.forEach { put(it) }
        })
    }.toString(2)
""",
"""        put("logs", org.json.JSONArray().apply {
            diagnosticLog.lines().filter { it.isNotBlank() }.forEach { put(it) }
        })
        put("systemActionDiagnostics", org.json.JSONObject().apply {
            val actions = context.systemUserActionDiagnosticEvents()
            put("total", actions.size)
            put("lastAction", actions.lastOrNull() ?: org.json.JSONObject.NULL)
            put("events", org.json.JSONArray().apply { actions.forEach { put(it) } })
        })
    }.toString(2)
""",
    )

    if (text != original) file.writeText(text)
}

systemWideActionDiagnostics.configure {
    mustRunAfter("diagnosticJsonToolsActions")
    mustRunAfter("bubbleSavePrimaryMenu")
    mustRunAfter("bubbleActionDiagnosticHardening")
    mustRunAfter("bubbleLongPressCaptureSave")
    mustRunAfter("bubbleLongPressDirectSaveAfterOcr")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(systemWideActionDiagnostics)
}
