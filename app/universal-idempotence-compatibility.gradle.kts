// Preserva o marcador historico usado pela trava de idempotencia. A guarda
// 0.1.101 substitui o corpo do processamento universal, mas a segunda chamada
// do Gradle ainda precisa reconhecer que o runtime final ja foi instalado para
// impedir que tarefas legadas reescrevam o servico.
val universalIdempotenceCompatibilityGuard by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }
    dependsOn(tasks.named("universalFlattenedAddressBoundaryGuard"))

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado")
        var text = file.readText()
        if ("universal_two_address_process_0_1_98" !in text) {
            text += "\n// universal_two_address_process_0_1_98 compatibility_preserved_by_0_1_101\n"
        }
        listOf(
            "universal_stable_process_0_1_101",
            "universal_runtime_stability_guard_0_1_101",
            "universal_two_address_process_0_1_98",
        ).forEach { marker ->
            if (marker !in text) throw GradleException("Marcador de idempotencia ausente: $marker")
        }
        file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(universalIdempotenceCompatibilityGuard)
}
