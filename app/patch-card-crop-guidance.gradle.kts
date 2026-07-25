val patchCardCropGuidance by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = mainFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text

        text = text.replace(
            """            Text("Modelos cadastrados: ${'$'}{cardTemplates.size}")
            Button(onClick = onPickCardModels, modifier = Modifier.fillMaxWidth()) {
                Text("Anexar modelos de cards (prints)")
            }
""",
            """            Text("Modelos cadastrados: ${'$'}{cardTemplates.size}")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Como cadastrar corretamente", fontWeight = FontWeight.Bold)
                    Text(
                        "Use print RECORTADO somente do bloco do card: tempos, km, endereco de embarque e endereco de destino. Nao use print da tela inteira, lista, mapa, notificacoes ou tela do Rota Certa.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Bom exemplo: 9min (1,3km) + endereco / 9min (2,9km) + endereco, ou A e B com os enderecos grandes.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Button(onClick = onPickCardModels, modifier = Modifier.fillMaxWidth()) {
                Text("Anexar recorte do card")
            }
""",
        )

        if (text != original) file.writeText(text)
    }
}

patchCardCropGuidance.configure {
    mustRunAfter("patchBubbleStateReport")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchCardCropGuidance)
}
