// Checklist 6 — orçamento curto para a primeira resposta exata do Google Routes.

fun patchSubsecondNetworkBudgetChecklist6(file: java.io.File) {
    if (!file.exists()) throw GradleException("GoogleMapsService.kt ausente no checklist 6.")
    var text = file.readText()
    if ("subsecond_connect_budget_checklist_6" !in text) {
        text = text.replace(
            Regex("const val CONNECT_TIMEOUT_MS = [0-9_]+"),
            "const val CONNECT_TIMEOUT_MS = 350 // subsecond_connect_budget_checklist_6",
        )
    }
    if ("subsecond_read_budget_checklist_6" !in text) {
        text = text.replace(
            Regex("const val READ_TIMEOUT_MS = [0-9_]+"),
            "const val READ_TIMEOUT_MS = 600 // subsecond_read_budget_checklist_6",
        )
    }
    if ("single_route_attempt_checklist_6" !in text) {
        text = text.replace(
            Regex("const val ROUTE_REQUEST_ATTEMPTS = [0-9_]+"),
            "const val ROUTE_REQUEST_ATTEMPTS = 1 // single_route_attempt_checklist_6",
        )
    }
    listOf(
        "subsecond_connect_budget_checklist_6",
        "subsecond_read_budget_checklist_6",
        "single_route_attempt_checklist_6",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Orçamento de rede ausente: $marker")
    }
    file.writeText(text)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchSubsecondNetworkBudgetChecklist6(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt").asFile,
        )
    }
}

// Registrado aqui para manter o aplicador principal da etapa compacto.
apply(from = "capture-store-temp-safety-checklist-6.gradle.kts")
