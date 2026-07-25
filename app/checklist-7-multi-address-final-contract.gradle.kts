// Contrato final: Casa + vários alfinetes, uma única matriz e farol seguro.

fun verifyMultiAddressWorkRegionChecklist7(
    modelsFile: java.io.File,
    repositoryFile: java.io.File,
    mainFile: java.io.File,
    serviceFile: java.io.File,
    policyFile: java.io.File,
    editorFile: java.io.File,
    decisionFile: java.io.File,
) {
    val models = modelsFile.readText()
    val repository = repositoryFile.readText()
    val main = mainFile.readText()
    val service = serviceFile.readText()
    val policy = policyFile.readText()
    val editor = editorFile.readText()
    val decision = decisionFile.readText()

    listOf(
        "val workRegionPins: List<WorkRegionPin> = emptyList()",
        "data class WorkRegionPin(",
    ).forEach { marker ->
        if (marker !in models) throw GradleException("Modelo multiendereço ausente: $marker")
    }
    listOf(
        "work_region_pins",
        "workRegionPins = decodeWorkRegionPins",
        "json.encodeToString(settings.workRegionPins",
    ).forEach { marker ->
        if (marker !in repository) throw GradleException("Persistência multiendereço ausente: $marker")
    }
    listOf(
        "multi_address_work_region_ui_checklist_7",
        "WorkRegionPinsCard(",
        "Todos os alfinetes",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Interface multiendereço ausente: $marker")
    }
    listOf(
        "KeyboardActions(onDone = { addAddress() })",
        "Adicionar alfinete",
        "WorkRegionTargetPolicy.setEnabled",
        "WorkRegionTargetPolicy.remove",
    ).forEach { marker ->
        if (marker !in editor) throw GradleException("Editor multiendereço incompleto: $marker")
    }
    listOf(
        "multi_address_route_matrix_final_checklist_7",
        "drivingDistancesFromAddressKm(",
        "decisionEngine.decideWorkRegion(",
        "applyUniversalTwoAddressResult(result",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Farol multiendereço incompleto: $marker")
    }

    val analyzeStart = service.indexOf("private suspend fun analyzeUniversalTwoAddress(")
    val applyStart = service.indexOf("private suspend fun applyUniversalTwoAddressResult(", analyzeStart)
    if (analyzeStart < 0 || applyStart <= analyzeStart) throw GradleException("Bloco de rota multiendereço não localizado.")
    val analyzeRegion = service.substring(analyzeStart, applyStart)
    if (Regex("routeDistanceKm\\(").findAll(analyzeRegion).count() > 0) {
        throw GradleException("O farol voltou a calcular uma rota separada por endereço.")
    }
    if (Regex("drivingDistancesFromAddressKm\\(").findAll(analyzeRegion).count() != 1) {
        throw GradleException("A região de trabalho deve usar exatamente uma matriz de rotas.")
    }

    listOf(
        "fun activePins(settings: AppSettings)",
        "LEGACY_PIN_ID",
        "fun addOrUpdate",
        "fun setEnabled",
    ).forEach { marker ->
        if (marker !in policy) throw GradleException("Política dos alfinetes ausente: $marker")
    }
    listOf(
        "fun decideWorkRegion(",
        "activePins.all { it.distanceKm != null }",
        "Recommendation.GoodRide",
        "Recommendation.OutsideRadius",
    ).forEach { marker ->
        if (marker !in decision) throw GradleException("Decisão multiendereço incompleta: $marker")
    }

    // As proteções de velocidade e leitura estrita continuam obrigatórias.
    listOf(
        "overlay_before_storage_final_checklist_6",
        "low_priority_capture_final_checklist_6",
        "strict_selected_app_policy_checklist_1",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Contrato operacional anterior perdido: $marker")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        val root = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile
        verifyMultiAddressWorkRegionChecklist7(
            modelsFile = java.io.File(root, "Models.kt"),
            repositoryFile = java.io.File(root, "Repositories.kt"),
            mainFile = java.io.File(root, "MainActivity.kt"),
            serviceFile = java.io.File(root, "LiveRideAccessibilityService.kt"),
            policyFile = java.io.File(root, "WorkRegionTargetPolicy.kt"),
            editorFile = java.io.File(root, "WorkRegionPinsCard.kt"),
            decisionFile = java.io.File(root, "DecisionEngine.kt"),
        )
    }
}
