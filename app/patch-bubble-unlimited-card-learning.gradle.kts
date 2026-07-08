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
                .takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
                ?: collectVisibleTextForAction().takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
                ?: ""
            val candidate = bestCardSaveCandidate(livePackageName, liveText)
            val packageName = candidate?.packageName
            val text = candidate?.text.orEmpty()
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
                .takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
                ?: collectVisibleTextForAction().takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
                ?: ""
            val candidate = bestCardSaveCandidate(livePackageName, liveText)
            val packageName = candidate?.packageName
            val text = candidate?.text.orEmpty()
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
            .takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
            ?: collectVisibleTextForAction().takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
            ?: return
        rememberCardSaveCandidate(packageName, actionText, reason)
    }

    private fun rememberCardSaveCandidate(packageName: String?, text: String, reason: String) {
        val normalizedPackage = normalizePackageName(packageName)?.takeIf { isLearnableRideAppPackage(it) } ?: return
        val cleanText = text.trim()
        if (cleanText.isBlank() || isBubbleActionMenuText(cleanText)) return
        lastCardSaveCandidatePackageName = normalizedPackage
        lastCardSaveCandidateText = cleanText
        lastCardSaveCandidateAtMillis = System.currentTimeMillis()
        traceEvent("card_save_candidate.remember reason=${'$'}reason package=${'$'}normalizedPackage length=${'$'}{cleanText.length}")
    }

    private fun bestCardSaveCandidate(packageName: String?, text: String): CardSaveCandidate? {
        val normalizedPackage = normalizePackageName(packageName)?.takeIf { isLearnableRideAppPackage(it) }
        val cleanText = text.trim().takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
        if (normalizedPackage != null && cleanText != null) {
            return CardSaveCandidate(normalizedPackage, cleanText)
        }
        val now = System.currentTimeMillis()
        val cachedPackage = lastCardSaveCandidatePackageName?.takeIf { isLearnableRideAppPackage(it) }
        val cachedText = lastCardSaveCandidateText.takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
        if (cachedPackage != null && cachedText != null && now - lastCardSaveCandidateAtMillis <= CARD_SAVE_CANDIDATE_TTL_MS) {
            traceEvent("card_save_candidate.use_cached package=${'$'}cachedPackage age_ms=${'$'}{now - lastCardSaveCandidateAtMillis}")
            return CardSaveCandidate(cachedPackage, cachedText)
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
        if (normalized in PASSIVE_DIAGNOSTIC_PACKAGES) return false
        if (normalized in IGNORED_PACKAGES) return false
        return true
    }

    private fun collectVisibleTextForAction(): String {
""",
        )
    }

    if ("private data class CardSaveCandidate" !in text) {
        replaceExact(
"""    private data class PendingLiveAnalysis(
""",
"""    private data class CardSaveCandidate(
        val packageName: String,
        val text: String,
    )

    private data class PendingLiveAnalysis(
""",
        )
    }

    if ("CARD_SAVE_CANDIDATE_TTL_MS" !in text) {
        replaceExact(
"""        const val DIAGNOSTIC_EVENT_LIMIT = 60
""",
"""        const val DIAGNOSTIC_EVENT_LIMIT = 60
        const val CARD_SAVE_CANDIDATE_TTL_MS = 15_000L
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
