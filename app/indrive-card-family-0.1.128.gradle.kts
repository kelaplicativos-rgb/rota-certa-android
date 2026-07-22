// Rota Certa 0.1.128
// Reconhece variacoes reais do card inDrive sem remover o modelo manual obrigatorio.
// Elementos volateis (tempo, km de aproximacao e layout) nao podem invalidar uma
// oferta que conserva o mesmo pacote, dois enderecos e os controles fortes da corrida.

val inDriveCardFamily128 by tasks.registering {
    val matcherFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcher.kt",
    )
    inputs.file(matcherFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = matcherFile.asFile
        if (!file.exists()) throw GradleException("RideCardTemplateMatcher.kt nao encontrado.")
        var source = file.readText()

        if ("indrive_offer_family_features_0_1_128" !in source) {
            val riskAnchor = "        val hasRiskArea = \"area de risco\" in normalized\n"
            if (riskAnchor !in source) throw GradleException("Ancora de caracteristicas do inDrive nao encontrada.")
            source = source.replaceFirst(
                riskAnchor,
                riskAnchor + """        val hasStrongInDriveOfferFamily =
            "pedido de viagem" in normalized &&
                ("aceitar por" in normalized || "ofereca sua tarifa" in normalized) &&
                moneyCount >= 1 &&
                addressCount >= 2 // indrive_offer_family_features_0_1_128
""",
            )

            val routeBlockAnchor = "        if (hasRouteBlock) features += \"card.crop.route_block\"\n"
            if (routeBlockAnchor !in source) throw GradleException("Marcador do bloco de rota nao encontrado.")
            source = source.replaceFirst(
                routeBlockAnchor,
                """        if (hasStrongInDriveOfferFamily) features += "card.indrive.offer_two_addresses"
        if (hasRouteBlock || hasStrongInDriveOfferFamily) features += "card.crop.route_block"
""",
            )
        }

        if ("indrive_same_package_family_match_0_1_128" !in source) {
            val filterAnchor = """                if (universalPackage) {
                    looksLikeLearnableRideCard(text) &&
                        cropOk &&
                        match.score >= UNIVERSAL_MIN_SCORE &&
                        match.matchedFeatures.size >= required.size.coerceAtMost(UNIVERSAL_MIN_FEATURES).coerceAtLeast(MIN_FEATURES)
                } else {
                    samePackage &&
                        cropOk &&
                        (structuralOk || "card.route.marked_stops" in match.matchedFeatures) &&
                        match.score >= MIN_SCORE &&
                        match.matchedFeatures.size >= MIN_FEATURES
                }
"""
            if (filterAnchor !in source) throw GradleException("Filtro final do matcher nao encontrado.")
            source = source.replaceFirst(
                filterAnchor,
                """                val strongInDriveFamily128 =
                    normalizedPackage == INDRIVE_PACKAGE &&
                        samePackage &&
                        "card.indrive.offer_two_addresses" in liveFeatures &&
                        "card.crop.route_block" in match.matchedFeatures &&
                        "card.route.two_addresses" in match.matchedFeatures &&
                        "pedido de viagem" in match.matchedFeatures &&
                        ("aceitar por" in match.matchedFeatures || "ofereca sua tarifa" in match.matchedFeatures) &&
                        "valor em reais" in match.matchedFeatures
                if (universalPackage) {
                    looksLikeLearnableRideCard(text) &&
                        cropOk &&
                        match.score >= UNIVERSAL_MIN_SCORE &&
                        match.matchedFeatures.size >= required.size.coerceAtMost(UNIVERSAL_MIN_FEATURES).coerceAtLeast(MIN_FEATURES)
                } else {
                    strongInDriveFamily128 || // indrive_same_package_family_match_0_1_128
                        (samePackage &&
                            cropOk &&
                            (structuralOk || "card.route.marked_stops" in match.matchedFeatures) &&
                            match.score >= MIN_SCORE &&
                            match.matchedFeatures.size >= MIN_FEATURES)
                }
""",
            )
        }

        listOf(
            "indrive_offer_family_features_0_1_128",
            "card.indrive.offer_two_addresses",
            "indrive_same_package_family_match_0_1_128",
            "samePackage",
        ).forEach { marker ->
            if (marker !in source) throw GradleException("Matcher inDrive 0.1.128 incompleto: $marker")
        }
        file.writeText(source)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(inDriveCardFamily128)
}
