val bubbleSavePrimaryMenu by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        val tracedSaveAction = """            addView(actionMenuItem("💾  Salvar card de corrida") {
                traceEvent("bubble.save_card_button clicked")
                toast("Salvando card de corrida...")
                cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 5_000L
                hideActionMenu()
                saveCurrentRideCardFromBubble()
            })
"""
        val primarySaveAction = """            addView(actionMenuItem("✅  SALVAR CARD DESTA TELA") {
                traceEvent("bubble.save_card_button clicked")
                toast("Salvando card de corrida...")
                cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 5_000L
                hideActionMenu()
                saveCurrentRideCardFromBubble()
            })
"""
        val tracedOpenAction = """            addView(actionMenuItem("🏠  Abrir Rota Certa") {
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
        traceEvent("bubble.menu_open primary_save_first")
        toast("Primeiro botao: SALVAR CARD")
        val manager = windowManager ?: return
""",
            )
        }

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
