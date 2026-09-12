from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match in {path}: {count}\n--- OLD ---\n{old}")
    path.write_text(text.replace(old, new, 1))


build = ROOT / "app/build.gradle.kts"
replace_once(build, 'versionCode = 5837\n        versionName = "0.1.545"', 'versionCode = 5838\n        versionName = "0.1.546"')

gmaps = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt"
replace_once(
    gmaps,
    '''        if (originAddress.isBlank() || destinations.isEmpty() || apiKey.isBlank()) {\n            return@withContext List(destinations.size) { null }\n        }''',
    '''        if (originAddress.isBlank() || destinations.isEmpty()) {\n            return@withContext List(destinations.size) { null }\n        }''',
)

replace_once(
    gmaps,
    '''        val missingDestinations = missingIndexes.map(destinations::get)\n        val body = addressRouteMatrixBody(originAddress, missingDestinations)\n        val matrixFetched = requestWithRetry(ROUTE_REQUEST_ATTEMPTS) {\n            requestAddressRouteMatrix(body, apiKey, missingDestinations.size)\n        }\n        val fetched = matrixFetched ?: requestAddressRouteFallback(\n            originAddress = originAddress,\n            destinations = missingDestinations,\n            apiKey = apiKey,\n        )\n''',
    '''        val missingDestinations = missingIndexes.map(destinations::get)\n\n        // 0.1.546: free road-routing is the primary authority for card distance.\n        // Google remains a secondary contingency only for entries the free provider\n        // could not resolve. The decision engine still receives road distance in km;\n        // no green/red threshold or rendering rule is changed here.\n        val osmFetched = requestOpenStreetMapAddressRoutes(\n            originAddress = originAddress,\n            destinations = missingDestinations,\n        )\n        val unresolvedIndexes = missingDestinations.indices\n            .filter { index -> osmFetched?.getOrNull(index) == null }\n        val googleFallbackByMissingIndex = mutableMapOf<Int, Double?>()\n        if (unresolvedIndexes.isNotEmpty() && apiKey.isNotBlank()) {\n            val googleDestinations = unresolvedIndexes.map(missingDestinations::get)\n            val body = addressRouteMatrixBody(originAddress, googleDestinations)\n            val matrixFetched = requestWithRetry(ROUTE_REQUEST_ATTEMPTS) {\n                requestAddressRouteMatrix(body, apiKey, googleDestinations.size)\n            }\n            val googleFetched = matrixFetched ?: requestAddressRouteFallback(\n                originAddress = originAddress,\n                destinations = googleDestinations,\n                apiKey = apiKey,\n            )\n            unresolvedIndexes.forEachIndexed { googleIndex, originalMissingIndex ->\n                googleFallbackByMissingIndex[originalMissingIndex] = googleFetched?.getOrNull(googleIndex)\n            }\n        }\n        val fetched = missingDestinations.indices.map { index ->\n            osmFetched?.getOrNull(index) ?: googleFallbackByMissingIndex[index]\n        }\n        FarolFlightRecorder0163.record(\n            stage = "ROUTE_PROVIDER_SELECTION_0546",\n            packageName = null,\n            details = "osmResolved=${osmFetched?.count { it != null } ?: 0}; googleFallbackRequested=${unresolvedIndexes.size}; googleKeyPresent=${apiKey.isNotBlank()}; finalResolved=${fetched.count { it != null }}; destinations=${missingDestinations.size}",\n        )\n''',
)

marker = '''\n    private fun requestAddressRouteFallback(\n'''
insert = r'''
    private fun requestOpenStreetMapAddressRoutes(
        originAddress: String,
        destinations: List<Coordinate>,
    ): List<Double?>? {
        if (originAddress.isBlank() || destinations.isEmpty()) return null
        val normalizedOrigin = normalizeAddress(originAddress)
        val originCacheKey = "osm_origin|$normalizedOrigin"
        val origin = geocodeCache[originCacheKey]
            ?: readPersistentCoordinate(originCacheKey)?.also { geocodeCache[originCacheKey] = it }
            ?: requestWithRetry(OSM_GEOCODE_REQUEST_ATTEMPTS) {
                requestNominatimGeocode(originAddress)
            }?.also { coordinate ->
                geocodeCache[originCacheKey] = coordinate
                persistCoordinate(originCacheKey, coordinate)
            }

        if (origin == null) {
            FarolFlightRecorder0163.record(
                stage = "OSM_PRIMARY_GEOCODE_FAILED_0546",
                packageName = null,
                details = "destinations=${destinations.size}",
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
            details = "resolved=${values.count { it != null }}; destinations=${destinations.size}",
        )
        return values.takeIf { list -> list.any { it != null } }
    }

    private fun requestNominatimGeocode(query: String): Coordinate? {
        val encodedAddress = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL(
            "$OSM_NOMINATIM_URL?format=jsonv2&limit=1&countrycodes=br&accept-language=pt-BR&q=$encodedAddress",
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
                    details = "code=$code",
                )
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val first = json.parseToJsonElement(body).jsonArray.firstOrNull()?.jsonObject ?: return null
            val latitude = first["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
            val longitude = first["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
            Coordinate(latitude, longitude)
        } catch (error: Throwable) {
            FarolFlightRecorder0163.record(
                stage = "OSM_PRIMARY_GEOCODE_ERROR_0546",
                packageName = null,
                details = "error=${error::class.java.simpleName}:${error.message}",
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

'''
text = gmaps.read_text()
if text.count(marker) != 1:
    raise SystemExit(f"requestAddressRouteFallback marker count={text.count(marker)}")
gmaps.write_text(text.replace(marker, "\n" + insert + "    private fun requestAddressRouteFallback(\n", 1))

replace_once(
    gmaps,
    '''        const val ROUTES_COMPUTE_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"\n        const val ROUTE_MATRIX_URL = "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix"\n        const val CONNECT_TIMEOUT_MS = 350 // subsecond_connect_budget_checklist_6''',
    '''        const val ROUTES_COMPUTE_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"\n        const val ROUTE_MATRIX_URL = "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix"\n        const val OSM_NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"\n        const val OSRM_ROUTE_URL = "https://router.project-osrm.org"\n        const val OSM_CONNECT_TIMEOUT_MS = 900\n        const val OSM_READ_TIMEOUT_MS = 1_200\n        const val OSM_GEOCODE_REQUEST_ATTEMPTS = 1\n        const val OSM_ROUTE_REQUEST_ATTEMPTS = 1\n        const val CONNECT_TIMEOUT_MS = 350 // subsecond_connect_budget_checklist_6''',
)

live = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
replace_once(
    live,
    '''        if (apiKeyStage19.isBlank()) return\n        val targetsStage19 = fastWorkRegionTargetsChecklist13(settingsStage19)''',
    '''        val targetsStage19 = fastWorkRegionTargetsChecklist13(settingsStage19)''',
)
replace_once(
    live,
    '''        if (apiKeyChecklist13.isBlank()) {\n            rememberBubbleReason("google_maps_api_required", "Destino confirmado, mas a Chave Google Maps API está ausente.")\n            showOverlay(RadarColor.Default, distanceKm = null)\n            return\n        }\n        val targetsChecklist13 = fastWorkRegionTargetsChecklist13(settingsChecklist13)''',
    '''        val targetsChecklist13 = fastWorkRegionTargetsChecklist13(settingsChecklist13)''',
)
replace_once(
    live,
    '''        return if (originAddress.isNotBlank() && destinations.isNotEmpty() && apiKey.isNotBlank()) {\n            googleMapsService.drivingDistancesFromAddressKm(originAddress, destinations, apiKey)\n        } else {''',
    '''        return if (originAddress.isNotBlank() && destinations.isNotEmpty()) {\n            googleMapsService.drivingDistancesFromAddressKm(originAddress, destinations, apiKey)\n        } else {''',
)

contract = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/ReadingFreePrimary0546ContractTest.kt"
contract.write_text(r'''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingFreePrimary0546ContractTest {
    private fun source(name: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
            File(cwd.parentFile ?: cwd, "app/src/main/java/br/com/mapeiaia/rotacerta/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("source not found: $name; cwd=${cwd.absolutePath}")
    }

    @Test fun addressRouteUsesFreeRoadProviderBeforeGoogleFallback() {
        val s = source("GoogleMapsService.kt")
        val start = s.indexOf("    suspend fun drivingDistancesFromAddressKm(")
        val end = s.indexOf("    fun cachedDrivingDistancesFromAddressKm(", start)
        assertTrue(start >= 0 && end > start)
        val block = s.substring(start, end)
        val osm = block.indexOf("requestOpenStreetMapAddressRoutes(")
        val google = block.indexOf("requestAddressRouteMatrix(")
        assertTrue(osm >= 0)
        assertTrue(google > osm)
        assertTrue(block.contains("ROUTE_PROVIDER_SELECTION_0546"))
        assertFalse(block.contains("destinations.isEmpty() || apiKey.isBlank()"))
    }

    @Test fun freeProviderProducesRoadDistanceNotHaversineApproximation() {
        val s = source("GoogleMapsService.kt")
        assertTrue(s.contains("https://nominatim.openstreetmap.org/search"))
        assertTrue(s.contains("https://router.project-osrm.org"))
        assertTrue(s.contains("/route/v1/driving/"))
        assertTrue(s.contains("OSM_PRIMARY_ROUTE_RESULT_0546"))
        assertFalse(s.contains("haversine"))
    }

    @Test fun readingNoLongerRequiresGoogleKeyToStartDistanceResolution() {
        val s = source("LiveRideAccessibilityService.kt")
        assertFalse(s.contains("if (apiKeyStage19.isBlank()) return"))
        assertFalse(s.contains("google_maps_api_required"))
        assertTrue(s.contains("googleMapsService.drivingDistancesFromAddressKm("))
    }
}
''')

print("reading free-primary 0.1.546 materialized")
