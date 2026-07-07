val patchFullDiagnosticExport by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        patchService(serviceFile.asFile)
        patchMainActivity(mainFile.asFile)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchFullDiagnosticExport)
}

fun patchService(file: java.io.File) {
    var text = file.readText()
    val original = text

    text = text.replace("const val DIAGNOSTIC_TEXT_LIMIT = 1200", "const val DIAGNOSTIC_TEXT_LIMIT = 6000")
    text = text.replace("const val DIAGNOSTIC_EVENT_LIMIT = 60", "const val DIAGNOSTIC_EVENT_LIMIT = 180")

    if ("LIVE_DECISION_CACHE_LIMIT" in text && "const val LIVE_DECISION_CACHE_LIMIT" !in text) {
        text = text.replace(
            "const val BUBBLE_PREFS = \"rota_certa_bubble\"",
            "const val LIVE_DECISION_CACHE_LIMIT = 8\n        const val BUBBLE_PREFS = \"rota_certa_bubble\"",
        )
    }

    text = text.replace(
        "reason = reason.withDiagnosticEvents(),",
        "reason = reason,",
    )

    text = text.replace(
        "error = error?.let { \"${'$'}{it::class.java.simpleName}: ${'$'}{it.message.orEmpty()}\" },\n        )",
        "error = error?.let { \"${'$'}{it::class.java.simpleName}: ${'$'}{it.message.orEmpty()}\" },\n            diagnosticLog = diagnosticEvents.joinToString(\"\\n\"),\n        )",
    )

    if (text != original) file.writeText(text)
}

fun patchMainActivity(file: java.io.File) {
    var text = file.readText()
    val original = text

    text = text.replace("Text(\"Copiar diagnostico\")", "Text(\"Copiar diagnostico completo\")")
    text = text.replace("Toast.makeText(context, \"Diagnostico copiado\", Toast.LENGTH_SHORT).show()", "Toast.makeText(context, \"Diagnostico completo copiado\", Toast.LENGTH_SHORT).show()")

    val start = text.indexOf("private fun LiveDiagnostic.toShareText(): String = buildString {")
    val end = text.indexOf("\nprivate fun savedPlaceTypeLabel", start)
    if (start >= 0 && end > start) {
        val replacement = """
private fun LiveDiagnostic.toShareText(): String = buildString {
    appendLine("ROTA CERTA DIAGNOSTICO COMPLETO")
    appendLine("Marcador: DIAGNOSTIC_FULL_EXPORT")
    appendLine("Versao: " + appVersionName + " (" + appVersionCode + ")")
    appendLine("Data: " + formatDate(createdAtMillis))
    appendLine("Pacote: " + (packageName ?: "nao informado"))
    appendLine("Etapa: " + stage)
    appendLine("Cor: " + bubbleColor)
    appendLine("Motivo: " + reason)
    appendLine("--- ESTADO ---")
    appendLine("Modo restrito: " + restrictToSelectedRideApps)
    appendLine("Card cadastrado obrigatorio: " + registeredCardRequired)
    appendLine("Card reconhecido: " + (registeredCardMatched ?: "nenhum"))
    appendLine("Pacotes selecionados: " + selectedPackages.joinToString(", ").ifBlank { "nenhum" })
    appendLine("Recomendacao: " + (recommendation ?: "sem decisao"))
    appendLine("Erro: " + (error ?: "nenhum"))
    appendLine("--- CARD E TEXTO ---")
    appendLine("Destino: " + (destination ?: "nao identificado"))
    appendLine("Embarque: " + (pickup ?: "nao identificado"))
    appendLine("Texto tamanho: " + textLength)
    appendLine("Texto hash: " + (textHash ?: "sem hash"))
    appendLine("--- DISTANCIAS ---")
    appendLine("Distancia casa: " + (homeDistanceKm?.let(::formatKm) ?: "nao calculada"))
    appendLine("Distancia alfinete: " + (alternativeDistanceKm?.let(::formatKm) ?: "nao calculada"))
    appendLine("Observacao: se a rota de carro nao for calculada, a bolinha nao deve mostrar numero de km.")
    appendLine("--- LOGS ---")
    appendLine(diagnosticLog.ifBlank { "sem logs" })
    appendLine("--- TEXTO LIDO COMPLETO ---")
    appendLine(textPreview.ifBlank { "sem texto" })
}
""".trimIndent()
        text = text.substring(0, start) + replacement + text.substring(end)
    }

    if (text != original) file.writeText(text)
}
