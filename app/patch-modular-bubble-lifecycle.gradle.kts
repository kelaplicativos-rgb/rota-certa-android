fun replacePrivateFunctionBlockModularBubble(
    source: String,
    functionName: String,
    transform: (String) -> String,
): String {
    val start = source.indexOf("    private fun $functionName")
    if (start < 0) return source
    val next = source.indexOf("\n    private fun ", start + 1)
    val block = if (next < 0) source.substring(start) else source.substring(start, next + 1)
    val replacement = transform(block)
    return if (next < 0) {
        source.substring(0, start) + replacement
    } else {
        source.substring(0, start) + replacement + source.substring(next + 1)
    }
}

val modularBubbleLifecycle by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val dollar = "$"
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        if ("private val bubbleCardSessionStore = BubbleCardSessionStore()" !in text) {
            text = text.replace(
                "    private var currentDistanceKm: Double? = null\n",
                "    private var currentDistanceKm: Double? = null\n    private val bubbleCardSessionStore = BubbleCardSessionStore()\n",
            )
        }

        text = replacePrivateFunctionBlockModularBubble(text, "clearBubbleForScreenChange") {
"""    private fun clearBubbleForScreenChange(snapshotHash: Int) {
        val reason = BubbleLifecycleGuard.screenChangeReason(bubbleCardSessionStore.current, snapshotHash)
        val event = bubbleCardSessionStore.clear(reason, snapshotHash)
        pendingAnalysis = null
        registeredCardGate.clear()
        clearRememberedRideText()
        currentDistanceKm = null
        currentBubbleLabel = null
        traceEvent(
            "bubble.lifecycle.clear reason=${dollar}reason previous_hash=${dollar}{event.previousSnapshotHash ?: "null"} new_hash=${dollar}snapshotHash package=${dollar}{event.previousPackageName.orEmpty()}",
        )
        showOverlay(RadarColor.Default, distanceKm = null)
    }

"""
        }

        text = text.replace(
"""        if (source == TextSource.Ocr && RideTextSanitizer.containsRotaCertaOverlay(text)) {
            traceEvent("ocr.overlay_contamination skipped=true length=${dollar}{text.length}")
            return
        }
""",
"""        if (source == TextSource.Ocr && BubbleLifecycleGuard.shouldIgnoreOcrText(text)) {
            traceEvent("ocr.overlay_contamination skipped=true length=${dollar}{text.length}")
            return
        }
""",
        )

        text = text.replace(
"""        registeredCardGate.markSeen()
        traceEvent("card_model.match name=${dollar}{cardMatch.template.name} score=${dollar}{cardMatch.score}")
""",
"""        registeredCardGate.markSeen()
        val activeBubbleSession = bubbleCardSessionStore.startOrUpdate(
            packageName = packageName,
            snapshotHash = snapshotHash,
            text = snapshotText,
            fields = fields,
            templateName = cardMatch.template.name,
        )
        traceEvent(
            "bubble.session.active package=${dollar}{activeBubbleSession.packageName.orEmpty()} hash=${dollar}{activeBubbleSession.snapshotHash} template=${dollar}{activeBubbleSession.templateName.orEmpty()}",
        )
        traceEvent("card_model.match name=${dollar}{cardMatch.template.name} score=${dollar}{cardMatch.score}")
""",
        )

        text = text.replace(
"""        if (snapshotText.isBlank()) {
            traceEvent("process.empty_text source=${dollar}source reset_bubble=true")
            if (allowPopupCandidate) return
            pendingAnalysis = null
            registeredCardGate.clear()
            currentDistanceKm = null
            currentBubbleLabel = null
            lastSnapshotHash = null
            lastAnalyzedHash = null
            showOverlay(RadarColor.Default, distanceKm = null)
            recordDiagnostic(
                stage = "card_disappeared",
                reason = "Texto visivel vazio; card saiu da tela e limpei a bolinha imediatamente.",
            )
            return
        }
""",
"""        if (snapshotText.isBlank()) {
            traceEvent("process.empty_text source=${dollar}source reset_bubble=true")
            if (allowPopupCandidate) return
            if (BubbleLifecycleGuard.shouldResetOnEmptyText(bubbleCardSessionStore.current != null, allowPopupCandidate)) {
                val event = bubbleCardSessionStore.clear("Texto visivel vazio; card saiu da tela.")
                traceEvent("bubble.lifecycle.empty_clear previous_hash=${dollar}{event.previousSnapshotHash ?: "null"} package=${dollar}{event.previousPackageName.orEmpty()}")
            }
            pendingAnalysis = null
            registeredCardGate.clear()
            currentDistanceKm = null
            currentBubbleLabel = null
            lastSnapshotHash = null
            lastAnalyzedHash = null
            showOverlay(RadarColor.Default, distanceKm = null)
            recordDiagnostic(
                stage = "card_disappeared",
                reason = "Texto visivel vazio; card saiu da tela e limpei a bolinha imediatamente.",
            )
            return
        }
""",
        )

        text = replacePrivateFunctionBlockModularBubble(text, "formatBubbleDistanceKm") {
"""    private fun formatBubbleDistanceKm(distanceKm: Double?): String =
        BubbleVisualStateFormatter.formatDistanceKm(distanceKm)

"""
        }

        text = replacePrivateFunctionBlockModularBubble(text, "bubbleTextSizeSp") {
"""    private fun bubbleTextSizeSp(text: String): Float =
        BubbleVisualStateFormatter.textSizeSp(text)

"""
        }

        if ("modular_bubble_lifecycle.patch_applied" !in text) {
            text = text.replace(
                "        traceEvent(\"local_adaptive_card_index.patch_applied=true\")\n",
                "        traceEvent(\"local_adaptive_card_index.patch_applied=true\")\n        traceEvent(\"modular_bubble_lifecycle.patch_applied=true\")\n",
            )
        }

        if (text != original) file.writeText(text)
    }
}

modularBubbleLifecycle.configure {
    mustRunAfter(
        "localAdaptiveCardIndex",
        "registeredCardPackageReading",
        "cardLifecycleStrictOverlay",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(modularBubbleLifecycle)
}
