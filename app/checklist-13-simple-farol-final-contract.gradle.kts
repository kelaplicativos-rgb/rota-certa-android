// Contrato final da etapa 13 — sem contrato visual no caminho crítico.

fun verifySimpleFarolChecklist13(root: java.io.File) {
    val service = java.io.File(root, "LiveRideAccessibilityService.kt").readText()
    val maps = java.io.File(root, "GoogleMapsService.kt").readText()
    val models = java.io.File(root, "Models.kt").readText()
    val report = java.io.File(root, "ManualTechnicalReportBuilder.kt").readText()

    listOf(
        "simple_saved_app_event_contract_checklist_13",
        "immediate_screen_change_clear_checklist_13",
        "immediate_accessibility_process_checklist_13",
        "simple_two_address_clear_checklist_13",
        "simple_saved_app_process_checklist_13",
        "exact_cache_before_yellow_checklist_13",
        "single_exact_route_matrix_checklist_13",
        "simple_saved_app_freshness_checklist_13",
        "capture_teaches_package_checklist_13",
        "capture_teaches_app_and_triggers_farol_checklist_13",
        "measured_end_to_end_farol_checklist_13",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Farol simples incompleto: $marker")
    }

    val processStart = service.indexOf("    private suspend fun processRideText(")
    val processEnd = service.indexOf("    //    private fun resolveRidePackageForText(", processStart)
    if (processStart < 0 || processEnd <= processStart) throw GradleException("processRideText final não localizado.")
    val process = service.substring(processStart, processEnd)
    listOf(
        "UniversalRideCardEvidencePolicy",
        "RidePassengerIdentityPolicy",
        "RideCardTemplateMatcher.match",
        "packageCardTemplates",
        "manualCardMatch",
        "manualActiveCardTemplateId127 != null",
    ).forEach { forbidden ->
        if (forbidden in process) throw GradleException("Bloqueio antigo voltou ao caminho crítico: $forbidden")
    }
    listOf(
        "SimpleSavedAppFarolPolicy.evaluate",
        "evaluationChecklist13.addresses",
        "evaluationChecklist13.destination",
        "cachedDrivingDistancesFromAddressKm",
    ).forEach { required ->
        if (required !in process) throw GradleException("Caminho simples ausente: $required")
    }

    val freshStart = service.indexOf("    private fun isUniversalResultFresh(")
    val clearStart = service.indexOf("    private fun hardClearUniversalTwoAddress(", freshStart)
    if (freshStart < 0 || clearStart <= freshStart) throw GradleException("Freshness final não localizada.")
    val fresh = service.substring(freshStart, clearStart)
    if ("currentCardTemplates" in fresh || "manualActiveCardTemplateId127 != null" in fresh) {
        throw GradleException("Modelo visual voltou a bloquear resultado fresco.")
    }

    listOf(
        "simple_cached_route_peek_checklist_13",
        "fast_network_connect_budget_checklist_13",
        "fast_network_read_budget_checklist_13",
    ).forEach { marker ->
        if (marker !in maps) throw GradleException("Google Maps rápido incompleto: $marker")
    }
    if ("const val CONNECT_TIMEOUT_MS = 1_200" in maps || "const val READ_TIMEOUT_MS = 2_600" in maps) {
        throw GradleException("Orçamento antigo de até 3,8 segundos voltou.")
    }

    if ("simple_saved_app_default_checklist_13" !in models || "requireRegisteredRideCard: Boolean = true" in models) {
        throw GradleException("Modelo ainda nasce como bloqueio obrigatório.")
    }
    listOf(
        "Modelo visual bloqueia o farol: false",
        "aplicativo salvo + dois ou mais enderecos",
        "Tempo da ultima decisao",
    ).forEach { marker ->
        if (marker !in report) throw GradleException("Relatório final não explica/mede o contrato simples: $marker")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        verifySimpleFarolChecklist13(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}
