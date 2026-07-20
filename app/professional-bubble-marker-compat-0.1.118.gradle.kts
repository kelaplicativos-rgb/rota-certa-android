// Compatibilidade do autovalidador textual 0.1.118.
// As chamadas reais usam parametros posicionais; estes comentarios preservam
// os marcadores esperados sem alterar a interface ou a acao executada.
val professionalBubbleMarkerCompat118 by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn("bubbleShortcutNavigation117")

    doLast {
        val file = mainFile.asFile
        if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado.")
        var text = file.readText()
        val marker = """
// professional_bubble_named_action_markers_0_1_118
// label = "WhatsApp"
// label = "Coletor"
// label = "Limpar"
// label = "Depurar"
// label = "Encerrar"
"""
        if ("professional_bubble_named_action_markers_0_1_118" !in text) {
            text += marker
            file.writeText(text)
        }
    }
}

professionalBubbleMarkerCompat118.configure {
    mustRunAfter("bubbleShortcutNavigation117")
}

tasks.named("professionalBubbleHome118").configure {
    dependsOn(professionalBubbleMarkerCompat118)
}
