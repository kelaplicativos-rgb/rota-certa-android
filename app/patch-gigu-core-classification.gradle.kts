// Implementacao propria baseada na arquitetura observada no app de referencia:
// leitura -> sanitizacao por app -> classificacao por modulo -> modelo cadastrado -> rota.
// Os filtros legados deixam de bloquear o card antes do modulo especifico do inDrive.

val giguCoreClassificationPatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")

        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("gigu_core_sanitized_snapshot_0_1_90" !in text) {
            val oldSnapshot = "        val snapshotText = coreReadSnapshot.text\n"
            if (oldSnapshot !in text) {
                throw GradleException("Nao encontrei snapshot do Core para aplicar sanitizacao por app.")
            }
            text = text.replaceFirst(
                oldSnapshot,
                "        val snapshotText = br.com.mapeiaia.rotacerta.core.CoreRideTextSanitizer.sanitize(coreReadSnapshot.text, packageName) // gigu_core_sanitized_snapshot_0_1_90\n",
            )
        }

        if ("gigu_core_semantic_hash_0_1_90" !in text) {
            val oldHash = "        val snapshotHash = coreReadSnapshot.hash\n"
            if (oldHash !in text) {
                throw GradleException("Nao encontrei hash bruto do snapshot para substituir pelo hash sanitizado.")
            }
            text = text.replaceFirst(
                oldHash,
                "        val snapshotHash = br.com.mapeiaia.rotacerta.core.CoreScreenReadEngine.stableHash(snapshotText) // gigu_core_semantic_hash_0_1_90\n",
            )
        }

        if ("gigu_core_legacy_ignore_bypass_0_1_90" !in text) {
            val oldIgnore = "        RideScreenTextClassifier.ignoreReason(snapshotText)?.let { reason ->\n"
            if (oldIgnore !in text) {
                throw GradleException("Nao encontrei filtro legado anterior ao classificador por aplicativo.")
            }
            text = text.replaceFirst(
                oldIgnore,
                "        RideScreenTextClassifier.ignoreReason(snapshotText)?.takeIf { !shouldScanPackage(packageName) }?.let { reason -> // gigu_core_legacy_ignore_bypass_0_1_90\n",
            )
        }

        if ("gigu_core_app_classifier_0_1_90" !in text) {
            val processStart = text.indexOf("    private suspend fun processRideText(")
            val processEnd = if (processStart >= 0) text.indexOf("    private fun resolveRidePackageForText(", processStart) else -1
            if (processStart < 0 || processEnd < 0) {
                throw GradleException("Nao encontrei os limites de processRideText.")
            }

            val legacyGateStart = text.indexOf(
                "        if (!RideOfferDetector.looksLikeRideOffer(snapshotText, fields, packageName)) {",
                processStart,
            )
            val acceptedGateEnd = text.indexOf("        registeredCardGate.markSeen()\n", legacyGateStart)
            if (legacyGateStart !in processStart until processEnd || acceptedGateEnd !in processStart until processEnd) {
                throw GradleException("Nao encontrei o bloco legado de oferta/match para substituir pelo Core por aplicativo.")
            }

            val coreGate = """        val coreScreenClassification = br.com.mapeiaia.rotacerta.core.RotaCertaCore.classifyScreen(
            packageName = packageName,
            text = snapshotText,
            fields = fields,
        )
        traceEvent("core.classify kind=${dollar}{coreScreenClassification.kind} confidence=${dollar}{coreScreenClassification.confidence} reason=${dollar}{coreScreenClassification.reason}") // gigu_core_app_classifier_0_1_90
        if (!coreScreenClassification.canAnalyzeRoute) {
            val reason = coreScreenClassification.reason
            if (allowPopupCandidate) return
            registeredCardGate.clear()
            saveCapturedReadToHistory(snapshotText, fields, snapshotHash, reason)
            resetToDefault(reason = reason, text = snapshotText, fields = fields)
            return
        }

        val coreCardMatch = br.com.mapeiaia.rotacerta.core.CoreCardMatchEngine.match(
            text = snapshotText,
            packageName = packageName,
            templates = currentCardTemplates,
        )
        val cardMatch = coreCardMatch.match
        if (!coreCardMatch.accepted || cardMatch == null) {
            val reason = coreCardMatch.reason
            traceEvent("core.card_match reject list=${dollar}{coreCardMatch.isListLike} package=${dollar}{packageName.orEmpty()} templates=${dollar}{currentCardTemplates.size} reason=${dollar}reason") // core_card_match_engine_0_1_94
            if (allowPopupCandidate) return
            registeredCardGate.clear()
            saveCapturedCardScreen(snapshotText, fields, snapshotHash, parseResult.parserName, packageName)
            saveCapturedReadToHistory(snapshotText, fields, snapshotHash, reason)
            resetToDefault(reason = reason, text = snapshotText, fields = fields)
            return
        }
        traceEvent("core.card_match accept name=${dollar}{cardMatch.template.name} score=${dollar}{cardMatch.score} reason=${dollar}{coreCardMatch.reason}") // core_card_match_engine_0_1_94

        val coreStableCardSignature = buildVisibleCardSignature(packageName, fields, cardMatch)
        val coreVisibleCardEvent = coreVisibleCardLifecycle.observe(
            packageName = packageName,
            snapshotHash = snapshotHash,
            text = snapshotText,
            stableSignature = coreStableCardSignature,
        )
        lastVisibleCardSignature = coreStableCardSignature
        if (coreVisibleCardEvent.action != br.com.mapeiaia.rotacerta.core.CoreVisibleCardAction.Same) {
            traceEvent("core.visible_card action=${dollar}{coreVisibleCardEvent.action} signature=${dollar}coreStableCardSignature reason=${dollar}{coreVisibleCardEvent.reason}") // core_visible_card_lifecycle_0_1_95 report_visible_card_after_match_0_1_86
        }
        val corePipelineVisible = coreLivePipeline.visibleCard( // core_live_pipeline_visible_card_0_1_96
            transaction = coreLivePipeline.transactionFor(snapshotHash)
                ?: coreLivePipeline.readReady(corePipelineTransaction, snapshotHash, snapshotText.length), // report_snapshot_read_binding_0_1_86
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

        val corePipelineCard = coreLivePipeline.cardAccepted(
            transaction = corePipelineVisible,
            contractName = coreCardMatch.contractName,
            cardTemplateName = cardMatch.template.name,
        )
        traceEvent("core.pipeline.card ${dollar}{corePipelineCard.traceSummary()}") // core_live_pipeline_card_0_1_96 gigu_core_pipeline_card_0_1_90
"""
            text = text.substring(0, legacyGateStart) + coreGate + text.substring(acceptedGateEnd)
        }

        if ("gigu_core_popup_classifier_0_1_90" !in text) {
            val popupStart = text.indexOf("    private fun looksLikeRegisteredPopupCandidate(text: String): Boolean {")
            val popupEnd = if (popupStart >= 0) text.indexOf("    private fun rememberSourceText(", popupStart) else -1
            if (popupStart < 0 || popupEnd < 0) {
                throw GradleException("Nao encontrei classificador de popup.")
            }
            val oldPopupBlock = """        val parseResult = parser.parseWithMetadata(text, packageName)
        if (!RideOfferDetector.looksLikeRideOffer(text, parseResult.fields, packageName)) return false
        return br.com.mapeiaia.rotacerta.core.CoreCardMatchEngine.match(text, packageName, currentCardTemplates).accepted // core_popup_card_match_0_1_94
"""
            if (oldPopupBlock !in text.substring(popupStart, popupEnd)) {
                throw GradleException("Nao encontrei gate legado de popup para mover ao Core por aplicativo.")
            }
            val newPopupBlock = """        val sanitizedText = br.com.mapeiaia.rotacerta.core.CoreRideTextSanitizer.sanitize(text, packageName)
        val parseResult = parser.parseWithMetadata(sanitizedText, packageName)
        val classification = br.com.mapeiaia.rotacerta.core.RotaCertaCore.classifyScreen(packageName, sanitizedText, parseResult.fields)
        if (!classification.canAnalyzeRoute) return false
        return br.com.mapeiaia.rotacerta.core.CoreCardMatchEngine.match(sanitizedText, packageName, currentCardTemplates).accepted // core_popup_card_match_0_1_94 gigu_core_popup_classifier_0_1_90
"""
            text = text.replaceFirst(oldPopupBlock, newPopupBlock)
        }

        listOf(
            "gigu_core_sanitized_snapshot_0_1_90",
            "gigu_core_semantic_hash_0_1_90",
            "gigu_core_legacy_ignore_bypass_0_1_90",
            "gigu_core_app_classifier_0_1_90",
            "gigu_core_pipeline_card_0_1_90",
            "gigu_core_popup_classifier_0_1_90",
            "report_visible_card_after_match_0_1_86",
            "report_snapshot_read_binding_0_1_86",
            "core_live_pipeline_visible_card_0_1_96",
            "transaction = corePipelineVisible",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Integracao do Core por aplicativo incompleta: $marker")
        }

        val processStart = text.indexOf("    private suspend fun processRideText(")
        val processEnd = text.indexOf("    private fun resolveRidePackageForText(", processStart)
        if (processStart >= 0 && processEnd > processStart &&
            "RideOfferDetector.looksLikeRideOffer(snapshotText" in text.substring(processStart, processEnd)
        ) {
            throw GradleException("O filtro legado ainda bloqueia processRideText antes do modulo por aplicativo.")
        }

        if (text != original) file.writeText(text)
    }
}

giguCoreClassificationPatch.configure {
    mustRunAfter(
        "coreScreenReadEngineInlinePatch",
        "coreCardMatchEnginePatch",
        "coreVisibleCardLifecyclePatch",
        "coreLiveAnalysisPipelinePatch",
        "reportStaleLifecycleFix",
        "reportStaleLifecycleCompileFix",
        "patchScreenPhoneWhatsApp",
        "giguInspiredLiveReaderPatch",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(giguCoreClassificationPatch)
}

apply(from = "patch-latest-card-wins.gradle.kts")
apply(from = "patch-same-card-route-coalescing.gradle.kts")
