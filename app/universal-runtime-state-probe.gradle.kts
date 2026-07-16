// Sonda somente de build debug para validar o comportamento real do serviço
// instalado. Não gera log contínuo: grava preferências apenas quando cor,
// gatilho, destino ou abertura do menu mudam.

val universalRuntimeStateProbe by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn("inAppBubbleImmediateState")

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
        var text = file.readText()

        if ("universal_runtime_probe_functions_0_1_98" !in text) {
            val functionAnchor = "    private fun toast(message: String) {\n"
            if (functionAnchor !in text) throw GradleException("Ponto das funcoes da sonda nao encontrado.")
            val functions = """    private fun publishRuntimeValidationState(color: RadarColor, distanceKm: Double?) {
        if (!BuildConfig.DEBUG) return
        val state = color.diagnosticLabel + "|" + (distanceKm?.let(::formatBubbleDistanceKm) ?: "")
        if (bubblePrefs.getString("runtime_validation_state", null) == state) return
        bubblePrefs.edit()
            .putString("runtime_validation_state", state)
            .putLong("runtime_validation_state_at", System.currentTimeMillis())
            .apply()
    }

    private fun publishRuntimeValidationTrigger(trigger: UniversalAddressTriggerDecision) {
        if (!BuildConfig.DEBUG) return
        bubblePrefs.edit()
            .putInt("runtime_visible_addresses", trigger.addresses.size)
            .putString("runtime_last_destination", trigger.destination.orEmpty())
            .putString("runtime_address_signature", trigger.addressSignature)
            .putInt("runtime_screen_hash", trigger.screenHash)
            .putLong("runtime_trigger_at", System.currentTimeMillis())
            .apply()
    }

    private fun clearRuntimeValidationTrigger() {
        if (!BuildConfig.DEBUG) return
        bubblePrefs.edit()
            .putInt("runtime_visible_addresses", 0)
            .remove("runtime_last_destination")
            .remove("runtime_address_signature")
            .remove("runtime_screen_hash")
            .putLong("runtime_clear_at", System.currentTimeMillis())
            .apply()
    }

    private fun publishRuntimeValidationMenu(open: Boolean) {
        if (!BuildConfig.DEBUG) return
        bubblePrefs.edit()
            .putBoolean("runtime_menu_open", open)
            .putLong("runtime_menu_state_at", System.currentTimeMillis())
            .apply()
    } // universal_runtime_probe_functions_0_1_98

"""
            text = text.replaceFirst(functionAnchor, functions + functionAnchor)
        }

        if ("universal_runtime_probe_color_0_1_98" !in text) {
            val start = text.indexOf("    private fun showOverlay(")
            val end = if (start >= 0) text.indexOf("\n    private fun ", start + 10) else -1
            if (start < 0 || end <= start) throw GradleException("showOverlay nao encontrado para sonda.")
            var block = text.substring(start, end)
            val closing = block.lastIndexOf("\n    }")
            if (closing < 0) throw GradleException("Fechamento de showOverlay nao encontrado para sonda.")
            block = block.substring(0, closing) +
                "\n        publishRuntimeValidationState(color, currentDistanceKm) // universal_runtime_probe_color_0_1_98\n" +
                block.substring(closing)
            text = text.substring(0, start) + block + text.substring(end)
        }

        if ("universal_runtime_probe_trigger_0_1_98" !in text) {
            val processStart = text.indexOf("    private suspend fun processRideText(")
            val processEnd = if (processStart >= 0) text.indexOf("    private fun resolveRidePackageForText(", processStart) else -1
            if (processStart < 0 || processEnd <= processStart) throw GradleException("processRideText final nao encontrado para sonda.")
            var block = text.substring(processStart, processEnd)
            val yellowAnchor = "            showOverlay(RadarColor.Default, distanceKm = null)\n"
            if (yellowAnchor !in block) throw GradleException("Gatilho amarelo nao encontrado para sonda.")
            block = block.replaceFirst(
                yellowAnchor,
                """            publishRuntimeValidationTrigger(trigger) // universal_runtime_probe_trigger_0_1_98
$yellowAnchor""",
            )
            text = text.substring(0, processStart) + block + text.substring(processEnd)
        }

        if ("universal_runtime_probe_clear_0_1_98" !in text) {
            val clearStart = text.indexOf("    private fun hardClearUniversalTwoAddress(")
            val clearEnd = if (clearStart >= 0) text.indexOf("\n    private fun ", clearStart + 10) else -1
            if (clearStart < 0 || clearEnd <= clearStart) throw GradleException("hardClearUniversalTwoAddress nao encontrado para sonda.")
            var block = text.substring(clearStart, clearEnd)
            val clearAnchor = "        currentDistanceKm = null\n"
            if (clearAnchor !in block) throw GradleException("Limpeza da distancia nao encontrada para sonda.")
            block = block.replaceFirst(
                clearAnchor,
                clearAnchor + "        clearRuntimeValidationTrigger() // universal_runtime_probe_clear_0_1_98\n",
            )
            text = text.substring(0, clearStart) + block + text.substring(clearEnd)
        }

        if ("universal_runtime_probe_menu_open_0_1_98" !in text) {
            val menuStart = text.indexOf("    private fun showActionMenu()")
            val menuEnd = if (menuStart >= 0) text.indexOf("\n    private fun hideActionMenu()", menuStart) else -1
            if (menuStart < 0 || menuEnd <= menuStart) throw GradleException("showActionMenu nao encontrado para sonda.")
            var block = text.substring(menuStart, menuEnd)
            val openAnchor = "                overlayMenuParams = params\n"
            if (openAnchor !in block) throw GradleException("Confirmacao de abertura do menu nao encontrada.")
            block = block.replaceFirst(
                openAnchor,
                openAnchor + "                publishRuntimeValidationMenu(true) // universal_runtime_probe_menu_open_0_1_98\n",
            )
            text = text.substring(0, menuStart) + block + text.substring(menuEnd)
        }

        if ("universal_runtime_probe_menu_close_0_1_98" !in text) {
            val hideStart = text.indexOf("    private fun hideActionMenu()")
            val hideEnd = if (hideStart >= 0) text.indexOf("\n    private fun ", hideStart + 10) else -1
            if (hideStart < 0 || hideEnd <= hideStart) throw GradleException("hideActionMenu nao encontrado para sonda.")
            var block = text.substring(hideStart, hideEnd)
            val closeAnchor = "        overlayMenuParams = null\n"
            if (closeAnchor !in block) throw GradleException("Confirmacao de fechamento do menu nao encontrada.")
            block = block.replaceFirst(
                closeAnchor,
                closeAnchor + "        publishRuntimeValidationMenu(false) // universal_runtime_probe_menu_close_0_1_98\n",
            )
            text = text.substring(0, hideStart) + block + text.substring(hideEnd)
        }

        listOf(
            "universal_runtime_probe_functions_0_1_98",
            "universal_runtime_probe_color_0_1_98",
            "universal_runtime_probe_trigger_0_1_98",
            "universal_runtime_probe_clear_0_1_98",
            "universal_runtime_probe_menu_open_0_1_98",
            "universal_runtime_probe_menu_close_0_1_98",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Sonda runtime incompleta: $marker")
        }

        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalRuntimeStateProbe)
}
