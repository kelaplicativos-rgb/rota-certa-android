// Integra o catalogo de modulos Kotlin ao servico da bolinha.

fun shortcutRuntime117ReplaceRegion(
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

fun enforceBubbleResourceShortcutsRuntime117(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
    var text = file.readText()
    if ("bubble_resource_shortcuts_runtime_0_1_117" in text) return

    val propertyAnchor = "    private lateinit var proximityAlertEngine: ProximityAlertEngine\n"
    if (propertyAnchor !in text) throw GradleException("ProximityAlertEngine nao encontrado no servico.")
    text = text.replaceFirst(
        propertyAnchor,
        propertyAnchor + "    private lateinit var shortcutOverlayController: BubbleShortcutOverlayController\n",
    )

    val initializationAnchor = "        proximityAlertEngine = ProximityAlertEngine(speechEngine)\n"
    if (initializationAnchor !in text) throw GradleException("Inicializacao de proximidade nao encontrada.")
    text = text.replaceFirst(
        initializationAnchor,
        initializationAnchor + """        shortcutOverlayController = BubbleShortcutOverlayController(
            context = applicationContext,
            windowManager = requireNotNull(windowManager),
            trace = ::traceEvent,
        )
""",
    )

    val showOverlayStart = text.indexOf("    private fun showOverlay(")
    val removeOverlayStart = if (showOverlayStart >= 0) text.indexOf("\n    private fun removeOverlay()", showOverlayStart) else -1
    if (showOverlayStart < 0 || removeOverlayStart <= showOverlayStart) {
        throw GradleException("showOverlay nao encontrado para o clique de atalhos.")
    }
    var showOverlayBlock = text.substring(showOverlayStart, removeOverlayStart)
    val listenerRegex = Regex("newView\\.setOnClickListener\\s*\\{[^}]*}")
    if (!listenerRegex.containsMatchIn(showOverlayBlock)) {
        throw GradleException("Listener principal da bolinha nao encontrado.")
    }
    showOverlayBlock = showOverlayBlock.replaceFirst(
        listenerRegex,
        "newView.setOnClickListener { toggleResourceShortcuts() }",
    )
    text = text.substring(0, showOverlayStart) + showOverlayBlock + text.substring(removeOverlayStart)

    text = shortcutRuntime117ReplaceRegion(
        source = text,
        startToken = "    private suspend fun checkProximityAlerts(",
        endToken = "    private fun scheduleVisibleTextAnalysis(",
        replacement = """    private suspend fun checkProximityAlerts(alerts: List<SavedPlace>, radars: List<ImportedRadar>) {
        if (!currentSettings.appEnabled || !currentSettings.proximityAlertsEnabled) return
        val coordinate = locationService.currentCoordinate() ?: return
        proximityAlertEngine.check(
            alerts = alerts,
            radars = radars,
            coordinate = coordinate,
            settings = currentSettings,
            onSavedPlacePopup = { alert, distanceMeters ->
                showSavedAlertPopup(alert, distanceMeters)
            },
            onDiagnostic = { diagnostic ->
                recordDiagnostic(stage = diagnostic.stage, reason = diagnostic.reason)
            },
        )
    }

""",
        label = "callback visual do alerta",
    )

    text = text.replace(
        "    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType) {\n",
        "    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType, defaultName: String = if (type == SavedPlaceType.ProximityAlert) \"Alerta\" else \"Local salvo\") {\n",
    )
    text = text.replace(
        "                name = if (isAlert) \"Alerta de proximidade\" else \"Local salvo\",\n",
        "                name = defaultName,\n",
    )

    val methodsAnchor = "    private fun openSavedPlaceEditor("
    if (methodsAnchor !in text) throw GradleException("Editor de locais nao encontrado para inserir atalhos.")
    val shortcutMethods = """    private fun toggleResourceShortcuts() {
        val params = overlayParams ?: return
        shortcutOverlayController.toggleShortcuts(
            anchor = params,
            onShortcut = ::executeShortcutModule,
        )
        traceEvent("bubble.shortcuts.toggle")
    }

    private fun executeShortcutModule(spec: BubbleShortcutSpec) {
        traceEvent("bubble.shortcut.execute id=" + spec.id)
        when (spec.action) {
            BubbleShortcutAction.CreateAlert -> saveCurrentPlaceFromBubble(
                type = SavedPlaceType.ProximityAlert,
                defaultName = requireNotNull(spec.defaultName),
            )
            BubbleShortcutAction.CreateSavedPlace -> saveCurrentPlaceFromBubble(
                type = SavedPlaceType.Place,
                defaultName = requireNotNull(spec.defaultName),
            )
            BubbleShortcutAction.SaveRideCard -> saveCurrentRideCardFromBubble()
            BubbleShortcutAction.OpenDestination,
            BubbleShortcutAction.OpenReading,
            BubbleShortcutAction.OpenSettings,
            -> openResourceGroup(
                group = requireNotNull(spec.targetGroup),
                tab = requireNotNull(spec.targetTab),
            )
        }
    }

    private fun openResourceGroup(group: String, tab: String) {
        shortcutOverlayController.hideAll()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, tab)
                    .putExtra(EXTRA_OPEN_BUBBLE_GROUP, group),
            )
        }
    }

    private fun showSavedAlertPopup(alert: SavedPlace, distanceMeters: Double) {
        shortcutOverlayController.showProximityAlert(
            alert = alert,
            distanceMeters = distanceMeters,
            actions = ProximityAlertPopupActions(
                onEdit = ::openSavedPlaceEditor,
                onDelete = { place ->
                    scope.launch {
                        repository.removeSavedPlace(place.id)
                        toast("Alerta excluido.")
                        traceEvent("proximity.popup.deleted id=" + place.id)
                    }
                },
            ),
        )
    }

"""
    text = text.replaceFirst(methodsAnchor, shortcutMethods + methodsAnchor)

    text = shortcutRuntime117ReplaceRegion(
        source = text,
        startToken = "    private fun openSavedPlaceEditor(",
        endToken = "    private fun toggleActionMenu() {",
        replacement = """    private fun openSavedPlaceEditor(place: SavedPlace) {
        shortcutOverlayController.hideAll()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
                    .putExtra(EXTRA_SAVED_PLACE_ID, place.id)
                    .putExtra(EXTRA_OPEN_BUBBLE_GROUP, BUBBLE_GROUP_ALERTS_VALUE),
            )
        }
    }

""",
        label = "editor direto de local e alerta",
    )

    val removeStart = text.indexOf("    private fun removeOverlay() {")
    val paramsStart = if (removeStart >= 0) text.indexOf("    private fun overlayLayoutParams()", removeStart) else -1
    if (removeStart < 0 || paramsStart <= removeStart) throw GradleException("removeOverlay nao encontrado.")
    var removeBlock = text.substring(removeStart, paramsStart)
    if ("shortcutOverlayController.hideAll()" !in removeBlock) {
        removeBlock = removeBlock.replaceFirst(
            "        hideActionMenu()\n",
            "        hideActionMenu()\n        shortcutOverlayController.hideAll()\n",
        )
    }
    text = text.substring(0, removeStart) + removeBlock + text.substring(paramsStart)

    val actionDownAnchor = "                    bubbleGestureActive = true\n"
    if (actionDownAnchor !in text) throw GradleException("Inicio do gesto instantaneo nao encontrado.")
    text = text.replaceFirst(
        actionDownAnchor,
        "                    shortcutOverlayController.hideShortcuts()\n" + actionDownAnchor,
    )

    val companionAnchor = "        const val BUBBLE_PREFS = \"rota_certa_bubble\"\n"
    if (companionAnchor !in text) throw GradleException("Companion da bolinha nao encontrado.")
    text = text.replaceFirst(
        companionAnchor,
        companionAnchor + """        const val EXTRA_OPEN_BUBBLE_GROUP = "open_bubble_group"
        const val BUBBLE_GROUP_GENERAL_VALUE = "general"
        const val BUBBLE_GROUP_READING_VALUE = "reading"
        const val BUBBLE_GROUP_DESTINATION_VALUE = "destination"
        const val BUBBLE_GROUP_ALERTS_VALUE = "alerts"
""",
    )

    text += "\n// bubble_resource_shortcuts_runtime_0_1_117\n"

    listOf(
        "newView.setOnClickListener { toggleResourceShortcuts() }",
        "onShortcut = ::executeShortcutModule",
        "BubbleShortcutAction.CreateAlert",
        "BubbleShortcutAction.CreateSavedPlace",
        "BubbleShortcutAction.SaveRideCard",
        "defaultName = requireNotNull(spec.defaultName)",
        "showSavedAlertPopup(alert, distanceMeters)",
        "shortcutOverlayController.hideShortcuts()",
        "bubble_resource_shortcuts_runtime_0_1_117",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Runtime modular de atalhos incompleto: $marker")
    }

    file.writeText(text)
}

val bubbleResourceShortcutsRuntime117 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn("mainBubbleTapMenuContract", "bubbleInstantDrag116")
    doLast { enforceBubbleResourceShortcutsRuntime117(serviceFile.asFile) }
}

bubbleResourceShortcutsRuntime117.configure {
    mustRunAfter("mainBubbleTapMenuContract", "bubbleInstantDrag116")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("test") }.configureEach {
    dependsOn(bubbleResourceShortcutsRuntime117)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(bubbleResourceShortcutsRuntime117)
    doFirst {
        enforceBubbleResourceShortcutsRuntime117(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
