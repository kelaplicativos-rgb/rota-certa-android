val patchLiveRideBubbleActions by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val service = serviceFile.asFile
        var text = service.readText()
        val original = text

        if ("openDecisionAddressSettingsFromBubble" !in text) {
            text = text.replace(
"""            addView(actionMenuItem("📍  Salvar este local") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            })
            addView(actionMenuItem("🔔  Criar alerta de proximidade") {
""",
"""            addView(actionMenuItem("📍  Salvar este local") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            })
            addView(actionMenuItem("🎯  Definir região de destino") {
                hideActionMenu()
                openDecisionAddressSettingsFromBubble()
            })
            addView(actionMenuItem("🔔  Criar alerta de proximidade") {
""",
            )

            text = text.replace(
"""    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType) {
""",
"""    private fun openDecisionAddressSettingsFromBubble() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_ANALYSIS)
                    .putExtra(EXTRA_OPEN_CONFIG_SECTION, CONFIG_SECTION_DECISION_ADDRESS),
            )
        }.onFailure {
            toast("Nao consegui abrir Salvar Casa/Alfinete agora.")
        }
    }

    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType) {
""",
            )
        }

        text = text.replace(
"""            x = bubbleParams.x + dp(76)
            y = bubbleParams.y
""",
"""            x = overlayMenuX(bubbleParams)
            y = overlayMenuY(bubbleParams)
""",
        )

        text = text.replace(
"""        params.x = bubbleParams.x + dp(76)
        params.y = bubbleParams.y
""",
"""        params.x = overlayMenuX(bubbleParams)
        params.y = overlayMenuY(bubbleParams)
""",
        )

        if ("private fun overlayMenuX(" !in text) {
            text = text.replace(
"""    private fun actionMenuItem(label: String, action: () -> Unit): TextView =
""",
"""    private fun overlayMenuX(bubbleParams: WindowManager.LayoutParams): Int {
        val menuWidth = dp(260)
        val gap = dp(10)
        val rightX = bubbleParams.x + dp(66) + gap
        val screenWidth = resources.displayMetrics.widthPixels
        return if (rightX + menuWidth <= screenWidth) {
            rightX
        } else {
            (bubbleParams.x - menuWidth - gap).coerceAtLeast(0)
        }
    }

    private fun overlayMenuY(bubbleParams: WindowManager.LayoutParams): Int =
        bubbleParams.y.coerceAtLeast(0)

    private fun actionMenuItem(label: String, action: () -> Unit): TextView =
""",
            )
        }

        if (text != original) {
            service.writeText(text)
        }

        val main = mainFile.asFile
        var mainText = main.readText()
        val originalMain = mainText

        if ("openDecisionAddressSettings" !in mainText) {
            mainText = mainText.replace(
                "    var highlightedSavedPlaceId by remember { mutableStateOf<String?>(null) }\n",
                "    var highlightedSavedPlaceId by remember { mutableStateOf<String?>(null) }\n    var openDecisionAddressSettings by remember { mutableStateOf(false) }\n",
            )

            mainText = mainText.replace(
"""        val requestedTab = launchIntent?.getStringExtra(EXTRA_OPEN_TAB)
        if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_TOOLS || requestedTab == TAB_HISTORY) {
            tab = requestedTab
        }
        highlightedSavedPlaceId = launchIntent?.getStringExtra(EXTRA_SAVED_PLACE_ID)
""",
"""        val requestedTab = launchIntent?.getStringExtra(EXTRA_OPEN_TAB)
        if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_TOOLS || requestedTab == TAB_HISTORY) {
            tab = requestedTab
        }
        val requestedConfigSection = launchIntent?.getStringExtra(EXTRA_OPEN_CONFIG_SECTION)
        if (requestedConfigSection == CONFIG_SECTION_DECISION_ADDRESS) {
            tab = TAB_ANALYSIS
            openDecisionAddressSettings = true
        }
        highlightedSavedPlaceId = launchIntent?.getStringExtra(EXTRA_SAVED_PLACE_ID)
""",
            )

            mainText = mainText.replace(
"""                    liveEnabled = liveEnabled,
                    onSaveSettings = { scope.launch { repository.saveSettings(it) } },
""",
"""                    liveEnabled = liveEnabled,
                    openDecisionAddressSettings = openDecisionAddressSettings,
                    onSaveSettings = { scope.launch { repository.saveSettings(it) } },
""",
            )

            mainText = mainText.replace(
"""                    onOpenBlaBlaCarCollector = {
                        context.startActivity(Intent(context, BlaBlaCarCollectorActivity::class.java))
                    },
                    onClearClipboard = { clearClipboard(context) },
                )
""",
"""                    onOpenBlaBlaCarCollector = {
                        context.startActivity(Intent(context, BlaBlaCarCollectorActivity::class.java))
                    },
                    onClearClipboard = { clearClipboard(context) },
                    savedPlaces = savedPlaces,
                    highlightedSavedPlaceId = highlightedSavedPlaceId,
                    onRenameSavedPlace = ::renameSavedPlace,
                    onDeleteSavedPlace = { place -> scope.launch { repository.removeSavedPlace(place.id) } },
                )
""",
            )

            mainText = mainText.replace(
"""    unreadTemplatePrints: Int,
    liveEnabled: Boolean,
    onSaveSettings: (AppSettings) -> Unit,
""",
"""    unreadTemplatePrints: Int,
    liveEnabled: Boolean,
    openDecisionAddressSettings: Boolean,
    onSaveSettings: (AppSettings) -> Unit,
""",
            )

            mainText = mainText.replace(
                "    var homeStatus by remember { mutableStateOf(\"\") }\n    var pendingHomeGps by remember { mutableStateOf(false) }\n",
                "    var homeStatus by remember { mutableStateOf(\"\") }\n    var pendingHomeGps by remember { mutableStateOf(false) }\n    var regionGpsStatus by remember { mutableStateOf(\"\") }\n    var pendingLocationTarget by remember { mutableStateOf<LocationTarget?>(null) }\n",
            )

            mainText = mainText.replace(
"""    fun captureHomeGps() {
""",
"""    fun captureRegionGps(target: LocationTarget) {
        scope.launch {
            regionGpsStatus = "Buscando sinal de GPS..."
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                regionGpsStatus = "Nao consegui captar o GPS. Autorize a localizacao e tente novamente."
                return@launch
            }
            val resolved = gpsAddressResolver.resolve(coordinate)
            val address = resolved.addressLine.ifBlank { formatCoordinate(coordinate) }
            quickSettings = when (target) {
                LocationTarget.Home -> quickSettings.copy(homeAddress = address, homeCoordinate = coordinate)
                LocationTarget.Alternative -> quickSettings.copy(alternativeAddress = address, alternativeCoordinate = coordinate)
            }
            regionGpsStatus = "GPS preenchido. Confira o raio em km e toque em Salvar."
        }
    }

    val regionGpsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val target = pendingLocationTarget
        pendingLocationTarget = null
        if (target != null) captureRegionGps(target)
    }

    fun requestRegionGps(target: LocationTarget) {
        pendingLocationTarget = target
        regionGpsPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    fun captureHomeGps() {
""",
            )

            mainText = mainText.replace(
"""    Spacer(Modifier.height(10.dp))
    HomeDecisionCard(
        quickSettings = quickSettings,
        homeStatus = homeStatus,
        onSettingsChange = { quickSettings = it },
        onRequestHomeGps = ::requestHomeGps,
        onSave = { saveQuickSettings(quickSettings) },
    )

    Spacer(Modifier.height(10.dp))
    RadiusQuickCard(
        quickSettings = quickSettings,
        onSettingsChange = { quickSettings = it },
        onSaveSettings = onSaveSettings,
    )
""",
"""    Spacer(Modifier.height(10.dp))
    SettingsLocationCard(
        draft = quickSettings,
        gpsStatus = regionGpsStatus,
        initiallyExpanded = openDecisionAddressSettings,
        onDraftChange = { quickSettings = it },
        onRequestGps = ::requestRegionGps,
        onSave = { saveQuickSettings(quickSettings) },
    )
""",
            )

            mainText = mainText.replace(
"""        SavedPlacesCard(
            savedPlaces = savedPlaces,
            highlightedSavedPlaceId = highlightedSavedPlaceId,
            onRenameSavedPlace = onRenameSavedPlace,
            onDeleteSavedPlace = onDeleteSavedPlace,
        )
        RadarImportCard(
""",
"""        RadarImportCard(
""",
            )

            mainText = mainText.replace(
"""        SettingsLocationCard(
            draft = draft,
            gpsStatus = gpsStatus,
            onDraftChange = { draft = it },
            onRequestGps = ::requestGps,
            onSave = { onSave(draft) },
        )
        MapsAndAdvancedCard(
""",
"""        MapsAndAdvancedCard(
""",
            )

            mainText = mainText.replace(
"""private fun ToolsScreen(
    onOpenBlaBlaCarCollector: () -> Unit,
    onClearClipboard: () -> Unit,
) {
""",
"""private fun ToolsScreen(
    onOpenBlaBlaCarCollector: () -> Unit,
    onClearClipboard: () -> Unit,
    savedPlaces: List<SavedPlace>,
    highlightedSavedPlaceId: String?,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
) {
""",
            )

            mainText = mainText.replace(
"""        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Area de transferencia", fontWeight = FontWeight.Bold)
                Text(
                    "Limpeza manual para remover o texto copiado quando o copiar/colar do celular travar ou ficar preso em conteudo antigo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onClearClipboard, modifier = Modifier.fillMaxWidth()) {
                    Text("Limpar area de transferencia")
                }
            }
        }
    }
}
""",
"""        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Area de transferencia", fontWeight = FontWeight.Bold)
                Text(
                    "Limpeza manual para remover o texto copiado quando o copiar/colar do celular travar ou ficar preso em conteudo antigo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onClearClipboard, modifier = Modifier.fillMaxWidth()) {
                    Text("Limpar area de transferencia")
                }
            }
        }
        SavedPlacesCard(
            savedPlaces = savedPlaces,
            highlightedSavedPlaceId = highlightedSavedPlaceId,
            onRenameSavedPlace = onRenameSavedPlace,
            onDeleteSavedPlace = onDeleteSavedPlace,
        )
    }
}
""",
            )

            mainText = mainText.replace(
"""    ExpandableCard(title = "Enderecos e raios", initiallyExpanded = false) {
""",
"""    ExpandableCard(title = "Salvar Casa/Alfinete", initiallyExpanded = initiallyExpanded) {
""",
            )
        }

        if (mainText != originalMain) {
            main.writeText(mainText)
        }
    }
}

patchLiveRideBubbleActions.configure {
    mustRunAfter("patchLiveRideOverlayStability")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchLiveRideBubbleActions)
}
