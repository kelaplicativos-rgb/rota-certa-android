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
                    .putExtra(EXTRA_OPEN_TAB, TAB_ANALYSIS),
            )
        }.onFailure {
            toast("Nao consegui abrir a regiao do farol agora.")
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

        if ("ToolsScreenOperationalPlaces" !in mainText) {
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
        // ToolsScreenOperationalPlaces
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
                "            Text(\"Endereco para aceitar corridas\", fontWeight = FontWeight.Bold)\n",
                "            Text(\"Salvar Casa/Alfinete\", fontWeight = FontWeight.Bold)\n",
            )
            mainText = mainText.replace(
                "            Text(\"Raio de aceite\", fontWeight = FontWeight.Bold)\n",
                "            Text(\"KM da regiao de destino\", fontWeight = FontWeight.Bold)\n",
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
