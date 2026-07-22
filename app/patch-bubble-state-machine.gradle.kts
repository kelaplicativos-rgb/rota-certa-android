val bubbleStateMachineIntegration by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        integrateBubbleStateMachineIntoLiveService(serviceFile.asFile)
    }
}

fun integrateBubbleStateMachineIntoLiveService(file: java.io.File) {
    var text = file.readText()
    val original = text

    fun replaceExact(target: String, replacement: String) {
        text = text.replace(target, replacement)
    }

    if ("private val bubbleStateMachine = BubbleStateMachine()" !in text) {
        replaceExact(
"""    private val registeredCardGate = RegisteredCardDecisionGate()
""",
"""    private val registeredCardGate = RegisteredCardDecisionGate()
    private val bubbleStateMachine = BubbleStateMachine()
""",
        )
    }

    replaceExact(
"""            currentCardTemplates = repository.cardTemplates.first()
            showOverlay(RadarColor.Idle)
""",
"""            currentCardTemplates = repository.cardTemplates.first()
            bubbleStateMachine.markIdle()
            showOverlay(RadarColor.Idle)
""",
    )

    replaceExact(
"""        if (currentRadarColor == RadarColor.Idle) showOverlay(RadarColor.Default)
        scheduleVisibleTextAnalysis(delayMs = 80L)
""",
"""        if (currentRadarColor == RadarColor.Idle) {
            bubbleStateMachine.markWaitingForRegisteredCard()
            showOverlay(RadarColor.Default)
        }
        scheduleVisibleTextAnalysis(delayMs = 80L)
""",
    )

    replaceExact(
"""                    if (currentRadarColor == RadarColor.Idle) showOverlay(RadarColor.Default)
                    scheduleVisibleTextAnalysis(delayMs = 0L)
""",
"""                    if (currentRadarColor == RadarColor.Idle) {
                        bubbleStateMachine.markWaitingForRegisteredCard()
                        showOverlay(RadarColor.Default)
                    }
                    scheduleVisibleTextAnalysis(delayMs = 0L)
""",
    )

    replaceExact(
"""            registeredCardGate.clear()
            resetToDefault(reason = "Texto visivel vazio; nenhum card lido neste momento.", record = !isPassiveDiagnosticPackage(activePackageName))
""",
"""            registeredCardGate.clear()
            bubbleStateMachine.clearCardDecision()
            resetToDefault(reason = "Texto visivel vazio; nenhum card lido neste momento.", record = !isPassiveDiagnosticPackage(activePackageName))
""",
    )

    replaceExact(
"""            registeredCardGate.clear()
            resetToDefault(reason = reason, text = snapshotText, record = !isPassiveDiagnosticPackage(activePackageName))
""",
"""            registeredCardGate.clear()
            bubbleStateMachine.clearCardDecision()
            resetToDefault(reason = reason, text = snapshotText, record = !isPassiveDiagnosticPackage(activePackageName))
""",
    )

    replaceExact(
"""            lastAnalyzedHash = null
            registeredCardGate.clear()
            showOverlay(RadarColor.Default)
""",
"""            lastAnalyzedHash = null
            registeredCardGate.clear()
            bubbleStateMachine.clearCardDecision()
            showOverlay(RadarColor.Default)
""",
    )

    replaceExact(
"""            registeredCardGate.clear()
            saveCapturedReadToHistory(snapshotText, fields, snapshotHash, reason)
""",
"""            registeredCardGate.clear()
            bubbleStateMachine.clearCardDecision()
            saveCapturedReadToHistory(snapshotText, fields, snapshotHash, reason)
""",
    )

    replaceExact(
"""            registeredCardGate.clear()
            saveCapturedCardScreen(snapshotText, fields, snapshotHash, parseResult.parserName, packageName)
""",
"""            registeredCardGate.clear()
            bubbleStateMachine.clearCardDecision()
            saveCapturedCardScreen(snapshotText, fields, snapshotHash, parseResult.parserName, packageName)
""",
    )

    replaceExact(
"""        return RideCardTemplateMatcher.match(text, packageName, currentCardTemplates) != null
""",
"""        return BubbleCardPresenceDetector.matchRegisteredCard(text, packageName, currentCardTemplates) != null
""",
    )

    replaceExact(
"""        val cardMatch = RideCardTemplateMatcher.match(snapshotText, packageName, currentCardTemplates)
""",
"""        val cardMatch = BubbleCardPresenceDetector.matchRegisteredCard(snapshotText, packageName, currentCardTemplates)
""",
    )

    replaceExact(
"""        registeredCardGate.markSeen()
        traceEvent("card_model.match name=${'$'}{cardMatch.template.name} score=${'$'}{cardMatch.score}")
""",
"""        val analysisToken = BubbleCardPresenceDetector.createToken(packageName, snapshotHash, cardMatch)
        if (analysisToken == null) {
            registeredCardGate.clear()
            bubbleStateMachine.clearCardDecision()
            resetToDefault(
                reason = "Card cadastrado reconhecido, mas o pacote do app nao foi confirmado; decisao bloqueada por seguranca.",
                text = snapshotText,
                fields = fields,
            )
            return
        }
        registeredCardGate.markSeen()
        bubbleStateMachine.markAnalyzing(analysisToken)
        traceEvent("card_model.match name=${'$'}{cardMatch.template.name} score=${'$'}{cardMatch.score} token=${'$'}analysisToken")
""",
    )

    if ("analysis.discard stale_token=" !in text) {
        replaceExact(
"""            lastAnalyzedHash = lastSnapshotHash ?: snapshotHash
            val radarColor = when (result.recommendation) {
""",
"""            val activeAnalysisToken = bubbleStateMachine.activeAnalysisToken()
            val currentPackageForToken = if (allowPopupCandidate) activeAnalysisToken?.packageName else currentWindowPackageName()
            if (activeAnalysisToken == null || !bubbleStateMachine.canApplyResult(activeAnalysisToken, currentPackageForToken, lastSnapshotHash ?: snapshotHash)) {
                traceEvent("analysis.discard stale_token=${'$'}{activeAnalysisToken ?: "null"} current_package=${'$'}{currentPackageForToken.orEmpty()} current_hash=${'$'}{lastSnapshotHash ?: snapshotHash}")
                registeredCardGate.clear()
                bubbleStateMachine.clearCardDecision()
                resetToDefault(
                    reason = "Resultado descartado: o card cadastrado mudou, fechou ou saiu da tela antes da bolinha aplicar a cor.",
                    text = text,
                    fields = fields,
                    record = false,
                )
                recordDiagnostic(
                    stage = "analysis_stale_result",
                    reason = "Resultado descartado porque o card analisado nao e mais o card visivel.",
                    text = text,
                    fields = fields,
                    result = result,
                    cardTemplateMatch = cardMatch,
                )
                return
            }

            lastAnalyzedHash = lastSnapshotHash ?: snapshotHash
            val radarColor = when (result.recommendation) {
""",
        )
    }

    replaceExact(
"""            traceEvent("overlay.apply color=${'$'}{radarColor.diagnosticLabel} distance=${'$'}{result.nearestConfiguredDistanceKm()?.let(::formatDiagnosticKm) ?: "null"}")
            showOverlay(color = radarColor, distanceKm = result.nearestConfiguredDistanceKm())
""",
"""            traceEvent("overlay.apply color=${'$'}{radarColor.diagnosticLabel} distance=${'$'}{result.nearestConfiguredDistanceKm()?.let(::formatDiagnosticKm) ?: "null"}")
            bubbleStateMachine.markDecision(activeAnalysisToken)
            showOverlay(color = radarColor, distanceKm = result.nearestConfiguredDistanceKm())
""",
    )

    replaceExact(
"""            registeredCardGate.clear()
            clearRememberedRideText()
            showOverlay(RadarColor.Default)
""",
"""            registeredCardGate.clear()
            bubbleStateMachine.clearCardDecision()
            clearRememberedRideText()
            showOverlay(RadarColor.Default)
""",
    )

    replaceExact(
"""            registeredCardGate.clear()
            resetToDefault(
                reason = "Card cadastrado nao esta mais visivel; bolinha voltou para amarelo.",
""",
"""            registeredCardGate.clear()
            bubbleStateMachine.clearCardDecision()
            resetToDefault(
                reason = "Card cadastrado nao esta mais visivel; bolinha voltou para amarelo.",
""",
    )

    replaceExact(
"""        registeredCardGate.clear()
        clearRememberedRideText()
        showOverlay(RadarColor.Idle)
""",
"""        registeredCardGate.clear()
        bubbleStateMachine.markIdle()
        clearRememberedRideText()
        showOverlay(RadarColor.Idle)
""",
    )

    if (text != original) file.writeText(text)
}

tasks.named("bubbleStateMachineIntegration").configure {
    mustRunAfter("enforceUserRegisteredPackagesOnly")
    mustRunAfter("patchStrictBubbleLifecycle")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(bubbleStateMachineIntegration)
}
