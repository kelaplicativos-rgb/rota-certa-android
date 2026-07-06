val patchBubbleCardParity by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = mainFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("val gpsAddressResolver = remember { GpsAddressResolver(context) }" !in text) {
            text = text.replace(
                "    val geocodingService = remember { GeocodingService(context) }\n",
                "    val geocodingService = remember { GeocodingService(context) }\n    val gpsAddressResolver = remember { GpsAddressResolver(context) }\n",
            )
        }

        if ("fun createCurrentSavedPlace(type: SavedPlaceType)" !in text) {
            text = text.replace(
"""    fun renameSavedPlace(place: SavedPlace, name: String) {
        val safeName = name.trim().ifBlank { defaultSavedPlaceName(place.type) }
        scope.launch {
            repository.updateSavedPlace(place.copy(name = safeName))
            Toast.makeText(context, "Nome salvo: ${dollar}safeName", Toast.LENGTH_SHORT).show()
        }
    }
""",
"""    fun renameSavedPlace(place: SavedPlace, name: String) {
        val safeName = name.trim().ifBlank { defaultSavedPlaceName(place.type) }
        scope.launch {
            repository.updateSavedPlace(place.copy(name = safeName))
            Toast.makeText(context, "Nome salvo: ${dollar}safeName", Toast.LENGTH_SHORT).show()
        }
    }

    fun createCurrentSavedPlace(type: SavedPlaceType) {
        scope.launch {
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                Toast.makeText(context, "Autorize a localizacao para salvar este local.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val resolved = gpsAddressResolver.resolve(coordinate)
            val createdAt = System.currentTimeMillis()
            val isAlert = type == SavedPlaceType.ProximityAlert
            val place = SavedPlace(
                id = "place-${dollar}createdAt-${dollar}{coordinate.latitude}-${dollar}{coordinate.longitude}",
                name = if (isAlert) defaultSavedPlaceName(SavedPlaceType.ProximityAlert) else defaultSavedPlaceName(SavedPlaceType.Place),
                type = type,
                address = resolved.addressLine,
                coordinate = coordinate,
                alertDistanceMeters = if (isAlert) settings.proximityAlertDistanceMeters else null,
                createdAtMillis = createdAt,
            )
            repository.addSavedPlace(place)
            highlightedSavedPlaceId = place.id
            savedPlaceNameDialogId = place.id
            Toast.makeText(
                context,
                if (isAlert) "Alerta criado. Informe o nome." else "Local salvo. Informe o nome.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
""",
            )
        }

        if ("onSaveCurrentPlace = { createCurrentSavedPlace(SavedPlaceType.Place) }" !in text) {
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
                    onSaveCurrentPlace = { createCurrentSavedPlace(SavedPlaceType.Place) },
                    onCreateProximityAlert = { createCurrentSavedPlace(SavedPlaceType.ProximityAlert) },
                )
""",
            )
        }

        if ("onSaveCurrentPlace: () -> Unit" !in text) {
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

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchBubbleCardParity.configure {
    mustRunAfter("patchLiveReadingCardRestore")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchBubbleCardParity)
}
