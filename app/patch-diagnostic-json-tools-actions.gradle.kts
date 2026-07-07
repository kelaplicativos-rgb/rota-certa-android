val patchDiagnosticJsonToolsActions by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.files(mainFile, serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        patchMainActivityForJsonAndToolActions(mainFile.asFile)
        patchServiceForBubbleDistancePolicy(serviceFile.asFile)
    }
}

patchDiagnosticJsonToolsActions.configure {
    mustRunAfter("patchBubbleCardParity", "patchFullDiagnosticExport", "patchPassiveScreenshotFailureGuard")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchDiagnosticJsonToolsActions)
}

fun patchMainActivityForJsonAndToolActions(file: java.io.File) {
    var text = file.readText()
    val original = text

    if ("Salvar diagnostico JSON" !in text) {
        text = text.replace(
"""    val context = LocalContext.current
    ExpandableCard(title = "Diagnostico tecnico", initiallyExpanded = false) {
""",
"""    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDiagnosticJson by remember { mutableStateOf<LiveDiagnostic?>(null) }
    val diagnosticJsonCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val selected = pendingDiagnosticJson
        pendingDiagnosticJson = null
        if (uri == null || selected == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(selected.toJsonText())
                } ?: error("Nao consegui criar o arquivo JSON.")
            }.onSuccess {
                Toast.makeText(context, "Diagnostico JSON salvo.", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context, "Falha ao salvar JSON: " + error.message.orEmpty(), Toast.LENGTH_SHORT).show()
            }
        }
    }
    ExpandableCard(title = "Diagnostico tecnico", initiallyExpanded = false) {
""",
        )

        text = text.replace(
"""            Text("Cor: ${'$'}{diagnostic.bubbleColor}")
""",
"""            OutlinedButton(
                onClick = {
                    pendingDiagnosticJson = diagnostic
                    diagnosticJsonCreator.launch(diagnosticJsonFileName(diagnostic))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Salvar diagnostico JSON")
            }
            Text("Cor: ${'$'}{diagnostic.bubbleColor}")
""",
        )
    }

    if ("// ToolsScreenSavePlaceActions" !in text) {
        text = text.replace(
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
                    // ToolsScreenSavePlaceActions
                    onSaveCurrentPlace = { createCurrentSavedPlace(SavedPlaceType.Place) },
                    onCreateProximityAlert = { createCurrentSavedPlace(SavedPlaceType.ProximityAlert) },
                )
""",
        )
    }

    if ("onCreateProximityAlert: () -> Unit" !in text) {
        text = text.replace(
"""    onOpenBlaBlaCarCollector: () -> Unit,
    onClearClipboard: () -> Unit,
) {
""",
"""    onOpenBlaBlaCarCollector: () -> Unit,
    onClearClipboard: () -> Unit,
    onSaveCurrentPlace: () -> Unit,
    onCreateProximityAlert: () -> Unit,
) {
""",
        )
    }

    if ("onSaveCurrentPlace = onSaveCurrentPlace" !in text) {
        text = text.replace(
"""            SavedPlacesCard(
                savedPlaces = savedPlaces,
                highlightedSavedPlaceId = highlightedSavedPlaceId,
                onRenameSavedPlace = onRenameSavedPlace,
                onDeleteSavedPlace = onDeleteSavedPlace,
            )
""",
"""            SavedPlacesCard(
                savedPlaces = savedPlaces,
                highlightedSavedPlaceId = highlightedSavedPlaceId,
                onRenameSavedPlace = onRenameSavedPlace,
                onDeleteSavedPlace = onDeleteSavedPlace,
                onSaveCurrentPlace = onSaveCurrentPlace,
                onCreateProximityAlert = onCreateProximityAlert,
            )
""",
        )
    }

    if ("onSaveCurrentPlace: (() -> Unit)? = null" !in text) {
        text = text.replace(
"""    highlightedSavedPlaceId: String?,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
) {
""",
"""    highlightedSavedPlaceId: String?,
    onRenameSavedPlace: (SavedPlace, String) -> Unit,
    onDeleteSavedPlace: (SavedPlace) -> Unit,
    onSaveCurrentPlace: (() -> Unit)? = null,
    onCreateProximityAlert: (() -> Unit)? = null,
) {
""",
        )
    }

    if ("Text(\"Salvar local atual\")" !in text) {
        text = text.replace(
"""    ExpandableCard(title = "Locais salvos (${'$'}{places.size})", initiallyExpanded = highlightedType == SavedPlaceType.Place) {
        if (places.isEmpty()) {
""",
"""    ExpandableCard(title = "Locais salvos (${'$'}{places.size})", initiallyExpanded = highlightedType == SavedPlaceType.Place) {
        onSaveCurrentPlace?.let { action ->
            Button(onClick = action, modifier = Modifier.fillMaxWidth()) {
                Text("Salvar local atual")
            }
            Spacer(Modifier.height(6.dp))
        }
        if (places.isEmpty()) {
""",
        )
    }

    if ("Text(\"Criar alerta neste local\")" !in text) {
        text = text.replace(
"""    ExpandableCard(title = "Alertas de proximidade (${'$'}{alerts.size})", initiallyExpanded = highlightedType == SavedPlaceType.ProximityAlert) {
        if (alerts.isEmpty()) {
""",
"""    ExpandableCard(title = "Alertas de proximidade (${'$'}{alerts.size})", initiallyExpanded = highlightedType == SavedPlaceType.ProximityAlert) {
        onCreateProximityAlert?.let { action ->
            Button(onClick = action, modifier = Modifier.fillMaxWidth()) {
                Text("Criar alerta neste local")
            }
            Spacer(Modifier.height(6.dp))
        }
        if (alerts.isEmpty()) {
""",
        )
    }

    if ("private fun diagnosticJsonFileName(" !in text) {
        text = text.replace(
"""private fun savedPlaceTypeLabel(place: SavedPlace): String = when (place.type) {
""",
"""private fun diagnosticJsonFileName(diagnostic: LiveDiagnostic): String =
    "rota-certa-diagnostico-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale("pt", "BR")).format(Date(diagnostic.createdAtMillis)) + ".json"

private fun LiveDiagnostic.toJsonText(): String =
    org.json.JSONObject().apply {
        put("marker", "DIAGNOSTIC_FULL_EXPORT")
        put("format", "rota-certa-diagnostic-json")
        put("createdAtMillis", createdAtMillis)
        put("date", formatDate(createdAtMillis))
        put("app", org.json.JSONObject().apply {
            put("versionName", appVersionName)
            put("versionCode", appVersionCode)
        })
        put("screen", org.json.JSONObject().apply {
            put("packageName", packageName ?: org.json.JSONObject.NULL)
            put("stage", stage)
            put("bubbleColor", bubbleColor)
            put("reason", reason)
            put("error", error ?: org.json.JSONObject.NULL)
        })
        put("state", org.json.JSONObject().apply {
            put("restrictToSelectedRideApps", restrictToSelectedRideApps)
            put("registeredCardRequired", registeredCardRequired)
            put("registeredCardMatched", registeredCardMatched ?: org.json.JSONObject.NULL)
            put("selectedPackages", org.json.JSONArray().apply { selectedPackages.forEach { put(it) } })
            put("recommendation", recommendation?.name ?: org.json.JSONObject.NULL)
        })
        put("card", org.json.JSONObject().apply {
            put("pickup", pickup ?: org.json.JSONObject.NULL)
            put("destination", destination ?: org.json.JSONObject.NULL)
            put("textLength", textLength)
            put("textHash", textHash ?: org.json.JSONObject.NULL)
            put("text", textPreview)
        })
        put("distances", org.json.JSONObject().apply {
            put("homeKm", homeDistanceKm ?: org.json.JSONObject.NULL)
            put("alternativeKm", alternativeDistanceKm ?: org.json.JSONObject.NULL)
            put("bubbleKmPolicy", "hidden_when_route_is_not_explicitly_trusted")
        })
        put("logs", org.json.JSONArray().apply {
            diagnosticLog.lines().filter { it.isNotBlank() }.forEach { put(it) }
        })
    }.toString(2)

private fun savedPlaceTypeLabel(place: SavedPlace): String = when (place.type) {
""",
        )
    }

    if (text != original) {
        file.writeText(text)
    }
}

fun patchServiceForBubbleDistancePolicy(file: java.io.File) {
    var text = file.readText()
    val original = text

    if ("bubble.distance_label hidden_by_policy" !in text) {
        text = text.replace(
"""        currentDistanceKm = distanceKm
        currentBubbleLabel = labelText
""",
"""        if (distanceKm != null) {
            traceEvent("bubble.distance_label hidden_by_policy km=" + formatDiagnosticKm(distanceKm))
        }
        currentDistanceKm = null
        currentBubbleLabel = labelText
""",
        )
    }

    if (text != original) {
        file.writeText(text)
    }
}
