// Contrato final do toque na bolinha principal.
// O APK 0.1.95 continha a grade funcional, mas o listener compilado ainda chamava openApp().
// Este patch roda no ultimo instante antes da compilacao e impede a regressao.

fun enforceMainBubbleTapMenuContract(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
    var text = file.readText()

    val showOverlayStart = text.indexOf("    private fun showOverlay(")
    val removeOverlayStart = if (showOverlayStart >= 0) text.indexOf("\n    private fun removeOverlay()", showOverlayStart) else -1
    if (showOverlayStart < 0 || removeOverlayStart < 0) {
        throw GradleException("Nao encontrei showOverlay para ligar o toque ao painel de bolinhas.")
    }

    var showOverlayBlock = text.substring(showOverlayStart, removeOverlayStart)
    showOverlayBlock = showOverlayBlock
        .replace("newView.setOnClickListener { openApp() }", "newView.setOnClickListener { onMainBubbleClick() }")
        .replace("newView.setOnClickListener { toggleActionMenu() }", "newView.setOnClickListener { onMainBubbleClick() }")
        .replace(
            Regex("newView\\.setOnClickListener\\s*\\{\\s*openApp\\([^}]*\\)\\s*}"),
            "newView.setOnClickListener { onMainBubbleClick() }",
        )

    if ("newView.setOnClickListener { onMainBubbleClick() }" !in showOverlayBlock) {
        val listenerRegex = Regex("newView\\.setOnClickListener\\s*\\{[^}]*}")
        if (!listenerRegex.containsMatchIn(showOverlayBlock)) {
            throw GradleException("Listener principal da bolinha nao encontrado.")
        }
        showOverlayBlock = showOverlayBlock.replaceFirst(
            listenerRegex,
            "newView.setOnClickListener { onMainBubbleClick() }",
        )
    }
    text = text.substring(0, showOverlayStart) + showOverlayBlock + text.substring(removeOverlayStart)

    if ("private fun onMainBubbleClick()" !in text) {
        val anchor = "    private fun toggleActionMenu() {\n"
        if (anchor !in text) throw GradleException("toggleActionMenu nao encontrado.")
        val helper = """    private fun onMainBubbleClick() {
        traceEvent("bubble.tap.menu_contract_0_1_96")
        toggleActionMenu()
    }

"""
        text = text.replaceFirst(anchor, helper + anchor)
    }

    // Nao deixa falha de WindowManager silenciosa: o usuario precisa saber se o Android recusou o painel.
    val silentAdd = """        if (runCatching { manager.addView(menu, params) }.isSuccess) {
            overlayMenuView = menu
            overlayMenuParams = params
        }
"""
    if (silentAdd in text) {
        text = text.replaceFirst(
            silentAdd,
            """        runCatching { manager.addView(menu, params) }
            .onSuccess {
                overlayMenuView = menu
                overlayMenuParams = params
                traceEvent("bubble.menu.opened grid=true")
            }
            .onFailure { error ->
                traceEvent("bubble.menu.open_failed ${'$'}{error::class.java.simpleName}: ${'$'}{error.message.orEmpty()}")
                toast("Nao foi possivel abrir as bolinhas. Reative a Acessibilidade do Rota Certa.")
            }
""",
        )
    }

    val finalShowStart = text.indexOf("    private fun showOverlay(")
    val finalShowEnd = if (finalShowStart >= 0) text.indexOf("\n    private fun removeOverlay()", finalShowStart) else -1
    val finalShowBlock = if (finalShowStart >= 0 && finalShowEnd > finalShowStart) {
        text.substring(finalShowStart, finalShowEnd)
    } else {
        ""
    }

    listOf(
        "newView.setOnClickListener { onMainBubbleClick() }",
        "private fun onMainBubbleClick()",
        "bubble.tap.menu_contract_0_1_96",
        "private fun showActionMenu()",
        "GridLayout(this)",
        "quickToggleBubbleButton(\"Rota\"",
        "quickToggleBubbleButton(\"Leitura\"",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Contrato do painel ausente: ${'$'}marker")
    }
    if ("setOnClickListener { openApp" in finalShowBlock || "openApp${'$'}default" in finalShowBlock) {
        throw GradleException("Regressao: toque principal ainda abre o app em vez das bolinhas.")
    }

    file.writeText(text)
}

val mainBubbleTapMenuContract by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn("functionalBubbleIdempotenceFinal")
    doLast { enforceMainBubbleTapMenuContract(serviceFile.asFile) }
}

// Executa novamente imediatamente antes do compilador Kotlin. Assim nenhum patch independente
// consegue recolocar openApp() depois da correcao.
tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(mainBubbleTapMenuContract)
    doFirst {
        enforceMainBubbleTapMenuContract(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("test") }.configureEach {
    dependsOn(mainBubbleTapMenuContract)
}
