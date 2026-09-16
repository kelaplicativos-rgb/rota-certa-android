package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore

/**
 * 0.1.564 — minimal real BlaBlaCar browser used by "Todas as viagens".
 *
 * There are intentionally no Rota Certa trip-operation shortcuts here. The activity
 * reuses the already isolated AndroidX WebView profile of the owning account, loads the
 * original administrative trip URL and only records success after the final main-frame
 * destination resolves back to the exact requested trip.
 */
class OperationalTripBrowserActivity0564 : Activity() {
    private val settleHandler = Handler(Looper.getMainLooper())
    private lateinit var registry: BlaBlaDynamicAccountRegistry
    private lateinit var account: BlaBlaDynamicAccount
    private lateinit var webView: WebView

    private var operationId = ""
    private var requestedTripId = ""
    private var requestedUrl = ""
    private var expectedProfileUuid = ""
    private var navigationGeneration = 0L
    private var terminalAttestation: OperationalBrowserNavigationResult0564? = null
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = BlaBlaDynamicAccountRegistry(applicationContext)

        val accountId = intent?.getStringExtra(OperationalTripBrowserIntents0564.EXTRA_ACCOUNT_ID)
            ?.trim().orEmpty()
        operationId = intent?.getStringExtra(OperationalTripBrowserIntents0564.EXTRA_OPERATION_ID)
            ?.trim().orEmpty()
        requestedTripId = intent?.getStringExtra(OperationalTripBrowserIntents0564.EXTRA_TARGET_TRIP_ID)
            ?.trim().orEmpty()
        requestedUrl = intent?.getStringExtra(OperationalTripBrowserIntents0564.EXTRA_TARGET_URL)
            ?.trim().orEmpty()
        expectedProfileUuid = intent?.getStringExtra(OperationalTripBrowserIntents0564.EXTRA_EXPECTED_PROFILE_UUID)
            ?.trim()?.lowercase().orEmpty()

        val liveAccount = registry.get(accountId)
        if (liveAccount == null) {
            rejectBeforeNavigation0564("ACCOUNT_NOT_AVAILABLE")
            return
        }
        account = liveAccount

        val liveProfile = account.profileUuid?.trim()?.lowercase().orEmpty()
        val urlTripId = BlaBlaCollectorUrlModule.tripId(requestedUrl).orEmpty()
        if (
            operationId.isBlank() ||
            requestedTripId.isBlank() ||
            expectedProfileUuid.isBlank() ||
            liveProfile != expectedProfileUuid ||
            urlTripId != requestedTripId ||
            !BlaBlaCollectorUrlModule.isManageTarget(requestedUrl)
        ) {
            rejectBeforeNavigation0564(
                when {
                    liveProfile != expectedProfileUuid -> "IDENTITY_MISMATCH"
                    urlTripId != requestedTripId -> "TARGET_URL_TRIP_MISMATCH"
                    !BlaBlaCollectorUrlModule.isManageTarget(requestedUrl) -> "INVALID_MANAGE_TARGET"
                    else -> "INCOMPLETE_TARGET"
                },
            )
            return
        }

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            rejectBeforeNavigation0564("MULTI_PROFILE_UNAVAILABLE")
            return
        }

        createBrowser0564()
        BlaBlaCarSessionKeeper0552.observeAcquire(this, account, "operational_browser_0564")
        UnifiedDebugEventStore.recordAlways(
            "OPERATIONAL_BROWSER_TARGET_LOAD_STARTED_0564",
            packageName,
            operationalAttestationDetails0564(
                result = "LOAD_STARTED",
                finalUrl = requestedUrl,
                finalTripId = urlTripId,
                profileMatches = true,
            ),
        )
        webView.loadUrl(requestedUrl)
    }

    private fun createBrowser0564() {
        webView = WebView(this)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                handleNonWebNavigation0564(request?.url)

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                handleNonWebNavigation0564(url?.let(Uri::parse))

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                navigationGeneration += 1L
                BlaBlaCarSessionKeeper0552.observeNavigation(
                    this@OperationalTripBrowserActivity0564,
                    account,
                    url,
                )
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (terminalAttestation != null || destroyed) return
                val expectedGeneration = navigationGeneration
                settleHandler.postDelayed({
                    if (
                        !destroyed &&
                        terminalAttestation == null &&
                        expectedGeneration == navigationGeneration
                    ) {
                        attestFinalDestination0564(view.url.orEmpty())
                    }
                }, FINAL_DESTINATION_SETTLE_MS)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                super.onReceivedError(view, request, error)
                if (!request.isForMainFrame || terminalAttestation != null) return
                UnifiedDebugEventStore.recordAlways(
                    "OPERATIONAL_BROWSER_TARGET_NAVIGATION_FAILED_0564",
                    packageName,
                    operationalAttestationDetails0564(
                        result = "TRANSPORT_ERROR",
                        finalUrl = request.url?.toString().orEmpty(),
                        finalTripId = "",
                        profileMatches = liveProfileMatches0564(),
                    ) + " errorCode=${error.errorCode}",
                )
            }
        }
        setContentView(webView)
    }

    private fun attestFinalDestination0564(finalUrl: String) {
        if (terminalAttestation != null || destroyed) return
        if (finalUrl.isBlank()) {
            UnifiedDebugEventStore.recordAlways(
                "OPERATIONAL_BROWSER_TARGET_NAVIGATION_UNVERIFIED_0564",
                packageName,
                operationalAttestationDetails0564(
                    result = OperationalBrowserNavigationResult0564.UNVERIFIED.name,
                    finalUrl = finalUrl,
                    finalTripId = "",
                    profileMatches = liveProfileMatches0564(),
                ),
            )
            return
        }

        val profileMatches = liveProfileMatches0564()
        val finalTripId = BlaBlaCollectorUrlModule.tripId(finalUrl).orEmpty()
        val authRequired = operationalBrowserAuthUrl0564(finalUrl)
        val allowed = BlaBlaCollectorUrlModule.isAllowed(finalUrl)
        val result = operationalBrowserNavigationResult0564(
            requestedTripId = requestedTripId,
            finalTripId = finalTripId,
            profileMatches = profileMatches,
            authRequired = authRequired,
            finalDestinationAllowed = allowed,
        )

        if (result == OperationalBrowserNavigationResult0564.UNVERIFIED) {
            UnifiedDebugEventStore.recordAlways(
                "OPERATIONAL_BROWSER_TARGET_NAVIGATION_UNVERIFIED_0564",
                packageName,
                operationalAttestationDetails0564(
                    result = result.name,
                    finalUrl = finalUrl,
                    finalTripId = finalTripId,
                    profileMatches = profileMatches,
                ),
            )
            return
        }

        terminalAttestation = result
        val event = if (result == OperationalBrowserNavigationResult0564.CONFIRMED) {
            "OPERATIONAL_BROWSER_TARGET_NAVIGATION_CONFIRMED_0564"
        } else {
            "OPERATIONAL_BROWSER_TARGET_NAVIGATION_FAILED_0564"
        }
        UnifiedDebugEventStore.recordAlways(
            event,
            packageName,
            operationalAttestationDetails0564(
                result = result.name,
                finalUrl = finalUrl,
                finalTripId = finalTripId,
                profileMatches = profileMatches,
            ),
        )
    }

    private fun liveProfileMatches0564(): Boolean {
        val live = registry.get(account.id)
        return live != null &&
            expectedProfileUuid.isNotBlank() &&
            live.profileUuid?.trim()?.lowercase() == expectedProfileUuid
    }

    private fun operationalAttestationDetails0564(
        result: String,
        finalUrl: String,
        finalTripId: String,
        profileMatches: Boolean,
    ): String {
        val requestedKey = sha256TripPublication0387(requestedTripId).take(16)
        val finalKey = finalTripId.takeIf(String::isNotBlank)
            ?.let { sha256TripPublication0387(it).take(16) }
            .orEmpty()
        val finalHost = runCatching { Uri.parse(finalUrl).host.orEmpty().lowercase() }
            .getOrDefault("")
            .take(120)
        return "operationId=${operationId.take(80)} " +
            "accountKey=${sha256TripPublication0387(account.id).take(16)} " +
            "requestedTripKey=$requestedKey finalTripKey=$finalKey " +
            "profileMatches=$profileMatches " +
            "finalTripMatches=${finalTripId.isNotBlank() && finalTripId == requestedTripId} " +
            "finalHost=$finalHost result=$result piiLogged=false cookiesLogged=false"
    }

    private fun rejectBeforeNavigation0564(reason: String) {
        UnifiedDebugEventStore.recordAlways(
            "OPERATIONAL_BROWSER_TARGET_NAVIGATION_REJECTED_0564",
            packageName,
            "operationId=${operationId.take(80)} reason=${reason.take(80)} " +
                "tripPresent=${requestedTripId.isNotBlank()} expectedProfilePresent=${expectedProfileUuid.isNotBlank()} " +
                "failClosed=true piiLogged=false cookiesLogged=false",
        )
        setContentView(TextView(this).apply {
            text = "Não foi possível confirmar com segurança a conta e a viagem da BlaBlaCar. Volte e reconfirme a conta conectada."
            setPadding(32, 32, 32, 32)
        })
    }

    private fun handleNonWebNavigation0564(uri: Uri?): Boolean {
        val target = uri ?: return false
        val scheme = target.scheme?.lowercase().orEmpty()
        if (scheme == "http" || scheme == "https") return false
        return runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, target))
            true
        }.getOrDefault(true)
    }

    @Deprecated("Android framework API")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        destroyed = true
        settleHandler.removeCallbacksAndMessages(null)
        if (terminalAttestation == null && operationId.isNotBlank()) {
            UnifiedDebugEventStore.recordAlways(
                "OPERATIONAL_BROWSER_TARGET_NAVIGATION_CANCELLED_0564",
                packageName,
                "operationId=${operationId.take(80)} result=CANCELLED_BY_LIFECYCLE " +
                    "confirmed=false failClosed=true",
            )
        }
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val FINAL_DESTINATION_SETTLE_MS = 650L
    }
}

internal object OperationalTripBrowserIntents0564 {
    const val EXTRA_ACCOUNT_ID = "operational_browser_account_id_0564"
    const val EXTRA_OPERATION_ID = "operational_browser_operation_id_0564"
    const val EXTRA_TARGET_TRIP_ID = "operational_browser_target_trip_id_0564"
    const val EXTRA_TARGET_URL = "operational_browser_target_url_0564"
    const val EXTRA_EXPECTED_PROFILE_UUID = "operational_browser_expected_profile_uuid_0564"

    fun open(
        context: Context,
        account: BlaBlaDynamicAccount,
        target: BlaBlaTripTarget0407,
        operationId: String,
    ): Intent = Intent(context, OperationalTripBrowserActivity0564::class.java)
        .putExtra(EXTRA_ACCOUNT_ID, account.id)
        .putExtra(EXTRA_OPERATION_ID, operationId)
        .putExtra(EXTRA_TARGET_TRIP_ID, target.tripId)
        .putExtra(EXTRA_TARGET_URL, target.tripHref)
        .putExtra(EXTRA_EXPECTED_PROFILE_UUID, target.profileUuid)
}

internal fun operationalBrowserAuthUrl0564(rawUrl: String): Boolean {
    val normalized = rawUrl.lowercase()
    return listOf(
        "/login",
        "/signin",
        "/sign-in",
        "/auth/",
        "/authentication",
        "/connexion",
    ).any(normalized::contains)
}
