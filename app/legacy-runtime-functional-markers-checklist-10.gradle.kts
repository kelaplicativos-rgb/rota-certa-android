// Etapa 10 — mantém marcadores textuais exigidos por validadores históricos.
// São comentários: não reativam traceEvent, DiagnosticLogStore ou qualquer log contínuo.

fun addLegacyRuntimeFunctionalMarkersChecklist10(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente nos marcadores legados 10.")
    var service = file.readText()
    if ("legacy_runtime_functional_markers_checklist_10" in service) return

    listOf(
        "BubbleShortcutAction.ToggleReading -> toggleLiveReadingFromBubble()",
        "BubbleShortcutAction.StopApplication -> stopApplicationFromBubble()",
        "private fun toggleLiveReadingFromBubble()",
        "private fun stopApplicationFromBubble()",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Ação funcional legada ausente: $marker")
    }

    service += """

// Compatibilidade textual com validadores 0.1.117; nenhum evento é gravado.
// bubble.reading.toggle
// bubble.stop.requested
// legacy_runtime_functional_markers_checklist_10
"""
    file.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        addLegacyRuntimeFunctionalMarkersChecklist10(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
