// Rota Certa 0.1.127
// Amarelo e um estado de espera, nao uma decisao ativa. Leituras vazias repetidas
// nao devem redesenhar a bolinha enquanto ela ja estiver amarela e sem dados.

fun patchYellowIdempotentNoRedraw127(serviceFile: java.io.File) {
    if (!serviceFile.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para amarelo idempotente 0.1.127.")

    var service = serviceFile.readText()
    if ("yellow_waiting_not_active_data_0_1_127" !in service) {
        val oldHadData = """        val hadData = currentRadarColor != RadarColor.Idle ||
            currentDistanceKm != null ||
"""
        if (oldHadData !in service) throw GradleException("Deteccao de dados ativos nao encontrada para amarelo idempotente.")
        val newHadData = """        val hadDecisionColor127 = currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red
        val hadData = hadDecisionColor127 || // yellow_waiting_not_active_data_0_1_127
            currentDistanceKm != null ||
"""
        service = service.replaceFirst(oldHadData, newHadData)
    }

    listOf(
        "yellow_waiting_not_active_data_0_1_127",
        "atomic_hard_clear_single_paint_0_1_127",
        "atomic_selected_app_clear_color_0_1_127",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Marcador ausente no amarelo idempotente 0.1.127: $marker")
    }

    serviceFile.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchYellowIdempotentNoRedraw127(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
