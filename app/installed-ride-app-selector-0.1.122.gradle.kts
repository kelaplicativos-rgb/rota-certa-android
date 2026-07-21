val installedRideAppSelector122Patch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    val manifestFile = layout.projectDirectory.file("src/main/AndroidManifest.xml")
    inputs.files(serviceFile, mainFile, manifestFile)
    outputs.upToDateWhen { false }

    doLast {
        val service = serviceFile.asFile
        var serviceText = service.readText()
        val originalService = serviceText

        if ("private val accessibilityEventFloodGate = AccessibilityEventFloodGate()" !in serviceText) {
            serviceText = serviceText.replace(
                "    private val registeredCardGate = RegisteredCardDecisionGate()\n",
                "    private val registeredCardGate = RegisteredCardDecisionGate()\n" +
                    "    private val accessibilityEventFloodGate = AccessibilityEventFloodGate()\n" +
                    "    private val importedRadarSpatialIndex = ImportedRadarSpatialIndex()\n",
            )
        }

        if ("selected_apps_event_gate_0_1_122" !in serviceText) {
            val anchor = "        val packageName = eventPackageName ?: currentRootPackageName()\n"
            if (anchor !in serviceText) {
                throw org.gradle.api.GradleException("Nao encontrei o ponto de entrada dos eventos da acessibilidade para aplicar o seletor de apps.")
            }
            serviceText = serviceText.replace(
                anchor,
                anchor +
                    "        if (!AccessibilityEventFloodGate.isRelevantEventType(event.eventType)) return\n" +
                    "        if (!shouldScanPackage(packageName)) {\n" +
                    "            if (currentRadarColor != RadarColor.Idle) {\n" +
                    "                resetToIdle(\"Aplicativo fora da selecao do usuario; bolinha em espera.\", record = false)\n" +
                    "            }\n" +
                    "            return\n" +
                    "        }\n" +
                    "        if (accessibilityEventFloodGate.classify(\n" +
                    "                packageName = packageName,\n" +
                    "                eventType = event.eventType,\n" +
                    "                monitoredPackage = true,\n" +
                    "            ) == AccessibilityEventMode.Ignore\n" +
                    "        ) return // selected_apps_event_gate_0_1_122\n",
            )
        }

        if ("selected_apps_scan_loop_0_1_122" !in serviceText) {
            val loopAnchor = "                val packageName = currentWindowPackageName()\n"
            if (loopAnchor !in serviceText) {
                throw org.gradle.api.GradleException("Nao encontrei o ciclo continuo da leitura para limitar aos apps selecionados.")
            }
            serviceText = serviceText.replace(
                loopAnchor,
                loopAnchor +
                    "                if (!shouldScanPackage(packageName)) {\n" +
                    "                    if (currentRadarColor != RadarColor.Idle) {\n" +
                    "                        resetToIdle(\"Aplicativo fora da selecao; leitura pausada.\", record = false)\n" +
                    "                    }\n" +
                    "                    delay(SCAN_LOOP_MS)\n" +
                    "                    continue // selected_apps_scan_loop_0_1_122\n" +
                    "                }\n",
            )
        }

        val selectedPackagesPattern = Regex(
            """    private fun selectedRidePackages\(settings: AppSettings\): Set<String>.*?(?=    private fun scanBlockReason\()""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        if (!selectedPackagesPattern.containsMatchIn(serviceText)) {
            throw org.gradle.api.GradleException("Nao encontrei selectedRidePackages para conectar a selecao dos apps instalados.")
        }
        serviceText = serviceText.replace(
            selectedPackagesPattern,
            """    private fun selectedRidePackages(settings: AppSettings): Set<String> =
        SelectedRideAppStore.selectedPackages(applicationContext, settings) // selected_apps_store_0_1_122

""",
        )

        if ("radar_spatial_index_0_1_122" !in serviceText) {
            val coordinateAnchor = "        val coordinate = locationService.currentCoordinate() ?: return\n"
            if (coordinateAnchor !in serviceText) {
                throw org.gradle.api.GradleException("Nao encontrei a coordenada do monitor de radares para aplicar o indice espacial.")
            }
            serviceText = serviceText.replace(
                coordinateAnchor,
                coordinateAnchor +
                    "        val radarSearchRadiusMeters = currentSettings.proximityAlertDistanceMeters.coerceAtLeast(200) + 1_000.0\n" +
                    "        val nearbyRadarQuery = importedRadarSpatialIndex.query(\n" +
                    "            source = radars,\n" +
                    "            center = coordinate,\n" +
                    "            radiusMeters = radarSearchRadiusMeters,\n" +
                    "        ) // radar_spatial_index_0_1_122\n" +
                    "        val nearbyRadars = nearbyRadarQuery.radars\n",
            )
            serviceText = serviceText.replace(
                "            radars = radars,\n            coordinate = coordinate,\n",
                "            radars = nearbyRadars,\n            coordinate = coordinate,\n",
            )
        }

        if (serviceText != originalService) service.writeText(serviceText)

        val main = mainFile.asFile
        var mainText = main.readText()
        val originalMain = mainText
        mainText = mainText.replace(
            "        MonitoredAppsCard(settings = draft, onChange = ::saveDraft)\n",
            "        InstalledRideAppsCard()\n",
        )
        mainText = mainText.replace(
            "        MonitoredAppsCard(cardTemplates = cardTemplates)\n",
            "        InstalledRideAppsCard()\n",
        )

        if ("private fun InstalledRideAppsCard()" !in mainText) {
            val functionAnchor = "@Composable\nprivate fun ExpandableCard(\n"
            if (functionAnchor !in mainText) {
                throw org.gradle.api.GradleException("Nao encontrei o ponto da tela Configuracoes para incluir o seletor de aplicativos.")
            }
            val installedAppsCard = """@Composable
private fun InstalledRideAppsCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedPackages by remember { mutableStateOf(SelectedRideAppStore.read(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                selectedPackages = SelectedRideAppStore.read(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ExpandableCard(title = "Aplicativos de corrida", initiallyExpanded = true) {
        Text(
            "Escolha diretamente entre os aplicativos instalados no celular. Acessibilidade, OCR e leitura de cards funcionarao somente nos aplicativos selecionados.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { context.startActivity(Intent(context, InstalledRideAppPickerActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Buscar aplicativos instalados")
        }
        if (selectedPackages.isEmpty()) {
            Text(
                "Nenhum aplicativo selecionado. A leitura da bolinha fica pausada ate voce escolher pelo menos um app de corrida.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text("Aplicativos selecionados: ${'$'}{selectedPackages.size}", fontWeight = FontWeight.Bold)
            selectedPackages.forEach { packageName ->
                Text(packageName, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Para remover ou adicionar aplicativos, abra novamente a lista instalada.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

"""
            mainText = mainText.replace(functionAnchor, installedAppsCard + functionAnchor)
        }

        if (mainText != originalMain) main.writeText(mainText)

        val manifestText = manifestFile.asFile.readText()
        if (".InstalledRideAppPickerActivity" !in manifestText || "android.intent.category.LAUNCHER" !in manifestText) {
            throw org.gradle.api.GradleException("InstalledRideAppPickerActivity ou consulta de apps instalados ausente do Manifest.")
        }
    }
}

val installedRideAppSelector122Predecessors = setOf(
    "enforceUserRegisteredPackagesOnly",
    "universalLastAddressFinalV2",
    "universalLastAddressCompileFix",
    "universalTwoAddressRuntimeFinal",
    "popupNavigationLateCompile120",
    "workTrackingCardAnchorCompat121",
    "radarWorkTracking121",
)

tasks.matching { it.name in installedRideAppSelector122Predecessors }.configureEach {
    val predecessor = this
    installedRideAppSelector122Patch.configure {
        mustRunAfter(predecessor)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(installedRideAppSelector122Patch)
}
