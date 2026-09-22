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
import br.com.mapeiaia.rotacerta.DiagnosticEventContext0507
import br.com.mapeiaia.rotacerta.DiagnosticModule0507
import br.com.mapeiaia.rotacerta.DiagnosticSeverity0507
import br.com.mapeiaia.rotacerta.SettingsRepository
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val passengerSegmentsResolved: Boolean = false,
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
    val stagedTrips0610: List<BlaBlaCollectorTrip> = emptyList(),
    val stagedLastUrl0610: String = "",
)

@Serializable
private data class UnifiedTripDetailEnvelope0605(
    val detail: BlaBlaDomTripDetail = BlaBlaDomTripDetail(),
    val editHref: String = "",
    val optionsHref: String = "",
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
    val departure = runCatching { LocalTime.parse(ride.departureTime) }.getOrNull()
    val arrival = runCatching { LocalTime.parse(ride.arrivalTime) }.getOrNull()
    val cutoff = arrival ?: departure ?: return true
    val cutoffDate = if (arrival != null && departure != null && arrival.isBefore(departure)) {
        today.plusDays(1)
    } else {
        today
    }
    // 0.1.612: keep the current ride through arrival + 1 hour so acquisition,
    // Agenda and Timeline share the same operational visibility semantics.
    return !cutoffDate.atTime(cutoff).plusHours(1).isBefore(today.atTime(now))
}

internal data class BlaBlaTargetedHtmlRefreshResult0607(
    val trip: BlaBlaCollectorTrip? = null,
    val errorCode: String = "",
    val operationalComplete: Boolean = false,
    val evidencePath: String = "",
)

internal fun globalHtmlAtomicCommitEligible0609(
    manifestResult: String,
    profileStatuses: List<String>,
    tripStatuses: List<String>,
    expectedAccountCount: Int,
    observedAccountCount: Int,
    stagedTripCount: Int,
    authoritySource: String,
): Boolean =
    manifestResult == "COMPLETE" &&
        expectedAccountCount > 0 &&
        observedAccountCount == expectedAccountCount &&
        profileStatuses.size == expectedAccountCount &&
        profileStatuses.all { it == BlaBlaRidesSnapshotStatus0526.COMPLETE } &&
        tripStatuses.all { it == "COMPLETE" } &&
        stagedTripCount == tripStatuses.size &&
        authoritySource == BlaBlaAcquisitionAuthority0607.HTML_DIRECT

internal fun htmlCanonicalResponseStatusAccepted0611(
    status: String,
    completeForScope: Boolean,
    unresolvedTargetCards: Int,
    authoritySource: String,
): Boolean =
    status.lowercase() in setOf("validated", "complete") &&
        completeForScope &&
        unresolvedTargetCards == 0 &&
        authoritySource == BlaBlaAcquisitionAuthority0607.HTML_DIRECT

internal object BlaBlaUnifiedHtmlCapture0605 {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    // 0.1.617: capture may run both isolated profiles concurrently, but canonical
    // publication remains serialized so TripStore/outbox read-modify-write stays deterministic.
    private val liveCardCommitMutex0617 = Mutex()

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

        val sessionStore = BlaBlaDynamicSessionStore(app)
        if (futureRides.isEmpty()) {
            store.updateProfile(captureId, account.id) { it.copy(tripCaptures0605 = emptyList()) }
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_UNIFIED_HTML_PROFILE_EMPTY_0605",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} accountKey=${store.accountKey(account.id)} futureTrips=0 privateStaging=true sessionStoreWrite=false",
            )
            return BlaBlaUnifiedProfileCaptureResult0605(
                futureTrips = 0,
                capturedTrips = 0,
                completeTrips = 0,
                incompleteTrips = 0,
                stagedTrips0610 = emptyList(),
                stagedLastUrl0610 = profile.finalUrl,
            )
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

                        val liveCommitted0617 =
                            captured.operationalComplete &&
                                captured.trip != null &&
                                publishLiveHtmlCard0617(
                                    context = app,
                                    account = account,
                                    trip = captured.trip,
                                    lastUrl = captured.evidence.finalUrl,
                                    captureId = captureId,
                                )
                        if (liveCommitted0617) {
                            onProgress(
                                "Atualizado agora • ${index + 1}/${futureRides.size} • " +
                                    "${ride.date} ${ride.departureTime} • ${account.displayLabel}",
                            )
                        }

                        UnifiedDebugEventStore.recordAlways(
                            "BLABLACAR_UNIFIED_HTML_TRIP_CAPTURED_0609",
                            app.packageName,
                            "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} " +
                                "accountKey=${store.accountKey(account.id)} trip=${index + 1}/${futureRides.size} " +
                                "complete=${captured.operationalComplete} liveCommitted0617=$liveCommitted0617 " +
                                "normalized=${captured.evidence.normalized} roster=${captured.evidence.passengerRosterComplete} " +
                                "itinerary=${captured.evidence.itineraryAuthoritative} " +
                                "segments=${captured.evidence.passengerSegmentsResolved} seats=${captured.evidence.publishedSeats != null} " +
                                "publicLink=${captured.evidence.publicTripUrl.isNotBlank()} " +
                                "errorCode=${captured.evidence.errorCode.ifBlank { "NONE" }} globalCommitFinalizer=true",
                            diagnosticContext = DiagnosticEventContext0507(
                                parentModule = DiagnosticModule0507.BLABLACAR,
                                operation = "HTML_TRIP_CAPTURE",
                                entityType = "BLABLACAR_TRIP",
                                entityId = seatSyncDiagnosticKey(definition.uuid + "|" + ride.tripId),
                                result = if (liveCommitted0617) {
                                    "LIVE_COMMITTED"
                                } else if (captured.operationalComplete) {
                                    "COMMIT_FAILED"
                                } else {
                                    "INCOMPLETE"
                                },
                                severity = if (captured.operationalComplete && !liveCommitted0617) {
                                    DiagnosticSeverity0507.ERROR
                                } else {
                                    DiagnosticSeverity0507.INFO
                                },
                                errorCode = captured.evidence.errorCode,
                            ),
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

        var indexError0612 = ""
        val existingIndex = store.read(captureId)
            ?.profiles
            ?.singleOrNull { it.accountKey == store.accountKey(account.id) }
            ?.let { current -> store.readRidesIndexJson0582(captureId, current) }
        if (existingIndex != null) {
            val byTrip = captures.associateBy(BlaBlaRidesTripCapture0605::tripId)
            val parsedByTrip = parsed.rides.associateBy(ParsedExternalRide0535::tripId)
            val links = existingIndex.tripIds.map { tripId ->
                val previous = existingIndex.tripLinks.singleOrNull { it.tripId == tripId }
                val capture = byTrip[tripId]
                if (capture != null) {
                    BlaBlaRidesTripLink0582(
                        tripId = tripId,
                        administrativeUrl = capture.administrativeUrl,
                        publicTripUrl = capture.publicTripUrl,
                        publicTripUrlSource = capture.publicTripUrlSource,
                        publicTripUrlBinding = capture.publicTripUrlBinding,
                        publicTripStatus = if (capture.publicTripUrl.isNotBlank()) "COMPLETE" else "PENDING_UNKNOWN",
                        shareEligibility = previous?.shareEligibility ?: "ELIGIBLE",
                    )
                } else {
                    val ride = parsedByTrip[tripId]
                    val administrativeUrl = BlaBlaCollectorUrlModule.absolute(ride?.administrativeUrl)
                    val terminalStatus = ride?.status.orEmpty()
                        .lowercase()
                        .replace("ã", "a")
                        .replace("á", "a")
                        .replace("ç", "c")
                    val explicitlyTerminal = listOf(
                        "cancelad", "concluid", "finalizad", "realizad", "arquivad",
                    ).any(terminalStatus::contains)
                    val provenNotCurrentOrFuture =
                        ride != null &&
                            (!shouldCaptureRide0605(ride, today, now) || explicitlyTerminal) &&
                            BlaBlaCollectorUrlModule.isSpecificTrip(administrativeUrl) &&
                            BlaBlaCollectorUrlModule.tripId(administrativeUrl) == tripId
                    when {
                        provenNotCurrentOrFuture -> BlaBlaRidesTripLink0582(
                            tripId = tripId,
                            administrativeUrl = administrativeUrl,
                            publicTripStatus = "NOT_REQUIRED_EXPIRED",
                            shareEligibility = "EXPIRED",
                        )
                        previous != null -> previous
                        else -> {
                            indexError0612 = "UNIFIED_RIDES_INDEX_UNRESOLVED_TRIP_LINK"
                            BlaBlaRidesTripLink0582(tripId = tripId)
                        }
                    }
                }
            }
            val rewritten = if (indexError0612.isBlank()) {
                store.rewriteRidesIndexTripLinks0582(captureId, account.id, expectedProfileUuid, links)
            } else {
                null
            }
            if (rewritten == null) {
                indexError0612 = indexError0612.ifBlank { "UNIFIED_RIDES_INDEX_LINK_REWRITE_FAILED" }
            }
        } else {
            indexError0612 = "UNIFIED_RIDES_INDEX_MISSING"
        }

        val captureIncomplete = captures.count { it.status != "COMPLETE" }
        val finalIncomplete = captureIncomplete + if (indexError0612.isNotBlank()) 1 else 0
        store.updateProfile(captureId, account.id) { previous ->
            if (finalIncomplete == 0) {
                previous.copy(tripCaptures0605 = captures.toList(), errorCode = "")
            } else {
                previous.copy(
                    tripCaptures0605 = captures.toList(),
                    status = BlaBlaRidesSnapshotStatus0526.INCOMPLETE,
                    errorCode = indexError0612.ifBlank { "UNIFIED_FUTURE_TRIPS_INCOMPLETE_$captureIncomplete" },
                )
            }
        }

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
            stagedTrips0610 = collected.toList(),
            stagedLastUrl0610 = captures.lastOrNull()?.finalUrl.orEmpty(),
        )
    }

    /**
     * 0.1.617 — live HTML card commit.
     *
     * A fully validated trip becomes canonical as soon as its own HTML finishes.
     * Sibling trips and other profiles are never tombstoned here: absence is only
     * authoritative in the final full-profile/global verification path.
     */
    private suspend fun publishLiveHtmlCard0617(
        context: Context,
        account: BlaBlaDynamicAccount,
        trip: BlaBlaCollectorTrip,
        lastUrl: String,
        captureId: String,
    ): Boolean {
        val app = context.applicationContext
        val transaction = BlaBlaHtmlCaptureTransaction0610.active(app)
            ?.takeIf { it.captureId == captureId }
            ?: return false
        val profileUuid = trip.profile_uuid.trim().takeIf(String::isNotBlank) ?: return false
        val tripId = trip.trip_id?.trim()?.takeIf(String::isNotBlank) ?: return false
        if (
            account.profileUuid?.trim()?.equals(profileUuid, ignoreCase = true) != true ||
            BlaBlaCollectorUrlModule.tripId(trip.trip_href.orEmpty()) != tripId ||
            !trip.passenger_roster_complete ||
            !trip.itinerary_authoritative ||
            trip.published_seats == null ||
            trip.public_trip_href.isNullOrBlank()
        ) {
            return false
        }

        val settings = withContext(Dispatchers.IO) {
            SettingsRepository(app).settings.first()
        }
        return withContext(Dispatchers.IO) {
            liveCardCommitMutex0617.withLock {
                val exactResponse = BlaBlaCollectorMonthResponse(
                    collected_at = Instant.now().toString(),
                    status = "validated",
                    strategy = "html_live_card_commit_0617",
                    authority_source_0607 = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
                    profiles = listOf(
                        BlaBlaCollectorProfile(
                            uuid = profileUuid,
                            name = account.displayLabel,
                            title = "HTML individual validado • commit imediato por card",
                        ),
                    ),
                    trips = listOf(trip),
                    coverage = BlaBlaCollectorCoverage(
                        complete_for_scope = false,
                        global_profile_month_complete = false,
                        reason = "live_html_card_commit_0617",
                        requested_queries = 1,
                        validated_queries = 1,
                        failed_or_mismatched_queries = 0,
                        unresolved_target_cards = 0,
                        past_dates_skipped = false,
                    ),
                )
                val tripStore = TripStore(app)
                val outbox = TripPublicationOutbox0387(app)
                val tripRollback = tripStore.snapshotHtmlRollback0612()
                val outboxRollback = outbox.snapshotHtmlRollback0612()

                val batch = runCatching {
                    AgendaBackgroundSync0392.reconcileCollectedExternalTrips0403(
                        context = app,
                        store = tripStore,
                        response = exactResponse,
                        rotaCertaSeatAllocation = settings.rotaCertaSeatAllocation,
                        seatAllocationVersion = settings.rotaCertaSeatAllocationVersion,
                        collectionRunId = "html-live-card-0617:" + captureId.take(48),
                        collectionGeneration = transaction.generation,
                        completeProfileUuids = emptySet(),
                        htmlTransactionCaptureId0610 = captureId,
                    )
                }.getOrElse { error ->
                    tripStore.restoreHtmlRollback0612(tripRollback)
                    outbox.restoreHtmlRollback0612(outboxRollback)
                    UnifiedDebugEventStore.recordAlways(
                        "BLABLACAR_LIVE_CARD_COMMIT_FAILED_0617",
                        app.packageName,
                        "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} " +
                            "tripKey=${seatSyncDiagnosticKey(profileUuid + "|" + tripId)} " +
                            "stage=canonical_reconcile error=${error::class.java.simpleName.take(80)} " +
                            "rollback=true preserveSiblings=true tombstone=false",
                        diagnosticContext = DiagnosticEventContext0507(
                            parentModule = DiagnosticModule0507.BLABLACAR,
                            operation = "HTML_LIVE_CARD_COMMIT",
                            entityType = "BLABLACAR_TRIP",
                            entityId = seatSyncDiagnosticKey(profileUuid + "|" + tripId),
                            result = "FAILED",
                            severity = DiagnosticSeverity0507.ERROR,
                            errorCode = "CANONICAL_RECONCILE_EXCEPTION",
                        ),
                    )
                    return@withLock false
                }

                val matches = tripStore.trips().filter { canonical ->
                    !canonical.deleted &&
                        canonical.externalSnapshotAuthority0607 == BlaBlaAcquisitionAuthority0607.HTML_DIRECT &&
                        canonical.blablaProfileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true &&
                        canonical.blablaTripId?.trim() == tripId
                }
                if (
                    matches.size != 1 ||
                    batch.blockedTrips != 0 ||
                    batch.staleResultsRejected != 0 ||
                    matches.singleOrNull()?.lastCollectionGeneration != transaction.generation
                ) {
                    val tripRestored = tripStore.restoreHtmlRollback0612(tripRollback)
                    val outboxRestored = outbox.restoreHtmlRollback0612(outboxRollback)
                    UnifiedDebugEventStore.recordAlways(
                        "BLABLACAR_LIVE_CARD_COMMIT_FAILED_0617",
                        app.packageName,
                        "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} " +
                            "tripKey=${seatSyncDiagnosticKey(profileUuid + "|" + tripId)} " +
                            "stage=canonical_readback matches=${matches.size} blocked=${batch.blockedTrips} " +
                            "stale=${batch.staleResultsRejected} tripRollback=$tripRestored outboxRollback=$outboxRestored " +
                            "preserveSiblings=true tombstone=false",
                        diagnosticContext = DiagnosticEventContext0507(
                            parentModule = DiagnosticModule0507.BLABLACAR,
                            operation = "HTML_LIVE_CARD_COMMIT",
                            entityType = "BLABLACAR_TRIP",
                            entityId = seatSyncDiagnosticKey(profileUuid + "|" + tripId),
                            result = "FAILED",
                            severity = DiagnosticSeverity0507.ERROR,
                            errorCode = "CANONICAL_READBACK_FAILED",
                        ),
                    )
                    return@withLock false
                }

                // Persist only after canonical readback succeeds. The exact-target session
                // replacement preserves every sibling card, so a partial capture cannot erase
                // another trip or another profile.
                val sessionStore = BlaBlaDynamicSessionStore(app)
                sessionStore.saveSync(
                    account = account,
                    lastUrl = lastUrl,
                    trips = listOf(trip),
                    skippedTrips = 0,
                    identityVerified = true,
                    targetedTripId = tripId,
                    selectiveScriptSync0449 = false,
                    acquisitionAuthority0607 = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
                )
                val combined = sessionStore.combinedResponse(BlaBlaDynamicAccountRegistry(app).list())
                BlaBlaCollectorStateStore(app).saveResponse(
                    response = combined,
                    preserveOnPartial = true,
                )

                BookingRealtimeEvents0356.notifyChanged()
                TripWidgetProvider.updateAll(app)
                UnifiedDebugEventStore.recordAlways(
                    "BLABLACAR_LIVE_CARD_COMMITTED_0617",
                    app.packageName,
                    "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} " +
                        "generation=${transaction.generation} tripKey=${seatSyncDiagnosticKey(profileUuid + "|" + tripId)} " +
                        "changed=${batch.changedTrips} unchanged=${batch.skippedTrips} " +
                        "publicationQueued=${batch.publicationQueued} preserveSiblings=true tombstone=false " +
                        "authority=HTML_DIRECT_0607 visibleImmediately=true",
                    diagnosticContext = DiagnosticEventContext0507(
                        parentModule = DiagnosticModule0507.BLABLACAR,
                        operation = "HTML_LIVE_CARD_COMMIT",
                        entityType = "BLABLACAR_TRIP",
                        entityId = seatSyncDiagnosticKey(profileUuid + "|" + tripId),
                        result = "COMMITTED",
                    ),
                )
                true
            }
        }
    }

    suspend fun captureSingleTrip0607(
        context: Context,
        target: BlaBlaTripTarget0407,
        existingSource: BlaBlaCollectorTrip?,
    ): BlaBlaTargetedHtmlRefreshResult0607 {
        val app = context.applicationContext
        BlaBlaHtmlCaptureTransaction0610.active(app)?.let { transaction ->
            UnifiedDebugEventStore.recordAlways(
                "TARGETED_HTML_BLOCKED_BY_GLOBAL_TRANSACTION_0610",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(transaction.captureId)} generation=${transaction.generation} targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} action=FAIL_CLOSED",
            )
            return BlaBlaTargetedHtmlRefreshResult0607(errorCode = "HTML_GLOBAL_TRANSACTION_ACTIVE_0610")
        }
        val account = BlaBlaDynamicAccountRegistry(app).get(target.accountId)
            ?.takeIf {
                it.profileUuid?.trim()?.equals(target.profileUuid.trim(), ignoreCase = true) == true
            }
            ?: return BlaBlaTargetedHtmlRefreshResult0607(errorCode = "HTML_TARGET_ACCOUNT_IDENTITY_MISMATCH")
        val definition = account.verifiedDefinition()
            ?: return BlaBlaTargetedHtmlRefreshResult0607(errorCode = "HTML_TARGET_ACCOUNT_UNVERIFIED")
        val administrativeUrl = BlaBlaCollectorUrlModule.absolute(target.tripHref)
        if (BlaBlaCollectorUrlModule.tripId(administrativeUrl) != target.tripId) {
            return BlaBlaTargetedHtmlRefreshResult0607(errorCode = "HTML_TARGET_TRIP_IDENTITY_MISMATCH")
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
            return BlaBlaTargetedHtmlRefreshResult0607(errorCode = "HTML_TARGET_SCRIPT_MISSING")
        }

        val sessionStore = BlaBlaDynamicSessionStore(app)
        val captureId = "targeted_" + Instant.now().toString().replace(":", "-") + "_" +
            seatSyncDiagnosticKey(target.tripId).replace(Regex("[^A-Za-z0-9._-]"), "").take(20)
        val lease = acquireUnifiedFlight0605(sessionStore, account, captureId)
            ?: return BlaBlaTargetedHtmlRefreshResult0607(errorCode = "HTML_TARGET_SINGLE_FLIGHT_BUSY")
        val source = existingSource
        val ride = ParsedExternalRide0535(
            tripId = target.tripId,
            listPosition = 0,
            date = source?.date.orEmpty().ifBlank { LocalDate.now().toString() },
            dateText = source?.date.orEmpty(),
            dateYearExplicit = true,
            dateResolution = "TARGETED_HTML_EXISTING_CANONICAL",
            departureTime = source?.departure_time.orEmpty(),
            arrivalTime = source?.arrival_time.orEmpty(),
            origin = source?.actual_departure.orEmpty().ifBlank { source?.search_from.orEmpty() },
            destination = source?.actual_arrival.orEmpty().ifBlank { source?.search_to.orEmpty() },
            status = "",
            administrativeUrl = administrativeUrl,
        )

        return try {
            val captured = withContext(Dispatchers.Main.immediate) {
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
                    captureTrip0605(
                        webView = webView,
                        store = BlaBlaRidesSnapshotStore0526(app),
                        captureId = captureId,
                        definition = definition,
                        ride = ride,
                        scripts = scripts,
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
            val trip = captured.trip
                ?: return BlaBlaTargetedHtmlRefreshResult0607(
                    errorCode = captured.evidence.errorCode.ifBlank { "HTML_TARGET_NORMALIZATION_FAILED" },
                    evidencePath = captured.evidence.htmlFile,
                )
            if (!captured.operationalComplete) {
                UnifiedDebugEventStore.recordAlways(
                    "TARGETED_HTML_INCOMPLETE_REJECTED_0615",
                    app.packageName,
                    "targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} evidencePathPresent=${captured.evidence.htmlFile.isNotBlank()} error=${captured.evidence.errorCode.take(120)} action=PRESERVE_LAST_VALIDATED_HTML canonicalWrite=false sessionWrite=false",
                )
                return BlaBlaTargetedHtmlRefreshResult0607(
                    errorCode = captured.evidence.errorCode.ifBlank { "HTML_TARGET_OPERATIONALLY_INCOMPLETE_0615" },
                    operationalComplete = false,
                    evidencePath = captured.evidence.htmlFile,
                )
            }

            sessionStore.saveSync(
                account = account,
                lastUrl = captured.evidence.finalUrl.ifBlank { administrativeUrl },
                trips = listOf(trip),
                skippedTrips = 0,
                identityVerified = true,
                dateScope = listOfNotNull(runCatching { LocalDate.parse(trip.date) }.getOrNull()),
                targetedTripId = target.tripId,
                selectiveScriptSync0449 = false,
                acquisitionAuthority0607 = BlaBlaAcquisitionAuthority0607.HTML_DIRECT,
            )
            val response = sessionStore.combinedResponse(BlaBlaDynamicAccountRegistry(app).list())
            BlaBlaCollectorStateStore(app).saveResponse(response, preserveOnPartial = false)
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_TARGETED_HTML_REFRESH_0607",
                app.packageName,
                "targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} normalized=true operationalComplete=true exactCardReset=true staleFieldInheritance=false evidencePathPresent=${captured.evidence.htmlFile.isNotBlank()} authority=HTML_DIRECT_0607 legacyCollector=false",
            )
            BlaBlaTargetedHtmlRefreshResult0607(
                trip = trip,
                operationalComplete = captured.operationalComplete,
                evidencePath = captured.evidence.htmlFile,
            )
        } finally {
            sessionStore.releaseExternalFlight0426(lease)
        }
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

        val publishedSeats = capturePublishedSeats0606(
            webView = webView,
            tripId = ride.tripId,
            administrativeUrl = administrativeUrl,
            editHref = detail.editHref,
            optionsHref = detail.optionsHref,
            editScript = scripts.edit,
            seatsScript = scripts.seats,
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

        val passengerSegmentsResolved = trip?.let { source ->
            val observedCapacity = source.published_seats ?: return@let false
            PublicAgendaAutoSync0300.toPublicTrip(
                source = source,
                capacity = observedCapacity,
                nowMillis = Long.MIN_VALUE,
                rotaCertaSeatAllocation = 0,
            )?.let { projection ->
                PublicAgendaAutoSync0300.externalPassengerSegmentsResolved(source, projection.trip)
            } == true
        } == true
        val operationalComplete =
            trip != null &&
                trip.passenger_roster_complete &&
                trip.itinerary_authoritative &&
                passengerSegmentsResolved &&
                trip.published_seats != null &&
                !trip.public_trip_href.isNullOrBlank()
        val missing = buildList {
            if (trip == null) add("NORMALIZATION")
            if (trip?.passenger_roster_complete != true) add("ROSTER")
            if (trip?.itinerary_authoritative != true) add("ITINERARY")
            if (!passengerSegmentsResolved) add("PASSENGER_SEGMENTS")
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
                passengerSegmentsResolved = passengerSegmentsResolved,
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

    private suspend fun capturePublishedSeats0606(
        webView: WebView,
        tripId: String,
        administrativeUrl: String,
        editHref: String,
        optionsHref: String,
        editScript: String,
        seatsScript: String,
    ): Int? {
        val origin = BlaBlaCollectorUrlModule.origin(administrativeUrl) ?: return null
        val canonicalEdit = BlaBlaCollectorUrlModule.absolute(editHref)
            .takeIf { BlaBlaCollectorUrlModule.editTripId(it) == tripId }
            ?: "$origin/rides/offer/edit/$tripId"
                .takeIf { BlaBlaCollectorUrlModule.editTripId(it) == tripId }
            ?: return null

        val directOptions = BlaBlaCollectorUrlModule.absolute(optionsHref)
            .takeIf { BlaBlaCollectorUrlModule.optionsTripId(it) == tripId }

        val optionsFromEdit = if (directOptions == null) {
            val editPage = loadAndEvaluate0605(webView, canonicalEdit, editScript, false) {
                BlaBlaCollectorUrlModule.editTripId(it) == tripId
            }
            val edit = decode0605<UnifiedEditEvidence0605>(editPage?.payload)
            BlaBlaCollectorUrlModule.absolute(edit?.optionsHref)
                .takeIf { BlaBlaCollectorUrlModule.optionsTripId(it) == tripId }
        } else {
            null
        }

        // The options route is a deterministic authenticated management route. Constructing
        // this URL never invents operational data: the page must still load under the exact
        // trip identity and seat_options.js must return a verified numeric value.
        val constructedOptions = "$origin/rides/offer/edit/$tripId/options"
            .takeIf { BlaBlaCollectorUrlModule.optionsTripId(it) == tripId }
        val optionsTarget = directOptions ?: optionsFromEdit ?: constructedOptions ?: return null

        val optionsPage = loadAndEvaluate0605(webView, optionsTarget, seatsScript, false) {
            BlaBlaCollectorUrlModule.optionsTripId(it) == tripId
        } ?: return null

        var seats = decode0605<UnifiedSeatEvidence0605>(optionsPage.payload)
        var retry = 0
        while ((seats?.seats ?: -1) < 0 && retry < SEAT_VALUE_RETRIES_0606) {
            retry++
            delay(SEAT_VALUE_RETRY_MS_0606)
            seats = decode0605(evaluateCurrent0605(webView, seatsScript))
        }
        val publishedSeats = seats?.seats?.takeIf { it >= 0 }
        val state = BlaBlaCollectorSeatModule.state(
            tripId = tripId,
            editHref = canonicalEdit,
            optionsHref = webView.url.orEmpty().ifBlank { optionsPage.finalUrl },
            publishedSeats = publishedSeats,
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

    internal suspend fun commitCompletedCapture0610(
        context: Context,
        accounts: List<BlaBlaDynamicAccount>,
        manifest: BlaBlaRidesSnapshotManifest0526,
        stagedByAccount: Map<String, BlaBlaUnifiedProfileCaptureResult0605>,
    ): Boolean {
        val app = context.applicationContext
        val transaction = BlaBlaHtmlCaptureTransaction0610.active(app)
        if (transaction == null || transaction.captureId != manifest.captureId) {
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_GLOBAL_HTML_COMMIT_BLOCKED_0610",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} reason=transaction_not_active preservePreviousCanonical=true",
            )
            return false
        }

        val profileStatuses = manifest.profiles.map(BlaBlaRidesSnapshotProfile0526::status)
        val tripStatuses = manifest.profiles.flatMap { profile ->
            profile.tripCaptures0605.map(BlaBlaRidesTripCapture0605::status)
        }
        val expectedTripCount = tripStatuses.size
        val stagedResults = accounts.mapNotNull { account ->
            stagedByAccount[account.id]?.let { account to it }
        }
        val stagedTrips = stagedResults.flatMap { it.second.stagedTrips0610 }
        val stagedStrongIdentities = stagedTrips.mapNotNull { trip ->
            val profileUuid = trip.profile_uuid.trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
            val tripId = trip.trip_id?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            profileUuid.lowercase() + "|" + tripId
        }
        val stagedComplete =
            stagedResults.size == accounts.size &&
                stagedResults.all { (_, result) ->
                    result.incompleteTrips == 0 &&
                        result.capturedTrips == result.completeTrips &&
                        result.stagedTrips0610.size == result.completeTrips
                } &&
                stagedTrips.size == expectedTripCount &&
                stagedStrongIdentities.size == expectedTripCount &&
                stagedStrongIdentities.distinct().size == expectedTripCount

        if (
            manifest.result != "COMPLETE" ||
            profileStatuses.size != accounts.size ||
            profileStatuses.any { it != BlaBlaRidesSnapshotStatus0526.COMPLETE } ||
            tripStatuses.any { it != "COMPLETE" } ||
            !stagedComplete
        ) {
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_GLOBAL_HTML_COMMIT_BLOCKED_0610",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} result=${manifest.result} profiles=${manifest.profiles.size}/${accounts.size} expectedTrips=$expectedTripCount stagedTrips=${stagedTrips.size} strongIdentities=${stagedStrongIdentities.distinct().size} reason=private_stage_incomplete preservePreviousCanonical=true",
            )
            return false
        }

        val sessionStore = BlaBlaDynamicSessionStore(app)
        val replacements = stagedResults.map { (account, result) ->
            BlaBlaHtmlSessionReplacement0610(
                account = account,
                trips = result.stagedTrips0610,
                lastUrl = result.stagedLastUrl0610,
            )
        }
        val response = sessionStore.replaceHtmlSnapshotsAtomically0610(replacements)
        val eligible = globalHtmlAtomicCommitEligible0609(
            manifestResult = manifest.result,
            profileStatuses = profileStatuses,
            tripStatuses = tripStatuses,
            expectedAccountCount = accounts.size,
            observedAccountCount = manifest.profiles.size,
            stagedTripCount = response.trips.size,
            authoritySource = response.authority_source_0607,
        )
        if (
            !eligible ||
            !htmlCanonicalResponseStatusAccepted0611(
                status = response.status,
                completeForScope = response.coverage.complete_for_scope,
                unresolvedTargetCards = response.coverage.unresolved_target_cards,
                authoritySource = response.authority_source_0607,
            ) ||
            response.trips.size != expectedTripCount
        ) {
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_GLOBAL_HTML_COMMIT_BLOCKED_0610",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} stage=session_replace status=${response.status} completeForScope=${response.coverage.complete_for_scope} unresolved=${response.coverage.unresolved_target_cards} expectedTrips=$expectedTripCount responseTrips=${response.trips.size} preservePreviousCanonical=true",
            )
            return false
        }

        val published = BlaBlaCollectorStateStore(app).saveResponse(response, preserveOnPartial = false)
        if (
            !htmlCanonicalResponseStatusAccepted0611(
                status = published.status,
                completeForScope = published.coverage.complete_for_scope,
                unresolvedTargetCards = published.coverage.unresolved_target_cards,
                authoritySource = published.authority_source_0607,
            ) ||
            published.trips.size != expectedTripCount
        ) {
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_GLOBAL_HTML_COMMIT_BLOCKED_0610",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} stage=state_store expectedTrips=$expectedTripCount publishedTrips=${published.trips.size} status=${published.status} completeForScope=${published.coverage.complete_for_scope} preservePreviousCanonical=true",
            )
            return false
        }

        val settings = SettingsRepository(app).settings.first()
        val tripStore = TripStore(app)
        val outbox = TripPublicationOutbox0387(app)
        val tripRollback0612 = tripStore.snapshotHtmlRollback0612()
        val outboxRollback0612 = outbox.snapshotHtmlRollback0612()
        val batch = try {
            AgendaBackgroundSync0392.reconcileCollectedExternalTrips0403(
                context = app,
                store = tripStore,
                response = published,
                rotaCertaSeatAllocation = settings.rotaCertaSeatAllocation,
                seatAllocationVersion = settings.rotaCertaSeatAllocationVersion,
                collectionRunId = "html-private-transaction-0610:" + manifest.captureId.take(48),
                collectionGeneration = transaction.generation,
                completeProfileUuids = accounts.mapNotNull {
                    it.profileUuid?.trim()?.lowercase()?.takeIf(String::isNotBlank)
                }.toSet(),
                htmlTransactionCaptureId0610 = manifest.captureId,
            )
        } catch (error: Throwable) {
            val tripRestored = tripStore.restoreHtmlRollback0612(tripRollback0612)
            val outboxRestored = outbox.restoreHtmlRollback0612(outboxRollback0612)
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_GLOBAL_HTML_ROLLBACK_0612",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} stage=reconcile_exception tripStoreRestored=$tripRestored outboxRestored=$outboxRestored error=${error::class.java.simpleName.take(80)}",
            )
            throw error
        }

        val canonicalTrips = tripStore.trips()
        data class Readback0612(
            val profileUuid: String,
            val tripId: String,
            val matches: List<Trip>,
        )
        val readback0612 = published.trips.map { source ->
            val profileUuid = source.profile_uuid.trim()
            val tripId = source.trip_id?.trim().orEmpty()
            Readback0612(
                profileUuid = profileUuid,
                tripId = tripId,
                matches = canonicalTrips.filter { trip ->
                    !trip.deleted &&
                        trip.externalSnapshotAuthority0607 == BlaBlaAcquisitionAuthority0607.HTML_DIRECT &&
                        trip.blablaProfileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true &&
                        trip.blablaTripId?.trim() == tripId
                },
            )
        }
        val canonicalized = readback0612.count { it.matches.size == 1 }
        val unresolved0612 = readback0612.filter { it.matches.size != 1 }
        val canonicalComplete =
            canonicalized == expectedTripCount &&
                unresolved0612.isEmpty() &&
                batch.blockedTrips == 0 &&
                batch.staleResultsRejected == 0 &&
                batch.missingPreserved == 0

        if (!canonicalComplete) {
            unresolved0612.take(24).forEach { unresolved ->
                UnifiedDebugEventStore.recordAlways(
                    "BLABLACAR_GLOBAL_HTML_CANONICAL_IDENTITY_FAILED_0612",
                    app.packageName,
                    "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} profileKey=${seatSyncDiagnosticKey(unresolved.profileUuid)} tripId=${unresolved.tripId.take(160)} activeMatches=${unresolved.matches.size} internalTripIds=${unresolved.matches.joinToString(",") { seatSyncDiagnosticKey(it.id) }.take(400)}",
                )
            }
            val tripRestored = tripStore.restoreHtmlRollback0612(tripRollback0612)
            val outboxRestored = outbox.restoreHtmlRollback0612(outboxRollback0612)
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_GLOBAL_HTML_CANONICAL_VALIDATION_FAILED_0610",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} htmlTrips=$expectedTripCount canonicalized=$canonicalized unresolved=${unresolved0612.size} blocked=${batch.blockedTrips} stale=${batch.staleResultsRejected} missingPreserved=${batch.missingPreserved} changed=${batch.changedTrips} skipped=${batch.skippedTrips} rollbackTripStore=$tripRestored rollbackOutbox=$outboxRestored",
            )
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_GLOBAL_HTML_ROLLBACK_0612",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} stage=canonical_readback tripStoreRestored=$tripRestored outboxRestored=$outboxRestored preservePreviousCanonical=${tripRestored && outboxRestored}",
            )
            return false
        }
        val delivered = TripMutationCoordinator0387(app, tripStore).drainPending(
            canonicalTripIds = batch.publicationCanonicalTripIds0431,
        )
        BookingRealtimeEvents0356.notifyChanged()
        TripWidgetProvider.updateAll(app)
        UnifiedDebugEventStore.recordAlways(
            "BLABLACAR_GLOBAL_HTML_COMMIT_0610",
            app.packageName,
            "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} generation=${transaction.generation} profiles=${accounts.size} htmlTrips=$expectedTripCount canonicalized=$canonicalized changed=${batch.changedTrips} unchanged=${batch.skippedTrips} tombstoned=${batch.tombstonedTrips} blocked=${batch.blockedTrips} conflicts=0 outboxDelivered=$delivered status=complete completeForScope=true skipped=0 canonicalDeltaEnqueued=false directReconcile=true commitCount=1",
        )
        return true
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
            if (value.firstOrNull() == '"') {
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
                if (!prepareTrip) {
                    evaluate(view)
                    return
                }
                view.evaluateJavascript(PREPARE_TRIP_DOM_0605) {
                    handler.postDelayed({
                        if (completed) return@postDelayed
                        view.evaluateJavascript(TRIP_READY_0606) { rawReady ->
                            val ready = rawReady?.trim()?.equals("true", ignoreCase = true) == true
                            if (ready || pass + 1 >= PREPARE_PASSES_0605) {
                                evaluate(view)
                            } else {
                                prepare(view, pass + 1)
                            }
                        }
                    }, PREPARE_RETRY_MS_0605)
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

    private const val PAGE_TIMEOUT_MS_0605 = 30_000L
    private const val EVALUATE_TIMEOUT_MS_0605 = 6_000L
    private const val PAGE_SETTLE_MS_0605 = 850L
    private const val PREPARE_RETRY_MS_0605 = 500L
    private const val PREPARE_PASSES_0605 = 16
    private const val SEAT_VALUE_RETRIES_0606 = 4
    private const val SEAT_VALUE_RETRY_MS_0606 = 450L
    private const val PUBLIC_SHARE_ATTEMPTS_0605 = 3
    private const val PUBLIC_SHARE_RETRY_MS_0605 = 350L
    private const val UNIFIED_FLIGHT_ATTEMPTS_0605 = 20
    private const val UNIFIED_FLIGHT_RETRY_MS_0605 = 250L

    private val TRIP_READY_0606 = """
        (function() {
          const text = String((document.body && document.body.innerText) || '').replace(/\s+/g, ' ').trim().toLowerCase();
          const summary = text.includes('resumo da viagem') ||
            text.includes('trip summary') ||
            text.includes('résumé du trajet') ||
            text.includes('resumen del viaje');
          const boundControl = !!document.querySelector(
            'a[href*="/rides/offer/edit/"], a[href*="/rides/offer/map"], a[href*="/rides/offer/passenger/"], a[href*="/trip?"]'
          );
          return summary && boundControl;
        })();
    """.trimIndent()

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
