package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import br.com.mapeiaia.rotacerta.Coordinate
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TripReferenceOrigin(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val capturedAtMillis: Long,
    val radiusKm: Double = DEFAULT_RADIUS_KM,
) {
    val coordinate: Coordinate
        get() = Coordinate(latitude, longitude)

    fun isValid(): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            radiusKm.isFinite() && radiusKm in MIN_RADIUS_KM..MAX_RADIUS_KM

    companion object {
        const val DEFAULT_RADIUS_KM = 10.0
        const val MIN_RADIUS_KM = 0.2
        const val MAX_RADIUS_KM = 100.0
    }
}

class TripReferenceOriginStore(context: Context) {
    private val appContext = context.applicationContext
    private val tenantScope = RotaCertaTenantRegistry(appContext).activeScope()
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val valueKey = tenantScope.key(KEY_VALUE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(): TripReferenceOrigin? = prefs.getString(valueKey, null)
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching { json.decodeFromString<TripReferenceOrigin>(it) }.getOrNull() }
        ?.takeIf(TripReferenceOrigin::isValid)

    fun save(origin: TripReferenceOrigin): TripReferenceOrigin {
        require(origin.isValid()) { "Origem de referência inválida." }
        prefs.edit().putString(valueKey, json.encodeToString(origin)).apply()
        return origin
    }

    fun clear() {
        prefs.edit().remove(valueKey).apply()
    }

    companion object {
        private const val PREFS = "rota_certa_trip_reference_origin_v1"
        private const val KEY_VALUE = "trip_reference_origin"
    }
}
