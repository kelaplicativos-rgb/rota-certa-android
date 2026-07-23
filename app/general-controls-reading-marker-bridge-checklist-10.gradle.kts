// Etapa 10 — reconhece quando a Leitura já saiu da tela Análise por um patch histórico.

fun bridgeAlreadyMovedLiveReadingChecklist10(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt ausente na ponte de Leitura 10.")
    var main = file.readText()
    if ("live_reading_moved_to_general_controls_checklist_7" in main) return

    val analysisStart = main.indexOf("private fun AnalysisScreen(")
    val liveCardStart = if (analysisStart >= 0) main.indexOf("private fun LiveReadingCard(", analysisStart) else -1
    if (analysisStart < 0 || liveCardStart < 0) {
        throw GradleException("Região da tela Análise/Leitura ausente na ponte 10.")
    }
    val analysisRegion = main.substring(analysisStart, liveCardStart)
    if ("    LiveReadingCard(" in analysisRegion) return

    main = main.substring(0, liveCardStart) +
        "// live_reading_moved_to_general_controls_checklist_7\n" +
        main.substring(liveCardStart)
    file.writeText(main)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        bridgeAlreadyMovedLiveReadingChecklist10(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}
