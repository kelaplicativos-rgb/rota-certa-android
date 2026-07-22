// Garante o contrato padrao final da 0.1.127 depois de todos os patches antigos.
// Aplicativos permitidos dependem da selecao manual; modelos de cards sao opcionais.

val manualOptionalModelsFinalizer127 by tasks.registering {
    val modelsFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/Models.kt")
    inputs.file(modelsFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = modelsFile.asFile
        if (!file.exists()) throw GradleException("Models.kt nao encontrado para o contrato opcional 0.1.127.")
        var text = file.readText()

        text = text
            .replace(
                "val restrictToSelectedRideApps: Boolean = false,",
                "val restrictToSelectedRideApps: Boolean = true,",
            )
            .replace(
                "val requireRegisteredRideCard: Boolean = true,",
                "val requireRegisteredRideCard: Boolean = false,",
            )
            .replace(
                "val monitor99: Boolean = true,",
                "val monitor99: Boolean = false,",
            )
            .replace(
                "val monitorUber: Boolean = true,",
                "val monitorUber: Boolean = false,",
            )
            .replace(
                "val monitorInDrive: Boolean = true,",
                "val monitorInDrive: Boolean = false,",
            )

        listOf(
            "val restrictToSelectedRideApps: Boolean = true,",
            "val requireRegisteredRideCard: Boolean = false,",
            "val monitor99: Boolean = false,",
            "val monitorUber: Boolean = false,",
            "val monitorInDrive: Boolean = false,",
        ).forEach { required ->
            if (required !in text) throw GradleException("Padrao 0.1.127 ausente em Models.kt: $required")
        }

        if ("manual_models_optional_finalizer_0_1_127" !in text) {
            text += "\n// manual_models_optional_finalizer_0_1_127\n"
        }
        file.writeText(text)
    }
}

manualOptionalModelsFinalizer127.configure {
    mustRunAfter(
        tasks.matching { task ->
            task.name != name &&
                task.name !in setOf("preBuild", "assemble", "assembleDebug") &&
                !task.name.startsWith("compile") &&
                !task.name.startsWith("test") &&
                (task.name.contains("patch", ignoreCase = true) ||
                    task.name.contains("fix", ignoreCase = true) ||
                    task.name.contains("strict", ignoreCase = true) ||
                    task.name.contains("final", ignoreCase = true))
        },
    )
}

tasks.matching { task ->
    task.name == "preBuild" || task.name.startsWith("compile") || task.name.startsWith("test")
}.configureEach {
    dependsOn(manualOptionalModelsFinalizer127)
}
