package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object BlaBlaPublicSearchIntents {
    const val EXTRA_REQUEST_JSON = "blablacar_public_search_request_json"
    const val EXTRA_RESULT_STATUS = "blablacar_public_search_result_status"

    fun search(context: Context, request: BlaBlaPublicSearchRequest): Intent =
        Intent(context, BlaBlaPublicSearchActivity::class.java)
            .putExtra(EXTRA_REQUEST_JSON, Json.encodeToString(BlaBlaPublicSearchRequest.serializer(), request))
}

@Serializable
private data class PublicRenderedCard(
    val driverName: String = "",
    val departureTime: String? = null,
    val arrivalTime: String? = null,
    val actualDeparture: String? = null,
    val actualArrival: String? = null,
    val priceText: String? = null,
    val ratingText: String? = null,
    val seatsText: String? = null,
    val text: String = "",
    val href: String? = null,
)

@Serializable
private data class PublicRenderedPage(
    val bodyText: String = "",
    val cards: List<PublicRenderedCard> = emptyList(),
)

class BlaBlaPublicSearchActivity : Activity() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var statusView: TextView
    private lateinit var webView: WebView
    private lateinit var store: BlaBlaPublicSearchStore
    private lateinit var request: BlaBlaPublicSearchRequest
    private lateinit var browserScripts: BlaBlaBrowserScriptRegistry
    private val browserOrchestrator = BlaBlaBrowserOrchestrator()
    private var tasks: List<BlaBlaPublicSearchTask> = emptyList()
    private var taskIndex = 0
    private var generation = 0L
    private var capturedGeneration = Long.MIN_VALUE
    private val matches = mutableListOf<BlaBlaPublicSearchCard>()
    private val queries = mutableListOf<BlaBlaPublicSearchQueryResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BlaBlaPublicSearchStore(this)
        browserScripts = BlaBlaBrowserScriptRegistry(this)
        request = runCatching {
            intent.getStringExtra(BlaBlaPublicSearchIntents.EXTRA_REQUEST_JSON)
                ?.let { json.decodeFromString<BlaBlaPublicSearchRequest>(it) }
        }.getOrNull() ?: run {
            finishWithError("Consulta pública inválida.")
            return
        }
        tasks = BlaBlaPublicSearchPlanner.tasks(request)
        if (tasks.isEmpty()) {
            finishWithError("Informe uma data AAAA-MM-DD ou um mês AAAA-MM válido.")
            return
        }
        val unsupported = tasks
            .flatMap { listOf(it.from, it.to) }
            .firstOrNull { !BlaBlaPublicPlaceDirectory.supported(it) }
        if (unsupported != null) {
            finishWithError("Cidade ainda não reconhecida pela Consulta Pública: $unsupported")
            return
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            finishWithError("O WebView deste aparelho não oferece perfil isolado para a Consulta Pública.")
            return
        }
        createUi()
        store.saveRequest(request)
        UnifiedDebugEventStore.record(
            "PUBLIC_SEARCH_STARTED",
            packageName,
            "period=${request.period} targets=${request.targetNames.size} tasks=${tasks.size} reverse=${request.includeReverse} isolated=true",
        )
        loadCurrentTask()
    }

    private fun createUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        statusView = TextView(this).apply {
            text = "Consulta Pública"
            setPadding(24, 18, 24, 18)
        }
        root.addView(
            statusView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        webView = WebView(this)
        WebViewCompat.setProfile(webView, PUBLIC_PROFILE)
        WebViewCompat.getProfile(webView).cookieManager.apply {
            setAcceptCookie(true)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(webView, true)
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString().orEmpty()
                return target.isNotBlank() && !isAllowedPublicUrl(target)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val target = url.orEmpty()
                return target.isNotBlank() && !isAllowedPublicUrl(target)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                val expectedGeneration = generation
                view.postDelayed({
                    if (expectedGeneration == generation && taskIndex < tasks.size) {
                        captureCurrentPage(expectedGeneration)
                    }
                }, PAGE_SETTLE_MS)
            }
        }
        root.addView(
            webView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(root)
    }

    private fun loadCurrentTask() {
        val task = tasks.getOrNull(taskIndex) ?: run {
            complete()
            return
        }
        val url = BlaBlaPublicPlaceDirectory.searchUrl(task) ?: run {
            recordFailure(task, "unsupported_place", "Origem/destino não resolvidas.")
            advance()
            return
        }
        generation++
        browserOrchestrator.start(
            BlaBlaBrowserRequest.PUBLIC_SEARCH_FORM,
            publicBrowserContext(),
            reason = "load_exact_public_search_url",
        )
        statusView.text = "Consulta Pública • ${taskIndex + 1}/${tasks.size}\n${task.date} • ${task.from} → ${task.to}"
        webView.loadUrl(url)
    }

    private fun captureCurrentPage(expectedGeneration: Long) {
        if (expectedGeneration != generation || capturedGeneration == expectedGeneration) return
        capturedGeneration = expectedGeneration
        val task = tasks.getOrNull(taskIndex) ?: return
        val token = browserOrchestrator.start(
            BlaBlaBrowserRequest.PUBLIC_SEARCH_RESULTS,
            publicBrowserContext(),
            reason = "capture_public_results",
        )
        val script = browserScripts.script(BlaBlaBrowserRequest.PUBLIC_SEARCH_RESULTS)
        webView.evaluateJavascript(script) { raw ->
            if (
                expectedGeneration != generation ||
                taskIndex >= tasks.size ||
                !browserOrchestrator.isCurrent(token, publicBrowserContext())
            ) return@evaluateJavascript
            val evidence = decodePage(raw)
            if (evidence == null) {
                recordFailure(task, "parse_error", "Não foi possível interpretar a página pública.")
                advance()
                return@evaluateJavascript
            }
            val currentUrl = webView.url.orEmpty()
            val exact = exactSearchUrl(currentUrl, task)
            val zeroResults = ZERO_RESULTS.any { marker ->
                evidence.bodyText.contains(marker, ignoreCase = true)
            }
            val visibleCards = evidence.cards.filter { it.driverName.isNotBlank() }
            val contentConfirmed = zeroResults || visibleCards.isNotEmpty()
            val status = if (exact && contentConfirmed) "validated" else "mismatch"
            val targetMatches = visibleCards.filter {
                BlaBlaPublicSearchPlanner.matchesTarget(it.driverName, request.targetNames)
            }
            queries += BlaBlaPublicSearchQueryResult(
                date = task.date.toString(),
                from = task.from,
                to = task.to,
                status = status,
                cardCount = visibleCards.size,
                zeroResultsConfirmed = zeroResults,
                error = if (status == "validated") null else "A página final não confirmou rota/data/conteúdo.",
            )
            if (status == "validated") {
                targetMatches.forEach { card -> matches += card.toPublicCard(task) }
            }
            UnifiedDebugEventStore.record(
                "PUBLIC_SEARCH_QUERY",
                packageName,
                "index=${taskIndex + 1}/${tasks.size} date=${task.date} status=$status cards=${visibleCards.size} targetMatches=${targetMatches.size} zero=$zeroResults",
            )
            advance()
        }
    }

    private fun advance() {
        taskIndex++
        if (taskIndex >= tasks.size) complete() else loadCurrentTask()
    }

    private fun recordFailure(task: BlaBlaPublicSearchTask, status: String, error: String) {
        queries += BlaBlaPublicSearchQueryResult(
            date = task.date.toString(),
            from = task.from,
            to = task.to,
            status = status,
            error = error,
        )
        UnifiedDebugEventStore.record(
            "PUBLIC_SEARCH_QUERY",
            packageName,
            "index=${taskIndex + 1}/${tasks.size} date=${task.date} status=$status error=true",
        )
    }

    private fun complete() {
        val status = when {
            queries.isNotEmpty() && queries.all { it.status == "validated" } -> "validated"
            queries.any { it.status == "validated" } -> "partial"
            else -> "error"
        }
        val response = BlaBlaPublicSearchResponse(
            status = status,
            request = request,
            cards = matches.distinctBy {
                listOf(it.driverName, it.date, it.departureTime, it.searchFrom, it.searchTo, it.tripHref)
                    .joinToString("|")
            },
            queries = queries.toList(),
        )
        store.saveResponse(response)
        browserOrchestrator.cancel()
        UnifiedDebugEventStore.record(
            "PUBLIC_SEARCH_COMPLETED",
            packageName,
            "status=$status tasks=${queries.size} validated=${response.validatedQueries} failed=${response.failedQueries} matches=${response.cards.size}",
        )
        setResult(
            if (status == "error") RESULT_CANCELED else RESULT_OK,
            Intent().putExtra(BlaBlaPublicSearchIntents.EXTRA_RESULT_STATUS, status),
        )
        finish()
    }

    private fun finishWithError(message: String) {
        browserOrchestrator.cancel()
        if (::store.isInitialized && ::request.isInitialized) {
            store.saveResponse(BlaBlaPublicSearchResponse(status = "error", request = request))
        }
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(BlaBlaPublicSearchIntents.EXTRA_RESULT_STATUS, "error"),
        )
        if (::statusView.isInitialized) statusView.text = message
        finish()
    }

    private fun publicBrowserContext(): BlaBlaBrowserExecutionContext = BlaBlaBrowserExecutionContext(
        accountId = PUBLIC_PROFILE,
        syncGeneration = generation,
        navigationGeneration = generation,
        tripId = "public-task-$taskIndex",
        url = if (::webView.isInitialized) webView.url.orEmpty() else "",
    )

    private fun decodePage(raw: String?): PublicRenderedPage? = runCatching {
        val encoded = raw?.takeIf { it != "null" } ?: return@runCatching null
        val decoded = json.decodeFromString<String>(encoded)
        json.decodeFromString<PublicRenderedPage>(decoded)
    }.getOrNull()

    private fun exactSearchUrl(raw: String, task: BlaBlaPublicSearchTask): Boolean {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", true) || !uri.host.equals("www.blablacar.com.br", true)) return false
        val query = uri.rawQuery.orEmpty().split('&').associate { part ->
            val key = URLDecoder.decode(part.substringBefore('='), StandardCharsets.UTF_8.name())
            val value = URLDecoder.decode(part.substringAfter('=', ""), StandardCharsets.UTF_8.name())
            key to value
        }
        return query["db"] == task.date.toString() &&
            BlaBlaPublicSearchPlanner.normalizePlace(query["fn"].orEmpty()) ==
            BlaBlaPublicSearchPlanner.normalizePlace(task.from) &&
            BlaBlaPublicSearchPlanner.normalizePlace(query["tn"].orEmpty()) ==
            BlaBlaPublicSearchPlanner.normalizePlace(task.to)
    }

    private fun PublicRenderedCard.toPublicCard(task: BlaBlaPublicSearchTask): BlaBlaPublicSearchCard {
        val normalizedText = text.lowercase()
        val flags = buildList {
            if ("cheio" in normalizedText) add("Cheio")
            if ("esgotará em breve" in normalizedText || "esgotara em breve" in normalizedText) add("Esgotará em breve")
            if ("super driver" in normalizedText) add("Super Driver")
            if ("perfil verificado" in normalizedText) add("Perfil Verificado")
        }
        val availability = when {
            flags.any { it == "Cheio" } -> "full"
            flags.any { it == "Esgotará em breve" } -> "scarce"
            else -> "available_or_unspecified"
        }
        return BlaBlaPublicSearchCard(
            driverName = driverName,
            date = task.date.toString(),
            searchFrom = task.from,
            searchTo = task.to,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            actualDeparture = actualDeparture,
            actualArrival = actualArrival,
            price = cleanPrice(priceText),
            duration = publicTripDuration(departureTime, arrivalTime),
            driverRating = cleanRating(ratingText, text),
            availableSeats = cleanAvailableSeats(seatsText, text),
            flags = flags,
            availability = availability,
            tripHref = href?.let { if (it.startsWith('/')) "https://www.blablacar.com.br$it" else it },
        )
    }

    private fun cleanPrice(raw: String?): String? = raw?.replace('\u00a0', ' ')
        ?.let { Regex("R\\$\\s*[0-9.]+(?:,[0-9]{2})?", RegexOption.IGNORE_CASE).find(it)?.value }
        ?.replace(Regex("\\s+"), " ")
        ?.trim()

    private fun cleanRating(raw: String?, fallback: String): String? {
        fun normalizedRating(value: String): String? = Regex("([0-5](?:[.,]\\d)?)")
            .find(value)?.groupValues?.getOrNull(1)?.replace(',', '.')
        raw?.trim()?.takeIf(String::isNotEmpty)?.let(::normalizedRating)?.let { return it }
        val marked = Regex(
            "(?:avalia[cç][aã]o|rating|★|⭐)\\s*[:=-]?\\s*([0-5](?:[.,]\\d)?)|([0-5](?:[.,]\\d)?)\\s*(?:/\\s*5|★|⭐)",
            RegexOption.IGNORE_CASE,
        ).find(fallback) ?: return null
        return marked.groupValues.drop(1).firstOrNull(String::isNotBlank)?.replace(',', '.')
    }

    private fun cleanAvailableSeats(raw: String?, fallback: String): Int? {
        val source = listOfNotNull(raw, fallback).joinToString(" ")
        return Regex("(\\d+)\\s*(?:vaga|vagas|lugar|lugares)\\s*(?:dispon[ií]vel|dispon[ií]veis)?", RegexOption.IGNORE_CASE)
            .find(source)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun publicTripDuration(departure: String?, arrival: String?): String? {
        val start = parsePublicClock(departure) ?: return null
        val end = parsePublicClock(arrival) ?: return null
        var minutes = end - start
        if (minutes < 0) minutes += 24 * 60
        if (minutes <= 0) return null
        val hours = minutes / 60
        val rest = minutes % 60
        return buildString {
            if (hours > 0) append("${hours}h")
            if (rest > 0) {
                if (isNotEmpty()) append(' ')
                append("${rest}min")
            }
        }
    }

    private fun parsePublicClock(raw: String?): Int? {
        val match = Regex("(\\d{1,2}):(\\d{2})").find(raw.orEmpty()) ?: return null
        val h = match.groupValues[1].toIntOrNull() ?: return null
        val m = match.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun isAllowedPublicUrl(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        uri.scheme.equals("https", true) && uri.host.equals("www.blablacar.com.br", true)
    }.getOrDefault(false)

    companion object {
        private const val PUBLIC_PROFILE = "rota_certa_blablacar_public_search"
        private const val PAGE_SETTLE_MS = 3_500L
        private val ZERO_RESULTS = listOf(
            "Ainda não existem viagens entre essas cidades",
            "0 viagem disponível",
            "Nenhuma viagem disponível",
        )

    }
}
