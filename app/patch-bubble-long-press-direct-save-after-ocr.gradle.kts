val bubbleLongPressDirectSaveAfterOcr by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        patchLongPressDirectSave(serviceFile.asFile)
        patchLongPressDirectSaveDiagnostics(mainFile.asFile)
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

bubbleLongPressDirectSaveAfterOcr.configure {
    mustRunAfter("bubbleLongPressCaptureSave")
    mustRunAfter("bubbleActionDiagnosticHardening")
    mustRunAfter("bubbleSavePrimaryMenu")
    mustRunAfter("bubbleUnlimitedCardLearning")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleLongPressDirectSaveAfterOcr)
}
