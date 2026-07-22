// Rota Certa 0.1.127 — portaria manual estrita.
//
// Contrato:
// - nenhum aplicativo nasce selecionado;
// - acessibilidade, screenshot e OCR rodam somente no pacote escolhido pelo usuario;
// - nenhum modelo de card nasce cadastrado;
// - verde/vermelho e chamadas de geocodificacao/rota exigem um modelo manual
//   correspondente ao mesmo pacote que esta em primeiro plano.

fun patchManualRegisteredCardsRequired127(
    serviceFile: java.io.File,
    mainFile: java.io.File,
) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para a portaria estrita 0.1.127.")
    if (!mainFile.exists()) throw GradleException("MainActivity.kt nao encontrado para a portaria estrita 0.1.127.")

    var service = serviceFile.readText()
    val dollar = "$"

    if ("manual_active_card_template_id_0_1_127" !in service) {
        val fieldAnchor = "    private var universalActiveAddressSignature: String? = null // universal_two_address_fields_0_1_98\n"
        if (fieldAnchor !in service) throw GradleException("Estado da assinatura universal nao encontrado para a trava de card.")
        service = service.replaceFirst(
            fieldAnchor,
            fieldAnchor + "    private var manualActiveCardTemplateId127: String? = null // manual_active_card_template_id_0_1_127\n",
        )
    }

    if ("manual_apps_and_cards_required_settings_0_1_127" !in service) {
        val settingsAnchor = "            currentCardTemplates = repository.cardTemplates.first() // manual_cards_preserved_0_1_127\n"
        if (settingsAnchor !in service) throw GradleException("Carregamento dos modelos manuais 0.1.127 nao encontrado.")
        val settingsBlock = settingsAnchor + """            if (!currentSettings.requireRegisteredRideCard || !currentSettings.restrictToSelectedRideApps) {
                currentSettings = currentSettings.copy(
                    requireRegisteredRideCard = true,
                    restrictToSelectedRideApps = true,
                    monitor99 = false,
                    monitorUber = false,
                    monitorInDrive = false,
                    extraMonitoredPackages = "",
                )
                repository.saveSettings(currentSettings)
            } // manual_apps_and_cards_required_settings_0_1_127
"""
        service = service.replaceFirst(settingsAnchor, settingsBlock)
    }

    if ("manual_registered_card_gate_0_1_127" !in service) {
        val gateAnchor = """        val cardDecisionSignature = passengerIdentity.candidates.single() + "|" + trigger.addressSignature // card_decision_signature_0_1_127
        universalLastActiveReadAtMillis = readNowMillis
"""
        if (gateAnchor !in service) throw GradleException("Assinatura do card 0.1.127 nao encontrada para aplicar a portaria manual.")
        val gateBlock = """        universalLastActiveReadAtMillis = readNowMillis
        val selectedPackageForCard = normalizePackageName(universalResolvedForegroundPackage())
        if (selectedPackageForCard == null) {
            hardClearUniversalTwoAddress("Aplicativo selecionado nao identificado para validar o modelo do card.")
            return
        }
        val packageCardTemplates = currentCardTemplates.filter { template ->
            normalizePackageName(template.packageName) == selectedPackageForCard
        }
        if (packageCardTemplates.isEmpty()) {
            if (universalActiveAddressSignature != null || universalRouteJob?.isActive == true ||
                currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red
            ) {
                universalScreenGeneration += 1L
            }
            universalRouteJob?.cancel()
            universalRouteJob = null
            universalActiveAddressSignature = null
            manualActiveCardTemplateId127 = null
            lastSnapshotHash = trigger.screenHash
            lastAnalyzedHash = null
            pendingAnalysis = null
            analyzing = false
            currentDistanceKm = null
            registeredCardGate.clear()
            rememberBubbleReason(
                "manual_card_required",
                "Nenhum modelo de card cadastrado para este aplicativo; rota e decisao bloqueadas.",
            )
            showOverlay(RadarColor.Default, distanceKm = null)
            traceEvent("manual.card.gate accepted=false reason=no_template package=${dollar}selectedPackageForCard")
            return
        }
        val manualCardMatch = RideCardTemplateMatcher.match(
            text = snapshotText,
            packageName = selectedPackageForCard,
            templates = packageCardTemplates,
        )
        if (manualCardMatch == null) {
            if (universalActiveAddressSignature != null || universalRouteJob?.isActive == true ||
                currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red
            ) {
                universalScreenGeneration += 1L
            }
            universalRouteJob?.cancel()
            universalRouteJob = null
            universalActiveAddressSignature = null
            manualActiveCardTemplateId127 = null
            lastSnapshotHash = trigger.screenHash
            lastAnalyzedHash = null
            pendingAnalysis = null
            analyzing = false
            currentDistanceKm = null
            registeredCardGate.clear()
            rememberBubbleReason(
                "manual_card_waiting",
                "Tela de corrida detectada, mas nao corresponde a nenhum modelo cadastrado deste aplicativo.",
            )
            showOverlay(RadarColor.Default, distanceKm = null)
            traceEvent(
                "manual.card.gate accepted=false reason=no_match package=${dollar}selectedPackageForCard templates=${dollar}{packageCardTemplates.size}",
            )
            return
        }
        registeredCardGate.markSeen()
        manualActiveCardTemplateId127 = manualCardMatch.template.id
        val cardDecisionSignature = selectedPackageForCard + "|" + manualCardMatch.template.id + "|" +
            passengerIdentity.candidates.single() + "|" + trigger.addressSignature // card_decision_signature_0_1_127 manual_registered_card_gate_0_1_127
        traceEvent(
            "manual.card.gate accepted=true package=${dollar}selectedPackageForCard template=${dollar}{manualCardMatch.template.id} score=${dollar}{manualCardMatch.score}",
        )
"""
        service = service.replaceFirst(gateAnchor, gateBlock)
    }

    if ("manual_registered_card_freshness_0_1_127" !in service) {
        val freshnessStart = service.indexOf("    private fun isUniversalResultFresh(")
        val freshnessEnd = if (freshnessStart >= 0) service.indexOf("    private fun hardClearUniversalTwoAddress(", freshnessStart) else -1
        if (freshnessStart < 0 || freshnessEnd < 0) throw GradleException("Validade da rota universal nao encontrada para a trava de card.")
        var freshnessRegion = service.substring(freshnessStart, freshnessEnd)
        val freshnessAnchor = """            addressSignature == universalActiveAddressSignature &&
            isUniversalExternalWindowActive() &&
"""
        if (freshnessAnchor !in freshnessRegion) throw GradleException("Condicao da assinatura ativa nao encontrada para a trava de card.")
        val freshnessReplacement = """            addressSignature == universalActiveAddressSignature &&
            manualActiveCardTemplateId127 != null &&
            currentCardTemplates.any { template ->
                template.id == manualActiveCardTemplateId127 &&
                    normalizePackageName(template.packageName) == normalizePackageName(currentWindowPackageName())
            } && // manual_registered_card_freshness_0_1_127
            isUniversalExternalWindowActive() &&
"""
        freshnessRegion = freshnessRegion.replaceFirst(freshnessAnchor, freshnessReplacement)
        service = service.substring(0, freshnessStart) + freshnessRegion + service.substring(freshnessEnd)
    }

    if ("manual_active_card_clear_0_1_127" !in service) {
        val clearStart = service.indexOf("    private fun hardClearUniversalTwoAddress(")
        val clearEnd = if (clearStart >= 0) service.indexOf("    private fun resetToDefault(", clearStart) else -1
        if (clearStart < 0 || clearEnd < 0) throw GradleException("Limpeza universal nao encontrada para zerar o modelo ativo.")
        var clearRegion = service.substring(clearStart, clearEnd)
        val clearAnchor = "        universalActiveAddressSignature = null\n"
        if (clearAnchor !in clearRegion) throw GradleException("Assinatura ativa nao encontrada dentro da limpeza universal.")
        clearRegion = clearRegion.replaceFirst(
            clearAnchor,
            clearAnchor + "        manualActiveCardTemplateId127 = null // manual_active_card_clear_0_1_127\n",
        )
        service = service.substring(0, clearStart) + clearRegion + service.substring(clearEnd)
    }

    var main = mainFile.readText()
    main = main
        .replace("Modelos de cards opcionais", "Modelos de cards obrigatorios")
        .replace(
            "Nenhum modelo nasce cadastrado. Use prints somente quando um aplicativo ou formato de card precisar ser ensinado manualmente.",
            "Nenhum modelo nasce cadastrado. Cadastre pelo menos um print do card de cada aplicativo selecionado; sem correspondencia a bolinha nao calcula rota nem libera verde/vermelho.",
        )
        .replace(
            "Nenhum modelo cadastrado.",
            "Nenhum modelo cadastrado. A bolinha permanece amarela e nao calcula rota.",
        )
        .replace(
            "Modelos de cards: opcionais; cadastrados somente pelo usuario",
            "Modelos de cards obrigatorios: true; cadastrados somente pelo usuario",
        )
        .replace(
            "Politica de leitura: app escolhido + passageiro + pelo menos dois enderecos",
            "Politica de leitura: app escolhido + modelo correspondente + passageiro + pelo menos dois enderecos",
        )

    listOf(
        "manual_active_card_template_id_0_1_127",
        "manual_apps_and_cards_required_settings_0_1_127",
        "manual_registered_card_gate_0_1_127",
        "manual_registered_card_freshness_0_1_127",
        "manual_active_card_clear_0_1_127",
        "RideCardTemplateMatcher.match(",
        "templates = packageCardTemplates",
        "manual.card.gate accepted=true",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Portaria estrita de apps/cards incompleta no servico: ${dollar}marker")
    }
    listOf(
        "Modelos de cards obrigatorios",
        "sem correspondencia a bolinha nao calcula rota",
        "Modelos de cards obrigatorios: true",
        "app escolhido + modelo correspondente",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Portaria estrita de apps/cards incompleta na interface: ${dollar}marker")
    }
    if ("requireRegisteredRideCard = false" in service.substring(
            service.indexOf("manual_selection_storage_ready_0_1_127"),
            service.indexOf("manual_cards_preserved_0_1_127") + "manual_cards_preserved_0_1_127".length,
        )
    ) {
        throw GradleException("A inicializacao 0.1.127 ainda desliga a exigencia de card cadastrado.")
    }

    serviceFile.writeText(service)
    mainFile.writeText(main)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchManualRegisteredCardsRequired127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
