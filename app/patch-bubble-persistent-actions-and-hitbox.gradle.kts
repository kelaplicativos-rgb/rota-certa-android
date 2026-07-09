val bubblePersistentActionsAndHitbox by tasks.registering {
    outputs.upToDateWhen { false }
    doLast {
        logger.lifecycle("Rota Certa: persistent bubble gesture diagnostics disabled; bubble opens home directly.")
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(bubblePersistentActionsAndHitbox)
}
