val bubblePersistentActionsAndHitbox by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        patchServiceBubblePersistenceAndHitbox(serviceFile.asFile)
        patchMainDiagnosticUsesPersistentBubbleActions(mainFile.asFile)
        patchMainUniversalPrintImport(mainFile.asFile)
    }
}

fun patchServiceBubblePersistenceAndHitbox(file: java.io.File) {
    var text = file.readText()
    val original = text

    text = text.replace(
"""    private fun overlayLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        dp(66),
        dp(66),
""",
"""    private fun overlayLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        dp(96),
        dp(96),
""",
    )

    text = text.replace(
"""            newView.contentDescription = "Rota Certa"
            newView.gravity = Gravity.CENTER
""",
"""            newView.contentDescription = "Rota Certa - toque duas vezes para salvar card"
            newView.isClickable = true
            newView.isLongClickable = true
            newView.minWidth = dp(96)
            newView.minHeight = dp(96)
            newView.gravity = Gravity.CENTER
""",
    )

    text = text.replace(
"""            newView.setOnClickListener { toggleActionMenu() }
""",
"""            newView.setOnClickListener {
                traceEvent("diagnostic.contract bubble_single_tap step=click ok=true")
                toggleActionMenu()
            }
""",
    )

    if ("private fun persistBubbleActionDiagnostic(" !in text) {
        text = text.replace(
"""    private fun traceEvent(message: String) {
""",
"""    private fun shouldPersistBubbleActionDiagnostic(message: String): Boolean =
        message.startsWith("diagnostic.contract bubble_") ||
            message.startsWith("diagnostic.contract save_card") ||
            message.startsWith("bubble.save_card") ||
            message.startsWith("bubble.open_app") ||
            message.startsWith("card_save_candidate")

    private fun persistBubbleActionDiagnostic(message: String) {
        if (!shouldPersistBubbleActionDiagnostic(message)) return
        val prefs = getSharedPreferences(SYSTEM_ACTION_DIAGNOSTIC_PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString(SYSTEM_ACTION_DIAGNOSTIC_LOG, "").orEmpty().lines().filter { it.isNotBlank() }
        val cleanMessage = message.replace(Regex("\\s+"), " ").trim().take(260)
        val line = "${'$'}{System.currentTimeMillis()} service.action ${'$'}cleanMessage"
        val next = (previous + line).takeLast(SYSTEM_ACTION_DIAGNOSTIC_LIMIT)
        prefs.edit().putString(SYSTEM_ACTION_DIAGNOSTIC_LOG, next.joinToString("\n")).apply()
    }

    private fun traceEvent(message: String) {
""",
        )
    }

    text = text.replace(
"""    private fun traceEvent(message: String) {
        diagnosticEvents += "${'$'}{System.currentTimeMillis()} ${'$'}message"
""",
"""    private fun traceEvent(message: String) {
        persistBubbleActionDiagnostic(message)
        diagnosticEvents += "${'$'}{System.currentTimeMillis()} ${'$'}message"
""",
    )

    if ("const val SYSTEM_ACTION_DIAGNOSTIC_PREFS" !in text) {
        text = text.replace(
"""        const val BUBBLE_PREFS = "rota_certa_bubble"
""",
"""        const val BUBBLE_PREFS = "rota_certa_bubble"
        const val SYSTEM_ACTION_DIAGNOSTIC_PREFS = "rota_certa_system_action_diagnostics"
        const val SYSTEM_ACTION_DIAGNOSTIC_LOG = "events"
        const val SYSTEM_ACTION_DIAGNOSTIC_LIMIT = 260
""",
        )
    }

    text = text.replace(
"""        "OK" -> 96f
""",
"""        "OK" -> 112f
        "2x" -> 112f
""",
    )

    text = text.replace(
"""            traceEvent("diagnostic.contract bubble_double_tap step=triggered ok=true")
            traceEvent("diagnostic.contract bubble_capture step=shortcut_requested ok=true source=double_tap")
            toast("Tirando print do card...")
""",
"""            traceEvent("diagnostic.contract bubble_double_tap step=triggered ok=true")
            traceEvent("diagnostic.contract bubble_capture step=shortcut_requested ok=true source=double_tap")
            updateBubbleLongPressCountdown("2x")
            toast("Tirando print do card...")
""",
    )

    text = text.replace(
"""            hideActionMenu()
            cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 10_000L
            captureAndSaveCardFromBubbleLongPress()
""",
"""            hideActionMenu()
            cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 10_000L
            captureAndSaveCardFromBubbleLongPress()
""",
    )

    if (text != original) file.writeText(text)
}

fun patchMainDiagnosticUsesPersistentBubbleActions(file: java.io.File) {
    var text = file.readText()
    val original = text

    text = text.replace(
"""        put("actionDiagnostics", diagnosticActionJson(diagnosticLog, stage))
        put("systemActionDiagnostics", org.json.JSONObject().apply {
            val actions = context.systemUserActionDiagnosticEvents()
""",
"""        val actions = context.systemUserActionDiagnosticEvents()
        val actionDiagnosticLog = (diagnosticLog + "\n" + actions.joinToString("\n")).trim()
        put("actionDiagnostics", diagnosticActionJson(actionDiagnosticLog, stage))
        put("systemActionDiagnostics", org.json.JSONObject().apply {
""",
    )

    text = text.replace(
"""        put("logs", org.json.JSONArray().apply {
            diagnosticLog.lines().filter { it.isNotBlank() }.forEach { put(it) }
        })
        put("systemActionDiagnostics", org.json.JSONObject().apply {
            val actions = context.systemUserActionDiagnosticEvents()
""",
"""        put("logs", org.json.JSONArray().apply {
            diagnosticLog.lines().filter { it.isNotBlank() }.forEach { put(it) }
        })
        val actions = context.systemUserActionDiagnosticEvents()
        val actionDiagnosticLog = (diagnosticLog + "\n" + actions.joinToString("\n")).trim()
        put("actionDiagnostics", diagnosticActionJson(actionDiagnosticLog, stage))
        put("systemActionDiagnostics", org.json.JSONObject().apply {
""",
    )

    if ("service.action diagnostic.contract bubble_double_tap" !in text) {
        text = text.replace(
"""    val secondBubbleTap = has("diagnostic.contract bubble_menu step=second_bubble_tap")
""",
"""    val secondBubbleTap = has("diagnostic.contract bubble_menu step=second_bubble_tap")
    val persistedBubbleTouch = has("service.action diagnostic.contract bubble_touch") || has("service.action diagnostic.contract bubble_single_tap")
    val persistedBubbleDoubleTap = has("service.action diagnostic.contract bubble_double_tap")
""",
        )
        text = text.replace(
"""    val noBubbleActionRegistered = !menuOpened && !saveButtonClicked && !openAppClicked && !bubbleTouchDown && !bubbleTouchUp && !shortcutCaptureRequested && !longPressCountdownStarted && !longPressTriggered && !doubleTapDetected
""",
"""    val noBubbleActionRegistered = !menuOpened && !saveButtonClicked && !openAppClicked && !bubbleTouchDown && !bubbleTouchUp && !shortcutCaptureRequested && !longPressCountdownStarted && !longPressTriggered && !doubleTapDetected && !persistedBubbleTouch && !persistedBubbleDoubleTap
""",
        )
    }

    text = text.replace(
"""        put("toqueBolinhaIniciado", bubbleTouchDown)
        put("toqueBolinhaFinalizado", bubbleTouchUp)
""",
"""        put("toqueBolinhaIniciado", bubbleTouchDown || persistedBubbleTouch)
        put("toqueBolinhaFinalizado", bubbleTouchUp || persistedBubbleTouch)
""",
    )

    text = text.replace(
"""        put("toqueDuploBolinha", doubleTapDetected)
""",
"""        put("toqueDuploBolinha", doubleTapDetected || persistedBubbleDoubleTap)
""",
    )

    if (text != original) file.writeText(text)
}

fun patchMainUniversalPrintImport(file: java.io.File) {
    var text = file.readText()
    val original = text

    text = text.replace(
"""                val packageName = RideCardTemplateMatcher.inferPackageName(extractedText)
                if (extractedText.isBlank() || packageName == null) {
                    failures += 1
                } else {
                    val template = RideCardTemplateMatcher.createTemplate(packageName, extractedText)
                    repository.addCardTemplate(template)
                    imported += 1
                }
""",
"""                val packageName = RideCardTemplateMatcher.packageNameForLearning(null, extractedText)
                if (extractedText.isBlank() || packageName == null) {
                    failures += 1
                } else {
                    val templateName = if (RideCardTemplateMatcher.isUniversalLearnedPackage(packageName)) "Card universal por print" else null
                    val template = RideCardTemplateMatcher.createTemplate(packageName, extractedText, templateName)
                    repository.addCardTemplate(template)
                    imported += 1
                }
""",
    )

    if (text != original) file.writeText(text)
}

bubblePersistentActionsAndHitbox.configure {
    mustRunAfter("bubbleDoubleTapDiagnosticsRobust")
    mustRunAfter("bubbleDoubleTapCardCapture")
    mustRunAfter("universalAiCardLearning")
    mustRunAfter("bubbleLongPressDirectSaveAfterOcr")
    mustRunAfter("bubbleLongPressCaptureSave")
    mustRunAfter("bubbleActionDiagnosticHardening")
    mustRunAfter("bubbleSavePrimaryMenu")
    mustRunAfter("bubbleUnlimitedCardLearning")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubblePersistentActionsAndHitbox)
}
