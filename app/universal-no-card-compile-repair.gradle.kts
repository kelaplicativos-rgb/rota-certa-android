// Repara referencias auxiliares da interface sem remover o modulo de Cards.
// Os modelos permanecem opcionais para a leitura universal e preservados para
// consulta, cadastro, backup e restauracao pelo usuario.
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
        if ("cards_ui_allowed_compile_0_1_120" !in text) {
            text += "// cards_ui_allowed_compile_0_1_120\n"
        }

        if (Regex("LaunchedEffect\\(launchIntent\\)\\s*LaunchedEffect\\(launchIntent\\)").containsMatchIn(text)) {
            throw GradleException("LaunchedEffect duplicado")
        }
        if ("repository.cardTemplates.first().forEach { template ->\n            repository.removeCardTemplate(template.id)" in text) {
            throw GradleException("A interface nao pode apagar modelos de Cards automaticamente.")
        }

        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalNoCardCompileRepair)
}
