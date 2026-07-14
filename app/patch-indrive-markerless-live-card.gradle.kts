// Corrige o caso real em que os marcadores A/B e os dois selos de tempo/km
// aparecem como elementos graficos no inDrive e nao chegam como texto ao Android.
// O card individual continua exigindo titulo singular, acao, valor, distancia e
// dois enderecos; a tela plural/lista continua bloqueada.

val inDriveMarkerlessLiveCardFix by tasks.registering {
    val matcherFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcher.kt")
    val contractsFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/core/CoreRideCardContracts.kt")
    inputs.files(matcherFile, contractsFile)
    outputs.upToDateWhen { false }

    doLast {
        matcherFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text

            // O contrato inserido pelo patch 0.1.86 exigia A/B textuais. Na tela real,
            // esses marcadores sao icones; dois enderecos distintos representam os dois pontos.
            val oldEndpoints = "val hasTwoEndpoints = endpointTextLines >= 2 || markerCount >= 2 || routeMarkerInlineRegex.findAll(rawText).count() >= 2"
            val newEndpoints = "val hasTwoEndpoints = endpointTextLines >= 2 || markerCount >= 2 || routeMarkerInlineRegex.findAll(rawText).count() >= 2 || addressCount >= 2 // indrive_markerless_endpoints_0_1_87"
            text = text.replace(oldEndpoints, newEndpoints)

            if ("indrive_markerless_route_block_0_1_87" !in text) {
                val functionStart = """    private fun isRouteCardCrop(
        normalized: String,
        timeDistanceCount: Int,
        timeCount: Int,
        distanceCount: Int,
        addressCount: Int,
        hasMarkers: Boolean,
        hasTwoMarkers: Boolean,
        endpointTextLines: Int,
    ): Boolean {
"""
                val replacement = functionStart + """        val markerlessInDriveIndividual =
            "pedido de viagem" in normalized &&
                "pedidos de viagem" !in normalized &&
                ("aceitar por" in normalized || "ofereca sua tarifa" in normalized) &&
                addressCount >= 2 &&
                distanceCount >= 1
        if (markerlessInDriveIndividual) return true // indrive_markerless_route_block_0_1_87
"""
                if (functionStart !in text) {
                    throw org.gradle.api.GradleException("Nao encontrei isRouteCardCrop para liberar o card inDrive sem A/B textual.")
                }
                text = text.replace(functionStart, replacement)
            }

            if ("indrive_markerless_endpoints_0_1_87" !in text) {
                throw org.gradle.api.GradleException("Contrato inDrive ainda exige marcadores A/B textuais.")
            }
            if ("indrive_markerless_route_block_0_1_87" !in text) {
                throw org.gradle.api.GradleException("Bloco de rota sem marcadores nao foi instalado.")
            }
            if (text != original) file.writeText(text)
        }

        contractsFile.asFile.takeIf { it.exists() }?.let { file ->
            var text = file.readText()
            val original = text
            if ("indrive_markerless_core_route_0_1_87" !in text) {
                text = text.replace(
                    """        val hasRoute = "card.route.address" in features ||
            "card.route.ab_markers" in features ||
            "card.route.marked_stops" in features ||
            routeMarkerRegex.findAll(text).count() >= 2
""",
                    """        val hasRoute = "card.route.address" in features ||
            "card.route.two_addresses" in features || // indrive_markerless_core_route_0_1_87
            "card.route.ab_markers" in features ||
            "card.route.marked_stops" in features ||
            routeMarkerRegex.findAll(text).count() >= 2
""",
                )
            }
            if ("indrive_markerless_core_route_0_1_87" !in text) {
                throw org.gradle.api.GradleException("Core inDrive nao aceitou dois enderecos sem A/B textual.")
            }
            if (text != original) file.writeText(text)
        }
    }
}

inDriveMarkerlessLiveCardFix.configure {
    mustRunAfter(
        "inDriveCardContractMatch",
        "coreCardMatchEnginePatch",
        "rotaCertaCoreGate",
    )
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(inDriveMarkerlessLiveCardFix)
}
