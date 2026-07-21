// Rota Certa 0.1.124
// Correcao global do tempo e da limpeza da bolinha, sem distinguir aplicativos:
// - qualquer leitura vazia ou card incompleto limpa imediatamente;
// - somente um card com um passageiro identificavel e pelo menos embarque/destino inicia a rota;
// - qualquer mudanca no texto visivel invalida o resultado anterior;
// - a rota usa as configuracoes ja carregadas em memoria;
// - verde/vermelho aparecem antes da gravacao do historico;
// - protecoes antigas que conservavam a cor anterior sao desativadas.

fun patchInstantFarolDecision124(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para o farol instantaneo 0.1.124.")
    var text = file.readText()
    val dollar = "$"

    if ("global_continuous_empty_clear_0_1_124" !in text) {
        val oldBlock = """                                val ignoreTransientOverlayEmpty = UniversalFastReadPolicy.shouldIgnoreTransientEmptyAccessibilityRead(
                                    text = visibleText,
                                    rootPackageName = currentRootPackageName(),
                                    effectivePackageName = expectedPackage,
                                    ownPackageName = this@LiveRideAccessibilityService.packageName,
                                )
                                if (ignoreTransientOverlayEmpty) {
                                    traceEvent("universal.accessibility transient_overlay_empty_ignored=true")
                                } else {
                                    processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
                                }
"""
        val newBlock = """                                processRideText(
                                    visibleText,
                                    TextSource.Accessibility,
                                    allowPopupCandidate = true,
                                ) // global_continuous_empty_clear_0_1_124
"""
        if (oldBlock !in text) throw GradleException("Protecao de leitura vazia do ciclo continuo nao encontrada.")
        text = text.replaceFirst(oldBlock, newBlock)
    }

    if ("global_scheduled_empty_clear_0_1_124" !in text) {
        val oldBlock = """            val ignoreTransientOverlayEmpty = UniversalFastReadPolicy.shouldIgnoreTransientEmptyAccessibilityRead(
                text = visibleText,
                rootPackageName = currentRootPackageName(),
                effectivePackageName = expectedPackage,
                ownPackageName = this@LiveRideAccessibilityService.packageName,
            )
            if (ignoreTransientOverlayEmpty) {
                traceEvent("universal.accessibility transient_overlay_empty_ignored=true")
                return@launch
            }
            processRideText(visibleText, TextSource.Accessibility, allowPopupCandidate = true)
"""
        val newBlock = """            processRideText(
                visibleText,
                TextSource.Accessibility,
                allowPopupCandidate = true,
            ) // global_scheduled_empty_clear_0_1_124
"""
        if (oldBlock !in text) throw GradleException("Protecao de leitura vazia agendada nao encontrada.")
        text = text.replaceFirst(oldBlock, newBlock)
    }

    if ("global_single_passenger_gate_0_1_124" !in text ||
        "global_passenger_and_addresses_card_0_1_124" !in text
    ) {
        val oldActiveTrigger = "        val activeTrigger = trigger.active && !trigger.destination.isNullOrBlank() && rideEvidence.accepted // universal_ride_evidence_gate_0_1_112\n"
        if (oldActiveTrigger !in text) throw GradleException("Gatilho ativo universal nao encontrado.")
        val passengerAndActiveBlock = """        val passengerIdentity = RidePassengerIdentityPolicy.evaluate(snapshotText)
        if (!passengerIdentity.accepted && trigger.addresses.size >= 2) {
            traceEvent(
                "universal.passenger accepted=false count=${dollar}{passengerIdentity.candidates.size} reason=${dollar}{passengerIdentity.reason}",
            )
        } // global_single_passenger_gate_0_1_124
        val activeTrigger = trigger.addresses.size >= 2 && trigger.active && !trigger.destination.isNullOrBlank() && rideEvidence.accepted && passengerIdentity.accepted // global_passenger_and_addresses_card_0_1_124
"""
        text = text.replaceFirst(oldActiveTrigger, passengerAndActiveBlock)
    }

    if ("global_inactive_clear_now_0_1_124" !in text) {
        val oldBlock = """        val readNowMillis = System.currentTimeMillis()
        if (!activeTrigger && liveSource == UniversalLiveReadSource.Accessibility &&
            UniversalFastReadPolicy.shouldIgnoreTransientInactiveRead(
                hasActiveAddressSignature = universalActiveAddressSignature != null,
                routeInFlight = universalRouteJob?.isActive == true,
                lastActiveReadAtMillis = universalLastActiveReadAtMillis,
                nowMillis = readNowMillis,
            )
        ) {
            traceEvent("universal.accessibility transient_empty_ignored_route_inflight=true")
            return
        }
        if (activeTrigger) {
            universalLastActiveReadAtMillis = readNowMillis
            universalActiveRidePackageName = universalResolvedForegroundPackage()
        }
"""
        val newBlock = """        val readNowMillis = System.currentTimeMillis()
        if (!activeTrigger) {
            val clearReason = when {
                passengerIdentity.candidates.size > 1 -> "Tela com varias corridas/passageiros; resultado removido imediatamente."
                passengerIdentity.candidates.isEmpty() && trigger.addresses.size >= 2 -> "Passageiro unico nao identificado; resultado removido imediatamente."
                else -> "Card saiu, mudou ou nao possui embarque e destino; resultado removido imediatamente."
            }
            hardClearUniversalTwoAddress(clearReason)
            return // global_inactive_clear_now_0_1_124
        }
        universalLastActiveReadAtMillis = readNowMillis
        universalActiveRidePackageName = universalResolvedForegroundPackage()
"""
        if (oldBlock !in text) throw GradleException("Tolerancia de leitura transitoria nao encontrada.")
        text = text.replaceFirst(oldBlock, newBlock)
    }

    if ("global_full_screen_hash_0_1_124" !in text) {
        val oldHash = "        val analysisHash = listOf(trigger.addressSignature, trigger.destination.orEmpty()).joinToString(\"|\").hashCode()\n"
        val newHash = "        val analysisHash = trigger.screenHash // global_full_screen_hash_0_1_124\n"
        if (oldHash !in text) throw GradleException("Hash antigo do card nao encontrado.")
        text = text.replaceFirst(oldHash, newHash)
    }

    if ("global_screen_change_clear_0_1_124" !in text) {
        val oldRender = """            rememberBubbleReason("universal_waiting", "Dois enderecos numerados encontrados; calculando o ultimo destino.")
            publishRuntimeValidationTrigger(trigger)
            showOverlay(RadarColor.Default, distanceKm = null)
            traceEvent("universal.screen.changed hash=${dollar}analysisHash yellow=true signature=${dollar}{trigger.addressSignature.hashCode()}")
"""
        val newRender = """            rememberBubbleReason("universal_waiting", "Tela alterada; resultado anterior removido e novo destino em calculo.")
            publishRuntimeValidationTrigger(trigger)
            showOverlay(RadarColor.Default, distanceKm = null) // global_screen_change_clear_0_1_124
            traceEvent("universal.screen.changed hash=${dollar}analysisHash yellow=true immediate_clear=true signature=${dollar}{trigger.addressSignature.hashCode()}")
"""
        if (oldRender !in text) throw GradleException("Renderizacao da mudanca de tela nao encontrada.")
        text = text.replaceFirst(oldRender, newRender)
    }

    if ("instant_farol_cached_settings_0_1_124" !in text) {
        val oldSettingsRead = """        currentSettings = repository.settings.first()
        val settings = currentSettings
"""
        if (oldSettingsRead !in text) throw GradleException("Leitura bloqueante das configuracoes nao encontrada.")
        text = text.replaceFirst(
            oldSettingsRead,
            "        val settings = currentSettings // instant_farol_cached_settings_0_1_124\n",
        )
    }

    if ("instant_farol_paint_before_history_0_1_124" !in text) {
        val oldResultBlock = """        if (universalAnalysisDeduper.shouldPersist(persistenceSignature)) {
            repository.addAnalysis(result)
        } else {
            traceEvent("universal.history duplicate_skipped=true")
        }
        rememberBubbleReason("universal_result", result.reason)
        showOverlay(color, distanceKm)
        traceEvent("universal.result applied color=${dollar}{color.diagnosticLabel} km=${dollar}{distanceKm?.toString().orEmpty()}")
"""
        if (oldResultBlock !in text) throw GradleException("Bloco final de resultado nao encontrado para antecipar a cor.")
        val newResultBlock = """        val shouldPersistHistory = universalAnalysisDeduper.shouldPersist(persistenceSignature)
        rememberBubbleReason("universal_result", result.reason)
        showOverlay(color, distanceKm)
        traceEvent("universal.result applied color=${dollar}{color.diagnosticLabel} km=${dollar}{distanceKm?.toString().orEmpty()} instant=true") // instant_farol_paint_before_history_0_1_124
        if (shouldPersistHistory) {
            scope.launch {
                runCatching { repository.addAnalysis(result) }
                    .onFailure { error ->
                        traceEvent("universal.history async_failure=${dollar}{error::class.java.simpleName}")
                    }
            }
        } else {
            traceEvent("universal.history duplicate_skipped=true")
        }
"""
        text = text.replaceFirst(oldResultBlock, newResultBlock)
    }

    if ("global_no_transient_decision_keep_0_1_124" !in text) {
        val oldCondition = """            val keepActiveDecisionForTransientInsufficient = computedRadarColor == RadarColor.Default &&
                hasActiveRegisteredDecision() &&
                shouldScanCurrentWindow()
"""
        val newCondition = "            val keepActiveDecisionForTransientInsufficient = false // global_no_transient_decision_keep_0_1_124\n"
        if (oldCondition !in text) throw GradleException("Protecao legada da decisao ativa nao encontrada.")
        text = text.replaceFirst(oldCondition, newCondition)
    }

    if ("global_idle_never_guarded_0_1_124" !in text) {
        val oldGuard = """        if (shouldScanCurrentWindow() && hasActiveRegisteredDecision()) {
            traceEvent("resetToIdle guarded active_ride_window reason=${dollar}reason")
            return
        }
"""
        val newGuard = "        Unit // global_idle_never_guarded_0_1_124\n"
        if (oldGuard !in text) throw GradleException("Protecao legada do reset para cinza nao encontrada.")
        text = text.replaceFirst(oldGuard, newGuard)
    }

    if ("global_overlay_idle_allowed_0_1_124" !in text) {
        val oldGuard = """        if (color == RadarColor.Idle && currentRadarColor == RadarColor.Default && shouldScanCurrentWindow()) {
            Unit
            return
        }
"""
        val newGuard = "        Unit // global_overlay_idle_allowed_0_1_124\n"
        if (oldGuard !in text) throw GradleException("Protecao legada do overlay cinza nao encontrada.")
        text = text.replaceFirst(oldGuard, newGuard)
    }

    listOf(
        "global_continuous_empty_clear_0_1_124",
        "global_scheduled_empty_clear_0_1_124",
        "global_single_passenger_gate_0_1_124",
        "global_passenger_and_addresses_card_0_1_124",
        "global_inactive_clear_now_0_1_124",
        "global_full_screen_hash_0_1_124",
        "global_screen_change_clear_0_1_124",
        "instant_farol_cached_settings_0_1_124",
        "instant_farol_paint_before_history_0_1_124",
        "global_no_transient_decision_keep_0_1_124",
        "global_idle_never_guarded_0_1_124",
        "global_overlay_idle_allowed_0_1_124",
        "scope.launch {\n                runCatching { repository.addAnalysis(result) }",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Contrato global do farol 0.1.124 incompleto: $marker")
    }

    listOf(
        "transient_overlay_empty_ignored=true",
        "transient_empty_ignored_route_inflight=true",
        "resetToIdle guarded active_ride_window",
    ).forEach { forbidden ->
        if (forbidden in text) throw GradleException("Protecao antiga ainda conserva resultado: $forbidden")
    }

    file.writeText(text)
}

fun configureInstantFarolDecision124() {
    val service = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    patchInstantFarolDecision124(service.asFile)
}

tasks.named("radarWorkTracking121").configure {
    doLast { configureInstantFarolDecision124() }
}

tasks.matching { it.name == "workTrackingCardAnchorCleanup121" }.configureEach {
    doLast { configureInstantFarolDecision124() }
}
