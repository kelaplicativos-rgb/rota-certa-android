// Etapa 10 — ponte temporária para compatibilizar o bloco 0.1.124 com o checklist 6.
// O bloco é removido pelo finalizador posterior antes da compilação Kotlin.

fun prepareSubsecondHistoryOrderBridgeChecklist10(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente na ponte de histórico 10.")
    var service = file.readText()
    if ("low_priority_capture_final_checklist_6" in service) return
    if ("history_order_bridge_start_checklist_10" in service) return

    val expectedOldOrder = """        lastAnalyzedHash = screenHash
        repository.addAnalysis(result)
        showOverlay(color, result.nearestConfiguredDistanceKm())
"""
    if (expectedOldOrder in service) return

    val applyStart = service.indexOf("    private suspend fun applyUniversalTwoAddressResult(")
    val applyEnd = if (applyStart >= 0) service.indexOf("    private fun isUniversalResultFresh(", applyStart) else -1
    if (applyStart < 0 || applyEnd <= applyStart) {
        throw GradleException("Aplicação do farol ausente na ponte de histórico 10.")
    }
    var region = service.substring(applyStart, applyEnd)
    val instantAnchor = "        val shouldPersistHistory = universalAnalysisDeduper.shouldPersist(persistenceSignature)\n"
    if (instantAnchor !in region || "instant_farol_paint_before_history_0_1_124" !in region) {
        throw GradleException("Bloco instantâneo 0.1.124 não reconhecido para a ponte de histórico 10.")
    }

    val bridge = """        // history_order_bridge_start_checklist_10
$expectedOldOrder        // history_order_bridge_end_checklist_10
"""
    region = region.replaceFirst(instantAnchor, bridge + instantAnchor)
    service = service.substring(0, applyStart) + region + service.substring(applyEnd)
    file.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        prepareSubsecondHistoryOrderBridgeChecklist10(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
