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

sessionDiagnosticScript.writeText(sessionDiagnosticSource)
val verifiedSource = sessionDiagnosticScript.readText()
if ("val replacement = target + \"\"\"        LiveFailureTraceStore.recordRead(" !in verifiedSource) {
    throw GradleException("Nao consegui tornar a ancora do diagnostico de leitura resiliente")
}
if ("Instrumentacao essencial de sessao ausente" !in verifiedSource) {
    throw GradleException("Nao consegui flexibilizar as ancoras opcionais do diagnostico")
}
