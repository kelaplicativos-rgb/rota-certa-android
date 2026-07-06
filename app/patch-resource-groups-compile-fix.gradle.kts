val patchResourceGroupsCompileFix by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
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
    }
}

patchResourceGroupsCompileFix.configure {
    mustRunAfter("patchLiveRideBubbleActions")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchResourceGroupsCompileFix)
}
