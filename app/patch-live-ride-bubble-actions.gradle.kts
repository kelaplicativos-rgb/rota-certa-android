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
                    .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
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
            tab = TAB_CONFIG
            openDecisionAddressSettings = true
        }
        highlightedSavedPlaceId = launchIntent?.getStringExtra(EXTRA_SAVED_PLACE_ID)
""",
            )

            mainText = mainText.replace(
"""                    radarImportStatus = radarImportStatus,
                    onSave = { scope.launch { repository.saveSettings(it) } },
""",
"""                    radarImportStatus = radarImportStatus,
                    openDecisionAddressSettings = openDecisionAddressSettings,
                    onSave = { scope.launch { repository.saveSettings(it) } },
""",
            )

            mainText = mainText.replace(
"""    radarImportSummary: RadarImportSummary,
    radarImportStatus: String,
    onSave: (AppSettings) -> Unit,
""",
"""    radarImportSummary: RadarImportSummary,
    radarImportStatus: String,
    openDecisionAddressSettings: Boolean,
    onSave: (AppSettings) -> Unit,
""",
            )

            mainText = mainText.replace(
"""        SettingsLocationCard(
            draft = draft,
            gpsStatus = gpsStatus,
            onDraftChange = { draft = it },
""",
"""        SettingsLocationCard(
            draft = draft,
            gpsStatus = gpsStatus,
            initiallyExpanded = openDecisionAddressSettings,
            onDraftChange = { draft = it },
""",
            )

            mainText = mainText.replace(
"""    draft: AppSettings,
    gpsStatus: String,
    onDraftChange: (AppSettings) -> Unit,
""",
"""    draft: AppSettings,
    gpsStatus: String,
    initiallyExpanded: Boolean,
    onDraftChange: (AppSettings) -> Unit,
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
