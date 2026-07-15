// Impede que variacoes de OCR do mesmo card cancelem a rota em andamento.
// O cancelamento continua imediato quando pacote/modelo/destino/valor realmente mudam.

val sameCardRouteCoalescingPatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")

        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("private val coreCardAnalysisCoalescer" !in text) {
            val anchor = "    private var liveAnalysisJob: Job? = null\n"
            if (anchor !in text) throw GradleException("Nao encontrei liveAnalysisJob para instalar coalescencia.")
            text = text.replaceFirst(
                anchor,
                anchor + "    private val coreCardAnalysisCoalescer = br.com.mapeiaia.rotacerta.core.CoreCardAnalysisCoalescer()\n",
            )
        }

        if ("same_card_coalesce_invalidate_0_1_93" !in text) {
            val anchor = "        liveAnalysisJob = null\n        pendingAnalysis = null\n        analyzing = false\n"
            if (anchor !in text) throw GradleException("Nao encontrei invalidacao da analise ao vivo.")
            text = text.replaceFirst(
                anchor,
                "        liveAnalysisJob = null\n        pendingAnalysis = null\n        coreCardAnalysisCoalescer.invalidate() // same_card_coalesce_invalidate_0_1_93\n        analyzing = false\n",
            )
        }

        if ("same_card_coalesce_start_0_1_93" !in text) {
            val anchor = "        val latestAnalysisToken = ++analysisSerial\n        val previousAnalysisJob = liveAnalysisJob\n"
            if (anchor !in text) throw GradleException("Nao encontrei inicio latest-card-wins.")
            val block = """        val latestCardAnalysisSignature = lastVisibleCardSignature
            ?: buildVisibleCardSignature(packageName, fields, cardMatch)
        val previousAnalysisJob = liveAnalysisJob
        when (coreCardAnalysisCoalescer.beforeStart(
            signature = latestCardAnalysisSignature,
            activeJob = previousAnalysisJob?.isActive == true,
            hasAppliedDecision = currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red,
        )) {
            br.com.mapeiaia.rotacerta.core.CoreCardAnalysisAction.CoalesceActive -> {
                traceEvent("analysis.same_card coalesced=true signature=${dollar}latestCardAnalysisSignature hash=${dollar}snapshotHash") // same_card_coalesce_start_0_1_93
                return
            }
            br.com.mapeiaia.rotacerta.core.CoreCardAnalysisAction.ReuseCompleted -> {
                traceEvent("analysis.same_card reused=true signature=${dollar}latestCardAnalysisSignature hash=${dollar}snapshotHash")
                return
            }
            br.com.mapeiaia.rotacerta.core.CoreCardAnalysisAction.Start -> Unit
        }
        val latestAnalysisToken = ++analysisSerial
"""
            text = text.replaceFirst(anchor, block)
        }

        if ("analysisCardSignature = latestCardAnalysisSignature" !in text) {
            val anchor = "                    analysisToken = latestAnalysisToken,\n                )\n"
            if (anchor !in text) throw GradleException("Nao encontrei chamada de analyzeLiveText com token.")
            text = text.replaceFirst(
                anchor,
                "                    analysisToken = latestAnalysisToken,\n                    analysisCardSignature = latestCardAnalysisSignature,\n                )\n",
            )
        }

        if ("same_card_coalesce_timeout_0_1_93" !in text) {
            val anchor = "                analyzing = false\n                liveAnalysisJob = null\n                traceEvent(\"analysis.timeout latest_card=true"
            val at = text.indexOf(anchor)
            if (at < 0) throw GradleException("Nao encontrei timeout da analise atual.")
            val replacement = "                analyzing = false\n                liveAnalysisJob = null\n                coreCardAnalysisCoalescer.finish(latestCardAnalysisSignature) // same_card_coalesce_timeout_0_1_93\n                traceEvent(\"analysis.timeout latest_card=true"
            text = text.replaceFirst(anchor, replacement)
        }

        if ("analysisCardSignature: String?" !in text) {
            val anchor = "        analysisToken: Long = analysisSerial,\n    ) {"
            if (anchor !in text) throw GradleException("Nao encontrei assinatura final de analyzeLiveText.")
            text = text.replaceFirst(
                anchor,
                "        analysisToken: Long = analysisSerial,\n        analysisCardSignature: String? = lastVisibleCardSignature,\n    ) {",
            )
        }

        if ("same_card_coalesce_current_guard_0_1_93" !in text) {
            val oldGuard = "if (analysisToken != analysisSerial) {"
            if (oldGuard !in text) throw GradleException("Nao encontrei guardas de token da analise.")
            text = text.replace(
                oldGuard,
                "if (analysisToken != analysisSerial || !coreCardAnalysisCoalescer.isCurrent(analysisCardSignature)) { // same_card_coalesce_current_guard_0_1_93",
            )
        }

        if ("same_card_coalesce_visual_complete_0_1_93" !in text) {
            val analyzeStart = text.indexOf("    private suspend fun analyzeLiveText(")
            val analyzeEnd = if (analyzeStart >= 0) text.indexOf("\n    private ", analyzeStart + 1) else -1
            if (analyzeStart < 0 || analyzeEnd < 0) throw GradleException("Nao encontrei analyzeLiveText para confirmar visual.")
            val block = text.substring(analyzeStart, analyzeEnd)
            val overlayAt = block.lastIndexOf("            showOverlay(color = radarColor")
            if (overlayAt < 0) throw GradleException("Nao encontrei showOverlay final da decisao.")
            val overlayLineEnd = block.indexOf('\n', overlayAt)
            if (overlayLineEnd < 0) throw GradleException("Nao encontrei fim do showOverlay final.")
            val completion = """            if (radarColor == RadarColor.Green || radarColor == RadarColor.Red) {
                coreCardAnalysisCoalescer.complete(analysisCardSignature)
            } else {
                coreCardAnalysisCoalescer.finish(analysisCardSignature)
            } // same_card_coalesce_visual_complete_0_1_93
"""
            val nextBlock = block.substring(0, overlayLineEnd + 1) + completion + block.substring(overlayLineEnd + 1)
            text = text.substring(0, analyzeStart) + nextBlock + text.substring(analyzeEnd)
        }

        if ("same_card_coalesce_finish_0_1_93" !in text) {
            val anchor = "            if (analysisToken == analysisSerial) {\n                analyzing = false\n                liveAnalysisJob = null\n            } // latest_card_wins_finish_0_1_91\n"
            if (anchor !in text) throw GradleException("Nao encontrei finally latest-card-wins.")
            text = text.replaceFirst(
                anchor,
                "            if (analysisToken == analysisSerial) {\n                analyzing = false\n                liveAnalysisJob = null\n                coreCardAnalysisCoalescer.finish(analysisCardSignature)\n            } // latest_card_wins_finish_0_1_91 same_card_coalesce_finish_0_1_93\n",
            )
        }

        // A assinatura/token ja bloqueia resultado de card antigo. O primeiro calculo real
        // pode levar mais que 1,8 s sem ser descartado prematuramente.
        text = text.replace("const val LIVE_RESULT_MAX_AGE_MS = 1_800L", "const val LIVE_RESULT_MAX_AGE_MS = 7_500L")
        text = text.replace("const val LIVE_ANALYSIS_TIMEOUT_MS = 4_500L", "const val LIVE_ANALYSIS_TIMEOUT_MS = 8_000L")

        listOf(
            "same_card_coalesce_invalidate_0_1_93",
            "same_card_coalesce_start_0_1_93",
            "same_card_coalesce_timeout_0_1_93",
            "same_card_coalesce_current_guard_0_1_93",
            "same_card_coalesce_visual_complete_0_1_93",
            "same_card_coalesce_finish_0_1_93",
            "analysisCardSignature = latestCardAnalysisSignature",
            "const val LIVE_RESULT_MAX_AGE_MS = 7_500L",
            "const val LIVE_ANALYSIS_TIMEOUT_MS = 8_000L",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Coalescencia do mesmo card incompleta: $marker")
        }

        if (text != original) file.writeText(text)
    }
}

sameCardRouteCoalescingPatch.configure {
    mustRunAfter("latestCardWinsPatch")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(sameCardRouteCoalescingPatch)
}
