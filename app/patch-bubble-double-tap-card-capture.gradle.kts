val bubbleDoubleTapCardCapture by tasks.registering {
    outputs.upToDateWhen { false }
    doLast {
        logger.lifecycle("Rota Certa: double-tap bubble capture disabled; single tap opens the app home.")
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubbleDoubleTapCardCapture)
}
