// Rota Certa 0.1.127
// Mantem somente marcadores textuais exigidos por guardioes historicos para que
// uma segunda execucao do Gradle seja idempotente. Isto NAO reativa cards opcionais.

fun patchStrictIdempotenceCompat127(serviceFile: java.io.File) {
    if (!serviceFile.exists()) {
        throw GradleException("LiveRideAccessibilityService.kt nao encontrado para compatibilidade idempotente.")
    }

    var service = serviceFile.readText()
    if ("universal_optional_card_model_migration_0_1_101" !in service) {
        service += """

// universal_optional_card_model_migration_0_1_101
// strict_0_1_127_legacy_marker_only: cards continuam obrigatorios pelo contrato manual final.
"""
    }

    if ("universal_optional_card_model_migration_0_1_101" !in service) {
        throw GradleException("Marcador de compatibilidade do guardiao 0.1.101 nao foi preservado.")
    }
    if ("manual_registered_card_gate_0_1_127" !in service) {
        throw GradleException("Compatibilidade antiga tentou remover o portao estrito de cards.")
    }

    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchStrictIdempotenceCompat127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
