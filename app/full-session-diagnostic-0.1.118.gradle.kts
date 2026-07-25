// Garante que o relatorio 0.1.118 preserve toda a linha do tempo disponivel em
// memoria, mesmo depois de tarefas antigas reconstruirem o corpo do relatorio.
fun enforceFullSessionDiagnostic118(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado.")
    var text = file.readText()

    text = text
        .replace("val complementaryEvents = DiagnosticLogStore.dump(120)", "val complementaryEvents = DiagnosticLogStore.dump()")
        .replace("--- EVENTOS GLOBAIS COMPLEMENTARES ---", "--- LINHA DO TEMPO COMPLETA DA EXECUCAO ---")
        .replace(
            "O relatorio nao inclui backup nem historico inteiro. Ele preserva a tentativa mais recente, textos de acessibilidade/OCR, enderecos, geocodificacao, rota, descarte e cor final.",
            "O relatorio registra a linha do tempo mantida em memoria desde o inicio da execucao, alem da tentativa detalhada de leitura, OCR, enderecos, geocodificacao, rota, descartes, atalhos e cor final.",
        )

    if ("app.session.started" !in text) {
        val contextAnchor = "    val context = LocalContext.current\n"
        val sessionBlock = """    LaunchedEffect("rota_certa_session_0_1_118") {
        DiagnosticLogStore.record(
            "app",
            "app.session.started version=" + BuildConfig.VERSION_NAME + " build=" + BuildConfig.VERSION_CODE,
        )
    } // full_session_start_0_1_118

"""
        when {
            contextAnchor in text -> {
                text = text.replaceFirst(contextAnchor, contextAnchor + sessionBlock)
            }
            "    LaunchedEffect(" in text -> {
                val effectStart = text.indexOf("    LaunchedEffect(")
                val bodyStart = text.indexOf("{\n", effectStart)
                if (bodyStart < 0) throw GradleException("LaunchedEffect sem corpo valido na Home.")
                val insertAt = bodyStart + 2
                val eventLine = "        DiagnosticLogStore.record(\"app\", \"app.session.started version=\" + BuildConfig.VERSION_NAME + \" build=\" + BuildConfig.VERSION_CODE) // full_session_start_0_1_118\n"
                text = text.substring(0, insertAt) + eventLine + text.substring(insertAt)
            }
            else -> throw GradleException("Nao encontrei ponto seguro para registrar o inicio da sessao.")
        }
    }

    if ("report.export.started" !in text) {
        val anchor = "            supportReportStatus = \"Gerando relatorio...\"\n"
        if (anchor !in text) throw GradleException("Inicio da exportacao do relatorio nao encontrado.")
        text = text.replaceFirst(
            anchor,
            anchor + "            DiagnosticLogStore.record(\"support\", \"report.export.started\")\n",
        )
    }

    if ("report.export.completed" !in text) {
        val anchor = "                supportReportStatus = \"Relatorio gerado. Anexe o arquivo aqui no chat.\"\n"
        if (anchor !in text) throw GradleException("Conclusao da exportacao do relatorio nao encontrada.")
        text = text.replaceFirst(
            anchor,
            "                DiagnosticLogStore.record(\"support\", \"report.export.completed\")\n" + anchor,
        )
    }

    val marker = "// full_session_diagnostic_0_1_118"
    if (marker !in text) text += "\n$marker\n"

    listOf(
        "DiagnosticLogStore.dump()",
        "LINHA DO TEMPO COMPLETA DA EXECUCAO",
        "app.session.started",
        "report.export.started",
        "report.export.completed",
        "full_session_start_0_1_118",
        marker,
    ).forEach { expected ->
        if (expected !in text) throw GradleException("Diagnostico completo 0.1.118 ausente: $expected")
    }
    file.writeText(text)
}

val fullSessionDiagnostic118 by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn("sessionDiagnosticV2", "professionalBubbleHome118")
    doLast { enforceFullSessionDiagnostic118(mainFile.asFile) }
}

fullSessionDiagnostic118.configure {
    mustRunAfter("sessionDiagnosticV2", "professionalBubbleHome118")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(fullSessionDiagnostic118)
}
