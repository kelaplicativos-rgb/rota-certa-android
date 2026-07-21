// Rota Certa 0.1.126
// Remove definitivamente os cadastros previos de aplicativos e modelos de cards
// do caminho critico da bolinha. A leitura passa a depender apenas de:
// - pacote externo comum (sistema/launcher/teclado/Maps/Waze continuam bloqueados);
// - evidencia real de corrida;
// - um passageiro e pelo menos dois enderecos no card isolado.

fun replaceKotlinFunction126(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Funcao nao encontrada para 0.1.126: $signature")
    val braceStart = source.indexOf('{', start)
    if (braceStart < 0) throw GradleException("Corpo da funcao nao encontrado para 0.1.126: $signature")
    var depth = 0
    var index = braceStart
    while (index < source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) {
                    return source.substring(0, start) + replacement + source.substring(index + 1)
                }
            }
        }
        index += 1
    }
    throw GradleException("Fim da funcao nao encontrado para 0.1.126: $signature")
}

fun patchUniversalNoPreRegisteredGates126(serviceFile: java.io.File, mainFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para 0.1.126.")
    if (!mainFile.exists()) throw GradleException("MainActivity.kt nao encontrado para 0.1.126.")

    var service = serviceFile.readText()
    val dollar = "$"

    if ("pre_registered_runtime_cleanup_0_1_126" !in service) {
        val settingsAnchor = "            currentSettings = repository.settings.first()\n"
        val templatesAnchor = "            currentCardTemplates = repository.cardTemplates.first() // universal_optional_card_model_migration_0_1_101\n"
        val start = service.indexOf(settingsAnchor)
        val endStart = service.indexOf(templatesAnchor, start)
        if (start < 0 || endStart < 0) {
            throw GradleException("Migracao antiga de apps/cards nao encontrada para substituicao 0.1.126.")
        }
        val end = endStart + templatesAnchor.length
        val replacement = """            currentSettings = repository.settings.first()
            val cleanupPrefs126 = getSharedPreferences("rota_certa_runtime_migrations", Context.MODE_PRIVATE)
            if (!cleanupPrefs126.getBoolean("pre_registered_runtime_cleanup_0_1_126", false)) {
                val removedTemplates126 = repository.cardTemplates.first()
                removedTemplates126.forEach { template -> repository.removeCardTemplate(template.id) }
                SelectedRideAppStore.save(applicationContext, emptySet())
                currentSettings = currentSettings.copy(
                    requireRegisteredRideCard = false,
                    restrictToSelectedRideApps = false,
                    monitor99 = false,
                    monitorUber = false,
                    monitorInDrive = false,
                    extraMonitoredPackages = "",
                )
                repository.saveSettings(currentSettings)
                cleanupPrefs126.edit()
                    .putBoolean("pre_registered_runtime_cleanup_0_1_126", true)
                    .apply()
                DiagnosticLogStore.record(
                    "migration",
                    "pre_registered_gates.removed cards=${dollar}{removedTemplates126.size} apps=cleared",
                )
            } else if (
                currentSettings.requireRegisteredRideCard ||
                currentSettings.restrictToSelectedRideApps ||
                currentSettings.monitor99 ||
                currentSettings.monitorUber ||
                currentSettings.monitorInDrive ||
                currentSettings.extraMonitoredPackages.isNotBlank()
            ) {
                currentSettings = currentSettings.copy(
                    requireRegisteredRideCard = false,
                    restrictToSelectedRideApps = false,
                    monitor99 = false,
                    monitorUber = false,
                    monitorInDrive = false,
                    extraMonitoredPackages = "",
                )
                repository.saveSettings(currentSettings)
            }
            currentCardTemplates = emptyList() // pre_registered_runtime_cleanup_0_1_126
"""
        service = service.substring(0, start) + replacement + service.substring(end)
    }

    val oldScanFunction = """    private fun shouldScanPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        val selectedPackages = SelectedRideAppStore.selectedPackages(applicationContext, currentSettings)
        return serviceReady &&
            currentSettings.appEnabled &&
            currentSettings.liveReadingEnabled &&
            normalized != this.packageName &&
            normalized in selectedPackages
    } // selected_apps_store_0_1_122
"""
    val newScanFunction = """    private fun shouldScanPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        val universalSettings = currentSettings.copy(
            restrictToSelectedRideApps = false,
            monitor99 = false,
            monitorUber = false,
            monitorInDrive = false,
            extraMonitoredPackages = "",
        )
        val classification = br.com.mapeiaia.rotacerta.core.CorePackageMonitor.classify(
            packageName = normalized,
            ownPackageName = this.packageName,
            settings = universalSettings,
        )
        return serviceReady && currentSettings.liveReadingEnabled && classification.canScan
    } // universal_package_content_gate_0_1_126
"""
    if (oldScanFunction in service) {
        service = service.replaceFirst(oldScanFunction, newScanFunction)
    } else if ("universal_package_content_gate_0_1_126" !in service) {
        throw GradleException("Portaria antiga por aplicativos selecionados nao encontrada para 0.1.126.")
    }

    service = service
        .replace(
            "hardClearUniversalTwoAddress(\"Aplicativo fora da selecao do usuario.\")",
            "hardClearUniversalTwoAddress(scanBlockReason(resolvedPackage)) // universal_package_block_reason_0_1_126",
        )
        .replace(
            "hardClearUniversalTwoAddress(\"Leitura recebida de aplicativo nao selecionado.\")",
            "hardClearUniversalTwoAddress(scanBlockReason(currentWindowPackageName())) // universal_process_block_reason_0_1_126",
        )

    listOf(
        "pre_registered_runtime_cleanup_0_1_126",
        "pre_registered_gates.removed cards=",
        "SelectedRideAppStore.save(applicationContext, emptySet())",
        "currentCardTemplates = emptyList()",
        "universal_package_content_gate_0_1_126",
        "CorePackageMonitor.classify(",
        "classification.canScan",
        "universal_package_block_reason_0_1_126",
        "universal_process_block_reason_0_1_126",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Contrato universal 0.1.126 incompleto no servico: $marker")
    }
    if ("normalized in selectedPackages" in service) {
        throw GradleException("A bolinha ainda depende da lista de aplicativos selecionados.")
    }
    if ("Aplicativo fora da selecao do usuario." in service || "Leitura recebida de aplicativo nao selecionado." in service) {
        throw GradleException("Motivo legado de aplicativo fora da selecao ainda existe.")
    }

    var main = mainFile.readText()

    val monitoredReplacement = """@Composable
private fun MonitoredAppsCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    ExpandableCard(title = "Leitura universal", initiallyExpanded = false) {
        Text(
            "Nao existem aplicativos pre-cadastrados. A bolinha reconhece uma oferta real pelo passageiro e pelos enderecos do card.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Pacotes do sistema, launcher, teclado, Google Maps e Waze permanecem bloqueados automaticamente.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
} // no_pre_registered_apps_ui_0_1_126
"""
    main = replaceKotlinFunction126(main, "@Composable\nprivate fun MonitoredAppsCard(", monitoredReplacement)

    val installedReplacement = """@Composable
private fun InstalledRideAppsCard() {
    ExpandableCard(title = "Leitura universal sem lista de apps", initiallyExpanded = false) {
        Text(
            "A selecao previa de aplicativos foi removida. Nenhuma lista pode pausar ou apagar a decisao da bolinha.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
} // no_selected_apps_picker_ui_0_1_126
"""
    main = replaceKotlinFunction126(main, "@Composable\nprivate fun InstalledRideAppsCard()", installedReplacement)

    val modelsReplacement = """@Composable
private fun CardModelsCard(
    cardTemplates: List<RideCardTemplate>,
    templateStatus: String,
    unreadTemplatePrints: Int,
    onPickCardModels: () -> Unit,
    onDeleteCardModel: (RideCardTemplate) -> Unit,
) {
    ExpandableCard(title = "Leitura de cards", initiallyExpanded = false) {
        Text(
            "Cards pre-cadastrados foram apagados e nao sao requisito para o farol. O card real e validado diretamente na tela.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
} // no_pre_registered_cards_ui_0_1_126
"""
    main = replaceKotlinFunction126(main, "@Composable\nprivate fun CardModelsCard(", modelsReplacement)

    val registeredReplacement = """@Composable
private fun RegisteredCardsModuleCard(
    cardTemplates: List<RideCardTemplate>,
    templateStatus: String,
    unreadTemplatePrints: Int,
    onPickCardModels: () -> Unit,
    onDeleteCardModel: (RideCardTemplate) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Leitura universal de cards", fontWeight = FontWeight.Bold)
            Text(
                "Nenhum modelo pre-cadastrado controla a bolinha. Passageiro, embarque e destino do card visivel sao validados diretamente.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
} // no_registered_cards_module_0_1_126
"""
    main = replaceKotlinFunction126(main, "@Composable\nprivate fun RegisteredCardsModuleCard(", registeredReplacement)

    if ("diagnostic_policy_no_pre_registered_0_1_126" !in main) {
        val reportAnchor = "        appendLine(\"Leitura universal ligada: ${dollar}{settings.liveReadingEnabled}\")\n"
        if (reportAnchor !in main) throw GradleException("Secao de configuracoes do relatorio nao encontrada para 0.1.126.")
        val reportBlock = reportAnchor + """        appendLine("Filtro por aplicativos pre-cadastrados: removido")
        appendLine("Modelos de cards como requisito: removidos")
        appendLine("Selecao manual de apps bloqueia a bolinha: false")
        appendLine("Politica de leitura: pacote externo comum + passageiro + pelo menos dois enderecos")
        appendLine("Pacotes passivos bloqueados: sistema, launcher, teclado, Google Maps e Waze") // diagnostic_policy_no_pre_registered_0_1_126
"""
        main = main.replaceFirst(reportAnchor, reportBlock)
    }

    main = main.replace(
        "O relatorio registra a linha do tempo mantida em memoria desde o inicio da execucao, alem da tentativa detalhada de leitura, OCR, enderecos, geocodificacao, rota, descartes, atalhos e cor final.",
        "O relatorio preserva os eventos tecnicos necessarios e agora informa explicitamente a politica universal de leitura. Nao usa IA generativa e nao depende de apps ou cards pre-cadastrados.",
    )

    listOf(
        "no_pre_registered_apps_ui_0_1_126",
        "no_selected_apps_picker_ui_0_1_126",
        "no_pre_registered_cards_ui_0_1_126",
        "no_registered_cards_module_0_1_126",
        "diagnostic_policy_no_pre_registered_0_1_126",
        "Filtro por aplicativos pre-cadastrados: removido",
        "Modelos de cards como requisito: removidos",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Contrato universal 0.1.126 incompleto na interface/relatorio: $marker")
    }

    serviceFile.writeText(service)
    mainFile.writeText(main)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchUniversalNoPreRegisteredGates126(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
