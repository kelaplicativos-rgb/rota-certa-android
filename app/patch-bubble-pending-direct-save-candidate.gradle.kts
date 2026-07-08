val bubblePendingDirectSaveCandidate by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        patchPendingDirectCandidate(serviceFile.asFile)
        patchSaveStartedVerdict(mainFile.asFile)
    }
}

fun patchPendingDirectCandidate(file: java.io.File) {
    var text = file.readText()
    val original = text

    if ("pendingDirectCardSaveText" !in text) {
        text = text.replace(
"""    private var cardSaveScreenshotRequestedUntilMillis: Long = 0L
""",
"""    private var cardSaveScreenshotRequestedUntilMillis: Long = 0L
    private var pendingDirectCardSavePackageName: String? = null
    private var pendingDirectCardSaveText: String = ""
    private var pendingDirectCardSaveAtMillis: Long = 0L
""",
        )
    }

    if ("private fun stashPendingDirectCardSaveCandidate" !in text) {
        text = text.replace(
"""    private fun triggerBubbleSaveFromAction(source: String) {
        traceEvent("diagnostic.contract save_card step=button_clicked ok=true source=${'$'}source")
        traceEvent("bubble.save_card_button clicked source=${'$'}source")
        toast("Salvando card de corrida...")
        cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 5_000L
        hideActionMenu()
        saveCurrentRideCardFromBubble()
    }
""",
"""    private fun stashPendingDirectCardSaveCandidate(source: String) {
        val packageName = lastCardSaveCandidatePackageName?.takeIf { it.isNotBlank() }
        val text = lastCardSaveCandidateText.takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
        if (packageName != null && text != null) {
            pendingDirectCardSavePackageName = packageName
            pendingDirectCardSaveText = text
            pendingDirectCardSaveAtMillis = System.currentTimeMillis()
            traceEvent("direct_save_candidate.stashed source=${'$'}source package=${'$'}packageName length=${'$'}{text.length}")
        } else {
            traceEvent("direct_save_candidate.missing source=${'$'}source last_len=${'$'}{lastCardSaveCandidateText.length}")
        }
    }

    private fun pendingDirectCardSaveCandidate(reason: String): Pair<String, String>? {
        val ageMillis = System.currentTimeMillis() - pendingDirectCardSaveAtMillis
        val packageName = pendingDirectCardSavePackageName?.takeIf { it.isNotBlank() }
        val text = pendingDirectCardSaveText.takeIf { it.isNotBlank() && !isBubbleActionMenuText(it) }
        if (packageName != null && text != null && ageMillis in 0L..20_000L) {
            traceEvent("direct_save_candidate.use_pending reason=${'$'}reason package=${'$'}packageName age_ms=${'$'}ageMillis length=${'$'}{text.length}")
            return packageName to text
        }
        return null
    }

    private fun triggerBubbleSaveFromAction(source: String) {
        stashPendingDirectCardSaveCandidate(source)
        traceEvent("diagnostic.contract save_card step=button_clicked ok=true source=${'$'}source")
        traceEvent("bubble.save_card_button clicked source=${'$'}source")
        toast("Salvando card de corrida...")
        cardSaveScreenshotRequestedUntilMillis = System.currentTimeMillis() + 5_000L
        hideActionMenu()
        saveCurrentRideCardFromBubble()
    }
""",
        )
    }

    text = text.replace(
"""            var candidate = bestCardSaveCandidate(livePackageName, liveText)
""",
"""            var candidate = bestCardSaveCandidate(livePackageName, liveText)
                ?: pendingDirectCardSaveCandidate("before_screenshot")
""",
    )

    text = text.replace(
"""                    candidate = bestCardSaveCandidate(null, "")
""",
"""                    candidate = bestCardSaveCandidate(null, "")
                        ?: pendingDirectCardSaveCandidate("after_screenshot_attempt")
""",
    )

    text = text.replace(
"""                traceEvent("diagnostic.contract save_card result=fail reason=text_blank")
                toast("Abra o card de corrida e tente salvar novamente.")
""",
"""                traceEvent("diagnostic.contract save_card result=fail reason=text_blank pending_len=${'$'}{pendingDirectCardSaveText.length} last_len=${'$'}{lastCardSaveCandidateText.length}")
                toast("Abra o card de corrida e tente salvar novamente.")
""",
    )

    if (text != original) file.writeText(text)
}

fun patchSaveStartedVerdict(file: java.io.File) {
    var text = file.readText()
    val original = text

    text = text.replace(
"""    val saveStarted = has("bubble.save_card_start") || has("diagnostic.contract save_card step=started")
""",
"""    val saveStarted = stage == "bubble_save_card" ||
        stage == "bubble_save_card_empty" ||
        stage == "bubble_save_card_missing_package" ||
        has("bubble.save_card_start") ||
        has("diagnostic.contract save_card step=started")
""",
    )

    text = text.replace(
"""        saveButtonClicked && !saveStarted -> "O comando Salvar Card foi acionado, mas a rotina de salvamento nao iniciou."
""",
"""        saveButtonClicked && !saveStarted -> "O comando Salvar Card foi acionado, mas a rotina de salvamento nao iniciou."
""",
    )

    if (text != original) file.writeText(text)
}

bubblePendingDirectSaveCandidate.configure {
    mustRunAfter("bubbleDirectSaveOnCandidate")
    mustRunAfter("bubbleSavePrimaryMenu")
    mustRunAfter("bubbleUnlimitedCardLearning")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubblePendingDirectSaveCandidate)
}
