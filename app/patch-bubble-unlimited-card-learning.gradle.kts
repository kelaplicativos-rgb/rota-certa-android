val bubbleUnlimitedCardLearning by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        patchUnlimitedCardLearning(serviceFile.asFile)
    }
}

fun patchUnlimitedCardLearning(file: java.io.File) {
    var text = file.readText()
    val original = text

    fun replaceExact(target: String, replacement: String) {
        text = text.replace(target, replacement)
    }

    if ("lastCardSaveCandidateText" !in text) {
        replaceExact(
"""    private var lastAccessibilityText: String = ""
    private var lastOcrText: String = ""
""",
"""    private var lastAccessibilityText: String = ""
    private var lastOcrText: String = ""
    private var lastCardSaveCandidatePackageName: String? = null
    private var lastCardSaveCandidateText: String = ""
    private var lastCardSaveCandidateAtMillis: Long = 0L
""",
        )
    }

    replaceExact(
"""        if (eventPackageName != null) {
            activePackageName = if (isPassiveDiagnosticPackage(eventPackageName)) null else eventPackageName
        }
""",
"""        if (eventPackageName != null && isLearnableRideAppPackage(eventPackageName)) {
            activePackageName = eventPackageName
        }
""",
    )

    val resolveRegex = Regex(
        """    private fun resolveRidePackageForText\([\s\S]*?\n    private fun looksLikeRegisteredPopupCandidate""",
    )
    text = resolveRegex.replace(text) {
"""    private fun resolveRidePackageForText(
        windowPackageName: String?,
        text: String,
        allowPopupCandidate: Boolean,
    ): String? {
        val normalizedWindowPackage = normalizePackageName(windowPackageName)
        if (shouldScanPackage(normalizedWindowPackage)) return normalizedWindowPackage
        if (!allowPopupCandidate) return normalizedWindowPackage
        if (isLearnableRideAppPackage(normalizedWindowPackage)) return normalizedWindowPackage
        activePackageName
            ?.takeIf { isLearnableRideAppPackage(it) }
            ?.let { return it }
        RegisteredRidePackagePolicy.packagesFromTemplates(currentCardTemplates)
            .firstOrNull { registeredPackage ->
                RideCardTemplateMatcher.match(text, registeredPackage, currentCardTemplates) != null
            }
            ?.let { return it }
        return RideCardTemplateMatcher.inferPackageName(text)
            ?.takeIf { inferred -> isLearnableRideAppPackage(inferred) || shouldScanPackage(inferred) }
    }

    private fun looksLikeRegisteredPopupCandidate"""
    }

    val saveRegex = Regex(
        """    private fun saveCurrentRideCardFromBubble\(\) \{[\s\S]*?\n    private fun clearRememberedRideText\(\) \{""",
    )
    text = saveRegex.replace(text) {
"""    private fun saveCurrentRideCardFromBubble() {
        hideActionMenu()
        snapshotCurrentCardCandidateForBubbleAction("save_card_click")
        val livePackageName = normalizePackageName(currentWindowPackageName() ?: activePackageName)
            ?.takeIf { isLearnableRideAppPackage(it) }
        val liveText = mergeRideTexts(lastAccessibilityText, lastOcrText)
            .takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
            ?: collectVisibleTextForAction().takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
            ?: ""
        val candidate = bestCardSaveCandidate(livePackageName, liveText)
        if (candidate != null) {
            scope.launch { saveRideCardTemplateFromBubble(candidate.first, candidate.second, "cached_text") }
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            toast("Abra o card de corrida e tente salvar novamente.")
            recordDiagnostic(
                stage = "bubble_save_card_no_screenshot_support",
                color = currentRadarColor,
                reason = "Android sem suporte ao print pela acessibilidade e nenhum texto de card estava em cache.",
            )
            return
        }
        toast("Capturando print do card...")
        captureCardScreenshotForSaving()
    }

    private fun captureCardScreenshotForSaving() {
        scope.launch {
            var acquired = false
            var attempts = 0
            while (!acquired && attempts < 4) {
                acquired = screenshotInProgress.compareAndSet(false, true)
                if (!acquired) delay(120L)
                attempts += 1
            }
            if (!acquired) {
                toast("Aguarde um instante e tente salvar novamente.")
                recordDiagnostic(
                    stage = "bubble_save_card_screenshot_busy",
                    color = currentRadarColor,
                    reason = "Nao salvei o card porque outro print ainda estava em andamento.",
                )
                return@launch
            }

            val bubbleView = overlayView
            val previousVisibility = bubbleView?.visibility ?: View.VISIBLE
            bubbleView?.visibility = View.INVISIBLE
            hideActionMenu()
            delay(220L)
            traceEvent("card_save_screenshot.request started")
            runCatching {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            scope.launch {
                                runCatching {
                                    val bitmap = screenshot.toSoftwareBitmap()
                                    val ocrText = bitmap?.let { ocrService.extractText(it) }.orEmpty().trim()
                                    traceEvent("card_save_screenshot.ocr length=${'$'}{ocrText.length}")
                                    val packageName = normalizePackageName(currentWindowPackageName() ?: activePackageName)
                                        ?.takeIf { isLearnableRideAppPackage(it) }
                                        ?: RideCardTemplateMatcher.inferPackageName(ocrText)
                                        ?: lastCardSaveCandidatePackageName
                                        ?: LEARNED_POPUP_PACKAGE
                                    saveRideCardTemplateFromBubble(packageName, ocrText, "screenshot_ocr")
                                }.onFailure { error ->
                                    traceEvent("card_save_screenshot.ocr error=${'$'}{error::class.java.simpleName}: ${'$'}{error.message.orEmpty()}")
                                    toast("Nao consegui ler o print do card.")
                                    recordDiagnostic(
                                        stage = "bubble_save_card_screenshot_ocr_error",
                                        color = currentRadarColor,
                                        reason = "Falha no OCR do print usado para salvar card.",
                                        error = error,
                                    )
                                }
                                bubbleView?.visibility = previousVisibility
                                screenshotInProgress.set(false)
                                showOverlay(currentRadarColor, currentDistanceKm)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            traceEvent("card_save_screenshot.request failed code=${'$'}errorCode")
                            bubbleView?.visibility = previousVisibility
                            screenshotInProgress.set(false)
                            showOverlay(currentRadarColor, currentDistanceKm)
                            toast("Android recusou o print do card.")
                            recordDiagnostic(
                                stage = "bubble_save_card_screenshot_failed",
                                color = currentRadarColor,
                                reason = "Android recusou o print para salvar card. Codigo: ${'$'}errorCode.",
                            )
                        }
                    },
                )
            }.onFailure { error ->
                traceEvent("card_save_screenshot.request error=${'$'}{error::class.java.simpleName}: ${'$'}{error.message.orEmpty()}")
                bubbleView?.visibility = previousVisibility
                screenshotInProgress.set(false)
                showOverlay(currentRadarColor, currentDistanceKm)
                toast("Nao consegui tirar print do card.")
                recordDiagnostic(
                    stage = "bubble_save_card_screenshot_request_error",
                    color = currentRadarColor,
                    reason = "Erro ao solicitar print para salvar card.",
                    error = error,
                )
            }
        }
    }

    private suspend fun saveRideCardTemplateFromBubble(rawPackageName: String?, rawText: String, source: String) {
        val text = rawText.trim()
        if (text.isBlank() || isBubbleActionMenuText(text)) {
            toast("Nao consegui capturar o texto do card. Tente enquanto a chamada ainda estiver visivel.")
            recordDiagnostic(
                stage = "bubble_save_card_empty",
                color = currentRadarColor,
                reason = "Nao havia texto util no card no momento do atalho. Fonte: ${'$'}source.",
                text = text,
            )
            return
        }
        val packageName = resolvePackageForCardSave(rawPackageName, text)
        val template = RideCardTemplateMatcher.createTemplate(packageName, text)
        repository.addCardTemplate(template)
        rememberCardSaveCandidate(packageName, text, "card_saved_${'$'}source")
        val parseResult = parser.parseWithMetadata(text, packageName)
        repository.addCapturedScreen(
            CapturedRideScreen(
                createdAtMillis = System.currentTimeMillis(),
                packageName = packageName,
                textHash = text.snapshotHash(),
                textPreview = text.take(DIAGNOSTIC_TEXT_LIMIT),
                parserName = parseResult.parserName,
                pickup = parseResult.fields.pickup,
                destination = parseResult.fields.destination,
                fare = parseResult.fields.fare,
            ),
        )
        toast("Card de corrida salvo.")
        recordDiagnostic(
            stage = "bubble_save_card",
            color = currentRadarColor,
            reason = "Card de corrida salvo pela bolinha via ${'$'}source: ${'$'}{template.name}. Pacote: ${'$'}packageName.",
            text = text,
            fields = parseResult.fields,
        )
    }

    private fun resolvePackageForCardSave(packageName: String?, text: String): String =
        normalizePackageName(packageName)
            ?.takeIf { isLearnableRideAppPackage(it) }
            ?: RideCardTemplateMatcher.inferPackageName(text)
            ?: LEARNED_POPUP_PACKAGE

    private fun snapshotCurrentCardCandidateForBubbleAction(reason: String) {
        val packageName = normalizePackageName(currentWindowPackageName() ?: activePackageName)
            ?.takeIf { isLearnableRideAppPackage(it) }
            ?: activePackageName?.takeIf { isLearnableRideAppPackage(it) }
        val actionText = mergeRideTexts(lastAccessibilityText, lastOcrText)
            .takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
            ?: collectVisibleTextForAction().takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
            ?: return
        rememberCardSaveCandidate(packageName, actionText, reason)
    }

    private fun rememberCardSaveCandidate(packageName: String?, text: String, reason: String) {
        val normalizedPackage = resolvePackageForCardSave(packageName, text)
        val cleanText = text.trim()
        if (cleanText.isBlank() || isBubbleActionMenuText(cleanText)) return
        lastCardSaveCandidatePackageName = normalizedPackage
        lastCardSaveCandidateText = cleanText
        lastCardSaveCandidateAtMillis = System.currentTimeMillis()
        traceEvent("card_save_candidate.remember reason=${'$'}reason package=${'$'}normalizedPackage length=${'$'}{cleanText.length}")
    }

    private fun bestCardSaveCandidate(packageName: String?, text: String): Pair<String, String>? {
        val cleanText = text.trim().takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
        val normalizedPackage = cleanText?.let { resolvePackageForCardSave(packageName, it) }
        if (normalizedPackage != null && cleanText != null) {
            return normalizedPackage to cleanText
        }
        val now = System.currentTimeMillis()
        val cachedPackage = lastCardSaveCandidatePackageName?.takeIf { it.isNotBlank() }
        val cachedText = lastCardSaveCandidateText.takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
        if (cachedPackage != null && cachedText != null && now - lastCardSaveCandidateAtMillis <= 15_000L) {
            traceEvent("card_save_candidate.use_cached package=${'$'}cachedPackage age_ms=${'$'}{now - lastCardSaveCandidateAtMillis}")
            return cachedPackage to cachedText
        }
        return null
    }

    private fun isBubbleActionMenuText(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
        return listOf(
            "abrir rota certa",
            "salvar card de corrida",
            "salvar este local",
            "criar alerta de proximidade",
        ).any { normalized.contains(it) }
    }

    private fun isLearnableRideAppPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        if (normalized == this.packageName) return false
        if (normalized == "android") return false
        if (normalized in PASSIVE_DIAGNOSTIC_PACKAGES) return false
        if (normalized in IGNORED_PACKAGES) return false
        if (normalized.startsWith("com.android.")) return false
        if (normalized.startsWith("com.google.android.inputmethod")) return false
        if (normalized.startsWith("com.samsung.android.biometrics")) return false
        if (normalized == "com.samsung.android.app.aodservice") return false
        if (normalized == "com.samsung.android.systemui") return false
        return true
    }

    private fun clearRememberedRideText() {"""
    }

    replaceExact(
"""    private fun showActionMenu() {
        val manager = windowManager ?: return
""",
"""    private fun showActionMenu() {
        snapshotCurrentCardCandidateForBubbleAction("menu_open")
        val manager = windowManager ?: return
""",
    )

    if ("const val LEARNED_POPUP_PACKAGE" !in text) {
        replaceExact(
"""        const val PACKAGE_INDRIVE_DRIVER = "sinet.startup.indriver"
""",
"""        const val PACKAGE_INDRIVE_DRIVER = "sinet.startup.indriver"
        const val LEARNED_POPUP_PACKAGE = "br.com.mapeiaia.rotacerta.learned.popup"
""",
        )
    }

    if (text != original) file.writeText(text)
}

tasks.named("bubbleUnlimitedCardLearning").configure {
    mustRunAfter("enforceUserRegisteredPackagesOnly")
    mustRunAfter("bubbleStateMachineIntegration")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(bubbleUnlimitedCardLearning)
}
