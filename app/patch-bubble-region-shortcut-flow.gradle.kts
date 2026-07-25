val bubbleRegionShortcutFlow by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        patchBubbleRegionShortcutService(serviceFile.asFile)
    }
}

bubbleRegionShortcutFlow.configure {
    mustRunAfter(
        "shortcutNavigationIdleReset",
        "patchBubbleShortcutClipboard",
        "patchUxPlacesAlertsRadars",
        "patchLiveReadingCardRestore",
        "patchBubbleCardParity",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleRegionShortcutFlow)
}

fun patchBubbleRegionShortcutService(file: java.io.File) {
    var text = file.readText()
    val original = text

    if ("private fun openRideRegionShortcut()" !in text) {
        text = text.replace(
"""    private fun openApp(tab: String? = null, expander: String? = null) {
""",
"""    private fun openRideRegionShortcut() {
        hideActionMenu()
        forceIdleOverlay("Abrindo Minha regiao de corridas pela bolinha.")
        traceEvent("shortcut.region.open gps_flow=true")
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_OPEN_TAB, TAB_ANALYSIS)
                    .putExtra("br.com.mapeiaia.rotacerta.extra.OPEN_EXPANDER", "Minha regiao de corridas"),
            )
        }.onFailure {
            toast("Nao consegui abrir Minha regiao de corridas agora.")
        }
    }

    private fun openApp(tab: String? = null, expander: String? = null) {
""",
        )
    }

    text = text.replace("label = \"🎯  Definir região de trabalho\"", "label = \"🎯  Minha região de corridas\"")
    text = text.replace("label = \"🎯  Definir regiao de trabalho\"", "label = \"🎯  Minha regiao de corridas\"")

    listOf(
        "action = { openApp(tab = TAB_TOOLS, expander = \"Definir regiao de trabalho\") }",
        "longAction = { openApp(tab = TAB_TOOLS, expander = \"Definir regiao de trabalho\") }",
        "action = { openApp(tab = TAB_TOOLS, expander = \"Minha regiao de corridas\") }",
        "longAction = { openApp(tab = TAB_TOOLS, expander = \"Minha regiao de corridas\") }",
        "action = { openApp(tab = TAB_ANALYSIS, expander = \"Minha regiao de corridas\") }",
        "longAction = { openApp(tab = TAB_ANALYSIS, expander = \"Minha regiao de corridas\") }",
    ).forEach { oldLine ->
        val replacement = oldLine.substringBefore("=") + "= { openRideRegionShortcut() }"
        text = text.replace(oldLine, replacement)
    }

    if ("bubble_region_shortcut_flow.patch_applied" !in text) {
        text = text.replace(
            "        traceEvent(\"shortcut.navigation.patch_applied=true\")\n",
            "        traceEvent(\"shortcut.navigation.patch_applied=true\")\n        traceEvent(\"bubble_region_shortcut_flow.patch_applied=true\")\n",
        )
    }

    if (text != original) file.writeText(text)
}
