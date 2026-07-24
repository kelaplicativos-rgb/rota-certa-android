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
        "evaluationChecklist13.active",
        "evaluationChecklist13.pickup",
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

    if ("simple_cached_route_peek_checklist_13" !in maps) {
        throw GradleException("Consulta síncrona do cache exato ausente.")
    }
    val connectBudget = Regex("const val CONNECT_TIMEOUT_MS = ([0-9_]+)")
        .find(maps)?.groupValues?.get(1)?.replace("_", "")?.toIntOrNull()
        ?: throw GradleException("Timeout de conexão não localizado.")
    val readBudget = Regex("const val READ_TIMEOUT_MS = ([0-9_]+)")
        .find(maps)?.groupValues?.get(1)?.replace("_", "")?.toIntOrNull()
        ?: throw GradleException("Timeout de leitura não localizado.")
    if (connectBudget > 450 || readBudget > 900) {
        throw GradleException("Orçamento de rede excede 450/900 ms: $connectBudget/$readBudget.")
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
