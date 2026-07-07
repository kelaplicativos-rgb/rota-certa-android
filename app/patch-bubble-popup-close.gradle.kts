val patchBubblePopupClose by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        if ("actionMenuItem(\"Fechar\")" !in text) {
            val openItemRegex = Regex("""(?m)^(\s*)addView\(actionMenuItem\("[^"]*Abrir Rota Certa"\) \{ openApp\(\) \}\)\s*$""")
            val match = openItemRegex.find(text)
            if (match != null) {
                val indent = match.groupValues[1]
                val replacement = "${indent}addView(actionMenuItem(\"Fechar\") { hideActionMenu() })\n${match.value}"
                text = text.replaceRange(match.range, replacement)
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
