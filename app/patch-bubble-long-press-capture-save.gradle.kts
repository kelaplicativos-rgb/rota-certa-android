val bubbleLongPressCaptureSave by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        patchServiceLongPressCapture(serviceFile.asFile)
        patchMainLongPressDiagnostic(mainFile.asFile)
    }
}

fun patchServiceLongPressCapture(file: java.io.File) {
    var text = file.readText()
    val original = text

    if ("bubbleLongPressCountdownText" !in text) {
        text = text.replace(
"""    private var currentDistanceKm: Double? = null
""",
"""    private var currentDistanceKm: Double? = null
    private var bubbleLongPressCountdownText: String? = null
    private var bubbleLongPressCountdownOverlayView: TextView? = null
    private var bubbleLongPressCountdownOverlayParams: WindowManager.LayoutParams? = null
""",
        )
    }

    if ("bubbleLongPressCountdownOverlayView" !in text) {
        text = text.replace(
"""    private var bubbleLongPressCountdownText: String? = null
""",
"""    private var bubbleLongPressCountdownText: String? = null
    private var bubbleLongPressCountdownOverlayView: TextView? = null
    private var bubbleLongPressCountdownOverlayParams: WindowManager.LayoutParams? = null
""",
        )
    }

    if ("hideBubbleLongPressCountdownOverlay()" !in text.substringBefore("override fun onDestroy()")) {
        text = text.replace(
"""        screenshotInProgress.set(false)
        analyzeJob?.cancel()
        removeOverlay()
""",
"""        screenshotInProgress.set(false)
        hideBubbleLongPressCountdownOverlay()
        analyzeJob?.cancel()
        removeOverlay()
""",
        )
    }

    text = text.replace(
"""        view.text = formatBubbleDistanceKm(currentDistanceKm)
""",
"""        view.text = bubbleLongPressCountdownText ?: formatBubbleDistanceKm(currentDistanceKm)
""",
    )

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
        ).apply {
            gravity = Gravity.CENTER
        }
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

    private fun captureAndSaveCardFromBubbleLongPress() {
        scope.launch {
            traceEvent("diagnostic.contract bubble_long_press step=save_start ok=true")
            traceEvent("diagnostic.contract bubble_capture step=shortcut_requested ok=true source=long_press_3s")
            updateBubbleLongPressCountdown("OK")
            toast("Capturando print do card...")
            hideActionMenu()
            cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 10_000L
            lastScreenshotMillis = 0L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                traceEvent("diagnostic.contract bubble_long_press step=screenshot_forced ok=true")
                requestScreenshotAnalysis(allowPopupCandidate = true)
                delay(1_400L)
            } else {
                traceEvent("diagnostic.contract bubble_long_press step=screenshot_forced ok=false reason=android_version")
            }
            updateBubbleLongPressCountdown(null)
            saveCurrentRideCardFromBubble()
        }
    }

    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false
        private var longPressHandled = false
        private var longPressJob: Job? = null

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
                    hideActionMenu()
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
                    if (!moved) view.performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressJob?.cancel()
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
    val withFullScreenCountdownRegex = Regex("(?s)    private fun updateBubbleLongPressCountdown\\(text: String\\?\\) \\{.*?    private fun bubbleLongPressCountdownTextSizeSp\\(value: String\\): Float = when \\(value\\) \\{.*?    private inner class BubbleTouchListener : View\\.OnTouchListener \\{.*?    private fun dp")
    val withCountdownRegex = Regex("(?s)    private fun updateBubbleLongPressCountdown\\(text: String\\?\\) \\{.*?    private inner class BubbleTouchListener : View\\.OnTouchListener \\{.*?    private fun dp")
    val withHelperRegex = Regex("(?s)    private fun captureAndSaveCardFromBubbleLongPress\\(\\) \\{.*?    private inner class BubbleTouchListener : View\\.OnTouchListener \\{.*?    private fun dp")
    val listenerOnlyRegex = Regex("(?s)    private inner class BubbleTouchListener : View\\.OnTouchListener \\{.*?    private fun dp")
    text = when {
        withFullScreenCountdownRegex.containsMatchIn(text) -> withFullScreenCountdownRegex.replace(text) { finalReplacement }
        withCountdownRegex.containsMatchIn(text) -> withCountdownRegex.replace(text) { finalReplacement }
        withHelperRegex.containsMatchIn(text) -> withHelperRegex.replace(text) { finalReplacement }
        listenerOnlyRegex.containsMatchIn(text) -> listenerOnlyRegex.replace(text) { finalReplacement }
        else -> text
    }

    if (text != original) file.writeText(text)
}

fun patchMainLongPressDiagnostic(file: java.io.File) {
    var text = file.readText()
    val original = text

    text = text.replace(
"""    val bubbleTouchUp = has("diagnostic.contract bubble_touch step=up")
    val noBubbleActionRegistered = !menuOpened && !saveButtonClicked && !openAppClicked && !bubbleTouchDown && !bubbleTouchUp && !shortcutCaptureRequested
""",
"""    val bubbleTouchUp = has("diagnostic.contract bubble_touch step=up")
    val longPressCountdownStarted = has("diagnostic.contract bubble_long_press step=countdown_tick")
    val longPressCountdownTicks = count("diagnostic.contract bubble_long_press step=countdown_tick")
    val longPressCountdownScreenShown = has("diagnostic.contract bubble_long_press step=countdown_screen_show ok=true")
    val longPressTriggered = has("diagnostic.contract bubble_long_press step=triggered")
    val longPressSaveStarted = has("diagnostic.contract bubble_long_press step=save_start")
    val longPressForcedScreenshot = has("diagnostic.contract bubble_long_press step=screenshot_forced ok=true")
    val longPressScreenshotAttemptCount = count("diagnostic.contract bubble_long_press step=screenshot_forced")
    val noBubbleActionRegistered = !menuOpened && !saveButtonClicked && !openAppClicked && !bubbleTouchDown && !bubbleTouchUp && !shortcutCaptureRequested && !longPressCountdownStarted && !longPressTriggered
""",
    )

    text = text.replace(
"""        saveSuccess -> "Card salvo com sucesso."
        noBubbleActionRegistered && screenshotBlockedCount > 0 -> "Nao ha acao da bolinha registrada. O diagnostico so encontrou prints automaticos ou bloqueados; toque no atalho da bolinha e exporte o diagnostico logo em seguida."
""",
"""        saveSuccess -> "Card salvo com sucesso."
        longPressCountdownStarted && !longPressCountdownScreenShown -> "A contagem regressiva iniciou, mas os numeros grandes nao apareceram na tela."
        longPressCountdownStarted && !longPressTriggered -> "A contagem regressiva apareceu, mas a bolinha foi solta ou movida antes de completar 3 segundos."
        longPressTriggered && !longPressSaveStarted -> "A bolinha detectou toque longo de 3 segundos, mas a rotina de captura nao iniciou."
        longPressTriggered && !longPressForcedScreenshot && !saveSuccess -> "A bolinha detectou toque longo de 3 segundos, mas nao conseguiu pedir o print da tela."
        longPressTriggered && screenshotStartedCount == 0 && !saveSuccess -> "A bolinha detectou toque longo de 3 segundos, mas nenhum print foi iniciado pelo Android."
        longPressTriggered && saveStarted && !saveSuccess -> "A bolinha detectou toque longo, iniciou captura e salvamento, mas ainda nao confirmou o card salvo."
        noBubbleActionRegistered && screenshotBlockedCount > 0 -> "Nao ha acao da bolinha registrada. O diagnostico so encontrou prints automaticos ou bloqueados; toque no atalho da bolinha e exporte o diagnostico logo em seguida."
""",
    )

    text = text.replace(
"""        put("toqueBolinhaFinalizado", bubbleTouchUp)
        put("semAcaoBolinhaRegistrada", noBubbleActionRegistered)
""",
"""        put("toqueBolinhaFinalizado", bubbleTouchUp)
        put("contagemRegressivaToqueLongo", longPressCountdownStarted)
        put("contagemRegressivaTicks", longPressCountdownTicks)
        put("contagemRegressivaGrandeNaTela", longPressCountdownScreenShown)
        put("toqueLongoTresSegundos", longPressTriggered)
        put("capturaToqueLongoIniciada", longPressSaveStarted)
        put("printToqueLongoSolicitado", longPressForcedScreenshot)
        put("tentativasPrintToqueLongo", longPressScreenshotAttemptCount)
        put("semAcaoBolinhaRegistrada", noBubbleActionRegistered)
""",
    )

    if (text != original) file.writeText(text)
}

bubbleLongPressCaptureSave.configure {
    mustRunAfter("bubbleActionDiagnosticHardening")
    mustRunAfter("bubbleSavePrimaryMenu")
    mustRunAfter("bubbleUnlimitedCardLearning")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(bubbleLongPressCaptureSave)
}
