// Garante o contrato de limpeza universal sem contornar o controlador unico da bolinha.
// Na linha 0.1.128 o retorno cinza continua imediato quando a saida real do card e
// confirmada, mas eventos transitorios passam pela confirmacao e pelo coordenador.

val universalImmediateGrayClear by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalRuntimeStateProbe"))

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
        var text = file.readText()

        val clearStart = text.indexOf("    private fun hardClearUniversalTwoAddress(")
        val clearEnd = if (clearStart >= 0) text.indexOf("\n    private fun ", clearStart + 10) else -1
        if (clearStart < 0 || clearEnd <= clearStart) {
            throw GradleException("Limpeza universal nao encontrada.")
        }

        var block = text.substring(clearStart, clearEnd)
        if ("universal_immediate_gray_clear_0_1_100" !in block) {
            val legacyAnchor = "        showOverlay(RadarColor.Idle, distanceKm = null)\n"
            val coordinatedAnchor = "            showOverlay(RadarColor.Idle, distanceKm = null, reason = reason, force = true)\n"

            block = when {
                coordinatedAnchor in block -> block.replaceFirst(
                    coordinatedAnchor,
                    coordinatedAnchor + "            // universal_immediate_gray_clear_0_1_100\n",
                )
                legacyAnchor in block -> block.replaceFirst(
                    legacyAnchor,
                    legacyAnchor + "        // universal_immediate_gray_clear_0_1_100\n",
                )
                else -> throw GradleException("Retorno cinza universal nao encontrado.")
            }
            text = text.substring(0, clearStart) + block + text.substring(clearEnd)
        }

        val verifiedStart = text.indexOf("    private fun hardClearUniversalTwoAddress(")
        val verifiedEnd = if (verifiedStart >= 0) text.indexOf("\n    private fun ", verifiedStart + 10) else -1
        val clearBlock = if (verifiedStart >= 0 && verifiedEnd > verifiedStart) {
            text.substring(verifiedStart, verifiedEnd)
        } else {
            ""
        }

        listOf(
            "registeredCardGate.clear()",
            "currentRadarColor = RadarColor.Idle",
            "setColor(RadarColor.Idle.argb(currentSettings))",
            ".putString(\"runtime_validation_state\", \"cinza|\")",
            "universal_immediate_gray_clear_0_1_100",
        ).forEach { marker ->
            if (marker !in clearBlock) throw GradleException("Limpeza cinza universal incompleta: $marker")
        }
        val hasGrayRender =
            "showOverlay(RadarColor.Idle, distanceKm = null)" in clearBlock ||
                "showOverlay(RadarColor.Idle, distanceKm = null, reason = reason, force = true)" in clearBlock
        if (!hasGrayRender) throw GradleException("Renderizacao cinza universal incompleta.")

        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalImmediateGrayClear)
}
