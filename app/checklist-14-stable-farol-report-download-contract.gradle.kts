// Contrato final da etapa 14 — resultado estável e relatório salvo, nunca compartilhado.

fun verifyStableFarolAndReport14(root: java.io.File) {
    val service = java.io.File(root, "LiveRideAccessibilityService.kt").readText()
    val main = java.io.File(root, "MainActivity.kt").readText()
    val exporter = java.io.File(root, "ManualTechnicalReportExporter.kt").readText()
    val simplePolicy = java.io.File(root, "SimpleSavedAppFarolPolicy.kt").readText()
    val stabilityPolicy = java.io.File(root, "FarolDisplayStabilityPolicy.kt").readText()

    listOf(
        "stable_farol_event_contract_checklist_14",
        "partial_read_confirmation_checklist_14",
        "real_screen_change_clear_checklist_14",
        "destination_change_clear_checklist_14",
        "transient_partial_read_keeps_decision_checklist_14",
        "same_destination_keeps_visible_result_checklist_14",
        "stable_exact_cache_checklist_14",
        "stable_farol_display_complete_checklist_14",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Estabilidade final da bolinha incompleta: $marker")
    }

    val eventStart = service.indexOf("override fun onAccessibilityEvent(")
    val eventEnd = service.indexOf("override fun onInterrupt()", eventStart)
    if (eventStart < 0 || eventEnd <= eventStart) throw GradleException("Evento final não localizado.")
    val eventRegion = service.substring(eventStart, eventEnd)
    if ("screenFingerprint(" in eventRegion) {
        throw GradleException("Texto completo voltou a decidir mudança de tela e pode fazer a bolinha piscar.")
    }
    if ("FarolDisplayStabilityPolicy.decide" !in eventRegion) {
        throw GradleException("Política de estabilidade não está no primeiro evento.")
    }

    val processStart = service.indexOf("private suspend fun processRideText(")
    val processEnd = service.indexOf("private suspend fun analyzeUniversalTwoAddress(", processStart)
    if (processStart < 0 || processEnd <= processStart) throw GradleException("Processamento final não localizado.")
    val processRegion = service.substring(processStart, processEnd)
    if ("hardClearUniversalTwoAddress" in processRegion.substringBefore("transient_partial_read_keeps_decision_checklist_14")) {
        val inactiveStart = processRegion.indexOf("if (!evaluationChecklist14.active)")
        val keepMarker = processRegion.indexOf("transient_partial_read_keeps_decision_checklist_14")
        val clearAfter = processRegion.indexOf("hardClearUniversalTwoAddress", inactiveStart)
        if (inactiveStart < 0 || keepMarker < inactiveStart || clearAfter < keepMarker) {
            throw GradleException("Leitura parcial ainda pode limpar antes de preservar a decisão.")
        }
    }

    if ("FarolDisplayStabilityPolicy.stableScreenHash" !in simplePolicy) {
        throw GradleException("Hash da decisão ainda depende de preço, cronômetro ou texto variável.")
    }
    listOf(
        "PARTIAL_ABSENCE_CONFIRM_MILLIS = 90L",
        "Action.ConfirmAbsence",
        "Action.ClearThenProcess",
    ).forEach { marker ->
        if (marker !in stabilityPolicy) throw GradleException("Política anti-pisca incompleta: $marker")
    }

    listOf(
        "saveToDownloads(context, report)",
        "Gerar e baixar relatorio",
        "Relatorio salvo em Downloads/Rota Certa.",
    ).forEach { marker ->
        if (marker !in main) throw GradleException("Interface de download incompleta: $marker")
    }
    if ("Gerar e compartilhar relatorio" in main || "createAndShare(context, report)" in main) {
        throw GradleException("Compartilhamento antigo ainda está ativo.")
    }
    listOf(
        "MediaStore.Downloads.EXTERNAL_CONTENT_URI",
        "Environment.DIRECTORY_DOWNLOADS",
        "DOWNLOAD_SUBDIRECTORY = \"Rota Certa\"",
        "fun saveToDownloads",
    ).forEach { marker ->
        if (marker !in exporter) throw GradleException("Exportador não salva diretamente em Downloads: $marker")
    }
    if ("Intent.ACTION_SEND" in exporter || "createChooser" in exporter) {
        throw GradleException("Exportador ainda abre compartilhamento.")
    }

    root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
        val text = file.readText()
        if ("app escolhido + modelo correspondente + passageiro" in text) {
            throw GradleException("Relatório antigo ainda descreve a política obsoleta: ${file.name}")
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        verifyStableFarolAndReport14(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}
