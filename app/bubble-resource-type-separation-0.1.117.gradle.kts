// Separacao defensiva dos recursos 0.1.117:
// - Salvar alerta participa do motor de proximidade e do popup;
// - Salvar local e apenas um ponto para abrir no GPS posteriormente;
// - mesmo que um Local seja enviado por engano ao motor, ele e ignorado.

fun enforceBubbleResourceTypeSeparation117(
    serviceFile: java.io.File,
    engineFile: java.io.File,
) {
    if (!serviceFile.exists() || !engineFile.exists()) {
        throw GradleException("Arquivos ausentes para separar Local e Alerta.")
    }
    var service = serviceFile.readText()
    var engine = engineFile.readText()

    if ("bubble_resource_type_separation_0_1_117" !in service) {
        service = service
            .replace(
                "resourceShortcutBubble(\"⚠️\\nAlerta\")",
                "resourceShortcutBubble(\"⚠️\\nSalvar\\nalerta\")",
            )
            .replace(
                "resourceShortcutBubble(\"📍\\nLocal\")",
                "resourceShortcutBubble(\"📍\\nSalvar\\nlocal\")",
            )
            .replace(
                "            textSize = 13f\n            setTextColor(Color.WHITE)\n            typeface = Typeface.DEFAULT_BOLD\n            gravity = Gravity.CENTER\n            contentDescription = label.replace(\"\\n\", \" \")\n",
                "            textSize = if (label.contains(\"Salvar\")) 11f else 13f\n            setTextColor(Color.WHITE)\n            typeface = Typeface.DEFAULT_BOLD\n            gravity = Gravity.CENTER\n            contentDescription = label.replace(\"\\n\", \" \")\n",
            )
            .replace(
                "    private fun showProximityAlertPopup(alert: SavedPlace, distanceMeters: Double) {\n        val manager = windowManager ?: return\n",
                "    private fun showProximityAlertPopup(alert: SavedPlace, distanceMeters: Double) {\n        if (alert.type != SavedPlaceType.ProximityAlert) return // bubble_resource_type_separation_0_1_117\n        val manager = windowManager ?: return\n",
            )
    }

    if ("proximity_alert_type_filter_0_1_117" !in engine) {
        val old = """        alerts.forEach { alert ->
            val threshold = (alert.alertDistanceMeters ?: settings.proximityAlertDistanceMeters).coerceIn(200, 1000)
"""
        val replacement = """        alerts
            .filter { it.type == SavedPlaceType.ProximityAlert } // proximity_alert_type_filter_0_1_117
            .forEach { alert ->
            val threshold = (alert.alertDistanceMeters ?: settings.proximityAlertDistanceMeters).coerceIn(200, 1000)
"""
        if (old !in engine) throw GradleException("Loop de alertas nao encontrado para filtro de tipo.")
        engine = engine.replaceFirst(old, replacement)
    }

    listOf(
        "Salvar\\nalerta",
        "Salvar\\nlocal",
        "bubble_resource_type_separation_0_1_117",
        "proximity_alert_type_filter_0_1_117",
    ).forEach { marker ->
        if (marker !in service && marker !in engine) {
            throw GradleException("Separacao Local/Alerta incompleta: $marker")
        }
    }

    serviceFile.writeText(service)
    engineFile.writeText(engine)
}

val bubbleResourceTypeSeparation117 by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    val engineFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/ProximityAlertEngine.kt")
    inputs.files(serviceFile, engineFile)
    outputs.upToDateWhen { false }
    dependsOn("bubbleResourceShortcutsAlertPopup117")
    doLast { enforceBubbleResourceTypeSeparation117(serviceFile.asFile, engineFile.asFile) }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("test") }.configureEach {
    dependsOn(bubbleResourceTypeSeparation117)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(bubbleResourceTypeSeparation117)
    doFirst {
        enforceBubbleResourceTypeSeparation117(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/ProximityAlertEngine.kt").asFile,
        )
    }
}
