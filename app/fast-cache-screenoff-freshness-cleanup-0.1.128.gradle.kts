// Limpeza final 0.1.128.
// A versao antiga de isUniversalResultFresh era uma expression function e continha
// uma lambda. Depois da conversao para corpo com chaves, remove a cauda da expressao
// antiga que poderia ficar depois do fechamento da lambda.

fun cleanupFreshnessExpressionTail128(serviceFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente na limpeza 0.1.128.")
    var service = serviceFile.readText()
    val orphanTail128 = """ && // manual_registered_card_freshness_0_1_127
            isUniversalExternalWindowActive() &&
            shouldScanCurrentWindow() // selected_apps_freshness_gate_0_1_122
"""
    service = service.replace(orphanTail128, "")

    val freshnessStart128 = service.indexOf("    private fun isUniversalResultFresh(")
    val clearStart128 = service.indexOf("    private fun hardClearUniversalTwoAddress(", freshnessStart128)
    if (freshnessStart128 < 0 || clearStart128 < 0) {
        throw GradleException("Limites da validade de rota nao encontrados na limpeza 0.1.128.")
    }
    val freshnessRegion128 = service.substring(freshnessStart128, clearStart128)
    if ("locked_popup_result_freshness_0_1_128" !in freshnessRegion128) {
        throw GradleException("Nova validade de popup bloqueado ausente na limpeza 0.1.128.")
    }
    if ("isUniversalExternalWindowActive()" in freshnessRegion128 ||
        "selected_apps_freshness_gate_0_1_122" in freshnessRegion128
    ) {
        throw GradleException("Cauda antiga de validade ainda presente depois da limpeza 0.1.128.")
    }
    serviceFile.writeText(service.replace(
        "// locked_popup_result_freshness_0_1_128",
        "// locked_popup_result_freshness_0_1_128 freshness_expression_tail_clean_0_1_128",
    ))
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        cleanupFreshnessExpressionTail128(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
