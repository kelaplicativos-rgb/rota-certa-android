val bubbleRegionShortcutFlow by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        patchBubbleRegionShortcutService(serviceFile.asFile)
        patchBubbleRegionShortcutMain(mainFile.asFile)
    }
}

bubbleRegionShortcutFlow.configure {
    mustRunAfter(
        "shortcutNavigationIdleReset",
        "patchBubbleShortcutClipboard",
        "patchUxPlacesAlertsRadars",
        "patchLiveReadingCardRestore",
        "patchBubbleCardParity",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleRegionShortcutFlow)
}

fun patchBubbleRegionShortcutService(file: java.io.File) {
    var text = file.readText()
    val original = text
    val dollar = "$"

    if ("private fun openRideRegionShortcut()" !in text) {
        text = text.replace(
"""    private fun openApp(tab: String? = null, expander: String? = null) {
""",
"""    private fun openRideRegionShortcut() {
        hideActionMenu()
        forceIdleOverlay("Abrindo Minha regiao de corridas pela bolinha.")
        traceEvent("shortcut.region.open gps_flow=true")
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_ANALYSIS)
                    .putExtra("br.com.mapeiaia.rotacerta.extra.OPEN_EXPANDER", "Minha regiao de corridas")
                    .putExtra("br.com.mapeiaia.rotacerta.extra.OPEN_REGION_SHORTCUT", true),
            )
        }.onFailure {
            toast("Nao consegui abrir Minha regiao de corridas agora.")
        }
    }

    private fun openApp(tab: String? = null, expander: String? = null) {
""",
        )
    }

    text = text.replace("label = \"🎯  Definir região de trabalho\"", "label = \"🎯  Minha região de corridas\"")
    text = text.replace("label = \"🎯  Definir regiao de trabalho\"", "label = \"🎯  Minha regiao de corridas\"")
    text = text.replace("label = \"🎯  Minha região de corridas\"", "label = \"🎯  Minha região de corridas\"")
    text = text.replace("label = \"🎯  Minha regiao de corridas\"", "label = \"🎯  Minha regiao de corridas\"")

    listOf(
        "action = { openApp(tab = TAB_TOOLS, expander = \"Definir regiao de trabalho\") }",
        "longAction = { openApp(tab = TAB_TOOLS, expander = \"Definir regiao de trabalho\") }",
        "action = { openApp(tab = TAB_TOOLS, expander = \"Minha regiao de corridas\") }",
        "longAction = { openApp(tab = TAB_TOOLS, expander = \"Minha regiao de corridas\") }",
        "action = { openApp(tab = TAB_ANALYSIS, expander = \"Minha regiao de corridas\") }",
        "longAction = { openApp(tab = TAB_ANALYSIS, expander = \"Minha regiao de corridas\") }",
    ).forEach { oldLine ->
        val replacement = oldLine.substringBefore("=") + "= { openRideRegionShortcut() }"
        text = text.replace(oldLine, replacement)
    }

    if ("bubble_region_shortcut_flow.patch_applied" !in text) {
        text = text.replace(
            "        traceEvent(\"shortcut.navigation.patch_applied=true\")\n",
            "        traceEvent(\"shortcut.navigation.patch_applied=true\")\n        traceEvent(\"bubble_region_shortcut_flow.patch_applied=true\")\n",
        )
    }

    if (text != original) file.writeText(text)
}

fun patchBubbleRegionShortcutMain(file: java.io.File) {
    var text = file.readText()
    val original = text

    if ("var requestedExpander by remember" !in text) {
        text = text.replace(
"""    var highlightedSavedPlaceId by remember { mutableStateOf<String?>(null) }
""",
"""    var highlightedSavedPlaceId by remember { mutableStateOf<String?>(null) }
    var requestedExpander by remember { mutableStateOf<String?>(null) }
    var regionShortcutActive by remember { mutableStateOf(false) }
""",
        )
    }

    text = text.replace(
"""    LaunchedEffect(launchIntent) {
        val requestedTab = launchIntent?.getStringExtra(EXTRA_OPEN_TAB)
        if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_TOOLS || requestedTab == TAB_HISTORY) {
            tab = requestedTab
        }
        highlightedSavedPlaceId = launchIntent?.getStringExtra(EXTRA_SAVED_PLACE_ID)
    }
""",
"""    LaunchedEffect(launchIntent) {
        val requestedTab = launchIntent?.getStringExtra(EXTRA_OPEN_TAB)
        requestedExpander = launchIntent?.getStringExtra("br.com.mapeiaia.rotacerta.extra.OPEN_EXPANDER")
        regionShortcutActive = launchIntent?.getBooleanExtra("br.com.mapeiaia.rotacerta.extra.OPEN_REGION_SHORTCUT", false) == true
        if (regionShortcutActive) {
            tab = TAB_ANALYSIS
            requestedExpander = "Minha regiao de corridas"
        } else if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_TOOLS || requestedTab == TAB_HISTORY) {
            tab = requestedTab
        }
        highlightedSavedPlaceId = launchIntent?.getStringExtra(EXTRA_SAVED_PLACE_ID)
    }
""",
    )

    text = text.replace(
"""                TAB_ANALYSIS -> AnalysisScreen(
                    settings = settings,
                    latestResult = history.firstOrNull(),
                    cardTemplates = cardTemplates,
                    templateStatus = templateStatus,
                    unreadTemplatePrints = unreadTemplatePrints,
                    liveEnabled = liveEnabled,
                    onSaveSettings = { scope.launch { repository.saveSettings(it) } },
                    onDeleteCardModel = ::deleteCardModel,
                    onPickCardModels = { cardModelPicker.launch("image/*") },
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRefreshLiveState = { liveEnabled = isLiveAccessibilityEnabled(context) },
                )
""",
"""                TAB_ANALYSIS -> AnalysisScreen(
                    settings = settings,
                    latestResult = history.firstOrNull(),
                    cardTemplates = cardTemplates,
                    templateStatus = templateStatus,
                    unreadTemplatePrints = unreadTemplatePrints,
                    liveEnabled = liveEnabled,
                    requestedExpander = requestedExpander,
                    regionShortcutActive = regionShortcutActive,
                    onRegionShortcutHandled = { regionShortcutActive = false },
                    onSaveSettings = { scope.launch { repository.saveSettings(it) } },
                    onDeleteCardModel = ::deleteCardModel,
                    onPickCardModels = { cardModelPicker.launch("image/*") },
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRefreshLiveState = { liveEnabled = isLiveAccessibilityEnabled(context) },
                )
""",
    )

    text = text.replace(
"""    liveEnabled: Boolean,
    onSaveSettings: (AppSettings) -> Unit,
""",
"""    liveEnabled: Boolean,
    requestedExpander: String? = null,
    regionShortcutActive: Boolean = false,
    onRegionShortcutHandled: () -> Unit = {},
    onSaveSettings: (AppSettings) -> Unit,
""",
    )

    text = text.replace(
"""    LiveReadingCard(
        liveEnabled = liveEnabled,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onRefreshLiveState = onRefreshLiveState,
    )

    Spacer(Modifier.height(10.dp))
""",
"""    val showRegionShortcut = regionShortcutActive || requestedExpander == "Minha regiao de corridas" || requestedExpander == "Minha região de corridas"
    if (showRegionShortcut) {
        RideRegionShortcutCard(
            quickSettings = quickSettings,
            homeStatus = homeStatus,
            onSettingsChange = { quickSettings = it },
            onRequestHomeGps = ::requestHomeGps,
            onSave = { saveQuickSettings(quickSettings) },
        )
        LaunchedEffect(regionShortcutActive) {
            if (regionShortcutActive) onRegionShortcutHandled()
        }
        Spacer(Modifier.height(10.dp))
    }

    LiveReadingCard(
        liveEnabled = liveEnabled,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onRefreshLiveState = onRefreshLiveState,
    )

    Spacer(Modifier.height(10.dp))
""",
    )

    if ("private fun RideRegionShortcutCard(" !in text) {
        text = text.replace(
"""@Composable
private fun LiveReadingCard(
""",
"""@Composable
private fun RideRegionShortcutCard(
    quickSettings: AppSettings,
    homeStatus: String,
    onSettingsChange: (AppSettings) -> Unit,
    onRequestHomeGps: () -> Unit,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Minha região de corridas", fontWeight = FontWeight.Bold)
            Text(
                "Defina pelo GPS atual, ajuste o raio em km e salve. A bolinha usa esse raio para aceitar ou recusar corridas.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onRequestHomeGps, modifier = Modifier.fillMaxWidth()) {
                Text("Definir pelo GPS atual")
            }
            quickSettings.homeCoordinate?.let {
                Text("GPS salvo: ${'$'}{formatCoordinate(it)}", style = MaterialTheme.typography.bodySmall)
            }
            RadiusSlider(
                label = "Km da minha região",
                value = quickSettings.homeRadiusKm,
                onValueChange = { onSettingsChange(quickSettings.copy(homeRadiusKm = it)) },
                onValueChangeFinished = onSave,
            )
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Salvar região de corridas")
            }
            if (homeStatus.isNotBlank()) {
                Text(homeStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LiveReadingCard(
""",
        )
    }

    text = text.replace(
"WorkRegionCard(settings = settings, onSaveSettings = onSaveSettings)",
"WorkRegionCard(settings = settings, onSaveSettings = onSaveSettings)",
    )

    if (text != original) file.writeText(text)
}
