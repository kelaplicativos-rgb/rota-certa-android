// Checklist 6 final — farol primeiro; OCR, histórico e captura depois.

fun replaceFunctionFinalChecklist6(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Função ausente no checklist 6 final: $signature")
    val open = source.indexOf('{', start)
    if (open < 0) throw GradleException("Corpo ausente no checklist 6 final: $signature")
    var depth = 0
    var index = open
    while (index < source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) {
                    return source.substring(0, start) + replacement + source.substring(index + 1)
                }
            }
        }
        index += 1
    }
    throw GradleException("Fim da função ausente no checklist 6 final: $signature")
}

fun insertCandidateFinalChecklist6(
    source: String,
    startToken: String,
    endToken: String,
    marker: String,
): String {
    if (marker in source) return source
    val start = source.indexOf(startToken)
    val end = if (start >= 0) source.indexOf(endToken, start) else -1
    if (start < 0 || end < 0) throw GradleException("Bloco do candidato ausente: $marker")
    var region = source.substring(start, end)
    val returnIndex = region.lastIndexOf("            return")
    if (returnIndex < 0) throw GradleException("Retorno do candidato ausente: $marker")
    val insertion = """            scheduleCandidateCaptureFinalChecklist6(
                DeferredAutomaticRideCaptureChecklist6(
                    snapshotText = snapshotText,
                    packageName = selectedPackageForCard,
                    fields = RideFields(pickup = trigger.pickup, destination = trigger.destination),
                    cardSignature = selectedPackageForCard + "|" + trigger.addressSignature,
                    screenHash = trigger.screenHash,
                    generation = universalScreenGeneration,
                    kind = AutomaticRideCaptureKind.Candidate,
                ),
            ) // $marker
"""
    region = region.substring(0, returnIndex) + insertion + region.substring(returnIndex)
    return source.substring(0, start) + region + source.substring(end)
}

fun verifySubsecondMarkersFinalChecklist6(service: String) {
    listOf(
        "subsecond_fields_final_checklist_6",
        "automatic_capture_after_farol_final_checklist_6",
        "candidate_without_template_final_checklist_6",
        "candidate_without_match_final_checklist_6",
        "subsecond_capture_helpers_final_checklist_6",
        "low_priority_capture_final_checklist_6",
        "trigger_default_dispatcher_final_checklist_6",
        "matcher_default_dispatcher_final_checklist_6",
        "overlay_before_storage_final_checklist_6",
        "ocr_outside_critical_path_final_checklist_6",
        "accessibility_won_skip_ocr_final_checklist_6",
        "capture_cleanup_final_checklist_6",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Contrato final do checklist 6 ausente: $marker")
    }
    if ("requestAutomaticRideCapture129(\n                snapshotText" in service) {
        throw GradleException("Captura imediata ainda está no caminho crítico.")
    }
}

fun patchSubsecondFarolFinalChecklist6(serviceFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no checklist 6 final.")
    var service = serviceFile.readText()
    if ("low_priority_capture_final_checklist_6" in service) {
        verifySubsecondMarkersFinalChecklist6(service)
        return
    }

    if ("import kotlinx.coroutines.withContext" !in service) {
        val importAnchor = "import kotlinx.coroutines.launch\n"
        if (importAnchor !in service) throw GradleException("Import launch ausente no checklist 6 final.")
        service = service.replaceFirst(importAnchor, importAnchor + "import kotlinx.coroutines.withContext\n")
    }

    val anchor = "    private var lastAutomaticCaptureRequestedAt129: Long = 0L"
    val anchorIndex = service.indexOf(anchor)
    if (anchorIndex < 0) throw GradleException("Campos da captura 0.1.129 ausentes.")
    val lineEnd = service.indexOf('\n', anchorIndex).let { if (it < 0) service.length else it + 1 }
    val fields = """    private var deferredCandidateCaptureJobFinalChecklist6: Job? = null
    private var deferredMatchedCaptureJobFinalChecklist6: Job? = null
    private var pendingMatchedCaptureFinalChecklist6: DeferredAutomaticRideCaptureChecklist6? = null
    private var lastCandidateCaptureSignatureFinalChecklist6: String? = null
    private var farolCriticalStartedAtFinalChecklist6: Long = 0L // subsecond_fields_final_checklist_6
"""
    service = service.substring(0, lineEnd) + fields + service.substring(lineEnd)

    val immediateCapture = """            requestAutomaticRideCapture129(
                snapshotText = snapshotText,
                packageName = selectedPackageForCard,
                fields = RideFields(pickup = trigger.pickup, destination = trigger.destination),
                cardSignature = cardDecisionSignature,
            ) // automatic_capture_after_manual_match_0_1_129
"""
    if (immediateCapture !in service) throw GradleException("Disparo imediato da captura 0.1.129 não encontrado.")
    service = service.replaceFirst(
        immediateCapture,
        """            farolCriticalStartedAtFinalChecklist6 = System.currentTimeMillis()
            pendingMatchedCaptureFinalChecklist6 = DeferredAutomaticRideCaptureChecklist6(
                snapshotText = snapshotText,
                packageName = selectedPackageForCard,
                fields = RideFields(pickup = trigger.pickup, destination = trigger.destination),
                cardSignature = cardDecisionSignature,
                screenHash = analysisHash,
                generation = universalScreenGeneration + 1L,
                kind = AutomaticRideCaptureKind.Matched,
                matchedTemplateId = manualCardMatch.template.id,
                matchedTemplateName = manualCardMatch.template.name,
            ) // automatic_capture_after_farol_final_checklist_6
""",
    )

    service = insertCandidateFinalChecklist6(
        source = service,
        startToken = "        if (packageCardTemplates.isEmpty()) {",
        endToken = "        val manualCardMatch =",
        marker = "candidate_without_template_final_checklist_6",
    )
    service = insertCandidateFinalChecklist6(
        source = service,
        startToken = "        if (manualCardMatch == null) {",
        endToken = "        registeredCardGate.markSeen()",
        marker = "candidate_without_match_final_checklist_6",
    )

    val oldCaptureSignature = "    private fun requestAutomaticRideCapture129("
    val captureIndex = service.indexOf(oldCaptureSignature)
    if (captureIndex < 0) throw GradleException("Função de captura 0.1.129 ausente.")
    val helpers = """    private fun scheduleCandidateCaptureFinalChecklist6(request: DeferredAutomaticRideCaptureChecklist6) {
        if (request.cardSignature == lastCandidateCaptureSignatureFinalChecklist6) return
        lastCandidateCaptureSignatureFinalChecklist6 = request.cardSignature
        deferredCandidateCaptureJobFinalChecklist6?.cancel()
        deferredCandidateCaptureJobFinalChecklist6 = scope.launch {
            delay(FarolCriticalPathPolicy.CANDIDATE_CAPTURE_DELAY_MILLIS)
            if (request.screenHash != lastSnapshotHash || request.generation != universalScreenGeneration) {
                if (lastCandidateCaptureSignatureFinalChecklist6 == request.cardSignature) {
                    lastCandidateCaptureSignatureFinalChecklist6 = null
                }
                return@launch
            }
            requestAutomaticRideCapture129(request)
        }
    }

    private fun releaseMatchedCaptureFinalChecklist6(
        screenHash: Int,
        addressSignature: String,
        generation: Long,
    ) {
        val request = pendingMatchedCaptureFinalChecklist6 ?: return
        if (request.screenHash != screenHash || request.generation != generation || request.cardSignature != addressSignature) return
        pendingMatchedCaptureFinalChecklist6 = null
        deferredMatchedCaptureJobFinalChecklist6?.cancel()
        deferredMatchedCaptureJobFinalChecklist6 = scope.launch {
            delay(FarolCriticalPathPolicy.MATCHED_CAPTURE_DELAY_MILLIS)
            if (screenHash != lastSnapshotHash || generation != universalScreenGeneration) return@launch
            requestAutomaticRideCapture129(request)
        }
    }

    // subsecond_capture_helpers_final_checklist_6

"""
    service = service.substring(0, captureIndex) + helpers + service.substring(captureIndex)

    val captureReplacement = """    private fun requestAutomaticRideCapture129(request: DeferredAutomaticRideCaptureChecklist6) {
        val packageName = normalizePackageName(request.packageName) ?: return
        if (!shouldScanPackage(packageName) || normalizePackageName(currentRootPackageName()) != packageName) return
        if (!automaticRideCaptureStore129.isEnabled() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (request.screenHash != lastSnapshotHash || request.generation != universalScreenGeneration) return

        scope.launch {
            var attemptsFinalChecklist6 = 0
            while (
                (screenshotInProgress.get() || universalRouteJob?.isActive == true || analyzing) &&
                attemptsFinalChecklist6 < FarolCriticalPathPolicy.CAPTURE_BUSY_RETRIES
            ) {
                delay(FarolCriticalPathPolicy.CAPTURE_BUSY_RETRY_MILLIS)
                attemptsFinalChecklist6 += 1
            }
            val readyFinalChecklist6 = FarolCriticalPathPolicy.canStartDeferredCapture(
                serviceReady = serviceReady,
                packageStillSelected = shouldScanPackage(packageName),
                sameRootPackage = normalizePackageName(currentRootPackageName()) == packageName,
                routeRunning = universalRouteJob?.isActive == true || analyzing,
                normalScreenshotRunning = screenshotInProgress.get(),
                automaticCaptureRunning = automaticCaptureInProgress129.get(),
            )
            if (!readyFinalChecklist6 || !automaticCaptureInProgress129.compareAndSet(false, true)) return@launch

            runCatching {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            if (!shouldScanPackage(packageName) || normalizePackageName(currentRootPackageName()) != packageName) {
                                automaticCaptureInProgress129.set(false)
                                return
                            }
                            val bitmapFinalChecklist6 = screenshot.toSoftwareBitmap()
                            if (bitmapFinalChecklist6 == null) {
                                automaticCaptureInProgress129.set(false)
                                return
                            }
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    automaticRideCaptureStore129.saveCard(
                                        bitmap = bitmapFinalChecklist6,
                                        packageName = packageName,
                                        text = request.snapshotText,
                                        fields = request.fields,
                                        kind = request.kind,
                                        matchedTemplateId = request.matchedTemplateId,
                                        matchedTemplateName = request.matchedTemplateName,
                                    )
                                }
                                bitmapFinalChecklist6.recycle()
                                automaticCaptureInProgress129.set(false)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            @Suppress("UNUSED_VARIABLE") val ignoredFinalChecklist6 = errorCode
                            automaticCaptureInProgress129.set(false)
                        }
                    },
                )
            }.onFailure { automaticCaptureInProgress129.set(false) }
        }
    } // low_priority_capture_final_checklist_6
"""
    service = replaceFunctionFinalChecklist6(service, oldCaptureSignature, captureReplacement)

    val processStart = service.indexOf("    private suspend fun processRideText(")
    val processEnd = if (processStart >= 0) service.indexOf("    private fun resolveRidePackageForText(", processStart) else -1
    if (processStart < 0 || processEnd < 0) throw GradleException("processRideText ausente no checklist 6 final.")
    var processRegion = service.substring(processStart, processEnd)
    val triggerOld = "        val trigger = UniversalAddressTrigger.evaluate(snapshotText)\n"
    if (triggerOld !in processRegion) throw GradleException("Avaliação do card ausente no checklist 6 final.")
    processRegion = processRegion.replaceFirst(
        triggerOld,
        "        val trigger = withContext(Dispatchers.Default) { UniversalAddressTrigger.evaluate(snapshotText) } // trigger_default_dispatcher_final_checklist_6\n",
    )
    val matcherOld = """        val manualCardMatch = RideCardTemplateMatcher.match(
            text = snapshotText,
            packageName = selectedPackageForCard,
            templates = packageCardTemplates,
        )
"""
    if (matcherOld !in processRegion) throw GradleException("Matcher manual ausente no checklist 6 final.")
    processRegion = processRegion.replaceFirst(
        matcherOld,
        """        val manualCardMatch = withContext(Dispatchers.Default) {
            RideCardTemplateMatcher.match(
                text = snapshotText,
                packageName = selectedPackageForCard,
                templates = packageCardTemplates,
            )
        } // matcher_default_dispatcher_final_checklist_6
""",
    )
    service = service.substring(0, processStart) + processRegion + service.substring(processEnd)

    val applyStart = service.indexOf("    private suspend fun applyUniversalTwoAddressResult(")
    val applyEnd = if (applyStart >= 0) service.indexOf("    private fun isUniversalResultFresh(", applyStart) else -1
    if (applyStart < 0 || applyEnd < 0) throw GradleException("Aplicação do farol ausente no checklist 6 final.")
    var applyRegion = service.substring(applyStart, applyEnd)
    val oldOrder = """        lastAnalyzedHash = screenHash
        repository.addAnalysis(result)
        showOverlay(color, result.nearestConfiguredDistanceKm())
"""
    if (oldOrder !in applyRegion) throw GradleException("Histórico ainda não está na ordem esperada.")
    applyRegion = applyRegion.replaceFirst(
        oldOrder,
        """        lastAnalyzedHash = screenHash
        showOverlay(color, result.nearestConfiguredDistanceKm()) // overlay_before_storage_final_checklist_6
        releaseMatchedCaptureFinalChecklist6(screenHash, addressSignature, generation)
        scope.launch(Dispatchers.IO) { runCatching { repository.addAnalysis(result) } }
""",
    )
    service = service.substring(0, applyStart) + applyRegion + service.substring(applyEnd)

    service = service.replace(
        "delay(90L)",
        "delay(FarolCriticalPathPolicy.OCR_FALLBACK_DELAY_MILLIS) // ocr_delay_final_checklist_6",
    )
    val screenshotStart = service.indexOf("    private fun requestScreenshotAnalysis(")
    val screenshotEnd = if (screenshotStart >= 0) service.indexOf("    private fun collectVisibleText(", screenshotStart) else -1
    if (screenshotStart < 0 || screenshotEnd < 0) throw GradleException("requestScreenshotAnalysis ausente.")
    var screenshotRegion = service.substring(screenshotStart, screenshotEnd)
    val bodyOpen = screenshotRegion.indexOf('{')
    if (bodyOpen < 0) throw GradleException("Corpo do OCR ausente.")
    screenshotRegion = screenshotRegion.substring(0, bodyOpen + 1) +
        "\n        if (universalRouteJob?.isActive == true || (lastAnalyzedHash != null && lastAnalyzedHash == lastSnapshotHash)) return // ocr_outside_critical_path_final_checklist_6" +
        screenshotRegion.substring(bodyOpen + 1)

    val bitmapToken = "val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching"
    val bitmapIndex = screenshotRegion.indexOf(bitmapToken)
    if (bitmapIndex < 0) throw GradleException("Conversão do bitmap OCR ausente.")
    val bitmapLineStart = screenshotRegion.lastIndexOf('\n', bitmapIndex).let { if (it < 0) 0 else it + 1 }
    val indentation = screenshotRegion.substring(bitmapLineStart, bitmapIndex)
    val skip = """${indentation}if (FarolCriticalPathPolicy.shouldSkipOcr(
${indentation}    screenshotRequestedAtMillis = lastScreenshotMillis,
${indentation}    accessibilityAcceptedAtMillis = lastAccessibilityAcceptedAtMillis127,
${indentation})) return@runCatching // accessibility_won_skip_ocr_final_checklist_6
"""
    screenshotRegion = screenshotRegion.substring(0, bitmapLineStart) + skip + screenshotRegion.substring(bitmapLineStart)
    service = service.substring(0, screenshotStart) + screenshotRegion + service.substring(screenshotEnd)

    val scanStart = service.indexOf("    private fun startContinuousScan() {")
    val scanEnd = if (scanStart >= 0) service.indexOf("    private fun startProximityAlertMonitor()", scanStart) else -1
    if (scanStart < 0 || scanEnd < 0) throw GradleException("Ciclo de segurança ausente.")
    var scanRegion = service.substring(scanStart, scanEnd)
    scanRegion = scanRegion
        .replace("requestScreenshotAnalysis(allowPopupCandidate = true)", "strictSelectedRootPackageChecklist1()?.let(::scheduleScreenshotFallback127)")
        .replace("requestScreenshotAnalysis()", "strictSelectedRootPackageChecklist1()?.let(::scheduleScreenshotFallback127)")
    service = service.substring(0, scanStart) + scanRegion + service.substring(scanEnd)

    val destroyStart = service.indexOf("    override fun onDestroy() {")
    val destroyOpen = if (destroyStart >= 0) service.indexOf('{', destroyStart) else -1
    if (destroyOpen < 0) throw GradleException("onDestroy ausente no checklist 6 final.")
    val cleanup = """
        deferredCandidateCaptureJobFinalChecklist6?.cancel()
        deferredMatchedCaptureJobFinalChecklist6?.cancel()
        pendingMatchedCaptureFinalChecklist6 = null // capture_cleanup_final_checklist_6
"""
    service = service.substring(0, destroyOpen + 1) + cleanup + service.substring(destroyOpen + 1)

    verifySubsecondMarkersFinalChecklist6(service)
    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchSubsecondFarolFinalChecklist6(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
