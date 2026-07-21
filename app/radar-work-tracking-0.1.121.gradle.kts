// Rota Certa 0.1.121
// - reconhece enderecos iniciados por Passagem;
// - radar importado ganha bip curto, voz e popup visual uma vez por aproximacao;
// - ciclo de proximidade reduzido de 15 s para 2 s e direcao usa trilha recente;
// - adiciona Rastreamento de trabalho local com servico GPS em primeiro plano.

fun replaceRequired121(source: String, old: String, new: String, label: String): String {
    if (old !in source) throw GradleException("Trecho ausente para $label")
    return source.replaceFirst(old, new)
}

fun patchParser121(file: java.io.File) {
    var text = file.readText()
    if ("|passagem|" !in text) {
        text = replaceRequired121(
            text,
            "|beco|marginal|servidao|servidão)",
            "|beco|marginal|passagem|servidao|servidão)",
            "logradouro Passagem",
        )
    }
    if ("passagem" !in text) throw GradleException("Parser sem suporte a Passagem.")
    file.writeText(text)
}

fun patchModels121(file: java.io.File) {
    var text = file.readText()
    text = text.replace("val proximityAlertDistanceMeters: Int = 200,", "val proximityAlertDistanceMeters: Int = 500,")
    if ("val proximityAlertDistanceMeters: Int = 500," !in text) {
        throw GradleException("Distancia padrao do primeiro aviso nao atualizada.")
    }
    file.writeText(text)
}

fun patchProximityEngine121(file: java.io.File) {
    var text = file.readText()
    if ("private val recentCoordinates = ArrayDeque<Coordinate>()" !in text) {
        text = replaceRequired121(
            text,
            "    private var lastCoordinate: Coordinate? = null\n",
            "    private var lastCoordinate: Coordinate? = null\n    private val recentCoordinates = ArrayDeque<Coordinate>()\n",
            "trilha recente do radar",
        )
    }
    if ("onImportedRadarDetected: (ImportedRadar, Double) -> Unit" !in text) {
        text = replaceRequired121(
            text,
            "        onSavedPlacePopup: (SavedPlace, Double) -> Unit = { _, _ -> },\n        onDiagnostic: (ProximityAlertDiagnostic) -> Unit,\n",
            "        onSavedPlacePopup: (SavedPlace, Double) -> Unit = { _, _ -> },\n        onImportedRadarDetected: (ImportedRadar, Double) -> Unit = { _, _ -> },\n        onDiagnostic: (ProximityAlertDiagnostic) -> Unit,\n",
            "callback do radar importado",
        )
        text = replaceRequired121(
            text,
            "        val previousCoordinate = lastCoordinate\n        val movementMeters = previousCoordinate?.let { GeoDistance.meters(it, coordinate) }\n        val movementBearing = previousCoordinate\n            ?.takeIf { movementMeters != null && movementMeters >= MIN_MOVEMENT_FOR_BEARING_METERS }\n            ?.let { GeoDistance.bearingDegrees(it, coordinate) }\n",
            "        val previousCoordinate = lastCoordinate\n        recentCoordinates.addLast(coordinate)\n        while (recentCoordinates.size > RECENT_COORDINATE_LIMIT) recentCoordinates.removeFirst()\n        val movementMeters = previousCoordinate?.let { GeoDistance.meters(it, coordinate) }\n        val bearingOrigin = recentCoordinates.firstOrNull { origin ->\n            GeoDistance.meters(origin, coordinate) >= MIN_MOVEMENT_FOR_BEARING_METERS\n        } ?: previousCoordinate\n        val movementBearing = bearingOrigin\n            ?.takeIf { GeoDistance.meters(it, coordinate) >= MIN_MOVEMENT_FOR_BEARING_METERS }\n            ?.let { GeoDistance.bearingDegrees(it, coordinate) }\n",
            "direcao por trilha recente",
        )
        text = replaceRequired121(
            text,
            "        checkImportedRadars(radars, coordinate, settings, now, movementBearing, onDiagnostic)\n",
            "        checkImportedRadars(radars, coordinate, settings, now, movementBearing, onImportedRadarDetected, onDiagnostic)\n",
            "ligacao do callback do radar",
        )
        text = replaceRequired121(
            text,
            "        movementBearing: Double?,\n        onDiagnostic: (ProximityAlertDiagnostic) -> Unit,\n",
            "        movementBearing: Double?,\n        onImportedRadarDetected: (ImportedRadar, Double) -> Unit,\n        onDiagnostic: (ProximityAlertDiagnostic) -> Unit,\n",
            "assinatura de radar importado",
        )
        text = replaceRequired121(
            text,
            "                if (runtime.spokenCount > 0 || runtime.lastSpokenAtMillis > 0L) {\n",
            "                if (runtime.spokenCount > 0 || runtime.lastSpokenAtMillis > 0L || runtime.popupShownThisApproach) {\n",
            "rearme visual do radar",
        )
        text = replaceRequired121(
            text,
            "        if (runtime.canSpeak(now, MAX_IMPORTED_RADAR_SPEECH_COUNT)) {\n",
            "        if (!runtime.popupShownThisApproach) {\n            runtime.popupShownThisApproach = true\n            onImportedRadarDetected(radar, distanceMeters)\n            trace(now = now, message = \"imported_radar.signal.shown id=${'$'}{radar.id}\")\n        }\n        if (runtime.canSpeak(now, MAX_IMPORTED_RADAR_SPEECH_COUNT)) {\n",
            "sinal unico do radar",
        )
        text = replaceRequired121(
            text,
            "        const val MIN_MOVEMENT_FOR_BEARING_METERS = 8.0\n",
            "        const val MIN_MOVEMENT_FOR_BEARING_METERS = 8.0\n        const val RECENT_COORDINATE_LIMIT = 6\n",
            "limite da trilha recente",
        )
    }
    listOf(
        "onImportedRadarDetected(radar, distanceMeters)",
        "imported_radar.signal.shown",
        "RECENT_COORDINATE_LIMIT = 6",
    ).forEach { marker -> if (marker !in text) throw GradleException("Engine 0.1.121 incompleto: $marker") }
    file.writeText(text)
}

fun patchOverlayController121(file: java.io.File) {
    var text = file.readText()
    if ("fun showImportedRadarAlert(" !in text) {
        val method = """    fun showImportedRadarAlert(
        radar: ImportedRadar,
        distanceMeters: Double,
        onDismiss: () -> Unit = {},
    ) {
        hideShortcuts()
        hideProximityAlert()

        val container = alertContainer()
        container.addView(TextView(context).apply {
            text = "⚠️  ${'$'}{importedRadarTypeLabel(radar.type)}"
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            contentDescription = importedRadarTypeLabel(radar.type)
        })
        radar.speedKmh?.let { speed ->
            container.addView(TextView(context).apply {
                text = "Limite ${'$'}speed km/h"
                textSize = 16f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(4), 0, 0)
            })
        }
        container.addView(TextView(context).apply {
            text = "A aproximadamente ${'$'}{distanceMeters.roundToInt()} m"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(8))
        })
        container.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(popupButton("Fechar") { hideProximityAlert(); onDismiss() })
        })

        val params = WindowManager.LayoutParams(
            dp(310).coerceAtMost(context.resources.displayMetrics.widthPixels - dp(24)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(72)
        }
        if (runCatching { windowManager.addView(container, params) }.isSuccess) {
            alertPopupView = container
            trace("imported_radar.popup.shown id=${'$'}{radar.id} distance=${'$'}{distanceMeters.roundToInt()}")
        }
    }

"""
        text = replaceRequired121(
            text,
            "    fun hideProximityAlert() {\n",
            method + "    fun hideProximityAlert() {\n",
            "popup visual do radar",
        )
    }
    listOf("showImportedRadarAlert", "Limite ${'$'}speed km/h", "imported_radar.popup.shown").forEach { marker ->
        if (marker !in text) throw GradleException("Popup do radar incompleto: $marker")
    }
    file.writeText(text)
}

fun patchLiveService121(file: java.io.File) {
    var text = file.readText()
    if ("radar_detection_audio_visual_0_1_121" !in text) {
        if ("private lateinit var radarDetectionCue: RadarDetectionCue" !in text) {
            text = replaceRequired121(
                text,
                "    private lateinit var shortcutOverlayController: BubbleShortcutOverlayController\n",
                "    private lateinit var shortcutOverlayController: BubbleShortcutOverlayController\n    private lateinit var radarDetectionCue: RadarDetectionCue\n",
                "propriedade do bip",
            )
        }
        if ("radarDetectionCue = RadarDetectionCue()" !in text) {
            text = replaceRequired121(
                text,
                "        proximityAlertEngine = ProximityAlertEngine(speechEngine)\n",
                "        proximityAlertEngine = ProximityAlertEngine(speechEngine)\n        radarDetectionCue = RadarDetectionCue()\n",
                "inicializacao do bip",
            )
        }
        if ("radarDetectionCue.release()" !in text) {
            text = replaceRequired121(
                text,
                "        textToSpeech?.stop()\n",
                "        radarDetectionCue.release()\n        textToSpeech?.stop()\n",
                "liberacao do bip",
            )
        }
        if ("onImportedRadarDetected =" !in text) {
            text = replaceRequired121(
                text,
                "            onSavedPlacePopup = { alert, distanceMeters -> showSavedAlertPopup(alert, distanceMeters) },\n            onDiagnostic = { diagnostic -> recordDiagnostic(stage = diagnostic.stage, reason = diagnostic.reason) },\n",
                "            onSavedPlacePopup = { alert, distanceMeters -> showSavedAlertPopup(alert, distanceMeters) },\n            onImportedRadarDetected = { radar, distanceMeters -> showImportedRadarPopup(radar, distanceMeters) },\n            onDiagnostic = { diagnostic -> recordDiagnostic(stage = diagnostic.stage, reason = diagnostic.reason) },\n",
                "callback no servico",
            )
        }
        if ("private fun showImportedRadarPopup(" !in text) {
            val method = """    private fun showImportedRadarPopup(radar: ImportedRadar, distanceMeters: Double) {
        radarDetectionCue.play()
        shortcutOverlayController.showImportedRadarAlert(radar, distanceMeters)
        persistResourceShortcutState()
        traceEvent("imported_radar.signal audio=true popup=true id=${'$'}{radar.id}")
    }

"""
            text = replaceRequired121(
                text,
                "    private fun showSavedAlertPopup(alert: SavedPlace, distanceMeters: Double) {\n",
                method + "    private fun showSavedAlertPopup(alert: SavedPlace, distanceMeters: Double) {\n",
                "metodo visual e sonoro do radar",
            )
        }
        text = text.replace("const val PROXIMITY_ALERT_LOOP_MS = 15_000L", "const val PROXIMITY_ALERT_LOOP_MS = 2_000L")
        text += "\n// radar_detection_audio_visual_0_1_121\n"
    }
    listOf(
        "onImportedRadarDetected = { radar, distanceMeters -> showImportedRadarPopup(radar, distanceMeters) }",
        "radarDetectionCue.play()",
        "showImportedRadarAlert(radar, distanceMeters)",
        "const val PROXIMITY_ALERT_LOOP_MS = 2_000L",
        "radar_detection_audio_visual_0_1_121",
    ).forEach { marker -> if (marker !in text) throw GradleException("Servico 0.1.121 incompleto: $marker") }
    file.writeText(text)
}

fun findBalancedCallEnd121(source: String, start: Int): Int {
    val open = source.indexOf('(', start)
    if (open < 0) return -1
    var depth = 0
    for (index in open until source.length) {
        when (source[index]) {
            '(' -> depth += 1
            ')' -> {
                depth -= 1
                if (depth == 0) return index
            }
        }
    }
    return -1
}

fun patchMainActivity121(file: java.io.File) {
    var text = file.readText()
    if ("work_tracking_ui_0_1_121" !in text) {
        if ("onOpenWorkTracking =" !in text) {
            val callStart = text.indexOf("TAB_TOOLS -> ToolsScreen(")
            val callEnd = if (callStart >= 0) findBalancedCallEnd121(text, callStart) else -1
            if (callStart < 0 || callEnd <= callStart) throw GradleException("Chamada ToolsScreen ausente.")
            val call = text.substring(callStart, callEnd + 1)
            val closingIndent = call.substringAfterLast('\n').takeWhile(Char::isWhitespace)
            val argumentIndent = closingIndent + "    "
            val updatedCall = call.dropLast(closingIndent.length + 1) +
                "${argumentIndent}onOpenWorkTracking = { context.startActivity(Intent(context, WorkTrackingActivity::class.java)) },\n" +
                closingIndent + ")"
            text = text.substring(0, callStart) + updatedCall + text.substring(callEnd + 1)
        }
        if ("onOpenWorkTracking: () -> Unit" !in text) {
            val signatureStart = text.indexOf("private fun ToolsScreen(")
            val signatureEnd = if (signatureStart >= 0) text.indexOf(") {", signatureStart) else -1
            if (signatureStart < 0 || signatureEnd <= signatureStart) throw GradleException("Assinatura ToolsScreen ausente.")
            text = text.substring(0, signatureEnd) +
                "    onOpenWorkTracking: () -> Unit = {},\n" +
                text.substring(signatureEnd)
        }
        if ("Text(\"Rastreamento de trabalho\"" !in text) {
            val card = """        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rastreamento de trabalho", fontWeight = FontWeight.Bold)
                Text(
                    "Registra o caminho percorrido, distancia, tempo e os pontos de GPS do dia somente neste aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onOpenWorkTracking, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir rastreamento")
                }
            }
        }
"""
            text = replaceRequired121(
                text,
                "        Text(\"Ferramentas\", fontWeight = FontWeight.Bold)\n",
                "        Text(\"Ferramentas\", fontWeight = FontWeight.Bold)\n" + card,
                "card do rastreamento",
            )
        }
        text = text
            .replace("Text(\"Distancia do alerta de proximidade\")", "Text(\"Distancia do primeiro aviso\")")
            .replace("val selectedIndex = allowedValues.indexOf(value).takeIf { it >= 0 } ?: 0", "val selectedIndex = allowedValues.indexOf(value).takeIf { it >= 0 } ?: 1")
            .replace("place.alertDistanceMeters ?: 200", "place.alertDistanceMeters ?: 500")
            .replace("Text(\"Falar radares e proximidade\"", "Text(\"Bip, voz e popup de radares e proximidade\"")
        text += "\n// work_tracking_ui_0_1_121\n"
    }
    listOf(
        "onOpenWorkTracking =",
        "onOpenWorkTracking: () -> Unit",
        "WorkTrackingActivity::class.java",
        "Abrir rastreamento",
        "Distancia do primeiro aviso",
        "work_tracking_ui_0_1_121",
    ).forEach { marker -> if (marker !in text) throw GradleException("Tela 0.1.121 incompleta: $marker") }
    file.writeText(text)
}

fun patchManifest121(file: java.io.File) {
    var text = file.readText()
    if ("android.permission.FOREGROUND_SERVICE_LOCATION" !in text) {
        text = replaceRequired121(
            text,
            "    <uses-permission android:name=\"android.permission.ACCESS_BACKGROUND_LOCATION\" />\n",
            "    <uses-permission android:name=\"android.permission.ACCESS_BACKGROUND_LOCATION\" />\n    <uses-permission android:name=\"android.permission.FOREGROUND_SERVICE\" />\n    <uses-permission android:name=\"android.permission.FOREGROUND_SERVICE_LOCATION\" />\n    <uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\" />\n",
            "permissoes do rastreamento",
        )
    }
    if (".WorkTrackingActivity" !in text) {
        text = replaceRequired121(
            text,
            "        <activity\n            android:name=\".BlaBlaCarCollectorActivity\"",
            "        <activity\n            android:name=\".WorkTrackingActivity\"\n            android:exported=\"false\"\n            android:label=\"Rastreamento de trabalho\" />\n        <activity\n            android:name=\".BlaBlaCarCollectorActivity\"",
            "activity do rastreamento",
        )
    }
    if (".WorkTrackingService" !in text) {
        text = replaceRequired121(
            text,
            "        <service\n            android:name=\".LiveRideAccessibilityService\"",
            "        <service\n            android:name=\".WorkTrackingService\"\n            android:exported=\"false\"\n            android:foregroundServiceType=\"location\" />\n        <service\n            android:name=\".LiveRideAccessibilityService\"",
            "servico do rastreamento",
        )
    }
    listOf("FOREGROUND_SERVICE_LOCATION", ".WorkTrackingActivity", ".WorkTrackingService").forEach { marker ->
        if (marker !in text) throw GradleException("Manifest 0.1.121 incompleto: $marker")
    }
    file.writeText(text)
}

val radarWorkTracking121 by tasks.registering {
    val parser = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt")
    val models = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/Models.kt")
    val engine = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/ProximityAlertEngine.kt")
    val controller = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt")
    val service = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val main = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    val manifest = layout.projectDirectory.file("src/main/AndroidManifest.xml")
    inputs.files(parser, models, engine, controller, service, main, manifest)
    outputs.upToDateWhen { false }
    dependsOn("bubbleResourceShortcutsRuntime117", "popupNavigationLateCompile120")
    doLast {
        patchParser121(parser.asFile)
        patchModels121(models.asFile)
        patchProximityEngine121(engine.asFile)
        patchOverlayController121(controller.asFile)
        patchLiveService121(service.asFile)
        patchMainActivity121(main.asFile)
        patchManifest121(manifest.asFile)
    }
}

radarWorkTracking121.configure {
    mustRunAfter("bubbleResourceShortcutsRuntime117", "popupNavigationLateCompile120")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(radarWorkTracking121)
}
