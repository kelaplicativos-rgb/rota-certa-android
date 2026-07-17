// Repara referencias auxiliares da interface depois da remocao integral dos
// modelos de cards. Nao altera o processamento da bolinha universal.
val universalNoCardCompileRepair by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalNoCardRuntimeContract"))

    doLast {
        val file = mainFile.asFile
        if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado")
        var text = file.readText()

        text = Regex("LaunchedEffect\\(launchIntent\\)\\s*LaunchedEffect\\(launchIntent\\)")
            .replace(text, "LaunchedEffect(launchIntent)")

        text = text.replace(
            "diagnostic.toShareText()",
            """buildString {
                        appendLine("ROTA CERTA DIAGNOSTICO")
                        appendLine("Versao: ${'$'}{diagnostic.appVersionName} (${'$'}{diagnostic.appVersionCode})")
                        appendLine("Data: ${'$'}{formatDate(diagnostic.createdAtMillis)}")
                        appendLine("Cor: ${'$'}{diagnostic.bubbleColor}")
                        appendLine("Etapa: ${'$'}{diagnostic.stage}")
                        appendLine("Pacote: ${'$'}{diagnostic.packageName ?: "nao informado"}")
                        appendLine("Motivo: ${'$'}{diagnostic.reason}")
                        appendLine("Destino: ${'$'}{diagnostic.destination ?: "nao identificado"}")
                        appendLine("Distancia casa: ${'$'}{diagnostic.homeDistanceKm?.let(::formatKm) ?: "nao calculada"}")
                        appendLine("Distancia alfinete: ${'$'}{diagnostic.alternativeDistanceKm?.let(::formatKm) ?: "nao calculada"}")
                        appendLine("Texto tamanho: ${'$'}{diagnostic.textLength}")
                        appendLine("Texto hash: ${'$'}{diagnostic.textHash ?: "sem hash"}")
                        appendLine("Erro: ${'$'}{diagnostic.error ?: "nenhum"}")
                    }""",
        )

        val reportStart = text.indexOf("private suspend fun buildManualSupportReport(")
        if (reportStart >= 0) {
            val returnStart = text.indexOf("    return buildString {", reportStart)
            if (returnStart <= reportStart) throw GradleException("Corpo do relatorio manual nao encontrado")
            val reportPrefix = text.substring(reportStart, returnStart)
            if ("val bubbleUpdatedAtMillis" !in reportPrefix) {
                val helpers = """    val bubbleStatePrefs = context.getSharedPreferences("rota_certa_bubble", Context.MODE_PRIVATE)
    val bubbleUpdatedAtMillis = bubbleStatePrefs.getLong("state_updated_at", 0L)
    fun bubbleText(key: String): String = bubbleStatePrefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: "nao informado"
    fun bubbleBool(key: String): String = bubbleStatePrefs.getBoolean(key, false).toString()
    fun bubbleInt(key: String): String = bubbleStatePrefs.getInt(key, -1).takeIf { it >= 0 }?.toString() ?: "nao informado"

"""
                text = text.substring(0, returnStart) + helpers + text.substring(returnStart)
            }
        }

        if ("universal_no_card_compile_repair_0_1_102" !in text) {
            text += "\n// universal_no_card_compile_repair_0_1_102\n"
        }

        listOf(
            "Modelos de cards",
            "Anexar modelos de cards",
            "Cadastrar texto lido como modelo",
            "CardModelsCard(",
            "cardModelPicker",
            "MonitoredAppsCard(",
        ).forEach { forbidden ->
            if (forbidden in text) throw GradleException("Recurso de cards ainda presente: $forbidden")
        }
        if (Regex("LaunchedEffect\\(launchIntent\\)\\s*LaunchedEffect\\(launchIntent\\)").containsMatchIn(text)) {
            throw GradleException("LaunchedEffect duplicado")
        }

        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalNoCardCompileRepair)
}
