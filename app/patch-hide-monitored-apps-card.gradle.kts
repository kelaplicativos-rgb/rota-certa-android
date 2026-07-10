val hideMonitoredAppsCard by tasks.registering {
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.file(mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = mainFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text

        text = text.replace(
            "        MonitoredAppsCard(settings = draft, onChange = ::saveDraft)\n",
            "",
        )

        if (text != original) file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(hideMonitoredAppsCard)
}
