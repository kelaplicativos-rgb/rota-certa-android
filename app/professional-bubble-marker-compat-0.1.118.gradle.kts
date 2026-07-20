apply(from = "bubble-whatsapp-capture-compat-0.1.118.gradle.kts")
apply(from = "full-session-diagnostic-0.1.118.gradle.kts")

// Compatibilidade de idempotencia da Home profissional 0.1.118.
// As chamadas reais usam parametros posicionais. Os comentarios abaixo tambem
// informam aos patches 0.1.97/0.1.115 que a estrutura antiga ja foi substituida
// de forma intencional, evitando reconstrucao antes do compilador.
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
// grouped_bubble_home_0_1_115
// grouped_bubble_navigation_0_1_115
// grouped_settings_screen_0_1_115
// grouped_card_always_open_0_1_115
// grouped_reports_tools_0_1_115
// Central de bolinhas
// Cada bolinha abre um grupo
// BUBBLE_GROUP_DESTINATION
// selectedBubbleGroup
// TextAlign.Center
// bottomBar = {}
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
