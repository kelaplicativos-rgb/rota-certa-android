val universalLastAddressCompileFix by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        text = text.replace(
            "destination=${'$'}{destination.diagnosticValue()}",
            "destination=${'$'}{destination.replace(Regex(\"\\\\s+\"), \" \ ").take(80)}",
        )
        if ("destination.diagnosticValue()" in text) {
            throw GradleException("A leitura universal ainda depende do helper de diagnostico removido.")
        }
        file.writeText(text)
    }
}

universalLastAddressCompileFix.configure {
    mustRunAfter("universalLastAddressFinalV2")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalLastAddressCompileFix)
}
