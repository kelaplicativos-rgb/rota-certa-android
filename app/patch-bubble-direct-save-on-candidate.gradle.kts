val bubbleDirectSaveOnCandidate by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        if ("private fun hasFreshBubbleCardCandidate()" !in text) {
            text = text.replace(
"""    private fun toggleActionMenu() {
""",
"""    private fun hasFreshBubbleCardCandidate(): Boolean {
        val ageMillis = System.currentTimeMillis() - lastCardSaveCandidateAtMillis
        return lastCardSaveCandidateText.isNotBlank() &&
            !isBubbleActionMenuText(lastCardSaveCandidateText) &&
            ageMillis in 0L..15_000L
    }

    private fun toggleActionMenu() {
""",
            )
        }

        text = text.replace(
"""    private fun toggleActionMenu() {
        if (overlayMenuView != null) {
            traceEvent("diagnostic.contract bubble_menu step=second_bubble_tap ok=true")
            triggerBubbleSaveFromAction("bubble_second_tap")
        } else {
            showActionMenu()
        }
    }
""",
"""    private fun toggleActionMenu() {
        if (overlayMenuView != null) {
            traceEvent("diagnostic.contract bubble_menu step=second_bubble_tap ok=true")
            triggerBubbleSaveFromAction("bubble_second_tap")
        } else if (hasFreshBubbleCardCandidate()) {
            traceEvent("diagnostic.contract bubble_menu step=direct_bubble_tap ok=true candidate_len=${'$'}{lastCardSaveCandidateText.length} candidate_package=${'$'}{lastCardSaveCandidatePackageName.orEmpty()}")
            triggerBubbleSaveFromAction("bubble_direct_tap")
        } else {
            showActionMenu()
        }
    }
""",
        )

        text = text.replace(
"""        snapshotCurrentCardCandidateForBubbleAction("menu_open")
        traceEvent("diagnostic.contract bubble_menu step=opened ok=true expected_first=save_card candidate_len=${'$'}{lastCardSaveCandidateText.length} candidate_package=${'$'}{lastCardSaveCandidatePackageName.orEmpty()}")
        traceEvent("bubble.menu_open primary_save_first")
        toast("Primeiro botao: SALVAR CARD")
        val manager = windowManager ?: return
""",
"""        snapshotCurrentCardCandidateForBubbleAction("menu_open")
        if (hasFreshBubbleCardCandidate()) {
            traceEvent("diagnostic.contract bubble_menu step=menu_open_autosave ok=true candidate_len=${'$'}{lastCardSaveCandidateText.length} candidate_package=${'$'}{lastCardSaveCandidatePackageName.orEmpty()}")
            triggerBubbleSaveFromAction("menu_open_autosave")
            return
        }
        traceEvent("diagnostic.contract bubble_menu step=opened ok=true expected_first=save_card candidate_len=${'$'}{lastCardSaveCandidateText.length} candidate_package=${'$'}{lastCardSaveCandidatePackageName.orEmpty()}")
        traceEvent("bubble.menu_open primary_save_first")
        toast("Primeiro botao: SALVAR CARD")
        val manager = windowManager ?: return
""",
        )

        text = text.replace(
"""    val secondBubbleTap = has("diagnostic.contract bubble_menu step=second_bubble_tap")
    val saveStarted = has("bubble.save_card_start") || has("diagnostic.contract save_card step=started")
""",
"""    val secondBubbleTap = has("diagnostic.contract bubble_menu step=second_bubble_tap")
    val directBubbleTap = has("diagnostic.contract bubble_menu step=direct_bubble_tap")
    val menuOpenAutosave = has("diagnostic.contract bubble_menu step=menu_open_autosave")
    val saveStarted = has("bubble.save_card_start") || has("diagnostic.contract save_card step=started")
""",
        )

        text = text.replace(
"""        secondBubbleTap && !saveStarted -> "O usuario tocou de novo na bolinha para salvar, mas a rotina de salvamento nao iniciou."
        topRegionDown && !topRegionUp -> "O toque chegou na area do botao Salvar Card, mas nao terminou com soltura valida."
""",
"""        secondBubbleTap && !saveStarted -> "O usuario tocou de novo na bolinha para salvar, mas a rotina de salvamento nao iniciou."
        directBubbleTap && !saveStarted -> "A bolinha tentou salvar direto, mas a rotina de salvamento nao iniciou."
        menuOpenAutosave && !saveStarted -> "O menu detectou candidato e tentou salvar automaticamente, mas a rotina nao iniciou."
        topRegionDown && !topRegionUp -> "O toque chegou na area do botao Salvar Card, mas nao terminou com soltura valida."
""",
        )

        text = text.replace(
"""        put("segundoToqueBolinha", secondBubbleTap)
        put("salvamentoIniciado", saveStarted)
""",
"""        put("segundoToqueBolinha", secondBubbleTap)
        put("toqueDiretoBolinha", directBubbleTap)
        put("salvamentoAutomaticoAoAbrirMenu", menuOpenAutosave)
        put("salvamentoIniciado", saveStarted)
""",
        )

        if (text != original) file.writeText(text)
    }
}

bubbleDirectSaveOnCandidate.configure {
    mustRunAfter("bubbleSavePrimaryMenu")
    mustRunAfter("bubbleUnlimitedCardLearning")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleDirectSaveOnCandidate)
}
