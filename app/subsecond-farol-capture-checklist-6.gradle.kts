// Checklist 6 — caminho crítico subsegundo e capturas fora da decisão.
// Executa por último, depois de todos os patches históricos e dos checklists anteriores.

fun replaceFunctionChecklist6(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Função ausente no checklist 6: $signature")
    val open = source.indexOf('{', start)
    if (open < 0) throw GradleException("Corpo ausente no checklist 6: $signature")
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
    throw GradleException("Fim da função ausente no checklist 6: $signature")
}

fun insertCandidateCaptureChecklist6(
    source: String,
    conditionToken: String,
    endToken: String,
    marker: String,
): String {
    if (marker in source) return source
    val start = source.indexOf(conditionToken)
    val end = if (start >= 0) source.indexOf(endToken, start) else -1
    if (start < 0 || end < 0) throw GradleException("Portaria manual ausente para $marker")
    var region = source.substring(start, end)
    val returnIndex = region.lastIndexOf("            return")
    if (returnIndex < 0) throw GradleException("Retorno da portaria manual ausente para $marker")
    val insertion = """            scheduleCandidateCaptureChecklist6(
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

fun patchSubsecondFarolCaptureChecklist6(serviceFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no checklist 6.")
    var service = serviceFile.readText()

    if ("import kotlinx.coroutines.withContext" !in service) {
        val anchor = "import kotlinx.coroutines.launch\n"
        if (anchor !in service) throw GradleException("Import de corrotina ausente no checklist 6.")
        service = service.replaceFirst(anchor, anchor + "import kotlinx.coroutines.withContext\n")
    }

    if ("subsecond_capture_fields_checklist_6" !in service) {
        val anchor = "    private var lastAutomaticCaptureRequestedAt129: Long = 0L"
        val lineEnd = service.indexOf('\n', service.indexOf(anchor))
        if (lineEnd < 0) throw GradleException("Campos da captura 0.1.129 ausentes no checklist 6.")
        val fields = """
    private var deferredCandidateCaptureJobChecklist6: Job? = null
    private var deferredMatchedCaptureJobChecklist6: Job? = null
    private var pendingMatchedCaptureChecklist6: DeferredAutomaticRideCaptureChecklist6? = null
    private var lastCandidateCaptureSignatureChecklist6: String? = null
    private var farolCriticalStartedAtChecklist6: Long = 0L // subsecond_capture_fields_checklist_6
"""
        service = service.substring(0, lineEnd + 1) + fields + service.substring(lineEnd + 1)
    }

    // A captura reconhecida deixa de iniciar junto com a rota. Apenas guardamos
    // metadados leves; o screenshot será liberado depois que cor/km forem pintados.
    val immediateCapture = """            requestAutomaticRideCapture129(
                snapshotText = snapshotText,
                packageName = selectedPackageForCard,
                fields = RideFields(pickup = trigger.pickup, destination = trigger.destination),
                cardSignature = cardDecisionSignature,
            ) // automatic_capture_after_manual_match_0_1_129
"""
    if (immediateCapture in service) {
        service = service.replaceFirst(
            immediateCapture,
            """            farolCriticalStartedAtChecklist6 = System.currentTimeMillis()
            pendingMatchedCaptureChecklist6 = DeferredAutomaticRideCaptureChecklist6(
                snapshotText = snapshotText,
                packageName = selectedPackageForCard,
                fields = RideFields(pickup = trigger.pickup, destination = trigger.destination),
                cardSignature = cardDecisionSignature,
                screenHash = analysisHash,
                generation = universalScreenGeneration + 1L,
                kind = AutomaticRideCaptureKind.Matched,
                matchedTemplateId = manualCardMatch.template.id,
                matchedTemplateName = manualCardMatch.template.name,
            ) // automatic_capture_deferred_until_farol_checklist_6
""",
        )
    }
    if ("automatic_capture_deferred_until_farol_checklist_6" !in service) {
        throw GradleException("Captura imediata não foi retirada do caminho da rota.")
    }

    service = insertCandidateCaptureChecklist6(
        source = service,
        conditionToken = "        if (packageCardTemplates.isEmpty()) {",
        endToken = "        val manualCardMatch =",
        marker = "candidate_capture_no_template_checklist_6",
    )
    service = insertCandidateCaptureChecklist6(
        source = service,
        conditionToken = "        if (manualCardMatch == null) {",
        endToken = "        registeredCardGate.markSeen()",
        marker = "candidate_capture_no_match_checklist_6",
    )

    if ("subsecond_capture_helpers_checklist_6" !in service) {
        val anchor = "    private fun requestAutomaticRideCapture129("
        val index = service.indexOf(anchor)
        if (index < 0) throw GradleException("Helper da captura automática ausente no checklist 6.")
        val helpers = """    private fun scheduleCandidateCaptureChecklist6(request: DeferredAutomaticRideCaptureChecklist6) {
        if (request.cardSignature == lastCandidateCaptureSignatureChecklist6) return
        lastCandidateCaptureSignatureChecklist6 = request.cardSignature
        deferredCandidateCaptureJobChecklist6?.cancel()
        deferredCandidateCaptureJobChecklist6 = scope.launch {
            delay(FarolCriticalPathPolicy.CANDIDATE_CAPTURE_DELAY_MILLIS)
            if (request.screenHash != lastSnapshotHash || request.generation != universalScreenGeneration) return@launch
            requestAutomaticRideCapture129(request)
        }
    }

    private fun releaseMatchedCaptureAfterFarolChecklist6(
        screenHash: Int,
        addressSignature: String,
        generation: Long,
    ) {
        val request = pendingMatchedCaptureChecklist6 ?: return
        if (request.screenHash != screenHash || request.generation != generation || request.cardSignature != addressSignature) return
        pendingMatchedCaptureChecklist6 = null
        deferredMatchedCaptureJobChecklist6?.cancel()
        deferredMatchedCaptureJobChecklist6 = scope.launch {
            delay(FarolCriticalPathPolicy.MATCHED_CAPTURE_DELAY_MILLIS)
            if (screenHash != lastSnapshotHash || generation != universalScreenGeneration) return@launch
            requestAutomaticRideCapture129(request)
        }
    }

    // subsecond_capture_helpers_checklist_6

"""
        service = service.substring(0, index) + helpers + service.substring(index)
    }

    val captureReplacement = """    private fun requestAutomaticRideCapture129(request: DeferredAutomaticRideCaptureChecklist6) {
        val packageName = normalizePackageName(request.packageName) ?: return
        if (!shouldScanPackage(packageName) || normalizePackageName(currentRootPackageName()) != packageName) return
        if (!automaticRideCaptureStore129.isEnabled() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (request.screenHash != lastSnapshotHash || request.generation != universalScreenGeneration) return

        scope.launch {
            var attemptChecklist6 = 0
            while (
                (screenshotInProgress.get() || universalRouteJob?.isActive == true || analyzing) &&
                attemptChecklist6 < FarolCriticalPathPolicy.CAPTURE_BUSY_RETRIES
            ) {
                delay(FarolCriticalPathPolicy.CAPTURE_BUSY_RETRY_MILLIS)
                attemptChecklist6 += 1
            }
            val canCaptureChecklist6 = FarolCriticalPathPolicy.canStartDeferredCapture(
                serviceReady = serviceReady,
                packageStillSelected = shouldScanPackage(packageName),
                sameRootPackage = normalizePackageName(currentRootPackageName()) == packageName,
                routeRunning = universalRouteJob?.isActive == true || analyzing,
                normalScreenshotRunning = screenshotInProgress.get(),
                automaticCaptureRunning = automaticCaptureInProgress129.get(),
            )
            if (!canCaptureChecklist6 || !automaticCaptureInProgress129.compareAndSet(false, true)) return@launch

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
                            val bitmapChecklist6 = screenshot.toSoftwareBitmap()
                            if (bitmapChecklist6 == null) {
                                automaticCaptureInProgress129.set(false)
                                return
                            }
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    automaticRideCaptureStore129.saveCard(
                                        bitmap = bitmapChecklist6,
                                        packageName = packageName,
                                        text = request.snapshotText,
                                        fields = request.fields,
                                        kind = request.kind,
                                        matchedTemplateId = request.matchedTemplateId,
                                        matchedTemplateName = request.matchedTemplateName,
                                    )
                                }
                                bitmapChecklist6.recycle()
                                automaticCaptureInProgress129.set(false)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            @Suppress("UNUSED_VARIABLE") val ignoredChecklist6 = errorCode
                            automaticCaptureInProgress129.set(false)
                        }
                    },
                )
            }.onFailure {
                automaticCaptureInProgress129.set(false)
            }
        }
    } // automatic_capture_low_priority_checklist_6
"""
    service = replaceFunctionChecklist6(
        source = service,
        signature = "    private fun requestAutomaticRideCapture129(",
        replacement = captureReplacement,
    )

    // Parser e comparação de modelo são puros e saem da thread que desenha a bolinha.
    val processStart = service.indexOf("    private suspend fun processRideText(")
    val processEnd = if (processStart >= 0) service.indexOf("    private fun resolveRidePackageForText(", processStart) else -1
    if (processStart < 0 || processEnd < 0) throw GradleException("Processamento do card ausente no checklist 6.")
    var processRegion = service.substring(processStart, processEnd)
    processRegion = processRegion.replaceFirst(
        "        val trigger = UniversalAddressTrigger.evaluate(snapshotText)\n",
        "        val trigger = withContext(Dispatchers.Default) { UniversalAddressTrigger.evaluate(snapshotText) } // trigger_off_main_checklist_6\n",
    )
    val matcherCall = """        val manualCardMatch = RideCardTemplateMatcher.match(
            text = snapshotText,
            packageName = selectedPackageForCard,
            templates = packageCardTemplates,
        )
"""
    if (matcherCall in processRegion) {
        processRegion = processRegion.replaceFirst(
            matcherCall,
            """        val manualCardMatch = withContext(Dispatchers.Default) {
            RideCardTemplateMatcher.match(
                text = snapshotText,
                packageName = selectedPackageForCard,
                templates = packageCardTemplates,
            )
        } // matcher_off_main_checklist_6
""",
        )
    }
    service = service.substring(0, processStart) + processRegion + service.substring(processEnd)

    // Um resultado exato deve aparecer antes de qualquer gravação de histórico.
    val applyStart = service.indexOf("    private suspend fun applyUniversalTwoAddressResult(")
    val applyEnd = if (applyStart >= 0) service.indexOf("    private fun isUniversalResultFresh(", applyStart) else -1
    if (applyStart < 0 || applyEnd < 0) throw GradleException("Aplicação do resultado ausente no checklist 6.")
    var applyRegion = service.substring(applyStart, applyEnd)
    val slowOrder = """        lastAnalyzedHash = screenHash
        repository.addAnalysis(result)
        showOverlay(color, result.nearestConfiguredDistanceKm())
"""
    if (slowOrder !in applyRegion) throw GradleException("Ordem antiga de histórico/overlay não encontrada.")
    applyRegion = applyRegion.replaceFirst(
        slowOrder,
        """        lastAnalyzedHash = screenHash
        showOverlay(color, result.nearestConfiguredDistanceKm()) // farol_painted_before_storage_checklist_6
        releaseMatchedCaptureAfterFarolChecklist6(screenHash, addressSignature, generation)
        scope.launch(Dispatchers.IO) { runCatching { repository.addAnalysis(result) } }
""",
    )
    service = service.substring(0, applyStart) + applyRegion + service.substring(applyEnd)

    // OCR é somente fallback. Se a acessibilidade aceitou o card depois do pedido
    // de screenshot, evitamos conversão de bitmap e ML Kit completamente.
    service = service.replace(
        "delay(90L)",
        "delay(FarolCriticalPathPolicy.OCR_FALLBACK_DELAY_MILLIS) // subsecond_ocr_delay_checklist_6",
    )
    val screenshotStart = service.indexOf("    private fun requestScreenshotAnalysis(")
    val screenshotEnd = if (screenshotStart >= 0) service.indexOf("    private fun collectVisibleText(", screenshotStart) else -1
    if (screenshotStart < 0 || screenshotEnd < 0) throw GradleException("OCR normal ausente no checklist 6.")
    var screenshotRegion = service.substring(screenshotStart, screenshotEnd)
    if ("ocr_skipped_after_accessibility_checklist_6" !in screenshotRegion) {
        val bitmapAnchor = "                                    val bitmap = screenshot.toSoftwareBitmap() ?: return@runCatching\n"
        if (bitmapAnchor !in screenshotRegion) throw GradleException("Conversão do screenshot OCR ausente.")
        screenshotRegion = screenshotRegion.replaceFirst(
            bitmapAnchor,
            """                                    if (FarolCriticalPathPolicy.shouldSkipOcr(
                                        screenshotRequestedAtMillis = lastScreenshotMillis,
                                        accessibilityAcceptedAtMillis = lastAccessibilityAcceptedAtMillis127,
                                    )) return@runCatching // ocr_skipped_after_accessibility_checklist_6
$bitmapAnchor""",
        )
    }
    val screenshotBodyAnchor = screenshotRegion.indexOf('{')
    if (screenshotBodyAnchor >= 0 && "ocr_blocked_during_route_checklist_6" !in screenshotRegion) {
        screenshotRegion = screenshotRegion.substring(0, screenshotBodyAnchor + 1) +
            "\n        if (universalRouteJob?.isActive == true || lastAnalyzedHash == lastSnapshotHash) return // ocr_blocked_during_route_checklist_6" +
            screenshotRegion.substring(screenshotBodyAnchor + 1)
    }
    service = service.substring(0, screenshotStart) + screenshotRegion + service.substring(screenshotEnd)

    // O ciclo de segurança também agenda o mesmo fallback, nunca OCR imediato.
    val scanStart = service.indexOf("    private fun startContinuousScan() {")
    val scanEnd = if (scanStart >= 0) service.indexOf("    private fun startProximityAlertMonitor()", scanStart) else -1
    if (scanStart < 0 || scanEnd < 0) throw GradleException("Ciclo de segurança ausente no checklist 6.")
    var scanRegion = service.substring(scanStart, scanEnd)
    scanRegion = scanRegion
        .replace("requestScreenshotAnalysis(allowPopupCandidate = true)", "strictSelectedRootPackageChecklist1()?.let(::scheduleScreenshotFallback127)")
        .replace("requestScreenshotAnalysis()", "strictSelectedRootPackageChecklist1()?.let(::scheduleScreenshotFallback127)")
    service = service.substring(0, scanStart) + scanRegion + service.substring(scanEnd)

    if ("subsecond_capture_destroy_checklist_6" !in service) {
        val destroyStart = service.indexOf("    override fun onDestroy() {")
        val destroyBrace = if (destroyStart >= 0) service.indexOf('{', destroyStart) else -1
        if (destroyBrace < 0) throw GradleException("onDestroy ausente no checklist 6.")
        val cleanup = """
        deferredCandidateCaptureJobChecklist6?.cancel()
        deferredMatchedCaptureJobChecklist6?.cancel()
        pendingMatchedCaptureChecklist6 = null // subsecond_capture_destroy_checklist_6
"""
        service = service.substring(0, destroyBrace + 1) + cleanup + service.substring(destroyBrace + 1)
    }

    listOf(
        "subsecond_capture_fields_checklist_6",
        "automatic_capture_deferred_until_farol_checklist_6",
        "candidate_capture_no_template_checklist_6",
        "candidate_capture_no_match_checklist_6",
        "subsecond_capture_helpers_checklist_6",
        "automatic_capture_low_priority_checklist_6",
        "trigger_off_main_checklist_6",
        "matcher_off_main_checklist_6",
        "farol_painted_before_storage_checklist_6",
        "ocr_skipped_after_accessibility_checklist_6",
        "ocr_blocked_during_route_checklist_6",
        "subsecond_capture_destroy_checklist_6",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Contrato subsegundo ausente: $marker")
    }
    if ("requestAutomaticRideCapture129(\n                snapshotText" in service) {
        throw GradleException("Captura imediata ainda está no caminho da rota.")
    }

    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchSubsecondFarolCaptureChecklist6(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
