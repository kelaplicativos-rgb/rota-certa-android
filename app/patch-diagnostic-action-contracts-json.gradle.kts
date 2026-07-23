val diagnosticActionContractsJson by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = mainFile.asFile
        var text = file.readText()
        val original = text

        if ("actionDiagnostics" !in text) {
            text = text.replace(
"""        put("logs", org.json.JSONArray().apply {
            diagnosticLog.lines().filter { it.isNotBlank() }.forEach { put(it) }
        })
""",
"""        put("logs", org.json.JSONArray().apply {
            diagnosticLog.lines().filter { it.isNotBlank() }.forEach { put(it) }
        })
        put("actionDiagnostics", diagnosticActionJson(diagnosticLog, stage))
""",
            )
        }

        if ("private fun diagnosticActionJson(" !in text) {
            text = text.replace(
"""private fun savedPlaceTypeLabel(place: SavedPlace): String = when (place.type) {
""",
"""private fun diagnosticActionJson(diagnosticLog: String, stage: String): org.json.JSONObject {
    val lines = diagnosticLog.lines().filter { it.isNotBlank() }
    fun has(value: String): Boolean = lines.any { it.contains(value) }
    fun count(value: String): Int = lines.count { it.contains(value) }

    val menuOpened = has("bubble.menu_open") || has("diagnostic.contract bubble_menu step=opened")
    val saveButtonClicked = has("bubble.save_card_button clicked") || has("diagnostic.contract save_card step=button_clicked")
    val openAppClicked = has("bubble.open_app_button clicked") || has("reason=open_app_clicked")
    val menuItemDown = has("bubble.menu_item_down") || has("diagnostic.contract menu_item step=down")
    val menuItemUp = has("bubble.menu_item_up") || has("diagnostic.contract menu_item step=up")
    val saveStarted = has("bubble.save_card_start") || has("diagnostic.contract save_card step=started")
    val saveSuccess = stage == "bubble_save_card" || has("diagnostic.contract save_card result=success")
    val textBlankFail = has("reason=text_blank") || stage == "bubble_save_card_empty"
    val missingPackageFail = has("reason=missing_package") || stage == "bubble_save_card_missing_package"
    val candidateCount = count("card_save_candidate.remember")
    val screenshotBlockedCount = count("screenshot.request skipped")

    val verdict = when {
        saveSuccess -> "Card salvo com sucesso."
        openAppClicked && !saveButtonClicked -> "O menu abriu, mas o usuario tocou em Abrir Rota Certa em vez de Salvar Card."
        menuOpened && candidateCount > 0 && !saveButtonClicked -> "O menu abriu e havia texto candidato, mas o botao Salvar Card nao foi acionado."
        saveButtonClicked && !saveStarted -> "O botao Salvar Card foi tocado, mas a rotina de salvamento nao iniciou."
        saveStarted && textBlankFail -> "A rotina de salvamento iniciou, mas nao havia texto suficiente para salvar."
        saveStarted && missingPackageFail -> "A rotina de salvamento iniciou, mas o pacote da tela nao foi identificado."
        saveStarted && !saveSuccess -> "A rotina de salvamento iniciou, mas nao confirmou sucesso."
        menuItemDown && !menuItemUp -> "O toque no item do menu começou, mas nao terminou com soltura valida."
        menuOpened && screenshotBlockedCount > 0 -> "O menu abriu, mas prints foram bloqueados por pacote ainda nao monitorado."
        else -> "Sem falha de acao identificada pelo diagnostico automatico."
    }

    return org.json.JSONObject().apply {
        put("veredito", verdict)
        put("menuAberto", menuOpened)
        put("botaoSalvarTocado", saveButtonClicked)
        put("botaoAbrirRotaCertaTocado", openAppClicked)
        put("toqueMenuIniciado", menuItemDown)
        put("toqueMenuFinalizado", menuItemUp)
        put("salvamentoIniciado", saveStarted)
        put("salvamentoConfirmado", saveSuccess)
        put("falhaTextoVazio", textBlankFail)
        put("falhaPacoteNaoIdentificado", missingPackageFail)
        put("candidatosDeCard", candidateCount)
        put("printsBloqueados", screenshotBlockedCount)
    }
}

private fun savedPlaceTypeLabel(place: SavedPlace): String = when (place.type) {
""",
            )
        }

        if (text != original) file.writeText(text)
    }
}

diagnosticActionContractsJson.configure {
    mustRunAfter("diagnosticJsonToolsActions")
    mustRunAfter("bubbleSavePrimaryMenu")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(diagnosticActionContractsJson)
}
