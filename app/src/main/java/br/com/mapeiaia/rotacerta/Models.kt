package br.com.mapeiaia.rotacerta

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val homeAddress: String = "",
    val alternativeAddress: String = "",
    val homeRadiusKm: Double = 10.0,
    val alternativeRadiusKm: Double = 10.0,
    val desiredKeywords: String = "",
    val avoidedKeywords: String = "",
    val googleMapsApiKey: String = "",
    val homeCoordinate: Coordinate? = null,
    val alternativeCoordinate: Coordinate? = null,
    val bubbleOpacity: Double = 1.0,
    val bubbleDarkMode: Boolean = false,
    val restrictToSelectedRideApps: Boolean = false,
    val monitor99: Boolean = true,
    val monitorUber: Boolean = true,
    val monitorInDrive: Boolean = true,
    val extraMonitoredPackages: String = "",
)

@Serializable
data class Coordinate(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class DeviceRegion(
    val city: String = "",
    val country: String = "",
)

@Serializable
data class RideFields(
    val pickup: String? = null,
    val destination: String? = null,
    val fare: String? = null,
    val distance: String? = null,
    val time: String? = null,
)

enum class Recommendation {
    GoodRide,
    OutsideRadius,
    InsufficientData,
}

@Serializable
data class AnalysisResult(
    val createdAtMillis: Long,
    val extractedText: String,
    val fields: RideFields,
    val recommendation: Recommendation,
    val reason: String,
    val pickupToHomeKm: Double? = null,
    val pickupToAlternativeKm: Double? = null,
)
