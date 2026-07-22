fun replacePrivateFunctionBlockRealtime(
    source: String,
    functionName: String,
    replacement: String,
): String {
    val start = source.indexOf("    private fun $functionName")
    if (start < 0) return source
    val next = source.indexOf("\n    private fun ", start + 1)
    return if (next < 0) {
        source.substring(0, start) + replacement
    } else {
        source.substring(0, start) + replacement + source.substring(next + 1)
    }
}

val patchRealtimeBubbleEngine by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        text = text.replace("const val SCAN_LOOP_MS = 850L", "const val SCAN_LOOP_MS = 180L")
        text = text.replace("const val SCAN_LOOP_MS = 350L", "const val SCAN_LOOP_MS = 180L")
        text = text.replace("const val SCREENSHOT_INTERVAL_MS = 650L", "const val SCREENSHOT_INTERVAL_MS = 420L")

        text = replacePrivateFunctionBlockRealtime(text, "scheduleVisibleTextAnalysis", """    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {
        if (!serviceReady) return
        val scheduleWindowPackageName = currentWindowPackageName()
        if (scheduleWindowPackageName == this.packageName) {
            traceEvent("accessibility.schedule skipped rota_certa_foreground")
            analyzeJob?.cancel()
            pendingAnalysis = null
            clearRememberedRideText()
            return
        }
        if (!allowPopupCandidate && !shouldScanPackage(scheduleWindowPackageName)) return
        analyzeJob?.cancel()
        traceEvent("accessibility.schedule delay=${dollar}{delayMs}ms supersede=true")
        analyzeJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            val visibleText = collectVisibleText(allowPopupCandidate)
            traceEvent("accessibility.collect length=${dollar}{visibleText.length}")
            processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate)
        }
    }

""")

        text = text.replace("scheduleVisibleTextAnalysis(delayMs = 80L", "scheduleVisibleTextAnalysis(delayMs = 0L")

        text = text.replace(
"""            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.ignore_reason keep_decision=true reason=${dollar}reason")
                return
            }
""",
"""            traceEvent("ocr.ignore_reason reset_decision=true reason=${dollar}reason")
""",
        )
        text = text.replace(
"""            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.ride_offer_false keep_decision=true reason=${dollar}reason")
                return
            }
""",
"""            traceEvent("ocr.ride_offer_false reset_decision=true reason=${dollar}reason")
""",
        )
        text = text.replace(
"""            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.card_model_missing keep_decision=true templates=${dollar}{currentCardTemplates.size}")
                return
            }
""",
"""            traceEvent("ocr.card_model_missing reset_decision=true templates=${dollar}{currentCardTemplates.size}")
""",
        )

        text = text.replace(
"""        if (snapshotHash != lastSnapshotHash) {
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            showOverlay(RadarColor.Default)
            recordDiagnostic(
                stage = "screen_changed",
                reason = "A imagem/texto da tela mudou; aguardando confirmar o card cadastrado.",
                text = snapshotText,
            )
        }
""",
"""        if (snapshotHash != lastSnapshotHash) {
            val previousSnapshotHash = lastSnapshotHash
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            pendingAnalysis = null
            registeredCardGate.clear()
            if (previousSnapshotHash != null) {
                traceEvent("screen.supersede previous=${dollar}previousSnapshotHash current=${dollar}snapshotHash")
            }
            showOverlay(RadarColor.Default, distanceKm = null)
            recordDiagnostic(
                stage = "screen_changed",
                reason = "A imagem/texto da tela mudou; limpei a bolinha antes de confirmar o proximo card cadastrado.",
                text = snapshotText,
            )
        }
""",
        )

        text = text.replace(
"""        if (snapshotHash != lastSnapshotHash) {
            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.screen_changed keep_decision=true hash=${dollar}snapshotHash")
            } else {
                lastSnapshotHash = snapshotHash
                lastAnalyzedHash = null
                showOverlay(RadarColor.Default)
                recordDiagnostic(
                    stage = "screen_changed",
                    reason = "A imagem/texto da tela mudou; aguardando confirmar o card cadastrado.",
                    text = snapshotText,
                )
            }
        }
""",
"""        if (snapshotHash != lastSnapshotHash) {
            val previousSnapshotHash = lastSnapshotHash
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            pendingAnalysis = null
            registeredCardGate.clear()
            if (previousSnapshotHash != null) {
                traceEvent("screen.supersede previous=${dollar}previousSnapshotHash current=${dollar}snapshotHash")
            }
            showOverlay(RadarColor.Default, distanceKm = null)
            recordDiagnostic(
                stage = "screen_changed",
                reason = "A imagem/texto da tela mudou; limpei a bolinha antes de confirmar o proximo card cadastrado.",
                text = snapshotText,
            )
        }
""",
        )

        text = text.replace(
"""            repository.addAnalysis(result)
            lastSavedReadHash = snapshotHash
            if (!allowPopupCandidate && !shouldScanCurrentWindow()) {
""",
"""            repository.addAnalysis(result)
            lastSavedReadHash = snapshotHash
            if (!allowPopupCandidate && lastSnapshotHash != snapshotHash) {
                registeredCardGate.clear()
                resetToDefault(
                    reason = "A tela mudou durante o calculo; descartei a decisao antiga antes de mostrar cor/km.",
                    record = false,
                )
                recordDiagnostic(
                    stage = "analysis_superseded",
                    reason = "A tela mudou durante o calculo; resultado antigo nao foi aplicado na bolinha.",
                    text = text,
                    fields = fields,
                    result = result,
                    cardTemplateMatch = cardMatch,
                )
                return
            }
            if (!allowPopupCandidate && !shouldScanCurrentWindow()) {
""",
        )
        text = text.replace(
"""            repository.addAnalysis(result)
            rememberLiveDecision(snapshotHash, result)
            lastSavedReadHash = snapshotHash
            if (!allowPopupCandidate && !shouldScanCurrentWindow()) {
""",
"""            repository.addAnalysis(result)
            rememberLiveDecision(snapshotHash, result)
            lastSavedReadHash = snapshotHash
            if (!allowPopupCandidate && lastSnapshotHash != snapshotHash) {
                registeredCardGate.clear()
                resetToDefault(
                    reason = "A tela mudou durante o calculo; descartei a decisao antiga antes de mostrar cor/km.",
                    record = false,
                )
                recordDiagnostic(
                    stage = "analysis_superseded",
                    reason = "A tela mudou durante o calculo; resultado antigo nao foi aplicado na bolinha.",
                    text = text,
                    fields = fields,
                    result = result,
                    cardTemplateMatch = cardMatch,
                )
                return
            }
            if (!allowPopupCandidate && !shouldScanCurrentWindow()) {
""",
        )

        text = text.replace(
"""            if (pending != null && pending.snapshotHash != lastAnalyzedHash && shouldScanCurrentWindow()) {
""",
"""            if (pending != null && pending.snapshotHash != lastAnalyzedHash && pending.snapshotHash == lastSnapshotHash && shouldScanCurrentWindow()) {
""",
        )

        text = text.replace(
"""    private fun AnalysisResult.trustedBubbleDistanceKm(): Double? =
        if (reason.contains("Google Maps", ignoreCase = true)) nearestConfiguredDistanceKm() else null
""",
"""    private fun AnalysisResult.trustedBubbleDistanceKm(): Double? =
        nearestConfiguredDistanceKm()
""",
        )
        text = text.replace(
            "val bubbleDistanceKm = result.nearestRoutedConfiguredDistanceKm(homeDistanceKm, alternativeDistanceKm)",
            "val bubbleDistanceKm = result.nearestConfiguredDistanceKm()",
        )
        text = text.replace(
            "showOverlay(color = quickColor, distanceKm = null)",
            "showOverlay(color = quickColor, distanceKm = quickResult.nearestConfiguredDistanceKm())",
        )
        text = text.replace("distance=hidden_until_final_route", "distance=quick_approx_until_final_route")
        text = text.replace("hidden_approximate", "approx_or_route")
        text = text.replace("hidden_no_route", "approx_or_route")

        text = text.replace(
            "newView.contentDescription = \"Rota Certa - toque duas vezes para salvar card\"",
            "newView.contentDescription = \"Rota Certa - toque para abrir o aplicativo\"",
        )
        text = text.replace(
            "newView.contentDescription = \"Rota Certa\"",
            "newView.contentDescription = \"Rota Certa - toque para abrir o aplicativo\"",
        )
        text = text.replace("newView.isLongClickable = true", "newView.isLongClickable = false")
        text = text.replace(
"""            newView.setOnClickListener { toggleActionMenu() }
""",
"""            newView.setOnClickListener {
                traceEvent("diagnostic.contract bubble_single_tap step=open_home ok=true")
                openApp()
            }
""",
        )
        text = text.replace(
"""            newView.setOnClickListener {
                traceEvent("diagnostic.contract bubble_single_tap step=click ok=true")
                toggleActionMenu()
            }
""",
"""            newView.setOnClickListener {
                traceEvent("diagnostic.contract bubble_single_tap step=open_home ok=true")
                openApp()
            }
""",
        )

        text = replacePrivateFunctionBlockRealtime(text, "toggleActionMenu", """    private fun toggleActionMenu() {
        traceEvent("diagnostic.contract bubble_menu step=removed_open_home ok=true")
        openApp()
    }

""")
        text = replacePrivateFunctionBlockRealtime(text, "showActionMenu", """    private fun showActionMenu() {
        traceEvent("diagnostic.contract bubble_menu step=blocked_removed ok=true")
        openApp()
    }

""")
        text = replacePrivateFunctionBlockRealtime(text, "captureAndSaveCardFromBubbleDoubleTap", """    private fun captureAndSaveCardFromBubbleDoubleTap() {
        traceEvent("diagnostic.contract bubble_double_tap step=removed ok=true")
        openApp()
    }

""")
        text = replacePrivateFunctionBlockRealtime(text, "captureAndSaveCardFromBubbleLongPress", """    private fun captureAndSaveCardFromBubbleLongPress() {
        traceEvent("diagnostic.contract bubble_long_press step=removed ok=true")
        openApp()
    }

""")
        text = replacePrivateFunctionBlockRealtime(text, "updateBubbleLongPressCountdown", """    private fun updateBubbleLongPressCountdown(text: String?) {
        bubbleLongPressCountdownText = null
        overlayView?.let { view ->
            val bubbleText = formatBubbleDistanceKm(currentDistanceKm)
            view.text = bubbleText
            view.textSize = bubbleTextSizeSp(bubbleText)
        }
        hideBubbleLongPressCountdownOverlay()
        if (!text.isNullOrBlank()) {
            traceEvent("diagnostic.contract bubble_long_press step=countdown_removed ok=true value=${dollar}{text.orEmpty()}")
        }
    }

""")

        val listenerReplacement = """    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

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
                    traceEvent("diagnostic.contract bubble_touch step=down ok=true mode=open_home_only")
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (abs(deltaX) > dp(4) || abs(deltaY) > dp(4)) moved = true
                    params.x = (startX + deltaX).roundToInt().coerceAtLeast(0)
                    params.y = (startY + deltaY).roundToInt().coerceAtLeast(0)
                    runCatching { manager.updateViewLayout(view, params) }
                    updateActionMenuPosition()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    bubblePrefs.edit().putInt(KEY_BUBBLE_X, params.x).putInt(KEY_BUBBLE_Y, params.y).apply()
                    traceEvent("diagnostic.contract bubble_touch step=up ok=true moved=${dollar}moved mode=open_home_only")
                    if (!moved) view.performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    traceEvent("diagnostic.contract bubble_touch step=cancel ok=true mode=open_home_only")
                    return true
                }
            }
            return false
        }
    }

"""
        val listenerRegex = Regex("(?s)    private inner class BubbleTouchListener : View\\.OnTouchListener \\{.*?    private fun dp")
        text = listenerRegex.replace(text) { listenerReplacement + "    private fun dp" }

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchRealtimeBubbleEngine.configure {
    mustRunAfter(
        "bubbleLongPressCaptureSave",
        "bubbleLongPressDirectSaveAfterOcr",
        "bubbleDoubleTapCardCapture",
        "bubbleDoubleTapDiagnosticsRobust",
        "bubblePersistentActionsAndHitbox",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchRealtimeBubbleEngine)
}
