val patchBubblePopupClose by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        if ("actionMenuItem(\"Fechar\")" !in text) {
            val menuPadding = "            setPadding(dp(8), dp(8), dp(8), dp(8))\n"
            val closeItem = "            addView(actionMenuItem(\"Fechar\") { hideActionMenu() })\n"
            if (menuPadding in text) {
                text = text.replaceFirst(menuPadding, menuPadding + closeItem)
            } else {
                val openAppItem = "            addView(actionMenuItem(\"🏠  Abrir Rota Certa\") { openApp() })\n"
                if (openAppItem in text) {
                    text = text.replaceFirst(openAppItem, closeItem + openAppItem)
                }
            }
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchBubblePopupClose)
}
