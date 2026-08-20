package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/**
 * Dynamic BlaBlaCar account registry. It starts EMPTY: no driver name or UUID is
 * seeded in the APK. Every account is created by the user and receives its own
 * AndroidX WebView profile, so cookies/WebStorage stay isolated without a fixed
 * process-per-account limit.
 */
@Serializable
data class BlaBlaDynamicAccount(
    val id: String,
    val label: String,
    val webProfileName: String,
    val profileUuid: String? = null,
    val profileName: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
) {
    val displayLabel: String
        get() = profileName?.trim()?.takeIf(String::isNotEmpty) ?: label

    fun verifiedDefinition(): BlaBlaAccountDefinition? = profileUuid?.trim()?.takeIf(String::isNotEmpty)?.let { uuid ->
        BlaBlaAccountDefinition(
            slot = id,
            label = displayLabel,
            uuid = uuid,
            dataDirectorySuffix = webProfileName,
        )
    }
}

class BlaBlaDynamicAccountRegistry(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun list(): List<BlaBlaDynamicAccount> = runCatching {
        json.decodeFromString<List<BlaBlaDynamicAccount>>(prefs.getString(KEY_ACCOUNTS, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    fun get(id: String?): BlaBlaDynamicAccount? = id?.let { wanted -> list().firstOrNull { it.id == wanted } }

    fun add(labelRaw: String = ""): BlaBlaDynamicAccount {
        val existing = list()
        val id = UUID.randomUUID().toString()
        val label = labelRaw.trim().ifBlank { "Conta BlaBlaCar ${existing.size + 1}" }
        val account = BlaBlaDynamicAccount(
            id = id,
            label = label,
            webProfileName = "rota_certa_blablacar_${id.replace("-", "")}",
        )
        save(existing + account)
        return account
    }

    fun bindIdentity(id: String, uuid: String, name: String?): BlaBlaDynamicAccount? {
        val normalizedUuid = uuid.trim().lowercase()
        val current = list()
        val account = current.firstOrNull { it.id == id } ?: return null
        val updated = account.copy(
            profileUuid = normalizedUuid,
            profileName = name?.trim()?.takeIf(String::isNotEmpty) ?: account.profileName,
        )
        save(current.map { if (it.id == id) updated else it })
        return updated
    }

    fun remove(id: String) {
        val current = list()
        val account = current.firstOrNull { it.id == id } ?: return
        save(current.filterNot { it.id == id })
        BlaBlaDynamicSessionStore(prefsContext()).delete(account)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { ProfileStore.getInstance().deleteProfile(account.webProfileName) }
        }
    }

    private fun save(accounts: List<BlaBlaDynamicAccount>) {
        prefs.edit().putString(KEY_ACCOUNTS, json.encodeToString(accounts)).apply()
    }

    private fun prefsContext(): Context = AppContextHolder.requireContext(prefs)

    companion object {
        private const val PREFS = "rota_certa_blablacar_dynamic_accounts_v2"
        private const val KEY_ACCOUNTS = "accounts"
    }
}

/** Small holder used only to recover the application Context from the registry path. */
private object AppContextHolder {
    private var context: Context? = null

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    fun requireContext(@Suppress("UNUSED_PARAMETER") ignored: Any): Context =
        context ?: error("BlaBlaCar account registry context not initialized")
}

@Serializable
data class BlaBlaDynamicSessionSnapshot(
    val accountId: String,
    val profileUuid: String? = null,
    val profileLabel: String = "",
    val identityVerified: Boolean = false,
    val lastUrl: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val trips: List<BlaBlaCollectorTrip> = emptyList(),
    val skippedTrips: Int = 0,
)

class BlaBlaDynamicSessionStore(context: Context) {
    private val appContext = context.applicationContext.also(AppContextHolder::initialize)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(account: BlaBlaDynamicAccount): BlaBlaDynamicSessionSnapshot? = runCatching {
        val target = file(account.id)
        if (!target.isFile) null else json.decodeFromString<BlaBlaDynamicSessionSnapshot>(target.readText(Charsets.UTF_8))
    }.getOrNull()?.takeIf { it.accountId == account.id }

    fun markSeen(account: BlaBlaDynamicAccount, lastUrl: String) {
        val previous = read(account)
        write(
            account,
            (previous ?: BlaBlaDynamicSessionSnapshot(account.id, account.profileUuid, account.displayLabel)).copy(
                profileUuid = account.profileUuid,
                profileLabel = account.displayLabel,
                lastUrl = lastUrl.take(1000),
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun saveSync(
        account: BlaBlaDynamicAccount,
        lastUrl: String,
        trips: List<BlaBlaCollectorTrip>,
        skippedTrips: Int,
        identityVerified: Boolean,
    ) {
        write(
            account,
            BlaBlaDynamicSessionSnapshot(
                accountId = account.id,
                profileUuid = account.profileUuid,
                profileLabel = account.displayLabel,
                identityVerified = identityVerified,
                lastUrl = lastUrl.take(1000),
                updatedAtMillis = System.currentTimeMillis(),
                trips = trips,
                skippedTrips = skippedTrips,
            ),
        )
    }

    fun combinedResponse(accounts: List<BlaBlaDynamicAccount>): BlaBlaCollectorMonthResponse {
        val snapshots = accounts.mapNotNull { account -> read(account)?.let { account to it } }
        val verified = snapshots.filter { (account, snapshot) ->
            snapshot.identityVerified && !account.profileUuid.isNullOrBlank() && snapshot.profileUuid == account.profileUuid
        }
        val trips = verified.flatMap { (_, snapshot) -> snapshot.trips }
            .distinctBy(::strongTripIdentity)
            .sortedWith(compareBy<BlaBlaCollectorTrip>({ it.date }, { it.departure_time.orEmpty() }))
        return BlaBlaCollectorMonthResponse(
            collected_at = Instant.now().toString(),
            status = when {
                accounts.isEmpty() -> "empty"
                verified.size == accounts.size -> "validated"
                verified.isEmpty() -> "blocked"
                else -> "partial"
            },
            month = null,
            strategy = "authenticated_on_device_webview_dynamic_multi_profile",
            profiles = verified.map { (account, _) ->
                BlaBlaCollectorProfile(
                    uuid = account.profileUuid.orEmpty(),
                    name = account.displayLabel,
                    title = "Sessão autenticada local • perfil WebView isolado • UUID confirmado",
                )
            },
            trips = trips,
            coverage = BlaBlaCollectorCoverage(
                complete_for_scope = accounts.isNotEmpty() && verified.size == accounts.size,
                global_profile_month_complete = false,
                reason = "Contas cadastradas pelo usuário; leitura autenticada local de Suas viagens com UUID confirmado.",
                requested_queries = accounts.size,
                validated_queries = verified.size,
                failed_or_mismatched_queries = (accounts.size - verified.size).coerceAtLeast(0),
                unresolved_target_cards = snapshots.sumOf { (_, snapshot) -> snapshot.skippedTrips },
                past_dates_skipped = false,
            ),
        )
    }

    fun delete(account: BlaBlaDynamicAccount) {
        file(account.id).delete()
        diagnosticDir(account.id).deleteRecursively()
    }

    fun saveDiagnosticHtml(account: BlaBlaDynamicAccount, kind: String, html: String) {
        if (html.isBlank()) return
        val dir = diagnosticDir(account.id).apply { mkdirs() }
        File(dir, "$kind-latest.html").writeText(html.take(MAX_HTML_CHARS), Charsets.UTF_8)
    }

    private fun write(account: BlaBlaDynamicAccount, snapshot: BlaBlaDynamicSessionSnapshot) {
        val target = file(account.id)
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(json.encodeToString(snapshot), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.writeText(temp.readText(Charsets.UTF_8), Charsets.UTF_8)
            temp.delete()
        }
    }

    private fun file(id: String): File = File(appContext.filesDir, "blablacar-dynamic-session-$id.json")
    private fun diagnosticDir(id: String): File = File(appContext.filesDir, "blablacar-dom/$id")

    private fun strongTripIdentity(trip: BlaBlaCollectorTrip): String = listOf(
        trip.profile_uuid,
        trip.trip_id?.trim()?.takeIf(String::isNotEmpty)
            ?: trip.trip_href?.substringBefore("&search_uuid=")?.trim()?.takeIf(String::isNotEmpty)
            ?: "${trip.date}|${trip.departure_time}|${trip.actual_departure}|${trip.actual_arrival}",
    ).joinToString("|")

    companion object {
        private const val MAX_HTML_CHARS = 350_000
    }
}

object BlaBlaDynamicSessionIntents {
    const val EXTRA_ACCOUNT_ID = "blablacar_account_id"
    const val EXTRA_MODE = "blablacar_mode"
    const val MODE_LOGIN = "login"
    const val MODE_SYNC = "sync"

    fun login(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_LOGIN)
    fun sync(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_SYNC)

    private fun intent(context: Context, account: BlaBlaDynamicAccount, mode: String): Intent =
        Intent(context, BlaBlaDynamicAccountSessionActivity::class.java)
            .putExtra(EXTRA_ACCOUNT_ID, account.id)
            .putExtra(EXTRA_MODE, mode)
}

@Serializable
private data class DynamicIdentityEvidence(
    val profileLinks: List<String> = emptyList(),
    val visibleName: String = "",
    val domHtml: String = "",
)

@Serializable
private data class DynamicRideList(
    val candidates: List<BlaBlaDomRideCandidate> = emptyList(),
    val bodyText: String = "",
    val domHtml: String = "",
)

@Serializable
private data class DynamicTripDetail(
    val detail: BlaBlaDomTripDetail = BlaBlaDomTripDetail(),
    val driverProfileLinks: List<String> = emptyList(),
    val domHtml: String = "",
)

class BlaBlaDynamicAccountSessionActivity : Activity() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var registry: BlaBlaDynamicAccountRegistry
    private lateinit var store: BlaBlaDynamicSessionStore
    private lateinit var account: BlaBlaDynamicAccount
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private var mode = BlaBlaDynamicSessionIntents.MODE_LOGIN
    private var phase = Phase.IDLE
    private var candidates = emptyList<BlaBlaDomRideCandidate>()
    private val collected = mutableListOf<BlaBlaCollectorTrip>()
    private var candidateIndex = 0
    private var skipped = 0
    private var identityConfirmedThisSync = false
    private var rideReadAttempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = BlaBlaDynamicAccountRegistry(this)
        store = BlaBlaDynamicSessionStore(this)
        account = registry.get(intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID)) ?: run {
            finish()
            return
        }
        mode = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_MODE) ?: BlaBlaDynamicSessionIntents.MODE_LOGIN
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            setContentView(TextView(this).apply {
                text = "Este Android System WebView ainda não oferece perfis múltiplos. Atualize o WebView/Chrome para usar várias contas isoladas."
                setPadding(32, 32, 32, 32)
            })
            return
        }
        createBrowserUi()
        if (mode == BlaBlaDynamicSessionIntents.MODE_SYNC) beginSync() else webView.loadUrl(HOME_URL)
    }

    private fun createBrowserUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        statusView = TextView(this).apply {
            text = "${account.displayLabel} • sessão isolada"
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
        action("Voltar") { finishSeen() }
        root.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        webView = WebView(this)
        WebViewCompat.setProfile(webView, account.webProfileName)
        WebViewCompat.getProfile(webView).cookieManager.apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) setAcceptThirdPartyCookies(webView, true)
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                statusView.text = "${account.displayLabel} • ${url.take(110)}"
                when (phase) {
                    Phase.IDENTITY -> if (isBlaBla(url)) view.postDelayed({ captureIdentityForSync() }, 650)
                    Phase.RIDES -> if (isBlaBla(url)) view.postDelayed({ captureRideList() }, 900)
                    Phase.DETAIL -> if (isBlaBla(url)) view.postDelayed({ captureTripDetail() }, 750)
                    Phase.IDLE -> if (isBlaBla(url)) view.postDelayed({ probeIdentity() }, 500)
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
        rideReadAttempts = 0
        identityConfirmedThisSync = false
        phase = Phase.IDENTITY
        statusView.text = "${account.displayLabel} • confirmando conta…"
        webView.loadUrl(PROFILE_URL)
    }

    private fun captureIdentityForSync() {
        evaluate<DynamicIdentityEvidence>(IDENTITY_JS) { evidence ->
            evidence?.let {
                store.saveDiagnosticHtml(account, "profile", it.domHtml)
                bindIdentityFromLinks(it.profileLinks, it.visibleName)?.let { updated -> account = updated }
            }
            phase = Phase.RIDES
            statusView.text = "${account.displayLabel} • lendo Suas viagens…"
            webView.loadUrl(RIDES_URL)
        }
    }

    private fun probeIdentity() {
        evaluate<DynamicIdentityEvidence>(IDENTITY_JS) { evidence ->
            if (evidence == null) return@evaluate
            store.saveDiagnosticHtml(account, "profile", evidence.domHtml)
            val updated = bindIdentityFromLinks(evidence.profileLinks, evidence.visibleName)
            if (updated != null) {
                account = updated
                store.markSeen(account, webView.url.orEmpty())
                statusView.text = "${account.displayLabel} • UUID confirmado ✅"
            }
        }
    }

    private fun captureRideList() {
        if (phase != Phase.RIDES) return
        evaluate<DynamicRideList>(RIDE_LIST_JS) { result ->
            if (result == null) {
                phase = Phase.IDLE
                statusView.text = "Não consegui ler o DOM de Suas viagens."
                return@evaluate
            }
            store.saveDiagnosticHtml(account, "rides", result.domHtml)
            candidates = result.candidates
                .filter { candidate ->
                    val href = candidate.href
                    href.contains("blablacar.com.br") &&
                        (href.contains("/rides/offer") || href.contains("/trip?") || href.contains("/trip/"))
                }
                .distinctBy { canonicalHref(it.href) }
                .take(MAX_TRIPS)
            if (candidates.isEmpty() && rideReadAttempts < 2 && !looksLoggedOut(result.bodyText)) {
                rideReadAttempts++
                statusView.text = "${account.displayLabel} • aguardando os cartões de viagem…"
                webView.postDelayed({ captureRideList() }, 1200)
                return@evaluate
            }
            if (candidates.isEmpty()) {
                val verified = identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()
                store.saveSync(account, webView.url.orEmpty(), emptyList(), 0, verified)
                if (verified) completeSync(0) else {
                    phase = Phase.IDLE
                    statusView.text = if (looksLoggedOut(result.bodyText)) {
                        "A sessão não está autenticada. Entre nesta conta e sincronize novamente."
                    } else {
                        "Nenhuma viagem encontrada e o UUID da conta ainda não foi confirmado."
                    }
                }
                return@evaluate
            }
            phase = Phase.DETAIL
            loadCurrentCandidate()
        }
    }

    private fun loadCurrentCandidate() {
        if (candidateIndex >= candidates.size) {
            val verified = identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()
            store.saveSync(account, webView.url.orEmpty(), collected.toList(), skipped, verified)
            if (verified) completeSync(collected.size) else {
                phase = Phase.IDLE
                statusView.text = "As viagens foram abertas, mas não consegui confirmar o UUID desta conta com segurança."
            }
            return
        }
        val candidate = candidates[candidateIndex]
        statusView.text = "${account.displayLabel} • viagem ${candidateIndex + 1}/${candidates.size}…"
        webView.loadUrl(candidate.href)
    }

    private fun captureTripDetail() {
        if (phase != Phase.DETAIL) return
        val candidate = candidates.getOrNull(candidateIndex) ?: return
        evaluate<DynamicTripDetail>(TRIP_DETAIL_DYNAMIC_JS) { result ->
            if (result != null) {
                store.saveDiagnosticHtml(account, "trip-${candidateIndex + 1}", result.domHtml)
                val driverUuids = uuids(result.driverProfileLinks)
                when {
                    !account.profileUuid.isNullOrBlank() && account.profileUuid!!.lowercase() in driverUuids -> identityConfirmedThisSync = true
                    account.profileUuid.isNullOrBlank() && driverUuids.size == 1 -> {
                        val updated = registry.bindIdentity(account.id, driverUuids.single(), result.detail.driverName)
                        if (updated != null) {
                            account = updated
                            identityConfirmedThisSync = true
                        }
                    }
                }
                val definition = account.verifiedDefinition()
                val trip = definition?.let { BlaBlaDomNormalizer.toTrip(it, candidate, result.detail, LocalDate.now()) }
                if (trip != null && identityConfirmedThisSync) collected += trip else skipped++
            } else {
                skipped++
            }
            candidateIndex++
            loadCurrentCandidate()
        }
    }

    private fun bindIdentityFromLinks(links: List<String>, visibleName: String): BlaBlaDynamicAccount? {
        val found = uuids(links)
        val currentUuid = account.profileUuid?.lowercase()
        return when {
            currentUuid != null && currentUuid in found -> {
                identityConfirmedThisSync = true
                registry.bindIdentity(account.id, currentUuid, visibleName)
            }
            currentUuid == null && found.size == 1 -> {
                identityConfirmedThisSync = true
                registry.bindIdentity(account.id, found.single(), visibleName)
            }
            else -> null
        }
    }

    private fun uuids(links: List<String>): Set<String> = links.flatMap { href ->
        UUID_REGEX.findAll(href).map { it.value.lowercase() }.toList()
    }.toSet()

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

    private fun completeSync(count: Int) {
        phase = Phase.IDLE
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID, account.id)
                .putExtra("trip_count", count),
        )
        finish()
    }

    private fun finishSeen() {
        store.markSeen(account, webView.url.orEmpty())
        setResult(RESULT_CANCELED, Intent().putExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID, account.id))
        finish()
    }

    @Deprecated("Android framework API")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else finishSeen()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    private fun canonicalHref(href: String): String = href.substringBefore("&search_uuid=")
    private fun isBlaBla(url: String): Boolean = url.contains("blablacar.com.br")
    private fun looksLoggedOut(text: String): Boolean {
        val normalized = text.lowercase()
        return normalized.contains("continuar com e-mail") || normalized.contains("como você deseja se conectar") || normalized.contains("como voce deseja se conectar")
    }

    private enum class Phase { IDLE, IDENTITY, RIDES, DETAIL }

    companion object {
        private const val HOME_URL = "https://www.blablacar.com.br/"
        private const val RIDES_URL = "https://www.blablacar.com.br/rides"
        private const val PROFILE_URL = "https://www.blablacar.com.br/dashboard/profile/menu"
        private const val MAX_TRIPS = 80
        private val UUID_REGEX = Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

        private val SANITIZED_HTML_JS = """
            const clone = document.documentElement.cloneNode(true);
            clone.querySelectorAll('script, style, noscript').forEach((node) => node.remove());
            clone.querySelectorAll('input, textarea').forEach((node) => {
              node.removeAttribute('value');
              node.textContent = '';
            });
            const html = clone.outerHTML || '';
        """.trimIndent()

        private val IDENTITY_JS = """
            (function() {
              const clean = (value) => (value || '').replace(/\s+/g, ' ').trim();
              const uuid = /[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i;
              const links = Array.from(document.querySelectorAll('a[href]'))
                .map((a) => a.href || '')
                .filter((href) => uuid.test(href) && /(profile|user|member)/i.test(href));
              if (uuid.test(location.href)) links.push(location.href);
              const nameNode = document.querySelector('[data-testid*="profile-name"], [data-testid*="driver-name"], h1');
              $SANITIZED_HTML_JS
              return JSON.stringify({
                profileLinks: Array.from(new Set(links)),
                visibleName: clean(nameNode && nameNode.innerText),
                domHtml: html.slice(0, 350000)
              });
            })();
        """.trimIndent()

        private val RIDE_LIST_JS = """
            (function() {
              const clean = (value) => (value || '').replace(/\s+/g, ' ').trim();
              const first = (root, selectors) => {
                for (const selector of selectors) {
                  const node = root && root.querySelector(selector);
                  if (node && clean(node.innerText)) return clean(node.innerText);
                }
                return '';
              };
              const roots = Array.from(document.querySelectorAll('[data-testid^="e2e-your-rides-trip-card-"], article[data-testid^="e2e-your-rides-trip-card-"], article'));
              const fromRoots = roots.map((root) => {
                const anchor = root.querySelector('a[href*="/rides/offer"], a[href*="/trip?"] , a[href*="/trip/"]');
                if (!anchor) return null;
                return {
                  href: anchor.href || '',
                  text: clean(root.innerText).slice(0, 2200),
                  departureTime: first(root, ['[data-testid="e2e-itinerary-departure-time"]', '[data-testid*="departure-time"]']),
                  arrivalTime: first(root, ['[data-testid="e2e-itinerary-arrival-time"]', '[data-testid*="arrival-time"]']),
                  origin: first(root, ['[data-testid="e2e-itinerary-departure-station"]', '[data-testid*="departure-station"]']),
                  destination: first(root, ['[data-testid="e2e-itinerary-arrival-station"]', '[data-testid*="arrival-station"]']),
                  price: first(root, ['[data-testid="e2e-tripcard-price"]', '[data-testid="e2e-tripcard-price-price-value"]', '[data-testid*="price"]']),
                  dateText: clean(Array.from(root.querySelectorAll('[data-testid*="date"], time, h1, h2, h3')).map((node) => node.innerText).join(' | ')).slice(0, 800)
                };
              }).filter(Boolean);
              const fallback = fromRoots.length ? [] : Array.from(document.querySelectorAll('a[href*="/rides/offer"], a[href*="/trip?"], a[href*="/trip/"]')).map((anchor) => {
                const root = anchor.closest('article, li, section, div') || anchor.parentElement || document.body;
                return {
                  href: anchor.href || '',
                  text: clean(root.innerText).slice(0, 2200),
                  departureTime: first(root, ['[data-testid="e2e-itinerary-departure-time"]', '[data-testid*="departure-time"]']),
                  arrivalTime: first(root, ['[data-testid="e2e-itinerary-arrival-time"]', '[data-testid*="arrival-time"]']),
                  origin: first(root, ['[data-testid="e2e-itinerary-departure-station"]', '[data-testid*="departure-station"]']),
                  destination: first(root, ['[data-testid="e2e-itinerary-arrival-station"]', '[data-testid*="arrival-station"]']),
                  price: first(root, ['[data-testid*="price"]']),
                  dateText: clean(root.innerText).slice(0, 800)
                };
              });
              $SANITIZED_HTML_JS
              return JSON.stringify({
                candidates: fromRoots.concat(fallback),
                bodyText: clean(document.body && document.body.innerText).slice(0, 12000),
                domHtml: html.slice(0, 350000)
              });
            })();
        """.trimIndent()

        private val TRIP_DETAIL_DYNAMIC_JS = """
            (function() {
              const clean = (value) => (value || '').replace(/\s+/g, ' ').trim();
              const first = (selectors) => {
                for (const selector of selectors) {
                  const node = document.querySelector(selector);
                  if (node && clean(node.innerText)) return clean(node.innerText);
                }
                return '';
              };
              const driverNode = document.querySelector('[data-testid="e2e-tripcard-driver-name"], [data-testid*="driver-name"], [data-testid*="driver"]');
              const driverLinks = [];
              if (driverNode) {
                const direct = driverNode.closest('a[href]');
                if (direct) driverLinks.push(direct.href);
                const root = driverNode.closest('section, article, li, div');
                if (root) Array.from(root.querySelectorAll('a[href]')).forEach((a) => driverLinks.push(a.href));
              }
              const uuid = /[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i;
              const scopedDriverLinks = Array.from(new Set(driverLinks.filter((href) => uuid.test(href))));
              const allProfileLinks = Array.from(new Set(Array.from(document.querySelectorAll('a[href]'))
                .map((a) => a.href || '')
                .filter((href) => uuid.test(href) && /(profile|user|member)/i.test(href))));
              const dateText = clean(Array.from(document.querySelectorAll('[data-testid*="date"], time, h1, h2, h3')).map((node) => node.innerText).join(' | ')).slice(0, 1400);
              $SANITIZED_HTML_JS
              return JSON.stringify({
                detail: {
                  url: location.href,
                  bodyText: clean(document.body && document.body.innerText).slice(0, 16000),
                  dateText: dateText,
                  departureTime: first(['[data-testid="e2e-itinerary-departure-time"]', '[data-testid*="departure-time"]']),
                  arrivalTime: first(['[data-testid="e2e-itinerary-arrival-time"]', '[data-testid*="arrival-time"]']),
                  origin: first(['[data-testid="e2e-itinerary-departure-station"]', '[data-testid*="departure-station"]']),
                  destination: first(['[data-testid="e2e-itinerary-arrival-station"]', '[data-testid*="arrival-station"]']),
                  price: first(['[data-testid="e2e-tripcard-price"]', '[data-testid="e2e-tripcard-price-price-value"]', '[data-testid*="price"]']),
                  driverName: clean(driverNode && driverNode.innerText),
                  profileLinks: scopedDriverLinks.length ? scopedDriverLinks : allProfileLinks
                },
                driverProfileLinks: scopedDriverLinks.length ? scopedDriverLinks : allProfileLinks,
                domHtml: html.slice(0, 350000)
              });
            })();
        """.trimIndent()
    }
}
