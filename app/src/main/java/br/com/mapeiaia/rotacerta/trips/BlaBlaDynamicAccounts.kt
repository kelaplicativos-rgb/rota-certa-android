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
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun list(): List<BlaBlaDynamicAccount> {
        val decoded = runCatching {
            json.decodeFromString<List<BlaBlaDynamicAccount>>(prefs.getString(KEY_ACCOUNTS, "[]") ?: "[]")
        }.getOrDefault(emptyList())
        val sanitized = decoded.map { account ->
            account.copy(profileName = BlaBlaDriverProfileNamePolicy.normalize(account.profileName))
        }
        if (sanitized != decoded) {
            save(sanitized)
            UnifiedDebugEventStore.record(
                "PROFILE_NAME_CONTAMINATION_CLEANED",
                appContext.packageName,
                "accounts=${decoded.size} cleaned=${decoded.zip(sanitized).count { (before, after) -> before.profileName != after.profileName }}",
            )
        }
        return sanitized
    }

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
        val cleanName = BlaBlaDriverProfileNamePolicy.normalize(name)
        val previousCleanName = BlaBlaDriverProfileNamePolicy.normalize(account.profileName)
        val updated = account.copy(
            profileUuid = normalizedUuid,
            profileName = cleanName ?: previousCleanName,
        )
        save(current.map { if (it.id == id) updated else it })
        return updated
    }

    fun remove(id: String) {
        val current = list()
        val account = current.firstOrNull { it.id == id } ?: return
        save(current.filterNot { it.id == id })
        BlaBlaDynamicSessionStore(appContext).delete(account)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { ProfileStore.getInstance().deleteProfile(account.webProfileName) }
        }
    }

    private fun save(accounts: List<BlaBlaDynamicAccount>) {
        prefs.edit().putString(KEY_ACCOUNTS, json.encodeToString(accounts)).apply()
    }

    companion object {
        private const val PREFS = "rota_certa_blablacar_dynamic_accounts_v2"
        private const val KEY_ACCOUNTS = "accounts"
    }
}

object BlaBlaDynamicSessionIntents {
    const val EXTRA_ACCOUNT_ID = "blablacar_account_id"
    const val EXTRA_MODE = "blablacar_mode"
    const val EXTRA_TARGET_URL = "blablacar_target_url"
    const val EXTRA_TARGET_TRIP_ID = "blablacar_target_trip_id"
    const val EXTRA_TARGET_DATE = "blablacar_target_date"
    const val MODE_LOGIN = "login"
    const val MODE_SYNC = "sync"
    const val MODE_PROFILE = "profile"
    const val MODE_MANAGE = "manage"

    fun login(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_LOGIN)
    fun profile(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_PROFILE)
    fun sync(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_SYNC)
    fun syncToday(context: Context, account: BlaBlaDynamicAccount, targetDate: LocalDate): Intent =
        intent(context, account, MODE_SYNC).putExtra(EXTRA_TARGET_DATE, targetDate.toString())
    fun syncExact(context: Context, account: BlaBlaDynamicAccount, tripId: String, tripHref: String): Intent =
        intent(context, account, MODE_SYNC)
            .putExtra(EXTRA_TARGET_TRIP_ID, tripId)
            .putExtra(EXTRA_TARGET_URL, tripHref)
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
    val photoUrl: String = "",
    val about: String = "",
    val rating: String = "",
    val reviewCount: Int? = null,
    val badge: String = "",
    val vehicleMakeModel: String = "",
    val vehicleColor: String = "",
    val amenities: String = "",
    val preferences: String = "",
    val reviewsHref: String = "",
    val reviews: List<BlaBlaPublicReview> = emptyList(),
    val domHtml: String = "",
)

@Serializable
private data class DynamicProfileReviewsPage(
    val observedUuids: List<String> = emptyList(),
    val reviews: List<BlaBlaPublicReview> = emptyList(),
    val scrollY: Int = 0,
    val scrollHeight: Int = 0,
    val viewportHeight: Int = 0,
    val atBottom: Boolean = false,
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
    val networkSource: BlaBlaNetworkTripSourceEvidence? = null,
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
    private lateinit var publicProfileStore: BlaBlaPublicProfileStore
    private lateinit var browserScripts: BlaBlaBrowserScriptRegistry
    private val browserOrchestrator = BlaBlaBrowserOrchestrator()
    private lateinit var account: BlaBlaDynamicAccount
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private var mode = BlaBlaDynamicSessionIntents.MODE_LOGIN
    private var targetTripId = ""
    private var targetTripHref = ""
    private var targetDate: LocalDate? = null
    // Compatibility projection for page-finished dispatch; script authority lives in browserOrchestrator.
    private var phase = Phase.IDLE
    private var candidates = emptyList<BlaBlaDomRideCandidate>()
    private val collected = mutableListOf<BlaBlaCollectorTrip>()
    private var candidateIndex = 0
    private var skipped = 0
    private var identityConfirmedThisSync = false
    private var rideReadAttempts = 0
    private var profileBaseEvidence: DynamicIdentityEvidence? = null
    private val profileReviewsCollected = mutableListOf<BlaBlaPublicReview>()
    private var profileReviewsLastCount = -1
    private var profileReviewsStablePasses = 0
    private var profileReviewsReadAttempts = 0
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
    private var networkDiagnosticRecorder: BlaBlaNetworkDiagnosticRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = BlaBlaDynamicAccountRegistry(this)
        store = BlaBlaDynamicSessionStore(this)
        passengerIdentityStore = PassengerIdentityStore(this)
        publicProfileStore = BlaBlaPublicProfileStore(this)
        browserScripts = BlaBlaBrowserScriptRegistry(this)
        account = registry.get(intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID)) ?: run {
            finish()
            return
        }
        mode = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_MODE) ?: BlaBlaDynamicSessionIntents.MODE_LOGIN
        targetTripId = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_TRIP_ID)?.trim().orEmpty()
        targetTripHref = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_URL)?.trim().orEmpty()
        targetDate = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_DATE)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
            ?.takeIf { mode == BlaBlaDynamicSessionIntents.MODE_SYNC }
        if (mode != BlaBlaDynamicSessionIntents.MODE_SYNC || BlaBlaCollectorUrlModule.tripId(targetTripHref) != targetTripId) {
            targetTripId = ""
            targetTripHref = ""
        } else {
            targetDate = null
        }
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
            BlaBlaDynamicSessionIntents.MODE_PROFILE -> beginProfileSync()
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
        if (mode == BlaBlaDynamicSessionIntents.MODE_SYNC || mode == BlaBlaDynamicSessionIntents.MODE_PROFILE) {
            actions.visibility = android.view.View.GONE
        }

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
        if (mode == BlaBlaDynamicSessionIntents.MODE_SYNC) {
            networkDiagnosticRecorder = BlaBlaNetworkDiagnosticRecorder(
                context = this,
                accountId = account.id,
                appPackageName = packageName,
            ).also { recorder -> recorder.install(webView) }
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
                    Phase.IDENTITY -> if (BlaBlaCollectorUrlModule.isAllowed(url)) view.postDelayed({ captureIdentityForSync() }, 650)
                    Phase.PROFILE_PUBLIC -> if (BlaBlaCollectorUrlModule.isAllowed(url)) view.postDelayed({ capturePublicProfilePage() }, 850)
                    Phase.PROFILE_REVIEWS -> if (BlaBlaCollectorUrlModule.isAllowed(url)) view.postDelayed({ captureProfileReviewsPage() }, 850)
                    Phase.RIDES -> if (BlaBlaCollectorUrlModule.isAllowed(url)) view.postDelayed({ captureRideList() }, 900)
                    Phase.DETAIL -> if (BlaBlaCollectorUrlModule.isAllowed(url)) scheduleTripDetailCapture(view)
                    Phase.PASSENGER_CARD -> if (BlaBlaCollectorUrlModule.isAllowed(url)) schedulePassengerCardOpen(view)
                    Phase.PASSENGER_CONTACT -> if (BlaBlaCollectorUrlModule.isAllowed(url)) schedulePassengerContactCapture(view)
                    Phase.EDIT -> if (BlaBlaCollectorUrlModule.isAllowed(url)) scheduleEditCapture(view)
                    Phase.OPTIONS -> if (BlaBlaCollectorUrlModule.isAllowed(url)) scheduleOptionsCapture(view)
                    Phase.IDLE -> if (BlaBlaCollectorUrlModule.isAllowed(url)) view.postDelayed({ probeIdentity() }, 500)
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
        val phone = BlaBlaCollectorPassengerModule.normalizePhone(url.substringAfter(':').substringBefore('?'))
        if (
            phase == Phase.PASSENGER_CONTACT &&
            passenger != null &&
            BlaBlaCollectorUrlModule.samePassengerPage(passenger.booking_href.orEmpty(), pageUrl)
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

    private fun enterBrowserPhase(
        phaseValue: Phase,
        request: BlaBlaBrowserRequest?,
        reason: String,
    ) {
        phase = phaseValue
        if (request == null) {
            browserOrchestrator.cancel()
        } else {
            val token = browserOrchestrator.start(request, browserExecutionContext(), reason)
            UnifiedDebugEventStore.record(
                "BROWSER_ORCHESTRATOR_TRANSITION",
                packageName,
                "account=${account.displayLabel} request=${request.name} token=${token.generation} phase=${phaseValue.name} reason=$reason",
            )
        }
    }

    private fun beginProfileSync() {
        syncGeneration++
        identityConfirmedThisSync = false
        profileBaseEvidence = null
        profileReviewsCollected.clear()
        profileReviewsLastCount = -1
        profileReviewsStablePasses = 0
        profileReviewsReadAttempts = 0
        enterBrowserPhase(Phase.IDENTITY, BlaBlaBrowserRequest.SESSION_IDENTITY, "profile_sync_start")
        statusView.text = "${account.displayLabel} • buscando dados públicos do motorista…"
        UnifiedDebugEventStore.record(
            "PUBLIC_PROFILE_SYNC_STARTED",
            packageName,
            "account=${account.displayLabel} expectedUuidPresent=${!account.profileUuid.isNullOrBlank()}",
        )
        loadTrackedUrl(PROFILE_URL)
    }

    private fun beginSync() {
        syncGeneration++
        networkDiagnosticRecorder?.startSync(syncGeneration)
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
        enterBrowserPhase(Phase.IDENTITY, BlaBlaBrowserRequest.SESSION_IDENTITY, "sync_start")
        statusView.text = "${account.displayLabel} • confirmando conta…"
        UnifiedDebugEventStore.record(
            "SYNC_START",
            packageName,
            "account=${account.displayLabel} expectedUuid=${account.profileUuid.orEmpty()} url=${BlaBlaCollectorUrlModule.sanitizeForLog(PROFILE_URL)}",
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
        evaluateRequest<DynamicIdentityEvidence>(BlaBlaBrowserRequest.SESSION_IDENTITY) { evidence ->
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
                if (identityConfirmedThisSync) persistPublicProfileEvidence(it)
                UnifiedDebugEventStore.record(
                    "IDENTITY_EVIDENCE",
                    packageName,
                    "account=${account.displayLabel} expectedUuid=${expectedUuid.orEmpty()} expectedFound=$expectedFoundInAuthenticatedPage observedCount=${observedUuids.size} profileLinkCount=${it.profileLinks.size} url=${BlaBlaCollectorUrlModule.sanitizeForLog(webView.url.orEmpty())}",
                )
            }
            if (identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()) {
                UnifiedDebugEventStore.record(
                    "IDENTITY_VERIFIED",
                    packageName,
                    "account=${account.displayLabel} expectedUuid=${account.profileUuid.orEmpty()} foundUuid=${account.profileUuid.orEmpty()} method=authenticated_profile",
                )
            }
            if (mode == BlaBlaDynamicSessionIntents.MODE_PROFILE) {
                continuePublicProfileSync(evidence)
                return@evaluateRequest
            }
            if (targetTripId.isNotBlank() && targetTripHref.isNotBlank()) {
                currentCardTraversalKey = "id|$targetTripId"
                candidates = listOf(BlaBlaDomRideCandidate(href = targetTripHref))
                candidateIndex = 0
                enterBrowserPhase(Phase.DETAIL, BlaBlaBrowserRequest.TRIP_OPEN, "exact_trip_open")
                UnifiedDebugEventStore.record(
                    "AGENDA_EXACT_CARD_SYNC_STARTED",
                    packageName,
                    "account=${account.displayLabel} profileUuidPresent=${!account.profileUuid.isNullOrBlank()} tripIdPresent=true directTarget=true",
                )
                loadCurrentCandidate()
            } else {
                enterBrowserPhase(Phase.RIDES, BlaBlaBrowserRequest.RIDE_LIST, "open_ride_list")
                statusView.text = "${account.displayLabel} • lendo Suas viagens…"
                loadTrackedUrl(RIDES_URL)
            }
        }
    }

    private fun continuePublicProfileSync(evidence: DynamicIdentityEvidence?) {
        if (!identityConfirmedThisSync || account.profileUuid.isNullOrBlank() || evidence == null) {
            statusView.text = "${account.displayLabel} • não foi possível confirmar o perfil público."
            UnifiedDebugEventStore.record(
                "PUBLIC_PROFILE_SYNC_BLOCKED",
                packageName,
                "account=${account.displayLabel} reason=identity_not_confirmed",
            )
            setResult(RESULT_CANCELED, Intent().putExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID, account.id))
            finish()
            return
        }
        val expectedUuid = account.profileUuid.orEmpty().lowercase()
        profileBaseEvidence = evidence
        profileReviewsCollected.clear()
        profileReviewsCollected += evidence.reviews

        val trustedProfile = BlaBlaCollectorIdentityModule.trustedDriverProfileLinks(
            expectedUuid = expectedUuid,
            authenticatedProfileSessionVerified = true,
            observedLinks = evidence.profileLinks,
        ).firstOrNull { link ->
            link.contains(expectedUuid, ignoreCase = true) && BlaBlaCollectorUrlModule.isAllowed(link)
        }
        if (!trustedProfile.isNullOrBlank() &&
            BlaBlaCollectorUrlModule.canonical(trustedProfile) != BlaBlaCollectorUrlModule.canonical(webView.url.orEmpty())
        ) {
            enterBrowserPhase(Phase.PROFILE_PUBLIC, BlaBlaBrowserRequest.DRIVER_PROFILE, "open_public_profile")
            statusView.text = "${account.displayLabel} • abrindo o perfil público…"
            loadTrackedUrl(trustedProfile)
            return
        }
        persistPublicProfileEvidence(evidence)
        continueToProfileReviews(evidence)
    }

    private fun capturePublicProfilePage() {
        if (phase != Phase.PROFILE_PUBLIC) return
        evaluateRequest<DynamicIdentityEvidence>(BlaBlaBrowserRequest.SESSION_IDENTITY) { evidence ->
            if (phase != Phase.PROFILE_PUBLIC || evidence == null) return@evaluateRequest
            val expectedUuid = account.profileUuid.orEmpty().lowercase()
            val observed = evidence.observedUuids.map(String::lowercase).toSet()
            val urlMatches = webView.url.orEmpty().contains(expectedUuid, ignoreCase = true)
            if (expectedUuid.isBlank() || (expectedUuid !in observed && !urlMatches)) {
                UnifiedDebugEventStore.record(
                    "PROFILE_UUID_MISMATCH",
                    packageName,
                    "account=${account.displayLabel} source=public_profile_page",
                )
                finishPublicProfileSync(success = false)
                return@evaluateRequest
            }
            store.saveDiagnosticHtml(account, "public-profile", evidence.domHtml)
            profileBaseEvidence = evidence
            profileReviewsCollected.clear()
            profileReviewsCollected += evidence.reviews
            persistPublicProfileEvidence(evidence)
            continueToProfileReviews(evidence)
        }
    }

    private fun continueToProfileReviews(evidence: DynamicIdentityEvidence) {
        val expectedUuid = account.profileUuid.orEmpty().lowercase()
        val reviewsHref = evidence.reviewsHref.trim()
        val trustedReviewsHref = reviewsHref.takeIf { href ->
            expectedUuid.isNotBlank() &&
                href.contains(expectedUuid, ignoreCase = true) &&
                BlaBlaCollectorUrlModule.isAllowed(href)
        }
        if (trustedReviewsHref == null) {
            finishPublicProfileSync(success = true)
            return
        }
        enterBrowserPhase(Phase.PROFILE_REVIEWS, BlaBlaBrowserRequest.DRIVER_REVIEWS, "open_driver_reviews")
        profileReviewsLastCount = -1
        profileReviewsStablePasses = 0
        profileReviewsReadAttempts = 0
        statusView.text = "${account.displayLabel} • buscando avaliações…"
        loadTrackedUrl(trustedReviewsHref)
    }

    private fun captureProfileReviewsPage() {
        if (phase != Phase.PROFILE_REVIEWS) return
        evaluateRequest<DynamicProfileReviewsPage>(BlaBlaBrowserRequest.DRIVER_REVIEWS) { page ->
            if (phase != Phase.PROFILE_REVIEWS || page == null) return@evaluateRequest
            val expectedUuid = account.profileUuid.orEmpty().lowercase()
            val observed = page.observedUuids.map(String::lowercase).toSet()
            val urlMatches = webView.url.orEmpty().contains(expectedUuid, ignoreCase = true)
            if (expectedUuid.isBlank() || (expectedUuid !in observed && !urlMatches)) {
                UnifiedDebugEventStore.record(
                    "PROFILE_UUID_MISMATCH",
                    packageName,
                    "account=${account.displayLabel} source=reviews_page",
                )
                finishPublicProfileSync(success = false)
                return@evaluateRequest
            }
            store.saveDiagnosticHtml(account, "public-profile-reviews", page.domHtml)
            val merged = (profileReviewsCollected + page.reviews)
                .filter { it.author.isNotBlank() || it.text.isNotBlank() }
                .distinctBy { "${it.author.lowercase()}|${it.dateLabel.lowercase()}|${it.text.lowercase()}" }
                .take(60)
            profileReviewsCollected.clear()
            profileReviewsCollected += merged
            if (merged.size == profileReviewsLastCount) profileReviewsStablePasses++ else profileReviewsStablePasses = 0
            profileReviewsLastCount = merged.size
            profileReviewsReadAttempts++

            val done = (page.atBottom && profileReviewsStablePasses >= 1) ||
                profileReviewsReadAttempts >= MAX_PROFILE_REVIEW_READ_ATTEMPTS
            if (done) {
                profileBaseEvidence?.let { base ->
                    persistPublicProfileEvidence(base.copy(reviews = profileReviewsCollected.toList()))
                }
                finishPublicProfileSync(success = true)
            } else {
                val nextY = (page.scrollY + (page.viewportHeight * 0.8)).toInt().coerceAtLeast(page.scrollY + 1)
                webView.evaluateJavascript("window.scrollTo(0, $nextY); 'ok';") {
                    webView.postDelayed({ captureProfileReviewsPage() }, PROFILE_REVIEW_SCROLL_SETTLE_MS)
                }
            }
        }
    }

    private fun finishPublicProfileSync(success: Boolean) {
        enterBrowserPhase(Phase.IDLE, null, "profile_sync_finished")
        UnifiedDebugEventStore.record(
            "PUBLIC_PROFILE_SYNC_FINISHED",
            packageName,
            "account=${account.displayLabel} success=$success reviews=${profileReviewsCollected.size} profileUuidPresent=${!account.profileUuid.isNullOrBlank()}",
        )
        setResult(
            if (success) RESULT_OK else RESULT_CANCELED,
            Intent()
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID, account.id)
                .putExtra("public_profile_review_count", profileReviewsCollected.size),
        )
        finish()
    }

    private fun probeIdentity() {
        evaluateRequest<DynamicIdentityEvidence>(BlaBlaBrowserRequest.SESSION_IDENTITY) { evidence ->
            if (evidence == null) return@evaluateRequest
            store.saveDiagnosticHtml(account, "profile", evidence.domHtml)
            val updated = bindIdentityFromLinks(evidence.profileLinks, evidence.visibleName)
            if (updated != null) {
                account = updated
                persistPublicProfileEvidence(evidence)
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
        evaluateRequest<DynamicRideList>(BlaBlaBrowserRequest.RIDE_LIST) { result ->
            if (result == null) {
                blockSyncWithoutCurrentCard("rides_dom_unreadable")
                return@evaluateRequest
            }
            store.saveDiagnosticHtml(account, "rides", result.domHtml)
            val visibleAll = result.candidates
                .filter { candidate ->
                    val href = candidate.href
                    BlaBlaCollectorUrlModule.isSpecificTrip(href)
                }
                .distinctBy { BlaBlaCollectorUrlModule.canonical(it.href) }
            val requestedDate = targetDate
            val visible = requestedDate?.let { date ->
                BlaBlaCollectorCardModule.candidatesOnDate(visibleAll, date)
            } ?: visibleAll
            UnifiedDebugEventStore.record(
                "RIDES_TRAVERSAL_SCAN",
                packageName,
                "account=${account.displayLabel} visible=${visibleAll.size} eligible=${visible.size} resolved=${resolvedCardTraversalKeys.size} completed=${completedCardTraversalKeys.size} quarantined=${quarantinedCardTraversalKeys.size} scrollY=${result.scrollY} scrollHeight=${result.scrollHeight} viewport=${result.viewportHeight} atBottom=${result.atBottom} pastDateFilter=${requestedDate != null} fixedTripLimit=${requestedDate != null} targetDate=${requestedDate ?: "none"}",
            )
            if (visibleAll.isEmpty() && rideReadAttempts < MAX_RIDES_EMPTY_READ_ATTEMPTS && !looksLoggedOut(result.bodyText)) {
                rideReadAttempts++
                webView.postDelayed({ captureRideList() }, 1200)
                return@evaluateRequest
            }
            if (visibleAll.isEmpty() && looksLoggedOut(result.bodyText)) {
                blockSyncWithoutCurrentCard("rides_session_logged_out")
                return@evaluateRequest
            }
            if (
                visibleAll.isEmpty() &&
                !BlaBlaCollectorCardModule.emptyListIsAuthoritative(result.explicitEmptyList)
            ) {
                blockSyncWithoutCurrentCard("rides_empty_without_explicit_terminal_evidence")
                return@evaluateRequest
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
                enterBrowserPhase(Phase.DETAIL, BlaBlaBrowserRequest.TRIP_OPEN, "open_next_trip")
                UnifiedDebugEventStore.record(
                    "CARD_TRAVERSAL_START",
                    packageName,
                    "account=${account.displayLabel} order=${resolvedCardTraversalKeys.size + 1} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(next.href).orEmpty()} uiOrder=true dateIgnored=${requestedDate == null} dateScope=${if (requestedDate == null) "all" else "today"} targetDate=${requestedDate ?: "none"}",
                )
                loadCurrentCandidate()
                return@evaluateRequest
            }
            if (visible.any { tripTraversalKey(it).isBlank() }) {
                blockSyncWithoutCurrentCard("visible_card_without_stable_identity")
                return@evaluateRequest
            }
            if (requestedDate != null) {
                val firstVisibleDate = visibleAll.firstOrNull()?.let { candidate ->
                    BlaBlaCollectorCardModule.candidateDate(candidate)
                }
                if (collected.isEmpty() && visible.isEmpty() && visibleAll.isNotEmpty() && firstVisibleDate == null) {
                    blockSyncWithoutCurrentCard("today_card_date_unreadable")
                    return@evaluateRequest
                }
                val verified = identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()
                saveFinalSnapshotOnce(verified)
                if (verified) {
                    UnifiedDebugEventStore.record(
                        "RIDES_TRAVERSAL_COMPLETE",
                        packageName,
                        "account=${account.displayLabel} resolvedCards=${resolvedCardTraversalKeys.size} completedCards=${completedCardTraversalKeys.size} quarantinedCards=${quarantinedCardTraversalKeys.size} pastDateFilter=true fixedTripLimit=true targetDate=$requestedDate noLaterCardsVisited=true",
                    )
                    completeSync(collected.size)
                } else {
                    blockSyncWithoutCurrentCard("identity_not_verified_after_today_card")
                }
                return@evaluateRequest
            }
            if (!result.atBottom) {
                val viewport = result.viewportHeight.coerceAtLeast(600)
                val maxScroll = (result.scrollHeight - 1).coerceAtLeast(0)
                val target = (result.scrollY + maxOf(600, viewport * 3 / 4)).coerceAtMost(maxScroll)
                if (target <= result.scrollY && result.scrollHeight > result.viewportHeight) {
                    blockSyncWithoutCurrentCard("rides_scroll_no_progress")
                    return@evaluateRequest
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
                return@evaluateRequest
            }
            if (ridesBottomStablePasses < REQUIRED_STABLE_BOTTOM_PASSES) {
                ridesBottomStablePasses++
                webView.postDelayed({ captureRideList() }, RIDES_BOTTOM_SETTLE_MS)
                return@evaluateRequest
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
        val tripId = BlaBlaTripIdentity.externalTripIdFromHref(candidate.href).orEmpty()
        networkDiagnosticRecorder?.beginFirstCard(tripId)
        networkDiagnosticRecorder?.markPhase(BlaBlaNetworkCapturePhase.CARD)
        statusView.text = "${account.displayLabel} • card ${resolvedCardTraversalKeys.size + 1} • lendo completo…"
        UnifiedDebugEventStore.record(
            "TRIP_DETAIL_REQUIRED",
            packageName,
            "account=${account.displayLabel} order=${resolvedCardTraversalKeys.size + 1} tripId=$tripId batchShortcut=false",
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
        evaluateRequest<DynamicTripDetail>(BlaBlaBrowserRequest.TRIP_DETAIL) { result ->
            detailCaptureInFlight = false
            if (!detailCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate)) {
                recordStale("trip_detail_after_evaluate", expectedSync, expectedCandidate)
                return@evaluateRequest
            }
            if (result == null) {
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} reason=detail_dom_unreadable url=${BlaBlaCollectorUrlModule.sanitizeForLog(webView.url.orEmpty())}",
                )
                advanceCandidate(expectedSync, expectedCandidate)
                return@evaluateRequest
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
                return@evaluateRequest
            }
            val networkResolution = BlaBlaCollectorNetworkSourceModule.resolve(candidateTripId, result.networkSource)
            val sourceBackedResult = (networkResolution?.let { resolution ->
                result.copy(
                    detail = result.detail.copy(
                        passengers = resolution.passengers,
                        passengerRosterComplete = true,
                    ),
                    passengerHrefs = resolution.passengers.mapNotNull { passenger -> passenger.booking_href },
                    explicitEmptyRoster = resolution.explicitEmpty,
                    rosterHasMore = false,
                    rosterTerminalEvidence = true,
                    itineraryStops = if (resolution.itineraryAuthoritative) {
                        resolution.itineraryStops
                    } else {
                        result.itineraryStops
                    },
                )
            } ?: result).let { source ->
                source.copy(
                    detail = source.detail.copy(
                        passengers = BlaBlaCollectorPassengerModule.coalesceDuplicateEvidence(source.detail.passengers),
                    ),
                    passengerHrefs = source.passengerHrefs.distinct(),
                )
            }
            if (networkResolution != null) {
                UnifiedDebugEventStore.record(
                    "BLABLACAR_NETWORK_SOURCE_APPLIED",
                    packageName,
                    "account=${account.displayLabel} tripId=$candidateTripId passengers=${networkResolution.passengers.size} seats=${networkResolution.passengers.sumOf { it.seats }} phones=${networkResolution.passengers.count { !it.phone.isNullOrBlank() }} fares=${networkResolution.bookings.count { it.fareMinorUnits != null }} addresses=${networkResolution.bookings.count { it.boardingAddress.isNotBlank() }} waypoints=${networkResolution.itineraryStops.size} itineraryAuthority=${networkResolution.itineraryAuthoritative} exactTrip=true rosterComplete=true piiLogged=false",
                )
            }
            val rosterSignature = directRosterSignature(sourceBackedResult)
            if (rosterSignature == lastTripRosterSignature) {
                tripRosterStablePasses++
            } else {
                lastTripRosterSignature = rosterSignature
                tripRosterStablePasses = 1
            }
            val awaitNetworkBeforeEmptyRoster = BlaBlaCollectorPassengerModule.shouldAwaitNetworkBeforeEmptyRoster(
                networkResolved = networkResolution != null,
                passengerCount = sourceBackedResult.detail.passengers.size,
                readAttempts = tripRosterReadAttempts,
                maxReadAttempts = MAX_TRIP_ROSTER_READ_ATTEMPTS,
            )
            val confirmedRosterComplete = networkResolution != null ||
                (!awaitNetworkBeforeEmptyRoster && BlaBlaCollectorPassengerModule.rosterCompleteAfterStableProbe(
                    passengerCount = sourceBackedResult.detail.passengers.size,
                    structurallyComplete = sourceBackedResult.detail.passengerRosterComplete,
                    explicitEmpty = sourceBackedResult.explicitEmptyRoster,
                    hasMore = sourceBackedResult.rosterHasMore,
                    terminalEvidence = sourceBackedResult.rosterTerminalEvidence,
                    stablePasses = tripRosterStablePasses,
                ))
            val acceptedResult = sourceBackedResult.copy(
                detail = sourceBackedResult.detail.copy(passengerRosterComplete = confirmedRosterComplete),
            )
            val rosterState = BlaBlaCollectorPassengerModule.rosterState(
                passengerCount = acceptedResult.detail.passengers.size,
                rosterComplete = acceptedResult.detail.passengerRosterComplete,
                explicitEmpty = acceptedResult.explicitEmptyRoster,
            )
            UnifiedDebugEventStore.record(
                "TRIP_ROSTER_PROBE",
                packageName,
                "account=${account.displayLabel} tripId=$candidateTripId attempt=${tripRosterReadAttempts + 1} passengerCards=${acceptedResult.detail.passengers.size} bookingLinks=${acceptedResult.passengerHrefs.count { !it.startsWith(CARD_TARGET_PREFIX) }} structuralComplete=${sourceBackedResult.detail.passengerRosterComplete} rosterComplete=${acceptedResult.detail.passengerRosterComplete} explicitEmpty=${acceptedResult.explicitEmptyRoster} hasMore=${acceptedResult.rosterHasMore} terminalEvidence=${acceptedResult.rosterTerminalEvidence} stablePasses=$tripRosterStablePasses networkSource=${networkResolution != null} waitingForNetwork=$awaitNetworkBeforeEmptyRoster state=$rosterState",
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
                        UnifiedDebugEventStore.record(
                            "ROSTER_EXPANSION_NOT_AUTOMATED",
                            packageName,
                            "account=${account.displayLabel} tripId=$candidateTripId reason=interaction_not_documented passiveRetry=true",
                        )
                    }
                    retryRoster()
                    return@evaluateRequest
                }
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} tripId=$candidateTripId reason=roster_unknown_after_probe attempts=${tripRosterReadAttempts + 1} action=skip_fail_closed",
                )
                advanceCandidate(expectedSync, expectedCandidate)
                return@evaluateRequest
            }
            if (BlaBlaHarvestPolicy.AUTOMATIC_PUBLISHED_SEAT_LOOKUP) {
                val editLinkMatches = BlaBlaHarvestAssociation.editPageMatches(candidateTripId, acceptedResult.editHref)
                if (!editLinkMatches && tripRosterReadAttempts < MAX_TRIP_ROSTER_READ_ATTEMPTS) {
                    tripRosterReadAttempts++
                    statusView.text = "${account.displayLabel} • vinculando edição ${tripRosterReadAttempts + 1}/$MAX_TRIP_ROSTER_READ_ATTEMPTS…"
                    webView.postDelayed({
                        captureTripDetail(expectedSync, expectedNavigation, expectedCandidate)
                    }, ROSTER_RETRY_MS)
                    return@evaluateRequest
                }
                if (!editLinkMatches) {
                    skipped++
                    UnifiedDebugEventStore.record(
                        "TRIP_REJECTED",
                        packageName,
                        "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} tripId=$candidateTripId reason=edit_link_missing_or_mismatch attempts=${tripRosterReadAttempts + 1} action=quarantine_and_continue",
                    )
                    advanceCandidate(expectedSync, expectedCandidate)
                    return@evaluateRequest
                }
            }
            tripRosterReadAttempts = 0
            store.saveDiagnosticHtml(account, "card-${resolvedCardTraversalKeys.size + 1}-trip", acceptedResult.domHtml)
            val expectedUuid = account.profileUuid?.lowercase()
            val trustedDriverLinks = BlaBlaCollectorIdentityModule.trustedDriverProfileLinks(
                expectedUuid = expectedUuid,
                authenticatedProfileSessionVerified = identityConfirmedThisSync,
                observedLinks = acceptedResult.driverProfileLinks,
            )
            val trustedDetailLinks = BlaBlaCollectorIdentityModule.trustedDriverProfileLinks(
                expectedUuid = expectedUuid,
                authenticatedProfileSessionVerified = identityConfirmedThisSync,
                observedLinks = acceptedResult.detail.profileLinks,
            )
            val identityAcceptedResult = acceptedResult.copy(
                detail = acceptedResult.detail.copy(profileLinks = trustedDetailLinks),
                driverProfileLinks = trustedDriverLinks,
            )
            val ignoredProfileLinks =
                (acceptedResult.driverProfileLinks.size - trustedDriverLinks.size).coerceAtLeast(0)
            val driverUuids = BlaBlaCollectorIdentityModule.uuids(trustedDriverLinks)
            UnifiedDebugEventStore.record(
                "TRIP_DETAIL_CAPTURED",
                packageName,
                "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} expectedUuid=${expectedUuid.orEmpty()} foundUuids=${driverUuids.joinToString(",")} ignoredNonDriverProfileLinks=$ignoredProfileLinks passengers=${identityAcceptedResult.detail.passengers.size} rosterComplete=${identityAcceptedResult.detail.passengerRosterComplete} networkSource=${networkResolution != null} editLinkPresent=${identityAcceptedResult.editHref.isNotBlank()} url=${BlaBlaCollectorUrlModule.sanitizeForLog(webView.url.orEmpty())}",
            )
            if (expectedUuid != null && driverUuids.isNotEmpty() && expectedUuid !in driverUuids) {
                skipped++
                UnifiedDebugEventStore.record(
                    "TRIP_REJECTED",
                    packageName,
                    "account=${account.displayLabel} index=${expectedCandidate + 1}/${candidates.size} reason=explicit_detail_uuid_mismatch expectedUuid=$expectedUuid foundUuids=${driverUuids.joinToString(",")}",
                )
                advanceCandidate(expectedSync, expectedCandidate)
                return@evaluateRequest
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
                return@evaluateRequest
            }

            val definition = account.verifiedDefinition()
            val preview = definition?.let {
                BlaBlaDomNormalizer.toTrip(
                    account = it,
                    candidate = candidate,
                    detail = identityAcceptedResult.detail,
                    today = LocalDate.now(),
                    authenticatedProfileSessionVerified = identityConfirmedThisSync,
                )
            }
            networkResolution?.let(::saveNetworkPassengerMetadata)
            pendingTripDetail = identityAcceptedResult
            pendingTripPassengers = (
                networkResolution?.passengers ?: preview?.passengers ?: identityAcceptedResult.detail.passengers
            ).toMutableList()
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
        if (href.isBlank() || !BlaBlaCollectorUrlModule.isAllowed(href)) return false
        val metadataKey = externalPassengerReservationKey(account.profileUuid, href)
        val metadata = passengerIdentityStore.externalMetadata(metadataKey)
        return passenger.phone.isNullOrBlank() || metadata?.fareMinorUnits == null || metadata?.boardingAddress.isNullOrBlank()
    }

    private fun loadNextPassengerContact(expectedSync: Long, expectedCandidate: Int) {
        if (!pendingTripIsCurrent(expectedSync, expectedCandidate)) {
            recordStale("passenger_load_pending_mismatch", expectedSync, expectedCandidate)
            return
        }
        if (pendingTripPassengers.isNotEmpty()) {
            networkDiagnosticRecorder?.markPhase(BlaBlaNetworkCapturePhase.PASSENGERS)
        }
        while (passengerContactIndex < pendingTripPassengers.size) {
            val passenger = pendingTripPassengers[passengerContactIndex]
            val href = passenger.booking_href?.trim().orEmpty()
            val hasBookingHref = href.isNotBlank() && BlaBlaCollectorUrlModule.isPassenger(href)
            val cardIndex = pendingTripPassengerCardIndexes[passengerContactIndex]
            when (
                BlaBlaCollectorPassengerNavigationModule.nextStep(
                    passengerPresent = true,
                    hasBookingHref = hasBookingHref,
                    needsReservationPage = hasBookingHref && passengerNeedsReservationPage(passenger),
                    hasPassengerCard = cardIndex != null,
                )
            ) {
                BlaBlaDirectPassengerStep.RESERVATION_URL -> {
                    enterBrowserPhase(Phase.PASSENGER_CONTACT, BlaBlaBrowserRequest.PASSENGER_OPEN, "open_passenger_reservation_url")
                    passengerContactReadAttempts = 0
                    passengerCallActionTriggered = false
                    interceptedPassengerPhone = null
                    passengerCaptureInFlight = false
                    statusView.text = "${account.displayLabel} • reserva ${passengerContactIndex + 1}/${pendingTripPassengers.size}…"
                    loadTrackedUrl(href)
                    return
                }
                BlaBlaDirectPassengerStep.PASSENGER_CARD -> {
                    enterBrowserPhase(Phase.PASSENGER_CARD, BlaBlaBrowserRequest.PASSENGER_OPEN, "open_passenger_card")
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
        if (BlaBlaHarvestPolicy.AUTOMATIC_PUBLISHED_SEAT_LOOKUP) {
            loadCurrentTripEdit(expectedSync, expectedCandidate)
        } else {
            UnifiedDebugEventStore.record(
                "AUTOMATIC_SEAT_LOOKUP_SKIPPED",
                packageName,
                "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} reason=policy_disabled manualSeatSyncPreserved=true",
            )
            finalizeCurrentTrip(expectedSync, expectedCandidate)
        }
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
        evaluateRequest<DynamicPassengerCardOpenState>(
            BlaBlaBrowserRequest.PASSENGER_OPEN,
            arguments = mapOf("PASSENGER_INDEX" to cardIndex.toString()),
        ) { state ->
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
                return@evaluateRequest
            }
            if (state?.clicked == true) {
                passengerCardReadAttempts = 0
                navigationGeneration++
                val passengerNavigation = navigationGeneration
                enterBrowserPhase(Phase.PASSENGER_CONTACT, BlaBlaBrowserRequest.PASSENGER_OPEN, "passenger_clicked_wait_navigation")
                statusView.text = "${account.displayLabel} • reserva ${expectedPassenger + 1}/${pendingTripPassengers.size}…"
                webView.postDelayed({
                    capturePassengerContactAfterNavigation(
                        expectedSync,
                        passengerNavigation,
                        expectedCandidate,
                        expectedPassenger,
                    )
                }, PASSENGER_NAVIGATION_SETTLE_MS)
                return@evaluateRequest
            }
            if (passengerCardReadAttempts < MAX_PASSENGER_CARD_READ_ATTEMPTS) {
                passengerCardReadAttempts++
                webView.postDelayed({
                    openPendingPassengerCard(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)
                }, ROSTER_RETRY_MS)
                return@evaluateRequest
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
        if (existingHref.isNotBlank()) return BlaBlaCollectorUrlModule.samePassengerPage(existingHref, webView.url.orEmpty())
        val cardIndex = pendingTripPassengerCardIndexes[expectedPassenger] ?: return false
        val actualHref = webView.url.orEmpty().takeIf(BlaBlaCollectorUrlModule::isPassenger) ?: return false
        val actualKey = BlaBlaCollectorUrlModule.passengerPageKey(actualHref)
        val duplicate = pendingTripPassengers.withIndex().any { (index, other) ->
            index != expectedPassenger &&
                !other.booking_href.isNullOrBlank() &&
                BlaBlaCollectorUrlModule.passengerPageKey(other.booking_href.orEmpty()) == actualKey
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
        evaluateRequest<DynamicPassengerContactEvidence>(BlaBlaBrowserRequest.PASSENGER_CONTACT) { evidence ->
            passengerCaptureInFlight = false
            if (!passengerCaptureIsCurrent(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger)) {
                recordStale("passenger_after_evaluate", expectedSync, expectedCandidate)
                return@evaluateRequest
            }
            if (evidence == null) {
                if (passengerContactReadAttempts < MAX_PASSENGER_EVIDENCE_READ_ATTEMPTS) {
                    passengerContactReadAttempts++
                    webView.postDelayed({ capturePassengerContact(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger) }, ROSTER_RETRY_MS)
                    return@evaluateRequest
                }
                skipped++
                blockCurrentCard(expectedSync, expectedCandidate, "passenger_evidence_unreadable")
                return@evaluateRequest
            }
            evidence.domHtml.takeIf(String::isNotBlank)?.let { html ->
                store.saveDiagnosticHtml(account, "card-${resolvedCardTraversalKeys.size + 1}-passenger-${expectedPassenger + 1}", html)
            }
            val effectivePhone = current.phone?.takeIf(String::isNotBlank)
                ?: BlaBlaCollectorPassengerModule.normalizePhone(evidence.phone)
                ?: interceptedPassengerPhone
            if (effectivePhone == null && evidence.callActionPresent) {
                UnifiedDebugEventStore.record(
                    "PASSENGER_CONTACT_NOT_VISIBLE",
                    packageName,
                    "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} passengerIndex=${expectedPassenger + 1}/${pendingTripPassengers.size} revealActionPresent=true revealAutomated=false reason=interaction_not_documented",
                )
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
                return@evaluateRequest
            }
            if (!requiredComplete) {
                UnifiedDebugEventStore.record(
                    "PASSENGER_EVIDENCE_INCOMPLETE",
                    packageName,
                    "account=${account.displayLabel} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(candidates[expectedCandidate].href).orEmpty()} passengerIndex=${expectedPassenger + 1}/${pendingTripPassengers.size} namePresent=${resolvedName.isNotBlank()} routePresent=$routePresent farePresent=$farePresent htmlPresent=$htmlPresent missing=${BlaBlaCollectorValueModule.missing(valueEvidence).joinToString(",")} action=block_card",
                )
                skipped++
                blockCurrentCard(expectedSync, expectedCandidate, "passenger_required_evidence_incomplete")
                return@evaluateRequest
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
        BlaBlaCollectorUrlModule.samePassengerPage(
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
        enterBrowserPhase(Phase.EDIT, BlaBlaBrowserRequest.TRIP_EDIT, "open_trip_edit")
        networkDiagnosticRecorder?.markPhase(BlaBlaNetworkCapturePhase.EDIT)
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
        evaluateRequest<DynamicEditEvidence>(BlaBlaBrowserRequest.TRIP_EDIT) { evidence ->
            editCaptureInFlight = false
            if (
                phase != Phase.EDIT ||
                expectedSync != syncGeneration ||
                expectedNavigation != navigationGeneration ||
                expectedCandidate != candidateIndex ||
                !pendingTripIsCurrent(expectedSync, expectedCandidate)
            ) {
                recordStale("edit_after_evaluate", expectedSync, expectedCandidate)
                return@evaluateRequest
            }
            val pageMatches = evidence != null && BlaBlaHarvestAssociation.editPageMatches(tripId, evidence.pageUrl)
            val optionsHref = evidence?.optionsHref?.trim().orEmpty()
            val optionsMatch = optionsHref.isNotBlank() && BlaBlaHarvestAssociation.optionsPageMatches(tripId, optionsHref)
            if ((!pageMatches || !optionsMatch) && editReadAttempts < MAX_EDIT_LINK_READ_ATTEMPTS) {
                editReadAttempts++
                webView.postDelayed({
                    captureEditEvidence(expectedSync, expectedNavigation, expectedCandidate)
                }, ROSTER_RETRY_MS)
                return@evaluateRequest
            }
            if (!pageMatches || !optionsMatch || evidence == null) {
                skipped++
                blockCurrentCard(expectedSync, expectedCandidate, "options_link_missing_or_mismatch")
                return@evaluateRequest
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
            enterBrowserPhase(Phase.OPTIONS, BlaBlaBrowserRequest.SEAT_OPTIONS, "open_seat_options")
            networkDiagnosticRecorder?.markPhase(BlaBlaNetworkCapturePhase.OPTIONS)
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
        evaluateRequest<SeatOptionState>(BlaBlaBrowserRequest.SEAT_OPTIONS) { evidence ->
            optionsCaptureInFlight = false
            if (
                phase != Phase.OPTIONS ||
                expectedSync != syncGeneration ||
                expectedNavigation != navigationGeneration ||
                expectedCandidate != candidateIndex ||
                !pendingTripIsCurrent(expectedSync, expectedCandidate)
            ) {
                recordStale("options_after_evaluate", expectedSync, expectedCandidate)
                return@evaluateRequest
            }
            val identityMatch = evidence != null && BlaBlaHarvestAssociation.optionsPageMatches(tripId, evidence.pageUrl)
            if ((evidence == null || evidence.seats < 0 || !identityMatch) && optionsReadAttempts < MAX_OPTIONS_READ_ATTEMPTS) {
                optionsReadAttempts++
                webView.postDelayed({
                    captureOptionsEvidence(expectedSync, expectedNavigation, expectedCandidate)
                }, ROSTER_RETRY_MS)
                return@evaluateRequest
            }
            if (evidence == null || evidence.seats < 0 || !identityMatch) {
                skipped++
                blockCurrentCard(expectedSync, expectedCandidate, "published_seats_unreadable_or_mismatched")
                return@evaluateRequest
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
        if (BlaBlaHarvestPolicy.AUTOMATIC_PUBLISHED_SEAT_LOOKUP) {
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
        val normalizedTrip = BlaBlaDomNormalizer.toTrip(
            account = definition,
            candidate = candidate,
            detail = enrichedDetail,
            today = LocalDate.now(),
            authenticatedProfileSessionVerified = identityConfirmedThisSync,
        )
        if (normalizedTrip == null || !identityConfirmedThisSync) {
            skipped++
            blockCurrentCard(expectedSync, expectedCandidate, "trip_fields_unparseable")
            return
        }
        val trip = normalizedTrip.copy(
            itinerary_stops = result.itineraryStops
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct(),
            published_seats = pendingPublishedSeats,
        )
        collected += trip
        UnifiedDebugEventStore.record(
            "TRIP_ACCEPTED",
            packageName,
            "account=${account.displayLabel} order=${resolvedCardTraversalKeys.size + 1} tripId=${trip.trip_id.orEmpty()} date=${trip.date} passengers=${trip.passengers.size} itineraryStops=${trip.itinerary_stops.size} rosterComplete=${trip.passenger_roster_complete} publishedSeats=${trip.published_seats ?: -1} sequential=true",
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
        if (targetTripId.isNotBlank()) {
            completedCardTraversalKeys += currentCardTraversalKey
            resolvedCardTraversalKeys += currentCardTraversalKey
            publishTargetedTripToTimeline(tripId)
            UnifiedDebugEventStore.record(
                "CARD_TRAVERSAL_COMPLETE",
                packageName,
                "account=${account.displayLabel} order=1 tripId=$tripId passengers=${pendingTripPassengers.size} publishedSeats=${pendingPublishedSeats ?: -1} result=targeted_complete nextCardAllowed=false",
            )
            networkDiagnosticRecorder?.finishFirstCard("targeted_complete")
            clearPendingCardState()
            currentCardTraversalKey = ""
            completeSync(collected.size)
            return
        }
        completedCardTraversalKeys += currentCardTraversalKey
        resolvedCardTraversalKeys += currentCardTraversalKey
        UnifiedDebugEventStore.record(
            "CARD_TRAVERSAL_COMPLETE",
            packageName,
            "account=${account.displayLabel} order=${resolvedCardTraversalKeys.size} tripId=$tripId passengers=${pendingTripPassengers.size} publishedSeats=${pendingPublishedSeats ?: -1} result=complete nextCardAllowed=true",
        )
        networkDiagnosticRecorder?.finishFirstCard("complete")
        saveProgressSnapshot("card_complete")
        clearPendingCardState()
        currentCardTraversalKey = ""
        candidates = emptyList()
        candidateIndex = 0
        enterBrowserPhase(Phase.RIDES, BlaBlaBrowserRequest.RIDE_LIST, "resume_ride_list_after_card")
        ridesRestorePending = ridesResumeScrollY > 0
        loadTrackedUrl(RIDES_URL)
    }

    private fun publishTargetedTripToTimeline(tripId: String) {
        val updatedTrip = collected.lastOrNull { it.trip_id == tripId } ?: return
        val timelineStore = BlaBlaCollectorStateStore(this)
        val previous = timelineStore.lastResponse() ?: return
        val merged = previous.trips.filterNot { trip ->
            trip.profile_uuid.equals(updatedTrip.profile_uuid, ignoreCase = true) && trip.trip_id == tripId
        } + updatedTrip
        timelineStore.saveResponse(
            previous.copy(
                status = "success",
                trips = merged.sortedWith(compareBy<BlaBlaCollectorTrip> { it.date }.thenBy { it.departure_time.orEmpty() }),
                coverage = previous.coverage.copy(
                    complete_for_scope = false,
                    global_profile_month_complete = false,
                    reason = "targeted_exact_card_sync",
                ),
            ),
            preserveOnPartial = false,
        )
        UnifiedDebugEventStore.record(
            "AGENDA_EXACT_CARD_TIMELINE_UPDATED",
            packageName,
            "profileUuidPresent=true tripIdPresent=true passengers=${updatedTrip.passengers.size}",
        )
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
            passengerRosterComplete = detail.detail.passengerRosterComplete,
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
        networkDiagnosticRecorder?.finishFirstCard("quarantined")
        saveProgressSnapshot("card_quarantined_$reason")
        clearPendingCardState()
        currentCardTraversalKey = ""
        candidates = emptyList()
        candidateIndex = 0
        enterBrowserPhase(Phase.RIDES, BlaBlaBrowserRequest.RIDE_LIST, "resume_ride_list_after_quarantine")
        ridesRestorePending = ridesResumeScrollY > 0
        statusView.text = "${account.displayLabel} • card não publicado: ${quarantineReasonLabel(reason)} • continuando…"
        loadTrackedUrl(RIDES_URL)
    }

    private fun quarantineReasonLabel(reason: String): String = when {
        "roster" in reason -> "passageiros não confirmados"
        "passenger" in reason -> "passageiro não confirmado"
        "seat" in reason || "options" in reason -> "lugares publicados não confirmados"
        "identity" in reason || "trip_id" in reason -> "conta ou viagem não confirmada"
        else -> "dados obrigatórios incompletos"
    }

    private fun blockSyncWithoutCurrentCard(reason: String) {
        skipped = maxOf(skipped, 1)
        networkDiagnosticRecorder?.finishFirstCard("sync_blocked")
        UnifiedDebugEventStore.record(
            "SYNC_BLOCKED",
            packageName,
            "account=${account.displayLabel} reason=$reason resolvedCards=${resolvedCardTraversalKeys.size} completedCards=${completedCardTraversalKeys.size} quarantinedCards=${quarantinedCardTraversalKeys.size} nextCardAllowed=false",
        )
        saveFinalSnapshotOnce(identityConfirmedThisSync && !account.profileUuid.isNullOrBlank())
        enterBrowserPhase(Phase.IDLE, null, "sync_blocked")
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
        return BlaBlaCollectorUrlModule.canonical(candidate.href).trim().takeIf(String::isNotBlank)?.let { "href|$it" }.orEmpty()
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

    private fun saveNetworkPassengerMetadata(resolution: BlaBlaNetworkTripResolution) {
        resolution.bookings.forEach { booking ->
            val href = booking.passenger.booking_href
            val key = externalPassengerReservationKey(account.profileUuid, href) ?: return@forEach
            val current = passengerIdentityStore.externalMetadata(key) ?: ExternalPassengerMetadata(reservationKey = key)
            val boardingAddress = booking.boardingAddress.ifBlank { booking.passenger.boarding.orEmpty() }.take(500)
            val dropoffAddress = booking.dropoffAddress.ifBlank { booking.passenger.dropoff.orEmpty() }.take(500)
            val latitude = validLatitude(booking.boardingLatitude)
            val longitude = validLongitude(booking.boardingLongitude)
            val hasCoordinates = latitude != null && longitude != null
            passengerIdentityStore.saveExternalMetadata(
            current.copy(
                externalPassengerId = booking.passengerId,
                externalTripId = resolution.tripId,
                externalProfileUuid = account.profileUuid.orEmpty(),
                fareMinorUnits = booking.fareMinorUnits ?: current.fareMinorUnits,
                    fareCurrencyCode = booking.fareCurrencyCode.ifBlank { current.fareCurrencyCode },
                    boardingAddress = boardingAddress.ifBlank { current.boardingAddress },
                    dropoffAddress = dropoffAddress.ifBlank { current.dropoffAddress },
                    boardingLatitude = if (hasCoordinates) latitude else current.boardingLatitude,
                    boardingLongitude = if (hasCoordinates) longitude else current.boardingLongitude,
                    boardingLocationSource = if (hasCoordinates) {
                        "blablacar_network_booking_pickup"
                    } else {
                        current.boardingLocationSource
                    },
                    boardingLocationCollectedAtMillis = if (hasCoordinates) {
                        System.currentTimeMillis()
                    } else {
                        current.boardingLocationCollectedAtMillis
                    },
                ),
            )
        }
        val blockedQueued = BlockedPassengerCancellationCoordinator.enqueueBlockedFromNetwork(this, account, resolution)
        if (blockedQueued > 0) {
            UnifiedDebugEventStore.record(
                "BLOCKED_PASSENGER_CANCEL_QUEUED",
                packageName,
                "account=${account.displayLabel} tripId=${resolution.tripId} count=$blockedQueued",
            )
        }
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

    private fun persistPublicProfileEvidence(evidence: DynamicIdentityEvidence) {
        val expectedUuid = account.profileUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return
        val observed = evidence.observedUuids.map(String::lowercase).toSet()
        if (expectedUuid !in observed) {
            UnifiedDebugEventStore.record(
                "PROFILE_UUID_MISMATCH",
                packageName,
                "account=${account.displayLabel} expectedUuidPresent=true observedCount=${observed.size}",
            )
            return
        }
        publicProfileStore.mergeConfirmed(
            account = account,
            capture = BlaBlaPublicProfileCapture(
                observedProfileUuid = expectedUuid,
                profileName = evidence.visibleName,
                photoUrl = evidence.photoUrl,
                about = evidence.about,
                rating = evidence.rating,
                reviewCount = evidence.reviewCount,
                badge = evidence.badge,
                vehicleMakeModel = evidence.vehicleMakeModel,
                vehicleColor = evidence.vehicleColor,
                amenities = evidence.amenities,
                preferences = evidence.preferences,
                reviews = evidence.reviews,
            ),
        )
    }

    private fun bindIdentityFromLinks(links: List<String>, visibleName: String): BlaBlaDynamicAccount? {
        val found = BlaBlaCollectorIdentityModule.uuids(links)
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

    private fun browserExecutionContext(): BlaBlaBrowserExecutionContext {
        val candidate = candidates.getOrNull(candidateIndex)
        val passenger = pendingTripPassengers.getOrNull(passengerContactIndex)
        return BlaBlaBrowserExecutionContext(
            accountId = account.id,
            expectedProfileUuid = account.profileUuid.orEmpty(),
            syncGeneration = syncGeneration,
            navigationGeneration = navigationGeneration,
            cardKey = currentCardTraversalKey,
            tripId = candidate?.let { BlaBlaTripIdentity.externalTripIdFromHref(it.href) }.orEmpty(),
            passengerKey = passenger?.booking_href?.let(BlaBlaCollectorUrlModule::passengerPageKey).orEmpty(),
            url = if (::webView.isInitialized) webView.url.orEmpty() else "",
        )
    }

    private inline fun <reified T> evaluateRequest(
        request: BlaBlaBrowserRequest,
        arguments: Map<String, String> = emptyMap(),
        crossinline callback: (T?) -> Unit,
    ) {
        val contextAtStart = browserExecutionContext()
        val previous = browserOrchestrator.current()
        val token = browserOrchestrator.startOrReuse(request, contextAtStart, reason = "webview_evaluate")
        if (previous?.generation != token.generation) {
            UnifiedDebugEventStore.record(
                "BROWSER_REQUEST_STARTED",
                packageName,
                "account=${account.displayLabel} request=${request.name} token=${token.generation} sync=${contextAtStart.syncGeneration} nav=${contextAtStart.navigationGeneration} tripId=${contextAtStart.tripId} passengerKeyPresent=${contextAtStart.passengerKey.isNotBlank()} mutates=${request.mutatesRemoteState}",
            )
        }
        val script = runCatching { browserScripts.script(request, arguments) }.getOrElse { error ->
            UnifiedDebugEventStore.record(
                "BROWSER_REQUEST_SCRIPT_ERROR",
                packageName,
                "account=${account.displayLabel} request=${request.name} error=${error.javaClass.simpleName}",
            )
            callback(null)
            return
        }
        evaluate<T>(script) { result ->
            val currentContext = browserExecutionContext()
            if (!browserOrchestrator.isCurrent(token, currentContext)) {
                UnifiedDebugEventStore.record(
                    "BROWSER_STALE_CALLBACK_IGNORED",
                    packageName,
                    "account=${account.displayLabel} request=${request.name} token=${token.generation} current=${browserOrchestrator.current()?.generation ?: -1L}",
                )
                return@evaluate
            }
            callback(result)
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

    private fun completeSync(count: Int) {
        if (!completionGate.claimCompletion(syncGeneration)) {
            UnifiedDebugEventStore.record(
                "STALE_CALLBACK_IGNORED",
                packageName,
                "account=${account.displayLabel} reason=duplicate_complete generation=$syncGeneration",
            )
            return
        }
        enterBrowserPhase(Phase.IDLE, null, "sync_complete")
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
        return value.takeIf(BlaBlaCollectorUrlModule::isManageTarget)
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
        networkDiagnosticRecorder?.finishFirstCard("activity_closed")
        networkDiagnosticRecorder?.close()
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    private fun currentTripMatchesCandidate(expectedCandidate: Int): Boolean {
        val candidate = candidates.getOrNull(expectedCandidate) ?: return false
        val expectedTripId = BlaBlaCollectorUrlModule.tripId(candidate.href) ?: return false
        val currentTripId = BlaBlaCollectorUrlModule.tripId(webView.url.orEmpty()) ?: return false
        return expectedTripId == currentTripId
    }

    private fun looksLoggedOut(text: String): Boolean {
        val normalized = text.lowercase()
        return normalized.contains("continuar com e-mail") || normalized.contains("como você deseja se conectar") || normalized.contains("como voce deseja se conectar")
    }

    private enum class Phase { IDLE, IDENTITY, PROFILE_PUBLIC, PROFILE_REVIEWS, RIDES, DETAIL, PASSENGER_CARD, PASSENGER_CONTACT, EDIT, OPTIONS }

    companion object {
        private const val HOME_URL = "https://www.blablacar.com.br/"
        private const val RIDES_URL = "https://www.blablacar.com.br/rides"
        private const val PROFILE_URL = "https://www.blablacar.com.br/dashboard/profile/menu"
        private const val MAX_RIDES_EMPTY_READ_ATTEMPTS = 3
        private const val MAX_PROFILE_REVIEW_READ_ATTEMPTS = 24
        private const val PROFILE_REVIEW_SCROLL_SETTLE_MS = 700L
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

    }
}
