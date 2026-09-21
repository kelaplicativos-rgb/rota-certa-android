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
import java.time.LocalTime
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

/**
 * 0.1.605 single acquisition path for the "Capturar Suas viagens" button.
 *
 * Source acquisition is deliberately independent from BlaBlaAutomaticCollectionCoordinator0400
 * and BlaBlaBrowserOrchestrator. One isolated authenticated WebView profile reads /rides, then
 * each current/future administrative offer, its published-link evidence and its seat-options page.
 * The resulting normalized observations enter the already-existing dynamic-session/canonical path.
 */
@Serializable
internal data class BlaBlaRidesTripCapture0605(
    val tripId: String,
    val administrativeUrl: String = "",
    val date: String = "",
    val capturedAt: String = "",
    val finalUrl: String = "",
    val htmlFile: String = "",
    val htmlBytes: Long = 0L,
    val htmlSha256: String = "",
    val normalized: Boolean = false,
    val passengerRosterComplete: Boolean = false,
    val itineraryAuthoritative: Boolean = false,
    val publishedSeats: Int? = null,
    val publicTripUrl: String = "",
    val publicTripUrlSource: String = "",
    val publicTripUrlBinding: String = "",
    val status: String = "PENDING",
    val errorCode: String = "",
)

internal data class BlaBlaUnifiedProfileCaptureResult0605(
    val futureTrips: Int,
    val capturedTrips: Int,
    val completeTrips: Int,
    val incompleteTrips: Int,
)

@Serializable
private data class UnifiedTripDetailEnvelope0605(
    val detail: BlaBlaDomTripDetail = BlaBlaDomTripDetail(),
    val editHref: String = "",
    val publicTripHref: String = "",
    val itineraryStops: List<String> = emptyList(),
    val itineraryAuthoritative: Boolean = false,
    val domHtml: String = "",
)

@Serializable
private data class UnifiedPublicShareEvidence0605(
    val tripId: String = "",
    val publishedOfferLinkPresent: Boolean = false,
    val publishedOfferHref: String = "",
    val publicTripHref: String = "",
)

@Serializable
private data class UnifiedEditEvidence0605(
    val optionsHref: String = "",
    val pageUrl: String = "",
)

@Serializable
private data class UnifiedSeatEvidence0605(
    val seats: Int = -1,
    val pageUrl: String = "",
)

private data class UnifiedDirectScripts0605(
    val detail: String,
    val share: String,
    val edit: String,
    val seats: String,
)

private data class UnifiedEvaluatedPage0605(
    val finalUrl: String,
    val payload: String,
)

private data class UnifiedCapturedTrip0605(
    val trip: BlaBlaCollectorTrip?,
    val evidence: BlaBlaRidesTripCapture0605,
    val operationalComplete: Boolean,
)

internal fun shouldCaptureRide0605(
    ride: ParsedExternalRide0535,
    today: LocalDate,
    now: LocalTime,
): Boolean {
    val status = ride.status
        .lowercase()
        .replace("ã", "a")
        .replace("á", "a")
        .replace("ç", "c")
    if (listOf("cancelad", "concluid", "finalizad", "realizad", "arquivad").any(status::contains)) return false
    val date = runCatching { LocalDate.parse(ride.date) }.getOrNull() ?: return false
    if (date.isAfter(today)) return true
    if (date.isBefore(today)) return false
    val cutoff = sequenceOf(ride.arrivalTime, ride.departureTime)
        .mapNotNull { raw -> runCatching { LocalTime.parse(raw) }.getOrNull() }
        .firstOrNull()
    return cutoff == null || !cutoff.isBefore(now)
}

internal object BlaBlaUnifiedHtmlCapture0605 {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun captureProfile(
        context: Context,
        store: BlaBlaRidesSnapshotStore0526,
        account: BlaBlaDynamicAccount,
        captureId: String,
        profile: BlaBlaRidesSnapshotProfile0526,
        onProgress: (String) -> Unit = {},
    ): BlaBlaUnifiedProfileCaptureResult0605 {
        val app = context.applicationContext
        val expectedProfileUuid = BlaBlaRidesSnapshotStore0526.strongUuid(profile.authenticatedProfileUuid)
            ?: return failedProfile0605(store, account, captureId, "UNIFIED_PROFILE_IDENTITY_MISSING")
        if (!profile.identityConfirmed || !expectedProfileUuid.equals(profile.expectedProfileUuid, ignoreCase = true)) {
            return failedProfile0605(store, account, captureId, "UNIFIED_PROFILE_IDENTITY_UNVERIFIED")
        }

        val htmlFile = store.resolveArtifact0528(captureId, profile.htmlFile)
            ?: return failedProfile0605(store, account, captureId, "UNIFIED_RIDES_HTML_MISSING")
        if (!verifySnapshotArtifact0528(htmlFile, profile.htmlBytes, profile.htmlSha256)) {
            return failedProfile0605(store, account, captureId, "UNIFIED_RIDES_HTML_INVALID")
        }
        val parsed = runCatching {
            parseExternalRideCards0535(htmlFile.readText(Charsets.UTF_8), LocalDate.now(), "HTML")
        }.getOrElse {
            return failedProfile0605(store, account, captureId, "UNIFIED_RIDES_HTML_PARSE_FAILED")
        }

        val today = LocalDate.now()
        val now = LocalTime.now()
        val futureRides = parsed.rides
            .filter { shouldCaptureRide0605(it, today, now) }
            .filter { ride ->
                val absolute = BlaBlaCollectorUrlModule.absolute(ride.administrativeUrl)
                BlaBlaCollectorUrlModule.isSpecificTrip(absolute) &&
                    BlaBlaCollectorUrlModule.tripId(absolute) == ride.tripId
            }
            .sortedWith(compareBy<ParsedExternalRide0535>({ it.date }, { it.departureTime }, { it.listPosition }))

        if (futureRides.isEmpty()) {
            store.updateProfile(captureId, account.id) { it.copy(tripCaptures0605 = emptyList()) }
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_UNIFIED_HTML_PROFILE_EMPTY_0605",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} accountKey=${store.accountKey(account.id)} futureTrips=0",
            )
            return BlaBlaUnifiedProfileCaptureResult0605(0, 0, 0, 0)
        }

        val definition = account.verifiedDefinition()
            ?: return failedProfile0605(store, account, captureId, "UNIFIED_ACCOUNT_DEFINITION_MISSING")
        if (!definition.uuid.equals(expectedProfileUuid, ignoreCase = true)) {
            return failedProfile0605(store, account, captureId, "UNIFIED_ACCOUNT_PROFILE_MISMATCH")
        }

        val scripts = withContext(Dispatchers.IO) {
            UnifiedDirectScripts0605(
                detail = readAsset0605(app, "blablacar/scripts/trip_detail.js"),
                share = readAsset0605(app, "blablacar/scripts/trip_public_share.js"),
                edit = readAsset0605(app, "blablacar/scripts/trip_edit.js"),
                seats = readAsset0605(app, "blablacar/scripts/seat_options.js"),
            )
        }
        if (listOf(scripts.detail, scripts.share, scripts.edit, scripts.seats).any(String::isBlank)) {
            return failedProfile0605(store, account, captureId, "UNIFIED_CAPTURE_SCRIPT_MISSING")
        }

        val sessionStore = BlaBlaDynamicSessionStore(app)
        val lease = acquireUnifiedFlight0605(sessionStore, account, captureId)
            ?: return failedFutureTrips0605(store, account, captureId, futureRides, "UNIFIED_SINGLE_FLIGHT_BUSY")

        val captures = mutableListOf<BlaBlaRidesTripCapture0605>()
        val collected = mutableListOf<BlaBlaCollectorTrip>()
        var incomplete = 0
        try {
            withContext(Dispatchers.Main.immediate) {
                val themed = ContextThemeWrapper(app, android.R.style.Theme_DeviceDefault)
                val webView = WebView(themed)
                try {
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

                    futureRides.forEachIndexed { index, ride ->
                        onProgress("Capturando viagem ${index + 1}/${futureRides.size} • ${ride.date} ${ride.departureTime} • ${account.displayLabel}")
                        val captured = captureTrip0605(webView, store, captureId, definition, ride, scripts)
                        captures += captured.evidence
                        captured.trip?.let(collected::add)
                        if (!captured.operationalComplete) incomplete++

                        store.updateProfile(captureId, account.id) { previous ->
                            previous.copy(tripCaptures0605 = captures.toList())
                        }

                        val remaining = futureRides.size - index - 1
                        persistProgress0605(
                            context = app,
                            sessionStore = sessionStore,
                            account = account,
                            trips = collected,
                            dateScope = futureRides.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() },
                            pendingOrIncomplete = incomplete + remaining,
                            lastUrl = captured.evidence.finalUrl.ifBlank { ride.administrativeUrl },
                            reason = "trip_${index + 1}_of_${futureRides.size}",
                        )
                    }
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
        }

        val finalIncomplete = captures.count { it.status != "COMPLETE" }
        val existingIndex = store.read(captureId)
            ?.profiles
            ?.singleOrNull { it.accountKey == store.accountKey(account.id) }
            ?.let { current -> store.readRidesIndexJson0582(captureId, current) }
        if (existingIndex != null) {
            val byTrip = captures.associateBy(BlaBlaRidesTripCapture0605::tripId)
            val links = existingIndex.tripIds.map { tripId ->
                val previous = existingIndex.tripLinks.singleOrNull { it.tripId == tripId }
                val capture = byTrip[tripId]
                if (capture == null) {
                    previous ?: BlaBlaRidesTripLink0582(tripId = tripId)
                } else {
                    BlaBlaRidesTripLink0582(
                        tripId = tripId,
                        administrativeUrl = capture.administrativeUrl,
                        publicTripUrl = capture.publicTripUrl,
                        publicTripUrlSource = capture.publicTripUrlSource,
                        publicTripUrlBinding = capture.publicTripUrlBinding,
                        publicTripStatus = if (capture.publicTripUrl.isNotBlank()) "COMPLETE" else "PENDING_UNKNOWN",
                        shareEligibility = previous?.shareEligibility ?: "ELIGIBLE",
                    )
                }
            }
            store.rewriteRidesIndexTripLinks0582(captureId, account.id, expectedProfileUuid, links)
        }

        store.updateProfile(captureId, account.id) { previous ->
            if (finalIncomplete == 0) {
                previous.copy(tripCaptures0605 = captures.toList(), errorCode = "")
            } else {
                previous.copy(
                    tripCaptures0605 = captures.toList(),
                    status = BlaBlaRidesSnapshotStatus0526.INCOMPLETE,
                    errorCode = "UNIFIED_FUTURE_TRIPS_INCOMPLETE_$finalIncomplete",
                )
            }
        }

        persistProgress0605(
            context = app,
            sessionStore = sessionStore,
            account = account,
            trips = collected,
            dateScope = futureRides.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() },
            pendingOrIncomplete = finalIncomplete,
            lastUrl = captures.lastOrNull()?.finalUrl.orEmpty(),
            reason = "profile_final",
        )

        UnifiedDebugEventStore.recordAlways(
            "BLABLACAR_UNIFIED_HTML_PROFILE_COMPLETED_0605",
            app.packageName,
            "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} accountKey=${store.accountKey(account.id)} futureTrips=${futureRides.size} captured=${captures.size} normalized=${collected.size} complete=${captures.count { it.status == "COMPLETE" }} incomplete=$finalIncomplete directWebView=true orchestrator=false automaticCollector=false",
        )
        return BlaBlaUnifiedProfileCaptureResult0605(
            futureTrips = futureRides.size,
            capturedTrips = captures.size,
            completeTrips = captures.count { it.status == "COMPLETE" },
            incompleteTrips = finalIncomplete,
        )
    }

    private suspend fun captureTrip0605(
        webView: WebView,
        store: BlaBlaRidesSnapshotStore0526,
        captureId: String,
        definition: BlaBlaAccountDefinition,
        ride: ParsedExternalRide0535,
        scripts: UnifiedDirectScripts0605,
    ): UnifiedCapturedTrip0605 {
        val administrativeUrl = BlaBlaCollectorUrlModule.absolute(ride.administrativeUrl)
        val capturedAt = Instant.now().toString()
        val detailPage = loadAndEvaluate0605(
            webView = webView,
            url = administrativeUrl,
            script = scripts.detail,
            prepareTrip = true,
        ) { finalUrl ->
            BlaBlaCollectorUrlModule.tripId(finalUrl) == ride.tripId &&
                !BlaBlaCollectorUrlModule.isPassenger(finalUrl)
        }
        val detail = decode0605<UnifiedTripDetailEnvelope0605>(detailPage?.payload)
        if (
            detailPage == null ||
            detail == null ||
            BlaBlaCollectorUrlModule.tripId(detail.detail.url) != ride.tripId ||
            detail.domHtml.isBlank()
        ) {
            return failedTrip0605(ride, capturedAt, detailPage?.finalUrl.orEmpty(), "TRIP_DETAIL_HTML_UNVERIFIED")
        }

        val htmlEvidence = runCatching {
            store.writeTripHtml0605(captureId, definition.uuid, ride.tripId, detail.domHtml)
        }.getOrNull()
            ?: return failedTrip0605(ride, capturedAt, detailPage.finalUrl, "TRIP_DETAIL_HTML_WRITE_FAILED")

        val savedHtml = store.resolveArtifact0528(captureId, htmlEvidence.relativePath)
        val sensitive = savedHtml?.let { sensitiveArtifactMarker0528(it) }
        if (sensitive != null) {
            runCatching { savedHtml.delete() }
            return failedTrip0605(
                ride,
                capturedAt,
                detailPage.finalUrl,
                "TRIP_DETAIL_HTML_SENSITIVE_${sensitive.take(48)}",
            )
        }

        val share = capturePublicLink0605(webView, scripts.share, ride.tripId)
        val literalPublicUrl = BlaBlaCollectorUrlModule.publicTripFromPublishedOfferHref(
            share?.publishedOfferHref,
            ride.tripId,
            ride.tripId,
        )
        val strictPassiveUrl = if (literalPublicUrl == null) {
            BlaBlaCollectorUrlModule.publicTrip(detail.publicTripHref, ride.tripId)
        } else null
        val publicUrl = literalPublicUrl ?: strictPassiveUrl
        val publicSource = when {
            literalPublicUrl != null -> "published_offer_href"
            strictPassiveUrl != null -> "trip_detail_dom_same_id"
            else -> ""
        }
        val publicBinding = when {
            literalPublicUrl != null -> BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_PUBLISHED_OFFER_HREF
            strictPassiveUrl != null -> BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_SAME_ID
            else -> ""
        }

        val publishedSeats = capturePublishedSeats0605(
            webView,
            ride.tripId,
            detail.editHref,
            scripts.edit,
            scripts.seats,
        )

        val candidate = BlaBlaDomRideCandidate(
            href = administrativeUrl,
            departureTime = ride.departureTime,
            arrivalTime = ride.arrivalTime,
            origin = ride.origin,
            destination = ride.destination,
            dateText = listOf(ride.date, ride.dateText).filter(String::isNotBlank).joinToString(" | "),
        )
        val normalized = BlaBlaDomNormalizer.toTrip(
            account = definition,
            candidate = candidate,
            detail = detail.detail,
            today = LocalDate.now(),
            authenticatedProfileSessionVerified = true,
        )?.takeIf { trip ->
            trip.trip_id == ride.tripId && trip.profile_uuid.equals(definition.uuid, ignoreCase = true)
        }

        val stops = detail.itineraryStops
            .map(String::trim)
            .filter(String::isNotBlank)
            .fold(mutableListOf<String>()) { result, stop ->
                if (result.lastOrNull() != stop) result += stop
                result
            }
            .toList()
        val trip = normalized?.copy(
            itinerary_stops = stops,
            itinerary_authoritative = detail.itineraryAuthoritative && stops.size >= 2,
            public_trip_href = publicUrl,
            public_trip_href_source = publicSource,
            public_trip_href_binding = publicBinding,
            published_seats = publishedSeats,
        )

        val operationalComplete =
            trip != null &&
                trip.passenger_roster_complete &&
                trip.itinerary_authoritative &&
                trip.published_seats != null &&
                !trip.public_trip_href.isNullOrBlank()
        val missing = buildList {
            if (trip == null) add("NORMALIZATION")
            if (trip?.passenger_roster_complete != true) add("ROSTER")
            if (trip?.itinerary_authoritative != true) add("ITINERARY")
            if (trip?.published_seats == null) add("SEATS")
            if (trip?.public_trip_href.isNullOrBlank()) add("PUBLIC_LINK")
        }
        val status = if (operationalComplete) "COMPLETE" else "INCOMPLETE"
        val error = if (operationalComplete) "" else "MISSING_" + missing.joinToString("_")

        return UnifiedCapturedTrip0605(
            trip = trip,
            operationalComplete = operationalComplete,
            evidence = BlaBlaRidesTripCapture0605(
                tripId = ride.tripId,
                administrativeUrl = administrativeUrl,
                date = ride.date,
                capturedAt = capturedAt,
                finalUrl = detailPage.finalUrl,
                htmlFile = htmlEvidence.relativePath,
                htmlBytes = htmlEvidence.bytes,
                htmlSha256 = htmlEvidence.sha256,
                normalized = trip != null,
                passengerRosterComplete = trip?.passenger_roster_complete == true,
                itineraryAuthoritative = trip?.itinerary_authoritative == true,
                publishedSeats = trip?.published_seats,
                publicTripUrl = trip?.public_trip_href.orEmpty(),
                publicTripUrlSource = trip?.public_trip_href_source.orEmpty(),
                publicTripUrlBinding = trip?.public_trip_href_binding.orEmpty(),
                status = status,
                errorCode = error.take(120),
            ),
        )
    }

    private suspend fun capturePublicLink0605(
        webView: WebView,
        script: String,
        expectedTripId: String,
    ): UnifiedPublicShareEvidence0605? {
        repeat(PUBLIC_SHARE_ATTEMPTS_0605) { attempt ->
            val evidence = decode0605<UnifiedPublicShareEvidence0605>(evaluateCurrent0605(webView, script))
            if (evidence != null && evidence.tripId == expectedTripId) {
                if (
                    BlaBlaCollectorUrlModule.publicTripFromPublishedOfferHref(
                        evidence.publishedOfferHref,
                        expectedTripId,
                        expectedTripId,
                    ) != null ||
                    BlaBlaCollectorUrlModule.publicTrip(evidence.publicTripHref, expectedTripId) != null
                ) return evidence
            }
            if (attempt + 1 < PUBLIC_SHARE_ATTEMPTS_0605) delay(PUBLIC_SHARE_RETRY_MS_0605)
        }
        return null
    }

    private suspend fun capturePublishedSeats0605(
        webView: WebView,
        tripId: String,
        editHref: String,
        editScript: String,
        seatsScript: String,
    ): Int? {
        val editTarget = BlaBlaCollectorUrlModule.absolute(editHref)
            .takeIf { BlaBlaCollectorUrlModule.editTripId(it) == tripId }
            ?: return null
        val editPage = loadAndEvaluate0605(webView, editTarget, editScript, false) {
            BlaBlaCollectorUrlModule.editTripId(it) == tripId
        } ?: return null
        val edit = decode0605<UnifiedEditEvidence0605>(editPage.payload) ?: return null
        val optionsTarget = BlaBlaCollectorUrlModule.absolute(edit.optionsHref)
            .takeIf { BlaBlaCollectorUrlModule.optionsTripId(it) == tripId }
            ?: return null
        val optionsPage = loadAndEvaluate0605(webView, optionsTarget, seatsScript, false) {
            BlaBlaCollectorUrlModule.optionsTripId(it) == tripId
        } ?: return null
        val seats = decode0605<UnifiedSeatEvidence0605>(optionsPage.payload) ?: return null
        val state = BlaBlaCollectorSeatModule.state(
            tripId = tripId,
            editHref = editPage.finalUrl,
            optionsHref = optionsPage.finalUrl,
            publishedSeats = seats.seats.takeIf { it >= 0 },
        )
        return state.publishedSeats.takeIf { BlaBlaCollectorSeatModule.complete(state) }
    }

    private suspend fun acquireUnifiedFlight0605(
        sessionStore: BlaBlaDynamicSessionStore,
        account: BlaBlaDynamicAccount,
        captureId: String,
    ): BlaBlaExternalFlightLease0426? {
        repeat(UNIFIED_FLIGHT_ATTEMPTS_0605) { attempt ->
            sessionStore.tryAcquireExternalFlight0426(
                account,
                "unified-html-0605-${captureId.take(28)}-${account.id.take(24)}",
            )?.let { return it }
            if (attempt + 1 < UNIFIED_FLIGHT_ATTEMPTS_0605) delay(UNIFIED_FLIGHT_RETRY_MS_0605)
        }
        return null
    }

    private fun persistProgress0605(
        context: Context,
        sessionStore: BlaBlaDynamicSessionStore,
        account: BlaBlaDynamicAccount,
        trips: List<BlaBlaCollectorTrip>,
        dateScope: List<LocalDate>,
        pendingOrIncomplete: Int,
        lastUrl: String,
        reason: String,
    ) {
        sessionStore.saveSync(
            account = account,
            lastUrl = lastUrl,
            trips = trips,
            skippedTrips = pendingOrIncomplete,
            identityVerified = true,
            dateScope = dateScope,
            targetedTripId = null,
            selectiveScriptSync0449 = false,
        )
        val accounts = BlaBlaDynamicAccountRegistry(context).list()
        val response = sessionStore.combinedResponse(accounts)
        val published = BlaBlaCollectorStateStore(context).saveResponse(response, preserveOnPartial = true)
        AgendaBackgroundSync0392.enqueueCollectorDelta0431(
            context = context,
            source = "unified_html_capture_0605:$reason",
        )
        UnifiedDebugEventStore.recordAlways(
            "BLABLACAR_UNIFIED_HTML_CANONICAL_CHECKPOINT_0605",
            context.packageName,
            "accountKey=${seatSyncDiagnosticKey(account.id)} reason=${reason.take(80)} capturedTrips=${trips.size} pendingOrIncomplete=$pendingOrIncomplete combinedTrips=${published.trips.size} acquisition=direct_webview canonicalDeltaEnqueued=true",
        )
    }

    private fun failedProfile0605(
        store: BlaBlaRidesSnapshotStore0526,
        account: BlaBlaDynamicAccount,
        captureId: String,
        errorCode: String,
    ): BlaBlaUnifiedProfileCaptureResult0605 {
        store.updateProfile(captureId, account.id) { previous ->
            previous.copy(status = BlaBlaRidesSnapshotStatus0526.INCOMPLETE, errorCode = errorCode.take(120))
        }
        return BlaBlaUnifiedProfileCaptureResult0605(0, 0, 0, 1)
    }

    private fun failedFutureTrips0605(
        store: BlaBlaRidesSnapshotStore0526,
        account: BlaBlaDynamicAccount,
        captureId: String,
        futureRides: List<ParsedExternalRide0535>,
        errorCode: String,
    ): BlaBlaUnifiedProfileCaptureResult0605 {
        val captures = futureRides.map { ride ->
            BlaBlaRidesTripCapture0605(
                tripId = ride.tripId,
                administrativeUrl = BlaBlaCollectorUrlModule.absolute(ride.administrativeUrl),
                date = ride.date,
                capturedAt = Instant.now().toString(),
                status = "INCOMPLETE",
                errorCode = errorCode,
            )
        }
        store.updateProfile(captureId, account.id) { previous ->
            previous.copy(
                tripCaptures0605 = captures,
                status = BlaBlaRidesSnapshotStatus0526.INCOMPLETE,
                errorCode = errorCode.take(120),
            )
        }
        return BlaBlaUnifiedProfileCaptureResult0605(futureRides.size, 0, 0, futureRides.size)
    }

    private fun failedTrip0605(
        ride: ParsedExternalRide0535,
        capturedAt: String,
        finalUrl: String,
        errorCode: String,
    ): UnifiedCapturedTrip0605 =
        UnifiedCapturedTrip0605(
            trip = null,
            operationalComplete = false,
            evidence = BlaBlaRidesTripCapture0605(
                tripId = ride.tripId,
                administrativeUrl = BlaBlaCollectorUrlModule.absolute(ride.administrativeUrl),
                date = ride.date,
                capturedAt = capturedAt,
                finalUrl = finalUrl,
                status = "INCOMPLETE",
                errorCode = errorCode.take(120),
            ),
        )

    private fun readAsset0605(context: Context, path: String): String =
        runCatching { context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrDefault("")

    private inline fun <reified T> decode0605(raw: String?): T? {
        val payload = decodeJavascriptPayload0605(raw) ?: return null
        return runCatching { json.decodeFromString<T>(payload) }.getOrNull()
    }

    private fun decodeJavascriptPayload0605(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() && it != "null" && it != "undefined" } ?: return null
        return runCatching {
            if (value.startsWith(""")) {
                json.decodeFromString<JsonPrimitive>(value).content
            } else {
                value
            }
        }.getOrElse { value.takeIf { it.startsWith("{") } }
    }

    private suspend fun evaluateCurrent0605(webView: WebView, script: String): String? =
        withTimeoutOrNull(EVALUATE_TIMEOUT_MS_0605) {
            suspendCancellableCoroutine { continuation ->
                var completed = false
                webView.evaluateJavascript(script) { raw ->
                    if (!completed && continuation.isActive) {
                        completed = true
                        continuation.resume(raw)
                    }
                }
                continuation.invokeOnCancellation { completed = true }
            }
        }

    private suspend fun loadAndEvaluate0605(
        webView: WebView,
        url: String,
        script: String,
        prepareTrip: Boolean,
        acceptUrl: (String) -> Boolean,
    ): UnifiedEvaluatedPage0605? = withTimeoutOrNull(PAGE_TIMEOUT_MS_0605) {
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            var completed = false

            fun finish(value: UnifiedEvaluatedPage0605?) {
                if (completed) return
                completed = true
                handler.removeCallbacksAndMessages(null)
                if (continuation.isActive) continuation.resume(value)
            }

            fun evaluate(view: WebView) {
                val finalUrl = view.url.orEmpty()
                if (!acceptUrl(finalUrl)) {
                    finish(null)
                    return
                }
                view.evaluateJavascript(script) { raw ->
                    finish(UnifiedEvaluatedPage0605(finalUrl, raw.orEmpty()))
                }
            }

            fun prepare(view: WebView, pass: Int) {
                if (!prepareTrip || pass >= PREPARE_PASSES_0605) {
                    evaluate(view)
                    return
                }
                view.evaluateJavascript(PREPARE_TRIP_DOM_0605) {
                    handler.postDelayed({ prepare(view, pass + 1) }, PREPARE_RETRY_MS_0605)
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    super.onReceivedError(view, request, error)
                    if (request.isForMainFrame) finish(null)
                }

                override fun onPageFinished(view: WebView, finalUrl: String) {
                    super.onPageFinished(view, finalUrl)
                    if (completed) return
                    handler.postDelayed({
                        if (completed) return@postDelayed
                        if (!acceptUrl(view.url.orEmpty())) finish(null) else prepare(view, 0)
                    }, PAGE_SETTLE_MS_0605)
                }
            }
            continuation.invokeOnCancellation {
                completed = true
                handler.removeCallbacksAndMessages(null)
                runCatching { webView.stopLoading() }
            }
            webView.loadUrl(url)
        }
    }

    private const val PAGE_TIMEOUT_MS_0605 = 20_000L
    private const val EVALUATE_TIMEOUT_MS_0605 = 6_000L
    private const val PAGE_SETTLE_MS_0605 = 850L
    private const val PREPARE_RETRY_MS_0605 = 450L
    private const val PREPARE_PASSES_0605 = 2
    private const val PUBLIC_SHARE_ATTEMPTS_0605 = 3
    private const val PUBLIC_SHARE_RETRY_MS_0605 = 350L
    private const val UNIFIED_FLIGHT_ATTEMPTS_0605 = 20
    private const val UNIFIED_FLIGHT_RETRY_MS_0605 = 250L

    private val PREPARE_TRIP_DOM_0605 = """
        (function() {
          const clean = (value) => String(value || '').replace(/\s+/g, ' ').trim().toLowerCase();
          const controls = Array.from(document.querySelectorAll(
            'button[aria-expanded="false"], [role="button"][aria-expanded="false"], button[data-testid], [role="button"][data-testid]'
          )).filter((node) => {
            const marker = clean(
              (node.innerText || '') + ' ' +
              (node.getAttribute('aria-label') || '') + ' ' +
              (node.getAttribute('data-testid') || '')
            );
            return /passenger|booking|reservation|passageir|reserva|more|expand|mostrar|ver mais/.test(marker);
          });
          controls.slice(0, 8).forEach((node) => {
            try { node.click(); } catch (_) {}
          });
          try { window.scrollTo(0, Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)); } catch (_) {}
          return controls.length;
        })();
    """.trimIndent()
}
