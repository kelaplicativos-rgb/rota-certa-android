// Rota Certa 0.1.127
// Contrato visual final:
// - aplicativo selecionado ativo, sem card valido: amarelo;
// - fora do aplicativo selecionado: cinza;
// - verde/vermelho somente com modelo manual e rota valida.

fun patchSelectedAppWaitingYellow127(serviceFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para estado amarelo 0.1.127.")

    var service = serviceFile.readText()
    if ("selected_app_clear_to_yellow_0_1_127" !in service) {
        val inactiveAnchor = """            hardClearUniversalTwoAddress(clearReason)
            return // global_inactive_clear_now_0_1_124
"""
        if (inactiveAnchor !in service) throw GradleException("Limpeza de card inativo nao encontrada para restaurar amarelo.")
        val inactiveReplacement = """            hardClearUniversalTwoAddress(clearReason)
            if (shouldScanCurrentWindow()) {
                rememberBubbleReason(
                    "manual_waiting",
                    "Aplicativo selecionado ativo; aguardando um card cadastrado correspondente.",
                )
                showOverlay(RadarColor.Default, distanceKm = null)
            } // selected_app_clear_to_yellow_0_1_127
            return // global_inactive_clear_now_0_1_124
"""
        service = service.replaceFirst(inactiveAnchor, inactiveReplacement)
    }

    listOf(
        "selected_app_clear_to_yellow_0_1_127",
        "manual_selected_apps_gate_0_1_127",
        "manual_registered_card_gate_0_1_127",
        "manual.card.gate accepted=false reason=no_template",
        "manual.card.gate accepted=false reason=no_match",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Marcador ausente no estado amarelo 0.1.127: $marker")
    }

    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchSelectedAppWaitingYellow127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
