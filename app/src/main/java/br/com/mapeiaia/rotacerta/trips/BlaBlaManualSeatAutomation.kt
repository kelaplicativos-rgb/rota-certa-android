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
        runCatching {
            webView.saveWebArchive(target.absolutePath, false) { saved ->
                if (!saved.isNullOrBlank()) {
                    UnifiedDebugEventStore.record(
                        "MHTML_ARCHIVE_SAVED",
                        context.packageName,
                        "account=${account.displayLabel} kind=${safe(kind)} file=${target.name}",
                    )
                }
                onDone(saved)
            }
        }.onFailure {
            UnifiedDebugEventStore.record(
                "MHTML_ARCHIVE_FAILED",
                context.packageName,
                "account=${account.displayLabel} kind=${safe(kind)} reason=${it.javaClass.simpleName}",
            )
            onDone(null)
        }
    }

    private fun safe(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-')
        .take(90)
        .ifBlank { "page" }
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
private data class MhtmlRideList(val tripHrefs: List<String> = emptyList())

@Serializable
private data class MhtmlPassengerLinks(val hrefs: List<String> = emptyList())

/**
 * Mirrors the authenticated pages that are actually needed by the collector:
 * /rides, each trip detail, individual passenger pages, and seat options.
 * It does not become a second source of truth; app/src collector snapshots remain authoritative.
 */
class BlaBlaMhtmlHarvestActivity : Activity() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var registry: BlaBlaDynamicAccountRegistry
    private lateinit var account: BlaBlaDynamicAccount
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private lateinit var archive: BlaBlaMhtmlArchiveStore
    private var phase = Phase.RIDES
    private var busy = false
    private var archived = 0
    private var tripHrefs = emptyList<String>()
    private var tripIndex = 0
    private val passengerHrefs = linkedSetOf<String>()
    private var passengerIndex = 0
    private var optionUrls = emptyList<String>()
    private var optionIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = BlaBlaDynamicAccountRegistry(this)
        account = registry.get(intent?.getStringExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID)) ?: run {
            finish(); return
        }
        archive = BlaBlaMhtmlArchiveStore(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusView = TextView(this).apply {
            text = "${account.displayLabel} • preparando MHTMLs necessários…"
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
        webView.loadUrl(RIDES_URL)
    }

    private fun handlePage() {
        if (busy) return
        busy = true
        when (phase) {
            Phase.RIDES -> archive.save(webView, account, "rides", "latest") { saved ->
                if (saved != null) archived++
                evaluate<MhtmlRideList>(RIDE_LINKS_JS) { list ->
                    tripHrefs = list?.tripHrefs.orEmpty()
                        .filter(::isSpecificTripHref)
                        .distinct()
                        .take(MAX_TRIPS)
                    optionUrls = tripHrefs.mapNotNull(::optionsUrl).distinct()
                    tripIndex = 0
                    phase = Phase.TRIP
                    busy = false
                    loadNextTrip()
                }
            }
            Phase.TRIP -> {
                val href = tripHrefs.getOrNull(tripIndex)
                if (href == null) {
                    phase = Phase.PASSENGER
                    busy = false
                    loadNextPassenger()
                    return
                }
                archive.save(webView, account, "trip", tripKey(href)) { saved ->
                    if (saved != null) archived++
                    evaluate<MhtmlPassengerLinks>(PASSENGER_LINKS_JS) { result ->
                        result?.hrefs.orEmpty().filter(::isPassengerHref).forEach(passengerHrefs::add)
                        tripIndex++
                        busy = false
                        loadNextTrip()
                    }
                }
            }
            Phase.PASSENGER -> {
                val href = passengerHrefs.elementAtOrNull(passengerIndex)
                if (href == null) {
                    phase = Phase.OPTIONS
                    busy = false
                    loadNextOption()
                    return
                }
                archive.save(webView, account, "passenger", passengerKey(href)) { saved ->
                    if (saved != null) archived++
                    passengerIndex++
                    busy = false
                    loadNextPassenger()
                }
            }
            Phase.OPTIONS -> {
                val href = optionUrls.getOrNull(optionIndex)
                if (href == null) {
                    finishHarvest()
                    return
                }
                archive.save(webView, account, "options", tripKey(href)) { saved ->
                    if (saved != null) archived++
                    optionIndex++
                    busy = false
                    loadNextOption()
                }
            }
        }
    }

    private fun loadNextTrip() {
        val href = tripHrefs.getOrNull(tripIndex)
        if (href == null) {
            phase = Phase.PASSENGER
            loadNextPassenger()
        } else {
            statusView.text = "${account.displayLabel} • MHTML viagem ${tripIndex + 1}/${tripHrefs.size}"
            webView.loadUrl(href)
        }
    }

    private fun loadNextPassenger() {
        val links = passengerHrefs.toList()
        val href = links.getOrNull(passengerIndex)
        if (href == null) {
            phase = Phase.OPTIONS
            loadNextOption()
        } else {
            statusView.text = "${account.displayLabel} • MHTML passageiro ${passengerIndex + 1}/${links.size}"
            webView.loadUrl(href)
        }
    }

    private fun loadNextOption() {
        val href = optionUrls.getOrNull(optionIndex)
        if (href == null) {
            finishHarvest()
        } else {
            statusView.text = "${account.displayLabel} • MHTML lugares ${optionIndex + 1}/${optionUrls.size}"
            webView.loadUrl(href)
        }
    }

    private fun finishHarvest() {
        UnifiedDebugEventStore.record(
            "MHTML_HARVEST_COMPLETE",
            packageName,
            "account=${account.displayLabel} trips=${tripHrefs.size} passengerPages=${passengerHrefs.size} optionPages=${optionUrls.size} archives=$archived",
        )
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(BlaBlaManualSeatAutomationIntents.EXTRA_ACCOUNT_ID, account.id)
                .putExtra("archive_count", archived),
        )
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

    private enum class Phase { RIDES, TRIP, PASSENGER, OPTIONS }

    companion object {
        private const val RIDES_URL = "https://www.blablacar.com.br/rides"
        private const val MAX_TRIPS = 80
        private val RIDE_LINKS_JS = """
            (function() {
              const hrefs = Array.from(document.querySelectorAll('article[data-testid^="e2e-your-rides-trip-card-"] a[href], [data-testid^="e2e-your-rides-trip-card-"] a[href]'))
                .map((a) => a.href || '')
                .filter((href) => /\/rides\/offer\?[^#]*\bid=/i.test(href));
              return JSON.stringify({ tripHrefs: Array.from(new Set(hrefs)) });
            })();
        """.trimIndent()
        private val PASSENGER_LINKS_JS = """
            (function() {
              const hrefs = Array.from(document.querySelectorAll('a[href*="/rides/offer/passenger/"]'))
                .map((a) => a.href || '')
                .filter(Boolean);
              return JSON.stringify({ hrefs: Array.from(new Set(hrefs)) });
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
                        UnifiedDebugEventStore.record(
                            "EXTERNAL_SEAT_SYNC_VERIFIED",
                            packageName,
                            "request=${request.id} after=${state.seats} expected=$expectedSeats manual=true",
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
              return JSON.stringify({
                seats: Number.isFinite(seats) ? seats : -1,
                canAdd: !!add && !add.disabled,
                canRemove: !!remove && !remove.disabled,
                savePresent: !!save
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

private fun isBlaBla(url: String): Boolean = url.startsWith("https://www.blablacar.com.br/")

private fun isSpecificTripHref(href: String): Boolean =
    href.startsWith("https://www.blablacar.com.br/rides/offer") && queryId(href) != null

private fun isPassengerHref(href: String): Boolean =
    href.startsWith("https://www.blablacar.com.br/rides/offer/passenger/") && queryId(href) != null

private fun optionsUrl(href: String): String? = queryId(href)?.let(::optionsUrlForTrip)

private fun optionsUrlForTrip(tripId: String): String =
    "https://www.blablacar.com.br/rides/offer/edit/${tripId.trim()}/options"

private fun tripKey(href: String): String = queryId(href) ?: href.substringAfterLast('/').substringBefore('?').take(80)

private fun passengerKey(href: String): String = href.substringAfter("/rides/offer/passenger/")
    .substringBefore('/')
    .substringBefore('?')
    .take(80)

private fun queryId(href: String): String? = runCatching {
    URI(href).rawQuery.orEmpty().split('&')
        .mapNotNull { part -> part.substringBefore('=', "").takeIf { it == "id" }?.let { part.substringAfter('=', "") } }
        .firstOrNull()
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}.getOrNull()
