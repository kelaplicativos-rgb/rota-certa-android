// Checklist 4 — remove instrumentacao continua e mantem somente relatorio manual.

fun findNextCodeCallChecklist4(source: String, callName: String, startAt: Int): Int {
    var index = startAt
    while (index < source.length) {
        when {
            source.startsWith("//", index) -> {
                index = source.indexOf('\n', index).let { if (it < 0) source.length else it + 1 }
            }
            source.startsWith("/*", index) -> {
                index = source.indexOf("*/", index + 2).let { if (it < 0) source.length else it + 2 }
            }
            source.startsWith("\"\"\"", index) -> {
                index = source.indexOf("\"\"\"", index + 3).let { if (it < 0) source.length else it + 3 }
            }
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
            source.startsWith(callName, index) &&
                (index == 0 || (!source[index - 1].isLetterOrDigit() && source[index - 1] != '_')) &&
                source.getOrNull(index + callName.length) == '(' -> {
                val lineStart = source.lastIndexOf('\n', index - 1).let { if (it < 0) 0 else it + 1 }
                val linePrefix = source.substring(lineStart, index)
                if (!linePrefix.contains("fun ")) return index
                index += callName.length
            }
            else -> index += 1
        }
    }
    return -1
}

fun findCallEndChecklist4(source: String, openParen: Int): Int {
    var depth = 0
    var index = openParen
    while (index < source.length) {
        when {
            source.startsWith("//", index) -> {
                index = source.indexOf('\n', index).let { if (it < 0) source.length else it + 1 }
            }
            source.startsWith("/*", index) -> {
                index = source.indexOf("*/", index + 2).let { if (it < 0) source.length else it + 2 }
            }
            source.startsWith("\"\"\"", index) -> {
                index = source.indexOf("\"\"\"", index + 3).let { if (it < 0) source.length else it + 3 }
            }
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
    throw GradleException("Chamada sem fechamento durante remocao dos diagnosticos: posicao $openParen")
}

fun replaceRuntimeCallsChecklist4(source: String, callName: String): String {
    var text = source
    var cursor = 0
    while (true) {
        val start = findNextCodeCallChecklist4(text, callName, cursor)
        if (start < 0) return text
        val openParen = start + callName.length
        val end = findCallEndChecklist4(text, openParen)
        text = text.substring(0, start) + "Unit /* diagnostics_off_checklist_4 */" + text.substring(end + 1)
        cursor = start + 1
    }
}

fun patchServiceDiagnosticsChecklist4(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no checklist 4.")
    var text = file.readText()
    val continuousCalls = listOf(
        "traceEvent",
        "DiagnosticLogStore.record",
        "LiveFailureTraceStore.recordRead",
        "LiveFailureTraceStore.recordTrace",
        "LiveFailureTraceStore.recordStep",
        "LiveFailureTraceStore.recordGeocode",
        "LiveFailureTraceStore.recordRoute",
        "LiveFailureTraceStore.recordDecision",
        "repository.saveDiagnostic",
    )
    continuousCalls.forEach { callName ->
        text = replaceRuntimeCallsChecklist4(text, callName)
    }
    continuousCalls.forEach { callName ->
        if (findNextCodeCallChecklist4(text, callName, 0) >= 0) {
            throw GradleException("Instrumentacao continua ainda presente no servico: $callName")
        }
    }
    file.writeText(text)
}

fun patchFailureTraceGateChecklist4(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveFailureTraceStore.kt ausente no checklist 4.")
    var text = file.readText()
    val methods = listOf(
        "recordRead",
        "recordTrace",
        "recordStep",
        "recordGeocode",
        "recordRoute",
        "recordDecision",
    )
    methods.forEach { method ->
        val marker = "manual_gate_${method}_checklist_4"
        if (marker in text) return@forEach
        val signature = "    fun $method("
        val start = text.indexOf(signature)
        if (start < 0) throw GradleException("Metodo $method ausente na trilha de falhas.")
        val bodyOpen = text.indexOf("    ) {", start)
        if (bodyOpen < 0) throw GradleException("Corpo de $method nao encontrado.")
        val insertion = bodyOpen + "    ) {".length
        text = text.substring(0, insertion) +
            "\n        if (!DiagnosticRuntimeGate.isEnabled(nowMillis)) return // $marker" +
            text.substring(insertion)
    }
    file.writeText(text)
}

fun patchRepositoryDiagnosticsChecklist4(file: java.io.File) {
    if (!file.exists()) throw GradleException("Repositories.kt ausente no checklist 4.")
    var text = file.readText()
    text = text.replace(
        Regex("diagnosticsEnabled = prefs\\[diagnosticsEnabled\\] \\?: false"),
        "diagnosticsEnabled = false",
    )
    text = text.replace(
        "prefs[diagnosticsEnabled] = settings.diagnosticsEnabled",
        "prefs[diagnosticsEnabled] = false // diagnostics_manual_only_checklist_4",
    )
    val saveAnchor = "    suspend fun saveDiagnostic(diagnostic: LiveDiagnostic) {\n"
    if (saveAnchor in text && "save_diagnostic_manual_gate_checklist_4" !in text) {
        text = text.replaceFirst(
            saveAnchor,
            saveAnchor + "        if (!DiagnosticRuntimeGate.isEnabled()) return // save_diagnostic_manual_gate_checklist_4\n",
        )
    }
    file.writeText(text)
}

fun patchManualReportUiChecklist4(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt ausente no checklist 4.")
    var text = file.readText()
    val callAnchor = "        DiagnosticExpander(\n            diagnostic = diagnostic,"
    if (callAnchor in text) {
        text = text.replaceFirst(
            callAnchor,
            "        DiagnosticExpander(\n            settings = draft,\n            diagnostic = diagnostic,",
        )
    }

    val functionStart = text.indexOf("@Composable\nprivate fun DiagnosticExpander(")
    val nextFunction = if (functionStart >= 0) text.indexOf("\n@Composable\nprivate fun SavedPlacesCard(", functionStart) else -1
    if (functionStart < 0 || nextFunction < 0) {
        throw GradleException("Bloco de diagnostico da interface nao encontrado.")
    }
    val replacement = """@Composable
private fun DiagnosticExpander(
    settings: AppSettings,
    @Suppress("UNUSED_PARAMETER") diagnostic: LiveDiagnostic?,
    cardTemplates: List<RideCardTemplate>,
    @Suppress("UNUSED_PARAMETER") onRegisterRideCard: (String?, String) -> Unit,
) {
    val context = LocalContext.current
    ExpandableCard(title = "Relatorio tecnico", initiallyExpanded = false) {
        Text(
            "Logs e diagnosticos automaticos ficam desligados durante o uso normal. O arquivo abaixo e criado somente quando voce toca no botao.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = {
                DiagnosticRuntimeGate.endManualCapture()
                DiagnosticLogStore.clear()
                LiveFailureTraceStore.clear()
                val report = ManualTechnicalReportBuilder.build(
                    context = context,
                    settings = settings,
                    cardTemplates = cardTemplates,
                )
                ManualTechnicalReportExporter.createAndShare(context, report)
                    .onSuccess {
                        Toast.makeText(context, "Relatorio criado. Escolha onde compartilhar.", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Rota Certa relatorio tecnico", report))
                        Toast.makeText(context, "Nao consegui abrir o compartilhamento. O relatorio foi copiado.", Toast.LENGTH_LONG).show()
                    }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Gerar e compartilhar relatorio")
        }
        Text("Modelos cadastrados: ${'$'}{cardTemplates.size}", style = MaterialTheme.typography.bodySmall)
    }
}
"""
    text = text.substring(0, functionStart) + replacement + text.substring(nextFunction)
    if ("Gerar e compartilhar relatorio" !in text || "settings = draft" !in text) {
        throw GradleException("Interface manual do relatorio nao foi aplicada.")
    }
    file.writeText(text)
}

fun patchManifestFileProviderChecklist4(file: java.io.File) {
    if (!file.exists()) throw GradleException("AndroidManifest.xml ausente no checklist 4.")
    var text = file.readText()
    if ("androidx.core.content.FileProvider" !in text) {
        val anchor = "        <activity\n"
        val provider = """        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${'$'}{applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
"""
        if (anchor !in text) throw GradleException("Ponto de insercao do FileProvider ausente.")
        text = text.replaceFirst(anchor, provider + anchor)
    }
    file.writeText(text)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        val sourceRoot = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile
        patchServiceDiagnosticsChecklist4(java.io.File(sourceRoot, "LiveRideAccessibilityService.kt"))
        patchFailureTraceGateChecklist4(java.io.File(sourceRoot, "LiveFailureTraceStore.kt"))
        patchRepositoryDiagnosticsChecklist4(java.io.File(sourceRoot, "Repositories.kt"))
        patchManualReportUiChecklist4(java.io.File(sourceRoot, "MainActivity.kt"))
        patchManifestFileProviderChecklist4(layout.projectDirectory.file("src/main/AndroidManifest.xml").asFile)
    }
}
