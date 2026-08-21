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
private const val MAX_WORK_REGION_PINS = 30

class SettingsRepository(private val context: Context) {
    private val tenantScope = RotaCertaTenantRegistry(context.applicationContext).activeScope()
    private fun stringKey(base: String) = stringPreferencesKey(tenantScope.key(base))
    private fun intKey(base: String) = intPreferencesKey(tenantScope.key(base))
    private fun doubleKey(base: String) = doublePreferencesKey(tenantScope.key(base))
    private fun booleanKey(base: String) = booleanPreferencesKey(tenantScope.key(base))

    private val homeAddress = stringKey("home_address")
    private val alternativeAddress = stringKey("alternative_address")
    private val tripDepartureAddress = stringKey("trip_departure_address")
    private val vehicleCapacity = intKey("vehicle_capacity")
    private val homeRadiusKm = doubleKey("home_radius_km")
    private val alternativeRadiusKm = doubleKey("alternative_radius_km")
    private val desiredKeywords = stringKey("desired_keywords")
    private val avoidedKeywords = stringKey("avoided_keywords")
    private val googleMapsApiKey = stringKey("google_maps_api_key")
    private val homeCoordinate = stringKey("home_coordinate")
    private val alternativeCoordinate = stringKey("alternative_coordinate")
    private val workRegionPinsKey = stringKey("work_region_pins")
    private val bubbleOpacity = doubleKey("bubble_opacity")
    private val bubbleDarkMode = booleanKey("bubble_dark_mode")
    private val restrictToSelectedRideApps = booleanKey("restrict_to_selected_ride_apps")
    private val extraMonitoredPackages = stringKey("extra_monitored_packages")
    private val appEnabled = booleanKey("app_enabled")
    private val liveReadingEnabled = booleanKey("live_reading_enabled")
    private val homeTargetEnabled = booleanKey("home_target_enabled")
    private val alternativeTargetEnabled = booleanKey("alternative_target_enabled")
    private val proximityAlertsEnabled = booleanKey("proximity_alerts_enabled")
    private val proximityAlertDistanceMeters = intKey("proximity_alert_distance_meters")
    private val diagnosticsEnabled = booleanKey("diagnostics_enabled")
    private val multiCardFocusLockEnabled = booleanKey("multi_card_focus_lock_enabled")
    private val proximityPopupAutoCloseEnabled = booleanKey("proximity_popup_auto_close_enabled")
    private val history = stringKey("history")
    private val liveDiagnostic = stringKey("live_diagnostic")
    private val savedPlacesKey = stringKey("saved_places")
    private val importedRadarsKey = stringKey("imported_radars")
    private val quickRepliesKey = stringKey("quick_replies")
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            homeAddress = prefs[homeAddress].orEmpty(),
            alternativeAddress = prefs[alternativeAddress].orEmpty(),
            tripDepartureAddress = prefs[tripDepartureAddress].orEmpty(),
            vehicleCapacity = (prefs[vehicleCapacity] ?: 0).coerceIn(0, 999),
            homeRadiusKm = prefs[homeRadiusKm] ?: 10.0,
            alternativeRadiusKm = prefs[alternativeRadiusKm] ?: 10.0,
            desiredKeywords = prefs[desiredKeywords].orEmpty(),
            avoidedKeywords = prefs[avoidedKeywords].orEmpty(),
            googleMapsApiKey = BuildConfig.GOOGLE_MAPS_API_KEY.takeIf { it.isNotBlank() }
                ?: prefs[googleMapsApiKey].orEmpty(),
            homeCoordinate = decodeCoordinate(prefs[homeCoordinate]),
            alternativeCoordinate = decodeCoordinate(prefs[alternativeCoordinate]),
            workRegionPins = decodeWorkRegionPins(prefs[workRegionPinsKey]),
            bubbleOpacity = prefs[bubbleOpacity] ?: 1.0,
            bubbleDarkMode = prefs[bubbleDarkMode] ?: false,
            restrictToSelectedRideApps = prefs[restrictToSelectedRideApps] ?: true,
            extraMonitoredPackages = prefs[extraMonitoredPackages].orEmpty(),
            appEnabled = prefs[appEnabled] ?: false,
            liveReadingEnabled = prefs[liveReadingEnabled] ?: false,
            homeTargetEnabled = prefs[homeTargetEnabled] ?: true,
            alternativeTargetEnabled = prefs[alternativeTargetEnabled] ?: true,
            proximityAlertsEnabled = prefs[proximityAlertsEnabled] ?: true,
            proximityAlertDistanceMeters = (prefs[proximityAlertDistanceMeters] ?: 200).coerceIn(200, 1000),
            diagnosticsEnabled = false,
            multiCardFocusLockEnabled = prefs[multiCardFocusLockEnabled] ?: true,
            proximityPopupAutoCloseEnabled = prefs[proximityPopupAutoCloseEnabled] ?: true,
        )
    }

    val analyses: Flow<List<AnalysisResult>> = context.dataStore.data.map { prefs ->
        runCatching { json.decodeFromString<List<AnalysisResult>>(prefs[history].orEmpty()) }
            .getOrDefault(emptyList())
    }

    val diagnostic: Flow<LiveDiagnostic?> = context.dataStore.data.map { prefs ->
        runCatching { json.decodeFromString<LiveDiagnostic>(prefs[liveDiagnostic].orEmpty()) }.getOrNull()
    }

    val savedPlaces: Flow<List<SavedPlace>> = context.dataStore.data.map { prefs ->
        runCatching { json.decodeFromString<List<SavedPlace>>(prefs[savedPlacesKey].orEmpty()) }
            .getOrDefault(emptyList())
    }

    val importedRadars: Flow<List<ImportedRadar>> = context.dataStore.data.map { prefs ->
        runCatching { json.decodeFromString<List<ImportedRadar>>(prefs[importedRadarsKey].orEmpty()) }
            .getOrDefault(emptyList())
    }

    val quickReplies: Flow<List<QuickReply>> = context.dataStore.data.map { prefs ->
        runCatching { json.decodeFromString<List<QuickReply>>(prefs[quickRepliesKey].orEmpty()) }
            .getOrDefault(emptyList())
            .sortedWith(compareByDescending<QuickReply> { it.updatedAtMillis }.thenBy { it.title })
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
            prefs[tripDepartureAddress] = settings.tripDepartureAddress.trim()
            if (settings.vehicleCapacity in 1..999) {
                prefs[vehicleCapacity] = settings.vehicleCapacity
            } else {
                prefs.remove(vehicleCapacity)
            }
            prefs[homeRadiusKm] = settings.homeRadiusKm
            prefs[alternativeRadiusKm] = settings.alternativeRadiusKm
            prefs[desiredKeywords] = settings.desiredKeywords
            prefs[avoidedKeywords] = settings.avoidedKeywords
            prefs[workRegionPinsKey] = json.encodeToString(settings.workRegionPins.take(MAX_WORK_REGION_PINS))
            prefs[bubbleOpacity] = settings.bubbleOpacity.coerceIn(0.25, 1.0)
            prefs[bubbleDarkMode] = settings.bubbleDarkMode
            prefs[restrictToSelectedRideApps] = settings.restrictToSelectedRideApps
            prefs[extraMonitoredPackages] = settings.extraMonitoredPackages.trim()
            prefs[appEnabled] = settings.appEnabled
            prefs[liveReadingEnabled] = settings.liveReadingEnabled
            prefs[homeTargetEnabled] = settings.homeTargetEnabled
            prefs[alternativeTargetEnabled] = settings.alternativeTargetEnabled
            prefs[proximityAlertsEnabled] = settings.proximityAlertsEnabled
            prefs[proximityAlertDistanceMeters] = settings.proximityAlertDistanceMeters.coerceIn(200, 1000)
            prefs[diagnosticsEnabled] = false // diagnostics_manual_only_checklist_4
            prefs[multiCardFocusLockEnabled] = settings.multiCardFocusLockEnabled
            prefs[proximityPopupAutoCloseEnabled] = settings.proximityPopupAutoCloseEnabled
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
        if (!DiagnosticRuntimeGate.isEnabled()) return // save_diagnostic_manual_gate_checklist_4
        context.dataStore.edit { prefs ->
            prefs[liveDiagnostic] = json.encodeToString(diagnostic)
        }
    }

// card_adds_package_checklist_15
 // card_adds_package_checklist_15


// last_card_removes_package_checklist_15


 // last_card_removes_package_checklist_15


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

    suspend fun upsertQuickReply(reply: QuickReply) {
        if (reply.text.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<QuickReply>>(prefs[quickRepliesKey].orEmpty()) }
                .getOrDefault(emptyList())
            val updated = listOf(reply) + current.filterNot { it.id == reply.id }
            prefs[quickRepliesKey] = json.encodeToString(updated.take(100))
        }
    }

    suspend fun removeQuickReply(replyId: String) {
        context.dataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<QuickReply>>(prefs[quickRepliesKey].orEmpty()) }
                .getOrDefault(emptyList())
            prefs[quickRepliesKey] = json.encodeToString(current.filterNot { it.id == replyId })
        }
    }

    suspend fun replaceImportedRadars(radars: List<ImportedRadar>) {
        context.dataStore.edit { prefs ->
            prefs[importedRadarsKey] = json.encodeToString(radars.take(MAX_IMPORTED_RADARS))
        }
    }

    suspend fun updateImportedRadar(radar: ImportedRadar) {
        context.dataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<ImportedRadar>>(prefs[importedRadarsKey].orEmpty()) }
                .getOrDefault(emptyList())
            prefs[importedRadarsKey] = json.encodeToString(
                current.map { stored -> if (stored.id == radar.id) radar else stored }.take(MAX_IMPORTED_RADARS),
            )
        }
    }

    suspend fun removeImportedRadar(radarId: String) {
        context.dataStore.edit { prefs ->
            val current = runCatching { json.decodeFromString<List<ImportedRadar>>(prefs[importedRadarsKey].orEmpty()) }
                .getOrDefault(emptyList())
            prefs[importedRadarsKey] = json.encodeToString(current.filterNot { it.id == radarId })
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
            savedPlaces = savedPlaces.first(),
            importedRadars = importedRadars.first(),
            quickReplies = quickReplies.first(),
        )
        return json.encodeToString(backup)
    }

    suspend fun restoreBackupJson(content: String): RotaCertaBackup {
        val backup = json.decodeFromString<RotaCertaBackup>(content)
        val currentKeyChecklist11 = settings.first().googleMapsApiKey
        val restoredSettingsChecklist11 = backup.settings.copy(
            googleMapsApiKey = GoogleMapsApiKeyPolicy.valueAfterRestore(
                currentValue = currentKeyChecklist11,
                restoredValue = backup.settings.googleMapsApiKey,
                bundledValue = BuildConfig.GOOGLE_MAPS_API_KEY,
            ),
        )
        saveSettings(restoredSettingsChecklist11) // backup_key_preservation_checklist_11
        context.dataStore.edit { prefs ->
            prefs[history] = json.encodeToString(backup.analyses.take(50))
            prefs[savedPlacesKey] = json.encodeToString(backup.savedPlaces.take(200))
            prefs[importedRadarsKey] = json.encodeToString(backup.importedRadars.take(MAX_IMPORTED_RADARS))
            prefs[quickRepliesKey] = json.encodeToString(backup.quickReplies.take(100))
            prefs.remove(liveDiagnostic)
        }
        return backup.copy(settings = restoredSettingsChecklist11)
    }

    private fun decodeCoordinate(value: String?): Coordinate? =
        runCatching { json.decodeFromString<Coordinate>(value.orEmpty()) }.getOrNull()

    private fun decodeWorkRegionPins(value: String?): List<WorkRegionPin> =
        runCatching { json.decodeFromString<List<WorkRegionPin>>(value.orEmpty()) }
            .getOrDefault(emptyList())
            .take(MAX_WORK_REGION_PINS)
}
