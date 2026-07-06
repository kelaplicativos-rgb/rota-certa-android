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
