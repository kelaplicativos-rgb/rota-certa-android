val bubbleDoubleTapCardCapture by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        patchServiceDoubleTapCapture(serviceFile.asFile)
        patchMainDoubleTapDiagnostics(mainFile.asFile)
    }
}

fun patchServiceDoubleTapCapture(file: java.io.File) {
    var text = file.readText()
    val original = text

    val replacement = """    private fun updateBubbleLongPressCountdown(text: String?) {
        bubbleLongPressCountdownText = text
        overlayView?.let { view ->
            val bubbleText = text ?: formatBubbleDistanceKm(currentDistanceKm)
            view.text = bubbleText
            view.textSize = bubbleTextSizeSp(bubbleText)
        }
        if (text.isNullOrBlank()) {
            hideBubbleLongPressCountdownOverlay()
        } else {
            showBubbleLongPressCountdownOverlay(text)
        }
        traceEvent("diagnostic.contract bubble_long_press step=countdown ok=true value=${'$'}{text.orEmpty()}")
    }

    private fun showBubbleLongPressCountdownOverlay(value: String) {
        val manager = windowManager ?: return
        val existing = bubbleLongPressCountdownOverlayView
        if (existing != null) {
            existing.text = value
            existing.textSize = bubbleLongPressCountdownTextSizeSp(value)
            traceEvent("diagnostic.contract bubble_long_press step=countdown_screen_update ok=true value=${'$'}value")
            return
        }
        val view = TextView(this).apply {
            text = value
            textSize = bubbleLongPressCountdownTextSizeSp(value)
            setTextColor(Color.WHITE)
            setShadowLayer(14f, 0f, 0f, Color.BLACK)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(Color.argb(138, 0, 0, 0))
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
        if (runCatching { manager.addView(view, params) }.isSuccess) {
            bubbleLongPressCountdownOverlayView = view
            bubbleLongPressCountdownOverlayParams = params
            traceEvent("diagnostic.contract bubble_long_press step=countdown_screen_show ok=true value=${'$'}value")
        } else {
            traceEvent("diagnostic.contract bubble_long_press step=countdown_screen_show ok=false")
        }
    }

    private fun hideBubbleLongPressCountdownOverlay() {
        val view = bubbleLongPressCountdownOverlayView ?: return
        runCatching { windowManager?.removeView(view) }
        bubbleLongPressCountdownOverlayView = null
        bubbleLongPressCountdownOverlayParams = null
        traceEvent("diagnostic.contract bubble_long_press step=countdown_screen_hide ok=true")
    }

    private fun bubbleLongPressCountdownTextSizeSp(value: String): Float = when (value) {
        "3", "2", "1" -> 172f
        "OK" -> 96f
        else -> 44f
    }

    private fun captureAndSaveCardFromBubbleDoubleTap() {
        scope.launch {
            traceEvent("diagnostic.contract bubble_double_tap step=triggered ok=true")
            traceEvent("diagnostic.contract bubble_capture step=shortcut_requested ok=true source=double_tap")
            hideActionMenu()
            toast("Tirando print do card...")
            val startedAt = System.currentTimeMillis()
            cardSaveScreenshotRequestedUntilMillis = startedAt + 10_000L
            snapshotCurrentCardCandidateForBubbleAction("double_tap_before_screenshot")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requestBubbleCardLearningScreenshot("double_tap", currentWindowPackageName() ?: activePackageName)
                repeat(10) {
                    delay(300L)
                    if (lastCardSaveCandidateAtMillis >= startedAt && lastCardSaveCandidateText.isNotBlank()) return@repeat
                }
            } else {
                traceEvent("diagnostic.contract bubble_double_tap step=screenshot_started ok=false reason=android_version")
                toast("Android sem suporte para print pela bolinha.")
            }
            saveCapturedCardModelFromBubble("double_tap")
        }
    }

    private fun captureAndSaveCardFromBubbleLongPress() {
        scope.launch {
            traceEvent("diagnostic.contract bubble_long_press step=save_start ok=true")
            traceEvent("diagnostic.contract bubble_capture step=shortcut_requested ok=true source=long_press_3s")
            updateBubbleLongPressCountdown("OK")
            toast("Capturando print do card...")
            hideActionMenu()
            val startedAt = System.currentTimeMillis()
            cardSaveScreenshotRequestedUntilMillis = startedAt + 10_000L
            lastScreenshotMillis = 0L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                traceEvent("diagnostic.contract bubble_long_press step=screenshot_forced ok=true")
                requestBubbleCardLearningScreenshot("long_press_3s", currentWindowPackageName() ?: activePackageName)
                repeat(10) {
                    delay(300L)
                    if (lastCardSaveCandidateAtMillis >= startedAt && lastCardSaveCandidateText.isNotBlank()) return@repeat
                }
            } else {
                traceEvent("diagnostic.contract bubble_long_press step=screenshot_forced ok=false reason=android_version")
            }
            updateBubbleLongPressCountdown(null)
            saveCapturedCardModelFromBubble("long_press_3s")
        }
    }

    private fun saveLongPressCapturedCardDirectly() {
        saveCapturedCardModelFromBubble("long_press_3s")
    }

    private fun requestBubbleCardLearningScreenshot(source: String, requestedPackageName: String?) {
        if (!serviceReady) {
            traceEvent("diagnostic.contract bubble_double_tap step=screenshot_started ok=false reason=service_not_ready source=${'$'}source")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            traceEvent("diagnostic.contract bubble_double_tap step=screenshot_started ok=false reason=android_version source=${'$'}source")
            return
        }
        if (!screenshotInProgress.compareAndSet(false, true)) {
            traceEvent("diagnostic.contract bubble_double_tap step=screenshot_started ok=false reason=in_progress source=${'$'}source")
            return
        }
        val sourcePackage = normalizePackageName(requestedPackageName ?: currentWindowPackageName() ?: activePackageName)
        traceEvent("diagnostic.contract bubble_double_tap step=screenshot_started ok=true source=${'$'}source package=${'$'}{sourcePackage.orEmpty()}")
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            runCatching {
                                val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching
                                val ocrText = ocrService.extractText(bitmap)
                                traceEvent("diagnostic.contract bubble_double_tap step=ocr_success ok=true source=${'$'}source package=${'$'}{sourcePackage.orEmpty()} text_len=${'$'}{ocrText.length}")
                                if (ocrText.isNotBlank() && !isBubbleActionMenuText(ocrText)) {
                                    toast("Print tirado. Lendo card...")
                                    rememberCardSaveCandidate(
                                        sourcePackage ?: RideCardTemplateMatcher.inferPackageName(ocrText) ?: LEARNED_POPUP_PACKAGE,
                                        ocrText,
                                        "${'$'}source_screenshot_ocr",
                                    )
                                } else {
                                    traceEvent("diagnostic.contract bubble_double_tap step=ocr_success ok=false reason=text_blank source=${'$'}source")
                                    toast("Print tirado, mas nao li texto suficiente.")
                                }
                            }.onFailure { error ->
                                traceEvent("diagnostic.contract bubble_double_tap step=ocr_error ok=false source=${'$'}source error=${'$'}{error::class.java.simpleName}: ${'$'}{error.message.orEmpty()}")
                                recordDiagnostic(
                                    stage = "bubble_double_tap_ocr_error",
                                    color = currentRadarColor,
                                    reason = "Print tirado, mas falhou a leitura do texto.",
                                    error = error,
                                )
                            }
                            screenshotInProgress.set(false)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        traceEvent("diagnostic.contract bubble_double_tap step=screenshot_failed ok=false source=${'$'}source code=${'$'}errorCode package=${'$'}{sourcePackage.orEmpty()}")
                        toast("Nao consegui tirar o print. Tente novamente.")
                        recordDiagnostic(
                            stage = "bubble_double_tap_screenshot_failed",
                            color = currentRadarColor,
                            reason = "Android recusou o print solicitado por dois toques. Codigo: ${'$'}errorCode.",
                        )
                        screenshotInProgress.set(false)
                    }
                },
            )
        }.onFailure { error ->
            traceEvent("diagnostic.contract bubble_double_tap step=screenshot_error ok=false source=${'$'}source error=${'$'}{error::class.java.simpleName}: ${'$'}{error.message.orEmpty()}")
            toast("Nao consegui solicitar o print.")
            recordDiagnostic(
                stage = "bubble_double_tap_screenshot_error",
                color = currentRadarColor,
                reason = "Erro ao solicitar print por dois toques na bolinha.",
                error = error,
            )
            screenshotInProgress.set(false)
        }
    }

    private fun saveCapturedCardModelFromBubble(source: String) {
        scope.launch {
            traceEvent("diagnostic.contract bubble_card_capture step=direct_save_start ok=true source=${'$'}source")
            val candidate = bestCardSaveCandidate(null, "")
            val sourcePackageName = candidate?.first ?: lastCardSaveCandidatePackageName ?: currentWindowPackageName() ?: activePackageName
            val capturedText = candidate?.second ?: lastCardSaveCandidateText
            val learnedPackage = RideCardTemplateMatcher.packageNameForLearning(sourcePackageName, capturedText)
            traceEvent("diagnostic.contract bubble_card_capture step=post_ocr_candidate ok=${'$'}{capturedText.isNotBlank()} source=${'$'}source source_package=${'$'}{sourcePackageName.orEmpty()} learned_package=${'$'}{learnedPackage.orEmpty()} text_len=${'$'}{capturedText.length}")

            if (capturedText.isBlank()) {
                traceEvent("diagnostic.contract bubble_card_capture step=direct_save_fail reason=text_blank source=${'$'}source")
                toast("Nao consegui ler o card neste print.")
                recordDiagnostic(
                    stage = "bubble_save_card_empty",
                    color = currentRadarColor,
                    reason = "A captura foi feita, mas nao havia texto suficiente para salvar o card.",
                    text = capturedText,
                )
                return@launch
            }

            if (learnedPackage == null) {
                traceEvent("diagnostic.contract bubble_card_capture step=direct_save_fail reason=not_ride_card source=${'$'}source source_package=${'$'}{sourcePackageName.orEmpty()} text_len=${'$'}{capturedText.length}")
                toast("Print tirado, mas nao parece card de corrida.")
                recordDiagnostic(
                    stage = "bubble_save_card_not_ride_card",
                    color = currentRadarColor,
                    reason = "O print foi lido, mas a validacao local nao encontrou dados suficientes de card de corrida.",
                    text = capturedText,
                )
                return@launch
            }

            val universalModel = RideCardTemplateMatcher.isUniversalLearnedPackage(learnedPackage)
            traceEvent("diagnostic.contract bubble_card_capture step=ai_validation ok=true source=${'$'}source source_package=${'$'}{sourcePackageName.orEmpty()} learned_package=${'$'}learnedPackage universal=${'$'}universalModel")
            traceEvent("diagnostic.contract save_card step=started ok=true source=${'$'}source")
            traceEvent("bubble.save_card_start source=${'$'}source")

            val templateName = if (universalModel) "Card universal por print" else null
            val template = RideCardTemplateMatcher.createTemplate(learnedPackage, capturedText, templateName)
            repository.addCardTemplate(template)
            rememberCardSaveCandidate(learnedPackage, capturedText, "${'$'}source_card_saved")

            val packageForParsing = RideCardTemplateMatcher.inferPackageName(capturedText)
                ?: learnedPackage.takeUnless { RideCardTemplateMatcher.isUniversalLearnedPackage(it) }
            val parseResult = parser.parseWithMetadata(capturedText, packageForParsing)
            repository.addCapturedScreen(
                CapturedRideScreen(
                    createdAtMillis = System.currentTimeMillis(),
                    packageName = learnedPackage,
                    textHash = capturedText.snapshotHash(),
                    textPreview = capturedText.trim().take(DIAGNOSTIC_TEXT_LIMIT),
                    parserName = parseResult.parserName,
                    pickup = parseResult.fields.pickup,
                    destination = parseResult.fields.destination,
                    fare = parseResult.fields.fare,
                ),
            )
            traceEvent("diagnostic.contract save_card result=success source=${'$'}source learned_package=${'$'}learnedPackage source_package=${'$'}{sourcePackageName.orEmpty()} universal=${'$'}universalModel text_len=${'$'}{capturedText.length}")
            toast(if (universalModel) "Print tirado. Card salvo como modelo universal." else "Print tirado. Card de corrida salvo.")
            recordDiagnostic(
                stage = "bubble_save_card",
                color = currentRadarColor,
                reason = if (universalModel) {
                    "Card de corrida salvo como modelo universal validado pelo conteudo: ${'$'}{template.name}."
                } else {
                    "Card de corrida salvo pela captura da bolinha: ${'$'}{template.name}."
                },
                text = capturedText,
                fields = parseResult.fields,
            )
        }
    }

    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false
        private var longPressHandled = false
        private var lastTapUpAtMillis = 0L
        private var longPressJob: Job? = null
        private var singleTapJob: Job? = null

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = overlayParams ?: return false
            val manager = windowManager ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    longPressHandled = false
                    longPressJob?.cancel()
                    traceEvent("diagnostic.contract bubble_touch step=down ok=true")
                    longPressJob = scope.launch {
                        try {
                            for (remaining in 3 downTo 1) {
                                if (moved || overlayView !== view) return@launch
                                updateBubbleLongPressCountdown(remaining.toString())
                                traceEvent("diagnostic.contract bubble_long_press step=countdown_tick ok=true remaining=${'$'}remaining")
                                delay(1_000L)
                            }
                            if (!moved && overlayView === view) {
                                singleTapJob?.cancel()
                                longPressHandled = true
                                traceEvent("diagnostic.contract bubble_long_press step=triggered ok=true duration_ms=3000")
                                updateBubbleLongPressCountdown("OK")
                                captureAndSaveCardFromBubbleLongPress()
                            }
                        } finally {
                            if (!longPressHandled) updateBubbleLongPressCountdown(null)
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (abs(deltaX) > dp(4) || abs(deltaY) > dp(4)) {
                        moved = true
                        singleTapJob?.cancel()
                        longPressJob?.cancel()
                        updateBubbleLongPressCountdown(null)
                    }
                    params.x = (startX + deltaX).roundToInt().coerceAtLeast(0)
                    params.y = (startY + deltaY).roundToInt().coerceAtLeast(0)
                    runCatching { manager.updateViewLayout(view, params) }
                    updateActionMenuPosition()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    bubblePrefs.edit().putInt(KEY_BUBBLE_X, params.x).putInt(KEY_BUBBLE_Y, params.y).apply()
                    longPressJob?.cancel()
                    traceEvent("diagnostic.contract bubble_touch step=up ok=true moved=${'$'}moved long_press=${'$'}longPressHandled")
                    if (longPressHandled) return true
                    updateBubbleLongPressCountdown(null)
                    if (!moved) {
                        val now = System.currentTimeMillis()
                        val isDoubleTap = now - lastTapUpAtMillis <= 450L
                        lastTapUpAtMillis = now
                        if (isDoubleTap) {
                            singleTapJob?.cancel()
                            traceEvent("diagnostic.contract bubble_double_tap step=detected ok=true")
                            captureAndSaveCardFromBubbleDoubleTap()
                        } else {
                            singleTapJob?.cancel()
                            singleTapJob = scope.launch {
                                delay(280L)
                                if (System.currentTimeMillis() - lastTapUpAtMillis >= 260L) {
                                    traceEvent("diagnostic.contract bubble_single_tap step=menu_scheduled ok=true")
                                    view.performClick()
                                }
                            }
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressJob?.cancel()
                    singleTapJob?.cancel()
                    updateBubbleLongPressCountdown(null)
                    traceEvent("diagnostic.contract bubble_touch step=cancel ok=true moved=${'$'}moved long_press=${'$'}longPressHandled")
                    return true
                }
            }
            return false
        }
    }

"""

    val finalReplacement = replacement + "    private fun dp"
    val bubbleBlockRegex = Regex("(?s)    private fun updateBubbleLongPressCountdown\\(text: String\\?\\) \\{.*?    private inner class BubbleTouchListener : View\\.OnTouchListener \\{.*?    private fun dp")
    text = bubbleBlockRegex.replace(text) { finalReplacement }

    if (text != original) file.writeText(text)
}

fun patchMainDoubleTapDiagnostics(file: java.io.File) {
    var text = file.readText()
    val original = text

    text = text.replace(
"""    val longPressDirectBlockedPackage = has("diagnostic.contract bubble_long_press step=direct_save_fail reason=blocked_package")
    val longPressDirectNotRideCard = has("diagnostic.contract bubble_long_press step=direct_save_fail reason=not_ride_card") || stage == "bubble_save_card_not_ride_card"
    val longPressUniversalModelSaved = has("diagnostic.contract save_card result=success source=long_press_direct") && has("universal=true")
    val noBubbleActionRegistered = !menuOpened && !saveButtonClicked && !openAppClicked && !bubbleTouchDown && !bubbleTouchUp && !shortcutCaptureRequested && !longPressCountdownStarted && !longPressTriggered
""",
"""    val longPressDirectBlockedPackage = has("diagnostic.contract bubble_long_press step=direct_save_fail reason=blocked_package")
    val longPressDirectNotRideCard = has("diagnostic.contract bubble_long_press step=direct_save_fail reason=not_ride_card") || has("diagnostic.contract bubble_card_capture step=direct_save_fail reason=not_ride_card") || stage == "bubble_save_card_not_ride_card"
    val longPressUniversalModelSaved = has("diagnostic.contract save_card result=success source=long_press_direct") && has("universal=true")
    val doubleTapDetected = has("diagnostic.contract bubble_double_tap step=detected") || has("diagnostic.contract bubble_double_tap step=triggered")
    val doubleTapTriggered = has("diagnostic.contract bubble_double_tap step=triggered")
    val doubleTapScreenshotStarted = has("diagnostic.contract bubble_double_tap step=screenshot_started ok=true source=double_tap")
    val doubleTapOcrSuccess = has("diagnostic.contract bubble_double_tap step=ocr_success ok=true source=double_tap")
    val doubleTapSaveStarted = has("diagnostic.contract bubble_card_capture step=direct_save_start ok=true source=double_tap")
    val doubleTapSaveSuccess = has("diagnostic.contract save_card result=success source=double_tap")
    val noBubbleActionRegistered = !menuOpened && !saveButtonClicked && !openAppClicked && !bubbleTouchDown && !bubbleTouchUp && !shortcutCaptureRequested && !longPressCountdownStarted && !longPressTriggered && !doubleTapDetected
""",
    )

    text = text.replace(
"""        saveSuccess -> "Card salvo com sucesso."
""",
"""        doubleTapSaveSuccess -> "Card salvo com sucesso pelo toque duplo da bolinha."
        saveSuccess -> "Card salvo com sucesso."
        doubleTapDetected && !doubleTapTriggered -> "A bolinha detectou o toque duplo, mas a captura nao iniciou."
        doubleTapTriggered && !doubleTapScreenshotStarted && !saveSuccess -> "O toque duplo iniciou, mas o print nao foi solicitado ou o Android bloqueou a captura."
        doubleTapScreenshotStarted && !doubleTapOcrSuccess && !saveSuccess -> "O toque duplo pediu o print, mas o OCR ainda nao confirmou texto lido."
        doubleTapOcrSuccess && !doubleTapSaveStarted && !saveSuccess -> "O toque duplo tirou print e leu texto, mas o salvamento do modelo nao iniciou."
""",
    )

    text = text.replace(
"""        put("modeloUniversalToqueLongo", longPressUniversalModelSaved)
        put("semAcaoBolinhaRegistrada", noBubbleActionRegistered)
""",
"""        put("modeloUniversalToqueLongo", longPressUniversalModelSaved)
        put("toqueDuploBolinha", doubleTapDetected)
        put("capturaToqueDuploIniciada", doubleTapTriggered)
        put("printToqueDuploSolicitado", doubleTapScreenshotStarted)
        put("ocrToqueDuploConfirmado", doubleTapOcrSuccess)
        put("salvamentoToqueDuploIniciado", doubleTapSaveStarted)
        put("salvamentoToqueDuploConfirmado", doubleTapSaveSuccess)
        put("semAcaoBolinhaRegistrada", noBubbleActionRegistered)
""",
    )

    if (text != original) file.writeText(text)
}

bubbleDoubleTapCardCapture.configure {
    mustRunAfter("universalAiCardLearning")
    mustRunAfter("bubbleLongPressDirectSaveAfterOcr")
    mustRunAfter("bubbleLongPressCaptureSave")
    mustRunAfter("bubbleActionDiagnosticHardening")
    mustRunAfter("bubbleSavePrimaryMenu")
    mustRunAfter("bubbleUnlimitedCardLearning")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleDoubleTapCardCapture)
}
