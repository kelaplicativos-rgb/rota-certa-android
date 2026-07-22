// Home 0.1.115: as bolinhas internas passam a ser grupos de navegacao.
// Somente o grupo selecionado aparece abaixo da central, sem repetir todos os
// cartoes de configuracao na mesma tela e sem obrigar um segundo toque em Abrir.

fun replaceGroupedRegion115(
    source: String,
    startToken: String,
    endToken: String,
    replacement: String,
    label: String,
): String {
    val start = source.indexOf(startToken)
    val end = if (start >= 0) source.indexOf(endToken, start + startToken.length) else -1
    if (start < 0 || end <= start) throw GradleException("Regiao ausente para $label")
    return source.substring(0, start) + replacement + source.substring(end)
}

fun enforceGroupedBubbleHome115(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado.")
    var text = file.readText()

    if ("grouped_bubble_home_0_1_115" !in text) {
        if ("import androidx.compose.ui.text.style.TextAlign" !in text) {
            text = text.replace(
                "import androidx.compose.ui.text.font.FontWeight\n",
                "import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.style.TextAlign\n",
            )
        }

        text = text.replace(
            "    var bubbleControlSettings by remember(settings) { mutableStateOf(settings) } // in_app_bubble_immediate_state_0_1_98\n",
            "    // in_app_bubble_immediate_state_0_1_98 substituido pela navegacao agrupada 0.1.115\n",
        )

        val tabAnchor = "    var tab by remember { mutableStateOf(TAB_ANALYSIS) }\n"
        if (tabAnchor !in text) throw GradleException("Estado de navegacao principal nao encontrado.")
        text = text.replaceFirst(
            tabAnchor,
            tabAnchor + "    var selectedBubbleGroup by remember { mutableStateOf(BUBBLE_GROUP_DESTINATION) } // grouped_bubble_state_0_1_115\n",
        )

        val requestedStart = text.indexOf("        val requestedTab = launchIntent?.getStringExtra(EXTRA_OPEN_TAB)")
        val highlightedStart = if (requestedStart >= 0) text.indexOf("        highlightedSavedPlaceId =", requestedStart) else -1
        if (requestedStart < 0 || highlightedStart <= requestedStart) {
            throw GradleException("Navegacao inicial nao encontrada para os grupos.")
        }
        val launchReplacement = """        val requestedTab = launchIntent?.getStringExtra(EXTRA_OPEN_TAB)
        tab = if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_TOOLS || requestedTab == TAB_HISTORY) {
            requestedTab
        } else {
            TAB_ANALYSIS
        }
        selectedBubbleGroup = when (tab) {
            TAB_TOOLS -> BUBBLE_GROUP_TOOLS
            TAB_HISTORY -> BUBBLE_GROUP_REPORTS
            TAB_CONFIG -> BUBBLE_GROUP_GENERAL
            else -> BUBBLE_GROUP_DESTINATION
        } // grouped_bubble_launch_0_1_115
"""
        text = text.substring(0, requestedStart) + launchReplacement + text.substring(highlightedStart)

        val callStart = listOf(
            text.indexOf("            UnifiedAppControlBubbles("),
            text.indexOf("                UnifiedAppControlBubbles("),
        ).filter { it >= 0 }.minOrNull() ?: -1
        val whenStart = if (callStart >= 0) text.indexOf("\n            when (tab) {", callStart) else -1
        if (callStart < 0 || whenStart <= callStart) {
            throw GradleException("Chamada da Central de bolinhas nao encontrada.")
        }
        val groupedCall = """            UnifiedAppControlBubbles(
                selectedGroup = selectedBubbleGroup,
                onSelectGroup = { group ->
                    selectedBubbleGroup = group
                    tab = when (group) {
                        BUBBLE_GROUP_DESTINATION -> TAB_ANALYSIS
                        BUBBLE_GROUP_REPORTS -> TAB_HISTORY
                        BUBBLE_GROUP_TOOLS -> TAB_TOOLS
                        else -> TAB_CONFIG
                    }
                },
            ) // grouped_bubble_navigation_0_1_115

"""
        text = text.substring(0, callStart) + groupedCall + text.substring(whenStart + 1)

        text = replaceGroupedRegion115(
            source = text,
            startToken = "@Composable\nprivate fun UnifiedAppControlBubbles(",
            endToken = "@Composable\nprivate fun AnalysisScreen(",
            replacement = """private const val BUBBLE_GROUP_GENERAL = "general"
private const val BUBBLE_GROUP_READING = "reading"
private const val BUBBLE_GROUP_DESTINATION = "destination"
private const val BUBBLE_GROUP_ALERTS = "alerts"
private const val BUBBLE_GROUP_APPEARANCE = "appearance"
private const val BUBBLE_GROUP_ACCESS = "access"
private const val BUBBLE_GROUP_REPORTS = "reports"
private const val BUBBLE_GROUP_BACKUP = "backup"
private const val BUBBLE_GROUP_TOOLS = "tools"

@Composable
private fun UnifiedAppControlBubbles(
    selectedGroup: String,
    onSelectGroup: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Central de bolinhas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Cada bolinha abre um grupo. Os controles ON/OFF ficam dentro do grupo selecionado.",
            style = MaterialTheme.typography.bodySmall,
        )
        BubbleGroupRow(
            items = listOf(
                "Rota" to BUBBLE_GROUP_GENERAL,
                "Leitura" to BUBBLE_GROUP_READING,
                "Destino" to BUBBLE_GROUP_DESTINATION,
            ),
            selectedGroup = selectedGroup,
            onSelectGroup = onSelectGroup,
        )
        BubbleGroupRow(
            items = listOf(
                "Alertas" to BUBBLE_GROUP_ALERTS,
                "Aparencia" to BUBBLE_GROUP_APPEARANCE,
                "Permissoes" to BUBBLE_GROUP_ACCESS,
            ),
            selectedGroup = selectedGroup,
            onSelectGroup = onSelectGroup,
        )
        BubbleGroupRow(
            items = listOf(
                "Relatorios" to BUBBLE_GROUP_REPORTS,
                "Backup" to BUBBLE_GROUP_BACKUP,
                "Ferramentas" to BUBBLE_GROUP_TOOLS,
            ),
            selectedGroup = selectedGroup,
            onSelectGroup = onSelectGroup,
        )
    }
} // grouped_bubble_home_0_1_115

@Composable
private fun BubbleGroupRow(
    items: List<Pair<String, String>>,
    selectedGroup: String,
    onSelectGroup: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        items.forEach { (label, group) ->
            AppControlBubble(
                label = label,
                selected = selectedGroup == group,
                onClick = { onSelectGroup(group) },
            )
        }
    }
}

@Composable
private fun AppControlBubble(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(82.dp),
        shape = CircleShape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (selected) 12.dp else 2.dp,
            pressedElevation = 14.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

private fun openWhatsAppApp(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
        ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
    if (launchIntent != null) {
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } else {
        Toast.makeText(context, "WhatsApp nao encontrado.", Toast.LENGTH_SHORT).show()
    }
}

""",
            label = "Central agrupada",
        )

        val analysisStart = text.indexOf("@Composable\nprivate fun AnalysisScreen(")
        val analysisEnd = if (analysisStart >= 0) text.indexOf("@Composable\nprivate fun LiveReadingCard(", analysisStart) else -1
        if (analysisStart < 0 || analysisEnd <= analysisStart) throw GradleException("AnalysisScreen nao encontrada.")
        var analysisBlock = text.substring(analysisStart, analysisEnd)
        val liveReadingCall = """    LiveReadingCard(
        liveEnabled = liveEnabled,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onRefreshLiveState = onRefreshLiveState,
    )

    Spacer(Modifier.height(10.dp))
"""
        analysisBlock = analysisBlock.replace(liveReadingCall, "")
        val homeCallAnchor = "    HomeDecisionCard(\n"
        if (homeCallAnchor !in analysisBlock) throw GradleException("Grupo Destino sem HomeDecisionCard.")
        analysisBlock = analysisBlock.replaceFirst(
            homeCallAnchor,
            """    Text("Destino, Casa e Alfinete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("Endereco, raio e Google Maps ficam reunidos neste grupo.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(10.dp))
    HomeDecisionCard(
""",
        )
        val resultAnchor = "    latestResult?.let {\n"
        if (resultAnchor !in analysisBlock) throw GradleException("Fim do grupo Destino nao encontrado.")
        analysisBlock = analysisBlock.replaceFirst(
            resultAnchor,
            """    Spacer(Modifier.height(10.dp))
    MapsAndAdvancedCard(
        draft = quickSettings,
        onDraftChange = { quickSettings = it },
        onSave = { saveQuickSettings(quickSettings) },
    )

    $resultAnchor""",
        )
        text = text.substring(0, analysisStart) + analysisBlock + text.substring(analysisEnd)

        text = text.replaceFirst(
            """                TAB_CONFIG -> SettingsScreen(
                    settings = settings,
""",
            """                TAB_CONFIG -> SettingsScreen(
                    selectedGroup = selectedBubbleGroup,
                    settings = settings,
                    liveEnabled = liveEnabled,
                    onOpenAccessibilitySettings = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onRefreshLiveState = { liveEnabled = isLiveAccessibilityEnabled(context) },
""",
        )
        text = text.replaceFirst(
            """                TAB_TOOLS -> ToolsScreen(
                    onOpenBlaBlaCarCollector = {
""",
            """                TAB_TOOLS -> ToolsScreen(
                    onOpenWhatsApp = { openWhatsAppApp(context) },
                    onOpenBlaBlaCarCollector = {
""",
        )
        text = text.replaceFirst(
            "                TAB_HISTORY -> HistoryScreen(history)\n",
            "                TAB_HISTORY -> ReportsGroupScreen(diagnostic, cardTemplates, history, ::registerRideCard)\n",
        )

        text = replaceGroupedRegion115(
            source = text,
            startToken = "@Composable\nprivate fun SettingsScreen(",
            endToken = "@Composable\nprivate fun SystemControlCard(",
            replacement = """@Composable
private fun SettingsScreen(
    selectedGroup: String,
    settings: AppSettings,
    liveEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onRefreshLiveState: () -> Unit,
    diagnostic: LiveDiagnostic?,
    cardTemplates: List<RideCardTemplate>,
    savedPlaces: List<SavedPlace>,
    backupStatus: String,
    highlightedSavedPlaceId: String?,
    radarImportSummary: RadarImportSummary,
    radarImportStatus: String,
    onSave: (AppSettings) -> Unit,
    onRegisterRideCard: (String?, String) -> Unit,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
    onRegionDetected: (DeviceRegion) -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onImportRadarFile: () -> Unit,
    onOpenMapaRadar: () -> Unit,
    onClearImportedRadars: () -> Unit,
) {
    val context = LocalContext.current
    val locationService = remember { DeviceLocationService(context) }
    val gpsAddressResolver = remember { GpsAddressResolver(context) }
    val scope = rememberCoroutineScope()

    var draft by remember(settings) { mutableStateOf(settings) }
    var gpsStatus by remember { mutableStateOf("") }
    var pendingLocationTarget by remember { mutableStateOf<LocationTarget?>(null) }

    fun saveDraft(updated: AppSettings) {
        draft = updated
        onSave(updated)
    }

    fun captureGps(target: LocationTarget) {
        scope.launch {
            gpsStatus = "Buscando sinal de GPS..."
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                gpsStatus = "Nao consegui captar o GPS. Autorize a localizacao e tente novamente."
                return@launch
            }
            val resolved = gpsAddressResolver.resolve(coordinate)
            if (resolved.region.city.isNotBlank() || resolved.region.country.isNotBlank()) onRegionDetected(resolved.region)
            val address = resolved.addressLine.ifBlank { formatCoordinate(coordinate) }
            draft = when (target) {
                LocationTarget.Home -> draft.copy(homeAddress = address, homeCoordinate = coordinate)
                LocationTarget.Alternative -> draft.copy(alternativeAddress = address, alternativeCoordinate = coordinate)
            }
            gpsStatus = "GPS preenchido. Confira e toque em Salvar."
        }
    }

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val target = pendingLocationTarget
        pendingLocationTarget = null
        if (target != null) captureGps(target)
    }

    fun requestGps(target: LocationTarget) {
        pendingLocationTarget = target
        gpsPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(groupedBubbleTitle(selectedGroup), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(groupedBubbleDescription(selectedGroup), style = MaterialTheme.typography.bodySmall)
        when (selectedGroup) {
            BUBBLE_GROUP_GENERAL -> SystemControlCard(settings = draft, onChange = ::saveDraft)
            BUBBLE_GROUP_READING -> LiveReadingCard(
                liveEnabled = liveEnabled,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onRefreshLiveState = onRefreshLiveState,
            )
            BUBBLE_GROUP_ALERTS -> {
                SavedPlacesCard(
                    savedPlaces = savedPlaces,
                    highlightedSavedPlaceId = highlightedSavedPlaceId,
                    onRenameSavedPlace = onRenameSavedPlace,
                    onDeleteSavedPlace = onDeleteSavedPlace,
                )
                Spacer(Modifier.height(10.dp))
                RadarImportCard(
                    summary = radarImportSummary,
                    importStatus = radarImportStatus,
                    onPickFile = onImportRadarFile,
                    onOpenMapaRadar = onOpenMapaRadar,
                    onClearRadars = onClearImportedRadars,
                )
            }
            BUBBLE_GROUP_APPEARANCE -> BubbleSettingsCard(settings = draft, onChange = ::saveDraft)
            BUBBLE_GROUP_ACCESS -> AlwaysLocationPermissionCard(
                hasAlwaysPermission = hasAlwaysLocationPermission(context),
                onOpenLocationSettings = { openAppLocationSettings(context) },
            )
            BUBBLE_GROUP_BACKUP -> BackupCard(
                status = backupStatus,
                onCreateBackup = onCreateBackup,
                onRestoreBackup = onRestoreBackup,
            )
            else -> SystemControlCard(settings = draft, onChange = ::saveDraft)
        }
    }
} // grouped_settings_screen_0_1_115

private fun groupedBubbleTitle(group: String): String = when (group) {
    BUBBLE_GROUP_GENERAL -> "Controle geral"
    BUBBLE_GROUP_READING -> "Leitura ao vivo"
    BUBBLE_GROUP_ALERTS -> "Alertas, locais e radares"
    BUBBLE_GROUP_APPEARANCE -> "Bolinha e aparencia"
    BUBBLE_GROUP_ACCESS -> "Permissoes e GPS continuo"
    BUBBLE_GROUP_BACKUP -> "Backup dos dados"
    else -> "Controle geral"
}

private fun groupedBubbleDescription(group: String): String = when (group) {
    BUBBLE_GROUP_GENERAL -> "Liga ou pausa o Rota Certa e os avisos."
    BUBBLE_GROUP_READING -> "Autoriza a Acessibilidade e controla a leitura da tela."
    BUBBLE_GROUP_ALERTS -> "Reune alertas de proximidade, locais salvos e radares importados."
    BUBBLE_GROUP_APPEARANCE -> "Ajusta transparencia, contraste e aparencia da bolinha flutuante."
    BUBBLE_GROUP_ACCESS -> "Configure a permissao de localizacao para funcionamento em movimento."
    BUBBLE_GROUP_BACKUP -> "Crie ou restaure uma copia das configuracoes e dos dados."
    else -> "Ajustes do Rota Certa."
}

""",
            label = "SettingsScreen agrupada",
        )

        text = replaceGroupedRegion115(
            source = text,
            startToken = "@Composable\nprivate fun ExpandableCard(",
            endToken = "@Composable\nprivate fun SettingsSwitchRow(",
            replacement = """@Composable
private fun ExpandableCard(
    title: String,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            content()
        }
    }
} // grouped_card_always_open_0_1_115

""",
            label = "Cartoes internos sem segundo toque",
        )

        text = replaceGroupedRegion115(
            source = text,
            startToken = "@Composable\nprivate fun ToolsScreen(",
            endToken = "@Composable\nprivate fun HistoryScreen(",
            replacement = """@Composable
private fun ToolsScreen(
    onOpenWhatsApp: () -> Unit,
    onOpenBlaBlaCarCollector: () -> Unit,
    onClearClipboard: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ferramentas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("WhatsApp", fontWeight = FontWeight.Bold)
                Text("Abre o WhatsApp instalado no celular.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onOpenWhatsApp, modifier = Modifier.fillMaxWidth()) { Text("Abrir WhatsApp") }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Coletor BlaBlaCar", fontWeight = FontWeight.Bold)
                Text(
                    "Registro manual de viagem logada: passageiros, telefones, WhatsApp, rotas, faturamento, despesas e lucro.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onOpenBlaBlaCarCollector, modifier = Modifier.fillMaxWidth()) { Text("Abrir coletor") }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Area de transferencia", fontWeight = FontWeight.Bold)
                Text("Remove manualmente o texto copiado do celular.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onClearClipboard, modifier = Modifier.fillMaxWidth()) { Text("Limpar area de transferencia") }
            }
        }
    }
}

@Composable
private fun ReportsGroupScreen(
    diagnostic: LiveDiagnostic?,
    cardTemplates: List<RideCardTemplate>,
    history: List<AnalysisResult>,
    onRegisterRideCard: (String?, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Relatorios e historico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        DiagnosticExpander(
            diagnostic = diagnostic,
            cardTemplates = cardTemplates,
            onRegisterRideCard = onRegisterRideCard,
        )
        Text("Historico de decisoes", fontWeight = FontWeight.Bold)
        HistoryScreen(history)
    }
} // grouped_reports_tools_0_1_115

""",
            label = "Ferramentas e relatorios agrupados",
        )
    }

    listOf(
        "grouped_bubble_home_0_1_115",
        "grouped_bubble_navigation_0_1_115",
        "grouped_settings_screen_0_1_115",
        "grouped_card_always_open_0_1_115",
        "grouped_reports_tools_0_1_115",
        "Cada bolinha abre um grupo",
        "BUBBLE_GROUP_DESTINATION",
        "selectedBubbleGroup",
        "TextAlign.Center",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Home agrupada incompleta: $marker")
    }

    if ("label + \"\\n\" + if (active)" in text) {
        throw GradleException("Regressao: bolinhas ainda exibem ON/OFF com texto quebrado.")
    }
    if ("Text(if (expanded) \"Fechar\" else \"Abrir\")" in text) {
        throw GradleException("Regressao: grupo ainda exige segundo toque em Abrir.")
    }
    if ("AppControlBubble(\"Backup\", null, onOpenSettings)" in text || "AppControlBubble(\"Mais\"" in text) {
        throw GradleException("Regressao: bolinhas antigas ainda duplicam a tela de configuracao.")
    }

    file.writeText(text)
}

val inAppGroupedBubbleHome115 by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn("inAppBubbleHomeFinal", "inAppBubbleImmediateState")
    doLast { enforceGroupedBubbleHome115(mainFile.asFile) }
}

inAppGroupedBubbleHome115.configure {
    mustRunAfter("inAppBubbleHomeFinal", "inAppBubbleImmediateState", "functionalBubbleTogglesFinal")
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(inAppGroupedBubbleHome115)
    doFirst {
        enforceGroupedBubbleHome115(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("test") }.configureEach {
    dependsOn(inAppGroupedBubbleHome115)
}
