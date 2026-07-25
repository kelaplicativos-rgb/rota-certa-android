// Compatibilidade entre os patches antigos da Home e a navegacao agrupada 0.1.115.
// Alguns estagios acrescentam comentarios na linha do estado `tab`; este passo
// normaliza somente essa linha antes do patch agrupado, preservando o valor atual.

val prepareGroupedTabAnchor115 by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn("inAppBubbleHomeFinal", "inAppBubbleImmediateState")

    doLast {
        val file = mainFile.asFile
        if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado.")
        var text = file.readText()
        if ("grouped_bubble_home_0_1_115" in text) return@doLast

        val tabLine = Regex("(?m)^\\s*var tab by remember \\{ mutableStateOf\\(TAB_[A-Z_]+\\) \\}.*$")
            .find(text)
            ?: throw GradleException("Estado tab nao encontrado para compatibilidade agrupada.")
        text = text.replaceRange(
            tabLine.range,
            "    var tab by remember { mutableStateOf(TAB_ANALYSIS) }",
        )
        if ("    var tab by remember { mutableStateOf(TAB_ANALYSIS) }\n" !in text) {
            throw GradleException("Ancora tab nao foi normalizada para a Home agrupada.")
        }
        file.writeText(text)
    }
}

tasks.named("inAppGroupedBubbleHome115").configure {
    dependsOn(prepareGroupedTabAnchor115)
    mustRunAfter(prepareGroupedTabAnchor115)
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(prepareGroupedTabAnchor115)
}
