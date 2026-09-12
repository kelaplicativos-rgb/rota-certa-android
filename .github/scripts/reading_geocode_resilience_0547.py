from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
service_path = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt"
build_path = ROOT / "app/build.gradle.kts"
test_path = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/ReadingGeocodeResilience0547ContractTest.kt"

service = service_path.read_text(encoding="utf-8")
build = build_path.read_text(encoding="utf-8")

if 'versionCode = 5838' not in build or 'versionName = "0.1.546"' not in build:
    raise SystemExit("baseline version is not 0.1.546/5838")
build = build.replace('versionCode = 5838', 'versionCode = 5839', 1)
build = build.replace('versionName = "0.1.546"', 'versionName = "0.1.547"', 1)

field_anchor = """    private val cachePrefs: SharedPreferences? = context
        ?.applicationContext
        ?.getSharedPreferences(PERSISTENT_CACHE_PREFS, Context.MODE_PRIVATE)
    private var writesSincePrune = 0
"""
field_replacement = """    private val cachePrefs: SharedPreferences? = context
        ?.applicationContext
        ?.getSharedPreferences(PERSISTENT_CACHE_PREFS, Context.MODE_PRIVATE)
    private val platformGeocodingService0547: GeocodingService? = context
        ?.applicationContext
        ?.let(::GeocodingService)
    private var writesSincePrune = 0
"""
if service.count(field_anchor) != 1:
    raise SystemExit("cache field anchor mismatch")
service = service.replace(field_anchor, field_replacement, 1)

pattern = re.compile(
    r"\n\n\n    private fun requestOpenStreetMapAddressRoutes\(.*?\n    private fun requestOsrmDrivingDistance\(",
    re.S,
)
replacement = r'''


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
                val selected = when (val result = platformGeocoder.geocode(query)) {
                    is Result.Success -> result.data
                    is Result.Failure -> null
                }
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

    private fun requestNominatimGeocodeCandidates0547(query: String): List<Coordinate>? {
        val encodedAddress = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL(
            "$OSM_NOMINATIM_URL?format=jsonv2&limit=5&countrycodes=br&accept-language=pt-BR&q=$encodedAddress",
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

    private fun requestOsrmDrivingDistance('''
service, count = pattern.subn(replacement, service, count=1)
if count != 1:
    raise SystemExit(f"OSM resolver block replacement count={count}")

if "GEOCODE_RESOLUTION_START_0547" not in service:
    raise SystemExit("0547 marker missing after patch")
if "requestNominatimGeocodeCandidates0547" not in service:
    raise SystemExit("0547 Nominatim candidate resolver missing")
if "platformGeocodingService0547" not in service:
    raise SystemExit("0547 Android geocoder fallback missing")

service_path.write_text(service, encoding="utf-8")
build_path.write_text(build, encoding="utf-8")

test_path.write_text(r'''package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadingGeocodeResilience0547ContractTest {
    private fun source(relative: String): String = File(System.getProperty("user.dir"), relative).readText()

    @Test
    fun malformed_parenthesis_from_indrive_is_normalized_without_losing_city_or_state() {
        val service = GoogleMapsService()
        val queries = service.geocodeQueries0547(
            "Rua Flores da Primavera, 263 (Conjunto Promorar Rio Claro, São Paulo - SP",
        )

        assertTrue(queries.isNotEmpty())
        assertEquals(
            "Rua Flores da Primavera, 263, Conjunto Promorar Rio Claro, São Paulo - SP",
            queries.first(),
        )
        assertTrue(queries.contains("Rua Flores da Primavera, 263, São Paulo - SP"))
        assertTrue(queries.all { !it.contains('(') && !it.contains(')') })
        assertTrue(queries.all { it.contains("São Paulo - SP") })
    }

    @Test
    fun candidate_disambiguation_prefers_coordinate_nearest_configured_work_target() {
        val service = GoogleMapsService()
        val target = Coordinate(latitude = -23.5954123, longitude = -46.4797131)
        val far = Coordinate(latitude = -22.90, longitude = -43.20)
        val near = Coordinate(latitude = -23.6000, longitude = -46.4800)

        assertEquals(
            near,
            service.selectNearestGeocodeCandidate0547(listOf(far, near), listOf(target)),
        )
    }

    @Test
    fun resolver_chain_keeps_osm_primary_and_android_geocoder_keyless_fallback() {
        val text = source("src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt")
        assertTrue(text.contains("resolveFreePrimaryOrigin0547(originAddress, destinations)"))
        assertTrue(text.contains("requestNominatimGeocodeCandidates0547"))
        assertTrue(text.contains("platformGeocodingService0547"))
        assertTrue(text.contains("GEOCODE_RESOLUTION_START_0547"))
        assertTrue(text.contains("GEOCODE_ANDROID_FALLBACK_0547"))
        assertTrue(text.contains("GEOCODE_RESOLUTION_FAILED_0547"))
        assertTrue(text.contains("limit=5&countrycodes=br"))
    }

    @Test
    fun no_protected_reading_decision_or_proximity_runtime_is_rewritten_by_0547() {
        val liveRide = source("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
        assertTrue(liveRide.contains("decideFastWorkRegionChecklist13"))
        assertTrue(liveRide.contains("MANUAL_READING_RUNTIME_STAGE43"))
        assertFalse(liveRide.contains("GEOCODE_RESOLUTION_START_0547"))
    }

    @Test
    fun version_is_0547() {
        val build = source("build.gradle.kts")
        assertTrue(build.contains("versionCode = 5839"))
        assertTrue(build.contains("versionName = \"0.1.547\""))
    }
}
''', encoding="utf-8")

print("materialized Rota Certa 0.1.547 geocode resilience")
