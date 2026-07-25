// Checklist 9 — integração final antes do build: popup acessível e alvos pré-resolvidos.

fun replaceIntegrationFunctionChecklist9(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Função ausente no checklist 9: $signature")
    val open = source.indexOf('{', start)
    if (open < 0) throw GradleException("Corpo ausente no checklist 9: $signature")
    var depth = 0
    var index = open
    while (index < source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return source.substring(0, start) + replacement + source.substring(index + 1)
            }
        }
        index += 1
    }
    throw GradleException("Fim da função ausente no checklist 9: $signature")
}

fun patchHomeTargetPreResolutionChecklist9(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt ausente no checklist 9.")
    var main = file.readText()
    if ("home_target_pre_resolved_checklist_9" in main) return

    val resolverAnchor = "    val gpsAddressResolver = remember { GpsAddressResolver(context) }\n"
    if (resolverAnchor !in main) throw GradleException("Resolvedor GPS da Casa ausente no checklist 9.")
    main = main.replaceFirst(
        resolverAnchor,
        resolverAnchor + "    val workRegionAddressResolverChecklist9 = remember { WorkRegionAddressResolver(context) }\n",
    )

    val stateAnchor = "    var pendingHomeGps by remember { mutableStateOf(false) }\n"
    if (stateAnchor !in main) throw GradleException("Estado da Casa ausente no checklist 9.")
    main = main.replaceFirst(
        stateAnchor,
        stateAnchor + "    var savingHomeAddressChecklist9 by remember { mutableStateOf(false) }\n",
    )

    val permissionAnchor = "    val homeGpsPermissionLauncher = rememberLauncherForActivityResult(\n"
    if (permissionAnchor !in main) throw GradleException("Permissão GPS da Casa ausente no checklist 9.")
    val saveHelper = """    fun saveHomeAddressValidatedChecklist9() {
        val address = quickSettings.homeAddress.trim()
        if (address.isBlank()) {
            homeStatus = "Informe o endereço da Casa antes de salvar."
            return
        }
        if (savingHomeAddressChecklist9) return
        if (quickSettings.homeCoordinate != null) {
            saveQuickSettings(quickSettings.copy(homeAddress = address))
            homeStatus = "Casa salva e pronta para o farol."
            return
        }

        savingHomeAddressChecklist9 = true
        homeStatus = "Localizando e validando o endereço da Casa..."
        scope.launch {
            val coordinate = runCatching {
                workRegionAddressResolverChecklist9.resolve(address, quickSettings.googleMapsApiKey)
            }.getOrNull()
            if (coordinate == null) {
                homeStatus = "Não consegui localizar a Casa. Inclua número, cidade e estado."
            } else {
                val updated = quickSettings.copy(
                    homeAddress = address,
                    homeCoordinate = coordinate,
                )
                saveQuickSettings(updated)
                homeStatus = "Casa salva e pronta para o farol: ${'$'}{formatCoordinate(coordinate)}"
            }
            savingHomeAddressChecklist9 = false
        }
    } // home_target_pre_resolved_checklist_9

"""
    main = main.replaceFirst(permissionAnchor, saveHelper + permissionAnchor)

    val callOld = """        onRequestHomeGps = ::requestHomeGps,
        onSave = { saveQuickSettings(quickSettings) },
"""
    val callNew = """        onRequestHomeGps = ::requestHomeGps,
        saving = savingHomeAddressChecklist9,
        onSave = ::saveHomeAddressValidatedChecklist9,
"""
    if (callOld !in main) throw GradleException("Chamada da Casa ausente no checklist 9.")
    main = main.replaceFirst(callOld, callNew)

    val homeCard = """@Composable
private fun HomeDecisionCard(
    quickSettings: AppSettings,
    homeStatus: String,
    onSettingsChange: (AppSettings) -> Unit,
    onRequestHomeGps: () -> Unit,
    saving: Boolean,
    onSave: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Casa / ponto principal", fontWeight = FontWeight.Bold)
            Text(
                "O endereço é validado ao salvar. Assim, o farol não precisa localizar a Casa quando a corrida aparece.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = quickSettings.homeAddress,
                onValueChange = { onSettingsChange(quickSettings.copy(homeAddress = it, homeCoordinate = null)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endereço completo da Casa") },
                singleLine = true,
                enabled = !saving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onSave()
                    },
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onRequestHomeGps,
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Usar GPS atual")
                }
                OutlinedButton(
                    onClick = { onSettingsChange(quickSettings.copy(homeCoordinate = null)) },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Digitar")
                }
            }
            quickSettings.homeCoordinate?.let {
                Text("Coordenada validada: ${'$'}{formatCoordinate(it)}", style = MaterialTheme.typography.bodySmall)
            }
            if (homeStatus.isNotBlank()) {
                Text(homeStatus, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSave()
                },
                enabled = quickSettings.homeAddress.isNotBlank() && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Localizando..." else "Salvar Casa")
            }
        }
    }
} // home_target_editor_final_checklist_9
"""
    main = replaceIntegrationFunctionChecklist9(main, "private fun HomeDecisionCard(", homeCard)

    listOf(
        "home_target_pre_resolved_checklist_9",
        "home_target_editor_final_checklist_9",
        "WorkRegionAddressResolver(context)",
        "KeyboardActions(",
        "Salvar Casa",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Integração da Casa incompleta: $marker")
    }
    file.writeText(main)
}

fun verifyScrollablePopupChecklist9(file: java.io.File) {
    if (!file.exists()) throw GradleException("BubbleShortcutOverlayController.kt ausente no checklist 9.")
    val overlay = file.readText()
    listOf(
        "import android.widget.ScrollView",
        "import android.view.ViewGroup",
        "maxMenuHeight",
        "visibleMenuHeight",
        "needsVerticalScroll",
        "View.OVER_SCROLL_IF_CONTENT_SCROLLS",
        "ViewGroup.LayoutParams(",
    ).forEach { marker ->
        if (marker !in overlay) throw GradleException("Popup grande sem rolagem segura: $marker")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        val root = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile
        patchHomeTargetPreResolutionChecklist9(java.io.File(root, "MainActivity.kt"))
        verifyScrollablePopupChecklist9(java.io.File(root, "BubbleShortcutOverlayController.kt"))
    }
}
