package br.com.mapeiaia.rotacerta

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "rota_certa")
private const val MAX_IMPORTED_RADARS = 50000

class SettingsRepository(private val context: Context) {
    private val homeAddress = stringPreferencesKey("home_address")
    private val alternativeAddress = stringPreferencesKey("alternative_address")
    private val homeRadiusKm = doublePreferencesKey("home_radius_km")
    private val alternativeRadiusKm = doublePreferencesKey("alternative_radius_km")
    private val desiredKeywords = stringPreferencesKey("desired_keywords")
    private val avoidedKeywords = stringPreferencesKey("avoided_keywords")
    private val googleMapsApiKey = stringPreferencesKey("google_maps_api_key")
    private val homeCoordinate = stringPreferencesKey("home_coordinate")
    private val alternativeCoordinate = stringPreferencesKey("alternative_coordinate")
    private val bubbleOpacity = doublePreferencesKey("bubble_opacity")
    private val bubbleDarkMode = booleanPreferencesKey("bubble_dark_mode")
    private val restrictToSelectedRideApps = booleanPreferencesKey("restrict_to_selected_ride_apps")
    private val monitor99 = booleanPreferencesKey("monitor_99")
    private val monitorUber = booleanPreferencesKey("monitor_uber")
    private val monitorInDrive = booleanPreferencesKey("monitor_indrive")
    private val extraMonitoredPackages = stringPreferencesKey("extra_monitored_packages")
    private val requireRegisteredRideCard = booleanPreferencesKey("require_registered_ride_card")
    private val proximityAlertDistanceMeters = intPreferencesKey("proximity_alert_distance_meters")
    private val history = stringPreferencesKey("history")
    private val liveDiagnostic = stringPreferencesKey("live_diagnostic")
    private val rideCardTemplates = stringPreferencesKey("ride_card_templates")
    private val capturedRideScreens = stringPreferencesKey("captured_ride_screens")
    private val savedPlacesKey = stringPreferencesKey("saved_places")
    private val importedRadarsKey = stringPreferencesKey("imported_radars")
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            homeAddress = prefs[homeAddress].orEmpty(),
            alternativeAddress = prefs[alternativeAddress].orEmpty(),
            homeRadiusKm = prefs[homeRadiusKm] ?: 10.0,
            alternativeRadiusKm = prefs[alternativeRadiusKm] ?: 10.0,
            desiredKeywords = prefs[desiredKeywords].orEmpty(),
            avoidedKeywords = prefs[avoidedKeywords].orEmpty(),
            googleMapsApiKey = prefs[googleMapsApiKey]?.takeIf { it.isNotBlank() }
                ?: BuildConfig.GOOGLE_MAPS_API_KEY,
            homeCoordinate = decodeCoordinate(prefs[homeCoordinate]),
            alternativeCoordinate = decodeCoordinate(prefs[alternativeCoordinate]),
            bubbleOpacity = prefs[bubbleOpacity] ?: 1.0,
            bubbleDarkMode = prefs[bubbleDarkMode] ?: false,
            restrictToSelectedRideApps = prefs[restrictToSelectedRideApps] ?: false,
            monitor99 = prefs[monitor99] ?: true,
            monitorUber = prefs[monitorUber] ?: true,
            monitorInDrive = prefs[monitorInDrive] ?: true,
            extraMonitoredPackages = prefs[extraMonitoredPackages].orEmpty(),
            requireRegisteredRideCard = prefs[requireRegisteredRideCard] ?: true,
            proximityAlertDistanceMeters = prefs[proximityAlertDistanceMeters] ?: 200,
        )
    }

    val analyses: Flow<List<AnalysisResult>> = context.dataStore.data.map { prefs ->
        runCatching { json.decodeFromString<List<AnalysisResult>>(prefs[history].orEmpty()) }
            .getOrDefault(emptyList())
    }

    val diagnostic: Flow<LiveDiagnostic?> = context.dataStore.data.map { prefs ->
        runCatching { json.decodeFromString<LiveDiagnostic>(prefs[liveDiagnostic].orEmpty()) }.getOrNull()
    }

    val cardTemplates: Flow<List<RideCardTemplate>> = context.dataStore.data.map { prefs ->
        runCatching { json.decodeFromString<List<RideCardTemplate>>(prefs[rideCardTemplates].orEmpty()) }
            .getOrDefault(emptyList())
    }

    val capturedScreens: Flow<List<CapturedRideScreen>> = context.dataStore.data.map { prefs ->
        runCatching { json.decodeFromString<List<CapturedRideScreen>>(prefs[capturedRideScreens].orEmpty()) }
            .getOrDefault(emptyList())
    }

    val savedPlaces: Flow<List<SavedPlace>> = context.dataStore.data.map { prefs ->
        runCatching { json.decodeFromString<List<SavedPlace>>(prefs[savedPlacesKey].orEmpty()) }
            .getOrDefault(emptyList())
    }

    val importedRadars: Flow<List<ImportedRadar>> = context.dataStore.data.map { prefs ->
        runCatching { json.decodeFromString<List<ImportedRadar>>(prefs[importedRadarsKey].orEmpty()) }
            .getOrDefault(emptyList())
    }

    val radarImportSummary: Flow<RadarImportSummary> = importedRadars.map { radars ->
        RadarImportSummary(
            count = radars.size,
            lastImportedAtMillis = radars.maxOfOrNull { it.createdAtMillis } ?: 0L,
        )
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[homeAddress] = settings.homeAddress
            prefs[alternativeAddress] = settings.alternativeAddress
            prefs[homeRadiusKm] = settings.homeRadiusKm
            prefs[alternativeRadiusKm] = settings.alternativeRadiusKm
            prefs[desiredKeywords] = settings.desiredKeywords
            prefs[avoidedKeywords] = settings.avoidedKeywords
            prefs[bubbleOpacity] = settings.bubbleOpacity.coerceIn(0.25, 1.0)
            prefs[bubbleDarkMode] = settings.bubbleDarkMode
            prefs[restrictToSelectedRideApps] = settings.restrictToSelectedRideApps
            prefs[monitor99] = settings.monitor99
            prefs[monitorUber] = settings.monitorUber
            prefs[monitorInDrive] = settings.monitorInDrive
            prefs[extraMonitoredPackages] = settings.extraMonitoredPackages.trim()
            prefs[requireRegisteredRideCard] = settings.requireRegisteredRideCard
            prefs[proximityAlertDistanceMeters] = settings.proximityAlertDistanceMeters.coerceIn(200, 1000)
            if (settings.googleMapsApiKey.isBlank() || settings.googleMapsApiKey == BuildConfig.GOOGLE_MAPS_API_KEY) {
                prefs.remove(googleMapsApiKey)
            } else {
                prefs[googleMapsApiKey] = settings.googleMapsApiKey.trim()
            }
            settings.homeCoordinate?.let { prefs[homeCoordinate] = json.encodeToString(it) } ?: prefs.remove(homeCoordinate)
            settings.alternativeCoordinate?.let { prefs[alternativeCoordinate] = json.encodeToString(it) } ?: prefs.remove(alternativeCoordinate)
        }
    }

    suspend fun addAnalysis(result: AnalysisResult) {
        context.dataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<AnalysisResult>>(prefs[history].orEmpty()) }
                .getOrDefault(emptyList())
            prefs[history] = json.encodeToString((listOf(result) + current).take(50))
        }
    }

    suspend fun saveDiagnostic(diagnostic: LiveDiagnostic) {
        context.dataStore.edit { prefs ->
            prefs[liveDiagnostic] = json.encodeToString(diagnostic)
        }
    }

    suspend fun addCardTemplate(template: RideCardTemplate) {
        context.dataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<RideCardTemplate>>(prefs[rideCardTemplates].orEmpty()) }
                .getOrDefault(emptyList())
            val updated = listOf(template) + current.filterNot { it.id == template.id || it.sampleHash == template.sampleHash }
            prefs[rideCardTemplates] = json.encodeToString(updated.take(30))
        }
    }

    suspend fun removeCardTemplate(templateId: String) {
        context.dataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<RideCardTemplate>>(prefs[rideCardTemplates].orEmpty()) }
                .getOrDefault(emptyList())
            prefs[rideCardTemplates] = json.encodeToString(current.filterNot { it.id == templateId })
        }
    }

    suspend fun addCapturedScreen(screen: CapturedRideScreen) {
        if (screen.textPreview.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<CapturedRideScreen>>(prefs[capturedRideScreens].orEmpty()) }
                .getOrDefault(emptyList())
            val updated = listOf(screen) + current.filterNot { it.textHash == screen.textHash && it.packageName == screen.packageName }
            prefs[capturedRideScreens] = json.encodeToString(updated.take(20))
        }
    }

    suspend fun addSavedPlace(place: SavedPlace) {
        context.dataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<SavedPlace>>(prefs[savedPlacesKey].orEmpty()) }
                .getOrDefault(emptyList())
            val updated = listOf(place) + current.filterNot { it.id == place.id }
            prefs[savedPlacesKey] = json.encodeToString(updated.take(200))
        }
    }

    suspend fun updateSavedPlace(place: SavedPlace) {
        context.dataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<SavedPlace>>(prefs[savedPlacesKey].orEmpty()) }
                .getOrDefault(emptyList())
            prefs[savedPlacesKey] = json.encodeToString(current.map { if (it.id == place.id) place else it })
        }
    }

    suspend fun removeSavedPlace(placeId: String) {
        context.dataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<SavedPlace>>(prefs[savedPlacesKey].orEmpty()) }
                .getOrDefault(emptyList())
            prefs[savedPlacesKey] = json.encodeToString(current.filterNot { it.id == placeId })
        }
    }

    suspend fun replaceImportedRadars(radars: List<ImportedRadar>) {
        context.dataStore.edit { prefs ->
            prefs[importedRadarsKey] = json.encodeToString(radars.take(MAX_IMPORTED_RADARS))
        }
    }

    suspend fun clearImportedRadars() {
        context.dataStore.edit { prefs -> prefs.remove(importedRadarsKey) }
    }

    suspend fun exportBackupJson(): String {
        val backupSettings = settings.first().let { current ->
            if (current.googleMapsApiKey == BuildConfig.GOOGLE_MAPS_API_KEY) {
                current.copy(googleMapsApiKey = "")
            } else {
                current
            }
        }
        val backup = RotaCertaBackup(
            createdAtMillis = System.currentTimeMillis(),
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            settings = backupSettings,
            analyses = analyses.first(),
            cardTemplates = cardTemplates.first(),
            capturedScreens = capturedScreens.first(),
            savedPlaces = savedPlaces.first(),
            importedRadars = importedRadars.first(),
        )
        return json.encodeToString(backup)
    }

    suspend fun restoreBackupJson(content: String): RotaCertaBackup {
        val backup = json.decodeFromString<RotaCertaBackup>(content)
        saveSettings(backup.settings)
        context.dataStore.edit { prefs ->
            prefs[history] = json.encodeToString(backup.analyses.take(50))
            prefs[rideCardTemplates] = json.encodeToString(backup.cardTemplates.take(30))
            prefs[capturedRideScreens] = json.encodeToString(backup.capturedScreens.take(20))
            prefs[savedPlacesKey] = json.encodeToString(backup.savedPlaces.take(200))
            prefs[importedRadarsKey] = json.encodeToString(backup.importedRadars.take(MAX_IMPORTED_RADARS))
            prefs.remove(liveDiagnostic)
        }
        return backup
    }

    private fun decodeCoordinate(value: String?): Coordinate? =
        runCatching { json.decodeFromString<Coordinate>(value.orEmpty()) }.getOrNull()
}
