package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.DiagnosticEventContext0507
import br.com.mapeiaia.rotacerta.DiagnosticModule0507
import br.com.mapeiaia.rotacerta.DiagnosticSeverity0507
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceError
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
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
    val cardIndex: Int = -1,
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
    val profileHrefs: List<String> = emptyList(),
    val verifiedProfileUuid: String? = null,
    val profileUuidEvidence: String? = null,
    val identityState: String = "PENDING_UNKNOWN",
)

@Serializable
private data class PublicRenderedPage(
    val bodyText: String = "",
    val cards: List<PublicRenderedCard> = emptyList(),
    val atBottom: Boolean = false,
    val loadingIndicatorPresent: Boolean = false,
    val scrollHeight: Int = 0,
)

@Serializable
private data class PublicScrollResult(
    val beforeY: Int = 0,
    val afterY: Int = 0,
    val scrollHeight: Int = 0,
)

@Serializable
private data class PublicNavigationResult0507(
    val found: Boolean = false,
    val clicked: Boolean = false,
    val href: String = "",
)

@Serializable
private data class PublicDriverProfileEvidence0507(
    val name: String = "",
    val photoUrl: String = "",
    val rating: String = "",
    val reviewCount: Int? = null,
    val bodyText: String = "",
    val url: String = "",
    val profileHrefs: List<String> = emptyList(),
)

private data class PendingQueryFinalize0507(
    val task: BlaBlaPublicSearchTask,
    val coverageStatus: String,
    val exact: Boolean,
    val zeroResults: Boolean,
    val terminal: Boolean,
    val bodyText: String,
    val errorStage: String?,
    val exceptionClass: String?,
    val exceptionMessage: String?,
)

private enum class PublicIdentityEnrichmentStage0507 {
    NONE,
    WAIT_TRIP,
    WAIT_PROFILE,
    WAIT_SEARCH,
}

@Serializable
private data class PublicSearchCheckpoint(
    val collectionId: String,
    val taskIndex: Int,
    val cards: List<BlaBlaPublicSearchCard>,
    val queries: List<BlaBlaPublicSearchQueryResult>,
    val demands: List<BlaBlaPublicSearchDemand>,
)

private object BlaBlaPublicSearchSingleFlight {
    private val activeCollection = AtomicReference<String?>(null)

    fun acquire(collectionId: String): Boolean {
        if (collectionId.isBlank()) return true
        val current = activeCollection.get()
        return current == collectionId || activeCollection.compareAndSet(null, collectionId)
    }

    fun release(collectionId: String) {
        if (collectionId.isNotBlank()) activeCollection.compareAndSet(collectionId, null)
    }
}

class BlaBlaPublicSearchActivity : Activity() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var statusView: TextView
    private lateinit var webView: WebView
    private lateinit var store: BlaBlaPublicSearchStore
    private lateinit var request: BlaBlaPublicSearchRequest
    private val browserOrchestrator = BlaBlaBrowserOrchestrator()
    private var tasks: List<BlaBlaPublicSearchTask> = emptyList()
    private var taskIndex = 0
    private var generation = 0L
    private var capturePass = 0
    private var captureInFlight = false
    private var queryStartedAtMillis = 0L
    private val taskCards = mutableListOf<PublicRenderedCard>()
    private val matches = mutableListOf<BlaBlaPublicSearchCard>()
    private val queries = mutableListOf<BlaBlaPublicSearchQueryResult>()
    private val demands = mutableListOf<BlaBlaPublicSearchDemand>()
    private var identityEnrichmentStage0507 = PublicIdentityEnrichmentStage0507.NONE
    private var identityQueue0507: List<Int> = emptyList()
    private var identityQueuePosition0507 = 0
    private var identitySearchUrl0507 = ""
    private var pendingFinalize0507: PendingQueryFinalize0507? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BlaBlaPublicSearchStore(this)
        request = runCatching {
            intent.getStringExtra(BlaBlaPublicSearchIntents.EXTRA_REQUEST_JSON)
                ?.let { json.decodeFromString<BlaBlaPublicSearchRequest>(it) }
        }.getOrNull() ?: run {
            finishWithError("Consulta pública inválida.")
            return
        }
        request = request.copy(includeReverse = true)
        tasks = BlaBlaPublicSearchPlanner.tasks(request)
        if (tasks.isEmpty()) {
            finishWithError("Nenhuma data futura está disponível para esta consulta.")
            return
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            finishWithError("O WebView deste aparelho não oferece perfil isolado para a Consulta Pública.")
            return
        }
        if (!BlaBlaPublicSearchSingleFlight.acquire(request.collectionId)) {
            finishWithError("Já existe uma coleta pública em execução.")
            return
        }
        createUi()
        store.clearResponse()
        store.saveRequest(request)
        restoreCheckpoint(savedInstanceState)
        UnifiedDebugEventStore.record(
            "PUBLIC_SEARCH_STARTED",
            packageName,
            "collectionIdPresent=${request.collectionId.isNotBlank()} period=${request.period.ifBlank { "visual_dates" }} selectedDates=${request.selectedDates.size} tasks=${tasks.size} reverse=true demand=${request.captureDemand} isolated=true allCards=true",
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
                    if (expectedGeneration != generation || taskIndex >= tasks.size || captureInFlight) return@postDelayed
                    if (identityEnrichmentStage0507 != PublicIdentityEnrichmentStage0507.NONE) {
                        continueIdentityEnrichment0507(expectedGeneration)
                    } else {
                        captureCurrentPage(expectedGeneration)
                    }
                }, PAGE_SETTLE_MS)
            }

            override fun onReceivedError(
                view: WebView,
                requestError: WebResourceRequest,
                error: WebResourceError,
            ) {
                super.onReceivedError(view, requestError, error)
                if (!requestError.isForMainFrame || taskIndex >= tasks.size) return
                val task = tasks[taskIndex]
                if (!exactSearchUrl(requestError.url.toString(), task)) return
                finalizeQuery(
                    task = task,
                    coverageStatus = "FAILED",
                    exact = false,
                    zeroResults = false,
                    terminal = false,
                    errorStage = "MAIN_FRAME_NAVIGATION",
                    exceptionClass = "WebResourceError",
                    exceptionMessage = "Falha de navegação da consulta pública.",
                )
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
        capturePass = 0
        captureInFlight = false
        taskCards.clear()
        queryStartedAtMillis = System.currentTimeMillis()
        browserOrchestrator.start(
            BlaBlaBrowserRequest.PUBLIC_SEARCH_FORM,
            publicBrowserContext(),
            reason = "load_exact_public_search_url",
        )
        statusView.text = "Consulta Pública • ${taskIndex + 1}/${tasks.size}\n${task.date} • ${task.from} → ${task.to}"
        webView.loadUrl(url)
    }

    private fun captureCurrentPage(expectedGeneration: Long) {
        if (expectedGeneration != generation || captureInFlight || taskIndex >= tasks.size) return
        val task = tasks.getOrNull(taskIndex) ?: return
        captureInFlight = true
        capturePass++
        browserOrchestrator.executeCollectionStep(
            androidContext = this,
            webView = webView,
            request = BlaBlaBrowserRequest.PUBLIC_SEARCH_RESULTS,
            executionContext = publicBrowserContext(),
            currentContext = ::publicBrowserContext,
            deserializer = PublicRenderedPage.serializer(),
            reason = "capture_public_results",
        ) { evidence ->
            captureInFlight = false
            if (expectedGeneration != generation || taskIndex >= tasks.size) {
                return@executeCollectionStep
            }
            if (evidence == null) {
                finalizeQuery(
                    task = task,
                    coverageStatus = if (taskCards.isEmpty()) "PENDING_UNKNOWN" else "PARTIAL",
                    exact = exactSearchUrl(webView.url.orEmpty(), task),
                    zeroResults = false,
                    terminal = false,
                    errorStage = "RESULT_CAPTURE",
                    exceptionClass = "CaptureUnavailable",
                    exceptionMessage = "A leitura pública não produziu evidência suficiente.",
                )
                return@executeCollectionStep
            }
            val currentUrl = webView.url.orEmpty()
            val exact = exactSearchUrl(currentUrl, task)
            val zeroResults = ZERO_RESULTS.any { marker ->
                evidence.bodyText.contains(marker, ignoreCase = true)
            }
            evidence.cards
                .filter { it.driverName.isNotBlank() || !it.href.isNullOrBlank() }
                .forEach { incoming ->
                    val key = renderedCardKey(incoming)
                    val existing = taskCards.indexOfFirst { renderedCardKey(it) == key }
                    if (existing < 0) taskCards += incoming else taskCards[existing] = incoming
                }
            val terminal = evidence.atBottom && !evidence.loadingIndicatorPresent
            val shouldProbeAgain = exact && !zeroResults && !terminal && capturePass < MAX_CAPTURE_PASSES
            if (shouldProbeAgain) {
                scrollAndRecapture(expectedGeneration)
                return@executeCollectionStep
            }
            val coverage = when {
                !exact -> "FAILED"
                zeroResults -> "COMPLETE"
                taskCards.isNotEmpty() && terminal -> "COMPLETE"
                taskCards.isNotEmpty() -> "PARTIAL"
                else -> "PENDING_UNKNOWN"
            }
            val diagnostic = when (coverage) {
                "FAILED" -> Triple(
                    "QUERY_VALIDATION",
                    "RouteOrDateMismatch",
                    "A página final não confirmou exatamente rota e data.",
                )
                "PARTIAL" -> Triple(
                    "PAGE_INCOMPLETE",
                    "IncompletePublicResultSet",
                    "Cards foram obtidos, mas a página não forneceu evidência terminal de cobertura completa.",
                )
                "PENDING_UNKNOWN" -> Triple(
                    "EVIDENCE_INCONCLUSIVE",
                    "InconclusivePublicSearch",
                    "A consulta terminou sem evidência suficiente para afirmar presença ou ausência de viagens.",
                )
                else -> null
            }
            finalizeQuery(
                task = task,
                coverageStatus = coverage,
                exact = exact,
                zeroResults = zeroResults,
                terminal = terminal || zeroResults,
                bodyText = evidence.bodyText,
                errorStage = diagnostic?.first,
                exceptionClass = diagnostic?.second,
                exceptionMessage = diagnostic?.third,
            )
        }
    }

    private fun scrollAndRecapture(expectedGeneration: Long) {
        if (expectedGeneration != generation || captureInFlight || taskIndex >= tasks.size) return
        captureInFlight = true
        browserOrchestrator.executeCollectionStep(
            androidContext = this,
            webView = webView,
            request = BlaBlaBrowserRequest.PUBLIC_SEARCH_SCROLL,
            executionContext = publicBrowserContext(),
            currentContext = ::publicBrowserContext,
            deserializer = PublicScrollResult.serializer(),
            reason = "public_search_scroll_pass_$capturePass",
        ) {
            captureInFlight = false
            if (expectedGeneration == generation && taskIndex < tasks.size) {
                webView.postDelayed({ captureCurrentPage(expectedGeneration) }, SCROLL_SETTLE_MS)
            }
        }
    }

    private fun renderedCardKey(card: PublicRenderedCard): String =
        listOf(
            card.href.orEmpty(),
            card.driverName,
            card.departureTime.orEmpty(),
            card.actualDeparture.orEmpty(),
            card.actualArrival.orEmpty(),
            card.cardIndex.toString(),
        ).joinToString("|")

    private fun finalizeQuery(
        task: BlaBlaPublicSearchTask,
        coverageStatus: String,
        exact: Boolean,
        zeroResults: Boolean,
        terminal: Boolean,
        bodyText: String = "",
        errorStage: String? = null,
        exceptionClass: String? = null,
        exceptionMessage: String? = null,
        allowIdentityEnrichment0507: Boolean = true,
    ) {
        if (taskIndex >= tasks.size) return
        if (
            allowIdentityEnrichment0507 &&
            beginIdentityEnrichment0507(
                PendingQueryFinalize0507(
                    task = task,
                    coverageStatus = coverageStatus,
                    exact = exact,
                    zeroResults = zeroResults,
                    terminal = terminal,
                    bodyText = bodyText,
                    errorStage = errorStage,
                    exceptionClass = exceptionClass,
                    exceptionMessage = exceptionMessage,
                ),
            )
        ) return
        val queryId = publicSearchQueryId(request, task)
        val direction = publicSearchDirectionName(request, task)
        val demand = if (exact && bodyText.isNotBlank()) {
            publicSearchDemandFor(request = request, task = task, bodyText = bodyText)
        } else null
        demand?.let { next ->
            demands.removeAll { it.date == next.date && it.from == next.from && it.to == next.to }
            demands += next
        }
        val capturedAt = System.currentTimeMillis()
        taskCards.forEachIndexed { index, card ->
            matches += card.toPublicCard(task, queryId, direction, index, capturedAt)
        }
        queries += BlaBlaPublicSearchQueryResult(
            date = task.date.toString(),
            from = task.from,
            to = task.to,
            status = if (coverageStatus == "COMPLETE") "validated" else coverageStatus.lowercase(),
            cardCount = taskCards.size,
            zeroResultsConfirmed = coverageStatus == "COMPLETE" && zeroResults,
            error = exceptionMessage,
            queryId = queryId,
            direction = direction,
            coverageStatus = coverageStatus,
            startedAtMillis = queryStartedAtMillis,
            finishedAtMillis = capturedAt,
            evidence = BlaBlaPublicSearchQueryEvidence(
                requestedDateConfirmed = exact,
                requestedRouteConfirmed = exact,
                terminalEvidence = terminal,
                stableAtBottom = terminal && !zeroResults,
            ),
            errorDetail = errorStage?.let {
                BlaBlaPublicSearchErrorDetail(
                    stage = it,
                    exceptionClass = exceptionClass,
                    exceptionMessage = exceptionMessage,
                )
            },
        )
        UnifiedDebugEventStore.record(
            "PUBLIC_SEARCH_QUERY",
            packageName,
            "index=${taskIndex + 1}/${tasks.size} queryId=$queryId direction=$direction date=${task.date} coverage=$coverageStatus cards=${taskCards.size} zero=$zeroResults terminal=$terminal allCards=true",
        )
        persistCheckpoint()
        advance()
    }

    private fun beginIdentityEnrichment0507(pending: PendingQueryFinalize0507): Boolean {
        if (identityEnrichmentStage0507 != PublicIdentityEnrichmentStage0507.NONE) return true
        val queue = taskCards.indices.filter { index ->
            val card = taskCards[index]
            val directUuid = strongPublicProfileUuid(card.profileHrefs)
            directUuid == null &&
                card.verifiedProfileUuid.isNullOrBlank() &&
                expectedProfileUuids0507(card.driverName).isNotEmpty()
        }
        if (queue.isEmpty()) return false

        pendingFinalize0507 = pending
        identityQueue0507 = queue
        identityQueuePosition0507 = 0
        identitySearchUrl0507 = webView.url.orEmpty()
            .takeIf { exactSearchUrl(it, pending.task) }
            ?: BlaBlaPublicPlaceDirectory.searchUrl(pending.task).orEmpty()
        identityEnrichmentStage0507 = PublicIdentityEnrichmentStage0507.WAIT_SEARCH
        recordIdentityEvent0507(
            stage = "BLABLACAR_PUBLIC_IDENTITY_ENRICHMENT_STARTED",
            card = taskCards[queue.first()],
            result = "STARTED",
            reason = "public_card_missing_profile_uuid",
        )
        openNextIdentityCandidate0507()
        return true
    }

    private fun continueIdentityEnrichment0507(expectedGeneration: Long) {
        if (expectedGeneration != generation || taskIndex >= tasks.size || captureInFlight) return
        when (identityEnrichmentStage0507) {
            PublicIdentityEnrichmentStage0507.WAIT_TRIP -> openPublicDriverProfile0507()
            PublicIdentityEnrichmentStage0507.WAIT_PROFILE -> capturePublicDriverProfile0507()
            PublicIdentityEnrichmentStage0507.WAIT_SEARCH -> openNextIdentityCandidate0507()
            PublicIdentityEnrichmentStage0507.NONE -> Unit
        }
    }

    private fun openNextIdentityCandidate0507() {
        val pending = pendingFinalize0507 ?: return
        if (identityQueuePosition0507 >= identityQueue0507.size) {
            finishIdentityEnrichment0507()
            return
        }
        val index = identityQueue0507[identityQueuePosition0507]
        val card = taskCards.getOrNull(index) ?: run {
            identityQueuePosition0507++
            openNextIdentityCandidate0507()
            return
        }
        val resultIndex = card.cardIndex.takeIf { it >= 0 } ?: index
        identityEnrichmentStage0507 = PublicIdentityEnrichmentStage0507.WAIT_TRIP
        captureInFlight = true
        recordIdentityEvent0507(
            stage = "BLABLACAR_PUBLIC_CANDIDATE_FOUND",
            card = card,
            result = "PENDING",
            reason = "name_discovery_only_requires_profile_uuid",
        )
        browserOrchestrator.executeCollectionStep(
            androidContext = this,
            webView = webView,
            request = BlaBlaBrowserRequest.PUBLIC_RESULT_OPEN,
            executionContext = publicBrowserContext(),
            currentContext = ::publicBrowserContext,
            deserializer = PublicNavigationResult0507.serializer(),
            arguments = mapOf("RESULT_INDEX" to resultIndex.toString()),
            reason = "identity_enrichment_open_public_result",
        ) { nav ->
            captureInFlight = false
            if (nav?.clicked != true) {
                markIdentityPending0507(card, "PUBLIC_RESULT_OPEN_FAILED")
                returnToSearchForNextIdentity0507()
            }
        }
    }

    private fun openPublicDriverProfile0507() {
        val index = identityQueue0507.getOrNull(identityQueuePosition0507) ?: return
        val card = taskCards.getOrNull(index) ?: return
        identityEnrichmentStage0507 = PublicIdentityEnrichmentStage0507.WAIT_PROFILE
        captureInFlight = true
        browserOrchestrator.executeCollectionStep(
            androidContext = this,
            webView = webView,
            request = BlaBlaBrowserRequest.PUBLIC_DRIVER_PROFILE_OPEN,
            executionContext = publicBrowserContext(),
            currentContext = ::publicBrowserContext,
            deserializer = PublicNavigationResult0507.serializer(),
            arguments = mapOf("DRIVER_NAME" to json.encodeToString(card.driverName)),
            reason = "identity_enrichment_open_public_profile",
        ) { nav ->
            captureInFlight = false
            if (nav?.clicked == true) {
                recordIdentityEvent0507(
                    stage = "BLABLACAR_PUBLIC_PROFILE_OPENED",
                    card = card,
                    result = "SUCCEEDED",
                )
            } else {
                markIdentityPending0507(card, "PUBLIC_DRIVER_PROFILE_OPEN_FAILED")
                returnToSearchForNextIdentity0507()
            }
        }
    }

    private fun capturePublicDriverProfile0507() {
        val index = identityQueue0507.getOrNull(identityQueuePosition0507) ?: return
        val card = taskCards.getOrNull(index) ?: return
        captureInFlight = true
        browserOrchestrator.executeCollectionStep(
            androidContext = this,
            webView = webView,
            request = BlaBlaBrowserRequest.PUBLIC_DRIVER_PROFILE,
            executionContext = publicBrowserContext(),
            currentContext = ::publicBrowserContext,
            deserializer = PublicDriverProfileEvidence0507.serializer(),
            reason = "identity_enrichment_capture_profile_uuid",
        ) { evidence ->
            captureInFlight = false
            val observed = evidence?.let {
                BlaBlaCollectorIdentityModule.uuids(
                    (listOf(it.url) + it.profileHrefs).filter(String::isNotBlank),
                )
            }.orEmpty()
            val resolution = BlaBlaPublicSearchPlanner.resolvePublicProfileIdentity0507(
                expectedProfileUuids = expectedProfileUuids0507(card.driverName),
                observedProfileUuids = observed,
            )
            taskCards[index] = card.copy(
                verifiedProfileUuid = resolution.profileUuid,
                profileUuidEvidence = resolution.profileUuid?.let { "PUBLIC_PROFILE_OPEN_UUID" },
                identityState = resolution.state,
            )
            when (resolution.state) {
                "CONFIRMED_STRONG_IDENTITY" -> recordIdentityEvent0507(
                    stage = "BLABLACAR_PUBLIC_PROFILE_UUID_CONFIRMED",
                    card = taskCards[index],
                    result = "CONFIRMED",
                    reason = resolution.reasonCode,
                )
                "IDENTITY_CONFLICT" -> recordIdentityEvent0507(
                    stage = "BLABLACAR_PUBLIC_IDENTITY_CONFLICT",
                    card = taskCards[index],
                    result = "FAILED",
                    errorCode = "PUBLIC_PROFILE_IDENTITY_CONFLICT",
                    reason = resolution.reasonCode,
                    severity = DiagnosticSeverity0507.WARNING,
                )
                else -> markIdentityPending0507(taskCards[index], resolution.reasonCode)
            }
            returnToSearchForNextIdentity0507()
        }
    }

    private fun markIdentityPending0507(card: PublicRenderedCard, reason: String) {
        val index = identityQueue0507.getOrNull(identityQueuePosition0507)
        if (index != null && index in taskCards.indices) {
            taskCards[index] = card.copy(identityState = "PENDING_IDENTITY_ENRICHMENT")
        }
        recordIdentityEvent0507(
            stage = "BLABLACAR_PUBLIC_IDENTITY_PENDING",
            card = card,
            result = "PENDING",
            reason = reason,
            severity = DiagnosticSeverity0507.WARNING,
        )
    }

    private fun returnToSearchForNextIdentity0507() {
        identityQueuePosition0507++
        if (identityQueuePosition0507 >= identityQueue0507.size) {
            finishIdentityEnrichment0507()
            return
        }
        identityEnrichmentStage0507 = PublicIdentityEnrichmentStage0507.WAIT_SEARCH
        if (identitySearchUrl0507.isBlank()) {
            finishIdentityEnrichment0507()
        } else {
            webView.loadUrl(identitySearchUrl0507)
        }
    }

    private fun finishIdentityEnrichment0507() {
        val pending = pendingFinalize0507 ?: return
        identityEnrichmentStage0507 = PublicIdentityEnrichmentStage0507.NONE
        identityQueue0507 = emptyList()
        identityQueuePosition0507 = 0
        identitySearchUrl0507 = ""
        pendingFinalize0507 = null
        finalizeQuery(
            task = pending.task,
            coverageStatus = pending.coverageStatus,
            exact = pending.exact,
            zeroResults = pending.zeroResults,
            terminal = pending.terminal,
            bodyText = pending.bodyText,
            errorStage = pending.errorStage,
            exceptionClass = pending.exceptionClass,
            exceptionMessage = pending.exceptionMessage,
            allowIdentityEnrichment0507 = false,
        )
    }

    private fun expectedProfileUuids0507(driverName: String): Set<String> {
        val wanted = BlaBlaPublicSearchPlanner.normalizePerson(driverName)
        if (wanted.isBlank()) return emptySet()
        return BlaBlaDynamicAccountRegistry(this).list()
            .filter { account ->
                val display = account.profileName?.trim().takeUnless { it.isNullOrBlank() } ?: account.displayLabel
                BlaBlaPublicSearchPlanner.normalizePerson(display) == wanted
            }
            .mapNotNull { it.profileUuid?.trim()?.lowercase()?.takeIf(String::isNotBlank) }
            .toSet()
    }

    private fun recordIdentityEvent0507(
        stage: String,
        card: PublicRenderedCard,
        result: String,
        errorCode: String = "",
        reason: String = "",
        severity: DiagnosticSeverity0507 = DiagnosticSeverity0507.INFO,
    ) {
        UnifiedDebugEventStore.recordAlways(
            stage,
            packageName,
            "tripIdPresent=${!card.href?.let(BlaBlaCollectorUrlModule::tripId).isNullOrBlank()} profileUuidPresent=${!card.verifiedProfileUuid.isNullOrBlank()} identityState=${card.identityState}",
            diagnosticContext = DiagnosticEventContext0507(
                parentModule = DiagnosticModule0507.PUBLIC_QUERY,
                originModule = DiagnosticModule0507.PUBLIC_QUERY,
                executorModule = DiagnosticModule0507.BLABLACAR,
                submodule = "PUBLIC_IDENTITY",
                component = "BlaBlaPublicSearchActivity",
                operation = "PUBLIC_PROFILE_IDENTITY_ENRICHMENT",
                severity = severity,
                correlationId = request.collectionId,
                entityType = "public_trip_candidate",
                entityId = card.href?.let(BlaBlaCollectorUrlModule::tripId).orEmpty(),
                result = result,
                errorCode = errorCode,
                reason = reason,
            ),
        )
    }

    private fun advance() {
        taskIndex++
        if (taskIndex >= tasks.size) complete() else loadCurrentTask()
    }

    private fun recordFailure(task: BlaBlaPublicSearchTask, status: String, error: String) {
        finalizeQuery(
            task = task,
            coverageStatus = "FAILED",
            exact = false,
            zeroResults = false,
            terminal = false,
            errorStage = status.uppercase(),
            exceptionClass = "PublicSearchFailure",
            exceptionMessage = error,
        )
    }

    private fun complete() {
        tasks.drop(queries.size).forEach { missing ->
            queries += BlaBlaPublicSearchQueryResult(
                date = missing.date.toString(),
                from = missing.from,
                to = missing.to,
                status = "pending_unknown",
                queryId = publicSearchQueryId(request, missing),
                direction = publicSearchDirectionName(request, missing),
                coverageStatus = "PENDING_UNKNOWN",
                evidence = BlaBlaPublicSearchQueryEvidence(),
                errorDetail = BlaBlaPublicSearchErrorDetail(
                    stage = "COLLECTION_INTERRUPTED",
                    exceptionClass = "MissingTerminalQueryState",
                    exceptionMessage = "A consulta não recebeu evidência terminal.",
                ),
            )
        }
        val status = when {
            queries.isNotEmpty() && queries.all { it.coverageStatus == "COMPLETE" } -> "validated"
            queries.any { it.coverageStatus in setOf("COMPLETE", "PARTIAL") } -> "partial"
            else -> "error"
        }
        val rawCards = matches
            .distinctBy { listOf(it.queryId, it.tripId.orEmpty(), it.captureIndex.toString(), it.tripHref.orEmpty(), it.driverName).joinToString("|") }
            .sortedWith(compareBy<BlaBlaPublicSearchCard> { it.date }.thenBy { it.direction }.thenBy { it.departureTime.orEmpty() }.thenBy { it.captureIndex })
        val knownProfiles = BlaBlaDynamicAccountRegistry(this).list().map { account ->
            BlaBlaPublicSearchPlanner.KnownProfile(
                name = account.profileName?.trim().takeUnless { it.isNullOrBlank() } ?: account.displayLabel,
                profileUuid = account.profileUuid,
            )
        }
        val requestedCards = BlaBlaPublicSearchPlanner.filterRequestedCards(
            rawCards = rawCards,
            request = request,
            knownProfiles = knownProfiles,
        )
        val response = BlaBlaPublicSearchResponse(
            collectedAtMillis = System.currentTimeMillis(),
            status = status,
            request = request,
            cards = requestedCards,
            rawCards = rawCards,
            queries = queries.sortedWith(compareBy<BlaBlaPublicSearchQueryResult> { it.date }.thenBy { it.direction }.thenBy { it.queryId }),
            demands = demands.distinctBy { listOf(it.date, it.from, it.to).joinToString("|") }
                .sortedWith(compareBy(BlaBlaPublicSearchDemand::date, BlaBlaPublicSearchDemand::from, BlaBlaPublicSearchDemand::to)),
        )
        store.saveResponse(response)
        runCatching { BlaBlaAuditableCollectionBuilder.build(this, response) }
            .onSuccess(store::saveSnapshot)
            .onFailure { error ->
                UnifiedDebugEventStore.record(
                    "PUBLIC_SEARCH_AUDIT_SNAPSHOT_FAILED",
                    packageName,
                    AgendaFailureEvidence.describe(
                        error = error,
                        operation = "PUBLIC_SEARCH_AUDIT_SNAPSHOT",
                        component = "BlaBlaPublicSearchActivity",
                        method = "finishCollection",
                    ),
                )
            }
        browserOrchestrator.cancel()
        BlaBlaPublicSearchSingleFlight.release(request.collectionId)
        UnifiedDebugEventStore.record(
            "PUBLIC_SEARCH_COMPLETED",
            packageName,
            "status=$status tasks=${response.queries.size} complete=${response.completeQueries} failed=${response.failedQueries} rawCards=${response.rawCards.size} resultCards=${response.cards.size} targets=${request.targetNames.size} demandRecords=${response.demands.size} snapshotImmutable=true",
        )
        setResult(
            if (status == "error") RESULT_CANCELED else RESULT_OK,
            Intent().putExtra(BlaBlaPublicSearchIntents.EXTRA_RESULT_STATUS, status),
        )
        finish()
    }

    private fun finishWithError(message: String) {
        browserOrchestrator.cancel()
        if (::request.isInitialized) BlaBlaPublicSearchSingleFlight.release(request.collectionId)
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
        diagnosticParentModule = DiagnosticModule0507.PUBLIC_QUERY,
        diagnosticOriginModule = DiagnosticModule0507.PUBLIC_QUERY,
        diagnosticCorrelationId = request.collectionId,
    )

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

    private fun PublicRenderedCard.toPublicCard(
        task: BlaBlaPublicSearchTask,
        queryId: String,
        direction: String,
        index: Int,
        capturedAtMillis: Long,
    ): BlaBlaPublicSearchCard {
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
            tripHref = href
                ?.let(BlaBlaCollectorUrlModule::absolute)
                ?.let(BlaBlaCollectorUrlModule::canonical)
                ?.takeIf(String::isNotBlank),
            queryId = queryId,
            direction = direction,
            tripId = href?.let(BlaBlaCollectorUrlModule::tripId),
            profileUuid = verifiedProfileUuid ?: strongPublicProfileUuid(profileHrefs),
            profileUuidEvidence = profileUuidEvidence
                ?: strongPublicProfileUuid(profileHrefs)?.let { "PUBLIC_CARD_PROFILE_LINK" },
            identityState = when {
                !verifiedProfileUuid.isNullOrBlank() -> "CONFIRMED_STRONG_IDENTITY"
                strongPublicProfileUuid(profileHrefs) != null -> "CONFIRMED_STRONG_IDENTITY"
                identityState == "IDENTITY_CONFLICT" -> "IDENTITY_CONFLICT"
                else -> "PENDING_IDENTITY_ENRICHMENT"
            },
            currency = cleanCurrency(priceText),
            capturedAtMillis = capturedAtMillis,
            captureIndex = cardIndex.takeIf { it >= 0 } ?: index,
        )
    }

    private fun cleanPrice(raw: String?): String? = raw?.replace('\u00a0', ' ')
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private fun cleanCurrency(raw: String?): String? =
        Regex("\\b[A-Z]{3}\\b").find(raw.orEmpty().uppercase())?.value

    private fun strongPublicProfileUuid(hrefs: List<String>): String? {
        val uuids = BlaBlaCollectorIdentityModule.uuids(
            hrefs.map(BlaBlaCollectorUrlModule::absolute),
        )
        return uuids.singleOrNull()
    }

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

    private fun persistCheckpoint() {
        if (!::request.isInitialized) return
        val checkpoint = PublicSearchCheckpoint(
            collectionId = request.collectionId,
            taskIndex = taskIndex + 1,
            cards = matches.toList(),
            queries = queries.toList(),
            demands = demands.toList(),
        )
        getPreferences(Context.MODE_PRIVATE).edit()
            .putString(CHECKPOINT_KEY, json.encodeToString(checkpoint))
            .apply()
    }

    private fun restoreCheckpoint(savedInstanceState: Bundle?) {
        val raw = savedInstanceState?.getString(CHECKPOINT_KEY)
            ?: getPreferences(Context.MODE_PRIVATE).getString(CHECKPOINT_KEY, null)
        val checkpoint = runCatching {
            raw?.let { json.decodeFromString<PublicSearchCheckpoint>(it) }
        }.getOrNull()?.takeIf { it.collectionId == request.collectionId }
        if (checkpoint == null) {
            getPreferences(Context.MODE_PRIVATE).edit().remove(CHECKPOINT_KEY).apply()
            return
        }
        matches.clear()
        matches += checkpoint.cards
        queries.clear()
        queries += checkpoint.queries
        demands.clear()
        demands += checkpoint.demands
        taskIndex = checkpoint.taskIndex.coerceIn(0, tasks.size)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::request.isInitialized) {
            val checkpoint = PublicSearchCheckpoint(
                collectionId = request.collectionId,
                taskIndex = taskIndex,
                cards = matches.toList(),
                queries = queries.toList(),
                demands = demands.toList(),
            )
            outState.putString(CHECKPOINT_KEY, json.encodeToString(checkpoint))
        }
    }

    override fun onDestroy() {
        browserOrchestrator.cancel()
        if (!isChangingConfigurations && ::request.isInitialized) {
            BlaBlaPublicSearchSingleFlight.release(request.collectionId)
        }
        super.onDestroy()
    }

    companion object {
        private const val PUBLIC_PROFILE = "rota_certa_blablacar_public_search"
        private const val PAGE_SETTLE_MS = 3_500L
        private const val SCROLL_SETTLE_MS = 1_500L
        private const val MAX_CAPTURE_PASSES = 4
        private const val CHECKPOINT_KEY = "public_search_checkpoint_v1"
        private val ZERO_RESULTS = listOf(
            "Ainda não existem viagens entre essas cidades",
            "0 viagem disponível",
            "Nenhuma viagem disponível",
        )

    }
}
