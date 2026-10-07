package br.com.mapeiaia.rotacerta

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.organicmaps.sdk.Router
import app.organicmaps.sdk.bookmarks.data.MapObject
import app.organicmaps.sdk.routing.RoutingController
import app.organicmaps.sdk.util.Distance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * 0.1.749 — Organic Maps becomes the primary ROAD-km engine for FAROL.
 *
 * Contract:
 *  - no network is used here;
 *  - a persistent exact coordinate-pair cache is checked before routing;
 *  - the embedded Organic Maps vehicle router receives a strict total latency budget;
 *  - partial/timeout/missing-map results are returned as null so the caller can fall back to Google;
 *  - only a successfully built vehicle route is eligible to become public road km.
 */
class OrganicMapsOfflineRoadRouter0749(context: Context) {
    data class Batch(
        val distancesKm: List<Double?>,
        val cacheHits: Int,
        val computedRoutes: Int,
        val elapsedMillis: Long,
        val lastFailure: String? = null,
    ) {
        val resolvedCount: Int get() = distancesKm.count { isUsableRoadKm(it) }
        val complete: Boolean get() = distancesKm.isNotEmpty() && distancesKm.all { isUsableRoadKm(it) }
    }

    private data class CacheEntry(val distanceKm: Double, val storedAtMillis: Long)
    private data class Attempt(val distanceKm: Double?, val reason: String? = null)

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()
    private val routeMutex = Mutex()

    fun cachedDrivingDistancesKm(
        origin: Coordinate,
        destinations: List<Coordinate>,
    ): List<Double?>? {
        if (!validCoordinate(origin) || destinations.isEmpty() || destinations.any { !validCoordinate(it) }) return null
        val now = System.currentTimeMillis()
        val values = destinations.map { destination ->
            readCache(cacheKey(origin, destination), now)?.distanceKm
        }
        return values.takeIf { it.all(::isUsableRoadKm) }
    }

    suspend fun drivingDistancesKm(
        origin: Coordinate,
        destinations: List<Coordinate>,
        totalBudgetMillis: Long = TOTAL_OFFLINE_BUDGET_MS,
    ): Batch {
        val started = SystemClock.elapsedRealtime()
        if (!validCoordinate(origin) || destinations.isEmpty()) {
            return Batch(List(destinations.size) { null }, 0, 0, 0L, "invalid_input")
        }

        val result = MutableList<Double?>(destinations.size) { null }
        val now = System.currentTimeMillis()
        var cacheHits = 0
        destinations.forEachIndexed { index, destination ->
            if (!validCoordinate(destination)) return@forEachIndexed
            readCache(cacheKey(origin, destination), now)?.let { cached ->
                result[index] = cached.distanceKm
                cacheHits += 1
            }
        }
        if (result.all(::isUsableRoadKm)) {
            return Batch(
                distancesKm = result,
                cacheHits = cacheHits,
                computedRoutes = 0,
                elapsedMillis = SystemClock.elapsedRealtime() - started,
            )
        }

        if (!OrganicMapsEmbeddedRuntime0711.ensureInitialized(appContext)) {
            return Batch(
                distancesKm = result,
                cacheHits = cacheHits,
                computedRoutes = 0,
                elapsedMillis = SystemClock.elapsedRealtime() - started,
                lastFailure = "runtime_not_ready:${OrganicMapsEmbeddedRuntime0711.failureReason().orEmpty()}",
            )
        }

        var computed = 0
        var lastFailure: String? = null
        routeMutex.withLock {
            for (index in destinations.indices) {
                if (isUsableRoadKm(result[index])) continue
                val elapsed = SystemClock.elapsedRealtime() - started
                val remaining = totalBudgetMillis - elapsed
                if (remaining <= 0L) {
                    lastFailure = "offline_budget_exhausted"
                    break
                }

                val attempt = withTimeoutOrNull(remaining) {
                    buildSingleVehicleRoute(origin, destinations[index])
                } ?: Attempt(null, "offline_route_timeout")

                if (isUsableRoadKm(attempt.distanceKm)) {
                    val value = attempt.distanceKm!!
                    result[index] = value
                    computed += 1
                    writeCache(cacheKey(origin, destinations[index]), value)
                } else {
                    lastFailure = attempt.reason ?: "offline_route_unresolved"
                }
            }
        }

        return Batch(
            distancesKm = result,
            cacheHits = cacheHits,
            computedRoutes = computed,
            elapsedMillis = SystemClock.elapsedRealtime() - started,
            lastFailure = lastFailure,
        )
    }

    private suspend fun buildSingleVehicleRoute(origin: Coordinate, destination: Coordinate): Attempt {
        val straightKm = GeoDistance.kilometers(origin, destination)
        if (straightKm <= SAME_POINT_KM) return Attempt(0.0)

        val answer = CompletableDeferred<Attempt>()
        lateinit var controller: RoutingController
        val container = object : RoutingController.Container {
            override fun showRoutePlan(show: Boolean, completionListener: Runnable?) {
                if (show) completionListener?.run()
            }

            override fun onBuiltRoute() {
                val roadKm = controller.cachedRoutingInfo?.distToTarget?.let(::distanceToKm)
                    ?.takeIf(::isUsableRoadKm)
                    ?.takeIf { plausibleAgainstStraightLine(straightKm, it) }
                // RoutingController invokes onBuiltRoute before onCommonBuildError for NEED_MORE_MAPS.
                // Defer success by one main-loop turn so the missing-map callback can win and force
                // the Google fallback instead of publishing a potentially partial offline route.
                Handler(Looper.getMainLooper()).post {
                    if (!answer.isCompleted) {
                        answer.complete(
                            if (roadKm != null) Attempt(roadKm)
                            else Attempt(null, "built_route_without_plausible_distance"),
                        )
                    }
                }
            }

            override fun onCommonBuildError(lastResultCode: Int, lastMissingMaps: Array<out String>) {
                if (!answer.isCompleted) {
                    val missing = lastMissingMaps.joinToString(",").take(240)
                    answer.complete(Attempt(null, "route_error:$lastResultCode:missing=$missing"))
                }
            }

            override fun onDrivingOptionsBuildError() {
                if (!answer.isCompleted) answer.complete(Attempt(null, "driving_options_error"))
            }
        }

        return try {
            withContext(Dispatchers.Main.immediate) {
                controller = RoutingController.get()
                controller.attach(container)
                val start = MapObject.createMapObject(
                    MapObject.POI,
                    "FAROL origem",
                    "",
                    origin.latitude,
                    origin.longitude,
                )
                val finish = MapObject.createMapObject(
                    MapObject.POI,
                    "FAROL destino",
                    "",
                    destination.latitude,
                    destination.longitude,
                )
                controller.prepare(start, finish, Router.Vehicle)
            }
            answer.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Attempt(null, "route_exception:${error::class.java.simpleName}")
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                runCatching {
                    val active = RoutingController.get()
                    active.detach()
                    active.cancel(false)
                }
            }
        }
    }

    private fun readCache(key: String, nowMillis: Long): CacheEntry? {
        memoryCache[key]?.let { entry ->
            val age = nowMillis - entry.storedAtMillis
            if (age in 0L..CACHE_TTL_MILLIS && isUsableRoadKm(entry.distanceKm)) return entry
            memoryCache.remove(key)
        }

        val raw = prefs.getString(key, null) ?: return null
        val parts = raw.split('|', limit = 2)
        val km = parts.getOrNull(0)?.toDoubleOrNull()
        val timestamp = parts.getOrNull(1)?.toLongOrNull()
        if (!isUsableRoadKm(km) || timestamp == null || nowMillis - timestamp !in 0L..CACHE_TTL_MILLIS) {
            prefs.edit().remove(key).apply()
            return null
        }
        return CacheEntry(km!!, timestamp).also { memoryCache[key] = it }
    }

    private fun writeCache(key: String, distanceKm: Double) {
        if (!isUsableRoadKm(distanceKm)) return
        val entry = CacheEntry(distanceKm, System.currentTimeMillis())
        memoryCache[key] = entry
        prefs.edit().putString(key, "${entry.distanceKm}|${entry.storedAtMillis}").apply()
    }

    companion object {
        const val CONTRACT_MARKER = "FAROL_ORGANIC_OFFLINE_ROAD_PRIMARY_0749"
        const val CACHE_MARKER = "FAROL_ORGANIC_ROAD_CACHE_HIT_0749"
        const val ROUTE_MARKER = "FAROL_ORGANIC_ROAD_CONFIRMED_0749"
        const val GOOGLE_FALLBACK_MARKER = "FAROL_GOOGLE_ROAD_FALLBACK_0749"
        const val TOTAL_OFFLINE_BUDGET_MS = 350L
        const val CACHE_TTL_DAYS = 14L
        const val CACHE_TTL_MILLIS = CACHE_TTL_DAYS * 24L * 60L * 60L * 1000L
        private const val PREFS = "farol_organic_road_cache_0749"
        private const val SAME_POINT_KM = 0.005
        private const val MIN_ROAD_VS_STRAIGHT_FACTOR = 0.90
        private const val MAX_ROAD_VS_STRAIGHT_FACTOR = 20.0
        private const val MAX_ROAD_EXTRA_KM = 25.0

        internal fun distanceToKm(distance: Distance): Double? {
            if (!distance.isValid() || !distance.mDistance.isFinite()) return null
            val km = when (distance.mUnits) {
                Distance.Units.Meters -> distance.mDistance / 1_000.0
                Distance.Units.Kilometers -> distance.mDistance
                Distance.Units.Feet -> distance.mDistance * 0.0003048
                Distance.Units.Miles -> distance.mDistance * 1.609344
            }
            return km.takeIf(::isUsableRoadKm)
        }

        internal fun plausibleAgainstStraightLine(straightKm: Double, roadKm: Double): Boolean {
            if (!straightKm.isFinite() || straightKm < 0.0 || !isUsableRoadKm(roadKm)) return false
            if (straightKm <= SAME_POINT_KM) return roadKm <= 0.2
            val minimum = straightKm * MIN_ROAD_VS_STRAIGHT_FACTOR
            val maximum = max(straightKm * MAX_ROAD_VS_STRAIGHT_FACTOR, straightKm + MAX_ROAD_EXTRA_KM)
            return roadKm + 0.15 >= minimum && roadKm <= maximum
        }

        internal fun isUsableRoadKm(value: Double?): Boolean =
            value != null && value.isFinite() && value >= 0.0

        private fun validCoordinate(value: Coordinate): Boolean =
            value.latitude.isFinite() && value.longitude.isFinite() &&
                value.latitude in -90.0..90.0 && value.longitude in -180.0..180.0

        internal fun cacheKey(origin: Coordinate, destination: Coordinate): String {
            val stable = buildString {
                append(CONTRACT_MARKER)
                append('|')
                append(OrganicMapsEmbeddedRuntime0711.EXACT_SDK_VERSION)
                append('|')
                append(OrganicMapsEmbeddedRuntime0711.COMPATIBLE_DATA_VERSION_FOLDER)
                append('|')
                append(String.format(Locale.US, "%.6f,%.6f", origin.latitude, origin.longitude))
                append("->")
                append(String.format(Locale.US, "%.6f,%.6f", destination.latitude, destination.longitude))
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(stable.toByteArray(Charsets.UTF_8))
            return "r:" + digest.joinToString("") { "%02x".format(it) }.take(40)
        }
    }
}
