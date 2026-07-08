val bubbleLongPressDirectSaveAfterOcr by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        patchLongPressDirectSave(serviceFile.asFile)
        patchLongPressDirectSaveDiagnostics(mainFile.asFile)
        patchMainActivitySystemWideActions(mainFile.asFile)
    }
}

fun patchLongPressDirectSave(file: java.io.File) {
    var text = file.readText()
    val original = text

    if ("private fun saveLongPressCapturedCardDirectly()" !in text) {
        text = text.replace(
"""    private fun captureAndSaveCardFromBubbleLongPress() {
""",
"""    private fun saveLongPressCapturedCardDirectly() {
        scope.launch {
            traceEvent("diagnostic.contract bubble_long_press step=direct_save_start ok=true")
            val candidate = bestCardSaveCandidate(null, "")
            val packageName = candidate?.first ?: lastCardSaveCandidatePackageName
            val text = candidate?.second ?: lastCardSaveCandidateText
            traceEvent("diagnostic.contract bubble_long_press step=post_ocr_candidate ok=${'$'}{text.isNotBlank()} package=${'$'}{packageName.orEmpty()} text_len=${'$'}{text.length}")

            if (text.isBlank()) {
                traceEvent("diagnostic.contract bubble_long_press step=direct_save_fail reason=text_blank")
                toast("Nao consegui ler o card neste print.")
                recordDiagnostic(
                    stage = "bubble_save_card_empty",
                    color = currentRadarColor,
                    reason = "Toque longo capturou a tela, mas nao havia texto suficiente para salvar o card.",
                    text = text,
                )
                return@launch
            }

            val normalizedPackage = packageName?.takeIf { it.isNotBlank() }
            if (normalizedPackage == null || isBlockedLongPressCardPackage(normalizedPackage)) {
                traceEvent("diagnostic.contract bubble_long_press step=direct_save_fail reason=blocked_package package=${'$'}{normalizedPackage.orEmpty()} text_len=${'$'}{text.length}")
                toast("Abra o card no app de corrida para salvar.")
                recordDiagnostic(
                    stage = "bubble_save_card_missing_package",
                    color = currentRadarColor,
                    reason = "Toque longo leu a tela, mas recusou salvar porque o pacote nao e app de corrida: ${'$'}{normalizedPackage.orEmpty()}.",
                    text = text,
                )
                return@launch
            }

            traceEvent("diagnostic.contract save_card step=started ok=true source=long_press_direct")
            traceEvent("bubble.save_card_start source=long_press_direct")
            val template = RideCardTemplateMatcher.createTemplate(normalizedPackage, text)
            repository.addCardTemplate(template)
            rememberCardSaveCandidate(normalizedPackage, text, "long_press_card_saved")

            val parseResult = parser.parseWithMetadata(text, normalizedPackage)
            repository.addCapturedScreen(
                CapturedRideScreen(
                    createdAtMillis = System.currentTimeMillis(),
                    packageName = normalizedPackage,
                    textHash = text.snapshotHash(),
                    textPreview = text.trim().take(DIAGNOSTIC_TEXT_LIMIT),
                    parserName = parseResult.parserName,
                    pickup = parseResult.fields.pickup,
                    destination = parseResult.fields.destination,
                    fare = parseResult.fields.fare,
                ),
            )
            traceEvent("diagnostic.contract save_card result=success source=long_press_direct package=${'$'}normalizedPackage text_len=${'$'}{text.length}")
            toast("Card de corrida salvo.")
            recordDiagnostic(
                stage = "bubble_save_card",
                color = currentRadarColor,
                reason = "Card de corrida salvo pelo toque longo: ${'$'}{template.name}.",
                text = text,
                fields = parseResult.fields,
            )
        }
    }

    private fun isBlockedLongPressCardPackage(packageName: String): Boolean {
        val normalized = normalizePackageName(packageName) ?: return true
        if (normalized == this.packageName) return true
        if (normalized == "android") return true
        if (normalized == "com.android.systemui") return true
        if (normalized == "com.samsung.android.systemui") return true
        if (normalized == "br.com.mapeiaia.rotacerta.learned.popup") return true
        if (normalized.contains("documentsui")) return true
        if (normalized.contains("android.apps.nbu.files")) return true
        if (normalized.contains("sec.android.app.myfiles")) return true
        if (normalized.contains("launcher")) return true
        if (normalized.contains("chrome")) return true
        if (normalized.contains("settings")) return true
        if (normalized.startsWith("com.google.android.inputmethod")) return true
        return false
    }

    private fun captureAndSaveCardFromBubbleLongPress() {
""",
        )
    }

    text = text.replace(
"""            updateBubbleLongPressCountdown(null)
            saveCurrentRideCardFromBubble()
""",
"""            updateBubbleLongPressCountdown(null)
            traceEvent("diagnostic.contract bubble_long_press step=direct_save_call ok=true")
            saveLongPressCapturedCardDirectly()
""",
    )

    if (text != original) file.writeText(text)
}

fun patchLongPressDirectSaveDiagnostics(file: java.io.File) {
    var text = file.readText()
    val original = text

    text = text.replace(
"""    val longPressScreenshotAttemptCount = count("diagnostic.contract bubble_long_press step=screenshot_forced")
    val noBubbleActionRegistered = !menuOpened && !saveButtonClicked && !openAppClicked && !bubbleTouchDown && !bubbleTouchUp && !shortcutCaptureRequested && !longPressCountdownStarted && !longPressTriggered
""",
"""    val longPressScreenshotAttemptCount = count("diagnostic.contract bubble_long_press step=screenshot_forced")
    val longPressDirectSaveCall = has("diagnostic.contract bubble_long_press step=direct_save_call")
    val longPressDirectSaveStarted = has("diagnostic.contract bubble_long_press step=direct_save_start")
    val longPressPostOcrCandidate = has("diagnostic.contract bubble_long_press step=post_ocr_candidate ok=true")
    val longPressDirectTextBlank = has("diagnostic.contract bubble_long_press step=direct_save_fail reason=text_blank")
    val longPressDirectBlockedPackage = has("diagnostic.contract bubble_long_press step=direct_save_fail reason=blocked_package")
    val noBubbleActionRegistered = !menuOpened && !saveButtonClicked && !openAppClicked && !bubbleTouchDown && !bubbleTouchUp && !shortcutCaptureRequested && !longPressCountdownStarted && !longPressTriggered
""",
    )

    text = text.replace(
"""        longPressTriggered && !longPressSaveStarted -> "A bolinha detectou toque longo de 3 segundos, mas a rotina de captura nao iniciou."
        longPressTriggered && !longPressForcedScreenshot && !saveSuccess -> "A bolinha detectou toque longo de 3 segundos, mas nao conseguiu pedir o print da tela."
        longPressTriggered && screenshotStartedCount == 0 && !saveSuccess -> "A bolinha detectou toque longo de 3 segundos, mas nenhum print foi iniciado pelo Android."
        longPressTriggered && saveStarted && !saveSuccess -> "A bolinha detectou toque longo, iniciou captura e salvamento, mas ainda nao confirmou o card salvo."
""",
"""        longPressTriggered && !longPressSaveStarted -> "A bolinha detectou toque longo de 3 segundos, mas a rotina de captura nao iniciou."
        longPressTriggered && !longPressForcedScreenshot && !saveSuccess -> "A bolinha detectou toque longo de 3 segundos, mas nao conseguiu pedir o print da tela."
        longPressTriggered && screenshotStartedCount == 0 && !saveSuccess -> "A bolinha detectou toque longo de 3 segundos, mas nenhum print foi iniciado pelo Android."
        longPressTriggered && longPressForcedScreenshot && !longPressDirectSaveCall && !saveSuccess -> "A bolinha tirou print no toque longo, mas a chamada de salvar apos OCR nao aconteceu."
        longPressDirectSaveCall && !longPressDirectSaveStarted && !saveSuccess -> "A bolinha chamou o salvamento apos OCR, mas a rotina direta nao iniciou."
        longPressDirectTextBlank && !saveSuccess -> "A bolinha tirou print no toque longo, mas o OCR nao entregou texto suficiente para salvar."
        longPressDirectBlockedPackage && !saveSuccess -> "A bolinha leu a tela, mas recusou salvar porque o pacote atual nao e app de corrida. Abra o card no app de corrida e segure novamente."
        longPressPostOcrCandidate && saveStarted && !saveSuccess -> "A bolinha leu texto no toque longo e iniciou o salvamento, mas ainda nao confirmou o card salvo."
        longPressTriggered && saveStarted && !saveSuccess -> "A bolinha detectou toque longo, iniciou captura e salvamento, mas ainda nao confirmou o card salvo."
""",
    )

    text = text.replace(
"""        put("tentativasPrintToqueLongo", longPressScreenshotAttemptCount)
        put("semAcaoBolinhaRegistrada", noBubbleActionRegistered)
""",
"""        put("tentativasPrintToqueLongo", longPressScreenshotAttemptCount)
        put("salvamentoDiretoToqueLongoChamado", longPressDirectSaveCall)
        put("salvamentoDiretoToqueLongoIniciado", longPressDirectSaveStarted)
        put("candidatoAposOcrToqueLongo", longPressPostOcrCandidate)
        put("falhaToqueLongoTextoVazio", longPressDirectTextBlank)
        put("falhaToqueLongoPacoteBloqueado", longPressDirectBlockedPackage)
        put("semAcaoBolinhaRegistrada", noBubbleActionRegistered)
""",
    )

    if (text != original) file.writeText(text)
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
"""                    onPickCardModels = { cardModelPicker.launch("image/*") },
""",
"""                    onPickCardModels = {
                        traceUserAction("cards.pick_prints", "started")
                        cardModelPicker.launch("image/*")
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

bubbleLongPressDirectSaveAfterOcr.configure {
    mustRunAfter("bubbleLongPressCaptureSave")
    mustRunAfter("bubbleActionDiagnosticHardening")
    mustRunAfter("bubbleSavePrimaryMenu")
    mustRunAfter("bubbleUnlimitedCardLearning")
    mustRunAfter("diagnosticJsonToolsActions")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleLongPressDirectSaveAfterOcr)
}
