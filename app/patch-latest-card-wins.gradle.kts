// Correcao final da leitura ao vivo 0.1.91.
// O card mais recente sempre vence: uma rota antiga nao pode bloquear nem pintar a bolinha
// depois que o card saiu, mudou ou foi substituido por outro.

val latestCardWinsPatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")

        var text = file.readText()
        val original = text
        val dollar = "$"

        fun requireMarker(marker: String, message: String) {
            if (marker !in text) throw GradleException(message)
        }

        fun insertAtFunctionBody(functionName: String, statement: String, marker: String) {
            if (marker in text) return
            val start = text.indexOf("    private fun $functionName")
            if (start < 0) throw GradleException("Nao encontrei a funcao $functionName.")
            val openBrace = text.indexOf('{', start)
            val lineEnd = if (openBrace >= 0) text.indexOf('\n', openBrace) else -1
            if (openBrace < 0 || lineEnd < 0) throw GradleException("Nao encontrei o corpo da funcao $functionName.")
            text = text.substring(0, lineEnd + 1) + statement + text.substring(lineEnd + 1)
        }

        if ("private var analysisSerial: Long = 0L" !in text) {
            val anchor = "    private var analyzing = false\n"
            if (anchor !in text) throw GradleException("Nao encontrei o estado analyzing da leitura ao vivo.")
            text = text.replaceFirst(
                anchor,
                anchor +
                    "    private var analysisSerial: Long = 0L\n" +
                    "    private var liveAnalysisJob: Job? = null\n",
            )
        }

        if ("latest_card_wins_invalidate_0_1_91" !in text) {
            val anchor = "    private suspend fun analyzeLiveText("
            if (anchor !in text) throw GradleException("Nao encontrei analyzeLiveText para instalar o cancelamento.")
            val helper = """    private fun invalidateLiveAnalysis(reason: String) {
        val previousJob = liveAnalysisJob
        if (previousJob?.isActive == true) {
            traceEvent("analysis.latest_card_wins cancel reason=${dollar}reason serial=${dollar}analysisSerial") // latest_card_wins_invalidate_0_1_91
        }
        analysisSerial += 1L
        previousJob?.cancel()
        liveAnalysisJob = null
        pendingAnalysis = null
        analyzing = false
    }

"""
            text = text.replaceFirst(anchor, helper + anchor)
        }

        if ("latest_card_wins_destroy_0_1_91" !in text) {
            val destroyStart = text.indexOf("    override fun onDestroy() {")
            val destroyEnd = if (destroyStart >= 0) text.indexOf("    private fun startContinuousScan()", destroyStart) else -1
            val anchor = "        analyzeJob?.cancel()\n"
            val anchorAt = if (destroyStart >= 0 && destroyEnd > destroyStart) text.indexOf(anchor, destroyStart) else -1
            if (anchorAt !in destroyStart until destroyEnd) throw GradleException("Nao encontrei cancelamento de jobs em onDestroy.")
            text = text.substring(0, anchorAt + anchor.length) +
                "        liveAnalysisJob?.cancel() // latest_card_wins_destroy_0_1_91\n" +
                text.substring(anchorAt + anchor.length)
        }

        insertAtFunctionBody(
            functionName = "resetToDefault(",
            statement = "        invalidateLiveAnalysis(\"reset_default:${dollar}reason\") // latest_card_wins_reset_default_0_1_91\n",
            marker = "latest_card_wins_reset_default_0_1_91",
        )
        insertAtFunctionBody(
            functionName = "resetToIdle(",
            statement = "        invalidateLiveAnalysis(\"reset_idle:${dollar}reason\") // latest_card_wins_reset_idle_0_1_91\n",
            marker = "latest_card_wins_reset_idle_0_1_91",
        )

        if ("latest_card_wins_launch_0_1_91" !in text) {
            val processStart = text.indexOf("    private suspend fun processRideText(")
            val processEnd = if (processStart >= 0) text.indexOf("    private fun resolveRidePackageForText(", processStart) else -1
            if (processStart < 0 || processEnd < 0) throw GradleException("Nao encontrei os limites de processRideText.")

            val duplicateGuard = text.indexOf("        if (snapshotHash == lastAnalyzedHash) {", processStart)
            val oldAnalyzingBlock = if (duplicateGuard >= 0) text.indexOf("        if (analyzing) {", duplicateGuard) else -1
            val analyzeCall = if (oldAnalyzingBlock >= 0) {
                text.indexOf(
                    "        analyzeLiveText(snapshotText, fields, snapshotHash, cardMatch, allowPopupCandidate)",
                    oldAnalyzingBlock,
                )
            } else {
                -1
            }
            val analyzeCallEnd = if (analyzeCall >= 0) text.indexOf('\n', analyzeCall) else -1
            if (duplicateGuard !in processStart until processEnd ||
                oldAnalyzingBlock !in duplicateGuard until processEnd ||
                analyzeCall !in oldAnalyzingBlock until processEnd ||
                analyzeCallEnd < analyzeCall
            ) {
                throw GradleException("Nao encontrei o bloco antigo que enfileirava a analise e travava o proximo card.")
            }

            val launchBlock = """        val latestAnalysisToken = ++analysisSerial
        val previousAnalysisJob = liveAnalysisJob
        if (previousAnalysisJob?.isActive == true) {
            traceEvent("analysis.latest_card_wins supersede=true hash=${dollar}snapshotHash token=${dollar}latestAnalysisToken") // latest_card_wins_launch_0_1_91
        }
        previousAnalysisJob?.cancel()
        pendingAnalysis = null
        liveAnalysisJob = scope.launch {
            val completed = kotlinx.coroutines.withTimeoutOrNull(LIVE_ANALYSIS_TIMEOUT_MS) {
                analyzeLiveText(
                    text = snapshotText,
                    fields = fields,
                    snapshotHash = snapshotHash,
                    cardMatch = cardMatch,
                    allowPopupCandidate = allowPopupCandidate,
                    analysisToken = latestAnalysisToken,
                )
                true
            } ?: false
            if (!completed && latestAnalysisToken == analysisSerial) {
                analyzing = false
                liveAnalysisJob = null
                traceEvent("analysis.timeout latest_card=true hash=${dollar}snapshotHash token=${dollar}latestAnalysisToken") // latest_card_wins_timeout_0_1_91
                if (shouldScanCurrentWindow()) {
                    showOverlay(RadarColor.Default)
                    recordDiagnostic(
                        stage = "analysis_timeout",
                        reason = "A rota do card atual excedeu o limite; mantive a bolinha amarela e descartei qualquer resultado antigo.",
                        text = snapshotText,
                        fields = fields,
                        cardTemplateMatch = cardMatch,
                    )
                }
            }
        }
        return
"""
            text = text.substring(0, oldAnalyzingBlock) + launchBlock + text.substring(analyzeCallEnd + 1)
        }

        run {
            val analyzeStart = text.indexOf("    private suspend fun analyzeLiveText(")
            val analyzeEnd = if (analyzeStart >= 0) text.indexOf("\n    private ", analyzeStart + 1) else -1
            if (analyzeStart < 0 || analyzeEnd < 0) throw GradleException("Nao encontrei os limites de analyzeLiveText.")
            var block = text.substring(analyzeStart, analyzeEnd)

            if ("analysisToken: Long" !in block) {
                val signatureTail = "        allowPopupCandidate: Boolean = false,\n    ) {"
                if (signatureTail !in block) throw GradleException("Nao encontrei o final da assinatura de analyzeLiveText.")
                block = block.replaceFirst(
                    signatureTail,
                    "        allowPopupCandidate: Boolean = false,\n        analysisToken: Long = analysisSerial,\n    ) {",
                )
            }

            block = block.replace(
                "if (!serviceReady || (!allowPopupCandidate && !shouldScanCurrentWindow()) || analyzing) return",
                "if (!serviceReady || (!allowPopupCandidate && !shouldScanCurrentWindow())) return",
            )

            if ("latest_card_wins_analysis_start_0_1_91" !in block) {
                val anchor = "        analyzing = true\n"
                if (anchor !in block) throw GradleException("Nao encontrei o inicio da analise ao vivo.")
                block = block.replaceFirst(
                    anchor,
                    anchor + "        traceEvent(\"analysis.latest_card_wins start token=${dollar}analysisToken hash=${dollar}snapshotHash\") // latest_card_wins_analysis_start_0_1_91\n",
                )
            }

            // Algumas versoes do Core persistem o resultado em outro modulo. Quando a
            // chamada ainda existe aqui, tambem a protegemos; quando nao existe, a trava
            // decisiva permanece imediatamente antes da cor/km.
            if ("latest_card_wins_drop_before_store_0_1_91" !in block) {
                val storeAnchor = "            repository.addAnalysis(result)\n"
                if (storeAnchor in block) {
                    val guard = """            if (analysisToken != analysisSerial) {
                traceEvent("analysis.drop_stale_result phase=store token=${dollar}analysisToken current=${dollar}analysisSerial hash=${dollar}snapshotHash") // latest_card_wins_drop_before_store_0_1_91
                return
            }
"""
                    block = block.replaceFirst(storeAnchor, guard + storeAnchor)
                }
            }

            if ("latest_card_wins_drop_before_visual_0_1_91" !in block) {
                val visualAt = block.lastIndexOf("            showOverlay(color = radarColor")
                if (visualAt < 0) throw GradleException("Nao encontrei a aplicacao visual da decisao.")
                val guard = """            if (analysisToken != analysisSerial) {
                traceEvent("analysis.drop_stale_result phase=visual token=${dollar}analysisToken current=${dollar}analysisSerial hash=${dollar}snapshotHash") // latest_card_wins_drop_before_visual_0_1_91
                return
            }
"""
                block = block.substring(0, visualAt) + guard + block.substring(visualAt)
            }

            if ("latest_card_wins_cancel_rethrow_0_1_91" !in block) {
                val catchAnchor = "        } catch (error: Exception) {\n"
                if (catchAnchor !in block) throw GradleException("Nao encontrei o tratamento de erro da analise.")
                val catchGuard = """        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error // latest_card_wins_cancel_rethrow_0_1_91
            if (analysisToken != analysisSerial) {
                traceEvent("analysis.drop_stale_result phase=error token=${dollar}analysisToken current=${dollar}analysisSerial hash=${dollar}snapshotHash")
                return
            }
"""
                block = block.replaceFirst(catchAnchor, catchGuard)
            }

            if ("latest_card_wins_finish_0_1_91" !in block) {
                val finallyAnchor = "            analyzing = false\n"
                if (finallyAnchor !in block) throw GradleException("Nao encontrei a finalizacao da analise.")
                block = block.replaceFirst(
                    finallyAnchor,
                    "            if (analysisToken == analysisSerial) {\n                analyzing = false\n                liveAnalysisJob = null\n            } // latest_card_wins_finish_0_1_91\n",
                )
            }

            text = text.substring(0, analyzeStart) + block + text.substring(analyzeEnd)
        }

        if ("const val LIVE_ANALYSIS_TIMEOUT_MS" !in text) {
            val anchor = "        const val PROXIMITY_ALERT_LOOP_MS = 15_000L\n"
            if (anchor !in text) throw GradleException("Nao encontrei as constantes do servico.")
            text = text.replaceFirst(
                anchor,
                anchor + "        const val LIVE_ANALYSIS_TIMEOUT_MS = 4_500L\n",
            )
        }

        listOf(
            "latest_card_wins_invalidate_0_1_91",
            "latest_card_wins_destroy_0_1_91",
            "latest_card_wins_reset_default_0_1_91",
            "latest_card_wins_reset_idle_0_1_91",
            "latest_card_wins_launch_0_1_91",
            "latest_card_wins_timeout_0_1_91",
            "latest_card_wins_analysis_start_0_1_91",
            "latest_card_wins_drop_before_visual_0_1_91",
            "latest_card_wins_cancel_rethrow_0_1_91",
            "latest_card_wins_finish_0_1_91",
            "const val LIVE_ANALYSIS_TIMEOUT_MS =",
        ).forEach { marker -> requireMarker(marker, "Correcao latest-card-wins incompleta: $marker") }

        if ("analysis.defer analyzing=true" in text) {
            throw GradleException("O caminho antigo ainda enfileira o card e bloqueia a leitura atual.")
        }

        if (text != original) file.writeText(text)
    }
}

latestCardWinsPatch.configure {
    mustRunAfter(
        "giguCoreClassificationPatch",
        "giguInspiredLiveReaderPatch",
        "coreLiveAnalysisPipelinePatch",
        "coreVisibleCardLifecyclePatch",
        "patchBubbleRenderStability",
        "reportStaleLifecycleFix",
        "reportStaleLifecycleCompileFix",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(latestCardWinsPatch)
}
