// Defesa final do checklist 4: remove chamadas de log direto de todos os modulos
// de producao depois que os scripts historicos terminarem de materializar o codigo.

fun nextProductionLogCallChecklist4(source: String, name: String, startAt: Int): Int {
    var index = startAt
    while (index < source.length) {
        when {
            source.startsWith("//", index) -> index = source.indexOf('\n', index).let { if (it < 0) source.length else it + 1 }
            source.startsWith("/*", index) -> index = source.indexOf("*/", index + 2).let { if (it < 0) source.length else it + 2 }
            source.startsWith("\"\"\"", index) -> index = source.indexOf("\"\"\"", index + 3).let { if (it < 0) source.length else it + 3 }
            source[index] == '"' -> {
                index += 1
                while (index < source.length) {
                    if (source[index] == '\\') index += 2
                    else if (source[index] == '"') { index += 1; break }
                    else index += 1
                }
            }
            source[index] == '\'' -> {
                index += 1
                while (index < source.length) {
                    if (source[index] == '\\') index += 2
                    else if (source[index] == '\'') { index += 1; break }
                    else index += 1
                }
            }
            source.startsWith(name, index) &&
                (index == 0 || (!source[index - 1].isLetterOrDigit() && source[index - 1] != '_')) &&
                source.getOrNull(index + name.length) == '(' -> {
                val lineStart = source.lastIndexOf('\n', index - 1).let { if (it < 0) 0 else it + 1 }
                if (!source.substring(lineStart, index).contains("fun ")) return index
                index += name.length
            }
            else -> index += 1
        }
    }
    return -1
}

fun productionLogCallEndChecklist4(source: String, openParen: Int): Int {
    var depth = 0
    var index = openParen
    while (index < source.length) {
        when {
            source.startsWith("//", index) -> index = source.indexOf('\n', index).let { if (it < 0) source.length else it + 1 }
            source.startsWith("/*", index) -> index = source.indexOf("*/", index + 2).let { if (it < 0) source.length else it + 2 }
            source.startsWith("\"\"\"", index) -> index = source.indexOf("\"\"\"", index + 3).let { if (it < 0) source.length else it + 3 }
            source[index] == '"' -> {
                index += 1
                while (index < source.length) {
                    if (source[index] == '\\') index += 2
                    else if (source[index] == '"') { index += 1; break }
                    else index += 1
                }
            }
            source[index] == '\'' -> {
                index += 1
                while (index < source.length) {
                    if (source[index] == '\\') index += 2
                    else if (source[index] == '\'') { index += 1; break }
                    else index += 1
                }
            }
            source[index] == '(' -> { depth += 1; index += 1 }
            source[index] == ')' -> {
                depth -= 1
                if (depth == 0) return index
                index += 1
            }
            else -> index += 1
        }
    }
    throw GradleException("Chamada de log sem fechamento no checklist 4.")
}

fun removeProductionLogCallChecklist4(source: String, name: String): String {
    var text = source
    var cursor = 0
    val replacement = if (name.contains("Log.")) {
        "0 /* production_android_log_removed_checklist_4 */"
    } else {
        "Unit /* production_log_removed_checklist_4 */"
    }
    while (true) {
        val start = nextProductionLogCallChecklist4(text, name, cursor)
        if (start < 0) return text
        val end = productionLogCallEndChecklist4(text, start + name.length)
        text = text.substring(0, start) + replacement + text.substring(end + 1)
        cursor = start + 1
    }
}

fun patchAllProductionLogsChecklist4(sourceRoot: java.io.File) {
    val excludedFiles = setOf(
        "DiagnosticLogStore.kt",
        "LiveFailureTraceStore.kt",
        "DiagnosticRuntimeGate.kt",
        "ManualTechnicalReportBuilder.kt",
        "ManualTechnicalReportExporter.kt",
    )
    val calls = listOf(
        "DiagnosticLogStore.record",
        "LiveFailureTraceStore.recordRead",
        "LiveFailureTraceStore.recordTrace",
        "LiveFailureTraceStore.recordStep",
        "LiveFailureTraceStore.recordGeocode",
        "LiveFailureTraceStore.recordRoute",
        "LiveFailureTraceStore.recordDecision",
        "android.util.Log.v",
        "android.util.Log.d",
        "android.util.Log.i",
        "android.util.Log.w",
        "android.util.Log.e",
        "Log.v",
        "Log.d",
        "Log.i",
        "Log.w",
        "Log.e",
    )
    sourceRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" && it.name !in excludedFiles }
        .forEach { file ->
            var text = file.readText()
            calls.forEach { call -> text = removeProductionLogCallChecklist4(text, call) }
            calls.forEach { call ->
                if (nextProductionLogCallChecklist4(text, call, 0) >= 0) {
                    throw GradleException("Log de producao ainda presente em ${file.name}: $call")
                }
            }
            file.writeText(text)
        }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchAllProductionLogsChecklist4(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}
