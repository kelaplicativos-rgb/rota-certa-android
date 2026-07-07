val patchBubblePopupClose by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        if ("actionMenuItem(\"Fechar\")" !in text) {
            text = text.replace(
"""            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(actionMenuItem("🏠  Abrir Rota Certa") { openApp() })
""",
"""            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(actionMenuItem("Fechar") { hideActionMenu() })
            addView(actionMenuItem("🏠  Abrir Rota Certa") { openApp() })
""",
            )
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchBubblePopupClose)
}
