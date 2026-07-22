val patchLiveReadingCardRestore by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = mainFile.asFile
        var text = file.readText()
        val original = text

        if ("ToolsScreenLiveReadingRestored" !in text) {
            text = text.replace(
"""        // ToolsScreenResourceGroups
        WorkRegionCard(settings = settings, onSaveSettings = onSaveSettings)
""",
"""        // ToolsScreenResourceGroups
        // ToolsScreenLiveReadingRestored
        LiveReadingCard(
            liveEnabled = liveEnabled,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onRefreshLiveState = onRefreshLiveState,
        )
        WorkRegionCard(settings = settings, onSaveSettings = onSaveSettings)
""",
            )
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchLiveReadingCardRestore.configure {
    mustRunAfter("patchUxPlacesAlertsRadars")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchLiveReadingCardRestore)
}
