val bubbleFinalHelperCompileFix by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        if ("lastCardSaveCandidateText" !in text) {
            text = text.replace(
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

        if ("private fun snapshotCurrentCardCandidateForBubbleAction" !in text) {
            text = text.replace(
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
        if (cachedPackage != null && cachedText != null && now - lastCardSaveCandidateAtMillis <= 20_000L) {
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
            "salvar card desta tela",
            "capturar dados do card",
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
            text = text.replace(
"""        const val PACKAGE_INDRIVE_DRIVER = "sinet.startup.indriver"
""",
"""        const val PACKAGE_INDRIVE_DRIVER = "sinet.startup.indriver"
        const val LEARNED_POPUP_PACKAGE = "br.com.mapeiaia.rotacerta.learned.popup"
""",
            )
        }

        if (text != original) file.writeText(text)
    }
}

bubbleFinalHelperCompileFix.configure {
    mustRunAfter("bubbleMenuRestoreFinalSave")
    mustRunAfter("bubblePendingDirectSaveCandidate")
    mustRunAfter("bubbleUnlimitedCardLearning")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleFinalHelperCompileFix)
}
