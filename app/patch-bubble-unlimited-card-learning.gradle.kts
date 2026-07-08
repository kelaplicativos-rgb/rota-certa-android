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
    private var cardSaveScreenshotRequestedUntilMillis: Long = 0L
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

    replaceExact(
"""        val packageName = resolveRidePackageForText(windowPackageName, text, allowPopupCandidate)
        if (!shouldScanPackage(packageName)) {
""",
"""        val packageName = resolveRidePackageForText(windowPackageName, text, allowPopupCandidate)
        if (allowPopupCandidate && text.isNotBlank() && !isBubbleActionMenuText(text)) {
            rememberCardSaveCandidate(packageName ?: activePackageName ?: RideCardTemplateMatcher.inferPackageName(text) ?: LEARNED_POPUP_PACKAGE, text, "popup_before_scan_gate")
        }
        if (!shouldScanPackage(packageName)) {
""",
    )

    replaceExact(
"""        return RideCardTemplateMatcher.inferPackageName(text)
            ?.takeIf { inferred -> shouldScanPackage(inferred) }
""",
"""        RegisteredRidePackagePolicy.packagesFromTemplates(currentCardTemplates)
            .firstOrNull { registeredPackage ->
                RideCardTemplateMatcher.match(text, registeredPackage, currentCardTemplates) != null
            }
            ?.let { return it }
        return RideCardTemplateMatcher.inferPackageName(text)
            ?.takeIf { inferred -> isLearnableRideAppPackage(inferred) || shouldScanPackage(inferred) }
""",
    )

    replaceExact(
"""            val packageName = normalizePackageName(currentWindowPackageName() ?: activePackageName)
            val text = mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank {
                collectVisibleTextForAction()
            }
""",
"""            traceEvent("bubble.save_card_start")
            snapshotCurrentCardCandidateForBubbleAction("save_card_click")
            val livePackageName = normalizePackageName(currentWindowPackageName() ?: activePackageName)
                ?.takeIf { isLearnableRideAppPackage(it) }
            val liveText = mergeRideTexts(lastAccessibilityText, lastOcrText)
                .takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
                ?: collectVisibleTextForAction().takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
                ?: ""
            var candidate = bestCardSaveCandidate(livePackageName, liveText)
            if (candidate == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                toast("Capturando print do card...")
                for (attempt in 0 until 3) {
                    cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 2_500L
                    lastScreenshotMillis = 0L
                    requestScreenshotAnalysis(allowPopupCandidate = true)
                    delay(700L)
                    candidate = bestCardSaveCandidate(null, "")
                    if (candidate != null) break
                }
            }
            candidate?.let { traceEvent("bubble.save_card_candidate.ready package=${'$'}{it.first} length=${'$'}{it.second.length}") }
            val packageName = candidate?.first
            val text = candidate?.second.orEmpty()
""",
    )

    replaceExact(
"""            val packageName = currentWindowPackageName() ?: activePackageName
            val text = mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank {
                collectVisibleTextForAction()
            }
""",
"""            traceEvent("bubble.save_card_start")
            snapshotCurrentCardCandidateForBubbleAction("save_card_click")
            val livePackageName = normalizePackageName(currentWindowPackageName() ?: activePackageName)
                ?.takeIf { isLearnableRideAppPackage(it) }
            val liveText = mergeRideTexts(lastAccessibilityText, lastOcrText)
                .takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
                ?: collectVisibleTextForAction().takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
                ?: ""
            var candidate = bestCardSaveCandidate(livePackageName, liveText)
            if (candidate == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                toast("Capturando print do card...")
                for (attempt in 0 until 3) {
                    cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 2_500L
                    lastScreenshotMillis = 0L
                    requestScreenshotAnalysis(allowPopupCandidate = true)
                    delay(700L)
                    candidate = bestCardSaveCandidate(null, "")
                    if (candidate != null) break
                }
            }
            candidate?.let { traceEvent("bubble.save_card_candidate.ready package=${'$'}{it.first} length=${'$'}{it.second.length}") }
            val packageName = candidate?.first
            val text = candidate?.second.orEmpty()
""",
    )

    replaceExact(
"""            if (packageName.isNullOrBlank() || packageName == this@LiveRideAccessibilityService.packageName || isPassiveDiagnosticPackage(packageName)) {
                toast("Abra o card dentro do app de corrida e salve novamente.")
""",
"""            if (packageName.isNullOrBlank()) {
                toast("Nao consegui identificar o pacote da tela para salvar.")
""",
    )

    replaceExact(
"""            val inferredPackage = packageName?.lowercase(Locale.ROOT)
                ?: RideCardTemplateMatcher.inferPackageName(text)
            val template = RideCardTemplateMatcher.createTemplate(inferredPackage, text)
""",
"""            if (packageName.isNullOrBlank()) {
                toast("Nao consegui identificar o pacote da tela para salvar.")
                recordDiagnostic(
                    stage = "bubble_save_card_missing_package",
                    color = currentRadarColor,
                    reason = "Card nao salvo: pacote da tela nao foi identificado.",
                    text = text,
                )
                return@launch
            }
            val inferredPackage = packageName
            val template = RideCardTemplateMatcher.createTemplate(inferredPackage, text)
""",
    )

    replaceExact(
"""            val inferredPackage = packageName
            val template = RideCardTemplateMatcher.createTemplate(inferredPackage, text)
""",
"""            val inferredPackage = packageName
            val template = RideCardTemplateMatcher.createTemplate(inferredPackage, text)
            rememberCardSaveCandidate(inferredPackage, text, "card_saved")
""",
    )

    replaceExact(
"""            addView(actionMenuItem("💾  Salvar card de corrida") {
                hideActionMenu()
                saveCurrentRideCardFromBubble()
            })
""",
"""            addView(actionMenuItem("💾  Salvar card de corrida") {
                traceEvent("bubble.save_card_button clicked")
                cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 5_000L
                hideActionMenu()
                saveCurrentRideCardFromBubble()
            })
""",
    )

    replaceExact(
"""    private fun showActionMenu() {
        val manager = windowManager ?: return
""",
"""    private fun showActionMenu() {
        snapshotCurrentCardCandidateForBubbleAction("menu_open")
        val manager = windowManager ?: return
""",
    )

    replaceExact(
"""            setOnClickListener { action() }
""",
"""            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        traceEvent("bubble.menu_item_down label=${'$'}{label.take(24)}")
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        traceEvent("bubble.menu_item_up label=${'$'}{label.take(24)}")
                        action()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> true
                    else -> true
                }
            }
""",
    )

    if ("bubble.long_press_save_card" !in text) {
        replaceExact(
"""        private var startY = 0
        private var moved = false
""",
"""        private var startY = 0
        private var downAtMillis = 0L
        private var moved = false
""",
        )
        replaceExact(
"""                    moved = false
                    return true
""",
"""                    downAtMillis = System.currentTimeMillis()
                    moved = false
                    return true
""",
        )
        replaceExact(
"""                    if (!moved) view.performClick()
                    return true
""",
"""                    if (!moved) {
                        val pressDuration = System.currentTimeMillis() - downAtMillis
                        if (pressDuration >= 650L) {
                            traceEvent("bubble.long_press_save_card")
                            cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 5_000L
                            hideActionMenu()
                            saveCurrentRideCardFromBubble()
                        } else {
                            view.performClick()
                        }
                    }
                    return true
""",
        )
    }

    if ("private fun snapshotCurrentCardCandidateForBubbleAction" !in text) {
        replaceExact(
"""    private fun collectVisibleTextForAction(): String {
""",
"""    private fun snapshotCurrentCardCandidateForBubbleAction(reason: String) {
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
        val cleanText = text.trim()
        if (cleanText.isBlank() || isBubbleActionMenuText(cleanText)) return
        val normalizedPackage = normalizePackageName(packageName)
            ?.takeIf { isLearnableRideAppPackage(it) }
            ?: activePackageName?.takeIf { isLearnableRideAppPackage(it) }
            ?: RideCardTemplateMatcher.inferPackageName(cleanText)
            ?: LEARNED_POPUP_PACKAGE
        lastCardSaveCandidatePackageName = normalizedPackage
        lastCardSaveCandidateText = cleanText
        lastCardSaveCandidateAtMillis = System.currentTimeMillis()
        traceEvent("card_save_candidate.remember reason=${'$'}reason package=${'$'}normalizedPackage length=${'$'}{cleanText.length}")
    }

    private fun bestCardSaveCandidate(packageName: String?, text: String): Pair<String, String>? {
        val cleanText = text.trim().takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
        val normalizedPackage = cleanText?.let {
            normalizePackageName(packageName)
                ?.takeIf { isLearnableRideAppPackage(it) }
                ?: activePackageName?.takeIf { isLearnableRideAppPackage(it) }
                ?: RideCardTemplateMatcher.inferPackageName(it)
                ?: LEARNED_POPUP_PACKAGE
        }
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

    private fun isCardSaveScreenshotRequested(): Boolean =
        System.currentTimeMillis() <= cardSaveScreenshotRequestedUntilMillis

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
        if (normalized.isBlank()) return false
        if (normalized == this.packageName) return false
        if (normalized == "android") return false
        if (normalized == "com.android.systemui") return false
        if (normalized == "com.samsung.android.systemui") return false
        if (normalized.startsWith("com.google.android.inputmethod")) return false
        if (normalized.startsWith("com.samsung.android.biometrics")) return false
        return true
    }

    private fun collectVisibleTextForAction(): String {
""",
        )
    }

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
    mustRunAfter("patchLiveRideBubbleActions")
    mustRunAfter("enforceUserRegisteredPackagesOnly")
    mustRunAfter("bubbleStateMachineIntegration")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(bubbleUnlimitedCardLearning)
}
