// Rota Certa Core Stable
// Consolida as correcoes finais da bolinha que antes estavam espalhadas em patches separados:
// - preservacao curta de decisao valida durante texto/hash transitorio;
// - estabilidade visual sem limpar verde/vermelho por oscilacao de OCR;
// - protecao contra resultado atrasado de outro card.

val noStickyDecisionCleanup by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val coreStateFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/core/CoreBubbleStateController.kt")
    inputs.file(serviceFile)
    inputs.file(coreStateFile)
    outputs.upToDateWhen { false }

    doLast {
        serviceFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text
            val dollar = "$"
            val coreStateText = coreStateFile.asFile.takeIf { it.exists() }?.readText().orEmpty()
            val hasRealCoreMissingCardGuard = "isMissingRegisteredCardReason" in coreStateText &&
                "CoreBubbleMode.Waiting" in coreStateText &&
                "distanceKm = null" in coreStateText

            text = text.replace(
"""        if ((color == RadarColor.Default || color == RadarColor.Idle) &&
            hasActiveRegisteredDecision() &&
            shouldScanCurrentWindow() &&
            now - lastDecisionOverlayAtMillis < DECISION_OVERLAY_STICKY_MS
        ) {
            traceEvent("overlay.keep_decision color=${dollar}{currentRadarColor.diagnosticLabel} requested=${dollar}{color.diagnosticLabel}")
            return
        }
""",
"""        if ((color == RadarColor.Default || color == RadarColor.Idle) &&
            hasActiveRegisteredDecision() &&
            shouldScanCurrentWindow() &&
            now - lastDecisionOverlayAtMillis < DECISION_OVERLAY_STICKY_MS
        ) {
            traceEvent("overlay.keep_valid_decision_transient requested=${dollar}{color.diagnosticLabel} previous=${dollar}{currentRadarColor.diagnosticLabel}") // preserve_valid_decision_0_1_84
            return
        }
""",
            )
            text = text.replace(
"""        if ((color == RadarColor.Default || color == RadarColor.Idle) &&
            hasActiveRegisteredDecision() &&
            shouldScanCurrentWindow() &&
            now - lastDecisionOverlayAtMillis < DECISION_OVERLAY_STICKY_MS
        ) {
            traceEvent("overlay.clear_previous_decision requested=${dollar}{color.diagnosticLabel} previous=${dollar}{currentRadarColor.diagnosticLabel}") // no_sticky_decision_cleanup_0_1_79
            registeredCardGate.clear()
            lastDecisionOverlayAtMillis = 0L
        }
""",
"""        if ((color == RadarColor.Default || color == RadarColor.Idle) &&
            hasActiveRegisteredDecision() &&
            shouldScanCurrentWindow() &&
            now - lastDecisionOverlayAtMillis < DECISION_OVERLAY_STICKY_MS
        ) {
            traceEvent("overlay.keep_valid_decision_transient requested=${dollar}{color.diagnosticLabel} previous=${dollar}{currentRadarColor.diagnosticLabel}") // preserve_valid_decision_0_1_84
            return
        }
""",
            )

            if (!hasRealCoreMissingCardGuard && "forceMissingCardOverlayDefault" !in text) {
                text = text.replace(
"""        if (color == RadarColor.Green || color == RadarColor.Red) lastDecisionOverlayAtMillis = now
        val nextText = formatBubbleDistanceKm(distanceKm)
        if (currentRadarColor == color && currentDistanceKm == distanceKm && overlayView?.text?.toString() == nextText) return
        currentRadarColor = color
        currentDistanceKm = distanceKm
""",
"""        val forceMissingCardOverlayDefault = lastBubbleStateReason.contains("ainda nao bate com nenhum card cadastrado", ignoreCase = true) ||
            lastBubbleStateReason.contains("cadastre o modelo para liberar o farol", ignoreCase = true) ||
            lastBubbleStateReason.contains("tela nao confirmada por card cadastrado", ignoreCase = true)
        val safeColor = if (forceMissingCardOverlayDefault) RadarColor.Default else color
        val safeDistanceKm = if (forceMissingCardOverlayDefault) null else distanceKm
        if (forceMissingCardOverlayDefault) {
            traceEvent("overlay.force_missing_card_default requested=${dollar}{color.diagnosticLabel} previous=${dollar}{currentRadarColor.diagnosticLabel}") // force_missing_card_overlay_default_0_1_80
            registeredCardGate.clear()
            lastDecisionOverlayAtMillis = 0L
        }
        if (safeColor == RadarColor.Green || safeColor == RadarColor.Red) lastDecisionOverlayAtMillis = now
        val nextText = formatBubbleDistanceKm(safeDistanceKm)
        if (currentRadarColor == safeColor && currentDistanceKm == safeDistanceKm && overlayView?.text?.toString() == nextText) return
        currentRadarColor = safeColor
        currentDistanceKm = safeDistanceKm
""",
                )
            }

            text = text.replace(
                "val keepActiveDecisionDuringTransientText = false // no_sticky_decision_cleanup_0_1_79",
                "val keepActiveDecisionDuringTransientText = hasActiveRegisteredDecision() && shouldScanCurrentWindow() // preserve_valid_decision_0_1_84",
            )
            text = text.replace(
                Regex("""val keepActiveDecisionForTransientInsufficient = false // no_sticky_decision_cleanup_0_1_79"""),
                "val keepActiveDecisionForTransientInsufficient = computedRadarColor == RadarColor.Default && !missingRegisteredCardDecision && hasActiveRegisteredDecision() && shouldScanCurrentWindow() // preserve_valid_decision_0_1_84",
            )

            text = text.replace(
"""            registeredCardGate.clear()
            lastDecisionOverlayAtMillis = 0L // no_sticky_decision_cleanup_0_1_79
            resetToDefault(reason = "Texto visivel vazio; nenhum card lido neste momento.", record = true)
""",
"""            if (hasActiveRegisteredDecision() && shouldScanCurrentWindow()) {
                traceEvent("process.empty_text keep_valid_decision_transient=true") // preserve_valid_decision_0_1_84
                recordDiagnostic(
                    stage = "screen_changed",
                    reason = "Texto visivel ficou vazio por instantes; mantive a decisao atual ate confirmar saida real do card.",
                    text = null,
                )
                return
            }
            registeredCardGate.clear()
            lastDecisionOverlayAtMillis = 0L
            resetToDefault(reason = "Texto visivel vazio; nenhum card lido neste momento.", record = true)
""",
            )

            text = text.replace(
"""        if (shouldScanCurrentWindow() && hasActiveRegisteredDecision()) {
            traceEvent("resetToIdle clear_active_ride_window reason=${dollar}reason") // no_sticky_decision_cleanup_0_1_79
        }
""",
"""        if (shouldScanCurrentWindow() && hasActiveRegisteredDecision()) {
            traceEvent("resetToIdle keep_valid_decision_guard reason=${dollar}reason") // preserve_valid_decision_0_1_84
            return
        }
""",
            )

            text = text.replace(
                "Pacote passivo ignorado sem apagar a ultima decisao:",
                "Pacote passivo ignorado; bolinha limpa:",
            )

            val hasPreserveDecisionPath = "preserve_valid_decision_0_1_84" in text ||
                "screen_changed.keep_active_decision" in text ||
                "process.empty_text keep_active_decision=true" in text ||
                "analysis.transient_insufficient keep_active_decision=true" in text
            val hasLegacyMissingCardOverlayGuard = "force_missing_card_overlay_default_0_1_80" in text
            if (!hasLegacyMissingCardOverlayGuard && !hasRealCoreMissingCardGuard) {
                throw org.gradle.api.GradleException("Nao encontrei trava real no Core nem trava legada para card nao cadastrado.")
            }
            if (!hasPreserveDecisionPath) {
                throw org.gradle.api.GradleException("Nao encontrei nenhum caminho ativo para preservar decisao valida transitoria.")
            }
            if ("overlay.clear_previous_decision" in text) {
                throw org.gradle.api.GradleException("A bolinha ainda contem limpeza agressiva de decisao valida.")
            }

            if (text != original) file.writeText(text)
        }
    }
}

noStickyDecisionCleanup.configure {
    mustRunAfter(
        "patchLiveRideOverlayStability",
        "patchBubbleStateReport",
        "liveRideWindowEventGuard",
        "keepDecisionDuringTransientText",
        "hardClearUnregisteredCardDecision",
        "modularLiveBubbleCore",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(noStickyDecisionCleanup)
}

val patchBubbleRenderStability by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast

        fun String.withoutExistingVisibleCardSignatureGuards(): String {
            var current = this
            val startToken = "        val visibleCardSignature = buildVisibleCardSignature(packageName, fields, cardMatch)\n"
            val endToken = "        lastVisibleCardSignature = visibleCardSignature\n"
            while (true) {
                val start = current.indexOf(startToken)
                if (start < 0) return current
                val end = current.indexOf(endToken, start)
                if (end < 0) return current
                current = current.removeRange(start, end + endToken.length)
            }
        }

        var text = file.readText()
        val original = text

        if ("private var lastVisibleCardSignature: String? = null" !in text) {
            text = text.replace(
                "    private var lastDecisionOverlayAtMillis: Long = 0L\n",
                "    private var lastDecisionOverlayAtMillis: Long = 0L\n    private var lastVisibleCardSignature: String? = null\n",
            )
        }

        if ("private fun buildVisibleCardSignature(" !in text) {
            text = text.replace(
                "    private fun hasActiveRegisteredDecision(): Boolean =\n",
                """    private fun buildVisibleCardSignature(
        packageName: String?,
        fields: RideFields,
        cardMatch: RideCardTemplateMatch,
    ): String = listOf(
        normalizePackageName(packageName).orEmpty(),
        cardMatch.template.id,
        fields.fare.stableSignaturePart(),
    ).joinToString("|")

    private fun String?.stableSignaturePart(): String =
        this.orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)

    private fun hasActiveRegisteredDecision(): Boolean =
""",
            )
        }

        if ("bubble_render_stability_clear_signature_0_1_81" !in text) {
            text = text.replace(
                "registeredCardGate.clear()",
                "registeredCardGate.clear()\n            lastVisibleCardSignature = null // bubble_render_stability_clear_signature_0_1_81",
            )
        }

        if ("screen_changed.defer_visual_until_card_match" !in text && "bubble_render_stability_quiet_defer_0_1_83" !in text) {
            text = text.replace(
                """                lastAnalyzedHash = null
                showOverlay(RadarColor.Default)
""",
                """                lastAnalyzedHash = null
                // bubble_render_stability_quiet_defer_0_1_83: nao renderiza amarelo nem registra log repetido ate bater com card cadastrado.
""",
            )
        }
        text = text.replace(
            """                traceEvent("screen_changed.defer_visual_until_card_match source=${'$'}source hash=${'$'}snapshotHash") // bubble_render_stability_0_1_81
""",
            """                // bubble_render_stability_quiet_defer_0_1_83: tela mudou, mas a bolinha so registra/aplica estado depois do match do card.
""",
        )

        text = text.withoutExistingVisibleCardSignatureGuards()
        val duplicateHashGuard = "        if (snapshotHash == lastAnalyzedHash) {\n"
        val insertionPoint = text.indexOf(duplicateHashGuard)
        if (insertionPoint >= 0) {
            val signatureGuard = """        val visibleCardSignature = buildVisibleCardSignature(packageName, fields, cardMatch)
        if (lastVisibleCardSignature != null && lastVisibleCardSignature != visibleCardSignature) {
            traceEvent("visible_card.signature_changed transient_previous=${'$'}lastVisibleCardSignature next=${'$'}visibleCardSignature") // bubble_render_signature_no_clear_0_1_84
        }
        lastVisibleCardSignature = visibleCardSignature
"""
            text = text.substring(0, insertionPoint) + signatureGuard + text.substring(insertionPoint)
        }

        if ("bubble_render_background_uses_current_state_0_1_81" !in text) {
            text = text.replace(
                "setColor(color.argb(currentSettings))",
                "setColor(currentRadarColor.argb(currentSettings)) // bubble_render_background_uses_current_state_0_1_81",
            )
        }

        if ("bubble_render_stability_quiet_defer_0_1_83" !in text) {
            throw org.gradle.api.GradleException("Nao consegui instalar a reducao de ruido do screen_changed da bolinha.")
        }
        if ("bubble_render_signature_no_clear_0_1_84" !in text) {
            throw org.gradle.api.GradleException("Nao consegui impedir limpeza por oscilacao de assinatura OCR.")
        }
        if (text.indexOf("val visibleCardSignature = buildVisibleCardSignature") < 0) {
            throw org.gradle.api.GradleException("Nao consegui instalar a assinatura visual do card da bolinha.")
        }
        if (text.indexOf("val visibleCardSignature = buildVisibleCardSignature") != text.lastIndexOf("val visibleCardSignature = buildVisibleCardSignature")) {
            throw org.gradle.api.GradleException("Assinatura visual da bolinha foi instalada mais de uma vez.")
        }
        if ("bubble_render_background_uses_current_state_0_1_81" !in text) {
            throw org.gradle.api.GradleException("Nao consegui alinhar a cor renderizada com o estado real da bolinha.")
        }

        if (text != original) file.writeText(text)
    }
}

patchBubbleRenderStability.configure {
    mustRunAfter(
        "patchLiveRideOverlayStability",
        "patchBubbleStateReport",
        "keepDecisionDuringTransientText",
        "hardClearUnregisteredCardDecision",
        "modularLiveBubbleCore",
        "noStickyDecisionCleanup",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchBubbleRenderStability)
}

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
            "assinaturas de cards",
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

        text = text.replace(
            "            lastAnalyzedHash = lastSnapshotHash ?: snapshotHash\n",
            "            lastAnalyzedHash = snapshotHash // analysis_hash_bound_to_transaction_0_1_83\n",
        )

        if ("stale_result_guard_0_1_83" !in text) {
            val resultApplicationAnchor = "            lastSavedReadHash = snapshotHash\n"
            val resultApplicationIndex = text.indexOf(resultApplicationAnchor)
            if (resultApplicationIndex < 0) {
                throw org.gradle.api.GradleException("Nao encontrei o ponto de aplicacao do resultado da corrida.")
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
            text = text.substring(0, resultApplicationIndex) + staleGuard + text.substring(resultApplicationIndex)
        }

        val oldSignatureChange = """        if (lastVisibleCardSignature != null && lastVisibleCardSignature != visibleCardSignature) {
            lastDecisionOverlayAtMillis = 0L
            traceEvent("visible_card.signature_changed previous=${dollar}lastVisibleCardSignature next=${dollar}visibleCardSignature") // bubble_render_stability_0_1_81
"""
        if (oldSignatureChange in text) {
            text = text.replace(
                oldSignatureChange,
                """        if (lastVisibleCardSignature != null && lastVisibleCardSignature != visibleCardSignature) {
            lastAnalyzedHash = null // force_new_card_analysis_0_1_83
            traceEvent("visible_card.signature_changed previous=${dollar}lastVisibleCardSignature next=${dollar}visibleCardSignature") // bubble_render_stability_0_1_81
""",
            )
        }
        val nonClearingSignatureChange = """        if (lastVisibleCardSignature != null && lastVisibleCardSignature != visibleCardSignature) {
            traceEvent("visible_card.signature_changed transient_previous=${dollar}lastVisibleCardSignature next=${dollar}visibleCardSignature") // bubble_render_signature_no_clear_0_1_84
"""
        if (nonClearingSignatureChange in text && "force_new_card_analysis_0_1_83" !in text) {
            text = text.replace(
                nonClearingSignatureChange,
                """        if (lastVisibleCardSignature != null && lastVisibleCardSignature != visibleCardSignature) {
            lastAnalyzedHash = null // force_new_card_analysis_0_1_83
            traceEvent("visible_card.signature_changed transient_previous=${dollar}lastVisibleCardSignature next=${dollar}visibleCardSignature") // bubble_render_signature_no_clear_0_1_84
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
