package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.io.File
import java.net.URI
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/**
 * A manual/private passenger changes the Rota Certa occupancy immediately.
 * This request is only an external mirror request for the exact publication
 * already identified by profile UUID + BlaBlaCar trip id.
 */
@Serializable
data class BlaBlaManualSeatSyncRequest(
    val id: String = UUID.randomUUID().toString(),
    val profileUuid: String,
    val tripId: String,
    /** Negative removes externally offered places; positive gives them back. */
    val seatDelta: Int,
    val localTripId: String,
    val localBookingId: String,
    val source: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

class BlaBlaManualSeatSyncRequestStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun list(): List<BlaBlaManualSeatSyncRequest> = runCatching {
        json.decodeFromString<List<BlaBlaManualSeatSyncRequest>>(prefs.getString(KEY_QUEUE, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    fun peek(): BlaBlaManualSeatSyncRequest? = list().firstOrNull()

    fun enqueue(request: BlaBlaManualSeatSyncRequest) {
        save(list() + request)
    }

    fun remove(id: String) {
        save(list().filterNot { it.id == id })
    }

    private fun save(queue: List<BlaBlaManualSeatSyncRequest>) {
        prefs.edit().putString(KEY_QUEUE, json.encodeToString(queue)).apply()
    }

    companion object {
        private const val PREFS = "rota_certa_blablacar_manual_seat_sync_v1"
        private const val KEY_QUEUE = "queue"
    }
}

object BlaBlaManualSeatSyncCoordinator {
    fun enqueueForManualBooking(
        context: Context,
        trip: Trip,
        booking: Booking,
        seatDelta: Int,
    ): BlaBlaManualSeatSyncRequest? {
        if (booking.source !in setOf(BookingSource.PRIVATE, BookingSource.OTHER)) return null
        if (seatDelta == 0 || kotlin.math.abs(seatDelta) != booking.seats) return null

        val response = BlaBlaCollectorStateStore(context).lastResponse() ?: return pending(
            context,
            "collector_snapshot_missing",
            trip,
            booking,
        )
        val match = BlaBlaManualSeatTripResolver.resolveExact(trip, response) ?: return pending(
            context,
            "external_trip_not_unique",
            trip,
            booking,
        )
        val request = BlaBlaManualSeatSyncRequest(
            profileUuid = match.profile_uuid,
            tripId = match.trip_id.orEmpty(),
            seatDelta = seatDelta,
            localTripId = trip.id,
            localBookingId = booking.id,
            source = booking.source.name,
        )
        BlaBlaManualSeatSyncRequestStore(context).enqueue(request)
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_SYNC_QUEUED",
            context.packageName,
            "source=${booking.source.name} manual=true profileUuidPresent=true tripIdPresent=true delta=$seatDelta request=${request.id}",
        )
        return request
    }

    private fun pending(context: Context, reason: String, trip: Trip, booking: Booking): BlaBlaManualSeatSyncRequest? {
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_SYNC_PENDING",
            context.packageName,
            "reason=$reason source=${booking.source.name} manual=true localTripPresent=${trip.id.isNotBlank()} seats=${booking.seats}",
        )
        return null
    }
}

internal object BlaBlaManualSeatTripResolver {
    fun resolveExact(trip: Trip, response: BlaBlaCollectorMonthResponse): BlaBlaCollectorTrip? {
        val stops = trip.stops.sortedBy(TripStop::order)
        val first = stops.firstOrNull()?.name?.trim().orEmpty()
        val last = stops.lastOrNull()?.name?.trim().orEmpty()
        if (first.isBlank() || last.isBlank()) return null
        val zoned = Instant.ofEpochMilli(trip.departureAtMillis).atZone(ZoneId.systemDefault())
        val date = zoned.toLocalDate().toString()
        val time = DateTimeFormatter.ofPattern("HH:mm").format(zoned)
        val matches = response.trips.filter { external ->
            val externalFrom = external.actual_departure?.takeIf(String::isNotBlank) ?: external.search_from.orEmpty()
            val externalTo = external.actual_arrival?.takeIf(String::isNotBlank) ?: external.search_to.orEmpty()
            external.trip_id?.isNotBlank() == true &&
                external.profile_uuid.isNotBlank() &&
                external.date == date &&
                external.departure_time?.take(5) == time &&
                samePlace(first, externalFrom) &&
                samePlace(last, externalTo)
        }
        return matches.singleOrNull()
    }

    private fun samePlace(left: String, right: String): Boolean {
        val a = placeKey(left)
        val b = placeKey(right)
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        return shorter.length >= 5 && longer.contains(shorter)
    }

    private fun placeKey(raw: String): String = Normalizer.normalize(raw.substringBefore(',').trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}

/** App-specific archive: MHTML may contain passenger contact data, so it is not put in public Downloads. */
class BlaBlaMhtmlArchiveStore(private val context: Context) {
    private val root: File = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "blablacar-mhtml",
    )

    fun save(
        webView: WebView,
        account: BlaBlaDynamicAccount,
        kind: String,
        key: String,
        onDone: (String?) -> Unit,
    ) {
        val dir = File(root, safe(account.id)).apply { mkdirs() }
        val target = File(dir, "${safe(kind)}-${safe(key)}.mht")
        var completed = false
        fun complete(saved: String?) {
            if (completed) return
            completed = true
            if (!saved.isNullOrBlank()) {
                UnifiedDebugEventStore.record(
                    "MHTML_ARCHIVE_SAVED",
                    context.packageName,
                    "account=${account.displayLabel} kind=${safe(kind)} file=${target.name}",
                )
            }
            onDone(saved)
        }
        webView.postDelayed({
            if (!completed) {
                UnifiedDebugEventStore.record(
                    "MHTML_ARCHIVE_TIMEOUT",
                    context.packageName,
                    "account=${account.displayLabel} kind=${safe(kind)} keyPresent=${key.isNotBlank()}",
                )
                complete(null)
            }
        }, MHTML_SAVE_TIMEOUT_MS)
        runCatching {
            webView.saveWebArchive(target.absolutePath, false) { saved ->
                complete(saved)
            }
        }.onFailure {
            UnifiedDebugEventStore.record(
                "MHTML_ARCHIVE_FAILED",
                context.packageName,
                "account=${account.displayLabel} kind=${safe(kind)} reason=${it.javaClass.simpleName}",
            )
            complete(null)
        }
    }

    private fun safe(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-')
        .take(90)
        .ifBlank { "page" }

    companion object { private const val MHTML_SAVE_TIMEOUT_MS = 4_500L }
}

object BlaBlaManualSeatAutomationIntents {
    const val EXTRA_ACCOUNT_ID = "blablacar_account_id"
    const val MODE_MHTML = "mhtml"
    const val MODE_SEAT_SYNC = "seat_sync"

    fun harvest(context: Context, account: BlaBlaDynamicAccount): Intent =
        Intent(context, BlaBlaMhtmlHarvestActivity::class.java)
            .putExtra(EXTRA_ACCOUNT_ID, account.id)

    fun seatSync(context: Context, account: BlaBlaDynamicAccount): Intent =
        Intent(context, BlaBlaManualSeatSyncActivity::class.java)
            .putExtra(EXTRA_ACCOUNT_ID, account.id)
}

@Serializable
data class BlaBlaHarvestTripEvidence(
    val tripId: String,
    val publishedSeats: Int? = null,
    val views: Int? = null,
    val itineraryStops: List<String> = emptyList(),
    val passengers: List<BlaBlaCollectorPassenger> = emptyList(),
    val passengerRosterComplete: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

class BlaBlaHarvestEvidenceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(accountId: String): List<BlaBlaHarvestTripEvidence> = runCatching {
        json.decodeFromString<List<BlaBlaHarvestTripEvidence>>(prefs.getString(key(accountId), "[]") ?: "[]")
    }.getOrDefault(emptyList())

    fun replace(accountId: String, evidence: List<BlaBlaHarvestTripEvidence>) {
        prefs.edit().putString(key(accountId), json.encodeToString(evidence)).apply()
    }

    private fun key(accountId: String): String = "account_${accountId.trim()}"

    companion object { private const val PREFS = "rota_certa_blablacar_harvest_evidence_v1" }
}

@Serializable
private data class MhtmlRideList(
    val tripHrefs: List<String> = emptyList(),
    val pageUrl: String = "",
    val domHtml: String = "",
)

@Serializable
private data class MhtmlTripEvidence(
    val passengers: List<BlaBlaCollectorPassenger> = emptyList(),
    val passengerHrefs: List<String> = emptyList(),
    val itineraryStops: List<String> = emptyList(),
    val rosterComplete: Boolean = false,
    val explicitEmptyRoster: Boolean = false,
    val views: Int = -1,
    val editHref: String = "",
    val pageUrl: String = "",
    val domHtml: String = "",
)

@Serializable
private data class MhtmlPassengerEvidence(
    val phone: String = "",
    val visibleName: String = "",
    val seats: Int = 1,
    val boarding: String = "",
    val dropoff: String = "",
    val price: String = "",
    val callActionPresent: Boolean = false,
    val pageUrl: String = "",
    val domHtml: String = "",
)

@Serializable
private data class MhtmlPassengerCardOpenState(
    val found: Boolean = false,
    val clicked: Boolean = false,
)

@Serializable
private data class MhtmlEditEvidence(
    val optionsHref: String = "",
    val pageUrl: String = "",
    val domHtml: String = "",
)

private data class TripTarget(
    val accountId: String,
    val tripId: String,
    val href: String,
)

private data class PassengerTarget(
    val accountId: String,
    val tripId: String,
    val tripHref: String,
    val href: String = "",
    val cardIndex: Int = -1,
    val expectedName: String = "",
    val expectedSeats: Int = 1,
    val expectedBoarding: String = "",
    val expectedDropoff: String = "",
    val requiresSemanticProof: Boolean = false,
) {
    val externalPassengerKey: String
        get() = href.takeIf(String::isNotBlank)?.let(::passengerKey) ?: "card:$cardIndex"
    val discoveryKey: String
        get() = href.takeIf(String::isNotBlank)?.let(::canonicalHref) ?: "card:$cardIndex"
    val scopedEvidenceKey: String
        get() = BlaBlaHarvestAssociation.passengerEvidenceKey(accountId, tripId, externalPassengerKey)
    val archiveKey: String
        get() = href.takeIf(String::isNotBlank)?.let(::passengerKey) ?: "card-${cardIndex + 1}"
}

class BlaBlaMhtmlHarvestActivity : Activity() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var registry: BlaBlaDynamicAccountRegistry
    private lateinit var sessionStore: BlaBlaDynamicSessionStore
    private lateinit var harvestStore: BlaBlaHarvestEvidenceStore
    private lateinit var account: BlaBlaDynamicAccount
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private lateinit var archive: BlaBlaMhtmlArchiveStore
    private var phase = Phase.RIDES
    private var busy = false
    private var archived = 0
    private var tripTargets = emptyList<TripTarget>()
    private var tripIndex = 0
    private val tripEvidence = linkedMapOf<String, MhtmlTripEvidence>()
    private val passengerTargets = mutableListOf<PassengerTarget>()
    private var passengerIndex = 0
    private val passengerEvidence = mutableMapOf<String, MhtmlPassengerEvidence>()
    private val editTargetByTrip = linkedMapOf<String, TripTarget>()
    private var editTargets = emptyList<TripTarget>()
    private var editIndex = 0
    private val optionTargetByTrip = linkedMapOf<String, TripTarget>()
    private var optionTargets = emptyList<TripTarget>()
    private var optionIndex = 0
    private val publishedSeatsByTrip = mutableMapOf<String, Int>()
    private var pageReadAttempts = 0
    private var passengerCallActionTriggered = false
    private var interceptedPassengerPhone: String? = null
    private var passengerReloadAttempts = 0
    private var evaluationGeneration = 0L
    private var navigationGeneration = 0L
    private var expectedNavigationPhase = Phase.RIDES
    private var expectedNavigationTripId = ""
    private var expectedNavigationPassengerIndex = -1
    private var expectedNavigationUrl = RIDES_URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = BlaBlaDynamicAccountRegistry(this)
        sessionStore = BlaBlaDynamicSessionStore(this)
        harvestStore = BlaBlaHarvestEvidenceStore(this)
        account = registry.get(intent?.getStringExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID)) ?: run {
            finish(); return
        }
        archive = BlaBlaMhtmlArchiveStore(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusView = TextView(this).apply {
            text = "${account.displayLabel} • coletando blocos HTML/MHTML…"
            setPadding(18, 18, 18, 18)
        }
        root.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        webView = WebView(this)
        configureProfiledWebView(webView, account)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                return if (interceptPhoneNavigation(url)) true else super.shouldOverrideUrlLoading(view, request)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return if (interceptPhoneNavigation(url)) true else super.shouldOverrideUrlLoading(view, url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!isBlaBla(url)) return
                scheduleCurrentPage(view, url)
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        loadTrackedUrl(RIDES_URL, Phase.RIDES)
    }

    private fun interceptPhoneNavigation(rawUrl: String?): Boolean {
        val url = rawUrl?.trim().orEmpty()
        if (!url.startsWith("tel:", ignoreCase = true)) return false
        val target = passengerTargets.getOrNull(passengerIndex)
        val pageUrl = if (::webView.isInitialized) webView.url.orEmpty() else ""
        val phone = normalizeCapturedPhone(url.substringAfter(':').substringBefore('?'))
        if (
            phase == Phase.PASSENGER &&
            target != null &&
            target.accountId == account.id &&
            passengerPageMatchesTarget(target, pageUrl)
        ) {
            interceptedPassengerPhone = phone
            UnifiedDebugEventStore.record(
                "PASSENGER_TEL_INTERCEPTED",
                packageName,
                "account=${account.displayLabel} tripId=${target.tripId} passengerKey=${target.externalPassengerKey} index=${passengerIndex + 1}/${passengerTargets.size} phonePresent=${phone != null} externalDialerOpened=false",
            )
        } else {
            recordStale("tel_intercept_without_current_passenger", navigationGeneration, passengerIndex)
        }
        return true
    }

    private fun scheduleCurrentPage(view: WebView, finishedUrl: String) {
        val expectedGeneration = navigationGeneration
        val expectedPhase = expectedNavigationPhase
        val expectedTripId = expectedNavigationTripId
        val expectedPassenger = expectedNavigationPassengerIndex
        view.postDelayed({
            if (!navigationIsCurrent(expectedGeneration, expectedPhase, expectedTripId, expectedPassenger, finishedUrl)) {
                recordStale("on_page_finished", expectedGeneration, expectedPassenger)
                return@postDelayed
            }
            if (!busy) handlePage()
        }, 900)
    }

    private fun handlePage() {
        if (busy) return
        if (!currentExpectedPageMatches()) {
            recordPageIdentityMismatch("handle_page", expectedNavigationTripId, webView.url.orEmpty())
            return
        }
        busy = true
        when (phase) {
            Phase.RIDES -> captureRides()
            Phase.TRIP -> captureTrip()
            Phase.PASSENGER_CARD -> openPassengerCard()
            Phase.PASSENGER -> capturePassenger()
            Phase.EDIT -> captureEdit()
            Phase.OPTIONS -> captureOptions()
        }
    }

    private fun captureRides() {
        val expectedNavigation = navigationGeneration
        evaluate<MhtmlRideList>(RIDE_LINKS_JS) { result ->
            if (!captureStillCurrent(expectedNavigation, Phase.RIDES, "", -1, result?.pageUrl.orEmpty())) {
                busy = false
                return@evaluate
            }
            if (result == null) {
                retryOrFinish("rides_unreadable")
                return@evaluate
            }
            val targets = result.tripHrefs
                .filter(::isSpecificTripHref)
                .mapNotNull { raw ->
                    val href = absoluteBlaBlaHref(raw)
                    tripIdFromHref(href)?.let { tripId -> TripTarget(account.id, tripId, href) }
                }
                .distinctBy { it.tripId }
                .take(MAX_TRIPS)
            if (targets.isEmpty() && pageReadAttempts < MAX_PAGE_READ_ATTEMPTS) {
                pageReadAttempts++
                busy = false
                webView.postDelayed({ handlePage() }, RETRY_MS)
                return@evaluate
            }
            pageReadAttempts = 0
            sessionStore.saveDiagnosticHtml(account, "rides", result.domHtml)
            archive.save(webView, account, "rides", "latest") { saved ->
                if (!captureStillCurrent(expectedNavigation, Phase.RIDES, "", -1, result.pageUrl)) {
                    busy = false
                    return@save
                }
                if (saved != null) archived++
                tripTargets = targets
                editTargetByTrip.clear()
                editTargets = emptyList()
                optionTargetByTrip.clear()
                UnifiedDebugEventStore.record(
                    "HARVEST_RIDES_CAPTURED",
                    packageName,
                    "account=${account.displayLabel} trips=${tripTargets.size} manifestFrozen=true html=true mhtml=${saved != null}",
                )
                tripIndex = 0
                busy = false
                loadNextTrip()
            }
        }
    }

    private fun captureTrip() {
        val target = tripTargets.getOrNull(tripIndex)
        if (target == null) {
            editTargets = editTargetByTrip.values.toList()
            phase = Phase.PASSENGER
            busy = false
            loadNextPassenger()
            return
        }
        val expectedNavigation = navigationGeneration
        val expectedIndex = tripIndex
        evaluate<MhtmlTripEvidence>(TRIP_EVIDENCE_JS) { result ->
            if (!captureStillCurrent(expectedNavigation, Phase.TRIP, target.tripId, expectedIndex, result?.pageUrl.orEmpty())) {
                busy = false
                return@evaluate
            }
            if (result == null) {
                retryCurrentOrAdvanceTrip(target, "trip_unreadable")
                return@evaluate
            }
            val capturedTripId = tripIdFromHref(result.pageUrl)
            if (capturedTripId != target.tripId) {
                recordPageIdentityMismatch("trip_after_evaluate", target.tripId, result.pageUrl)
                busy = false
                return@evaluate
            }
            val waitingForRoster = result.passengers.isEmpty() && !result.rosterComplete
            if (waitingForRoster && pageReadAttempts < MAX_TRIP_ROSTER_READ_ATTEMPTS) {
                pageReadAttempts++
                busy = false
                webView.postDelayed({ handlePage() }, RETRY_MS)
                return@evaluate
            }
            pageReadAttempts = 0
            tripEvidence[target.tripId] = result
            sessionStore.saveDiagnosticHtml(account, "trip-${target.tripId}", result.domHtml)
            passengerTargetsForTrip(target, result).forEach { passengerTarget ->
                if (passengerTargets.none { it.tripId == passengerTarget.tripId && it.discoveryKey == passengerTarget.discoveryKey }) {
                    passengerTargets += passengerTarget
                    UnifiedDebugEventStore.record(
                        "PASSENGER_CARD_DISCOVERED",
                        packageName,
                        "account=${account.displayLabel} tripId=${target.tripId} passengerKey=${passengerTarget.externalPassengerKey} cardIndex=${passengerTarget.cardIndex} hrefPresent=${passengerTarget.href.isNotBlank()} clickable=true",
                    )
                }
            }
            val realEdit = result.editHref
                .takeIf(String::isNotBlank)
                ?.let(::absoluteBlaBlaHref)
                ?.takeIf { href -> tripIdFromEditHref(href) == target.tripId && !isOptionsHref(href) }
            if (realEdit != null) {
                editTargetByTrip[target.tripId] = target.copy(href = realEdit)
            } else if (result.editHref.isNotBlank()) {
                UnifiedDebugEventStore.record(
                    "association_conflict",
                    packageName,
                    "account=${account.displayLabel} tripId=${target.tripId} reason=edit_link_trip_mismatch action=reject",
                )
            }
            archive.save(webView, account, "trip", target.tripId) { saved ->
                if (!captureStillCurrent(expectedNavigation, Phase.TRIP, target.tripId, expectedIndex, result.pageUrl)) {
                    busy = false
                    return@save
                }
                if (saved != null) archived++
                UnifiedDebugEventStore.record(
                    "HARVEST_TRIP_CAPTURED",
                    packageName,
                    "account=${account.displayLabel} expectedTripId=${target.tripId} capturedTripId=$capturedTripId index=${expectedIndex + 1}/${tripTargets.size} passengers=${result.passengers.size} rosterComplete=${result.rosterComplete} empty=${result.explicitEmptyRoster} stops=${result.itineraryStops.size} views=${result.views.coerceAtLeast(0)} editLinkPresent=${realEdit != null} identityMatch=true",
                )
                tripIndex = expectedIndex + 1
                busy = false
                loadNextTrip()
            }
        }
    }

    private fun passengerTargetsForTrip(target: TripTarget, result: MhtmlTripEvidence): List<PassengerTarget> {
        val discovered = linkedMapOf<String, PassengerTarget>()
        fun fromPassenger(index: Int, passenger: BlaBlaCollectorPassenger, hrefOverride: String? = null): PassengerTarget {
            val realHref = (hrefOverride ?: passenger.booking_href)
                ?.takeIf(String::isNotBlank)
                ?.let(::absoluteBlaBlaHref)
                ?.takeIf(::isPassengerHref)
                .orEmpty()
            return PassengerTarget(
                accountId = account.id,
                tripId = target.tripId,
                tripHref = target.href,
                href = realHref,
                cardIndex = index,
                expectedName = passenger.name.trim(),
                expectedSeats = passenger.seats.coerceAtLeast(1),
                expectedBoarding = passenger.boarding.orEmpty().trim(),
                expectedDropoff = passenger.dropoff.orEmpty().trim(),
                requiresSemanticProof = realHref.isBlank(),
            )
        }
        result.passengers.forEachIndexed { index, passenger ->
            val passengerTarget = fromPassenger(index, passenger)
            discovered[passengerTarget.discoveryKey] = passengerTarget
        }
        result.passengerHrefs.forEach { raw ->
            when {
                raw.startsWith(CARD_TARGET_PREFIX) -> {
                    val cardIndex = raw.removePrefix(CARD_TARGET_PREFIX).toIntOrNull() ?: return@forEach
                    val existingPassenger = result.passengers.getOrNull(cardIndex) ?: return@forEach
                    val passengerTarget = fromPassenger(cardIndex, existingPassenger)
                    discovered.putIfAbsent(passengerTarget.discoveryKey, passengerTarget)
                }
                else -> {
                    val passengerHref = absoluteBlaBlaHref(raw).takeIf(::isPassengerHref) ?: return@forEach
                    val canonical = canonicalHref(passengerHref)
                    val matchIndex = result.passengers.indexOfFirst { passenger ->
                        passenger.booking_href?.let(::absoluteBlaBlaHref)?.let(::canonicalHref) == canonical
                    }
                    val passengerTarget = if (matchIndex >= 0) {
                        fromPassenger(matchIndex, result.passengers[matchIndex], passengerHref)
                    } else {
                        PassengerTarget(
                            accountId = account.id,
                            tripId = target.tripId,
                            tripHref = target.href,
                            href = passengerHref,
                        )
                    }
                    discovered[passengerTarget.discoveryKey] = passengerTarget
                }
            }
        }
        return discovered.values.toList()
    }

    private fun retryCurrentOrAdvanceTrip(target: TripTarget, reason: String) {
        if (pageReadAttempts < MAX_PAGE_READ_ATTEMPTS) {
            pageReadAttempts++
            busy = false
            webView.postDelayed({ handlePage() }, RETRY_MS)
            return
        }
        pageReadAttempts = 0
        UnifiedDebugEventStore.record(
            "HARVEST_BLOCK_UNREADABLE",
            packageName,
            "account=${account.displayLabel} block=trip tripId=${target.tripId} reason=$reason",
        )
        tripIndex++
        busy = false
        loadNextTrip()
    }

    private fun openPassengerCard() {
        val target = passengerTargets.getOrNull(passengerIndex)
        if (target == null) {
            phase = Phase.EDIT
            busy = false
            loadNextEdit()
            return
        }
        val expectedIndex = passengerIndex
        val expectedNavigation = navigationGeneration
        if (target.cardIndex < 0 || !BlaBlaHarvestAssociation.tripPageMatches(target.tripId, webView.url.orEmpty())) {
            recordPageIdentityMismatch("passenger_card_parent", target.tripId, webView.url.orEmpty())
            advancePassenger(expectedIndex, "parent_trip_not_proven")
            return
        }
        evaluate<MhtmlPassengerCardOpenState>(passengerCardOpenJs(target.cardIndex)) { state ->
            if (!captureStillCurrent(expectedNavigation, Phase.PASSENGER_CARD, target.tripId, expectedIndex, webView.url.orEmpty())) {
                busy = false
                return@evaluate
            }
            if (state?.clicked == true) {
                pageReadAttempts = 0
                beginImplicitPassengerNavigation(target, expectedIndex)
                UnifiedDebugEventStore.record(
                    "PASSENGER_CARD_OPENED",
                    packageName,
                    "account=${account.displayLabel} tripId=${target.tripId} index=${expectedIndex + 1}/${passengerTargets.size} hrefPresent=false cardIndex=${target.cardIndex} clicked=true identityPending=true",
                )
                webView.postDelayed({
                    if (!bindPassengerTargetToCurrentPage(expectedIndex, target.tripId)) {
                        if (phase == Phase.PASSENGER && passengerIndex == expectedIndex) {
                            advancePassenger(expectedIndex, "passenger_url_not_proven")
                        }
                        return@postDelayed
                    }
                    if (phase == Phase.PASSENGER && passengerIndex == expectedIndex && !busy) handlePage()
                }, PASSENGER_NAVIGATION_SETTLE_MS)
                return@evaluate
            }
            if (pageReadAttempts < MAX_PASSENGER_CARD_READ_ATTEMPTS) {
                pageReadAttempts++
                busy = false
                webView.postDelayed({ handlePage() }, RETRY_MS)
                return@evaluate
            }
            pageReadAttempts = 0
            advancePassenger(expectedIndex, "card_not_clickable")
        }
    }

    private fun beginImplicitPassengerNavigation(target: PassengerTarget, expectedIndex: Int) {
        navigationGeneration++
        evaluationGeneration++
        phase = Phase.PASSENGER
        expectedNavigationPhase = Phase.PASSENGER
        expectedNavigationTripId = target.tripId
        expectedNavigationPassengerIndex = expectedIndex
        expectedNavigationUrl = ""
        busy = false
    }

    private fun bindPassengerTargetToCurrentPage(expectedIndex: Int, expectedTripId: String): Boolean {
        if (phase != Phase.PASSENGER || passengerIndex != expectedIndex || expectedNavigationPassengerIndex != expectedIndex) return false
        val current = passengerTargets.getOrNull(expectedIndex) ?: return false
        if (current.tripId != expectedTripId || current.accountId != account.id) return false
        if (current.href.isNotBlank()) return passengerPageMatchesTarget(current, webView.url.orEmpty())
        val actualHref = webView.url.orEmpty().takeIf(::isPassengerHref) ?: run {
            recordPageIdentityMismatch("passenger_bind_url", expectedTripId, webView.url.orEmpty())
            return false
        }
        val actualKey = passengerKey(actualHref)
        val duplicate = passengerTargets.withIndex().any { (index, other) ->
            index != expectedIndex &&
                other.tripId == expectedTripId &&
                other.href.isNotBlank() &&
                passengerKey(other.href) == actualKey
        }
        if (duplicate) {
            UnifiedDebugEventStore.record(
                "association_conflict",
                packageName,
                "account=${account.displayLabel} tripId=$expectedTripId passengerKey=$actualKey reason=duplicate_passenger_key_within_trip action=reject",
            )
            return false
        }
        val bound = current.copy(href = actualHref)
        passengerTargets[expectedIndex] = bound
        if (current.cardIndex >= 0) {
            val summary = tripEvidence[expectedTripId]
            val passengers = summary?.passengers?.toMutableList()
            val existing = passengers?.getOrNull(current.cardIndex)
            if (summary != null && passengers != null && existing != null) {
                passengers[current.cardIndex] = existing.copy(booking_href = actualHref)
                tripEvidence[expectedTripId] = summary.copy(passengers = passengers)
            }
        }
        UnifiedDebugEventStore.record(
            "PASSENGER_TARGET_BOUND",
            packageName,
            "account=${account.displayLabel} parentTripId=$expectedTripId passengerKey=$actualKey cardIndex=${current.cardIndex} realHref=true semanticProofRequired=${current.requiresSemanticProof}",
        )
        return passengerPageMatchesTarget(bound, actualHref)
    }

    private fun capturePassenger() {
        if (!bindPassengerTargetToCurrentPage(passengerIndex, passengerTargets.getOrNull(passengerIndex)?.tripId.orEmpty())) {
            val target = passengerTargets.getOrNull(passengerIndex)
            if (target != null && target.href.isNotBlank() && passengerPageMatchesTarget(target, webView.url.orEmpty())) {
                // already bound direct target
            } else {
                advancePassenger(passengerIndex, "passenger_page_identity_unproven")
                return
            }
        }
        val target = passengerTargets.getOrNull(passengerIndex)
        if (target == null) {
            phase = Phase.EDIT
            busy = false
            loadNextEdit()
            return
        }
        val expectedIndex = passengerIndex
        val expectedNavigation = navigationGeneration
        evaluate<MhtmlPassengerEvidence>(PASSENGER_EVIDENCE_JS) { result ->
            if (!passengerCaptureStillCurrent(expectedNavigation, expectedIndex, target, result?.pageUrl.orEmpty())) {
                busy = false
                return@evaluate
            }
            val pageLooksReady = result != null && (
                result.visibleName.isNotBlank() ||
                    result.callActionPresent ||
                    result.boarding.isNotBlank() ||
                    result.dropoff.isNotBlank()
                )
            if (result != null && pageLooksReady && !passengerEvidenceCompatible(target, result)) {
                recordPageIdentityMismatch("passenger_semantic_mismatch", target.tripId, result.pageUrl)
                advancePassenger(expectedIndex, "passenger_semantic_mismatch")
                return@evaluate
            }
            val directPhone = normalizeCapturedPhone(result?.phone)
            val capturedPhone = directPhone ?: interceptedPassengerPhone
            if (capturedPhone == null && result?.callActionPresent == true && !passengerCallActionTriggered) {
                passengerCallActionTriggered = true
                UnifiedDebugEventStore.record(
                    "PASSENGER_CALL_ACTION_PRESENT",
                    packageName,
                    "account=${account.displayLabel} tripId=${target.tripId} passengerKey=${target.externalPassengerKey} index=${expectedIndex + 1}/${passengerTargets.size} actionPresent=true clickIntercepted=true",
                )
                evaluateRaw(CLICK_CALL_ACTION_JS) {
                    if (!passengerCaptureStillCurrent(expectedNavigation, expectedIndex, target, webView.url.orEmpty())) {
                        busy = false
                        return@evaluateRaw
                    }
                    busy = false
                    webView.postDelayed({
                        if (phase == Phase.PASSENGER && passengerIndex == expectedIndex && !busy) handlePage()
                    }, PASSENGER_CALL_SETTLE_MS)
                }
                return@evaluate
            }
            if ((capturedPhone == null || !pageLooksReady) && pageReadAttempts < MAX_PASSENGER_PHONE_READ_ATTEMPTS) {
                pageReadAttempts++
                busy = false
                webView.postDelayed({
                    if (phase == Phase.PASSENGER && passengerIndex == expectedIndex && !busy) handlePage()
                }, RETRY_MS)
                return@evaluate
            }
            pageReadAttempts = 0
            if (!pageLooksReady) {
                if (target.href.isNotBlank() && passengerReloadAttempts < MAX_PASSENGER_PAGE_RELOAD_ATTEMPTS) {
                    passengerReloadAttempts++
                    passengerCallActionTriggered = false
                    interceptedPassengerPhone = null
                    UnifiedDebugEventStore.record(
                        "PASSENGER_PAGE_RELOAD",
                        packageName,
                        "account=${account.displayLabel} tripId=${target.tripId} passengerKey=${target.externalPassengerKey} index=${expectedIndex + 1}/${passengerTargets.size} attempt=$passengerReloadAttempts reason=evidence_not_ready hrefPresent=true",
                    )
                    busy = false
                    loadTrackedUrl(target.href, Phase.PASSENGER, target.tripId, expectedIndex)
                    return@evaluate
                }
                advancePassenger(expectedIndex, "passenger_evidence_not_ready")
                return@evaluate
            }
            val baseEvidence = result ?: MhtmlPassengerEvidence()
            val evidence = baseEvidence.copy(phone = capturedPhone ?: baseEvidence.phone)
            persistPassengerEvidence(target, evidence)
            sessionStore.saveDiagnosticHtml(
                account,
                "passenger-${target.tripId}-${target.archiveKey}",
                evidence.domHtml,
            )
            archive.save(webView, account, "passenger", "${target.tripId}-${target.archiveKey}") { saved ->
                if (!passengerCaptureStillCurrent(expectedNavigation, expectedIndex, target, evidence.pageUrl)) {
                    busy = false
                    return@save
                }
                if (saved != null) archived++
                UnifiedDebugEventStore.record(
                    "HARVEST_PASSENGER_CAPTURED",
                    packageName,
                    "account=${account.displayLabel} parentTripId=${target.tripId} enrichedTripId=${target.tripId} passengerKey=${target.externalPassengerKey} index=${expectedIndex + 1}/${passengerTargets.size} namePresent=${evidence.visibleName.isNotBlank()} phonePresent=${normalizeCapturedPhone(evidence.phone) != null} seats=${evidence.seats.coerceAtLeast(1)} routePresent=${evidence.boarding.isNotBlank() && evidence.dropoff.isNotBlank()} callActionPresent=${evidence.callActionPresent} identityMatch=true",
                )
                passengerIndex = expectedIndex + 1
                resetPassengerReadState()
                busy = false
                loadNextPassenger()
            }
        }
    }

    private fun passengerEvidenceCompatible(target: PassengerTarget, evidence: MhtmlPassengerEvidence): Boolean {
val canonicalIdentityProven = target.href.isNotBlank() &&
  BlaBlaHarvestAssociation.passengerCanonicalIdentityProven(
      target.externalPassengerKey,
      target.href,
      evidence.pageUrl,
  )
var semanticCompatible = true
var semanticEvidencePresent = false
if (target.expectedName.isNotBlank() && evidence.visibleName.isNotBlank()) {
  semanticEvidencePresent = true
  if (normalizeText(target.expectedName) != normalizeText(evidence.visibleName)) semanticCompatible = false
}
if (target.expectedBoarding.isNotBlank() && evidence.boarding.isNotBlank()) {
  semanticEvidencePresent = true
  if (!samePlaceEvidence(target.expectedBoarding, evidence.boarding)) semanticCompatible = false
}
if (target.expectedDropoff.isNotBlank() && evidence.dropoff.isNotBlank()) {
  semanticEvidencePresent = true
  if (!samePlaceEvidence(target.expectedDropoff, evidence.dropoff)) semanticCompatible = false
}
if (canonicalIdentityProven && semanticEvidencePresent && !semanticCompatible) {
  UnifiedDebugEventStore.record(
      "HARVEST_PASSENGER_SEMANTIC_DIFFERENCE",
      packageName,
      "account=${account.displayLabel} tripId=${target.tripId} passengerKey=${target.externalPassengerKey} canonicalIdentity=true action=accept_canonical_identity",
  )
}
return BlaBlaHarvestAssociation.passengerEvidenceAccepted(canonicalIdentityProven)
}

    private fun persistPassengerEvidence(target: PassengerTarget, evidence: MhtmlPassengerEvidence) {
        if (!passengerPageMatchesTarget(target, evidence.pageUrl)) {
            recordPageIdentityMismatch("persist_passenger", target.tripId, evidence.pageUrl)
            return
        }
        passengerEvidence[target.scopedEvidenceKey] = evidence
    }

    private fun captureEdit() {
        val target = editTargets.getOrNull(editIndex)
        if (target == null) {
            phase = Phase.OPTIONS
            optionTargets = optionTargetByTrip.values.toList()
            optionIndex = 0
            busy = false
            loadNextOption()
            return
        }
        val expectedNavigation = navigationGeneration
        val expectedIndex = editIndex
        evaluate<MhtmlEditEvidence>(EDIT_EVIDENCE_JS) { result ->
            if (!captureStillCurrent(expectedNavigation, Phase.EDIT, target.tripId, expectedIndex, result?.pageUrl.orEmpty())) {
                busy = false
                return@evaluate
            }
            if ((result == null || result.optionsHref.isBlank()) && pageReadAttempts < MAX_EDIT_LINK_READ_ATTEMPTS) {
                pageReadAttempts++
                busy = false
                webView.postDelayed({ handlePage() }, RETRY_MS)
                return@evaluate
            }
            pageReadAttempts = 0
            if (result == null) {
                UnifiedDebugEventStore.record(
                    "HARVEST_BLOCK_UNREADABLE",
                    packageName,
                    "account=${account.displayLabel} block=edit tripId=${target.tripId} reason=edit_dom_unreadable",
                )
                editIndex = expectedIndex + 1
                busy = false
                loadNextEdit()
                return@evaluate
            }
            val evidence = result
            val options = evidence.optionsHref
                .takeIf(String::isNotBlank)
                ?.let(::absoluteBlaBlaHref)
                ?.takeIf(::isOptionsHref)
                ?.takeIf { optionHref -> tripIdFromOptionsHref(optionHref) == target.tripId }
            if (options != null) {
                optionTargetByTrip[target.tripId] = target.copy(href = options)
            } else {
                if (evidence.optionsHref.isNotBlank()) {
                    UnifiedDebugEventStore.record(
                        "association_conflict",
                        packageName,
                        "account=${account.displayLabel} tripId=${target.tripId} reason=options_link_trip_mismatch action=reject",
                    )
                }
                UnifiedDebugEventStore.record(
                    "HARVEST_BLOCK_UNREADABLE",
                    packageName,
                    "account=${account.displayLabel} block=edit tripId=${target.tripId} reason=options_link_missing_or_mismatch",
                )
            }
            sessionStore.saveDiagnosticHtml(account, "edit-${target.tripId}", evidence.domHtml)
            archive.save(webView, account, "edit", target.tripId) { saved ->
                if (!captureStillCurrent(expectedNavigation, Phase.EDIT, target.tripId, expectedIndex, evidence.pageUrl)) {
                    busy = false
                    return@save
                }
                if (saved != null) archived++
                UnifiedDebugEventStore.record(
                    "HARVEST_EDIT_CAPTURED",
                    packageName,
                    "account=${account.displayLabel} expectedTripId=${target.tripId} capturedTripId=${tripIdFromEditHref(evidence.pageUrl).orEmpty()} optionsLinkPresent=${options != null} html=true mhtml=${saved != null} identityMatch=true",
                )
                editIndex = expectedIndex + 1
                busy = false
                loadNextEdit()
            }
        }
    }

    private fun captureOptions() {
        val target = optionTargets.getOrNull(optionIndex)
        if (target == null) {
            finishHarvest()
            return
        }
        val expectedNavigation = navigationGeneration
        val expectedIndex = optionIndex
        evaluate<SeatOptionState>(SEAT_OPTIONS_READ_JS) { result ->
            if (!captureStillCurrent(expectedNavigation, Phase.OPTIONS, target.tripId, expectedIndex, result?.pageUrl.orEmpty())) {
                busy = false
                return@evaluate
            }
            if ((result == null || result.seats < 0) && pageReadAttempts < MAX_PAGE_READ_ATTEMPTS) {
                pageReadAttempts++
                busy = false
                webView.postDelayed({ handlePage() }, RETRY_MS)
                return@evaluate
            }
            pageReadAttempts = 0
            if (result == null) {
                UnifiedDebugEventStore.record(
                    "HARVEST_BLOCK_UNREADABLE",
                    packageName,
                    "account=${account.displayLabel} block=options tripId=${target.tripId} reason=options_dom_unreadable",
                )
                optionIndex = expectedIndex + 1
                busy = false
                loadNextOption()
                return@evaluate
            }
            val state = result
            if (state.seats >= 0) publishedSeatsByTrip[target.tripId] = state.seats
            sessionStore.saveDiagnosticHtml(account, "options-${target.tripId}", state.domHtml)
            archive.save(webView, account, "options", target.tripId) { saved ->
                if (!captureStillCurrent(expectedNavigation, Phase.OPTIONS, target.tripId, expectedIndex, state.pageUrl)) {
                    busy = false
                    return@save
                }
                if (saved != null) archived++
                UnifiedDebugEventStore.record(
                    "SEAT_OPTIONS_CAPTURED",
                    packageName,
                    "account=${account.displayLabel} expectedTripId=${target.tripId} capturedTripId=${tripIdFromOptionsHref(state.pageUrl).orEmpty()} publishedSeats=${state.seats} canAdd=${state.canAdd} canRemove=${state.canRemove} savePresent=${state.savePresent} html=true mhtml=${saved != null} identityMatch=true",
                )
                optionIndex = expectedIndex + 1
                busy = false
                loadNextOption()
            }
        }
    }

    private fun loadNextTrip() {
        val target = tripTargets.getOrNull(tripIndex)
        if (target == null) {
            editTargets = editTargetByTrip.values.toList()
            phase = Phase.PASSENGER
            passengerIndex = 0
            loadNextPassenger()
        } else {
            statusView.text = "${account.displayLabel} • resumo ${tripIndex + 1}/${tripTargets.size}"
            loadTrackedUrl(target.href, Phase.TRIP, target.tripId, tripIndex)
        }
    }

    private fun loadNextPassenger() {
        val target = passengerTargets.getOrNull(passengerIndex)
        if (target == null) {
            phase = Phase.EDIT
            editIndex = 0
            loadNextEdit()
        } else {
            resetPassengerReadState()
            statusView.text = "${account.displayLabel} • passageiro ${passengerIndex + 1}/${passengerTargets.size}"
            if (target.href.isNotBlank()) {
                loadTrackedUrl(target.href, Phase.PASSENGER, target.tripId, passengerIndex)
            } else if (target.cardIndex >= 0) {
                loadTrackedUrl(target.tripHref, Phase.PASSENGER_CARD, target.tripId, passengerIndex)
            } else {
                advancePassenger(passengerIndex, "target_missing")
            }
        }
    }

    private fun loadNextEdit() {
        val target = editTargets.getOrNull(editIndex)
        if (target == null) {
            phase = Phase.OPTIONS
            optionTargets = optionTargetByTrip.values.toList()
            optionIndex = 0
            loadNextOption()
        } else {
            statusView.text = "${account.displayLabel} • editar ${editIndex + 1}/${editTargets.size}"
            loadTrackedUrl(target.href, Phase.EDIT, target.tripId, editIndex)
        }
    }

    private fun loadNextOption() {
        val target = optionTargets.getOrNull(optionIndex)
        if (target == null) {
            finishHarvest()
        } else {
            statusView.text = "${account.displayLabel} • lugares ${optionIndex + 1}/${optionTargets.size}"
            loadTrackedUrl(target.href, Phase.OPTIONS, target.tripId, optionIndex)
        }
    }

    private fun loadTrackedUrl(url: String, targetPhase: Phase, tripId: String = "", passengerOrBlockIndex: Int = -1) {
        navigationGeneration++
        evaluationGeneration++
        phase = targetPhase
        expectedNavigationPhase = targetPhase
        expectedNavigationTripId = tripId
        expectedNavigationPassengerIndex = passengerOrBlockIndex
        expectedNavigationUrl = url
        busy = false
        webView.loadUrl(url)
    }

    private fun navigationIsCurrent(
        expectedGeneration: Long,
        expectedPhase: Phase,
        expectedTripId: String,
        expectedPassengerOrBlockIndex: Int,
        finishedUrl: String,
    ): Boolean {
        if (expectedGeneration != navigationGeneration || expectedPhase != phase || expectedPhase != expectedNavigationPhase) return false
        if (expectedTripId != expectedNavigationTripId || expectedPassengerOrBlockIndex != expectedNavigationPassengerIndex) return false
        if (!pageIdentityMatches(expectedPhase, expectedTripId, expectedPassengerOrBlockIndex, finishedUrl)) return false
        return pageIdentityMatches(expectedPhase, expectedTripId, expectedPassengerOrBlockIndex, webView.url.orEmpty())
    }

    private fun currentExpectedPageMatches(): Boolean = pageIdentityMatches(
        expectedNavigationPhase,
        expectedNavigationTripId,
        expectedNavigationPassengerIndex,
        webView.url.orEmpty(),
    )

    private fun captureStillCurrent(
        expectedGeneration: Long,
        expectedPhase: Phase,
        expectedTripId: String,
        expectedPassengerOrBlockIndex: Int,
        capturedUrl: String,
    ): Boolean {
        if (
            expectedGeneration != navigationGeneration ||
            expectedPhase != phase ||
            expectedPhase != expectedNavigationPhase ||
            expectedTripId != expectedNavigationTripId ||
            expectedPassengerOrBlockIndex != expectedNavigationPassengerIndex
        ) {
            recordStale("capture_context", expectedGeneration, expectedPassengerOrBlockIndex)
            return false
        }
        if (!pageIdentityMatches(expectedPhase, expectedTripId, expectedPassengerOrBlockIndex, webView.url.orEmpty())) {
            recordPageIdentityMismatch("capture_current_page", expectedTripId, webView.url.orEmpty())
            return false
        }
        if (capturedUrl.isNotBlank() && !pageIdentityMatches(expectedPhase, expectedTripId, expectedPassengerOrBlockIndex, capturedUrl)) {
            recordPageIdentityMismatch("capture_result_page", expectedTripId, capturedUrl)
            return false
        }
        return true
    }

    private fun passengerCaptureStillCurrent(
        expectedGeneration: Long,
        expectedIndex: Int,
        target: PassengerTarget,
        capturedUrl: String,
    ): Boolean {
        if (
            expectedGeneration != navigationGeneration ||
            phase != Phase.PASSENGER ||
            expectedNavigationPhase != Phase.PASSENGER ||
            expectedIndex != passengerIndex ||
            expectedIndex != expectedNavigationPassengerIndex ||
            target.tripId != expectedNavigationTripId ||
            target.accountId != account.id
        ) {
            recordStale("passenger_capture_context", expectedGeneration, expectedIndex)
            return false
        }
        val currentTarget = passengerTargets.getOrNull(expectedIndex) ?: return false
        if (currentTarget.tripId != target.tripId || currentTarget.externalPassengerKey != target.externalPassengerKey) {
            UnifiedDebugEventStore.record(
                "association_conflict",
                packageName,
                "account=${account.displayLabel} tripId=${target.tripId} passengerKey=${target.externalPassengerKey} reason=target_changed_during_capture action=reject",
            )
            return false
        }
        if (!passengerPageMatchesTarget(currentTarget, webView.url.orEmpty())) {
            recordPageIdentityMismatch("passenger_current_page", target.tripId, webView.url.orEmpty())
            return false
        }
        if (capturedUrl.isNotBlank() && !passengerPageMatchesTarget(currentTarget, capturedUrl)) {
            recordPageIdentityMismatch("passenger_result_page", target.tripId, capturedUrl)
            return false
        }
        return true
    }

    private fun pageIdentityMatches(targetPhase: Phase, tripId: String, index: Int, url: String): Boolean = when (targetPhase) {
        Phase.RIDES -> BlaBlaHarvestAssociation.ridesPageMatches(url)
        Phase.TRIP -> BlaBlaHarvestAssociation.tripPageMatches(tripId, url)
        Phase.PASSENGER_CARD -> BlaBlaHarvestAssociation.tripPageMatches(tripId, url)
        Phase.PASSENGER -> passengerTargets.getOrNull(index)?.let { target ->
            target.tripId == tripId && passengerPageMatchesTarget(target, url)
        } == true
        Phase.EDIT -> BlaBlaHarvestAssociation.editPageMatches(tripId, url)
        Phase.OPTIONS -> BlaBlaHarvestAssociation.optionsPageMatches(tripId, url)
    }

    private fun passengerPageMatchesTarget(target: PassengerTarget, url: String): Boolean {
        if (!isPassengerHref(url)) return false
        if (target.href.isBlank()) return true
        return BlaBlaHarvestAssociation.passengerPageMatches(target.externalPassengerKey, url)
    }

    private fun recordStale(reason: String, expectedGeneration: Long, expectedIndex: Int) {
        UnifiedDebugEventStore.record(
            "HARVEST_STALE_CALLBACK_IGNORED",
            packageName,
            "account=${account.displayLabel} reason=$reason expectedNavigation=$expectedGeneration currentNavigation=$navigationGeneration expectedPhase=${expectedNavigationPhase.name.lowercase()} currentPhase=${phase.name.lowercase()} expectedIndex=$expectedIndex currentTripIndex=$tripIndex currentPassengerIndex=$passengerIndex currentEditIndex=$editIndex currentOptionIndex=$optionIndex",
        )
    }

    private fun recordPageIdentityMismatch(reason: String, expectedTripId: String, capturedUrl: String) {
        UnifiedDebugEventStore.record(
            "HARVEST_PAGE_IDENTITY_MISMATCH",
            packageName,
            "account=${account.displayLabel} reason=$reason phase=${phase.name.lowercase()} expectedTripId=$expectedTripId expectedUrl=${sanitizeHarvestUrl(expectedNavigationUrl)} capturedUrl=${sanitizeHarvestUrl(capturedUrl)} action=reject",
        )
    }

    private fun advancePassenger(expectedIndex: Int, reason: String) {
        val target = passengerTargets.getOrNull(expectedIndex)
        UnifiedDebugEventStore.record(
            "HARVEST_BLOCK_UNREADABLE",
            packageName,
            "account=${account.displayLabel} block=passenger tripId=${target?.tripId.orEmpty()} passengerKey=${target?.externalPassengerKey.orEmpty()} reason=$reason",
        )
        passengerIndex = maxOf(passengerIndex, expectedIndex + 1)
        resetPassengerReadState()
        busy = false
        loadNextPassenger()
    }

    private fun resetPassengerReadState() {
        pageReadAttempts = 0
        passengerCallActionTriggered = false
        interceptedPassengerPhone = null
        passengerReloadAttempts = 0
    }

    private fun finishHarvest() {
        val enrichment = applyHarvestToSession()
        UnifiedDebugEventStore.record(
            "MHTML_HARVEST_COMPLETE",
            packageName,
            "account=${account.displayLabel} trips=${tripTargets.size} passengerPages=${passengerTargets.size} editPages=${editTargets.size} optionPages=${optionTargets.size} archives=$archived enrichedTrips=${enrichment.first} enrichedPassengers=${enrichment.second} deterministic=true",
        )
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID, account.id)
                .putExtra("archive_count", archived),
        )
        finish()
    }

    private fun applyHarvestToSession(): Pair<Int, Int> {
        val snapshot = sessionStore.read(account) ?: return 0 to 0
        val previousEvidenceByTripId = harvestStore.read(account.id).associateBy(BlaBlaHarvestTripEvidence::tripId)
        val persistedEvidence = mutableListOf<BlaBlaHarvestTripEvidence>()
        val touchedEvidenceTripIds = mutableSetOf<String>()
        var enrichedTrips = 0
        var enrichedPassengers = 0
        val updatedTrips = snapshot.trips.map { trip ->
            val tripId = trip.trip_id?.trim()?.takeIf(String::isNotEmpty)
                ?: trip.trip_href?.let(::tripIdFromHref)
                ?: run {
                    UnifiedDebugEventStore.record(
                        "HARVEST_TRIP_AWAITING_READ",
                        packageName,
                        "account=${account.displayLabel} tripId=missing action=preserve_last_confirmed reason=trip_identity_missing",
                    )
                    return@map trip
                }
            touchedEvidenceTripIds += tripId
            val previousEvidence = previousEvidenceByTripId[tripId]
            val summary = tripEvidence[tripId]
            if (summary == null) {
                previousEvidence?.let(persistedEvidence::add)
                UnifiedDebugEventStore.record(
                    "HARVEST_TRIP_AWAITING_READ",
                    packageName,
                    "account=${account.displayLabel} tripId=$tripId action=preserve_last_confirmed rosterComplete=${trip.passenger_roster_complete} passengers=${trip.passengers.size}",
                )
                return@map trip
            }
            val mergedPassengers = summary.passengers.map { passenger -> enrichPassenger(tripId, passenger) }
            val rosterComplete = summary.rosterComplete || summary.explicitEmptyRoster
            val stops = summary.itineraryStops.ifEmpty { previousEvidence?.itineraryStops.orEmpty() }
            val occupied = occupiedSeatsForTimeline(mergedPassengers, stops)
            val publishedSeats = publishedSeatsByTrip[tripId] ?: previousEvidence?.publishedSeats
            val views = summary.views.takeIf { it >= 0 } ?: previousEvidence?.views
            val capturedTrip = trip.copy(
                passengers = mergedPassengers,
                booked_seats = occupied,
                passenger_roster_complete = rosterComplete,
            )
            val monotonicTrip = BlaBlaCollectorPassengerModule.mergeMonotonic(
                previous = trip,
                current = capturedTrip,
            )
            persistedEvidence += BlaBlaHarvestTripEvidence(
                tripId = tripId,
                publishedSeats = publishedSeats,
                views = views,
                itineraryStops = stops,
                passengers = monotonicTrip.passengers,
                passengerRosterComplete = monotonicTrip.passenger_roster_complete,
            )
            enrichedTrips++
            enrichedPassengers += monotonicTrip.passengers.size
            UnifiedDebugEventStore.record(
                "HARVEST_TRIP_ENRICHED",
                packageName,
                "account=${account.displayLabel} tripId=$tripId passengers=${monotonicTrip.passengers.size} phones=${monotonicTrip.passengers.count { !it.phone.isNullOrBlank() }} totalPassengerSeats=${monotonicTrip.passengers.sumOf { it.seats.coerceAtLeast(1) }} bookedSeats=${monotonicTrip.booked_seats} publishedSeats=${publishedSeats ?: -1} rosterComplete=${monotonicTrip.passenger_roster_complete} stops=${stops.size} views=${views ?: -1} deterministicJoin=true phoneEvidenceScoped=true monotonic=true",
            )
            monotonicTrip
        }
        persistedEvidence += previousEvidenceByTripId
            .filterKeys { it !in touchedEvidenceTripIds }
            .values
        harvestStore.replace(account.id, persistedEvidence)
        saveDeterministicSnapshot(snapshot, updatedTrips)
        return enrichedTrips to enrichedPassengers
    }

    private fun saveDeterministicSnapshot(
        previous: BlaBlaDynamicSessionSnapshot,
        trips: List<BlaBlaCollectorTrip>,
    ) {
        val target = File(filesDir, "blablacar-dynamic-session-${account.id}.json")
        val temp = File(target.parentFile, target.name + ".harvest.tmp")
        val replacement = previous.copy(
            profileUuid = account.profileUuid,
            profileLabel = account.displayLabel,
            lastUrl = previous.lastUrl,
            updatedAtMillis = System.currentTimeMillis(),
            trips = trips,
        )
        temp.writeText(json.encodeToString(replacement), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.writeText(temp.readText(Charsets.UTF_8), Charsets.UTF_8)
            temp.delete()
        }
        UnifiedDebugEventStore.record(
            "SNAPSHOT_SAVED",
            packageName,
            "account=${account.displayLabel} expectedUuid=${account.profileUuid.orEmpty()} trips=${trips.size} rosterComplete=${trips.count { it.passenger_roster_complete }} rosterIncomplete=${trips.count { !it.passenger_roster_complete }} preservedIncomplete=0 skipped=${previous.skippedTrips} identityVerified=${previous.identityVerified} deterministicHarvest=true",
        )
    }

    private fun enrichPassenger(tripId: String, passenger: BlaBlaCollectorPassenger): BlaBlaCollectorPassenger {
        val href = passenger.booking_href?.trim()?.takeIf(String::isNotEmpty)
            ?: return passenger.copy(phone = null)
        val absoluteHref = absoluteBlaBlaHref(href)
        val key = BlaBlaHarvestAssociation.passengerEvidenceKey(account.id, tripId, passengerKey(absoluteHref))
        val evidence = passengerEvidence[key]
            ?: return passenger.copy(phone = null, booking_href = absoluteHref)
        if (!BlaBlaHarvestAssociation.passengerPageMatches(passengerKey(absoluteHref), evidence.pageUrl)) {
            UnifiedDebugEventStore.record(
                "association_conflict",
                packageName,
                "account=${account.displayLabel} tripId=$tripId passengerKey=${passengerKey(absoluteHref)} reason=evidence_page_mismatch action=reject",
            )
            return passenger.copy(phone = null, booking_href = absoluteHref)
        }
        val phone = normalizeCapturedPhone(evidence.phone)
        return passenger.copy(
            name = passenger.name.ifBlank { evidence.visibleName.trim() },
            seats = maxOf(passenger.seats.coerceAtLeast(1), evidence.seats.coerceAtLeast(1)),
            boarding = passenger.boarding?.takeIf(String::isNotBlank) ?: evidence.boarding.takeIf(String::isNotBlank),
            dropoff = passenger.dropoff?.takeIf(String::isNotBlank) ?: evidence.dropoff.takeIf(String::isNotBlank),
            phone = phone,
            booking_href = absoluteHref,
        )
    }

    private fun occupiedSeatsForTimeline(passengers: List<BlaBlaCollectorPassenger>, stops: List<String>): Int {
        if (passengers.isEmpty()) return 0
        val total = passengers.sumOf { it.seats.coerceAtLeast(1) }
        if (stops.size < 2) return total
        val loads = IntArray(stops.size - 1)
        var unresolved = false
        passengers.forEach { passenger ->
            val boarding = stopIndex(stops, passenger.boarding)
            val dropoff = stopIndex(stops, passenger.dropoff, minIndex = boarding + 1)
            if (boarding < 0 || dropoff <= boarding) {
                unresolved = true
            } else {
                for (segment in boarding until dropoff) loads[segment] += passenger.seats.coerceAtLeast(1)
            }
        }
        val maxKnown = loads.maxOrNull() ?: 0
        return if (unresolved) maxOf(maxKnown, total) else maxKnown
    }

    private fun stopIndex(stops: List<String>, raw: String?, minIndex: Int = 0): Int {
        val wanted = normalizeText(raw.orEmpty())
        if (wanted.isBlank()) return -1
        return stops.indices.firstOrNull { index ->
            index >= minIndex && samePlaceEvidence(stops[index], raw.orEmpty())
        } ?: -1
    }

    private fun samePlaceEvidence(left: String, right: String): Boolean {
        val a = normalizeText(left.substringBefore(','))
        val b = normalizeText(right.substringBefore(','))
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        return shorter.length >= 5 && longer.contains(shorter)
    }

    private fun normalizeText(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun retryOrFinish(reason: String) {
        if (pageReadAttempts < MAX_PAGE_READ_ATTEMPTS) {
            pageReadAttempts++
            busy = false
            webView.postDelayed({ handlePage() }, RETRY_MS)
        } else {
            UnifiedDebugEventStore.record(
                "MHTML_HARVEST_ABORTED",
                packageName,
                "account=${account.displayLabel} reason=$reason",
            )
            finishHarvest()
        }
    }

    private fun evaluateRaw(script: String, callback: (String?) -> Unit) {
        val expectedGeneration = ++evaluationGeneration
        var completed = false
        webView.postDelayed({
            if (completed || expectedGeneration != evaluationGeneration || isFinishing || isDestroyed) return@postDelayed
            completed = true
            UnifiedDebugEventStore.record(
                "HARVEST_EVALUATION_TIMEOUT",
                packageName,
                "account=${account.displayLabel} phase=${phase.name.lowercase()} timeoutMs=$EVALUATION_TIMEOUT_MS failClosed=true",
            )
            callback(null)
        }, EVALUATION_TIMEOUT_MS)
        webView.evaluateJavascript(script) { encoded ->
            if (completed || expectedGeneration != evaluationGeneration) return@evaluateJavascript
            completed = true
            callback(encoded)
        }
    }

    private inline fun <reified T> evaluate(script: String, crossinline callback: (T?) -> Unit) {
        evaluateRaw(script) { encoded ->
            val decoded = runCatching {
                if (encoded.isNullOrBlank() || encoded == "null") return@runCatching null
                val raw = json.parseToJsonElement(encoded).jsonPrimitive.content
                json.decodeFromString<T>(raw)
            }.getOrNull()
            callback(decoded)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!::webView.isInitialized || isFinishing || isDestroyed) {
            super.onBackPressed()
            return
        }
        evaluationGeneration++
        navigationGeneration++
        webView.stopLoading()
        busy = false
        pageReadAttempts = 0
        UnifiedDebugEventStore.record(
            "HARVEST_USER_BACK_RECOVERY",
            packageName,
            "account=${account.displayLabel} phase=${phase.name.lowercase()} cancel=false partialEvidencePreserved=true",
        )
        when (phase) {
            Phase.RIDES -> finishHarvest()
            Phase.TRIP -> {
                val target = tripTargets.getOrNull(tripIndex)
                UnifiedDebugEventStore.record(
                    "HARVEST_BLOCK_UNREADABLE",
                    packageName,
                    "account=${account.displayLabel} block=trip tripId=${target?.tripId.orEmpty()} reason=user_back_recovery",
                )
                tripIndex++
                loadNextTrip()
            }
            Phase.PASSENGER_CARD, Phase.PASSENGER -> {
                val target = passengerTargets.getOrNull(passengerIndex)
                UnifiedDebugEventStore.record(
                    "HARVEST_BLOCK_UNREADABLE",
                    packageName,
                    "account=${account.displayLabel} block=passenger tripId=${target?.tripId.orEmpty()} passengerKey=${target?.externalPassengerKey.orEmpty()} reason=user_back_recovery",
                )
                passengerIndex++
                resetPassengerReadState()
                loadNextPassenger()
            }
            Phase.EDIT -> {
                val target = editTargets.getOrNull(editIndex)
                UnifiedDebugEventStore.record(
                    "HARVEST_BLOCK_UNREADABLE",
                    packageName,
                    "account=${account.displayLabel} block=edit tripId=${target?.tripId.orEmpty()} reason=user_back_recovery",
                )
                editIndex++
                loadNextEdit()
            }
            Phase.OPTIONS -> {
                val target = optionTargets.getOrNull(optionIndex)
                UnifiedDebugEventStore.record(
                    "HARVEST_BLOCK_UNREADABLE",
                    packageName,
                    "account=${account.displayLabel} block=options tripId=${target?.tripId.orEmpty()} reason=user_back_recovery",
                )
                optionIndex++
                loadNextOption()
            }
        }
    }

    override fun onDestroy() {
        evaluationGeneration++
        navigationGeneration++
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    private enum class Phase { RIDES, TRIP, PASSENGER_CARD, PASSENGER, EDIT, OPTIONS }

    companion object {
        private const val RIDES_URL = "https://www.blablacar.com.br/rides"
        private const val MAX_TRIPS = 80
        private const val MAX_PAGE_READ_ATTEMPTS = 2
        private const val MAX_TRIP_ROSTER_READ_ATTEMPTS = 5
        private const val MAX_EDIT_LINK_READ_ATTEMPTS = 6
        private const val MAX_PASSENGER_CARD_READ_ATTEMPTS = 4
        private const val MAX_PASSENGER_PHONE_READ_ATTEMPTS = 5
        private const val MAX_PASSENGER_PAGE_RELOAD_ATTEMPTS = 1
        private const val RETRY_MS = 800L
        private const val EVALUATION_TIMEOUT_MS = 6_000L
        private const val PASSENGER_NAVIGATION_SETTLE_MS = 1_200L
        private const val PASSENGER_CALL_SETTLE_MS = 900L
        private const val CARD_TARGET_PREFIX = "rotacerta-card:"

        private val SANITIZED_HTML_JS = """
            const clone = document.documentElement.cloneNode(true);
            clone.querySelectorAll('script, style, noscript').forEach((node) => node.remove());
            clone.querySelectorAll('input, textarea').forEach((node) => {
              node.removeAttribute('value');
              node.textContent = '';
            });
            const html = clone.outerHTML || '';
        """.trimIndent()

        private val RIDE_LINKS_JS = """
            (function() {
              const hrefs = Array.from(document.querySelectorAll('a[href]'))
                .map((a) => a.href || '')
                .filter((href) => href && /blablacar\.com\.br/i.test(href))
                .filter((href) => /\/rides\/offer|\/trip(?:\/|\?)/i.test(href))
                .filter((href) => !/\/rides\/offer\/(?:passenger|edit)\//i.test(href));
              $SANITIZED_HTML_JS
              return JSON.stringify({
                tripHrefs: Array.from(new Set(hrefs)),
                pageUrl: location.href || '',
                domHtml: html.slice(0, 350000)
              });
            })();
        """.trimIndent()

        private val TRIP_EVIDENCE_JS = """
            (function() {
              const clean = (v) => (v || '').replace(/\s+/g, ' ').trim();
              const linesOf = (node) => ((node && node.innerText) || '').split(/\n+/).map(clean).filter(Boolean);
              const absolute = (href) => { try { return new URL(href || '', location.href).href; } catch (_) { return href || ''; } };
              const pageText = clean(document.body && document.body.innerText);
              const rows = [];
              const seen = new Set();
              const passengerTargets = [];
              const candidateNodes = Array.from(document.querySelectorAll(
                'a[href*="passenger"], a[href*="booking"], [data-testid*="passenger"], [data-testid*="booking"], [role="link"]'
              ));
              Array.from(document.querySelectorAll('a[href], [role="link"], button')).forEach((node) => {
                const text = clean(node.innerText);
                if ((text.includes('→') || text.includes('->')) && !candidateNodes.includes(node)) candidateNodes.push(node);
              });
              candidateNodes.forEach((node, index) => {
                const anchor = (node.matches && node.matches('a[href]')) ? node : (node.querySelector && node.querySelector('a[href]'));
                const href = absolute((anchor && anchor.getAttribute('href')) || (node.getAttribute && node.getAttribute('data-href')) || '');
                const container = (node.closest && node.closest('li, article, [role="listitem"], [data-testid*="passenger"], [data-testid*="booking"]')) || node;
                const lines = linesOf(container);
                const route = lines.find((line) => line.includes('→') || line.includes('->')) || '';
                if (!route) return;
                const explicit = container && container.querySelector
                  ? container.querySelector('[data-testid*="passenger-name"], [data-testid*="profile-name"], img[alt]')
                  : null;
                const alt = explicit && explicit.getAttribute ? clean(explicit.getAttribute('alt')) : '';
                let name = clean(alt || (explicit && explicit.innerText) || lines[0] || '');
                if (!name || /^(ver sua carona|editar sua carona|resumo da viagem|hor[aá]rio|santo|s[aã]o|pouso|extrema|camanducaia|tr[eê]s)/i.test(name)) return;
                const suffixSource = lines.find((line) => /\(\d+\)\s*$/.test(line)) || name;
                const suffix = suffixSource.match(/\((\d+)\)\s*$/);
                const seats = suffix ? Math.max(1, parseInt(suffix[1], 10) || 1) : 1;
                name = name.replace(/\s*\(\d+\)\s*$/, '').trim();
                const routeParts = route.split(/→|->/).map(clean);
                const key = href || [name.toLowerCase(), seats, route].join('|') || String(index);
                if (seen.has(key)) return;
                seen.add(key);
                const rowIndex = rows.length;
                passengerTargets.push(/passenger|booking/i.test(href) ? href : 'rotacerta-card:' + rowIndex);
                rows.push({
                  name: name,
                  seats: seats,
                  boarding: routeParts.length >= 2 ? routeParts[0] : null,
                  dropoff: routeParts.length >= 2 ? routeParts[routeParts.length - 1] : null,
                  phone: null,
                  booking_href: /passenger|booking/i.test(href) ? href : null
                });
              });
              const explicitPassengerHrefs = Array.from(document.querySelectorAll('a[href]'))
                .map((a) => absolute(a.getAttribute('href') || ''))
                .filter((href) => /\/passenger\/|\/booking\//i.test(href));
              explicitPassengerHrefs.forEach((href) => passengerTargets.push(href));
              const links = Array.from(document.querySelectorAll('a[href]'));
              const edit = links.find((a) => /\/rides\/offer\/edit\/[^/?#]+(?:$|[?#])/i.test(a.href || '') && !/\/options(?:$|[?#])/i.test(a.href || ''))
                || links.find((a) => /editar sua carona/i.test(clean(a.innerText)));
              const stopSelectors = [
                '[data-testid*="itinerary-departure-station"]',
                '[data-testid*="itinerary-arrival-station"]',
                '[data-testid*="itinerary-stop"]',
                '[data-testid*="station"]'
              ];
              const itineraryStops = [];
              stopSelectors.forEach((selector) => {
                Array.from(document.querySelectorAll(selector)).forEach((node) => {
                  const value = clean(node.innerText);
                  if (value && !itineraryStops.includes(value)) itineraryStops.push(value);
                });
              });
              const explicitEmptyRoster = /nenhum passageiro nesta carona/i.test(pageText);
              const terminalSeen = /ver sua carona publicada|editar sua carona/i.test(pageText);
              const rosterContainers = Array.from(document.querySelectorAll('[data-testid], [aria-label]')).filter((node) => {
                const marker = ((node.getAttribute('data-testid') || '') + ' ' + (node.getAttribute('aria-label') || '')).toLowerCase();
                return marker.includes('passenger') || marker.includes('passageir') || marker.includes('booking') || marker.includes('reserva');
              });
              const hasMore = Array.from(document.querySelectorAll('button, a, [role="button"]')).some(
                (node) => /mostrar mais|ver mais|mais passageir|mais reserva/i.test(clean(node.innerText))
              );
              const viewsMatch = pageText.match(/(\d{1,9})\s+visualiza(?:ç|c)[õo]es/i);
              const views = viewsMatch ? parseInt(viewsMatch[1], 10) : -1;
              const rosterComplete = explicitEmptyRoster
                || (rosterContainers.length > 0 && !hasMore)
                || (rows.length > 0 && terminalSeen && !hasMore);
              $SANITIZED_HTML_JS
              return JSON.stringify({
                passengers: rows,
                passengerHrefs: Array.from(new Set(passengerTargets)),
                itineraryStops: itineraryStops,
                rosterComplete: rosterComplete,
                explicitEmptyRoster: explicitEmptyRoster,
                views: Number.isFinite(views) ? views : -1,
                editHref: edit ? absolute(edit.getAttribute('href') || edit.href || '') : '',
                pageUrl: location.href || '',
                domHtml: html.slice(0, 350000)
              });
            })();
        """.trimIndent()

        private val PASSENGER_EVIDENCE_JS = """
            (function() {
              const clean = (v) => (v || '').replace(/\s+/g, ' ').trim();
              const body = clean(document.body && document.body.innerText);
              const nameNode = document.querySelector('[data-testid*="passenger-name"], [data-testid*="profile-name"], h1');
              const visibleName = clean(nameNode && nameNode.innerText);
              const actionNodes = Array.from(document.querySelectorAll('a[href], button, [role="button"], [role="link"]'));
              const callAction = actionNodes.find((node) => {
                const text = clean(node.innerText || node.textContent);
                const label = clean((node.getAttribute && (node.getAttribute('aria-label') || node.getAttribute('title'))) || '');
                const href = (node.getAttribute && node.getAttribute('href')) || '';
                return /^tel:/i.test(href) || /^(ligar|chamar|telefone|telefonar)$/i.test(text) || /\b(ligar|telefone|telefonar)\b/i.test(label);
              });
              const telCandidates = [];
              Array.from(document.querySelectorAll('[href^="tel:"], a, button, [role="button"]')).forEach((node) => {
                const href = (node.getAttribute && node.getAttribute('href')) || '';
                if (/^tel:/i.test(href)) telCandidates.push(href);
                (node.outerHTML || '').match(/tel:[+0-9(). \-]{8,32}/ig)?.forEach((value) => telCandidates.push(value));
              });
              const pageHtml = document.documentElement ? (document.documentElement.outerHTML || '') : '';
              (pageHtml.match(/tel:[+0-9(). \-]{8,32}/ig) || []).forEach((value) => telCandidates.push(value));
              const rawPhone = telCandidates.find((value) => /^tel:/i.test(value)) || '';
              const phone = rawPhone ? rawPhone.replace(/^tel:/i, '').split('?')[0].replace(/[^+0-9]/g, '') : '';
              const seatsMatch = body.match(/(\d{1,3})\s+lugar(?:es)?\b/i);
              const seats = seatsMatch ? Math.max(1, parseInt(seatsMatch[1], 10) || 1) : 1;
              const routeMatch = body.match(/([^\n|]{2,80})\s*(?:→|->)\s*([^\n|]{2,80})/);
              const priceMatch = body.match(/R\$\s*[0-9.,]+/i);
              $SANITIZED_HTML_JS
              return JSON.stringify({
                phone: phone,
                visibleName: visibleName,
                seats: seats,
                boarding: routeMatch ? clean(routeMatch[1]) : '',
                dropoff: routeMatch ? clean(routeMatch[2]) : '',
                price: priceMatch ? clean(priceMatch[0]) : '',
                callActionPresent: !!callAction,
                pageUrl: location.href || '',
                domHtml: html.slice(0, 350000)
              });
            })();
        """.trimIndent()

        private fun passengerCardOpenJs(cardIndex: Int): String = """
            (function() {
              const wantedIndex = $cardIndex;
              const clean = (v) => (v || '').replace(/\s+/g, ' ').trim();
              const linesOf = (node) => ((node && node.innerText) || '').split(/\n+/).map(clean).filter(Boolean);
              const candidates = Array.from(document.querySelectorAll(
                'a[href*="passenger"], a[href*="booking"], [data-testid*="passenger"], [data-testid*="booking"], [role="link"]'
              ));
              Array.from(document.querySelectorAll('a[href], [role="link"], button')).forEach((node) => {
                const value = clean(node.innerText);
                if ((value.includes('→') || value.includes('->')) && !candidates.includes(node)) candidates.push(node);
              });
              const valid = [];
              candidates.forEach((node) => {
                const container = (node.closest && node.closest('li, article, [role="listitem"], [data-testid*="passenger"], [data-testid*="booking"]')) || node;
                const lines = linesOf(container);
                const route = lines.find((line) => line.includes('→') || line.includes('->')) || '';
                if (!route) return;
                const explicit = container && container.querySelector
                  ? container.querySelector('[data-testid*="passenger-name"], [data-testid*="profile-name"], img[alt]')
                  : null;
                const alt = explicit && explicit.getAttribute ? clean(explicit.getAttribute('alt')) : '';
                const name = clean(alt || (explicit && explicit.innerText) || lines[0] || '');
                if (!name || /^(ver sua carona|editar sua carona|resumo da viagem|hor[aá]rio|santo|s[aã]o|pouso|extrema|camanducaia|tr[eê]s)/i.test(name)) return;
                valid.push({ node: node, container: container });
              });
              const item = valid[wantedIndex];
              if (!item) return JSON.stringify({ found: false, clicked: false });
              const selector = 'a[href], button, [role="link"], [role="button"]';
              const direct = item.node && item.node.matches && item.node.matches(selector) ? item.node : null;
              const nested = !direct && item.node && item.node.querySelector ? item.node.querySelector(selector) : null;
              const containerTarget = !direct && !nested && item.container && item.container.matches && item.container.matches(selector)
                ? item.container
                : (!direct && !nested && item.container && item.container.querySelector ? item.container.querySelector(selector) : null);
              const clickable = direct || nested || containerTarget || item.node;
              if (!clickable || typeof clickable.click !== 'function') return JSON.stringify({ found: true, clicked: false });
              clickable.click();
              return JSON.stringify({ found: true, clicked: true });
            })();
        """.trimIndent()

        private val CLICK_CALL_ACTION_JS = """
            (function() {
              const clean = (v) => (v || '').replace(/\s+/g, ' ').trim();
              const nodes = Array.from(document.querySelectorAll('a[href], button, [role="button"], [role="link"]'));
              const action = nodes.find((node) => {
                const text = clean(node.innerText || node.textContent);
                const label = clean((node.getAttribute && (node.getAttribute('aria-label') || node.getAttribute('title'))) || '');
                const href = (node.getAttribute && node.getAttribute('href')) || '';
                return /^tel:/i.test(href) || /^(ligar|chamar|telefone|telefonar)$/i.test(text) || /\b(ligar|telefone|telefonar)\b/i.test(label);
              });
              if (!action || typeof action.click !== 'function') return JSON.stringify({ present: !!action, clicked: false });
              action.click();
              return JSON.stringify({ present: true, clicked: true });
            })();
        """.trimIndent()

        private val EDIT_EVIDENCE_JS = """
            (function() {
              const clean = (v) => (v || '').replace(/\s+/g, ' ').trim();
              const links = Array.from(document.querySelectorAll('a[href]'));
              const option = links.find((a) => /\/edit\/[^/?#]+\/options/i.test(a.href || ''))
                || links.find((a) => /lugares e op[cç][õo]es|op[cç][õo]es de passageiros/i.test(clean(a.innerText)));
              $SANITIZED_HTML_JS
              return JSON.stringify({
                optionsHref: option ? (option.href || '') : '',
                pageUrl: location.href || '',
                domHtml: html.slice(0, 350000)
              });
            })();
        """.trimIndent()
    }
}

@Serializable
internal data class SeatOptionState(
    val seats: Int = -1,
    val canAdd: Boolean = false,
    val canRemove: Boolean = false,
    val savePresent: Boolean = false,
    val pageUrl: String = "",
    val domHtml: String = "",
)

class BlaBlaManualSeatSyncActivity : Activity() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var registry: BlaBlaDynamicAccountRegistry
    private lateinit var account: BlaBlaDynamicAccount
    private lateinit var requestStore: BlaBlaManualSeatSyncRequestStore
    private lateinit var request: BlaBlaManualSeatSyncRequest
    private lateinit var ledger: BlaBlaManualSeatSyncLedger
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private lateinit var archive: BlaBlaMhtmlArchiveStore
    private var phase = Phase.BEFORE
    private var busy = false
    private var expectedSeats = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = BlaBlaDynamicAccountRegistry(this)
        requestStore = BlaBlaManualSeatSyncRequestStore(this)
        ledger = BlaBlaManualSeatSyncLedger(this)
        account = registry.get(intent?.getStringExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID)) ?: run {
            finishPending("Conta BlaBlaCar não encontrada."); return
        }
        request = requestStore.peek() ?: run {
            finishPending("Nenhuma sincronização manual pendente."); return
        }
        if (!account.profileUuid.equals(request.profileUuid, ignoreCase = true)) {
            finishPending("UUID da conta não corresponde à publicação."); return
        }
        if (request.source !in setOf(BookingSource.PRIVATE.name, BookingSource.OTHER.name) || request.seatDelta == 0) {
            finishPending("A escrita externa é permitida somente para passageiro manual."); return
        }
        archive = BlaBlaMhtmlArchiveStore(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusView = TextView(this).apply {
            text = "${account.displayLabel} • sincronizando lugares da publicação…"
            setPadding(18, 18, 18, 18)
        }
        root.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        webView = WebView(this)
        configureProfiledWebView(webView, account)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!isBlaBla(url) || busy) return
                view.postDelayed({ handlePage() }, 650)
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        UnifiedDebugEventStore.record(
            "EXTERNAL_SEAT_SYNC_START",
            packageName,
            "manual=true source=${request.source} delta=${request.seatDelta} profileUuidPresent=true tripIdPresent=true request=${request.id}",
        )
        webView.loadUrl(optionsUrlForTrip(request.tripId))
    }

    private fun handlePage() {
        if (busy) return
        busy = true
        when (phase) {
            Phase.BEFORE -> archive.save(webView, account, "options-before", request.tripId) {
                evaluate<SeatOptionState>(SEAT_OPTIONS_READ_JS) { state ->
                    if (state == null || state.seats < 0 || !state.savePresent) {
                        finishPending("Não consegui ler a quantidade atual de lugares.")
                        return@evaluate
                    }
                    expectedSeats = state.seats + request.seatDelta
                    UnifiedDebugEventStore.record(
                        "EXTERNAL_SEAT_SYNC_READ",
                        packageName,
                        "request=${request.id} before=${state.seats} delta=${request.seatDelta} expected=$expectedSeats canAdd=${state.canAdd} canRemove=${state.canRemove}",
                    )
                    if (expectedSeats < 0 || (request.seatDelta > 0 && !state.canAdd) || (request.seatDelta < 0 && !state.canRemove)) {
                        finishPending("A interface externa não permite essa alteração de lugares.")
                        return@evaluate
                    }
                    statusView.text = "${account.displayLabel} • ${state.seats} → $expectedSeats lugares • salvando…"
                    phase = Phase.VERIFY
                    webView.evaluateJavascript(applySeatsJs(expectedSeats), null)
                    val waitMillis = 1_600L + kotlin.math.abs(request.seatDelta).coerceAtMost(20) * 320L
                    webView.postDelayed({
                        busy = false
                        webView.loadUrl(optionsUrlForTrip(request.tripId))
                    }, waitMillis)
                }
            }
            Phase.VERIFY -> archive.save(webView, account, "options-after", request.tripId) {
                evaluate<SeatOptionState>(SEAT_OPTIONS_READ_JS) { state ->
                    if (state?.seats == expectedSeats) {
                        if (request.seatDelta < 0) {
                            ledger.markVerifiedDecrease(request)
                        } else if (request.seatDelta > 0) {
                            ledger.clearAfterVerifiedReverse(request.localBookingId)
                        }
                        UnifiedDebugEventStore.record(
                            "EXTERNAL_SEAT_SYNC_VERIFIED",
                            packageName,
                            "request=${request.id} after=${state.seats} expected=$expectedSeats manual=true ledger=true",
                        )
                        requestStore.remove(request.id)
                        setResult(
                            RESULT_OK,
                            Intent()
                                .putExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID, account.id)
                                .putExtra("seat_sync_message", "Sincronizado externamente ✅ • $expectedSeats lugar(es) publicados"),
                        )
                        finish()
                    } else {
                        finishPending("Alteração externa não confirmada após releitura.")
                    }
                }
            }
        }
    }

    private fun finishPending(message: String) {
        if (::request.isInitialized) {
            UnifiedDebugEventStore.record(
                "EXTERNAL_SEAT_SYNC_PENDING",
                packageName,
                "request=${request.id} manual=true reason=${message.replace(' ', '_').take(90)}",
            )
            requestStore.remove(request.id)
        }
        setResult(RESULT_CANCELED, Intent().putExtra("seat_sync_message", "Sincronização externa pendente ⚠️ • $message"))
        finish()
    }

    private inline fun <reified T> evaluate(script: String, crossinline callback: (T?) -> Unit) {
        webView.evaluateJavascript(script) { encoded ->
            val decoded = runCatching {
                if (encoded.isNullOrBlank() || encoded == "null") return@runCatching null
                val raw = json.parseToJsonElement(encoded).jsonPrimitive.content
                json.decodeFromString<T>(raw)
            }.getOrNull()
            callback(decoded)
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    private enum class Phase { BEFORE, VERIFY }

    companion object {
        private val SEAT_OPTIONS_READ_JS = """
            (function() {
              const clean = (v) => (v || '').replace(/\s+/g, ' ').trim();
              const remove = document.querySelector('button[aria-label="Remover um lugar"]');
              const add = document.querySelector('button[aria-label="Adicionar um lugar"]');
              let root = remove && remove.parentElement;
              while (root && add && !root.contains(add)) root = root.parentElement;
              root = root || (add && add.parentElement) || document.body;
              const leaves = Array.from(root.querySelectorAll('span, p, div'))
                .filter((node) => node.children.length === 0)
                .map((node) => clean(node.innerText))
                .filter((text) => /^\d{1,3}$/.test(text));
              let seats = leaves.length ? parseInt(leaves[0], 10) : -1;
              if (seats < 0) {
                const all = clean(root.innerText).match(/(?:^|\s)(\d{1,3})(?:\s|$)/);
                seats = all ? parseInt(all[1], 10) : -1;
              }
              const save = Array.from(document.querySelectorAll('button')).find((button) => /^Salvar$/i.test(clean(button.innerText)));
              const clone = document.documentElement.cloneNode(true);
              clone.querySelectorAll('script, style, noscript').forEach((node) => node.remove());
              clone.querySelectorAll('input, textarea').forEach((node) => { node.removeAttribute('value'); node.textContent = ''; });
              const html = clone.outerHTML || '';
              return JSON.stringify({
                seats: Number.isFinite(seats) ? seats : -1,
                canAdd: !!add && !add.disabled,
                canRemove: !!remove && !remove.disabled,
                savePresent: !!save,
                domHtml: html.slice(0, 350000)
              });
            })();
        """.trimIndent()

        private fun applySeatsJs(target: Int): String = """
            (function() {
              const target = $target;
              const clean = (v) => (v || '').replace(/\s+/g, ' ').trim();
              const read = () => {
                const remove = document.querySelector('button[aria-label="Remover um lugar"]');
                const add = document.querySelector('button[aria-label="Adicionar um lugar"]');
                let root = remove && remove.parentElement;
                while (root && add && !root.contains(add)) root = root.parentElement;
                root = root || (add && add.parentElement) || document.body;
                const leaves = Array.from(root.querySelectorAll('span, p, div'))
                  .filter((node) => node.children.length === 0)
                  .map((node) => clean(node.innerText))
                  .filter((text) => /^\d{1,3}$/.test(text));
                return leaves.length ? parseInt(leaves[0], 10) : -1;
              };
              let attempts = 0;
              const step = () => {
                attempts++;
                const current = read();
                if (current === target) {
                  const save = Array.from(document.querySelectorAll('button')).find((button) => /^Salvar$/i.test(clean(button.innerText)));
                  if (save && !save.disabled) save.click();
                  return;
                }
                if (attempts > 30 || current < 0) return;
                const selector = current > target ? 'button[aria-label="Remover um lugar"]' : 'button[aria-label="Adicionar um lugar"]';
                const button = document.querySelector(selector);
                if (!button || button.disabled) return;
                button.click();
                setTimeout(step, 280);
              };
              step();
              return JSON.stringify({scheduled:true,target:target});
            })();
        """.trimIndent()
    }
}

private fun configureProfiledWebView(webView: WebView, account: BlaBlaDynamicAccount) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
        WebViewCompat.setProfile(webView, account.webProfileName)
        WebViewCompat.getProfile(webView).cookieManager.apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) setAcceptThirdPartyCookies(webView, true)
        }
    }
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.settings.allowFileAccess = false
    webView.settings.allowContentAccess = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }
}

private fun normalizeCapturedPhone(raw: String?): String? {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val hasPlus = value.startsWith("+")
    val digits = value.filter(Char::isDigit)
    if (digits.length !in 8..15) return null
    return if (hasPlus) "+$digits" else digits
}

private fun isBlaBla(url: String): Boolean = url.startsWith("https://www.blablacar.com.br/")

private fun absoluteBlaBlaHref(href: String): String {
    val value = href.trim()
    if (value.startsWith("https://www.blablacar.com.br/")) return value
    if (value.startsWith('/')) return "https://www.blablacar.com.br$value"
    return value
}

private fun canonicalHref(href: String): String = absoluteBlaBlaHref(href)
    .substringBefore("&search_uuid=")
    .substringBefore('#')

private fun isSpecificTripHref(href: String): Boolean =
    absoluteBlaBlaHref(href).contains("blablacar.com.br") && tripIdFromHref(href) != null

private fun isPassengerHref(href: String): Boolean {
    val value = absoluteBlaBlaHref(href)
    return value.startsWith("https://www.blablacar.com.br/") &&
        (value.contains("/passenger/") || value.contains("/booking/"))
}

private fun isOptionsHref(href: String): Boolean = Regex("/rides/offer/edit/[^/?#]+/options", RegexOption.IGNORE_CASE)
    .containsMatchIn(absoluteBlaBlaHref(href))

private fun editUrlForTrip(tripId: String): String =
    "https://www.blablacar.com.br/rides/offer/edit/${tripId.trim()}"

private fun optionsUrlForTrip(tripId: String): String =
    "https://www.blablacar.com.br/rides/offer/edit/${tripId.trim()}/options"

private fun tripIdFromHref(href: String): String? {
    queryId(href)?.let { return it }
    val path = runCatching { URI(absoluteBlaBlaHref(href)).path.orEmpty() }.getOrDefault("")
    Regex("/rides/offer/(?!edit(?:/|$)|passenger(?:/|$))([^/?#]+)", RegexOption.IGNORE_CASE)
        .find(path)?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)?.let { return it }
    Regex("/trip/([^/?#]+)", RegexOption.IGNORE_CASE)
        .find(path)?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)?.let { return it }
    return null
}

private fun tripIdFromEditHref(href: String): String? = Regex(
    "/rides/offer/edit/([^/?#]+)(?:$|[/?#])",
    RegexOption.IGNORE_CASE,
).find(runCatching { URI(absoluteBlaBlaHref(href)).path.orEmpty() }.getOrDefault(""))
    ?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)

private fun tripIdFromOptionsHref(href: String): String? = Regex(
    "/rides/offer/edit/([^/?#]+)/options",
    RegexOption.IGNORE_CASE,
).find(runCatching { URI(absoluteBlaBlaHref(href)).path.orEmpty() }.getOrDefault(""))
    ?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)

private fun passengerKey(href: String): String {
    val value = absoluteBlaBlaHref(href)
    val fromPath = Regex("/(?:passenger|booking)/([^/?#]+)", RegexOption.IGNORE_CASE)
        .find(value)?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)
    return (fromPath ?: queryId(value) ?: value.substringAfterLast('/').substringBefore('?'))
        .take(80)
        .ifBlank { "passenger" }
}

private fun queryId(href: String): String? = runCatching {
    URI(absoluteBlaBlaHref(href)).rawQuery.orEmpty().split('&')
        .mapNotNull { part -> part.substringBefore('=', "").takeIf { it == "id" }?.let { part.substringAfter('=', "") } }
        .firstOrNull()
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}.getOrNull()

private fun sanitizeHarvestUrl(url: String): String = url.substringBefore('#').take(240)

internal object BlaBlaHarvestAssociation {
    fun passengerEvidenceKey(accountId: String, tripId: String, passengerKey: String): String =
        "${accountId.trim()}|${tripId.trim()}|${passengerKey.trim()}"

    fun ridesPageMatches(url: String): Boolean = runCatching {
        val uri = URI(absoluteBlaBlaHref(url))
        uri.host.equals("www.blablacar.com.br", ignoreCase = true) && uri.path.trimEnd('/') == "/rides"
    }.getOrDefault(false)

    fun tripPageMatches(expectedTripId: String, url: String): Boolean =
        expectedTripId.isNotBlank() && tripIdFromHref(url) == expectedTripId

    fun editPageMatches(expectedTripId: String, url: String): Boolean =
        expectedTripId.isNotBlank() && tripIdFromEditHref(url) == expectedTripId && !isOptionsHref(url)

    fun optionsPageMatches(expectedTripId: String, url: String): Boolean =
        expectedTripId.isNotBlank() && tripIdFromOptionsHref(url) == expectedTripId

    fun passengerPageMatches(expectedPassengerKey: String, url: String): Boolean =
        expectedPassengerKey.isNotBlank() && isPassengerHref(url) && passengerKey(url) == expectedPassengerKey

    fun passengerCanonicalIdentityProven(expectedPassengerKey: String, expectedUrl: String, capturedUrl: String): Boolean =
        expectedUrl.isNotBlank() &&
            capturedUrl.isNotBlank() &&
            passengerPageMatches(expectedPassengerKey, expectedUrl) &&
            passengerPageMatches(expectedPassengerKey, capturedUrl) &&
            canonicalHref(expectedUrl) == canonicalHref(capturedUrl)

    fun passengerEvidenceAccepted(canonicalIdentityProven: Boolean): Boolean = canonicalIdentityProven
}

internal val SEAT_OPTIONS_READ_JS = """
    (function() {
      const clean = (v) => (v || '').replace(/\s+/g, ' ').trim();
      const marker = (node) => clean(
        ((node && node.getAttribute && node.getAttribute('data-testid')) || '') + ' ' +
        ((node && node.getAttribute && node.getAttribute('aria-label')) || '') + ' ' +
        ((node && node.getAttribute && node.getAttribute('title')) || '') + ' ' +
        ((node && node.innerText) || '')
      ).toLowerCase();
      const buttons = Array.from(document.querySelectorAll('button, [role="button"]'));
      let remove = buttons.find((node) => /decrement|decrease|remove|minus/.test(marker(node)) || /^[−–-]$/.test(clean(node.innerText)));
      let add = buttons.find((node) => /increment|increase|add|plus/.test(marker(node)) || /^\+$/.test(clean(node.innerText)));
      let root = (remove && remove.parentElement) || (add && add.parentElement) || null;
      while (root && root !== document.body && root.querySelectorAll('button, [role="button"]').length < 2) root = root.parentElement;
      const groupedButtons = root ? Array.from(root.querySelectorAll('button, [role="button"]')) : [];
      if (!remove && groupedButtons.length >= 2) remove = groupedButtons[0];
      if (!add && groupedButtons.length >= 2) add = groupedButtons[groupedButtons.length - 1];
      root = root || document.querySelector('[data-testid*="seat"], [data-testid*="capacity"], [role="spinbutton"]') || document.body;
      const numericControl = root.querySelector('input[type="number"], [role="spinbutton"], select');
      const controlledValue = numericControl && clean(
        numericControl.value || numericControl.getAttribute('aria-valuenow') || numericControl.getAttribute('value') || ''
      );
      const leaves = Array.from(root.querySelectorAll('span, p, div'))
        .filter((node) => node.children.length === 0)
        .map((node) => clean(node.innerText))
        .filter((text) => /^\d{1,3}$/.test(text));
      let seats = /^\d{1,3}$/.test(controlledValue || '') ? parseInt(controlledValue, 10) : (leaves.length ? parseInt(leaves[0], 10) : -1);
      if (seats < 0) {
        const all = clean(root.innerText).match(/(?:^|\s)(\d{1,3})(?:\s|$)/);
        seats = all ? parseInt(all[1], 10) : -1;
      }
      const save = document.querySelector('button[type="submit"], [data-testid*="save"], [data-testid*="submit"]');
      const clone = document.documentElement.cloneNode(true);
      clone.querySelectorAll('script, style, noscript').forEach((node) => node.remove());
      clone.querySelectorAll('input, textarea').forEach((node) => { node.removeAttribute('value'); node.textContent = ''; });
      const html = clone.outerHTML || '';
      return JSON.stringify({
        seats: Number.isFinite(seats) ? seats : -1,
        canAdd: !!add && !add.disabled,
        canRemove: !!remove && !remove.disabled,
        savePresent: !!save,
        pageUrl: location.href || '',
        domHtml: html.slice(0, 350000)
      });
    })();
""".trimIndent()
