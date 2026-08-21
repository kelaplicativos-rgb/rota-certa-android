package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
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

/**
 * Persisted evidence from the authenticated collector pages.  This is kept
 * separate from physical vehicle capacity: the number shown by BlaBlaCar in
 * "Opções de passageiros" is publication inventory evidence, not a hardcoded
 * global vehicle capacity.
 */
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
    val domHtml: String = "",
)

@Serializable
private data class MhtmlEditEvidence(
    val optionsHref: String = "",
    val domHtml: String = "",
)

private data class PassengerTarget(val tripId: String, val href: String)

/**
 * Mirrors every authenticated block observed in the physical BlaBlaCar flow:
 * /rides -> trip summary -> each passenger -> Editar sua carona -> Lugares e opções.
 * Both a full private MHTML and a sanitized per-block HTML snapshot are kept.
 * The harvested passenger evidence is reconciled back into the dynamic session
 * snapshot before the Timeline is rebuilt.
 */
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
    private var tripHrefs = emptyList<String>()
    private var tripIndex = 0
    private val tripEvidence = linkedMapOf<String, MhtmlTripEvidence>()
    private val passengerTargets = mutableListOf<PassengerTarget>()
    private var passengerIndex = 0
    private val passengerEvidence = mutableMapOf<String, MhtmlPassengerEvidence>()
    private var editUrls = emptyList<String>()
    private var editIndex = 0
    private val optionUrlByTrip = linkedMapOf<String, String>()
    private var optionUrls = emptyList<String>()
    private var optionIndex = 0
    private val publishedSeatsByTrip = mutableMapOf<String, Int>()
    private var pageReadAttempts = 0

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
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (!isBlaBla(url) || busy) return
                view.postDelayed({ handlePage() }, 900)
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        webView.loadUrl(RIDES_URL)
    }

    private fun handlePage() {
        if (busy) return
        busy = true
        when (phase) {
            Phase.RIDES -> captureRides()
            Phase.TRIP -> captureTrip()
            Phase.PASSENGER -> capturePassenger()
            Phase.EDIT -> captureEdit()
            Phase.OPTIONS -> captureOptions()
        }
    }

    private fun captureRides() {
        evaluate<MhtmlRideList>(RIDE_LINKS_JS) { result ->
            if (result == null) {
                retryOrFinish("rides_unreadable")
                return@evaluate
            }
            val links = result.tripHrefs
                .filter(::isSpecificTripHref)
                .distinctBy(::canonicalHref)
                .take(MAX_TRIPS)
            if (links.isEmpty() && pageReadAttempts < MAX_PAGE_READ_ATTEMPTS) {
                pageReadAttempts++
                busy = false
                webView.postDelayed({ handlePage() }, RETRY_MS)
                return@evaluate
            }
            pageReadAttempts = 0
            sessionStore.saveDiagnosticHtml(account, "rides", result.domHtml)
            archive.save(webView, account, "rides", "latest") { saved ->
                if (saved != null) archived++
                tripHrefs = links
                editUrls = tripHrefs.mapNotNull { href -> tripIdFromHref(href)?.let(::editUrlForTrip) }
                optionUrlByTrip.clear()
                UnifiedDebugEventStore.record(
                    "HARVEST_RIDES_CAPTURED",
                    packageName,
                    "account=${account.displayLabel} trips=${tripHrefs.size} html=true mhtml=${saved != null}",
                )
                tripIndex = 0
                phase = Phase.TRIP
                busy = false
                loadNextTrip()
            }
        }
    }

    private fun captureTrip() {
        val href = tripHrefs.getOrNull(tripIndex)
        val tripId = href?.let(::tripIdFromHref)
        if (href == null || tripId == null) {
            phase = Phase.PASSENGER
            busy = false
            loadNextPassenger()
            return
        }
        evaluate<MhtmlTripEvidence>(TRIP_EVIDENCE_JS) { result ->
            if (result == null) {
                retryCurrentOrAdvanceTrip(tripId, "trip_unreadable")
                return@evaluate
            }
            val waitingForRoster = result.passengers.isEmpty() && !result.explicitEmptyRoster
            if (waitingForRoster && pageReadAttempts < MAX_PAGE_READ_ATTEMPTS) {
                pageReadAttempts++
                busy = false
                webView.postDelayed({ handlePage() }, RETRY_MS)
                return@evaluate
            }
            pageReadAttempts = 0
            tripEvidence[tripId] = result
            sessionStore.saveDiagnosticHtml(account, "trip-$tripId", result.domHtml)
            val targets = buildList {
                result.passengerHrefs.forEach { add(it) }
                result.passengers.mapNotNull(BlaBlaCollectorPassenger::booking_href).forEach { add(it) }
            }
                .map(::absoluteBlaBlaHref)
                .filter(::isPassengerHref)
                .distinctBy(::canonicalHref)
                .map { PassengerTarget(tripId, it) }
            targets.forEach { target ->
                if (passengerTargets.none { it.tripId == target.tripId && canonicalHref(it.href) == canonicalHref(target.href) }) {
                    passengerTargets += target
                }
            }
            archive.save(webView, account, "trip", tripId) { saved ->
                if (saved != null) archived++
                UnifiedDebugEventStore.record(
                    "HARVEST_TRIP_CAPTURED",
                    packageName,
                    "account=${account.displayLabel} tripIdPresent=true index=${tripIndex + 1}/${tripHrefs.size} passengers=${result.passengers.size} passengerLinks=${targets.size} rosterComplete=${result.rosterComplete} empty=${result.explicitEmptyRoster} stops=${result.itineraryStops.size} views=${result.views.coerceAtLeast(0)}",
                )
                tripIndex++
                busy = false
                loadNextTrip()
            }
        }
    }

    private fun retryCurrentOrAdvanceTrip(tripId: String, reason: String) {
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
            "account=${account.displayLabel} block=trip tripId=$tripId reason=$reason",
        )
        tripIndex++
        busy = false
        loadNextTrip()
    }

    private fun capturePassenger() {
        val target = passengerTargets.getOrNull(passengerIndex)
        if (target == null) {
            phase = Phase.EDIT
            busy = false
            loadNextEdit()
            return
        }
        evaluate<MhtmlPassengerEvidence>(PASSENGER_EVIDENCE_JS) { result ->
            if (result == null || (result.phone.isBlank() && result.visibleName.isBlank())) {
                if (pageReadAttempts < MAX_PAGE_READ_ATTEMPTS) {
                    pageReadAttempts++
                    busy = false
                    webView.postDelayed({ handlePage() }, RETRY_MS)
                    return@evaluate
                }
            }
            pageReadAttempts = 0
            val evidence = result ?: MhtmlPassengerEvidence()
            passengerEvidence[canonicalHref(target.href)] = evidence
            sessionStore.saveDiagnosticHtml(
                account,
                "passenger-${target.tripId}-${passengerKey(target.href)}",
                evidence.domHtml,
            )
            archive.save(webView, account, "passenger", "${target.tripId}-${passengerKey(target.href)}") { saved ->
                if (saved != null) archived++
                UnifiedDebugEventStore.record(
                    "HARVEST_PASSENGER_CAPTURED",
                    packageName,
                    "account=${account.displayLabel} tripId=${target.tripId} index=${passengerIndex + 1}/${passengerTargets.size} namePresent=${evidence.visibleName.isNotBlank()} phonePresent=${normalizeCapturedPhone(evidence.phone) != null} seats=${evidence.seats.coerceAtLeast(1)} routePresent=${evidence.boarding.isNotBlank() && evidence.dropoff.isNotBlank()}",
                )
                passengerIndex++
                busy = false
                loadNextPassenger()
            }
        }
    }

    private fun captureEdit() {
        val href = editUrls.getOrNull(editIndex)
        val tripId = href?.let(::tripIdFromEditHref)
        if (href == null || tripId == null) {
            phase = Phase.OPTIONS
            optionUrls = optionUrlByTrip.values.distinct()
            optionIndex = 0
            busy = false
            loadNextOption()
            return
        }
        evaluate<MhtmlEditEvidence>(EDIT_EVIDENCE_JS) { result ->
            if ((result == null || result.optionsHref.isBlank()) && pageReadAttempts < MAX_EDIT_LINK_READ_ATTEMPTS) {
                pageReadAttempts++
                busy = false
                webView.postDelayed({ handlePage() }, RETRY_MS)
                return@evaluate
            }
            pageReadAttempts = 0
            val evidence = result ?: MhtmlEditEvidence()
            val options = evidence.optionsHref
                .takeIf(String::isNotBlank)
                ?.let(::absoluteBlaBlaHref)
                ?.takeIf(::isOptionsHref)
            if (options != null) {
                optionUrlByTrip[tripId] = options
            } else {
                UnifiedDebugEventStore.record(
                    "HARVEST_BLOCK_UNREADABLE",
                    packageName,
                    "account=${account.displayLabel} block=edit tripId=$tripId reason=options_link_missing",
                )
            }
            sessionStore.saveDiagnosticHtml(account, "edit-$tripId", evidence.domHtml)
            archive.save(webView, account, "edit", tripId) { saved ->
                if (saved != null) archived++
                UnifiedDebugEventStore.record(
                    "HARVEST_EDIT_CAPTURED",
                    packageName,
                    "account=${account.displayLabel} tripId=$tripId optionsLinkPresent=${options != null} html=true mhtml=${saved != null}",
                )
                editIndex++
                busy = false
                loadNextEdit()
            }
        }
    }

    private fun captureOptions() {
        val href = optionUrls.getOrNull(optionIndex)
        val tripId = href?.let(::tripIdFromOptionsHref)
        if (href == null || tripId == null) {
            finishHarvest()
            return
        }
        evaluate<SeatOptionState>(SEAT_OPTIONS_READ_JS) { result ->
            if ((result == null || result.seats < 0) && pageReadAttempts < MAX_PAGE_READ_ATTEMPTS) {
                pageReadAttempts++
                busy = false
                webView.postDelayed({ handlePage() }, RETRY_MS)
                return@evaluate
            }
            pageReadAttempts = 0
            val state = result ?: SeatOptionState()
            if (state.seats >= 0) publishedSeatsByTrip[tripId] = state.seats
            sessionStore.saveDiagnosticHtml(account, "options-$tripId", state.domHtml)
            archive.save(webView, account, "options", tripId) { saved ->
                if (saved != null) archived++
                UnifiedDebugEventStore.record(
                    "SEAT_OPTIONS_CAPTURED",
                    packageName,
                    "account=${account.displayLabel} tripId=$tripId publishedSeats=${state.seats} canAdd=${state.canAdd} canRemove=${state.canRemove} savePresent=${state.savePresent} html=true mhtml=${saved != null}",
                )
                optionIndex++
                busy = false
                loadNextOption()
            }
        }
    }

    private fun loadNextTrip() {
        val href = tripHrefs.getOrNull(tripIndex)
        if (href == null) {
            phase = Phase.PASSENGER
            passengerIndex = 0
            loadNextPassenger()
        } else {
            statusView.text = "${account.displayLabel} • resumo ${tripIndex + 1}/${tripHrefs.size}"
            webView.loadUrl(href)
        }
    }

    private fun loadNextPassenger() {
        val target = passengerTargets.getOrNull(passengerIndex)
        if (target == null) {
            phase = Phase.EDIT
            editIndex = 0
            loadNextEdit()
        } else {
            statusView.text = "${account.displayLabel} • passageiro ${passengerIndex + 1}/${passengerTargets.size}"
            webView.loadUrl(target.href)
        }
    }

    private fun loadNextEdit() {
        val href = editUrls.getOrNull(editIndex)
        if (href == null) {
            phase = Phase.OPTIONS
            optionUrls = optionUrlByTrip.values.distinct()
            optionIndex = 0
            loadNextOption()
        } else {
            statusView.text = "${account.displayLabel} • editar ${editIndex + 1}/${editUrls.size}"
            webView.loadUrl(href)
        }
    }

    private fun loadNextOption() {
        val href = optionUrls.getOrNull(optionIndex)
        if (href == null) {
            finishHarvest()
        } else {
            statusView.text = "${account.displayLabel} • lugares ${optionIndex + 1}/${optionUrls.size}"
            webView.loadUrl(href)
        }
    }

    private fun finishHarvest() {
        val enrichment = applyHarvestToSession()
        UnifiedDebugEventStore.record(
            "MHTML_HARVEST_COMPLETE",
            packageName,
            "account=${account.displayLabel} trips=${tripHrefs.size} passengerPages=${passengerTargets.size} editPages=${editUrls.size} optionPages=${optionUrls.size} archives=$archived enrichedTrips=${enrichment.first} enrichedPassengers=${enrichment.second}",
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
        val persistedEvidence = mutableListOf<BlaBlaHarvestTripEvidence>()
        var enrichedTrips = 0
        var enrichedPassengers = 0
        val updatedTrips = snapshot.trips.map { trip ->
            val tripId = trip.trip_id?.trim()?.takeIf(String::isNotEmpty)
                ?: trip.trip_href?.let(::tripIdFromHref)
                ?: return@map trip
            val summary = tripEvidence[tripId]
            val mergedPassengers = mergePassengers(trip.passengers, summary?.passengers.orEmpty())
                .map { passenger -> enrichPassenger(passenger) }
            val rosterComplete = summary?.rosterComplete == true || summary?.explicitEmptyRoster == true
            val stops = summary?.itineraryStops.orEmpty()
            val occupied = occupiedSeatsForTimeline(mergedPassengers, stops)
            val publishedSeats = publishedSeatsByTrip[tripId]
            val views = summary?.views?.takeIf { it >= 0 }
            persistedEvidence += BlaBlaHarvestTripEvidence(
                tripId = tripId,
                publishedSeats = publishedSeats,
                views = views,
                itineraryStops = stops,
                passengers = mergedPassengers,
                passengerRosterComplete = rosterComplete,
            )
            if (summary != null || publishedSeats != null || mergedPassengers != trip.passengers) enrichedTrips++
            enrichedPassengers += mergedPassengers.size
            UnifiedDebugEventStore.record(
                "HARVEST_TRIP_ENRICHED",
                packageName,
                "account=${account.displayLabel} tripId=$tripId passengers=${mergedPassengers.size} phones=${mergedPassengers.count { !it.phone.isNullOrBlank() }} totalPassengerSeats=${mergedPassengers.sumOf { it.seats.coerceAtLeast(1) }} bookedSeats=$occupied publishedSeats=${publishedSeats ?: -1} rosterComplete=$rosterComplete stops=${stops.size} views=${views ?: -1}",
            )
            trip.copy(
                passengers = mergedPassengers,
                booked_seats = occupied,
                passenger_roster_complete = rosterComplete,
            )
        }
        harvestStore.replace(account.id, persistedEvidence)
        sessionStore.saveSync(
            account = account,
            lastUrl = snapshot.lastUrl,
            trips = updatedTrips,
            skippedTrips = snapshot.skippedTrips,
            identityVerified = snapshot.identityVerified,
        )
        return enrichedTrips to enrichedPassengers
    }

    private fun enrichPassenger(passenger: BlaBlaCollectorPassenger): BlaBlaCollectorPassenger {
        val href = passenger.booking_href?.trim()?.takeIf(String::isNotEmpty) ?: return passenger
        val evidence = passengerEvidence[canonicalHref(absoluteBlaBlaHref(href))] ?: return passenger
        val phone = passenger.phone?.takeIf(String::isNotBlank) ?: normalizeCapturedPhone(evidence.phone)
        return passenger.copy(
            name = passenger.name.ifBlank { evidence.visibleName.trim() },
            seats = maxOf(passenger.seats.coerceAtLeast(1), evidence.seats.coerceAtLeast(1)),
            boarding = passenger.boarding?.takeIf(String::isNotBlank) ?: evidence.boarding.takeIf(String::isNotBlank),
            dropoff = passenger.dropoff?.takeIf(String::isNotBlank) ?: evidence.dropoff.takeIf(String::isNotBlank),
            phone = phone,
            booking_href = absoluteBlaBlaHref(href),
        )
    }

    private fun mergePassengers(
        current: List<BlaBlaCollectorPassenger>,
        harvested: List<BlaBlaCollectorPassenger>,
    ): List<BlaBlaCollectorPassenger> {
        val merged = current.toMutableList()
        harvested.forEach { incoming ->
            val index = merged.indexOfFirst { existing -> passengerMatches(existing, incoming) }
            if (index < 0) {
                merged += incoming
            } else {
                val existing = merged[index]
                merged[index] = existing.copy(
                    name = incoming.name.ifBlank { existing.name },
                    seats = maxOf(existing.seats.coerceAtLeast(1), incoming.seats.coerceAtLeast(1)),
                    boarding = incoming.boarding?.takeIf(String::isNotBlank) ?: existing.boarding,
                    dropoff = incoming.dropoff?.takeIf(String::isNotBlank) ?: existing.dropoff,
                    phone = incoming.phone?.takeIf(String::isNotBlank) ?: existing.phone,
                    booking_href = incoming.booking_href?.takeIf(String::isNotBlank) ?: existing.booking_href,
                )
            }
        }
        return merged.filter { it.name.isNotBlank() || !it.booking_href.isNullOrBlank() }
    }

    private fun passengerMatches(left: BlaBlaCollectorPassenger, right: BlaBlaCollectorPassenger): Boolean {
        val leftHref = left.booking_href?.let(::absoluteBlaBlaHref)?.let(::canonicalHref).orEmpty()
        val rightHref = right.booking_href?.let(::absoluteBlaBlaHref)?.let(::canonicalHref).orEmpty()
        if (leftHref.isNotBlank() && rightHref.isNotBlank()) return leftHref == rightHref
        val leftPhone = normalizeCapturedPhone(left.phone).orEmpty()
        val rightPhone = normalizeCapturedPhone(right.phone).orEmpty()
        if (leftPhone.isNotBlank() && rightPhone.isNotBlank()) return leftPhone == rightPhone
        return normalizeText(left.name) == normalizeText(right.name) &&
            left.seats.coerceAtLeast(1) == right.seats.coerceAtLeast(1) &&
            normalizeText(left.boarding.orEmpty()) == normalizeText(right.boarding.orEmpty()) &&
            normalizeText(left.dropoff.orEmpty()) == normalizeText(right.dropoff.orEmpty())
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

    private enum class Phase { RIDES, TRIP, PASSENGER, EDIT, OPTIONS }

    companion object {
        private const val RIDES_URL = "https://www.blablacar.com.br/rides"
        private const val MAX_TRIPS = 80
        private const val MAX_PAGE_READ_ATTEMPTS = 2
        private const val MAX_EDIT_LINK_READ_ATTEMPTS = 6
        private const val RETRY_MS = 800L

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
                const tel = container && container.querySelector ? container.querySelector('a[href^="tel:"]') : null;
                const key = href || [name.toLowerCase(), seats, route].join('|') || String(index);
                if (seen.has(key)) return;
                seen.add(key);
                rows.push({
                  name: name,
                  seats: seats,
                  boarding: routeParts.length >= 2 ? routeParts[0] : null,
                  dropoff: routeParts.length >= 2 ? routeParts[routeParts.length - 1] : null,
                  phone: tel ? (tel.getAttribute('href') || '').replace(/^tel:/i, '') : null,
                  booking_href: /passenger|booking/i.test(href) ? href : null
                });
              });
              const passengerHrefs = Array.from(document.querySelectorAll('a[href]'))
                .map((a) => absolute(a.getAttribute('href') || ''))
                .filter((href) => /\/passenger\/|\/booking\//i.test(href));
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
              const hasMore = /mostrar mais|ver mais|mais passageir|mais reserva/i.test(pageText);
              const viewsMatch = pageText.match(/(\d{1,9})\s+visualiza(?:ç|c)[õo]es/i);
              const views = viewsMatch ? parseInt(viewsMatch[1], 10) : -1;
              const rosterComplete = explicitEmptyRoster || (rows.length > 0 && terminalSeen && !hasMore);
              $SANITIZED_HTML_JS
              return JSON.stringify({
                passengers: rows,
                passengerHrefs: Array.from(new Set(passengerHrefs)),
                itineraryStops: itineraryStops,
                rosterComplete: rosterComplete,
                explicitEmptyRoster: explicitEmptyRoster,
                views: Number.isFinite(views) ? views : -1,
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
                domHtml: html.slice(0, 350000)
              });
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
                domHtml: html.slice(0, 350000)
              });
            })();
        """.trimIndent()
    }
}

@Serializable
private data class SeatOptionState(
    val seats: Int = -1,
    val canAdd: Boolean = false,
    val canRemove: Boolean = false,
    val savePresent: Boolean = false,
    val domHtml: String = "",
)

/**
 * Experimental authenticated UI mirror for a MANUAL/PRIVATE passenger only.
 * This is not an official BlaBlaCar API. Internal Rota Certa capacity remains
 * authoritative even when this external mirror cannot be completed.
 */
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
