// Remove integralmente o cadastro de cards da interface e do runtime.
// Contrato final: leitura universal; dois ou mais enderecos numerados usam o
// ultimo como destino; zero ou um endereco deixa a bolinha cinza.

fun noCardReplaceRegion(
    source: String,
    startToken: String,
    endToken: String,
    replacement: String = "",
    label: String,
): String {
    val start = source.indexOf(startToken)
    val end = if (start >= 0) source.indexOf(endToken, start + startToken.length) else -1
    if (start < 0 || end <= start) throw GradleException("Regiao ausente: $label")
    return source.substring(0, start) + replacement + source.substring(end)
}

fun noCardRemoveBalancedCall(source: String, token: String): String {
    var result = source
    while (true) {
        val start = result.indexOf(token)
        if (start < 0) return result
        val open = result.indexOf('(', start)
        if (open < 0) throw GradleException("Abertura da chamada ausente: $token")
        var depth = 0
        var index = open
        var end = -1
        while (index < result.length) {
            when (result[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) {
                        end = index + 1
                        break
                    }
                }
            }
            index += 1
        }
        if (end < 0) throw GradleException("Fechamento da chamada ausente: $token")
        while (end < result.length && (result[end] == '\n' || result[end] == '\r' || result[end] == ' ' || result[end] == '\t')) end += 1
        result = result.substring(0, start) + result.substring(end)
    }
}

val universalNoCardRuntimeFinal by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.files(mainFile, serviceFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalIdempotenceCompatibilityGuard"))

    doLast {
        val mainSource = mainFile.asFile
        val serviceSource = serviceFile.asFile
        if (!mainSource.exists() || !serviceSource.exists()) throw GradleException("Fontes principais nao encontrados")

        var main = mainSource.readText()
        var service = serviceSource.readText()

        if ("universal_no_card_registration_0_1_102" !in main) {
            if ("import kotlinx.coroutines.flow.first\n" !in main) {
                main = main.replace(
                    "import kotlinx.coroutines.launch\n",
                    "import kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.launch\n",
                )
            }

            if ("    fun registerRideCard(packageName: String?, text: String) {" in main) {
                main = noCardReplaceRegion(
                    main,
                    "    fun registerRideCard(packageName: String?, text: String) {",
                    "    fun renameSavedPlace(",
                    label = "funcoes de cadastro de cards",
                )
            }
            if ("    val cardModelPicker = rememberLauncherForActivityResult" in main) {
                main = noCardReplaceRegion(
                    main,
                    "    val cardModelPicker = rememberLauncherForActivityResult",
                    "    val radarFilePicker = rememberLauncherForActivityResult",
                    label = "seletor de prints de cards",
                )
            }
            if ("    LaunchedEffect(cardTemplates.size) {" in main) {
                main = noCardReplaceRegion(
                    main,
                    "    LaunchedEffect(cardTemplates.size) {",
                    "    LaunchedEffect(launchIntent)",
                    replacement = "    LaunchedEffect(launchIntent)",
                    label = "efeito de modelos",
                )
            }

            main = noCardRemoveBalancedCall(main, "    CardModelsCard(")

            if ("@Composable\nprivate fun CardModelsCard(" in main) {
                main = noCardReplaceRegion(
                    main,
                    "@Composable\nprivate fun CardModelsCard(",
                    "@Composable\nprivate fun DiagnosticExpander(",
                    label = "componente de modelos",
                )
            }

            if ("@Composable\nprivate fun DiagnosticExpander(" in main) {
                main = noCardReplaceRegion(
                    main,
                    "@Composable\nprivate fun DiagnosticExpander(",
                    "@Composable\nprivate fun SavedPlacesCard(",
                    replacement = """@Composable
private fun DiagnosticExpander(
    diagnostic: LiveDiagnostic?,
) {
    val context = LocalContext.current
    ExpandableCard(title = "Diagnostico tecnico", initiallyExpanded = false) {
        if (diagnostic == null) {
            Text(
                "Nenhum diagnostico registrado. Mantenha dois enderecos numerados visiveis para testar.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Rota Certa diagnostico", diagnostic.toShareText()))
                    Toast.makeText(context, "Diagnostico copiado", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copiar diagnostico") }
            Text("Cor: ${'$'}{diagnostic.bubbleColor}")
            Text("Etapa: ${'$'}{diagnostic.stage}")
            Text("Pacote: ${'$'}{diagnostic.packageName ?: "nao informado"}")
            Text("Motivo: ${'$'}{diagnostic.reason}", style = MaterialTheme.typography.bodySmall)
            diagnostic.destination?.takeIf { it.isNotBlank() }?.let {
                Text("Destino: ${'$'}it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

""",
                    label = "diagnostico dependente de cards",
                )
            }

            main = main.replace("        MonitoredAppsCard(settings = draft, onChange = ::saveDraft)\n", "")
            if ("@Composable\nprivate fun MonitoredAppsCard(" in main) {
                main = noCardReplaceRegion(
                    main,
                    "@Composable\nprivate fun MonitoredAppsCard(",
                    "@Composable\nprivate fun ExpandableCard(",
                    label = "selecao de apps monitorados",
                )
            }

            val selectedPackagesStart = main.indexOf("    val selectedPackages = buildList {")
            if (selectedPackagesStart >= 0) {
                val selectedPackagesEnd = main.indexOf("\n\n    return buildString", selectedPackagesStart)
                if (selectedPackagesEnd <= selectedPackagesStart) throw GradleException("Fim da lista de pacotes ausente")
                main = main.substring(0, selectedPackagesStart) + main.substring(selectedPackagesEnd + 2)
            }

            val reportModelsStart = main.indexOf("        appendLine(\"--- MODELOS DE CARDS ---\")")
            if (reportModelsStart >= 0) {
                val reportModelsEnd = main.indexOf("        appendLine(\"--- LOCAIS E ALERTAS ---\")", reportModelsStart)
                if (reportModelsEnd <= reportModelsStart) throw GradleException("Fim da secao de modelos ausente")
                main = main.substring(0, reportModelsStart) + main.substring(reportModelsEnd)
            }

            val migrationAnchor = "    var radarImportStatus by remember { mutableStateOf(\"\") }\n"
            if (migrationAnchor !in main) throw GradleException("Ponto de migracao sem cards ausente")
            main = main.replaceFirst(
                migrationAnchor,
                migrationAnchor + """    LaunchedEffect(repository) {
        repository.cardTemplates.first().forEach { template ->
            repository.removeCardTemplate(template.id)
        }
    }
    LaunchedEffect(settings.requireRegisteredRideCard, settings.restrictToSelectedRideApps) {
        if (settings.requireRegisteredRideCard || settings.restrictToSelectedRideApps) {
            repository.saveSettings(
                settings.copy(
                    requireRegisteredRideCard = false,
                    restrictToSelectedRideApps = false,
                ),
            )
        }
    }

""",
            )

            main = main.replace(
                "                repository.restoreBackupJson(backupJson)\n",
                """                repository.restoreBackupJson(backupJson)
                repository.cardTemplates.first().forEach { template ->
                    repository.removeCardTemplate(template.id)
                }
""",
            )
            main = Regex("\"Backup restaurado:[^\"]*\"").replace(main, "\"Backup restaurado com sucesso.\"")

            val forbiddenLineFragments = listOf(
                "val cardTemplates by repository.cardTemplates",
                "val ocrService = remember { OcrService",
                "templateStatus",
                "unreadTemplatePrints",
                "cardTemplates = cardTemplates",
                "cardTemplates: List<RideCardTemplate>",
                "onDeleteCardModel",
                "onPickCardModels",
                "onRegisterRideCard",
                "Cards cadastrados:",
                "Card reconhecido:",
                "Card cadastrado obrigatorio:",
                "Modelos carregados na bolinha:",
                "Modo restrito apps selecionados:",
                "Pacotes monitorados:",
                "Pacotes selecionados:",
            )
            main = main.lineSequence()
                .filterNot { line -> forbiddenLineFragments.any { fragment -> fragment in line } }
                .joinToString("\n")

            main = main.replace(
                "Operando. Verde/vermelho aparecem quando o app reconhece um card de corrida cadastrado.",
                "Operando em qualquer tela. Dois ou mais enderecos completos e numerados acionam o calculo; o ultimo e o destino.",
            )
            main = main.replace(
                "Aceite corridas cujo destino final fique dentro do raio definido por voce.",
                "Ao encontrar dois ou mais enderecos numerados, o ultimo e calculado ate o ponto definido por voce.",
            )
            main = main.replace(
                "Salva configuracoes, modelos de cards, locais e alertas de proximidade em um arquivo do celular.",
                "Salva configuracoes, locais e alertas de proximidade em um arquivo do celular.",
            )
            main = main.replace(
                "        appendLine(\"Alertas proximidade ligados: ${'$'}{settings.proximityAlertsEnabled}\")\n",
                """        appendLine("Alertas proximidade ligados: ${'$'}{settings.proximityAlertsEnabled}")
        appendLine("Leitura universal de tela: true")
""",
            )

            main += "\n// universal_no_card_registration_0_1_102\n"
        }

        if ("universal_no_card_runtime_0_1_102" !in service) {
            service = Regex("\\s*scope\\.launch \\{ repository\\.cardTemplates\\.collect \\{ currentCardTemplates = it \\} \\}\\n")
                .replace(service, "\n")

            val settingsAnchor = "            currentSettings = repository.settings.first()\n"
            if (settingsAnchor !in service) throw GradleException("Carregamento de configuracoes ausente")
            service = service.replaceFirst(
                settingsAnchor,
                settingsAnchor + """            if (currentSettings.requireRegisteredRideCard || currentSettings.restrictToSelectedRideApps) {
                currentSettings = currentSettings.copy(
                    requireRegisteredRideCard = false,
                    restrictToSelectedRideApps = false,
                )
                repository.saveSettings(currentSettings)
            }
            repository.cardTemplates.first().forEach { template ->
                repository.removeCardTemplate(template.id)
            }
            currentCardTemplates = emptyList()
""",
            )
            service = service.replace(
                "            currentCardTemplates = repository.cardTemplates.first()\n",
                "            currentCardTemplates = emptyList()\n",
            )
            service = service.replace(
                ".putInt(KEY_STATE_TEMPLATE_COUNT, currentCardTemplates.size)",
                ".putInt(KEY_STATE_TEMPLATE_COUNT, 0)",
            )
            service = service.replace(
                "registeredCardMatched = registeredCardGate.matchedTemplateName,",
                "registeredCardMatched = null,",
            )
            service += "\n// universal_no_card_runtime_0_1_102\n"
        }

        val processStart = service.indexOf("    private suspend fun processRideText(")
        val processEnd = if (processStart >= 0) service.indexOf("    private fun resolveRidePackageForText(", processStart) else -1
        if (processStart < 0 || processEnd <= processStart) throw GradleException("Processamento universal final ausente")
        val processBlock = service.substring(processStart, processEnd)

        listOf("RideCardTemplateMatcher", "RegisteredCardAddressGate", "selectedRidePackages", "currentCardTemplates").forEach { forbidden ->
            if (forbidden in processBlock) throw GradleException("Cadastro de cards ainda interfere no runtime: $forbidden")
        }
        listOf("Modelos de cards", "Anexar modelos de cards", "Cadastrar texto lido como modelo", "CardModelsCard(", "cardModelPicker").forEach { forbidden ->
            if (forbidden in main) throw GradleException("Cadastro de cards ainda aparece na interface: $forbidden")
        }
        listOf("universal_no_card_registration_0_1_102", "Leitura universal de tela: true").forEach { marker ->
            if (marker !in main) throw GradleException("Remocao visual incompleta: $marker")
        }
        listOf("universal_no_card_runtime_0_1_102", "currentCardTemplates = emptyList()", ".putInt(KEY_STATE_TEMPLATE_COUNT, 0)").forEach { marker ->
            if (marker !in service) throw GradleException("Remocao runtime incompleta: $marker")
        }

        mainSource.writeText(main)
        serviceSource.writeText(service)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalNoCardRuntimeFinal)
}
