val patchLiveWindowStaleOcr by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        text = text.replace(
"""        if (!shouldScanPackage(packageName)) {
            val reason = scanBlockReason(packageName)
            traceEvent("event blocked package=${dollar}packageName reason=${dollar}reason")
            scheduleVisibleTextAnalysis(delayMs = 80L, allowPopupCandidate = true)
            requestScreenshotAnalysis(allowPopupCandidate = true)
            if (isPassiveDiagnosticPackage(packageName)) {
                resetToDefaultForNonRideScreen(reason)
                return
            }
            resetToIdle(reason = reason, record = true)
            return
        }
""",
"""        if (!shouldScanPackage(packageName)) {
            val reason = scanBlockReason(packageName)
            traceEvent("event blocked package=${dollar}packageName reason=${dollar}reason")
            if (isPassiveDiagnosticPackage(packageName) || packageName in IGNORED_PACKAGES || packageName == this.packageName) {
                analyzeJob?.cancel()
                pendingAnalysis = null
                screenshotInProgress.set(false)
                clearRememberedRideText()
                resetToDefaultForNonRideScreen(reason)
                return
            }
            scheduleVisibleTextAnalysis(delayMs = 80L, allowPopupCandidate = true)
            requestScreenshotAnalysis(allowPopupCandidate = true)
            resetToIdle(reason = reason, record = true)
            return
        }
""",
        )

        text = text.replace(
"""                } else if (isPassiveDiagnosticPackage(packageName)) {
                    val visibleText = collectVisibleText(allowPopupCandidate = true)
                    if (visibleText.isNotBlank() && looksLikeRegisteredPopupCandidate(visibleText)) {
                        processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
                        requestScreenshotAnalysis(allowPopupCandidate = true)
                    } else {
                        resetToDefaultForNonRideScreen("Tela passiva detectada fora do card cadastrado; bolinha voltou para cinza.")
                    }
                } else if (!isPassiveDiagnosticPackage(packageName)) {
""",
"""                } else if (isPassiveDiagnosticPackage(packageName)) {
                    analyzeJob?.cancel()
                    pendingAnalysis = null
                    screenshotInProgress.set(false)
                    clearRememberedRideText()
                    resetToDefaultForNonRideScreen("Tela passiva detectada; bolinha cinza em espera.")
                } else if (!isPassiveDiagnosticPackage(packageName)) {
""",
        )

        text = text.replace(
"""        lastScreenshotMillis = now
        traceEvent("screenshot.request started")
        runCatching {
""",
"""        lastScreenshotMillis = now
        val requestedWindowPackageName = currentWindowPackageName()
        traceEvent("screenshot.request started package=${dollar}{requestedWindowPackageName.orEmpty()}")
        runCatching {
""",
        )

        text = text.replace(
"""                            runCatching {
                                if (allowPopupCandidate || shouldScanCurrentWindow()) {
                                    val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching
                                    val ocrText = ocrService.extractText(bitmap)
                                    traceEvent("screenshot.ocr success length=${dollar}{ocrText.length}")
                                    processRideText(ocrText, TextSource.Ocr, allowPopupCandidate)
                                }
                            }.onFailure { error ->
""",
"""                            runCatching {
                                val callbackWindowPackageName = currentWindowPackageName()
                                if (!allowPopupCandidate && callbackWindowPackageName != requestedWindowPackageName) {
                                    traceEvent("screenshot.discard window_changed request=${dollar}{requestedWindowPackageName.orEmpty()} current=${dollar}{callbackWindowPackageName.orEmpty()}")
                                    return@runCatching
                                }
                                if (allowPopupCandidate && isPassiveDiagnosticPackage(callbackWindowPackageName)) {
                                    traceEvent("screenshot.discard passive_popup_window=${dollar}{callbackWindowPackageName.orEmpty()}")
                                    return@runCatching
                                }
                                if (allowPopupCandidate || shouldScanCurrentWindow()) {
                                    val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching
                                    val ocrText = ocrService.extractText(bitmap)
                                    if (!allowPopupCandidate && currentWindowPackageName() != requestedWindowPackageName) {
                                        traceEvent("screenshot.discard after_ocr window_changed request=${dollar}{requestedWindowPackageName.orEmpty()} current=${dollar}{currentWindowPackageName().orEmpty()}")
                                        return@runCatching
                                    }
                                    traceEvent("screenshot.ocr success length=${dollar}{ocrText.length}")
                                    processRideText(ocrText, TextSource.Ocr, allowPopupCandidate)
                                }
                            }.onFailure { error ->
""",
        )

        text = text.replace(
"""        val windowPackageName = currentWindowPackageName()
        if (!allowPopupCandidate && !shouldScanPackage(windowPackageName)) return
        val packageName = resolveRidePackageForText(windowPackageName, text, allowPopupCandidate)
""",
"""        val windowPackageName = currentWindowPackageName()
        if (!allowPopupCandidate && !shouldScanPackage(windowPackageName)) return
        if (allowPopupCandidate && isPassiveDiagnosticPackage(windowPackageName)) {
            traceEvent("popup.candidate ignored reason=passive_window package=${dollar}{windowPackageName.orEmpty()} raw_length=${dollar}{text.length}")
            return
        }
        val packageName = resolveRidePackageForText(windowPackageName, text, allowPopupCandidate)
""",
        )

        text = text.replace(
            "return \"Pacote passivo ignorado sem apagar a ultima decisao: ${dollar}normalized.\"",
            "return \"Pacote passivo; leitura cancelada e bolinha cinza: ${dollar}normalized.\"",
        )

        if (text != original) {
            file.writeText(text)
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchLiveWindowStaleOcr)
}
