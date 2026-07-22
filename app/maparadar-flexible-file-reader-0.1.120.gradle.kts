// Importacao flexivel de radares orientada pelo arquivo real maparadar.csv.xls.
// O arquivo possui conteudo CSV textual, apesar da extensao .xls. Esta camada
// le os bytes originais para permitir UTF-8, Windows-1252 e UTF-16 e amplia os
// tipos aceitos pelo seletor de documentos.

fun mapaRadar120ReplaceOnce(
    source: String,
    oldValue: String,
    newValue: String,
    label: String,
): String {
    if (oldValue !in source) throw GradleException("Ponto ausente para $label")
    return source.replaceFirst(oldValue, newValue)
}

val mapaRadarFlexibleFileReader120 by tasks.registering {
    val mainFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt",
    )
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn("universalRouteInflightProtection120")

    doLast {
        val file = mainFile.asFile
        if (!file.exists()) throw GradleException("MainActivity nao encontrada: ${file.path}")
        var text = file.readText()

        if ("parseMapaRadarFile(fileBytes)" !in text) {
            text = mapaRadar120ReplaceOnce(
                source = text,
                oldValue = """                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    reader.readText()
                } ?: error("Nao consegui abrir o arquivo selecionado.")
                val radars = parseMapaRadarCsv(content)
                if (radars.isEmpty()) error("Arquivo sem radares validos. Use TXT/CSV do MapaRadar.")
""",
                newValue = """                val fileBytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                } ?: error("Nao consegui abrir o arquivo selecionado.")
                val radars = parseMapaRadarFile(fileBytes) // maparadar_flexible_file_reader_0_1_120
                if (radars.isEmpty()) error("Arquivo sem radares validos. Use TXT, CSV ou CSV salvo como XLS do MapaRadar.")
""",
                label = "leitura binaria do arquivo de radares",
            )
        }

        if ("application/vnd.ms-excel" !in text) {
            val expandedPicker = """radarFilePicker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/vnd.ms-excel", "application/octet-stream", "*/*"))"""
            val oldPicker = """radarFilePicker.launch(arrayOf("text/*", "text/comma-separated-values", "application/octet-stream", "*/*"))"""
            text = when {
                oldPicker in text -> text.replace(oldPicker, expandedPicker)
                else -> {
                    val pickerRegex = Regex("""radarFilePicker\.launch\(arrayOf\([^\n]*\)\)""")
                    if (!pickerRegex.containsMatchIn(text)) {
                        throw GradleException("Ponto ausente para tipos MIME do importador de radares")
                    }
                    pickerRegex.replaceFirst(text, expandedPicker)
                }
            }
        }

        file.writeText(text)

        val finalText = file.readText()
        listOf(
            "parseMapaRadarFile(fileBytes)",
            "maparadar_flexible_file_reader_0_1_120",
            "application/vnd.ms-excel",
            "text/csv",
        ).forEach { marker ->
            if (marker !in finalText) throw GradleException("Importacao flexivel incompleta: $marker")
        }
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(mapaRadarFlexibleFileReader120)
}
