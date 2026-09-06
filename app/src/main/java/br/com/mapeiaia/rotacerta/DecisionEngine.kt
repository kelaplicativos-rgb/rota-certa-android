package br.com.mapeiaia.rotacerta

class DecisionEngine {
    fun decide(
        fields: RideFields,
        settings: AppSettings,
        destinationCoordinate: Coordinate?,
        homeCoordinate: Coordinate?,
        alternativeCoordinate: Coordinate?,
        fullText: String,
        homeDistanceKm: Double? = null,
        alternativeDistanceKm: Double? = null,
        alternativeLabel: String? = null,
    ): AnalysisResult {
        val destinationText = fields.destination.orEmpty()
        if (destinationText.isBlank()) {
            return result(fields, fullText, Recommendation.InsufficientData, "Nao foi possivel identificar o destino final do passageiro.")
        }

        if (hasAvoidedKeyword(destinationText, settings.avoidedKeywords)) {
            return result(fields, fullText, Recommendation.OutsideRadius, "Destino final contem palavra ou bairro evitado.")
        }

        val hasExactAddressRoute = homeDistanceKm != null || alternativeDistanceKm != null
        if (destinationCoordinate == null && !hasExactAddressRoute) {
            return result(fields, fullText, Recommendation.InsufficientData, "Destino final identificado, mas sem coordenada ou rota exata confiavel.")
        }

        if (homeCoordinate == null && alternativeCoordinate == null) {
            return result(fields, fullText, Recommendation.InsufficientData, "Configure a Casa ou pelo menos um alfinete com coordenada confiavel.")
        }

        val needsHomeDistance = homeCoordinate != null
        val needsAlternativeDistance = alternativeCoordinate != null
        if ((needsHomeDistance && homeDistanceKm == null) || (needsAlternativeDistance && alternativeDistanceKm == null)) {
            return result(
                fields = fields,
                fullText = fullText,
                recommendation = Recommendation.InsufficientData,
                reason = "Distancia real do Google Maps indisponivel; calculo em linha reta proibido.",
            )
        }

        val distanceToHome = if (needsHomeDistance) homeDistanceKm else null
        val distanceToAlternative = if (needsAlternativeDistance) alternativeDistanceKm else null
        val insideHome = distanceToHome != null && distanceToHome <= settings.homeRadiusKm
        val insideAlternative = distanceToAlternative != null && distanceToAlternative <= settings.alternativeRadiusKm
        val safeAlternativeLabel = alternativeLabel?.trim()?.takeIf { it.isNotBlank() } ?: "alfinete"

        val recommendation = if (insideHome || insideAlternative) Recommendation.GoodRide else Recommendation.OutsideRadius
        val reason = when {
            insideHome && insideAlternative && distanceToHome!! <= distanceToAlternative!! ->
                "Destino final dentro do raio da Casa por rota real do Google Maps."
            insideAlternative -> "Destino final dentro do raio de $safeAlternativeLabel por rota real do Google Maps."
            insideHome -> "Destino final dentro do raio da Casa por rota real do Google Maps."
            else -> "Destino final fora dos raios configurados por rota real do Google Maps."
        }

        return result(
            fields = fields,
            fullText = fullText,
            recommendation = recommendation,
            reason = reason,
            pickupToHomeKm = distanceToHome,
            pickupToAlternativeKm = distanceToAlternative,
        )
    }

    fun decideWorkRegion(
        fields: RideFields,
        settings: AppSettings,
        fullText: String,
        homeTargetActive: Boolean,
        homeDistanceKm: Double?,
        pinRoutes: List<WorkRegionPinRoute>,
    ): AnalysisResult {
        val destinationText = fields.destination.orEmpty()
        if (destinationText.isBlank()) {
            return result(fields, fullText, Recommendation.InsufficientData, "Nao foi possivel identificar o destino final do passageiro.")
        }
        if (hasAvoidedKeyword(destinationText, settings.avoidedKeywords)) {
            return result(fields, fullText, Recommendation.OutsideRadius, "Destino final contem palavra ou bairro evitado.")
        }

        val activePins = if (settings.alternativeTargetEnabled) pinRoutes else emptyList()
        if (!homeTargetActive && activePins.isEmpty()) {
            return result(
                fields,
                fullText,
                Recommendation.InsufficientData,
                "Ligue Casa ou cadastre e ligue pelo menos um alfinete na regiao de trabalho.",
            )
        }

        val insideCandidates = buildList {
            if (homeTargetActive && homeDistanceKm != null && homeDistanceKm <= settings.homeRadiusKm) {
                add(WorkRegionWinner("Casa", homeDistanceKm, isHome = true))
            }
            activePins.forEach { route ->
                val distance = route.distanceKm ?: return@forEach
                if (distance <= settings.alternativeRadiusKm) {
                    add(WorkRegionWinner(route.pin.address, distance, isHome = false))
                }
            }
        }
        val nearestPinDistance = activePins.mapNotNull(WorkRegionPinRoute::distanceKm).minOrNull()
        val winner = insideCandidates.minByOrNull(WorkRegionWinner::distanceKm)
        if (winner != null) {
            return result(
                fields = fields,
                fullText = fullText,
                recommendation = Recommendation.GoodRide,
                reason = if (winner.isHome) {
                    "Destino final dentro do raio da Casa por rota real do Google Maps."
                } else {
                    "Destino final dentro do raio do alfinete ${winner.label} por rota real do Google Maps."
                },
                pickupToHomeKm = homeDistanceKm,
                pickupToAlternativeKm = nearestPinDistance,
            )
        }

        val allExact = (!homeTargetActive || homeDistanceKm != null) && activePins.all { it.distanceKm != null }
        if (!allExact) {
            return result(
                fields = fields,
                fullText = fullText,
                recommendation = Recommendation.InsufficientData,
                reason = "Uma ou mais distancias exatas da regiao de trabalho ainda nao ficaram disponiveis.",
                pickupToHomeKm = homeDistanceKm,
                pickupToAlternativeKm = nearestPinDistance,
            )
        }

        return result(
            fields = fields,
            fullText = fullText,
            recommendation = Recommendation.OutsideRadius,
            reason = "Destino final fora da Casa e de todos os alfinetes ligados por rota real do Google Maps.",
            pickupToHomeKm = homeDistanceKm,
            pickupToAlternativeKm = nearestPinDistance,
        )
    }

    private fun hasAvoidedKeyword(text: String, avoidedKeywords: String): Boolean {
        val normalizedText = text.lowercase()
        return avoidedKeywords
            .split(",", ";", "\n")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .any { normalizedText.contains(it) }
    }

    private fun result(
        fields: RideFields,
        fullText: String,
        recommendation: Recommendation,
        reason: String,
        pickupToHomeKm: Double? = null,
        pickupToAlternativeKm: Double? = null,
    ) = AnalysisResult(
        createdAtMillis = System.currentTimeMillis(),
        extractedText = fullText,
        fields = fields,
        recommendation = recommendation,
        reason = reason,
        pickupToHomeKm = pickupToHomeKm,
        pickupToAlternativeKm = pickupToAlternativeKm,
    )

    private data class WorkRegionWinner(
        val label: String,
        val distanceKm: Double,
        val isHome: Boolean,
    )
}
