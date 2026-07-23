// Etapa 10 — remove a ponte temporária e aplica a ordem final no bloco real 0.1.124.

fun finalizeSubsecondHistoryOrderChecklist10(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente no finalizador de histórico 10.")
    var service = file.readText()

    val bridgeStart = "        // history_order_bridge_start_checklist_10\n"
    val bridgeEnd = "        // history_order_bridge_end_checklist_10\n"
    val start = service.indexOf(bridgeStart)
    if (start >= 0) {
        val endStart = service.indexOf(bridgeEnd, start)
        if (endStart < 0) throw GradleException("Fim da ponte de histórico 10 ausente.")
        service = service.substring(0, start) + service.substring(endStart + bridgeEnd.length)
    }

    val applyStart = service.indexOf("    private suspend fun applyUniversalTwoAddressResult(")
    val applyEnd = if (applyStart >= 0) service.indexOf("    private fun isUniversalResultFresh(", applyStart) else -1
    if (applyStart < 0 || applyEnd <= applyStart) {
        throw GradleException("Aplicação do farol ausente no finalizador de histórico 10.")
    }
    var region = service.substring(applyStart, applyEnd)

    if ("releaseMatchedCaptureFinalChecklist6(screenHash, addressSignature, generation)" !in region) {
        val overlayAnchor = "        showOverlay(color, distanceKm)\n"
        if (overlayAnchor !in region) {
            throw GradleException("Pintura instantânea 0.1.124 ausente no finalizador de histórico 10.")
        }
        region = region.replaceFirst(
            overlayAnchor,
            """        showOverlay(color, distanceKm) // overlay_before_storage_final_checklist_6
        releaseMatchedCaptureFinalChecklist6(screenHash, addressSignature, generation)
""",
        )
    }

    val historyLaunch = """            scope.launch {
                runCatching { repository.addAnalysis(result) }
"""
    if (historyLaunch in region) {
        region = region.replaceFirst(
            historyLaunch,
            """            scope.launch(Dispatchers.IO) {
                runCatching { repository.addAnalysis(result) }
""",
        )
    }

    val overlayIndex = region.indexOf("showOverlay(color, distanceKm)")
    val releaseIndex = region.indexOf("releaseMatchedCaptureFinalChecklist6(screenHash, addressSignature, generation)")
    val historyIndex = region.indexOf("repository.addAnalysis(result)")
    if (overlayIndex < 0 || releaseIndex <= overlayIndex || historyIndex <= releaseIndex) {
        throw GradleException("Ordem final do farol inválida: pintura, captura e histórico precisam permanecer nessa sequência.")
    }
    if ("history_order_bridge_start_checklist_10" in region || "history_order_bridge_end_checklist_10" in region) {
        throw GradleException("Ponte temporária de histórico permaneceu no código compilado.")
    }

    service = service.substring(0, applyStart) + region + service.substring(applyEnd)
    file.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        finalizeSubsecondHistoryOrderChecklist10(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
