package br.com.mapeiaia.rotacerta

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "rota_certa")

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
    private val history = stringPreferencesKey("history")
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
        )
    }

    val analyses: Flow<List<AnalysisResult>> = context.dataStore.data.map { prefs ->
        runCatching { json.decodeFromString<List<AnalysisResult>>(prefs[history].orEmpty()) }
            .getOrDefault(emptyList())
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[homeAddress] = settings.homeAddress
            prefs[alternativeAddress] = settings.alternativeAddress
            prefs[homeRadiusKm] = settings.homeRadiusKm
            prefs[alternativeRadiusKm] = settings.alternativeRadiusKm
            prefs[desiredKeywords] = settings.desiredKeywords
            prefs[avoidedKeywords] = settings.avoidedKeywords
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

    private fun decodeCoordinate(value: String?): Coordinate? =
        runCatching { json.decodeFromString<Coordinate>(value.orEmpty()) }.getOrNull()
}
