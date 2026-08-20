package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/**
 * Canonical BlaBlaCar accounts currently linked to this physical driver.
 * Session plumbing is slot-based so more isolated account processes can be
 * added later without changing the Timeline/Agenda domain.
 */
data class BlaBlaAccountDefinition(
    val slot: String,
    val label: String,
    val uuid: String,
    val dataDirectorySuffix: String,
)

object BlaBlaAccounts {
    val EZEQUIEL = BlaBlaAccountDefinition(
        slot = "ezequiel",
        label = "Ezequiel S",
        uuid = "7371f028-9c55-4903-8444-308015823efd",
        dataDirectorySuffix = "blablacar_ezequiel",
    )
    val BARBOSA = BlaBlaAccountDefinition(
        slot = "barbosa",
        label = "Barbosa",
        uuid = "175a7068-50d8-40c3-a27a-214b9c6e0461",
        dataDirectorySuffix = "blablacar_barbosa",
    )
    val all = listOf(EZEQUIEL, BARBOSA)

    fun bySlot(slot: String?): BlaBlaAccountDefinition? = all.firstOrNull { it.slot == slot }
}

object BlaBlaSessionIntents {
    const val EXTRA_MODE = "mode"
    const val MODE_LOGIN = "login"
    const val MODE_SYNC = "sync"

    fun login(context: Context, account: BlaBlaAccountDefinition): Intent = intent(context, account, MODE_LOGIN)
    fun sync(context: Context, account: BlaBlaAccountDefinition): Intent = intent(context, account, MODE_SYNC)

    private fun intent(context: Context, account: BlaBlaAccountDefinition, mode: String): Intent {
        val activity = when (account.slot) {
            BlaBlaAccounts.EZEQUIEL.slot -> BlaBlaEzequielSessionActivity::class.java
            BlaBlaAccounts.BARBOSA.slot -> BlaBlaBarbosaSessionActivity::class.java
            else -> error("Conta BlaBlaCar sem processo isolado: ${account.slot}")
        }
        return Intent(context, activity).putExtra(EXTRA_MODE, mode)
    }
}

@Serializable
data class BlaBlaLocalSessionSnapshot(
    val accountUuid: String,
    val accountLabel: String,
    val identityVerified: Boolean = false,
    val lastUrl: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val trips: List<BlaBlaCollectorTrip> = emptyList(),
    val skippedTrips: Int = 0,
)

class BlaBlaLocalSessionStore(context: Context) {
    private val filesDir = context.applicationContext.filesDir
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(account: BlaBlaAccountDefinition): BlaBlaLocalSessionSnapshot? = runCatching {
        val file = file(account)
        if (!file.isFile) null else json.decodeFromString<BlaBlaLocalSessionSnapshot>(file.readText(Charsets.UTF_8))
    }.getOrNull()?.takeIf { it.accountUuid == account.uuid }

    fun markSeen(account: BlaBlaAccountDefinition, lastUrl: String) {
        val previous = read(account)
        write(
            account,
            (previous ?: BlaBlaLocalSessionSnapshot(account.uuid, account.label)).copy(
                lastUrl = lastUrl.take(1000),
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun confirmIdentity(account: BlaBlaAccountDefinition, lastUrl: String) {
        val previous = read(account)
        write(
            account,
            (previous ?: BlaBlaLocalSessionSnapshot(account.uuid, account.label)).copy(
                identityVerified = true,
                lastUrl = lastUrl.take(1000),
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun saveSync(
        account: BlaBlaAccountDefinition,
        lastUrl: String,
        trips: List<BlaBlaCollectorTrip>,
        skippedTrips: Int,
        identityVerified: Boolean,
    ) {
        write(
            account,
            BlaBlaLocalSessionSnapshot(
                accountUuid = account.uuid,
                accountLabel = account.label,
                identityVerified = identityVerified,
                lastUrl = lastUrl.take(1000),
                updatedAtMillis = System.currentTimeMillis(),
                trips = trips,
                skippedTrips = skippedTrips,
            ),
        )
    }

    fun combinedResponse(): BlaBlaCollectorMonthResponse {
        val snapshots = BlaBlaAccounts.all.mapNotNull { account -> read(account)?.let { account to it } }
        val verified = snapshots.filter { (_, snapshot) -> snapshot.identityVerified }
        val trips = verified.flatMap { (_, snapshot) -> snapshot.trips }
            .distinctBy { trip -> strongTripIdentity(trip) }
            .sortedWith(compareBy<BlaBlaCollectorTrip>({ it.date }, { it.departure_time.orEmpty() }))
        val validated = verified.size
        return BlaBlaCollectorMonthResponse(
            collected_at = Instant.now().toString(),
            status = when (validated) {
                BlaBlaAccounts.all.size -> "validated"
                0 -> "blocked"
                else -> "partial"
            },
            month = null,
            strategy = "authenticated_on_device_webview_isolated_sessions",
            profiles = verified.map { (account, _) ->
                BlaBlaCollectorProfile(
                    uuid = account.uuid,
                    name = account.label,
                    title = "Sessão autenticada local • UUID confirmado",
                )
            },
            trips = trips,
            coverage = BlaBlaCollectorCoverage(
                complete_for_scope = validated == BlaBlaAccounts.all.size,
                global_profile_month_complete = false,
                reason = "Leitura autenticada local da área Suas viagens; cada viagem só entra após UUID exato no detalhe.",
                requested_queries = BlaBlaAccounts.all.size,
                validated_queries = validated,
                failed_or_mismatched_queries = BlaBlaAccounts.all.size - validated,
                unresolved_target_cards = snapshots.sumOf { (_, snapshot) -> snapshot.skippedTrips },
                past_dates_skipped = false,
            ),
        )
    }

    private fun write(account: BlaBlaAccountDefinition, snapshot: BlaBlaLocalSessionSnapshot) {
        val target = file(account)
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(json.encodeToString(snapshot), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.writeText(temp.readText(Charsets.UTF_8), Charsets.UTF_8)
            temp.delete()
        }
    }

    private fun file(account: BlaBlaAccountDefinition): File = File(filesDir, "blablacar-session-${account.slot}.json")

    private fun strongTripIdentity(trip: BlaBlaCollectorTrip): String = listOf(
        trip.profile_uuid,
        trip.trip_id?.trim()?.takeIf(String::isNotEmpty)
            ?: trip.trip_href?.substringBefore("&search_uuid=")?.trim()?.takeIf(String::isNotEmpty)
            ?: "${trip.date}|${trip.departure_time}|${trip.actual_departure}|${trip.actual_arrival}",
    ).joinToString("|")
}

@Serializable
data class BlaBlaDomRideCandidate(
    val href: String = "",
    val text: String = "",
    val departureTime: String = "",
    val arrivalTime: String = "",
    val origin: String = "",
    val destination: String = "",
    val price: String = "",
    val dateText: String = "",
)

@Serializable
data class BlaBlaDomRideList(val candidates: List<BlaBlaDomRideCandidate> = emptyList())

@Serializable
data class BlaBlaDomTripDetail(
    val url: String = "",
    val bodyText: String = "",
    val dateText: String = "",
    val departureTime: String = "",
    val arrivalTime: String = "",
    val origin: String = "",
    val destination: String = "",
    val price: String = "",
    val driverName: String = "",
    val profileLinks: List<String> = emptyList(),
)

object BlaBlaDomNormalizer {
    private val uuidRegex = Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val timeRegex = Regex("(?<!\\d)([01]?\\d|2[0-3]):[0-5]\\d(?!\\d)")
    private val isoDateRegex = Regex("(?<!\\d)(20\\d{2})-(0?[1-9]|1[0-2])-([0-2]?\\d|3[01])(?!\\d)")
    private val numericDateRegex = Regex("(?<!\\d)([0-2]?\\d|3[01])[/.-](0?[1-9]|1[0-2])(?:[/.-](20\\d{2}|\\d{2}))?(?!\\d)")
    private val monthDateRegex = Regex("(?i)([0-2]?\\d|3[01])\\s*(?:de\\s+)?([a-zçãáâéêíóôõú]{3,12})(?:\\s*(?:de\\s+)?(20\\d{2}))?")
    private val months = mapOf(
        "jan" to 1, "janeiro" to 1,
        "fev" to 2, "fevereiro" to 2,
        "mar" to 3, "marco" to 3,
        "abr" to 4, "abril" to 4,
        "mai" to 5, "maio" to 5,
        "jun" to 6, "junho" to 6,
        "jul" to 7, "julho" to 7,
        "ago" to 8, "agosto" to 8,
        "set" to 9, "setembro" to 9,
        "out" to 10, "outubro" to 10,
        "nov" to 11, "novembro" to 11,
        "dez" to 12, "dezembro" to 12,
    )

    fun profileUuids(detail: BlaBlaDomTripDetail): Set<String> = detail.profileLinks
        .flatMap { link -> uuidRegex.findAll(link).map { it.value.lowercase() }.toList() }
        .toSet()

    fun isExpectedProfile(account: BlaBlaAccountDefinition, detail: BlaBlaDomTripDetail): Boolean =
        account.uuid.lowercase() in profileUuids(detail)

    fun toTrip(
        account: BlaBlaAccountDefinition,
        candidate: BlaBlaDomRideCandidate,
        detail: BlaBlaDomTripDetail,
        today: LocalDate = LocalDate.now(),
    ): BlaBlaCollectorTrip? {
        if (!isExpectedProfile(account, detail)) return null
        val date = parseDate(
            listOf(detail.dateText, candidate.dateText, candidate.text, detail.bodyText.take(6000)).joinToString(" | "),
            today,
        ) ?: return null
        val departureTime = normalizeTime(detail.departureTime.ifBlank { candidate.departureTime })
            ?: timeRegex.find(candidate.text)?.value?.let(::normalizeTime)
            ?: return null
        val timeValues = timeRegex.findAll(candidate.text).map { it.value }.toList()
        val arrivalTime = normalizeTime(detail.arrivalTime.ifBlank { candidate.arrivalTime })
            ?: timeValues.drop(1).firstOrNull()?.let(::normalizeTime)
        val origin = detail.origin.trim().ifBlank { candidate.origin.trim() }
        val destination = detail.destination.trim().ifBlank { candidate.destination.trim() }
        if (origin.isBlank() || destination.isBlank()) return null
        val allText = "${candidate.text} ${detail.bodyText}"
        val full = listOf("cheio", "esgotad", "sem vagas", "indisponível", "indisponivel").any { token ->
            normalize(allText).contains(token)
        }
        val href = detail.url.takeIf(String::isNotBlank) ?: candidate.href
        return BlaBlaCollectorTrip(
            profile_uuid = account.uuid,
            profile_name = account.label,
            date = date.toString(),
            departure_time = departureTime,
            arrival_time = arrivalTime,
            actual_departure = origin,
            actual_arrival = destination,
            price = detail.price.trim().ifBlank { candidate.price.trim() }.takeIf(String::isNotBlank),
            flags = if (full) listOf("Cheio") else emptyList(),
            availability = if (full) "full" else "unknown",
            trip_href = href.takeIf(String::isNotBlank),
            trip_id = tripId(href),
            uuid_validation = "verified_from_trip_detail_profile_link",
        )
    }

    fun parseDate(textRaw: String, today: LocalDate = LocalDate.now()): LocalDate? {
        val text = normalize(textRaw)
        if (Regex("\\bhoje\\b").containsMatchIn(text)) return today
        if (Regex("\\bamanha\\b").containsMatchIn(text)) return today.plusDays(1)
        isoDateRegex.find(text)?.let { match ->
            return runCatching {
                LocalDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
            }.getOrNull()
        }
        numericDateRegex.find(text)?.let { match ->
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val yearText = match.groupValues[3]
            val year = when (yearText.length) {
                2 -> 2000 + yearText.toInt()
                4 -> yearText.toInt()
                else -> today.year
            }
            return sensibleDate(year, month, day, today)
        }
        monthDateRegex.findAll(text).forEach { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@forEach
            val monthKey = normalize(match.groupValues[2]).takeWhile(Char::isLetter)
            val month = months[monthKey] ?: months.entries.firstOrNull { monthKey.startsWith(it.key) }?.value ?: return@forEach
            val year = match.groupValues[3].toIntOrNull() ?: today.year
            sensibleDate(year, month, day, today)?.let { return it }
        }
        return null
    }

    private fun sensibleDate(year: Int, month: Int, day: Int, today: LocalDate): LocalDate? = runCatching {
        var value = LocalDate.of(year, month, day)
        if (year == today.year && value.isBefore(today.minusMonths(3))) value = value.plusYears(1)
        value
    }.getOrNull()

    private fun normalizeTime(value: String): String? {
        val match = timeRegex.find(value.trim()) ?: return null
        val parts = match.value.split(':')
        return "%02d:%02d".format(parts[0].toInt(), parts[1].toInt())
    }

    private fun tripId(href: String): String? = Regex("[?&]id=([^&#]+)").find(href)?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
}

abstract class BlaBlaSessionActivity : Activity() {
    protected abstract val account: BlaBlaAccountDefinition
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private lateinit var store: BlaBlaLocalSessionStore
    private var mode = BlaBlaSessionIntents.MODE_LOGIN
    private var syncPhase = SyncPhase.IDLE
    private var candidates = emptyList<BlaBlaDomRideCandidate>()
    private val collected = mutableListOf<BlaBlaCollectorTrip>()
    private var candidateIndex = 0
    private var skipped = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BlaBlaLocalSessionStore(this)
        mode = intent?.getStringExtra(BlaBlaSessionIntents.EXTRA_MODE) ?: BlaBlaSessionIntents.MODE_LOGIN
        if (!prepareIsolatedWebViewDirectory()) {
            setContentView(TextView(this).apply {
                text = "Não foi possível abrir uma sessão WebView isolada com segurança neste aparelho."
                setPadding(32, 32, 32, 32)
            })
            return
        }
        createBrowserUi()
        if (mode == BlaBlaSessionIntents.MODE_SYNC) beginSync() else webView.loadUrl(HOME_URL)
    }

    private fun prepareIsolatedWebViewDirectory(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return synchronized(directoryLock) {
            if (directoryPrepared) return@synchronized true
            runCatching { WebView.setDataDirectorySuffix(account.dataDirectorySuffix) }
                .onSuccess { directoryPrepared = true }
                .isSuccess
        }
    }

    private fun createBrowserUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        statusView = TextView(this).apply {
            text = "${account.label} • sessão isolada local"
            setPadding(24, 18, 24, 18)
        }
        root.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun action(label: String, onClick: () -> Unit) {
            actions.addView(Button(this).apply {
                text = label
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        action("Perfil") { webView.loadUrl(PROFILE_URL) }
        action("Suas viagens") { webView.loadUrl(RIDES_URL) }
        action("Sincronizar") { beginSync() }
        action("Voltar") { finishWithSeenState() }
        root.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) setAcceptThirdPartyCookies(webView, true)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                statusView.text = "${account.label} • ${url.take(100)}"
                if (syncPhase == SyncPhase.RIDES && url.contains("blablacar.com.br/rides")) {
                    captureRideList()
                } else if (syncPhase == SyncPhase.DETAIL && url.contains("blablacar.com.br/trip")) {
                    captureTripDetail()
                } else if (syncPhase == SyncPhase.IDLE) {
                    probeIdentity()
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun beginSync() {
        collected.clear()
        candidates = emptyList()
        candidateIndex = 0
        skipped = 0
        syncPhase = SyncPhase.RIDES
        statusView.text = "${account.label} • abrindo Suas viagens…"
        webView.loadUrl(RIDES_URL)
    }

    private fun captureRideList() {
        webView.evaluateJavascript(RIDE_LIST_JS) { encoded ->
            val result = decodeJsResult<BlaBlaDomRideList>(encoded)
            if (result == null) {
                statusView.text = "Não consegui ler Suas viagens. Use a tela oficial e toque Sincronizar novamente."
                syncPhase = SyncPhase.IDLE
                return@evaluateJavascript
            }
            candidates = result.candidates
                .filter { it.href.contains("blablacar.com.br") && (it.href.contains("/trip?") || it.href.contains("/trip/")) }
                .distinctBy { it.href.substringBefore("&search_uuid=") }
                .take(MAX_TRIPS)
            if (candidates.isEmpty()) {
                val previous = store.read(account)
                if (previous?.identityVerified == true) {
                    store.saveSync(account, webView.url.orEmpty(), emptyList(), 0, identityVerified = true)
                    completeSync(0)
                } else {
                    statusView.text = "Nenhuma viagem encontrada ou UUID ainda não confirmado. Faça login, abra Perfil e confirme a conta."
                    syncPhase = SyncPhase.IDLE
                }
                return@evaluateJavascript
            }
            syncPhase = SyncPhase.DETAIL
            loadCurrentCandidate()
        }
    }

    private fun loadCurrentCandidate() {
        if (candidateIndex >= candidates.size) {
            val verified = collected.isNotEmpty()
            store.saveSync(account, webView.url.orEmpty(), collected.toList(), skipped, verified)
            if (verified) completeSync(collected.size) else {
                statusView.text = "Nenhuma viagem teve o UUID ${account.uuid} confirmado no detalhe. Verifique se esta é a conta ${account.label}."
                syncPhase = SyncPhase.IDLE
            }
            return
        }
        val candidate = candidates[candidateIndex]
        statusView.text = "${account.label} • validando viagem ${candidateIndex + 1}/${candidates.size} pelo UUID…"
        webView.loadUrl(candidate.href)
    }

    private fun captureTripDetail() {
        val candidate = candidates.getOrNull(candidateIndex) ?: return
        webView.evaluateJavascript(TRIP_DETAIL_JS) { encoded ->
            val detail = decodeJsResult<BlaBlaDomTripDetail>(encoded)
            val trip = detail?.let { BlaBlaDomNormalizer.toTrip(account, candidate, it) }
            if (trip != null) collected += trip else skipped++
            candidateIndex++
            loadCurrentCandidate()
        }
    }

    private fun probeIdentity() {
        webView.evaluateJavascript(PROFILE_EVIDENCE_JS) { encoded ->
            val links = decodeJsResult<List<String>>(encoded).orEmpty()
            val uuids = links.flatMap { UUID_REGEX.findAll(it).map { match -> match.value.lowercase() }.toList() }.toSet()
            when {
                account.uuid.lowercase() in uuids -> {
                    store.confirmIdentity(account, webView.url.orEmpty())
                    statusView.text = "${account.label} • UUID confirmado ✅"
                }
                uuids.isNotEmpty() -> statusView.text = "UUID diferente detectado. Entre na conta ${account.label}."
            }
        }
    }

    private inline fun <reified T> decodeJsResult(encoded: String?): T? = runCatching {
        if (encoded.isNullOrBlank() || encoded == "null") return@runCatching null
        val raw = json.parseToJsonElement(encoded).jsonPrimitive.content
        json.decodeFromString<T>(raw)
    }.getOrNull()

    private fun completeSync(count: Int) {
        syncPhase = SyncPhase.IDLE
        setResult(Activity.RESULT_OK, Intent().putExtra("account", account.slot).putExtra("trip_count", count))
        finish()
    }

    private fun finishWithSeenState() {
        store.markSeen(account, webView.url.orEmpty())
        setResult(Activity.RESULT_CANCELED, Intent().putExtra("account", account.slot))
        finish()
    }

    @Deprecated("Deprecated in Android framework; retained for WebView back navigation compatibility")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else finishWithSeenState()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    private enum class SyncPhase { IDLE, RIDES, DETAIL }

    companion object {
        @Volatile private var directoryPrepared = false
        private val directoryLock = Any()
        private const val HOME_URL = "https://www.blablacar.com.br/"
        private const val RIDES_URL = "https://www.blablacar.com.br/rides"
        private const val PROFILE_URL = "https://www.blablacar.com.br/dashboard/profile/menu"
        private const val MAX_TRIPS = 40
        private val UUID_REGEX = Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

        private val RIDE_LIST_JS = """
            (function() {
              const clean = (value) => (value || '').replace(/\\s+/g, ' ').trim();
              const value = (root, testid) => clean(root && root.querySelector('[data-testid="' + testid + '"]') && root.querySelector('[data-testid="' + testid + '"]').innerText);
              const dateText = (root) => clean(Array.from((root || document).querySelectorAll('[data-testid*="date"], time, h1, h2, h3')).map((node) => node.innerText).join(' | ')).slice(0, 600);
              const anchors = Array.from(document.querySelectorAll('a[href]')).filter((a) => /\/trip(\?|\/)/.test(a.href));
              const candidates = anchors.map((anchor) => {
                const root = anchor.closest('[data-testid="e2e-srp-card"], article, li') || anchor.parentElement || document.body;
                return {
                  href: anchor.href || '',
                  text: clean(root.innerText).slice(0, 1600),
                  departureTime: value(root, 'e2e-itinerary-departure-time'),
                  arrivalTime: value(root, 'e2e-itinerary-arrival-time'),
                  origin: value(root, 'e2e-itinerary-departure-station'),
                  destination: value(root, 'e2e-itinerary-arrival-station'),
                  price: value(root, 'e2e-tripcard-price') || value(root, 'e2e-tripcard-price-price-value'),
                  dateText: dateText(root)
                };
              });
              return JSON.stringify({ candidates: candidates });
            })();
        """.trimIndent()

        private val TRIP_DETAIL_JS = """
            (function() {
              const clean = (value) => (value || '').replace(/\\s+/g, ' ').trim();
              const first = (selectors) => {
                for (const selector of selectors) {
                  const node = document.querySelector(selector);
                  if (node && clean(node.innerText)) return clean(node.innerText);
                }
                return '';
              };
              const driverNode = document.querySelector('[data-testid="e2e-tripcard-driver-name"], [data-testid*="driver-name"], [data-testid*="driver"]');
              const scoped = [];
              if (driverNode) {
                const link = driverNode.closest('a[href]');
                if (link) scoped.push(link.href);
                const root = driverNode.closest('section, article, li, div');
                if (root) Array.from(root.querySelectorAll('a[href]')).forEach((a) => scoped.push(a.href));
              }
              Array.from(document.querySelectorAll('a[href]')).forEach((a) => {
                if (/(profile|user|member)/i.test(a.href)) scoped.push(a.href);
              });
              const uuid = /[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i;
              const profileLinks = Array.from(new Set(scoped.filter((href) => uuid.test(href))));
              const dateText = clean(Array.from(document.querySelectorAll('[data-testid*="date"], time, h1, h2, h3')).map((node) => node.innerText).join(' | ')).slice(0, 1200);
              return JSON.stringify({
                url: location.href,
                bodyText: clean(document.body && document.body.innerText).slice(0, 12000),
                dateText: dateText,
                departureTime: first(['[data-testid="e2e-itinerary-departure-time"]', '[data-testid*="departure-time"]']),
                arrivalTime: first(['[data-testid="e2e-itinerary-arrival-time"]', '[data-testid*="arrival-time"]']),
                origin: first(['[data-testid="e2e-itinerary-departure-station"]', '[data-testid*="departure-station"]']),
                destination: first(['[data-testid="e2e-itinerary-arrival-station"]', '[data-testid*="arrival-station"]']),
                price: first(['[data-testid="e2e-tripcard-price"]', '[data-testid="e2e-tripcard-price-price-value"]', '[data-testid*="price"]']),
                driverName: clean(driverNode && driverNode.innerText),
                profileLinks: profileLinks
              });
            })();
        """.trimIndent()

        private val PROFILE_EVIDENCE_JS = """
            (function() {
              const uuid = /[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i;
              const links = Array.from(document.querySelectorAll('a[href]'))
                .map((a) => a.href || '')
                .filter((href) => uuid.test(href) && /(profile|user|member)/i.test(href));
              if (uuid.test(location.href)) links.push(location.href);
              return JSON.stringify(Array.from(new Set(links)));
            })();
        """.trimIndent()
    }
}

class BlaBlaEzequielSessionActivity : BlaBlaSessionActivity() {
    override val account: BlaBlaAccountDefinition = BlaBlaAccounts.EZEQUIEL
}

class BlaBlaBarbosaSessionActivity : BlaBlaSessionActivity() {
    override val account: BlaBlaAccountDefinition = BlaBlaAccounts.BARBOSA
}
