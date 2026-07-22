val patchBubbleStateReportCompileFix by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text
        text = text.replace(
            "currentDistanceKm?.let(::formatDiagnosticKm).orEmpty()",
            "currentDistanceKm?.let { value -> String.format(Locale(\"pt\", \"BR\"), \"%.1fkm\", value) }.orEmpty()",
        )
        if (text != original) file.writeText(text)
    }
}

patchBubbleStateReportCompileFix.configure {
    mustRunAfter("patchBubbleStateReport")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchBubbleStateReportCompileFix)
}
