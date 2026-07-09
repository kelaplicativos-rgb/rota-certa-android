val bubbleLongPressDirectSaveAfterOcr by tasks.registering {
    outputs.upToDateWhen { false }
    doLast {
        logger.lifecycle("Rota Certa: long-press direct save disabled; bubble no longer captures screenshots by gesture.")
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleLongPressDirectSaveAfterOcr)
}
