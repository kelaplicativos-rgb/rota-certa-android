// Match contratual para card individual do inDrive.
// A bolinha nao deve depender de todos os detalhes dinamicos do OCR.
// Ela deve reconhecer o card salvo quando o contrato visual/textual aparece:
// pedido de viagem + aceitar/oferta + valor + rota A/B + enderecos.

val inDriveCardContractMatch by tasks.registering {
    val matcherFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcher.kt")
    inputs.file(matcherFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = matcherFile.asFile
        if (!file.exists()) return@doLast

        var text = file.readText()
        val original = text

        if ("private val acceptButtonRegex" !in text) {
            text = text.replace(
                """    private val timeDistanceLineRegex = Regex(
        """,
                """    private val acceptButtonRegex = Regex("aceitar\\s+por\\s+r\\$\\s*\\d+", RegexOption.IGNORE_CASE)
    private val farePerKmRegex = Regex("r\\$\\s*\\d+(?:[,.]\\d+)?\\s*/\\s*km", RegexOption.IGNORE_CASE)
    private val inDriveOfferButtonRegex = Regex("ofere[cç]a\\s+sua\\s+tarifa", RegexOption.IGNORE_CASE)
    private val routeMarkerInlineRegex = Regex("(?m)^\\s*[ab]\\s+.{5,}", RegexOption.IGNORE_CASE)

    private val timeDistanceLineRegex = Regex(
        """,
            )
        }

        if ("card.contract.indrive_individual" !in text) {
            text = text.replace(
                """        if (hasRouteBlock) features += "card.crop.route_block"
        return features
""",
                """        if (hasRouteBlock) features += "card.crop.route_block"
        if (isInDriveIndividualContract(normalized, text, moneyCount, distanceCount, addressCount, markerCount, endpointTextLines)) {
            features += "card.contract.indrive_individual"
        }
        return features
""",
            )
        }

        if ("private fun isInDriveIndividualContract" !in text) {
            text = text.replace(
                """    private fun isRouteCardCrop(
""",
                """    private fun isInDriveIndividualContract(
        normalized: String,
        rawText: String,
        moneyCount: Int,
        distanceCount: Int,
        addressCount: Int,
        markerCount: Int,
        endpointTextLines: Int,
    ): Boolean {
        if (ownAppMarkers.any { marker -> marker in normalized }) return false
        val hasRideTitle = "pedido de viagem" in normalized || "pedidos de viagem" in normalized
        val hasAccept = acceptButtonRegex.containsMatchIn(rawText) || "aceitar por" in normalized
        val hasOffer = inDriveOfferButtonRegex.containsMatchIn(rawText) || "ofereca sua tarifa" in normalized || "ofereça sua tarifa" in normalized
        val hasMoney = moneyCount >= 1
        val hasRouteKm = distanceCount >= 1 || farePerKmRegex.containsMatchIn(rawText)
        val hasTwoEndpoints = endpointTextLines >= 2 || markerCount >= 2 || routeMarkerInlineRegex.findAll(rawText).count() >= 2
        val hasAddress = addressCount >= 1 || endpointTextLines >= 2
        return hasRideTitle && hasAccept && hasOffer && hasMoney && hasRouteKm && hasTwoEndpoints && hasAddress
    }

    private fun isRouteCardCrop(
""",
            )
        }

        if ("inDriveContractOk" !in text) {
            text = text.replace(
                """                val requiredStructuralFeatures = structuralFeatures.intersect(required)
                val structuralOk = requiredStructuralFeatures.all { it in match.matchedFeatures }
                val cropOk = "card.crop.route_block" in match.matchedFeatures &&
                    requiredCardFeatures.filter { it in strictCardFeatures }.all { it in match.matchedFeatures }
                if (universalPackage) {
""",
                """                val requiredStructuralFeatures = structuralFeatures.intersect(required)
                val structuralOk = requiredStructuralFeatures.all { it in match.matchedFeatures }
                val inDriveContractOk = normalizedPackage == INDRIVE_PACKAGE && "card.contract.indrive_individual" in match.matchedFeatures // indrive_contract_match_0_1_84
                val cropOk = if (inDriveContractOk) {
                    true
                } else {
                    "card.crop.route_block" in match.matchedFeatures &&
                        requiredCardFeatures.filter { it in strictCardFeatures }.all { it in match.matchedFeatures }
                }
                if (universalPackage) {
""",
            )
            text = text.replace(
                """                    samePackage &&
                        cropOk &&
                        (structuralOk || "card.route.marked_stops" in match.matchedFeatures) &&
                        match.score >= MIN_SCORE &&
                        match.matchedFeatures.size >= MIN_FEATURES
""",
                """                    samePackage &&
                        cropOk &&
                        (inDriveContractOk || structuralOk || "card.route.marked_stops" in match.matchedFeatures) &&
                        (inDriveContractOk || match.score >= MIN_SCORE) &&
                        match.matchedFeatures.size >= if (inDriveContractOk) INDRIVE_CONTRACT_MIN_FEATURES else MIN_FEATURES
""",
            )
        }

        if ("private const val INDRIVE_CONTRACT_MIN_FEATURES" !in text) {
            text = text.replace(
                """    private const val MIN_FEATURES = 4
""",
                """    private const val MIN_FEATURES = 4
    private const val INDRIVE_CONTRACT_MIN_FEATURES = 6
""",
            )
        }

        if ("indrive_contract_match_0_1_84" !in text) {
            throw org.gradle.api.GradleException("Nao consegui instalar o match contratual do card inDrive.")
        }
        if ("card.contract.indrive_individual" !in text) {
            throw org.gradle.api.GradleException("Nao consegui instalar a feature de card individual inDrive.")
        }
        if ("private const val INDRIVE_CONTRACT_MIN_FEATURES" !in text) {
            throw org.gradle.api.GradleException("Nao consegui instalar o limite minimo do contrato inDrive.")
        }

        if (text != original) file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(inDriveCardContractMatch)
}
