val patchBubblePopupClose by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        text = text.replace("Salvar card de corrida", "Salvar card desta corrida")

        if ("actionMenuItem(\"Fechar\"" !in text) {
            val lines = text.lines().toMutableList()
            val openIndex = lines.indexOfFirst { line ->
                line.contains("addView(actionMenuItem(") && line.contains("Abrir Rota Certa")
            }
            if (openIndex >= 0) {
                val indent = lines[openIndex].takeWhile { it.isWhitespace() }
                lines.add(openIndex, "${indent}addView(actionMenuItem(\"Fechar\", { hideActionMenu() }))")
                text = lines.joinToString("\n")
            }
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchBubblePopupClose.configure {
    mustRunAfter(tasks.matching { it.name.startsWith("patch") && it.name != "patchBubblePopupClose" })
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchBubblePopupClose)
}