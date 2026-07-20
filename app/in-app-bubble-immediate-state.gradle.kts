// Estado visual imediato para as bolinhas internas.
// A interface antiga usava controles ON/OFF dentro dos circulos. A Home 0.1.115
// usa selecao imediata de grupos e mantem os interruptores dentro de cada grupo.

val inAppBubbleImmediateState by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn("universalOverlayRuntimeMetadata")

    doLast {
        val file = mainFile.asFile
        if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado.")
        var text = file.readText()

        // Em uma segunda invocacao do Gradle, a fonte ja pode estar agrupada.
        // Nao recoloque bubbleControlSettings nem os callbacks antigos.
        if ("grouped_bubble_home_0_1_115" in text) {
            listOf(
                "grouped_bubble_state_0_1_115",
                "selectedBubbleGroup = group",
                "grouped_bubble_navigation_0_1_115",
            ).forEach { marker ->
                if (marker !in text) throw GradleException("Estado agrupado imediato ausente: $marker")
            }
            if ("settings = bubbleControlSettings," in text || "QuickBubbleToggleReducer.toggle(bubbleControlSettings" in text) {
                throw GradleException("Regressao: estado antigo ON/OFF voltou para a Home agrupada.")
            }
            file.writeText(text)
            return@doLast
        }

        if ("in_app_bubble_immediate_state_0_1_98" !in text) {
            val settingsAnchor = "    val settings by repository.settings.collectAsState(initial = AppSettings())\n"
            if (settingsAnchor !in text) {
                throw GradleException("Estado principal de configuracoes nao encontrado.")
            }
            text = text.replaceFirst(
                settingsAnchor,
                settingsAnchor + """    var bubbleControlSettings by remember(settings) { mutableStateOf(settings) } // in_app_bubble_immediate_state_0_1_98
""",
            )

            val callStart = text.indexOf("                UnifiedAppControlBubbles(")
                .takeIf { it >= 0 }
                ?: text.indexOf("            UnifiedAppControlBubbles(")
            val callEnd = if (callStart >= 0) text.indexOf("                )", callStart) else -1
            if (callStart < 0 || callEnd <= callStart) {
                throw GradleException("Chamada da Central de bolinhas nao encontrada.")
            }

            var callBlock = text.substring(callStart, callEnd + "                )".length)
            callBlock = callBlock.replaceFirst(
                "settings = settings,",
                "settings = bubbleControlSettings,",
            )

            val oldCallback = """                    onToggle = { toggle ->
                         scope.launch { repository.saveSettings(QuickBubbleToggleReducer.toggle(settings, toggle)) }
                     },
"""
            val newCallback = """                    onToggle = { toggle ->
                         val updated = QuickBubbleToggleReducer.toggle(bubbleControlSettings, toggle)
                         bubbleControlSettings = updated
                         scope.launch { repository.saveSettings(updated) }
                     },
"""
            if (oldCallback !in callBlock) {
                throw GradleException("Callback antigo das bolinhas internas nao encontrado.")
            }
            callBlock = callBlock.replaceFirst(oldCallback, newCallback)
            text = text.substring(0, callStart) + callBlock + text.substring(callEnd + "                )".length)
        }

        listOf(
            "in_app_bubble_immediate_state_0_1_98",
            "settings = bubbleControlSettings,",
            "val updated = QuickBubbleToggleReducer.toggle(bubbleControlSettings, toggle)",
            "bubbleControlSettings = updated",
            "repository.saveSettings(updated)",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Estado imediato ausente: $marker")
        }

        val callStart = text.indexOf("UnifiedAppControlBubbles(")
        val callEnd = if (callStart >= 0) text.indexOf("\n                )", callStart) else -1
        val callBlock = if (callStart >= 0 && callEnd > callStart) text.substring(callStart, callEnd) else ""
        if ("QuickBubbleToggleReducer.toggle(settings, toggle)" in callBlock) {
            throw GradleException("Regressao: callback ainda usa estado atrasado do DataStore.")
        }

        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(inAppBubbleImmediateState)
}
