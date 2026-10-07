package br.com.mapeiaia.rotacerta

import android.content.Context
import android.os.SystemClock
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class GoogleMapsService(context: Context? = null) {
    private val json = Json { ignoreUnknownKeys = true }
    private val geocodeCache = ConcurrentHashMap<String, Coordinate>()
    private val routeCache = ConcurrentHashMap<String, Double>()
    private val addressRouteCache = ConcurrentHashMap<String, Double>()
    private val trafficAddressRouteCache = ConcurrentHashMap<String, Double>()
    private val cachePrefs: SharedPreferences? = context
        ?.applicationContext
        ?.getSharedPreferences(PERSISTENT_CACHE_PREFS, Context.MODE_PRIVATE)
    private val platformGeocodingService0547: GeocodingService? = context
        ?.applicationContext
        ?.let(::GeocodingService)
    private val offlineAddressAtlas642: OfflineAddressAtlas642? = context
        ?.applicationContext
        ?.let(::OfflineAddressAtlas642)
    private val organicMapsOfflineResolver0711: OrganicMapsOfflineAddressResolver0711? = context
        ?.applicationContext
        ?.let(::OrganicMapsOfflineAddressResolver0711)
    private val organicMapsOfflineRoadRouter0749: OrganicMapsOfflineRoadRouter0749? = context
        ?.applicationContext
        ?.let(::OrganicMapsOfflineRoadRouter0749)
    private var writesSincePrune = 0

    suspend fun geocode(query: String, region: DeviceRegion, apiKey: String): Coordinate? = withContext(Dispatchers.IO) {
        if (query.isBlank() || apiKey.isBlank()) return@withContext null

        geocodeQueries(query, region).forEach { scopedQuery ->
            val cacheKey = scopedQuery.lowercase(Locale.ROOT)
            geocodeCache[cacheKey]?.let { return@withContext it }
            readPersistentCoordinate(cacheKey)?.let { coordinate ->
                geocodeCache[cacheKey] = coordinate
                return@withContext coordinate
            }

            val coordinate = requestWithRetry(GEOCODE_REQUEST_ATTEMPTS) { requestGeocode(scopedQuery, apiKey) }
            if (coordinate != null) {
                geocodeCache[cacheKey] = coordinate
                persistCoordinate(cacheKey, coordinate)
                return@withContext coordinate
            }
        }

        null
    }

    /**
     * FAROL Edge 0.1.634: coordinate cache is the critical-path authority.
     * Historical road-route caches are deliberately not used for geographic-radius decisions.
     */
    fun cachedFarolCoordinate(originAddress: String): Coordinate? {
        if (originAddress.isBlank()) return null
        val normalized = normalizeAddress(originAddress)
        offlineAddressAtlas642?.lookup(originAddress)?.let { coordinate ->
            geocodeCache["osm_origin|${normalized}"] = coordinate
            FarolFlightRecorder0163.record(
                stage = "OFFLINE_ATLAS_HIT_0642",
                packageName = null,
                details = "source=learned_local_atlas; network=false",
            )
            return coordinate
        }
        val keys = buildList {
            add("osm_origin|${normalized}")
            geocodeQueries0547(originAddress).forEach { add(it.lowercase(Locale.ROOT)) }
        }.distinct()
        keys.forEach { key ->
            geocodeCache[key]?.let { return it }
            readPersistentCoordinate(key)?.let { coordinate ->
                geocodeCache[key] = coordinate
                return coordinate
            }
        }
        return null
    }

    suspend fun resolveFarolCoordinate(
        originAddress: String,
        targetHints: List<Coordinate>,
        apiKey: String,
    ): Coordinate? = withContext(Dispatchers.IO) {
        cachedFarolCoordinate(originAddress)?.let { return@withContext it }
        resolveFreePrimaryOrigin0547(originAddress, targetHints)?.let { return@withContext it }
        if (apiKey.isBlank()) return@withContext null
        geocode(originAddress, DeviceRegion(), apiKey)
    }

    /**
     * Stage640 critical path: the first Green/Red must not wait for the public Nominatim round-trip.
     *
     * Cache remains the first authority. On a cold address, Android's platform geocoder is tried
     * before the existing free/network chain only for the Farol's local-radius decision. The old
     * 0547 resolver remains intact as the fallback and continues to own general routing behavior.
     * Exact road km is still resolved later by the traffic-aware route phase.
     */
    suspend fun resolveFarolCoordinateInstant640(
        originAddress: String,
        targetHints: List<Coordinate>,
        apiKey: String,
    ): Coordinate? = withContext(Dispatchers.IO) {
        cachedFarolCoordinate(originAddress)?.let { return@withContext it }
        resolvePlatformFirstOrigin640(originAddress)?.let { return@withContext it }
        resolveFreePrimaryOrigin0547(originAddress, targetHints)?.let { return@withContext it }
        if (apiKey.isBlank()) return@withContext null
        geocode(originAddress, DeviceRegion(), apiKey)
    }


    /**
     * Stage642 offline-first resolver. The learned local atlas is consulted before every provider.
     * Any successful platform/OSM/Google result is immediately promoted into the local atlas so the
     * same destination can be decided later with zero network, including without mobile data.
     */
    suspend fun resolveFarolCoordinateInstant642(
        originAddress: String,
        targetHints: List<Coordinate>,
        apiKey: String,
    ): Coordinate? = withContext(Dispatchers.IO) {
        cachedFarolCoordinate(originAddress)?.let { return@withContext it }

        organicMapsOfflineResolver0711?.resolve(originAddress)?.let { coordinate ->
            learnOfflineAtlas642(originAddress, coordinate)
            FarolFlightRecorder0163.record(
                stage = "FAROL_ORGANIC_OFFLINE_RESOLVED_0711",
                packageName = null,
                details = "provider=organicmaps_embedded; network=false",
            )
            return@withContext coordinate
        }

        resolvePlatformFirstOrigin640(originAddress)?.let { coordinate ->
            learnOfflineAtlas642(originAddress, coordinate)
            return@withContext coordinate
        }
        resolveFreePrimaryOrigin0547(originAddress, targetHints)?.let { coordinate ->
            learnOfflineAtlas642(originAddress, coordinate)
            return@withContext coordinate
        }
        if (apiKey.isBlank()) return@withContext null
        geocode(originAddress, DeviceRegion(), apiKey)?.also { coordinate ->
            learnOfflineAtlas642(originAddress, coordinate)
        }
    }

    suspend fun resolveFarolCoordinateResilient0697(
        originAddress: String,
        targetHints: List<Coordinate>,
        apiKey: String,
        requestContext0699: FarolNetworkFailureIsolation0699.RequestContext? = null,
    ): Coordinate? {
        if (originAddress.isBlank()) return null
        cachedFarolCoordinate(originAddress)?.let { coordinate ->
            FarolFlightRecorder0163.record(
                stage = FarolCoordinateResolution0697.RESOLVED_MARKER,
                packageName = null,
                details = "provider=cache; elapsed_ms=0; captureFabric0751=true",
            )
            return coordinate
        }

        val started0751 = SystemClock.elapsedRealtime()
        FarolFlightRecorder0163.record(
            stage = FarolCoordinateResolution0697.STARTED_MARKER,
            packageName = null,
            details = "offlineBudgetMs=${FarolCaptureFabric0751.OFFLINE_GEOCODE_BUDGET_MS}; hedgeDeadlineMs=${FarolCaptureFabric0751.HEDGED_GEOCODE_DEADLINE_MS}; offlineFirst=organicmaps0711; nominatimHotPath=false",
        )

        val organic0751 = withTimeoutOrNull(FarolCaptureFabric0751.OFFLINE_GEOCODE_BUDGET_MS) {
            organicMapsOfflineResolver0711?.resolve(originAddress)
        }
        if (organic0751 != null) {
            learnOfflineAtlas642(originAddress, organic0751)
            FarolFlightRecorder0163.record(
                stage = "FAROL_ORGANIC_OFFLINE_RESOLVED_0711",
                packageName = null,
                details = "provider=organicmaps_embedded; network=false; elapsed_ms=${SystemClock.elapsedRealtime() - started0751}; budget0751=true",
            )
            return organic0751
        }

        FarolFlightRecorder0163.record(
            stage = "FAROL_ORGANIC_OFFLINE_MISS_0711",
            packageName = null,
            details = "fallback=hedged_resolvers_0751; elapsed_ms=${SystemClock.elapsedRealtime() - started0751}; sdkFailure=${OrganicMapsEmbeddedRuntime0711.failureReason().orEmpty()}",
        )
        FarolFlightRecorder0163.record(
            stage = FarolCaptureFabric0751.GEOCODE_HEDGE_STARTED_MARKER,
            packageName = null,
            details = "android=true; google=${apiKey.isNotBlank()}; osmOnlyWithoutGoogle=${apiKey.isBlank()}; deadlineMs=${FarolCaptureFabric0751.HEDGED_GEOCODE_DEADLINE_MS}",
        )

        val hedged0751 = coroutineScope {
            val providers0751 = mutableListOf<Pair<String, kotlinx.coroutines.Deferred<Coordinate?>>>()
            providers0751 += "android" to async(Dispatchers.IO) {
                resolvePlatformOrigin0697(originAddress)
            }
            if (apiKey.isNotBlank()) {
                providers0751 += "google" to async(Dispatchers.IO) {
                    resolveGoogleOrigin0697(originAddress, targetHints, apiKey, requestContext0699)
                }
            } else {
                providers0751 += "osm" to async(Dispatchers.IO) {
                    resolveFreeOrigin0697(originAddress, targetHints)
                }
            }

            val pending0751 = providers0751.toMutableList()
            val winner0751 = withTimeoutOrNull(FarolCaptureFabric0751.HEDGED_GEOCODE_DEADLINE_MS) {
                var accepted0751: Pair<String, Coordinate>? = null
                while (pending0751.isNotEmpty() && accepted0751 == null) {
                    val completed0751 = select<Pair<String, Coordinate?>> {
                        pending0751.forEach { (provider0751, deferred0751) ->
                            deferred0751.onAwait { coordinate0751 -> provider0751 to coordinate0751 }
                        }
                    }
                    pending0751.removeAll { it.first == completed0751.first }
                    if (completed0751.second != null) {
                        accepted0751 = completed0751.first to completed0751.second!!
                    }
                }
                accepted0751
            }
            providers0751.forEach { (_, deferred0751) ->
                if (deferred0751.isActive) deferred0751.cancel()
            }
            winner0751
        }

        if (hedged0751 != null) {
            val (provider0751, coordinate0751) = hedged0751
            if (provider0751 != "google") {
                learnOfflineAtlas642(originAddress, coordinate0751)
            }
            FarolFlightRecorder0163.record(
                stage = FarolCaptureFabric0751.GEOCODE_HEDGE_WON_MARKER,
                packageName = null,
                details = "provider=$provider0751; elapsed_ms=${SystemClock.elapsedRealtime() - started0751}; googleStarted=${apiKey.isNotBlank()}",
            )
            FarolFlightRecorder0163.record(
                stage = FarolCoordinateResolution0697.RESOLVED_MARKER,
                packageName = null,
                details = "provider=$provider0751; elapsed_ms=${SystemClock.elapsedRealtime() - started0751}; hedged0751=true",
            )
            return coordinate0751
        }

        FarolFlightRecorder0163.record(
            stage = FarolCaptureFabric0751.GEOCODE_HEDGE_FAILED_MARKER,
            packageName = null,
            details = "elapsed_ms=${SystemClock.elapsedRealtime() - started0751}; deadline_ms=${FarolCaptureFabric0751.HEDGED_GEOCODE_DEADLINE_MS}",
        )
        FarolFlightRecorder0163.record(
            stage = FarolCoordinateResolution0697.ALL_FAILED_MARKER,
            packageName = null,
            details = "elapsed_ms=${SystemClock.elapsedRealtime() - started0751}; deadline_ms=${FarolCaptureFabric0751.OFFLINE_GEOCODE_BUDGET_MS + FarolCaptureFabric0751.HEDGED_GEOCODE_DEADLINE_MS}; captureFabric0751=true",
        )
        return null
    }

    suspend fun drivingDistanceKm(origin: Coordinate, destination: Coordinate, apiKey: String): Double? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext null
            val cacheKey = coordinateRouteKey(origin, destination)
            routeCache[cacheKey]?.let { return@withContext it }
            readPersistentDistance(PERSISTENT_COORD_ROUTE_PREFIX, cacheKey, ROUTE_CACHE_TTL_MS)?.let { distance ->
                routeCache[cacheKey] = distance
                return@withContext distance
            }

            val body = coordinateRouteBody(origin, destination)
            val distanceKm = requestWithRetry(ROUTE_REQUEST_ATTEMPTS) { requestDrivingDistance(body, apiKey) }
            if (distanceKm != null) {
                routeCache[cacheKey] = distanceKm
                persistDistance(PERSISTENT_COORD_ROUTE_PREFIX, cacheKey, distanceKm)
            }
            distanceKm
        }

    /**
     * Caminho rapido 0.1.128: o Routes API aceita o endereco legivel diretamente
     * como origem. Assim, a primeira decisao deixa de esperar uma chamada separada
     * de geocodificacao antes de iniciar a rota.
     */
    suspend fun drivingDistancesFromAddressKm(
        originAddress: String,
        destinations: List<Coordinate>,
        apiKey: String,
    ): List<Double?> = withContext(Dispatchers.IO) {
        if (originAddress.isBlank() || destinations.isEmpty()) {
            return@withContext List(destinations.size) { null }
        }

        val routeStartedElapsedNanos0163 = SystemClock.elapsedRealtimeNanos()
        FarolFlightRecorder0163.record(
            stage = "MAPS_ROUTE_MATRIX_ENTER",
            packageName = null,
            details = "origin=$originAddress; destinations=${destinations.size}; apiKeyPresent=${apiKey.isNotBlank()}",
            elapsedRealtimeNanos = routeStartedElapsedNanos0163,
        )
        val normalizedOrigin = normalizeAddress(originAddress)
        val result = MutableList<Double?>(destinations.size) { null }
        val missingIndexes = mutableListOf<Int>()

        destinations.forEachIndexed { index, destination ->
            val cacheKey = addressRouteKey(normalizedOrigin, destination)
            val cached = addressRouteCache[cacheKey]
                ?: readPersistentDistance(PERSISTENT_ADDRESS_ROUTE_PREFIX, cacheKey, ROUTE_CACHE_TTL_MS)
            if (cached != null) {
                addressRouteCache[cacheKey] = cached
                result[index] = cached
            } else {
                missingIndexes += index
            }
        }

        FarolFlightRecorder0163.record(
            stage = "MAPS_ROUTE_CACHE_EVALUATED",
            packageName = null,
            details = "origin=$originAddress; hits=${destinations.size - missingIndexes.size}; misses=${missingIndexes.size}; destinations=${destinations.size}",
        )
        if (missingIndexes.isEmpty()) {
            FarolFlightRecorder0163.record(
                stage = "MAPS_ROUTE_MATRIX_COMPLETE",
                packageName = null,
                details = "path=cache_only; distances=$result; elapsed_us=${(SystemClock.elapsedRealtimeNanos() - routeStartedElapsedNanos0163).coerceAtLeast(0L) / 1_000L}",
            )
            return@withContext result
        }

        val missingDestinations = missingIndexes.map(destinations::get)

        // 0.1.546: free road-routing is the primary authority for card distance.
        // Google remains a secondary contingency only for entries the free provider
        // could not resolve. The decision engine still receives road distance in km;
        // no green/red threshold or rendering rule is changed here.
        val osmFetched = requestOpenStreetMapAddressRoutes(
            originAddress = originAddress,
            destinations = missingDestinations,
        )
        val unresolvedIndexes = missingDestinations.indices
            .filter { index -> osmFetched?.getOrNull(index) == null }
        val googleFallbackByMissingIndex = mutableMapOf<Int, Double?>()
        if (unresolvedIndexes.isNotEmpty() && apiKey.isNotBlank()) {
            val googleDestinations = unresolvedIndexes.map(missingDestinations::get)
            val body = addressRouteMatrixBody(originAddress, googleDestinations)
            val matrixFetched = requestWithRetry(ROUTE_REQUEST_ATTEMPTS) {
                requestAddressRouteMatrix(body, apiKey, googleDestinations.size)
            }
            val googleFetched = matrixFetched ?: requestAddressRouteFallback(
                originAddress = originAddress,
                destinations = googleDestinations,
                apiKey = apiKey,
            )
            unresolvedIndexes.forEachIndexed { googleIndex, originalMissingIndex ->
                googleFallbackByMissingIndex[originalMissingIndex] = googleFetched?.getOrNull(googleIndex)
            }
        }
        val fetched = missingDestinations.indices.map { index ->
            osmFetched?.getOrNull(index) ?: googleFallbackByMissingIndex[index]
        }
        FarolFlightRecorder0163.record(
            stage = "ROUTE_PROVIDER_SELECTION_0546",
            packageName = null,
            details = "osmResolved=${osmFetched?.count { it != null } ?: 0}; googleFallbackRequested=${unresolvedIndexes.size}; googleKeyPresent=${apiKey.isNotBlank()}; finalResolved=${fetched.count { it != null }}; destinations=${missingDestinations.size}",
        )

        FarolFlightRecorder0163.record(
            stage = "MAPS_ROUTE_NETWORK_RESULT",
            packageName = null,
            details = "requested=${missingDestinations.size}; returned=${fetched?.size ?: 0}; values=$fetched; elapsed_us=${(SystemClock.elapsedRealtimeNanos() - routeStartedElapsedNanos0163).coerceAtLeast(0L) / 1_000L}",
        )
        fetched?.forEachIndexed { fetchedIndex, distanceKm ->
            if (distanceKm == null) return@forEachIndexed
            val originalIndex = missingIndexes[fetchedIndex]
            val destination = destinations[originalIndex]
            val cacheKey = addressRouteKey(normalizedOrigin, destination)
            result[originalIndex] = distanceKm
            addressRouteCache[cacheKey] = distanceKm
            persistDistance(PERSISTENT_ADDRESS_ROUTE_PREFIX, cacheKey, distanceKm)
        }

        FarolFlightRecorder0163.record(
            stage = "MAPS_ROUTE_MATRIX_COMPLETE",
            packageName = null,
            details = "path=cache_and_network; distances=$result; elapsed_us=${(SystemClock.elapsedRealtimeNanos() - routeStartedElapsedNanos0163).coerceAtLeast(0L) / 1_000L}",
        )
        result
    } // direct_address_route_matrix_0_1_128

    fun cachedDrivingDistancesFromAddressKm(
        originAddress: String,
        destinations: List<Coordinate>,
    ): List<Double?>? {
        if (originAddress.isBlank() || destinations.isEmpty()) return null
        val normalizedOrigin = normalizeAddress(originAddress)
        val result = MutableList<Double?>(destinations.size) { null }
        destinations.forEachIndexed { index, destination ->
            val cacheKey = addressRouteKey(normalizedOrigin, destination)
            val cached = addressRouteCache[cacheKey]
                ?: readPersistentDistance(PERSISTENT_ADDRESS_ROUTE_PREFIX, cacheKey, ROUTE_CACHE_TTL_MS)
                ?: return null
            addressRouteCache[cacheKey] = cached
            result[index] = cached
        }
        return result
    } // simple_cached_route_peek_checklist_13


    /**
     * Stage637: exact road distance authority after the local instant color.
     * Google Route Matrix is attempted first with TRAFFIC_AWARE. OSM/OSRM is only
     * a contingency when Google is unavailable; neither provider blocks bubble input.
     */
    /**
     * 0.1.749 instant road-cache authority: embedded Organic Maps first, then the existing
     * persistent Google/OSM exact-road cache. No network is started from this method.
     */
    fun cachedOfflineFirstDrivingDistancesFromAddressKm0749(
        originAddress: String,
        destinations: List<Coordinate>,
    ): List<Double?>? {
        if (originAddress.isBlank() || destinations.isEmpty()) return null
        val origin0749 = cachedFarolCoordinate(originAddress)
        if (origin0749 != null) {
            organicMapsOfflineRoadRouter0749
                ?.cachedDrivingDistancesKm(origin0749, destinations)
                ?.let { values0749 ->
                    FarolFlightRecorder0163.record(
                        stage = OrganicMapsOfflineRoadRouter0749.CACHE_MARKER,
                        packageName = null,
                        details = "resolved=${values0749.count { it != null }}; destinations=${destinations.size}; network=false",
                    )
                    return values0749
                }
        }
        return cachedTrafficAwareDrivingDistancesFromAddressKm(originAddress, destinations)
    }

    /**
     * 0.1.749 exact-road pipeline:
     *   1) persistent Organic Maps route cache;
     *   2) embedded Organic Maps vehicle route with a 350 ms total budget;
     *   3) existing Google Route Matrix path only for unresolved targets.
     *
     * Haversine never enters this result. Any value returned here is an exact road-route result.
     */
    suspend fun offlineFirstDrivingDistancesFromAddressKm0749(
        originAddress: String,
        destinations: List<Coordinate>,
        apiKey: String,
    ): List<Double?> = withContext(Dispatchers.IO) {
        if (originAddress.isBlank() || destinations.isEmpty()) {
            return@withContext List(destinations.size) { null }
        }

        val started0749 = SystemClock.elapsedRealtime()
        val result0749 = MutableList<Double?>(destinations.size) { null }
        val origin0749 = cachedFarolCoordinate(originAddress)
        val offline0749 = if (origin0749 != null) {
            organicMapsOfflineRoadRouter0749?.drivingDistancesKm(
                origin = origin0749,
                destinations = destinations,
                totalBudgetMillis = OrganicMapsOfflineRoadRouter0749.TOTAL_OFFLINE_BUDGET_MS,
            )
        } else {
            null
        }

        offline0749?.distancesKm?.forEachIndexed { index0749, distance0749 ->
            if (OrganicMapsOfflineRoadRouter0749.isUsableRoadKm(distance0749)) {
                result0749[index0749] = distance0749
            }
        }
        if (offline0749 != null) {
            if (offline0749.cacheHits > 0) {
                FarolFlightRecorder0163.record(
                    stage = OrganicMapsOfflineRoadRouter0749.CACHE_MARKER,
                    packageName = null,
                    details = "cacheHits=${offline0749.cacheHits}; computed=${offline0749.computedRoutes}; resolved=${offline0749.resolvedCount}; elapsedMs=${offline0749.elapsedMillis}; network=false",
                )
            }
            if (offline0749.computedRoutes > 0) {
                FarolFlightRecorder0163.record(
                    stage = OrganicMapsOfflineRoadRouter0749.ROUTE_MARKER,
                    packageName = null,
                    details = "computed=${offline0749.computedRoutes}; resolved=${offline0749.resolvedCount}; elapsedMs=${offline0749.elapsedMillis}; network=false",
                )
            }
        }

        val unresolved0749 = result0749.indices.filter { result0749[it] == null }
        if (unresolved0749.isEmpty()) {
            FarolFlightRecorder0163.record(
                stage = OrganicMapsOfflineRoadRouter0749.CONTRACT_MARKER,
                packageName = null,
                details = "offlineComplete=true; destinations=${destinations.size}; elapsedMs=${SystemClock.elapsedRealtime() - started0749}; googleCalled=false",
            )
            return@withContext result0749
        }

        FarolFlightRecorder0163.record(
            stage = OrganicMapsOfflineRoadRouter0749.GOOGLE_FALLBACK_MARKER,
            packageName = null,
            details = "offlineResolved=${result0749.count { it != null }}; unresolved=${unresolved0749.size}; reason=${offline0749?.lastFailure ?: if (origin0749 == null) "origin_not_cached" else "offline_router_unavailable"}; budgetMs=${OrganicMapsOfflineRoadRouter0749.TOTAL_OFFLINE_BUDGET_MS}",
        )
        val fallback0749 = trafficAwareDrivingDistancesFromAddressKm(
            originAddress = originAddress,
            destinations = unresolved0749.map(destinations::get),
            apiKey = apiKey,
        )
        unresolved0749.forEachIndexed { localIndex0749, originalIndex0749 ->
            val exact0749 = fallback0749.getOrNull(localIndex0749)
            if (OrganicMapsOfflineRoadRouter0749.isUsableRoadKm(exact0749)) {
                result0749[originalIndex0749] = exact0749
            }
        }
        FarolFlightRecorder0163.record(
            stage = OrganicMapsOfflineRoadRouter0749.CONTRACT_MARKER,
            packageName = null,
            details = "offlineResolved=${offline0749?.resolvedCount ?: 0}; finalResolved=${result0749.count { it != null }}; destinations=${destinations.size}; elapsedMs=${SystemClock.elapsedRealtime() - started0749}; googleFallback=true",
        )
        result0749
    }

    fun cachedTrafficAwareDrivingDistancesFromAddressKm(
        originAddress: String,
        destinations: List<Coordinate>,
    ): List<Double?>? {
        if (originAddress.isBlank() || destinations.isEmpty()) return null
        val normalizedOrigin = normalizeAddress(originAddress)
        val result = MutableList<Double?>(destinations.size) { null }
        destinations.forEachIndexed { index, destination ->
            val cacheKey = addressRouteKey(normalizedOrigin, destination)
            val cached = trafficAddressRouteCache[cacheKey]
                ?: readPersistentDistance(PERSISTENT_TRAFFIC_ADDRESS_ROUTE_PREFIX, cacheKey, ROUTE_CACHE_TTL_MS)
                ?: return null
            trafficAddressRouteCache[cacheKey] = cached
            result[index] = cached
        }
        return result
    }

    suspend fun trafficAwareDrivingDistancesFromAddressKm(
        originAddress: String,
        destinations: List<Coordinate>,
        apiKey: String,
    ): List<Double?> = withContext(Dispatchers.IO) {
        if (originAddress.isBlank() || destinations.isEmpty()) {
            return@withContext List(destinations.size) { null }
        }
        val normalizedOrigin = normalizeAddress(originAddress)
        val result = MutableList<Double?>(destinations.size) { null }
        val missingIndexes = mutableListOf<Int>()
        destinations.forEachIndexed { index, destination ->
            val cacheKey = addressRouteKey(normalizedOrigin, destination)
            val cached = trafficAddressRouteCache[cacheKey]
                ?: readPersistentDistance(PERSISTENT_TRAFFIC_ADDRESS_ROUTE_PREFIX, cacheKey, ROUTE_CACHE_TTL_MS)
            if (cached != null) {
                trafficAddressRouteCache[cacheKey] = cached
                result[index] = cached
            } else {
                missingIndexes += index
            }
        }
        if (missingIndexes.isEmpty()) return@withContext result

        val missingDestinations = missingIndexes.map(destinations::get)
        val googleFetched = if (apiKey.isNotBlank()) {
            val body = trafficAwareAddressRouteMatrixBody(originAddress, missingDestinations)
            requestWithRetry(ROUTE_REQUEST_ATTEMPTS) {
                requestAddressRouteMatrix(body, apiKey, missingDestinations.size)
            }
        } else null

        val unresolved = missingDestinations.indices.filter { googleFetched?.getOrNull(it) == null }
        val fallback = if (unresolved.isNotEmpty()) {
            requestOpenStreetMapAddressRoutes(originAddress, unresolved.map(missingDestinations::get))
        } else null
        var fallbackIndex = 0
        missingDestinations.indices.forEach { localIndex ->
            val exact = googleFetched?.getOrNull(localIndex)
                ?: if (localIndex in unresolved) fallback?.getOrNull(fallbackIndex++) else null
            if (exact != null) {
                val originalIndex = missingIndexes[localIndex]
                val key = addressRouteKey(normalizedOrigin, destinations[originalIndex])
                result[originalIndex] = exact
                trafficAddressRouteCache[key] = exact
                persistDistance(PERSISTENT_TRAFFIC_ADDRESS_ROUTE_PREFIX, key, exact)
            }
        }
        FarolFlightRecorder0163.record(
            stage = "TRAFFIC_AWARE_ROUTE_RESULT_STAGE637",
            packageName = null,
            details = "googleResolved=${googleFetched?.count { it != null } ?: 0}; fallbackResolved=${fallback?.count { it != null } ?: 0}; destinations=${destinations.size}",
        )
        result
    }

    /**
     * 0.1.682 trusted primary FAROL route.
     *
     * Sends the last address text from the current card directly to Google Route Matrix and the
     * configured driver targets as coordinates. This deliberately bypasses the learned atlas,
     * cached geocodes and target-biased candidate selection. There is no OSM/legacy fallback here:
     * the caller owns the short timeout and may fall back to the preserved legacy pipeline.
     */
    suspend fun trustedDirectDrivingDistancesFromAddressKm0682(
        originAddress: String,
        destinations: List<Coordinate>,
        apiKey: String,
    ): List<Double?>? = withContext(Dispatchers.IO) {
        if (originAddress.isBlank() || destinations.isEmpty()) {
            FarolFlightRecorder0163.record(
                stage = "FAROL_TRUSTED_DIRECT_ROUTE_SKIPPED_0682",
                packageName = null,
                details = "blankOrigin=${originAddress.isBlank()}; destinations=${destinations.size}",
            )
            return@withContext null
        }

        val started = SystemClock.elapsedRealtimeNanos()
        if (apiKey.isNotBlank()) {
            val body = addressRouteMatrixBody(originAddress, destinations)
            val googleValues = runCatching {
                requestAddressRouteMatrix(body, apiKey, destinations.size)
            }.getOrNull()?.takeIf { result ->
                result.size == destinations.size && result.any { it != null }
            }
            if (googleValues != null) {
                FarolFlightRecorder0163.record(
                    stage = "FAROL_TRUSTED_DIRECT_ROUTE_RESULT_0682",
                    packageName = null,
                    details = "provider=google_raw_address; resolved=${googleValues.count { it != null }}; destinations=${destinations.size}; complete=${googleValues.all { it != null }}; elapsed_us=${(SystemClock.elapsedRealtimeNanos() - started).coerceAtLeast(0L) / 1_000L}",
                )
                return@withContext googleValues
            }
        }

        val unbiasedOrigin0682 = resolveUnbiasedOrigin0682(originAddress)
        if (unbiasedOrigin0682 == null) {
            FarolFlightRecorder0163.record(
                stage = "FAROL_TRUSTED_DIRECT_ROUTE_AMBIGUOUS_0682",
                packageName = null,
                details = "provider=osm_unbiased; query=${originAddress.take(180)}; result=yellow_fallback",
            )
            return@withContext null
        }
        val osmValues = destinations.map { destination ->
            requestWithRetry(OSM_ROUTE_REQUEST_ATTEMPTS) {
                requestOsrmDrivingDistance(unbiasedOrigin0682, destination)
            }
        }.takeIf { values -> values.any { it != null } }
        FarolFlightRecorder0163.record(
            stage = "FAROL_TRUSTED_DIRECT_ROUTE_RESULT_0682",
            packageName = null,
            details = "provider=osm_unbiased; resolved=${osmValues?.count { it != null } ?: 0}; destinations=${destinations.size}; complete=${osmValues?.all { it != null } == true}; elapsed_us=${(SystemClock.elapsedRealtimeNanos() - started).coerceAtLeast(0L) / 1_000L}",
        )
        osmValues
    }

    private suspend fun resolveUnbiasedOrigin0682(originAddress: String): Coordinate? {
        val queries0682 = geocodeQueries0547(originAddress)
        queries0682.forEachIndexed { index0682, query0682 ->
            val candidates0682 = requestWithRetry(OSM_GEOCODE_REQUEST_ATTEMPTS) {
                requestNominatimGeocodeCandidates0547(query0682)
            }.orEmpty()
            val selected0682 = selectUnbiasedGeocodeCandidate0682(candidates0682)
            FarolFlightRecorder0163.record(
                stage = "FAROL_UNBIASED_GEOCODE_RESULT_0682",
                packageName = null,
                details = "index=$index0682; candidates=${candidates0682.size}; selected=${selected0682 != null}; query=${query0682.take(160)}",
            )
            if (selected0682 != null) return selected0682
        }
        return null
    }

    internal fun selectUnbiasedGeocodeCandidate0682(candidates: List<Coordinate>): Coordinate? {
        if (candidates.isEmpty()) return null
        val first0682 = candidates.first()
        if (candidates.size == 1) return first0682
        val geographicallyCoherent0682 = candidates.take(5).all { candidate0682 ->
            straightLineKm0547(first0682, candidate0682) <= TRUSTED_GEOCODE_CLUSTER_KM_0682
        }
        return first0682.takeIf { geographicallyCoherent0682 }
    }
    private fun requestDrivingDistance(body: String, apiKey: String): Double? {
        val connection = (URL(ROUTES_COMPUTE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", apiKey.trim())
            setRequestProperty("X-Goog-FieldMask", "routes.distanceMeters")
            setRequestProperty("X-Android-Package", BuildConfig.APPLICATION_ID)
        }

        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return null
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parseDistanceKm(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun requestAddressRouteMatrix(body: String, apiKey: String, destinationCount: Int): List<Double?>? {
        val connection = (URL(ROUTE_MATRIX_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", apiKey.trim())
            setRequestProperty(
                "X-Goog-FieldMask",
                "originIndex,destinationIndex,distanceMeters,status,condition",
            )
            setRequestProperty("X-Android-Package", BuildConfig.APPLICATION_ID)
        }

        val requestStartedElapsedNanos0163 = SystemClock.elapsedRealtimeNanos()
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val responseCode0163 = connection.responseCode
            FarolFlightRecorder0163.record(
                stage = "MAPS_HTTP_RESPONSE",
                packageName = null,
                details = "endpoint=route_matrix; code=$responseCode0163; destinations=$destinationCount; elapsed_us=${(SystemClock.elapsedRealtimeNanos() - requestStartedElapsedNanos0163).coerceAtLeast(0L) / 1_000L}",
            )
            if (responseCode0163 !in 200..299) {
                val errorBody0163 = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }.getOrDefault("")
                FarolFlightRecorder0163.record(
                    stage = "ROUTE_MATRIX_HTTP_ERROR",
                    packageName = null,
                    details = "code=$responseCode0163; destinations=$destinationCount; body=${sanitizeMapsErrorBody(errorBody0163)}",
                )
                null
            } else {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed0163 = parseRouteMatrixDistances(response, destinationCount)
                FarolFlightRecorder0163.record(
                    stage = "MAPS_HTTP_PARSED",
                    packageName = null,
                    details = "endpoint=route_matrix; body_len=${response.length}; distances=$parsed0163",
                )
                parsed0163
            }
        } catch (error: Throwable) {
            FarolFlightRecorder0163.record(
                stage = "MAPS_HTTP_ERROR",
                packageName = null,
                details = "endpoint=route_matrix; error=${error::class.java.simpleName}:${error.message}; elapsed_us=${(SystemClock.elapsedRealtimeNanos() - requestStartedElapsedNanos0163).coerceAtLeast(0L) / 1_000L}",
            )
            throw error
        } finally {
            connection.disconnect()
        }
    }



    private suspend fun requestOpenStreetMapAddressRoutes(
        originAddress: String,
        destinations: List<Coordinate>,
    ): List<Double?>? {
        if (originAddress.isBlank() || destinations.isEmpty()) return null
        val origin = resolveFreePrimaryOrigin0547(originAddress, destinations)

        if (origin == null) {
            FarolFlightRecorder0163.record(
                stage = "OSM_PRIMARY_GEOCODE_FAILED_0546",
                packageName = null,
                details = "destinations=${destinations.size}; resolver=0547",
            )
            FarolFlightRecorder0163.record(
                stage = "GEOCODE_RESOLUTION_FAILED_0547",
                packageName = null,
                details = "query=${originAddress.take(160)}; destinations=${destinations.size}",
            )
            return null
        }

        val values = destinations.map { destination ->
            requestWithRetry(OSM_ROUTE_REQUEST_ATTEMPTS) {
                requestOsrmDrivingDistance(origin, destination)
            }
        }
        FarolFlightRecorder0163.record(
            stage = "OSM_PRIMARY_ROUTE_RESULT_0546",
            packageName = null,
            details = "resolved=${values.count { it != null }}; destinations=${destinations.size}; geocodeResolver=0547",
        )
        return values.takeIf { list -> list.any { it != null } }
    }

    private fun learnOfflineAtlas642(originAddress: String, coordinate: Coordinate) {
        val aliases = buildList {
            add(originAddress)
            add(normalizeAddress(originAddress))
            addAll(geocodeQueries0547(originAddress))
        }
        offlineAddressAtlas642?.learnAll(aliases, coordinate)
        FarolFlightRecorder0163.record(
            stage = "OFFLINE_ATLAS_LEARNED_0642",
            packageName = null,
            details = "aliases=${aliases.distinct().size}; networkNextTime=false",
        )
    }

    private suspend fun resolvePlatformOrigin0697(originAddress: String): Coordinate? {
        val platformGeocoder = platformGeocodingService0547 ?: return null
        val query = geocodeQueries0547(originAddress).firstOrNull() ?: return null
        FarolFlightRecorder0163.record(
            stage = FarolCoordinateResolution0697.PLATFORM_STARTED_MARKER,
            packageName = null,
            details = "deadline_ms=${FarolCoordinateResolution0697.PLATFORM_DEADLINE_MS}",
        )
        val started = SystemClock.elapsedRealtime()
        val selected = platformGeocoder.geocodeBounded0697(
            query = query,
            region = DeviceRegion(city = "", country = ""),
            timeoutMillis = FarolCoordinateResolution0697.PLATFORM_DEADLINE_MS,
        )
        if (
            selected == null &&
            SystemClock.elapsedRealtime() - started >= FarolCoordinateResolution0697.PLATFORM_DEADLINE_MS - 25L
        ) {
            FarolFlightRecorder0163.record(
                stage = FarolCoordinateResolution0697.PLATFORM_TIMEOUT_MARKER,
                packageName = null,
                details = "elapsed_ms=${SystemClock.elapsedRealtime() - started}",
            )
        }
        if (selected != null) cacheCoordinateAliases0697(originAddress, selected)
        return selected
    }

    private suspend fun resolveFreeOrigin0697(
        originAddress: String,
        destinations: List<Coordinate>,
    ): Coordinate? = withContext(Dispatchers.IO) {
        val query = geocodeQueries0547(originAddress).firstOrNull() ?: return@withContext null
        val candidates = requestNominatimGeocodeCandidates0697(query).orEmpty()
        val selected = selectNearestGeocodeCandidate0547(candidates, destinations)
        if (selected != null) cacheCoordinateAliases0697(originAddress, selected)
        selected
    }

    private suspend fun resolveGoogleOrigin0697(
        originAddress: String,
        targetHints: List<Coordinate>,
        apiKey: String,
        requestContext0699: FarolNetworkFailureIsolation0699.RequestContext? = null,
    ): Coordinate? {
        val query = geocodeQueries(originAddress, DeviceRegion()).firstOrNull() ?: return null
        val boundsBias0703 = if (containsExplicitLocality(query)) null else targetBiasBounds0703(targetHints)
        if (boundsBias0703 != null) {
            FarolFlightRecorder0163.record(
                stage = TARGET_BIAS_MARKER_0703,
                packageName = null,
                details = "provider=google_geocode; bounds=$boundsBias0703; source=configured_work_targets",
            )
        }
        val started0699 = SystemClock.elapsedRealtime()
        FarolFlightRecorder0163.record(
            stage = FarolNetworkFailureIsolation0699.GOOGLE_GEOCODE_STARTED_MARKER,
            packageName = null,
            details = requestContext0699?.diagnostic(
                provider = "google_geocode",
                extra = "deadlineMs=${FarolCoordinateResolution0697.GOOGLE_DEADLINE_MS}; query=${query.take(180)}",
            ) ?: "provider=google_geocode; deadlineMs=${FarolCoordinateResolution0697.GOOGLE_DEADLINE_MS}; query=${query.take(180)}",
        )
        val selected = withTimeoutOrNull(FarolCoordinateResolution0697.GOOGLE_DEADLINE_MS) {
            withContext(Dispatchers.IO) { requestGeocode(query, apiKey, requestContext0699, boundsBias0703) }
        }
        if (selected != null) {
            cacheCoordinateAliases0697(originAddress, selected)
        } else {
            FarolFlightRecorder0163.record(
                stage = FarolNetworkFailureIsolation0699.GOOGLE_GEOCODE_UNAVAILABLE_MARKER,
                packageName = null,
                details = requestContext0699?.diagnostic(
                    provider = "google_geocode",
                    extra = "elapsedMs=${SystemClock.elapsedRealtime() - started0699}; result=null",
                ) ?: "provider=google_geocode; elapsedMs=${SystemClock.elapsedRealtime() - started0699}; result=null",
            )
        }
        return selected
    }

    private fun cacheCoordinateAliases0697(originAddress: String, coordinate: Coordinate) {
        val normalizedOrigin = normalizeAddress(originAddress)
        val keys = buildList {
            add("osm_origin|${normalizedOrigin}")
            geocodeQueries0547(originAddress).forEach { add(it.lowercase(Locale.ROOT)) }
        }.distinct()
        keys.forEach { key ->
            geocodeCache[key] = coordinate
            persistCoordinate(key, coordinate)
        }
    }

    private suspend fun resolvePlatformFirstOrigin640(originAddress: String): Coordinate? {
        val platformGeocoder = platformGeocodingService0547 ?: return null
        val normalizedOrigin = normalizeAddress(originAddress)
        val originCacheKey = "osm_origin|$normalizedOrigin"
        val queries = geocodeQueries0547(originAddress)
        queries.forEachIndexed { index, query ->
            val selected = platformGeocoder.geocode(
                query = query,
                region = DeviceRegion(city = "", country = ""),
            )
            FarolFlightRecorder0163.record(
                stage = "GEOCODE_PLATFORM_FIRST_0640",
                packageName = null,
                details = "index=$index; selected=${selected != null}; query=${query.take(160)}",
            )
            if (selected != null) {
                geocodeCache[originCacheKey] = selected
                persistCoordinate(originCacheKey, selected)
                // Populate the query aliases too so every subsequent card mutation is an O(1) hit.
                queries.forEach { alias ->
                    val key = alias.lowercase(Locale.ROOT)
                    geocodeCache[key] = selected
                    persistCoordinate(key, selected)
                }
                return selected
            }
        }
        return null
    }

    private suspend fun resolveFreePrimaryOrigin0547(
        originAddress: String,
        destinations: List<Coordinate>,
    ): Coordinate? {
        val normalizedOrigin = normalizeAddress(originAddress)
        val originCacheKey = "osm_origin|$normalizedOrigin"
        geocodeCache[originCacheKey]?.let { coordinate ->
            FarolFlightRecorder0163.record(
                stage = "GEOCODE_CACHE_HIT_0547",
                packageName = null,
                details = "layer=memory",
            )
            return coordinate
        }
        readPersistentCoordinate(originCacheKey)?.let { coordinate ->
            geocodeCache[originCacheKey] = coordinate
            FarolFlightRecorder0163.record(
                stage = "GEOCODE_CACHE_HIT_0547",
                packageName = null,
                details = "layer=persistent",
            )
            return coordinate
        }

        val queries = geocodeQueries0547(originAddress)
        FarolFlightRecorder0163.record(
            stage = "GEOCODE_RESOLUTION_START_0547",
            packageName = null,
            details = "queries=${queries.size}; destinations=${destinations.size}; malformedParenthesis=${originAddress.count { it == '(' } != originAddress.count { it == ')' }}",
        )

        queries.forEachIndexed { index, query ->
            val candidates = requestWithRetry(OSM_GEOCODE_REQUEST_ATTEMPTS) {
                requestNominatimGeocodeCandidates0547(query)
            }.orEmpty()
            val selected = selectNearestGeocodeCandidate0547(candidates, destinations)
            FarolFlightRecorder0163.record(
                stage = "GEOCODE_QUERY_RESULT_0547",
                packageName = null,
                details = "provider=nominatim; index=$index; candidates=${candidates.size}; selected=${selected != null}; query=${query.take(160)}",
            )
            if (selected != null) {
                geocodeCache[originCacheKey] = selected
                persistCoordinate(originCacheKey, selected)
                return selected
            }
        }

        val platformGeocoder = platformGeocodingService0547
        if (platformGeocoder != null) {
            queries.forEachIndexed { index, query ->
                val selected = platformGeocoder.geocode(
                    query = query,
                    region = DeviceRegion(city = "", country = ""),
                )
                FarolFlightRecorder0163.record(
                    stage = "GEOCODE_ANDROID_FALLBACK_0547",
                    packageName = null,
                    details = "index=$index; selected=${selected != null}; query=${query.take(160)}",
                )
                if (selected != null) {
                    geocodeCache[originCacheKey] = selected
                    persistCoordinate(originCacheKey, selected)
                    return selected
                }
            }
        }

        return null
    }

    /**
     * 0.1.547: corrige texto de card antes da geocodificacao sem inventar localidade.
     * O inDrive pode entregar um parenteses de bairro aberto e sem fechamento, como
     * "Rua Flores da Primavera, 263 (Conjunto Promorar Rio Claro, São Paulo - SP".
     * A consulta preserva rua/numero/cidade/UF e oferece uma segunda forma removendo
     * apenas o bairro. Nunca faz fallback para uma rua nacional sem cidade/UF.
     */
    internal fun geocodeQueries0547(originAddress: String): List<String> {
        val compact = originAddress.trim().replace(Regex("""\s+"""), " ")
        if (compact.isBlank()) return emptyList()

        val punctuationNormalized = compact
            .replace('(', ',')
            .replace(')', ' ')
            .replace(Regex("""\s*,\s*"""), ", ")
            .replace(Regex(""",\s*,+"""), ", ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd(',')

        val simplified = Regex(
            """^(.+?,\s*\d+[A-Za-z]?)\s*,?.*?,\s*([^,]+?)\s*-\s*([A-Za-z]{2})\s*$""",
        ).matchEntire(punctuationNormalized)?.let { match ->
            val streetAndNumber = match.groupValues[1].trim().trimEnd(',')
            val city = match.groupValues[2].trim()
            val state = match.groupValues[3].uppercase(Locale.ROOT)
            "$streetAndNumber, $city - $state"
        }

        return linkedSetOf(punctuationNormalized, simplified)
            .filterNotNull()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }

    internal fun selectNearestGeocodeCandidate0547(
        candidates: List<Coordinate>,
        destinations: List<Coordinate>,
    ): Coordinate? {
        if (candidates.isEmpty()) return null
        if (destinations.isEmpty()) return candidates.first()
        return candidates.minByOrNull { candidate ->
            destinations.minOf { destination -> straightLineKm0547(candidate, destination) }
        }
    }

    private fun straightLineKm0547(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(b.longitude - a.longitude)
        val sinLat = kotlin.math.sin(deltaLat / 2.0)
        val sinLon = kotlin.math.sin(deltaLon / 2.0)
        val haversine = sinLat * sinLat +
            kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * sinLon * sinLon
        return 2.0 * 6371.0088 * kotlin.math.asin(kotlin.math.sqrt(haversine.coerceIn(0.0, 1.0)))
    }

    private fun requestNominatimGeocode(query: String): Coordinate? =
        requestNominatimGeocodeCandidates0547(query)?.firstOrNull()

    private fun requestNominatimGeocodeCandidates0697(query: String): List<Coordinate>? {
        val encodedAddress = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL(
            "$OSM_NOMINATIM_URL?format=jsonv2&limit=5&accept-language=${URLEncoder.encode(Locale.getDefault().toLanguageTag(), Charsets.UTF_8.name())}&q=$encodedAddress",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = FarolCoordinateResolution0697.OSM_CONNECT_TIMEOUT_MS
            readTimeout = FarolCoordinateResolution0697.OSM_READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Connection", "close")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "RotaCerta/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            json.parseToJsonElement(body).jsonArray.mapNotNull { item ->
                val objectValue = item.jsonObject
                val latitude = objectValue["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: return@mapNotNull null
                val longitude = objectValue["lon"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: return@mapNotNull null
                Coordinate(latitude, longitude)
            }
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun requestNominatimGeocodeCandidates0547(query: String): List<Coordinate>? {
        val encodedAddress = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL(
            "$OSM_NOMINATIM_URL?format=jsonv2&limit=5&accept-language=${URLEncoder.encode(Locale.getDefault().toLanguageTag(), Charsets.UTF_8.name())}&q=$encodedAddress",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = OSM_CONNECT_TIMEOUT_MS
            readTimeout = OSM_READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "RotaCerta/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                FarolFlightRecorder0163.record(
                    stage = "OSM_PRIMARY_GEOCODE_HTTP_0546",
                    packageName = null,
                    details = "code=$code; resolver=0547",
                )
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            json.parseToJsonElement(body).jsonArray.mapNotNull { item ->
                val objectValue = item.jsonObject
                val latitude = objectValue["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: return@mapNotNull null
                val longitude = objectValue["lon"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: return@mapNotNull null
                Coordinate(latitude, longitude)
            }
        } catch (error: Throwable) {
            FarolFlightRecorder0163.record(
                stage = "OSM_PRIMARY_GEOCODE_ERROR_0546",
                packageName = null,
                details = "error=${error::class.java.simpleName}:${error.message}; resolver=0547",
            )
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun requestOsrmDrivingDistance(origin: Coordinate, destination: Coordinate): Double? {
        val routeUrl = String.format(
            Locale.US,
            "%s/route/v1/driving/%.7f,%.7f;%.7f,%.7f?overview=false&alternatives=false&steps=false",
            OSRM_ROUTE_URL,
            origin.longitude,
            origin.latitude,
            destination.longitude,
            destination.latitude,
        )
        val connection = (URL(routeUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = OSM_CONNECT_TIMEOUT_MS
            readTimeout = OSM_READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "RotaCerta/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                FarolFlightRecorder0163.record(
                    stage = "OSM_PRIMARY_ROUTE_HTTP_0546",
                    packageName = null,
                    details = "code=$code",
                )
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(body).jsonObject
            if (root["code"]?.jsonPrimitive?.content != "Ok") return null
            val distanceMeters = root["routes"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("distance")?.jsonPrimitive
                ?.doubleOrNull
                ?: return null
            (distanceMeters / 1000.0).takeIf { it >= 0.0 }
        } catch (error: Throwable) {
            FarolFlightRecorder0163.record(
                stage = "OSM_PRIMARY_ROUTE_ERROR_0546",
                packageName = null,
                details = "error=${error::class.java.simpleName}:${error.message}",
            )
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun requestAddressRouteFallback(
        originAddress: String,
        destinations: List<Coordinate>,
        apiKey: String,
    ): List<Double?>? {
        if (originAddress.isBlank() || destinations.isEmpty() || apiKey.isBlank()) return null
        FarolFlightRecorder0163.record(
            stage = "ROUTE_MATRIX_FALLBACK_GEOCODE_STARTED",
            packageName = null,
            details = "destinations=${destinations.size}",
        )
        val origin = requestWithRetry(GEOCODE_REQUEST_ATTEMPTS) {
            requestGeocode(originAddress, apiKey)
        } ?: run {
            FarolFlightRecorder0163.record(
                stage = "ROUTE_MATRIX_FALLBACK_GEOCODE_FAILED",
                packageName = null,
                details = "originResolved=false; destinations=${destinations.size}",
            )
            return null
        }
        val values = destinations.map { destination ->
            requestWithRetry(ROUTE_REQUEST_ATTEMPTS) {
                requestDrivingDistance(coordinateRouteBody(origin, destination), apiKey)
            }
        }
        FarolFlightRecorder0163.record(
            stage = "ROUTE_MATRIX_FALLBACK_RESOLVED",
            packageName = null,
            details = "returned=${values.count { it != null }}; destinations=${destinations.size}",
        )
        return values
    }

    private fun sanitizeMapsErrorBody(raw: String): String = raw
        .replace(Regex("AIza[0-9A-Za-z_-]+"), "<redacted-api-key>")
        .replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer <redacted>")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(600)

    /**
     * Monta consultas sem inventar uma cidade fixa.
     *
     * Antes, qualquer endereco sem cidade era tentado primeiro em Sao Paulo, o que
     * podia gravar coordenadas erradas no cache quando o motorista estava em outro
     * municipio ou estado. Agora a cidade do aparelho/configuracao tem prioridade;
     * quando ela nao existe, a busca permanece nacional e conserva o texto original.
     */
    internal fun geocodeQueries(query: String, region: DeviceRegion): List<String> {
        val cleanQuery = query.trim().replace(Regex("""\s+"""), " ")
        if (cleanQuery.isBlank()) return emptyList()

        val country = region.country.trim().ifBlank { "Brasil" }
        val regionCity = region.city.trim().takeIf { it.isNotBlank() }
        val queryAlreadyContainsRegion = containsExplicitLocality(cleanQuery)

        return buildList {
            if (regionCity != null && !queryAlreadyContainsRegion) {
                add("$cleanQuery, $regionCity, $country")
            }
            add("$cleanQuery, $country")
            add(cleanQuery)
        }
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }

    internal fun targetBiasBounds0703(targetHints: List<Coordinate>): String? {
        val valid = targetHints.filter {
            it.latitude.isFinite() && it.longitude.isFinite() &&
                it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
        }.take(12)
        if (valid.isEmpty()) return null
        val paddingDegrees = 0.35
        val minLat = (valid.minOf { it.latitude } - paddingDegrees).coerceAtLeast(-90.0)
        val maxLat = (valid.maxOf { it.latitude } + paddingDegrees).coerceAtMost(90.0)
        val minLon = (valid.minOf { it.longitude } - paddingDegrees).coerceAtLeast(-180.0)
        val maxLon = (valid.maxOf { it.longitude } + paddingDegrees).coerceAtMost(180.0)
        return String.format(Locale.US, "%.6f,%.6f|%.6f,%.6f", minLat, minLon, maxLat, maxLon)
    } // GOOGLE_TARGET_BIAS_0703

    private fun containsExplicitLocality(query: String): Boolean {
        val normalized = query.lowercase(Locale.ROOT)
        val statePattern = Regex("""(?:^|[,\s-])(?:ac|al|ap|am|ba|ce|df|es|go|ma|mt|ms|mg|pa|pb|pr|pe|pi|rj|rn|rs|ro|rr|sc|sp|se|to)(?:$|[,\s-])""", RegexOption.IGNORE_CASE)
        return statePattern.containsMatchIn(normalized) ||
            Regex("""\b\d{5}-?\d{3}\b""").containsMatchIn(normalized) ||
            normalized.contains(" brasil")
    }

    private fun requestGeocode(
        scopedQuery: String,
        apiKey: String,
        requestContext0699: FarolNetworkFailureIsolation0699.RequestContext? = null,
        boundsBias0703: String? = null,
    ): Coordinate? {
        val encodedAddress = URLEncoder.encode(scopedQuery, "UTF-8")
        val encodedKey = URLEncoder.encode(apiKey.trim(), "UTF-8")
        val encodedBounds0703 = boundsBias0703?.trim()?.takeIf(String::isNotBlank)
            ?.let { URLEncoder.encode(it, "UTF-8") }
        val url = URL(
            "https://maps.googleapis.com/maps/api/geocode/json" +
                "?address=$encodedAddress" +
                "&region=br" +
                "&language=pt-BR" +
                (encodedBounds0703?.let { "&bounds=$it" } ?: "") +
                "&key=$encodedKey",
        )

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("X-Android-Package", BuildConfig.APPLICATION_ID)
        }
        val started0699 = SystemClock.elapsedRealtime()

        return try {
            val responseCode0699 = connection.responseCode
            if (responseCode0699 !in 200..299) {
                null
            } else {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseCoordinate(body)
            }
        } catch (error0699: Throwable) {
            if (!FarolNetworkFailureIsolation0699.isRecoverableProviderFailure(error0699)) throw error0699
            FarolFlightRecorder0163.record(
                stage = FarolNetworkFailureIsolation0699.GOOGLE_GEOCODE_TRANSPORT_FAILED_MARKER,
                packageName = null,
                details = requestContext0699?.diagnostic(
                    provider = "google_geocode",
                    extra = "elapsedMs=${SystemClock.elapsedRealtime() - started0699}; error=${FarolNetworkFailureIsolation0699.failureChain(error0699)}",
                ) ?: "provider=google_geocode; elapsedMs=${SystemClock.elapsedRealtime() - started0699}; error=${FarolNetworkFailureIsolation0699.failureChain(error0699)}",
            )
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCoordinate(body: String): Coordinate? {
        val root = json.parseToJsonElement(body).jsonObject
        val status = root["status"]?.jsonPrimitive?.content.orEmpty()
        if (status != "OK") return null

        val firstResult = root["results"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val location = firstResult["geometry"]?.jsonObject
            ?.get("location")?.jsonObject
            ?: return null

        val latitude = location["lat"]?.jsonPrimitive?.doubleOrNull ?: return null
        val longitude = location["lng"]?.jsonPrimitive?.doubleOrNull ?: return null
        return Coordinate(latitude, longitude)
    }

    private fun parseDistanceKm(body: String): Double? {
        val root = json.parseToJsonElement(body).jsonObject
        val distanceMeters = root["routes"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("distanceMeters")?.jsonPrimitive
            ?.intOrNull
            ?: return null
        return distanceMeters / 1000.0
    }

    private fun parseRouteMatrixDistances(body: String, destinationCount: Int): List<Double?> {
        val result = MutableList<Double?>(destinationCount.coerceAtLeast(0)) { null }
        val elements = json.parseToJsonElement(body).jsonArray
        elements.forEach { element ->
            val objectValue = element.jsonObject
            val destinationIndex = objectValue["destinationIndex"]?.jsonPrimitive?.intOrNull ?: return@forEach
            if (destinationIndex !in result.indices) return@forEach
            val distanceMeters = objectValue["distanceMeters"]?.jsonPrimitive?.intOrNull ?: return@forEach
            result[destinationIndex] = distanceMeters / 1000.0
        }
        return result
    }

    private fun coordinateRouteBody(origin: Coordinate, destination: Coordinate): String = String.format(
        Locale.US,
        """
        {
          "origin": {"location": {"latLng": {"latitude": %.7f, "longitude": %.7f}}},
          "destination": {"location": {"latLng": {"latitude": %.7f, "longitude": %.7f}}},
          "travelMode": "DRIVE",
          "routingPreference": "TRAFFIC_UNAWARE",
          "languageCode": "pt-BR",
          "units": "METRIC"
        }
        """.trimIndent(),
        origin.latitude,
        origin.longitude,
        destination.latitude,
        destination.longitude,
    )

    private fun addressRouteMatrixBody(originAddress: String, destinations: List<Coordinate>): String {
        val destinationJson = destinations.joinToString(",") { destination ->
            String.format(
                Locale.US,
                """{"waypoint":{"location":{"latLng":{"latitude":%.7f,"longitude":%.7f}}}}""",
                destination.latitude,
                destination.longitude,
            )
        }
        return """
            {
              "origins": [{"waypoint": {"address": "${jsonEscape(originAddress)}"}}],
              "destinations": [$destinationJson],
              "travelMode": "DRIVE",
              "routingPreference": "TRAFFIC_UNAWARE",
              "languageCode": "pt-BR"
            }
        """.trimIndent()
    }


    private fun trafficAwareAddressRouteMatrixBody(originAddress: String, destinations: List<Coordinate>): String {
        val destinationJson = destinations.joinToString(",") { destination ->
            String.format(
                Locale.US,
                """{"waypoint":{"location":{"latLng":{"latitude":%.7f,"longitude":%.7f}}}}""",
                destination.latitude,
                destination.longitude,
            )
        }
        return """
            {
              "origins": [{"waypoint": {"address": "${jsonEscape(originAddress)}"}}],
              "destinations": [$destinationJson],
              "travelMode": "DRIVE",
              "routingPreference": "TRAFFIC_AWARE",
              "languageCode": "pt-BR"
            }
        """.trimIndent()
    }

    private fun normalizeAddress(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("""\s+"""), " ").trim()

    private fun coordinateRouteKey(origin: Coordinate, destination: Coordinate): String =
        listOf(origin.latitude, origin.longitude, destination.latitude, destination.longitude).joinToString("|")

    private fun addressRouteKey(originAddress: String, destination: Coordinate): String =
        listOf(originAddress, destination.latitude, destination.longitude).joinToString("|")

    private fun jsonEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", " ")
        .replace("\n", " ")

    private fun readPersistentCoordinate(cacheKey: String): Coordinate? {
        val value = cachePrefs?.getString(persistentKey(PERSISTENT_GEOCODE_PREFIX, cacheKey), null) ?: return null
        val parts = value.split('|')
        if (parts.size != 3) return null
        val timestamp = parts[0].toLongOrNull() ?: return null
        if (isExpired(timestamp, GEOCODE_CACHE_TTL_MS)) return null
        val latitude = parts[1].toDoubleOrNull() ?: return null
        val longitude = parts[2].toDoubleOrNull() ?: return null
        return Coordinate(latitude, longitude)
    }

    private fun persistCoordinate(cacheKey: String, coordinate: Coordinate) {
        cachePrefs?.edit()?.putString(
            persistentKey(PERSISTENT_GEOCODE_PREFIX, cacheKey),
            "${System.currentTimeMillis()}|${coordinate.latitude}|${coordinate.longitude}",
        )?.apply()
        prunePersistentCacheEventually()
    }

    private fun readPersistentDistance(prefix: String, cacheKey: String, ttlMillis: Long): Double? {
        val key = persistentKey(prefix, cacheKey)
        val value = cachePrefs?.getString(key, null) ?: return null
        val parts = value.split('|')
        if (parts.size != 2) return null
        val timestamp = parts[0].toLongOrNull() ?: return null
        if (isExpired(timestamp, ttlMillis)) {
            cachePrefs.edit().remove(key).apply()
            return null
        }
        return parts[1].toDoubleOrNull()
    }

    private fun persistDistance(prefix: String, cacheKey: String, distanceKm: Double) {
        cachePrefs?.edit()?.putString(
            persistentKey(prefix, cacheKey),
            "${System.currentTimeMillis()}|$distanceKm",
        )?.apply()
        prunePersistentCacheEventually()
    }

    @Synchronized
    private fun prunePersistentCacheEventually() {
        writesSincePrune += 1
        if (writesSincePrune < PRUNE_EVERY_WRITES) return
        writesSincePrune = 0
        val prefs = cachePrefs ?: return
        val now = System.currentTimeMillis()
        val entries = prefs.all.mapNotNull { (key, rawValue) ->
            if (!key.startsWith(PERSISTENT_CACHE_KEY_PREFIX)) return@mapNotNull null
            val timestamp = (rawValue as? String)?.substringBefore('|')?.toLongOrNull() ?: return@mapNotNull null
            key to timestamp
        }
        val editor = prefs.edit()
        entries.filter { (_, timestamp) -> now - timestamp > MAX_CACHE_TTL_MS }
            .forEach { (key, _) -> editor.remove(key) }
        entries.sortedByDescending { it.second }
            .drop(MAX_PERSISTENT_ENTRIES)
            .forEach { (key, _) -> editor.remove(key) }
        editor.apply()
    }

    private fun persistentKey(prefix: String, rawKey: String): String =
        PERSISTENT_CACHE_KEY_PREFIX + prefix + sha256(rawKey)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun isExpired(timestamp: Long, ttlMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        return timestamp <= 0L || now < timestamp || now - timestamp > ttlMillis
    }

    private fun <T> requestWithRetry(attempts: Int, block: () -> T?): T? {
        repeat(attempts.coerceAtLeast(1)) { attempt ->
            val result = runCatching(block).getOrNull()
            if (result != null) return result
            if (attempt < attempts - 1) Thread.sleep(RETRY_DELAY_MS)
        }
        return null
    }

    private companion object {
        const val TARGET_BIAS_MARKER_0703 = "GOOGLE_TARGET_BIAS_0703"
        const val ROUTES_COMPUTE_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
        const val ROUTE_MATRIX_URL = "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix"
        const val OSM_NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"
        const val OSRM_ROUTE_URL = "https://router.project-osrm.org"
        const val OSM_CONNECT_TIMEOUT_MS = 900
        const val OSM_READ_TIMEOUT_MS = 1_200
        const val OSM_GEOCODE_REQUEST_ATTEMPTS = 1
        const val OSM_ROUTE_REQUEST_ATTEMPTS = 1
        const val TRUSTED_GEOCODE_CLUSTER_KM_0682 = 3.0
        const val CONNECT_TIMEOUT_MS = 350 // subsecond_connect_budget_checklist_6
        const val READ_TIMEOUT_MS = 600 // subsecond_read_budget_checklist_6
        const val ROUTE_REQUEST_ATTEMPTS = 1 // single_route_attempt_checklist_6
        const val GEOCODE_REQUEST_ATTEMPTS = 1
        const val RETRY_DELAY_MS = 80L

        const val PERSISTENT_CACHE_PREFS = "maps_fast_cache_v128"
        const val PERSISTENT_CACHE_KEY_PREFIX = "maps128_"
        const val PERSISTENT_GEOCODE_PREFIX = "geocode_"
        const val PERSISTENT_COORD_ROUTE_PREFIX = "coord_route_"
        const val PERSISTENT_ADDRESS_ROUTE_PREFIX = "address_route_"
        const val PERSISTENT_TRAFFIC_ADDRESS_ROUTE_PREFIX = "traffic_address_route_"
        const val MAX_PERSISTENT_ENTRIES = 500
        const val PRUNE_EVERY_WRITES = 20
        const val ROUTE_CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1_000L
        const val GEOCODE_CACHE_TTL_MS = 90L * 24L * 60L * 60L * 1_000L
        const val MAX_CACHE_TTL_MS = GEOCODE_CACHE_TTL_MS
    }
}
