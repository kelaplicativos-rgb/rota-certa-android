// Rota Certa 0.1.127 — contrato manual final e indivisivel.
// Aplicado depois do restaurador 0.1.127 para garantir que nenhum patch antigo
// reabra leitura universal, cards opcionais ou polling agressivo.

fun patchManualStrictContract127(
    serviceFile: java.io.File,
    mainFile: java.io.File,
) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para o contrato manual final.")
    if (!mainFile.exists()) throw GradleException("MainActivity.kt nao encontrado para o contrato manual final.")

    var service = serviceFile.readText()
    val dollar = "$"

    // A instalacao nasce sem apps selecionados, mas com as duas portarias ligadas.
    val migrationStart = service.indexOf("            val manualSelectionPrefs127")
    val migrationEnd = if (migrationStart >= 0) {
        service.indexOf("            currentCardTemplates = repository.cardTemplates.first()", migrationStart)
    } else {
        -1
    }
    if (migrationStart < 0 || migrationEnd < 0) {
        throw GradleException("Migracao manual 0.1.127 nao encontrada para o contrato final.")
    }
    var migrationRegion = service.substring(migrationStart, migrationEnd)
    migrationRegion = migrationRegion.replace(
        "requireRegisteredRideCard = false,",
        "requireRegisteredRideCard = true, // manual_registered_card_required_migration_0_1_127",
    )
    if ("manual_registered_card_required_migration_0_1_127" !in migrationRegion) {
        throw GradleException("A migracao nao ativou a exigencia de card cadastrado.")
    }
    service = service.substring(0, migrationStart) + migrationRegion + service.substring(migrationEnd)

    if ("manual_active_card_template_id_0_1_127" !in service) {
        val fieldAnchor = "    private var universalActiveAddressSignature: String? = null // universal_two_address_fields_0_1_98\n"
        if (fieldAnchor !in service) throw GradleException("Campo da assinatura universal nao encontrado.")
        service = service.replaceFirst(
            fieldAnchor,
            fieldAnchor + "    private var manualActiveCardTemplateId127: String? = null // manual_active_card_template_id_0_1_127\n",
        )
    }

    if ("manual_apps_and_cards_required_settings_0_1_127" !in service) {
        val settingsAnchor = "            currentCardTemplates = repository.cardTemplates.first() // manual_cards_preserved_0_1_127\n"
        if (settingsAnchor !in service) throw GradleException("Carregamento dos modelos manuais nao encontrado.")
        service = service.replaceFirst(
            settingsAnchor,
            settingsAnchor + """            if (!currentSettings.requireRegisteredRideCard || !currentSettings.restrictToSelectedRideApps) {
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
""",
        )
    }

    // O pacote ja foi validado pela portaria de aplicativos. Agora o card precisa
    // corresponder a um modelo do MESMO pacote antes de iniciar geocodificacao/rota.
    if ("manual_registered_card_gate_0_1_127" !in service) {
        val gateAnchor = """        val cardDecisionSignature = passengerIdentity.candidates.single() + "|" + trigger.addressSignature // card_decision_signature_0_1_127
        universalLastActiveReadAtMillis = readNowMillis
"""
        if (gateAnchor !in service) throw GradleException("Assinatura de card 0.1.127 nao encontrada.")
        val gateBlock = """        universalLastActiveReadAtMillis = readNowMillis
        val selectedPackageForCard = normalizePackageName(universalResolvedForegroundPackage())
        if (selectedPackageForCard == null || selectedPackageForCard !in SelectedRideAppStore.read(applicationContext)) {
            hardClearUniversalTwoAddress("Aplicativo nao selecionado; leitura, OCR e rota bloqueados.")
            return
        }
        val packageCardTemplates = currentCardTemplates.filter { template ->
            normalizePackageName(template.packageName) == selectedPackageForCard
        }
        if (packageCardTemplates.isEmpty()) {
            universalScreenGeneration += 1L
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
            universalScreenGeneration += 1L
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
                "Card visivel nao corresponde aos modelos cadastrados deste aplicativo.",
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
        if (freshnessStart < 0 || freshnessEnd < 0) throw GradleException("Validade da rota nao encontrada.")
        var freshnessRegion = service.substring(freshnessStart, freshnessEnd)
        val freshnessAnchor = """            addressSignature == universalActiveAddressSignature &&
            isUniversalExternalWindowActive() &&
"""
        if (freshnessAnchor !in freshnessRegion) throw GradleException("Condicao de validade da assinatura nao encontrada.")
        freshnessRegion = freshnessRegion.replaceFirst(
            freshnessAnchor,
            """            addressSignature == universalActiveAddressSignature &&
            manualActiveCardTemplateId127 != null &&
            currentCardTemplates.any { template ->
                template.id == manualActiveCardTemplateId127 &&
                    normalizePackageName(template.packageName) == normalizePackageName(currentWindowPackageName())
            } && // manual_registered_card_freshness_0_1_127
            isUniversalExternalWindowActive() &&
""",
        )
        service = service.substring(0, freshnessStart) + freshnessRegion + service.substring(freshnessEnd)
    }

    if ("manual_active_card_clear_0_1_127" !in service) {
        val clearStart = service.indexOf("    private fun hardClearUniversalTwoAddress(")
        val clearEnd = if (clearStart >= 0) service.indexOf("    private fun resetToDefault(", clearStart) else -1
        if (clearStart < 0 || clearEnd < 0) throw GradleException("Limpeza universal nao encontrada.")
        var clearRegion = service.substring(clearStart, clearEnd)
        val clearAnchor = "        universalActiveAddressSignature = null\n"
        if (clearAnchor !in clearRegion) throw GradleException("Assinatura ativa nao e limpa pelo servico.")
        clearRegion = clearRegion.replaceFirst(
            clearAnchor,
            clearAnchor + "        manualActiveCardTemplateId127 = null // manual_active_card_clear_0_1_127\n",
        )
        service = service.substring(0, clearStart) + clearRegion + service.substring(clearEnd)
    }

    // Eventos de acessibilidade continuam imediatos. O ciclo vira fallback para
    // reduzir CPU, bateria, varredura de arvore e tentativas repetidas de screenshot.
    service = service.replace(
        Regex("const val SCAN_LOOP_MS = \\d+L"),
        "const val SCAN_LOOP_MS = 350L // adaptive_fallback_scan_0_1_127",
    )

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
        "manual_registered_card_required_migration_0_1_127",
        "manual_active_card_template_id_0_1_127",
        "manual_apps_and_cards_required_settings_0_1_127",
        "manual_registered_card_gate_0_1_127",
        "manual_registered_card_freshness_0_1_127",
        "manual_active_card_clear_0_1_127",
        "templates = packageCardTemplates",
        "manual.card.gate accepted=true",
        "adaptive_fallback_scan_0_1_127",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Contrato manual final ausente no servico: ${dollar}marker")
    }
    listOf(
        "Modelos de cards obrigatorios",
        "sem correspondencia a bolinha nao calcula rota",
        "Modelos de cards obrigatorios: true",
        "app escolhido + modelo correspondente",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Contrato manual final ausente na interface: ${dollar}marker")
    }
    if ("const val SCAN_LOOP_MS = 120L" in service) {
        throw GradleException("Polling agressivo de 120 ms ainda esta ativo.")
    }

    serviceFile.writeText(service)
    mainFile.writeText(main)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchManualStrictContract127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
