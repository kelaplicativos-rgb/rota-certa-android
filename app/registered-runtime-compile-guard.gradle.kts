// Executa por ultimo para impedir que sondas de runtime antigas reintroduzam
// referencias do leitor universal depois do contrato de card cadastrado.

val registeredRuntimeCompileGuard by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalRuntimeStateProbe"))
    dependsOn(tasks.named("registeredCardStableAddressRuntime"))

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
        var text = file.readText()

        // selectedRidePackages ja exclui SystemUI, DocumentsUI e qualquer app
        // que nao seja 99/Uber/inDrive/pacote extra. Nao dependa de constantes
        // removidas por patches legados.
        text = text
            .replace("        if (normalized in PASSIVE_DIAGNOSTIC_PACKAGES) return false\n", "")
            .replace("        if (normalized in IGNORED_PACKAGES) return false\n", "")

        // A sonda universal antiga usa a variavel trigger. O leitor final usa
        // addressDecision e matchedCard; remova qualquer injecao atrasada.
        text = text.replace(
            "            publishRuntimeValidationTrigger(trigger) // universal_runtime_probe_trigger_0_1_98\n",
            "",
        )

        if ("registered_runtime_probe_trigger_0_1_99" !in text) {
            val processStart = text.indexOf("    private suspend fun processRideText(")
            val processEnd = if (processStart >= 0) text.indexOf("    private fun resolveRidePackageForText(", processStart) else -1
            if (processStart < 0 || processEnd <= processStart) throw GradleException("processRideText cadastrado nao encontrado.")
            var block = text.substring(processStart, processEnd)
            val anchor = "            showOverlay(RadarColor.Default, distanceKm = null)\n"
            if (anchor !in block) throw GradleException("Ponto amarelo cadastrado nao encontrado.")
            val probe = """            if (BuildConfig.DEBUG) {
                bubblePrefs.edit()
                    .putInt("runtime_visible_addresses", addressDecision.addresses.size)
                    .putString("runtime_last_destination", addressDecision.destination.orEmpty())
                    .putString("runtime_address_signature", addressDecision.addressSignature)
                    .putInt("runtime_screen_hash", screenHash)
                    .putString("runtime_registered_template", matchedCard.template.id)
                    .putLong("runtime_trigger_at", System.currentTimeMillis())
                    .apply()
            } // registered_runtime_probe_trigger_0_1_99
"""
            block = block.replaceFirst(anchor, probe + anchor)
            text = text.substring(0, processStart) + block + text.substring(processEnd)
        }

        // A limpeza da sonda tambem precisa acompanhar a limpeza real do card.
        text = text.replace(
            "        clearRuntimeValidationTrigger() // universal_runtime_probe_clear_0_1_98\n",
            "",
        )
        if ("registered_runtime_probe_clear_0_1_99" !in text) {
            val clearStart = text.indexOf("    private fun hardClearUniversalTwoAddress(")
            val clearEnd = if (clearStart >= 0) text.indexOf("\n    private fun ", clearStart + 10) else -1
            if (clearStart < 0 || clearEnd <= clearStart) throw GradleException("Limpeza cadastrada nao encontrada.")
            var block = text.substring(clearStart, clearEnd)
            val anchor = "        currentDistanceKm = null\n"
            if (anchor !in block) throw GradleException("Distancia nao encontrada na limpeza cadastrada.")
            val probe = """        if (BuildConfig.DEBUG) {
            bubblePrefs.edit()
                .putInt("runtime_visible_addresses", 0)
                .remove("runtime_last_destination")
                .remove("runtime_address_signature")
                .remove("runtime_screen_hash")
                .remove("runtime_registered_template")
                .putLong("runtime_clear_at", System.currentTimeMillis())
                .apply()
        } // registered_runtime_probe_clear_0_1_99
"""
            block = block.replaceFirst(anchor, anchor + probe)
            text = text.substring(0, clearStart) + block + text.substring(clearEnd)
        }

        listOf(
            "registered_stable_process_0_1_99",
            "registered_stable_package_filter_0_1_99",
            "registered_runtime_probe_trigger_0_1_99",
            "registered_runtime_probe_clear_0_1_99",
            "normalized in selectedRidePackages(currentSettings)",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Guarda final incompleta: $marker")
        }
        if ("publishRuntimeValidationTrigger(trigger)" in text) {
            throw GradleException("Sonda universal antiga voltou a referenciar trigger.")
        }
        if ("PASSIVE_DIAGNOSTIC_PACKAGES" in text.substring(
                text.indexOf("    private fun shouldScanPackage("),
                text.indexOf("    private fun selectedRidePackages("),
            )
        ) {
            throw GradleException("Filtro final ainda depende de constante passiva legada.")
        }

        file.writeText(text)
    }
}

tasks.named("registeredCardStableAddressRuntime").configure {
    mustRunAfter(tasks.named("universalRuntimeStateProbe"))
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(registeredRuntimeCompileGuard)
}
