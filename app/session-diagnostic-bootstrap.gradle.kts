// Ajusta o script de instrumentacao antes de ele ser aplicado. O runtime final
// pode inserir metadados entre a avaliacao do gatilho e o traceEvent; por isso a
// ancora deve ser apenas a linha estavel do UniversalAddressTrigger.
val sessionDiagnosticScript = layout.projectDirectory.file("session-diagnostic-v2.gradle.kts").asFile
if (!sessionDiagnosticScript.exists()) throw GradleException("session-diagnostic-v2.gradle.kts nao encontrado")
var sessionDiagnosticSource = sessionDiagnosticScript.readText()
val fragileReadBlock = """            val target = """        val trigger = UniversalAddressTrigger.evaluate(snapshotText)
        traceEvent(
"""
            val replacement = """        val trigger = UniversalAddressTrigger.evaluate(snapshotText)
        LiveFailureTraceStore.recordRead(
            source = source.toString(),
            packageName = currentWindowPackageName(),
            text = snapshotText,
            addresses = trigger.addresses,
            destination = trigger.destination,
            active = trigger.active,
            screenHash = trigger.screenHash,
            generation = if (lastSnapshotHash != trigger.screenHash) universalScreenGeneration + 1L else universalScreenGeneration,
        ) // session_diagnostic_read_v2
        traceEvent(
"""
"""
val robustReadBlock = """            val target = """        val trigger = UniversalAddressTrigger.evaluate(snapshotText)
"""
            val replacement = target + """        LiveFailureTraceStore.recordRead(
            source = source.toString(),
            packageName = currentWindowPackageName(),
            text = snapshotText,
            addresses = trigger.addresses,
            destination = trigger.destination,
            active = trigger.active,
            screenHash = trigger.screenHash,
            generation = if (lastSnapshotHash != trigger.screenHash) universalScreenGeneration + 1L else universalScreenGeneration,
        ) // session_diagnostic_read_v2
"""
"""
if (fragileReadBlock in sessionDiagnosticSource) {
    sessionDiagnosticSource = sessionDiagnosticSource.replace(fragileReadBlock, robustReadBlock)
    sessionDiagnosticScript.writeText(sessionDiagnosticSource)
}
if ("val replacement = target + \"\"\"        LiveFailureTraceStore.recordRead(" !in sessionDiagnosticScript.readText()) {
    throw GradleException("Nao consegui tornar a ancora do diagnostico de leitura resiliente")
}
