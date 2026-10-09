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
    val passengerDetailsExpected0653: Int = 0,
    val passengerDetailsResolved0653: Int = 0,
    val passengerHtmlFiles0653: List<String> = emptyList(),
    val passengerHtmlArtifacts0658: List<BlaBlaRidesSnapshotFile0526> = emptyList(),
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
    val passengerHrefs: List<String> = emptyList(),
    val itineraryStops: List<String> = emptyList(),
    val itineraryStopTimes: List<String> = emptyList(),
    val itineraryAuthoritative: Boolean = false,
    val stopLocations: List<BlaBlaTripStopLocation0659> = emptyList(),
    val domHtml: String = "",
    val scriptError: String = "",
    val scriptStage: String = "",
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

@Serializable
private data class UnifiedPassengerOpenEvidence0653(
    val found: Boolean = false,
    val clicked: Boolean = false,
)

@Serializable
private data class UnifiedPassengerIdentityEvidence0653(
    val name: String = "",
    val photoUrl: String = "",
    val observedUuids: List<String> = emptyList(),
    val url: String = "",
)

@Serializable
private data class UnifiedPassengerContactEvidence0653(
    val phone: String = "",
    val visibleName: String = "",
    val fareAmount: String = "",
    val fareCurrencyCode: String = "",
    val callActionPresent: Boolean = false,
    val boardingAddress: String = "",
    val boardingLatitude: Double? = null,
    val boardingLongitude: Double? = null,
    val boardingAccuracyMeters: Double? = null,
    val boardingLocationSource: String = "",
    val domHtml: String = "",
)

@Serializable
private data class UnifiedPassengerFareEvidence0653(
    val driverReceives: String = "",
    val passengerTotal: String = "",
    val visibleAmounts: List<String> = emptyList(),
    val url: String = "",
)

@Serializable
private data class UnifiedPassengerSegmentEvidence0653(
    val boarding: String = "",
    val dropoff: String = "",
    val url: String = "",
)

@Serializable
private data class UnifiedPassengerReadyEvidence0659(
    val ready: Boolean = false,
    val rootInert: Boolean = false,
    val markerCount: Int = 0,
    val bodyLength: Int = 0,
    val url: String = "",
)

@Serializable
private data class UnifiedPassengerAddressEvidence0653(
    val specificAddresses: List<String> = emptyList(),
    val hasSpecificAddress: Boolean = false,
    val boardingAddress: String = "",
    val dropoffAddress: String = "",
    val url: String = "",
)

private data class UnifiedPassengerPageCapture0653(
    val finalUrl: String,
    val identity: UnifiedPassengerIdentityEvidence0653,
    val contact: UnifiedPassengerContactEvidence0653,
    val fare: UnifiedPassengerFareEvidence0653,
    val segment: UnifiedPassengerSegmentEvidence0653,
    val addresses: UnifiedPassengerAddressEvidence0653,
)

/**
 * Payment figures come only from distinct, explicitly labeled BlaBlaCar passenger HTML fields.
 * Never derive passenger payable from trip price, driver receipts or occupied seat counts.
 */
internal data class BlaBlaPassengerPaymentEvidence0764(
    val passengerTotalMinorUnits: Long? = null,
    val driverReceivesMinorUnits: Long? = null,
    val currencyCode: String = "",
)

private data class UnifiedPassengerDepthResult0653(
    val trip: BlaBlaCollectorTrip,
    val expected: Int,
    val resolved: Int,
    val htmlFiles: List<String>,
    val htmlArtifacts: List<BlaBlaRidesSnapshotFile0526>,
    val paymentEvidence0764: List<BlaBlaPassengerPaymentEvidence0764?> = emptyList(),
)

private data class UnifiedDirectScripts0605(
    val detail: String,
    val share: String,
    val edit: String,
    val seats: String,
    val passengerOpen: String,
    val passengerPrepare: String,
    val passengerReady: String,
    val passengerIdentity: String,
    val passengerContact: String,
    val passengerFare: String,
    val passengerSegment: String,
    val passengerAddresses: String,
)

private data class UnifiedEvaluatedPage0605(
    val finalUrl: String,
    val payload: String,
    val tripReady0721: Boolean = true,
)

private data class UnifiedCapturedTrip0605(
    val trip: BlaBlaCollectorTrip?,
    val evidence: BlaBlaRidesTripCapture0605,
    val operationalComplete: Boolean,
    val paymentEvidence0764: List<BlaBlaPassengerPaymentEvidence0764?> = emptyList(),
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
    val coreOperationalComplete0737: Boolean = false,
    val evidencePath: String = "",
    val paymentEvidence0764: List<BlaBlaPassengerPaymentEvidence0764?> = emptyList(),
)

internal enum class TargetedHtmlAcceptance0675 {
    FULL_COMMIT,
    CORE_COMMIT_PRIVATE_PENDING,
    REJECT,
}

internal fun targetedHtmlCoreOperationalComplete0675(
    evidence: BlaBlaRidesTripCapture0605,
): Boolean =
    evidence.tripId.isNotBlank() &&
        evidence.normalized &&
        evidence.passengerRosterComplete &&
        evidence.itineraryAuthoritative &&
        evidence.passengerSegmentsResolved &&
        evidence.publishedSeats != null &&
        evidence.publicTripUrl.isNotBlank()

internal fun targetedHtmlAcceptance0675(
    evidence: BlaBlaRidesTripCapture0605,
    operationalComplete: Boolean,
): TargetedHtmlAcceptance0675 = when {
    operationalComplete -> TargetedHtmlAcceptance0675.FULL_COMMIT
    targetedHtmlCoreOperationalComplete0675(evidence) ->
        TargetedHtmlAcceptance0675.CORE_COMMIT_PRIVATE_PENDING
    else -> TargetedHtmlAcceptance0675.REJECT
}

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

internal fun rebindParsedRidesToPersistedTripLinks0676(
    rides: List<ParsedExternalRide0535>,
    links: List<BlaBlaRidesTripLink0582>,
): List<ParsedExternalRide0535> {
    val exactByTrip0676 = links.mapNotNull { link0676 ->
        val absolute0676 = BlaBlaCollectorUrlModule.absolute(link0676.administrativeUrl)
        val linkedTripId0676 = BlaBlaCollectorUrlModule.tripId(absolute0676)
        if (
            linkedTripId0676 != link0676.tripId ||
            !BlaBlaCollectorUrlModule.isSpecificTrip(absolute0676)
        ) {
            null
        } else {
            link0676.tripId to BlaBlaCollectorUrlModule.canonical(absolute0676)
        }
    }.toMap()
    return rides.map { ride0676 ->
        val persisted0676 = exactByTrip0676[ride0676.tripId]
        if (persisted0676.isNullOrBlank()) ride0676
        else ride0676.copy(administrativeUrl = persisted0676)
    }
}

internal fun tripDetailVerificationError0676(
    detailPagePresent: Boolean,
    payloadPresent: Boolean,
    payloadDecoded: Boolean,
    expectedTripId: String,
    observedTripId: String?,
    domHtmlBytes: Int,
    detailReady0721: Boolean = true,
): String = when {
    !detailPagePresent -> "TRIP_DETAIL_LOAD_FAILED_0676"
    !detailReady0721 -> "TRIP_DETAIL_NOT_READY_0721"
    !payloadPresent || !payloadDecoded -> "TRIP_DETAIL_PAYLOAD_DECODE_FAILED_0676"
    observedTripId?.trim() != expectedTripId.trim() -> "TRIP_DETAIL_ID_MISMATCH_0676"
    domHtmlBytes <= 0 -> "TRIP_DETAIL_DOM_EMPTY_0676"
    else -> ""
}

internal fun shouldRetryTripDetailFailure0676(errorCode: String): Boolean =
    errorCode in setOf(
        "TRIP_DETAIL_LOAD_FAILED_0676",
        "TRIP_DETAIL_PAYLOAD_DECODE_FAILED_0676",
        "TRIP_DETAIL_ID_MISMATCH_0676",
        "TRIP_DETAIL_DOM_EMPTY_0676",
        "TRIP_DETAIL_NOT_READY_0721",
    )

internal object BlaBlaUnifiedHtmlCapture0605 {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    // 0.1.618: capture may run both isolated profiles concurrently; canonical
    // publication remains serialized, while partial-card absence scans are skipped to keep commits bounded.
    private val liveCardCommitMutex0617 = Mutex()

    suspend fun captureProfile(
        context: Context,
        store: BlaBlaRidesSnapshotStore0526,
        account: BlaBlaDynamicAccount,
        captureId: String,
        profile: BlaBlaRidesSnapshotProfile0526,
        onProgress: (String) -> Unit = {},
        targetDate0661: LocalDate? = null,
        scopedStateIsolation0662: Boolean = false,
        transaction0610: BlaBlaHtmlCaptureTransactionState0610? = null,
    ): BlaBlaUnifiedProfileCaptureResult0605 {
        val app = context.applicationContext
        transaction0610?.let { BlaBlaHtmlCaptureTransaction0610.heartbeat(app, it) }
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

        val persistedIndex0676 = store.readRidesIndexJson0582(captureId, profile)
        val parsedRides0676 = rebindParsedRidesToPersistedTripLinks0676(
            rides = parsed.rides,
            links = persistedIndex0676?.tripLinks.orEmpty(),
        )
        val today = LocalDate.now()
        val now = LocalTime.now()
        val futureRides = parsedRides0676
            .filter { shouldCaptureRide0605(it, today, now) }
            .filter { ride ->
                targetDate0661 == null || runCatching { LocalDate.parse(ride.date) }.getOrNull() == targetDate0661
            }
            .filter { ride ->
                val absolute = BlaBlaCollectorUrlModule.absolute(ride.administrativeUrl)
                BlaBlaCollectorUrlModule.isSpecificTrip(absolute) &&
                    BlaBlaCollectorUrlModule.tripId(absolute) == ride.tripId
            }
            .sortedWith(compareBy<ParsedExternalRide0535>({ it.date }, { it.departureTime }, { it.listPosition }))

        val sessionStore = BlaBlaDynamicSessionStore(app)
        val liveSettings0617 = withContext(Dispatchers.IO) {
            SettingsRepository(app).settings.first()
        }
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
                passengerOpen = readAsset0605(app, "blablacar/scripts/passenger_open.js"),
                passengerPrepare = readAsset0605(app, "blablacar/scripts/passenger_prepare.js"),
                passengerReady = readAsset0605(app, "blablacar/scripts/passenger_ready.js"),
                passengerIdentity = readAsset0605(app, "blablacar/scripts/passenger_identity.js"),
                passengerContact = readAsset0605(app, "blablacar/scripts/passenger_contact.js"),
                passengerFare = readAsset0605(app, "blablacar/scripts/passenger_fare.js"),
                passengerSegment = readAsset0605(app, "blablacar/scripts/passenger_segment.js"),
                passengerAddresses = readAsset0605(app, "blablacar/scripts/passenger_addresses.js"),
            )
        }
        if (
            listOf(
                scripts.detail,
                scripts.share,
                scripts.edit,
                scripts.seats,
                scripts.passengerOpen,
                scripts.passengerPrepare,
                scripts.passengerReady,
                scripts.passengerIdentity,
                scripts.passengerContact,
                scripts.passengerFare,
                scripts.passengerSegment,
                scripts.passengerAddresses,
            ).any(String::isBlank)
        ) {
            return failedProfile0605(store, account, captureId, "UNIFIED_CAPTURE_SCRIPT_MISSING")
        }

        val lease = acquireUnifiedFlight0605(sessionStore, account, captureId)
            ?: return failedFutureTrips0605(store, account, captureId, futureRides, "UNIFIED_SINGLE_FLIGHT_BUSY")

        val captures = mutableListOf<BlaBlaRidesTripCapture0605>()
        val collected = mutableListOf<BlaBlaCollectorTrip>()
        val deferredNotReady0721 = mutableListOf<Pair<Int, ParsedExternalRide0535>>()
        var incomplete = 0
        var transportRecoveryExhausted0621 = false
        var unattemptedDueTransport0621 = 0
        try {
            withContext(Dispatchers.Main.immediate) {
                var webView = createUnifiedCaptureWebView0621(app, account)
                try {
                    for ((index, ride) in futureRides.withIndex()) {
                        transaction0610?.let { BlaBlaHtmlCaptureTransaction0610.heartbeat(app, it) }
                        onProgress("Capturando viagem ${index + 1}/${futureRides.size} • ${ride.date} ${ride.departureTime} • ${account.displayLabel}")
                        var captured = captureTrip0605(webView, store, captureId, definition, ride, scripts)
                        var recoveryAttempt0621 = 0
                        while (
                            !captured.operationalComplete &&
                            shouldRetryTripDetailFailure0676(captured.evidence.errorCode) &&
                            recoveryAttempt0621 < TRANSPORT_RECOVERY_ATTEMPTS_0621
                        ) {
                            recoveryAttempt0621++
                            transaction0610?.let { BlaBlaHtmlCaptureTransaction0610.heartbeat(app, it) }
                            val backoffMs = TRANSPORT_RECOVERY_BACKOFF_MS_0621 * recoveryAttempt0621
                            UnifiedDebugEventStore.recordAlways(
                                "BLABLACAR_HTML_TRANSPORT_RECOVERY_0621",
                                app.packageName,
                                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} " +
                                    "accountKey=${store.accountKey(account.id)} trip=${index + 1}/${futureRides.size} " +
                                    "attempt=$recoveryAttempt0621 action=RECYCLE_WEBVIEW_RETRY_SAME_CARD backoffMs=$backoffMs " +
                                    "preserveLastValidated=true advanceToNextCard=false",
                            )
                            destroyUnifiedCaptureWebView0621(webView)
                            delay(backoffMs)
                            webView = createUnifiedCaptureWebView0621(app, account)
                            captured = captureTrip0605(webView, store, captureId, definition, ride, scripts)
                            transaction0610?.let { BlaBlaHtmlCaptureTransaction0610.heartbeat(app, it) }
                        }

                        transaction0610?.let { BlaBlaHtmlCaptureTransaction0610.heartbeat(app, it) }
                        val recoveredAfterTransport0621 =
                            captured.operationalComplete && recoveryAttempt0621 > 0
                        if (recoveredAfterTransport0621) {
                            UnifiedDebugEventStore.recordAlways(
                                "BLABLACAR_HTML_TRANSPORT_RECOVERED_0621",
                                app.packageName,
                                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} " +
                                    "accountKey=${store.accountKey(account.id)} trip=${index + 1}/${futureRides.size} " +
                                    "attempts=$recoveryAttempt0621 sameCard=true resumeSequence=true",
                            )
                        }

                        captures += captured.evidence
                        captured.trip?.let(collected::add)
                        if (!captured.operationalComplete) incomplete++
                        if (captured.evidence.errorCode == "TRIP_DETAIL_NOT_READY_0721") {
                            deferredNotReady0721 += index to ride
                        }

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
                                    rotaCertaSeatAllocation = liveSettings0617.rotaCertaSeatAllocation,
                                    seatAllocationVersion = liveSettings0617.rotaCertaSeatAllocationVersion,
                                    scopedStateIsolation0662 = scopedStateIsolation0662,
                                    expectedTransaction0610 = transaction0610,
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
                                "transportRecoveryAttempts0621=$recoveryAttempt0621 " +
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

                        if (
                            !captured.operationalComplete &&
                            shouldRetryTripDetailFailure0676(captured.evidence.errorCode) &&
                            recoveryAttempt0621 >= TRANSPORT_RECOVERY_ATTEMPTS_0621
                        ) {
                            transportRecoveryExhausted0621 = true
                            unattemptedDueTransport0621 = 0
                            UnifiedDebugEventStore.recordAlways(
                                "BLABLACAR_HTML_TRANSPORT_RECOVERY_EXHAUSTED_0621",
                                app.packageName,
                                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} " +
                                    "accountKey=${store.accountKey(account.id)} failedTrip=${index + 1}/${futureRides.size} " +
                                    "recoveryAttempts=$recoveryAttempt0621 remainingUnattempted=0 " +
                                    "action=SKIP_FAILED_CARD_CONTINUE_PROFILE preserveLastValidated=true cascadePrevented=true",
                                diagnosticContext = DiagnosticEventContext0507(
                                    parentModule = DiagnosticModule0507.BLABLACAR,
                                    operation = "HTML_TRANSPORT_RECOVERY",
                                    entityType = "BLABLACAR_PROFILE",
                                    entityId = store.accountKey(account.id),
                                    result = "INCOMPLETE",
                                    severity = DiagnosticSeverity0507.WARNING,
                                    errorCode = "HTML_TRANSPORT_RECOVERY_EXHAUSTED_0621",
                                ),
                            )
                            if (index + 1 < futureRides.size) {
                                destroyUnifiedCaptureWebView0621(webView)
                                webView = createUnifiedCaptureWebView0621(app, account)
                            }
                        }
                    }

                    if (deferredNotReady0721.isNotEmpty()) {
                        destroyUnifiedCaptureWebView0621(webView)
                        delay(DEFERRED_NOT_READY_BACKOFF_MS_0721)
                        webView = createUnifiedCaptureWebView0621(app, account)
                        deferredNotReady0721.forEachIndexed { retryIndex0721, pair0721 ->
                            val captureIndex0721 = pair0721.first
                            val ride0721 = pair0721.second
                            transaction0610?.let { BlaBlaHtmlCaptureTransaction0610.heartbeat(app, it) }
                            onProgress(
                                "Revalidando carregamento " + (retryIndex0721 + 1) + "/" +
                                    deferredNotReady0721.size + " • " + ride0721.date + " " +
                                    ride0721.departureTime + " • " + account.displayLabel,
                            )
                            val recaptured0721 = captureTrip0605(
                                webView = webView,
                                store = store,
                                captureId = captureId,
                                definition = definition,
                                ride = ride0721,
                                scripts = scripts,
                            )
                            captures[captureIndex0721] = recaptured0721.evidence
                            store.updateProfile(captureId, account.id) { previous ->
                                previous.copy(tripCaptures0605 = captures.toList())
                            }
                            if (recaptured0721.operationalComplete && recaptured0721.trip != null) {
                                incomplete = (incomplete - 1).coerceAtLeast(0)
                                collected.removeAll { it.trip_id == ride0721.tripId }
                                collected += recaptured0721.trip
                                val deferredCommitted0721 = publishLiveHtmlCard0617(
                                    context = app,
                                    account = account,
                                    trip = recaptured0721.trip,
                                    lastUrl = recaptured0721.evidence.finalUrl,
                                    captureId = captureId,
                                    rotaCertaSeatAllocation = liveSettings0617.rotaCertaSeatAllocation,
                                    seatAllocationVersion = liveSettings0617.rotaCertaSeatAllocationVersion,
                                    scopedStateIsolation0662 = scopedStateIsolation0662,
                                    expectedTransaction0610 = transaction0610,
                                )
                                UnifiedDebugEventStore.recordAlways(
                                    "BLABLACAR_TRIP_DETAIL_DEFERRED_RECOVERED_0721",
                                    app.packageName,
                                    "captureId=" + BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId) +
                                        " accountKey=" + store.accountKey(account.id) +
                                        " trip=" + (captureIndex0721 + 1) + "/" + futureRides.size +
                                        " liveCommitted=" + deferredCommitted0721 +
                                        " sameCard=true freshWebView=true",
                                )
                            } else {
                                UnifiedDebugEventStore.recordAlways(
                                    "BLABLACAR_TRIP_DETAIL_DEFERRED_STILL_PENDING_0721",
                                    app.packageName,
                                    "captureId=" + BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId) +
                                        " accountKey=" + store.accountKey(account.id) +
                                        " trip=" + (captureIndex0721 + 1) + "/" + futureRides.size +
                                        " errorCode=" + recaptured0721.evidence.errorCode.ifBlank { "UNKNOWN" } +
                                        " preservePreviousCanonical=true",
                                )
                            }
                            if (retryIndex0721 + 1 < deferredNotReady0721.size) {
                                destroyUnifiedCaptureWebView0621(webView)
                                delay(DEFERRED_NOT_READY_BETWEEN_CARDS_MS_0721)
                                webView = createUnifiedCaptureWebView0621(app, account)
                            }
                        }
                    }
                } finally {
                    destroyUnifiedCaptureWebView0621(webView)
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
                if (capture != null && capture.status == "COMPLETE") {
                    BlaBlaRidesTripLink0582(
                        tripId = tripId,
                        administrativeUrl = capture.administrativeUrl,
                        publicTripUrl = capture.publicTripUrl,
                        publicTripUrlSource = capture.publicTripUrlSource,
                        publicTripUrlBinding = capture.publicTripUrlBinding,
                        publicTripStatus = if (capture.publicTripUrl.isNotBlank()) "COMPLETE" else "PENDING_UNKNOWN",
                        shareEligibility = previous?.shareEligibility ?: "ELIGIBLE",
                    )
                } else if (capture != null && previous != null) {
                    previous
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
        val finalIncomplete =
            captureIncomplete + unattemptedDueTransport0621 + if (indexError0612.isNotBlank()) 1 else 0
        store.updateProfile(captureId, account.id) { previous ->
            if (finalIncomplete == 0) {
                previous.copy(tripCaptures0605 = captures.toList(), errorCode = "")
            } else {
                previous.copy(
                    tripCaptures0605 = captures.toList(),
                    status = BlaBlaRidesSnapshotStatus0526.INCOMPLETE,
                    errorCode = when {
                        indexError0612.isNotBlank() -> indexError0612
                        transportRecoveryExhausted0621 -> "UNIFIED_TRANSPORT_RECOVERY_EXHAUSTED_0621"
                        else -> "UNIFIED_FUTURE_TRIPS_INCOMPLETE_$captureIncomplete"
                    },
                )
            }
        }

        UnifiedDebugEventStore.recordAlways(
            "BLABLACAR_UNIFIED_HTML_PROFILE_COMPLETED_0605",
            app.packageName,
            "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} accountKey=${store.accountKey(account.id)} futureTrips=${futureRides.size} captured=${captures.size} normalized=${collected.size} complete=${captures.count { it.status == "COMPLETE" }} incomplete=$finalIncomplete unattemptedDueTransport0621=$unattemptedDueTransport0621 transportRecoveryExhausted0621=$transportRecoveryExhausted0621 directWebView=true orchestrator=false automaticCollector=false",
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
        rotaCertaSeatAllocation: Int,
        seatAllocationVersion: Long,
        scopedStateIsolation0662: Boolean = false,
        expectedTransaction0610: BlaBlaHtmlCaptureTransactionState0610? = null,
    ): Boolean {
        val app = context.applicationContext
        val transaction = if (expectedTransaction0610 != null) {
            BlaBlaHtmlCaptureTransaction0610.heartbeat(app, expectedTransaction0610)
        } else {
            BlaBlaHtmlCaptureTransaction0610.active(app)
        }?.takeIf { it.captureId == captureId }
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
                        rotaCertaSeatAllocation = rotaCertaSeatAllocation,
                        seatAllocationVersion = seatAllocationVersion,
                        collectionRunId = "html-live-card-0617:" + captureId.take(48),
                        collectionGeneration = transaction.generation,
                        completeProfileUuids = emptySet(),
                        htmlTransactionCaptureId0610 = captureId,
                        evaluateAbsentTrips0618 = false,
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

                if (!scopedStateIsolation0662) {
                    // Full/global capture may maintain the aggregate cache. Scoped captures
                    // are forbidden from reconstructing unrelated account/trip state.
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
                } else {
                    UnifiedDebugEventStore.recordAlways(
                        "BLABLACAR_SCOPED_SESSION_PROJECTION_BLOCKED_0662",
                        app.packageName,
                        "scope=SCOPED_HTML tripKey=${seatSyncDiagnosticKey(profileUuid + "|" + tripId)} " +
                            "combinedResponse=false sessionContentWrite=false canonicalHtmlOnly=true",
                    )
                }

                val publicParityStartedNs0620 = System.nanoTime()
                val targetPublicationIds0620 = batch.publicationCanonicalTripIds0431
                    .ifEmpty {
                        matches.singleOrNull()
                            ?.let { canonical -> setOf(canonical.tripKey.ifBlank { canonical.id }) }
                            .orEmpty()
                    }
                val publicDelivered0620 = if (
                    batch.changedTrips > 0 &&
                    batch.publicationQueued > 0 &&
                    targetPublicationIds0620.isNotEmpty()
                ) {
                    runCatching {
                        TripMutationCoordinator0387(app, tripStore).drainPending(
                            limit = targetPublicationIds0620.size.coerceAtLeast(1),
                            canonicalTripIds = targetPublicationIds0620,
                        )
                    }.getOrElse { error ->
                        UnifiedDebugEventStore.recordAlways(
                            "BLABLACAR_LIVE_CARD_PUBLIC_PARITY_FAILED_0620",
                            app.packageName,
                            "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} " +
                                "generation=${transaction.generation} tripKey=${seatSyncDiagnosticKey(profileUuid + "|" + tripId)} " +
                                "changed=${batch.changedTrips} publicationQueued=${batch.publicationQueued} " +
                                "error=${error.javaClass.simpleName.take(80)} immediateRetry=false outboxPreserved=true",
                            diagnosticContext = DiagnosticEventContext0507(
                                parentModule = DiagnosticModule0507.BLABLACAR,
                                operation = "HTML_LIVE_CARD_PUBLIC_PARITY",
                                entityType = "BLABLACAR_TRIP",
                                entityId = seatSyncDiagnosticKey(profileUuid + "|" + tripId),
                                result = "FAILED",
                                severity = DiagnosticSeverity0507.ERROR,
                                errorCode = "PUBLIC_PARITY_DELIVERY_FAILED",
                            ),
                        )
                        0
                    }
                } else {
                    0
                }
                val publicParityRequired0620 = batch.changedTrips > 0 && batch.publicationQueued > 0
                val publicParityConfirmed0620 =
                    !publicParityRequired0620 || publicDelivered0620 >= targetPublicationIds0620.size
                val publicParityDurationMs0620 =
                    ((System.nanoTime() - publicParityStartedNs0620).coerceAtLeast(0L)) / 1_000_000L

                UnifiedDebugEventStore.recordAlways(
                    "BLABLACAR_LIVE_CARD_PUBLIC_PARITY_0620",
                    app.packageName,
                    "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} " +
                        "generation=${transaction.generation} tripKey=${seatSyncDiagnosticKey(profileUuid + "|" + tripId)} " +
                        "changed=${batch.changedTrips} publicationQueued=${batch.publicationQueued} " +
                        "targetPublications=${targetPublicationIds0620.size} delivered=$publicDelivered0620 " +
                        "parityRequired=$publicParityRequired0620 parityConfirmed=$publicParityConfirmed0620 " +
                        "durationMs=$publicParityDurationMs0620 waitForGlobalBatch=false",
                    diagnosticContext = DiagnosticEventContext0507(
                        parentModule = DiagnosticModule0507.BLABLACAR,
                        operation = "HTML_LIVE_CARD_PUBLIC_PARITY",
                        entityType = "BLABLACAR_TRIP",
                        entityId = seatSyncDiagnosticKey(profileUuid + "|" + tripId),
                        result = if (publicParityConfirmed0620) "CONFIRMED" else "PENDING_RETRY",
                        severity = if (publicParityConfirmed0620) {
                            DiagnosticSeverity0507.INFO
                        } else {
                            DiagnosticSeverity0507.ERROR
                        },
                        errorCode = if (publicParityConfirmed0620) "" else "PUBLIC_PARITY_NOT_CONFIRMED",
                    ),
                )

                BookingRealtimeEvents0356.notifyChanged()
                TripWidgetProvider.updateAll(app)
                UnifiedDebugEventStore.recordAlways(
                    "BLABLACAR_LIVE_CARD_COMMITTED_0617",
                    app.packageName,
                    "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(captureId)} " +
                        "generation=${transaction.generation} tripKey=${seatSyncDiagnosticKey(profileUuid + "|" + tripId)} " +
                        "changed=${batch.changedTrips} unchanged=${batch.skippedTrips} " +
                        "publicationQueued=${batch.publicationQueued} publicDelivered=$publicDelivered0620 " +
                        "publicParityConfirmed=$publicParityConfirmed0620 preserveSiblings=true tombstone=false " +
                        "authority=HTML_DIRECT_0607 visibleImmediately=true waitForGlobalBatch=false",
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
        scopedStateIsolation0662: Boolean = false,
        persistPrivateMetadata0653: Boolean = true,
    ): BlaBlaTargetedHtmlRefreshResult0607 {
        val app = context.applicationContext
        val activeGlobalTransaction0726 = BlaBlaHtmlCaptureTransaction0610.active(app)
        if (activeGlobalTransaction0726 != null) {
            UnifiedDebugEventStore.recordAlways(
                "TARGETED_HTML_GLOBAL_ARBITRATION_0726",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(activeGlobalTransaction0726.captureId)} " +
                    "generation=${activeGlobalTransaction0726.generation} targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} " +
                    "action=ACCOUNT_PROFILE_LEASE globalBlanketBlock=false scopedIsolation=$scopedStateIsolation0662",
            )
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
                passengerOpen = readAsset0605(app, "blablacar/scripts/passenger_open.js"),
                passengerPrepare = readAsset0605(app, "blablacar/scripts/passenger_prepare.js"),
                passengerReady = readAsset0605(app, "blablacar/scripts/passenger_ready.js"),
                passengerIdentity = readAsset0605(app, "blablacar/scripts/passenger_identity.js"),
                passengerContact = readAsset0605(app, "blablacar/scripts/passenger_contact.js"),
                passengerFare = readAsset0605(app, "blablacar/scripts/passenger_fare.js"),
                passengerSegment = readAsset0605(app, "blablacar/scripts/passenger_segment.js"),
                passengerAddresses = readAsset0605(app, "blablacar/scripts/passenger_addresses.js"),
            )
        }
        if (
            listOf(
                scripts.detail,
                scripts.share,
                scripts.edit,
                scripts.seats,
                scripts.passengerOpen,
                scripts.passengerPrepare,
                scripts.passengerReady,
                scripts.passengerIdentity,
                scripts.passengerContact,
                scripts.passengerFare,
                scripts.passengerSegment,
                scripts.passengerAddresses,
            ).any(String::isBlank)
        ) {
            return BlaBlaTargetedHtmlRefreshResult0607(errorCode = "HTML_TARGET_SCRIPT_MISSING")
        }

        val sessionStore = BlaBlaDynamicSessionStore(app)
        val captureId = "targeted_" + Instant.now().toString().replace(":", "-") + "_" +
            seatSyncDiagnosticKey(target.tripId).replace(Regex("[^A-Za-z0-9._-]"), "").take(20)
        val lease = acquireUnifiedFlight0605(sessionStore, account, captureId)
            ?: run {
                UnifiedDebugEventStore.recordAlways(
                    "TARGETED_HTML_ACCOUNT_LEASE_PENDING_0726",
                    app.packageName,
                    "targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} " +
                        "accountKey=${BlaBlaRidesSnapshotStore0526(app).accountKey(account.id)} " +
                        "retryable=true globalTransactionActive=${activeGlobalTransaction0726 != null}",
                )
                return BlaBlaTargetedHtmlRefreshResult0607(errorCode = "HTML_TARGET_SINGLE_FLIGHT_BUSY")
            }
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
                        persistPrivateMetadata0653 = persistPrivateMetadata0653,
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
            val acceptance0675 = targetedHtmlAcceptance0675(
                evidence = captured.evidence,
                operationalComplete = captured.operationalComplete,
            )
            if (acceptance0675 == TargetedHtmlAcceptance0675.REJECT) {
                UnifiedDebugEventStore.recordAlways(
                    "TARGETED_HTML_INCOMPLETE_REJECTED_0615",
                    app.packageName,
                    "targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} evidencePathPresent=${captured.evidence.htmlFile.isNotBlank()} error=${captured.evidence.errorCode.take(120)} action=PRESERVE_LAST_VALIDATED_HTML canonicalWrite=false sessionWrite=false coreOperationalComplete0675=false",
                )
                return BlaBlaTargetedHtmlRefreshResult0607(
                    errorCode = captured.evidence.errorCode.ifBlank { "HTML_TARGET_OPERATIONALLY_INCOMPLETE_0615" },
                    operationalComplete = false,
                    evidencePath = captured.evidence.htmlFile,
                )
            }
            if (acceptance0675 == TargetedHtmlAcceptance0675.CORE_COMMIT_PRIVATE_PENDING) {
                UnifiedDebugEventStore.recordAlways(
                    "TARGETED_HTML_PRIVATE_PENDING_ACCEPTED_0675",
                    app.packageName,
                    "targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} " +
                        "coreOperationalComplete0675=true privateEnrichmentComplete=false " +
                        "missing=PASSENGER_DETAILS canonicalWrite=true sessionContentWrite=false " +
                        "scope=TRIP_ONLY preserveLastPrivateValues=true evidencePathPresent=${captured.evidence.htmlFile.isNotBlank()}",
                )
            }

            if (!scopedStateIsolation0662 && acceptance0675 == TargetedHtmlAcceptance0675.FULL_COMMIT) {
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
            } else if (!scopedStateIsolation0662) {
                UnifiedDebugEventStore.recordAlways(
                    "TARGETED_HTML_PARTIAL_SESSION_ISOLATED_0675",
                    app.packageName,
                    "targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} " +
                        "coreOperationalComplete0675=true privateEnrichmentComplete=false " +
                        "sessionContentWrite=false canonicalCallerMayCommit=true",
                )
            } else {
                UnifiedDebugEventStore.recordAlways(
                    "BLABLACAR_TARGETED_SCOPE_ISOLATED_0662",
                    app.packageName,
                    "targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} scope=TRIP_ONLY " +
                        "combinedResponse=false sessionContentWrite=false canonicalHtmlOnly=true " +
                        "privateEnrichmentPending=${acceptance0675 == TargetedHtmlAcceptance0675.CORE_COMMIT_PRIVATE_PENDING}",
                )
            }
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_TARGETED_HTML_REFRESH_0607",
                app.packageName,
                "targetKey=${seatSyncDiagnosticKey(target.strongIdentityKey)} normalized=true " +
                    "operationalComplete=${captured.operationalComplete} coreOperationalComplete0675=true " +
                    "privateEnrichmentComplete=${captured.operationalComplete} " +
                    "exactCardReset=true staleFieldInheritance=false evidencePathPresent=${captured.evidence.htmlFile.isNotBlank()} " +
                    "authority=HTML_DIRECT_0607 legacyCollector=false scopedIsolation0662=$scopedStateIsolation0662",
            )
            BlaBlaTargetedHtmlRefreshResult0607(
                trip = trip,
                operationalComplete = captured.operationalComplete,
                coreOperationalComplete0737 = targetedHtmlCoreOperationalComplete0675(captured.evidence),
                evidencePath = captured.evidence.htmlFile,
                paymentEvidence0764 = captured.paymentEvidence0764,
            )
        } finally {
            sessionStore.releaseExternalFlight0426(lease)
        }
    }

    private fun createUnifiedCaptureWebView0621(
        app: Context,
        account: BlaBlaDynamicAccount,
    ): WebView {
        val themed = ContextThemeWrapper(app, android.R.style.Theme_DeviceDefault)
        return WebView(themed).also { webView ->
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
    }

    private fun destroyUnifiedCaptureWebView0621(webView: WebView) {
        runCatching { webView.stopLoading() }
        runCatching { webView.webViewClient = WebViewClient() }
        runCatching { webView.loadUrl("about:blank") }
        runCatching { webView.clearHistory() }
        runCatching { webView.removeAllViews() }
        runCatching { webView.destroy() }
    }
    private suspend fun captureTrip0605(
        webView: WebView,
        store: BlaBlaRidesSnapshotStore0526,
        captureId: String,
        definition: BlaBlaAccountDefinition,
        ride: ParsedExternalRide0535,
        scripts: UnifiedDirectScripts0605,
        persistPrivateMetadata0653: Boolean = true,
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
        val detail = if (detailPage?.tripReady0721 == true) {
            decode0605<UnifiedTripDetailEnvelope0605>(detailPage.payload)
        } else {
            null
        }
        val observedTripId0676 = detail?.let { BlaBlaCollectorUrlModule.tripId(it.detail.url) }
        val scriptError0677 = detail?.scriptError?.trim().orEmpty()
        val detailError0676 = if (scriptError0677.isNotBlank()) {
            "TRIP_DETAIL_SCRIPT_ERROR_0677"
        } else {
            tripDetailVerificationError0676(
                detailPagePresent = detailPage != null,
                payloadPresent = !detailPage?.payload.isNullOrBlank(),
                payloadDecoded = detail != null,
                expectedTripId = ride.tripId,
                observedTripId = observedTripId0676,
                domHtmlBytes = detail?.domHtml?.toByteArray(Charsets.UTF_8)?.size ?: 0,
                detailReady0721 = detailPage?.tripReady0721 == true,
            )
        }
        if (detailError0676.isNotBlank()) {
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_TRIP_DETAIL_REJECTED_0676",
                webView.context.applicationContext.packageName,
                "requestedUrl=${BlaBlaCollectorUrlModule.sanitizeForLog(administrativeUrl)} " +
                    "finalUrl=${BlaBlaCollectorUrlModule.sanitizeForLog(detailPage?.finalUrl)} " +
                    "expectedTripKey=${seatSyncDiagnosticKey(ride.tripId)} " +
                    "observedTripKey=${seatSyncDiagnosticKey(observedTripId0676.orEmpty())} " +
                    "detailPagePresent=${detailPage != null} payloadPresent=${!detailPage?.payload.isNullOrBlank()} " +
                    "payloadDecoded=${detail != null} domBytes=${detail?.domHtml?.toByteArray(Charsets.UTF_8)?.size ?: 0} " +
                    "scriptErrorPresent=${scriptError0677.isNotBlank()} scriptStage=${detail?.scriptStage.orEmpty().take(48)} " +
                    "errorCode=$detailError0676",
            )
            return failedTrip0605(
                ride = ride,
                capturedAt = capturedAt,
                finalUrl = detailPage?.finalUrl.orEmpty(),
                errorCode = detailError0676,
            )
        }
        require(detailPage != null)
        require(detail != null)

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

        val stopObservations0672 = detail.itineraryStops
            .mapIndexedNotNull { index0672, rawStop0672 ->
                val stop0672 = rawStop0672.trim().takeIf(String::isNotBlank) ?: return@mapIndexedNotNull null
                val time0672 = detail.itineraryStopTimes
                    .getOrNull(index0672)
                    .orEmpty()
                    .trim()
                    .take(5)
                    .takeIf { value0672 -> Regex("""^(?:[01]?\d|2[0-3]):[0-5]\d$""").matches(value0672) }
                    .orEmpty()
                stop0672 to time0672
            }
            .fold(mutableListOf<Pair<String, String>>()) { result0672, observation0672 ->
                val previous0672 = result0672.lastOrNull()
                when {
                    previous0672 == null || previous0672.first != observation0672.first ->
                        result0672 += observation0672
                    previous0672.second.isBlank() && observation0672.second.isNotBlank() ->
                        result0672[result0672.lastIndex] = previous0672.first to observation0672.second
                }
                result0672
            }
            .toList()
        val stops = stopObservations0672.map(Pair<String, String>::first)
        val stopTimes0672 = stopObservations0672.map(Pair<String, String>::second)
        val baseTrip = normalized?.copy(
            itinerary_stops = stops,
            itinerary_stop_times = stopTimes0672,
            itinerary_authoritative = detail.itineraryAuthoritative && stops.size >= 2,
            public_trip_href = publicUrl,
            public_trip_href_source = publicSource,
            public_trip_href_binding = publicBinding,
            published_seats = publishedSeats,
        )
        val passengerDepth0653 = baseTrip?.let { source ->
            capturePassengerDepth0653(
                webView = webView,
                store = store,
                captureId = captureId,
                definition = definition,
                ride = ride,
                administrativeUrl = administrativeUrl,
                source = source,
                passengerHrefs = detail.passengerHrefs,
                tripStopLocations = detail.stopLocations,
                scripts = scripts,
                persistPrivateMetadata0653 = persistPrivateMetadata0653,
            )
        }
        val trip = passengerDepth0653?.trip ?: baseTrip
        val passengerDetailsExpected0653 = passengerDepth0653?.expected ?: trip?.passengers?.size.orZero0653()
        val passengerDetailsResolved0653 = passengerDepth0653?.resolved ?: 0
        val passengerDetailsComplete0653 =
            trip != null &&
                passengerDeepCaptureComplete0653(
                    expectedPassengers = passengerDetailsExpected0653,
                    resolvedPassengers = passengerDetailsResolved0653,
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
                passengerDetailsComplete0653 &&
                trip.published_seats != null &&
                !trip.public_trip_href.isNullOrBlank()
        val missing = buildList {
            if (trip == null) add("NORMALIZATION")
            if (trip?.passenger_roster_complete != true) add("ROSTER")
            if (trip?.itinerary_authoritative != true) add("ITINERARY")
            if (!passengerSegmentsResolved) add("PASSENGER_SEGMENTS")
            if (trip != null && !passengerDetailsComplete0653) add("PASSENGER_DETAILS")
            if (trip?.published_seats == null) add("SEATS")
            if (trip?.public_trip_href.isNullOrBlank()) add("PUBLIC_LINK")
        }
        val status = if (operationalComplete) "COMPLETE" else "INCOMPLETE"
        val error = if (operationalComplete) "" else "MISSING_" + missing.joinToString("_")

        return UnifiedCapturedTrip0605(
            trip = trip,
            operationalComplete = operationalComplete,
            paymentEvidence0764 = passengerDepth0653?.paymentEvidence0764.orEmpty(),
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
                passengerDetailsExpected0653 = passengerDetailsExpected0653,
                passengerDetailsResolved0653 = passengerDetailsResolved0653,
                passengerHtmlFiles0653 = passengerDepth0653?.htmlFiles.orEmpty(),
                passengerHtmlArtifacts0658 = passengerDepth0653?.htmlArtifacts.orEmpty(),
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
        expectedTransaction0610: BlaBlaHtmlCaptureTransactionState0610? = null,
    ): Boolean {
        val app = context.applicationContext
        val transaction = if (expectedTransaction0610 != null) {
            BlaBlaHtmlCaptureTransaction0610.heartbeat(app, expectedTransaction0610)
        } else {
            BlaBlaHtmlCaptureTransaction0610.active(app)
        }
        if (
            transaction == null ||
            transaction.captureId != manifest.captureId ||
            (expectedTransaction0610 != null && transaction.generation != expectedTransaction0610.generation)
        ) {
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_GLOBAL_HTML_COMMIT_BLOCKED_0610",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} reason=transaction_not_active_or_owner_lost_0678 preservePreviousCanonical=true",
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
                evaluateAbsentTrips0618 = true,
                // 0.1.622: COMPLETE is a convergence barrier, not a presence-only pass.
                // Reconcile every HTML card again so stale per-card/public state cannot survive.
                skipPresentAlreadyCommittedGeneration0618 = false,
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
            val source: BlaBlaCollectorTrip,
            val matches: List<Trip>,
        )
        val readback0612 = published.trips.map { source ->
            val profileUuid = source.profile_uuid.trim()
            val tripId = source.trip_id?.trim().orEmpty()
            Readback0612(
                profileUuid = profileUuid,
                tripId = tripId,
                source = source,
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
        val semanticMismatch06122 = readback0612.filter { readback ->
            val current = readback.matches.singleOrNull() ?: return@filter false
            val allocation = current.rotaCertaSeatAllocation?.takeIf { it in 0..999 }
                ?: settings.rotaCertaSeatAllocation
            val expectedFingerprint =
                PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(readback.source, allocation)
            !current.externalSnapshotComplete ||
                current.lastCollectionGeneration != transaction.generation ||
                current.externalSnapshotFingerprint != expectedFingerprint
        }
        val canonicalComplete =
            canonicalized == expectedTripCount &&
                unresolved0612.isEmpty() &&
                semanticMismatch06122.isEmpty() &&
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
            semanticMismatch06122.take(24).forEach { mismatch ->
                val current = mismatch.matches.singleOrNull()
                val allocation = current?.rotaCertaSeatAllocation?.takeIf { it in 0..999 }
                    ?: settings.rotaCertaSeatAllocation
                val expectedFingerprint =
                    PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(mismatch.source, allocation)
                UnifiedDebugEventStore.recordAlways(
                    "BLABLACAR_GLOBAL_HTML_SEMANTIC_MISMATCH_0622",
                    app.packageName,
                    "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} " +
                        "profileKey=${seatSyncDiagnosticKey(mismatch.profileUuid)} tripId=${mismatch.tripId.take(160)} " +
                        "expectedFingerprint=${expectedFingerprint.takeLast(12)} " +
                        "actualFingerprint=${current?.externalSnapshotFingerprint.orEmpty().takeLast(12)} " +
                        "expectedGeneration=${transaction.generation} actualGeneration=${current?.lastCollectionGeneration ?: -1L}",
                )
            }
            val tripRestored = tripStore.restoreHtmlRollback0612(tripRollback0612)
            val outboxRestored = outbox.restoreHtmlRollback0612(outboxRollback0612)
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_GLOBAL_HTML_CANONICAL_VALIDATION_FAILED_0610",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} htmlTrips=$expectedTripCount canonicalized=$canonicalized unresolved=${unresolved0612.size} semanticMismatch=${semanticMismatch06122.size} blocked=${batch.blockedTrips} stale=${batch.staleResultsRejected} missingPreserved=${batch.missingPreserved} changed=${batch.changedTrips} skipped=${batch.skippedTrips} rollbackTripStore=$tripRestored rollbackOutbox=$outboxRestored",
            )
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_GLOBAL_HTML_ROLLBACK_0612",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} stage=canonical_readback tripStoreRestored=$tripRestored outboxRestored=$outboxRestored preservePreviousCanonical=${tripRestored && outboxRestored}",
            )
            return false
        }
        // 0.1.622: retry publication for every identity from the COMPLETE HTML generation.
        // A failed live-card transport must not remain stale merely because no new event
        // was queued by the final reconciliation.
        val tenantId06122 = tripStore.bookingReconcileScopeKey()
        val completeCanonicalIds06122 = published.trips.mapNotNull { source ->
            canonicalBlaBlaTripKey0406(
                tenantId = tenantId06122,
                profileUuid = source.profile_uuid,
                providerTripId = source.trip_id,
            )
        }.toSet()
        val parityTargets06122 =
            (completeCanonicalIds06122 + batch.publicationCanonicalTripIds0431).toSet()
        val parityDrainLimit06122 = maxOf(128, parityTargets06122.size + 32)
        val delivered = TripMutationCoordinator0387(app, tripStore).drainPending(
            limit = parityDrainLimit06122,
            canonicalTripIds = parityTargets06122,
        )
        val pendingParity06122 = outbox.pending(
            limit = parityDrainLimit06122,
            canonicalTripIds = parityTargets06122,
        )
        if (pendingParity06122.isNotEmpty()) {
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_GLOBAL_HTML_PUBLIC_PARITY_PENDING_0622",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} " +
                    "targets=${parityTargets06122.size} delivered=$delivered pending=${pendingParity06122.size} " +
                    "action=retryable_outbox_preserved",
            )
        }
        BookingRealtimeEvents0356.notifyChanged()
        TripWidgetProvider.updateAll(app)
        UnifiedDebugEventStore.recordAlways(
            "BLABLACAR_GLOBAL_HTML_COMMIT_0610",
            app.packageName,
            "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} generation=${transaction.generation} profiles=${accounts.size} htmlTrips=$expectedTripCount canonicalized=$canonicalized semanticMismatch=0 parityTargets=${parityTargets06122.size} parityPending=${pendingParity06122.size} changed=${batch.changedTrips} unchanged=${batch.skippedTrips} tombstoned=${batch.tombstonedTrips} blocked=${batch.blockedTrips} conflicts=0 outboxDelivered=$delivered status=complete completeForScope=true skipped=0 canonicalDeltaEnqueued=false directReconcile=true commitCount=1",
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


    private suspend fun capturePassengerDepth0653(
        webView: WebView,
        store: BlaBlaRidesSnapshotStore0526,
        captureId: String,
        definition: BlaBlaAccountDefinition,
        ride: ParsedExternalRide0535,
        administrativeUrl: String,
        source: BlaBlaCollectorTrip,
        passengerHrefs: List<String>,
        tripStopLocations: List<BlaBlaTripStopLocation0659>,
        scripts: UnifiedDirectScripts0605,
        persistPrivateMetadata0653: Boolean = true,
    ): UnifiedPassengerDepthResult0653 {
        val expected = source.passengers.size
        if (expected == 0) {
            return UnifiedPassengerDepthResult0653(
                trip = source,
                expected = 0,
                resolved = 0,
                htmlFiles = emptyList(),
                htmlArtifacts = emptyList(),
            )
        }

        val passengers = source.passengers.toMutableList()
        val paymentEvidence0764 = MutableList<BlaBlaPassengerPaymentEvidence0764?>(expected) { null }
        val pendingMetadata = mutableListOf<ExternalPassengerMetadata>()
        val htmlFiles = mutableListOf<String>()
        val htmlArtifacts = mutableListOf<BlaBlaRidesSnapshotFile0526>()
        val identityStore = PassengerIdentityStore(webView.context.applicationContext)
        var resolved = 0

        source.passengers.forEachIndexed { index, passenger ->
            val target = passengerDeepCaptureTarget0653(
                passenger = passenger,
                passengerIndex = index,
                passengerHrefs = passengerHrefs,
            )
            if (target == null) {
                UnifiedDebugEventStore.recordAlways(
                    "BLABLACAR_HTML_PASSENGER_UNRESOLVED_0653",
                    webView.context.packageName,
                    "tripKey=${seatSyncDiagnosticKey(definition.uuid + "|" + ride.tripId)} passengerIndex=$index reason=TARGET_MISSING",
                )
                return@forEachIndexed
            }

            var captured: UnifiedPassengerPageCapture0653? = null
            var attempt = 0
            var privateEvidenceMissing0657 = false
            while (attempt < PASSENGER_DETAIL_ATTEMPTS_0653) {
                attempt++
                val candidate0657 = capturePassengerPage0653(
                    webView = webView,
                    administrativeUrl = administrativeUrl,
                    ride = ride,
                    passengerIndex = index,
                    target = target,
                    scripts = scripts,
                )
                if (candidate0657 != null) {
                    val candidateFareValues0657 = buildList<String?> {
                        add(candidate0657.fare.driverReceives)
                        add(candidate0657.contact.fareAmount)
                        add(candidate0657.fare.passengerTotal)
                        addAll(candidate0657.fare.visibleAmounts)
                    }
                    val candidateFare0657 = parsePassengerFareMinorUnits0653(candidateFareValues0657)
                    val candidateBoardingSegment0658 = candidate0657.segment.boarding.trim()
                    val candidateDropoffSegment0658 = candidate0657.segment.dropoff.trim()
                    val candidateBoardingStop0659 = passengerTripStopLocation0659(
                        candidateBoardingSegment0658,
                        tripStopLocations,
                    )
                    val candidateDropoffStop0659 = passengerTripStopLocation0659(
                        candidateDropoffSegment0658,
                        tripStopLocations,
                    )
                    privateEvidenceMissing0657 = !passengerOperationalEvidenceComplete0659(
                        phone = BlaBlaCollectorPassengerModule.normalizePhone(candidate0657.contact.phone),
                        fareMinorUnits = candidateFare0657,
                        boardingSegment = candidateBoardingSegment0658,
                        dropoffSegment = candidateDropoffSegment0658,
                        boardingStop = candidateBoardingStop0659,
                        dropoffStop = candidateDropoffStop0659,
                    )
                    if (!privateEvidenceMissing0657 || attempt >= PASSENGER_DETAIL_ATTEMPTS_0653) {
                        captured = candidate0657
                        break
                    }
                    UnifiedDebugEventStore.recordAlways(
                        "BLABLACAR_HTML_PASSENGER_PRIVATE_RETRY_0657",
                        webView.context.packageName,
                        "tripKey=${seatSyncDiagnosticKey(definition.uuid + "|" + ride.tripId)} passengerIndex=$index attempt=$attempt reason=REQUIRED_PRIVATE_OR_TRIP_GEO_MISSING_0659 privateValuesLogged=false",
                    )
                }
                if (attempt < PASSENGER_DETAIL_ATTEMPTS_0653) {
                    delay(PASSENGER_DETAIL_RETRY_MS_0653 * attempt)
                }
            }
            val page = captured
            if (page == null) {
                UnifiedDebugEventStore.recordAlways(
                    "BLABLACAR_HTML_PASSENGER_UNRESOLVED_0653",
                    webView.context.packageName,
                    "tripKey=${seatSyncDiagnosticKey(definition.uuid + "|" + ride.tripId)} passengerIndex=$index attempts=$attempt reason=PAGE_UNVERIFIED",
                )
                return@forEachIndexed
            }
            val bookingHref = BlaBlaCollectorUrlModule.canonical(page.finalUrl)
                .takeIf { passengerPageBelongsToTrip0653(it, ride.tripId) }
                ?: return@forEachIndexed
            val reservationKey = externalPassengerReservationKey(definition.uuid, bookingHref)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return@forEachIndexed

            val normalizedPhone = BlaBlaCollectorPassengerModule.normalizePhone(page.contact.phone)
            val updatedPassenger = passenger.copy(
                name = page.identity.name.trim()
                    .ifBlank { page.contact.visibleName.trim() }
                    .ifBlank { passenger.name },
                boarding = page.segment.boarding.trim()
                    .takeIf(String::isNotBlank)
                    ?: passenger.boarding,
                dropoff = page.segment.dropoff.trim()
                    .takeIf(String::isNotBlank)
                    ?: passenger.dropoff,
                phone = normalizedPhone ?: passenger.phone,
                booking_href = bookingHref,
            )

            val html = page.contact.domHtml.trim()
            if (html.isBlank()) return@forEachIndexed
            val htmlEvidence = runCatching {
                store.writeTripHtml0605(
                    captureId = captureId,
                    profileUuid = definition.uuid,
                    tripId = ride.tripId + "|passenger|" + reservationKey,
                    html = html,
                )
            }.getOrNull() ?: return@forEachIndexed
            val htmlFile = store.resolveArtifact0528(captureId, htmlEvidence.relativePath)
            val sensitive = htmlFile?.let { sensitiveArtifactMarker0528(it) }
            if (sensitive != null) {
                runCatching { htmlFile.delete() }
                UnifiedDebugEventStore.recordAlways(
                    "BLABLACAR_HTML_PASSENGER_UNRESOLVED_0653",
                    webView.context.packageName,
                    "tripKey=${seatSyncDiagnosticKey(definition.uuid + "|" + ride.tripId)} passengerIndex=$index reason=SENSITIVE_HTML_${sensitive.take(40)}",
                )
                return@forEachIndexed
            }
            htmlFiles += htmlEvidence.relativePath
            htmlArtifacts += htmlEvidence
            // Preserve provenance: "passenger total" is NOT the driver's amount and
            // neither can be inferred from the trip card. Evidence stays in-memory for
            // the isolated remote query; no new private persistence is introduced.
            val paymentFields0764 = listOf(page.fare.passengerTotal, page.fare.driverReceives)
            val passengerPayable0764 = parsePassengerFareMinorUnits0653(listOf(page.fare.passengerTotal))
            val driverReceives0764 = parsePassengerFareMinorUnits0653(listOf(page.fare.driverReceives))
            val paymentCurrency0764 = passengerFareCurrency0653(
                explicitCurrency = page.contact.fareCurrencyCode,
                fareValues = paymentFields0764,
            )
            paymentEvidence0764[index] = BlaBlaPassengerPaymentEvidence0764(
                passengerTotalMinorUnits = passengerPayable0764,
                driverReceivesMinorUnits = driverReceives0764,
                currencyCode = paymentCurrency0764.takeIf { it == "BRL" }.orEmpty(),
            )
            if (privateEvidenceMissing0657) {
                UnifiedDebugEventStore.recordAlways(
                    "BLABLACAR_HTML_PASSENGER_PRIVATE_MISSING_0657",
                    webView.context.packageName,
                    "tripKey=${seatSyncDiagnosticKey(definition.uuid + "|" + ride.tripId)} passengerIndex=$index attempts=$attempt action=MARK_PASSENGER_INCOMPLETE_PRESERVE_PREVIOUS_CANONICAL evidence=true privateValuesLogged=false",
                )
                return@forEachIndexed
            }

            val existingMetadata = identityStore.externalMetadata(reservationKey)
            val boardingTripStop0659 = passengerTripStopLocation0659(page.segment.boarding, tripStopLocations)
                ?: return@forEachIndexed
            val dropoffTripStop0659 = passengerTripStopLocation0659(page.segment.dropoff, tripStopLocations)
                ?: return@forEachIndexed
            val fareValues = buildList<String?> {
                add(page.fare.driverReceives)
                add(page.contact.fareAmount)
                add(page.fare.passengerTotal)
                addAll(page.fare.visibleAmounts)
            }
            val fareMinorUnits = parsePassengerFareMinorUnits0653(fareValues)
            val fareCurrencyCode = passengerFareCurrency0653(
                explicitCurrency = page.contact.fareCurrencyCode,
                fareValues = fareValues,
            )
            val externalPassengerId = stableExternalPassengerId(
                BlaBlaCollectorUrlModule.passengerIdentityKey(bookingHref),
            ).orEmpty()
            val boardingAddress = boardingTripStop0659.address.trim()
            val dropoffAddress = dropoffTripStop0659.address.trim()

            pendingMetadata += (existingMetadata ?: ExternalPassengerMetadata(reservationKey = reservationKey)).copy(
                externalPassengerId = externalPassengerId.ifBlank { existingMetadata?.externalPassengerId.orEmpty() },
                externalTripId = ride.tripId,
                externalProfileUuid = definition.uuid,
                passengerContact = normalizedPhone ?: existingMetadata?.passengerContact.orEmpty(),
                fareMinorUnits = fareMinorUnits ?: existingMetadata?.fareMinorUnits,
                fareCurrencyCode = fareCurrencyCode.ifBlank { existingMetadata?.fareCurrencyCode.orEmpty() },
                boardingAddress = boardingAddress,
                dropoffAddress = dropoffAddress,
                boardingLatitude = boardingTripStop0659.latitude,
                boardingLongitude = boardingTripStop0659.longitude,
                dropoffLatitude = dropoffTripStop0659.latitude,
                dropoffLongitude = dropoffTripStop0659.longitude,
                boardingAccuracyMeters = null,
                boardingLocationSource = "blablacar_trip_map_zoomOn_0659",
                boardingLocationCollectedAtMillis = System.currentTimeMillis(),
            )
            passengers[index] = updatedPassenger
            resolved++

            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_HTML_PASSENGER_RESOLVED_0653",
                webView.context.packageName,
                "tripKey=${seatSyncDiagnosticKey(definition.uuid + "|" + ride.tripId)} passengerIndex=$index/$expected phonePresent=${!updatedPassenger.phone.isNullOrBlank()} farePresent=${fareMinorUnits != null} boardingAddressPresent=${boardingAddress.isNotBlank()} externalPassengerIdPresent=${externalPassengerId.isNotBlank()} evidence=true",
            )
        }

        val complete = passengerDeepCaptureComplete0653(expected, resolved)
        if (complete && persistPrivateMetadata0653) {
            withContext(Dispatchers.IO) {
                pendingMetadata.forEach(identityStore::saveExternalMetadata)
            }
        } else if (complete) {
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_HTML_PASSENGER_PRIVATE_PERSIST_SKIPPED_0737",
                webView.context.packageName,
                "tripKey=${seatSyncDiagnosticKey(definition.uuid + "|" + ride.tripId)} remoteReadOnly=true privateMetadataWrite=false",
            )
        }

        val enriched = source.copy(
            passengers = passengers,
            booked_seats = maxOf(
                source.booked_seats,
                passengers.sumOf { it.seats.coerceAtLeast(1) },
            ),
        )
        return UnifiedPassengerDepthResult0653(
            trip = enriched,
            expected = expected,
            resolved = resolved,
            htmlFiles = htmlFiles,
            htmlArtifacts = htmlArtifacts,
            paymentEvidence0764 = paymentEvidence0764,
        )
    }

    private suspend fun capturePassengerPage0653(
        webView: WebView,
        administrativeUrl: String,
        ride: ParsedExternalRide0535,
        passengerIndex: Int,
        target: String,
        scripts: UnifiedDirectScripts0605,
    ): UnifiedPassengerPageCapture0653? {
        val contactPage = if (target.startsWith("rotacerta-card:")) {
            val openScript = scripts.passengerOpen.replace("{{PASSENGER_INDEX}}", passengerIndex.toString())
            val clickPage = loadAndEvaluate0605(
                webView = webView,
                url = administrativeUrl,
                script = openScript,
                prepareTrip = true,
            ) { finalUrl ->
                BlaBlaCollectorUrlModule.tripId(finalUrl) == ride.tripId &&
                    !BlaBlaCollectorUrlModule.isPassenger(finalUrl)
            }
            val click = decode0605<UnifiedPassengerOpenEvidence0653>(clickPage?.payload)
            if (click?.found != true || click.clicked != true) return null
            val finalUrl = awaitPassengerUrl0653(webView, ride.tripId) ?: return null
            delay(PAGE_SETTLE_MS_0605)
            val raw = evaluateCurrent0605(webView, scripts.passengerContact) ?: return null
            UnifiedEvaluatedPage0605(finalUrl = finalUrl, payload = raw)
        } else {
            val exactTarget = BlaBlaCollectorUrlModule.absolute(target)
            loadAndEvaluate0605(
                webView = webView,
                url = exactTarget,
                script = scripts.passengerContact,
                prepareTrip = false,
            ) { finalUrl ->
                BlaBlaCollectorUrlModule.samePassengerPage(exactTarget, finalUrl) &&
                    passengerPageBelongsToTrip0653(finalUrl, ride.tripId)
            }
        } ?: return null

        if (!passengerPageBelongsToTrip0653(contactPage.finalUrl, ride.tripId)) return null
        val readyBeforePrepare0659 = awaitPassengerPrivateReady0659(
            webView = webView,
            tripId = ride.tripId,
            script = scripts.passengerReady,
        )
        if (readyBeforePrepare0659 == null) {
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_HTML_PASSENGER_PAGE_NOT_READY_0659",
                webView.context.packageName,
                "tripBound=true passengerIndex=$passengerIndex stage=before_prepare privateValuesLogged=false",
            )
            return null
        }
        repeat(PASSENGER_PREPARE_PASSES_0657) {
            evaluateCurrent0605(webView, scripts.passengerPrepare)
            delay(PASSENGER_PREPARE_SETTLE_MS_0657)
        }
        val preparedUrl0657 = webView.url.orEmpty()
        if (!passengerPageBelongsToTrip0653(preparedUrl0657, ride.tripId)) return null
        val readyAfterPrepare0659 = awaitPassengerPrivateReady0659(
            webView = webView,
            tripId = ride.tripId,
            script = scripts.passengerReady,
        )
        if (readyAfterPrepare0659 == null) {
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_HTML_PASSENGER_PAGE_NOT_READY_0659",
                webView.context.packageName,
                "tripBound=true passengerIndex=$passengerIndex stage=after_prepare privateValuesLogged=false",
            )
            return null
        }
        val contact = decode0605<UnifiedPassengerContactEvidence0653>(
            evaluateCurrent0605(webView, scripts.passengerContact),
        ) ?: return null
        val identity = decode0605<UnifiedPassengerIdentityEvidence0653>(
            evaluateCurrent0605(webView, scripts.passengerIdentity),
        ) ?: return null
        val fare = decode0605<UnifiedPassengerFareEvidence0653>(
            evaluateCurrent0605(webView, scripts.passengerFare),
        ) ?: return null
        val segment = decode0605<UnifiedPassengerSegmentEvidence0653>(
            evaluateCurrent0605(webView, scripts.passengerSegment),
        ) ?: return null
        val addresses = decode0605<UnifiedPassengerAddressEvidence0653>(
            evaluateCurrent0605(webView, scripts.passengerAddresses),
        ) ?: return null

        return UnifiedPassengerPageCapture0653(
            finalUrl = preparedUrl0657,
            identity = identity,
            contact = contact,
            fare = fare,
            segment = segment,
            addresses = addresses,
        )
    }

    private suspend fun awaitPassengerPrivateReady0659(
        webView: WebView,
        tripId: String,
        script: String,
    ): UnifiedPassengerReadyEvidence0659? {
        repeat(PASSENGER_READY_POLLS_0659) {
            val current = webView.url.orEmpty()
            if (!passengerPageBelongsToTrip0653(current, tripId)) return null
            val evidence = decode0605<UnifiedPassengerReadyEvidence0659>(
                evaluateCurrent0605(webView, script),
            )
            if (
                evidence?.ready == true &&
                !evidence.rootInert &&
                passengerPageBelongsToTrip0653(evidence.url, tripId)
            ) {
                return evidence
            }
            delay(PASSENGER_READY_POLL_MS_0659)
        }
        return null
    }

    private suspend fun awaitPassengerUrl0653(
        webView: WebView,
        tripId: String,
    ): String? {
        repeat(PASSENGER_NAVIGATION_POLLS_0653) {
            val current = webView.url.orEmpty()
            if (passengerPageBelongsToTrip0653(current, tripId)) return current
            delay(PASSENGER_NAVIGATION_POLL_MS_0653)
        }
        return null
    }

    private fun Int?.orZero0653(): Int = this ?: 0

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
                            if (ready) {
                                evaluate(view)
                            } else if (pass + 1 >= PREPARE_PASSES_0605) {
                                val finalUrl = view.url.orEmpty()
                                if (!acceptUrl(finalUrl)) {
                                    finish(null)
                                } else {
                                    finish(
                                        UnifiedEvaluatedPage0605(
                                            finalUrl = finalUrl,
                                            payload = "",
                                            tripReady0721 = false,
                                        ),
                                    )
                                }
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
    private const val TRANSPORT_RECOVERY_ATTEMPTS_0621 = 2
    private const val TRANSPORT_RECOVERY_BACKOFF_MS_0621 = 2_500L
    private const val DEFERRED_NOT_READY_BACKOFF_MS_0721 = 4_000L
    private const val DEFERRED_NOT_READY_BETWEEN_CARDS_MS_0721 = 1_000L
    private const val PASSENGER_DETAIL_ATTEMPTS_0653 = 3
    private const val PASSENGER_DETAIL_RETRY_MS_0653 = 650L
    private const val PASSENGER_PREPARE_PASSES_0657 = 2
    private const val PASSENGER_PREPARE_SETTLE_MS_0657 = 350L
    private const val PASSENGER_READY_POLLS_0659 = 10
    private const val PASSENGER_READY_POLL_MS_0659 = 250L
    private const val PASSENGER_NAVIGATION_POLLS_0653 = 40
    private const val PASSENGER_NAVIGATION_POLL_MS_0653 = 200L

    private val TRIP_READY_0606 = """
        (function() {
          const text = String((document.body && document.body.innerText) || '').replace(/\s+/g, ' ').trim().toLowerCase();
          const summary = text.includes('resumo da viagem') ||
            text.includes('trip summary') ||
            text.includes('résumé du trajet') ||
            text.includes('resumen del viaje');
          const isVisible = (node) => {
            if (!node || !node.isConnected) return false;
            const style = window.getComputedStyle ? window.getComputedStyle(node) : null;
            if (style && (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0')) return false;
            return !node.getClientRects || node.getClientRects().length > 0;
          };
          const loadingActive = Array.from(document.querySelectorAll(
            '[role="progressbar"], [aria-busy="true"], [aria-valuetext]'
          )).some((node) => {
            if (!isVisible(node)) return false;
            const marker = String(
              (node.getAttribute('aria-valuetext') || '') + ' ' +
              (node.getAttribute('aria-label') || '') + ' ' +
              (node.textContent || '')
            ).replace(/\s+/g, ' ').trim().toLowerCase();
            return node.getAttribute('role') === 'progressbar' ||
              node.getAttribute('aria-busy') === 'true' ||
              /loading|carregando|chargement|cargando/.test(marker);
          });
          const boundControl = !!document.querySelector(
            'a[href*="/rides/offer/edit/"], a[href*="/rides/offer/map"], a[href*="/rides/offer/passenger/"], a[href*="/trip?"]'
          );
          return !loadingActive && summary && boundControl;
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
