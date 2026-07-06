val patchUxPlacesAlertsRadars by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.files(mainFile, serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val main = mainFile.asFile
        var text = main.readText()
        val original = text

        text = text.replace(
            "if (initiallyExpanded || requestedExpander == title) expanded = true",
            "if (initiallyExpanded || requestedExpander == title || (requestedExpander != null && title.startsWith(requestedExpander))) expanded = true",
        )

        text = text.replace(
"""    LaunchedEffect(highlightedSavedPlaceId, savedPlaces) {
        val id = highlightedSavedPlaceId ?: return@LaunchedEffect
        val place = savedPlaces.firstOrNull { it.id == id && it.type == SavedPlaceType.Place }
        if (place != null && handledSavedPlaceNameDialogId != id) {
            savedPlaceNameDialogId = id
        }
    }

    savedPlaces.firstOrNull { it.id == savedPlaceNameDialogId && it.type == SavedPlaceType.Place }?.let { place ->
""",
"""    LaunchedEffect(highlightedSavedPlaceId, savedPlaces) {
        val id = highlightedSavedPlaceId ?: return@LaunchedEffect
        val place = savedPlaces.firstOrNull { it.id == id }
        if (place != null && handledSavedPlaceNameDialogId != id) {
            savedPlaceNameDialogId = id
        }
    }

    savedPlaces.firstOrNull { it.id == savedPlaceNameDialogId }?.let { place ->
""",
        )

        text = text.replace(
            "title = { Text(\"Nome do local\") },",
            "title = { Text(if (place.type == SavedPlaceType.ProximityAlert) \"Nome do alerta\" else \"Nome do local\") },",
        )
        text = text.replace(
            "label = { Text(\"Digite o nome\") },",
            "label = { Text(if (place.type == SavedPlaceType.ProximityAlert) \"Digite o alerta\" else \"Digite o nome\") },",
        )
        text = text.replace(
            "Text(place.address.ifBlank { formatCoordinate(place.coordinate) }, style = MaterialTheme.typography.bodySmall)",
            "Text(place.address.ifBlank { formatCoordinate(place.coordinate) }, style = MaterialTheme.typography.bodySmall)\n                if (place.type == SavedPlaceType.ProximityAlert) {\n                    Text(\"Esse nome sera usado no aviso quando voce se aproximar deste ponto.\", style = MaterialTheme.typography.bodySmall)\n                }",
        )

        text = text.replace(
"""        ExpandableCard(title = "Alertas de proximidade", initiallyExpanded = false) {
            RadarImportCard(
                summary = radarImportSummary,
                importStatus = radarImportStatus,
                onPickFile = onImportRadarFile,
                onOpenMapaRadar = onOpenMapaRadar,
                onClearRadars = onClearImportedRadars,
            )
            Spacer(Modifier.height(8.dp))
            SavedPlacesCard(
                savedPlaces = savedPlaces,
                highlightedSavedPlaceId = highlightedSavedPlaceId,
                onRenameSavedPlace = onRenameSavedPlace,
                onDeleteSavedPlace = onDeleteSavedPlace,
            )
        }
""",
"""        SavedPlacesCard(
            savedPlaces = savedPlaces,
            highlightedSavedPlaceId = highlightedSavedPlaceId,
            onRenameSavedPlace = onRenameSavedPlace,
            onDeleteSavedPlace = onDeleteSavedPlace,
        )
        ExpandableCard(title = "Radares importados (" + radarImportSummary.count + ")", initiallyExpanded = false) {
            RadarImportCard(
                summary = radarImportSummary,
                importStatus = radarImportStatus,
                onPickFile = onImportRadarFile,
                onOpenMapaRadar = onOpenMapaRadar,
                onClearRadars = onClearImportedRadars,
            )
        }
""",
        )

        text = text.replace("Salvar Casa/Alfinete", "Minha regiao de corridas")
        text = text.replace(
            "Configure o ponto que o destino final precisa ficar perto. O Rota Certa usa este endereco e o raio em km para decidir aceitar ou recusar.",
            "Defina rapidamente o ponto onde o destino final precisa ficar perto. Use o GPS atual, ajuste o raio e salve.",
        )
        text = text.replace("Usar GPS atual", "Definir pelo GPS atual")
        text = text.replace("Salvar endereco", "Salvar regiao de trabalho")
        text = text.replace("KM da regiao de destino", "Raio da regiao de trabalho")
        text = text.replace("RadiusSlider(\n                label = \"Casa\",", "RadiusSlider(\n                label = \"Destino ate regiao\",")
        text = text.replace("RadiusSlider(\n                label = \"Alfinete\",", "RadiusSlider(\n                label = \"Destino ate alfinete\",")

        if (text != original) {
            main.writeText(text)
        }

        val service = serviceFile.asFile
        var serviceText = service.readText()
        val originalService = serviceText

        serviceText = serviceText.replace(
"""    private fun openSavedPlaceEditor(place: SavedPlace) {
        hideActionMenu()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_TOOLS)
                    .putExtra("br.com.mapeiaia.rotacerta.extra.OPEN_EXPANDER", "Alertas de proximidade")
                    .putExtra(EXTRA_SAVED_PLACE_ID, place.id),
            )
        }
    }
""",
"""    private fun openSavedPlaceEditor(place: SavedPlace) {
        hideActionMenu()
        val expander = if (place.type == SavedPlaceType.ProximityAlert) "Alertas de proximidade" else "Locais salvos"
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_TOOLS)
                    .putExtra("br.com.mapeiaia.rotacerta.extra.OPEN_EXPANDER", expander)
                    .putExtra(EXTRA_SAVED_PLACE_ID, place.id),
            )
        }
    }
""",
        )

        serviceText = serviceText.replace(
"""            addView(actionMenuItem(
                label = "📍  Salvar este local",
                action = {
                    hideActionMenu()
                    saveCurrentPlaceFromBubble(SavedPlaceType.Place)
                },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Alertas de proximidade") },
            ))
""",
"""            addView(actionMenuItem(
                label = "📍  Salvar este local",
                action = {
                    hideActionMenu()
                    saveCurrentPlaceFromBubble(SavedPlaceType.Place)
                },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Locais salvos") },
            ))
""",
        )

        if (serviceText != originalService) {
            service.writeText(serviceText)
        }
    }
}

patchUxPlacesAlertsRadars.configure {
    mustRunAfter("patchBubbleShortcutClipboard")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchUxPlacesAlertsRadars)
}
