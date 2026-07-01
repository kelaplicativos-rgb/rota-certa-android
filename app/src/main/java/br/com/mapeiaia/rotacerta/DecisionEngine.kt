package br.com.mapeiaia.rotacerta

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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

        if (settings.googleMapsApiKey.isBlank()) {
            return result(fields, fullText, Recommendation.InsufficientData, "Configure a chave Google Maps para calcular a distancia real do destino final.")
        }

        if (destinationCoordinate == null) {
            return result(fields, fullText, Recommendation.InsufficientData, "Destino final identificado, mas sem coordenada confiavel.")
        }

        if (homeCoordinate == null && alternativeCoordinate == null) {
            return result(fields, fullText, Recommendation.InsufficientData, "Configure a casa ou o alfinete com coordenada confiavel.")
        }

        val distanceToHome = homeCoordinate?.let { homeDistanceKm }
        val distanceToAlternative = alternativeCoordinate?.let { alternativeDistanceKm }

        if (distanceToHome == null && distanceToAlternative == null) {
            return result(
                fields = fields,
                fullText = fullText,
                recommendation = Recommendation.InsufficientData,
                reason = "Nao foi possivel calcular a rota pelo Google Maps.",
            )
        }

        val insideHome = distanceToHome != null && distanceToHome <= settings.homeRadiusKm
        val insideAlternative = distanceToAlternative != null && distanceToAlternative <= settings.alternativeRadiusKm

        val recommendation = if (insideHome || insideAlternative) Recommendation.GoodRide else Recommendation.OutsideRadius
        val reason = when {
            insideHome -> "Destino final dentro do raio da casa pelo Google Maps."
            insideAlternative -> "Destino final dentro do raio do alfinete pelo Google Maps."
            else -> "Destino final fora dos raios configurados pelo Google Maps."
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

    @Suppress("unused")
    private fun haversineKm(a: Coordinate, b: Coordinate): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2.0) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)
        return 2 * earthRadiusKm * asin(sqrt(h))
    }
}
