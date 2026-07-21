// Compatibilidade para telas de Ferramentas reconstruidas pelos patches antigos.
// Cria uma ancora temporaria dentro da primeira Column do ToolsScreen, deixa o
// patch 0.1.121 inserir o card e remove a ancora antes da compilacao.

val temporaryToolsTitle121 = "        Text(\"Ferramentas\", fontWeight = FontWeight.Bold)\n"

fun insertTemporaryToolsAnchor121(file: java.io.File) {
    var text = file.readText()
    if (temporaryToolsTitle121 in text) return

    val toolsStart = text.indexOf("private fun ToolsScreen(")
    val signatureEnd = if (toolsStart >= 0) text.indexOf(") {", toolsStart) else -1
    val columnStart = if (signatureEnd >= 0) text.indexOf("Column(", signatureEnd) else -1
    val columnBodyStart = if (columnStart >= 0) text.indexOf('{', columnStart) else -1
    if (toolsStart < 0 || signatureEnd < 0 || columnStart < 0 || columnBodyStart < 0) {
        throw GradleException("Estrutura de ToolsScreen ausente para ancora temporaria 0.1.121.")
    }

    text = text.substring(0, columnBodyStart + 1) + "\n" + temporaryToolsTitle121 + text.substring(columnBodyStart + 1)
    file.writeText(text)
}

fun removeTemporaryToolsAnchor121(file: java.io.File) {
    var text = file.readText()
    if (temporaryToolsTitle121 in text) {
        text = text.replaceFirst(temporaryToolsTitle121, "")
    }
    listOf(
        "Text(\"Rastreamento de trabalho\"",
        "Button(onClick = onOpenWorkTracking",
        "work_tracking_ui_0_1_121",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Card de rastreamento nao foi gerado: $marker")
    }
    file.writeText(text)
}

val workTrackingCardAnchorCompat121 by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn("popupNavigationLateCompile120")
    doLast { insertTemporaryToolsAnchor121(mainFile.asFile) }
}

tasks.named("radarWorkTracking121").configure {
    dependsOn(workTrackingCardAnchorCompat121)
    mustRunAfter(workTrackingCardAnchorCompat121)
}

val workTrackingCardAnchorCleanup121 by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn("radarWorkTracking121")
    doLast { removeTemporaryToolsAnchor121(mainFile.asFile) }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(workTrackingCardAnchorCleanup121)
}
