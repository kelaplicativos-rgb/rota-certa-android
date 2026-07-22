// Preserva as marcas dos estagios anteriores para que a segunda chamada do Gradle
// nao tente reconstruir a central que ja foi substituida pelas bolinhas funcionais.

val functionalBubbleIdempotenceFinal by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val mainFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
    inputs.files(serviceFile, mainFile)
    outputs.upToDateWhen { false }

    doLast {
        val service = serviceFile.asFile
        var serviceText = service.readText()
        if ("unified_bubble_grid_0_1_94" !in serviceText) {
            serviceText += "\n// unified_bubble_grid_0_1_94 preserved_by_functional_bubbles\n"
            service.writeText(serviceText)
        }

        val main = mainFile.asFile
        var mainText = main.readText()
        if ("unified_app_control_bubbles_0_1_94" !in mainText) {
            mainText += "\n// unified_app_control_bubbles_0_1_94 preserved_by_functional_bubbles\n"
            main.writeText(mainText)
        }
    }
}

functionalBubbleIdempotenceFinal.configure {
    dependsOn("functionalBubbleTogglesFinal")
    mustRunAfter("functionalBubbleTogglesFinal")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(functionalBubbleIdempotenceFinal)
}
