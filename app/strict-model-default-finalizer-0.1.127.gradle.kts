// Rota Certa 0.1.127
// Alguns patches historicos ainda restauram o valor antigo de cards opcionais
// em Models.kt. Este finalizador roda por ultimo e fixa o contrato de instalacao nova.

fun patchStrictModelDefaults127(modelsFile: java.io.File) {
    if (!modelsFile.exists()) {
        throw GradleException("Models.kt nao encontrado para fixar os padroes estritos 0.1.127.")
    }

    var models = modelsFile.readText()
    models = models.replace(
        "val requireRegisteredRideCard: Boolean = false,",
        "val requireRegisteredRideCard: Boolean = true, // strict_model_card_required_default_0_1_127",
    )
    models = models.replace(
        "val restrictToSelectedRideApps: Boolean = false,",
        "val restrictToSelectedRideApps: Boolean = true, // strict_model_manual_apps_default_0_1_127",
    )

    if ("val requireRegisteredRideCard: Boolean = true" !in models) {
        throw GradleException("AppSettings ainda nasce com card opcional.")
    }
    if ("val restrictToSelectedRideApps: Boolean = true" !in models) {
        throw GradleException("AppSettings ainda nasce sem restricao aos aplicativos selecionados.")
    }
    if (Regex("val requireRegisteredRideCard: Boolean = false").containsMatchIn(models)) {
        throw GradleException("Valor antigo requireRegisteredRideCard=false ainda esta em Models.kt.")
    }

    modelsFile.writeText(models)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchStrictModelDefaults127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/Models.kt").asFile,
        )
    }
}
