val patchManualSupportReport by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = mainFile.asFile
        var text = file.readText()
        val original = text

        if ("var supportReportStatus by remember" !in text) {
            text = text.replace(
                "    var radarImportStatus by remember { mutableStateOf(\"\") }\n",
                "    var radarImportStatus by remember { mutableStateOf(\"\") }\n    var supportReportStatus by remember { mutableStateOf(\"\") }\n",
            )
        }

        if ("val supportReportFileCreator = rememberLauncherForActivityResult" !in text) {
            text = text.replace(
                """    val backupFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
""",
                """    val supportReportFileCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) {
            supportReportStatus = "Relatorio cancelado."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            supportReportStatus = "Gerando relatorio..."
            runCatching {
                val report = buildManualSupportReport(
                    context = context,
                    repository = repository,
                    settings = settings,
                    liveEnabled = liveEnabled,
                    cardTemplates = cardTemplates,
                    savedPlaces = savedPlaces,
                    radarImportSummary = radarImportSummary,
                )
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(report)
                } ?: error("Nao consegui abrir o arquivo do relatorio.")
            }.onSuccess {
                supportReportStatus = "Relatorio gerado. Anexe o arquivo aqui no chat."
                Toast.makeText(context, "Relatorio gerado para anexar.", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                supportReportStatus = "Falha ao gerar relatorio: ${'$'}{error.message.orEmpty()}"
            }
        }
    }

    val backupFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
""",
            )
        }

        if ("onCreateSupportReport = { supportReportFileCreator.launch" !in text) {
            text = text.replace(
                "                    onClearClipboard = { clearClipboard(context) },\n",
                "                    onClearClipboard = { clearClipboard(context) },\n                    onCreateSupportReport = { supportReportFileCreator.launch(\"rota-certa-relatorio-falha.txt\") },\n                    supportReportStatus = supportReportStatus,\n",
            )
        }

        if ("onCreateSupportReport: () -> Unit" !in text) {
            text = text.replace(
                "    onClearClipboard: () -> Unit,\n",
                "    onClearClipboard: () -> Unit,\n    onCreateSupportReport: () -> Unit,\n    supportReportStatus: String,\n",
            )
        }

        if ("Text(\"Gerar relatorio para anexar\")" !in text) {
            text = text.replace(
                "                Text(\"Area de transferencia\", fontWeight = FontWeight.Bold)\n",
                """                Text("Relatorio manual de falha", fontWeight = FontWeight.Bold)
                Text(
                    "Gera um arquivo leve somente quando voce tocar aqui. A bolinha continua sem logs automaticos.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onCreateSupportReport, modifier = Modifier.fillMaxWidth()) {
                    Text("Gerar relatorio para anexar")
                }
                if (supportReportStatus.isNotBlank()) Text(supportReportStatus, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Text("Area de transferencia", fontWeight = FontWeight.Bold)
""",
            )
        }

        if ("private suspend fun buildManualSupportReport(" !in text) {
            text = text.replace(
                """private fun clearClipboard(context: Context) {
""",
                """private suspend fun buildManualSupportReport(
    context: Context,
    repository: SettingsRepository,
    settings: AppSettings,
    liveEnabled: Boolean,
    cardTemplates: List<RideCardTemplate>,
    savedPlaces: List<SavedPlace>,
    radarImportSummary: RadarImportSummary,
): String {
    val backupJson = runCatching { repository.exportBackupJson() }
        .getOrElse { error -> "Falha ao exportar backup interno: ${'$'}{error::class.java.simpleName}: ${'$'}{error.message.orEmpty()}" }
    val selectedPackages = buildList {
        if (settings.monitor99) add("com.app99.driver")
        if (settings.monitorUber) add("com.ubercab.driver")
        if (settings.monitorInDrive) add("sinet.startup.indriver")
        settings.extraMonitoredPackages
            .split(Regex("[,;\\s]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { add(it) }
    }

    return buildString {
        appendLine("ROTA CERTA RELATORIO MANUAL")
        appendLine("Gerado apenas por clique do usuario; nao roda na bolinha.")
        appendLine("Versao: ${'$'}{BuildConfig.VERSION_NAME} (${'$'}{BuildConfig.VERSION_CODE})")
        appendLine("Data: ${'$'}{formatDate(System.currentTimeMillis())}")
        appendLine("Pacote: ${'$'}{context.packageName}")
        appendLine("Leitura ao vivo ativa: ${'$'}liveEnabled")
        appendLine()
        appendLine("--- CONFIGURACOES PRINCIPAIS ---")
        appendLine("Rota Certa ligado: ${'$'}{settings.appEnabled}")
        appendLine("Alertas proximidade ligados: ${'$'}{settings.proximityAlertsEnabled}")
        appendLine("Modo restrito apps selecionados: ${'$'}{settings.restrictToSelectedRideApps}")
        appendLine("Pacotes monitorados: ${'$'}{selectedPackages.joinToString(", ").ifBlank { "nenhum" }}")
        appendLine("Casa/ponto principal: ${'$'}{settings.homeAddress.ifBlank { "nao informado" }}")
        appendLine("Raio casa: ${'$'}{formatKm(settings.homeRadiusKm)}")
        appendLine("Alfinete/local alternativo: ${'$'}{settings.alternativeAddress.ifBlank { "nao informado" }}")
        appendLine("Raio alfinete: ${'$'}{formatKm(settings.alternativeRadiusKm)}")
        appendLine("Google Maps API configurada: ${'$'}{settings.googleMapsApiKey.isNotBlank()}")
        appendLine("Distancia alerta proximidade: ${'$'}{settings.proximityAlertDistanceMeters} m")
        appendLine()
        appendLine("--- MODELOS DE CARDS ---")
        appendLine("Total: ${'$'}{cardTemplates.size}")
        cardTemplates.forEachIndexed { index, template ->
            appendLine("${'$'}{index + 1}. nome=${'$'}{template.name}; pacote=${'$'}{template.packageName ?: "nao identificado"}; hash=${'$'}{template.sampleHash}; recursos=${'$'}{template.requiredFeatures.joinToString("|")}")
        }
        appendLine()
        appendLine("--- LOCAIS E ALERTAS ---")
        appendLine("Total: ${'$'}{savedPlaces.size}")
        savedPlaces.forEachIndexed { index, place ->
            appendLine("${'$'}{index + 1}. tipo=${'$'}{place.type}; nome=${'$'}{place.name}; endereco=${'$'}{place.address}; coordenada=${'$'}{formatCoordinate(place.coordinate)}; distanciaAlerta=${'$'}{place.alertDistanceMeters ?: 0}")
        }
        appendLine()
        appendLine("--- RADARES IMPORTADOS ---")
        appendLine(radarImportSummary.toString())
        appendLine()
        appendLine("--- BACKUP INTERNO ---")
        appendLine(backupJson)
    }
}

private fun clearClipboard(context: Context) {
""",
            )
        }

        if (text != original) file.writeText(text)
    }
}

patchManualSupportReport.configure {
    mustRunAfter("patchRemoveLiveDiagnostics")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchManualSupportReport)
}
