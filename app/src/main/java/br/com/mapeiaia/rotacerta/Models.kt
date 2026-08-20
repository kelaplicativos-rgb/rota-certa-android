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
    val restrictToSelectedRideApps: Boolean = true,
    val extraMonitoredPackages: String = "",
    val appEnabled: Boolean = false,
    val liveReadingEnabled: Boolean = false,
    val homeTargetEnabled: Boolean = true,
    val alternativeTargetEnabled: Boolean = true,
    val workRegionPins: List<WorkRegionPin> = emptyList(),
    val proximityAlertsEnabled: Boolean = true,
    val proximityAlertDistanceMeters: Int = 500,
    val diagnosticsEnabled: Boolean = false,
    val multiCardFocusLockEnabled: Boolean = true,
    val proximityPopupAutoCloseEnabled: Boolean = true,
)

@Serializable
data class RotaCertaBackup(
    val version: Int = 1,
    val createdAtMillis: Long = 0L,
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val settings: AppSettings = AppSettings(),
    val analyses: List<AnalysisResult> = emptyList(),
    val savedPlaces: List<SavedPlace> = emptyList(),
    val importedRadars: List<ImportedRadar> = emptyList(),
    val quickReplies: List<QuickReply> = emptyList(),
)

@Serializable
data class Coordinate(val latitude: Double, val longitude: Double)

@Serializable
data class WorkRegionPin(
    val id: String,
    val address: String,
    val coordinate: Coordinate? = null,
    val enabled: Boolean = true,
    val createdAtMillis: Long = 0L,
)

@Serializable
data class DeviceRegion(val city: String = "", val country: String = "")

@Serializable
data class RideFields(
    val pickup: String? = null,
    val destination: String? = null,
    val fare: String? = null,
    val distance: String? = null,
    val time: String? = null,
)

@Serializable
data class QuickReply(
    val id: String,
    val title: String = "",
    val text: String,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

@Serializable
enum class SavedPlaceType { Place, ProximityAlert }

@Serializable
data class SavedPlace(
    val id: String,
    val name: String,
    val type: SavedPlaceType = SavedPlaceType.Place,
    val address: String = "",
    val coordinate: Coordinate,
    val alertDistanceMeters: Int? = null,
    val createdAtMillis: Long = 0L,
    val lastTriggeredAtMillis: Long? = null,
    val triggerCountInCurrentApproach: Int = 0,
)

@Serializable
data class ImportedRadar(
    val id: String,
    val coordinate: Coordinate,
    val type: Int,
    val speedKmh: Int? = null,
    val directionType: Int? = null,
    val direction: Int? = null,
    val source: String = "MapaRadar",
    val createdAtMillis: Long = 0L,
    val name: String = "",
)

@Serializable
data class RadarImportSummary(val count: Int = 0, val lastImportedAtMillis: Long = 0L)

enum class Recommendation { GoodRide, OutsideRadius, InsufficientData }

@Serializable
data class AnalysisResult(
    val createdAtMillis: Long,
    val extractedText: String,
    val fields: RideFields,
    val recommendation: Recommendation,
    val reason: String,
    val pickupToHomeKm: Double? = null,
    val pickupToAlternativeKm: Double? = null,
) {
    val destinationToHomeKm: Double? get() = pickupToHomeKm
    val destinationToAlternativeKm: Double? get() = pickupToAlternativeKm
}

@Serializable
data class LiveDiagnostic(
    val createdAtMillis: Long = 0L,
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val packageName: String? = null,
    val stage: String = "",
    val bubbleColor: String = "amarelo",
    val reason: String = "",
    val restrictToSelectedRideApps: Boolean = true,
    val selectedPackages: List<String> = emptyList(),
    val textLength: Int = 0,
    val textHash: Int? = null,
    val textPreview: String = "",
    val pickup: String? = null,
    val destination: String? = null,
    val recommendation: Recommendation? = null,
    val homeDistanceKm: Double? = null,
    val alternativeDistanceKm: Double? = null,
    val error: String? = null,
    val diagnosticLog: String = "",
)
