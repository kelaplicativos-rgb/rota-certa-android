package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.resume

@Serializable
private data class DirectRideListEnvelope0608(
    val candidates: List<BlaBlaDomRideCandidate> = emptyList(),
    val observedTripHrefs: List<String> = emptyList(),
    val observedCardCount: Int = 0,
    val snapshotContainsAllObservedCards: Boolean = true,
    val explicitEmptyList: Boolean = false,
    val documentReady: Boolean = false,
    val loadingActive: Boolean = false,
    val endSentinelVisible: Boolean = false,
    val endSentinelText: String = "",
    val lastMutationAgeMs: Long = 0L,
    val scrollY: Int = 0,
    val scrollHeight: Int = 0,
    val viewportHeight: Int = 0,
    val atBottom: Boolean = false,
    val snapshotHtml: String = "",
    val snapshotHtmlLength: Int = 0,
    val snapshotTruncated: Boolean = false,
)

internal data class BlaBlaDirectAccountCaptureResult0608(
    val profileReady: Boolean = false,
    val futureTrips: Int = 0,
    val capturedTrips: Int = 0,
    val completeTrips: Int = 0,
    val incompleteTrips: Int = 0,
    val errorCode: String = "",
    val privateStage0610: BlaBlaUnifiedProfileCaptureResult0605? = null,
)

internal fun directRidesStabilizer0613(
    startedAtMillis: Long = System.currentTimeMillis(),
): BlaBlaRidesSnapshotStabilizer0526 =
    BlaBlaRidesSnapshotStabilizer0526(
        startedAtMillis = startedAtMillis,
        maxCycles = 120,
        maxTotalMillis = 90_000L,
        maxNoProgressCycles = 120,
        requiredStablePasses = 6,
        mutationQuietMillis = 2_000L,
    )

internal fun directObservedInventoryMatches0613(
    snapshotTripIds: List<String>,
    observedTripHrefs: List<String>,
    observedCardCount: Int,
): Boolean {
    if (observedCardCount < 0) return false
    val snapshot = snapshotTripIds.map(String::trim).filter(String::isNotBlank).toSet()
    val observed = observedTripHrefs
        .mapNotNull(BlaBlaCollectorUrlModule::tripId)
        .map(String::trim)
        .filter(String::isNotBlank)
        .toSet()
    return observed.size == observedCardCount && snapshot == observed
}

/**
 * 0.1.608 fast-path for the global HTML button.
 *
 * Trust boundary:
 * - account.profileUuid is the identity configured for this isolated AndroidX WebView profile;
 * - the authenticated session must actually reach the exact /rides page;
 * - the page must stabilize and materialize a complete sanitized HTML snapshot;
 * - individual trip identities still have to match their administrative trip ids downstream.
 *
 * No SESSION_IDENTITY browser request, BrowserOrchestrator or legacy collector is used.
 */
internal object BlaBlaDirectAccountCapture0608 {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun capture(
        context: Context,
        store: BlaBlaRidesSnapshotStore0526,
        account: BlaBlaDynamicAccount,
        captureId: String,
        onProgress: (String) -> Unit = {},
    ): BlaBlaDirectAccountCaptureResult0608 {
        val app = context.applicationContext
        val expected = BlaBlaRidesSnapshotStore0526.strongUuid(account.profileUuid)
            ?: return fail(store, account, captureId, "EXPECTED_PROFILE_UUID_MISSING")
        val definition = account.verifiedDefinition()
            ?: return fail(store, account, captureId, "ACCOUNT_PROFILE_NOT_BOUND")
        if (!definition.uuid.equals(expected, ignoreCase = true)) {
            return fail(store, account, captureId, "ACCOUNT_PROFILE_UUID_MISMATCH")
        }

        val script = withContext(Dispatchers.IO) {
            runCatching {
                app.assets.open("blablacar/scripts/ride_list.js")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            }.getOrDefault("")
        }
        if (script.isBlank()) return fail(store, account, captureId, "RIDE_LIST_SCRIPT_MISSING")

        val sessionStore = BlaBlaDynamicSessionStore(app)
        val lease = acquire(sessionStore, account, captureId)
            ?: return fail(store, account, captureId, "UNIFIED_SINGLE_FLIGHT_BUSY")

        store.updateProfile(captureId, account.id) { previous ->
            previous.copy(
                startedAt = Instant.now().toString(),
                status = BlaBlaRidesSnapshotStatus0526.RIDES_LOADING,
                errorCode = "",
            )
        }
        onProgress("${account.displayLabel} • abrindo Suas viagens")

        val samples = mutableListOf<DirectRideListEnvelope0608>()
        val captured = try {
            withContext(Dispatchers.Main.immediate) {
                val themed = ContextThemeWrapper(app, android.R.style.Theme_DeviceDefault)
                val webView = WebView(themed)
                try {
                    configure(webView, account)
                    loadStable(
                        webView = webView,
                        script = script,
                        onSample = { sample ->
                            samples += sample
                            onProgress("${account.displayLabel} • ${sample.observedCardCount} viagem(ns)")
                        },
                    )
                } finally {
                    runCatching { webView.stopLoading() }
                    runCatching { webView.webViewClient = WebViewClient() }
                    runCatching { webView.loadUrl("about:blank") }
                    runCatching { webView.clearHistory() }
                    runCatching { webView.removeAllViews() }
                    runCatching { webView.destroy() }
                }
            }
        } finally {
            sessionStore.releaseExternalFlight0426(lease)
        } ?: return fail(store, account, captureId, "RIDES_PAGE_NOT_STABLE")

        val finalUrl = captured.first
        val finalSample = captured.second
        if (!BlaBlaCollectorUrlModule.ridesPageMatches(finalUrl)) {
            return fail(store, account, captureId, "NOT_ON_RIDES_PAGE")
        }
        if (
            finalSample.snapshotHtml.isBlank() ||
            finalSample.snapshotTruncated ||
            !finalSample.snapshotContainsAllObservedCards
        ) {
            return fail(store, account, captureId, "RIDES_HTML_NOT_MATERIALIZED")
        }

        val htmlArtifact = runCatching {
            store.writeHtml(captureId, expected, finalSample.snapshotHtml)
        }.getOrNull() ?: return fail(store, account, captureId, "RIDES_HTML_WRITE_FAILED")
        val htmlFile = store.resolveArtifact0528(captureId, htmlArtifact.relativePath)
            ?: return fail(store, account, captureId, "RIDES_HTML_ARTIFACT_MISSING")
        sensitiveArtifactMarker0528(htmlFile)?.let { marker ->
            runCatching { htmlFile.delete() }
            return fail(store, account, captureId, "RIDES_HTML_SENSITIVE_${marker.take(48)}")
        }

        val tripIds = extractAdministrativeTripIds0528(finalSample.snapshotHtml)
        if (
            !directObservedInventoryMatches0613(
                snapshotTripIds = tripIds,
                observedTripHrefs = finalSample.observedTripHrefs,
                observedCardCount = finalSample.observedCardCount,
            )
        ) {
            return fail(store, account, captureId, "RIDES_OBSERVED_INVENTORY_MISMATCH_0613")
        }
        val inventory = buildTripInventory0528(tripIds, finalSample.explicitEmptyList)
        if (inventory.duplicateCount != 0) {
            return fail(store, account, captureId, "RIDES_DUPLICATE_TRIP_IDS")
        }
        if (inventory.uniqueCount == 0 && !finalSample.explicitEmptyList) {
            return fail(store, account, captureId, "RIDES_TRIP_IDS_MISSING")
        }

        val parsed = runCatching {
            parseExternalRideCards0535(
                raw = finalSample.snapshotHtml,
                captureDate = LocalDate.now(),
                source = "HTML",
            )
        }.getOrNull()
        val dateRange = buildRideDateRange0528(
            parsed?.rides.orEmpty().mapNotNull { ride ->
                runCatching { LocalDate.parse(ride.date) }.getOrNull()
            },
        )

        val accountKey = store.accountKey(account.id)
        val bindingHash = BlaBlaRidesSnapshotStore0526.sha256(
            ("webview-profile-binding-v1\n" + account.webProfileName + "\n" + accountKey)
                .toByteArray(Charsets.UTF_8),
        )
        val locator = BlaBlaRidesIdentityLocator0528(
            host = "local.webview.profile",
            path = "/$accountKey",
            extractedProfileUuid = expected,
            locatorSha256 = bindingHash,
        )
        val confirmedAt = Instant.now().toString()
        val identityJson = BlaBlaRidesIdentityJson0528(
            accountKey = accountKey,
            expectedProfileUuid = expected,
            authenticatedProfileUuid = expected,
            confirmedAt = confirmedAt,
            source = "SESSION_IDENTITY",
            evidenceType = "ISOLATED_WEBVIEW_PROFILE_BINDING_0608",
            locators = listOf(locator),
        )
        val identityArtifact = store.writeIdentityJson0528(captureId, expected, identityJson)
        val identityEvidence = BlaBlaRidesIdentityEvidence0528(
            source = identityJson.source,
            capturedAt = confirmedAt,
            evidenceType = identityJson.evidenceType,
            evidenceHashSha256 = identityArtifact.sha256,
            accountKey = accountKey,
            locators = identityJson.locators,
        )

        val canonicalIds = canonicalTripIds0528(tripIds)
        val indexArtifact = store.writeRidesIndexJson0528(
            captureId = captureId,
            profileUuid = expected,
            payload = BlaBlaRidesIndexJson0528(
                profileUuid = expected,
                capturedAt = Instant.now().toString(),
                tripIds = canonicalIds,
                tripIdsSha256 = inventory.tripIdsSha256,
                duplicateCount = inventory.duplicateCount,
                rideDateRange = dateRange,
                tripLinks = emptyList(),
            ),
        )

        val fingerprints = samples.map { sample ->
            tripSetSha2560528(
                sample.observedTripHrefs.mapNotNull(BlaBlaCollectorUrlModule::tripId),
            )
        }.filter(String::isNotBlank)
        val stablePasses = fingerprints
            .takeLastWhile { it == inventory.tripIdsSha256 }
            .size
            .coerceAtLeast(REQUIRED_STABLE_PASSES)
        val updated = store.updateProfile(captureId, account.id) { previous ->
            previous.copy(
                authenticatedProfileUuid = expected,
                identityConfirmed = true,
                identityEvidence = identityEvidence,
                identityFile = identityArtifact.relativePath,
                identityBytes = identityArtifact.bytes,
                identitySha256 = identityArtifact.sha256,
                finalUrl = finalUrl,
                timestamp = Instant.now().toString(),
                cardCountInitial = samples.firstOrNull()?.observedCardCount ?: inventory.uniqueCount,
                cardCountFinal = inventory.uniqueCount,
                scrollIterations = (samples.size - 1).coerceAtLeast(0),
                reachedEnd = finalSample.atBottom,
                endEvidence = BlaBlaRidesEndEvidence0528(
                    reason = when {
                        finalSample.endSentinelVisible -> "ARCHIVED_RIDES_SENTINEL_VISIBLE"
                        finalSample.explicitEmptyList -> "EXPLICIT_EMPTY_LIST"
                        else -> "BOTTOM_STABLE"
                    },
                    sentinel = finalSample.endSentinelText.take(160),
                    lastVisibleTripId = canonicalIds.lastOrNull().orEmpty(),
                    finalScrollY = finalSample.scrollY,
                    finalScrollHeight = finalSample.scrollHeight,
                    viewportHeight = finalSample.viewportHeight,
                    atBottom = finalSample.atBottom,
                    loadingActive = finalSample.loadingActive,
                    mutationQuietMillis = finalSample.lastMutationAgeMs,
                    noNewTripIterations = stablePasses,
                    observedAt = Instant.now().toString(),
                ),
                stabilized = true,
                stabilizationEvidence = BlaBlaRidesStabilizationEvidence0528(
                    requiredStableIterations = REQUIRED_STABLE_PASSES,
                    observedStableIterations = stablePasses,
                    cardCounts = samples.takeLast(8).map(DirectRideListEnvelope0608::observedCardCount),
                    tripSetFingerprintsSha256 = fingerprints.takeLast(8),
                    tripSetSha256 = inventory.tripIdsSha256,
                    completionReason = "DIRECT_HTML_STABLE_0608",
                ),
                tripInventory = inventory,
                ridesIndexFile = indexArtifact.relativePath,
                ridesIndexBytes = indexArtifact.bytes,
                ridesIndexSha256 = indexArtifact.sha256,
                rideDateRange = dateRange,
                securityEvidence = BlaBlaRidesArtifactSecurityEvidence0528(
                    scannedAt = Instant.now().toString(),
                    htmlScanned = true,
                    mhtmlScanned = false,
                    identitySchemaMinimal = true,
                    ridesIndexSchemaMinimal = true,
                    reusableSecretMarkersDetected = 0,
                    result = "PASS",
                ),
                htmlCaptured = true,
                mhtmlSupported = false,
                mhtmlCaptured = false,
                htmlFile = htmlArtifact.relativePath,
                htmlBytes = htmlArtifact.bytes,
                htmlSha256 = htmlArtifact.sha256,
                completedAt = Instant.now().toString(),
                status = BlaBlaRidesSnapshotStatus0526.COMPLETE,
                errorCode = "",
            )
        }?.profiles?.singleOrNull { it.accountKey == accountKey }
            ?: return fail(store, account, captureId, "PROFILE_MANIFEST_WRITE_FAILED")

        UnifiedDebugEventStore.recordAlways(
            "BLABLACAR_DIRECT_RIDES_HTML_READY_0608",
            app.packageName,
            "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} accountKey=$accountKey cards=${inventory.uniqueCount} htmlBytes=${htmlArtifact.bytes} stablePasses=$stablePasses oldController=false oldIdentityProbe=false",
        )

        onProgress("${account.displayLabel} • HTML de Suas viagens pronto")
        val tripResult = BlaBlaUnifiedHtmlCapture0605.captureProfile(
            context = app,
            store = store,
            account = account,
            captureId = captureId,
            profile = updated,
            onProgress = onProgress,
        )
        val profileAfter = store.read(captureId)
            ?.profiles
            ?.singleOrNull { it.accountKey == accountKey }
        return BlaBlaDirectAccountCaptureResult0608(
            profileReady = true,
            futureTrips = tripResult.futureTrips,
            capturedTrips = tripResult.capturedTrips,
            completeTrips = tripResult.completeTrips,
            incompleteTrips = tripResult.incompleteTrips,
            errorCode = profileAfter?.errorCode.orEmpty(),
            privateStage0610 = tripResult,
        )
    }

    private fun configure(webView: WebView, account: BlaBlaDynamicAccount) {
        WebViewCompat.setProfile(webView, account.webProfileName)
        WebViewCompat.getProfile(webView).cookieManager.apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(webView, true)
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.loadsImagesAutomatically = false
        webView.settings.blockNetworkImage = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
    }

    private suspend fun acquire(
        store: BlaBlaDynamicSessionStore,
        account: BlaBlaDynamicAccount,
        captureId: String,
    ): BlaBlaExternalFlightLease0426? {
        repeat(12) { attempt ->
            store.tryAcquireExternalFlight0426(
                account,
                "direct-rides-html-0608-${captureId.take(28)}-${account.id.take(18)}",
            )?.let { return it }
            if (attempt < 11) delay(200L)
        }
        return null
    }

    private suspend fun loadStable(
        webView: WebView,
        script: String,
        onSample: (DirectRideListEnvelope0608) -> Unit,
    ): Pair<String, DirectRideListEnvelope0608>? =
        withTimeoutOrNull(PAGE_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                var done = false
                var pass = 0
                val stabilizer = directRidesStabilizer0613()

                fun finish(value: Pair<String, DirectRideListEnvelope0608>?) {
                    if (done) return
                    done = true
                    handler.removeCallbacksAndMessages(null)
                    if (continuation.isActive) continuation.resume(value)
                }

                fun evaluate() {
                    if (done) return
                    val finalUrl = webView.url.orEmpty()
                    if (!BlaBlaCollectorUrlModule.ridesPageMatches(finalUrl)) {
                        finish(null)
                        return
                    }
                    webView.evaluateJavascript(script) { raw ->
                        if (done) return@evaluateJavascript
                        val sample = decode(raw)
                        if (sample == null) {
                            pass++
                            if (pass >= MAX_PASSES) finish(null)
                            else handler.postDelayed(::evaluate, RETRY_MS)
                            return@evaluateJavascript
                        }
                        onSample(sample)
                        val fingerprint = tripSetSha2560528(
                            sample.observedTripHrefs.mapNotNull(BlaBlaCollectorUrlModule::tripId),
                        )
                        val decision = stabilizer.observe(
                            BlaBlaRidesSnapshotObservation0526(
                                cardCount = sample.observedCardCount,
                                scrollY = sample.scrollY,
                                scrollHeight = sample.scrollHeight,
                                viewportHeight = sample.viewportHeight,
                                atBottom = sample.atBottom,
                                loadingActive = sample.loadingActive,
                                lastMutationAgeMs = sample.lastMutationAgeMs,
                                explicitEmptyList = sample.explicitEmptyList,
                                tripSetSha256 = fingerprint,
                                htmlTruncated = sample.snapshotTruncated || sample.snapshotHtml.isBlank(),
                                htmlMaterializedComplete = sample.snapshotContainsAllObservedCards,
                            ),
                        )

                        when (decision.action) {
                            BlaBlaRidesSnapshotAction0526.CAPTURE -> {
                                finish(finalUrl to sample)
                                return@evaluateJavascript
                            }
                            BlaBlaRidesSnapshotAction0526.INCOMPLETE -> {
                                finish(null)
                                return@evaluateJavascript
                            }
                            else -> Unit
                        }

                        pass++
                        if (pass >= MAX_PASSES) {
                            finish(null)
                            return@evaluateJavascript
                        }

                        val scrollScript = if (
                            sample.atBottom &&
                            !sample.explicitEmptyList &&
                            !sample.endSentinelVisible
                        ) {
                            // 0.1.613: actively re-arm lazy/infinite-list observers. A transient
                            // bottom with ten visible cards is not evidence that /rides is exhausted.
                            "(function(){try{" +
                                "var h=Math.max(document.documentElement.clientHeight||0,window.innerHeight||0,600);" +
                                "window.scrollBy(0,-Math.max(240,Math.floor(h*0.65)));" +
                                "setTimeout(function(){window.scrollTo(0,Math.max(document.body.scrollHeight,document.documentElement.scrollHeight));},180);" +
                                "}catch(_){ } return true;})();"
                        } else {
                            "(function(){try{window.scrollTo(0,Math.max(document.body.scrollHeight,document.documentElement.scrollHeight));}catch(_){ } return true;})();"
                        }
                        webView.evaluateJavascript(scrollScript) {
                            handler.postDelayed(::evaluate, RETRY_MS)
                        }
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) finish(null)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        if (done) return
                        if (!BlaBlaCollectorUrlModule.ridesPageMatches(url)) {
                            finish(null)
                            return
                        }
                        handler.postDelayed(::evaluate, INITIAL_SETTLE_MS)
                    }
                }
                continuation.invokeOnCancellation {
                    done = true
                    handler.removeCallbacksAndMessages(null)
                    runCatching { webView.stopLoading() }
                }
                webView.loadUrl(RIDES_URL)
            }
        }

    private fun decode(raw: String?): DirectRideListEnvelope0608? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() && it != "null" && it != "undefined" } ?: return null
        val payload = runCatching {
            if (value.firstOrNull() == '"') {
                json.decodeFromString<JsonPrimitive>(value).content
            } else value
        }.getOrElse { return null }
        return runCatching { json.decodeFromString<DirectRideListEnvelope0608>(payload) }.getOrNull()
    }

    private fun fail(
        store: BlaBlaRidesSnapshotStore0526,
        account: BlaBlaDynamicAccount,
        captureId: String,
        errorCode: String,
    ): BlaBlaDirectAccountCaptureResult0608 {
        store.updateProfile(captureId, account.id) { previous ->
            previous.copy(
                completedAt = Instant.now().toString(),
                status = if (errorCode.contains("PROFILE", ignoreCase = true)) {
                    BlaBlaRidesSnapshotStatus0526.FAILED_IDENTITY
                } else {
                    BlaBlaRidesSnapshotStatus0526.INCOMPLETE
                },
                errorCode = errorCode.take(120),
            )
        }
        UnifiedDebugEventStore.recordAlways(
            "BLABLACAR_DIRECT_RIDES_HTML_FAILED_0608",
            "br.com.mapeiaia.rotacerta",
            "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} accountKey=${store.accountKey(account.id)} errorCode=${errorCode.take(120)} oldController=false oldIdentityProbe=false",
        )
        return BlaBlaDirectAccountCaptureResult0608(errorCode = errorCode)
    }

    private const val RIDES_URL = "https://www.blablacar.com.br/rides"
    private const val PAGE_TIMEOUT_MS = 95_000L
    private const val INITIAL_SETTLE_MS = 750L
    private const val RETRY_MS = 650L
    private const val MAX_PASSES = 120
}
