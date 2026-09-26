package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.ContextThemeWrapper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONTokener

/**
 * 0.1.655 — BlaBlaCar notification -> authoritative messages HTML -> exact-card refresh.
 *
 * Android notifications are intentionally treated only as a wake-up signal. Notification text is
 * never interpreted as booking/cancellation state. The authenticated /messages/list HTML decides
 * whether an activity row advanced; any affected canonical card is then revalidated through the
 * existing HTML_DIRECT targeted collector (AgendaBackgroundSync0392 -> captureSingleTrip0607).
 */
@Serializable
internal data class BlaBlaMessageIndexEntry0655(
    val conversationKey: String,
    val groupKey: String = "",
    val href: String = "",
    val passengerName: String = "",
    val routeText: String = "",
    val lastActivity: String = "",
)

@Serializable
internal data class BlaBlaMessageIndexSnapshot0655(
    val capturedAtMillis: Long,
    val entries: List<BlaBlaMessageIndexEntry0655>,
)

internal data class BlaBlaMessageIndexDelta0655(
    val baseline: Boolean,
    val changed: List<BlaBlaMessageIndexEntry0655>,
    val merged: BlaBlaMessageIndexSnapshot0655,
)

internal fun isBlaBlaNotificationPackage0655(packageName: String?): Boolean {
    val value = packageName?.trim()?.lowercase().orEmpty()
    return value == "com.comuto" ||
        value.startsWith("com.comuto.") ||
        value == "com.blablacar" ||
        value.startsWith("com.blablacar.")
}

internal fun messageConversationKey0655(rawHref: String?): String {
    val href = rawHref?.trim().orEmpty()
    if (href.isBlank()) return ""
    val marker = "/messages/show/"
    val index = href.indexOf(marker, ignoreCase = true)
    if (index < 0) return ""
    return href.substring(index + marker.length)
        .substringBefore('?')
        .substringBefore('#')
        .trim('/')
        .take(240)
}

internal fun messageGroupKey0655(rawHref: String?): String {
    val conversation = messageConversationKey0655(rawHref)
    return MESSAGE_GROUP_PREFIX_0655.find(conversation)?.groupValues?.getOrNull(1)?.lowercase().orEmpty()
}

internal fun normalizeMessageIdentity0655(value: String?): String =
    java.text.Normalizer.normalize(value.orEmpty(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

internal fun messageRouteEndpoints0655(routeText: String?): Pair<String, String>? {
    val raw = routeText?.trim().orEmpty()
    if (raw.isBlank()) return null
    val parts = raw.split(Regex("\\s*(?:→|->|›|➜)\\s*"), limit = 2)
    if (parts.size != 2) return null
    val from = normalizeMessageIdentity0655(parts[0])
    val to = normalizeMessageIdentity0655(parts[1])
    return if (from.isNotBlank() && to.isNotBlank()) from to to else null
}

internal fun messageIndexDelta0655(
    previous: BlaBlaMessageIndexSnapshot0655?,
    incoming: BlaBlaMessageIndexSnapshot0655,
): BlaBlaMessageIndexDelta0655 {
    val normalizedIncoming = incoming.entries
        .map(::normalizedMessageEntry0655)
        .filter { it.conversationKey.isNotBlank() }
        .distinctBy(BlaBlaMessageIndexEntry0655::conversationKey)

    if (previous == null) {
        return BlaBlaMessageIndexDelta0655(
            baseline = true,
            changed = emptyList(),
            merged = incoming.copy(entries = normalizedIncoming.take(MAX_MESSAGE_ROWS_0655)),
        )
    }

    val previousByConversation = previous.entries
        .map(::normalizedMessageEntry0655)
        .filter { it.conversationKey.isNotBlank() }
        .associateBy(BlaBlaMessageIndexEntry0655::conversationKey)
        .toMutableMap()
    val changed = mutableListOf<BlaBlaMessageIndexEntry0655>()

    normalizedIncoming.forEach { next ->
        val old = previousByConversation[next.conversationKey]
        when {
            old == null -> {
                previousByConversation[next.conversationKey] = next
                changed += next
            }
            messageEntryIsNewer0655(old, next) -> {
                previousByConversation[next.conversationKey] = next
                changed += next
            }
            messageEntrySameRevisionChanged0655(old, next) -> {
                previousByConversation[next.conversationKey] = next
                changed += next
            }
        }
    }

    val mergedEntries = previousByConversation.values
        .sortedWith(
            compareByDescending<BlaBlaMessageIndexEntry0655> { messageActivityEpoch0655(it.lastActivity) }
                .thenBy { it.conversationKey },
        )
        .take(MAX_MESSAGE_ROWS_0655)

    return BlaBlaMessageIndexDelta0655(
        baseline = false,
        changed = changed.distinctBy(BlaBlaMessageIndexEntry0655::conversationKey),
        merged = BlaBlaMessageIndexSnapshot0655(
            capturedAtMillis = maxOf(previous.capturedAtMillis, incoming.capturedAtMillis),
            entries = mergedEntries,
        ),
    )
}

private fun normalizedMessageEntry0655(entry: BlaBlaMessageIndexEntry0655): BlaBlaMessageIndexEntry0655 {
    val href = entry.href.trim().take(1000)
    return entry.copy(
        conversationKey = entry.conversationKey.trim().ifBlank { messageConversationKey0655(href) }.take(240),
        groupKey = entry.groupKey.trim().lowercase().ifBlank { messageGroupKey0655(href) }.take(80),
        href = href,
        passengerName = entry.passengerName.trim().replace(Regex("\\s+"), " ").take(160),
        routeText = entry.routeText.trim().replace(Regex("\\s+"), " ").take(240),
        lastActivity = entry.lastActivity.trim().take(80),
    )
}

private fun messageEntryIsNewer0655(
    old: BlaBlaMessageIndexEntry0655,
    next: BlaBlaMessageIndexEntry0655,
): Boolean {
    val oldEpoch = messageActivityEpoch0655(old.lastActivity)
    val nextEpoch = messageActivityEpoch0655(next.lastActivity)
    return nextEpoch > oldEpoch
}

private fun messageEntrySameRevisionChanged0655(
    old: BlaBlaMessageIndexEntry0655,
    next: BlaBlaMessageIndexEntry0655,
): Boolean {
    val oldEpoch = messageActivityEpoch0655(old.lastActivity)
    val nextEpoch = messageActivityEpoch0655(next.lastActivity)
    if (oldEpoch != nextEpoch) return false
    if (old.lastActivity != next.lastActivity && oldEpoch == Long.MIN_VALUE) return true
    return old.passengerName != next.passengerName ||
        old.routeText != next.routeText ||
        old.groupKey != next.groupKey ||
        old.href != next.href
}

private fun messageActivityEpoch0655(value: String): Long =
    runCatching { Instant.parse(value.trim()).toEpochMilli() }.getOrDefault(Long.MIN_VALUE)

private const val MAX_MESSAGE_ROWS_0655 = 500
private val MESSAGE_GROUP_PREFIX_0655 = Regex(
    "^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:_|$)",
    RegexOption.IGNORE_CASE,
)

class BlaBlaMessageNotificationListener0655 : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        BlaBlaMessageRefreshScheduler0655.signal(applicationContext, "listener_connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (!isBlaBlaNotificationPackage0655(posted.packageName)) return

        val eventKey = sha256TripPublication0387(
            posted.packageName + "|" + posted.id + "|" + posted.postTime,
        ).take(16)
        UnifiedDebugEventStore.record(
            "BLABLACAR_NOTIFICATION_INVALIDATION_0655",
            packageName,
            "eventKey=" + eventKey + " contentRead=false authoritative=false action=REFRESH_MESSAGES_HTML",
        )
        BlaBlaMessageRefreshScheduler0655.signal(applicationContext, "notification")
    }
}

internal object BlaBlaMessageRefreshScheduler0655 {
    private const val PREFS = "blablacar_message_trigger_0655"
    private const val KEY_REVISION = "trigger_revision"
    private const val WORK_NAME = "blablacar-message-html-invalidation-0655"

    fun signal(context: Context, reason: String) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = prefs.getLong(KEY_REVISION, 0L).coerceAtLeast(0L) + 1L
        prefs.edit().putLong(KEY_REVISION, next).apply()

        val request = OneTimeWorkRequestBuilder<BlaBlaMessageRefreshWorker0655>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInitialDelay(NOTIFICATION_DEBOUNCE_MS_0655, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(app).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
        UnifiedDebugEventStore.record(
            "BLABLACAR_MESSAGE_HTML_TRIGGER_QUEUED_0655",
            app.packageName,
            "revision=" + next + " reason=" + reason.take(40) +
                " uniqueWork=true debounceMs=" + NOTIFICATION_DEBOUNCE_MS_0655,
        )
    }

    fun revision(context: Context): Long =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_REVISION, 0L)
            .coerceAtLeast(0L)
}

class BlaBlaMessageRefreshWorker0655(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        var pass = 0
        var transientFailure = false
        do {
            val before = BlaBlaMessageRefreshScheduler0655.revision(applicationContext)
            val result = BlaBlaMessageHtmlCoordinator0655.refresh(applicationContext)
            transientFailure = transientFailure || result.transientFailure
            val after = BlaBlaMessageRefreshScheduler0655.revision(applicationContext)
            pass += 1
            if (after <= before) break
        } while (pass < MAX_DRAIN_PASSES_0655)

        return if (transientFailure && runAttemptCount < 3) Result.retry() else Result.success()
    }
}

private data class BlaBlaMessageCoordinatorResult0655(
    val transientFailure: Boolean,
)

@Serializable
private data class BlaBlaMessageDomEnvelope0655(
    val finalUrl: String = "",
    val documentReady: Boolean = false,
    val explicitEmpty: Boolean = false,
    val entries: List<BlaBlaMessageDomEntry0655> = emptyList(),
    val domHtml: String = "",
)

@Serializable
private data class BlaBlaMessageDomEntry0655(
    val href: String = "",
    val passengerName: String = "",
    val routeText: String = "",
    val lastActivity: String = "",
)

private data class BlaBlaMessageHtmlCapture0655(
    val snapshot: BlaBlaMessageIndexSnapshot0655? = null,
    val rawHtml: String = "",
    val authRequired: Boolean = false,
    val transientFailure: Boolean = false,
)

private object BlaBlaMessageHtmlCoordinator0655 {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun refresh(context: Context): BlaBlaMessageCoordinatorResult0655 {
        val app = context.applicationContext
        val registry = BlaBlaDynamicAccountRegistry(app)
        val accounts = registry.list().filter { account ->
            !account.profileUuid.isNullOrBlank() && account.verifiedDefinition() != null
        }
        if (accounts.isEmpty()) {
            UnifiedDebugEventStore.record(
                "BLABLACAR_MESSAGE_HTML_NO_VERIFIED_ACCOUNT_0655",
                app.packageName,
                "accounts=0 action=NO_OP",
            )
            return BlaBlaMessageCoordinatorResult0655(transientFailure = false)
        }

        var transientFailure = false
        for (account in accounts) {
            val capture = captureMessagesList0655(app, account)
            if (capture.authRequired) {
                UnifiedDebugEventStore.record(
                    "BLABLACAR_MESSAGE_HTML_AUTH_REQUIRED_0655",
                    app.packageName,
                    "accountKey=" + seatSyncDiagnosticKey(account.id) + " snapshotAdvanced=false",
                )
                continue
            }
            if (capture.snapshot == null) {
                transientFailure = transientFailure || capture.transientFailure
                continue
            }

            persistHtmlEvidence0655(app, account.id, capture.rawHtml)

            val store = BlaBlaMessageIndexStore0655(app)
            val previous = store.read(account.id)
            val delta = messageIndexDelta0655(previous, capture.snapshot)
            if (delta.baseline) {
                store.save(account.id, delta.merged)
                UnifiedDebugEventStore.recordAlways(
                    "BLABLACAR_MESSAGE_HTML_BASELINE_0655",
                    app.packageName,
                    "accountKey=" + seatSyncDiagnosticKey(account.id) +
                        " rows=" + delta.merged.entries.size + " refreshQueued=0",
                )
                continue
            }
            if (delta.changed.isEmpty()) {
                store.save(account.id, delta.merged)
                UnifiedDebugEventStore.record(
                    "BLABLACAR_MESSAGE_HTML_UNCHANGED_0655",
                    app.packageName,
                    "accountKey=" + seatSyncDiagnosticKey(account.id) +
                        " rows=" + delta.merged.entries.size + " refreshQueued=0",
                )
                continue
            }

            val affected = resolveAffectedTrips0655(app, account, delta.changed)
            val allAcknowledged = enqueueAffectedTrips0655(app, affected)
            if (allAcknowledged) {
                store.save(account.id, delta.merged)
            } else {
                transientFailure = true
            }
            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_MESSAGE_HTML_DELTA_0655",
                app.packageName,
                "accountKey=" + seatSyncDiagnosticKey(account.id) +
                    " changedRows=" + delta.changed.size +
                    " affectedTrips=" + affected.size +
                    " allAcknowledged=" + allAcknowledged +
                    " snapshotAdvanced=" + allAcknowledged +
                    " authority=HTML_MESSAGES_TRIGGER_ONLY notificationContentRead=false",
            )
        }
        return BlaBlaMessageCoordinatorResult0655(transientFailure = transientFailure)
    }

    private fun resolveAffectedTrips0655(
        context: Context,
        account: BlaBlaDynamicAccount,
        changed: List<BlaBlaMessageIndexEntry0655>,
    ): List<Trip> {
        val profileUuid = account.profileUuid?.trim()?.lowercase().orEmpty()
        val now = System.currentTimeMillis()
        val tripStore = TripStore(context)
        val candidates = tripStore.trips().filter { trip ->
            !trip.deleted &&
                trip.blablaProfileUuid?.trim()?.lowercase() == profileUuid &&
                !trip.blablaTripId.isNullOrBlank() &&
                (
                    trip.agendaVisibleUntilMillis0581 > now ||
                        trip.departureAtMillis >= now - ACTIVE_TRIP_GRACE_MS_0655
                    )
        }
        if (candidates.isEmpty()) return emptyList()

        val resolved = linkedSetOf<Trip>()
        var ambiguousOrUnknown = false

        changed.forEach { entry ->
            val direct = candidates.filter { it.blablaTripId?.trim() == entry.groupKey }
            if (direct.size == 1) {
                resolved += direct.single()
                return@forEach
            }

            val passengerKey = normalizeMessageIdentity0655(entry.passengerName)
            val byPassenger = if (passengerKey.isBlank()) {
                emptyList()
            } else {
                candidates.filter { trip ->
                    tripStore.bookingsFor(trip.id).any { booking ->
                        normalizeMessageIdentity0655(booking.passengerName) == passengerKey
                    }
                }
            }
            val route = messageRouteEndpoints0655(entry.routeText)
            val narrowed = if (route == null) byPassenger else byPassenger.filter { trip ->
                tripRouteMatches0655(trip, route)
            }
            when {
                narrowed.size == 1 -> resolved += narrowed.single()
                byPassenger.size == 1 -> resolved += byPassenger.single()
                else -> ambiguousOrUnknown = true
            }
        }

        if (ambiguousOrUnknown) {
            resolved += candidates
        }
        return resolved.toList()
    }

    private fun tripRouteMatches0655(trip: Trip, route: Pair<String, String>): Boolean {
        val stops = trip.stops.sortedBy(TripStop::order)
        val first = normalizeMessageIdentity0655(stops.firstOrNull()?.name)
        val last = normalizeMessageIdentity0655(stops.lastOrNull()?.name)
        return placeEquivalent0655(first, route.first) && placeEquivalent0655(last, route.second)
    }

    private fun placeEquivalent0655(left: String, right: String): Boolean =
        left.isNotBlank() && right.isNotBlank() &&
            (left == right || left.contains(right) || right.contains(left))

    private fun enqueueAffectedTrips0655(context: Context, trips: List<Trip>): Boolean {
        var allAcknowledged = true
        trips.distinctBy { it.tripKey.ifBlank { it.id } }.forEach { trip ->
            val target = CentralDayCommandBridge0552.target(context, trip)
            if (target == null) {
                allAcknowledged = false
                return@forEach
            }
            val commandStore = BlaBlaTripCommandStatusStore0407(context)
            if (commandStore.get(target)?.pending == true) return@forEach

            val command = BlaBlaCommand0407.forTarget(
                target = target,
                operation = BlaBlaTripCapability0407.REVERIFY_TRIP,
                origin = BlaBlaCommandOrigin0407.SYSTEM_RECONCILIATION,
            )
            val queued = AgendaBackgroundSync0392.enqueueTripCollectorRefresh0517(
                context = context,
                target = target,
                commandId = command.commandId,
                requestedAtMillis = command.requestedAtMillis,
            )
            val acknowledged = queued || commandStore.get(target)?.pending == true
            allAcknowledged = allAcknowledged && acknowledged
        }
        return allAcknowledged
    }

    private suspend fun captureMessagesList0655(
        context: Context,
        account: BlaBlaDynamicAccount,
    ): BlaBlaMessageHtmlCapture0655 = withContext(Dispatchers.Main.immediate) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            return@withContext BlaBlaMessageHtmlCapture0655(transientFailure = false)
        }

        val themed = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault)
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
                webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }

            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                var completed = false
                var mainFrameError = false

                fun finish(result: BlaBlaMessageHtmlCapture0655) {
                    if (completed || !continuation.isActive) return
                    completed = true
                    handler.removeCallbacksAndMessages(null)
                    continuation.resume(result)
                }

                fun evaluate() {
                    if (completed) return
                    val finalUrl = webView.url.orEmpty()
                    if (BlaBlaCarSessionKeeper0552.isExplicitLoginUrl(finalUrl)) {
                        finish(BlaBlaMessageHtmlCapture0655(authRequired = true))
                        return
                    }
                    webView.evaluateJavascript(MESSAGES_LIST_SCRIPT_0655) { raw ->
                        if (completed) return@evaluateJavascript
                        val decoded = decodeJavascriptString0655(raw)
                        val envelope = runCatching {
                            json.decodeFromString<BlaBlaMessageDomEnvelope0655>(decoded)
                        }.getOrNull()
                        if (envelope == null) {
                            finish(BlaBlaMessageHtmlCapture0655(transientFailure = true))
                            return@evaluateJavascript
                        }
                        if (BlaBlaCarSessionKeeper0552.isExplicitLoginUrl(envelope.finalUrl)) {
                            finish(BlaBlaMessageHtmlCapture0655(authRequired = true))
                            return@evaluateJavascript
                        }
                        val entries = envelope.entries.map { row ->
                            BlaBlaMessageIndexEntry0655(
                                conversationKey = messageConversationKey0655(row.href),
                                groupKey = messageGroupKey0655(row.href),
                                href = row.href,
                                passengerName = row.passengerName,
                                routeText = row.routeText,
                                lastActivity = row.lastActivity,
                            )
                        }.filter { it.conversationKey.isNotBlank() }
                        val complete = envelope.documentReady &&
                            (entries.isNotEmpty() || envelope.explicitEmpty)
                        if (!complete) {
                            finish(BlaBlaMessageHtmlCapture0655(transientFailure = true))
                            return@evaluateJavascript
                        }
                        finish(
                            BlaBlaMessageHtmlCapture0655(
                                snapshot = BlaBlaMessageIndexSnapshot0655(
                                    capturedAtMillis = System.currentTimeMillis(),
                                    entries = entries,
                                ),
                                rawHtml = envelope.domHtml,
                            ),
                        )
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        handler.postDelayed(::evaluate, MESSAGE_DOM_SETTLE_MS_0655)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) mainFrameError = true
                    }
                }

                continuation.invokeOnCancellation {
                    handler.removeCallbacksAndMessages(null)
                }
                handler.postDelayed({
                    finish(
                        BlaBlaMessageHtmlCapture0655(
                            transientFailure = mainFrameError || webView.url.isNullOrBlank(),
                        ),
                    )
                }, MESSAGE_CAPTURE_TIMEOUT_MS_0655)
                webView.loadUrl(MESSAGES_LIST_URL_0655)
            }
        } finally {
            runCatching { webView.stopLoading() }
            runCatching { webView.webViewClient = WebViewClient() }
            runCatching { webView.loadUrl("about:blank") }
            runCatching { webView.clearHistory() }
            runCatching { webView.removeAllViews() }
            runCatching { webView.destroy() }
        }
    }

    private fun decodeJavascriptString0655(raw: String?): String {
        val value = raw?.takeIf { it != "null" } ?: return ""
        return runCatching { JSONTokener(value).nextValue() as? String }
            .getOrNull()
            .orEmpty()
    }

    private suspend fun persistHtmlEvidence0655(
        context: Context,
        accountId: String,
        html: String,
    ) = withContext(Dispatchers.IO) {
        if (html.isBlank()) return@withContext
        val root = File(
            context.filesDir,
            "blablacar_messages_0655/" + seatSyncDiagnosticKey(accountId).take(24),
        )
        if (!root.exists() && !root.mkdirs()) return@withContext
        val latest = File(root, "messages-list-latest.html")
        val previous = File(root, "messages-list-previous.html")
        val temp = File(root, "messages-list.tmp")
        runCatching {
            temp.writeText(html, Charsets.UTF_8)
            if (latest.exists()) {
                if (previous.exists()) previous.delete()
                latest.renameTo(previous)
            }
            if (!temp.renameTo(latest)) {
                latest.writeText(html, Charsets.UTF_8)
                temp.delete()
            }
        }
    }
}

private class BlaBlaMessageIndexStore0655(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(accountId: String): BlaBlaMessageIndexSnapshot0655? =
        prefs.getString(key(accountId), null)?.let { encoded ->
            runCatching {
                json.decodeFromString<BlaBlaMessageIndexSnapshot0655>(encoded)
            }.getOrNull()
        }

    fun save(accountId: String, snapshot: BlaBlaMessageIndexSnapshot0655) {
        prefs.edit().putString(key(accountId), json.encodeToString(snapshot)).apply()
    }

    private fun key(accountId: String): String =
        "snapshot_" + seatSyncDiagnosticKey(accountId).take(32)

    companion object {
        private const val PREFS = "blablacar_message_index_0655"
    }
}

private const val MESSAGES_LIST_URL_0655 = "https://www.blablacar.com.br/messages/list"
private const val NOTIFICATION_DEBOUNCE_MS_0655 = 1_500L
private const val MESSAGE_DOM_SETTLE_MS_0655 = 1_200L
private const val MESSAGE_CAPTURE_TIMEOUT_MS_0655 = 20_000L
private const val MAX_DRAIN_PASSES_0655 = 4
private const val ACTIVE_TRIP_GRACE_MS_0655 = 12L * 60L * 60L * 1000L

private val MESSAGES_LIST_SCRIPT_0655 = """
(function() {
  const clean = (value) => (value || '').replace(/\\s+/g, ' ').trim();
  const anchors = Array.from(document.querySelectorAll('a[href*="/messages/show/"]'));
  const seen = new Set();
  const entries = [];
  for (const anchor of anchors) {
    let href = '';
    try { href = new URL(anchor.getAttribute('href') || '', location.href).href; } catch (_) {}
    if (!href || seen.has(href)) continue;
    seen.add(href);
    const titleNode =
      anchor.querySelector('[data-testid="legacy-item-title-with-body"]') ||
      anchor.querySelector('.kirk-item-title--withBody') ||
      anchor.querySelector('[class*="item-title"]');
    const routeNode =
      anchor.querySelector('[data-testid="e2e-messaging-summary-item-sublabel"]') ||
      anchor.querySelector('[data-testid*="messaging-summary"][data-testid*="sublabel"]');
    const timeNode = anchor.querySelector('time[datetime]');
    entries.push({
      href: href,
      passengerName: clean(titleNode && (titleNode.innerText || titleNode.textContent)),
      routeText: clean(routeNode && (routeNode.innerText || routeNode.textContent)),
      lastActivity: clean(timeNode && timeNode.getAttribute('datetime'))
    });
  }
  const body = clean(document.body && document.body.innerText).toLowerCase();
  const explicitEmpty =
    /nenhuma mensagem|nenhuma conversa|no messages|no conversation/.test(body);
  return JSON.stringify({
    finalUrl: location.href,
    documentReady: document.readyState === 'complete' || document.readyState === 'interactive',
    explicitEmpty: explicitEmpty,
    entries: entries,
    domHtml: document.documentElement ? document.documentElement.outerHTML : ''
  });
})();
""".trimIndent()
