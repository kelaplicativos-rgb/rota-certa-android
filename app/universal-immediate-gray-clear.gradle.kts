// Garante o contrato de limpeza universal:
// ao restarem menos de dois enderecos completos e numerados, qualquer decisao
// anterior e invalidada e a bolinha volta para cinza imediatamente.

val universalImmediateGrayClear by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalRuntimeStateProbe"))

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
        var text = file.readText()

        if ("universal_immediate_gray_clear_0_1_100" !in text) {
            val clearStart = text.indexOf("    private fun hardClearUniversalTwoAddress(")
            val clearEnd = if (clearStart >= 0) text.indexOf("\n    private fun ", clearStart + 10) else -1
            if (clearStart < 0 || clearEnd <= clearStart) {
                throw GradleException("Limpeza universal nao encontrada.")
            }

            var block = text.substring(clearStart, clearEnd)
            val overlayAnchor = "        showOverlay(RadarColor.Idle, distanceKm = null)\n"
            if (overlayAnchor !in block) {
                throw GradleException("Retorno cinza universal nao encontrado.")
            }

            if ("registeredCardGate.clear()" !in block) {
                block = block.replaceFirst(
                    overlayAnchor,
                    "        registeredCardGate.clear()\n" + overlayAnchor,
                )
            }
            block = block.replaceFirst(
                overlayAnchor,
                overlayAnchor + "        // universal_immediate_gray_clear_0_1_100\n",
            )
            text = text.substring(0, clearStart) + block + text.substring(clearEnd)
        }

        val clearStart = text.indexOf("    private fun hardClearUniversalTwoAddress(")
        val clearEnd = if (clearStart >= 0) text.indexOf("\n    private fun ", clearStart + 10) else -1
        val clearBlock = if (clearStart >= 0 && clearEnd > clearStart) text.substring(clearStart, clearEnd) else ""
        listOf(
            "registeredCardGate.clear()",
            "showOverlay(RadarColor.Idle, distanceKm = null)",
            "universal_immediate_gray_clear_0_1_100",
        ).forEach { marker ->
            if (marker !in clearBlock) throw GradleException("Limpeza cinza universal incompleta: $marker")
        }

        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalImmediateGrayClear)
}
