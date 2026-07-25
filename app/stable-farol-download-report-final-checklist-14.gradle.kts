// Checklist 14 — mantém a decisão estável em leituras parciais e salva o relatório em Downloads.

fun replaceFunctionStable14(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Função ausente no checklist 14: $signature")
    val open = source.indexOf('{', start)
    if (open < 0) throw GradleException("Corpo ausente no checklist 14: $signature")
    var depth = 0
    var index = open
    while (index < source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return source.substring(0, start) + replacement + source.substring(index + 1)
            }
        }
        index += 1
    }
    throw GradleException("Fim da função ausente no checklist 14: $signature")
}

fun patchStableFarolService14(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no checklist 14.")
    var service = file.readText()
    if ("stable_farol_display_complete_checklist_14" in service) return

    val fieldAnchor = "    private var fastFarolStartedAtChecklist13: Long = 0L // simple_saved_app_fields_checklist_13\n"
    if (fieldAnchor !in service) throw GradleException("Campos do farol simples ausentes no checklist 14.")
    service = service.replaceFirst(
        fieldAnchor,
        fieldAnchor + """    private var lastStableFarolPackageChecklist14: String? = null
    private var lastStableFarolWindowIdChecklist14: Int? = null
    private var partialReadConfirmationJobChecklist14: Job? = null
""",
    )

    val helperAnchor = "    private suspend fun processRideText(\n"
    val helperIndex = service.indexOf(helperAnchor)
    if (helperIndex < 0) throw GradleException("processRideText ausente para helper 14.")
    val helpers = """    private fun stableWindowIdChecklist14(eventWindowId: Int): Int? =
        rootInActiveWindow?.windowId?.takeIf { it >= 0 }
            ?: eventWindowId.takeIf { it >= 0 }

    private fun schedulePartialReadConfirmationChecklist14(
        packageName: String,
        windowId: Int?,
    ) {
        partialReadConfirmationJobChecklist14?.cancel()
        partialReadConfirmationJobChecklist14 = scope.launch {
            delay(FarolDisplayStabilityPolicy.PARTIAL_ABSENCE_CONFIRM_MILLIS)
            val savedPackagesChecklist14 = SelectedRideAppStore.read(applicationContext)
            val currentPackageChecklist14 = strictSelectedRootPackageChecklist1()
                ?: normalizePackageName(universalResolvedForegroundPackage())
                    ?.takeIf { it in savedPackagesChecklist14 }
                ?: return@launch
            if (currentPackageChecklist14 != packageName) return@launch
            val currentWindowChecklist14 = rootInActiveWindow?.windowId?.takeIf { it >= 0 }
            if (windowId != null && currentWindowChecklist14 != null && windowId != currentWindowChecklist14) {
                hardClearUniversalTwoAddress(
                    reason = "A janela mudou; cor e quilometros removidos imediatamente.",
                    keepWaitingYellow = true,
                )
                return@launch
            }
            val confirmedTextChecklist14 = collectImmediateVisibleTextChecklist13()
            val confirmedEvaluationChecklist14 = withContext(Dispatchers.Default) {
                SimpleSavedAppFarolPolicy.evaluate(
                    packageName = packageName,
                    savedPackages = savedPackagesChecklist14,
                    text = confirmedTextChecklist14,
                )
            }
            if (confirmedEvaluationChecklist14.active) {
                processRideText(
                    confirmedTextChecklist14,
                    TextSource.Accessibility,
                    allowPopupCandidate = true,
                )
            } else {
                hardClearUniversalTwoAddress(
                    reason = "O card saiu da tela; cor e quilometros removidos.",
                    keepWaitingYellow = true,
                )
                scheduleScreenshotFallback127(packageName)
            }
        }
    } // partial_read_confirmation_checklist_14

"""
    service = service.substring(0, helperIndex) + helpers + service.substring(helperIndex)

    val onEventReplacement = """    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!serviceReady || event == null) return
        if (!currentSettings.appEnabled || !currentSettings.liveReadingEnabled) {
            hardClearUniversalTwoAddress("Leitura universal desligada.")
            return
        }
        if (!AccessibilityEventFloodGate.isRelevantEventType(event.eventType)) return

        val eventPackage = normalizePackageName(event.packageName?.toString())
        val rootPackage = currentRootPackageName()
        val candidatePackage = eventPackage ?: rootPackage
        val ownMainActivityEvent = UniversalWindowPackageResolver.isOwnMainActivityEvent(
            eventPackageName = candidatePackage,
            eventClassName = event.className?.toString(),
            eventType = event.eventType,
            ownPackageName = this.packageName,
            mainActivityClassName = MainActivity::class.java.name,
            windowStateChangedType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        )
        if (candidatePackage == this.packageName) {
            if (ownMainActivityEvent) {
                universalForegroundPackageName = this.packageName
                activePackageName = this.packageName
                hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
            }
            return
        }

        val savedPackagesChecklist14 = SelectedRideAppStore.read(applicationContext)
        var resolvedPackageChecklist14 = candidatePackage ?: lastExternalWindowPackageName ?: return
        SelectedRideOverlayWindowPolicy.resolve(
            rootPackageName = resolvedPackageChecklist14,
            lastSelectedPackageName = recentSelectedRidePackageChecklist11,
            lastSelectedAtMillis = recentSelectedRidePackageAtMillisChecklist11,
            selectedPackages = savedPackagesChecklist14,
            nowMillis = System.currentTimeMillis(),
        )?.let { resolvedPackageChecklist14 = it }

        if (resolvedPackageChecklist14 !in savedPackagesChecklist14 || !shouldScanPackage(resolvedPackageChecklist14)) {
            lastStableFarolPackageChecklist14 = null
            lastStableFarolWindowIdChecklist14 = null
            universalForegroundPackageName = resolvedPackageChecklist14
            activePackageName = resolvedPackageChecklist14
            lastExternalWindowPackageName = resolvedPackageChecklist14
            hardClearUniversalTwoAddress(scanBlockReason(resolvedPackageChecklist14))
            return
        }

        recentSelectedRidePackageChecklist11 = resolvedPackageChecklist14
        recentSelectedRidePackageAtMillisChecklist11 = System.currentTimeMillis()
        universalForegroundPackageName = resolvedPackageChecklist14
        activePackageName = resolvedPackageChecklist14
        lastExternalWindowPackageName = resolvedPackageChecklist14

        val currentWindowChecklist14 = stableWindowIdChecklist14(event.windowId)
        val immediateTextChecklist14 = collectImmediateVisibleTextChecklist13()
        val evaluationChecklist14 = SimpleSavedAppFarolPolicy.evaluate(
            packageName = resolvedPackageChecklist14,
            savedPackages = savedPackagesChecklist14,
            text = immediateTextChecklist14,
        )
        val stabilityActionChecklist14 = FarolDisplayStabilityPolicy.decide(
            previousPackageName = lastStableFarolPackageChecklist14,
            previousWindowId = lastStableFarolWindowIdChecklist14,
            activeAddressSignature = universalActiveAddressSignature,
            currentPackageName = resolvedPackageChecklist14,
            currentWindowId = currentWindowChecklist14,
            currentAddressSignature = evaluationChecklist14.addressSignature.takeIf { evaluationChecklist14.active },
            hasTwoAddresses = evaluationChecklist14.active,
            eventType = event.eventType,
        )

        when (stabilityActionChecklist14) {
            FarolDisplayStabilityPolicy.Action.ClearImmediately -> {
                partialReadConfirmationJobChecklist14?.cancel()
                partialReadConfirmationJobChecklist14 = null
                lastStableFarolPackageChecklist14 = resolvedPackageChecklist14
                lastStableFarolWindowIdChecklist14 = currentWindowChecklist14
                hardClearUniversalTwoAddress(
                    reason = "A tela mudou; cor e quilometros removidos imediatamente.",
                    keepWaitingYellow = true,
                ) // real_screen_change_clear_checklist_14
                scheduleScreenshotFallback127(resolvedPackageChecklist14)
                return
            }
            FarolDisplayStabilityPolicy.Action.ClearThenProcess -> {
                partialReadConfirmationJobChecklist14?.cancel()
                partialReadConfirmationJobChecklist14 = null
                hardClearUniversalTwoAddress(
                    reason = "Novo destino detectado; resultado anterior removido imediatamente.",
                    keepWaitingYellow = true,
                ) // destination_change_clear_checklist_14
            }
            FarolDisplayStabilityPolicy.Action.ConfirmAbsence -> {
                schedulePartialReadConfirmationChecklist14(
                    packageName = resolvedPackageChecklist14,
                    windowId = currentWindowChecklist14,
                )
                scheduleScreenshotFallback127(resolvedPackageChecklist14)
                return
            }
            FarolDisplayStabilityPolicy.Action.KeepCurrent -> {
                scheduleScreenshotFallback127(resolvedPackageChecklist14)
                return
            }
            FarolDisplayStabilityPolicy.Action.ProcessCurrent -> Unit
        }

        partialReadConfirmationJobChecklist14?.cancel()
        partialReadConfirmationJobChecklist14 = null
        lastStableFarolPackageChecklist14 = resolvedPackageChecklist14
        lastStableFarolWindowIdChecklist14 = currentWindowChecklist14
        analyzeJob?.cancel()
        analyzeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            processRideText(immediateTextChecklist14, TextSource.Accessibility, allowPopupCandidate = true)
        }
        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null
    } // stable_farol_event_contract_checklist_14
"""
    service = replaceFunctionStable14(
        service,
        "    override fun onAccessibilityEvent(event: AccessibilityEvent?)",
        onEventReplacement,
    )

    val processReplacement = """    private suspend fun processRideText(
        text: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
    ) {
        @Suppress("UNUSED_VARIABLE") val ignoredPopupCandidateChecklist14 = allowPopupCandidate
        if (bubbleGestureActive || !serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return
        val savedPackagesChecklist14 = SelectedRideAppStore.read(applicationContext)
        val selectedPackageChecklist14 = strictSelectedRootPackageChecklist1()
            ?: normalizePackageName(universalResolvedForegroundPackage())
                ?.takeIf { it in savedPackagesChecklist14 }
            ?: run {
                hardClearUniversalTwoAddress("Aplicativo nao ensinado; leitura e rota bloqueadas.")
                return
            }

        val snapshotTextChecklist14 = text.trim()
        val evaluationChecklist14 = withContext(Dispatchers.Default) {
            SimpleSavedAppFarolPolicy.evaluate(
                packageName = selectedPackageChecklist14,
                savedPackages = savedPackagesChecklist14,
                text = snapshotTextChecklist14,
            )
        }
        if (!evaluationChecklist14.active) {
            val hasVisibleDecisionChecklist14 = universalActiveAddressSignature != null &&
                (currentDistanceKm != null || currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red)
            if (hasVisibleDecisionChecklist14) {
                return // transient_partial_read_keeps_decision_checklist_14
            }
            hardClearUniversalTwoAddress(
                reason = "Tela sem dois enderecos validos.",
                keepWaitingYellow = true,
            )
            return
        }

        if (source == TextSource.Accessibility) {
            lastAccessibilityAcceptedAtMillis127 = System.currentTimeMillis()
            screenshotFallbackJob127?.cancel()
            screenshotFallbackJob127 = null
        }
        universalLastActiveReadAtMillis = System.currentTimeMillis()
        val fieldsChecklist14 = RideFields(
            pickup = evaluationChecklist14.pickup,
            destination = evaluationChecklist14.destination,
        )
        val cardChangedChecklist14 = universalActiveAddressSignature != evaluationChecklist14.addressSignature ||
            lastSnapshotHash != evaluationChecklist14.screenHash
        if (cardChangedChecklist14 && (
                universalActiveAddressSignature != null ||
                    currentDistanceKm != null ||
                    currentRadarColor == RadarColor.Green ||
                    currentRadarColor == RadarColor.Red
            )
        ) {
            hardClearUniversalTwoAddress(
                reason = "Novo endereco detectado; resultado anterior removido imediatamente.",
                keepWaitingYellow = true,
            )
        }

        universalActiveRidePackageName = selectedPackageChecklist14
        universalActiveAddressSignature = evaluationChecklist14.addressSignature
        lastSnapshotHash = evaluationChecklist14.screenHash
        manualActiveCardTemplateId127 = null
        registeredCardGate.markSeen()

        if (cardChangedChecklist14) {
            universalScreenGeneration += 1L
            universalRouteJob?.cancel()
            universalRouteJob = null
            lastAnalyzedHash = null
            currentDistanceKm = null
            fastFarolStartedAtChecklist13 = System.currentTimeMillis()
            bubblePrefs.edit()
                .putLong("fast_farol_started_at", fastFarolStartedAtChecklist13)
                .putString("fast_farol_last_destination", fieldsChecklist14.destination.orEmpty())
                .apply()
        } else if (lastAnalyzedHash == evaluationChecklist14.screenHash || universalRouteJob?.isActive == true) {
            return // same_destination_keeps_visible_result_checklist_14
        }

        val settingsChecklist14 = currentSettings
        val targetsChecklist14 = fastWorkRegionTargetsChecklist13(settingsChecklist14)
        if (targetsChecklist14.destinations.isEmpty()) {
            rememberBubbleReason("work_region_missing", "Configure Casa ou pelo menos um alfinete com coordenada validada.")
            showOverlay(RadarColor.Default, distanceKm = null)
            return
        }

        val cachedDistancesChecklist14 = googleMapsService.cachedDrivingDistancesFromAddressKm(
            originAddress = fieldsChecklist14.destination.orEmpty(),
            destinations = targetsChecklist14.destinations,
        )
        val generationChecklist14 = universalScreenGeneration
        if (cachedDistancesChecklist14 != null) {
            val cachedResultChecklist14 = decideFastWorkRegionChecklist13(
                snapshotText = snapshotTextChecklist14,
                fields = fieldsChecklist14,
                settings = settingsChecklist14,
                targets = targetsChecklist14,
                routeDistances = cachedDistancesChecklist14,
            )
            bubblePrefs.edit().putString("fast_farol_last_path", "cache_exato").apply()
            applyUniversalTwoAddressResult(
                cachedResultChecklist14,
                evaluationChecklist14.screenHash,
                evaluationChecklist14.addressSignature,
                generationChecklist14,
            ) // stable_exact_cache_checklist_14
            return
        }

        rememberBubbleReason("universal_waiting", "Dois enderecos identificados; calculando o ultimo destino.")
        showOverlay(RadarColor.Default, distanceKm = null)
        bubblePrefs.edit().putString("fast_farol_last_path", "rota_google").apply()
        universalRouteJob = scope.launch {
            analyzeUniversalTwoAddress(
                snapshotText = snapshotTextChecklist14,
                fields = fieldsChecklist14,
                screenHash = evaluationChecklist14.screenHash,
                addressSignature = evaluationChecklist14.addressSignature,
                generation = generationChecklist14,
            )
        }
    } // stable_farol_process_contract_checklist_14
"""
    service = replaceFunctionStable14(
        service,
        "    private suspend fun processRideText(",
        processReplacement,
    )

    val clearStart = service.indexOf("    private fun hardClearUniversalTwoAddress(")
    val clearBody = if (clearStart >= 0) service.indexOf('{', clearStart) else -1
    if (clearStart < 0 || clearBody < 0) throw GradleException("Limpeza final ausente no checklist 14.")
    service = service.substring(0, clearBody + 1) +
        "\n        partialReadConfirmationJobChecklist14?.cancel()\n        partialReadConfirmationJobChecklist14 = null" +
        service.substring(clearBody + 1)

    service += "\n// stable_farol_display_complete_checklist_14\n"
    file.writeText(service)
}

fun patchReportDownloadUi14(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt ausente no checklist 14.")
    var text = file.readText()
    text = text
        .replace("ManualTechnicalReportExporter.createAndShare(context, report)", "ManualTechnicalReportExporter.saveToDownloads(context, report)")
        .replace("Relatorio criado. Escolha onde compartilhar.", "Relatorio salvo em Downloads/Rota Certa.")
        .replace("Gerar e compartilhar relatorio", "Gerar e baixar relatorio")

    val oldFailure = """                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Rota Certa relatorio tecnico", report))
                        Toast.makeText(context, "Nao consegui abrir o compartilhamento. O relatorio foi copiado.", Toast.LENGTH_LONG).show()
"""
    val newFailure = """                        Toast.makeText(
                            context,
                            "Nao consegui salvar o relatorio em Downloads.",
                            Toast.LENGTH_LONG,
                        ).show()
"""
    if (oldFailure in text) text = text.replaceFirst(oldFailure, newFailure)
    if ("createAndShare(context, report)" in text || "Gerar e compartilhar relatorio" in text) {
        throw GradleException("Interface ainda abre compartilhamento no checklist 14.")
    }
    if ("saveToDownloads(context, report)" !in text || "Gerar e baixar relatorio" !in text) {
        throw GradleException("Download direto do relatorio nao foi aplicado.")
    }
    file.writeText(text)
}

fun patchStaleSessionReportCopy14(sourceRoot: java.io.File) {
    sourceRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .forEach { file ->
            var text = file.readText()
            val original = text
            text = text
                .replace(
                    "Politica de leitura: app escolhido + modelo correspondente + passageiro + pelo menos dois enderecos",
                    "Politica de leitura: aplicativo salvo + dois ou mais enderecos; o ultimo e o destino",
                )
                .replace(
                    "Vermelho rapido provisório continua calculando a rota exata para preencher o km",
                    "Cache exato reaplica verde/vermelho e km em milissegundos; rota nova usa Google Maps",
                )
                .replace(
                    "Vermelho rapido provisorio continua calculando a rota exata para preencher o km",
                    "Cache exato reaplica verde/vermelho e km em milissegundos; rota nova usa Google Maps",
                )
            if (text != original) file.writeText(text)
        }
}

fun patchStableFarolAndReport14(root: java.io.File) {
    patchStableFarolService14(java.io.File(root, "LiveRideAccessibilityService.kt"))
    patchReportDownloadUi14(java.io.File(root, "MainActivity.kt"))
    patchStaleSessionReportCopy14(root)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchStableFarolAndReport14(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    doFirst {
        patchStableFarolAndReport14(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}
