// Rota Certa 0.1.117
//
// Contrato:
// - toque simples na bolinha principal abre uma grade leve de atalhos circulares;
// - arraste continua instantaneo e fecha a grade antes de mover;
// - Alerta cria item no GPS atual com nome editavel "Alerta" e abre o grupo Alertas;
// - Local cria local no GPS atual e abre o editor;
// - Card salva o card atual;
// - Destino, Leitura e Ajustes abrem diretamente o grupo correspondente;
// - ao entrar novamente na zona de um alerta salvo, mostra popup com Fechar, Editar e Excluir;
// - popup aparece uma vez por aproximacao e so volta depois de sair e reentrar.

fun shortcut117ReplaceRegion(
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

fun enforceBubbleResourceShortcuts117(
    serviceFile: java.io.File,
    engineFile: java.io.File,
    mainFile: java.io.File,
) {
    if (!serviceFile.exists() || !engineFile.exists() || !mainFile.exists()) {
        throw GradleException("Arquivos da versao 0.1.117 nao encontrados.")
    }

    var service = serviceFile.readText()
    var engine = engineFile.readText()
    var main = mainFile.readText()

    if ("bubble_resource_shortcuts_0_1_117" !in service) {
        if ("import android.widget.GridLayout" !in service) {
            service = service.replace(
                "import android.widget.LinearLayout\n",
                "import android.widget.GridLayout\nimport android.widget.LinearLayout\n",
            )
        }
        service = service.replace(
            "    private var overlayMenuView: LinearLayout? = null\n",
            "    private var overlayMenuView: View? = null\n    private var proximityPopupView: View? = null\n",
        )

        service = service.replace(
            "                onDiagnostic = { diagnostic ->\n",
            "                onSavedPlacePopup = { alert, distanceMeters ->\n                    scope.launch { showProximityAlertPopup(alert, distanceMeters) }\n                },\n                onDiagnostic = { diagnostic ->\n",
        )

        service = service.replace(
            "                name = if (isAlert) \"Alerta de proximidade\" else \"Local salvo\",\n",
            "                name = if (isAlert) \"Alerta\" else \"Local salvo\",\n",
        )

        service = service.replace(
            "                    .putExtra(EXTRA_SAVED_PLACE_ID, place.id),\n",
            "                    .putExtra(EXTRA_SAVED_PLACE_ID, place.id)\n                    .putExtra(EXTRA_OPEN_BUBBLE_GROUP, if (place.type == SavedPlaceType.ProximityAlert) BUBBLE_GROUP_ALERTS_VALUE else BUBBLE_GROUP_DESTINATION_VALUE),\n",
        )

        val openAppStart = service.indexOf("    private fun openApp() {")
        val editorStart = if (openAppStart >= 0) service.indexOf("    private fun openSavedPlaceEditor", openAppStart) else -1
        if (openAppStart < 0 || editorStart <= openAppStart) throw GradleException("openApp ausente para atalhos.")
        val openHelpers = """    private fun openApp() {
        openAppGroup(BUBBLE_GROUP_DESTINATION_VALUE, TAB_ANALYSIS)
    }

    private fun openAppGroup(group: String, tab: String) {
        hideActionMenu()
        hideProximityAlertPopup()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, tab)
                    .putExtra(EXTRA_OPEN_BUBBLE_GROUP, group),
            )
        }
    }

"""
        service = service.substring(0, openAppStart) + openHelpers + service.substring(editorStart)

        val toggleStart = service.indexOf("    private fun toggleActionMenu() {")
        val hideStart = if (toggleStart >= 0) service.indexOf("    private fun hideActionMenu() {", toggleStart) else -1
        if (toggleStart < 0 || hideStart <= toggleStart) throw GradleException("Menu antigo ausente para atalhos.")
        val shortcutMenu = """    private fun toggleActionMenu() {
        if (overlayMenuView != null) hideActionMenu() else showActionMenu()
    }

    private fun showActionMenu() {
        val manager = windowManager ?: return
        if (overlayMenuView != null) return
        hideProximityAlertPopup()
        val bubbleParams = overlayParams ?: return
        val menuSize = dp(222)
        val menu = GridLayout(this).apply {
            columnCount = 3
            rowCount = 2
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.argb(232, 25, 25, 25))
                setStroke(dp(1), Color.argb(220, 255, 255, 255))
            }
            setPadding(dp(7), dp(7), dp(7), dp(7))
            addView(resourceShortcutBubble("⚠️\nAlerta") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)
            })
            addView(resourceShortcutBubble("📍\nLocal") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            })
            addView(resourceShortcutBubble("💾\nCard") {
                hideActionMenu()
                saveCurrentRideCardFromBubble()
            })
            addView(resourceShortcutBubble("🏠\nDestino") {
                openAppGroup(BUBBLE_GROUP_DESTINATION_VALUE, TAB_ANALYSIS)
            })
            addView(resourceShortcutBubble("👁\nLeitura") {
                openAppGroup(BUBBLE_GROUP_READING_VALUE, TAB_CONFIG)
            })
            addView(resourceShortcutBubble("⚙️\nAjustes") {
                openAppGroup(BUBBLE_GROUP_GENERAL_VALUE, TAB_CONFIG)
            })
        }
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val params = WindowManager.LayoutParams(
            menuSize,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val rightX = bubbleParams.x + dp(74)
            x = if (rightX + menuSize <= screenWidth) rightX else (bubbleParams.x - menuSize - dp(8)).coerceAtLeast(0)
            y = bubbleParams.y.coerceIn(0, (screenHeight - dp(170)).coerceAtLeast(0))
        }
        if (runCatching { manager.addView(menu, params) }.isSuccess) {
            overlayMenuView = menu
            overlayMenuParams = params
            traceEvent("bubble.shortcuts.opened count=6")
        }
    } // bubble_resource_shortcuts_0_1_117

    private fun resourceShortcutBubble(label: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            contentDescription = label.replace("\n", " ")
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(72, 64, 82))
                setStroke(dp(2), Color.argb(230, 205, 180, 255))
            }
            layoutParams = GridLayout.LayoutParams().apply {
                width = dp(66)
                height = dp(66)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            setOnClickListener { action() }
        }

"""
        service = service.substring(0, toggleStart) + shortcutMenu + service.substring(hideStart)

        val toastStart = service.indexOf("    private fun toast(message: String) {")
        if (toastStart < 0) throw GradleException("Toast ausente para popup de alerta.")
        val popupHelpers = """    private fun showProximityAlertPopup(alert: SavedPlace, distanceMeters: Double) {
        val manager = windowManager ?: return
        hideProximityAlertPopup()
        hideActionMenu()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.argb(246, 38, 38, 38))
                setStroke(dp(3), Color.rgb(255, 193, 7))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(TextView(this@LiveRideAccessibilityService).apply {
                text = "⚠️  " + alert.name
                textSize = 19f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@LiveRideAccessibilityService).apply {
                text = "Alerta a " + distanceMeters.roundToInt() + " m"
                textSize = 14f
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(4), 0, dp(8))
            })
            addView(LinearLayout(this@LiveRideAccessibilityService).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(proximityPopupButton("Fechar") { hideProximityAlertPopup() })
                addView(proximityPopupButton("Editar") {
                    hideProximityAlertPopup()
                    openSavedPlaceEditor(alert)
                })
                addView(proximityPopupButton("Excluir") {
                    showProximityDeleteConfirmation(alert)
                })
            })
        }
        val width = dp(310).coerceAtMost(resources.displayMetrics.widthPixels - dp(24))
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(72)
        }
        if (runCatching { manager.addView(container, params) }.isSuccess) {
            proximityPopupView = container
            traceEvent("proximity.popup.shown id=" + alert.id + " distance=" + distanceMeters.roundToInt())
        }
    }

    private fun proximityPopupButton(label: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(9), dp(10), dp(9))
            layoutParams = LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.rgb(79, 68, 88))
            }
            setOnClickListener { action() }
        }

    private fun showProximityDeleteConfirmation(alert: SavedPlace) {
        val manager = windowManager ?: return
        val old = proximityPopupView ?: return
        val parent = old as? LinearLayout ?: return
        parent.removeAllViews()
        parent.addView(TextView(this).apply {
            text = "⚠️ Excluir \"" + alert.name + "\"?"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(10))
        })
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(proximityPopupButton("Cancelar") { hideProximityAlertPopup() })
            addView(proximityPopupButton("Confirmar") {
                scope.launch {
                    repository.removeSavedPlace(alert.id)
                    hideProximityAlertPopup()
                    toast("Alerta excluido.")
                    traceEvent("proximity.popup.deleted id=" + alert.id)
                }
            })
        })
        runCatching { manager.updateViewLayout(parent, parent.layoutParams) }
    }

    private fun hideProximityAlertPopup() {
        val view = proximityPopupView ?: return
        runCatching { windowManager?.removeView(view) }
        proximityPopupView = null
    }

"""
        service = service.substring(0, toastStart) + popupHelpers + service.substring(toastStart)

        service = service.replace(
            "                    bubbleGestureActive = true\n",
            "                    hideActionMenu()\n                    bubbleGestureActive = true\n",
        )

        val companionAnchor = "        const val BUBBLE_PREFS = \"rota_certa_bubble\"\n"
        if (companionAnchor !in service) throw GradleException("Companion da bolinha ausente.")
        service = service.replaceFirst(
            companionAnchor,
            companionAnchor + """        const val EXTRA_OPEN_BUBBLE_GROUP = "open_bubble_group"
        const val BUBBLE_GROUP_GENERAL_VALUE = "general"
        const val BUBBLE_GROUP_READING_VALUE = "reading"
        const val BUBBLE_GROUP_DESTINATION_VALUE = "destination"
        const val BUBBLE_GROUP_ALERTS_VALUE = "alerts"
""",
        )
    }

    if ("proximity_popup_once_per_approach_0_1_117" !in engine) {
        engine = engine.replace(
            "        onDiagnostic: (ProximityAlertDiagnostic) -> Unit,\n    ) {\n",
            "        onDiagnostic: (ProximityAlertDiagnostic) -> Unit,\n        onSavedPlacePopup: (SavedPlace, Double) -> Unit = { _, _ -> },\n    ) {\n",
        )
        engine = engine.replace(
            "        checkSavedPlaceAlerts(alerts, coordinate, settings, now, onDiagnostic)\n",
            "        checkSavedPlaceAlerts(alerts, coordinate, settings, now, onDiagnostic, onSavedPlacePopup)\n",
        )
        engine = engine.replace(
            "        onDiagnostic: (ProximityAlertDiagnostic) -> Unit,\n    ) {\n        alerts.forEach { alert ->\n",
            "        onDiagnostic: (ProximityAlertDiagnostic) -> Unit,\n        onSavedPlacePopup: (SavedPlace, Double) -> Unit,\n    ) {\n        alerts.forEach { alert ->\n",
        )
        engine = engine.replace(
            "                if (runtime.canSpeak(now, MAX_SAVED_PLACE_SPEECH_COUNT)) {\n",
            "                if (!runtime.popupShownThisApproach) {\n                    runtime.popupShownThisApproach = true // proximity_popup_once_per_approach_0_1_117\n                    onSavedPlacePopup(alert, distanceMeters)\n                    trace(now = now, message = \"saved_alert.popup.shown id=${alert.id}\")\n                }\n                if (runtime.canSpeak(now, MAX_SAVED_PLACE_SPEECH_COUNT)) {\n",
        )
        engine = engine.replace(
            "        var savedPlaceInsideZone: Boolean = false,\n",
            "        var savedPlaceInsideZone: Boolean = false,\n        var popupShownThisApproach: Boolean = false,\n",
        )
        engine = engine.replace(
            "            savedPlaceInsideZone = false\n        }\n",
            "            savedPlaceInsideZone = false\n            popupShownThisApproach = false\n        }\n",
        )
        engine = engine.replace(
            "            savedPlaceInsideZone = false\n        }\n    }\n",
            "            savedPlaceInsideZone = false\n            popupShownThisApproach = false\n        }\n    }\n",
        )
    }

    if ("bubble_shortcut_group_launch_0_1_117" !in main) {
        val stateAnchor = "    var selectedBubbleGroup by remember { mutableStateOf(BUBBLE_GROUP_DESTINATION) } // grouped_bubble_state_0_1_115\n"
        if (stateAnchor !in main) throw GradleException("Estado agrupado ausente para atalhos.")
        val launchMarker = "        } // grouped_bubble_launch_0_1_115\n"
        val launchIndex = main.indexOf(launchMarker)
        if (launchIndex < 0) throw GradleException("Navegacao agrupada ausente para atalhos.")
        val launchEnd = launchIndex + launchMarker.length
        main = main.substring(0, launchEnd) + """        launchIntent?.getStringExtra(EXTRA_OPEN_BUBBLE_GROUP)?.let { requestedGroup ->
            if (requestedGroup in BUBBLE_GROUP_VALUES) selectedBubbleGroup = requestedGroup
        } // bubble_shortcut_group_launch_0_1_117
""" + main.substring(launchEnd)

        main = main.replace(
            "                enabled = draftName.trim() != place.name,\n",
            "                enabled = draftName.trim().isNotBlank(),\n",
        )
        main = main.replace(
            "    SavedPlaceType.ProximityAlert -> \"Alerta de proximidade\"\n",
            "    SavedPlaceType.ProximityAlert -> \"Alerta\"\n",
        )
        val groupConstantsAnchor = "private const val BUBBLE_GROUP_TOOLS = \"tools\"\n"
        if (groupConstantsAnchor !in main) throw GradleException("Constantes dos grupos ausentes.")
        main = main.replaceFirst(
            groupConstantsAnchor,
            groupConstantsAnchor + """private const val EXTRA_OPEN_BUBBLE_GROUP = "open_bubble_group"
private val BUBBLE_GROUP_VALUES = setOf(
    BUBBLE_GROUP_GENERAL,
    BUBBLE_GROUP_READING,
    BUBBLE_GROUP_DESTINATION,
    BUBBLE_GROUP_ALERTS,
    BUBBLE_GROUP_APPEARANCE,
    BUBBLE_GROUP_ACCESS,
    BUBBLE_GROUP_REPORTS,
    BUBBLE_GROUP_BACKUP,
    BUBBLE_GROUP_TOOLS,
)
""",
        )
    }

    listOf(
        "bubble_resource_shortcuts_0_1_117",
        "bubble.shortcuts.opened count=6",
        "name = if (isAlert) \"Alerta\"",
        "proximity.popup.shown",
        "proximity_popup_once_per_approach_0_1_117",
        "bubble_shortcut_group_launch_0_1_117",
    ).forEach { marker ->
        if (marker !in service && marker !in engine && marker !in main) {
            throw GradleException("Contrato 0.1.117 incompleto: $marker")
        }
    }

    serviceFile.writeText(service)
    engineFile.writeText(engine)
    mainFile.writeText(main)
}

val bubbleResourceShortcutsAlertPopup117 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val engineFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/ProximityAlertEngine.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, engineFile, mainFile)
    outputs.upToDateWhen { false }
    dependsOn("bubbleInstantDrag116", "groupedBubbleHome115")
    doLast { enforceBubbleResourceShortcuts117(serviceFile.asFile, engineFile.asFile, mainFile.asFile) }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("test") }.configureEach {
    dependsOn(bubbleResourceShortcutsAlertPopup117)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(bubbleResourceShortcutsAlertPopup117)
    doFirst {
        enforceBubbleResourceShortcuts117(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/ProximityAlertEngine.kt").asFile,
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
