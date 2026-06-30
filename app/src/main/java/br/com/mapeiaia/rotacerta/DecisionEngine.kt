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
        pickupCoordinate: Coordinate?,
        homeCoordinate: Coordinate?,
        alternativeCoordinate: Coordinate?,
        fullText: String,
    ): AnalysisResult {
        if (fields.pickup.isNullOrBlank()) {
            return result(fields, fullText, Recommendation.InsufficientData, "Nao foi possivel identificar o embarque.")
        }

        if (hasAvoidedKeyword(fullText, settings.avoidedKeywords)) {
            return result(fields, fullText, Recommendation.OutsideRadius, "Encontrou palavra ou bairro evitado.")
        }

        if (pickupCoordinate == null) {
            return result(fields, fullText, Recommendation.InsufficientData, "Embarque identificado, mas sem coordenada confiavel.")
        }

        val distanceToHome = homeCoordinate?.let { haversineKm(pickupCoordinate, it) }
        val distanceToAlternative = alternativeCoordinate?.let { haversineKm(pickupCoordinate, it) }

        val insideHome = distanceToHome != null && distanceToHome <= settings.homeRadiusKm
        val insideAlternative = distanceToAlternative != null && distanceToAlternative <= settings.alternativeRadiusKm

        val recommendation = if (insideHome || insideAlternative) Recommendation.GoodRide else Recommendation.OutsideRadius
        val reason = when {
            insideHome -> "Embarque dentro do raio da casa."
            insideAlternative -> "Embarque dentro do raio da localidade alternativa."
            else -> "Embarque fora dos raios configurados."
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
