// Central de bolinhas visivel dentro do aplicativo em qualquer tela.
// Nao depende do servico de Acessibilidade para aparecer.

fun enforceInAppBubbleHome(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado.")
    var text = file.readText()

    // Remove a barra de abas. As secoes continuam acessiveis pelas bolinhas.
    val scaffoldStart = text.indexOf("    Scaffold(\n        bottomBar = {")
    val scaffoldContent = if (scaffoldStart >= 0) text.indexOf("    ) { padding ->", scaffoldStart) else -1
    if (scaffoldStart >= 0 && scaffoldContent > scaffoldStart) {
        text = text.substring(0, scaffoldStart) +
            "    Scaffold(\n        bottomBar = {},\n" +
            text.substring(scaffoldContent)
    }

    // A central antiga foi inserida somente na aba Analise. Ela agora aparece antes de qualquer secao.
    val conditionalStartToken = "            if (tab == TAB_ANALYSIS) {\n                UnifiedAppControlBubbles("
    val conditionalStart = text.indexOf(conditionalStartToken)
    val whenToken = "\n            when (tab) {"
    val whenIndex = if (conditionalStart >= 0) text.indexOf(whenToken, conditionalStart) else -1
    if (conditionalStart >= 0 && whenIndex > conditionalStart) {
        val block = text.substring(conditionalStart, whenIndex)
        val firstLineEnd = block.indexOf('\n')
        var unconditional = block.substring(firstLineEnd + 1)
        val closing = unconditional.lastIndexOf("            }")
        if (closing >= 0) unconditional = unconditional.removeRange(closing, closing + "            }".length)
        text = text.substring(0, conditionalStart) + unconditional + text.substring(whenIndex)
    }

    // Sempre abre na central quando o icone do aplicativo foi tocado sem um destino interno explicito.
    val launchBlockOld = """        val requestedTab = launchIntent?.getStringExtra(EXTRA_OPEN_TAB)
        if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_TOOLS || requestedTab == TAB_HISTORY) {
            tab = requestedTab
        }
"""
    val launchBlockNew = """        val requestedTab = launchIntent?.getStringExtra(EXTRA_OPEN_TAB)
        tab = if (requestedTab == TAB_ANALYSIS || requestedTab == TAB_CONFIG || requestedTab == TAB_TOOLS || requestedTab == TAB_HISTORY) {
            requestedTab
        } else {
            TAB_ANALYSIS
        } // in_app_bubble_home_default_0_1_97
"""
    if (launchBlockOld in text) text = text.replaceFirst(launchBlockOld, launchBlockNew)

    // Estado real: sem Acessibilidade, Leitura e Acesso aparecem OFF e levam direto ao Android.
    text = text
        .replace(
            "Text(\"Central de controles\", fontWeight = FontWeight.Bold)",
            "Text(\"Central de bolinhas\", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)",
        )
        .replace(
            "\"Bolinhas com ON/OFF alternam a funcao a cada toque. As demais executam uma acao imediata.\"",
            "if (liveEnabled) \"Toque para ligar ou desligar. A bolinha flutuante esta autorizada.\" else \"Acessibilidade OFF: toque em Acesso OFF ou Leitura OFF para liberar a bolinha flutuante.\"",
        )
        .replace(
            "AppControlBubble(\"Leitura\", settings.liveReadingEnabled) { onToggle(QuickBubbleToggle.LiveReading) }",
            "AppControlBubble(\"Leitura\", settings.liveReadingEnabled && liveEnabled) { if (liveEnabled) onToggle(QuickBubbleToggle.LiveReading) else onOpenAccessibility() }",
        )
        .replace(
            "AppControlBubble(\"Acesso\", if (liveEnabled) true else null, onOpenAccessibility)",
            "AppControlBubble(\"Acesso\", liveEnabled, onOpenAccessibility)",
        )

    // O cadastro de modelos nao participa mais da leitura universal e nao deve ocupar a tela principal.
    val modelsCall = """    Spacer(Modifier.height(10.dp))
    CardModelsCard(
        cardTemplates = cardTemplates,
        templateStatus = templateStatus,
        unreadTemplatePrints = unreadTemplatePrints,
        onPickCardModels = onPickCardModels,
        onDeleteCardModel = onDeleteCardModel,
    )

"""
    text = text.replace(modelsCall, "")

    if ("in_app_bubble_home_visible_0_1_97" !in text) {
        val markerAnchor = "@Composable\nprivate fun UnifiedAppControlBubbles("
        val index = text.indexOf(markerAnchor)
        if (index < 0) throw GradleException("Central de bolinhas nao encontrada no MainActivity.")
        text = text.substring(0, index) + "// in_app_bubble_home_visible_0_1_97\n" + text.substring(index)
    }

    // Contratos que impedem repetir exatamente o defeito mostrado no video.
    listOf(
        "in_app_bubble_home_visible_0_1_97",
        "in_app_bubble_home_default_0_1_97",
        "Central de bolinhas",
        "Acessibilidade OFF: toque em Acesso OFF ou Leitura OFF",
        "AppControlBubble(\"Acesso\", liveEnabled, onOpenAccessibility)",
        "settings.liveReadingEnabled && liveEnabled",
        "bottomBar = {}",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Central interna incompleta: ${'$'}marker")
    }
    if ("if (tab == TAB_ANALYSIS) {\n                UnifiedAppControlBubbles(" in text) {
        throw GradleException("Regressao: central ainda esta limitada a uma aba.")
    }

    file.writeText(text)
}

val inAppBubbleHomeFinal by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn("mainBubbleTapMenuContract")
    doLast { enforceInAppBubbleHome(mainFile.asFile) }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(inAppBubbleHomeFinal)
    doFirst {
        enforceInAppBubbleHome(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt").asFile,
        )
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("test") }.configureEach {
    dependsOn(inAppBubbleHomeFinal)
}
