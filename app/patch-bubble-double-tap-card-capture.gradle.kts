val bubbleDoubleTapCardCapture by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        patchServiceDoubleTapCapture(serviceFile.asFile)
    }
}

fun patchServiceDoubleTapCapture(file: java.io.File) {
    var text = file.readText()
    val original = text

    if ("private fun captureAndSaveCardFromBubbleDoubleTap()" !in text) {
        text = text.replace(
"""    private inner class BubbleTouchListener : View.OnTouchListener {
""",
"""    private fun captureAndSaveCardFromBubbleDoubleTap() {
        traceEvent("diagnostic.contract bubble_double_tap step=triggered ok=true")
        traceEvent("diagnostic.contract bubble_capture step=shortcut_requested ok=true source=double_tap")
        toast("Tirando print do card...")
        hideActionMenu()
        cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 10_000L
        captureAndSaveCardFromBubbleLongPress()
    }

    private inner class BubbleTouchListener : View.OnTouchListener {
""",
        )
    }

    val listenerReplacement = """    private inner class BubbleTouchListener : View.OnTouchListener {
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

    val listenerRegex = Regex("(?s)    private inner class BubbleTouchListener : View\\.OnTouchListener \\{.*?    private fun dp")
    text = listenerRegex.replace(text) { listenerReplacement + "    private fun dp" }

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
