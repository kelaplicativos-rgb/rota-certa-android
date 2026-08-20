package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
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
        get() = label.trim().takeIf(String::isNotEmpty)
            ?: profileName?.trim()?.takeIf(String::isNotEmpty)
            ?: "Conta BlaBlaCar"

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
        val previousByIdentity = read(account)?.trips
            ?.associateBy { previous -> BlaBlaTripIdentity.evidence(previous).key }
            .orEmpty()
        var preservedIncompleteRosters = 0
        val reconciledTrips = trips.map { current ->
            val previous = previousByIdentity[BlaBlaTripIdentity.evidence(current).key]
            val reconciled = BlaBlaPassengerRosterReconciler.reconcile(previous, current)
            if (!current.passenger_roster_complete && previous != null && reconciled.booked_seats > current.booked_seats) {
                preservedIncompleteRosters++
            }
            reconciled
        }
        write(
            account,
            BlaBlaDynamicSessionSnapshot(
                accountId = account.id,
                profileUuid = account.profileUuid,
                profileLabel = account.displayLabel,
                identityVerified = identityVerified,
                lastUrl = lastUrl.take(1000),
                updatedAtMillis = System.currentTimeMillis(),
                trips = reconciledTrips,
                skippedTrips = skippedTrips,
            ),
        )
        UnifiedDebugEventStore.record(
            "SNAPSHOT_SAVED",
            appContext.packageName,
            "account=${account.displayLabel} expectedUuid=${account.profileUuid.orEmpty()} trips=${reconciledTrips.size} rosterComplete=${reconciledTrips.count { it.passenger_roster_complete }} rosterIncomplete=${reconciledTrips.count { !it.passenger_roster_complete }} preservedIncomplete=$preservedIncompleteRosters skipped=$skippedTrips identityVerified=$identityVerified",
        )
    }

    fun combinedResponse(accounts: List<BlaBlaDynamicAccount>): BlaBlaCollectorMonthResponse {
        val snapshots = accounts.mapNotNull { account -> read(account)?.let { account to it } }
        val verified = snapshots.filter { (account, snapshot) ->
            snapshot.identityVerified && !account.profileUuid.isNullOrBlank() && snapshot.profileUuid == account.profileUuid
        }
        val beforeDistinct = verified.flatMap { (_, snapshot) -> snapshot.trips }
        beforeDistinct.forEachIndexed { index, trip ->
            val identity = BlaBlaTripIdentity.evidence(trip)
            UnifiedDebugEventStore.record(
                "TRIP_IDENTITY",
                appContext.packageName,
                "index=${index + 1}/${beforeDistinct.size} externalTripIdPresent=${identity.externalTripIdPresent} specificHrefPresent=${identity.specificHrefPresent} fallbackIdentityUsed=${identity.fallbackIdentityUsed} identityHash=${identity.identityHash}",
            )
        }
        val trips = beforeDistinct
            .distinctBy { trip -> BlaBlaTripIdentity.evidence(trip).key }
            .sortedWith(compareBy<BlaBlaCollectorTrip>({ it.date }, { it.departure_time.orEmpty() }))
        val response = BlaBlaCollectorMonthResponse(
            collected_at = Instant.now().toString(),
            status = when {
                accounts.isEmpty() -> "empty"
                verified.size == accounts.size -> "validated"
                verified.isEmpty() -> "blocked"
                else -> "partial"
            },
            month = null,
            strategy = "authenticated_on_device_batch_first_dynamic_multi_profile",
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
        UnifiedDebugEventStore.record(
            "COMBINED_RESPONSE",
            appContext.packageName,
            "accounts=${accounts.size} verifiedAccounts=${verified.size} beforeDistinct=${beforeDistinct.size} tripCount=${trips.size} deduped=${(beforeDistinct.size - trips.size).coerceAtLeast(0)} rosterComplete=${trips.count { it.passenger_roster_complete }} rosterIncomplete=${trips.count { !it.passenger_roster_complete }} skipped=${snapshots.sumOf { (_, snapshot) -> snapshot.skippedTrips }} status=${response.status}",
        )
        return response
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

    companion object {
        private const val MAX_HTML_CHARS = 350_000
    }
}

object BlaBlaDynamicSessionIntents {
    const val EXTRA_ACCOUNT_ID = "blablacar_account_id"
    const val EXTRA_MODE = "blablacar_mode"
    const val EXTRA_TARGET_URL = "blablacar_target_url"
    const val MODE_LOGIN = "login"
    const val MODE_SYNC = "sync"
    const val MODE_MANAGE = "manage"

    fun login(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_LOGIN)
    fun sync(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_SYNC)
    fun manage(context: Context, account: BlaBlaDynamicAccount, tripHref: String): Intent =
        intent(context, account, MODE_MANAGE).putExtra(EXTRA_TARGET_URL, tripHref)

    private fun intent(context: Context, account: BlaBlaDynamicAccount, mode: String): Intent =
        Intent(context, BlaBlaDynamicAccountSessionActivity::class.java)
            .putExtra(EXTRA_ACCOUNT_ID, account.id)
            .putExtra(EXTRA_MODE, mode)
}

@Serializable
private data class DynamicIdentityEvidence(
    val profileLinks: List<String> = emptyList(),
    val observedUuids: List<String> = emptyList(),
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

@Serializable
private data class DynamicPassengerContactEvidence(
    val phone: String = "",
    val visibleName: String = "",
)

internal class BlaBlaSyncCompletionGate {
    private var snapshotGeneration = Long.MIN_VALUE
    private var completionGeneration = Long.MIN_VALUE

    fun claimSnapshot(generation: Long): Boolean {
        if (snapshotGeneration == generation) return false
        snapshotGeneration = generation
        return true
    }

    fun claimCompletion(generation: Long): Boolean {
        if (completionGeneration == generation) return false
        completionGeneration = generation
        return true
    }
}

internal fun nextBlaBlaCandidateIndex(current: Int, size: Int): Int = when {
    size <= 0 -> 0
    current < 0 -> 0
    current >= size -> size
    else -> current + 1
}

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
    private var pendingTripDetail: DynamicTripDetail? = null
    private var pendingTripPassengers = mutableListOf<BlaBlaCollectorPassenger>()
    private var passengerContactIndex = 0
    private var passengerContactReadAttempts = 0
    private var syncGeneration = 0L
    private var navigationGeneration = 0L
    private var detailCaptureInFlight = false
    private var passengerCaptureInFlight = false
    private var pendingTripSyncGeneration = -1L
    private var pendingTripCandidateIndex = -1
    private val completionGate = BlaBlaSyncCompletionGate()

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
        when (mode) {
            BlaBlaDynamicSessionIntents.MODE_SYNC -> beginSync()
            BlaBlaDynamicSessionIntents.MODE_MANAGE -> webView.loadUrl(manageTargetUrl() ?: RIDES_URL)
            else -> webView.loadUrl(HOME_URL)
        }
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
        if (mode == BlaBlaDynamicSessionIntents.MODE_SYNC) actions.visibility = android.view.View.GONE

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
                if (mode != BlaBlaDynamicSessionIntents.MODE_SYNC) {
                    statusView.text = "${account.displayLabel} • ${url.take(110)}"
                }
                when (phase) {
                    Phase.IDENTITY -> if (isBlaBla(url)) view.postDelayed({ captureIdentityForSync() }, 650)
                    Phase.RIDES -> if (isBlaBla(url)) view.postDelayed({ captureRideList() }, 900)
                    Phase.DETAIL -> if (isBlaBla(url)) scheduleTripDetailCapture(view)
                    Phase.PASSENGER_CONTACT -> if (isBlaBla(url)) schedulePassengerContactCapture(view)
                    Phase.IDLE -> if (isBlaBla(url)) view.postDelayed({ probeIdentity() }, 500)
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun beginSync() {
        syncGeneration++
        navigationGeneration = 0L
        detailCaptureInFlight = false
        passengerCaptureInFlight = false
        pendingTripSyncGeneration = -1L
        pendingTripCandidateIndex = -1
        collected.clear()
        candidates = emptyList()
        candidateIndex = 0
        skipped = 0
        rideReadAttempts = 0
        identityConfirmedThisSync = false
        pendingTripDetail = null
        pendingTripPassengers.clear()
        passengerContactIndex = 0
        passengerContactReadAttempts = 0
        phase = Phase.IDENTITY
        statusView.text = "${account.displayLabel} • confirmando conta…"
        UnifiedDebugEventStore.record(
            "SYNC_START",
            packageName,
            "account=${account.displayLabel} expectedUuid=${account.profileUuid.orEmpty()} url=${sanitizedUrl(PROFILE_URL)}",
        )
        loadTrackedUrl(PROFILE_URL)
    }

    private fun loadTrackedUrl(url: String) {
        navigationGeneration++
        webView.loadUrl(url)
    }

    private fun scheduleTripDetailCapture(view: WebView) {
        val expectedSync = syncGeneration
        val expectedNavigation = navigationGeneration
        val expectedCandidate = candidateIndex
        view.postDelayed({ captureTripDetail(expectedSync, expectedNavigation, expectedCandidate) }, 750)
    }

    private fun schedulePassengerContactCapture(view: WebView) {
        val expectedSync = syncGeneration
        val expectedNavigation = navigationGeneration
        val expectedCandidate = candidateIndex
        val expectedPassenger = passengerContactIndex
        view.postDelayed({ capturePassengerContact(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger) }, 850)
    }

    private fun captureIdentityForSync() {
        evaluate<DynamicIdentityEvidence>(IDENTITY_JS) { evidence ->
            evidence?.let {
                store.saveDiagnosticHtml(account, "profile", it.domHtml)
                val expectedUuid = account.profileUuid?.lowercase()
                val observedUuids = it.observedUuids.map(String::lowercase).toSet()
                val expectedFoundInAuthenticatedPage = expectedUuid != null && expectedUuid in observedUuids
                if (expectedFoundInAuthenticatedPage) {
                    identityConfirmedThisSync = true
                } else {
                    bindIdentityFromLinks(it.profileLinks, it.visibleName)?.let { updated -> account = updated }
                }
                UnifiedDebugEventStore.record(
                    "IDENTITY_EVIDENCE",
                    packageName,
                    "account=${account.displayLabel} expectedUuid=${expectedUuid.orEmpty()} expectedFound=$expectedFoundInAuthenticatedPage observedCount=${observedUuids.size} profileLinkCount=${it.profileLinks.size} url=${sanitizedUrl(webView.url.orEmpty())}",
                )
            }
            if (identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()) {
                UnifiedDebugEventStore.record(
                    "IDENTITY_VERIFIED",
                    packageName,
                    "account=${account.displayLabel} expectedUuid=${account.profileUuid.orEmpty()} foundUuid=${account.profileUuid.orEmpty()} method=authenticated_profile",
                )
            }
            phase = Phase.RIDES
            statusView.text = "${account.displayLabel} • lendo Suas viagens…"
            loadTrackedUrl(RIDES_URL)
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
                UnifiedDebugEventStore.record(
                    "SYNC_END",
                    packageName,
                    "account=${account.displayLabel} status=rides_dom_unreadable trips=${collected.size} skipped=$skipped",
                )
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
            UnifiedDebugEventStore.record(
                "RIDES_DOM_CAPTURED",
                packageName,
                "account=${account.displayLabel} candidateCount=${candidates.size} attempt=$rideReadAttempts url=${sanitizedUrl(webView.url.orEmpty())}",
            )
            UnifiedDebugEventStore.record(
                "RIDES_BATCH_EVIDENCE",
                packageName,
                "account=${account.displayLabel} cards=${candidates.size} passengers=${candidates.sumOf { it.passengers.size }} bookingLinks=${candidates.sumOf { candidate -> candidate.passengers.count { !it.booking_href.isNullOrBlank() } }} phones=${candidates.sumOf { candidate -> candidate.passengers.count { !it.phone.isNullOrBlank() } }} completeRosters=${candidates.count { it.passengerRosterComplete }}",
            )
            if (candidates.isEmpty() && rideReadAttempts < 2 && !looksLoggedOut(result.bodyText)) {
                rideReadAttempts++
                statusView.text = "${account.displayLabel} • aguardando os cartões de viagem…"
                webView.postDelayed({ captureRideList() }, 1200)
                return@evaluate
            }
            if (candidates.isEmpty()) {
                val verified = identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()
                saveFinalSnapshotOnce(verified)
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
        if (candidateIndex > candidates.size) {
            UnifiedDebugEventStore.record(
                "SYNC_INDEX_GUARD",
                packageName,
                "account=${account.displayLabel} candidateIndex=$candidateIndex candidateCount=${candidates.size} action=clamp",
            )
            candidateIndex = candidates.size
        }
        if (candidateIndex >= candidates.size) {
            val verified = identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()
            saveFinalSnapshotOnce(verified)
            if (verified) completeSync(collected.size) else {
                phase = Phase.IDLE
                statusView.text = "As viagens foram processadas, mas não consegui confirmar o UUID desta conta com segurança."
            }
            return
        }

        val candidate = candidates[candidateIndex]
        val definition = account.verifiedDefinition()
        val missingContactLink = candidate.passengers.any { it.phone.isNullOrBlank() && it.booking_href.isNullOrBlank() }
        val hasBatchRosterEvidence = candidate.passengerRosterComplete || candidate.passengers.isNotEmpty()
        val synthetic = DynamicTripDetail(
            detail = BlaBlaDomTripDetail(
                url = candidate.href,
                passengers = candidate.passengers,
                passengerRosterComplete = candidate.passengerRosterComplete,
            ),
        )
        val batchTrip = if (definition != null && identityConfirmedThisSync) {
            BlaBlaDomNormalizer.toTrip(
                account = definition,
                candidate = candidate,
                detail = synthetic.detail,
                today = LocalDate.now(),
                authenticatedProfileSessionVerified = true,
            )
        } else null

        if (batchTrip != null && hasBatchRosterEvidence && !missingContactLink) {
            pendingTripDetail = synthetic
            pendingTripPassengers = candidate.passengers.toMutableList()
            pendingTripSyncGeneration = syncGeneration
            pendingTripCandidateIndex = candidateIndex
            passengerContactIndex = 0
            passengerContactReadAttempts = 0
            UnifiedDebugEventStore.record(
                "TRIP_BATCH_READY",
                packageName,
                "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} passengers=${candidate.passengers.size} bookingLinks=${candidate.passengers.count { !it.booking_href.isNullOrBlank() }} phones=${candidate.passengers.count { !it.phone.isNullOrBlank() }} rosterComplete=${candidate.passengerRosterComplete}",
            )
            if (pendingTripPassengers.any { it.phone.isNullOrBlank() && !it.booking_href.isNullOrBlank() }) {
                loadNextPassengerContact(syncGeneration, candidateIndex)
            } else {
                finalizeCurrentTrip(syncGeneration, candidateIndex)
            }
            return
        }

        statusView.text = "${account.displayLabel} • complementando viagem ${candidateIndex + 1}/${candidates.size}…"
        UnifiedDebugEventStore.record(
            "TRIP_DETAIL_FALLBACK",
            packageName,
            "account=${account.displayLabel} index=${candidateIndex + 1}/${candidates.size} batchCoreValid=${batchTrip != null} batchPassengers=${candidate.passengers.size} rosterComplete=${candidate.passengerRosterComplete} missingContactLink=$missingContactLink",
        )
        loadTrackedUrl(candidate.href)
    }

    private fun saveFinalSnapshotOnce(verified: Boolean): Boolean {
        if (!completionGate.claimSnapshot(syncGeneration)) {
            UnifiedDebugEventStore.record(
                "STALE_CALLBACK_IGNORED",
                packageName,
                "account=${account.displayLabel} reason=duplicate_snapshot generation=$syncGeneration",
            )
            return false
        }
        store.saveSync(account, webView.url.orEmpty(), collected.toList(), skipped, verified)
        return true
    }

    private fun captureTripDetail(expectedSync: Long, expectedNavigation: Long, expectedCandidate: Int) {
        if (!detailCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate)) {
            recordStale("trip_detail_before_evaluate", expectedSync, expectedCandidate)
            return
        }
        if (detailCaptureInFlight) {
            recordStale("trip_detail_in_flight", expectedSync, expectedCandidate)
            return
        }
        val candidate = candidates.getOrNull(expectedCandidate) ?: run {
            recordStale("trip_detail_candidate_missing", expectedSync, expectedCandidate)
            return
        }
        detailCaptureInFlight = true
        evaluate<DynamicTripDetail>(TRIP_DETAIL_DYNAMIC_JS) { result ->
            detailCaptureInFlight = false
            if (!detailCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate)) {
                recordStale("trip_detail_after_evaluate", expectedSync, expectedCandidate)
                return@evaluate
            }
            if (result == null) {
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} reason=detail_dom_unreadable url=${sanitizedUrl(webView.url.orEmpty())}",
                )
                advanceCandidate(expectedSync, expectedCandidate)
                return@evaluate
            }

            store.saveDiagnosticHtml(account, "trip-${expectedCandidate + 1}", result.domHtml)
            val driverUuids = uuids(result.driverProfileLinks)
            val expectedUuid = account.profileUuid?.lowercase()
            UnifiedDebugEventStore.record(
                "TRIP_DETAIL_CAPTURED",
                packageName,
                "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} expectedUuid=${expectedUuid.orEmpty()} foundUuids=${driverUuids.joinToString(",")} passengers=${result.detail.passengers.size} rosterComplete=${result.detail.passengerRosterComplete} url=${sanitizedUrl(webView.url.orEmpty())}",
            )
            if (expectedUuid != null && driverUuids.isNotEmpty() && expectedUuid !in driverUuids) {
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} reason=explicit_detail_uuid_mismatch expectedUuid=$expectedUuid foundUuids=${driverUuids.joinToString(",")}",
                )
                advanceCandidate(expectedSync, expectedCandidate)
                return@evaluate
            }
            when {
                expectedUuid != null && expectedUuid in driverUuids -> identityConfirmedThisSync = true
                expectedUuid == null && driverUuids.size == 1 -> {
                    val updated = registry.bindIdentity(account.id, driverUuids.single(), result.detail.driverName)
                    if (updated != null) {
                        account = updated
                        identityConfirmedThisSync = true
                    }
                }
            }

            if (!identityConfirmedThisSync || account.verifiedDefinition() == null) {
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} reason=identity_not_verified expectedUuid=${account.profileUuid.orEmpty()} foundUuids=${driverUuids.joinToString(",")}",
                )
                advanceCandidate(expectedSync, expectedCandidate)
                return@evaluate
            }

            val definition = account.verifiedDefinition()
            val preview = definition?.let {
                BlaBlaDomNormalizer.toTrip(
                    account = it,
                    candidate = candidate,
                    detail = result.detail,
                    today = LocalDate.now(),
                    authenticatedProfileSessionVerified = identityConfirmedThisSync,
                )
            }
            pendingTripDetail = result
            pendingTripPassengers = (preview?.passengers ?: result.detail.passengers).toMutableList()
            pendingTripSyncGeneration = expectedSync
            pendingTripCandidateIndex = expectedCandidate
            passengerContactIndex = 0
            passengerContactReadAttempts = 0
            if (pendingTripPassengers.any { it.phone.isNullOrBlank() && !it.booking_href.isNullOrBlank() }) {
                loadNextPassengerContact(expectedSync, expectedCandidate)
            } else {
                finalizeCurrentTrip(expectedSync, expectedCandidate)
            }
        }
    }

    private fun detailCaptureIsCurrent(expectedSync: Long, expectedNavigation: Long, expectedCandidate: Int): Boolean =
        phase == Phase.DETAIL &&
            expectedSync == syncGeneration &&
            expectedNavigation == navigationGeneration &&
            expectedCandidate == candidateIndex &&
            expectedCandidate in candidates.indices

    private fun loadNextPassengerContact(expectedSync: Long, expectedCandidate: Int) {
        if (!pendingTripIsCurrent(expectedSync, expectedCandidate)) {
            recordStale("passenger_load_pending_mismatch", expectedSync, expectedCandidate)
            return
        }
        while (passengerContactIndex < pendingTripPassengers.size) {
            val passenger = pendingTripPassengers[passengerContactIndex]
            val href = passenger.booking_href?.trim().orEmpty()
            if (passenger.phone.isNullOrBlank() && href.isNotBlank() && isBlaBla(href)) {
                phase = Phase.PASSENGER_CONTACT
                passengerContactReadAttempts = 0
                passengerCaptureInFlight = false
                statusView.text = "${account.displayLabel} • contato ${passengerContactIndex + 1}/${pendingTripPassengers.size}…"
                loadTrackedUrl(href)
                return
            }
            passengerContactIndex++
        }
        finalizeCurrentTrip(expectedSync, expectedCandidate)
    }

    private fun capturePassengerContact(
        expectedSync: Long,
        expectedNavigation: Long,
        expectedCandidate: Int,
        expectedPassenger: Int,
    ) {
        if (!passengerCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)) {
            recordStale("passenger_before_evaluate", expectedSync, expectedCandidate)
            return
        }
        if (passengerCaptureInFlight) {
            recordStale("passenger_in_flight", expectedSync, expectedCandidate)
            return
        }
        val current = pendingTripPassengers.getOrNull(expectedPassenger) ?: run {
            recordStale("passenger_missing", expectedSync, expectedCandidate)
            return
        }
        passengerCaptureInFlight = true
        evaluate<DynamicPassengerContactEvidence>(PASSENGER_CONTACT_JS) { evidence ->
            passengerCaptureInFlight = false
            if (!passengerCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)) {
                recordStale("passenger_after_evaluate", expectedSync, expectedCandidate)
                return@evaluate
            }
            val phone = normalizeCapturedPhone(evidence?.phone)
            if (phone == null && passengerContactReadAttempts < 2) {
                passengerContactReadAttempts++
                webView.postDelayed({
                    capturePassengerContact(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)
                }, 700)
                return@evaluate
            }

            val visibleName = evidence?.visibleName?.trim().orEmpty()
            pendingTripPassengers[expectedPassenger] = current.copy(
                name = current.name.ifBlank { visibleName },
                phone = current.phone?.takeIf(String::isNotBlank) ?: phone,
            )
            UnifiedDebugEventStore.record(
                "PASSENGER_CONTACT_CAPTURED",
                packageName,
                "account=${account.displayLabel} tripIndex=${expectedCandidate + 1}/${candidates.size} passengerIndex=${expectedPassenger + 1}/${pendingTripPassengers.size} phonePresent=${phone != null} bookingLinkPresent=${!current.booking_href.isNullOrBlank()}",
            )
            passengerContactIndex = expectedPassenger + 1
            loadNextPassengerContact(expectedSync, expectedCandidate)
        }
    }

    private fun passengerCaptureIsCurrent(
        expectedSync: Long,
        expectedNavigation: Long,
        expectedCandidate: Int,
        expectedPassenger: Int,
    ): Boolean = phase == Phase.PASSENGER_CONTACT &&
        expectedSync == syncGeneration &&
        expectedNavigation == navigationGeneration &&
        expectedCandidate == candidateIndex &&
        expectedPassenger == passengerContactIndex &&
        pendingTripIsCurrent(expectedSync, expectedCandidate) &&
        expectedPassenger in pendingTripPassengers.indices

    private fun pendingTripIsCurrent(expectedSync: Long, expectedCandidate: Int): Boolean =
        expectedSync == syncGeneration &&
            expectedCandidate == candidateIndex &&
            pendingTripSyncGeneration == expectedSync &&
            pendingTripCandidateIndex == expectedCandidate &&
            pendingTripDetail != null

    private fun finalizeCurrentTrip(expectedSync: Long, expectedCandidate: Int) {
        if (!pendingTripIsCurrent(expectedSync, expectedCandidate)) {
            recordStale("finalize_pending_mismatch", expectedSync, expectedCandidate)
            return
        }
        val candidate = candidates.getOrNull(expectedCandidate)
        val result = pendingTripDetail
        val definition = account.verifiedDefinition()
        if (candidate == null || result == null || definition == null) {
            skipped++
            UnifiedDebugEventStore.record(
                "TRIP_REJECTED",
                packageName,
                "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} reason=pending_trip_state_missing",
            )
            advanceCandidate(expectedSync, expectedCandidate)
            return
        }

        val enrichedDetail = result.detail.copy(passengers = pendingTripPassengers.toList())
        val trip = BlaBlaDomNormalizer.toTrip(
            account = definition,
            candidate = candidate,
            detail = enrichedDetail,
            today = LocalDate.now(),
            authenticatedProfileSessionVerified = identityConfirmedThisSync,
        )
        if (trip != null && identityConfirmedThisSync) {
            collected += trip
            UnifiedDebugEventStore.record(
                "TRIP_ACCEPTED",
                packageName,
                "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} validation=${trip.uuid_validation} date=${trip.date} departure=${trip.departure_time.orEmpty()} origin=${trip.actual_departure.orEmpty()} destination=${trip.actual_arrival.orEmpty()} passengers=${trip.passengers.size} phones=${trip.passengers.count { !it.phone.isNullOrBlank() }} rosterComplete=${trip.passenger_roster_complete}",
            )
        } else {
            skipped++
            UnifiedDebugEventStore.record(
                "TRIP_REJECTED",
                packageName,
                "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} reason=trip_fields_unparseable expectedUuid=${account.profileUuid.orEmpty()}",
            )
        }
        advanceCandidate(expectedSync, expectedCandidate)
    }

    private fun advanceCandidate(expectedSync: Long, expectedCandidate: Int) {
        if (expectedSync != syncGeneration || expectedCandidate != candidateIndex) {
            recordStale("advance_candidate_mismatch", expectedSync, expectedCandidate)
            return
        }
        pendingTripDetail = null
        pendingTripPassengers.clear()
        pendingTripSyncGeneration = -1L
        pendingTripCandidateIndex = -1
        passengerContactIndex = 0
        passengerContactReadAttempts = 0
        passengerCaptureInFlight = false
        phase = Phase.DETAIL
        candidateIndex = nextBlaBlaCandidateIndex(candidateIndex, candidates.size)
        loadCurrentCandidate()
    }

    private fun recordStale(reason: String, expectedSync: Long, expectedCandidate: Int) {
        UnifiedDebugEventStore.record(
            "STALE_CALLBACK_IGNORED",
            packageName,
            "account=${account.displayLabel} reason=$reason expectedGeneration=$expectedSync currentGeneration=$syncGeneration expectedCandidate=${expectedCandidate + 1} currentCandidate=${candidateIndex + 1} candidateCount=${candidates.size}",
        )
    }

    private fun normalizeCapturedPhone(raw: String?): String? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val hasPlus = value.startsWith("+")
        val digits = value.filter(Char::isDigit)
        if (digits.length < 8 || digits.length > 15) return null
        return if (hasPlus) "+$digits" else digits
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
        if (!completionGate.claimCompletion(syncGeneration)) {
            UnifiedDebugEventStore.record(
                "STALE_CALLBACK_IGNORED",
                packageName,
                "account=${account.displayLabel} reason=duplicate_complete generation=$syncGeneration",
            )
            return
        }
        phase = Phase.IDLE
        UnifiedDebugEventStore.record(
            "SYNC_END",
            packageName,
            "account=${account.displayLabel} status=success trips=$count skipped=$skipped identityVerified=$identityConfirmedThisSync",
        )
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID, account.id)
                .putExtra("trip_count", count),
        )
        finish()
    }

    private fun manageTargetUrl(): String? {
        val value = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_URL)?.trim().orEmpty()
        return value.takeIf { href ->
            href.startsWith("https://www.blablacar.com.br/") &&
                (href.contains("/trip") || href.contains("/rides/offer"))
        }
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
    private fun sanitizedUrl(url: String): String = url.substringBefore('?').substringBefore('#').take(240)
    private fun isBlaBla(url: String): Boolean = url.contains("blablacar.com.br")
    private fun looksLoggedOut(text: String): Boolean {
        val normalized = text.lowercase()
        return normalized.contains("continuar com e-mail") || normalized.contains("como você deseja se conectar") || normalized.contains("como voce deseja se conectar")
    }

    private enum class Phase { IDLE, IDENTITY, RIDES, DETAIL, PASSENGER_CONTACT }

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
              const nameNode = document.querySelector('[data-testid*="profile-name"], [data-testid*="driver-name"]');
              const resourceUrls = (performance && performance.getEntriesByType)
                ? performance.getEntriesByType('resource').map((entry) => entry.name || '')
                : [];
              const navigationUrls = (performance && performance.getEntriesByType)
                ? performance.getEntriesByType('navigation').map((entry) => entry.name || '')
                : [];
              const rawIdentityEvidence = [
                location.href || '',
                document.documentElement ? (document.documentElement.outerHTML || '') : '',
                ...resourceUrls,
                ...navigationUrls
              ].join('\n');
              const observedUuids = Array.from(new Set(
                (rawIdentityEvidence.match(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/ig) || [])
                  .map((value) => value.toLowerCase())
              ));
              $SANITIZED_HTML_JS
              return JSON.stringify({
                profileLinks: Array.from(new Set(links)),
                observedUuids: observedUuids,
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
              const candidateHref = (root) => {
                const anchors = Array.from(root.querySelectorAll('a[href]'))
                  .map((anchor) => ({ anchor, href: anchor.href || '' }))
                  .filter((item) => item.href && !item.href.includes('/rides/offer/passenger/'));
                return (
                  anchors.find((item) => /\/rides\/offer\/[^/?#]+/i.test(item.href) || /\/trip\/[^/?#]+/i.test(item.href)) ||
                  anchors.find((item) => /\/rides\/offer\?[^#]*\bid=/i.test(item.href) || /\/trip\?[^#]*\bid=/i.test(item.href)) ||
                  anchors.find((item) => item.href.includes('/rides/offer') || item.href.includes('/trip?') || item.href.includes('/trip/')) ||
                  null
                );
              };
              const normalizePassengerName = (value) => clean(value)
                .replace(/\s*[•|].*$/, '')
                .replace(/\s*\(\d+\)\s*$/, '');
              const extractPassengers = (root) => {
                const passengerMap = new Map();
                const scoped = Array.from(root.querySelectorAll('a[href*="/rides/offer/passenger/"], [data-testid*="passenger"], [data-testid*="booking"]'));
                const hintedRoot = /passageir|reserva/i.test(clean(root.innerText));
                if (hintedRoot) {
                  Array.from(root.querySelectorAll('img[alt]')).forEach((image) => {
                    const scope = image.closest('a[href*="/rides/offer/passenger/"], [data-testid*="passenger"], [data-testid*="booking"]');
                    if (scope) scoped.push(scope);
                  });
                }
                scoped.forEach((node, index) => {
                  const link = (node.matches && node.matches('a[href*="/rides/offer/passenger/"]'))
                    ? node
                    : (node.querySelector && node.querySelector('a[href*="/rides/offer/passenger/"]'));
                  const href = link ? (link.href || '') : '';
                  const container = node.closest && (node.closest('li, [role="listitem"], [data-testid*="passenger"], [data-testid*="booking"]') || node);
                  const raw = clean((container && container.innerText) || node.innerText || '');
                  const lines = raw.split(/\n+/).map(clean).filter(Boolean);
                  const explicitName = container && container.querySelector
                    ? container.querySelector('[data-testid*="passenger-name"], [data-testid*="profile-name"], img[alt]')
                    : null;
                  const alt = explicitName && explicitName.getAttribute ? clean(explicitName.getAttribute('alt')) : '';
                  let name = normalizePassengerName(alt || clean(explicitName && explicitName.innerText) || lines[0] || '');
                  if (!name || /^(foto|avatar|perfil|blablacar|passageiro|passageira)$/i.test(name)) return;
                  const suffixSource = lines.find((line) => /\(\d+\)\s*$/.test(line)) || name;
                  const suffix = suffixSource.match(/\((\d+)\)\s*$/);
                  const seats = suffix ? Math.max(1, parseInt(suffix[1], 10) || 1) : 1;
                  name = normalizePassengerName(name);
                  const route = lines.find((line) => line.includes('→') || line.includes('->')) || '';
                  const routeParts = route.split(/→|->/).map(clean);
                  const tel = container && container.querySelector ? container.querySelector('a[href^="tel:"]') : null;
                  const key = href || [name.toLowerCase(), seats, route].join('|') || String(index);
                  if (!passengerMap.has(key)) {
                    passengerMap.set(key, {
                      name: name,
                      seats: seats,
                      boarding: routeParts.length >= 2 ? routeParts[0] : null,
                      dropoff: routeParts.length >= 2 ? routeParts[routeParts.length - 1] : null,
                      phone: tel ? (tel.getAttribute('href') || '').replace(/^tel:/i, '') : null,
                      booking_href: href || null
                    });
                  }
                });
                const passengers = Array.from(passengerMap.values()).filter((item) => item.name);
                const rosterContainers = Array.from(root.querySelectorAll('[data-testid], [aria-label]')).filter((node) => {
                  const marker = ((node.getAttribute('data-testid') || '') + ' ' + (node.getAttribute('aria-label') || '')).toLowerCase();
                  return marker.includes('passenger') || marker.includes('passageir') || marker.includes('booking') || marker.includes('reserva');
                });
                const hasMore = Array.from(root.querySelectorAll('button, a, [role="button"]')).some((node) => /mostrar mais|ver mais|mais passageir|mais reserva/i.test(clean(node.innerText)));
                return {
                  passengers: passengers,
                  passengerRosterComplete: rosterContainers.length > 0 && !hasMore
                };
              };
              const dateEvidence = (root) => {
                const structured = Array.from(root.querySelectorAll('time[datetime]'))
                  .map((node) => clean(node.getAttribute('datetime')))
                  .filter(Boolean);
                const visible = Array.from(root.querySelectorAll('[data-testid*="date"], time, h1, h2, h3'))
                  .map((node) => clean(node.innerText))
                  .filter(Boolean);
                return clean(structured.concat(visible).join(' | ')).slice(0, 1200);
              };
              const roots = Array.from(document.querySelectorAll('[data-testid^="e2e-your-rides-trip-card-"], article[data-testid^="e2e-your-rides-trip-card-"], article'));
              const fromRoots = roots.map((root) => {
                const selected = candidateHref(root);
                if (!selected) return null;
                const roster = extractPassengers(root);
                return {
                  href: selected.href || '',
                  text: clean(root.innerText).slice(0, 3200),
                  departureTime: first(root, ['[data-testid="e2e-itinerary-departure-time"]', '[data-testid*="departure-time"]']),
                  arrivalTime: first(root, ['[data-testid="e2e-itinerary-arrival-time"]', '[data-testid*="arrival-time"]']),
                  origin: first(root, ['[data-testid="e2e-itinerary-departure-station"]', '[data-testid*="departure-station"]']),
                  destination: first(root, ['[data-testid="e2e-itinerary-arrival-station"]', '[data-testid*="arrival-station"]']),
                  price: first(root, ['[data-testid="e2e-tripcard-price"]', '[data-testid="e2e-tripcard-price-price-value"]', '[data-testid*="price"]']),
                  dateText: dateEvidence(root),
                  passengers: roster.passengers,
                  passengerRosterComplete: roster.passengerRosterComplete
                };
              }).filter(Boolean);
              const fallback = fromRoots.length ? [] : Array.from(document.querySelectorAll('a[href*="/rides/offer"], a[href*="/trip?"], a[href*="/trip/"]'))
                .filter((anchor) => !(anchor.href || '').includes('/rides/offer/passenger/'))
                .map((anchor) => {
                  const root = anchor.closest('article, li, section, div') || anchor.parentElement || document.body;
                  const roster = extractPassengers(root);
                  return {
                    href: anchor.href || '',
                    text: clean(root.innerText).slice(0, 3200),
                    departureTime: first(root, ['[data-testid="e2e-itinerary-departure-time"]', '[data-testid*="departure-time"]']),
                    arrivalTime: first(root, ['[data-testid="e2e-itinerary-arrival-time"]', '[data-testid*="arrival-time"]']),
                    origin: first(root, ['[data-testid="e2e-itinerary-departure-station"]', '[data-testid*="departure-station"]']),
                    destination: first(root, ['[data-testid="e2e-itinerary-arrival-station"]', '[data-testid*="arrival-station"]']),
                    price: first(root, ['[data-testid*="price"]']),
                    dateText: dateEvidence(root),
                    passengers: roster.passengers,
                    passengerRosterComplete: roster.passengerRosterComplete
                  };
                });
              $SANITIZED_HTML_JS
              return JSON.stringify({
                candidates: fromRoots.concat(fallback),
                bodyText: clean(document.body && document.body.innerText).slice(0, 16000),
                domHtml: html.slice(0, 350000)
              });
            })();
        """.trimIndent()

        private val PASSENGER_CONTACT_JS = """
            (function() {
              const clean = (value) => (value || '').replace(/\s+/g, ' ').trim();
              const nodes = Array.from(document.querySelectorAll('[href^="tel:"], a, button, [role="button"]'));
              const candidates = [];
              nodes.forEach((node) => {
                const href = (node.getAttribute && node.getAttribute('href')) || '';
                if (/^tel:/i.test(href)) candidates.push(href);
                const outer = node.outerHTML || '';
                const matches = outer.match(/tel:[+0-9(). \-]{8,32}/ig) || [];
                matches.forEach((value) => candidates.push(value));
              });
              const pageHtml = document.documentElement ? (document.documentElement.outerHTML || '') : '';
              (pageHtml.match(/tel:[+0-9(). \-]{8,32}/ig) || []).forEach((value) => candidates.push(value));
              const rawPhone = candidates.find((value) => /^tel:/i.test(value)) || '';
              const phone = rawPhone
                ? rawPhone.replace(/^tel:/i, '').split('?')[0].replace(/[^+0-9]/g, '')
                : '';
              const nameNode = document.querySelector('[data-testid*="passenger-name"], [data-testid*="profile-name"], h1');
              return JSON.stringify({
                phone: phone,
                visibleName: clean(nameNode && nameNode.innerText)
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
              const structuredDates = Array.from(document.querySelectorAll('time[datetime]'))
                .map((node) => clean(node.getAttribute('datetime')))
                .filter(Boolean);
              const visibleDates = Array.from(document.querySelectorAll('[data-testid*="date"], time, h1, h2, h3'))
                .map((node) => clean(node.innerText))
                .filter(Boolean);
              const dateText = clean(structuredDates.concat(visibleDates).join(' | ')).slice(0, 1600);
              const passengerAnchors = Array.from(document.querySelectorAll('a[href*="/rides/offer/passenger/"]'));
              const passengerMap = new Map();
              passengerAnchors.forEach((anchor) => {
                const href = anchor.href || '';
                if (!href || passengerMap.has(href)) return;
                const root = anchor.closest('li, article, [data-testid*="passenger"], [role="listitem"]') || anchor;
                const raw = (root.innerText || anchor.innerText || '').trim();
                const lines = raw.split(/\n+/).map(clean).filter(Boolean);
                const named = root.querySelector && root.querySelector('[data-testid*="passenger-name"], [data-testid*="profile-name"], img[alt]');
                const alt = named && named.getAttribute ? clean(named.getAttribute('alt')) : '';
                let name = clean(alt || (named && named.innerText) || lines[0] || '').replace(/\s*\(\d+\)\s*$/, '');
                const suffixSource = lines.find((line) => /\(\d+\)\s*$/.test(line)) || name;
                const suffix = suffixSource.match(/\((\d+)\)\s*$/);
                const seats = suffix ? Math.max(1, parseInt(suffix[1], 10) || 1) : 1;
                const route = lines.find((line) => line.includes('→') || line.includes('->')) || '';
                const routeParts = route.split(/→|->/).map(clean);
                const tel = root.querySelector && root.querySelector('a[href^="tel:"]');
                passengerMap.set(href, {
                  name: name,
                  seats: seats,
                  boarding: routeParts.length >= 2 ? routeParts[0] : null,
                  dropoff: routeParts.length >= 2 ? routeParts[routeParts.length - 1] : null,
                  phone: tel ? (tel.getAttribute('href') || '').replace(/^tel:/i, '') : null,
                  booking_href: href
                });
              });
              const passengers = Array.from(passengerMap.values()).filter((item) => item.name);
              const rosterContainers = Array.from(document.querySelectorAll('[data-testid], [aria-label]')).filter((node) => {
                const marker = ((node.getAttribute('data-testid') || '') + ' ' + (node.getAttribute('aria-label') || '')).toLowerCase();
                return marker.includes('passenger') || marker.includes('passageir') || marker.includes('booking') || marker.includes('reserva');
              });
              const hasMore = Array.from(document.querySelectorAll('button, a, [role="button"]')).some((node) => /mostrar mais|ver mais|mais passageir|mais reserva/i.test(clean(node.innerText)));
              const passengerRosterComplete = rosterContainers.length > 0 && !hasMore;
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
                  profileLinks: scopedDriverLinks.length ? scopedDriverLinks : allProfileLinks,
                  passengers: passengers,
                  passengerRosterComplete: passengerRosterComplete
                },
                driverProfileLinks: scopedDriverLinks.length ? scopedDriverLinks : allProfileLinks,
                domHtml: html.slice(0, 350000)
              });
            })();
        """.trimIndent()
    }
}
