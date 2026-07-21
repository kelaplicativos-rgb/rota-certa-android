// Rota Certa 0.1.120
// Corrige a navegacao dos atalhos e separa Alertas, Locais, Radares e Cards.
// Tambem corrige o segundo toque para fechar o popup e inicia a Home em Permissoes.

fun popup120ReplaceRegion(
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

fun enforcePopupNavigationSeparation120(
    mainFile: java.io.File,
    serviceFile: java.io.File,
    catalogFile: java.io.File,
    controllerFile: java.io.File,
) {
    listOf(mainFile, serviceFile, catalogFile, controllerFile).forEach { file ->
        if (!file.exists()) throw GradleException("Arquivo ausente para 0.1.120: ${file.name}")
    }

    catalogFile.writeText(
        """package br.com.mapeiaia.rotacerta

enum class BubbleShortcutAction {
    OpenRoute,
    OpenDestination,
    OpenAlerts,
    OpenSavedPlaces,
    OpenRadars,
    OpenAppearance,
    OpenPermissions,
    OpenBackup,
    OpenReports,
    OpenScreenWhatsApp,
    OpenCollector,
    ClearClipboard,
    ExportDiagnostic,
    StopApplication,
    CreateAlert,
    CreateSavedPlace,
    SaveRideCard,
    OpenCards,
    ToggleReading,
    OpenSettings,
}

data class BubbleShortcutSpec(
    val id: String,
    val emoji: String,
    val label: String,
    val action: BubbleShortcutAction,
    val displayLabel: String = label,
    val defaultName: String? = null,
    val targetGroup: String? = null,
    val targetTab: String? = null,
) {
    val displayText: String
        get() = "${'$'}emoji\n${'$'}displayLabel"
}

interface BubbleShortcutModule {
    val spec: BubbleShortcutSpec
}

object RouteBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "route",
        emoji = "⚡",
        label = "Rota",
        action = BubbleShortcutAction.OpenRoute,
        targetGroup = "general",
        targetTab = "config",
    )
}

object AlertsManagementBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "alerts",
        emoji = "⚠️",
        label = "Alertas",
        action = BubbleShortcutAction.OpenAlerts,
        targetGroup = "alerts",
        targetTab = "config",
    )
}

object SavedPlacesManagementBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "saved_places",
        emoji = "📍",
        label = "Locais",
        action = BubbleShortcutAction.OpenSavedPlaces,
        targetGroup = "saved_places",
        targetTab = "config",
    )
}

object RadarsManagementBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "radars",
        emoji = "📡",
        label = "Radares",
        action = BubbleShortcutAction.OpenRadars,
        targetGroup = "radars",
        targetTab = "config",
    )
}

object AppearanceBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "appearance",
        emoji = "🎨",
        label = "Aparencia",
        action = BubbleShortcutAction.OpenAppearance,
        displayLabel = "Aparência",
        targetGroup = "appearance",
        targetTab = "config",
    )
}

object PermissionsBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "permissions",
        emoji = "🔐",
        label = "Permissoes",
        action = BubbleShortcutAction.OpenPermissions,
        displayLabel = "Permissão",
        targetGroup = "access",
        targetTab = "config",
    )
}

object BackupBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "backup",
        emoji = "💾",
        label = "Backup",
        action = BubbleShortcutAction.OpenBackup,
        targetGroup = "backup",
        targetTab = "config",
    )
}

object CollectorBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "collector",
        emoji = "🚗",
        label = "Coletor",
        action = BubbleShortcutAction.OpenCollector,
    )
}

object ClearClipboardBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "clear_clipboard",
        emoji = "🧹",
        label = "Limpar area de transferencia",
        action = BubbleShortcutAction.ClearClipboard,
        displayLabel = "Limpar",
    )
}

object DiagnosticBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "diagnostic",
        emoji = "🛠️",
        label = "Depurar",
        action = BubbleShortcutAction.ExportDiagnostic,
    )
}

object CardsManagementBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "cards",
        emoji = "🪪",
        label = "Cards cadastrados",
        action = BubbleShortcutAction.OpenCards,
        displayLabel = "Cards",
        targetGroup = "cards",
        targetTab = "config",
    )
}

object BubbleShortcutCatalog {
    val modules: List<BubbleShortcutModule> = listOf(
        RouteBubbleShortcutModule,
        DestinationBubbleShortcutModule,
        AlertsManagementBubbleShortcutModule,
        SavedPlacesManagementBubbleShortcutModule,
        RadarsManagementBubbleShortcutModule,
        AppearanceBubbleShortcutModule,
        PermissionsBubbleShortcutModule,
        BackupBubbleShortcutModule,
        WhatsAppBubbleShortcutModule,
        CollectorBubbleShortcutModule,
        ClearClipboardBubbleShortcutModule,
        DiagnosticBubbleShortcutModule,
        StopBubbleShortcutModule,
        CardsManagementBubbleShortcutModule,
        ReadingBubbleShortcutModule,
    )

    fun requireValid() {
        require(modules.size == 15) { "O popup deve conter quinze modulos." }
        require(modules.map { it.spec.id }.distinct().size == modules.size) {
            "Cada atalho precisa ter identificador unico."
        }
        require(modules.map { it.spec.action }.distinct().size == modules.size) {
            "Cada recurso precisa executar uma acao propria."
        }
    }
}

// popup_navigation_catalog_0_1_120
""",
    )

    var service = serviceFile.readText()
    service = popup120ReplaceRegion(
        source = service,
        startToken = "    private fun executeShortcutModule(spec: BubbleShortcutSpec) {",
        endToken = "    private fun toggleLiveReadingFromBubble() {",
        replacement = """    private fun executeShortcutModule(spec: BubbleShortcutSpec) {
        traceEvent("bubble.shortcut.execute id=" + spec.id)
        DiagnosticLogStore.record("bubble_action", "shortcut id=" + spec.id + " label=" + spec.label)
        when (spec.action) {
            BubbleShortcutAction.OpenRoute,
            BubbleShortcutAction.OpenDestination,
            BubbleShortcutAction.OpenAlerts,
            BubbleShortcutAction.OpenSavedPlaces,
            BubbleShortcutAction.OpenRadars,
            BubbleShortcutAction.OpenAppearance,
            BubbleShortcutAction.OpenPermissions,
            BubbleShortcutAction.OpenBackup,
            BubbleShortcutAction.OpenReports,
            BubbleShortcutAction.OpenCards,
            BubbleShortcutAction.OpenSettings,
            -> openResourceGroup(requireNotNull(spec.targetGroup), requireNotNull(spec.targetTab))

            BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp118()
            BubbleShortcutAction.OpenCollector -> openCollectorFromBubble()
            BubbleShortcutAction.ClearClipboard -> clearClipboardFromBubble()
            BubbleShortcutAction.ExportDiagnostic -> exportDiagnosticFromBubble()
            BubbleShortcutAction.StopApplication -> stopApplicationFromBubble()
            BubbleShortcutAction.CreateAlert -> saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert, requireNotNull(spec.defaultName))
            BubbleShortcutAction.CreateSavedPlace -> saveCurrentPlaceFromBubble(SavedPlaceType.Place, requireNotNull(spec.defaultName))
            BubbleShortcutAction.SaveRideCard -> saveCurrentRideCardFromBubble()
            BubbleShortcutAction.ToggleReading -> toggleLiveReadingFromBubble()
        }
    }

""",
        label = "despacho dos atalhos 0.1.120",
    )

    service = popup120ReplaceRegion(
        source = service,
        startToken = "    private fun toggleLiveReadingFromBubble() {",
        endToken = "    private fun stopApplicationFromBubble() {",
        replacement = """    private fun toggleLiveReadingFromBubble() {
        shortcutOverlayController.hideShortcuts()
        persistResourceShortcutState()
        val enabled = !currentSettings.liveReadingEnabled
        val updated = currentSettings.copy(liveReadingEnabled = enabled)
        currentSettings = updated
        scope.launch { runCatching { repository.saveSettings(updated) } }
        if (enabled) {
            showOverlay(RadarColor.Default)
            scheduleVisibleTextAnalysis(delayMs = 0L)
            requestScreenshotAnalysis()
        } else {
            analyzeJob?.cancel()
            analyzeJob = null
            screenshotInProgress.set(false)
            lastAccessibilityText = ""
            lastOcrText = ""
            resetToIdle("Leitura pausada pelo atalho da bolinha.", record = false)
        }
        val message = if (enabled) "Leitura ao vivo ATIVADA" else "Leitura ao vivo PAUSADA"
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        overlayView?.announceForAccessibility(message)
        bubblePrefs.edit().putString("runtime_reading_status", if (enabled) "active" else "paused").apply()
        traceEvent("bubble.reading.toggle enabled=" + enabled + " feedback=long")
        DiagnosticLogStore.record("bubble_action", "reading enabled=" + enabled + " feedback=visible")
    } // reading_visible_feedback_0_1_120

""",
        label = "feedback da Leitura",
    )

    service = service.replace(
        "                    hideResourceShortcuts()\n                    bubbleGestureActive = true\n",
        "                    bubbleGestureActive = true\n",
    )
    val dragStart = """                    if (!moved && BubbleDragPolicy.hasExceededTouchSlop(deltaX, deltaY, touchSlop)) {
                        moved = true
                    }
"""
    val dragReplacement = """                    if (!moved && BubbleDragPolicy.hasExceededTouchSlop(deltaX, deltaY, touchSlop)) {
                        moved = true
                        hideResourceShortcuts() // popup_close_only_on_drag_0_1_120
                    }
"""
    if ("popup_close_only_on_drag_0_1_120" !in service) {
        if (dragStart !in service) throw GradleException("Limiar do arraste nao encontrado para corrigir o fechamento.")
        service = service.replaceFirst(dragStart, dragReplacement)
    }

    service = service.replace(
        ".putExtra(EXTRA_OPEN_BUBBLE_GROUP, BUBBLE_GROUP_ALERTS_VALUE),",
        ".putExtra(EXTRA_OPEN_BUBBLE_GROUP, if (place.type == SavedPlaceType.ProximityAlert) \"alerts\" else \"saved_places\"),",
    )
    if ("popup_navigation_service_0_1_120" !in service) {
        service += "\n// popup_navigation_service_0_1_120\n"
    }
    listOf(
        "BubbleShortcutAction.OpenSavedPlaces",
        "BubbleShortcutAction.OpenRadars",
        "BubbleShortcutAction.OpenCards",
        "popup_close_only_on_drag_0_1_120",
        "reading_visible_feedback_0_1_120",
        "if (place.type == SavedPlaceType.ProximityAlert) \"alerts\" else \"saved_places\"",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Servico 0.1.120 incompleto: $marker")
    }
    if ("hideResourceShortcuts()\n                    bubbleGestureActive = true" in service) {
        throw GradleException("O ACTION_DOWN ainda fecha o popup antes do clique.")
    }
    serviceFile.writeText(service)

    var main = mainFile.readText()
    if ("val gpsAddressResolver = remember { GpsAddressResolver(context) } // popup_modules_0_1_120" !in main) {
        val locationAnchor = "    val locationService = remember { DeviceLocationService(context) }\n"
        if (locationAnchor !in main) throw GradleException("LocationService da Home nao encontrado.")
        main = main.replaceFirst(
            locationAnchor,
            locationAnchor + "    val gpsAddressResolver = remember { GpsAddressResolver(context) } // popup_modules_0_1_120\n",
        )
    }

    main = main
        .replace("    var tab by remember { mutableStateOf(TAB_ANALYSIS) }\n", "    var tab by remember { mutableStateOf(TAB_CONFIG) }\n")
        .replace(
            "    var selectedBubbleGroup by remember { mutableStateOf(BUBBLE_GROUP_DESTINATION) } // grouped_bubble_state_0_1_115\n",
            "    var selectedBubbleGroup by remember { mutableStateOf(BUBBLE_GROUP_ACCESS) } // grouped_bubble_state_0_1_115\n",
        )

    if ("createSavedPlaceFromHome_0_1_120" !in main) {
        val insertAt = main.indexOf("    val locationPermissionLauncher")
        if (insertAt < 0) throw GradleException("Ponto de criacao de Local/Alerta nao encontrado.")
        val creator = """    fun createSavedPlaceFromHome(type: SavedPlaceType) {
        scope.launch {
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                Toast.makeText(context, "Autorize a localizacao para salvar este ponto.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val resolved = gpsAddressResolver.resolve(coordinate)
            val createdAt = System.currentTimeMillis()
            val isAlert = type == SavedPlaceType.ProximityAlert
            val place = SavedPlace(
                id = "place-${'$'}createdAt-${'$'}{coordinate.latitude}-${'$'}{coordinate.longitude}",
                name = if (isAlert) "Alerta" else "Local salvo",
                type = type,
                address = resolved.addressLine,
                coordinate = coordinate,
                alertDistanceMeters = if (isAlert) settings.proximityAlertDistanceMeters else null,
                createdAtMillis = createdAt,
            )
            repository.addSavedPlace(place)
            highlightedSavedPlaceId = place.id
            tab = TAB_CONFIG
            selectedBubbleGroup = if (isAlert) BUBBLE_GROUP_ALERTS else BUBBLE_GROUP_SAVED_PLACES
            Toast.makeText(
                context,
                if (isAlert) "Alerta criado. Defina o nome e a distancia." else "Local salvo. Defina um nome.",
                Toast.LENGTH_LONG,
            ).show()
        }
    } // createSavedPlaceFromHome_0_1_120

"""
        main = main.substring(0, insertAt) + creator + main.substring(insertAt)
    }

    val launchStart = main.indexOf("        val requestedTab = launchIntent?.getStringExtra(EXTRA_OPEN_TAB)")
    val highlightStart = if (launchStart >= 0) main.indexOf("        highlightedSavedPlaceId =", launchStart) else -1
    if (launchStart < 0 || highlightStart <= launchStart) {
        throw GradleException("Navegacao inicial nao encontrada para Permissoes.")
    }
    val launchReplacement = """        val requestedTab = launchIntent?.getStringExtra(EXTRA_OPEN_TAB)
        tab = if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_TOOLS || requestedTab == TAB_HISTORY) {
            requestedTab
        } else {
            TAB_CONFIG
        }
        selectedBubbleGroup = when (tab) {
            TAB_CONFIG -> BUBBLE_GROUP_ACCESS
            TAB_ANALYSIS -> BUBBLE_GROUP_DESTINATION
            else -> BUBBLE_GROUP_ACCESS
        }
        launchIntent?.getStringExtra(EXTRA_OPEN_BUBBLE_GROUP)?.let { requestedGroup ->
            if (requestedGroup in BUBBLE_GROUP_VALUES) selectedBubbleGroup = requestedGroup
        } // startup_permissions_0_1_120
"""
    main = main.substring(0, launchStart) + launchReplacement + main.substring(highlightStart)

    if ("private const val BUBBLE_GROUP_SAVED_PLACES" !in main) {
        val constantAnchor = "private const val BUBBLE_GROUP_ALERTS = \"alerts\"\n"
        if (constantAnchor !in main) throw GradleException("Constante Alertas nao encontrada.")
        main = main.replaceFirst(
            constantAnchor,
            constantAnchor + """private const val BUBBLE_GROUP_SAVED_PLACES = "saved_places"
private const val BUBBLE_GROUP_RADARS = "radars"
private const val BUBBLE_GROUP_CARDS = "cards"
""",
        )
    }
    val valuesStart = main.indexOf("private val BUBBLE_GROUP_VALUES = setOf(")
    val valuesEnd = if (valuesStart >= 0) main.indexOf(")\n", valuesStart) else -1
    if (valuesStart < 0 || valuesEnd <= valuesStart) throw GradleException("Conjunto de grupos nao encontrado.")
    var valuesBlock = main.substring(valuesStart, valuesEnd + 2)
    listOf(
        "    BUBBLE_GROUP_SAVED_PLACES,\n",
        "    BUBBLE_GROUP_RADARS,\n",
        "    BUBBLE_GROUP_CARDS,\n",
    ).forEach { item ->
        if (item !in valuesBlock) {
            valuesBlock = valuesBlock.replace("    BUBBLE_GROUP_ALERTS,\n", "    BUBBLE_GROUP_ALERTS,\n$item")
        }
    }
    main = main.substring(0, valuesStart) + valuesBlock + main.substring(valuesEnd + 2)

    main = main.replace(
        "                    cardTemplates = cardTemplates,\n                    savedPlaces = savedPlaces,",
        """                    cardTemplates = cardTemplates,
                    templateStatus = templateStatus,
                    unreadTemplatePrints = unreadTemplatePrints,
                    onPickCardModels = { cardModelPicker.launch("image/*") },
                    onDeleteCardModel = ::deleteCardModel,
                    savedPlaces = savedPlaces,""",
    )
    main = main.replace(
        "                    onRegisterRideCard = ::registerRideCard,\n                    onRenameSavedPlace = ::renameSavedPlace,",
        """                    onRegisterRideCard = ::registerRideCard,
                    onCreateSavedPlace = { createSavedPlaceFromHome(SavedPlaceType.Place) },
                    onCreateProximityAlert = { createSavedPlaceFromHome(SavedPlaceType.ProximityAlert) },
                    onRenameSavedPlace = ::renameSavedPlace,""",
    )

    main = main.replace(
        "    cardTemplates: List<RideCardTemplate>,\n    savedPlaces: List<SavedPlace>,",
        """    cardTemplates: List<RideCardTemplate>,
    templateStatus: String,
    unreadTemplatePrints: Int,
    onPickCardModels: () -> Unit,
    onDeleteCardModel: (RideCardTemplate) -> Unit,
    savedPlaces: List<SavedPlace>,""",
    )
    main = main.replace(
        "    onRegisterRideCard: (String?, String) -> Unit,\n    onRenameSavedPlace: (SavedPlace, String) -> Unit,",
        """    onRegisterRideCard: (String?, String) -> Unit,
    onCreateSavedPlace: () -> Unit,
    onCreateProximityAlert: () -> Unit,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,""",
    )

    val settingsWhenStart = main.indexOf("        when (selectedGroup) {")
    val settingsWhenEndToken = "        }\n    }\n} // grouped_settings_screen_0_1_115"
    val settingsWhenEnd = if (settingsWhenStart >= 0) main.indexOf(settingsWhenEndToken, settingsWhenStart) else -1
    if (settingsWhenStart < 0 || settingsWhenEnd <= settingsWhenStart) {
        throw GradleException("When dos modulos de configuracao nao encontrado.")
    }
    val separatedWhen = """        when (selectedGroup) {
            BUBBLE_GROUP_GENERAL -> SystemControlCard(settings = draft, onChange = ::saveDraft)
            BUBBLE_GROUP_READING,
            BUBBLE_GROUP_ACCESS,
            -> {
                LiveReadingCard(
                    liveEnabled = liveEnabled,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onRefreshLiveState = onRefreshLiveState,
                )
                Spacer(Modifier.height(10.dp))
                AlwaysLocationPermissionCard(
                    hasAlwaysPermission = hasAlwaysLocationPermission(context),
                    onOpenLocationSettings = { openAppLocationSettings(context) },
                )
            }
            BUBBLE_GROUP_ALERTS -> SavedPlacesModuleCard(
                savedPlaces = savedPlaces,
                type = SavedPlaceType.ProximityAlert,
                highlightedSavedPlaceId = highlightedSavedPlaceId,
                onCreate = onCreateProximityAlert,
                onRenameSavedPlace = onRenameSavedPlace,
                onDeleteSavedPlace = onDeleteSavedPlace,
            )
            BUBBLE_GROUP_SAVED_PLACES -> SavedPlacesModuleCard(
                savedPlaces = savedPlaces,
                type = SavedPlaceType.Place,
                highlightedSavedPlaceId = highlightedSavedPlaceId,
                onCreate = onCreateSavedPlace,
                onRenameSavedPlace = onRenameSavedPlace,
                onDeleteSavedPlace = onDeleteSavedPlace,
            )
            BUBBLE_GROUP_RADARS -> RadarImportCard(
                summary = radarImportSummary,
                importStatus = radarImportStatus,
                onPickFile = onImportRadarFile,
                onOpenMapaRadar = onOpenMapaRadar,
                onClearRadars = onClearImportedRadars,
            )
            BUBBLE_GROUP_APPEARANCE -> BubbleSettingsCard(settings = draft, onChange = ::saveDraft)
            BUBBLE_GROUP_BACKUP -> BackupCard(
                status = backupStatus,
                onCreateBackup = onCreateBackup,
                onRestoreBackup = onRestoreBackup,
            )
            BUBBLE_GROUP_CARDS -> CardModelsCard(
                cardTemplates = cardTemplates,
                templateStatus = templateStatus,
                unreadTemplatePrints = unreadTemplatePrints,
                onPickCardModels = onPickCardModels,
                onDeleteCardModel = onDeleteCardModel,
            )
            else -> SystemControlCard(settings = draft, onChange = ::saveDraft)
        }
"""
    main = main.substring(0, settingsWhenStart) + separatedWhen + main.substring(settingsWhenEnd + "        }\n".length)

    val cardBlock = """    Spacer(Modifier.height(10.dp))
    CardModelsCard(
        cardTemplates = cardTemplates,
        templateStatus = templateStatus,
        unreadTemplatePrints = unreadTemplatePrints,
        onPickCardModels = onPickCardModels,
        onDeleteCardModel = onDeleteCardModel,
    )

"""
    main = main.replace(cardBlock, "")

    if ("private fun SavedPlacesModuleCard(" !in main) {
        val editorAnchor = "@Composable\nprivate fun SavedPlaceEditor("
        val editorIndex = main.indexOf(editorAnchor)
        if (editorIndex < 0) throw GradleException("Editor de local nao encontrado.")
        val moduleCard = """@Composable
private fun SavedPlacesModuleCard(
    savedPlaces: List<SavedPlace>,
    type: SavedPlaceType,
    highlightedSavedPlaceId: String?,
    onCreate: () -> Unit,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
) {
    val items = savedPlaces.filter { it.type == type }
    val isAlert = type == SavedPlaceType.ProximityAlert
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (isAlert) "Alertas de proximidade (${ '$' }{items.size})" else "Locais salvos (${ '$' }{items.size})",
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (isAlert) {
                    "Somente pontos que geram aviso de aproximacao."
                } else {
                    "Somente locais salvos para consultar ou voltar depois. Nao geram alerta."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text(if (isAlert) "Criar alerta neste local" else "Salvar local atual")
            }
            if (items.isEmpty()) {
                Text(
                    if (isAlert) "Nenhum alerta criado." else "Nenhum local salvo.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                items.forEach { place ->
                    SavedPlaceEditor(
                        place = place,
                        highlighted = place.id == highlightedSavedPlaceId,
                        onRenameSavedPlace = onRenameSavedPlace,
                        onDeleteSavedPlace = onDeleteSavedPlace,
                    )
                }
            }
        }
    }
} // separate_saved_place_modules_0_1_120

"""
        main = main.substring(0, editorIndex) + moduleCard + main.substring(editorIndex)
    }

    main = main
        .replace("    BUBBLE_GROUP_ALERTS -> \"Alertas, locais e radares\"\n", """    BUBBLE_GROUP_ALERTS -> "Alertas de proximidade"
    BUBBLE_GROUP_SAVED_PLACES -> "Locais salvos"
    BUBBLE_GROUP_RADARS -> "Radares importados"
    BUBBLE_GROUP_CARDS -> "Cards cadastrados"
""")
        .replace(
            "    BUBBLE_GROUP_ALERTS -> \"Reune alertas de proximidade, locais salvos e radares importados.\"\n",
            """    BUBBLE_GROUP_ALERTS -> "Crie e edite somente alertas de proximidade."
    BUBBLE_GROUP_SAVED_PLACES -> "Gerencie somente locais salvos, sem alerta."
    BUBBLE_GROUP_RADARS -> "Importe e gerencie radares separadamente."
    BUBBLE_GROUP_CARDS -> "Cadastre, confira e remova modelos de cards."
""",
        )

    if ("popup_navigation_main_0_1_120" !in main) {
        main += "\n// popup_navigation_main_0_1_120\n"
    }
    listOf(
        "startup_permissions_0_1_120",
        "BUBBLE_GROUP_SAVED_PLACES",
        "BUBBLE_GROUP_RADARS",
        "BUBBLE_GROUP_CARDS",
        "separate_saved_place_modules_0_1_120",
        "createSavedPlaceFromHome_0_1_120",
        "CardModelsCard(",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("MainActivity 0.1.120 incompleta: $marker")
    }
    if ("CardModelsCard(\n        cardTemplates = cardTemplates" in main.substring(
            main.indexOf("@Composable\nprivate fun AnalysisScreen("),
            main.indexOf("@Composable\nprivate fun LiveReadingCard("),
        )
    ) {
        throw GradleException("Cards ainda aparecem misturados ao modulo Destino.")
    }
    mainFile.writeText(main)

    var controller = controllerFile.readText()
    if ("popup_close_after_tap_0_1_120" !in controller) {
        controller += "\n// popup_close_after_tap_0_1_120\n"
    }
    controllerFile.writeText(controller)

    listOf(
        "modules.size == 15",
        "SavedPlacesManagementBubbleShortcutModule",
        "RadarsManagementBubbleShortcutModule",
        "CardsManagementBubbleShortcutModule",
    ).forEach { marker ->
        if (marker !in catalogFile.readText()) throw GradleException("Catalogo 0.1.120 incompleto: $marker")
    }
    listOf("ReportsBubbleShortcutModule", "AlertBubbleShortcutModule", "SavedPlaceBubbleShortcutModule", "RideCardBubbleShortcutModule").forEach { forbidden ->
        val catalog = catalogFile.readText()
        val modulesStart = catalog.indexOf("val modules:")
        val modulesEnd = catalog.indexOf("\n    )", modulesStart)
        val modulesBlock = if (modulesStart >= 0 && modulesEnd > modulesStart) catalog.substring(modulesStart, modulesEnd) else catalog
        if (forbidden in modulesBlock) throw GradleException("Atalho duplicado/obsoleto ainda esta no popup: $forbidden")
    }
}

val popupNavigationSeparation120 by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val catalogFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt")
    val controllerFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt")
    inputs.files(mainFile, serviceFile, catalogFile, controllerFile)
    outputs.upToDateWhen { false }
    dependsOn("popupOnlyCompileCleanup119")
    doLast {
        enforcePopupNavigationSeparation120(
            mainFile.asFile,
            serviceFile.asFile,
            catalogFile.asFile,
            controllerFile.asFile,
        )
    }
}

popupNavigationSeparation120.configure {
    mustRunAfter("popupOnlyCompileCleanup119")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(popupNavigationSeparation120)
}
