package br.com.mapeiaia.rotacerta

import android.content.Context
import app.organicmaps.sdk.OrganicMaps
import app.organicmaps.sdk.downloader.MapManager
import app.organicmaps.sdk.search.SearchEngine
import app.organicmaps.sdk.search.SearchListener
import app.organicmaps.sdk.search.SearchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Motor Organic Maps incorporado ao processo do Rota Certa.
 *
 * Esta camada não abre o aplicativo Organic Maps. Ela chama o SearchEngine nativo
 * diretamente e devolve coordenada para o mesmo pipeline local do FAROL.
 */
object OrganicMapsEmbeddedRuntime0711 {
    const val CONTRACT_MARKER = "ORGANIC_MAPS_EMBEDDED_RUNTIME_0711"
    const val EXACT_UPSTREAM_TAG = "2026.08.27-18-android"
    const val EXACT_SDK_VERSION = "2026.08.27-18"
    const val COMPATIBLE_DATA_VERSION_FOLDER = "260826"

    private val initMutex = Mutex()
    @Volatile private var runtime: OrganicMaps? = null
    @Volatile private var failed = false
    @Volatile private var lastFailure: String? = null

    suspend fun ensureInitialized(context: Context): Boolean = initMutex.withLock {
        runtime?.takeIf { it.arePlatformAndCoreInitialized() }?.let { return true }
        if (failed) return false

        return withContext(Dispatchers.Main.immediate) {
            runCatching {
                val appContext = context.applicationContext
                val created = runtime ?: OrganicMaps(
                    appContext,
                    "google",
                    appContext.packageName,
                    BuildConfig.VERSION_CODE,
                    BuildConfig.VERSION_NAME,
                ).also { runtime = it }
                created.init(Runnable { })
                check(created.arePlatformAndCoreInitialized()) {
                    "Organic Maps framework não confirmou inicialização."
                }
                true
            }.getOrElse { error ->
                failed = true
                lastFailure = error::class.java.simpleName + ":" + error.message.orEmpty().take(180)
                false
            }
        }
    }

    fun downloadedRegionalMapCount(): Int? {
        val active = runtime?.takeIf { it.arePlatformAndCoreInitialized() } ?: return null
        @Suppress("UNUSED_VARIABLE") val keepReference = active
        return runCatching { MapManager.nativeGetDownloadedCount() }.getOrNull()
    }

    fun failureReason(): String? = lastFailure
}

class OrganicMapsOfflineAddressResolver0711(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val searchMutex = Mutex()
    private val requestSerial = AtomicLong(System.nanoTime())

    suspend fun resolve(address: String): Coordinate? {
        val query = address.trim()
        if (query.isBlank()) return null
        if (!OrganicMapsEmbeddedRuntime0711.ensureInitialized(appContext)) return null

        return searchMutex.withLock {
            val timestamp = requestSerial.incrementAndGet()
            val answer = CompletableDeferred<Coordinate?>()
            val listener = object : SearchListener {
                override fun onResultsUpdate(results: Array<SearchResult>, resultTimestamp: Long) {
                    if (resultTimestamp != timestamp || answer.isCompleted) return
                    val selected = OrganicMapsAddressValidation0711.select(query, results.toList())
                    if (selected != null) answer.complete(Coordinate(selected.lat, selected.lon))
                }

                override fun onResultsEnd(resultTimestamp: Long) {
                    if (resultTimestamp == timestamp && !answer.isCompleted) answer.complete(null)
                }
            }

            withContext(Dispatchers.Main.immediate) {
                SearchEngine.INSTANCE.addListener(listener)
            }
            try {
                val started = withContext(Dispatchers.Main.immediate) {
                    SearchEngine.INSTANCE.search(
                        appContext,
                        query,
                        false,
                        timestamp,
                        false,
                        0.0,
                        0.0,
                    )
                }
                if (!started) return@withLock null

                withTimeoutOrNull(900L) { answer.await() }
            } finally {
                withContext(Dispatchers.Main.immediate) {
                    SearchEngine.INSTANCE.removeListener(listener)
                    runCatching { SearchEngine.INSTANCE.cancel() }
                }
            }
        }
    }
}

object OrganicMapsAddressValidation0711 {
    const val CONTRACT_MARKER = "ORGANIC_MAPS_ADDRESS_VALIDATION_0711"

    private val stateNames = mapOf(
        "AC" to "acre",
        "AL" to "alagoas",
        "AP" to "amapa",
        "AM" to "amazonas",
        "BA" to "bahia",
        "CE" to "ceara",
        "DF" to "distrito federal",
        "ES" to "espirito santo",
        "GO" to "goias",
        "MA" to "maranhao",
        "MT" to "mato grosso",
        "MS" to "mato grosso do sul",
        "MG" to "minas gerais",
        "PA" to "para",
        "PB" to "paraiba",
        "PR" to "parana",
        "PE" to "pernambuco",
        "PI" to "piaui",
        "RJ" to "rio de janeiro",
        "RN" to "rio grande do norte",
        "RS" to "rio grande do sul",
        "RO" to "rondonia",
        "RR" to "roraima",
        "SC" to "santa catarina",
        "SP" to "sao paulo",
        "SE" to "sergipe",
        "TO" to "tocantins",
    )

    private val genericTokens = setOf(
        "rua", "avenida", "av", "rodovia", "estrada", "travessa", "alameda",
        "praca", "praça", "numero", "n", "brasil", "bairro",
    ).map(::canonical).toSet()

    fun select(query: String, results: List<SearchResult>): SearchResult? {
        val candidates = results
            .asSequence()
            .filter { it.type == SearchResult.TYPE_RESULT }
            .filter { it.lat.isFinite() && it.lon.isFinite() }
            .filter { it.lat in -90.0..90.0 && it.lon in -180.0..180.0 }
            .map { it to score(query, it) }
            .filter { it.second.accepted }
            .sortedWith(
                compareByDescending<Pair<SearchResult, Score>> { it.second.score }
                    .thenByDescending { it.second.overlapCount },
            )
            .toList()

        if (candidates.isEmpty()) return null
        if (candidates.size >= 2) {
            val first = candidates[0].second
            val second = candidates[1].second
            if (first.score - second.score < 0.08 && first.localityStrength == second.localityStrength) {
                return null
            }
        }
        return candidates.first().first
    }

    data class Score(
        val accepted: Boolean,
        val score: Double,
        val overlapCount: Int,
        val localityStrength: Int,
    )

    internal fun score(query: String, result: SearchResult): Score {
        val normalizedQuery = canonical(query)
        val region = result.description?.region.orEmpty()
        val description = result.description?.description.orEmpty()
        val haystack = canonical(listOf(result.name, region, description).joinToString(" "))

        val state = explicitState(query)
        if (state != null) {
            val stateName = stateNames[state].orEmpty()
            val stateAccepted = containsWhole(haystack, canonical(state)) ||
                stateName.isNotBlank() && haystack.contains(canonical(stateName))
            if (!stateAccepted) return Score(false, 0.0, 0, 0)
        }

        val city = explicitCityBeforeState(query)
        if (!city.isNullOrBlank() && !haystack.contains(canonical(city))) {
            return Score(false, 0.0, 0, 0)
        }

        val queryTokens = normalizedQuery
            .split(' ')
            .filter { it.length >= 3 && it !in genericTokens && it.toIntOrNull() == null }
            .distinct()

        if (queryTokens.isEmpty()) return Score(false, 0.0, 0, 0)

        val overlap = queryTokens.count { containsWhole(haystack, it) }
        val ratio = overlap.toDouble() / queryTokens.size.toDouble()
        val localityStrength = (if (state != null) 1 else 0) + (if (!city.isNullOrBlank()) 1 else 0)
        val minimumOverlap = if (queryTokens.size == 1) 1 else 2
        val accepted = overlap >= minimumOverlap && ratio >= 0.42
        return Score(
            accepted = accepted,
            score = ratio + localityStrength * 0.20,
            overlapCount = overlap,
            localityStrength = localityStrength,
        )
    }

    private fun explicitState(query: String): String? {
        val match = Regex("""(?:-|/|,)\s*([A-Za-z]{2})(?:\s*[\)\]]?)\s*$""")
            .find(query.trim())
            ?: return null
        return match.groupValues[1].uppercase(Locale.ROOT).takeIf(stateNames::containsKey)
    }

    private fun explicitCityBeforeState(query: String): String? {
        val match = Regex("""(?:\(|,)\s*([^,()]+?)\s*(?:-|/)\s*[A-Za-z]{2}(?:\s*[\)\]]?)\s*$""")
            .find(query.trim())
            ?: return null
        return match.groupValues[1]
            .trim()
            .takeIf { canonical(it).length >= 4 }
    }

    private fun containsWhole(haystack: String, token: String): Boolean =
        (" " + haystack + " ").contains(" " + token + " ")

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("""\p{Mn}+"""), "")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}
