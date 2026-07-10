val liveCardAnalysisRaceFix by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast

        var text = file.readText()
        val original = text
        val dollar = "$"

        // A atualizacao da propria bolinha gera eventos com o pacote do Rota Certa.
        // Eles nao podem apagar o pacote de corrida ativo nem interromper a leitura.
        val oldActivePackageUpdate = """        if (eventPackageName != null) {
            activePackageName = if (isPassiveDiagnosticPackage(eventPackageName)) null else eventPackageName
        }
"""
        val newActivePackageUpdate = """        val ownAppMainWindowVisible = eventPackageName == this.packageName && isOwnAppMainWindowVisible()
        if (eventPackageName != null) {
            if (eventPackageName != this.packageName || ownAppMainWindowVisible) {
                activePackageName = if (isPassiveDiagnosticPackage(eventPackageName)) null else eventPackageName
            }
        }
        if (eventPackageName == this.packageName && !ownAppMainWindowVisible) {
            traceEvent("event self_overlay ignored active_ride=${dollar}{activePackageName.orEmpty()} root=${dollar}{currentRootPackageName().orEmpty()}") // live_card_analysis_race_fix_0_1_83
            return
        }
"""
        if (oldActivePackageUpdate in text) {
            text = text.replace(oldActivePackageUpdate, newActivePackageUpdate)
        }

        if ("private fun isOwnAppMainWindowVisible(): Boolean" !in text) {
            text = text.replace(
                "    private fun shouldScanCurrentWindow(): Boolean = shouldScanPackage(currentWindowPackageName())\n",
                """    private fun isOwnAppMainWindowVisible(): Boolean {
        val root = rootInActiveWindow ?: return false
        if (normalizePackageName(root.packageName?.toString()) != this.packageName) return false
        val lines = mutableListOf<String>()
        collectNodeText(root, lines)
        val normalized = lines.joinToString("\n")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
        val strongMarkers = listOf(
            "leitura ao vivo",
            "ferramentas",
            "configuracoes",
            "configurações",
            "modelos de cards",
            "casa/ponto principal",
            "alertas de proximidade",
            "gerar relatorio",
            "gerar relatório",
            "definir regiao de trabalho",
            "definir região de trabalho",
            "aparencia da bolinha",
            "aparência da bolinha",
            "rota certa ligado",
        )
        return root.childCount > 0 && normalized.length >= 40 && strongMarkers.any { marker -> marker in normalized }
    }

    private fun shouldScanCurrentWindow(): Boolean = shouldScanPackage(currentWindowPackageName())
""",
            )
        }

        // O resultado deve ser associado ao hash que iniciou a analise, nunca ao hash global
        // que pode ter sido trocado por um card mais novo enquanto geocodificacao/rota estavam rodando.
        text = text.replace(
            "            lastAnalyzedHash = lastSnapshotHash ?: snapshotHash\n",
            "            lastAnalyzedHash = snapshotHash // analysis_hash_bound_to_transaction_0_1_83\n",
        )

        // Se o card visivel mudou durante uma chamada de rede, descarta o resultado atrasado.
        val decisionTrace = """            traceEvent("decision.result recommendation=${dollar}{result.recommendation} reason=${dollar}{result.reason}")
            repository.addAnalysis(result)
"""
        if ("analysis.discard stale_card" !in text && decisionTrace in text) {
            text = text.replace(
                decisionTrace,
                """            traceEvent("decision.result recommendation=${dollar}{result.recommendation} reason=${dollar}{result.reason}")
            val analyzedCardSignature = cardMatch?.let { match ->
                buildVisibleCardSignature(lastTextPackageName ?: currentWindowPackageName(), fields, match)
            }
            if (analyzedCardSignature != null &&
                lastVisibleCardSignature != null &&
                analyzedCardSignature != lastVisibleCardSignature
            ) {
                lastAnalyzedHash = snapshotHash
                traceEvent("analysis.discard stale_card analyzed=${dollar}analyzedCardSignature visible=${dollar}lastVisibleCardSignature") // stale_result_guard_0_1_83
                return
            }
            repository.addAnalysis(result)
""",
            )
        }

        // A troca real de origem/destino/valor sempre libera uma nova analise,
        // mesmo se houver colisao de hash ou uma analise anterior ainda estiver terminando.
        val signatureChange = """        if (lastVisibleCardSignature != null && lastVisibleCardSignature != visibleCardSignature) {
            lastDecisionOverlayAtMillis = 0L
            traceEvent("visible_card.signature_changed previous=${dollar}lastVisibleCardSignature next=${dollar}visibleCardSignature") // bubble_render_stability_0_1_81
"""
        if (signatureChange in text) {
            text = text.replace(
                signatureChange,
                """        if (lastVisibleCardSignature != null && lastVisibleCardSignature != visibleCardSignature) {
            lastDecisionOverlayAtMillis = 0L
            lastAnalyzedHash = null // force_new_card_analysis_0_1_83
            traceEvent("visible_card.signature_changed previous=${dollar}lastVisibleCardSignature next=${dollar}visibleCardSignature") // bubble_render_stability_0_1_81
""",
            )
        }

        if ("live_card_analysis_race_fix_0_1_83" !in text) {
            throw org.gradle.api.GradleException("Nao consegui isolar eventos da propria bolinha da leitura do card.")
        }
        if ("analysis_hash_bound_to_transaction_0_1_83" !in text) {
            throw org.gradle.api.GradleException("Nao consegui vincular o hash ao card realmente analisado.")
        }
        if ("stale_result_guard_0_1_83" !in text) {
            throw org.gradle.api.GradleException("Nao consegui instalar a protecao contra resultado atrasado de outro card.")
        }
        if ("force_new_card_analysis_0_1_83" !in text) {
            throw org.gradle.api.GradleException("Nao consegui forcar a analise quando o card visivel muda.")
        }

        if (text != original) file.writeText(text)
    }
}

liveCardAnalysisRaceFix.configure {
    mustRunAfter(
        "liveRideWindowEventGuard",
        "keepDecisionDuringTransientText",
        "hardClearUnregisteredCardDecision",
        "modularLiveBubbleCore",
        "noStickyDecisionCleanup",
        "patchBubbleRenderStability",
        "passiveEventCompileFix",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(liveCardAnalysisRaceFix)
}
