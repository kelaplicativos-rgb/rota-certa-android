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

        // Eventos gerados pela propria bolinha carregam o pacote do Rota Certa.
        // A protecao entra antes da atualizacao de activePackageName para preservar
        // o app de corrida que continua realmente visivel abaixo do overlay.
        if ("live_card_analysis_race_fix_0_1_83" !in text) {
            val packageAnchor = "        val packageName = eventPackageName ?: currentRootPackageName()\n"
            if (packageAnchor !in text) {
                throw org.gradle.api.GradleException("Nao encontrei o ponto de identificacao do pacote ativo.")
            }
            text = text.replace(
                packageAnchor,
                packageAnchor + """        val ownAppMainWindowVisible = eventPackageName == this.packageName && isOwnAppMainWindowVisible()
        if (eventPackageName == this.packageName && !ownAppMainWindowVisible) {
            traceEvent("event self_overlay ignored active_ride=${dollar}{activePackageName.orEmpty()} root=${dollar}{currentRootPackageName().orEmpty()}") // live_card_analysis_race_fix_0_1_83
            return
        }
""",
            )
        }

        if ("private fun isOwnAppMainWindowVisible(): Boolean" !in text) {
            val currentWindowAnchor = "    private fun currentWindowPackageName(): String?"
            val insertionIndex = text.indexOf(currentWindowAnchor)
            if (insertionIndex < 0) {
                throw org.gradle.api.GradleException("Nao encontrei o resolvedor da janela atual.")
            }
            val helper = """    private fun isOwnAppMainWindowVisible(): Boolean {
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

"""
            text = text.substring(0, insertionIndex) + helper + text.substring(insertionIndex)
        }

        // O resultado deve ser associado ao hash que iniciou a analise, nunca ao hash global
        // que pode ter sido trocado por um card mais novo enquanto geocodificacao/rota estavam rodando.
        text = text.replace(
            "            lastAnalyzedHash = lastSnapshotHash ?: snapshotHash\n",
            "            lastAnalyzedHash = snapshotHash // analysis_hash_bound_to_transaction_0_1_83\n",
        )

        // Se o card visivel mudou durante uma chamada de rede, descarta o resultado atrasado
        // exatamente antes de persistir/aplicar a decisao calculada.
        if ("stale_result_guard_0_1_83" !in text) {
            val persistenceAnchor = "            repository.addAnalysis(result)\n"
            val persistenceIndex = text.indexOf(persistenceAnchor)
            if (persistenceIndex < 0) {
                throw org.gradle.api.GradleException("Nao encontrei o ponto de persistencia do resultado da corrida.")
            }
            val staleGuard = """            val analyzedCardSignature = cardMatch?.let { match ->
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
"""
            text = text.substring(0, persistenceIndex) + staleGuard + text.substring(persistenceIndex)
        }

        // A troca real de origem/destino/valor sempre libera uma nova analise.
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
