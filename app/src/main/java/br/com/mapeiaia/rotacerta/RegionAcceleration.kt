package br.com.mapeiaia.rotacerta

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Perfil leve da regiao preparada pelo usuario.
 *
 * Nao contem mapa, ruas ou conteudo baixado do Google. Guarda somente os pontos
 * definidos pelo usuario, os raios, a validade do preparo e o resultado da
 * verificacao de conectividade da API de rotas.
 */
data class RegionAccelerationProfile(
    val signature: String,
    val preparedAtMillis: Long,
    val expiresAtMillis: Long,
    val homeCoordinate: Coordinate?,
    val alternativeCoordinate: Coordinate?,
    val homeRadiusKm: Double,
    val alternativeRadiusKm: Double,
    val boundaryPointCount: Int,
    val routesApiReady: Boolean,
    val geocodeElapsedMillis: Long,
    val routeProbeElapsedMillis: Long,
)

data class RegionAccelerationResult(
    val success: Boolean,
    val updatedSettings: AppSettings,
    val profile: RegionAccelerationProfile?,
    val message: String,
)

/** Regras puras e testaveis do Acelerador de Regiao. */
object RegionAccelerationPlanner {
    const val PROFILE_TTL_DAYS = 14L
    const val PROFILE_TTL_MILLIS = PROFILE_TTL_DAYS * 24L * 60L * 60L * 1000L
    const val DEFAULT_BOUNDARY_POINTS = 16

    fun signature(settings: AppSettings): String {
        val canonical = listOf(
            settings.homeTargetEnabled.toString(),
            normalize(settings.homeAddress),
            settings.homeCoordinate.coordinatePart(),
            String.format(Locale.US, "%.3f", settings.homeRadiusKm),
            settings.alternativeTargetEnabled.toString(),
            normalize(settings.alternativeAddress),
            settings.alternativeCoordinate.coordinatePart(),
            String.format(Locale.US, "%.3f", settings.alternativeRadiusKm),
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    fun isValid(
        profile: RegionAccelerationProfile?,
        settings: AppSettings,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = profile != null &&
        profile.signature == signature(settings) &&
        nowMillis in profile.preparedAtMillis until profile.expiresAtMillis &&
        (profile.homeCoordinate != null || profile.alternativeCoordinate != null)

    fun boundaryPoints(
        center: Coordinate,
        radiusKm: Double,
        pointCount: Int = DEFAULT_BOUNDARY_POINTS,
    ): List<Coordinate> {
        val safeCount = pointCount.coerceAtLeast(4)
        val safeRadius = radiusKm.coerceAtLeast(0.05)
        return List(safeCount) { index ->
            val bearing = 360.0 * index / safeCount
            pointAt(center, safeRadius, bearing)
        }
    }

    /** Ponto curto usado apenas para verificar e aquecer a API de rotas. */
    fun routeProbePoint(center: Coordinate, configuredRadiusKm: Double): Coordinate {
        val distanceKm = (configuredRadiusKm * 0.08).coerceIn(0.10, 0.40)
        return pointAt(center, distanceKm, 90.0)
    }

    fun pointAt(center: Coordinate, distanceKm: Double, bearingDegrees: Double): Coordinate {
        val angularDistance = distanceKm.coerceAtLeast(0.0) / EARTH_RADIUS_KM
        val bearing = Math.toRadians(bearingDegrees)
        val latitude1 = Math.toRadians(center.latitude)
        val longitude1 = Math.toRadians(center.longitude)

        val latitude2 = asin(
            sin(latitude1) * cos(angularDistance) +
                cos(latitude1) * sin(angularDistance) * cos(bearing),
        )
        val longitude2 = longitude1 + atan2(
            sin(bearing) * sin(angularDistance) * cos(latitude1),
            cos(angularDistance) - sin(latitude1) * sin(latitude2),
        )
        return Coordinate(
            latitude = Math.toDegrees(latitude2),
            longitude = Math.toDegrees(longitude2),
        )
    }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")

    private fun Coordinate?.coordinatePart(): String = this?.let {
        String.format(Locale.US, "%.6f,%.6f", it.latitude, it.longitude)
    }.orEmpty()

    private const val EARTH_RADIUS_KM = 6_371.0
}

private class RegionAccelerationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(profile: RegionAccelerationProfile) {
        preferences.edit()
            .putString(KEY_SIGNATURE, profile.signature)
            .putLong(KEY_PREPARED_AT, profile.preparedAtMillis)
            .putLong(KEY_EXPIRES_AT, profile.expiresAtMillis)
            .putString(KEY_HOME_COORDINATE, profile.homeCoordinate.serialize())
            .putString(KEY_ALTERNATIVE_COORDINATE, profile.alternativeCoordinate.serialize())
            .putLong(KEY_HOME_RADIUS_BITS, profile.homeRadiusKm.toBits())
            .putLong(KEY_ALTERNATIVE_RADIUS_BITS, profile.alternativeRadiusKm.toBits())
            .putInt(KEY_BOUNDARY_COUNT, profile.boundaryPointCount)
            .putBoolean(KEY_ROUTES_READY, profile.routesApiReady)
            .putLong(KEY_GEOCODE_ELAPSED, profile.geocodeElapsedMillis)
            .putLong(KEY_ROUTE_PROBE_ELAPSED, profile.routeProbeElapsedMillis)
            .apply()
    }

    fun read(): RegionAccelerationProfile? {
        val signature = preferences.getString(KEY_SIGNATURE, null)?.takeIf(String::isNotBlank) ?: return null
        return RegionAccelerationProfile(
            signature = signature,
            preparedAtMillis = preferences.getLong(KEY_PREPARED_AT, 0L),
            expiresAtMillis = preferences.getLong(KEY_EXPIRES_AT, 0L),
            homeCoordinate = preferences.getString(KEY_HOME_COORDINATE, null).deserializeCoordinate(),
            alternativeCoordinate = preferences.getString(KEY_ALTERNATIVE_COORDINATE, null).deserializeCoordinate(),
            homeRadiusKm = Double.fromBits(preferences.getLong(KEY_HOME_RADIUS_BITS, 0L)),
            alternativeRadiusKm = Double.fromBits(preferences.getLong(KEY_ALTERNATIVE_RADIUS_BITS, 0L)),
            boundaryPointCount = preferences.getInt(KEY_BOUNDARY_COUNT, 0),
            routesApiReady = preferences.getBoolean(KEY_ROUTES_READY, false),
            geocodeElapsedMillis = preferences.getLong(KEY_GEOCODE_ELAPSED, 0L),
            routeProbeElapsedMillis = preferences.getLong(KEY_ROUTE_PROBE_ELAPSED, 0L),
        )
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun Coordinate?.serialize(): String = this?.let {
        String.format(Locale.US, "%.8f,%.8f", it.latitude, it.longitude)
    }.orEmpty()

    private fun String?.deserializeCoordinate(): Coordinate? {
        if (this.isNullOrBlank()) return null
        val parts = split(',', limit = 2)
        if (parts.size != 2) return null
        val latitude = parts[0].toDoubleOrNull() ?: return null
        val longitude = parts[1].toDoubleOrNull() ?: return null
        return Coordinate(latitude, longitude)
    }

    private companion object {
        const val PREFS_NAME = "region_acceleration_v1"
        const val KEY_SIGNATURE = "signature"
        const val KEY_PREPARED_AT = "prepared_at"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_HOME_COORDINATE = "home_coordinate"
        const val KEY_ALTERNATIVE_COORDINATE = "alternative_coordinate"
        const val KEY_HOME_RADIUS_BITS = "home_radius_bits"
        const val KEY_ALTERNATIVE_RADIUS_BITS = "alternative_radius_bits"
        const val KEY_BOUNDARY_COUNT = "boundary_count"
        const val KEY_ROUTES_READY = "routes_ready"
        const val KEY_GEOCODE_ELAPSED = "geocode_elapsed"
        const val KEY_ROUTE_PROBE_ELAPSED = "route_probe_elapsed"
    }
}

/**
 * Prepara os alvos configurados sem baixar um mapa pesado.
 *
 * O ganho principal e retirar a geocodificacao de Casa/Alfinete do caminho critico
 * do card, persistindo as coordenadas no AppSettings. Uma pequena rota de teste
 * valida a chave e aquece a conexao da API, mas seu resultado nao vira banco de mapa.
 */
class RegionAccelerationManager(
    context: Context,
    private val mapsService: GoogleMapsService = GoogleMapsService(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val store = RegionAccelerationStore(context.applicationContext)

    fun statusText(settings: AppSettings): String {
        val profile = store.read() ?: return "Regiao ainda nao preparada."
        if (profile.signature != RegionAccelerationPlanner.signature(settings)) {
            return "Endereco ou raio mudou. Prepare a regiao novamente."
        }
        val now = nowMillis()
        if (now !in profile.preparedAtMillis until profile.expiresAtMillis) {
            return "O preparo regional expirou. Prepare novamente para renovar o cache leve."
        }
        val routeStatus = if (profile.routesApiReady) {
            "Conexao de rotas verificada em ${profile.routeProbeElapsedMillis} ms."
        } else {
            "Perfil local pronto; a conexao de rotas nao foi validada."
        }
        return "Regiao preparada por ${RegionAccelerationPlanner.PROFILE_TTL_DAYS} dias. " +
            "${profile.boundaryPointCount} pontos de limite local. $routeStatus"
    }

    fun clear() {
        store.clear()
    }

    suspend fun prepare(
        settings: AppSettings,
        region: DeviceRegion = DeviceRegion(country = "Brasil"),
    ): RegionAccelerationResult = withContext(Dispatchers.IO) {
        val startedAt = nowMillis()
        val apiKey = settings.googleMapsApiKey.ifBlank { BuildConfig.GOOGLE_MAPS_API_KEY }

        var homeCoordinate = settings.homeCoordinate
        var alternativeCoordinate = settings.alternativeCoordinate
        val geocodeStartedAt = nowMillis()

        if (settings.homeTargetEnabled && homeCoordinate == null && settings.homeAddress.isNotBlank()) {
            if (apiKey.isBlank()) {
                return@withContext RegionAccelerationResult(
                    success = false,
                    updatedSettings = settings,
                    profile = null,
                    message = "Informe uma chave do Google Maps para preparar o endereco digitado.",
                )
            }
            homeCoordinate = mapsService.geocode(settings.homeAddress, region, apiKey)
        }
        if (settings.alternativeTargetEnabled && alternativeCoordinate == null && settings.alternativeAddress.isNotBlank()) {
            if (apiKey.isBlank()) {
                return@withContext RegionAccelerationResult(
                    success = false,
                    updatedSettings = settings,
                    profile = null,
                    message = "Informe uma chave do Google Maps para preparar o Alfinete digitado.",
                )
            }
            alternativeCoordinate = mapsService.geocode(settings.alternativeAddress, region, apiKey)
        }
        val geocodeElapsed = nowMillis() - geocodeStartedAt

        val missingHome = settings.homeTargetEnabled && homeCoordinate == null
        val missingAlternative = settings.alternativeTargetEnabled && alternativeCoordinate == null
        if (missingHome && missingAlternative) {
            return@withContext RegionAccelerationResult(
                success = false,
                updatedSettings = settings,
                profile = null,
                message = "Defina Casa ou Alfinete com endereco valido antes de preparar a regiao.",
            )
        }

        val updatedSettings = settings.copy(
            homeCoordinate = homeCoordinate,
            alternativeCoordinate = alternativeCoordinate,
        )

        val primaryCoordinate = homeCoordinate ?: alternativeCoordinate!!
        val primaryRadius = if (homeCoordinate != null) settings.homeRadiusKm else settings.alternativeRadiusKm
        val boundaryCount = RegionAccelerationPlanner.boundaryPoints(
            center = primaryCoordinate,
            radiusKm = primaryRadius,
        ).size

        var routesReady = false
        var routeElapsed = 0L
        if (apiKey.isNotBlank()) {
            val probeStartedAt = nowMillis()
            val probePoint = RegionAccelerationPlanner.routeProbePoint(primaryCoordinate, primaryRadius)
            routesReady = mapsService.drivingDistanceKm(primaryCoordinate, probePoint, apiKey) != null
            routeElapsed = nowMillis() - probeStartedAt
        }

        val preparedAt = nowMillis()
        val profile = RegionAccelerationProfile(
            signature = RegionAccelerationPlanner.signature(updatedSettings),
            preparedAtMillis = preparedAt,
            expiresAtMillis = preparedAt + RegionAccelerationPlanner.PROFILE_TTL_MILLIS,
            homeCoordinate = homeCoordinate,
            alternativeCoordinate = alternativeCoordinate,
            homeRadiusKm = settings.homeRadiusKm,
            alternativeRadiusKm = settings.alternativeRadiusKm,
            boundaryPointCount = boundaryCount,
            routesApiReady = routesReady,
            geocodeElapsedMillis = geocodeElapsed,
            routeProbeElapsedMillis = routeElapsed,
        )
        store.save(profile)

        val targets = listOfNotNull(
            homeCoordinate?.let { "Casa" },
            alternativeCoordinate?.let { "Alfinete" },
        ).joinToString(" e ")
        val routeMessage = if (routesReady) {
            "A conexao de rotas respondeu em ${routeElapsed} ms."
        } else {
            "O perfil local foi criado, mas a API de rotas nao respondeu ao teste."
        }
        RegionAccelerationResult(
            success = true,
            updatedSettings = updatedSettings,
            profile = profile,
            message = "$targets preparados em ${nowMillis() - startedAt} ms. $routeMessage",
        )
    }
}
