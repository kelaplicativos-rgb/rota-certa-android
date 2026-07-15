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
    val monitor99: Boolean = false,
    val monitorUber: Boolean = false,
    val monitorInDrive: Boolean = false,
    val extraMonitoredPackages: String = "",
    val appEnabled: Boolean = true,
    val liveReadingEnabled: Boolean = true,
    val homeTargetEnabled: Boolean = true,
    val alternativeTargetEnabled: Boolean = true,
    val requireRegisteredRideCard: Boolean = false,
    val proximityAlertsEnabled: Boolean = true,
    val proximityAlertDistanceMeters: Int = 200,
)

@Serializable
data class RotaCertaBackup(
    val version: Int = 1,
    val createdAtMillis: Long = 0L,
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val settings: AppSettings = AppSettings(),
    val analyses: List<AnalysisResult> = emptyList(),
    val cardTemplates: List<RideCardTemplate> = emptyList(),
    val capturedScreens: List<CapturedRideScreen> = emptyList(),
    val savedPlaces: List<SavedPlace> = emptyList(),
    val importedRadars: List<ImportedRadar> = emptyList(),
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

@Serializable
data class RideCardTemplate(
    val id: String,
    val name: String,
    val packageName: String? = null,
    val requiredFeatures: List<String> = emptyList(),
    val sampleHash: Int? = null,
    val createdAtMillis: Long = 0L,
)

@Serializable
data class CapturedRideScreen(
    val createdAtMillis: Long = 0L,
    val packageName: String? = null,
    val textHash: Int? = null,
    val textPreview: String = "",
    val parserName: String = "",
    val pickup: String? = null,
    val destination: String? = null,
    val fare: String? = null,
)

@Serializable
enum class SavedPlaceType {
    Place,
    ProximityAlert,
}

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
)

@Serializable
data class RadarImportSummary(
    val count: Int = 0,
    val lastImportedAtMillis: Long = 0L,
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
    val registeredCardRequired: Boolean = true,
    val registeredCardMatched: String? = null,
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
