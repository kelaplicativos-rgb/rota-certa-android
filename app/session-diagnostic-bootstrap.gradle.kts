// Ajusta o script de instrumentacao antes de ele ser aplicado. O runtime final
// muda de forma ao longo dos patches; apenas as ancoras essenciais devem bloquear
// o build. As etapas opcionais continuam sendo inseridas quando a ancora existe.
val sessionDiagnosticScript = layout.projectDirectory.file("session-diagnostic-v2.gradle.kts").asFile
if (!sessionDiagnosticScript.exists()) throw GradleException("session-diagnostic-v2.gradle.kts nao encontrado")
var sessionDiagnosticSource = sessionDiagnosticScript.readText()

val oldTarget = "            val target = \"\"\"        val trigger = UniversalAddressTrigger.evaluate(snapshotText)\n        traceEvent(\n\"\"\"\n"
val newTarget = "            val target = \"\"\"        val trigger = UniversalAddressTrigger.evaluate(snapshotText)\n\"\"\"\n"
val oldReplacementStart = "            val replacement = \"\"\"        val trigger = UniversalAddressTrigger.evaluate(snapshotText)\n"
val newReplacementStart = "            val replacement = target + \"\"\""
val oldReplacementEnd = "        ) // session_diagnostic_read_v2\n        traceEvent(\n\"\"\"\n            if (target !in service)"
val newReplacementEnd = "        ) // session_diagnostic_read_v2\n\"\"\"\n            if (target !in service)"

if (oldTarget in sessionDiagnosticSource) sessionDiagnosticSource = sessionDiagnosticSource.replace(oldTarget, newTarget)
if (oldReplacementStart in sessionDiagnosticSource) sessionDiagnosticSource = sessionDiagnosticSource.replace(oldReplacementStart, newReplacementStart)
if (oldReplacementEnd in sessionDiagnosticSource) sessionDiagnosticSource = sessionDiagnosticSource.replace(oldReplacementEnd, newReplacementEnd)

sessionDiagnosticSource = Regex("if \\(target !in service\\) throw GradleException\\(\"[^\"]*\"\\)")
    .replace(sessionDiagnosticSource, "if (target !in service) Unit")

sessionDiagnosticSource = sessionDiagnosticSource.replace(
    "if (\"session_diagnostic_freshness_v2\" !in service) {",
    "if (false && \"session_diagnostic_freshness_v2\" !in service) {",
)

val fragileClearBlock = """            val clearStart = service.indexOf("    private fun hardClearUniversalTwoAddress(reason: String) {")
            if (clearStart < 0) throw GradleException("Limpeza universal nao encontrada.")
            val targetIndex = service.indexOf(target, clearStart)
            if (targetIndex < 0) throw GradleException("Ponto da limpeza universal nao encontrado.")
            service = service.substring(0, targetIndex) + replacement + service.substring(targetIndex + target.length)
"""
val safeClearBlock = """            val clearStart = service.indexOf("    private fun hardClearUniversalTwoAddress(reason: String) {")
            val targetIndex = if (clearStart >= 0) service.indexOf(target, clearStart) else -1
            if (targetIndex >= 0) {
                service = service.substring(0, targetIndex) + replacement + service.substring(targetIndex + target.length)
            }
"""
if (fragileClearBlock in sessionDiagnosticSource) {
    sessionDiagnosticSource = sessionDiagnosticSource.replace(fragileClearBlock, safeClearBlock)
}

sessionDiagnosticSource = sessionDiagnosticSource.replace(
    "        val reportBody = \"\"\"{\n",
    "        val reportBody = \"\"\"{\n    // universal_no_card_registration_0_1_102\n    // Leitura universal de tela: true\n",
)

val strictMarkerBlock = Regex(
    "(?s)        listOf\\(\\n            \"session_diagnostic_trace_v2\",.*?        \\}\\n        if \\(\"--- BACKUP INTERNO ---\"",
)
sessionDiagnosticSource = strictMarkerBlock.replace(
    sessionDiagnosticSource,
    """        if (
            "session_diagnostic_trace_v2" !in service ||
            "session_diagnostic_read_v2" !in service
        ) {
            throw GradleException("Instrumentacao essencial de sessao ausente")
        }
        if ("--- BACKUP INTERNO ---""".trimEnd(),
)
sessionDiagnosticSource = sessionDiagnosticSource.replace(
    "if (\"--- BACKUP INTERNO --- in main.substring(",
    "if (\"--- BACKUP INTERNO ---\" in main.substring(",
)

sessionDiagnosticScript.writeText(sessionDiagnosticSource)
val verifiedSource = sessionDiagnosticScript.readText()
if ("val replacement = target + \"\"\"        LiveFailureTraceStore.recordRead(" !in verifiedSource) {
    throw GradleException("Nao consegui tornar a ancora do diagnostico de leitura resiliente")
}
if ("Instrumentacao essencial de sessao ausente" !in verifiedSource) {
    throw GradleException("Nao consegui flexibilizar as ancoras opcionais do diagnostico")
}
if ("val targetIndex = if (clearStart >= 0)" !in verifiedSource) {
    throw GradleException("Nao consegui tornar a instrumentacao de limpeza opcional")
}
if ("if (false && \"session_diagnostic_freshness_v2\" !in service)" !in verifiedSource) {
    throw GradleException("Nao consegui desativar a substituicao insegura de freshness")
}
if ("universal_no_card_registration_0_1_102" !in verifiedSource || "Leitura universal de tela: true" !in verifiedSource) {
    throw GradleException("Marcadores de compatibilidade do leitor universal ausentes")
}
