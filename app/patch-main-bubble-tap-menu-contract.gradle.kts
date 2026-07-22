// Contrato do toque na bolinha principal.
//
// Ate a versao 0.1.116 o toque simples abria diretamente a Home. A partir da
// 0.1.117, quando o marcador de atalhos estiver presente, este patch preserva a
// grade leve de recursos e valida o catalogo modular.

fun replaceMainBubbleRegion114(
    source: String,
    startToken: String,
    endToken: String,
    replacement: String,
    label: String,
): String {
    val start = source.indexOf(startToken)
    val end = if (start >= 0) source.indexOf(endToken, start + startToken.length) else -1
    if (start < 0 || end <= start) throw GradleException("Regiao ausente para $label")
    return source.substring(0, start) + replacement + source.substring(end)
}

fun enforceMainBubbleTapHomeContract(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado.")
    var text = file.readText()

    // Em uma segunda invocacao do Gradle, o runtime modular ja esta aplicado.
    // Nao restaurar a Home direta nem exigir os antigos seis callbacks fixos.
    if ("bubble_resource_shortcuts_runtime_0_1_117" in text) {
        listOf(
            "newView.setOnClickListener { toggleResourceShortcuts() }",
            "onShortcut = ::executeShortcutModule",
            "BubbleShortcutAction.CreateAlert",
            "BubbleShortcutAction.CreateSavedPlace",
            "BubbleShortcutAction.SaveRideCard",
            "BubbleShortcutCatalog.modules.joinToString",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Atalhos modulares 0.1.117 incompletos: $marker")
        }
        if ("BubbleShortcutActions(" in text || "onSaveAlert =" in text || "onSaveLocal =" in text) {
            throw GradleException("Atalhos 0.1.117 regrediram para callbacks fixos.")
        }
        file.writeText(text)
        return
    }

    val showOverlayStart = text.indexOf("    private fun showOverlay(")
    val removeOverlayStart = if (showOverlayStart >= 0) {
        text.indexOf("\n    private fun removeOverlay()", showOverlayStart)
    } else {
        -1
    }
    if (showOverlayStart < 0 || removeOverlayStart <= showOverlayStart) {
        throw GradleException("Nao encontrei showOverlay para ligar o toque diretamente a Home.")
    }

    var showOverlayBlock = text.substring(showOverlayStart, removeOverlayStart)
    val listenerRegex = Regex("newView\\.setOnClickListener\\s*\\{[^}]*}")
    if (!listenerRegex.containsMatchIn(showOverlayBlock)) {
        throw GradleException("Listener principal da bolinha nao encontrado.")
    }
    showOverlayBlock = showOverlayBlock.replaceFirst(
        listenerRegex,
        "newView.setOnClickListener { onMainBubbleClick() }",
    )
    text = text.substring(0, showOverlayStart) + showOverlayBlock + text.substring(removeOverlayStart)

    val directHomeHelper = """    private fun onMainBubbleClick() {
        hideActionMenu()
        traceEvent("bubble.tap.home_direct_0_1_114")
        openApp()
    }

"""
    if ("    private fun onMainBubbleClick() {" in text) {
        text = replaceMainBubbleRegion114(
            source = text,
            startToken = "    private fun onMainBubbleClick() {",
            endToken = "    private fun toggleActionMenu() {",
            replacement = directHomeHelper,
            label = "clique principal direto na Home",
        )
    } else {
        val anchor = "    private fun toggleActionMenu() {\n"
        if (anchor !in text) throw GradleException("toggleActionMenu nao encontrado.")
        text = text.replaceFirst(anchor, directHomeHelper + anchor)
    }

    text = replaceMainBubbleRegion114(
        source = text,
        startToken = "    private fun toggleActionMenu() {",
        endToken = "    private fun showActionMenu() {",
        replacement = """    private fun toggleActionMenu() {
        onMainBubbleClick()
    }

""",
        label = "desativacao do alternador do popup",
    )

    text = replaceMainBubbleRegion114(
        source = text,
        startToken = "    private fun showActionMenu() {",
        endToken = "    private fun hideActionMenu() {",
        replacement = """    private fun showActionMenu() {
        onMainBubbleClick()
    } // floating_bubble_popup_removed_0_1_114

""",
        label = "remocao da grade flutuante",
    )

    text = text
        .replace("        traceEvent(\"bubble.tap.menu_contract_0_1_96\")\n", "")
        .replace("                traceEvent(\"bubble.menu.opened grid=true\")\n", "")

    val finalShowStart = text.indexOf("    private fun showOverlay(")
    val finalShowEnd = if (finalShowStart >= 0) text.indexOf("\n    private fun removeOverlay()", finalShowStart) else -1
    val finalShowBlock = if (finalShowStart >= 0 && finalShowEnd > finalShowStart) text.substring(finalShowStart, finalShowEnd) else ""
    val clickStart = text.indexOf("    private fun onMainBubbleClick() {")
    val clickEnd = if (clickStart >= 0) text.indexOf("    private fun toggleActionMenu() {", clickStart) else -1
    val clickBlock = if (clickStart >= 0 && clickEnd > clickStart) text.substring(clickStart, clickEnd) else ""
    val menuStart = text.indexOf("    private fun showActionMenu() {")
    val menuEnd = if (menuStart >= 0) text.indexOf("    private fun hideActionMenu() {", menuStart) else -1
    val menuBlock = if (menuStart >= 0 && menuEnd > menuStart) text.substring(menuStart, menuEnd) else ""

    listOf(
        "newView.setOnClickListener { onMainBubbleClick() }",
        "private fun onMainBubbleClick()",
        "bubble.tap.home_direct_0_1_114",
        "floating_bubble_popup_removed_0_1_114",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Contrato do toque direto ausente: $marker")
    }
    if ("openApp()" !in clickBlock || "toggleActionMenu()" in clickBlock || "showActionMenu()" in clickBlock) {
        throw GradleException("Regressao: clique principal nao abre diretamente a Home.")
    }
    if ("toggleActionMenu()" in finalShowBlock || "showActionMenu()" in finalShowBlock) {
        throw GradleException("Regressao: listener principal ainda tenta abrir o popup.")
    }
    if ("GridLayout(this)" in menuBlock || "manager.addView(menu" in menuBlock) {
        throw GradleException("Regressao: grade flutuante ainda pode ser criada.")
    }
    if ("bubble.tap.menu_contract_0_1_96" in text || "bubble.menu.opened grid=true" in text) {
        throw GradleException("Regressao: marcadores do popup antigo ainda estao no runtime.")
    }

    file.writeText(text)
}

val mainBubbleTapMenuContract by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn("functionalBubbleIdempotenceFinal")
    doLast { enforceMainBubbleTapHomeContract(serviceFile.asFile) }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(mainBubbleTapMenuContract)
    doFirst {
        enforceMainBubbleTapHomeContract(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("test") }.configureEach {
    dependsOn(mainBubbleTapMenuContract)
}
