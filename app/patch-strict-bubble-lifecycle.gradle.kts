val patchStrictBubbleLifecycle by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        fun replaceExact(target: String, replacement: String) {
            text = text.replace(target, replacement)
        }

        replaceExact(
"""        traceEvent("event package=${'$'}{packageName.orEmpty()} type=${'$'}{event.eventType}")
        if (packageName == null) {
""",
"""        traceEvent("event package=${'$'}{packageName.orEmpty()} type=${'$'}{event.eventType}")
        resetStaleRegisteredCardDecision()
        if (packageName == null) {
""",
        )

        replaceExact(
"""                val packageName = currentWindowPackageName()
                if (shouldScanPackage(packageName)) {
""",
"""                val packageName = currentWindowPackageName()
                resetStaleRegisteredCardDecision()
                if (shouldScanPackage(packageName)) {
""",
        )

        replaceExact(
"""        val snapshotText = if (allowPopupCandidate) {
            text.trim()
        } else {
            mergeRideTexts(lastAccessibilityText, lastOcrText).ifBlank { text.trim() }
        }
""",
"""        val snapshotText = text.trim()
""",
        )

        replaceExact(
"""        RideScreenTextClassifier.ignoreReason(snapshotText)?.let { reason ->
            traceEvent("classifier.ignore=true reason=${'$'}reason hash=${'$'}snapshotHash")
            if (allowPopupCandidate) return
            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.ignore_reason keep_decision=true reason=${'$'}reason")
                return
            }
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            registeredCardGate.clear()
            resetToDefault(reason = reason, text = snapshotText, record = !isPassiveDiagnosticPackage(activePackageName))
            return
        }
""",
"""        RideScreenTextClassifier.ignoreReason(snapshotText)?.let { reason ->
            traceEvent("classifier.ignore=true reason=${'$'}reason hash=${'$'}snapshotHash")
            if (allowPopupCandidate) return
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            registeredCardGate.clear()
            resetToDefault(reason = reason, text = snapshotText, record = !isPassiveDiagnosticPackage(activePackageName))
            return
        }
""",
        )

        replaceExact(
"""        if (snapshotHash != lastSnapshotHash) {
            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.screen_changed keep_decision=true hash=${'$'}snapshotHash")
            } else {
                lastSnapshotHash = snapshotHash
                lastAnalyzedHash = null
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
            registeredCardGate.clear()
            showOverlay(RadarColor.Default)
            recordDiagnostic(
                stage = "screen_changed",
                reason = "A imagem/texto da tela mudou; aguardando confirmar o card cadastrado.",
                text = snapshotText,
            )
        }
""",
        )

        replaceExact(
"""        if (snapshotHash != lastSnapshotHash) {
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            showOverlay(RadarColor.Default)
            recordDiagnostic(
                stage = "screen_changed",
                reason = "A imagem/texto da tela mudou; aguardando confirmar o card cadastrado.",
                text = snapshotText,
            )
        }
""",
"""        if (snapshotHash != lastSnapshotHash) {
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            registeredCardGate.clear()
            showOverlay(RadarColor.Default)
            recordDiagnostic(
                stage = "screen_changed",
                reason = "A imagem/texto da tela mudou; aguardando confirmar o card cadastrado.",
                text = snapshotText,
            )
        }
""",
        )

        replaceExact(
"""            traceEvent("classifier.ride_offer=false reason=${'$'}reason")
            if (allowPopupCandidate) return
            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.ride_offer_false keep_decision=true reason=${'$'}reason")
                return
            }
            registeredCardGate.clear()
""",
"""            traceEvent("classifier.ride_offer=false reason=${'$'}reason")
            if (allowPopupCandidate) return
            registeredCardGate.clear()
""",
        )

        replaceExact(
"""            traceEvent("card_model.missing package=${'$'}{packageName.orEmpty()} templates=${'$'}{currentCardTemplates.size}")
            if (allowPopupCandidate) return
            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.card_model_missing keep_decision=true templates=${'$'}{currentCardTemplates.size}")
                return
            }
            registeredCardGate.clear()
""",
"""            traceEvent("card_model.missing package=${'$'}{packageName.orEmpty()} templates=${'$'}{currentCardTemplates.size}")
            if (allowPopupCandidate) return
            registeredCardGate.clear()
""",
        )

        replaceExact(
"""    private fun formatBubbleDistanceKm(distanceKm: Double?): String = when {
        distanceKm == null -> ""
        distanceKm < 1.0 -> String.format(Locale("pt", "BR"), "%.1f", distanceKm).removeSuffix(",0")
        else -> distanceKm.roundToInt().coerceAtMost(99).toString()
    }
""",
"""    private fun formatBubbleDistanceKm(distanceKm: Double?): String = when {
        distanceKm == null -> ""
        distanceKm >= 100.0 -> "99+"
        else -> String.format(Locale("pt", "BR"), "%.1f", distanceKm).removeSuffix(",0")
    }
""",
        )

        if (text != original) {
            file.writeText(text)
        }
    }
}

tasks.named("patchStrictBubbleLifecycle").configure {
    mustRunAfter(tasks.matching { task -> task.name.startsWith("patch") && task.name != "patchStrictBubbleLifecycle" })
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchStrictBubbleLifecycle)
}
