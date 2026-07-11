// Liga o servico Android ao CoreLiveAnalysisPipeline.
// O pipeline registra a ordem profissional: pacote -> leitura -> card -> rota -> decisao -> visual.

val coreLiveAnalysisPipelinePatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("private val coreLivePipeline = br.com.mapeiaia.rotacerta.core.CoreLiveAnalysisPipeline()" !in text) {
            text = text.replace(
                "    private val registeredCardGate = RegisteredCardDecisionGate()\n",
                "    private val registeredCardGate = RegisteredCardDecisionGate()\n    private val coreLivePipeline = br.com.mapeiaia.rotacerta.core.CoreLiveAnalysisPipeline()\n",
            )
        }

        if ("core_live_pipeline_begin_0_1_96" !in text) {
            val stableStart = listOf(
                "        if (!allowPopupCandidate) {\n            rememberSourceText(packageName, source, text)",
                "        if (!allowPopupCandidate) {\n",
                "        rememberSourceText(packageName, source, text)",
                "        val snapshotText =",
                "        val coreReadSnapshot =",
            ).map { text.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1
            if (stableStart < 0) {
                throw org.gradle.api.GradleException("Nao encontrei inicio estavel de processRideText para ligar pipeline Core.")
            }
            val beginBlock = """        val corePipelineTransaction = coreLivePipeline.begin(
            packageName = packageName,
            source = source.name,
            rawLength = text.length,
            allowPopupCandidate = allowPopupCandidate,
        )
        traceEvent("core.pipeline.begin ${dollar}{corePipelineTransaction.traceSummary()}") // core_live_pipeline_begin_0_1_96
"""
            text = text.substring(0, stableStart) + beginBlock + text.substring(stableStart)
        }

        if ("core_live_pipeline_read_0_1_96" !in text) {
            val marker = when {
                "core_visible_card_lifecycle_0_1_95" in text -> {
                    val start = text.indexOf("        val coreVisibleCardEvent = coreVisibleCardLifecycle.observe(")
                    if (start >= 0) start else -1
                }
                "RideScreenTextClassifier.ignoreReason(snapshotText)" in text -> text.indexOf("        RideScreenTextClassifier.ignoreReason(snapshotText)")
                else -> -1
            }
            if (marker < 0) {
                throw org.gradle.api.GradleException("Nao encontrei ponto de leitura pronta para pipeline Core.")
            }
            val readBlock = """        val corePipelineRead = coreLivePipeline.readReady(
            transaction = corePipelineTransaction,
            snapshotHash = snapshotHash,
            textLength = snapshotText.length,
        )
        traceEvent("core.pipeline.read ${dollar}{corePipelineRead.traceSummary()}") // core_live_pipeline_read_0_1_96
"""
            text = text.substring(0, marker) + readBlock + text.substring(marker)
        }

        if ("core_live_pipeline_visible_card_0_1_96" !in text && "core_visible_card_lifecycle_0_1_95" in text) {
            val target = """        if (coreVisibleCardEvent.shouldClearPreviousDecision) {
"""
            val replacement = """        val corePipelineVisible = coreLivePipeline.visibleCard(
            transaction = corePipelineRead,
            action = coreVisibleCardEvent.action,
            visibleCardSignature = coreVisibleCardEvent.currentSignature,
        )
        traceEvent("core.pipeline.visible_card ${dollar}{corePipelineVisible.traceSummary()}") // core_live_pipeline_visible_card_0_1_96
        if (coreVisibleCardEvent.shouldClearPreviousDecision) {
"""
            if (target !in text) {
                throw org.gradle.api.GradleException("Nao encontrei evento de card visivel para pipeline Core.")
            }
            text = text.replace(target, replacement)
        }

        if ("core_live_pipeline_card_0_1_96" !in text) {
            val target = when {
                "traceEvent(\"core.card_match accept" in text -> {
                    val start = text.indexOf("        traceEvent(\"core.card_match accept")
                    val lineEnd = text.indexOf("\n", start)
                    if (start >= 0 && lineEnd >= 0) text.substring(start, lineEnd + 1) else null
                }
                "traceEvent(\"card_model.match" in text -> {
                    val start = text.indexOf("        traceEvent(\"card_model.match")
                    val lineEnd = text.indexOf("\n", start)
                    if (start >= 0 && lineEnd >= 0) text.substring(start, lineEnd + 1) else null
                }
                else -> null
            } ?: throw org.gradle.api.GradleException("Nao encontrei aceite de card para pipeline Core.")
            val replacement = target + """        val corePipelineCard = coreLivePipeline.cardAccepted(
            transaction = coreLivePipeline.currentTransaction() ?: corePipelineTransaction,
            contractName = runCatching { coreCardMatch.contractName }.getOrDefault("Core"),
            cardTemplateName = cardMatch.template.name,
        )
        traceEvent("core.pipeline.card ${dollar}{corePipelineCard.traceSummary()}") // core_live_pipeline_card_0_1_96
"""
            text = text.replace(target, replacement)
        }

        if ("core_live_pipeline_route_0_1_96" !in text) {
            val routeBlockAnalysis = """            val corePipelineRoute = coreLivePipeline.routeReady(
                transaction = coreLivePipeline.currentTransaction() ?: coreLivePipeline.begin(packageName, "analysis", text.length, allowPopupCandidate),
                fromCache = false,
            )
            traceEvent("core.pipeline.route ${dollar}{corePipelineRoute.traceSummary()}") // core_live_pipeline_route_0_1_96
"""
            val routeTarget = when {
                "core.route.cache hit=" in text -> {
                    val start = text.indexOf("            traceEvent(\"core.route.cache hit=")
                    val lineEnd = text.indexOf("\n", start)
                    if (start >= 0 && lineEnd >= 0) text.substring(start, lineEnd + 1) else null
                }
                "traceEvent(\"route.distance" in text -> {
                    val start = text.indexOf("            traceEvent(\"route.distance")
                    val lineEnd = text.indexOf("\n", start)
                    if (start >= 0 && lineEnd >= 0) text.substring(start, lineEnd + 1) else null
                }
                else -> null
            }
            if (routeTarget != null) {
                text = text.replace(routeTarget, routeTarget + routeBlockAnalysis)
            } else {
                val cardMarker = text.indexOf("core_live_pipeline_card_0_1_96")
                if (cardMarker < 0) {
                    throw org.gradle.api.GradleException("Nao encontrei card para fallback de rota do pipeline Core.")
                }
                val lineEnd = text.indexOf("\n", cardMarker).takeIf { it >= 0 }?.plus(1) ?: cardMarker
                val routeBlockAfterCard = """        val corePipelineRoute = coreLivePipeline.routeReady(
            transaction = coreLivePipeline.currentTransaction() ?: corePipelineTransaction,
            fromCache = false,
        )
        traceEvent("core.pipeline.route ${dollar}{corePipelineRoute.traceSummary()}") // core_live_pipeline_route_0_1_96
"""
                text = text.substring(0, lineEnd) + routeBlockAfterCard + text.substring(lineEnd)
            }
        }

        if ("core_live_pipeline_decision_0_1_96" !in text) {
            val decisionBlock = """            val corePipelineDecision = coreLivePipeline.decisionReady(
                transaction = coreLivePipeline.currentTransaction() ?: coreLivePipeline.begin(packageName, "analysis", text.length, allowPopupCandidate),
                recommendation = result.recommendation,
                distanceKm = coreBubbleDecision.distanceKm,
            )
            traceEvent("core.pipeline.decision ${dollar}{corePipelineDecision.traceSummary()}") // core_live_pipeline_decision_0_1_96
"""
            val coreBubbleApplyStart = text.indexOf("            traceEvent(\"core.bubble apply")
            if (coreBubbleApplyStart >= 0) {
                text = text.substring(0, coreBubbleApplyStart) + decisionBlock + text.substring(coreBubbleApplyStart)
            } else {
                val oldTarget = """            traceEvent("decision.result recommendation=${dollar}{result.recommendation} reason=${dollar}{result.reason}")
"""
                if (oldTarget !in text) {
                    throw org.gradle.api.GradleException("Nao encontrei resultado de decisao para pipeline Core.")
                }
                val replacement = """            val corePipelineDecision = coreLivePipeline.decisionReady(
                transaction = coreLivePipeline.currentTransaction() ?: coreLivePipeline.begin(packageName, "analysis", text.length, allowPopupCandidate),
                recommendation = result.recommendation,
                distanceKm = result.nearestConfiguredDistanceKm(),
            )
            traceEvent("core.pipeline.decision ${dollar}{corePipelineDecision.traceSummary()}") // core_live_pipeline_decision_0_1_96
            traceEvent("decision.result recommendation=${dollar}{result.recommendation} reason=${dollar}{result.reason}")
"""
                text = text.replace(oldTarget, replacement)
            }
        }

        if ("core_live_pipeline_visual_0_1_96" !in text) {
            val coreTarget = """            showOverlay(color = radarColor, distanceKm = coreBubbleDecision.distanceKm)
"""
            val oldTarget = """            showOverlay(color = radarColor, distanceKm = result.nearestConfiguredDistanceKm())
"""
            val target = when {
                coreTarget in text -> coreTarget
                oldTarget in text -> oldTarget
                else -> throw org.gradle.api.GradleException("Nao encontrei aplicacao visual para pipeline Core.")
            }
            val freshnessBlock = """            val coreFreshnessDecision = br.com.mapeiaia.rotacerta.core.CoreFreshnessGuard.evaluate(
                transaction = coreLivePipeline.currentTransaction(),
                currentPackageName = packageName,
                currentSnapshotHash = snapshotHash,
                currentVisibleCardSignature = lastVisibleCardSignature,
            )
            if (!coreFreshnessDecision.fresh) {
                traceEvent("core.freshness stale reason=${dollar}{coreFreshnessDecision.reason}") // core_freshness_guard_0_1_97
                recordDiagnostic(
                    stage = "stale_result",
                    reason = coreFreshnessDecision.reason,
                    text = text,
                    fields = fields,
                    result = result,
                    cardTemplateMatch = cardMatch,
                )
                return
            }
            traceEvent("core.freshness fresh reason=${dollar}{coreFreshnessDecision.reason}") // core_freshness_guard_0_1_97
"""
            val replacement = freshnessBlock + """            val corePipelineVisual = coreLivePipeline.visualApplied(
                transaction = coreLivePipeline.currentTransaction() ?: coreLivePipeline.begin(packageName, "analysis", text.length, allowPopupCandidate),
                mode = when (radarColor) {
                    RadarColor.Green -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Good
                    RadarColor.Red -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Bad
                    RadarColor.Default -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Waiting
                    RadarColor.Idle -> br.com.mapeiaia.rotacerta.core.CoreBubbleMode.Hidden
                },
            )
            traceEvent("core.pipeline.visual ${dollar}{corePipelineVisual.traceSummary()}") // core_live_pipeline_visual_0_1_96
""" + target
            text = text.replace(target, replacement)
        }

        listOf(
            "core_live_pipeline_begin_0_1_96",
            "core_live_pipeline_read_0_1_96",
            "core_live_pipeline_card_0_1_96",
            "core_live_pipeline_route_0_1_96",
            "core_live_pipeline_decision_0_1_96",
            "core_live_pipeline_visual_0_1_96",
            "core_freshness_guard_0_1_97",
        ).forEach { marker ->
            if (marker !in text) throw org.gradle.api.GradleException("Marcador do pipeline ausente: $marker")
        }
        if ("private val coreLivePipeline = br.com.mapeiaia.rotacerta.core.CoreLiveAnalysisPipeline()" !in text) {
            throw org.gradle.api.GradleException("CoreLiveAnalysisPipeline nao foi instalado no servico.")
        }

        if (text != original) file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(coreLiveAnalysisPipelinePatch)
}
