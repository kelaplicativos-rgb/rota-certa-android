package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import kotlinx.serialization.serializer
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
    private val tenantScope = RotaCertaTenantRegistry(appContext).activeScope()
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val accountsKey = tenantScope.key(KEY_ACCOUNTS)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun list(): List<BlaBlaDynamicAccount> {
        val decoded = runCatching {
            json.decodeFromString<List<BlaBlaDynamicAccount>>(prefs.getString(accountsKey, "[]") ?: "[]")
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
        prefs.edit().putString(accountsKey, json.encodeToString(accounts)).apply()
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
    const val EXTRA_TARGET_DATES = "blablacar_target_dates"
    const val EXTRA_ENABLED_SCRIPTS_0449 = "blablacar_enabled_scripts_0449"
    const val EXTRA_AUTOMATIC_COLLECTION_GENERATION = "blablacar_automatic_collection_generation_0400"
    const val EXTRA_AUTOMATIC_COLLECTION_ORIGIN = "blablacar_automatic_collection_origin_0400"
    const val EXTRA_SYNC_FAILURE_0407 = "blablacar_sync_failure_0407"
    const val EXTRA_SOURCE_ACCESS_STATUS_0426 = "blablacar_source_access_status_0426"
    const val MODE_LOGIN = "login"
    const val MODE_SYNC = "sync"
    const val MODE_PROFILE = "profile"
    const val MODE_MANAGE = "manage"

    fun login(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_LOGIN)
    fun profile(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_PROFILE)
    fun sync(context: Context, account: BlaBlaDynamicAccount): Intent = intent(context, account, MODE_SYNC)
    internal fun syncPayload(account: BlaBlaDynamicAccount): Intent = Intent()
        .putExtra(EXTRA_ACCOUNT_ID, account.id)
        .putExtra(EXTRA_MODE, MODE_SYNC)
    fun syncToday(context: Context, account: BlaBlaDynamicAccount, targetDate: LocalDate): Intent =
        syncDates(context, account, listOf(targetDate))

    fun syncDates(
        context: Context,
        account: BlaBlaDynamicAccount,
        targetDates: Collection<LocalDate>,
    ): Intent = syncDatesInternal0449(context, account, targetDates, enabledScripts = null)

    internal fun syncDates(
        context: Context,
        account: BlaBlaDynamicAccount,
        targetDates: Collection<LocalDate>,
        enabledScripts: Collection<BlaBlaBrowserRequest>,
    ): Intent = syncDatesInternal0449(context, account, targetDates, enabledScripts)

    private fun syncDatesInternal0449(
        context: Context,
        account: BlaBlaDynamicAccount,
        targetDates: Collection<LocalDate>,
        enabledScripts: Collection<BlaBlaBrowserRequest>?,
    ): Intent {
        val result = intent(context, account, MODE_SYNC).putStringArrayListExtra(
            EXTRA_TARGET_DATES,
            ArrayList(targetDates.distinct().sorted().map(LocalDate::toString)),
        )
        if (enabledScripts != null) {
            result.putStringArrayListExtra(
                EXTRA_ENABLED_SCRIPTS_0449,
                ArrayList(enabledScripts.map(BlaBlaBrowserRequest::name).distinct().sorted()),
            )
        }
        return result
    }
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
private data class DynamicPageState0426(
    val state: String = "UNKNOWN",
    val url: String = "",
    val title: String = "",
    val error: Boolean = false,
    val bodyText: String = "",
)

@Serializable
private data class BlaBlaRestrictionDiagnosticEvidence0426(
    val schemaVersion: String = "blablacar-source-access-v1",
    val timestampMillis: Long,
    val profileKey: String,
    val sessionId: String,
    val traceId: String,
    val stage: String,
    val requestedUrl: String,
    val finalUrl: String,
    val httpStatus: Int,
    val errorType: String,
    val detector: String,
    val incidentReference: String,
    val previousRestrictionCount: Int,
    val millisSincePreviousNavigation: Long,
    val concurrentEquivalentOperation: Boolean,
    val circuitWasOpen: Boolean,
    val circuitIsOpen: Boolean,
    val javaScriptEnabled: Boolean,
    val domStorageEnabled: Boolean,
    val cookieProfileIsolated: Boolean,
    val rawCookieLogged: Boolean = false,
    val exceptionClass: String = "",
    val exceptionMessage: String = "",
    val rootCause: String = "",
)

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
    val publicTripHref: String = "",
    val publicTripHrefSource: String = "",
    val publicTripHrefBinding: String = "",
    val itineraryStops: List<String> = emptyList(),
    val itineraryAuthoritative: Boolean = false,
    val views: Int? = null,
    val domHtml: String = "",
)

internal data class ResolvedPublicTripLink0423(
    val href: String,
    val source: String,
    val binding: String,
)

internal fun bindOrchestratorPublicTripNavigation0443(
    rawUrl: String?,
    expectedAdministrativeTripId: String?,
    requestedAdministrativeTripId: String?,
): ResolvedPublicTripLink0423? {
    val expected = expectedAdministrativeTripId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val requested = requestedAdministrativeTripId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (expected != requested) return null
    val href = BlaBlaCollectorUrlModule.publicTripFromAuthoritativeOrchestratorNavigation(
        raw = rawUrl,
        expectedAdministrativeTripId = expected,
        boundAdministrativeTripId = requested,
    ) ?: return null
    return ResolvedPublicTripLink0423(
        href = href,
        source = "orchestrator_navigation",
        binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION,
    )
}

private data class BoundPublicTripNavigation0443(
    val syncGeneration: Long,
    val navigationGeneration: Long,
    val candidateIndex: Int,
    val administrativeTripId: String,
    val resolved: ResolvedPublicTripLink0423,
)

internal fun resolvePreferredPublicTripLink0423(
    network: ResolvedPublicTripLink0423?,
    passiveDom: ResolvedPublicTripLink0423?,
    persistedCanonical: ResolvedPublicTripLink0423?,
    authoritativeNavigation: ResolvedPublicTripLink0423? = null,
): ResolvedPublicTripLink0423? = network ?: authoritativeNavigation ?: passiveDom ?: persistedCanonical

@Serializable
private data class DynamicPublicTripShareEvidence(
    val tripId: String = "",
    val shareControlPresent: Boolean = false,
    val shareInterceptInstalled: Boolean = false,
    val shareInvoked: Boolean = false,
    val clickCount: Int = 0,
    val publicTripHref: String = "",
)

@Serializable
internal data class DynamicPublicSearchLinkCard(
    val driverName: String = "",
    val departureTime: String? = null,
    val actualDeparture: String? = null,
    val actualArrival: String? = null,
    val href: String? = null,
)

@Serializable
private data class DynamicPublicSearchLinkPage(
    val bodyText: String = "",
    val cards: List<DynamicPublicSearchLinkCard> = emptyList(),
)

private fun normalizedExactPublicSearchTime0448(raw: String?): String? {
    val match = Regex("(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)")
        .find(raw.orEmpty())
        ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    return hour.toString().padStart(2, '0') + ":" + match.groupValues[2]
}

internal fun resolveExactPublicSearchTripLink0448(
    expectedAdministrativeTripId: String?,
    expectedDriverName: String?,
    expectedDepartureTime: String?,
    cards: List<DynamicPublicSearchLinkCard>,
    providerOrigin: String?,
): ResolvedPublicTripLink0423? {
    val administrativeTripId = expectedAdministrativeTripId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val driverKey = BlaBlaPublicSearchPlanner.normalizePerson(expectedDriverName.orEmpty())
        .takeIf(String::isNotEmpty)
        ?: return null
    val departureTime = normalizedExactPublicSearchTime0448(expectedDepartureTime) ?: return null
    val origin = BlaBlaCollectorUrlModule.origin(providerOrigin) ?: return null

    val matchingHrefs = cards.mapNotNull { card ->
        if (BlaBlaPublicSearchPlanner.normalizePerson(card.driverName) != driverKey) return@mapNotNull null
        if (normalizedExactPublicSearchTime0448(card.departureTime) != departureTime) return@mapNotNull null
        val raw = card.href?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        val absolute = if (raw.startsWith('/')) "$origin$raw" else raw
        BlaBlaCollectorUrlModule.publicTripFromAuthoritativeOrchestratorNavigation(
            raw = absolute,
            expectedAdministrativeTripId = administrativeTripId,
            boundAdministrativeTripId = administrativeTripId,
        )
    }.distinct()

    val href = matchingHrefs.singleOrNull() ?: return null
    return ResolvedPublicTripLink0423(
        href = href,
        source = "exact_public_search",
        binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION,
    )
}

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

internal const val BLABLA_TRIP_DETAIL_CAPTURE_TIMEOUT_MS_0389 = 10_000L

internal fun blaBlaDynamicCollectionTimeoutMs0389(request: BlaBlaBrowserRequest): Long = when (request) {
    BlaBlaBrowserRequest.TRIP_DETAIL -> BLABLA_TRIP_DETAIL_CAPTURE_TIMEOUT_MS_0389
    else -> 0L
}

internal fun nextBlaBlaCandidateIndex(current: Int, size: Int): Int = when {
    size <= 0 -> 0
    current < 0 -> 0
    current >= size -> size
    else -> current + 1
}

class BlaBlaDynamicAccountSessionActivity : Activity() {
    private var controller: BlaBlaDynamicAccountSessionController0401? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = BlaBlaDynamicAccountSessionController0401(
            baseContext = this,
            launchIntent = intent,
            visualHost = { view -> setContentView(view) },
            finishHost = { resultCode, data ->
                setResult(resultCode, data)
                if (!isFinishing) finish()
            },
        ).also(BlaBlaDynamicAccountSessionController0401::start)
    }

    @Deprecated("Android framework API")
    override fun onBackPressed() {
        controller?.handleBackPressed() ?: super.onBackPressed()
    }

    override fun onDestroy() {
        controller?.destroy("visual_activity_destroyed")
        controller = null
        super.onDestroy()
    }
}

/** Existing authenticated collector state machine with a pluggable visual host. */
internal class BlaBlaDynamicAccountSessionController0401(
    baseContext: Context,
    private val launchIntent: Intent?,
    private val visualHost: ((android.view.View) -> Unit)?,
    private val finishHost: (Int, Intent) -> Unit,
) : ContextThemeWrapper(baseContext, baseContext.applicationInfo.theme) {
    private var pendingResultCode = Activity.RESULT_CANCELED
    private var pendingResultData = Intent()
    private var hostFinished = false
    private var destroyed = false
    private val intent: Intent? get() = launchIntent

    private fun setContentView(view: android.view.View) { visualHost?.invoke(view) }
    private fun setResult(resultCode: Int, data: Intent = Intent()) {
        pendingResultCode = resultCode
        pendingResultData = data
    }
    private fun finish() {
        if (hostFinished) return
        hostFinished = true
        finishHost(pendingResultCode, pendingResultData)
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var registry: BlaBlaDynamicAccountRegistry
    private lateinit var store: BlaBlaDynamicSessionStore
    private lateinit var passengerIdentityStore: PassengerIdentityStore
    private lateinit var publicProfileStore: BlaBlaPublicProfileStore
    private val browserOrchestrator = BlaBlaBrowserOrchestrator()
    private lateinit var account: BlaBlaDynamicAccount
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private var mode = BlaBlaDynamicSessionIntents.MODE_LOGIN
    private var targetTripId = ""
    private var targetTripHref = ""
    private var targetDates: List<LocalDate> = emptyList()
    private var scriptSelection0449 = BlaBlaDateScopeScriptSelection0449.legacyAll()
    // Compatibility projection for page-finished dispatch; script authority lives in browserOrchestrator.
    private var phase = Phase.IDLE
    private var candidates = emptyList<BlaBlaDomRideCandidate>()
    private val collected = mutableListOf<BlaBlaCollectorTrip>()
    private var candidateIndex = 0
    private var skipped = 0
    private var identityConfirmedThisSync = false
    private var rideReadAttempts = 0
    private var identityReadAttempts = 0
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
    private var networkTripSourceReadAttempts0407 = 0
    private var publicTripNavigation0443: BoundPublicTripNavigation0443? = null
    private var targetedSnapshotSaved0407 = false
    private var lastTripRosterSignature = ""
    private var tripRosterStablePasses = 0
    private var publicTripShareReadAttempts = 0
    private var publicTripShareCaptureInFlight = false
    private var publicTripSearchReadAttempts = 0
    private var publicTripSearchCaptureInFlight = false
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
    private var syncCrashGuard: AgendaSyncCrashGuard? = null
    private var automaticCollectionGeneration = 0L
    private var automaticCollectionClaimed = false
    private var automaticCollectionReported = false
    private var headlessPageFinishedNavigationGeneration0404 = -1L
    private val headlessDelayedHandler0405 = Handler(Looper.getMainLooper())
    private val internalSessionId0426 = UUID.randomUUID().toString()
    private var externalFlightLease0426: BlaBlaExternalFlightLease0426? = null
    private var lastMainFrameHttpStatus0426 = 0
    private var lastRequestedUrl0426 = ""
    private var lastExternalNavigationAtMillis0426 = 0L
    private var lastNavigationIntervalMillis0426 = -1L
    private var pageAccessInspectionInFlight0426 = false
    private var sourceAccessInspectedSyncGeneration0448 = Long.MIN_VALUE
    private var sourceAccessInspectedNavigationGeneration0448 = Long.MIN_VALUE
    private var restrictionHandledGeneration0426 = Long.MIN_VALUE


    fun start() {
        registry = BlaBlaDynamicAccountRegistry(this)
        store = BlaBlaDynamicSessionStore(this)
        passengerIdentityStore = PassengerIdentityStore(this)
        publicProfileStore = BlaBlaPublicProfileStore(this)
        account = registry.get(intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID)) ?: run {
            finish()
            return
        }
        mode = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_MODE) ?: BlaBlaDynamicSessionIntents.MODE_LOGIN
        automaticCollectionGeneration = intent?.getLongExtra(
            BlaBlaDynamicSessionIntents.EXTRA_AUTOMATIC_COLLECTION_GENERATION,
            0L,
        ) ?: 0L
        if (automaticCollectionGeneration > 0L) {
            automaticCollectionClaimed =
                mode == BlaBlaDynamicSessionIntents.MODE_SYNC &&
                    BlaBlaAutomaticCollectionCoordinator0400.claimHost(
                        context = this,
                        generation = automaticCollectionGeneration,
                        accountId = account.id,
                    )
            if (!automaticCollectionClaimed) {
                UnifiedDebugEventStore.record(
                    "BLABLACAR_AUTOMATIC_COLLECTION_STALE_LAUNCH_0400",
                    packageName,
                    "generation=$automaticCollectionGeneration accountKey=${seatSyncDiagnosticKey(account.id)} action=finish_without_collection",
                )
                finish()
                return
            }
        }
        if (mode == BlaBlaDynamicSessionIntents.MODE_SYNC) {
            AgendaSyncCrashTraceStore.arm(this)
            syncCrashGuard = AgendaSyncCrashGuard.install(this) { syncCrashSnapshot() }
            AgendaSyncCrashTraceStore.checkpoint(this, "activity_created")
        }
        targetTripId = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_TRIP_ID)?.trim().orEmpty()
        targetTripHref = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_URL)?.trim().orEmpty()
        val requestedDates = intent?.getStringArrayListExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_DATES)
            .orEmpty()
            .mapNotNull { raw -> runCatching { LocalDate.parse(raw.trim()) }.getOrNull() }
        val legacyTargetDate = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_DATE)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
        targetDates = (requestedDates + listOfNotNull(legacyTargetDate))
            .distinct()
            .sorted()
            .takeIf { mode == BlaBlaDynamicSessionIntents.MODE_SYNC }
            .orEmpty()
        val enabledScriptNames0449 = intent?.getStringArrayListExtra(
            BlaBlaDynamicSessionIntents.EXTRA_ENABLED_SCRIPTS_0449,
        )
        scriptSelection0449 = when {
            mode == BlaBlaDynamicSessionIntents.MODE_SYNC && targetDates.isNotEmpty() ->
                BlaBlaDateScopeScriptSelection0449.fromNames(enabledScriptNames0449)
            mode == BlaBlaDynamicSessionIntents.MODE_SYNC && automaticCollectionGeneration > 0L ->
                BlaBlaDateScopeScriptSelection0449.automaticAgendaListing0476()
            else -> BlaBlaDateScopeScriptSelection0449.legacyAll()
        }
        if (
            mode == BlaBlaDynamicSessionIntents.MODE_SYNC &&
            automaticCollectionGeneration > 0L &&
            targetDates.isEmpty()
        ) {
            UnifiedDebugEventStore.record(
                "BLABLACAR_AUTOMATIC_AGENDA_LISTING_0476",
                packageName,
                "generation=$automaticCollectionGeneration accountKey=${seatSyncDiagnosticKey(account.id)} coreTrip=true passengerEnrichment=false seatEnrichment=false publicUrlEnrichment=false preserveExistingEnrichment=true",
            )
        }
        if (mode != BlaBlaDynamicSessionIntents.MODE_SYNC || BlaBlaCollectorUrlModule.tripId(targetTripHref) != targetTripId) {
            targetTripId = ""
            targetTripHref = ""
        } else {
            targetDates = emptyList()
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            if (visualHost == null && mode == BlaBlaDynamicSessionIntents.MODE_SYNC) {
                if (automaticCollectionClaimed) {
                    automaticCollectionReported = true
                    BlaBlaAutomaticCollectionCoordinator0400.onAccountFinished(
                        context = this,
                        generation = automaticCollectionGeneration,
                        accountId = account.id,
                        accountResult = "partial",
                        error = "android_system_webview_multi_profile_unavailable",
                    )
                }
                UnifiedDebugEventStore.record(
                    "BLABLACAR_HEADLESS_WEBVIEW_UNAVAILABLE_0407",
                    packageName,
                    "accountKey=${seatSyncDiagnosticKey(account.id)} targeted=${targetTripId.isNotBlank()} action=fail_closed browserOpened=false",
                )
                setResult(
                    Activity.RESULT_CANCELED,
                    Intent()
                        .putExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID, account.id)
                        .putExtra(BlaBlaDynamicSessionIntents.EXTRA_SYNC_FAILURE_0407, "BROKEN_FOR_VERSION"),
                )
                finish()
                return
            }
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
            BlaBlaDynamicSessionIntents.MODE_MANAGE -> {
                if (acquireExternalFlight0426("manage_browser")) {
                    loadTrackedUrl(manageTargetUrl() ?: RIDES_URL)
                }
            }
            else -> {
                if (acquireExternalFlight0426("interactive_browser")) {
                    loadTrackedUrl(HOME_URL)
                }
            }
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
        action("Perfil") {
            if (acquireExternalFlight0426("interactive_profile")) loadTrackedUrl(PROFILE_URL)
        }
        action("Suas viagens") {
            if (acquireExternalFlight0426("interactive_rides")) loadTrackedUrl(RIDES_URL)
        }
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
        if (visualHost == null && mode == BlaBlaDynamicSessionIntents.MODE_SYNC) {
            webView.settings.loadsImagesAutomatically = false
            webView.settings.blockNetworkImage = true
            webView.settings.setSupportZoom(false)
            webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            UnifiedDebugEventStore.record(
                "BLABLACAR_HEADLESS_WEBVIEW_TUNED_0402",
                packageName,
                "images=false visualHost=false sameCollector=true activityLaunch=false",
            )
        }
        if (mode == BlaBlaDynamicSessionIntents.MODE_SYNC) {
            networkDiagnosticRecorder = browserOrchestrator.installNetworkEvidenceCapture(
                androidContext = this,
                webView = webView,
                accountId = account.id,
            )
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

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                captureAuthoritativePublicTripNavigation0443(url)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                super.onReceivedError(view, request, error)
                if (!request.isForMainFrame) return
                val target = request.url?.toString().orEmpty()
                UnifiedDebugEventStore.record(
                    "BLABLACAR_MAIN_FRAME_NETWORK_ERROR_0426",
                    packageName,
                    "accountKey=" + seatSyncDiagnosticKey(account.id) +
                        " phase=" + phase.name +
                        " code=" + error.errorCode +
                        " url=" + BlaBlaCollectorUrlModule.sanitizeForLog(target) +
                        " retryScheduled=false exceptionClass=WebResourceError exceptionMessage=" +
                        error.description?.toString().orEmpty().replace(Regex("\\s+"), " ").take(180) +
                        " rootCause=main_frame_transport_error",
                )
                handleMainFrameTransportFailure0426(error.errorCode, target)
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (!request.isForMainFrame) return
                lastMainFrameHttpStatus0426 = errorResponse.statusCode
                if (errorResponse.statusCode == 429) {
                    val detection = BlaBlaSourceAccessDetector0426.detect(
                        BlaBlaSourceAccessProbe0426(
                            finalUrl = request.url?.toString().orEmpty(),
                            httpStatus = errorResponse.statusCode,
                        ),
                    )
                    if (detection.temporarilyRestricted) {
                        handleTemporaryRestriction0426(
                            detection = detection,
                            finalUrl = request.url?.toString().orEmpty(),
                            stage = phase.name,
                        )
                    }
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (isAutomaticHeadless0404()) {
                    headlessPageFinishedNavigationGeneration0404 = navigationGeneration
                    UnifiedDebugEventStore.record(
                        "BLABLACAR_HEADLESS_PAGE_FINISHED_0404",
                        packageName,
                        "accountKey=" + seatSyncDiagnosticKey(account.id) +
                            " generation=" + automaticCollectionGeneration +
                            " navigation=" + navigationGeneration +
                            " phase=" + phase.name +
                            " allowed=" + BlaBlaCollectorUrlModule.isAllowed(url) +
                            " browserOpened=false",
                    )
                }
                inspectSourceAccess0426(view, url) {
                    dispatchPageFinished0426(view, url)
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun dispatchPageFinished0426(view: WebView, url: String) {
        if (mode != BlaBlaDynamicSessionIntents.MODE_SYNC) {
            statusView.text = account.displayLabel + " • " + url.take(110)
        }
        when (phase) {
            Phase.IDENTITY -> if (BlaBlaCollectorUrlModule.isAllowed(url)) postSessionDelayed0405({ captureIdentityForSync() }, 650)
            Phase.PROFILE_PUBLIC -> if (BlaBlaCollectorUrlModule.isAllowed(url)) postSessionDelayed0405({ capturePublicProfilePage() }, 850)
            Phase.PROFILE_REVIEWS -> if (BlaBlaCollectorUrlModule.isAllowed(url)) postSessionDelayed0405({ captureProfileReviewsPage() }, 850)
            Phase.RIDES -> if (BlaBlaCollectorUrlModule.isAllowed(url)) postSessionDelayed0405({ captureRideList() }, 900)
            Phase.DETAIL -> if (BlaBlaCollectorUrlModule.isAllowed(url)) scheduleTripDetailCapture(view)
            Phase.PUBLIC_SHARE -> if (BlaBlaCollectorUrlModule.isAllowed(url)) {
                val expectedSync = syncGeneration
                val expectedNavigation = navigationGeneration
                val expectedCandidate = candidateIndex
                postSessionDelayed0405({ capturePublicTripShare(expectedSync, expectedNavigation, expectedCandidate) }, 350)
            }
            Phase.PUBLIC_SEARCH_LINK -> if (BlaBlaCollectorUrlModule.isAllowed(url)) {
                val expectedSync = syncGeneration
                val expectedNavigation = navigationGeneration
                val expectedCandidate = candidateIndex
                postSessionDelayed0405({ capturePublicTripFromExactSearch(expectedSync, expectedNavigation, expectedCandidate) }, PUBLIC_TRIP_SEARCH_SETTLE_MS)
            }
            Phase.PASSENGER_CARD -> if (BlaBlaCollectorUrlModule.isAllowed(url)) schedulePassengerCardOpen(view)
            Phase.PASSENGER_CONTACT -> if (BlaBlaCollectorUrlModule.isAllowed(url)) schedulePassengerContactCapture(view)
            Phase.EDIT -> if (BlaBlaCollectorUrlModule.isAllowed(url)) scheduleEditCapture(view)
            Phase.OPTIONS -> if (BlaBlaCollectorUrlModule.isAllowed(url)) scheduleOptionsCapture(view)
            Phase.IDLE -> if (BlaBlaCollectorUrlModule.isAllowed(url)) postSessionDelayed0405({ probeIdentity() }, 500)
        }
    }

    private fun captureAuthoritativePublicTripNavigation0443(rawUrl: String?) {
        if (
            mode != BlaBlaDynamicSessionIntents.MODE_SYNC ||
            targetTripId.isBlank() ||
            (phase != Phase.DETAIL && phase != Phase.PUBLIC_SHARE)
        ) return
        val capturedSync = syncGeneration
        val capturedNavigation = navigationGeneration
        val expectedCandidate = candidateIndex
        val candidate = candidates.getOrNull(expectedCandidate) ?: return
        val administrativeTripId = BlaBlaTripIdentity.externalTripIdFromHref(candidate.href).orEmpty()
        if (administrativeTripId.isBlank() || administrativeTripId != targetTripId) return
        val requestedAdministrativeTripId = BlaBlaCollectorUrlModule.tripId(lastRequestedUrl0426)
        val resolved = bindOrchestratorPublicTripNavigation0443(
            rawUrl = rawUrl,
            expectedAdministrativeTripId = administrativeTripId,
            requestedAdministrativeTripId = requestedAdministrativeTripId,
        ) ?: return
        val existing = publicTripNavigation0443
        if (
            existing != null &&
            existing.syncGeneration == capturedSync &&
            existing.navigationGeneration == capturedNavigation &&
            existing.candidateIndex == expectedCandidate &&
            existing.administrativeTripId == administrativeTripId &&
            existing.resolved.href == resolved.href
        ) return

        publicTripNavigation0443 = BoundPublicTripNavigation0443(
            syncGeneration = capturedSync,
            navigationGeneration = capturedNavigation,
            candidateIndex = expectedCandidate,
            administrativeTripId = administrativeTripId,
            resolved = resolved,
        )
        val publicTripId = BlaBlaCollectorUrlModule.publicTripPublicId(resolved.href).orEmpty()
        val relation = if (publicTripId == administrativeTripId) "same" else "different"
        UnifiedDebugEventStore.record(
            "PUBLIC_TRIP_LINK_NAVIGATION_DISCOVERED",
            packageName,
            "account=${account.displayLabel} tripId=$administrativeTripId source=${resolved.source} sync=$capturedSync nav=$capturedNavigation candidate=${expectedCandidate + 1} publicIdRelation=$relation fingerprint=${publicTripHrefFingerprint0423(resolved.href)} piiLogged=false",
        )
        UnifiedDebugEventStore.record(
            "PUBLIC_TRIP_LINK_NAVIGATION_BOUND",
            packageName,
            "account=${account.displayLabel} tripId=$administrativeTripId binding=${resolved.binding} sync=$capturedSync nav=$capturedNavigation candidate=${expectedCandidate + 1} requestedAdministrativeTrip=true publicIdRelation=$relation fingerprint=${publicTripHrefFingerprint0423(resolved.href)}",
        )

        if (phase == Phase.PUBLIC_SHARE && pendingTripIsCurrent(capturedSync, expectedCandidate)) {
            pendingTripDetail = pendingTripDetail?.copy(
                publicTripHref = resolved.href,
                publicTripHrefSource = resolved.source,
                publicTripHrefBinding = resolved.binding,
            )
            UnifiedDebugEventStore.record(
                "PUBLIC_TRIP_LINK_CAPTURED",
                packageName,
                "account=${account.displayLabel} tripId=$administrativeTripId source=${resolved.source} binding=${resolved.binding} sync=$capturedSync nav=$capturedNavigation candidate=${expectedCandidate + 1} fingerprint=${publicTripHrefFingerprint0423(resolved.href)} networkFirst=false",
            )
            postSessionDelayed0405({
                if (
                    capturedSync == syncGeneration &&
                    capturedNavigation == navigationGeneration &&
                    phase == Phase.PUBLIC_SHARE &&
                    pendingTripIsCurrent(capturedSync, expectedCandidate) &&
                    pendingTripDetail?.publicTripHref == resolved.href
                ) {
                    loadNextPassengerContact(capturedSync, expectedCandidate)
                }
            }, 0L)
        }
    }

    private fun inspectSourceAccess0426(view: WebView, url: String, onAvailable: () -> Unit) {
        if (destroyed) return
        val expectedSync = syncGeneration
        val expectedNavigation = navigationGeneration
        if (
            sourceAccessInspectedSyncGeneration0448 == expectedSync &&
            sourceAccessInspectedNavigationGeneration0448 == expectedNavigation
        ) {
            onAvailable()
            return
        }
        if (pageAccessInspectionInFlight0426) return
        pageAccessInspectionInFlight0426 = true
        browserOrchestrator.executeCollectionStep(
            androidContext = this,
            webView = view,
            request = BlaBlaBrowserRequest.PAGE_STATE,
            executionContext = browserExecutionContext(),
            currentContext = ::browserExecutionContext,
            deserializer = serializer<DynamicPageState0426>(),
            reason = "source_access_probe_0426",
            timeoutMs = SOURCE_ACCESS_PROBE_TIMEOUT_MS_0447,
        ) { page ->
            pageAccessInspectionInFlight0426 = false
            if (
                destroyed ||
                expectedSync != syncGeneration ||
                expectedNavigation != navigationGeneration
            ) return@executeCollectionStep
            sourceAccessInspectedSyncGeneration0448 = expectedSync
            sourceAccessInspectedNavigationGeneration0448 = expectedNavigation
            val detection = BlaBlaSourceAccessDetector0426.detect(
                BlaBlaSourceAccessProbe0426(
                    finalUrl = page?.url?.takeIf(String::isNotBlank) ?: url,
                    title = page?.title.orEmpty(),
                    bodyText = page?.bodyText.orEmpty(),
                    httpStatus = lastMainFrameHttpStatus0426,
                ),
            )
            if (detection.temporarilyRestricted) {
                handleTemporaryRestriction0426(
                    detection = detection,
                    finalUrl = page?.url?.takeIf(String::isNotBlank) ?: url,
                    stage = phase.name,
                )
                return@executeCollectionStep
            }
            if (
                page != null &&
                automaticCollectionGeneration <= 0L &&
                store.isSourceCircuitOpen0426(account)
            ) {
                store.markSourceAvailable0426(account, page?.url?.takeIf(String::isNotBlank) ?: url)
                UnifiedDebugEventStore.record(
                    "BLABLACAR_SOURCE_ACCESS_RECOVERED_0426",
                    packageName,
                    "accountKey=" + seatSyncDiagnosticKey(account.profileUuid ?: account.id) +
                        " sessionId=" + internalSessionId0426 +
                        " recovery=user_controlled circuitClosed=true automaticRetry=false",
                )
            }
            onAvailable()
        }
    }

    private fun handleTemporaryRestriction0426(
        detection: BlaBlaSourceAccessDetection0426,
        finalUrl: String,
        stage: String,
    ) {
        if (
            restrictionHandledGeneration0426 == syncGeneration &&
            phase == Phase.IDLE &&
            store.isSourceCircuitOpen0426(account)
        ) return
        restrictionHandledGeneration0426 = syncGeneration
        val previous = store.read(account)
        val circuitWasOpen = previous?.sourceAccessStatus0426 == BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED
        val previousCount = previous?.sourceRestrictionCount0426 ?: 0
        if (::webView.isInitialized) webView.stopLoading()
        enterBrowserPhase(Phase.IDLE, null, "temporarily_restricted_0426")
        val saved = store.markTemporarilyRestricted0426(account, finalUrl, detection)
        val evidence = BlaBlaRestrictionDiagnosticEvidence0426(
            timestampMillis = System.currentTimeMillis(),
            profileKey = seatSyncDiagnosticKey(account.profileUuid ?: account.id),
            sessionId = internalSessionId0426,
            traceId = internalSessionId0426 + ":" + syncGeneration,
            stage = stage.take(80),
            requestedUrl = BlaBlaCollectorUrlModule.sanitizeForLog(lastRequestedUrl0426),
            finalUrl = BlaBlaCollectorUrlModule.sanitizeForLog(finalUrl),
            httpStatus = detection.httpStatus,
            errorType = "TEMPORARILY_RESTRICTED",
            detector = detection.detector,
            incidentReference = detection.incidentReference,
            previousRestrictionCount = previousCount,
            millisSincePreviousNavigation = lastNavigationIntervalMillis0426,
            concurrentEquivalentOperation = false,
            circuitWasOpen = circuitWasOpen,
            circuitIsOpen = saved.sourceAccessStatus0426 == BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED,
            javaScriptEnabled = webView.settings.javaScriptEnabled,
            domStorageEnabled = webView.settings.domStorageEnabled,
            cookieProfileIsolated = true,
        )
        UnifiedDebugEventStore.record(
            "BLABLACAR_SOURCE_ACCESS_RESTRICTED_0426",
            packageName,
            json.encodeToString(evidence),
        )
        statusView.text =
            "BlaBlaCar restringiu temporariamente esta sessão. Seus últimos dados válidos foram preservados. " +
                "A sincronização será retomada após a sessão voltar a ficar disponível."
        if (automaticCollectionClaimed && !automaticCollectionReported) {
            automaticCollectionReported = true
            BlaBlaAutomaticCollectionCoordinator0400.onAccountTemporarilyRestricted0426(
                context = this,
                generation = automaticCollectionGeneration,
                accountId = account.id,
                reason = detection.detector,
            )
        }
        releaseExternalFlight0426()
        setResult(
            Activity.RESULT_CANCELED,
            Intent()
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID, account.id)
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_SYNC_FAILURE_0407, "TEMPORARILY_RESTRICTED")
                .putExtra(
                    BlaBlaDynamicSessionIntents.EXTRA_SOURCE_ACCESS_STATUS_0426,
                    BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED.name,
                ),
        )
        if (mode != BlaBlaDynamicSessionIntents.MODE_LOGIN && mode != BlaBlaDynamicSessionIntents.MODE_MANAGE) {
            finish()
        }
    }

    private fun handleMainFrameTransportFailure0426(errorCode: Int, targetUrl: String) {
        if (phase == Phase.IDLE) {
            statusView.text =
                account.displayLabel + " • BlaBlaCar não carregou. Use a navegação novamente quando a conexão estiver disponível."
            return
        }
        if (mode != BlaBlaDynamicSessionIntents.MODE_SYNC && mode != BlaBlaDynamicSessionIntents.MODE_PROFILE) return
        enterBrowserPhase(Phase.IDLE, null, "main_frame_transport_failure_0426")
        statusView.text = account.displayLabel + " • falha temporária de conexão; dados anteriores preservados."
        if (automaticCollectionClaimed && !automaticCollectionReported) {
            automaticCollectionReported = true
            BlaBlaAutomaticCollectionCoordinator0400.onAccountTransientFailure0426(
                context = this,
                generation = automaticCollectionGeneration,
                accountId = account.id,
                reason = "main_frame_error_" + errorCode,
            )
        }
        releaseExternalFlight0426()
        setResult(
            Activity.RESULT_CANCELED,
            Intent()
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID, account.id)
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_SYNC_FAILURE_0407, "NETWORK_ERROR"),
        )
        if (mode != BlaBlaDynamicSessionIntents.MODE_LOGIN && mode != BlaBlaDynamicSessionIntents.MODE_MANAGE) {
            finish()
        }
    }

    private fun acquireExternalFlight0426(operation: String): Boolean {
        if (phase != Phase.IDLE) {
            UnifiedDebugEventStore.record(
                "BLABLACAR_PROFILE_SINGLE_FLIGHT_DEDUPED_0426",
                packageName,
                "accountKey=" + seatSyncDiagnosticKey(account.profileUuid ?: account.id) +
                    " operation=" + operation.take(80) +
                    " phase=" + phase.name +
                    " owner=current_session action=ignore_duplicate_trigger",
            )
            statusView.text = account.displayLabel + " • já existe uma operação BlaBlaCar em andamento nesta sessão."
            return false
        }
        if (externalFlightLease0426 != null) {
            return true
        }
        val token = internalSessionId0426 + ":" + operation + ":" + (syncGeneration + 1L)
        val lease = store.tryAcquireExternalFlight0426(account, token)
        if (lease != null) {
            externalFlightLease0426 = lease
            return true
        }
        UnifiedDebugEventStore.record(
            "BLABLACAR_PROFILE_SINGLE_FLIGHT_DEDUPED_0426",
            packageName,
            "accountKey=" + seatSyncDiagnosticKey(account.profileUuid ?: account.id) +
                " operation=" + operation.take(80) +
                " action=no_second_external_chain",
        )
        statusView.text = account.displayLabel + " • já existe uma sincronização desta conta em andamento."
        setResult(
            Activity.RESULT_CANCELED,
            Intent()
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID, account.id)
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_SYNC_FAILURE_0407, "SINGLE_FLIGHT_BUSY"),
        )
        if (mode == BlaBlaDynamicSessionIntents.MODE_SYNC || mode == BlaBlaDynamicSessionIntents.MODE_PROFILE) finish()
        return false
    }

    private fun releaseExternalFlight0426() {
        store.releaseExternalFlight0426(externalFlightLease0426)
        externalFlightLease0426 = null
    }

    private fun completeAutomaticCircuitOpen0426() {
        val snapshot = store.read(account)
        UnifiedDebugEventStore.record(
            "BLABLACAR_SOURCE_CIRCUIT_OPEN_SKIP_0426",
            packageName,
            "accountKey=" + seatSyncDiagnosticKey(account.profileUuid ?: account.id) +
                " restrictionCount=" + (snapshot?.sourceRestrictionCount0426 ?: 0) +
                " automatic=true externalNavigationStarted=false previousSnapshotPreserved=true",
        )
        if (automaticCollectionClaimed && !automaticCollectionReported) {
            automaticCollectionReported = true
            BlaBlaAutomaticCollectionCoordinator0400.onAccountTemporarilyRestricted0426(
                context = this,
                generation = automaticCollectionGeneration,
                accountId = account.id,
                reason = "circuit_open_skip",
            )
        }
        setResult(
            Activity.RESULT_CANCELED,
            Intent()
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID, account.id)
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_SYNC_FAILURE_0407, "TEMPORARILY_RESTRICTED")
                .putExtra(
                    BlaBlaDynamicSessionIntents.EXTRA_SOURCE_ACCESS_STATUS_0426,
                    BlaBlaSourceAccessStatus0426.TEMPORARILY_RESTRICTED.name,
                ),
        )
        finish()
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
        if (mode == BlaBlaDynamicSessionIntents.MODE_SYNC) {
            AgendaSyncCrashTraceStore.checkpoint(
                this,
                "phase=${phaseValue.name} request=${request?.name ?: "none"} reason=${reason.take(80)}",
            )
        }
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
        if (!acquireExternalFlight0426("profile_sync")) return
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
        if (isAutomaticHeadless0404() && store.isSourceCircuitOpen0426(account)) {
            completeAutomaticCircuitOpen0426()
            return
        }
        if (!acquireExternalFlight0426("trip_sync")) return
        syncGeneration++
        networkDiagnosticRecorder?.startSync(syncGeneration)
        navigationGeneration = 0L
        headlessPageFinishedNavigationGeneration0404 = -1L
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
        identityReadAttempts = 0
        completedCardTraversalKeys.clear()
        quarantinedCardTraversalKeys.clear()
        resolvedCardTraversalKeys.clear()
        currentCardTraversalKey = ""
        ridesResumeScrollY = 0
        ridesRestorePending = false
        ridesBottomStablePasses = 0
        tripRosterReadAttempts = 0
        networkTripSourceReadAttempts0407 = 0
        publicTripNavigation0443 = null
        targetedSnapshotSaved0407 = false
        lastTripRosterSignature = ""
        tripRosterStablePasses = 0
        publicTripShareReadAttempts = 0
        publicTripShareCaptureInFlight = false
        publicTripSearchReadAttempts = 0
        publicTripSearchCaptureInFlight = false
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
            "account=${account.displayLabel} expectedUuid=${account.profileUuid.orEmpty()} url=${BlaBlaCollectorUrlModule.sanitizeForLog(PROFILE_URL)} dateScopeCount=${targetDates.size} dateScopeStart=${targetDates.firstOrNull() ?: "none"} dateScopeEnd=${targetDates.lastOrNull() ?: "none"} selectiveScripts=${scriptSelection0449.selective} requestedScripts=${scriptSelection0449.requestedNames().joinToString(",")}",
        )
        loadTrackedUrl(PROFILE_URL)
    }

    private fun loadTrackedUrl(url: String) {
        val now = System.currentTimeMillis()
        lastNavigationIntervalMillis0426 =
            if (lastExternalNavigationAtMillis0426 > 0L) now - lastExternalNavigationAtMillis0426 else -1L
        lastExternalNavigationAtMillis0426 = now
        lastRequestedUrl0426 = url
        lastMainFrameHttpStatus0426 = 0
        navigationGeneration++
        val expectedNavigation = navigationGeneration
        webView.loadUrl(url)
        scheduleHeadlessPageFallback0404(expectedNavigation)
    }

    private fun isAutomaticHeadless0404(): Boolean =
        visualHost == null &&
            mode == BlaBlaDynamicSessionIntents.MODE_SYNC

    private fun postSessionDelayed0405(action: () -> Unit, delayMs: Long) {
        if (destroyed) return
        val guarded = Runnable {
            if (!destroyed) action()
        }
        if (isAutomaticHeadless0404()) {
            headlessDelayedHandler0405.postDelayed(guarded, delayMs)
        } else {
            webView.postDelayed(guarded, delayMs)
        }
    }

    private fun scheduleHeadlessPageFallback0404(expectedNavigation: Long) {
        if (!isAutomaticHeadless0404()) return
        val expectedSync = syncGeneration
        val expectedPhase = phase
        postSessionDelayed0405({
            if (
                destroyed ||
                !isAutomaticHeadless0404() ||
                expectedSync != syncGeneration ||
                expectedNavigation != navigationGeneration ||
                expectedPhase != phase ||
                headlessPageFinishedNavigationGeneration0404 == expectedNavigation
            ) {
                return@postSessionDelayed0405
            }
            UnifiedDebugEventStore.record(
                "BLABLACAR_HEADLESS_PAGE_FALLBACK_0404",
                packageName,
                "accountKey=${seatSyncDiagnosticKey(account.id)} generation=$automaticCollectionGeneration navigation=$expectedNavigation phase=${expectedPhase.name} progress=${webView.progress} urlAllowed=${BlaBlaCollectorUrlModule.isAllowed(webView.url.orEmpty())} action=phase_probe browserOpened=false",
            )
            inspectSourceAccess0426(webView, webView.url.orEmpty()) {
                when (expectedPhase) {
                    Phase.IDENTITY -> captureIdentityForSync()
                    Phase.RIDES -> captureRideList()
                    Phase.DETAIL -> scheduleTripDetailCapture(webView)
                    Phase.PUBLIC_SHARE -> capturePublicTripShare(expectedSync, expectedNavigation, candidateIndex)
                    Phase.PUBLIC_SEARCH_LINK -> capturePublicTripFromExactSearch(expectedSync, expectedNavigation, candidateIndex)
                    Phase.PASSENGER_CARD -> schedulePassengerCardOpen(webView)
                    Phase.PASSENGER_CONTACT -> schedulePassengerContactCapture(webView)
                    Phase.EDIT -> scheduleEditCapture(webView)
                    Phase.OPTIONS -> scheduleOptionsCapture(webView)
                    else -> Unit
                }
            }
        }, HEADLESS_PAGE_CALLBACK_FALLBACK_MS_0404)
    }

    private fun scheduleTripDetailCapture(view: WebView) {
        val expectedSync = syncGeneration
        val expectedNavigation = navigationGeneration
        val expectedCandidate = candidateIndex
        postSessionDelayed0405({ captureTripDetail(expectedSync, expectedNavigation, expectedCandidate) }, 750)
    }

    private fun schedulePassengerCardOpen(view: WebView) {
        val expectedSync = syncGeneration
        val expectedNavigation = navigationGeneration
        val expectedCandidate = candidateIndex
        val expectedPassenger = passengerContactIndex
        postSessionDelayed0405({ openPendingPassengerCard(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger) }, 600)
    }

    private fun schedulePassengerContactCapture(view: WebView) {
        val expectedSync = syncGeneration
        val expectedNavigation = navigationGeneration
        val expectedCandidate = candidateIndex
        val expectedPassenger = passengerContactIndex
        postSessionDelayed0405({ capturePassengerContactAfterNavigation(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger) }, 850)
    }

    private fun scheduleEditCapture(view: WebView) {
        val expectedSync = syncGeneration
        val expectedNavigation = navigationGeneration
        val expectedCandidate = candidateIndex
        postSessionDelayed0405({ captureEditEvidence(expectedSync, expectedNavigation, expectedCandidate) }, 850)
    }

    private fun scheduleOptionsCapture(view: WebView) {
        val expectedSync = syncGeneration
        val expectedNavigation = navigationGeneration
        val expectedCandidate = candidateIndex
        postSessionDelayed0405({ captureOptionsEvidence(expectedSync, expectedNavigation, expectedCandidate) }, 850)
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
            if (isAutomaticHeadless0404() && mode == BlaBlaDynamicSessionIntents.MODE_SYNC && !identityConfirmedThisSync) {
                identityReadAttempts++
                if (identityReadAttempts < MAX_IDENTITY_READ_ATTEMPTS) {
                    UnifiedDebugEventStore.record(
                        "BLABLACAR_AUTOMATIC_AUTH_RETRY_0401", packageName,
                        "accountKey=${seatSyncDiagnosticKey(account.id)} generation=$automaticCollectionGeneration attempt=$identityReadAttempts/$MAX_IDENTITY_READ_ATTEMPTS visibleUi=false",
                    )
                    postSessionDelayed0405({ captureIdentityForSync() }, IDENTITY_RETRY_MS)
                    return@evaluateRequest
                }
                completeAutomaticAuthenticationRequired("authenticated_profile_identity_not_verified")
                return@evaluateRequest
            }
            identityReadAttempts = 0
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
                val hydration0451 = rehydrateExactCandidate0451(
                    targetHref = targetTripHref,
                    profileUuid = account.profileUuid.orEmpty(),
                    tripId = targetTripId,
                    canonicalTrips = TripStore(this).trips(),
                    dynamicTrips = store.read(account)?.trips.orEmpty(),
                    collectorTrips = BlaBlaCollectorStateStore(this).lastResponse()?.trips.orEmpty(),
                )
                val exactCandidate = hydration0451.candidate
                candidates = listOf(exactCandidate)
                candidateIndex = 0
                enterBrowserPhase(Phase.DETAIL, BlaBlaBrowserRequest.TRIP_OPEN, "exact_trip_open")
                UnifiedDebugEventStore.record(
                    "BLABLACAR_EXACT_CANDIDATE_REHYDRATED_0451",
                    packageName,
                    "account=${account.displayLabel} tripKey=${seatSyncDiagnosticKey(targetTripId)} strongIdentity=true source=${hydration0451.sourceChain.joinToString("+").ifBlank { "none" }} datePresent=${exactCandidate.dateText.isNotBlank()} timePresent=${exactCandidate.departureTime.isNotBlank()} originPresent=${exactCandidate.origin.isNotBlank()} destinationPresent=${exactCandidate.destination.isNotBlank()} hrefOnly=${exactCandidate.dateText.isBlank() && exactCandidate.departureTime.isBlank() && exactCandidate.origin.isBlank() && exactCandidate.destination.isBlank()}",
                )
                UnifiedDebugEventStore.record(
                    "AGENDA_EXACT_CARD_SYNC_STARTED",
                    packageName,
                    "account=${account.displayLabel} profileUuidPresent=${!account.profileUuid.isNullOrBlank()} tripIdPresent=true directTarget=true contextRehydrated=${hydration0451.sourceChain.isNotEmpty()}",
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
            setResult(Activity.RESULT_CANCELED, Intent().putExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID, account.id))
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
                    postSessionDelayed0405({ captureProfileReviewsPage() }, PROFILE_REVIEW_SCROLL_SETTLE_MS)
                }
            }
        }
    }

    private fun finishPublicProfileSync(success: Boolean) {
        releaseExternalFlight0426()
        enterBrowserPhase(Phase.IDLE, null, "profile_sync_finished")
        UnifiedDebugEventStore.record(
            "PUBLIC_PROFILE_SYNC_FINISHED",
            packageName,
            "account=${account.displayLabel} success=$success reviews=${profileReviewsCollected.size} profileUuidPresent=${!account.profileUuid.isNullOrBlank()}",
        )
        setResult(
            if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED,
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
                postSessionDelayed0405({ captureRideList() }, RIDES_SCROLL_SETTLE_MS)
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
            val requestedDates = targetDates.toSet().takeIf { it.isNotEmpty() }
            val visible = requestedDates?.let { dates ->
                BlaBlaCollectorCardModule.candidatesOnDates(visibleAll, dates)
            } ?: visibleAll
            UnifiedDebugEventStore.record(
                "RIDES_TRAVERSAL_SCAN",
                packageName,
                "account=${account.displayLabel} visible=${visibleAll.size} eligible=${visible.size} resolved=${resolvedCardTraversalKeys.size} completed=${completedCardTraversalKeys.size} quarantined=${quarantinedCardTraversalKeys.size} scrollY=${result.scrollY} scrollHeight=${result.scrollHeight} viewport=${result.viewportHeight} atBottom=${result.atBottom} dateFilter=${requestedDates != null} dateScopeCount=${requestedDates?.size ?: 0} targetStart=${requestedDates?.minOrNull() ?: "none"} targetEnd=${requestedDates?.maxOrNull() ?: "none"}",
            )
            if (visibleAll.isEmpty() && rideReadAttempts < MAX_RIDES_EMPTY_READ_ATTEMPTS && !looksLoggedOut(result.bodyText)) {
                rideReadAttempts++
                postSessionDelayed0405({ captureRideList() }, 1200)
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
                    "account=${account.displayLabel} order=${resolvedCardTraversalKeys.size + 1} tripId=${BlaBlaTripIdentity.externalTripIdFromHref(next.href).orEmpty()} uiOrder=true dateIgnored=${requestedDates == null} dateScope=${if (requestedDates == null) "all" else "selected"} targetStart=${requestedDates?.minOrNull() ?: "none"} targetEnd=${requestedDates?.maxOrNull() ?: "none"} outOfScopeCardOpened=false",
                )
                loadCurrentCandidate()
                return@evaluateRequest
            }
            if (visible.any { tripTraversalKey(it).isBlank() }) {
                blockSyncWithoutCurrentCard("visible_card_without_stable_identity")
                return@evaluateRequest
            }
            if (requestedDates != null) {
                val firstVisibleDate = visibleAll.firstOrNull()?.let { candidate ->
                    BlaBlaCollectorCardModule.candidateDate(candidate)
                }
                if (collected.isEmpty() && visible.isEmpty() && visibleAll.isNotEmpty() && firstVisibleDate == null) {
                    blockSyncWithoutCurrentCard("selected_scope_card_date_unreadable")
                    return@evaluateRequest
                }
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
                    postSessionDelayed0405({ captureRideList() }, RIDES_SCROLL_SETTLE_MS)
                }
                return@evaluateRequest
            }
            if (ridesBottomStablePasses < REQUIRED_STABLE_BOTTOM_PASSES) {
                ridesBottomStablePasses++
                postSessionDelayed0405({ captureRideList() }, RIDES_BOTTOM_SETTLE_MS)
                return@evaluateRequest
            }
            val verified = identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()
            saveFinalSnapshotOnce(verified)
            if (verified) {
                UnifiedDebugEventStore.record(
                    "RIDES_TRAVERSAL_COMPLETE",
                    packageName,
                    "account=${account.displayLabel} resolvedCards=${resolvedCardTraversalKeys.size} completedCards=${completedCardTraversalKeys.size} quarantinedCards=${quarantinedCardTraversalKeys.size} dateFilter=${requestedDates != null} dateScopeCount=${requestedDates?.size ?: 0} targetStart=${requestedDates?.minOrNull() ?: "none"} targetEnd=${requestedDates?.maxOrNull() ?: "none"} outOfScopeCardOpened=false",
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
        store.saveSync(
            account = account,
            lastUrl = webView.url.orEmpty(),
            trips = collected.toList(),
            skippedTrips = skipped,
            identityVerified = verified,
            dateScope = targetDates.takeIf { it.isNotEmpty() },
            targetedTripId = targetTripId.takeIf(String::isNotBlank),
            selectiveScriptSync0449 = scriptSelection0449.selective,
        )
        BlaBlaAutomaticCollectionCoordinator0400.publishCurrentSessions(
            context = this,
            reason = "final_snapshot",
        )
        if (targetTripId.isNotBlank()) {
            AgendaBackgroundSync0392.enqueueCollectorDelta0431(
                context = this,
                source = "exact_card_final",
            )
        }
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
            if (shouldAwaitNetworkTripSource0407(
                    sourcePresent = result.networkSource != null,
                    readAttempts = networkTripSourceReadAttempts0407,
                    maxReadAttempts = MAX_NETWORK_FIRST_SOURCE_ATTEMPTS_0407,
                )
            ) {
                networkTripSourceReadAttempts0407++
                UnifiedDebugEventStore.record(
                    "SYNC_NETWORK_SOURCE_PENDING",
                    packageName,
                    "account=${account.displayLabel} tripId=$candidateTripId attempt=$networkTripSourceReadAttempts0407/$MAX_NETWORK_FIRST_SOURCE_ATTEMPTS_0407 exactTrip=true fallback=DOM_AFTER_BOUNDED_WAIT piiLogged=false",
                )
                postSessionDelayed0405({
                    captureTripDetail(expectedSync, expectedNavigation, expectedCandidate)
                }, NETWORK_FIRST_SOURCE_RETRY_MS_0407)
                return@evaluateRequest
            }
            val networkResolution = BlaBlaCollectorNetworkSourceModule.resolve(candidateTripId, result.networkSource)
            if (networkResolution == null) {
                val fallbackReason = if (result.networkSource == null) {
                    "network_source_unavailable_after_bounded_wait"
                } else {
                    "network_contract_incomplete_or_unverified"
                }
                UnifiedDebugEventStore.record(
                    "SYNC_NETWORK_FALLBACK_DOM",
                    packageName,
                    "account=${account.displayLabel} tripId=$candidateTripId networkSourcePresent=${result.networkSource != null} reason=$fallbackReason exactTrip=true fallback=DOM piiLogged=false",
                )
            }
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
                    itineraryAuthoritative = resolution.itineraryAuthoritative,
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
            val acceptedResult = if (scriptSelection0449.wantsPassengerData()) {
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
                val passengerResult = sourceBackedResult.copy(
                    detail = sourceBackedResult.detail.copy(passengerRosterComplete = confirmedRosterComplete),
                )
                val rosterState = BlaBlaCollectorPassengerModule.rosterState(
                    passengerCount = passengerResult.detail.passengers.size,
                    rosterComplete = passengerResult.detail.passengerRosterComplete,
                    explicitEmpty = passengerResult.explicitEmptyRoster,
                )
                UnifiedDebugEventStore.record(
                    "TRIP_ROSTER_PROBE",
                    packageName,
                    "account=${account.displayLabel} tripId=$candidateTripId attempt=${tripRosterReadAttempts + 1} passengerCards=${passengerResult.detail.passengers.size} bookingLinks=${passengerResult.passengerHrefs.count { !it.startsWith(CARD_TARGET_PREFIX) }} structuralComplete=${sourceBackedResult.detail.passengerRosterComplete} rosterComplete=${passengerResult.detail.passengerRosterComplete} explicitEmpty=${passengerResult.explicitEmptyRoster} hasMore=${passengerResult.rosterHasMore} terminalEvidence=${passengerResult.rosterTerminalEvidence} stablePasses=$tripRosterStablePasses networkSource=${networkResolution != null} waitingForNetwork=$awaitNetworkBeforeEmptyRoster state=$rosterState",
                )
                if (rosterState == BlaBlaDirectRosterState.UNKNOWN) {
                    if (tripRosterReadAttempts < MAX_TRIP_ROSTER_READ_ATTEMPTS) {
                        tripRosterReadAttempts++
                        statusView.text = "${account.displayLabel} • confirmando passageiros ${tripRosterReadAttempts + 1}/$MAX_TRIP_ROSTER_READ_ATTEMPTS…"
                        if (passengerResult.rosterHasMore) {
                            UnifiedDebugEventStore.record(
                                "ROSTER_EXPANSION_NOT_AUTOMATED",
                                packageName,
                                "account=${account.displayLabel} tripId=$candidateTripId reason=interaction_not_documented passiveRetry=true",
                            )
                        }
                        postSessionDelayed0405({
                            captureTripDetail(expectedSync, expectedNavigation, expectedCandidate)
                        }, ROSTER_RETRY_MS)
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
                passengerResult
            } else {
                UnifiedDebugEventStore.record(
                    "ORCHESTRATOR_SCRIPT_SKIPPED_0449",
                    packageName,
                    "account=${account.displayLabel} tripId=$candidateTripId group=passengers requested=false action=preserve_previous",
                )
                sourceBackedResult.copy(
                    detail = sourceBackedResult.detail.copy(
                        passengers = emptyList(),
                        passengerRosterComplete = true,
                    ),
                    passengerHrefs = emptyList(),
                    explicitEmptyRoster = true,
                    rosterHasMore = false,
                    rosterTerminalEvidence = true,
                )
            }
            if (
                BlaBlaHarvestPolicy.AUTOMATIC_PUBLISHED_SEAT_LOOKUP &&
                scriptSelection0449.wantsSeatData()
            ) {
                val editLinkMatches = BlaBlaHarvestAssociation.editPageMatches(candidateTripId, acceptedResult.editHref)
                if (!editLinkMatches && tripRosterReadAttempts < MAX_TRIP_ROSTER_READ_ATTEMPTS) {
                    tripRosterReadAttempts++
                    statusView.text = "${account.displayLabel} • vinculando edição ${tripRosterReadAttempts + 1}/$MAX_TRIP_ROSTER_READ_ATTEMPTS…"
                    postSessionDelayed0405({
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
            if (scriptSelection0449.wantsPassengerData()) {
                networkResolution?.let(::saveNetworkPassengerMetadata)
            }
            val networkPublicLink = if (scriptSelection0449.wantsPublicUrl()) {
                BlaBlaCollectorNetworkSourceModule.resolvePublicTrip(
                    candidateTripId,
                    identityAcceptedResult.networkSource,
                )?.let { resolved ->
                    val relation = if (resolved.publicTripId == candidateTripId) "same" else "different"
                    UnifiedDebugEventStore.record(
                        "PUBLIC_TRIP_LINK_NETWORK_DISCOVERED",
                        packageName,
                        "account=${account.displayLabel} tripId=$candidateTripId source=${resolved.source} endpoint=${resolved.endpoint} jsonPath=${resolved.jsonPath} protocol=https publicIdRelation=$relation fingerprint=${publicTripHrefFingerprint0423(resolved.publicTripHref)} piiLogged=false",
                    )
                    UnifiedDebugEventStore.record(
                        "PUBLIC_TRIP_LINK_NETWORK_BOUND",
                        packageName,
                        "account=${account.displayLabel} tripId=$candidateTripId binding=${resolved.binding} publicIdRelation=$relation exactAdministrativeTrip=true fingerprint=${publicTripHrefFingerprint0423(resolved.publicTripHref)}",
                    )
                    ResolvedPublicTripLink0423(
                        href = resolved.publicTripHref,
                        source = resolved.source,
                        binding = resolved.binding,
                    )
                }
            } else {
                null
            }
            val authoritativeNavigationPublicLink = if (scriptSelection0449.wantsPublicUrl()) {
                publicTripNavigation0443
                    ?.takeIf { captured ->
                        captured.syncGeneration == expectedSync &&
                            captured.navigationGeneration == expectedNavigation &&
                            captured.candidateIndex == expectedCandidate &&
                            captured.administrativeTripId == candidateTripId
                    }
                    ?.resolved
            } else {
                null
            }
            val passivePublicLink = if (scriptSelection0449.wantsPublicUrl()) {
                BlaBlaCollectorUrlModule.publicTrip(
                    identityAcceptedResult.publicTripHref,
                    candidateTripId,
                )?.let { href ->
                    ResolvedPublicTripLink0423(
                        href = href,
                        source = "passive_dom",
                        binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_SAME_ID,
                    )
                }
            } else {
                null
            }
            val persistedPublicLink = if (
                scriptSelection0449.wantsPublicUrl() &&
                networkPublicLink == null &&
                authoritativeNavigationPublicLink == null &&
                passivePublicLink == null
            ) {
                persistedCanonicalPublicTrip0423(candidateTripId)
            } else {
                null
            }
            val resolvedPublicLink = if (scriptSelection0449.wantsPublicUrl()) {
                resolvePreferredPublicTripLink0423(
                    network = networkPublicLink,
                    passiveDom = passivePublicLink,
                    persistedCanonical = persistedPublicLink,
                    authoritativeNavigation = authoritativeNavigationPublicLink,
                )
            } else {
                UnifiedDebugEventStore.record(
                    "ORCHESTRATOR_SCRIPT_SKIPPED_0449",
                    packageName,
                    "account=${account.displayLabel} tripId=$candidateTripId group=public_url requested=false action=skip_capture_preserve_previous",
                )
                null
            }
            pendingTripDetail = identityAcceptedResult.copy(
                publicTripHref = resolvedPublicLink?.href.orEmpty(),
                publicTripHrefSource = resolvedPublicLink?.source.orEmpty(),
                publicTripHrefBinding = resolvedPublicLink?.binding.orEmpty(),
            )
            pendingTripPassengers = if (scriptSelection0449.wantsPassengerData()) {
                (networkResolution?.passengers ?: preview?.passengers ?: identityAcceptedResult.detail.passengers)
                    .toMutableList()
            } else {
                mutableListOf()
            }
            pendingTripPassengerCardIndexes.clear()
            pendingTripPassengers.indices.forEach { rowIndex ->
                pendingTripPassengerCardIndexes[rowIndex] = rowIndex
            }
            pendingTripSyncGeneration = expectedSync
            pendingTripCandidateIndex = expectedCandidate
            passengerContactIndex = 0
            passengerContactReadAttempts = 0
            passengerCardReadAttempts = 0
            publicTripShareReadAttempts = 0
            publicTripShareCaptureInFlight = false
            if (!scriptSelection0449.wantsPublicUrl()) {
                loadNextPassengerContact(expectedSync, expectedCandidate)
            } else if (resolvedPublicLink != null) {
                val event = when (resolvedPublicLink.source) {
                    "network_structured" -> "PUBLIC_TRIP_LINK_CAPTURED"
                    "passive_dom" -> "PUBLIC_TRIP_LINK_DOM_FALLBACK"
                    "persisted_canonical" -> "PUBLIC_TRIP_LINK_PRESERVED"
                    else -> "PUBLIC_TRIP_LINK_CAPTURED"
                }
                UnifiedDebugEventStore.record(
                    event,
                    packageName,
                    "account=${account.displayLabel} tripId=$candidateTripId source=${resolvedPublicLink.source} binding=${resolvedPublicLink.binding} fingerprint=${publicTripHrefFingerprint0423(resolvedPublicLink.href)} networkFirst=${networkPublicLink != null}",
                )
                loadNextPassengerContact(expectedSync, expectedCandidate)
            } else {
                enterBrowserPhase(
                    Phase.PUBLIC_SHARE,
                    BlaBlaBrowserRequest.TRIP_PUBLIC_SHARE,
                    "capture_documented_share_action",
                )
                statusView.text = "${account.displayLabel} • capturando link público do card…"
                UnifiedDebugEventStore.record(
                    "PUBLIC_TRIP_LINK_SHARE_FALLBACK",
                    packageName,
                    "account=${account.displayLabel} tripId=$candidateTripId reason=network_dom_persisted_unavailable",
                )
                capturePublicTripShare(expectedSync, navigationGeneration, expectedCandidate)
            }
        }
    }

    private fun capturePublicTripShare(expectedSync: Long, expectedNavigation: Long, expectedCandidate: Int) {
        if (
            phase != Phase.PUBLIC_SHARE ||
            expectedSync != syncGeneration ||
            expectedNavigation != navigationGeneration ||
            expectedCandidate != candidateIndex ||
            !pendingTripIsCurrent(expectedSync, expectedCandidate)
        ) {
            recordStale("public_share_before_evaluate", expectedSync, expectedCandidate)
            return
        }
        val tripId = candidates.getOrNull(expectedCandidate)
            ?.let { BlaBlaTripIdentity.externalTripIdFromHref(it.href) }
            .orEmpty()
        if (
            tripId.isBlank() ||
            BlaBlaTripIdentity.externalTripIdFromHref(webView.url.orEmpty()) != tripId
        ) {
            recordStale("public_share_trip_identity_mismatch", expectedSync, expectedCandidate)
            return
        }
        if (publicTripShareCaptureInFlight) return
        publicTripShareCaptureInFlight = true
        evaluateRequest<DynamicPublicTripShareEvidence>(BlaBlaBrowserRequest.TRIP_PUBLIC_SHARE) { evidence ->
            publicTripShareCaptureInFlight = false
            if (
                phase != Phase.PUBLIC_SHARE ||
                expectedSync != syncGeneration ||
                expectedNavigation != navigationGeneration ||
                expectedCandidate != candidateIndex ||
                !pendingTripIsCurrent(expectedSync, expectedCandidate)
            ) {
                recordStale("public_share_after_evaluate", expectedSync, expectedCandidate)
                return@evaluateRequest
            }

            val captured = BlaBlaCollectorUrlModule.publicTripFromAuthoritativeOrchestratorNavigation(
                raw = evidence?.publicTripHref,
                expectedAdministrativeTripId = tripId,
                boundAdministrativeTripId = tripId,
            )
            if (captured != null) {
                pendingTripDetail = pendingTripDetail?.copy(
                    publicTripHref = captured,
                    publicTripHrefSource = "share_action",
                    publicTripHrefBinding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION,
                )
                val sharedPublicTripId = BlaBlaCollectorUrlModule.publicTripPublicId(captured).orEmpty()
                UnifiedDebugEventStore.record(
                    "PUBLIC_TRIP_LINK_CAPTURED",
                    packageName,
                    "account=${account.displayLabel} tripId=$tripId source=share_action binding=" +
                        BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_ORCHESTRATOR_NAVIGATION +
                        " exactAdministrativeTrip=true publicIdRelation=" +
                        (if (sharedPublicTripId == tripId) "same" else "different") +
                        " shareControlPresent=${evidence?.shareControlPresent == true}" +
                        " shareInterceptInstalled=${evidence?.shareInterceptInstalled == true}" +
                        " shareInvoked=${evidence?.shareInvoked == true}" +
                        " clickCount=${evidence?.clickCount ?: 0}",
                )
                loadNextPassengerContact(expectedSync, expectedCandidate)
                return@evaluateRequest
            }

            val shouldRetry =
                publicTripShareReadAttempts < MAX_PUBLIC_TRIP_SHARE_READ_ATTEMPTS &&
                    evidence?.shareControlPresent == true &&
                    evidence.shareInterceptInstalled
            if (shouldRetry) {
                publicTripShareReadAttempts++
                statusView.text =
                    "${account.displayLabel} • capturando link público ${publicTripShareReadAttempts + 1}/$MAX_PUBLIC_TRIP_SHARE_READ_ATTEMPTS…"
                postSessionDelayed0405({
                    capturePublicTripShare(expectedSync, expectedNavigation, expectedCandidate)
                }, PUBLIC_TRIP_SHARE_RETRY_MS)
                return@evaluateRequest
            }

            UnifiedDebugEventStore.record(
                "PUBLIC_TRIP_SHARE_FALLBACK_REQUIRED",
                packageName,
                "account=${account.displayLabel} tripId=$tripId shareControlPresent=${evidence?.shareControlPresent == true} shareInterceptInstalled=${evidence?.shareInterceptInstalled == true} shareInvoked=${evidence?.shareInvoked == true} clickCount=${evidence?.clickCount ?: 0} attempts=${publicTripShareReadAttempts + 1} systemShareOpened=false next=exact_public_search",
            )
            beginExactPublicTripSearch(expectedSync, expectedCandidate)
        }
    }

    private fun beginExactPublicTripSearch(expectedSync: Long, expectedCandidate: Int) {
        if (!pendingTripIsCurrent(expectedSync, expectedCandidate)) {
            recordStale("public_search_link_pending_mismatch", expectedSync, expectedCandidate)
            return
        }
        val candidate = candidates.getOrNull(expectedCandidate) ?: run {
            recordStale("public_search_link_candidate_missing", expectedSync, expectedCandidate)
            return
        }
        val definition = account.verifiedDefinition()
        val detail = pendingTripDetail?.detail
        val normalized = if (definition != null && detail != null) {
            BlaBlaDomNormalizer.toTrip(
                account = definition,
                candidate = candidate,
                detail = detail,
                today = LocalDate.now(),
                authenticatedProfileSessionVerified = identityConfirmedThisSync,
            )
        } else {
            null
        }
        val date = normalized?.date
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val from = listOf(candidate.origin, normalized?.actual_departure.orEmpty())
            .map(String::trim)
            .firstOrNull { it.isNotBlank() && BlaBlaPublicPlaceDirectory.supported(it) }
        val to = listOf(candidate.destination, normalized?.actual_arrival.orEmpty())
            .map(String::trim)
            .firstOrNull { it.isNotBlank() && BlaBlaPublicPlaceDirectory.supported(it) }
        val task = if (date != null && from != null && to != null) {
            BlaBlaPublicSearchTask(date = date, from = from, to = to)
        } else {
            null
        }
        val providerOrigin = BlaBlaCollectorUrlModule.origin(candidate.href) ?: BlaBlaCollectorUrlModule.origin(webView.url)
        val searchUrl = task?.let { BlaBlaPublicPlaceDirectory.searchUrl(it, providerOrigin) }
        val tripId = BlaBlaTripIdentity.externalTripIdFromHref(candidate.href).orEmpty()
        if (tripId.isBlank() || searchUrl.isNullOrBlank()) {
            UnifiedDebugEventStore.record(
                "PUBLIC_TRIP_LINK_UNAVAILABLE",
                packageName,
                "account=${account.displayLabel} tripId=$tripId source=exact_public_search reason=search_scope_unavailable datePresent=${date != null} fromSupported=${from != null} toSupported=${to != null} action=continue_without_inventing_link",
            )
            loadNextPassengerContact(expectedSync, expectedCandidate)
            return
        }

        publicTripSearchReadAttempts = 0
        publicTripSearchCaptureInFlight = false
        enterBrowserPhase(
            Phase.PUBLIC_SEARCH_LINK,
            BlaBlaBrowserRequest.PUBLIC_SEARCH_RESULTS,
            "resolve_public_trip_from_exact_search",
        )
        statusView.text = "${account.displayLabel} • procurando link público exato do card…"
        UnifiedDebugEventStore.record(
            "PUBLIC_TRIP_EXACT_SEARCH_STARTED",
            packageName,
            "account=${account.displayLabel} tripId=$tripId date=$date from=${from.orEmpty().take(80)} to=${to.orEmpty().take(80)}",
        )
        loadTrackedUrl(searchUrl)
    }

    private fun capturePublicTripFromExactSearch(
        expectedSync: Long,
        expectedNavigation: Long,
        expectedCandidate: Int,
    ) {
        if (
            phase != Phase.PUBLIC_SEARCH_LINK ||
            expectedSync != syncGeneration ||
            expectedNavigation != navigationGeneration ||
            expectedCandidate != candidateIndex ||
            !pendingTripIsCurrent(expectedSync, expectedCandidate)
        ) {
            recordStale("public_search_link_before_evaluate", expectedSync, expectedCandidate)
            return
        }
        val candidate = candidates.getOrNull(expectedCandidate) ?: run {
            recordStale("public_search_link_candidate_missing_after_navigation", expectedSync, expectedCandidate)
            return
        }
        val tripId = BlaBlaTripIdentity.externalTripIdFromHref(candidate.href).orEmpty()
        if (tripId.isBlank()) {
            loadNextPassengerContact(expectedSync, expectedCandidate)
            return
        }
        if (publicTripSearchCaptureInFlight) return
        publicTripSearchCaptureInFlight = true
        evaluateRequest<DynamicPublicSearchLinkPage>(BlaBlaBrowserRequest.PUBLIC_SEARCH_RESULTS) { evidence ->
            publicTripSearchCaptureInFlight = false
            if (
                phase != Phase.PUBLIC_SEARCH_LINK ||
                expectedSync != syncGeneration ||
                expectedNavigation != navigationGeneration ||
                expectedCandidate != candidateIndex ||
                !pendingTripIsCurrent(expectedSync, expectedCandidate)
            ) {
                recordStale("public_search_link_after_evaluate", expectedSync, expectedCandidate)
                return@evaluateRequest
            }

            val providerOrigin = BlaBlaCollectorUrlModule.origin(webView.url)
                ?: BlaBlaCollectorUrlModule.origin(candidate.href)
            val sameIdPublicHref = exactPublicTripHrefForTrip(
                expectedTripId = tripId,
                hrefs = evidence?.cards.orEmpty().map { it.href },
                providerOrigin = providerOrigin,
            )
            val resolvedPublicLink = sameIdPublicHref?.let { href ->
                ResolvedPublicTripLink0423(
                    href = href,
                    source = "exact_public_search",
                    binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_SAME_ID,
                )
            } ?: resolveExactPublicSearchTripLink0448(
                expectedAdministrativeTripId = tripId,
                expectedDriverName = pendingTripDetail?.detail?.driverName
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: account.profileName,
                expectedDepartureTime = pendingTripDetail?.detail?.departureTime
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: candidate.departureTime,
                cards = evidence?.cards.orEmpty(),
                providerOrigin = providerOrigin,
            )
            if (resolvedPublicLink != null) {
                pendingTripDetail = pendingTripDetail?.copy(
                    publicTripHref = resolvedPublicLink.href,
                    publicTripHrefSource = resolvedPublicLink.source,
                    publicTripHrefBinding = resolvedPublicLink.binding,
                )
                val publicTripId = BlaBlaCollectorUrlModule.publicTripPublicId(resolvedPublicLink.href).orEmpty()
                val relation = if (publicTripId == tripId) "same" else "different"
                UnifiedDebugEventStore.record(
                    "PUBLIC_TRIP_LINK_CAPTURED",
                    packageName,
                    "account=${account.displayLabel} tripId=$tripId source=${resolvedPublicLink.source} binding=${resolvedPublicLink.binding} exactTrip=true cards=${evidence?.cards?.size ?: 0} publicIdRelation=$relation fingerprint=${publicTripHrefFingerprint0423(resolvedPublicLink.href)}",
                )
                loadNextPassengerContact(expectedSync, expectedCandidate)
                return@evaluateRequest
            }

            if (publicTripSearchReadAttempts < MAX_PUBLIC_TRIP_SEARCH_READ_ATTEMPTS) {
                publicTripSearchReadAttempts++
                statusView.text =
                    "${account.displayLabel} • confirmando link público ${publicTripSearchReadAttempts + 1}/$MAX_PUBLIC_TRIP_SEARCH_READ_ATTEMPTS…"
                postSessionDelayed0405({
                    capturePublicTripFromExactSearch(expectedSync, expectedNavigation, expectedCandidate)
                }, PUBLIC_TRIP_SEARCH_RETRY_MS)
                return@evaluateRequest
            }

            UnifiedDebugEventStore.record(
                "PUBLIC_TRIP_LINK_UNAVAILABLE",
                packageName,
                "account=${account.displayLabel} tripId=$tripId source=exact_public_search cards=${evidence?.cards?.size ?: 0} attempts=${publicTripSearchReadAttempts + 1} reason=no_unique_verified_public_card_match action=continue_without_inventing_link",
            )
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
                        postSessionDelayed0405({
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
        if (
            BlaBlaHarvestPolicy.AUTOMATIC_PUBLISHED_SEAT_LOOKUP &&
            scriptSelection0449.wantsSeatData()
        ) {
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
                postSessionDelayed0405({
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
                postSessionDelayed0405({
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
                postSessionDelayed0405({
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
                    postSessionDelayed0405({ capturePassengerContact(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger) }, ROSTER_RETRY_MS)
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
                postSessionDelayed0405({ capturePassengerContact(expectedSync, expectedNavigation, expectedCandidate, expectedPassenger) }, ROSTER_RETRY_MS)
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
                postSessionDelayed0405({
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
                postSessionDelayed0405({
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
        if (
            BlaBlaHarvestPolicy.AUTOMATIC_PUBLISHED_SEAT_LOOKUP &&
            scriptSelection0449.wantsSeatData()
        ) {
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
        if (scriptSelection0449.wantsPassengerData()) {
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
        val freshTrip = normalizedTrip.copy(
            itinerary_stops = result.itineraryStops
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct(),
            itinerary_authoritative = result.itineraryAuthoritative,
            public_trip_href = BlaBlaCollectorUrlModule.publicTripForCollectorState(
                result.publicTripHref,
                normalizedTrip.trip_id,
                result.publicTripHrefBinding,
            ),
            public_trip_href_source = result.publicTripHrefSource,
            public_trip_href_binding = result.publicTripHrefBinding,
            published_seats = pendingPublishedSeats,
        )
        val previousTrip0449 = candidateTripId?.let { strongTripId ->
            store.read(account)?.trips?.singleOrNull { previous ->
                previous.trip_id?.trim() == strongTripId
            }
        }
        val trip = mergeSelectiveCollectorTrip0449(
            previous = previousTrip0449,
            fresh = freshTrip,
            selection = scriptSelection0449,
        )
        if (trip == null) {
            skipped++
            UnifiedDebugEventStore.record(
                "SELECTIVE_SYNC_REQUIRES_EXISTING_TRIP_0449",
                packageName,
                "account=${account.displayLabel} tripId=${candidateTripId.orEmpty()} requestedScripts=${scriptSelection0449.requestedNames().joinToString(",")} action=preserve_previous_skip_new_card",
            )
            blockCurrentCard(expectedSync, expectedCandidate, "selective_sync_requires_existing_trip")
            return
        }
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
            val freshTargetCaptured =
                identityConfirmedThisSync &&
                    tripId == targetTripId &&
                    collected.count { trip ->
                        trip.profile_uuid.trim().equals(account.profileUuid?.trim(), ignoreCase = true) &&
                            trip.trip_id?.trim() == targetTripId &&
                            BlaBlaCollectorUrlModule.tripId(trip.trip_href.orEmpty()) == targetTripId
                    } == 1 &&
                    skipped == 0 &&
                    quarantinedCardTraversalKeys.isEmpty()
            targetedSnapshotSaved0407 =
                freshTargetCaptured && saveFinalSnapshotOnce(verified = true)
            UnifiedDebugEventStore.record(
                "CARD_TRAVERSAL_COMPLETE",
                packageName,
                "account=${account.displayLabel} order=1 tripId=$tripId passengers=${pendingTripPassengers.size} publishedSeats=${pendingPublishedSeats ?: -1} result=${if (targetedSnapshotSaved0407) "targeted_complete" else "targeted_unverified"} nextCardAllowed=false siblingCardsPreserved=true canonicalWriteDeferred=true",
            )
            networkDiagnosticRecorder?.finishFirstCard(if (targetedSnapshotSaved0407) "targeted_complete" else "targeted_unverified")
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

    private fun persistedCanonicalPublicTrip0423(tripId: String): ResolvedPublicTripLink0423? {
        val profileUuid = account.profileUuid?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val canonical = TripStore(this).trips().firstOrNull { trip ->
            !trip.deleted &&
                trip.blablaProfileUuid?.trim()?.equals(profileUuid, ignoreCase = true) == true &&
                trip.blablaTripId?.trim() == tripId
        } ?: return null
        val raw = canonical.blablaPublicUrl?.trim()?.takeIf(String::isNotEmpty) ?: return null
        BlaBlaCollectorUrlModule.publicTrip(raw, tripId)?.let { href ->
            return ResolvedPublicTripLink0423(
                href = href,
                source = "persisted_canonical",
                binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_SAME_ID,
            )
        }
        val href = BlaBlaCollectorUrlModule.publicTripFromAuthoritativeNetwork(
            raw = raw,
            expectedAdministrativeTripId = tripId,
            boundAdministrativeTripId = tripId,
        ) ?: return null
        return ResolvedPublicTripLink0423(
            href = href,
            source = "persisted_canonical",
            binding = BlaBlaCollectorUrlModule.PUBLIC_TRIP_BINDING_NETWORK_AUTHORITATIVE,
        )
    }

    private fun publicTripHrefFingerprint0423(raw: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            .take(16)

    private fun persistDirectTripEvidence(tripId: String) {
        if (tripId.isBlank()) return
        val detail = pendingTripDetail ?: return
        val evidenceStore = BlaBlaHarvestEvidenceStore(this)
        val existing = evidenceStore.read(account.id)
        val prior = existing.firstOrNull { it.tripId == tripId }
        val evidence = BlaBlaHarvestTripEvidence(
            tripId = tripId,
            publishedSeats = if (scriptSelection0449.wantsSeatData()) {
                pendingPublishedSeats ?: prior?.publishedSeats
            } else {
                prior?.publishedSeats
            },
            views = if (scriptSelection0449.wantsCoreTripData()) {
                detail.views ?: prior?.views
            } else {
                prior?.views
            },
            itineraryStops = if (scriptSelection0449.wantsCoreTripData()) {
                detail.itineraryStops.ifEmpty { prior?.itineraryStops.orEmpty() }
            } else {
                prior?.itineraryStops.orEmpty()
            },
            passengers = if (scriptSelection0449.wantsPassengerData()) {
                pendingTripPassengers.toList()
            } else {
                prior?.passengers.orEmpty()
            },
            passengerRosterComplete = if (scriptSelection0449.wantsPassengerData()) {
                detail.detail.passengerRosterComplete
            } else {
                prior?.passengerRosterComplete ?: false
            },
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
            "account=${account.displayLabel} reason=$reason resolvedCards=${resolvedCardTraversalKeys.size} completedCards=${completedCardTraversalKeys.size} quarantinedCards=${quarantinedCardTraversalKeys.size} nextCardAllowed=false returnPartial=true",
        )
        saveFinalSnapshotOnce(identityConfirmedThisSync && !account.profileUuid.isNullOrBlank())
        completeSync(collected.size)
    }

    private fun saveProgressSnapshot(reason: String) {
        val verified = identityConfirmedThisSync && !account.profileUuid.isNullOrBlank()
        store.saveSync(
            account = account,
            lastUrl = webView.url.orEmpty(),
            trips = collected.toList(),
            skippedTrips = maxOf(skipped, 1),
            identityVerified = verified,
            dateScope = targetDates.takeIf { it.isNotEmpty() },
            targetedTripId = targetTripId.takeIf(String::isNotBlank),
            selectiveScriptSync0449 = scriptSelection0449.selective,
        )
        BlaBlaAutomaticCollectionCoordinator0400.publishCurrentSessions(
            context = this,
            reason = reason,
        )
        // A persisted per-card checkpoint is already safe collector state. Promote it
        // through the existing canonical delta worker immediately instead of waiting
        // for the whole account/run to finish. The worker remains the only writer that
        // resolves strong identity, performs semantic upsert and emits Timeline changes.
        AgendaBackgroundSync0392.enqueueCollectorDelta0431(
            context = this,
            source = "card_checkpoint:" + reason,
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
        networkTripSourceReadAttempts0407 = 0
        publicTripNavigation0443 = null
        lastTripRosterSignature = ""
        tripRosterStablePasses = 0
        publicTripShareReadAttempts = 0
        publicTripShareCaptureInFlight = false
        publicTripSearchReadAttempts = 0
        publicTripSearchCaptureInFlight = false
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
        browserOrchestrator.executeCollectionStep(
            androidContext = this,
            webView = webView,
            request = request,
            executionContext = browserExecutionContext(),
            currentContext = ::browserExecutionContext,
            deserializer = serializer<T>(),
            arguments = arguments,
            reason = "dynamic_account_collection",
            timeoutMs = blaBlaDynamicCollectionTimeoutMs0389(request),
        ) { result ->
            callback(result)
        }
    }

    private fun completeAutomaticAuthenticationRequired(reason: String) {
        if (!completionGate.claimCompletion(syncGeneration)) return
        releaseExternalFlight0426()
        enterBrowserPhase(Phase.IDLE, null, "pending_auth")
        UnifiedDebugEventStore.record(
            "BLABLACAR_AUTOMATIC_PENDING_AUTH_0401", packageName,
            "accountKey=${seatSyncDiagnosticKey(account.id)} generation=$automaticCollectionGeneration reason=${reason.take(120)} previousSnapshotPreserved=true browserOpened=false",
        )
        if (automaticCollectionClaimed && !automaticCollectionReported) {
            automaticCollectionReported = true
            BlaBlaAutomaticCollectionCoordinator0400.onAccountPendingAuth(this, automaticCollectionGeneration, account.id, reason)
        }
        setResult(
            Activity.RESULT_CANCELED,
            Intent()
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID, account.id)
                .putExtra(BlaBlaDynamicSessionIntents.EXTRA_SYNC_FAILURE_0407, "AUTH_REQUIRED"),
        )
        finish()
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
        releaseExternalFlight0426()
        enterBrowserPhase(Phase.IDLE, null, "sync_complete")
        val targeted = targetTripId.isNotBlank()
        val exactTargetFresh = !targeted || (
            targetedSnapshotSaved0407 &&
                identityConfirmedThisSync &&
                completedCardTraversalKeys.size == 1 &&
                collected.count { trip ->
                    trip.profile_uuid.trim().equals(account.profileUuid?.trim(), ignoreCase = true) &&
                        trip.trip_id?.trim() == targetTripId &&
                        BlaBlaCollectorUrlModule.tripId(trip.trip_href.orEmpty()) == targetTripId
                } == 1
        )
        val finalStatus = if (
            !exactTargetFresh ||
            skipped > 0 ||
            quarantinedCardTraversalKeys.isNotEmpty()
        ) "partial" else "success"
        UnifiedDebugEventStore.record(
            "SYNC_END",
            packageName,
            "account=${account.displayLabel} status=$finalStatus trips=$count skipped=$skipped completedCards=${completedCardTraversalKeys.size} quarantinedCards=${quarantinedCardTraversalKeys.size} identityVerified=$identityConfirmedThisSync automaticGeneration=$automaticCollectionGeneration targeted=$targeted exactTargetFresh=$exactTargetFresh siblingCardsPreserved=${!targeted || targetedSnapshotSaved0407}",
        )
        if (automaticCollectionClaimed && !automaticCollectionReported) {
            automaticCollectionReported = true
            BlaBlaAutomaticCollectionCoordinator0400.onAccountFinished(
                context = this,
                generation = automaticCollectionGeneration,
                accountId = account.id,
                accountResult = finalStatus,
                error = if (finalStatus == "success") "" else "skipped=$skipped quarantined=${quarantinedCardTraversalKeys.size}",
            )
        }
        if (targeted && !exactTargetFresh) {
            setResult(
                Activity.RESULT_CANCELED,
                Intent()
                    .putExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID, account.id)
                    .putExtra(BlaBlaDynamicSessionIntents.EXTRA_SYNC_FAILURE_0407, "TRIP_NOT_VERIFIED"),
            )
        } else {
            setResult(
                Activity.RESULT_OK,
                Intent()
                    .putExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID, account.id)
                    .putExtra("trip_count", count),
            )
        }
        finish()
    }

    private fun manageTargetUrl(): String? {
        val value = intent?.getStringExtra(BlaBlaDynamicSessionIntents.EXTRA_TARGET_URL)?.trim().orEmpty()
        return value.takeIf(BlaBlaCollectorUrlModule::isManageTarget)
    }

    private fun finishSeen() {
        releaseExternalFlight0426()
        store.markSeen(account, webView.url.orEmpty())
        if (automaticCollectionClaimed && !automaticCollectionReported) {
            automaticCollectionReported = true
            BlaBlaAutomaticCollectionCoordinator0400.onAccountInterrupted(
                context = this,
                generation = automaticCollectionGeneration,
                accountId = account.id,
                reason = "automatic_collection_visual_host_closed",
            )
        }
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(BlaBlaDynamicSessionIntents.EXTRA_ACCOUNT_ID, account.id))
        finish()
    }

    fun handleBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else finishSeen()
    }

    fun destroy(reason: String = "host_destroyed") {
        if (destroyed) return
        destroyed = true
        if (automaticCollectionClaimed && !automaticCollectionReported) {
            automaticCollectionReported = true
            BlaBlaAutomaticCollectionCoordinator0400.onAccountInterrupted(
                context = this,
                generation = automaticCollectionGeneration,
                accountId = account.id,
                reason = reason.take(120),
            )
        }
        releaseExternalFlight0426()
        networkDiagnosticRecorder?.finishFirstCard("host_closed")
        networkDiagnosticRecorder?.close()
        headlessDelayedHandler0405.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) webView.destroy()
        syncCrashGuard?.close()
    }

    private fun syncCrashSnapshot(): String = buildString {
        append("mode=").append(mode)
        append(" syncGeneration=").append(syncGeneration)
        append(" navigationGeneration=").append(navigationGeneration)
        append(" phase=").append(phase.name)
        append(" candidateIndex=").append(candidateIndex)
        append(" candidates=").append(candidates.size)
        append(" collected=").append(collected.size)
        append(" pendingPassengers=").append(pendingTripPassengers.size)
        append(" resolvedCards=").append(resolvedCardTraversalKeys.size)
        append(" completedCards=").append(completedCardTraversalKeys.size)
        append(" quarantinedCards=").append(quarantinedCardTraversalKeys.size)
        append(" detailInFlight=").append(detailCaptureInFlight)
        append(" publicShareInFlight=").append(publicTripShareCaptureInFlight)
        append(" publicSearchInFlight=").append(publicTripSearchCaptureInFlight)
        append(" passengerInFlight=").append(passengerCaptureInFlight || passengerCardCaptureInFlight)
        append(" editInFlight=").append(editCaptureInFlight)
        append(" optionsInFlight=").append(optionsCaptureInFlight)
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

    private enum class Phase { IDLE, IDENTITY, PROFILE_PUBLIC, PROFILE_REVIEWS, RIDES, DETAIL, PUBLIC_SHARE, PUBLIC_SEARCH_LINK, PASSENGER_CARD, PASSENGER_CONTACT, EDIT, OPTIONS }

    companion object {
        private const val HOME_URL = "https://www.blablacar.com.br/"
        private const val RIDES_URL = "https://www.blablacar.com.br/rides"
        private const val PROFILE_URL = "https://www.blablacar.com.br/dashboard/profile/menu"
        private const val MAX_RIDES_EMPTY_READ_ATTEMPTS = 3
        private const val MAX_IDENTITY_READ_ATTEMPTS = 3
        private const val IDENTITY_RETRY_MS = 700L
        private const val HEADLESS_PAGE_CALLBACK_FALLBACK_MS_0404 = 20_000L
        internal const val SOURCE_ACCESS_PROBE_TIMEOUT_MS_0447 = 4_000L
        private const val MAX_PROFILE_REVIEW_READ_ATTEMPTS = 24
        private const val PROFILE_REVIEW_SCROLL_SETTLE_MS = 700L
        private const val REQUIRED_STABLE_BOTTOM_PASSES = 2
        private const val RIDES_SCROLL_SETTLE_MS = 750L
        private const val RIDES_BOTTOM_SETTLE_MS = 1200L
        private const val MAX_PASSENGER_EVIDENCE_READ_ATTEMPTS = 3
        private const val MAX_TRIP_ROSTER_READ_ATTEMPTS = 5
        private const val MAX_PUBLIC_TRIP_SHARE_READ_ATTEMPTS = 2
        private const val PUBLIC_TRIP_SHARE_RETRY_MS = 350L
        private const val MAX_PUBLIC_TRIP_SEARCH_READ_ATTEMPTS = 3
        private const val PUBLIC_TRIP_SEARCH_SETTLE_MS = 2_500L
        private const val PUBLIC_TRIP_SEARCH_RETRY_MS = 900L
        private const val MAX_PASSENGER_CARD_READ_ATTEMPTS = 4
        private const val MAX_PASSENGER_BIND_READ_ATTEMPTS = 3
        private const val MAX_EDIT_LINK_READ_ATTEMPTS = 5
        private const val MAX_OPTIONS_READ_ATTEMPTS = 3
        private const val MAX_NETWORK_FIRST_SOURCE_ATTEMPTS_0407 = 2
        private const val NETWORK_FIRST_SOURCE_RETRY_MS_0407 = 300L
        private const val ROSTER_RETRY_MS = 800L
        private const val PASSENGER_NAVIGATION_SETTLE_MS = 1_200L
        private const val PASSENGER_CALL_SETTLE_MS = 650L
        private const val CARD_TARGET_PREFIX = "rotacerta-card:"

    }
}