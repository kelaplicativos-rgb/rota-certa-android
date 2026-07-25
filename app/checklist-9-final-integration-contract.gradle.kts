// Checklist 9 — contrato cruzado final antes de executar Gradle e gerar o APK.

fun verifyFinalIntegrationChecklist9(
    mainFile: java.io.File,
    catalogFile: java.io.File,
    serviceFile: java.io.File,
    overlayFile: java.io.File,
    modelsFile: java.io.File,
    repositoryFile: java.io.File,
    decisionFile: java.io.File,
    formatterFile: java.io.File,
    workRegionEditorFile: java.io.File,
    diagnosticGateFile: java.io.File,
    quickReplyFillerFile: java.io.File,
    directionalEngineFile: java.io.File,
) {
    val main = mainFile.readText()
    val catalog = catalogFile.readText()
    val service = serviceFile.readText()
    val overlay = overlayFile.readText()
    val models = modelsFile.readText()
    val repository = repositoryFile.readText()
    val decision = decisionFile.readText()
    val formatter = formatterFile.readText()
    val workRegionEditor = workRegionEditorFile.readText()
    val diagnosticGate = diagnosticGateFile.readText()
    val quickReplyFiller = quickReplyFillerFile.readText()
    val directionalEngine = directionalEngineFile.readText()

    listOf(
        "home_target_pre_resolved_checklist_9",
        "home_target_editor_final_checklist_9",
        "WorkRegionPinsCard(",
        "general_controls_final_checklist_7",
        "popup_scale_ui_final_checklist_7",
        "Gerar e compartilhar relatorio",
        "saving = savingHomeAddressChecklist9,",
        "onSave = ::saveHomeAddressValidatedChecklist9,",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Interface integrada incompleta: $marker")
    }
    listOf(
        "KeyboardActions(onDone = { addAddress() })",
        "WorkRegionTargetPolicy.setEnabled",
        "WorkRegionTargetPolicy.remove",
        "Adicionar alfinete",
    ).forEach { marker ->
        if (marker !in workRegionEditor) throw GradleException("Editor dos alfinetes incompleto: $marker")
    }

    listOf(
        "require(modules.size == 15)",
        "TripConfirmationBubbleShortcutModule,",
        "QuickRepliesBubbleShortcutModule,",
    ).forEach { marker ->
        if (marker !in catalog) throw GradleException("Catálogo integrado incompleto: $marker")
    }
    if ("ReadingBubbleShortcutModule," in catalog || "PermissionsBubbleShortcutModule," in catalog) {
        throw GradleException("Leitura ou permissão voltaram a duplicar Controles gerais.")
    }

    listOf(
        "import android.widget.ScrollView",
        "maxMenuHeight",
        "visibleMenuHeight",
        "needsVerticalScroll",
        "View.OVER_SCROLL_IF_CONTENT_SCROLLS",
    ).forEach { marker ->
        if (marker !in overlay) throw GradleException("Popup final não cabe com acessibilidade: $marker")
    }

    listOf(
        "strict_selected_app_policy_checklist_1",
        "quick_reply_receiver_checklist_3",
        "directional_alert_monitor_checklist_5",
        "overlay_before_storage_final_checklist_6",
        "ocr_outside_critical_path_final_checklist_6",
        "low_priority_capture_final_checklist_6",
        "multi_address_route_matrix_final_checklist_7",
        "trip_confirmation_copy_complete_checklist_8",
        "manual_trip_tree_read_checklist_8",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Serviço integrado perdeu contrato: $marker")
    }

    val scanStart = service.indexOf("private fun startContinuousScan()")
    val alertStart = service.indexOf("private fun startProximityAlertMonitor()", scanStart)
    if (scanStart < 0 || alertStart <= scanStart) throw GradleException("Ciclo contínuo final não localizado.")
    val scanRegion = service.substring(scanStart, alertStart)
    listOf(
        "TripConfirmationFormatter",
        "QuickReplyAccessibilityFiller",
        "ManualTechnicalReportBuilder",
        "AutomaticRideCaptureStore",
    ).forEach { forbidden ->
        if (forbidden in scanRegion) throw GradleException("Recurso manual voltou ao ciclo do farol: $forbidden")
    }

    val routeStart = service.indexOf("private suspend fun analyzeUniversalTwoAddress(")
    val resultStart = service.indexOf("private suspend fun applyUniversalTwoAddressResult(", routeStart)
    if (routeStart < 0 || resultStart <= routeStart) throw GradleException("Rota final não localizada.")
    val routeRegion = service.substring(routeStart, resultStart)
    if (Regex("drivingDistancesFromAddressKm\\(").findAll(routeRegion).count() != 1) {
        throw GradleException("Casa e alfinetes devem usar uma única matriz de rotas.")
    }
    if ("routeDistanceKm(" in routeRegion) {
        throw GradleException("Rota voltou a fazer chamadas sequenciais por alvo.")
    }

    val tripStart = service.indexOf("private fun copyTripConfirmationFromBubbleChecklist8()")
    val tripEnd = service.indexOf("private fun open", tripStart)
    if (tripStart < 0 || tripEnd <= tripStart) throw GradleException("Cópia manual da viagem não localizada.")
    val tripRegion = service.substring(tripStart, tripEnd)
    if ("repository." in tripRegion || "startActivity(" in tripRegion) {
        throw GradleException("Cópia da viagem não pode armazenar conversa nem enviar automaticamente.")
    }
    if ("collectVisibleTextForAction()" in tripRegion) {
        throw GradleException("Cópia da viagem voltou a depender dos aplicativos selecionados do farol.")
    }

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
        "fun decideWorkRegion(",
        "activePins.all { it.distanceKm != null }",
        "Recommendation.GoodRide",
        "Recommendation.OutsideRadius",
    ).forEach { marker ->
        if (marker !in decision) throw GradleException("Decisão de Casa/alfinetes incompleta: $marker")
    }

    listOf(
        "fun extractAndFormat(rawText: String)",
        "STANDALONE_TIME_REGEX",
        "INLINE_ROUTE_STOP_REGEX",
        "Está tudo certo?",
    ).forEach { marker ->
        if (marker !in formatter) throw GradleException("Formatação da confirmação incompleta: $marker")
    }
    listOf(
        "fun beginManualCapture",
        "fun endManualCapture",
        "return false",
    ).forEach { marker ->
        if (marker !in diagnosticGate) throw GradleException("Diagnóstico deixou de ser manual: $marker")
    }
    listOf(
        "expectedPackageName",
        "ClipboardManager",
        "MAX_ATTEMPTS",
    ).forEach { marker ->
        if (marker !in quickReplyFiller) throw GradleException("Resposta rápida perdeu proteção: $marker")
    }
    listOf(
        "DirectionalAlertPolicy",
        "shouldClose",
        "if (!spoken) return",
        "runtime.recordSpoken(now)",
    ).forEach { marker ->
        if (marker !in directionalEngine) throw GradleException("Alerta direcional perdeu proteção: $marker")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        val root = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile
        verifyFinalIntegrationChecklist9(
            mainFile = java.io.File(root, "MainActivity.kt"),
            catalogFile = java.io.File(root, "BubbleShortcutModule.kt"),
            serviceFile = java.io.File(root, "LiveRideAccessibilityService.kt"),
            overlayFile = java.io.File(root, "BubbleShortcutOverlayController.kt"),
            modelsFile = java.io.File(root, "Models.kt"),
            repositoryFile = java.io.File(root, "Repositories.kt"),
            decisionFile = java.io.File(root, "DecisionEngine.kt"),
            formatterFile = java.io.File(root, "TripConfirmationFormatter.kt"),
            workRegionEditorFile = java.io.File(root, "WorkRegionPinsCard.kt"),
            diagnosticGateFile = java.io.File(root, "DiagnosticRuntimeGate.kt"),
            quickReplyFillerFile = java.io.File(root, "QuickReplyAccessibilityFiller.kt"),
            directionalEngineFile = java.io.File(root, "DirectionalProximityAlertEngine.kt"),
        )
    }
}
