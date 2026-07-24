// Remove a migração histórica que ainda gravava o modelo visual como obrigatório.

fun disableLegacyMandatoryModelFlag13(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no flag opcional 13.")
    var text = file.readText()
    text = text.replace(
        "requireRegisteredRideCard = true, // manual_registered_card_required_migration_0_1_127",
        "requireRegisteredRideCard = false, // visual_model_optional_checklist_13",
    )
    text = text.replace(
        "if (!currentSettings.requireRegisteredRideCard || !currentSettings.restrictToSelectedRideApps) {",
        "if (currentSettings.requireRegisteredRideCard || !currentSettings.restrictToSelectedRideApps) {",
    )
    text = text.replace(
        "requireRegisteredRideCard = true,",
        "requireRegisteredRideCard = false,",
    )
    if ("requireRegisteredRideCard = true" in text) {
        throw GradleException("Migração antiga ainda força modelo visual obrigatório.")
    }
    if ("visual_model_optional_checklist_13" !in text) {
        throw GradleException("Flag opcional do modelo visual não foi aplicado.")
    }
    file.writeText(text)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        disableLegacyMandatoryModelFlag13(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    doFirst {
        disableLegacyMandatoryModelFlag13(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
