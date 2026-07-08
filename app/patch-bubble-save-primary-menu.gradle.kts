val bubbleSavePrimaryMenu by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        val tracedSaveAction = """            addView(actionMenuItem("💾  Salvar card de corrida") {
                traceEvent("diagnostic.contract save_card step=button_clicked ok=true source=menu")
                traceEvent("bubble.save_card_button clicked")
                toast("Salvando card de corrida...")
                cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 5_000L
                hideActionMenu()
                saveCurrentRideCardFromBubble()
            })
"""
        val primarySaveAction = """            addView(actionMenuItem("✅  SALVAR CARD DESTA TELA") {
                traceEvent("diagnostic.contract save_card step=button_clicked ok=true source=menu")
                traceEvent("bubble.save_card_button clicked")
                toast("Salvando card de corrida...")
                cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 5_000L
                hideActionMenu()
                saveCurrentRideCardFromBubble()
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
$tracedSaveAction""",
            primarySaveAction + tracedOpenAction,
        )

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
"""            addView(actionMenuItem("🏠  Abrir Rota Certa") {
                traceEvent("bubble.open_app_button clicked")
                openApp()
            })
""",
            tracedOpenAction,
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
}

bubbleSavePrimaryMenu.configure {
    mustRunAfter("bubbleUnlimitedCardLearning")
    mustRunAfter("patchLiveRideBubbleActions")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(bubbleSavePrimaryMenu)
}
