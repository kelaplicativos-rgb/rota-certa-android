// Expõe o estado real da bolinha na descrição de acessibilidade. Isso permite
// validar em Android instalado se ela ficou cinza, amarela, verde ou vermelha,
// sem depender de comparação frágil de pixels.

val universalOverlayRuntimeMetadata by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn("universalTwoAddressRuntimeFinal")

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
        var text = file.readText()
        if ("universal_overlay_runtime_metadata_0_1_98" !in text) {
            val start = text.indexOf("    private fun showOverlay(")
            val end = if (start >= 0) text.indexOf("\n    private fun ", start + 10) else -1
            if (start < 0 || end <= start) {
                throw GradleException("Funcao showOverlay gerada nao encontrada.")
            }
            var block = text.substring(start, end)
            val closing = block.lastIndexOf("\n    }")
            if (closing < 0) throw GradleException("Fechamento de showOverlay nao encontrado.")
            val metadata = """
        overlayView?.contentDescription = buildString {
            append("Rota Certa ")
            append(color.diagnosticLabel)
            currentDistanceKm?.let { append(" ").append(formatBubbleDistanceKm(it)).append(" km") }
        } // universal_overlay_runtime_metadata_0_1_98
"""
            block = block.substring(0, closing) + metadata + block.substring(closing)
            text = text.substring(0, start) + block + text.substring(end)
        }
        if ("universal_overlay_runtime_metadata_0_1_98" !in text) {
            throw GradleException("Metadados de estado da bolinha nao foram aplicados.")
        }
        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalOverlayRuntimeMetadata)
}
