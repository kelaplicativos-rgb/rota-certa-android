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
