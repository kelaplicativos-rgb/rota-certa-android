val patchBubbleShortcutClipboard by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        text = text.replace(
"""                        longPressRunnable = Runnable {
                            if (view.isPressed && longAction != null) {
                                longPressHandled = true
                                longAction.invoke()
                            }
                        }
                        view.postDelayed(longPressRunnable, 2_000L)
""",
"""                        val runnable = Runnable {
                            if (view.isPressed && longAction != null) {
                                longPressHandled = true
                                longAction.invoke()
                            }
                        }
                        longPressRunnable = runnable
                        view.postDelayed(runnable, 2_000L)
""",
        )

        if ("private fun clearClipboardFromBubble()" !in text) {
            text = text.replace(
"""    private fun openApp(tab: String? = null, expander: String? = null) {
""",
"""    private fun clearClipboardFromBubble() {
        hideActionMenu()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            }
        }.onSuccess {
            traceEvent("bubble.clear_clipboard success=true")
            toast("Area de transferencia limpa.")
        }.onFailure { error ->
            traceEvent("bubble.clear_clipboard success=false error=${dollar}{error.message.orEmpty()}")
            toast("Nao foi possivel limpar a area de transferencia.")
        }
    }

    private fun openApp(tab: String? = null, expander: String? = null) {
""",
            )
        }

        text = text.replace(
"""            addView(actionMenuItem(
                label = "💾  Salvar card de corrida",
                action = {
                    hideActionMenu()
                    saveCurrentRideCardFromBubble()
                },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Modelos de cards") },
            ))
""",
"""            addView(actionMenuItem(
                label = "💾  Salvar card de corrida",
                action = {
                    hideActionMenu()
                    saveCurrentRideCardFromBubble()
                },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Modelos de cards") },
            ))
""",
        )

        text = text.replace(
"""            addView(actionMenuItem(
                label = "💾  Salvar card de corrida",
                action = { openApp(tab = TAB_TOOLS, expander = "Modelos de cards") },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Modelos de cards") },
            ))
""",
"""            addView(actionMenuItem(
                label = "💾  Salvar card de corrida",
                action = {
                    hideActionMenu()
                    saveCurrentRideCardFromBubble()
                },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Modelos de cards") },
            ))
""",
        )

        text = text.replace(
"""            addView(actionMenuItem(
                label = "💾  Salvar card desta corrida",
                action = { openApp(tab = TAB_TOOLS, expander = "Modelos de cards") },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Modelos de cards") },
            ))
""",
"""            addView(actionMenuItem(
                label = "💾  Salvar card desta corrida",
                action = {
                    hideActionMenu()
                    saveCurrentRideCardFromBubble()
                },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Modelos de cards") },
            ))
""",
        )

        if ("Limpar area de transferencia" !in text) {
            text = text.replace(
"""            addView(actionMenuItem(
                label = "🎯  Definir região de trabalho",
                action = { openApp(tab = TAB_TOOLS, expander = "Definir regiao de trabalho") },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Definir regiao de trabalho") },
            ))
            addView(actionMenuItem(
                label = "🔔  Criar alerta de proximidade",
""",
"""            addView(actionMenuItem(
                label = "🎯  Definir região de trabalho",
                action = { openApp(tab = TAB_TOOLS, expander = "Definir regiao de trabalho") },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Definir regiao de trabalho") },
            ))
            addView(actionMenuItem(
                label = "🧹  Limpar area de transferencia",
                action = { clearClipboardFromBubble() },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Area de transferencia") },
            ))
            addView(actionMenuItem(
                label = "🔔  Criar alerta de proximidade",
""",
            )
        }

        if (text != original) {
            file.writeText(text)
        }

        val main = mainFile.asFile
        var mainText = main.readText()
        val originalMain = mainText

        if ("import androidx.compose.foundation.text.KeyboardActions" !in mainText) {
            mainText = mainText.replace(
                "import androidx.compose.foundation.rememberScrollState\n",
                "import androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.text.KeyboardActions\nimport androidx.compose.foundation.text.KeyboardOptions\n",
            )
        }
        if ("import androidx.compose.material3.AlertDialog" !in mainText) {
            mainText = mainText.replace(
                "import androidx.compose.material3.Button\n",
                "import androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.Button\n",
            )
        }
        if ("import androidx.compose.ui.focus.FocusRequester" !in mainText) {
            mainText = mainText.replace(
                "import androidx.compose.ui.Modifier\n",
                "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.focus.FocusRequester\nimport androidx.compose.ui.focus.focusRequester\n",
            )
        }
        if ("import androidx.compose.ui.text.input.ImeAction" !in mainText) {
            mainText = mainText.replace(
                "import androidx.compose.ui.text.font.FontWeight\n",
                "import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.input.ImeAction\n",
            )
        }

        if ("var savedPlaceNameDialogId by remember" !in mainText) {
            mainText = mainText.replace(
"""    var highlightedSavedPlaceId by remember { mutableStateOf<String?>(null) }
    var region by remember { mutableStateOf(DeviceRegion()) }
""",
"""    var highlightedSavedPlaceId by remember { mutableStateOf<String?>(null) }
    var savedPlaceNameDialogId by remember { mutableStateOf<String?>(null) }
    var handledSavedPlaceNameDialogId by remember { mutableStateOf<String?>(null) }
    var region by remember { mutableStateOf(DeviceRegion()) }
""",
            )
        }

        if ("SavedPlaceNameDialog(" !in mainText) {
            mainText = mainText.replace(
"""    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                liveEnabled = isLiveAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
""",
"""    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                liveEnabled = isLiveAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(highlightedSavedPlaceId, savedPlaces) {
        val id = highlightedSavedPlaceId ?: return@LaunchedEffect
        val place = savedPlaces.firstOrNull { it.id == id && it.type == SavedPlaceType.Place }
        if (place != null && handledSavedPlaceNameDialogId != id) {
            savedPlaceNameDialogId = id
        }
    }

    savedPlaces.firstOrNull { it.id == savedPlaceNameDialogId && it.type == SavedPlaceType.Place }?.let { place ->
        SavedPlaceNameDialog(
            place = place,
            onSave = { name ->
                renameSavedPlace(place, name)
                handledSavedPlaceNameDialogId = place.id
                savedPlaceNameDialogId = null
            },
            onDismiss = {
                handledSavedPlaceNameDialogId = place.id
                savedPlaceNameDialogId = null
            },
        )
    }

    Scaffold(
""",
            )
        }

        if ("private fun SavedPlaceNameDialog(" !in mainText) {
            mainText = mainText.replace(
"""@Composable
private fun SavedPlacesCard(
""",
"""@Composable
private fun SavedPlaceNameDialog(
    place: SavedPlace,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftName by remember(place.id, place.name) {
        mutableStateOf(place.name.takeUnless { it == defaultSavedPlaceName(place.type) }.orEmpty())
    }
    val focusRequester = remember { FocusRequester() }

    fun confirm() {
        onSave(draftName.trim().ifBlank { defaultSavedPlaceName(place.type) })
    }

    LaunchedEffect(place.id) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nome do local") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(place.address.ifBlank { formatCoordinate(place.coordinate) }, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    label = { Text("Digite o nome") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                )
            }
        },
        confirmButton = {
            Button(onClick = { confirm() }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun SavedPlacesCard(
""",
            )
        }

        if (mainText != originalMain) {
            main.writeText(mainText)
        }
    }
}

patchBubbleShortcutClipboard.configure {
    mustRunAfter("patchResourceGroupsCompileFix")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchBubbleShortcutClipboard)
}