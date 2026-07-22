val patchInstantCardDecisionCache by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("private val liveDecisionCache = linkedMapOf<Int, CachedLiveDecision>()" !in text) {
            text = text.replace(
                "    private var currentDistanceKm: Double? = null\n",
                "    private var currentDistanceKm: Double? = null\n    private val liveDecisionCache = linkedMapOf<Int, CachedLiveDecision>()\n",
            )
        }

        text = text.replace(
"""        if (analyzing) {
            traceEvent("accessibility.schedule skipped analyzing=true")
            return
        }
        if (analyzeJob?.isActive == true) {
""",
"""        if (analyzing) {
            traceEvent("accessibility.schedule while_analyzing=true")
        }
        if (!analyzing && analyzeJob?.isActive == true) {
""",
        )

        if ("cache.instant_apply" !in text) {
            text = text.replace(
"""        registeredCardGate.markSeen()
        traceEvent("card_model.match name=${dollar}{cardMatch.template.name} score=${dollar}{cardMatch.score}")

        if (snapshotHash == lastAnalyzedHash) {
""",
"""        registeredCardGate.markSeen()
        traceEvent("card_model.match name=${dollar}{cardMatch.template.name} score=${dollar}{cardMatch.score}")
        if (applyCachedLiveDecision(snapshotHash, snapshotText, fields, cardMatch)) return

        if (snapshotHash == lastAnalyzedHash) {
""",
            )
        }

        if ("rememberLiveDecision(snapshotHash, result)" !in text) {
            text = text.replace(
"""            repository.addAnalysis(result)
            lastSavedReadHash = snapshotHash
""",
"""            repository.addAnalysis(result)
            rememberLiveDecision(snapshotHash, result)
            lastSavedReadHash = snapshotHash
""",
            )
        }

        if ("private fun applyCachedLiveDecision(" !in text) {
            text = text.replace(
"""    private suspend fun analyzeLiveText(
""",
"""    private fun applyCachedLiveDecision(
        snapshotHash: Int,
        text: String,
        fields: RideFields,
        cardMatch: RideCardTemplateMatch,
    ): Boolean {
        val cached = liveDecisionCache[snapshotHash] ?: return false
        val result = cached.result
        if (result.recommendation == Recommendation.InsufficientData) return false
        val radarColor = when (result.recommendation) {
            Recommendation.GoodRide -> RadarColor.Green
            Recommendation.OutsideRadius -> RadarColor.Red
            Recommendation.InsufficientData -> RadarColor.Default
        }
        traceEvent("cache.instant_apply color=${dollar}{radarColor.diagnosticLabel} hash=${dollar}snapshotHash")
        lastAnalyzedHash = snapshotHash
        showOverlay(color = radarColor, distanceKm = result.nearestConfiguredDistanceKm())
        recordDiagnostic(
            stage = "analysis_cached_result",
            color = radarColor,
            reason = "Decisao reaplicada instantaneamente do cache do card.",
            text = text,
            fields = fields,
            result = result,
            cardTemplateMatch = cardMatch,
        )
        return true
    }

    private fun rememberLiveDecision(snapshotHash: Int, result: AnalysisResult) {
        if (result.recommendation == Recommendation.InsufficientData) return
        liveDecisionCache[snapshotHash] = CachedLiveDecision(result)
        while (liveDecisionCache.size > LIVE_DECISION_CACHE_LIMIT) {
            val firstKey = liveDecisionCache.keys.firstOrNull() ?: break
            liveDecisionCache.remove(firstKey)
        }
    }

    private suspend fun analyzeLiveText(
""",
            )
        }

        if ("private data class CachedLiveDecision" !in text) {
            text = text.replace(
"""    private data class PendingLiveAnalysis(
        val text: String,
        val fields: RideFields,
        val snapshotHash: Int,
        val cardMatch: RideCardTemplateMatch?,
        val allowPopupCandidate: Boolean,
    )
""",
"""    private data class PendingLiveAnalysis(
        val text: String,
        val fields: RideFields,
        val snapshotHash: Int,
        val cardMatch: RideCardTemplateMatch?,
        val allowPopupCandidate: Boolean,
    )

    private data class CachedLiveDecision(
        val result: AnalysisResult,
    )
""",
            )
        }

        if ("const val LIVE_DECISION_CACHE_LIMIT" !in text) {
            text = text.replace(
                "        const val DIAGNOSTIC_EVENT_LIMIT = 60\n",
                "        const val DIAGNOSTIC_EVENT_LIMIT = 60\n        const val LIVE_DECISION_CACHE_LIMIT = 32\n",
            )
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchInstantCardDecisionCache.configure {
    mustRunAfter("patchLiveFastColorPriority")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchInstantCardDecisionCache)
}
