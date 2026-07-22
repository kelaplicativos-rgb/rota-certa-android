// Correcao baseada no relatorio manual 0.1.85:
// - nao cria ciclo de card antes do match real;
// - nao mistura transacoes paralelas do pipeline;
// - nao relê print/arquivo como se fosse card ao vivo;
// - usa a mesma assinatura estavel no lifecycle e no freshness guard.

val reportStaleLifecycleFix by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast

        var text = file.readText()
        val original = text
        val dollar = "$"

        // Qualquer leitura de popup precisa continuar presa a uma raiz real de app
        // monitorado. Isso bloqueia DocumentsUI, galeria e seletor de fotos.
        if ("report_popup_root_guard_0_1_86" !in text) {
            val scheduleSignature = """    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {
"""
            val scheduleReplacement = scheduleSignature + """        if (allowPopupCandidate && !shouldScanPackage(currentRootPackageName())) return // report_popup_root_guard_0_1_86
"""
            if (scheduleSignature !in text) {
                throw org.gradle.api.GradleException("Nao encontrei scheduleVisibleTextAnalysis para bloquear popup passivo.")
            }
            text = text.replace(scheduleSignature, scheduleReplacement)

            val screenshotSignature = """    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {
"""
            val screenshotReplacement = screenshotSignature + """        if (allowPopupCandidate && !shouldScanPackage(currentRootPackageName())) return
"""
            if (screenshotSignature !in text) {
                throw org.gradle.api.GradleException("Nao encontrei requestScreenshotAnalysis para bloquear popup passivo.")
            }
            text = text.replace(screenshotSignature, screenshotReplacement)
        }

        // Mesmo que uma leitura antiga ja tenha sido agendada, ela nao pode continuar
        // depois que a raiz ativa deixou o aplicativo de corrida.
        if ("report_process_popup_root_guard_0_1_86" !in text) {
            val target = """        val windowPackageName = currentWindowPackageName()
"""
            val replacement = target + """        val liveRootPackageName = currentRootPackageName()
        if (allowPopupCandidate && !shouldScanPackage(liveRootPackageName)) {
            traceEvent("popup.process discarded passive_root=${dollar}{liveRootPackageName.orEmpty()}") // report_process_popup_root_guard_0_1_86
            return
        }
"""
            if (target !in text) throw org.gradle.api.GradleException("Nao encontrei inicio de processRideText para instalar guarda de popup.")
            text = text.replaceFirst(target, replacement)
        }

        if ("report_candidate_root_guard_0_1_86" !in text) {
            val target = """    private fun looksLikeRegisteredPopupCandidate(text: String): Boolean {
"""
            val replacement = target + """        if (!shouldScanPackage(currentRootPackageName())) return false // report_candidate_root_guard_0_1_86
"""
            if (target !in text) throw org.gradle.api.GradleException("Nao encontrei looksLikeRegisteredPopupCandidate.")
            text = text.replace(target, replacement)
        }

        // O resultado do screenshot fica sempre preso ao pacote/raiz que o solicitou,
        // inclusive no caminho de popup.
        text = text.replace(
            "if (!allowPopupCandidate && (screenshotAgeMillis > OCR_RESULT_MAX_AGE_MS || currentPackageAfterScreenshot != screenshotRequestPackage))",
            "if (screenshotAgeMillis > OCR_RESULT_MAX_AGE_MS || currentPackageAfterScreenshot != screenshotRequestPackage || !shouldScanPackage(currentRootPackageName())) // report_screenshot_root_guard_0_1_86",
        )

        // Remove o lifecycle instalado cedo demais, logo depois de qualquer snapshot parcial.
        // Em execucoes seguintes o lifecycle final ja existe e nao deve ser removido novamente.
        val hasFinalMatchedLifecycle = "report_visible_card_after_match_0_1_86" in text
        val earlyLifecycleStart = text.indexOf("        val coreVisibleCardEvent = coreVisibleCardLifecycle.observe(")
        if (earlyLifecycleStart >= 0 && !hasFinalMatchedLifecycle) {
            val classifierStart = text.indexOf("        RideScreenTextClassifier.ignoreReason(snapshotText)", earlyLifecycleStart)
            if (classifierStart < 0) {
                throw org.gradle.api.GradleException("Nao encontrei fim do lifecycle precoce do card.")
            }
            text = text.removeRange(earlyLifecycleStart, classifierStart)
        }

        // A identidade visual precisa incluir destino; valor sozinho pode se repetir em outra corrida.
        text = text.replace(
            """        cardMatch.template.id,
        fields.fare.stableSignaturePart(),
""",
            """        cardMatch.template.id,
        fields.destination.stableSignaturePart(),
        fields.fare.stableSignaturePart(),
""",
        )

        if ("report_visible_card_after_match_0_1_86" !in text) {
            val cardPipelineStart = text.indexOf("        val corePipelineCard = coreLivePipeline.cardAccepted(")
            if (cardPipelineStart < 0) {
                throw org.gradle.api.GradleException("Nao encontrei pipeline de card aceito para mover lifecycle.")
            }
            val lifecycleAfterMatch = """        val coreStableCardSignature = buildVisibleCardSignature(packageName, fields, cardMatch)
        val coreVisibleCardEvent = coreVisibleCardLifecycle.observe(
            packageName = packageName,
            snapshotHash = snapshotHash,
            text = snapshotText,
            stableSignature = coreStableCardSignature,
        )
        lastVisibleCardSignature = coreStableCardSignature
        if (coreVisibleCardEvent.action != br.com.mapeiaia.rotacerta.core.CoreVisibleCardAction.Same) {
            traceEvent("core.visible_card action=${dollar}{coreVisibleCardEvent.action} signature=${dollar}coreStableCardSignature reason=${dollar}{coreVisibleCardEvent.reason}") // report_visible_card_after_match_0_1_86
        }
        val corePipelineVisible = coreLivePipeline.visibleCard(
            transaction = corePipelineRead,
            action = coreVisibleCardEvent.action,
            visibleCardSignature = coreStableCardSignature,
        )
        if (coreVisibleCardEvent.shouldClearPreviousDecision) {
            registeredCardGate.clear()
            lastAnalyzedHash = null
            if (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) {
                showOverlay(RadarColor.Default)
            }
        }
"""
            text = text.substring(0, cardPipelineStart) + lifecycleAfterMatch + text.substring(cardPipelineStart)
        }

        // O estagio Card deve continuar exatamente a transacao Read/Visible do mesmo snapshot.
        val cardBlockStart = text.indexOf("        val corePipelineCard = coreLivePipeline.cardAccepted(")
        if (cardBlockStart >= 0) {
            val cardBlockEnd = text.indexOf("        )", cardBlockStart).takeIf { it >= 0 }?.plus("        )".length) ?: -1
            if (cardBlockEnd > cardBlockStart) {
                val block = text.substring(cardBlockStart, cardBlockEnd)
                val fixed = block.replace(
                    Regex("transaction = coreLivePipeline\\.currentTransaction\\(\\) \\?: corePipelineTransaction"),
                    "transaction = corePipelineVisible",
                )
                text = text.substring(0, cardBlockStart) + fixed + text.substring(cardBlockEnd)
            }
        }

        // Rota/decisao/visual recuperam a transacao pelo hash do snapshot, nunca pela
        // ultima chamada global que pode pertencer a outro evento concorrente.
        val boundTransaction = "coreLivePipeline.transactionFor(snapshotHash) ?: coreLivePipeline.readReady(coreLivePipeline.begin(packageName, \"analysis\", text.length, allowPopupCandidate), snapshotHash, text.length)"
        text = text.replace(
            "coreLivePipeline.currentTransaction() ?: coreLivePipeline.begin(packageName, \"analysis\", text.length, allowPopupCandidate)",
            boundTransaction,
        )
        text = text.replace(
            "transaction = coreLivePipeline.currentTransaction(),\n                currentPackageName = packageName,",
            "transaction = coreLivePipeline.transactionFor(snapshotHash),\n                currentPackageName = packageName,",
        )

        listOf(
            "report_popup_root_guard_0_1_86",
            "report_process_popup_root_guard_0_1_86",
            "report_candidate_root_guard_0_1_86",
            "report_screenshot_root_guard_0_1_86",
            "report_visible_card_after_match_0_1_86",
            "stableSignature = coreStableCardSignature",
            "transaction = corePipelineVisible",
            "coreLivePipeline.transactionFor(snapshotHash)",
        ).forEach { marker ->
            if (marker !in text) throw org.gradle.api.GradleException("Correcao do relatorio ausente: $marker")
        }
        if (text.indexOf("val coreVisibleCardEvent = coreVisibleCardLifecycle.observe") !=
            text.lastIndexOf("val coreVisibleCardEvent = coreVisibleCardLifecycle.observe")
        ) {
            throw org.gradle.api.GradleException("Lifecycle do card foi instalado mais de uma vez.")
        }

        if (text != original) file.writeText(text)
    }
}

reportStaleLifecycleFix.configure {
    mustRunAfter(
        "liveRideWindowEventGuard",
        "passiveEventCompileFix",
        "patchBubbleRenderStability",
        "liveResultFreshnessGuard",
        "rotaCertaCoreGate",
        "coreCardMatchEnginePatch",
        "coreVisibleCardLifecyclePatch",
        "coreLiveAnalysisPipelinePatch",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(reportStaleLifecycleFix)
}
