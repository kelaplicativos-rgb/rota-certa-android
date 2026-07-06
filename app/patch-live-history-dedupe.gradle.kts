val patchLiveHistoryDedupe by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("lastInsufficientHistorySignature" !in text) {
            text = text.replace(
"""    private var lastSnapshotHash: Int? = null
    private var lastAnalyzedHash: Int? = null
    private var lastSavedReadHash: Int? = null
    private var lastDiagnosticSignature: String? = null
""",
"""    private var lastSnapshotHash: Int? = null
    private var lastAnalyzedHash: Int? = null
    private var lastSavedReadHash: Int? = null
    private var lastInsufficientHistorySignature: String? = null
    private var lastInsufficientHistoryAtMillis: Long = 0L
    private var lastCapturedScreenSignature: String? = null
    private var lastCapturedScreenAtMillis: Long = 0L
    private var lastDiagnosticSignature: String? = null
""",
            )
        }

        text = text.replace(
"""    private suspend fun saveCapturedReadToHistory(text: String, fields: RideFields, snapshotHash: Int, reason: String) {
        if (snapshotHash == lastSavedReadHash) return
        lastSavedReadHash = snapshotHash
        repository.addAnalysis(
            AnalysisResult(
                createdAtMillis = System.currentTimeMillis(),
                extractedText = text,
                fields = fields,
                recommendation = Recommendation.InsufficientData,
                reason = "Leitura capturada: ${dollar}reason",
            ),
        )
    }
""",
"""    private suspend fun saveCapturedReadToHistory(text: String, fields: RideFields, snapshotHash: Int, reason: String) {
        val now = System.currentTimeMillis()
        val signature = insufficientHistorySignature(fields, text, reason)
        val repeatedSignature = signature == lastInsufficientHistorySignature && now - lastInsufficientHistoryAtMillis < HISTORY_DEDUPE_WINDOW_MS
        val tooSoon = now - lastInsufficientHistoryAtMillis < HISTORY_MIN_INTERVAL_MS
        if (snapshotHash == lastSavedReadHash || repeatedSignature || tooSoon) {
            traceEvent("history.skip duplicate=true signature=${dollar}signature")
            return
        }
        lastSavedReadHash = snapshotHash
        lastInsufficientHistorySignature = signature
        lastInsufficientHistoryAtMillis = now
        repository.addAnalysis(
            AnalysisResult(
                createdAtMillis = now,
                extractedText = text,
                fields = fields,
                recommendation = Recommendation.InsufficientData,
                reason = "Leitura capturada: ${dollar}reason",
            ),
        )
    }
""",
        )

        text = text.replace(
"""    private suspend fun saveCapturedCardScreen(
        text: String,
        fields: RideFields,
        snapshotHash: Int,
        parserName: String,
        packageName: String?,
    ) {
        repository.addCapturedScreen(
""",
"""    private suspend fun saveCapturedCardScreen(
        text: String,
        fields: RideFields,
        snapshotHash: Int,
        parserName: String,
        packageName: String?,
    ) {
        val now = System.currentTimeMillis()
        val signature = capturedScreenSignature(fields, text, packageName)
        if (snapshotHash == lastSavedReadHash || signature == lastCapturedScreenSignature && now - lastCapturedScreenAtMillis < CAPTURED_SCREEN_DEDUPE_WINDOW_MS) {
            traceEvent("captured_screen.skip duplicate=true signature=${dollar}signature")
            return
        }
        lastCapturedScreenSignature = signature
        lastCapturedScreenAtMillis = now
        repository.addCapturedScreen(
""",
        )

        text = text.replace(
"""                createdAtMillis = System.currentTimeMillis(),
                packageName = packageName?.lowercase(Locale.ROOT),
""",
"""                createdAtMillis = now,
                packageName = packageName?.lowercase(Locale.ROOT),
""",
        )

        if ("private fun insufficientHistorySignature(" !in text) {
            text = text.replace(
"""    private suspend fun analyzeLiveText(
""",
"""    private fun insufficientHistorySignature(fields: RideFields, text: String, reason: String): String {
        val destination = fields.destination.normalizedHistoryPart()
        val pickup = fields.pickup.normalizedHistoryPart()
        val reasonKey = when {
            reason.contains("card", ignoreCase = true) -> "card_missing"
            reason.contains("Destino foi lido", ignoreCase = true) -> "not_accepted_as_offer"
            else -> reason.normalizedHistoryPart().take(60)
        }
        val textFallback = if (destination.isBlank() && pickup.isBlank()) text.normalizedHistoryPart().take(120) else ""
        return listOf(activePackageName.orEmpty(), destination, pickup, reasonKey, textFallback).joinToString("|")
    }

    private fun capturedScreenSignature(fields: RideFields, text: String, packageName: String?): String {
        val destination = fields.destination.normalizedHistoryPart()
        val pickup = fields.pickup.normalizedHistoryPart()
        val textFallback = if (destination.isBlank() && pickup.isBlank()) text.normalizedHistoryPart().take(120) else ""
        return listOf(packageName.orEmpty(), destination, pickup, textFallback).joinToString("|")
    }

    private fun String?.normalizedHistoryPart(): String =
        orEmpty()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private suspend fun analyzeLiveText(
""",
            )
        }

        if ("HISTORY_MIN_INTERVAL_MS" !in text) {
            text = text.replace(
"""        const val DIAGNOSTIC_TEXT_LIMIT = 1200
        const val DIAGNOSTIC_EVENT_LIMIT = 60
""",
"""        const val DIAGNOSTIC_TEXT_LIMIT = 1200
        const val DIAGNOSTIC_EVENT_LIMIT = 60
        const val HISTORY_MIN_INTERVAL_MS = 10_000L
        const val HISTORY_DEDUPE_WINDOW_MS = 120_000L
        const val CAPTURED_SCREEN_DEDUPE_WINDOW_MS = 120_000L
""",
            )
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchLiveHistoryDedupe)
}
