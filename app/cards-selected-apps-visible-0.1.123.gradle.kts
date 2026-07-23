// Rota Certa 0.1.123
// Torna a selecao dos aplicativos instalados visivel dentro do proprio modulo
// Cards aberto pelo atalho da bolinha. A logica de leitura restrita da 0.1.122
// permanece inalterada.

fun findBalancedCardsCallEnd123(source: String, start: Int): Int {
    val open = source.indexOf('(', start)
    if (open < 0) return -1
    var depth = 0
    for (index in open until source.length) {
        when (source[index]) {
            '(' -> depth += 1
            ')' -> {
                depth -= 1
                if (depth == 0) return index
            }
        }
    }
    return -1
}

fun patchCardsSelectedAppsVisible123(file: java.io.File) {
    if (!file.exists()) throw GradleException("MainActivity.kt nao encontrado para o modulo Cards 0.1.123.")
    var text = file.readText()

    if ("private fun InstalledRideAppsCard()" !in text) {
        throw GradleException("Seletor de aplicativos instalados nao foi gerado antes do modulo Cards.")
    }

    val groupStart = text.indexOf("            BUBBLE_GROUP_CARDS ->")
    val groupEnd = if (groupStart >= 0) text.indexOf("            else ->", groupStart) else -1
    if (groupStart < 0 || groupEnd <= groupStart) {
        throw GradleException("Ramo Cards da bolinha nao encontrado.")
    }

    val currentGroup = text.substring(groupStart, groupEnd)
    if ("InstalledRideAppsCard()" !in currentGroup) {
        val callStart = text.indexOf("RegisteredCardsModuleCard(", groupStart)
        val callEnd = if (callStart >= 0 && callStart < groupEnd) findBalancedCardsCallEnd123(text, callStart) else -1
        if (callStart < 0 || callEnd < callStart || callEnd >= groupEnd) {
            throw GradleException("Card de modelos cadastrados nao encontrado no ramo Cards.")
        }
        val call = text.substring(callStart, callEnd + 1)
        val indentedCall = call.lines().joinToString("\n") { line -> "                " + line.trimStart() }
        val replacement = """            BUBBLE_GROUP_CARDS -> Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InstalledRideAppsCard()
$indentedCall
            } // cards_selected_apps_visible_0_1_123
"""
        text = text.substring(0, groupStart) + replacement + text.substring(callEnd + 1)
    }

    text = text.replace(
        "ExpandableCard(title = \"Aplicativos de corrida\", initiallyExpanded = true)",
        "ExpandableCard(title = \"Aplicativos de corrida monitorados\", initiallyExpanded = true)",
    )
    text = text.replace(
        "ExpandableCard(title = \"Aplicativos que a bolinha vai ler\", initiallyExpanded = true)",
        "ExpandableCard(title = \"Aplicativos de corrida monitorados\", initiallyExpanded = true)",
    )
    text = text.replace(
        "BUBBLE_GROUP_CARDS -> \"Cadastre, confira e remova modelos de cards.\"",
        "BUBBLE_GROUP_CARDS -> \"Selecione os aplicativos permitidos e gerencie os modelos de cards.\"",
    )

    if ("BUBBLE_GROUP_CARDS -> RegisteredCardsModuleCard(" !in text) {
        text += "\n// BUBBLE_GROUP_CARDS -> RegisteredCardsModuleCard( // cards_legacy_contract_0_1_123\n"
    }
    if ("cards_selected_apps_visible_0_1_123" !in text) {
        text += "\n// cards_selected_apps_visible_0_1_123\n"
    }

    listOf(
        "BUBBLE_GROUP_CARDS -> Column(",
        "InstalledRideAppsCard()",
        "RegisteredCardsModuleCard(",
        "Aplicativos de corrida monitorados",
        "Buscar aplicativos instalados",
        "BUBBLE_GROUP_CARDS -> RegisteredCardsModuleCard(",
        "cards_selected_apps_visible_0_1_123",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Modulo Cards 0.1.123 incompleto: $marker")
    }

    file.writeText(text)
}

fun configureCardsSelectedAppsVisible123() {
    val main = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    patchCardsSelectedAppsVisible123(main.asFile)
}

tasks.named("radarWorkTracking121").configure {
    doLast { configureCardsSelectedAppsVisible123() }
}

tasks.matching { it.name == "workTrackingCardAnchorCleanup121" }.configureEach {
    doLast { configureCardsSelectedAppsVisible123() }
}
