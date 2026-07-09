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

        if (text != original) {
            file.writeText(text)
        }
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchRealtimeBubbleEngine)
}
