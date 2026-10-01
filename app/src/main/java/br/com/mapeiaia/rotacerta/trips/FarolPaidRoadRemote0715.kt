package br.com.mapeiaia.rotacerta.trips

import kotlinx.serialization.Serializable

@Serializable
data class FarolPaidRoadTarget0715(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class FarolPaidRoadRequest0715(
    val destination: String,
    val context: String,
    val packageName: String,
    val fingerprint: String,
    val targets: List<FarolPaidRoadTarget0715>,
)

@Serializable
data class FarolPaidRoadResponse0715(
    val status: String = "",
    val normalizedAddress: String = "",
    val confidence: Double = 0.0,
    val roadKm: Double? = null,
    val reason: String = "",
    val routeProvider: String = "",
    val addressProvider: String = "",
    val provider: String = "",
    val model: String = "",
    val contract: String = "",
) {
    val resolved: Boolean
        get() = status.equals("RESOLVED", ignoreCase = true) &&
            normalizedAddress.isNotBlank() &&
            confidence >= 0.80 &&
            roadKm?.let { it.isFinite() && it >= 0.0 } == true
}
