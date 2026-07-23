val bubbleLongPressCaptureSave by tasks.registering {
    outputs.upToDateWhen { false }
    doLast {
        logger.lifecycle("Rota Certa: long-press bubble capture disabled; single tap opens the app home.")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(bubbleLongPressCaptureSave)
}
