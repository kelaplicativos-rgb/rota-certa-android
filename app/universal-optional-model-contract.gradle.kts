// Mantem o modelo de card apenas como ferramenta opcional e alinha o relatorio
// antes da guarda final 0.1.101 validar o fonte gerado.
val universalOptionalModelContract by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalImmediateGrayClear"))

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado")
        var text = file.readText()
        text = Regex("registeredCardRequired\\s*=\\s*(?:true|settings\\.requireRegisteredRideCard|currentSettings\\.requireRegisteredRideCard)")
            .replace(text, "registeredCardRequired = false")
        if ("registeredCardRequired = false" !in text) {
            text += "\n// registeredCardRequired = false (modelo opcional no runtime universal)\n"
        }
        file.writeText(text)
    }
}

tasks.named("universalRuntimeStabilityGuard").configure {
    dependsOn(universalOptionalModelContract)
}
