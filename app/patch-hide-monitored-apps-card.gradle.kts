val hideMonitoredAppsCard by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = mainFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text

        text = text.replace(
            "        MonitoredAppsCard(settings = draft, onChange = ::saveDraft)\n",
            "",
        )

        text = text.replace("Modelos de cards", "Assinaturas de cards")
        text = text.replace("Modelos cadastrados", "Assinaturas cadastradas")
        text = text.replace("Anexar modelos de cards (prints)", "Anexar prints dos cards")
        text = text.replace("Nenhum modelo cadastrado ainda.", "Nenhuma assinatura de card cadastrada ainda.")
        text = text.replace("Cadastrar texto lido como modelo", "Cadastrar como assinatura de card")
        text = text.replace("Modelo cadastrado:", "Assinatura cadastrada:")
        text = text.replace("Modelo removido:", "Assinatura removida:")

        if (text != original) file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(hideMonitoredAppsCard)
}
