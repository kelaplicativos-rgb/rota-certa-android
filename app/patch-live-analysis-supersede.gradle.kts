val patchLiveAnalysisSupersede by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("private var analysisSerial: Long = 0L" !in text) {
            text = text.replace(
                "    private var analyzing = false\n    private var activePackageName: String? = null\n",
                "    private var analyzing = false\n    private var analysisSerial: Long = 0L\n    private var activePackageName: String? = null\n",
            )
        }

        text = text.replace(
"""        val snapshotText = if (allowPopupCandidate) {
            text.trim()
        } else {
            mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank { text.trim() }
        }
""",
"""        val snapshotText = when {
            allowPopupCandidate -> text.trim()
            source == TextSource.Accessibility -> text.trim()
            else -> mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank { text.trim() }
        }
""",
        )

        text = text.replace(
"""                lastSnapshotHash = snapshotHash
                lastAnalyzedHash = null
                showOverlay(RadarColor.Default)
""",
"""                lastSnapshotHash = snapshotHash
                lastAnalyzedHash = null
                analysisSerial += 1
                traceEvent("analysis.invalidate_on_screen_change hash=${dollar}snapshotHash")
                showOverlay(RadarColor.Default)
""",
        )

        text = text.replace(
"""            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            showOverlay(RadarColor.Default)
""",
"""            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            analysisSerial += 1
            traceEvent("analysis.invalidate_on_screen_change hash=${dollar}snapshotHash")
            showOverlay(RadarColor.Default)
""",
        )

        text = text.replace(
"""        if (analyzing) {
            pendingAnalysis = PendingLiveAnalysis(snapshotText, fields, snapshotHash, cardMatch, allowPopupCandidate)
            traceEvent("analysis.defer analyzing=true hash=${dollar}snapshotHash")
            return
        }
        analyzeLiveText(snapshotText, fields, snapshotHash, cardMatch, allowPopupCandidate)
""",
"""        if (analyzing) {
            pendingAnalysis = null
            analysisSerial += 1
            traceEvent("analysis.supersede previous=true hash=${dollar}snapshotHash")
        }
        analyzeLiveText(snapshotText, fields, snapshotHash, cardMatch, allowPopupCandidate)
""",
        )

        text = text.replace(
            "        if (!serviceReady || (!allowPopupCandidate && !shouldScanCurrentWindow()) || analyzing) return\n",
            "        if (!serviceReady || (!allowPopupCandidate && !shouldScanCurrentWindow())) return\n",
        )

        text = text.replace(
"""        analyzing = true
        traceEvent("analysis.start hash=${dollar}snapshotHash destination=${dollar}{fields.destination.diagnosticValue()}")
""",
"""        val analysisToken = ++analysisSerial
        analyzing = true
        traceEvent("analysis.start token=${dollar}analysisToken hash=${dollar}snapshotHash destination=${dollar}{fields.destination.diagnosticValue()}")
""",
        )

        if ("analysis.drop_stale_before_quick" !in text) {
            text = text.replace(
"""            if (quickResult.recommendation != Recommendation.InsufficientData) {
""",
"""            if (analysisToken != analysisSerial) {
                traceEvent("analysis.drop_stale_before_quick token=${dollar}analysisToken current=${dollar}analysisSerial hash=${dollar}snapshotHash")
                return
            }
            if (quickResult.recommendation != Recommendation.InsufficientData) {
""",
            )
        }

        text = text.replace(
"""            traceEvent("decision.result recommendation=${dollar}{result.recommendation} reason=${dollar}{result.reason}")
            repository.addAnalysis(result)
""",
"""            traceEvent("decision.result recommendation=${dollar}{result.recommendation} reason=${dollar}{result.reason}")
            if (analysisToken != analysisSerial) {
                traceEvent("analysis.drop_stale token=${dollar}analysisToken current=${dollar}analysisSerial hash=${dollar}snapshotHash")
                return
            }
            repository.addAnalysis(result)
""",
        )

        text = text.replace(
"""        return geocodingService.geocode(query, region)
            ?: googleMapsService.geocode(query, region, settings.googleMapsApiKey)
""",
"""        return geocodingService.geocode(query, region)
""",
        )

        text = text.replace(
            "const val SCAN_LOOP_MS = 350L",
            "const val SCAN_LOOP_MS = 180L",
        )

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchLiveAnalysisSupersede.configure {
    mustRunAfter("patchLiveRideAccessibilityService", "patchInstantCardDecisionCache")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchLiveAnalysisSupersede)
}
