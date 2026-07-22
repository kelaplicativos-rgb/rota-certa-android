// Rota Certa 0.1.118
// - organiza a Home por logica operacional;
// - remove as categorias principais Leitura e Ferramentas;
// - move Leitura para Permissoes;
// - promove WhatsApp, Coletor, Limpar e Depurar para bolinhas de acao;
// - adiciona Encerrar, que pausa tudo e abre os detalhes do aplicativo;
// - amplia o relatorio manual para a linha do tempo completa mantida em memoria.

fun professional118ReplaceRegion(
    source: String,
    startToken: String,
    endToken: String,
    replacement: String,
    label: String,
): String {
    val start = source.indexOf(startToken)
    val end = if (start >= 0) source.indexOf(endToken, start + startToken.length) else -1
    if (start < 0 || end <= start) throw GradleException("Regiao ausente para $label")
    return source.substring(0, start) + replacement + source.substring(end)
}

fun enforceProfessionalBubbleHome118(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado.")
    var text = file.readText()

    if ("professional_bubble_home_0_1_118" in text) {
        listOf(
            "Text(\"Operacao\"",
            "Text(\"Sistema\"",
            "Text(\"Acoes rapidas\"",
            "Text(\"Suporte\"",
            "label = \"WhatsApp\"",
            "label = \"Coletor\"",
            "label = \"Limpar\"",
            "label = \"Depurar\"",
            "label = \"Encerrar\"",
            "BUBBLE_GROUP_ACCESS -> {",
            "LiveReadingCard(",
            "DiagnosticLogStore.dump()",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Home profissional incompleta: $marker")
        }
        if ("\"Ferramentas\" to BUBBLE_GROUP_TOOLS" in text) throw GradleException("Ferramentas voltou para a grade principal.")
        if ("\"Leitura\" to BUBBLE_GROUP_READING" in text) throw GradleException("Leitura voltou para a grade principal.")
        return
    }

    if ("supportReportFileCreator" !in text) {
        throw GradleException("Exportador de diagnostico nao encontrado para a bolinha Depurar.")
    }

    text = text.replace("            TAB_TOOLS -> BUBBLE_GROUP_TOOLS\n", "            TAB_TOOLS -> BUBBLE_GROUP_GENERAL\n")

    val dashboardCallStart = text.indexOf("            UnifiedAppControlBubbles(")
    val dashboardCallEnd = if (dashboardCallStart >= 0) text.indexOf("\n\n            when (tab) {", dashboardCallStart) else -1
    if (dashboardCallStart < 0 || dashboardCallEnd <= dashboardCallStart) {
        throw GradleException("Chamada da Central de bolinhas nao encontrada.")
    }
    val dashboardCall = """            ProfessionalBubbleDashboard(
                selectedGroup = selectedBubbleGroup,
                onSelectGroup = { group ->
                    selectedBubbleGroup = group
                    tab = when (group) {
                        BUBBLE_GROUP_DESTINATION -> TAB_ANALYSIS
                        BUBBLE_GROUP_REPORTS -> TAB_HISTORY
                        else -> TAB_CONFIG
                    }
                    DiagnosticLogStore.record("ui_action", "home.group selected=" + group)
                },
                onOpenWhatsApp = {
                    DiagnosticLogStore.record("ui_action", "home.action whatsapp")
                    openWhatsAppApp(context)
                },
                onOpenCollector = {
                    DiagnosticLogStore.record("ui_action", "home.action collector")
                    context.startActivity(Intent(context, BlaBlaCarCollectorActivity::class.java))
                },
                onClearClipboard = {
                    DiagnosticLogStore.record("ui_action", "home.action clear_clipboard")
                    clearClipboard(context)
                    Toast.makeText(context, "Area de transferencia limpa.", Toast.LENGTH_SHORT).show()
                },
                onCreateSupportReport = {
                    DiagnosticLogStore.record("ui_action", "home.action debug_report")
                    supportReportFileCreator.launch("rota-certa-relatorio-completo.txt")
                },
                onStopApplication = {
                    DiagnosticLogStore.record("ui_action", "home.action stop_application")
                    val stopped = settings.copy(
                        appEnabled = false,
                        liveReadingEnabled = false,
                        proximityAlertsEnabled = false,
                    )
                    scope.launch { repository.saveSettings(stopped) }
                    Toast.makeText(
                        context,
                        "Rota Certa pausado. Confirme Forcar interrupcao para encerrar totalmente.",
                        Toast.LENGTH_LONG,
                    ).show()
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)),
                        )
                    }.onFailure {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
                    }
                },
            ) // professional_bubble_home_0_1_118"""
    text = text.substring(0, dashboardCallStart) + dashboardCall + text.substring(dashboardCallEnd)

    text = professional118ReplaceRegion(
        source = text,
        startToken = "private const val BUBBLE_GROUP_GENERAL = \"general\"\n",
        endToken = "@Composable\nprivate fun AnalysisScreen(",
        replacement = """private const val BUBBLE_GROUP_GENERAL = "general"
private const val BUBBLE_GROUP_READING = "reading"
private const val BUBBLE_GROUP_DESTINATION = "destination"
private const val BUBBLE_GROUP_ALERTS = "alerts"
private const val BUBBLE_GROUP_APPEARANCE = "appearance"
private const val BUBBLE_GROUP_ACCESS = "access"
private const val BUBBLE_GROUP_REPORTS = "reports"
private const val BUBBLE_GROUP_BACKUP = "backup"
private const val BUBBLE_GROUP_TOOLS = "tools"
private const val EXTRA_OPEN_BUBBLE_GROUP = "open_bubble_group"
private val BUBBLE_GROUP_VALUES = setOf(
    BUBBLE_GROUP_GENERAL,
    BUBBLE_GROUP_DESTINATION,
    BUBBLE_GROUP_ALERTS,
    BUBBLE_GROUP_APPEARANCE,
    BUBBLE_GROUP_ACCESS,
    BUBBLE_GROUP_REPORTS,
    BUBBLE_GROUP_BACKUP,
)

@Composable
private fun ProfessionalBubbleDashboard(
    selectedGroup: String,
    onSelectGroup: (String) -> Unit,
    onOpenWhatsApp: () -> Unit,
    onOpenCollector: () -> Unit,
    onClearClipboard: () -> Unit,
    onCreateSupportReport: () -> Unit,
    onStopApplication: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Central de controle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Recursos separados por funcao. As bolinhas de grupo mostram seus controles logo abaixo; as bolinhas de acao executam imediatamente.",
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Operacao", fontWeight = FontWeight.Bold)
        ProfessionalBubbleRow(
            items = listOf(
                ProfessionalBubbleItem("⚡", "Rota", selectedGroup == BUBBLE_GROUP_GENERAL) { onSelectGroup(BUBBLE_GROUP_GENERAL) },
                ProfessionalBubbleItem("🏠", "Destino", selectedGroup == BUBBLE_GROUP_DESTINATION) { onSelectGroup(BUBBLE_GROUP_DESTINATION) },
                ProfessionalBubbleItem("⚠️", "Alertas", selectedGroup == BUBBLE_GROUP_ALERTS) { onSelectGroup(BUBBLE_GROUP_ALERTS) },
            ),
        )

        Text("Sistema", fontWeight = FontWeight.Bold)
        ProfessionalBubbleRow(
            items = listOf(
                ProfessionalBubbleItem("🎨", "Aparencia", selectedGroup == BUBBLE_GROUP_APPEARANCE) { onSelectGroup(BUBBLE_GROUP_APPEARANCE) },
                ProfessionalBubbleItem("🔐", "Permissoes", selectedGroup == BUBBLE_GROUP_ACCESS) { onSelectGroup(BUBBLE_GROUP_ACCESS) },
                ProfessionalBubbleItem("💾", "Backup", selectedGroup == BUBBLE_GROUP_BACKUP) { onSelectGroup(BUBBLE_GROUP_BACKUP) },
            ),
        )

        Text("Registros", fontWeight = FontWeight.Bold)
        ProfessionalBubbleRow(
            items = listOf(
                ProfessionalBubbleItem("📋", "Relatorios", selectedGroup == BUBBLE_GROUP_REPORTS) { onSelectGroup(BUBBLE_GROUP_REPORTS) },
            ),
        )

        Text("Acoes rapidas", fontWeight = FontWeight.Bold)
        ProfessionalBubbleRow(
            items = listOf(
                ProfessionalBubbleItem("🟢", "WhatsApp", false, onOpenWhatsApp),
                ProfessionalBubbleItem("🚗", "Coletor", false, onOpenCollector),
                ProfessionalBubbleItem("🧹", "Limpar", false, onClearClipboard),
            ),
        )

        Text("Suporte", fontWeight = FontWeight.Bold)
        ProfessionalBubbleRow(
            items = listOf(
                ProfessionalBubbleItem("🛠️", "Depurar", false, onCreateSupportReport),
                ProfessionalBubbleItem("⏹️", "Encerrar", false, onStopApplication),
            ),
        )
    }
}

private data class ProfessionalBubbleItem(
    val emoji: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun ProfessionalBubbleRow(items: List<ProfessionalBubbleItem>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        items.forEach { item ->
            AppControlBubble(
                emoji = item.emoji,
                label = item.label,
                selected = item.selected,
                onClick = item.onClick,
            )
        }
    }
}

@Composable
private fun AppControlBubble(
    emoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(88.dp),
        shape = CircleShape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (selected) 12.dp else 2.dp,
            pressedElevation = 14.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(5.dp),
    ) {
        Text(
            text = emoji + "\n" + label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

private fun openWhatsAppApp(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
        ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
    if (launchIntent != null) {
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } else {
        Toast.makeText(context, "WhatsApp nao encontrado.", Toast.LENGTH_SHORT).show()
    }
}

""",
        label = "painel profissional",
    )

    val readingCase = """            BUBBLE_GROUP_READING -> LiveReadingCard(
                liveEnabled = liveEnabled,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onRefreshLiveState = onRefreshLiveState,
            )
"""
    text = text.replace(readingCase, "")

    val accessCase = """            BUBBLE_GROUP_ACCESS -> AlwaysLocationPermissionCard(
                hasAlwaysPermission = hasAlwaysLocationPermission(context),
                onOpenLocationSettings = { openAppLocationSettings(context) },
            )
"""
    val mergedAccessCase = """            BUBBLE_GROUP_ACCESS -> {
                LiveReadingCard(
                    liveEnabled = liveEnabled,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onRefreshLiveState = onRefreshLiveState,
                )
                Spacer(Modifier.height(10.dp))
                AlwaysLocationPermissionCard(
                    hasAlwaysPermission = hasAlwaysLocationPermission(context),
                    onOpenLocationSettings = { openAppLocationSettings(context) },
                )
            }
"""
    if (accessCase !in text) throw GradleException("Grupo Permissoes nao encontrado para receber Leitura.")
    text = text.replaceFirst(accessCase, mergedAccessCase)

    text = text
        .replace("    BUBBLE_GROUP_ACCESS -> \"Permissoes e GPS continuo\"", "    BUBBLE_GROUP_ACCESS -> \"Permissoes, leitura e GPS\"")
        .replace(
            "    BUBBLE_GROUP_ACCESS -> \"Configure a permissao de localizacao para funcionamento em movimento.\"",
            "    BUBBLE_GROUP_ACCESS -> \"Controle a leitura ao vivo, a Acessibilidade, a localizacao e o GPS continuo.\"",
        )

    // O diagnostico exportado passa a incluir todos os eventos globais ainda
    // retidos em memoria, e nao somente os ultimos 120.
    text = text
        .replace("val complementaryEvents = DiagnosticLogStore.dump(120)", "val complementaryEvents = DiagnosticLogStore.dump()")
        .replace("--- EVENTOS GLOBAIS COMPLEMENTARES ---", "--- LINHA DO TEMPO COMPLETA DA EXECUCAO ---")
        .replace(
            "O relatorio nao inclui backup nem historico inteiro. Ele preserva a tentativa mais recente, textos de acessibilidade/OCR, enderecos, geocodificacao, rota, descarte e cor final.",
            "O relatorio registra a linha do tempo mantida em memoria desde o inicio da execucao, alem da tentativa detalhada de leitura, OCR, enderecos, geocodificacao, rota, descartes, atalhos e cor final.",
        )

    val sessionStartAnchor = "    LaunchedEffect(Unit) {\n"
    if (sessionStartAnchor in text && "app.session.started" !in text) {
        text = text.replaceFirst(
            sessionStartAnchor,
            sessionStartAnchor + "        DiagnosticLogStore.record(\"app\", \"app.session.started version=\" + BuildConfig.VERSION_NAME + \" build=\" + BuildConfig.VERSION_CODE)\n",
        )
    }
    text = text
        .replace(
            "            supportReportStatus = \"Gerando relatorio...\"\n",
            "            supportReportStatus = \"Gerando relatorio...\"\n            DiagnosticLogStore.record(\"support\", \"report.export.started\")\n",
        )
        .replace(
            "                supportReportStatus = \"Relatorio gerado. Anexe o arquivo aqui no chat.\"\n",
            "                DiagnosticLogStore.record(\"support\", \"report.export.completed\")\n                supportReportStatus = \"Relatorio gerado. Anexe o arquivo aqui no chat.\"\n",
        )

    listOf(
        "professional_bubble_home_0_1_118",
        "Text(\"Operacao\"",
        "Text(\"Sistema\"",
        "Text(\"Acoes rapidas\"",
        "Text(\"Suporte\"",
        "label = \"WhatsApp\"",
        "label = \"Coletor\"",
        "label = \"Limpar\"",
        "label = \"Depurar\"",
        "label = \"Encerrar\"",
        "BUBBLE_GROUP_ACCESS -> {",
        "DiagnosticLogStore.dump()",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Home profissional incompleta: $marker")
    }
    if ("\"Ferramentas\" to BUBBLE_GROUP_TOOLS" in text) throw GradleException("Ferramentas ainda aparece na grade principal.")
    if ("\"Leitura\" to BUBBLE_GROUP_READING" in text) throw GradleException("Leitura ainda aparece na grade principal.")

    file.writeText(text)
}

val professionalBubbleHome118 by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn("sessionDiagnosticV2", "bubbleShortcutNavigation117")
    doLast { enforceProfessionalBubbleHome118(mainFile.asFile) }
}

professionalBubbleHome118.configure {
    mustRunAfter("sessionDiagnosticV2", "bubbleShortcutNavigation117", "inAppGroupedBubbleHome115")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("test") }.configureEach {
    dependsOn(professionalBubbleHome118)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(professionalBubbleHome118)
    doFirst {
        enforceProfessionalBubbleHome118(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
