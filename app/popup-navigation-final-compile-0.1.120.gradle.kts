// Ultima camada de compilacao 0.1.120.
// Executa depois dos patches legados que ainda podem reconstruir chamadas da
// SettingsScreen e garante que o modulo de Cards receba os dados reais.

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

fun applyPopupNavigationLateCompile120(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado.")
    var text = file.readText()

    // Defaults mantem qualquer chamada legada compilavel. A chamada principal
    // abaixo recebe os valores reais e continua totalmente funcional.
    val settingsStart = text.indexOf("@Composable\nprivate fun SettingsScreen(")
    val settingsEnd = if (settingsStart >= 0) text.indexOf(") {", settingsStart) else -1
    if (settingsStart < 0 || settingsEnd <= settingsStart) {
        throw GradleException("Assinatura SettingsScreen nao encontrada.")
    }
    var signature = text.substring(settingsStart, settingsEnd)
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
    text = text.substring(0, settingsStart) + signature + text.substring(settingsEnd)

    // Injeta os dados reais na navegacao principal, independentemente do
    // formato/espacamento reconstruido pelos patches anteriores.
    val tabMarker = "TAB_CONFIG -> SettingsScreen("
    val callStart = text.indexOf(tabMarker)
    val callEnd = if (callStart >= 0) findBalancedCallEnd120(text, callStart) else -1
    if (callStart < 0 || callEnd <= callStart) {
        throw GradleException("Chamada principal SettingsScreen nao encontrada.")
    }
    var call = text.substring(callStart, callEnd + 1)
    if ("cardTemplates = cardTemplates" !in call) {
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
        text = text.substring(0, callStart) + call + text.substring(callEnd + 1)
    }

    if ("popup_navigation_final_compile_0_1_120" !in text) {
        text += "\n// popup_navigation_final_compile_0_1_120\n"
    }

    listOf(
        "cardTemplates: List<RideCardTemplate> = emptyList()",
        "templateStatus: String = \"\"",
        "onPickCardModels: () -> Unit = {}",
        "cardTemplates = cardTemplates",
        "onPickCardModels = { cardModelPicker.launch(\"image/*\") }",
        "popup_navigation_final_compile_0_1_120",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Ligacao final de Cards incompleta: $marker")
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
