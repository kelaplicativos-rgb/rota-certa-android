// Contrato final da confirmação manual: um toque, sem coleta contínua e sem envio automático.

fun verifyTripConfirmationChecklist8(
    catalogFile: java.io.File,
    serviceFile: java.io.File,
    formatterFile: java.io.File,
    moduleFile: java.io.File,
) {
    val catalog = catalogFile.readText()
    val service = serviceFile.readText()
    val formatter = formatterFile.readText()
    val module = moduleFile.readText()

    listOf(
        "CopyTripConfirmation",
        "TripConfirmationBubbleShortcutModule,",
        "require(modules.size == 15)",
    ).forEach { marker ->
        if (marker !in catalog) throw GradleException("Catálogo final da confirmação ausente: $marker")
    }
    listOf(
        "id = \"copy_trip_confirmation\"",
        "displayLabel = \"Copiar viagem\"",
        "BubbleShortcutAction.CopyTripConfirmation",
    ).forEach { marker ->
        if (marker !in module) throw GradleException("Módulo da confirmação ausente: $marker")
    }
    listOf(
        "trip_confirmation_action_checklist_8",
        "trip_confirmation_copy_complete_checklist_8",
        "collectVisibleTextForAction()",
        "TripConfirmationFormatter.extractAndFormat",
        "ocrService.extractText",
        "ClipData.newPlainText(\"Confirmação da viagem\"",
        "Confirmação copiada. Abra o WhatsApp e cole.",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Serviço final da confirmação ausente: $marker")
    }

    val helperStart = service.indexOf("private fun copyTripConfirmationFromBubbleChecklist8()")
    val helperEnd = service.indexOf("private fun open", helperStart)
    if (helperStart < 0 || helperEnd <= helperStart) throw GradleException("Fluxo manual da confirmação não localizado.")
    val helperRegion = service.substring(helperStart, helperEnd)
    if ("repository." in helperRegion || "DiagnosticLogStore" in helperRegion || "LiveFailureTraceStore" in helperRegion) {
        throw GradleException("A confirmação não pode armazenar texto da conversa nem gerar diagnóstico contínuo.")
    }
    if ("startActivity(" in helperRegion || "sendEmail" in helperRegion || "sendMessage" in helperRegion) {
        throw GradleException("A confirmação deve apenas copiar; envio automático não é permitido.")
    }

    val scanStart = service.indexOf("private fun startContinuousScan()")
    val proximityStart = service.indexOf("private fun startProximityAlertMonitor()", scanStart)
    if (scanStart >= 0 && proximityStart > scanStart) {
        val scanRegion = service.substring(scanStart, proximityStart)
        if ("TripConfirmationFormatter" in scanRegion || "copyTripConfirmationFromBubbleChecklist8" in scanRegion) {
            throw GradleException("A confirmação voltou ao ciclo contínuo da bolinha.")
        }
    }

    listOf(
        "data class TripConfirmationData(",
        "fun extractAndFormat(rawText: String)",
        "STANDALONE_TIME_REGEX",
        "INLINE_ROUTE_STOP_REGEX",
        "Está tudo certo?",
    ).forEach { marker ->
        if (marker !in formatter) throw GradleException("Formatador da confirmação incompleto: $marker")
    }

    // Mantém intactas as garantias anteriores de velocidade e captura sob demanda.
    listOf(
        "overlay_before_storage_final_checklist_6",
        "ocr_outside_critical_path_final_checklist_6",
        "low_priority_capture_final_checklist_6",
        "strict_selected_app_policy_checklist_1",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Contrato operacional anterior perdido: $marker")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        val root = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile
        verifyTripConfirmationChecklist8(
            catalogFile = java.io.File(root, "BubbleShortcutModule.kt"),
            serviceFile = java.io.File(root, "LiveRideAccessibilityService.kt"),
            formatterFile = java.io.File(root, "TripConfirmationFormatter.kt"),
            moduleFile = java.io.File(root, "TripConfirmationBubbleShortcutModule.kt"),
        )
    }
}
