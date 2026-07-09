fun replacePrivateFunctionBlockImmediateExecution(
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

fun replaceMemberBlockImmediateExecution(
    source: String,
    startMarker: String,
    nextMarker: String,
    replacement: String,
): String {
    val start = source.indexOf(startMarker)
    if (start < 0) return source
    val next = source.indexOf(nextMarker, start + startMarker.length)
    if (next < 0) return source
    return source.substring(0, start) + replacement + source.substring(next + 1)
}

val bubbleImmediateExecutionGuard by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("com.google.android.documentsui" !in text) {
            text = text.replace(
                "\"com.openai.chatgpt\",\n            \"com.samsung.android.app.settings\",",
                "\"com.openai.chatgpt\",\n            \"com.google.android.documentsui\",\n            \"com.samsung.android.app.settings\",",
            )
        }

        val eventReplacement = """    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!serviceReady || event == null) return
        if (!currentSettings.appEnabled) {
            if (currentRadarColor != RadarColor.Idle) resetToIdle("Rota Certa desligado pelo usuario.", record = false)
            return
        }
        val eventPackageName = normalizePackageName(event.packageName?.toString())
        val packageName = eventPackageName ?: currentRootPackageName()
        if (eventPackageName != null) {
            activePackageName = if (isPassiveDiagnosticPackage(eventPackageName)) null else eventPackageName
        }
        if (packageName == null) {
            traceEvent("event ignored package= reason=Pacote ativo nao informado pelo Android.")
            analyzeJob?.cancel()
            pendingAnalysis = null
            resetToIdle("Pacote ativo nao informado pelo Android.", record = false)
            return
        }
        if (!shouldScanPackage(packageName)) {
            val reason = scanBlockReason(packageName)
            traceEvent("event blocked package=${dollar}packageName type=${dollar}{event.eventType} reason=${dollar}reason fast_pause=true")
            analyzeJob?.cancel()
            pendingAnalysis = null
            clearRememberedRideText()
            resetToIdle(reason = reason, record = false)
            return
        }
        traceEvent("event package=${dollar}{packageName.orEmpty()} type=${dollar}{event.eventType}")
        if (currentRadarColor == RadarColor.Idle) showOverlay(RadarColor.Default)
        scheduleVisibleTextAnalysis(delayMs = 0L)
        requestScreenshotAnalysis()
    }

"""
        text = replaceMemberBlockImmediateExecution(
            text,
            "    override fun onAccessibilityEvent(event: AccessibilityEvent?) {",
            "\n    override fun onInterrupt() = Unit",
            eventReplacement,
        )

        text = replacePrivateFunctionBlockImmediateExecution(text, "startContinuousScan", """    private fun startContinuousScan() {
        if (continuousScanStarted || !serviceReady) return
        continuousScanStarted = true
        traceEvent("scan.loop started interval=${dollar}{SCAN_LOOP_MS}ms immediate_guard=true")
        scope.launch {
            while (serviceReady) {
                if (!currentSettings.appEnabled) {
                    if (currentRadarColor != RadarColor.Idle) resetToIdle("Rota Certa desligado pelo usuario.", record = false)
                    delay(SCAN_LOOP_MS)
                    continue
                }
                val packageName = currentWindowPackageName()
                if (shouldScanPackage(packageName)) {
                    if (currentRadarColor == RadarColor.Idle) showOverlay(RadarColor.Default)
                    scheduleVisibleTextAnalysis(delayMs = 0L)
                    requestScreenshotAnalysis()
                } else if (currentRadarColor != RadarColor.Idle || lastSnapshotHash != null || pendingAnalysis != null) {
                    analyzeJob?.cancel()
                    pendingAnalysis = null
                    resetToIdle(
                        reason = scanBlockReason(packageName),
                        record = false,
                    )
                }
                delay(SCAN_LOOP_MS)
            }
        }
    }

""")

        text = replacePrivateFunctionBlockImmediateExecution(text, "scheduleVisibleTextAnalysis", """    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {
        if (!serviceReady) return
        val scheduleWindowPackageName = currentWindowPackageName()
        if (!shouldScanPackage(scheduleWindowPackageName)) {
            analyzeJob?.cancel()
            pendingAnalysis = null
            clearRememberedRideText()
            traceEvent("accessibility.schedule skipped blocked_window=${dollar}{scheduleWindowPackageName.orEmpty()}")
            return
        }
        analyzeJob?.cancel()
        traceEvent("accessibility.schedule delay=${dollar}{delayMs}ms supersede=true immediate_guard=true")
        analyzeJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            val visibleText = collectVisibleText(false)
            traceEvent("accessibility.collect length=${dollar}{visibleText.length}")
            processRideText(visibleText, TextSource.Accessibility, false)
        }
    }

""")

        text = replacePrivateFunctionBlockImmediateExecution(text, "requestScreenshotAnalysis", """    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {
        if (!serviceReady || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val requestedWindowPackageName = currentWindowPackageName()
        if (!shouldScanPackage(requestedWindowPackageName)) {
            traceEvent("screenshot.request skipped blocked_window=${dollar}{requestedWindowPackageName.orEmpty()}")
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastScreenshotMillis < SCREENSHOT_INTERVAL_MS) return
        if (!screenshotInProgress.compareAndSet(false, true)) {
            traceEvent("screenshot.request skipped in_progress=true")
            return
        }
        lastScreenshotMillis = now
        traceEvent("screenshot.request started package=${dollar}{requestedWindowPackageName.orEmpty()}")
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            runCatching {
                                val currentPackageName = currentWindowPackageName()
                                if (requestedWindowPackageName == currentPackageName && shouldScanPackage(currentPackageName)) {
                                    val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching
                                    val ocrText = ocrService.extractText(bitmap)
                                    traceEvent("screenshot.ocr success length=${dollar}{ocrText.length}")
                                    processRideText(ocrText, TextSource.Ocr, false)
                                } else {
                                    traceEvent("screenshot.discard window_changed request=${dollar}{requestedWindowPackageName.orEmpty()} current=${dollar}{currentPackageName.orEmpty()}")
                                }
                            }.onFailure { error ->
                                traceEvent("screenshot.ocr error=${dollar}{error::class.java.simpleName}: ${dollar}{error.message.orEmpty()}")
                                recordDiagnostic(
                                    stage = "screenshot_ocr_error",
                                    reason = "Falha ao ler texto do print da tela.",
                                    error = error,
                                )
                            }
                            screenshotInProgress.set(false)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        val failureWindowPackageName = currentWindowPackageName()
                        traceEvent("screenshot.request failed code=${dollar}errorCode package=${dollar}{failureWindowPackageName.orEmpty()}")
                        if (shouldIgnoreScreenshotFailure(requestedWindowPackageName, failureWindowPackageName, allowPopupCandidate)) {
                            traceEvent("screenshot.failure ignored passive_or_unmonitored=true request=${dollar}{requestedWindowPackageName.orEmpty()} current=${dollar}{failureWindowPackageName.orEmpty()}")
                            screenshotInProgress.set(false)
                            return
                        }
                        recordDiagnostic(
                            stage = "screenshot_failed",
                            reason = "Android recusou o print da acessibilidade. Codigo: ${dollar}errorCode.",
                        )
                        screenshotInProgress.set(false)
                    }
                },
            )
        }.onFailure { error ->
            traceEvent("screenshot.request error=${dollar}{error::class.java.simpleName}: ${dollar}{error.message.orEmpty()}")
            recordDiagnostic(
                stage = "screenshot_request_error",
                reason = "Nao consegui solicitar print da tela pela acessibilidade.",
                error = error,
            )
            screenshotInProgress.set(false)
        }
    }

""")

        text = text.replace(
"""        if (snapshotHash == lastAnalyzedHash) {
            traceEvent("analysis.skip duplicate_hash=${dollar}snapshotHash")
            return
        }
        if (analyzing) {
            pendingAnalysis = null
            analysisSerial += 1
            traceEvent("analysis.supersede previous=true hash=${dollar}snapshotHash")
        }
        analyzeLiveText(snapshotText, fields, snapshotHash, cardMatch, allowPopupCandidate)
""",
"""        if (snapshotHash == lastAnalyzedHash) {
            traceEvent("analysis.skip duplicate_hash=${dollar}snapshotHash")
            return
        }
        if (analyzing) {
            if (pendingAnalysis?.snapshotHash == snapshotHash) {
                traceEvent("analysis.skip duplicate_pending_hash=${dollar}snapshotHash")
                return
            }
            pendingAnalysis = PendingLiveAnalysis(snapshotText, fields, snapshotHash, cardMatch, allowPopupCandidate)
            traceEvent("analysis.defer analyzing=true hash=${dollar}snapshotHash immediate_guard=true")
            return
        }
        analyzeLiveText(snapshotText, fields, snapshotHash, cardMatch, allowPopupCandidate)
""",
        )

        text = text.replace(
"""                traceEvent("decision.quick recommendation=${dollar}{quickResult.recommendation} distance=quick_approx_until_final_route")
                showOverlay(color = quickColor, distanceKm = quickResult.nearestConfiguredDistanceKm())
""",
"""                traceEvent("decision.quick recommendation=${dollar}{quickResult.recommendation} distance=hidden_until_final_route")
                showOverlay(color = quickColor, distanceKm = null)
""",
        )
        text = text.replace(
"""                traceEvent("decision.quick recommendation=${dollar}{quickResult.recommendation} distance=${dollar}{quickResult.nearestConfiguredDistanceKm()?.let(::formatDiagnosticKm) ?: "null"}")
                showOverlay(color = quickColor, distanceKm = quickResult.nearestConfiguredDistanceKm())
""",
"""                traceEvent("decision.quick recommendation=${dollar}{quickResult.recommendation} distance=hidden_until_final_route")
                showOverlay(color = quickColor, distanceKm = null)
""",
        )
        text = text.replace(
"""    private fun AnalysisResult.trustedBubbleDistanceKm(): Double? =
        nearestConfiguredDistanceKm()
""",
"""    private fun AnalysisResult.trustedBubbleDistanceKm(): Double? =
        if (reason.contains("Google Maps", ignoreCase = true)) nearestConfiguredDistanceKm() else null
""",
        )
        text = text.replace(
            "val bubbleDistanceKm = result.nearestConfiguredDistanceKm()",
            "val bubbleDistanceKm = result.nearestRoutedConfiguredDistanceKm(homeDistanceKm, alternativeDistanceKm)",
        )
        text = text.replace("distance=quick_approx_until_final_route", "distance=hidden_until_final_route")
        text = text.replace("approx_or_route", "hidden_until_final_route")

        if ("bubble_immediate_execution_guard.patch_applied" !in text) {
            text = text.replace(
                "        traceEvent(\"precise_bubble_route_km.patch_applied=true\")\n",
                "        traceEvent(\"precise_bubble_route_km.patch_applied=true\")\n        traceEvent(\"bubble_immediate_execution_guard.patch_applied=true\")\n",
            )
        }

        if (text != original) file.writeText(text)
    }
}

bubbleImmediateExecutionGuard.configure {
    mustRunAfter(
        "patchRealtimeBubbleEngine",
        "patchOcrAccessibilityPriority",
        "preciseBubbleRouteKm",
        "finalKmAndStrictRideCard",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleImmediateExecutionGuard)
}
