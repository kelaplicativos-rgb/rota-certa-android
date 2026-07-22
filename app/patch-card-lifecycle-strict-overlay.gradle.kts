fun replacePrivateFunctionBlockCardLifecycle(
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

val cardLifecycleStrictOverlay by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val matcherFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcher.kt")
    inputs.files(serviceFile, matcherFile)
    outputs.upToDateWhen { false }

    doLast {
        val dollar = "$"

        serviceFile.asFile.let { file ->
            var text = file.readText()
            val original = text

            text = replacePrivateFunctionBlockCardLifecycle(text, "clearBubbleForScreenChange") {
"""    private fun clearBubbleForScreenChange(snapshotHash: Int) {
        pendingAnalysis = null
        registeredCardGate.clear()
        clearRememberedRideText()
        currentDistanceKm = null
        currentBubbleLabel = null
        traceEvent("bubble.clear_on_screen_change hash=${dollar}snapshotHash")
        showOverlay(RadarColor.Default, distanceKm = null)
    }

"""
            }

            if ("private fun clearBubbleForScreenChange(" !in text) {
                text = text.replace(
"""
    private fun resetToDefault(
""",
"""
    private fun clearBubbleForScreenChange(snapshotHash: Int) {
        pendingAnalysis = null
        registeredCardGate.clear()
        clearRememberedRideText()
        currentDistanceKm = null
        currentBubbleLabel = null
        traceEvent("bubble.clear_on_screen_change hash=${dollar}snapshotHash")
        showOverlay(RadarColor.Default, distanceKm = null)
    }

    private fun resetToDefault(
""",
                )
            }

            if ("bubble.clear_inactive_card analyzed_hash" !in text) {
                text = text.replace(
"""            if (allowPopupCandidate && !looksLikeRegisteredPopupCandidate(collectVisibleText(allowPopupCandidate = true))) {
                registeredCardGate.clear()
                resetToDefaultForNonRideScreen(
                    reason = "O pop-up de corrida nao esta mais visivel; bolinha voltou para cinza.",
                    record = false,
                )
                return
            }

            lastAnalyzedHash = lastSnapshotHash ?: snapshotHash
""",
"""            if (allowPopupCandidate && !looksLikeRegisteredPopupCandidate(collectVisibleText(allowPopupCandidate = true))) {
                registeredCardGate.clear()
                resetToDefaultForNonRideScreen(
                    reason = "O pop-up de corrida nao esta mais visivel; bolinha voltou para cinza.",
                    record = false,
                )
                return
            }
            if (!allowPopupCandidate && lastSnapshotHash != snapshotHash) {
                traceEvent("bubble.clear_inactive_card analyzed_hash=${dollar}snapshotHash active_hash=${dollar}{lastSnapshotHash ?: "null"}")
                registeredCardGate.clear()
                pendingAnalysis = null
                currentDistanceKm = null
                currentBubbleLabel = null
                showOverlay(RadarColor.Default, distanceKm = null)
                recordDiagnostic(
                    stage = "card_inactive_before_overlay",
                    reason = "O card analisado saiu da tela antes de aplicar resultado; limpei a bolinha.",
                    text = text,
                    fields = fields,
                    cardTemplateMatch = cardMatch,
                )
                return
            }

            lastAnalyzedHash = lastSnapshotHash ?: snapshotHash
""",
                )
            }

            if ("analysis.pending discard inactive_card" !in text) {
                text = text.replace(
"""            if (pending != null && pending.snapshotHash != lastAnalyzedHash && shouldScanCurrentWindow()) {
                traceEvent("analysis.pending replay hash=${dollar}{pending.snapshotHash}")
                scope.launch {
                    analyzeLiveText(
                        text = pending.text,
                        fields = pending.fields,
                        snapshotHash = pending.snapshotHash,
                        cardMatch = pending.cardMatch,
                        allowPopupCandidate = pending.allowPopupCandidate,
                    )
                }
            }
""",
"""            if (pending != null && pending.snapshotHash == lastSnapshotHash && pending.snapshotHash != lastAnalyzedHash && shouldScanCurrentWindow()) {
                traceEvent("analysis.pending replay hash=${dollar}{pending.snapshotHash}")
                scope.launch {
                    analyzeLiveText(
                        text = pending.text,
                        fields = pending.fields,
                        snapshotHash = pending.snapshotHash,
                        cardMatch = pending.cardMatch,
                        allowPopupCandidate = pending.allowPopupCandidate,
                    )
                }
            } else if (pending != null) {
                traceEvent("analysis.pending discard inactive_card hash=${dollar}{pending.snapshotHash} active_hash=${dollar}{lastSnapshotHash ?: "null"}")
            }
""",
                )
            }

            if ("card_lifecycle_strict_overlay.patch_applied" !in text) {
                text = text.replace(
                    "        traceEvent(\"clear_bubble_on_screen_change.patch_applied=true\")\n",
                    "        traceEvent(\"clear_bubble_on_screen_change.patch_applied=true\")\n        traceEvent(\"card_lifecycle_strict_overlay.patch_applied=true\")\n",
                )
            }

            if (text != original) file.writeText(text)
        }

        matcherFile.asFile.let { file ->
            var text = file.readText()
            val original = text

            text = text.replace(
"""        return relaxedInDriveFeedMatch(text, normalizedPackage, features, candidates)
""",
"""        return null
""",
            )

            if (text != original) file.writeText(text)
        }
    }
}

cardLifecycleStrictOverlay.configure {
    mustRunAfter(
        "clearBubbleOnScreenChange",
        "stableBubbleNoFlicker",
        "preciseBubbleRouteKm",
        "bubbleRegionShortcutFlow",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(cardLifecycleStrictOverlay)
}
