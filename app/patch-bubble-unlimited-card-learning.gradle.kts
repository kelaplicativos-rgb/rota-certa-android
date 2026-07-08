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
        if (allowPopupCandidate && text.isNotBlank() && looksLikeCardSaveText(text)) {
            rememberCardSaveCandidate(packageName ?: RideCardTemplateMatcher.inferPackageName(text) ?: LEARNED_POPUP_PACKAGE, text, "popup_before_scan_gate")
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
"""            snapshotCurrentCardCandidateForBubbleAction("save_card_click")
            val livePackageName = normalizePackageName(currentWindowPackageName() ?: activePackageName)
                ?.takeIf { isLearnableRideAppPackage(it) }
            val liveText = mergeRideTexts(lastAccessibilityText, lastOcrText)
                .takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) && looksLikeCardSaveText(it) }
                ?: collectVisibleTextForAction().takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) && looksLikeCardSaveText(it) }
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
"""            snapshotCurrentCardCandidateForBubbleAction("save_card_click")
            val livePackageName = normalizePackageName(currentWindowPackageName() ?: activePackageName)
                ?.takeIf { isLearnableRideAppPackage(it) }
            val liveText = mergeRideTexts(lastAccessibilityText, lastOcrText)
                .takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) && looksLikeCardSaveText(it) }
                ?: collectVisibleTextForAction().takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) && looksLikeCardSaveText(it) }
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
            val packageName = candidate?.first
            val text = candidate?.second.orEmpty()
""",
    )

    replaceExact(
"""            if (packageName.isNullOrBlank() || packageName == this@LiveRideAccessibilityService.packageName || isPassiveDiagnosticPackage(packageName)) {
                toast("Abra o card dentro do app de corrida e salve novamente.")
""",
"""            if (packageName.isNullOrBlank() || !isLearnableRideAppPackage(packageName)) {
                toast("Abra o card dentro do app de corrida e salve novamente.")
""",
    )

    replaceExact(
"""            val inferredPackage = packageName?.lowercase(Locale.ROOT)
                ?: RideCardTemplateMatcher.inferPackageName(text)
            val template = RideCardTemplateMatcher.createTemplate(inferredPackage, text)
""",
"""            if (packageName.isNullOrBlank() || !isLearnableRideAppPackage(packageName)) {
                toast("Abra o card dentro do app de corrida e salve novamente.")
                recordDiagnostic(
                    stage = "bubble_save_card_missing_package",
                    color = currentRadarColor,
                    reason = "Card nao salvo: pacote real do app de corrida nao foi identificado.",
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
"""    private fun showActionMenu() {
        val manager = windowManager ?: return
""",
"""    private fun showActionMenu() {
        snapshotCurrentCardCandidateForBubbleAction("menu_open")
        val manager = windowManager ?: return
""",
    )

    if ("private fun snapshotCurrentCardCandidateForBubbleAction" !in text) {
        replaceExact(
"""    private fun collectVisibleTextForAction(): String {
""",
"""    private fun snapshotCurrentCardCandidateForBubbleAction(reason: String) {
        val packageName = normalizePackageName(currentWindowPackageName() ?: activePackageName)
            ?.takeIf { isLearnableRideAppPackage(it) }
            ?: activePackageName?.takeIf { isLearnableRideAppPackage(it) }
        val actionText = mergeRideTexts(lastAccessibilityText, lastOcrText)
            .takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) && looksLikeCardSaveText(it) }
            ?: collectVisibleTextForAction().takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) && looksLikeCardSaveText(it) }
            ?: return
        rememberCardSaveCandidate(packageName, actionText, reason)
    }

    private fun rememberCardSaveCandidate(packageName: String?, text: String, reason: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank() || isBubbleActionMenuText(cleanText) || !looksLikeCardSaveText(cleanText)) return
        val normalizedPackage = normalizePackageName(packageName)
            ?.takeIf { isLearnableRideAppPackage(it) }
            ?: RideCardTemplateMatcher.inferPackageName(cleanText)
            ?: LEARNED_POPUP_PACKAGE
        lastCardSaveCandidatePackageName = normalizedPackage
        lastCardSaveCandidateText = cleanText
        lastCardSaveCandidateAtMillis = System.currentTimeMillis()
        traceEvent("card_save_candidate.remember reason=${'$'}reason package=${'$'}normalizedPackage length=${'$'}{cleanText.length}")
    }

    private fun bestCardSaveCandidate(packageName: String?, text: String): Pair<String, String>? {
        val cleanText = text.trim().takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) && looksLikeCardSaveText(it) }
        val normalizedPackage = cleanText?.let {
            normalizePackageName(packageName)
                ?.takeIf { isLearnableRideAppPackage(it) }
                ?: RideCardTemplateMatcher.inferPackageName(it)
                ?: LEARNED_POPUP_PACKAGE
        }
        if (normalizedPackage != null && cleanText != null) {
            return normalizedPackage to cleanText
        }
        val now = System.currentTimeMillis()
        val cachedPackage = lastCardSaveCandidatePackageName?.takeIf { it.isNotBlank() }
        val cachedText = lastCardSaveCandidateText.takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) && looksLikeCardSaveText(it) }
        if (cachedPackage != null && cachedText != null && now - lastCardSaveCandidateAtMillis <= 15_000L) {
            traceEvent("card_save_candidate.use_cached package=${'$'}cachedPackage age_ms=${'$'}{now - lastCardSaveCandidateAtMillis}")
            return cachedPackage to cachedText
        }
        return null
    }

    private fun isCardSaveScreenshotRequested(): Boolean =
        System.currentTimeMillis() <= cardSaveScreenshotRequestedUntilMillis

    private fun looksLikeCardSaveText(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
        if (normalized.isBlank() || isBubbleActionMenuText(normalized)) return false
        val hasMoney = Regex("""r\$\s*\d|\b\d+[,.]\d{2}\b""").containsMatchIn(normalized)
        val hasDistance = Regex("""\b\d+(?:[,.]\d+)?\s*km\b""").containsMatchIn(normalized)
        val hasRideWord = listOf(
            "corrida",
            "viagem",
            "embarque",
            "destino",
            "motorista",
            "passageiro",
            "pedido",
            "aceitar",
            "recusar",
            "tarifa",
            "oferta",
            "uber",
            "99",
            "indrive",
        ).any { normalized.contains(it) }
        val hasAddressWord = listOf(
            "rua",
            "avenida",
            "av.",
            "bairro",
            "jardim",
            "rodovia",
            "rod.",
            "praça",
            "praca",
            "centro",
        ).any { normalized.contains(it) }
        return (hasRideWord && (hasMoney || hasDistance || hasAddressWord)) || (hasMoney && hasDistance)
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
        if (normalized == "com.google.android.apps.nbu.files") return false
        if (normalized == "com.google.android.documentsui") return false
        if (normalized == "com.sec.android.app.myfiles") return false
        if (normalized == "com.samsung.android.app.myfiles") return false
        if (normalized in PASSIVE_DIAGNOSTIC_PACKAGES) return false
        if (normalized in IGNORED_PACKAGES) return false
        if (normalized.startsWith("com.android.")) return false
        if (normalized.startsWith("com.google.android.inputmethod")) return false
        if (normalized.startsWith("com.samsung.android.biometrics")) return false
        if (normalized == "com.samsung.android.app.aodservice") return false
        if (normalized == "com.samsung.android.systemui") return false
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
    mustRunAfter("enforceUserRegisteredPackagesOnly")
    mustRunAfter("bubbleStateMachineIntegration")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(bubbleUnlimitedCardLearning)
}
