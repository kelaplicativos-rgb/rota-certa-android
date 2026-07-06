val patchResourceGroupsCompileFix by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = mainFile.asFile
        var text = file.readText()
        val original = text

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
