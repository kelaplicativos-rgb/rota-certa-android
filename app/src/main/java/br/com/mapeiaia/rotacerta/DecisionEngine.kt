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
    ): AnalysisResult {
        val destinationText = fields.destination.orEmpty()
        if (destinationText.isBlank()) {
            return result(fields, fullText, Recommendation.InsufficientData, "Nao foi possivel identificar o destino final do passageiro.")
        }

        if (hasAvoidedKeyword(destinationText, settings.avoidedKeywords)) {
            return result(fields, fullText, Recommendation.OutsideRadius, "Destino final contem palavra ou bairro evitado.")
        }

        if (destinationCoordinate == null) {
            return result(fields, fullText, Recommendation.InsufficientData, "Destino final identificado, mas sem coordenada confiavel.")
        }

        if (homeCoordinate == null && alternativeCoordinate == null) {
            return result(fields, fullText, Recommendation.InsufficientData, "Configure a casa ou o alfinete com coordenada confiavel.")
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

        if (distanceToHome == null && distanceToAlternative == null) {
            return result(
                fields = fields,
                fullText = fullText,
                recommendation = Recommendation.InsufficientData,
                reason = "Nao foi possivel calcular a distancia real do destino final pelo Google Maps.",
            )
        }

        val insideHome = distanceToHome != null && distanceToHome <= settings.homeRadiusKm
        val insideAlternative = distanceToAlternative != null && distanceToAlternative <= settings.alternativeRadiusKm

        val recommendation = if (insideHome || insideAlternative) Recommendation.GoodRide else Recommendation.OutsideRadius
        val reason = when {
            insideHome -> "Destino final dentro do raio da casa por rota real do Google Maps."
            insideAlternative -> "Destino final dentro do raio do alfinete por rota real do Google Maps."
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
}
