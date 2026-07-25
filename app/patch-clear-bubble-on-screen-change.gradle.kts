val clearBubbleOnScreenChange by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        text = text.replace(
"""            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.ignore_reason keep_decision=true reason=${dollar}reason")
                return
            }
""",
"""            traceEvent("bubble.reset_after_ocr_ignore reason=${dollar}reason")
""",
        )

        text = text.replace(
"""            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.ride_offer_false keep_decision=true reason=${dollar}reason")
                return
            }
""",
"""            traceEvent("bubble.reset_after_ocr_ride_offer_false reason=${dollar}reason")
""",
        )

        text = text.replace(
"""            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.card_model_missing keep_decision=true templates=${dollar}{currentCardTemplates.size}")
                return
            }
""",
"""            traceEvent("bubble.reset_after_ocr_card_model_missing templates=${dollar}{currentCardTemplates.size}")
""",
        )

        text = text.replace(
"""        if (snapshotHash != lastSnapshotHash) {
            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.screen_changed keep_decision=true hash=${dollar}snapshotHash")
            } else {
                lastSnapshotHash = snapshotHash
                lastAnalyzedHash = null
                analysisSerial += 1
                traceEvent("analysis.invalidate_on_screen_change hash=${dollar}snapshotHash")
                showOverlay(RadarColor.Default)
                recordDiagnostic(
                    stage = "screen_changed",
                    reason = "A imagem/texto da tela mudou; aguardando confirmar o card cadastrado.",
                    text = snapshotText,
                )
            }
        }
""",
"""        if (snapshotHash != lastSnapshotHash) {
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            analysisSerial += 1
            clearBubbleForScreenChange(snapshotHash)
            recordDiagnostic(
                stage = "screen_changed",
                reason = "A imagem/texto da tela mudou; limpei a bolinha e estou aguardando confirmar o card cadastrado.",
                text = snapshotText,
            )
        }
""",
        )

        text = text.replace(
"""                analysisSerial += 1
                traceEvent("analysis.invalidate_on_screen_change hash=${dollar}snapshotHash")
                showOverlay(RadarColor.Default)
""",
"""                analysisSerial += 1
                clearBubbleForScreenChange(snapshotHash)
""",
        )

        text = text.replace(
"""            analysisSerial += 1
            traceEvent("analysis.invalidate_on_screen_change hash=${dollar}snapshotHash")
            showOverlay(RadarColor.Default)
""",
"""            analysisSerial += 1
            clearBubbleForScreenChange(snapshotHash)
""",
        )

        if ("private fun clearBubbleForScreenChange(" !in text) {
            text = text.replace(
"""
    private fun resetToDefault(
""",
"""
    private fun clearBubbleForScreenChange(snapshotHash: Int) {
        pendingAnalysis = null
        currentDistanceKm = null
        currentBubbleLabel = null
        traceEvent("bubble.clear_on_screen_change hash=${dollar}snapshotHash")
        showOverlay(RadarColor.Default, distanceKm = null)
    }

    private fun resetToDefault(
""",
            )
        }

        if ("clear_bubble_on_screen_change.patch_applied" !in text) {
            text = text.replace(
                "        traceEvent(\"stable_bubble_no_flicker.patch_applied=true\")\n",
                "        traceEvent(\"stable_bubble_no_flicker.patch_applied=true\")\n        traceEvent(\"clear_bubble_on_screen_change.patch_applied=true\")\n",
            )
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

clearBubbleOnScreenChange.configure {
    mustRunAfter(
        "preciseBubbleRouteKm",
        "stableBubbleNoFlicker",
        "patchLiveRideAccessibilityService",
        "patchLiveAnalysisSupersede",
        "patchInstantCardDecisionCache",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(clearBubbleOnScreenChange)
}
