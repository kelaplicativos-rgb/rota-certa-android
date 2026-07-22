// Rota Certa 0.1.127
// Corrige a migracao provisoria para manter a exigencia de modelo de card manual.

fun patchManualRegisteredCardMigration127(serviceFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para corrigir a migracao de cards.")
    var service = serviceFile.readText()
    val migrationStart = service.indexOf("            val manualSelectionPrefs127")
    val migrationEnd = if (migrationStart >= 0) {
        service.indexOf("            currentCardTemplates = repository.cardTemplates.first()", migrationStart)
    } else {
        -1
    }
    if (migrationStart < 0 || migrationEnd < 0) {
        throw GradleException("Migracao manual 0.1.127 nao encontrada.")
    }
    var migrationRegion = service.substring(migrationStart, migrationEnd)
    migrationRegion = migrationRegion.replace(
        "requireRegisteredRideCard = false,",
        "requireRegisteredRideCard = true, // manual_registered_card_required_migration_0_1_127",
    )
    if ("manual_registered_card_required_migration_0_1_127" !in migrationRegion) {
        throw GradleException("Nao foi possivel tornar o modelo de card obrigatorio na migracao 0.1.127.")
    }
    service = service.substring(0, migrationStart) + migrationRegion + service.substring(migrationEnd)
    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchManualRegisteredCardMigration127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
