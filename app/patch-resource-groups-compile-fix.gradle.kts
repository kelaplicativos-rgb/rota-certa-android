val patchResourceGroupsCompileFix by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.files(mainFile, serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = mainFile.asFile
        var text = file.readText()
        val original = text

        text = text.replace(
            "                else -> requestedTab\n",
            "                else -> requestedTab ?: TAB_TOOLS\n",
        )

        text = text.replace(
            "ExpandableCard(title = \"Controle geral\", initiallyExpanded = true)",
            "ExpandableCard(title = \"Controle geral\", initiallyExpanded = false)",
        )
        text = text.replace(
            "ExpandableCard(title = \"Definir regiao de destino\", initiallyExpanded = true)",
            "ExpandableCard(title = \"Definir regiao de trabalho\", initiallyExpanded = false)",
        )
        text = text.replace(
            "ExpandableCard(title = \"Definir regiao de corridas\", initiallyExpanded = true)",
            "ExpandableCard(title = \"Definir regiao de trabalho\", initiallyExpanded = false)",
        )
        text = text.replace(
            "ExpandableCard(title = \"Definir regiao de trabalho\", initiallyExpanded = true)",
            "ExpandableCard(title = \"Definir regiao de trabalho\", initiallyExpanded = false)",
        )
        text = text.replace(
            "ExpandableCard(title = \"Coletor BlaBlaCar\", initiallyExpanded = false)",
            "ExpandableCard(title = \"Assistente de Viagens\", initiallyExpanded = false)",
        )
        text = text.replace(
            "Registro manual de viagem logada: passageiros, telefones, WhatsApp, rotas, faturamento, despesas e lucro.",
            "Controle passageiros, rotas, faturamento, despesas e lucro das viagens.",
        )
        text = text.replace("Abrir coletor", "Abrir assistente")

        text = text.replace(
"""private fun ExpandableCard(
    title: String,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(initiallyExpanded) {
        if (initiallyExpanded) expanded = true
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Fechar" else "Abrir")
                }
            }
            if (expanded) {
                content()
            }
        }
    }
}
""",
"""private fun ExpandableCard(
    title: String,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    val requestedExpander = (LocalContext.current as? android.app.Activity)
        ?.intent
        ?.getStringExtra("br.com.mapeiaia.rotacerta.extra.OPEN_EXPANDER")
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(initiallyExpanded, requestedExpander, title) {
        if (initiallyExpanded || requestedExpander == title) expanded = true
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Fechar" else "Abrir")
                }
            }
            if (expanded) {
                content()
            }
        }
    }
}
""",
        )

        text = text.replace(
"""        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Coletor BlaBlaCar", fontWeight = FontWeight.Bold)
                Text(
                    "Controle passageiros, rotas, faturamento, despesas e lucro das viagens.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onOpenBlaBlaCarCollector, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir assistente")
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
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
""",
"""        ExpandableCard(title = "Assistente de Viagens", initiallyExpanded = false) {
            Text(
                "Controle passageiros, rotas, faturamento, despesas e lucro das viagens.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onOpenBlaBlaCarCollector, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir assistente")
            }
        }
        ExpandableCard(title = "Area de transferencia", initiallyExpanded = false) {
            Text(
                "Limpeza manual para remover o texto copiado quando o copiar/colar do celular travar ou ficar preso em conteudo antigo.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onClearClipboard, modifier = Modifier.fillMaxWidth()) {
                Text("Limpar area de transferencia")
            }
        }
""",
        )

        text = text.replace(
"""        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Coletor BlaBlaCar", fontWeight = FontWeight.Bold)
                Text(
                    "Registro manual de viagem logada: passageiros, telefones, WhatsApp, rotas, faturamento, despesas e lucro.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onOpenBlaBlaCarCollector, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir coletor")
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
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
""",
"""        ExpandableCard(title = "Assistente de Viagens", initiallyExpanded = false) {
            Text(
                "Controle passageiros, rotas, faturamento, despesas e lucro das viagens.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onOpenBlaBlaCarCollector, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir assistente")
            }
        }
        ExpandableCard(title = "Area de transferencia", initiallyExpanded = false) {
            Text(
                "Limpeza manual para remover o texto copiado quando o copiar/colar do celular travar ou ficar preso em conteudo antigo.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onClearClipboard, modifier = Modifier.fillMaxWidth()) {
                Text("Limpar area de transferencia")
            }
        }
""",
        )

        text = text.replace(
"""@Composable
private fun CardModelsCard(
    cardTemplates: List<RideCardTemplate>,
    templateStatus: String,
    unreadTemplatePrints: Int,
    onPickCardModels: () -> Unit,
    onDeleteCardModel: (RideCardTemplate) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Modelos de cards", fontWeight = FontWeight.Bold)
            Text("Modelos cadastrados: ${'$'}{cardTemplates.size}")
            Button(onClick = onPickCardModels, modifier = Modifier.fillMaxWidth()) {
                Text("Anexar modelos de cards (prints)")
            }
            Text(templateStatus, style = MaterialTheme.typography.bodySmall)
            if (unreadTemplatePrints > 0) {
                Text("Prints sem leitura: ${'$'}unreadTemplatePrints", style = MaterialTheme.typography.bodySmall)
            }
            if (cardTemplates.isEmpty()) {
                Text("Nenhum modelo cadastrado ainda.", style = MaterialTheme.typography.bodySmall)
            } else {
                cardTemplates.forEach { template ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(template.name, fontWeight = FontWeight.Bold)
                            Text(template.packageName ?: "app nao identificado", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { onDeleteCardModel(template) }) {
                            Text("Apagar")
                        }
                    }
                }
            }
        }
    }
}
""",
"""@Composable
private fun CardModelsCard(
    cardTemplates: List<RideCardTemplate>,
    templateStatus: String,
    unreadTemplatePrints: Int,
    onPickCardModels: () -> Unit,
    onDeleteCardModel: (RideCardTemplate) -> Unit,
) {
    ExpandableCard(title = "Modelos de cards", initiallyExpanded = false) {
        Text("Modelos cadastrados: " + cardTemplates.size)
        Button(onClick = onPickCardModels, modifier = Modifier.fillMaxWidth()) {
            Text("Anexar modelos de cards (prints)")
        }
        Text(templateStatus, style = MaterialTheme.typography.bodySmall)
        if (unreadTemplatePrints > 0) {
            Text("Prints sem leitura: " + unreadTemplatePrints, style = MaterialTheme.typography.bodySmall)
        }
        if (cardTemplates.isEmpty()) {
            Text("Nenhum modelo cadastrado ainda.", style = MaterialTheme.typography.bodySmall)
        } else {
            cardTemplates.forEach { template ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(template.name, fontWeight = FontWeight.Bold)
                        Text(template.packageName ?: "app nao identificado", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { onDeleteCardModel(template) }) {
                        Text("Apagar")
                    }
                }
            }
        }
    }
}
""",
        )

        text = text.replace(
"""        if (history.isEmpty()) {
            Text("Nenhuma analise salva ainda.")
            return@ExpandableCard
        }
        history.forEach { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(recommendationLabel(result.recommendation), fontWeight = FontWeight.Bold)
                    Text(formatDate(result.createdAtMillis))
                    Text(result.fields.destination ?: "Destino final nao identificado")
                    Text(result.reason)
                }
            }
        }
""",
"""        if (history.isEmpty()) {
            Text("Nenhuma analise salva ainda.")
        } else {
            history.forEach { result ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(recommendationLabel(result.recommendation), fontWeight = FontWeight.Bold)
                        Text(formatDate(result.createdAtMillis))
                        Text(result.fields.destination ?: "Destino final nao identificado")
                        Text(result.reason)
                    }
                }
            }
        }
""",
        )

        text = text.replace(
"""private fun List<AnalysisResult>.toHistoryShareText(): String = buildString {
    appendLine("ROTA CERTA HISTORICO")
    if (isEmpty()) {
        appendLine("Nenhuma analise salva.")
        return@buildString
    }
    forEachIndexed { index, result ->
        appendLine((index + 1).toString() + ". " + recommendationLabel(result.recommendation))
        appendLine("Data: " + formatDate(result.createdAtMillis))
        appendLine("Destino: " + (result.fields.destination ?: "nao identificado"))
        appendLine("Motivo: " + result.reason)
        appendLine()
    }
}
""",
"""private fun List<AnalysisResult>.toHistoryShareText(): String {
    val entries = this
    return buildString {
        appendLine("ROTA CERTA HISTORICO")
        if (entries.isEmpty()) {
            appendLine("Nenhuma analise salva.")
            return@buildString
        }
        entries.forEachIndexed { index, entry ->
            appendLine((index + 1).toString() + ". " + recommendationLabel(entry.recommendation))
            appendLine("Data: " + formatDate(entry.createdAtMillis))
            appendLine("Destino: " + (entry.fields.destination ?: "nao identificado"))
            appendLine("Motivo: " + entry.reason)
            appendLine()
        }
    }
}
""",
        )

        if (text != original) {
            file.writeText(text)
        }

        val service = serviceFile.asFile
        var serviceText = service.readText()
        val originalService = serviceText

        serviceText = serviceText.replace(
"""    private fun openApp() {
        hideActionMenu()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }
""",
"""    private fun openApp(tab: String? = null, expander: String? = null) {
        hideActionMenu()
        runCatching {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (tab != null) intent.putExtra(EXTRA_OPEN_TAB, tab)
            if (expander != null) intent.putExtra("br.com.mapeiaia.rotacerta.extra.OPEN_EXPANDER", expander)
            startActivity(intent)
        }
    }
""",
        )

        serviceText = serviceText.replace(
"""                    .putExtra(EXTRA_OPEN_TAB, TAB_CONFIG)
                    .putExtra(EXTRA_SAVED_PLACE_ID, place.id),
""",
"""                    .putExtra(EXTRA_OPEN_TAB, TAB_TOOLS)
                    .putExtra("br.com.mapeiaia.rotacerta.extra.OPEN_EXPANDER", "Alertas de proximidade")
                    .putExtra(EXTRA_SAVED_PLACE_ID, place.id),
""",
        )

        serviceText = serviceText.replace(
"""            addView(actionMenuItem("🏠  Abrir Rota Certa") { openApp() })
            addView(actionMenuItem("💾  Salvar card de corrida") {
                hideActionMenu()
                saveCurrentRideCardFromBubble()
            })
            addView(actionMenuItem("📍  Salvar este local") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            })
            addView(actionMenuItem("🎯  Definir região de destino") {
                hideActionMenu()
                openDecisionAddressSettingsFromBubble()
            })
            addView(actionMenuItem("🔔  Criar alerta de proximidade") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)
            })
""",
"""            addView(actionMenuItem(
                label = "🏠  Abrir Rota Certa",
                action = { openApp() },
                longAction = { openApp(tab = TAB_TOOLS) },
            ))
            addView(actionMenuItem(
                label = "💾  Salvar card de corrida",
                action = {
                    hideActionMenu()
                    saveCurrentRideCardFromBubble()
                },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Modelos de cards") },
            ))
            addView(actionMenuItem(
                label = "📍  Salvar este local",
                action = {
                    hideActionMenu()
                    saveCurrentPlaceFromBubble(SavedPlaceType.Place)
                },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Alertas de proximidade") },
            ))
            addView(actionMenuItem(
                label = "🎯  Definir região de trabalho",
                action = { openApp(tab = TAB_TOOLS, expander = "Definir regiao de trabalho") },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Definir regiao de trabalho") },
            ))
            addView(actionMenuItem(
                label = "🔔  Criar alerta de proximidade",
                action = {
                    hideActionMenu()
                    saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)
                },
                longAction = { openApp(tab = TAB_TOOLS, expander = "Alertas de proximidade") },
            ))
""",
        )

        serviceText = serviceText.replace(
"""    private fun actionMenuItem(label: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            minHeight = dp(42)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { action() }
        }
""",
"""    private fun actionMenuItem(
        label: String,
        action: () -> Unit,
        longAction: (() -> Unit)? = null,
    ): TextView =
        TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            minHeight = dp(42)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
            var longPressHandled = false
            var longPressRunnable: Runnable? = null
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        longPressHandled = false
                        longPressRunnable = Runnable {
                            if (view.isPressed && longAction != null) {
                                longPressHandled = true
                                longAction.invoke()
                            }
                        }
                        view.postDelayed(longPressRunnable, 2_000L)
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { view.removeCallbacks(it) }
                        longPressRunnable = null
                    }
                }
                false
            }
            setOnClickListener {
                if (longPressHandled) {
                    longPressHandled = false
                } else {
                    action()
                }
            }
        }
""",
        )

        if (serviceText != originalService) {
            service.writeText(serviceText)
        }
    }
}

patchResourceGroupsCompileFix.configure {
    mustRunAfter("patchLiveRideBubbleActions")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchResourceGroupsCompileFix)
}
