fun replacePrivateFunctionBlockDiagnosticBubble(
    source: String,
    functionName: String,
    transform: (String) -> String,
): String {
    val start = source.indexOf("    private fun $functionName")
    if (start < 0) return source
    val next = source.indexOf("\n    private fun ", start + 1)
    val block = if (next < 0) source.substring(start) else source.substring(start, next + 1)
    val replacement = transform(block)
    return if (next < 0) {
        source.substring(0, start) + replacement
    } else {
        source.substring(0, start) + replacement + source.substring(next + 1)
    }
}

val bubbleDiagnosticSizeAndLifecycleFix by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val modelsFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/Models.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, modelsFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        patchBubbleSettingsModel(modelsFile.asFile)
        patchBubbleMainAppearance(mainFile.asFile)
        patchBubbleServiceLifecycleSizeAndTouch(serviceFile.asFile)
    }
}

fun patchBubbleSettingsModel(file: java.io.File) {
    var text = file.readText()
    val original = text

    text = text.replace(
        "    val bubbleOpacity: Double = 1.0,\n    val bubbleDarkMode: Boolean = false,\n",
        "    val bubbleOpacity: Double = 1.0,\n    val bubbleSizeDp: Int = 96,\n    val bubbleDarkMode: Boolean = false,\n",
    )

    if (text != original) file.writeText(text)
}

fun patchBubbleMainAppearance(file: java.io.File) {
    var text = file.readText()
    val original = text

    text = text.replace(
"""        BubbleOpacitySlider(
            value = settings.bubbleOpacity,
            onValueChange = { onChange(settings.copy(bubbleOpacity = it)) },
            onValueChangeFinished = {},
        )
""",
"""        BubbleOpacitySlider(
            value = settings.bubbleOpacity,
            onValueChange = { onChange(settings.copy(bubbleOpacity = it)) },
            onValueChangeFinished = {},
        )
        BubbleSizeSlider(
            value = settings.bubbleSizeDp,
            onValueChange = { onChange(settings.copy(bubbleSizeDp = it)) },
            onValueChangeFinished = {},
        )
""",
    )

    if ("private fun BubbleSizeSlider(" !in text) {
        text = text.replace(
"""
@Composable
private fun SettingsLocationCard(
""",
"""
@Composable
private fun BubbleSizeSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val safeValue = value.coerceIn(48, 120)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Tamanho da bolinha: $safeValue dp")
        Slider(
            value = safeValue.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(48, 120)) },
            valueRange = 48f..120f,
            steps = 17,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

@Composable
private fun SettingsLocationCard(
""",
        )
    }

    if (text != original) file.writeText(text)
}

fun patchBubbleServiceLifecycleSizeAndTouch(file: java.io.File) {
    var text = file.readText()
    val original = text
    val dollar = "$"

    text = text.replace(
"""                    previousSettings.bubbleOpacity != settings.bubbleOpacity ||
                    previousSettings.bubbleDarkMode != settings.bubbleDarkMode
""",
"""                    previousSettings.bubbleOpacity != settings.bubbleOpacity ||
                    previousSettings.bubbleSizeDp != settings.bubbleSizeDp ||
                    previousSettings.bubbleDarkMode != settings.bubbleDarkMode
""",
    )

    text = text.replace(
"""            now - lastDecisionOverlayAtMillis < DECISION_OVERLAY_STICKY_MS
        ) {
""",
"""            now - lastDecisionOverlayAtMillis < DECISION_OVERLAY_STICKY_MS &&
            shouldKeepDecisionOverlayForSameRegisteredCard()
        ) {
""",
    )

    if ("private fun shouldKeepDecisionOverlayForSameRegisteredCard()" !in text) {
        text = text.replace(
"""
    private fun resetToDefault(
""",
"""
    private fun shouldKeepDecisionOverlayForSameRegisteredCard(): Boolean {
        val session = bubbleCardSessionStore.current ?: return false
        val activeHash = lastSnapshotHash ?: return false
        if (activeHash != session.snapshotHash) return false
        val activePackage = normalizePackageName(currentWindowPackageName())
        if (activePackage != null && session.packageName != null && activePackage != session.packageName) return false
        return true
    }

    private fun resetToDefault(
""",
        )
    }

    text = replacePrivateFunctionBlockDiagnosticBubble(text, "refreshOverlayAppearance") {
"""    private fun refreshOverlayAppearance() {
        val view = overlayView ?: return
        val params = overlayParams
        val sizePx = dp(currentSettings.bubbleSizeDp.coerceIn(48, 120))
        if (params != null && (params.width != sizePx || params.height != sizePx)) {
            params.width = sizePx
            params.height = sizePx
            runCatching { windowManager?.updateViewLayout(view, params) }
        }
        view.minWidth = sizePx
        view.minHeight = sizePx
        view.text = currentBubbleLabel ?: formatBubbleDistanceKm(currentDistanceKm)
        view.textSize = bubbleTextSizeSp(view.text.toString())
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(currentRadarColor.argb(currentSettings))
            setStroke(dp(3), Color.argb((currentSettings.bubbleOpacity.coerceIn(0.25, 1.0) * 255).roundToInt(), 255, 255, 255))
        }
        overlayMenuView?.background = actionMenuBackgroundDrawable()
    }

"""
    }

    text = replacePrivateFunctionBlockDiagnosticBubble(text, "overlayLayoutParams") {
"""    private fun overlayLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        dp(currentSettings.bubbleSizeDp.coerceIn(48, 120)),
        dp(currentSettings.bubbleSizeDp.coerceIn(48, 120)),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bubblePrefs.getInt(KEY_BUBBLE_X, dp(18))
        y = bubblePrefs.getInt(KEY_BUBBLE_Y, dp(90))
    }

"""
    }

    text = text.replace(
"""            newView.isLongClickable = true
            newView.minWidth = dp(96)
            newView.minHeight = dp(96)
""",
"""            newView.isLongClickable = false
            val bubbleSizePx = dp(currentSettings.bubbleSizeDp.coerceIn(48, 120))
            newView.minWidth = bubbleSizePx
            newView.minHeight = bubbleSizePx
""",
    )
    text = text.replace("            newView.minWidth = dp(96)\n            newView.minHeight = dp(96)\n", "            val bubbleSizePx = dp(currentSettings.bubbleSizeDp.coerceIn(48, 120))\n            newView.minWidth = bubbleSizePx\n            newView.minHeight = bubbleSizePx\n")

    text = replacePrivateFunctionBlockDiagnosticBubble(text, "captureAndSaveCardFromBubbleDoubleTap") {
"""    private fun captureAndSaveCardFromBubbleDoubleTap() {
        traceEvent("diagnostic.contract bubble_double_tap step=triggered ok=true")
        traceEvent("diagnostic.contract bubble_capture step=shortcut_requested ok=true source=double_tap")
        updateBubbleLongPressCountdown("2x")
        toast("Tirando print do card...")
        hideActionMenu()
        cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 10_000L
        lastScreenshotMillis = 0L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestScreenshotAnalysis(allowPopupCandidate = true)
        }
        scope.launch {
            delay(1_200L)
            updateBubbleLongPressCountdown(null)
            saveCurrentRideCardFromBubble()
        }
    }

"""
    }

    val listenerReplacement = """    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false
        private var lastTapUpAtMillis = 0L
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
                    singleTapJob?.cancel()
                    traceEvent("diagnostic.contract bubble_touch step=down ok=true long_press_disabled=true")
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (abs(deltaX) > dp(4) || abs(deltaY) > dp(4)) {
                        moved = true
                        singleTapJob?.cancel()
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
                    traceEvent("diagnostic.contract bubble_touch step=up ok=true moved=${dollar}moved long_press=false")
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
                    singleTapJob?.cancel()
                    updateBubbleLongPressCountdown(null)
                    traceEvent("diagnostic.contract bubble_touch step=cancel ok=true moved=${dollar}moved long_press=false")
                    return true
                }
            }
            return false
        }
    }

"""
    val listenerRegex = Regex("(?s)    private inner class BubbleTouchListener : View\\.OnTouchListener \\{.*?    private fun dp")
    text = listenerRegex.replace(text) { listenerReplacement + "    private fun dp" }

    if ("bubble_diagnostic_size_and_lifecycle_fix.patch_applied" !in text) {
        text = text.replace(
            "        traceEvent(\"modular_bubble_lifecycle.patch_applied=true\")\n",
            "        traceEvent(\"modular_bubble_lifecycle.patch_applied=true\")\n        traceEvent(\"bubble_diagnostic_size_and_lifecycle_fix.patch_applied=true\")\n",
        )
    }

    if (text != original) file.writeText(text)
}

bubbleDiagnosticSizeAndLifecycleFix.configure {
    mustRunAfter(
        "bubblePersistentActionsAndHitbox",
        "bubbleDoubleTapCardCapture",
        "bubbleLongPressDirectSaveAfterOcr",
        "bubbleLongPressCaptureSave",
        "modularBubbleLifecycle",
        "patchLiveRideOverlayStability",
        "bubbleLiveAppearance",
        "stableBubbleNoFlicker",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleDiagnosticSizeAndLifecycleFix)
}
