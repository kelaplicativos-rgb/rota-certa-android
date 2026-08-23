package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
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
        val previous = read(account)
        val authoritativeComplete = identityVerified && skippedTrips == 0
        val merged = BlaBlaCollectorTimelineModule.mergeSnapshotTrips(
            previous = previous?.trips.orEmpty(),
            current = trips,
            authoritativeComplete = authoritativeComplete,
        )
        val preservedVerifiedIdentity =
            !authoritativeComplete &&
                previous?.identityVerified == true &&
                previous.profileUuid == account.profileUuid
        val effectiveIdentityVerified = identityVerified || preservedVerifiedIdentity
        write(
            account,
            BlaBlaDynamicSessionSnapshot(
                accountId = account.id,
                profileUuid = account.profileUuid,
                profileLabel = account.displayLabel,
                identityVerified = effectiveIdentityVerified,
                lastUrl = lastUrl.take(1000),
                updatedAtMillis = System.currentTimeMillis(),
                trips = merged.trips,
                skippedTrips = skippedTrips,
            ),
        )
        UnifiedDebugEventStore.record(
            "SNAPSHOT_SAVED",
            appContext.packageName,
            "account=${account.displayLabel} expectedUuid=${account.profileUuid.orEmpty()} trips=${merged.trips.size} rosterComplete=${merged.trips.count { it.passenger_roster_complete }} rosterIncomplete=${merged.trips.count { !it.passenger_roster_complete }} preservedIncomplete=${merged.preservedIncompleteRosters} preservedMissing=${merged.preservedMissingTrips} skipped=$skippedTrips identityVerified=$effectiveIdentityVerified currentIdentityVerified=$identityVerified authoritativeComplete=$authoritativeComplete",
        )
    }

    fun combinedResponse(accounts: List<BlaBlaDynamicAccount>): BlaBlaCollectorMonthResponse {
val snapshots = accounts.mapNotNull { account -> read(account)?.let { account to it } }
val verified = snapshots.filter { (account, snapshot) ->
  snapshot.identityVerified && !account.profileUuid.isNullOrBlank() && snapshot.profileUuid == account.profileUuid
}
val beforeDistinct = verified.flatMap { (_, snapshot) -> snapshot.trips }
val resolution = BlaBlaTripIdentity.resolveDistinct(beforeDistinct)
beforeDistinct.forEachIndexed { index, trip ->
  val identity = BlaBlaTripIdentity.evidence(trip)
  UnifiedDebugEventStore.record(
      "TRIP_IDENTITY",
      appContext.packageName,
      "index=${index + 1}/${beforeDistinct.size} tripId=${trip.trip_id.orEmpty()} core=${BlaBlaTripIdentity.physicalCoreKey(trip)} externalTripIdPresent=${identity.externalTripIdPresent} specificHrefPresent=${identity.specificHrefPresent} fallbackIdentityUsed=${identity.fallbackIdentityUsed} identityHash=${identity.identityHash} identityConflict=${identity.identityConflict}",
  )
}
resolution.conflicts.forEach { conflict ->
  UnifiedDebugEventStore.record(
      "TRIP_IDENTITY_CONFLICT",
      appContext.packageName,
      "identityHash=${conflict.identityHash} tripId=${conflict.externalTripId.orEmpty()} physicalCoreCount=${conflict.physicalCores.size} cores=${conflict.physicalCores.joinToString(" || ")} action=preserve_and_flag",
  )
}
val trips = resolution.trips
  .sortedWith(compareBy<BlaBlaCollectorTrip>({ it.date }, { it.departure_time.orEmpty() }))
val identityConflictCount = resolution.conflicts.size
val hasIdentityConflict = identityConflictCount > 0
val skippedCount = snapshots.sumOf { (_, snapshot) -> snapshot.skippedTrips }
val rosterIncompleteCount = trips.count { !it.passenger_roster_complete }
val dataCoveragePartial = rosterIncompleteCount > 0 || skippedCount > 0
val identityStatus = when {
    accounts.isEmpty() -> "empty"
    verified.size == accounts.size -> "validated"
    verified.isEmpty() -> "blocked"
    else -> "partial"
}
val dataCoverage = if (!dataCoveragePartial && !hasIdentityConflict) "complete" else "partial"
val response = BlaBlaCollectorMonthResponse(
  collected_at = Instant.now().toString(),
  status = blaBlaDirectCollectorStatus(
      accountCount = accounts.size,
      verifiedAccountCount = verified.size,
      identityConflictCount = identityConflictCount,
      rosterIncompleteCount = rosterIncompleteCount,
      skippedCount = skippedCount,
  ),
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
      complete_for_scope = blaBlaDirectCoverageComplete(
          accountCount = accounts.size,
          verifiedAccountCount = verified.size,
          identityConflictCount = identityConflictCount,
          rosterIncompleteCount = rosterIncompleteCount,
          skippedCount = skippedCount,
      ),
      global_profile_month_complete = false,
      reason = when {
          hasIdentityConflict -> "Conflito de identidade externa detectado; viagens preservadas para conferência sem descarte silencioso."
          dataCoveragePartial -> "Identidade das contas validada, mas a cobertura dos dados está parcial por roster incompleto ou cartão não resolvido."
          else -> "Contas cadastradas pelo usuário; leitura autenticada local de Suas viagens com UUID confirmado."
      },
      requested_queries = accounts.size,
      validated_queries = verified.size,
      failed_or_mismatched_queries = (accounts.size - verified.size).coerceAtLeast(0),
      unresolved_target_cards = skippedCount,
      identity_conflicts = resolution.conflicts.size,
      past_dates_skipped = false,
  ),
)
UnifiedDebugEventStore.record(
  "COMBINED_RESPONSE",
  appContext.packageName,
  "accounts=${accounts.size} verifiedAccounts=${verified.size} beforeDistinct=${beforeDistinct.size} tripCount=${trips.size} deduped=${resolution.dedupedCount} identityConflicts=$identityConflictCount rosterComplete=${trips.count { it.passenger_roster_complete }} rosterIncomplete=$rosterIncompleteCount skipped=$skippedCount identityStatus=$identityStatus dataCoverage=$dataCoverage status=${response.status}",
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
    val explicitEmptyList: Boolean = false,
    val scrollY: Int = 0,
    val scrollHeight: Int = 0,
    val viewportHeight: Int = 0,
    val atBottom: Boolean = false,
    val domHtml: String = "",
)

@Serializable
private data class DynamicTripDetail(
    val detail: BlaBlaDomTripDetail = BlaBlaDomTripDetail(),
    val driverProfileLinks: List<String> = emptyList(),
    val passengerHrefs: List<String> = emptyList(),
    val explicitEmptyRoster: Boolean = false,
    val rosterHasMore: Boolean = false,
    val rosterTerminalEvidence: Boolean = false,
    val editHref: String = "",
    val itineraryStops: List<String> = emptyList(),
    val views: Int? = null,
    val domHtml: String = "",
)

@Serializable
private data class DynamicPassengerCardOpenState(
    val found: Boolean = false,
    val clicked: Boolean = false,
)

@Serializable
private data class DynamicPassengerContactEvidence(
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
private data class DynamicEditEvidence(
    val optionsHref: String = "",
    val pageUrl: String = "",
    val domHtml: String = "",
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
    private lateinit var passengerIdentityStore: PassengerIdentityStore
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
    private val completedCardTraversalKeys = linkedSetOf<String>()
    private val quarantinedCardTraversalKeys = linkedSetOf<String>()
    private val resolvedCardTraversalKeys = linkedSetOf<String>()
    private var currentCardTraversalKey = ""
    private var ridesResumeScrollY = 0
    private var ridesRestorePending = false
    private var ridesBottomStablePasses = 0
    private var tripRosterReadAttempts = 0
    private var lastTripRosterSignature = ""
    private var tripRosterStablePasses = 0
    private var pendingTripDetail: DynamicTripDetail? = null
    private var pendingTripPassengers = mutableListOf<BlaBlaCollectorPassenger>()
    private val pendingTripPassengerCardIndexes = mutableMapOf<Int, Int>()
    private var passengerContactIndex = 0
    private var passengerContactReadAttempts = 0
    private var passengerCardReadAttempts = 0
    private var passengerCallActionTriggered = false
    private var interceptedPassengerPhone: String? = null
    private var pendingEditHref = ""
    private var pendingOptionsHref = ""
    private var pendingPublishedSeats: Int? = null
    private var editReadAttempts = 0
    private var optionsReadAttempts = 0
    private var syncGeneration = 0L
    private var navigationGeneration = 0L
    private var detailCaptureInFlight = false
    private var passengerCaptureInFlight = false
    private var passengerCardCaptureInFlight = false
    private var editCaptureInFlight = false
    private var optionsCaptureInFlight = false
    private var pendingTripSyncGeneration = -1L
    private var pendingTripCandidateIndex = -1
    private val completionGate = BlaBlaSyncCompletionGate()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = BlaBlaDynamicAccountRegistry(this)
        store = BlaBlaDynamicSessionStore(this)
        passengerIdentityStore = PassengerIdentityStore(this)
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
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString()
                return if (interceptPhoneNavigation(target)) true else super.shouldOverrideUrlLoading(view, request)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return if (interceptPhoneNavigation(url)) true else super.shouldOverrideUrlLoading(view, url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (mode != BlaBlaDynamicSessionIntents.MODE_SYNC) {
                    statusView.text = "${account.displayLabel} • ${url.take(110)}"
                }
                when (phase) {
                    Phase.IDENTITY -> if (isBlaBla(url)) view.postDelayed({ captureIdentityForSync() }, 650)
                    Phase.RIDES -> if (isBlaBla(url)) view.postDelayed({ captureRideList() }, 900)
                    Phase.DETAIL -> if (isBlaBla(url)) scheduleTripDetailCapture(view)
                    Phase.PASSENGER_CARD -> if (isBlaBla(url)) schedulePassengerCardOpen(view)
                    Phase.PASSENGER_CONTACT -> if (isBlaBla(url)) schedulePassengerContactCapture(view)
                    Phase.EDIT -> if (isBlaBla(url)) scheduleEditCapture(view)
                    Phase.OPTIONS -> if (isBlaBla(url)) scheduleOptionsCapture(view)
                    Phase.IDLE -> if (isBlaBla(url)) view.postDelayed({ probeIdentity() }, 500)
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun interceptPhoneNavigation(rawUrl: String?): Boolean {
        val url = rawUrl?.trim().orEmpty()
        if (!url.startsWith("tel:", ignoreCase = true)) return false
        val passenger = pendingTripPassengers.getOrNull(passengerContactIndex)
        val pageUrl = if (::webView.isInitialized) webView.url.orEmpty() else ""
        val phone = normalizeCapturedPhone(url.substringAfter(':').substringBefore('?'))
        if (
            phase == Phase.PASSENGER_CONTACT &&
            passenger != null &&
            passengerPageMatchesExpected(passenger.booking_href.orEmpty(), pageUrl)
        ) {
            interceptedPassengerPhone = phone
            UnifiedDebugEventStore.record(
                "PASSENGER_TEL_INTERCEPTED",
                packageName,
                "account=${account.displayLabel} tripId=${candidates.getOrNull(candidateIndex)?.let { BlaBlaTripIdentity.externalTripIdFromHref(it.href) }.orEmpty()} passengerIndex=${passengerContactIndex + 1}/${pendingTripPassengers.size} phonePresent=${phone != null} externalDialerOpened=false",
            )
        } else {
            recordStale("tel_intercept_without_current_passenger", syncGeneration, candidateIndex)
        }
        return true
    }

    private fun beginSync() {
        syncGeneration++
        navigationGeneration = 0L
        detailCaptureInFlight = false
        passengerCaptureInFlight = false
        passengerCardCaptureInFlight = false
        editCaptureInFlight = false
        optionsCaptureInFlight = false
        pendingTripSyncGeneration = -1L
        pendingTripCandidateIndex = -1
        collected.clear()
        candidates = emptyList()
        candidateIndex = 0
        skipped = 0
        rideReadAttempts = 0
        completedCardTraversalKeys.clear()
        quarantinedCardTraversalKeys.clear()
        resolvedCardTraversalKeys.clear()
        currentCardTraversalKey = ""
        ridesResumeScrollY = 0
        ridesRestorePending = false
        ridesBottomStablePasses = 0
        tripRosterReadAttempts = 0
        lastTripRosterSignature = ""
        tripRosterStablePasses = 0
        identityConfirmedThisSync = false
        pendingTripDetail = null
        pendingTripPassengers.clear()
        pendingTripPassengerCardIndexes.clear()
        passengerContactIndex = 0
        passengerContactReadAttempts = 0
        passengerCardReadAttempts = 0
        passengerCallActionTriggered = false
        interceptedPassengerPhone = null
        pendingEditHref = ""
        pendingOptionsHref = ""
        pendingPublishedSeats = null
        editReadAttempts = 0
        optionsReadAttempts = 0
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

    private fun schedulePassengerCardOpen(view: WebView) {
        val expectedSync = syncGeneration
        val expectedNavigation = navigationGeneration
        val expectedCandidate = candidateIndex
        val expectedPassenger = passengerContactIndex
        view.postDelayed({ openPendingPassengerCard(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger) }, 600)
    }

    private fun schedulePassengerContactCapture(view: WebView) {
        val expectedSync = syncGeneration
        val expectedNavigation = navigationGeneration
        val expectedCandidate = candidateIndex
        val expectedPassenger = passengerContactIndex
        view.postDelayed({ capturePassengerContactAfterNavigation(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger) }, 850)
    }

    private fun scheduleEditCapture(view: WebView) {
        val expectedSync = syncGeneration
        val expectedNavigation = navigationGeneration
        val expectedCandidate = candidateIndex
        view.postDelayed({ captureEditEvidence(expectedSync, expectedNavigation, expectedCandidate) }, 850)
    }

    private fun scheduleOptionsCapture(view: WebView) {
        val expectedSync = syncGeneration
        val expectedNavigation = navigationGeneration
        val expectedCandidate = candidateIndex
        view.postDelayed({ captureOptionsEvidence(expectedSync, expectedNavigation, expectedCandidate) }, 850)
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
        if (ridesRestorePending && ridesResumeScrollY > 0) {
            ridesRestorePending = false
            webView.evaluateJavascript("window.scrollTo(0, ${ridesResumeScrollY.coerceAtLeast(0)}); 'ok';") {
                webView.postDelayed({ captureRideList() }, RIDES_SCROLL_SETTLE_MS)
            }
            return
        }
        evaluate<DynamicRideList>(RIDE_LIST_JS) { result ->
            if (result == null) {
                blockSyncWithoutCurrentCard("rides_dom_unreadable")
                return@evaluate
            }
            store.saveDiagnosticHtml(account, "rides", result.domHtml)
            val visible = result.candidates
                .filter { candidate ->
                    val href = candidate.href
                    isBlaBla(href) && (href.contains("/rides/offer") || href.contains("/trip?") || href.contains("/trip/"))
                }
                .distinctBy { canonicalHref(it.href) }
            UnifiedDebugEventStore.record(
                "RIDES_TRAVERSAL_SCAN",
                packageName,
                "account=${account.displayLabel} visible=${visible.size} resolved=${resolvedCardTraversalKeys.size} completed=${completedCardTraversalKeys.size} quarantined=${quarantinedCardTraversalKeys.size} scrollY=${result.scrollY} scrollHeight=${result.scrollHeight} viewport=${result.viewportHeight} atBottom=${result.atBottom} pastDateFilter=false fixedTripLimit=false",
            )
            if (visible.isEmpty() && rideReadAttempts < MAX_RIDES_EMPTY_READ_ATTEMPTS && !looksLoggedOut(result.bodyText)) {
                rideReadAttempts++
                webView.postDelayed({ captureRideList() }, 1200)
                return@evaluate
            }
            if (visible.isEmpty() && looksLoggedOut(result.bodyText)) {
                blockSyncWithoutCurrentCard("rides_session_logged_out")
                return@evaluate
            }
            if (
                visible.isEmpty() &&
                !BlaBlaCollectorCardModule.emptyListIsAuthoritative(result.explicitEmptyList)
            ) {
                blockSyncWithoutCurrentCard("rides_empty_without_explicit_terminal_evidence")
                return@evaluate
            }
            val nextKey = BlaBlaCollectorCardModule.firstUnresolvedVisibleKey(
                visibleKeysInUiOrder = visible.map(::tripTraversalKey),
                resolvedKeys = resolvedCardTraversalKeys,
            )
            val next = nextKey?.let { key -> visible.firstOrNull { tripTraversalKey(it) == key } }
            if (next != null) {
                rideReadAttempts = 0
                ridesBottomStablePasses = 0
                ridesResumeScrollY = result.scrollY.coerceAtLeast(0)
                currentCardTraversalKey = tripTraversalKey(next)
                candidates = listOf(next)
                candidateIndex = 0
                phase = Phase.DETAIL
                UnifiedDebugEventStore.record(
                    "CARD_TRAVERSAL_START",
                    packageName,
                    "account=${account.displayLabel} order=${resolvedCardTraversalKeys.size + 1} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(next.href).orEmpty()} uiOrder=true dateIgnored=true",
                )
                loadCurrentCandidate()
                return@evaluate
            }
            if (visible.any { tripTraversalKey(it).isBlank() }) {
                blockSyncWithoutCurrentCard("visible_card_without_stable_identity")
                return@evaluate
            }
            if (!result.atBottom) {
                val viewport = result.viewportHeight.coerceAtLeast(600)
                val maxScroll = (result.scrollHeight - 1).coerceAtLeast(0)
                val target = (result.scrollY + maxOf(600, viewport * 3 / 4)).coerceAtMost(maxScroll)
                if (target <= result.scrollY && result.scrollHeight > result.viewportHeight) {
                    blockSyncWithoutCurrentCard("rides_scroll_no_progress")
                    return@evaluate
                }
                ridesResumeScrollY = target
                UnifiedDebugEventStore.record(
                    "RIDES_TRAVERSAL_SCROLL",
                    packageName,
                    "account=${account.displayLabel} from=${result.scrollY} to=$target resolved=${resolvedCardTraversalKeys.size} completed=${completedCardTraversalKeys.size} quarantined=${quarantinedCardTraversalKeys.size}",
                )
                webView.evaluateJavascript("window.scrollTo(0, $target); 'ok';") {
                    webView.postDelayed({ captureRideList() }, RIDES_SCROLL_SETTLE_MS)
                }
                return@evaluate
            }
            if (ridesBottomStablePasses < REQUIRED_STABLE_BOTTOM_PASSES) {
                ridesBottomStablePasses++
                webView.postDelayed({ captureRideList() }, RIDES_BOTTOM_SETTLE_MS)
                return@evaluate
            }
            val verified = identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()
            saveFinalSnapshotOnce(verified)
            if (verified) {
                UnifiedDebugEventStore.record(
                    "RIDES_TRAVERSAL_COMPLETE",
                    packageName,
                    "account=${account.displayLabel} resolvedCards=${resolvedCardTraversalKeys.size} completedCards=${completedCardTraversalKeys.size} quarantinedCards=${quarantinedCardTraversalKeys.size} pastDateFilter=false fixedTripLimit=false",
                )
                completeSync(collected.size)
            } else {
                blockSyncWithoutCurrentCard("identity_not_verified_after_traversal")
            }
        }
    }

    private fun loadCurrentCandidate() {
        val candidate = candidates.getOrNull(candidateIndex)
        if (candidate == null || currentCardTraversalKey.isBlank()) {
            blockSyncWithoutCurrentCard("current_card_missing")
            return
        }
        statusView.text = "${account.displayLabel} • card ${resolvedCardTraversalKeys.size + 1} • lendo completo…"
        UnifiedDebugEventStore.record(
            "TRIP_DETAIL_REQUIRED",
            packageName,
            "account=${account.displayLabel} order=${resolvedCardTraversalKeys.size + 1} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidate.href).orEmpty()} batchShortcut=false",
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

            val candidateTripId = BlaBlaTripIdentity.externalTripIdFromHref(candidate.href)
            val detailTripId = BlaBlaTripIdentity.externalTripIdFromHref(result.detail.url)
            if (candidateTripId.isNullOrBlank() || detailTripId.isNullOrBlank() || candidateTripId != detailTripId) {
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} reason=detail_trip_id_mismatch candidateTripId=${candidateTripId.orEmpty()} detailTripId=${detailTripId.orEmpty()} action=reject_stale_detail",
                )
                advanceCandidate(expectedSync, expectedCandidate)
                return@evaluate
            }
            val rosterSignature = directRosterSignature(result)
            if (rosterSignature == lastTripRosterSignature) {
                tripRosterStablePasses++
            } else {
                lastTripRosterSignature = rosterSignature
                tripRosterStablePasses = 1
            }
            val confirmedRosterComplete = BlaBlaCollectorPassengerModule.rosterCompleteAfterStableProbe(
                passengerCount = result.detail.passengers.size,
                structurallyComplete = result.detail.passengerRosterComplete,
                explicitEmpty = result.explicitEmptyRoster,
                hasMore = result.rosterHasMore,
                terminalEvidence = result.rosterTerminalEvidence,
                stablePasses = tripRosterStablePasses,
            )
            val acceptedResult = if (confirmedRosterComplete && !result.detail.passengerRosterComplete) {
                result.copy(detail = result.detail.copy(passengerRosterComplete = true))
            } else {
                result
            }
            val rosterState = BlaBlaCollectorPassengerModule.rosterState(
                passengerCount = acceptedResult.detail.passengers.size,
                rosterComplete = acceptedResult.detail.passengerRosterComplete,
                explicitEmpty = acceptedResult.explicitEmptyRoster,
            )
            UnifiedDebugEventStore.record(
                "TRIP_ROSTER_PROBE",
                packageName,
                "account=${account.displayLabel} tripId=$candidateTripId attempt=${tripRosterReadAttempts + 1} passengerCards=${acceptedResult.detail.passengers.size} bookingLinks=${acceptedResult.passengerHrefs.count { !it.startsWith(CARD_TARGET_PREFIX) }} structuralComplete=${result.detail.passengerRosterComplete} rosterComplete=${acceptedResult.detail.passengerRosterComplete} explicitEmpty=${acceptedResult.explicitEmptyRoster} hasMore=${acceptedResult.rosterHasMore} terminalEvidence=${acceptedResult.rosterTerminalEvidence} stablePasses=$tripRosterStablePasses state=$rosterState",
            )
            if (rosterState == BlaBlaDirectRosterState.UNKNOWN) {
                if (tripRosterReadAttempts < MAX_TRIP_ROSTER_READ_ATTEMPTS) {
                    tripRosterReadAttempts++
                    statusView.text = "${account.displayLabel} • confirmando passageiros ${tripRosterReadAttempts + 1}/$MAX_TRIP_ROSTER_READ_ATTEMPTS…"
                    val retryRoster = {
                        webView.postDelayed({
                            captureTripDetail(expectedSync, expectedNavigation, expectedCandidate)
                        }, ROSTER_RETRY_MS)
                    }
                    if (acceptedResult.rosterHasMore) {
                        webView.evaluateJavascript(EXPAND_ROSTER_JS) { retryRoster() }
                    } else {
                        retryRoster()
                    }
                    return@evaluate
                }
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} tripId=$candidateTripId reason=roster_unknown_after_probe attempts=${tripRosterReadAttempts + 1} action=skip_fail_closed",
                )
                advanceCandidate(expectedSync, expectedCandidate)
                return@evaluate
            }
            val editLinkMatches = BlaBlaHarvestAssociation.editPageMatches(candidateTripId, acceptedResult.editHref)
            if (!editLinkMatches && tripRosterReadAttempts < MAX_TRIP_ROSTER_READ_ATTEMPTS) {
                tripRosterReadAttempts++
                statusView.text = "${account.displayLabel} • vinculando edição ${tripRosterReadAttempts + 1}/$MAX_TRIP_ROSTER_READ_ATTEMPTS…"
                webView.postDelayed({
                    captureTripDetail(expectedSync, expectedNavigation, expectedCandidate)
                }, ROSTER_RETRY_MS)
                return@evaluate
            }
            if (!editLinkMatches) {
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} tripId=$candidateTripId reason=edit_link_missing_or_mismatch attempts=${tripRosterReadAttempts + 1} action=quarantine_and_continue",
                )
                advanceCandidate(expectedSync, expectedCandidate)
                return@evaluate
            }
            tripRosterReadAttempts = 0
            store.saveDiagnosticHtml(account, "card-${resolvedCardTraversalKeys.size + 1}-trip", acceptedResult.domHtml)
            val driverUuids = uuids(acceptedResult.driverProfileLinks)
            val expectedUuid = account.profileUuid?.lowercase()
            UnifiedDebugEventStore.record(
                "TRIP_DETAIL_CAPTURED",
                packageName,
                "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} expectedUuid=${expectedUuid.orEmpty()} foundUuids=${driverUuids.joinToString(",")} passengers=${acceptedResult.detail.passengers.size} rosterComplete=${acceptedResult.detail.passengerRosterComplete} editLinkPresent=${acceptedResult.editHref.isNotBlank()} url=${sanitizedUrl(webView.url.orEmpty())}",
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
                    val updated = registry.bindIdentity(account.id, driverUuids.single(), acceptedResult.detail.driverName)
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
                    detail = acceptedResult.detail,
                    today = LocalDate.now(),
                    authenticatedProfileSessionVerified = identityConfirmedThisSync,
                )
            }
            pendingTripDetail = acceptedResult
            pendingTripPassengers = (preview?.passengers ?: acceptedResult.detail.passengers).toMutableList()
            pendingTripPassengerCardIndexes.clear()
            pendingTripPassengers.indices.forEach { rowIndex ->
                pendingTripPassengerCardIndexes[rowIndex] = rowIndex
            }
            pendingTripSyncGeneration = expectedSync
            pendingTripCandidateIndex = expectedCandidate
            passengerContactIndex = 0
            passengerContactReadAttempts = 0
            passengerCardReadAttempts = 0
            loadNextPassengerContact(expectedSync, expectedCandidate)
        }
    }

    private fun detailCaptureIsCurrent(expectedSync: Long, expectedNavigation: Long, expectedCandidate: Int): Boolean {
        if (phase != Phase.DETAIL || expectedCandidate !in candidates.indices) return false
        val expectedTripId = BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href)
        val currentTripId = BlaBlaTripIdentity.externalTripIdFromHref(webView.url.orEmpty())
        return blaBlaDirectCallbackMatches(
            expectedSyncGeneration = expectedSync,
            expectedNavigationGeneration = expectedNavigation,
            expectedCandidateIndex = expectedCandidate,
            expectedTripId = expectedTripId,
            currentSyncGeneration = syncGeneration,
            currentNavigationGeneration = navigationGeneration,
            currentCandidateIndex = candidateIndex,
            currentTripId = currentTripId,
        )
    }

    private fun passengerNeedsReservationPage(passenger: BlaBlaCollectorPassenger): Boolean {
        val href = passenger.booking_href?.trim().orEmpty()
        if (href.isBlank() || !isBlaBla(href)) return false
        val metadataKey = externalPassengerReservationKey(account.profileUuid, href)
        val metadata = passengerIdentityStore.externalMetadata(metadataKey)
        return passenger.phone.isNullOrBlank() || metadata?.fareMinorUnits == null || metadata?.boardingAddress.isNullOrBlank()
    }

    private fun loadNextPassengerContact(expectedSync: Long, expectedCandidate: Int) {
        if (!pendingTripIsCurrent(expectedSync, expectedCandidate)) {
            recordStale("passenger_load_pending_mismatch", expectedSync, expectedCandidate)
            return
        }
        while (passengerContactIndex < pendingTripPassengers.size) {
            val passenger = pendingTripPassengers[passengerContactIndex]
            val href = passenger.booking_href?.trim().orEmpty()
            val hasBookingHref = href.isNotBlank() && isPassengerHref(href)
            val cardIndex = pendingTripPassengerCardIndexes[passengerContactIndex]
            when (
                BlaBlaCollectorPassengerModule.nextStep(
                    passengerPresent = true,
                    hasBookingHref = hasBookingHref,
                    needsReservationPage = hasBookingHref && passengerNeedsReservationPage(passenger),
                    hasPassengerCard = cardIndex != null,
                )
            ) {
                BlaBlaDirectPassengerStep.RESERVATION_URL -> {
                    phase = Phase.PASSENGER_CONTACT
                    passengerContactReadAttempts = 0
                    passengerCallActionTriggered = false
                    interceptedPassengerPhone = null
                    passengerCaptureInFlight = false
                    statusView.text = "${account.displayLabel} • reserva ${passengerContactIndex + 1}/${pendingTripPassengers.size}…"
                    loadTrackedUrl(href)
                    return
                }
                BlaBlaDirectPassengerStep.PASSENGER_CARD -> {
                    phase = Phase.PASSENGER_CARD
                    passengerContactReadAttempts = 0
                    passengerCardReadAttempts = 0
                    passengerCallActionTriggered = false
                    interceptedPassengerPhone = null
                    passengerCardCaptureInFlight = false
                    statusView.text = "${account.displayLabel} • abrindo passageiro ${passengerContactIndex + 1}/${pendingTripPassengers.size}…"
                    if (currentTripMatchesCandidate(expectedCandidate)) {
                        val expectedNavigation = navigationGeneration
                        webView.postDelayed({
                            openPendingPassengerCard(expectedSync, expectedNavigation, expectedCandidate, passengerContactIndex)
                        }, 350)
                    } else {
                        loadTrackedUrl(candidates[expectedCandidate].href)
                    }
                    return
                }
                BlaBlaDirectPassengerStep.SKIP -> {
                    UnifiedDebugEventStore.record(
                        "PASSENGER_EVIDENCE_INCOMPLETE",
                        packageName,
                        "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} passengerIndex=${passengerContactIndex + 1}/${pendingTripPassengers.size} reason=no_individual_target action=block_card",
                    )
                    skipped++
                    blockCurrentCard(expectedSync, expectedCandidate, "passenger_individual_target_missing")
                    return
                }
                BlaBlaDirectPassengerStep.FINISH -> break
            }
        }
        loadCurrentTripEdit(expectedSync, expectedCandidate)
    }

    private fun openPendingPassengerCard(
        expectedSync: Long,
        expectedNavigation: Long,
        expectedCandidate: Int,
        expectedPassenger: Int,
    ) {
        if (!passengerCardCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)) {
            recordStale("passenger_card_before_evaluate", expectedSync, expectedCandidate)
            return
        }
        if (passengerCardCaptureInFlight) {
            recordStale("passenger_card_in_flight", expectedSync, expectedCandidate)
            return
        }
        val cardIndex = pendingTripPassengerCardIndexes[expectedPassenger] ?: run {
            recordStale("passenger_card_index_missing", expectedSync, expectedCandidate)
            return
        }
        passengerCardCaptureInFlight = true
        evaluate<DynamicPassengerCardOpenState>(passengerCardOpenJs(cardIndex)) { state ->
            passengerCardCaptureInFlight = false
            if (
                phase != Phase.PASSENGER_CARD ||
                expectedSync != syncGeneration ||
                expectedNavigation != navigationGeneration ||
                expectedCandidate != candidateIndex ||
                expectedPassenger != passengerContactIndex ||
                !pendingTripIsCurrent(expectedSync, expectedCandidate)
            ) {
                recordStale("passenger_card_after_evaluate", expectedSync, expectedCandidate)
                return@evaluate
            }
            if (state?.clicked == true) {
                passengerCardReadAttempts = 0
                navigationGeneration++
                val passengerNavigation = navigationGeneration
                phase = Phase.PASSENGER_CONTACT
                statusView.text = "${account.displayLabel} • reserva ${expectedPassenger + 1}/${pendingTripPassengers.size}…"
                webView.postDelayed({
                    capturePassengerContactAfterNavigation(
                        expectedSync,
                        passengerNavigation,
                        expectedCandidate,
                        expectedPassenger,
                    )
                }, PASSENGER_NAVIGATION_SETTLE_MS)
                return@evaluate
            }
            if (passengerCardReadAttempts < MAX_PASSENGER_CARD_READ_ATTEMPTS) {
                passengerCardReadAttempts++
                webView.postDelayed({
                    openPendingPassengerCard(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)
                }, ROSTER_RETRY_MS)
                return@evaluate
            }
            UnifiedDebugEventStore.record(
                "PASSENGER_CONTACT_SKIPPED",
                packageName,
                "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} passengerIndex=${expectedPassenger + 1}/${pendingTripPassengers.size} reason=card_not_clickable attempts=${passengerCardReadAttempts + 1}",
            )
            skipped++
            passengerCardReadAttempts = 0
            blockCurrentCard(expectedSync, expectedCandidate, "passenger_card_not_clickable")
        }
    }

    private fun passengerCardCaptureIsCurrent(
        expectedSync: Long,
        expectedNavigation: Long,
        expectedCandidate: Int,
        expectedPassenger: Int,
    ): Boolean =
        phase == Phase.PASSENGER_CARD &&
            expectedSync == syncGeneration &&
            expectedNavigation == navigationGeneration &&
            expectedCandidate == candidateIndex &&
            expectedPassenger == passengerContactIndex &&
            pendingTripIsCurrent(expectedSync, expectedCandidate) &&
            currentTripMatchesCandidate(expectedCandidate)

    private fun capturePassengerContactAfterNavigation(
        expectedSync: Long,
        expectedNavigation: Long,
        expectedCandidate: Int,
        expectedPassenger: Int,
    ) {
        if (
            phase != Phase.PASSENGER_CONTACT ||
            expectedSync != syncGeneration ||
            expectedNavigation != navigationGeneration ||
            expectedCandidate != candidateIndex ||
            expectedPassenger != passengerContactIndex ||
            !pendingTripIsCurrent(expectedSync, expectedCandidate)
        ) {
            recordStale("passenger_bind_before_capture", expectedSync, expectedCandidate)
            return
        }
        if (!bindPendingPassengerTarget(expectedCandidate, expectedPassenger)) {
            if (passengerContactReadAttempts < MAX_PASSENGER_BIND_READ_ATTEMPTS) {
                passengerContactReadAttempts++
                webView.postDelayed({
                    capturePassengerContactAfterNavigation(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)
                }, ROSTER_RETRY_MS)
                return
            }
            UnifiedDebugEventStore.record(
                "PASSENGER_CONTACT_SKIPPED",
                packageName,
                "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} passengerIndex=${expectedPassenger + 1}/${pendingTripPassengers.size} reason=passenger_page_identity_unproven attempts=${passengerContactReadAttempts + 1}",
            )
            skipped++
            passengerContactReadAttempts = 0
            blockCurrentCard(expectedSync, expectedCandidate, "passenger_page_identity_unproven")
            return
        }
        passengerContactReadAttempts = 0
        capturePassengerContact(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)
    }

    private fun bindPendingPassengerTarget(expectedCandidate: Int, expectedPassenger: Int): Boolean {
        val current = pendingTripPassengers.getOrNull(expectedPassenger) ?: return false
        val existingHref = current.booking_href?.trim().orEmpty()
        if (existingHref.isNotBlank()) return passengerPageMatchesExpected(existingHref, webView.url.orEmpty())
        val cardIndex = pendingTripPassengerCardIndexes[expectedPassenger] ?: return false
        val actualHref = webView.url.orEmpty().takeIf(::isPassengerHref) ?: return false
        val actualKey = passengerPageKey(actualHref)
        val duplicate = pendingTripPassengers.withIndex().any { (index, other) ->
            index != expectedPassenger &&
                !other.booking_href.isNullOrBlank() &&
                passengerPageKey(other.booking_href.orEmpty()) == actualKey
        }
        if (duplicate) {
            UnifiedDebugEventStore.record(
                "PASSENGER_TARGET_REJECTED",
                packageName,
                "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} passengerIndex=${expectedPassenger + 1}/${pendingTripPassengers.size} reason=duplicate_passenger_page action=fail_closed",
            )
            return false
        }
        pendingTripPassengers[expectedPassenger] = current.copy(booking_href = actualHref)
        pendingTripPassengerCardIndexes.remove(expectedPassenger)
        UnifiedDebugEventStore.record(
            "PASSENGER_TARGET_BOUND",
            packageName,
            "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} passengerIndex=${expectedPassenger + 1}/${pendingTripPassengers.size} cardIndex=$cardIndex hrefPresent=true",
        )
        return true
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
        if (passengerCaptureInFlight) return
        val current = pendingTripPassengers.getOrNull(expectedPassenger) ?: run {
            skipped++
            blockCurrentCard(expectedSync, expectedCandidate, "passenger_missing")
            return
        }
        passengerCaptureInFlight = true
        evaluate<DynamicPassengerContactEvidence>(PASSENGER_CONTACT_JS) { evidence ->
            passengerCaptureInFlight = false
            if (!passengerCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)) {
                recordStale("passenger_after_evaluate", expectedSync, expectedCandidate)
                return@evaluate
            }
            if (evidence == null) {
                if (passengerContactReadAttempts < MAX_PASSENGER_EVIDENCE_READ_ATTEMPTS) {
                    passengerContactReadAttempts++
                    webView.postDelayed({ capturePassengerContact(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger) }, ROSTER_RETRY_MS)
                    return@evaluate
                }
                skipped++
                blockCurrentCard(expectedSync, expectedCandidate, "passenger_evidence_unreadable")
                return@evaluate
            }
            evidence.domHtml.takeIf(String::isNotBlank)?.let { html ->
                store.saveDiagnosticHtml(account, "card-${resolvedCardTraversalKeys.size + 1}-passenger-${expectedPassenger + 1}", html)
            }
            val effectivePhone = current.phone?.takeIf(String::isNotBlank)
                ?: normalizeCapturedPhone(evidence.phone)
                ?: interceptedPassengerPhone
            if (effectivePhone == null && evidence.callActionPresent && !passengerCallActionTriggered) {
                passengerCallActionTriggered = true
                UnifiedDebugEventStore.record(
                    "PASSENGER_CALL_ACTION_PRESENT",
                    packageName,
                    "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} passengerIndex=${expectedPassenger + 1}/${pendingTripPassengers.size} actionPresent=true clickIntercepted=true",
                )
                webView.evaluateJavascript(CLICK_CALL_ACTION_JS) {
                    if (passengerCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)) {
                        webView.postDelayed({
                            capturePassengerContact(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)
                        }, PASSENGER_CALL_SETTLE_MS)
                    }
                }
                return@evaluate
            }
            saveCapturedPassengerFare(current.booking_href, evidence)
            saveCapturedPassengerBoardingEvidence(current.booking_href, evidence)
            val metadata = passengerIdentityStore.externalMetadata(externalPassengerReservationKey(account.profileUuid, current.booking_href))
            val farePresent = metadata?.fareMinorUnits != null
            val routePresent = !current.boarding.isNullOrBlank() && !current.dropoff.isNullOrBlank()
            val resolvedName = current.name.ifBlank { evidence.visibleName.trim() }
            val htmlPresent = evidence.domHtml.isNotBlank()
            val valueEvidence = BlaBlaPassengerValueEvidence(
                namePresent = resolvedName.isNotBlank(),
                routePresent = routePresent,
                farePresent = farePresent,
                htmlPresent = htmlPresent,
            )
            val requiredComplete = BlaBlaCollectorValueModule.complete(valueEvidence)
            if (!requiredComplete && passengerContactReadAttempts < MAX_PASSENGER_EVIDENCE_READ_ATTEMPTS) {
                passengerContactReadAttempts++
                webView.postDelayed({ capturePassengerContact(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger) }, ROSTER_RETRY_MS)
                return@evaluate
            }
            if (!requiredComplete) {
                UnifiedDebugEventStore.record(
                    "PASSENGER_EVIDENCE_INCOMPLETE",
                    packageName,
                    "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} passengerIndex=${expectedPassenger + 1}/${pendingTripPassengers.size} namePresent=${resolvedName.isNotBlank()} routePresent=$routePresent farePresent=$farePresent htmlPresent=$htmlPresent missing=${BlaBlaCollectorValueModule.missing(valueEvidence).joinToString(",")} action=block_card",
                )
                skipped++
                blockCurrentCard(expectedSync, expectedCandidate, "passenger_required_evidence_incomplete")
                return@evaluate
            }
            pendingTripPassengers[expectedPassenger] = current.copy(name = resolvedName, phone = effectivePhone)
            val metadataAfter = passengerIdentityStore.externalMetadata(externalPassengerReservationKey(account.profileUuid, current.booking_href))
            UnifiedDebugEventStore.record(
                "PASSENGER_CONTACT_CAPTURED",
                packageName,
                "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} passengerIndex=${expectedPassenger + 1}/${pendingTripPassengers.size} phonePresent=${effectivePhone != null} farePresent=${metadataAfter?.fareMinorUnits != null} routePresent=$routePresent addressPresent=${!metadataAfter?.boardingAddress.isNullOrBlank()} coordinatePresent=${metadataAfter?.hasBoardingCoordinates == true} bookingLinkPresent=${!current.booking_href.isNullOrBlank()} htmlCaptured=$htmlPresent sequential=true",
            )
            passengerContactIndex = expectedPassenger + 1
            passengerContactReadAttempts = 0
            passengerCallActionTriggered = false
            interceptedPassengerPhone = null
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
        expectedPassenger in pendingTripPassengers.indices &&
        passengerPageMatchesExpected(
            pendingTripPassengers[expectedPassenger].booking_href.orEmpty(),
            webView.url.orEmpty(),
        )

    private fun pendingTripIsCurrent(expectedSync: Long, expectedCandidate: Int): Boolean =
        expectedSync == syncGeneration &&
            expectedCandidate == candidateIndex &&
            pendingTripSyncGeneration == expectedSync &&
            pendingTripCandidateIndex == expectedCandidate &&
            pendingTripDetail != null

    private fun loadCurrentTripEdit(expectedSync: Long, expectedCandidate: Int) {
        if (!pendingTripIsCurrent(expectedSync, expectedCandidate)) {
            recordStale("edit_load_pending_mismatch", expectedSync, expectedCandidate)
            return
        }
        val tripId = candidates.getOrNull(expectedCandidate)
            ?.let { BlaBlaTripIdentity.externalTripIdFromHref(it.href) }
        val editHref = pendingTripDetail?.editHref?.trim().orEmpty()
        if (tripId.isNullOrBlank() || !BlaBlaHarvestAssociation.editPageMatches(tripId, editHref)) {
            skipped++
            blockCurrentCard(expectedSync, expectedCandidate, "edit_link_missing_or_mismatch")
            return
        }
        pendingEditHref = editHref
        pendingOptionsHref = ""
        pendingPublishedSeats = null
        editReadAttempts = 0
        editCaptureInFlight = false
        phase = Phase.EDIT
        statusView.text = "${account.displayLabel} • edição do card ${resolvedCardTraversalKeys.size + 1}…"
        UnifiedDebugEventStore.record(
            "DIRECT_EDIT_REQUIRED",
            packageName,
            "account=${account.displayLabel} tripId=$tripId editLinkPresent=true sequential=true",
        )
        loadTrackedUrl(editHref)
    }

    private fun captureEditEvidence(expectedSync: Long, expectedNavigation: Long, expectedCandidate: Int) {
        if (
            phase != Phase.EDIT ||
            expectedSync != syncGeneration ||
            expectedNavigation != navigationGeneration ||
            expectedCandidate != candidateIndex ||
            !pendingTripIsCurrent(expectedSync, expectedCandidate)
        ) {
            recordStale("edit_before_evaluate", expectedSync, expectedCandidate)
            return
        }
        val tripId = candidates.getOrNull(expectedCandidate)
            ?.let { BlaBlaTripIdentity.externalTripIdFromHref(it.href) }
        if (tripId.isNullOrBlank() || !BlaBlaHarvestAssociation.editPageMatches(tripId, webView.url.orEmpty())) {
            skipped++
            blockCurrentCard(expectedSync, expectedCandidate, "edit_page_identity_mismatch")
            return
        }
        if (editCaptureInFlight) return
        editCaptureInFlight = true
        evaluate<DynamicEditEvidence>(DIRECT_EDIT_EVIDENCE_JS) { evidence ->
            editCaptureInFlight = false
            if (
                phase != Phase.EDIT ||
                expectedSync != syncGeneration ||
                expectedNavigation != navigationGeneration ||
                expectedCandidate != candidateIndex ||
                !pendingTripIsCurrent(expectedSync, expectedCandidate)
            ) {
                recordStale("edit_after_evaluate", expectedSync, expectedCandidate)
                return@evaluate
            }
            val pageMatches = evidence != null && BlaBlaHarvestAssociation.editPageMatches(tripId, evidence.pageUrl)
            val optionsHref = evidence?.optionsHref?.trim().orEmpty()
            val optionsMatch = optionsHref.isNotBlank() && BlaBlaHarvestAssociation.optionsPageMatches(tripId, optionsHref)
            if ((!pageMatches || !optionsMatch) && editReadAttempts < MAX_EDIT_LINK_READ_ATTEMPTS) {
                editReadAttempts++
                webView.postDelayed({
                    captureEditEvidence(expectedSync, expectedNavigation, expectedCandidate)
                }, ROSTER_RETRY_MS)
                return@evaluate
            }
            if (!pageMatches || !optionsMatch || evidence == null) {
                skipped++
                blockCurrentCard(expectedSync, expectedCandidate, "options_link_missing_or_mismatch")
                return@evaluate
            }
            store.saveDiagnosticHtml(account, "card-${resolvedCardTraversalKeys.size + 1}-edit", evidence.domHtml)
            pendingOptionsHref = optionsHref
            editReadAttempts = 0
            optionsReadAttempts = 0
            optionsCaptureInFlight = false
            UnifiedDebugEventStore.record(
                "DIRECT_EDIT_CAPTURED",
                packageName,
                "account=${account.displayLabel} tripId=$tripId optionsLinkPresent=true htmlCaptured=${evidence.domHtml.isNotBlank()} identityMatch=true sequential=true",
            )
            phase = Phase.OPTIONS
            statusView.text = "${account.displayLabel} • vagas do card ${resolvedCardTraversalKeys.size + 1}…"
            loadTrackedUrl(optionsHref)
        }
    }

    private fun captureOptionsEvidence(expectedSync: Long, expectedNavigation: Long, expectedCandidate: Int) {
        if (
            phase != Phase.OPTIONS ||
            expectedSync != syncGeneration ||
            expectedNavigation != navigationGeneration ||
            expectedCandidate != candidateIndex ||
            !pendingTripIsCurrent(expectedSync, expectedCandidate)
        ) {
            recordStale("options_before_evaluate", expectedSync, expectedCandidate)
            return
        }
        val tripId = candidates.getOrNull(expectedCandidate)
            ?.let { BlaBlaTripIdentity.externalTripIdFromHref(it.href) }
        if (tripId.isNullOrBlank() || !BlaBlaHarvestAssociation.optionsPageMatches(tripId, webView.url.orEmpty())) {
            skipped++
            blockCurrentCard(expectedSync, expectedCandidate, "options_page_identity_mismatch")
            return
        }
        if (optionsCaptureInFlight) return
        optionsCaptureInFlight = true
        evaluate<SeatOptionState>(SEAT_OPTIONS_READ_JS) { evidence ->
            optionsCaptureInFlight = false
            if (
                phase != Phase.OPTIONS ||
                expectedSync != syncGeneration ||
                expectedNavigation != navigationGeneration ||
                expectedCandidate != candidateIndex ||
                !pendingTripIsCurrent(expectedSync, expectedCandidate)
            ) {
                recordStale("options_after_evaluate", expectedSync, expectedCandidate)
                return@evaluate
            }
            val identityMatch = evidence != null && BlaBlaHarvestAssociation.optionsPageMatches(tripId, evidence.pageUrl)
            if ((evidence == null || evidence.seats < 0 || !identityMatch) && optionsReadAttempts < MAX_OPTIONS_READ_ATTEMPTS) {
                optionsReadAttempts++
                webView.postDelayed({
                    captureOptionsEvidence(expectedSync, expectedNavigation, expectedCandidate)
                }, ROSTER_RETRY_MS)
                return@evaluate
            }
            if (evidence == null || evidence.seats < 0 || !identityMatch) {
                skipped++
                blockCurrentCard(expectedSync, expectedCandidate, "published_seats_unreadable_or_mismatched")
                return@evaluate
            }
            pendingPublishedSeats = evidence.seats
            store.saveDiagnosticHtml(account, "card-${resolvedCardTraversalKeys.size + 1}-options", evidence.domHtml)
            UnifiedDebugEventStore.record(
                "DIRECT_SEAT_OPTIONS_CAPTURED",
                packageName,
                "account=${account.displayLabel} tripId=$tripId publishedSeats=${evidence.seats} canAdd=${evidence.canAdd} canRemove=${evidence.canRemove} savePresent=${evidence.savePresent} htmlCaptured=${evidence.domHtml.isNotBlank()} identityMatch=true sequential=true",
            )
            finalizeCurrentTrip(expectedSync, expectedCandidate)
        }
    }

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
            blockCurrentCard(expectedSync, expectedCandidate, "pending_trip_state_missing")
            return
        }
        val candidateTripId = BlaBlaTripIdentity.externalTripIdFromHref(candidate.href)
        val seatState = BlaBlaCollectorSeatModule.state(
            tripId = candidateTripId,
            editHref = pendingEditHref,
            optionsHref = pendingOptionsHref,
            publishedSeats = pendingPublishedSeats,
        )
        if (!BlaBlaCollectorSeatModule.complete(seatState)) {
            skipped++
            blockCurrentCard(expectedSync, expectedCandidate, "seat_module_incomplete_or_mismatched")
            return
        }
        val rosterState = BlaBlaCollectorPassengerModule.rosterState(
            passengerCount = pendingTripPassengers.size,
            rosterComplete = result.detail.passengerRosterComplete,
            explicitEmpty = result.explicitEmptyRoster,
        )
        if (rosterState == BlaBlaDirectRosterState.UNKNOWN) {
            skipped++
            blockCurrentCard(expectedSync, expectedCandidate, "finalize_unknown_roster")
            return
        }
        val enrichedDetail = result.detail.copy(passengers = pendingTripPassengers.toList())
        val detailTripId = BlaBlaTripIdentity.externalTripIdFromHref(enrichedDetail.url)
        if (candidateTripId == null || detailTripId == null || candidateTripId != detailTripId) {
            skipped++
            blockCurrentCard(expectedSync, expectedCandidate, "detail_trip_id_mismatch")
            return
        }
        val trip = BlaBlaDomNormalizer.toTrip(
            account = definition,
            candidate = candidate,
            detail = enrichedDetail,
            today = LocalDate.now(),
            authenticatedProfileSessionVerified = identityConfirmedThisSync,
        )
        if (trip == null || !identityConfirmedThisSync) {
            skipped++
            blockCurrentCard(expectedSync, expectedCandidate, "trip_fields_unparseable")
            return
        }
        collected += trip
        UnifiedDebugEventStore.record(
            "TRIP_ACCEPTED",
            packageName,
            "account=${account.displayLabel} order=${resolvedCardTraversalKeys.size + 1} tripId=${trip.trip_id.orEmpty()} date=${trip.date} passengers=${trip.passengers.size} rosterComplete=${trip.passenger_roster_complete} publishedSeats=${pendingPublishedSeats ?: -1} sequential=true",
        )
        completeCurrentCard(expectedSync, expectedCandidate)
    }

    private fun advanceCandidate(expectedSync: Long, expectedCandidate: Int) {
        blockCurrentCard(expectedSync, expectedCandidate, "previous_trip_rejection")
    }

    private fun completeCurrentCard(expectedSync: Long, expectedCandidate: Int) {
        if (!pendingTripIsCurrent(expectedSync, expectedCandidate) || currentCardTraversalKey.isBlank()) {
            blockCurrentCard(expectedSync, expectedCandidate, "completion_context_invalid")
            return
        }
        val tripId = candidates.getOrNull(expectedCandidate)?.let { BlaBlaTripIdentity.externalTripIdFromHref(it.href) }.orEmpty()
        persistDirectTripEvidence(tripId)
        completedCardTraversalKeys += currentCardTraversalKey
        resolvedCardTraversalKeys += currentCardTraversalKey
        UnifiedDebugEventStore.record(
            "CARD_TRAVERSAL_COMPLETE",
            packageName,
            "account=${account.displayLabel} order=${resolvedCardTraversalKeys.size} tripId=$tripId passengers=${pendingTripPassengers.size} publishedSeats=${pendingPublishedSeats ?: -1} result=complete nextCardAllowed=true",
        )
        saveProgressSnapshot("card_complete")
        clearPendingCardState()
        currentCardTraversalKey = ""
        candidates = emptyList()
        candidateIndex = 0
        phase = Phase.RIDES
        ridesRestorePending = ridesResumeScrollY > 0
        loadTrackedUrl(RIDES_URL)
    }

    private fun persistDirectTripEvidence(tripId: String) {
        if (tripId.isBlank()) return
        val detail = pendingTripDetail ?: return
        val evidenceStore = BlaBlaHarvestEvidenceStore(this)
        val existing = evidenceStore.read(account.id)
        val prior = existing.firstOrNull { it.tripId == tripId }
        val evidence = BlaBlaHarvestTripEvidence(
            tripId = tripId,
            publishedSeats = pendingPublishedSeats ?: prior?.publishedSeats,
            views = detail.views ?: prior?.views,
            itineraryStops = detail.itineraryStops.ifEmpty { prior?.itineraryStops.orEmpty() },
            passengers = pendingTripPassengers.toList(),
            passengerRosterComplete = detail.detail.passengerRosterComplete || detail.explicitEmptyRoster,
        )
        evidenceStore.replace(account.id, existing.filterNot { it.tripId == tripId } + evidence)
        UnifiedDebugEventStore.record(
            "DIRECT_TRIP_EVIDENCE_PERSISTED",
            packageName,
            "account=${account.displayLabel} tripId=$tripId stops=${evidence.itineraryStops.size} viewsPresent=${evidence.views != null} passengers=${evidence.passengers.size} rosterComplete=${evidence.passengerRosterComplete} publishedSeats=${evidence.publishedSeats ?: -1}",
        )
    }

    private fun blockCurrentCard(expectedSync: Long, expectedCandidate: Int, reason: String) {
        if (expectedSync != syncGeneration || expectedCandidate != candidateIndex) {
            recordStale("block_card_mismatch_$reason", expectedSync, expectedCandidate)
            return
        }
        val tripId = candidates.getOrNull(expectedCandidate)?.let { BlaBlaTripIdentity.externalTripIdFromHref(it.href) }.orEmpty()
        if (currentCardTraversalKey.isBlank()) {
            blockSyncWithoutCurrentCard("card_without_traversal_key_$reason")
            return
        }
        quarantinedCardTraversalKeys += currentCardTraversalKey
        resolvedCardTraversalKeys += currentCardTraversalKey
        UnifiedDebugEventStore.record(
            "CARD_TRAVERSAL_QUARANTINED",
            packageName,
            "account=${account.displayLabel} order=${resolvedCardTraversalKeys.size} tripId=$tripId reason=$reason completedCards=${completedCardTraversalKeys.size} quarantinedCards=${quarantinedCardTraversalKeys.size} published=false nextCardAllowed=${BlaBlaCollectorCardModule.canAdvance(currentCardComplete = false, currentCardQuarantined = true)}",
        )
        saveProgressSnapshot("card_quarantined_$reason")
        clearPendingCardState()
        currentCardTraversalKey = ""
        candidates = emptyList()
        candidateIndex = 0
        phase = Phase.RIDES
        ridesRestorePending = ridesResumeScrollY > 0
        statusView.text = "${account.displayLabel} • card isolado ⚠️ • continuando…"
        loadTrackedUrl(RIDES_URL)
    }

    private fun blockSyncWithoutCurrentCard(reason: String) {
        skipped = maxOf(skipped, 1)
        UnifiedDebugEventStore.record(
            "SYNC_BLOCKED",
            packageName,
            "account=${account.displayLabel} reason=$reason resolvedCards=${resolvedCardTraversalKeys.size} completedCards=${completedCardTraversalKeys.size} quarantinedCards=${quarantinedCardTraversalKeys.size} nextCardAllowed=false",
        )
        saveFinalSnapshotOnce(identityConfirmedThisSync && !account.profileUuid.isNullOrBlank())
        phase = Phase.IDLE
    }

    private fun saveProgressSnapshot(reason: String) {
        val verified = identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()
        store.saveSync(
            account = account,
            lastUrl = webView.url.orEmpty(),
            trips = collected.toList(),
            skippedTrips = maxOf(skipped, 1),
            identityVerified = verified,
        )
        UnifiedDebugEventStore.record(
            "TIMELINE_CARD_CHECKPOINT_SAVED",
            packageName,
            "account=${account.displayLabel} reason=$reason trips=${collected.size} skipped=${maxOf(skipped, 1)} identityVerified=$verified authoritativeComplete=false",
        )
    }

    private fun clearPendingCardState() {
        pendingTripDetail = null
        pendingTripPassengers.clear()
        pendingTripPassengerCardIndexes.clear()
        pendingTripSyncGeneration = -1L
        pendingTripCandidateIndex = -1
        passengerContactIndex = 0
        passengerContactReadAttempts = 0
        passengerCardReadAttempts = 0
        passengerCallActionTriggered = false
        interceptedPassengerPhone = null
        tripRosterReadAttempts = 0
        lastTripRosterSignature = ""
        tripRosterStablePasses = 0
        pendingEditHref = ""
        pendingOptionsHref = ""
        pendingPublishedSeats = null
        editReadAttempts = 0
        optionsReadAttempts = 0
        passengerCaptureInFlight = false
        passengerCardCaptureInFlight = false
        editCaptureInFlight = false
        optionsCaptureInFlight = false
    }

    private fun directRosterSignature(result: DynamicTripDetail): String = buildString {
        append(result.detail.passengers.size)
        result.detail.passengers.forEach { passenger ->
            append('|')
            append(passenger.name.trim())
            append('|')
            append(passenger.seats.coerceAtLeast(1))
            append('|')
            append(passenger.boarding.orEmpty().trim())
            append('|')
            append(passenger.dropoff.orEmpty().trim())
            append('|')
            append(passenger.booking_href.orEmpty().substringBefore('#').substringBefore('?'))
        }
        append("|targets=")
        append(result.passengerHrefs.joinToString("|") { it.substringBefore('#').substringBefore('?') })
        append("|more=")
        append(result.rosterHasMore)
    }

    private fun tripTraversalKey(candidate: BlaBlaDomRideCandidate): String {
        BlaBlaTripIdentity.externalTripIdFromHref(candidate.href)?.takeIf(String::isNotBlank)?.let { return "id|$it" }
        return canonicalHref(candidate.href).trim().takeIf(String::isNotBlank)?.let { "href|$it" }.orEmpty()
    }

    private fun recordStale(reason: String, expectedSync: Long, expectedCandidate: Int) {
        UnifiedDebugEventStore.record(
            "STALE_CALLBACK_IGNORED",
            packageName,
            "account=${account.displayLabel} reason=$reason expectedGeneration=$expectedSync currentGeneration=$syncGeneration expectedCandidate=${expectedCandidate + 1} currentCandidate=${candidateIndex + 1} candidateCount=${candidates.size}",
        )
    }

    private fun saveCapturedPassengerFare(
        href: String?,
        evidence: DynamicPassengerContactEvidence?,
    ): Boolean {
        val key = externalPassengerReservationKey(account.profileUuid, href) ?: return false
        val baseSpec = PassengerMoney.spec(this)
        val currencyCode = resolvePassengerFareCurrency(evidence?.fareCurrencyCode, baseSpec.currencyCode) ?: return false
        val rawAmount = evidence?.fareAmount?.trim()?.takeIf(String::isNotEmpty) ?: return false
        val currency = runCatching { java.util.Currency.getInstance(currencyCode) }.getOrNull() ?: return false
        val fractionDigits = currency.defaultFractionDigits.takeIf { it in 0..3 } ?: 2
        val amount = PassengerMoney.parseMinorUnits(
            rawAmount,
            baseSpec.copy(currencyCode = currencyCode, fractionDigits = fractionDigits),
        ) ?: return false
        val current = passengerIdentityStore.externalMetadata(key) ?: ExternalPassengerMetadata(reservationKey = key)
        passengerIdentityStore.saveExternalMetadata(
            current.copy(fareMinorUnits = amount, fareCurrencyCode = currencyCode),
        )
        return true
    }

    private fun saveCapturedPassengerBoardingEvidence(
        href: String?,
        evidence: DynamicPassengerContactEvidence?,
    ): Boolean {
        val key = externalPassengerReservationKey(account.profileUuid, href) ?: return false
        evidence ?: return false
        val address = evidence.boardingAddress.trim().take(500)
        val latitude = validLatitude(evidence.boardingLatitude)
        val longitude = validLongitude(evidence.boardingLongitude)
        val hasCoordinates = latitude != null && longitude != null
        if (address.isBlank() && !hasCoordinates) return false
        val current = passengerIdentityStore.externalMetadata(key) ?: ExternalPassengerMetadata(reservationKey = key)
        passengerIdentityStore.saveExternalMetadata(
            current.copy(
                boardingAddress = address.ifBlank { current.boardingAddress },
                boardingLatitude = if (hasCoordinates) latitude else current.boardingLatitude,
                boardingLongitude = if (hasCoordinates) longitude else current.boardingLongitude,
                boardingAccuracyMeters = if (hasCoordinates) {
                    evidence.boardingAccuracyMeters?.takeIf { it.isFinite() && it >= 0.0 && it <= 100_000.0 }
                } else {
                    current.boardingAccuracyMeters
                },
                boardingLocationSource = if (hasCoordinates) {
                    evidence.boardingLocationSource.trim().take(80).ifBlank { "blablacar_booking_structured_pickup" }
                } else {
                    current.boardingLocationSource
                },
                boardingLocationCollectedAtMillis = if (hasCoordinates) System.currentTimeMillis() else current.boardingLocationCollectedAtMillis,
            ),
        )
        return true
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
        val finalStatus = if (skipped > 0 || quarantinedCardTraversalKeys.isNotEmpty()) "partial" else "success"
        UnifiedDebugEventStore.record(
            "SYNC_END",
            packageName,
            "account=${account.displayLabel} status=$finalStatus trips=$count skipped=$skipped completedCards=${completedCardTraversalKeys.size} quarantinedCards=${quarantinedCardTraversalKeys.size} identityVerified=$identityConfirmedThisSync",
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
    private fun isPassengerHref(url: String): Boolean =
        isBlaBla(url) && (url.contains("/passenger/") || url.contains("/booking/"))

    private fun passengerPageKey(url: String): String = url.substringBefore('#').substringBefore('?').trimEnd('/')

    private fun passengerPageMatchesExpected(expected: String, actual: String): Boolean =
        expected.isNotBlank() && actual.isNotBlank() &&
            isPassengerHref(expected) && isPassengerHref(actual) &&
            passengerPageKey(expected) == passengerPageKey(actual)

    private fun currentTripMatchesCandidate(expectedCandidate: Int): Boolean {
        val candidate = candidates.getOrNull(expectedCandidate) ?: return false
        val expectedTripId = BlaBlaTripIdentity.externalTripIdFromHref(candidate.href) ?: return false
        val currentTripId = BlaBlaTripIdentity.externalTripIdFromHref(webView.url.orEmpty()) ?: return false
        return expectedTripId == currentTripId
    }

    private fun looksLoggedOut(text: String): Boolean {
        val normalized = text.lowercase()
        return normalized.contains("continuar com e-mail") || normalized.contains("como você deseja se conectar") || normalized.contains("como voce deseja se conectar")
    }

    private enum class Phase { IDLE, IDENTITY, RIDES, DETAIL, PASSENGER_CARD, PASSENGER_CONTACT, EDIT, OPTIONS }

    companion object {
        private const val HOME_URL = "https://www.blablacar.com.br/"
        private const val RIDES_URL = "https://www.blablacar.com.br/rides"
        private const val PROFILE_URL = "https://www.blablacar.com.br/dashboard/profile/menu"
        private const val MAX_RIDES_EMPTY_READ_ATTEMPTS = 3
        private const val REQUIRED_STABLE_BOTTOM_PASSES = 2
        private const val RIDES_SCROLL_SETTLE_MS = 750L
        private const val RIDES_BOTTOM_SETTLE_MS = 1200L
        private const val MAX_PASSENGER_EVIDENCE_READ_ATTEMPTS = 3
        private const val MAX_TRIP_ROSTER_READ_ATTEMPTS = 5
        private const val MAX_PASSENGER_CARD_READ_ATTEMPTS = 4
        private const val MAX_PASSENGER_BIND_READ_ATTEMPTS = 3
        private const val MAX_EDIT_LINK_READ_ATTEMPTS = 5
        private const val MAX_OPTIONS_READ_ATTEMPTS = 3
        private const val ROSTER_RETRY_MS = 800L
        private const val PASSENGER_NAVIGATION_SETTLE_MS = 1_200L
        private const val PASSENGER_CALL_SETTLE_MS = 650L
        private const val CARD_TARGET_PREFIX = "rotacerta-card:"
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
              const bodyText = clean(document.body && document.body.innerText).slice(0, 16000);
              const emptyStructure = document.querySelector(
                '[data-testid*="empty"][data-testid*="ride"], [data-testid*="empty"][data-testid*="trip"], ' +
                '[data-testid*="no-ride"], [data-testid*="no-trip"], [aria-label*="no ride" i], [aria-label*="no trip" i]'
              );
              const emptyText = /nenhuma viagem|sem viagens|no trips|no rides|aucun trajet|keine fahrten|sin viajes|nessun viaggio/i.test(bodyText);
              $SANITIZED_HTML_JS
              return JSON.stringify({
                candidates: fromRoots.concat(fallback),
                bodyText: bodyText,
                explicitEmptyList: !!emptyStructure || emptyText,
                scrollY: Math.max(0, Math.round(window.scrollY || window.pageYOffset || 0)),
                scrollHeight: Math.max(0, Math.round(document.documentElement.scrollHeight || document.body.scrollHeight || 0)),
                viewportHeight: Math.max(0, Math.round(window.innerHeight || document.documentElement.clientHeight || 0)),
                atBottom: Math.ceil((window.scrollY || window.pageYOffset || 0) + (window.innerHeight || document.documentElement.clientHeight || 0)) >= Math.max(document.documentElement.scrollHeight || 0, document.body.scrollHeight || 0) - 8,
                domHtml: html.slice(0, 350000)
              });
            })();
        """.trimIndent()

        private val PASSENGER_CONTACT_JS = """
            (function() {
              const clean = (value) => (value || '').replace(/\s+/g, ' ').trim();
              const numberOrNull = (value) => {
                if (value === null || value === undefined || value === '') return null;
                const parsed = Number(value);
                return Number.isFinite(parsed) ? parsed : null;
              };
              const validLatitude = (value) => value !== null && value >= -90 && value <= 90;
              const validLongitude = (value) => value !== null && value >= -180 && value <= 180;
              const nodes = Array.from(document.querySelectorAll('[href^="tel:"], a, button, [role="button"], [role="link"]'));
              const callAction = nodes.find((node) => {
                const text = clean(node.innerText || node.textContent);
                const label = clean((node.getAttribute && (node.getAttribute('aria-label') || node.getAttribute('title'))) || '');
                const href = (node.getAttribute && node.getAttribute('href')) || '';
                return /^tel:/i.test(href) || /^(ligar|chamar|telefone|telefonar)$/i.test(text) || /\b(ligar|telefone|telefonar)\b/i.test(label);
              });
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
              const fareNode = document.querySelector(
                '[data-testid*="booking-price"], [data-testid*="reservation-price"], [data-testid*="passenger-price"], [data-testid*="booking-total"], [data-testid*="reservation-total"]'
              );
              const currencyNode = fareNode && fareNode.closest('[data-currency], [data-currency-code], [data-testid*="booking"], [data-testid*="reservation"]');
              const fareAmount = clean(fareNode && (
                fareNode.getAttribute('data-value') ||
                fareNode.getAttribute('content') ||
                fareNode.innerText
              ));
              const fareCurrencyCode = clean(currencyNode && (
                currencyNode.getAttribute('data-currency-code') ||
                currencyNode.getAttribute('data-currency')
              )).toUpperCase();

              const pickup = {
                address: '',
                latitude: null,
                longitude: null,
                accuracyMeters: null,
                source: ''
              };
              const addressText = (value) => {
                if (!value) return '';
                if (typeof value === 'string') return clean(value);
                if (typeof value !== 'object') return '';
                return clean(
                  value.label || value.name || value.formattedAddress || value.formatted_address ||
                  value.fullAddress || value.full_address || value.address ||
                  [value.street, value.streetNumber || value.number, value.cityName || value.city, value.postalCode || value.zipCode]
                    .filter(Boolean).join(', ')
                );
              };
              const readCoordinate = (place) => {
                if (!place || typeof place !== 'object') return null;
                const coordinate = place.coordinates || place.coordinate || place.location || place.geo || place;
                if (!coordinate || typeof coordinate !== 'object') return null;
                const latitude = numberOrNull(
                  coordinate.latitude !== undefined ? coordinate.latitude : coordinate.lat
                );
                const longitude = numberOrNull(
                  coordinate.longitude !== undefined ? coordinate.longitude :
                    (coordinate.lng !== undefined ? coordinate.lng : coordinate.lon)
                );
                if (!validLatitude(latitude) || !validLongitude(longitude)) return null;
                const accuracy = numberOrNull(
                  coordinate.accuracy !== undefined ? coordinate.accuracy : coordinate.accuracyMeters
                );
                return { latitude: latitude, longitude: longitude, accuracyMeters: accuracy };
              };
              const acceptPickupPlace = (place, source) => {
                if (!place || typeof place !== 'object') return;
                const address = addressText(place.address || place);
                if (!pickup.address && address) pickup.address = address;
                const coordinate = readCoordinate(place);
                if (coordinate && pickup.latitude === null) {
                  pickup.latitude = coordinate.latitude;
                  pickup.longitude = coordinate.longitude;
                  pickup.accuracyMeters = coordinate.accuracyMeters;
                  pickup.source = source;
                }
              };
              const seen = new Set();
              const walk = (value, depth) => {
                if (value === null || value === undefined || depth > 10) return;
                if (typeof value !== 'object') return;
                if (seen.has(value)) return;
                seen.add(value);
                if (Array.isArray(value)) {
                  value.forEach((item) => walk(item, depth + 1));
                  return;
                }
                Object.keys(value).forEach((key) => {
                  const normalized = key.toLowerCase().replace(/[^a-z]/g, '');
                  if (normalized === 'pickupplace' || normalized === 'boardingplace' || normalized === 'pickuppoint') {
                    acceptPickupPlace(value[key], 'blablacar_booking_structured_pickup');
                  }
                  walk(value[key], depth + 1);
                });
              };
              const scriptTexts = Array.from(document.querySelectorAll('script'))
                .map((script) => script.textContent || '')
                .filter((text) => /pickup|boarding/i.test(text));
              scriptTexts.forEach((text) => {
                try {
                  walk(JSON.parse(text), 0);
                } catch (_) {
                  // Framework bootstrap scripts are often JavaScript rather than pure JSON.
                }
              });
              if (pickup.latitude === null) {
                scriptTexts.concat([pageHtml]).some((raw) => {
                  const marker = raw.search(/pickupPlace|pickup_place|boardingPlace|boarding_place/i);
                  if (marker < 0) return false;
                  const slice = raw.slice(marker, marker + 6000);
                  const latitudeMatch = slice.match(/["'](?:latitude|lat)["']\s*:\s*(-?\d{1,3}(?:\.\d+)?)/i);
                  const longitudeMatch = slice.match(/["'](?:longitude|lng|lon)["']\s*:\s*(-?\d{1,3}(?:\.\d+)?)/i);
                  const latitude = numberOrNull(latitudeMatch && latitudeMatch[1]);
                  const longitude = numberOrNull(longitudeMatch && longitudeMatch[1]);
                  if (!validLatitude(latitude) || !validLongitude(longitude)) return false;
                  pickup.latitude = latitude;
                  pickup.longitude = longitude;
                  pickup.source = 'blablacar_booking_structured_pickup_text';
                  const accuracyMatch = slice.match(/["'](?:accuracy|accuracyMeters)["']\s*:\s*(\d+(?:\.\d+)?)/i);
                  pickup.accuracyMeters = numberOrNull(accuracyMatch && accuracyMatch[1]);
                  if (!pickup.address) {
                    const addressMatch = slice.match(/["'](?:formattedAddress|fullAddress|address)["']\s*:\s*["']([^"']{3,240})["']/i);
                    if (addressMatch) pickup.address = clean(addressMatch[1]);
                  }
                  return true;
                });
              }
              if (!pickup.address) {
                const pickupNode = document.querySelector(
                  '[data-testid*="pickup-address"], [data-testid*="boarding-address"], [data-testid*="pickup-place"], [data-testid*="boarding-place"]'
                );
                pickup.address = clean(pickupNode && pickupNode.innerText);
              }
              $SANITIZED_HTML_JS
              return JSON.stringify({
                phone: phone,
                visibleName: clean(nameNode && nameNode.innerText),
                fareAmount: fareAmount,
                fareCurrencyCode: fareCurrencyCode,
                callActionPresent: !!callAction,
                boardingAddress: pickup.address,
                boardingLatitude: pickup.latitude,
                boardingLongitude: pickup.longitude,
                boardingAccuracyMeters: pickup.accuracyMeters,
                boardingLocationSource: pickup.source,
                domHtml: html.slice(0, 350000)
              });
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
              const seen = new Set();
              candidates.forEach((node, index) => {
                const anchor = (node.matches && node.matches('a[href]')) ? node : (node.querySelector && node.querySelector('a[href]'));
                const href = (anchor && (anchor.href || anchor.getAttribute('href'))) || (node.getAttribute && node.getAttribute('data-href')) || '';
                const container = (node.closest && node.closest('li, article, [role="listitem"], [data-testid*="passenger"], [data-testid*="booking"]')) || node;
                const lines = linesOf(container);
                const route = lines.find((line) => line.includes('→') || line.includes('->')) || '';
                if (!route) return;
                const explicit = container && container.querySelector
                  ? container.querySelector('[data-testid*="passenger-name"], [data-testid*="profile-name"], img[alt]')
                  : null;
                const marker = clean(
                  ((container && container.getAttribute && container.getAttribute('data-testid')) || '') + ' ' +
                  ((container && container.getAttribute && container.getAttribute('aria-label')) || '')
                ).toLowerCase();
                const passengerMarked = /passenger|booking|reservation/i.test(marker) || /passenger|booking/i.test(href);
                if (!explicit && !passengerMarked) return;
                const alt = explicit && explicit.getAttribute ? clean(explicit.getAttribute('alt')) : '';
                const name = clean(alt || (explicit && explicit.innerText) || lines[0] || '');
                if (!name) return;
                const suffixSource = lines.find((line) => /\(\d+\)\s*$/.test(line)) || name;
                const suffix = suffixSource.match(/\((\d+)\)\s*$/);
                const seats = suffix ? Math.max(1, parseInt(suffix[1], 10) || 1) : 1;
                const key = href || [name.toLowerCase(), seats, route].join('|') || String(index);
                if (seen.has(key)) return;
                seen.add(key);
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

        private val EXPAND_ROSTER_JS = """
            (function() {
              const clean = (value) => (value || '').replace(/\s+/g, ' ').trim();
              const controls = Array.from(document.querySelectorAll(
                'button, a, [role="button"], [data-testid], [aria-label], [aria-controls]'
              ));
              const target = controls.find((node) => {
                const marker = (
                  (node.getAttribute('data-testid') || '') + ' ' +
                  (node.getAttribute('aria-label') || '') + ' ' +
                  (node.getAttribute('aria-controls') || '')
                ).toLowerCase();
                const passengerMarker = marker.includes('passenger') || marker.includes('booking') || marker.includes('reservation');
                const markerRequestsMore = passengerMarker && (marker.includes('more') || marker.includes('expand') || marker.includes('load'));
                const collapsedNearRoute = node.getAttribute('aria-expanded') === 'false' && /(?:→|->)/.test(clean((node.parentElement && node.parentElement.innerText) || ''));
                return markerRequestsMore || collapsedNearRoute;
              });
              if (!target || typeof target.click !== 'function') return JSON.stringify({ found: false, clicked: false });
              target.click();
              return JSON.stringify({ found: true, clicked: true });
            })();
        """.trimIndent()

        private val DIRECT_EDIT_EVIDENCE_JS = """
            (function() {
              const absolute = (href) => { try { return new URL(href || '', location.href).href; } catch (_) { return href || ''; } };
              const candidates = Array.from(document.querySelectorAll('a[href], [data-href]'))
                .map((node) => absolute(node.getAttribute('href') || node.getAttribute('data-href') || ''))
                .filter(Boolean);
              const option = candidates.find((href) => /\/rides\/offer\/edit\/[^/?#]+\/options\/?(?:$|[?#])/i.test(href)) || '';
              $SANITIZED_HTML_JS
              return JSON.stringify({
                optionsHref: option,
                pageUrl: location.href || '',
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
              const structuredDates = Array.from(document.querySelectorAll('time[datetime]'))
                .map((node) => clean(node.getAttribute('datetime')))
                .filter(Boolean);
              const visibleDates = Array.from(document.querySelectorAll('[data-testid*="date"], time, h1, h2, h3'))
                .map((node) => clean(node.innerText))
                .filter(Boolean);
              const dateText = clean(structuredDates.concat(visibleDates).join(' | ')).slice(0, 1600);
              const linesOf = (node) => ((node && node.innerText) || '').split(/\n+/).map(clean).filter(Boolean);
              const absolute = (href) => { try { return new URL(href || '', location.href).href; } catch (_) { return href || ''; } };
              const rows = [];
              const seenPassengers = new Set();
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
                const marker = clean(
                  ((container && container.getAttribute && container.getAttribute('data-testid')) || '') + ' ' +
                  ((container && container.getAttribute && container.getAttribute('aria-label')) || '')
                ).toLowerCase();
                const passengerMarked = /passenger|booking|reservation/i.test(marker) || /passenger|booking/i.test(href);
                if (!explicit && !passengerMarked) return;
                const alt = explicit && explicit.getAttribute ? clean(explicit.getAttribute('alt')) : '';
                let name = clean(alt || (explicit && explicit.innerText) || lines[0] || '').replace(/\s*\(\d+\)\s*$/, '');
                if (!name) return;
                const suffixSource = lines.find((line) => /\(\d+\)\s*$/.test(line)) || name;
                const suffix = suffixSource.match(/\((\d+)\)\s*$/);
                const seats = suffix ? Math.max(1, parseInt(suffix[1], 10) || 1) : 1;
                const routeParts = route.split(/→|->/).map(clean);
                const key = href || [name.toLowerCase(), seats, route].join('|') || String(index);
                if (seenPassengers.has(key)) return;
                seenPassengers.add(key);
                const rowIndex = rows.length;
                const realPassengerHref = /\/passenger\/|\/booking\//i.test(href) ? href : '';
                passengerTargets.push(realPassengerHref || 'rotacerta-card:' + rowIndex);
                const tel = container && container.querySelector ? container.querySelector('a[href^="tel:"]') : null;
                rows.push({
                  name: name,
                  seats: seats,
                  boarding: routeParts.length >= 2 ? routeParts[0] : null,
                  dropoff: routeParts.length >= 2 ? routeParts[routeParts.length - 1] : null,
                  phone: tel ? (tel.getAttribute('href') || '').replace(/^tel:/i, '') : null,
                  booking_href: realPassengerHref || null
                });
              });
              Array.from(document.querySelectorAll('a[href]'))
                .map((a) => absolute(a.getAttribute('href') || ''))
                .filter((href) => /\/passenger\/|\/booking\//i.test(href))
                .forEach((href) => passengerTargets.push(href));
              const passengers = rows;
              const links = Array.from(document.querySelectorAll('a[href]'));
              const edit = links.find((a) => {
                const href = absolute(a.getAttribute('href') || a.href || '');
                return /\/rides\/offer\/edit\/[^/?#]+\/?(?:$|[?#])/i.test(href) && !/\/options\/?(?:$|[?#])/i.test(href);
              });
              const rosterContainers = Array.from(document.querySelectorAll('[data-testid], [aria-label]')).filter((node) => {
                const marker = ((node.getAttribute('data-testid') || '') + ' ' + (node.getAttribute('aria-label') || '')).toLowerCase();
                return marker.includes('passenger') || marker.includes('booking') || marker.includes('reservation');
              });
              const rosterExpandControls = Array.from(document.querySelectorAll('button, a, [role="button"], [data-testid], [aria-label], [aria-controls]')).filter((node) => {
                const marker = ((node.getAttribute('data-testid') || '') + ' ' + (node.getAttribute('aria-label') || '') + ' ' + (node.getAttribute('aria-controls') || '')).toLowerCase();
                const passengerMarker = marker.includes('passenger') || marker.includes('booking') || marker.includes('reservation');
                const markerRequestsMore = passengerMarker && (marker.includes('more') || marker.includes('expand') || marker.includes('load'));
                const collapsedNearRoute = node.getAttribute('aria-expanded') === 'false' && /(?:→|->)/.test(clean((node.parentElement && node.parentElement.innerText) || ''));
                return markerRequestsMore || collapsedNearRoute;
              });
              const hasMore = rosterExpandControls.length > 0;
              const explicitEmptyRoster = Array.from(document.querySelectorAll('[data-testid]')).some((node) => {
                const marker = (node.getAttribute('data-testid') || '').toLowerCase();
                const passengerMarker = marker.includes('passenger') || marker.includes('booking') || marker.includes('reservation');
                return passengerMarker && (marker.includes('empty') || marker.includes('none') || marker.includes('zero') || marker.includes('no-'));
              });
              const passengerRosterComplete = explicitEmptyRoster || (passengers.length > 0 && rosterContainers.length > 0 && !hasMore);
              const rosterTerminalEvidence = !!edit || rosterContainers.length > 0 || document.readyState === 'complete';
              const itineraryStops = [];
              [
                '[data-testid*="itinerary-departure-station"]',
                '[data-testid*="itinerary-arrival-station"]',
                '[data-testid*="itinerary-stop"]',
                '[data-testid*="station"]'
              ].forEach((selector) => {
                Array.from(document.querySelectorAll(selector)).forEach((node) => {
                  const value = clean(node.innerText);
                  if (value && !itineraryStops.includes(value)) itineraryStops.push(value);
                });
              });
              const pageText = clean(document.body && document.body.innerText);
              const viewsMatch = pageText.match(/(\d{1,9})\s+visualiza(?:ç|c)[õo]es/i);
              const views = viewsMatch ? parseInt(viewsMatch[1], 10) : null;
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
                passengerHrefs: Array.from(new Set(passengerTargets)),
                explicitEmptyRoster: explicitEmptyRoster,
                rosterHasMore: hasMore,
                rosterTerminalEvidence: rosterTerminalEvidence,
                editHref: edit ? absolute(edit.getAttribute('href') || edit.href || '') : '',
                itineraryStops: itineraryStops,
                views: Number.isFinite(views) ? views : null,
                domHtml: html.slice(0, 350000)
              });
            })();
        """.trimIndent()
    }
}
