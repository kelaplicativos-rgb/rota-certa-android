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

    if ("bubble_resource_shortcuts_runtime_0_1_117" in text) {
        listOf(
            "BubbleShortcutAction.ToggleReading",
            "BubbleShortcutAction.OpenScreenWhatsApp",
            "BubbleShortcutAction.StopApplication",
            "private fun toggleLiveReadingFromBubble()",
            "private fun stopApplicationFromBubble()",
            "bubble.reading.toggle",
            "bubble.stop.requested",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Runtime modular atualizado incompleto: $marker")
        }
        return
    }

    if ("import android.net.Uri" !in text) {
        text = text.replace("import android.content.Intent\n", "import android.content.Intent\nimport android.net.Uri\n")
    }
    if ("import android.provider.Settings" !in text) {
        text = text.replace("import android.os.Build\n", "import android.os.Build\nimport android.provider.Settings\n")
    }

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

    // A bolinha WA antiga deixa de ficar permanentemente sobre a tela. O mesmo
    // capturador continua disponivel no modulo WhatsApp da grade principal.
    text = text
        .replace("            showWhatsAppShortcut() // screen_phone_whatsapp_0_1_84\n", "")
        .replace("                    updateWhatsAppShortcutPosition()\n", "")

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

    // Pausar Leitura precisa interromper acessibilidade/OCR imediatamente, sem
    // desligar os alertas de proximidade. O segundo toque reativa o caminho.
    val eventAnchor = "        if (!serviceReady || event == null) return\n"
    if (eventAnchor !in text) throw GradleException("Entrada de eventos de acessibilidade ausente.")
    text = text.replaceFirst(
        eventAnchor,
        eventAnchor + "        if (!currentSettings.liveReadingEnabled) return // bubble_reading_gate_0_1_118\n",
    )

    val scanStart = text.indexOf("    private fun startContinuousScan() {")
    val scanWhile = if (scanStart >= 0) text.indexOf("            while (serviceReady) {\n", scanStart) else -1
    if (scanWhile < 0) throw GradleException("Loop continuo ausente para pausa da leitura.")
    val scanInsert = scanWhile + "            while (serviceReady) {\n".length
    text = text.substring(0, scanInsert) + """                if (!currentSettings.liveReadingEnabled) {
                    delay(SCAN_LOOP_MS)
                    continue
                } // bubble_reading_scan_gate_0_1_118
""" + text.substring(scanInsert)

    val scheduleAnchor = "    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {\n"
    if (scheduleAnchor !in text) throw GradleException("Agendamento de leitura ausente.")
    text = text.replaceFirst(
        scheduleAnchor,
        scheduleAnchor + "        if (!currentSettings.liveReadingEnabled) return // bubble_reading_schedule_gate_0_1_118\n",
    )
    val screenshotAnchor = "    private fun requestScreenshotAnalysis(allowPopupCandidate: Boolean = false) {\n"
    if (screenshotAnchor !in text) throw GradleException("Solicitacao OCR ausente.")
    text = text.replaceFirst(
        screenshotAnchor,
        screenshotAnchor + "        if (!currentSettings.liveReadingEnabled) return // bubble_reading_ocr_gate_0_1_118\n",
    )

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
        DiagnosticLogStore.record("bubble_action", "shortcuts visible=" + shortcutOverlayController.shortcutsVisible)
    }

    private fun executeShortcutModule(spec: BubbleShortcutSpec) {
        traceEvent("bubble.shortcut.execute id=" + spec.id)
        DiagnosticLogStore.record("bubble_action", "shortcut id=" + spec.id + " label=" + spec.label)
        when (spec.action) {
            BubbleShortcutAction.CreateAlert -> saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert, requireNotNull(spec.defaultName))
            BubbleShortcutAction.CreateSavedPlace -> saveCurrentPlaceFromBubble(SavedPlaceType.Place, requireNotNull(spec.defaultName))
            BubbleShortcutAction.SaveRideCard -> saveCurrentRideCardFromBubble()
            BubbleShortcutAction.OpenDestination -> openResourceGroup(requireNotNull(spec.targetGroup), requireNotNull(spec.targetTab))
            BubbleShortcutAction.ToggleReading -> toggleLiveReadingFromBubble()
            BubbleShortcutAction.OpenScreenWhatsApp -> capturePhoneAndOpenWhatsApp()
            BubbleShortcutAction.OpenSettings -> openResourceGroup(requireNotNull(spec.targetGroup), requireNotNull(spec.targetTab))
            BubbleShortcutAction.StopApplication -> stopApplicationFromBubble()
        }
    }

    private fun toggleLiveReadingFromBubble() {
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
        val message = if (enabled) "Leitura ativa" else "Leitura pausada"
        toast(message)
        traceEvent("bubble.reading.toggle enabled=" + enabled)
        DiagnosticLogStore.record("bubble_action", "reading enabled=" + enabled)
    }

    private fun stopApplicationFromBubble() {
        val updated = currentSettings.copy(
            appEnabled = false,
            liveReadingEnabled = false,
            proximityAlertsEnabled = false,
        )
        currentSettings = updated
        analyzeJob?.cancel()
        analyzeJob = null
        screenshotInProgress.set(false)
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        traceEvent("bubble.stop.requested open_app_details=true")
        DiagnosticLogStore.record("bubble_action", "stop requested; reading=false alerts=false")
        scope.launch {
            runCatching { repository.saveSettings(updated) }
            toast("Rota Certa pausado. Confirme Forcar interrupcao para encerrar totalmente.")
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            delay(220L)
            removeOverlay()
            disableSelf()
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
        "BubbleShortcutAction.ToggleReading",
        "BubbleShortcutAction.OpenScreenWhatsApp",
        "BubbleShortcutAction.StopApplication",
        "showSavedAlertPopup(alert, distanceMeters)",
        "hideResourceShortcuts()",
        "KEY_RUNTIME_SHORTCUTS_OPEN",
        "bubble_reading_gate_0_1_118",
    ).forEach { if (it !in text) throw GradleException("Runtime modular incompleto: $it") }
    file.writeText(text)
}

val bubbleResourceShortcutsRuntime117 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn("patchScreenPhoneWhatsApp", "mainBubbleTapMenuContract", "bubbleInstantDrag116")
    doLast { enforceBubbleResourceShortcutsRuntime117(serviceFile.asFile) }
}

bubbleResourceShortcutsRuntime117.configure {
    mustRunAfter("patchScreenPhoneWhatsApp", "mainBubbleTapMenuContract", "bubbleInstantDrag116")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("test") }.configureEach { dependsOn(bubbleResourceShortcutsRuntime117) }
tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(bubbleResourceShortcutsRuntime117)
    doFirst { enforceBubbleResourceShortcutsRuntime117(layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile) }
}
