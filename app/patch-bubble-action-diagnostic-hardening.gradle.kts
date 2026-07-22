val bubbleActionDiagnosticHardening by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        patchServiceActionDiagnostics(serviceFile.asFile)
        patchMainActionDiagnostics(mainFile.asFile)
    }
}

fun patchServiceActionDiagnostics(file: java.io.File) {
    var text = file.readText()
    val original = text

    text = text.replace(
        "const val DIAGNOSTIC_EVENT_LIMIT = 180",
        "const val DIAGNOSTIC_EVENT_LIMIT = 320",
    )
    text = text.replace(
        "const val DIAGNOSTIC_EVENT_LIMIT = 60",
        "const val DIAGNOSTIC_EVENT_LIMIT = 320",
    )

    text = text.replace(
"""    private fun triggerBubbleSaveFromAction(source: String) {
        traceEvent("diagnostic.contract save_card step=button_clicked ok=true source=${'$'}source")
""",
"""    private fun triggerBubbleSaveFromAction(source: String) {
        traceEvent("diagnostic.contract bubble_capture step=shortcut_requested ok=true source=${'$'}source")
        traceEvent("diagnostic.contract save_card step=button_clicked ok=true source=${'$'}source")
""",
    )

    text = text.replace(
"""                for (attempt in 0 until 3) {
                    cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 2_500L
                    lastScreenshotMillis = 0L
                    requestScreenshotAnalysis(allowPopupCandidate = true)
                    delay(700L)
                    candidate = bestCardSaveCandidate(null, "")
                    if (candidate != null) break
                }
""",
"""                for (attempt in 0 until 3) {
                    traceEvent("diagnostic.contract bubble_capture step=screenshot_attempt ok=true attempt=${'$'}{attempt + 1}")
                    cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 2_500L
                    lastScreenshotMillis = 0L
                    requestScreenshotAnalysis(allowPopupCandidate = true)
                    delay(700L)
                    candidate = bestCardSaveCandidate(null, "")
                    traceEvent("diagnostic.contract bubble_capture step=screenshot_attempt_done ok=${'$'}{candidate != null} attempt=${'$'}{attempt + 1}")
                    if (candidate != null) break
                }
""",
    )

    text = text.replace(
"""                    if (!moved) view.performClick()
""",
"""                    traceEvent("diagnostic.contract bubble_touch step=up ok=true moved=${'$'}moved")
                    if (!moved) view.performClick()
""",
    )

    text = text.replace(
"""                    moved = false
                    return true
""",
"""                    moved = false
                    traceEvent("diagnostic.contract bubble_touch step=down ok=true")
                    return true
""",
    )

    if (text != original) file.writeText(text)
}

fun patchMainActionDiagnostics(file: java.io.File) {
    var text = file.readText()
    val original = text

    text = text.replace(
"""    val screenshotBlockedCount = count("screenshot.request skipped")

    val verdict = when {
""",
"""    val screenshotBlockedCount = count("screenshot.request skipped")
    val screenshotStartedCount = count("screenshot.request started")
    val screenshotFailedCount = count("screenshot.request failed")
    val screenshotDiscardedCount = count("screenshot.discard")
    val shortcutCaptureRequested = has("diagnostic.contract bubble_capture step=shortcut_requested")
    val shortcutScreenshotAttemptCount = count("diagnostic.contract bubble_capture step=screenshot_attempt")
    val bubbleTouchDown = has("diagnostic.contract bubble_touch step=down")
    val bubbleTouchUp = has("diagnostic.contract bubble_touch step=up")
    val noBubbleActionRegistered = !menuOpened && !saveButtonClicked && !openAppClicked && !bubbleTouchDown && !bubbleTouchUp && !shortcutCaptureRequested

    val verdict = when {
""",
    )

    text = text.replace(
"""        saveSuccess -> "Card salvo com sucesso."
""",
"""        saveSuccess -> "Card salvo com sucesso."
        noBubbleActionRegistered && screenshotBlockedCount > 0 -> "Nao ha acao da bolinha registrada. O diagnostico so encontrou prints automaticos ou bloqueados; toque no atalho da bolinha e exporte o diagnostico logo em seguida."
        shortcutCaptureRequested && shortcutScreenshotAttemptCount <= 1 && !saveSuccess -> "O atalho da bolinha iniciou captura, mas fez no maximo uma tentativa de print e nao confirmou salvamento."
        shortcutCaptureRequested && screenshotFailedCount > 0 && !saveSuccess -> "O atalho da bolinha pediu print, mas o Android recusou pelo menos uma captura."
        shortcutCaptureRequested && screenshotBlockedCount > 0 && !saveSuccess -> "O atalho da bolinha pediu captura, mas o print foi bloqueado por pacote fora do monitoramento."
""",
    )

    text = text.replace(
"""        put("printsBloqueados", screenshotBlockedCount)
""",
"""        put("printsBloqueados", screenshotBlockedCount)
        put("printsIniciados", screenshotStartedCount)
        put("printsFalhos", screenshotFailedCount)
        put("printsDescartados", screenshotDiscardedCount)
        put("capturaPeloAtalhoRegistrada", shortcutCaptureRequested)
        put("tentativasPrintAtalho", shortcutScreenshotAttemptCount)
        put("toqueBolinhaIniciado", bubbleTouchDown)
        put("toqueBolinhaFinalizado", bubbleTouchUp)
        put("semAcaoBolinhaRegistrada", noBubbleActionRegistered)
""",
    )

    text = text.replace(
"""    val saveStarted = has("bubble.save_card_start") || has("diagnostic.contract save_card step=started")
""",
"""    val saveStarted = stage == "bubble_save_card" ||
        stage == "bubble_save_card_empty" ||
        stage == "bubble_save_card_missing_package" ||
        has("bubble.save_card_start") ||
        has("diagnostic.contract save_card step=started")
""",
    )

    if ("SYSTEM_ACTION_DIAGNOSTIC_PREFS" !in text) {
        text = text.replace(
"""private fun diagnosticJsonFileName(diagnostic: LiveDiagnostic): String =
""",
"""private const val SYSTEM_ACTION_DIAGNOSTIC_PREFS = "rota_certa_system_action_diagnostics"
private const val SYSTEM_ACTION_DIAGNOSTIC_LOG = "events"
private const val SYSTEM_ACTION_DIAGNOSTIC_LIMIT = 260

private fun Context.traceSystemUserAction(action: String, status: String = "started", details: String = "") {
    val cleanAction = action.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_.-]+"), "_").trim('_').take(72).ifBlank { "unknown" }
    val cleanStatus = status.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_.-]+"), "_").trim('_').take(40).ifBlank { "unknown" }
    val cleanDetails = details.replace(Regex("\\s+"), " ").trim().take(220)
    val line = "${'$'}{System.currentTimeMillis()} ui.action name=${'$'}cleanAction status=${'$'}cleanStatus details=${'$'}cleanDetails"
    val prefs = getSharedPreferences(SYSTEM_ACTION_DIAGNOSTIC_PREFS, Context.MODE_PRIVATE)
    val previous = prefs.getString(SYSTEM_ACTION_DIAGNOSTIC_LOG, "").orEmpty().lines().filter { it.isNotBlank() }
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

    if ("traceUserAction(\"app.open\"" !in text) {
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
"""                    onPickCardModels = { cardModelPicker.launch("image/*") },
""",
"""                    onPickCardModels = {
                        traceUserAction("cards.pick_prints", "started")
                        cardModelPicker.launch("image/*")
                    },
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

    if ("systemActionDiagnostics" !in text) {
        text = text.replace(
"""        put("actionDiagnostics", diagnosticActionJson(diagnosticLog, stage))
""",
"""        put("actionDiagnostics", diagnosticActionJson(diagnosticLog, stage))
        put("systemActionDiagnostics", org.json.JSONObject().apply {
            val actions = context.systemUserActionDiagnosticEvents()
            put("total", actions.size)
            put("lastAction", actions.lastOrNull() ?: org.json.JSONObject.NULL)
            put("events", org.json.JSONArray().apply { actions.forEach { put(it) } })
        })
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
    }

    if (text != original) file.writeText(text)
}

bubbleActionDiagnosticHardening.configure {
    mustRunAfter("bubbleSavePrimaryMenu")
    mustRunAfter("bubbleUnlimitedCardLearning")
    mustRunAfter("patchFullDiagnosticExport")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleActionDiagnosticHardening)
}
