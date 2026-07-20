// Integra o catalogo de modulos Kotlin ao servico da bolinha.

fun shortcutRuntime117ReplaceRegion(source: String, startToken: String, endToken: String, replacement: String, label: String): String {
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
    text = text.replaceFirst(propertyAnchor, propertyAnchor + "    private lateinit var shortcutOverlayController: BubbleShortcutOverlayController\n")

    val initializationAnchor = "        proximityAlertEngine = ProximityAlertEngine(speechEngine)\n"
    if (initializationAnchor !in text) throw GradleException("Inicializacao de proximidade nao encontrada.")
    text = text.replaceFirst(initializationAnchor, initializationAnchor + """        shortcutOverlayController = BubbleShortcutOverlayController(
            context = applicationContext,
            windowManager = requireNotNull(windowManager),
            trace = ::traceEvent,
        )
""")

    val showOverlayStart = text.indexOf("    private fun showOverlay(")
    val removeOverlayStart = if (showOverlayStart >= 0) text.indexOf("\n    private fun removeOverlay()", showOverlayStart) else -1
    if (showOverlayStart < 0 || removeOverlayStart <= showOverlayStart) throw GradleException("showOverlay nao encontrado.")
    var showOverlayBlock = text.substring(showOverlayStart, removeOverlayStart)
    val listenerRegex = Regex("newView\\.setOnClickListener\\s*\\{[^}]*}")
    if (!listenerRegex.containsMatchIn(showOverlayBlock)) throw GradleException("Listener principal nao encontrado.")
    showOverlayBlock = showOverlayBlock.replaceFirst(listenerRegex, "newView.setOnClickListener { toggleResourceShortcuts() }")
    text = text.substring(0, showOverlayStart) + showOverlayBlock + text.substring(removeOverlayStart)

    text = shortcutRuntime117ReplaceRegion(
        text,
        "    private suspend fun checkProximityAlerts(",
        "    private fun scheduleVisibleTextAnalysis(",
        """    private suspend fun checkProximityAlerts(alerts: List<SavedPlace>, radars: List<ImportedRadar>) {
        if (!currentSettings.appEnabled || !currentSettings.proximityAlertsEnabled) return
        val coordinate = locationService.currentCoordinate() ?: return
        proximityAlertEngine.check(
            alerts = alerts,
            radars = radars,
            coordinate = coordinate,
            settings = currentSettings,
            onSavedPlacePopup = { alert, distanceMeters -> showSavedAlertPopup(alert, distanceMeters) },
            onDiagnostic = { diagnostic -> recordDiagnostic(stage = diagnostic.stage, reason = diagnostic.reason) },
        )
    }

""",
        "callback visual do alerta",
    )

    text = text.replace(
        "    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType) {\n",
        "    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType, defaultName: String = if (type == SavedPlaceType.ProximityAlert) \"Alerta\" else \"Local salvo\") {\n",
    )
    text = text.replace("                name = if (isAlert) \"Alerta de proximidade\" else \"Local salvo\",\n", "                name = defaultName,\n")

    val methodsAnchor = "    private fun openSavedPlaceEditor("
    if (methodsAnchor !in text) throw GradleException("Editor de locais nao encontrado.")
    val shortcutMethods = """    private fun persistResourceShortcutState() {
        val visible = shortcutOverlayController.shortcutsVisible
        val labels = BubbleShortcutCatalog.modules.joinToString("|") { it.spec.label }
        bubblePrefs.edit()
            .putBoolean(KEY_RUNTIME_SHORTCUTS_OPEN, visible)
            .putInt(KEY_RUNTIME_SHORTCUT_COUNT, if (visible) BubbleShortcutCatalog.modules.size else 0)
            .putString(KEY_RUNTIME_SHORTCUT_LABELS, if (visible) labels else "")
            .apply()
    }

    private fun hideResourceShortcuts() {
        shortcutOverlayController.hideShortcuts()
        persistResourceShortcutState()
    }

    private fun toggleResourceShortcuts() {
        val params = overlayParams ?: return
        shortcutOverlayController.toggleShortcuts(anchor = params, onShortcut = ::executeShortcutModule)
        persistResourceShortcutState()
        traceEvent("bubble.shortcuts.toggle visible=" + shortcutOverlayController.shortcutsVisible)
    }

    private fun executeShortcutModule(spec: BubbleShortcutSpec) {
        traceEvent("bubble.shortcut.execute id=" + spec.id)
        when (spec.action) {
            BubbleShortcutAction.CreateAlert -> saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert, requireNotNull(spec.defaultName))
            BubbleShortcutAction.CreateSavedPlace -> saveCurrentPlaceFromBubble(SavedPlaceType.Place, requireNotNull(spec.defaultName))
            BubbleShortcutAction.SaveRideCard -> saveCurrentRideCardFromBubble()
            BubbleShortcutAction.OpenDestination,
            BubbleShortcutAction.OpenReading,
            BubbleShortcutAction.OpenSettings,
            -> openResourceGroup(requireNotNull(spec.targetGroup), requireNotNull(spec.targetTab))
        }
    }

    private fun openResourceGroup(group: String, tab: String) {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
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
            alert,
            distanceMeters,
            ProximityAlertPopupActions(
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
        persistResourceShortcutState()
    }

"""
    text = text.replaceFirst(methodsAnchor, shortcutMethods + methodsAnchor)

    text = shortcutRuntime117ReplaceRegion(
        text,
        "    private fun openSavedPlaceEditor(",
        "    private fun toggleActionMenu() {",
        """    private fun openSavedPlaceEditor(place: SavedPlace) {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
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
        "editor direto de local e alerta",
    )

    val removeStart = text.indexOf("    private fun removeOverlay() {")
    val paramsStart = if (removeStart >= 0) text.indexOf("    private fun overlayLayoutParams()", removeStart) else -1
    if (removeStart < 0 || paramsStart <= removeStart) throw GradleException("removeOverlay nao encontrado.")
    var removeBlock = text.substring(removeStart, paramsStart)
    if ("shortcutOverlayController.hideAll()" !in removeBlock) {
        removeBlock = removeBlock.replaceFirst("        hideActionMenu()\n", "        hideActionMenu()\n        shortcutOverlayController.hideAll()\n        persistResourceShortcutState()\n")
    }
    text = text.substring(0, removeStart) + removeBlock + text.substring(paramsStart)

    val actionDownAnchor = "                    bubbleGestureActive = true\n"
    if (actionDownAnchor !in text) throw GradleException("Inicio do gesto nao encontrado.")
    text = text.replaceFirst(actionDownAnchor, "                    hideResourceShortcuts()\n" + actionDownAnchor)

    val companionAnchor = "        const val BUBBLE_PREFS = \"rota_certa_bubble\"\n"
    if (companionAnchor !in text) throw GradleException("Companion nao encontrado.")
    text = text.replaceFirst(companionAnchor, companionAnchor + """        const val KEY_RUNTIME_SHORTCUTS_OPEN = "runtime_shortcuts_open"
        const val KEY_RUNTIME_SHORTCUT_COUNT = "runtime_shortcut_count"
        const val KEY_RUNTIME_SHORTCUT_LABELS = "runtime_shortcut_labels"
        const val EXTRA_OPEN_BUBBLE_GROUP = "open_bubble_group"
        const val BUBBLE_GROUP_GENERAL_VALUE = "general"
        const val BUBBLE_GROUP_READING_VALUE = "reading"
        const val BUBBLE_GROUP_DESTINATION_VALUE = "destination"
        const val BUBBLE_GROUP_ALERTS_VALUE = "alerts"
""")

    text += "\n// bubble_resource_shortcuts_runtime_0_1_117\n"
    listOf(
        "newView.setOnClickListener { toggleResourceShortcuts() }",
        "onShortcut = ::executeShortcutModule",
        "BubbleShortcutCatalog.modules.joinToString",
        "BubbleShortcutAction.CreateAlert",
        "BubbleShortcutAction.CreateSavedPlace",
        "BubbleShortcutAction.SaveRideCard",
        "showSavedAlertPopup(alert, distanceMeters)",
        "hideResourceShortcuts()",
        "KEY_RUNTIME_SHORTCUTS_OPEN",
    ).forEach { if (it !in text) throw GradleException("Runtime modular incompleto: $it") }
    file.writeText(text)
}

val bubbleResourceShortcutsRuntime117 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn("mainBubbleTapMenuContract", "bubbleInstantDrag116")
    doLast { enforceBubbleResourceShortcutsRuntime117(serviceFile.asFile) }
}

bubbleResourceShortcutsRuntime117.configure { mustRunAfter("mainBubbleTapMenuContract", "bubbleInstantDrag116") }

tasks.matching { it.name == "preBuild" || it.name.startsWith("test") }.configureEach { dependsOn(bubbleResourceShortcutsRuntime117) }
tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(bubbleResourceShortcutsRuntime117)
    doFirst { enforceBubbleResourceShortcutsRuntime117(layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile) }
}
