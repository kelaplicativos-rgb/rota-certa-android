val bubbleSavePrimaryMenu by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        patchBubbleSaveContracts(serviceFile.asFile)
        patchDiagnosticActionVerdicts(mainFile.asFile)
    }
}

fun patchBubbleSaveContracts(file: java.io.File) {
    var text = file.readText()
    val original = text

    val primarySaveAction = """            addView(actionMenuItem("✅  SALVAR CARD DESTA TELA") {
                triggerBubbleSaveFromAction("menu_button")
            })
"""
    val tracedOpenAction = """            addView(actionMenuItem("🏠  Abrir Rota Certa") {
                traceEvent("diagnostic.contract save_card step=not_executed ok=false reason=open_app_clicked")
                traceEvent("bubble.open_app_button clicked")
                openApp()
            })
"""

    text = text.replace(
"""            addView(actionMenuItem("🏠  Abrir Rota Certa") { openApp() })
            addView(actionMenuItem("💾  Salvar card de corrida") {
                hideActionMenu()
                saveCurrentRideCardFromBubble()
            })
""",
        primarySaveAction + tracedOpenAction,
    )

    text = text.replace(
"""            addView(actionMenuItem("💾  Salvar card de corrida") {
                traceEvent("bubble.save_card_button clicked")
                toast("Salvando card de corrida...")
                cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 5_000L
                hideActionMenu()
                saveCurrentRideCardFromBubble()
            })
""",
        primarySaveAction,
    )

    text = text.replace(
"""            addView(actionMenuItem("💾  Salvar card de corrida") {
                traceEvent("diagnostic.contract save_card step=button_clicked ok=true source=menu")
                traceEvent("bubble.save_card_button clicked")
                toast("Salvando card de corrida...")
                cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 5_000L
                hideActionMenu()
                saveCurrentRideCardFromBubble()
            })
""",
        primarySaveAction,
    )

    text = text.replace(
"""            addView(actionMenuItem("✅  SALVAR CARD DESTA TELA") {
                traceEvent("diagnostic.contract save_card step=button_clicked ok=true source=menu")
                traceEvent("bubble.save_card_button clicked")
                toast("Salvando card de corrida...")
                cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 5_000L
                hideActionMenu()
                saveCurrentRideCardFromBubble()
            })
""",
        primarySaveAction,
    )

    text = text.replace(
"""            addView(actionMenuItem("🏠  Abrir Rota Certa") {
                traceEvent("bubble.open_app_button clicked")
                openApp()
            })
""",
        tracedOpenAction,
    )

    if ("private fun triggerBubbleSaveFromAction(" !in text) {
        text = text.replace(
"""    private fun toggleActionMenu() {
""",
"""    private fun triggerBubbleSaveFromAction(source: String) {
        traceEvent("diagnostic.contract save_card step=button_clicked ok=true source=${'$'}source")
        traceEvent("bubble.save_card_button clicked source=${'$'}source")
        toast("Salvando card de corrida...")
        cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 5_000L
        hideActionMenu()
        saveCurrentRideCardFromBubble()
    }

    private fun toggleActionMenu() {
""",
        )
    }

    text = text.replace(
"""    private fun toggleActionMenu() {
        if (overlayMenuView != null) {
            hideActionMenu()
        } else {
            showActionMenu()
        }
    }
""",
"""    private fun toggleActionMenu() {
        if (overlayMenuView != null) {
            traceEvent("diagnostic.contract bubble_menu step=second_bubble_tap ok=true")
            triggerBubbleSaveFromAction("bubble_second_tap")
        } else {
            showActionMenu()
        }
    }
""",
    )

    text = text.replace(
"""            text = label
            textSize = 15f
""",
"""            text = label
            textSize = if (label.contains("SALVAR CARD")) 17f else 15f
""",
    )
    text = text.replace(
"""            minHeight = dp(42)
""",
"""            minHeight = if (label.contains("SALVAR CARD")) dp(58) else dp(42)
""",
    )

    if ("bubble.menu_open primary_save_first" !in text) {
        text = text.replace(
"""        snapshotCurrentCardCandidateForBubbleAction("menu_open")
        val manager = windowManager ?: return
""",
"""        snapshotCurrentCardCandidateForBubbleAction("menu_open")
        traceEvent("diagnostic.contract bubble_menu step=opened ok=true expected_first=save_card candidate_len=${'$'}{lastCardSaveCandidateText.length} candidate_package=${'$'}{lastCardSaveCandidatePackageName.orEmpty()}")
        traceEvent("bubble.menu_open primary_save_first")
        toast("Primeiro botao: SALVAR CARD")
        val manager = windowManager ?: return
""",
        )
    }

    if ("bubble_menu step=top_region_up" !in text) {
        text = text.replace(
"""            setPadding(dp(8), dp(8), dp(8), dp(8))
""",
"""            setPadding(dp(8), dp(8), dp(8), dp(8))
            isClickable = true
            setOnTouchListener { _, event ->
                val isTopSaveRegion = event.y <= dp(66)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (isTopSaveRegion) {
                            traceEvent("diagnostic.contract bubble_menu step=top_region_down ok=true")
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isTopSaveRegion) {
                            traceEvent("diagnostic.contract bubble_menu step=top_region_up ok=true")
                            triggerBubbleSaveFromAction("menu_top_region")
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> isTopSaveRegion
                    else -> isTopSaveRegion
                }
            }
""",
        )
    }

    text = text.replace(
"""                        traceEvent("bubble.menu_item_down label=${'$'}{label.take(24)}")
""",
"""                        traceEvent("diagnostic.contract menu_item step=down ok=true label=${'$'}{label.take(40)}")
                        traceEvent("bubble.menu_item_down label=${'$'}{label.take(24)}")
""",
    )
    text = text.replace(
"""                        traceEvent("bubble.menu_item_up label=${'$'}{label.take(24)}")
""",
"""                        traceEvent("diagnostic.contract menu_item step=up ok=true label=${'$'}{label.take(40)}")
                        traceEvent("bubble.menu_item_up label=${'$'}{label.take(24)}")
""",
    )

    text = text.replace(
"""            traceEvent("bubble.save_card_start")
""",
"""            traceEvent("diagnostic.contract save_card step=started ok=true")
            traceEvent("bubble.save_card_start")
""",
    )

    text = text.replace(
"""                toast("Abra o card de corrida e tente salvar novamente.")
""",
"""                traceEvent("diagnostic.contract save_card result=fail reason=text_blank")
                toast("Abra o card de corrida e tente salvar novamente.")
""",
    )
    text = text.replace(
"""                toast("Nao consegui identificar o pacote da tela para salvar.")
""",
"""                traceEvent("diagnostic.contract save_card result=fail reason=missing_package")
                toast("Nao consegui identificar o pacote da tela para salvar.")
""",
    )
    text = text.replace(
"""            toast("Card de corrida salvo.")
""",
"""            traceEvent("diagnostic.contract save_card result=success package=${'$'}inferredPackage text_len=${'$'}{text.length}")
            toast("Card de corrida salvo.")
""",
    )

    if (text != original) file.writeText(text)
}

fun patchDiagnosticActionVerdicts(file: java.io.File) {
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
    val topRegionDown = has("diagnostic.contract bubble_menu step=top_region_down")
    val topRegionUp = has("diagnostic.contract bubble_menu step=top_region_up")
    val secondBubbleTap = has("diagnostic.contract bubble_menu step=second_bubble_tap")
    val saveStarted = has("bubble.save_card_start") || has("diagnostic.contract save_card step=started")
    val saveSuccess = stage == "bubble_save_card" || has("diagnostic.contract save_card result=success")
    val textBlankFail = has("reason=text_blank") || stage == "bubble_save_card_empty"
    val missingPackageFail = has("reason=missing_package") || stage == "bubble_save_card_missing_package"
    val candidateCount = count("card_save_candidate.remember")
    val screenshotBlockedCount = count("screenshot.request skipped")

    val verdict = when {
        saveSuccess -> "Card salvo com sucesso."
        openAppClicked && !saveButtonClicked -> "O menu abriu, mas o usuario tocou em Abrir Rota Certa em vez de Salvar Card."
        secondBubbleTap && !saveStarted -> "O usuario tocou de novo na bolinha para salvar, mas a rotina de salvamento nao iniciou."
        topRegionDown && !topRegionUp -> "O toque chegou na area do botao Salvar Card, mas nao terminou com soltura valida."
        menuOpened && candidateCount > 0 && !saveButtonClicked -> "O menu abriu e havia texto candidato, mas nenhum caminho de salvar foi acionado."
        saveButtonClicked && !saveStarted -> "O comando Salvar Card foi acionado, mas a rotina de salvamento nao iniciou."
        saveStarted && textBlankFail -> "A rotina de salvamento iniciou, mas nao havia texto suficiente para salvar."
        saveStarted && missingPackageFail -> "A rotina de salvamento iniciou, mas o pacote da tela nao foi identificado."
        saveStarted && !saveSuccess -> "A rotina de salvamento iniciou, mas nao confirmou sucesso."
        menuItemDown && !menuItemUp -> "O toque no item do menu comecou, mas nao terminou com soltura valida."
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
        put("toqueRegiaoSalvarIniciado", topRegionDown)
        put("toqueRegiaoSalvarFinalizado", topRegionUp)
        put("segundoToqueBolinha", secondBubbleTap)
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

bubbleSavePrimaryMenu.configure {
    mustRunAfter("bubbleUnlimitedCardLearning")
    mustRunAfter("patchLiveRideBubbleActions")
    mustRunAfter("diagnosticJsonToolsActions")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleSavePrimaryMenu)
}
