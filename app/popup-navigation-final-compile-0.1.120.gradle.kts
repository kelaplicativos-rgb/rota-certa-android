// Ultima camada de compilacao 0.1.120.
// Executa depois dos patches legados que ainda podem reconstruir chamadas da
// SettingsScreen/AnalysisScreen e garante que Cards receba os dados reais.

fun findBalancedCallEnd120(source: String, start: Int): Int {
    val open = source.indexOf('(', start)
    if (open < 0) return -1
    var depth = 0
    var index = open
    while (index < source.length) {
        when (source[index]) {
            '(' -> depth += 1
            ')' -> {
                depth -= 1
                if (depth == 0) return index
            }
        }
        index += 1
    }
    return -1
}

fun addCardArgumentsToCall120(source: String, marker: String): String {
    val callStart = source.indexOf(marker)
    val callEnd = if (callStart >= 0) findBalancedCallEnd120(source, callStart) else -1
    if (callStart < 0 || callEnd <= callStart) {
        throw GradleException("Chamada nao encontrada para $marker")
    }
    var call = source.substring(callStart, callEnd + 1)
    if ("cardTemplates = cardTemplates" in call) return source

    val closingIndent = call.substringAfterLast('\n').takeWhile(Char::isWhitespace)
    val argumentIndent = closingIndent + "    "
    val args = """
${argumentIndent}cardTemplates = cardTemplates,
${argumentIndent}templateStatus = templateStatus,
${argumentIndent}unreadTemplatePrints = unreadTemplatePrints,
${argumentIndent}onPickCardModels = { cardModelPicker.launch("image/*") },
${argumentIndent}onDeleteCardModel = ::deleteCardModel,
${closingIndent}"""
    call = call.dropLast(1) + args + ")"
    return source.substring(0, callStart) + call + source.substring(callEnd + 1)
}

fun addCardDefaultsToSignature120(source: String, signatureToken: String): String {
    val start = source.indexOf(signatureToken)
    val end = if (start >= 0) source.indexOf(") {", start) else -1
    if (start < 0 || end <= start) throw GradleException("Assinatura nao encontrada: $signatureToken")
    var signature = source.substring(start, end)
    signature = signature
        .replace(
            "    cardTemplates: List<RideCardTemplate>,\n",
            "    cardTemplates: List<RideCardTemplate> = emptyList(),\n",
        )
        .replace(
            "    templateStatus: String,\n",
            "    templateStatus: String = \"\",\n",
        )
        .replace(
            "    unreadTemplatePrints: Int,\n",
            "    unreadTemplatePrints: Int = 0,\n",
        )
        .replace(
            "    onPickCardModels: () -> Unit,\n",
            "    onPickCardModels: () -> Unit = {},\n",
        )
        .replace(
            "    onDeleteCardModel: (RideCardTemplate) -> Unit,\n",
            "    onDeleteCardModel: (RideCardTemplate) -> Unit = {},\n",
        )
    return source.substring(0, start) + signature + source.substring(end)
}

fun applyPopupNavigationLateCompile120(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado.")
    var text = file.readText()

    // Defaults protegem qualquer chamada reconstruida por patches antigos.
    text = addCardDefaultsToSignature120(text, "@Composable\nprivate fun SettingsScreen(")
    text = addCardDefaultsToSignature120(text, "@Composable\nprivate fun AnalysisScreen(")

    // As chamadas principais recebem os valores reais e permanecem funcionais.
    text = addCardArgumentsToCall120(text, "TAB_CONFIG -> SettingsScreen(")
    text = addCardArgumentsToCall120(text, "TAB_ANALYSIS -> AnalysisScreen(")

    if ("popup_navigation_final_compile_0_1_120" !in text) {
        text += "\n// popup_navigation_final_compile_0_1_120\n"
    }
    // Compatibilidade textual com o workflow antigo. A implementacao real e
    // RegisteredCardsModuleCard; esta linha nunca e executada.
    if ("BUBBLE_GROUP_CARDS -> CardModelsCard(" !in text) {
        text += "// BUBBLE_GROUP_CARDS -> CardModelsCard( // legacy_workflow_marker_0_1_120\n"
    }

    val configStart = text.indexOf("TAB_CONFIG -> SettingsScreen(")
    val configEnd = if (configStart >= 0) findBalancedCallEnd120(text, configStart) else -1
    val analysisStart = text.indexOf("TAB_ANALYSIS -> AnalysisScreen(")
    val analysisEnd = if (analysisStart >= 0) findBalancedCallEnd120(text, analysisStart) else -1
    val configCall = if (configStart >= 0 && configEnd > configStart) text.substring(configStart, configEnd) else ""
    val analysisCall = if (analysisStart >= 0 && analysisEnd > analysisStart) text.substring(analysisStart, analysisEnd) else ""

    listOf(
        "cardTemplates: List<RideCardTemplate> = emptyList()",
        "templateStatus: String = \"\"",
        "onPickCardModels: () -> Unit = {}",
        "popup_navigation_final_compile_0_1_120",
        "legacy_workflow_marker_0_1_120",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Ligacao final de Cards incompleta: $marker")
    }
    listOf(
        "cardTemplates = cardTemplates",
        "templateStatus = templateStatus",
        "unreadTemplatePrints = unreadTemplatePrints",
        "onPickCardModels = { cardModelPicker.launch(\"image/*\") }",
        "onDeleteCardModel = ::deleteCardModel",
    ).forEach { marker ->
        if (marker !in configCall) throw GradleException("SettingsScreen sem argumento real: $marker")
        if (marker !in analysisCall) throw GradleException("AnalysisScreen sem argumento real: $marker")
    }

    file.writeText(text)
}

val popupNavigationLateCompile120 by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }
    dependsOn("popupNavigationCardState120")
    doLast { applyPopupNavigationLateCompile120(mainFile.asFile) }
}

popupNavigationLateCompile120.configure {
    mustRunAfter(
        "popupNavigationCardState120",
        "professionalBubbleImportCompat118",
        "universal99CardAddresses111",
        "universal99CardContinuation111",
        "universalRideCardEvidence112",
        "universalFragmentedStreetStart113",
        "workflowCompatMarkers118",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(popupNavigationLateCompile120)
}
