// Rota Certa 0.1.127
// Corrige a interpretacao da limpeza 0.1.126:
// - nenhum aplicativo nasce selecionado;
// - o usuario volta a escolher os aplicativos que a bolinha pode ler;
// - o cadastro manual de modelos volta a existir e continua opcional;
// - o vermelho geometrico e apenas provisório e a rota exata continua ate obter km;
// - mudancas cosmeticas da lista nao cancelam a rota do mesmo card.

fun replaceKotlinFunction127(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Funcao nao encontrada para 0.1.127: $signature")
    val braceStart = source.indexOf('{', start)
    if (braceStart < 0) throw GradleException("Corpo da funcao nao encontrado para 0.1.127: $signature")
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
    throw GradleException("Fim da funcao nao encontrado para 0.1.127: $signature")
}

fun patchManualAppsCardsExactRoute127(serviceFile: java.io.File, mainFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para 0.1.127.")
    if (!mainFile.exists()) throw GradleException("MainActivity.kt nao encontrado para 0.1.127.")

    var service = serviceFile.readText()
    val dollar = "$"

    // A limpeza destrutiva da 0.1.126 e substituida por uma inicializacao vazia,
    // que preserva escolhas e modelos criados manualmente depois da atualizacao.
    if ("manual_selection_storage_ready_0_1_127" !in service) {
        val startToken = "            currentSettings = repository.settings.first()\n            val cleanupPrefs126"
        val start = service.indexOf(startToken)
        val endToken = "            currentCardTemplates = emptyList() // pre_registered_runtime_cleanup_0_1_126 universal_optional_card_model_migration_0_1_101\n"
        val endStart = service.indexOf(endToken, start)
        if (start < 0 || endStart < 0) {
            throw GradleException("Bloco destrutivo 0.1.126 nao encontrado para restauracao 0.1.127.")
        }
        val end = endStart + endToken.length
        val replacement = """            currentSettings = repository.settings.first()
            val manualSelectionPrefs127 = getSharedPreferences("rota_certa_runtime_migrations", Context.MODE_PRIVATE)
            if (!manualSelectionPrefs127.getBoolean("manual_selection_storage_ready_0_1_127", false)) {
                if (!SelectedRideAppStore.hasExplicitSelection(applicationContext)) {
                    SelectedRideAppStore.save(applicationContext, emptySet())
                }
                currentSettings = currentSettings.copy(
                    requireRegisteredRideCard = false,
                    restrictToSelectedRideApps = true,
                    monitor99 = false,
                    monitorUber = false,
                    monitorInDrive = false,
                    extraMonitoredPackages = "",
                )
                repository.saveSettings(currentSettings)
                manualSelectionPrefs127.edit()
                    .putBoolean("manual_selection_storage_ready_0_1_127", true)
                    .apply()
                DiagnosticLogStore.record(
                    "migration",
                    "manual_selection.ready selected=${dollar}{SelectedRideAppStore.read(applicationContext).size} cards=${dollar}{repository.cardTemplates.first().size}",
                )
            }
            currentCardTemplates = repository.cardTemplates.first() // manual_cards_preserved_0_1_127
            // pre_registered_runtime_cleanup_0_1_126 superseded_by_manual_selection_0_1_127
"""
        service = service.substring(0, start) + replacement + service.substring(end)
    }

    if ("repository.cardTemplates.collect { currentCardTemplates = it }" !in service) {
        val anchor = "        scope.launch { repository.savedPlaces.collect { currentSavedPlaces = it } }\n"
        if (anchor !in service) throw GradleException("Coletores do servico nao encontrados para restaurar cards.")
        service = service.replaceFirst(
            anchor,
            anchor + "        scope.launch { repository.cardTemplates.collect { currentCardTemplates = it } } // manual_cards_observer_0_1_127\n",
        )
    }

    val scanReplacement = """    private fun shouldScanPackage(packageName: String?): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        if (!serviceReady || !currentSettings.appEnabled || !currentSettings.liveReadingEnabled) return false
        val passiveProbeSettings = currentSettings.copy(
            restrictToSelectedRideApps = false,
            monitor99 = false,
            monitorUber = false,
            monitorInDrive = false,
            extraMonitoredPackages = "",
        )
        val classification = br.com.mapeiaia.rotacerta.core.CorePackageMonitor.classify(
            packageName = normalized,
            ownPackageName = this.packageName,
            settings = passiveProbeSettings,
        )
        val selectedPackages = SelectedRideAppStore.read(applicationContext)
        return classification.canScan && normalized in selectedPackages
    } // universal_package_content_gate_0_1_126 manual_selected_apps_gate_0_1_127
"""
    service = replaceKotlinFunction127(service, "    private fun shouldScanPackage(packageName: String?): Boolean {", scanReplacement)

    val selectedPackagesReplacement = """    private fun selectedRidePackages(settings: AppSettings): Set<String> {
        @Suppress("UNUSED_VARIABLE")
        val ignoredLegacySettings = settings
        return SelectedRideAppStore.read(applicationContext)
    } // manual_selected_packages_diagnostic_0_1_127
"""
    service = replaceKotlinFunction127(service, "    private fun selectedRidePackages(settings: AppSettings): Set<String>", selectedPackagesReplacement)

    val blockReasonReplacement = """    private fun scanBlockReason(packageName: String?): String {
        val normalized = normalizePackageName(packageName)
            ?: return "Pacote ativo nao informado pelo Android."
        val passiveProbeSettings = currentSettings.copy(
            restrictToSelectedRideApps = false,
            monitor99 = false,
            monitorUber = false,
            monitorInDrive = false,
            extraMonitoredPackages = "",
        )
        val classification = br.com.mapeiaia.rotacerta.core.CorePackageMonitor.classify(
            packageName = normalized,
            ownPackageName = this.packageName,
            settings = passiveProbeSettings,
        )
        if (!classification.canScan) return classification.reason
        return if (normalized !in SelectedRideAppStore.read(applicationContext)) {
            "Aplicativo nao selecionado pelo usuario: ${dollar}normalized."
        } else {
            classification.reason
        }
    } // manual_selected_apps_reason_0_1_127
"""
    service = replaceKotlinFunction127(service, "    private fun scanBlockReason(packageName: String?): String", blockReasonReplacement)

    if ("card_decision_signature_0_1_127" !in service) {
        val anchor = """            return // global_inactive_clear_now_0_1_124
        }
        universalLastActiveReadAtMillis = readNowMillis
"""
        if (anchor !in service) throw GradleException("Fim da validacao do card nao encontrado para assinatura estavel.")
        service = service.replaceFirst(
            anchor,
            """            return // global_inactive_clear_now_0_1_124
        }
        val cardDecisionSignature = passengerIdentity.candidates.single() + "|" + trigger.addressSignature // card_decision_signature_0_1_127
        universalLastActiveReadAtMillis = readNowMillis
""",
        )
    }

    val processStart = service.indexOf("    private suspend fun processRideText(")
    val processEnd = service.indexOf("    } // universal_stable_process_0_1_101", processStart)
    if (processStart < 0 || processEnd < 0) throw GradleException("Processamento universal nao encontrado para estabilizar a rota.")
    var processRegion = service.substring(processStart, processEnd)
    val screenStartToken = "        val analysisHash = trigger.screenHash // global_full_screen_hash_0_1_124\n"
    val screenStart = processRegion.indexOf(screenStartToken)
    val screenEndToken = "        }\n"
    val launchStart = processRegion.indexOf("        universalRouteJob = scope.launch {", screenStart)
    val launchEnd = if (launchStart >= 0) processRegion.indexOf(screenEndToken, launchStart) else -1
    if (screenStart < 0 || launchStart < 0 || launchEnd < 0) {
        throw GradleException("Bloco de troca de tela/rota nao encontrado para 0.1.127.")
    }
    val launchBlockEnd = launchEnd + screenEndToken.length
    val stableBlock = """        val analysisHash = trigger.screenHash // global_full_screen_hash_0_1_124
        val cardChanged = universalActiveAddressSignature != cardDecisionSignature
        if (cardChanged) {
            universalScreenGeneration += 1L
            universalRouteJob?.cancel()
            universalActiveAddressSignature = cardDecisionSignature
            lastSnapshotHash = analysisHash
            lastAnalyzedHash = null
            pendingAnalysis = null
            analyzing = false
            currentDistanceKm = null
            rememberBubbleReason("universal_waiting", "Novo card identificado; destino em calculo.")
            publishRuntimeValidationTrigger(trigger)
            showOverlay(RadarColor.Default, distanceKm = null)
            traceEvent("universal.card.changed hash=${dollar}analysisHash yellow=true signature=${dollar}{cardDecisionSignature.hashCode()}")
        } else {
            if (lastSnapshotHash != analysisHash) {
                traceEvent("universal.screen.cosmetic_change ignored=true hash=${dollar}analysisHash signature=${dollar}{cardDecisionSignature.hashCode()}")
            }
            if (lastAnalyzedHash != null || universalRouteJob?.isActive == true) return
            lastSnapshotHash = analysisHash
        } // stable_card_signature_route_0_1_127

        val generation = universalScreenGeneration
        val fields = RideFields(
            pickup = trigger.pickup,
            destination = trigger.destination,
        )
        universalRouteJob = scope.launch {
            analyzeUniversalTwoAddress(
                snapshotText = snapshotText,
                fields = fields,
                screenHash = analysisHash,
                addressSignature = cardDecisionSignature,
                generation = generation,
            )
        }
"""
    processRegion = processRegion.substring(0, screenStart) + stableBlock + processRegion.substring(launchBlockEnd)
    service = service.substring(0, processStart) + processRegion + service.substring(processEnd)

    if ("fast_red_continues_exact_route_0_1_127" !in service) {
        val oldFastEnd = """            applyUniversalTwoAddressResult(
                fastOutsideResult,
                screenHash,
                addressSignature,
                generation,
            )
            return
        } // subsecond_exact_red_lower_bound_0_1_125
"""
        if (oldFastEnd !in service) throw GradleException("Retorno antecipado do vermelho rapido nao encontrado.")
        val newFastEnd = """            rememberBubbleReason("universal_fast_red", fastOutsideResult.reason)
            showOverlay(RadarColor.Red, distanceKm = null)
            traceEvent("universal.fast_red provisional=true exact_route_continues=true")
        } // subsecond_exact_red_lower_bound_0_1_125 fast_red_continues_exact_route_0_1_127
"""
        service = service.replaceFirst(oldFastEnd, newFastEnd)
    }

    var main = mainFile.readText()

    val installedAppsReplacement = """@Composable
private fun InstalledRideAppsCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedPackages by remember { mutableStateOf(SelectedRideAppStore.read(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                selectedPackages = SelectedRideAppStore.read(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ExpandableCard(title = "Aplicativos que a bolinha pode ler", initiallyExpanded = true) {
        Text(
            "Nenhum aplicativo vem marcado. Escolha manualmente somente os aplicativos de corrida que deseja monitorar.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { context.startActivity(Intent(context, InstalledRideAppPickerActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Buscar aplicativos instalados")
        }
        if (selectedPackages.isEmpty()) {
            Text(
                "Nenhum aplicativo selecionado. A leitura de cards fica pausada ate voce escolher pelo menos um.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text("Aplicativos selecionados: ${dollar}{selectedPackages.size}", fontWeight = FontWeight.Bold)
            selectedPackages.forEach { packageName ->
                Text(packageName, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
} // no_selected_apps_picker_ui_0_1_126 superseded manual_apps_picker_restored_0_1_127
"""
    main = replaceKotlinFunction127(main, "private fun InstalledRideAppsCard() {", installedAppsReplacement)

    val monitoredReplacement = """@Composable
private fun MonitoredAppsCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val compatibility = settings to onChange
    InstalledRideAppsCard()
} // no_pre_registered_apps_ui_0_1_126 superseded manual_apps_card_restored_0_1_127
"""
    main = replaceKotlinFunction127(main, "private fun MonitoredAppsCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {", monitoredReplacement)

    val cardModelsReplacement = """@Composable
private fun CardModelsCard(
    cardTemplates: List<RideCardTemplate>,
    templateStatus: String,
    unreadTemplatePrints: Int,
    onPickCardModels: () -> Unit,
    onDeleteCardModel: (RideCardTemplate) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Modelos de cards opcionais", fontWeight = FontWeight.Bold)
            Text(
                "Nenhum modelo nasce cadastrado. Use prints somente quando um aplicativo ou formato de card precisar ser ensinado manualmente.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Modelos cadastrados: ${dollar}{cardTemplates.size}")
            Button(onClick = onPickCardModels, modifier = Modifier.fillMaxWidth()) {
                Text("Anexar modelos de cards (prints)")
            }
            if (templateStatus.isNotBlank()) Text(templateStatus, style = MaterialTheme.typography.bodySmall)
            if (unreadTemplatePrints > 0) {
                Text("Prints sem leitura: ${dollar}unreadTemplatePrints", style = MaterialTheme.typography.bodySmall)
            }
            if (cardTemplates.isEmpty()) {
                Text("Nenhum modelo cadastrado.", style = MaterialTheme.typography.bodySmall)
            } else {
                cardTemplates.forEach { template ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(template.name, fontWeight = FontWeight.Bold)
                            Text(template.packageName ?: "app nao identificado", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { onDeleteCardModel(template) }) { Text("Apagar") }
                    }
                }
            }
        }
    }
} // no_pre_registered_cards_ui_0_1_126 superseded manual_card_models_restored_0_1_127
"""
    main = replaceKotlinFunction127(main, "private fun CardModelsCard(", cardModelsReplacement)

    val registeredCardsReplacement = """@Composable
private fun RegisteredCardsModuleCard(
    cardTemplates: List<RideCardTemplate>,
    templateStatus: String,
    unreadTemplatePrints: Int,
    onPickCardModels: () -> Unit,
    onDeleteCardModel: (RideCardTemplate) -> Unit,
) {
    CardModelsCard(
        cardTemplates = cardTemplates,
        templateStatus = templateStatus,
        unreadTemplatePrints = unreadTemplatePrints,
        onPickCardModels = onPickCardModels,
        onDeleteCardModel = onDeleteCardModel,
    )
} // no_registered_cards_module_0_1_126 superseded registered_cards_module_restored_0_1_127
"""
    main = replaceKotlinFunction127(main, "private fun RegisteredCardsModuleCard(", registeredCardsReplacement)

    val oldReportBlock = """        appendLine("Filtro por aplicativos pre-cadastrados: removido")
        appendLine("Modelos de cards como requisito: removidos")
        appendLine("Selecao manual de apps bloqueia a bolinha: false")
        appendLine("Politica de leitura: pacote externo comum + passageiro + pelo menos dois enderecos")
        appendLine("Pacotes passivos bloqueados: sistema, launcher, teclado, Google Maps e Waze") // diagnostic_policy_no_pre_registered_0_1_126
"""
    val newReportBlock = """        appendLine("Aplicativos pre-cadastrados: nenhum")
        appendLine("Selecao manual de apps obrigatoria: true")
        appendLine("Aplicativos selecionados: ${dollar}{SelectedRideAppStore.read(context).joinToString(", ").ifBlank { "nenhum" }}")
        appendLine("Modelos de cards: opcionais; cadastrados somente pelo usuario")
        appendLine("Modelos cadastrados agora: ${dollar}{cardTemplates.size}")
        appendLine("Politica de leitura: app escolhido + passageiro + pelo menos dois enderecos")
        appendLine("Vermelho rapido provisório continua calculando a rota exata para preencher o km")
        appendLine("Pacotes passivos bloqueados: sistema, launcher, teclado, Google Maps e Waze") // diagnostic_policy_no_pre_registered_0_1_126 manual_policy_report_0_1_127
"""
    if (oldReportBlock in main) {
        main = main.replaceFirst(oldReportBlock, newReportBlock)
    } else if ("manual_policy_report_0_1_127" !in main) {
        throw GradleException("Bloco de politica do relatorio nao encontrado para 0.1.127.")
    }

    listOf(
        "manual_selection_storage_ready_0_1_127",
        "manual_cards_preserved_0_1_127",
        "manual_selected_apps_gate_0_1_127",
        "stable_card_signature_route_0_1_127",
        "fast_red_continues_exact_route_0_1_127",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Contrato 0.1.127 ausente no servico: $marker")
    }
    listOf(
        "manual_apps_picker_restored_0_1_127",
        "manual_card_models_restored_0_1_127",
        "registered_cards_module_restored_0_1_127",
        "Buscar aplicativos instalados",
        "Anexar modelos de cards (prints)",
        "manual_policy_report_0_1_127",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Contrato 0.1.127 ausente na interface: $marker")
    }
    if ("removedTemplates126.forEach" in service) throw GradleException("Limpeza destrutiva de modelos ainda ativa.")
    if ("return\n        } // subsecond_exact_red_lower_bound_0_1_125" in service) {
        throw GradleException("Vermelho rapido ainda encerra a rota antes do km exato.")
    }

    serviceFile.writeText(service)
    mainFile.writeText(main)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchManualAppsCardsExactRoute127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
