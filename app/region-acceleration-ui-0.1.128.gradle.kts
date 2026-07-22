// Rota Certa 0.1.128 — interface do Acelerador de Regiao.
// Aplicado no fim do build para nao ser removido pelos patches historicos da tela.

fun patchRegionAccelerationUi128(mainFile: java.io.File) {
    if (!mainFile.exists()) throw GradleException("MainActivity.kt nao encontrado para o Acelerador de Regiao.")
    var source = mainFile.readText()
    if ("region_acceleration_ui_0_1_128" in source) return

    val analysisStart = source.indexOf("private fun AnalysisScreen(")
    val analysisEnd = if (analysisStart >= 0) source.indexOf("@Composable\nprivate fun LiveReadingCard(", analysisStart) else -1
    if (analysisStart < 0 || analysisEnd < 0) {
        throw GradleException("AnalysisScreen final nao encontrada.")
    }
    var analysis = source.substring(analysisStart, analysisEnd)

    val stateAnchor = """    val gpsAddressResolver = remember { GpsAddressResolver(context) }
    val scope = rememberCoroutineScope()

    var quickSettings by remember(settings) { mutableStateOf(settings) }
"""
    if (stateAnchor !in analysis) throw GradleException("Estado principal da tela de destino nao encontrado.")
    analysis = analysis.replaceFirst(
        stateAnchor,
        """    val gpsAddressResolver = remember { GpsAddressResolver(context) }
    val scope = rememberCoroutineScope()
    val regionAccelerationManager128 = remember { RegionAccelerationManager(context) }

    var quickSettings by remember(settings) { mutableStateOf(settings) }
    var regionAccelerationRunning128 by remember { mutableStateOf(false) }
    var regionAccelerationStatus128 by remember(settings) {
        mutableStateOf(regionAccelerationManager128.statusText(settings))
    }
    LaunchedEffect(settings) {
        regionAccelerationStatus128 = regionAccelerationManager128.statusText(settings)
    }
""",
    )

    val actionAnchor = """    fun saveQuickSettings(updated: AppSettings) {
        quickSettings = updated
        onSaveSettings(updated)
    }

    fun captureHomeGps() {
"""
    if (actionAnchor !in analysis) throw GradleException("Acao de salvar regiao nao encontrada.")
    analysis = analysis.replaceFirst(
        actionAnchor,
        """    fun saveQuickSettings(updated: AppSettings) {
        quickSettings = updated
        onSaveSettings(updated)
    }

    fun prepareRegionAcceleration128() {
        if (regionAccelerationRunning128) return
        scope.launch {
            regionAccelerationRunning128 = true
            try {
                val result128 = regionAccelerationManager128.prepare(
                    settings = quickSettings,
                    region = DeviceRegion(country = "Brasil"),
                )
                quickSettings = result128.updatedSettings
                if (result128.success) {
                    onSaveSettings(result128.updatedSettings)
                }
                regionAccelerationStatus128 = result128.message
            } catch (error128: Throwable) {
                regionAccelerationStatus128 = "Nao foi possivel preparar a regiao: " +
                    (error128.message ?: error128::class.java.simpleName)
            } finally {
                regionAccelerationRunning128 = false
            }
        }
    }

    fun clearRegionAcceleration128() {
        regionAccelerationManager128.clear()
        regionAccelerationStatus128 = "Preparo regional removido. As configuracoes de Casa e Alfinete foram mantidas."
    }

    fun captureHomeGps() {
""",
    )

    val cardAnchor = """    RadiusQuickCard(
        quickSettings = quickSettings,
        onSettingsChange = { quickSettings = it },
        onSaveSettings = onSaveSettings,
    )

    Spacer(Modifier.height(10.dp))
    // Modelos removidos; o gatilho e o ultimo endereco. // universal_models_removed_v2_0_1_95
"""
    if (cardAnchor !in analysis) throw GradleException("Cartao de raio final nao encontrado.")
    analysis = analysis.replaceFirst(
        cardAnchor,
        """    RadiusQuickCard(
        quickSettings = quickSettings,
        onSettingsChange = { quickSettings = it },
        onSaveSettings = onSaveSettings,
    )

    Spacer(Modifier.height(10.dp))
    RegionAccelerationCard128(
        status = regionAccelerationStatus128,
        running = regionAccelerationRunning128,
        onPrepare = ::prepareRegionAcceleration128,
        onClear = ::clearRegionAcceleration128,
    ) // region_acceleration_ui_0_1_128

    Spacer(Modifier.height(10.dp))
    // Modelos removidos; o gatilho e o ultimo endereco. // universal_models_removed_v2_0_1_95
""",
    )

    source = source.substring(0, analysisStart) + analysis + source.substring(analysisEnd)

    val composableAnchor = """@Composable
private fun CardModelsCard(
"""
    if (composableAnchor !in source) throw GradleException("Ponto para inserir o cartao regional nao encontrado.")
    source = source.replaceFirst(
        composableAnchor,
        """@Composable
private fun RegionAccelerationCard128(
    status: String,
    running: Boolean,
    onPrepare: () -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Resposta rapida da regiao", fontWeight = FontWeight.Bold)
            Text(
                "Prepara as coordenadas de Casa e Alfinete, valida a conexao de rota e cria um perfil local do raio. Nao baixa um mapa pesado.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onPrepare,
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (running) "Preparando regiao..." else "Preparar regiao para resposta rapida")
            }
            OutlinedButton(
                onClick = onClear,
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Limpar preparo regional")
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
            Text(
                "Validade: 14 dias. O preparo e invalidado automaticamente quando o endereco ou o raio mudar.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
} // region_acceleration_card_0_1_128

@Composable
private fun CardModelsCard(
""",
    )

    listOf(
        "region_acceleration_ui_0_1_128",
        "region_acceleration_card_0_1_128",
        "Preparar regiao para resposta rapida",
        "RegionAccelerationManager(context)",
        "finally {\n                regionAccelerationRunning128 = false",
    ).forEach { marker ->
        if (marker !in source) throw GradleException("Interface regional incompleta: $marker")
    }
    mainFile.writeText(source)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchRegionAccelerationUi128(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
