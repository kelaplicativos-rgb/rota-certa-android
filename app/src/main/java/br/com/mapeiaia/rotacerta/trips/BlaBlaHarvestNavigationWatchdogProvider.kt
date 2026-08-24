package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * Fail-closed navigation watchdog for the authenticated BlaBlaCar harvester.
 *
 * The harvester already knows how to retry unreadable pages and advance its
 * current block. Its remaining physical failure mode is a WebView navigation
 * that never emits onPageFinished; in that case the existing state machine is
 * never given a chance to apply those rules. This provider installs a small
 * wrapper around the harvester's existing WebViewClient and, after a bounded
 * timeout, delivers the missing completion callback back to that same client.
 *
 * Automatic harvesting is passenger-focused. Published-seat Editar/Lugares work
 * is not part of the automatic path. The provider strips those actions from trip
 * detail pages and also fail-closes if an old state-machine target still tries to
 * navigate there. The explicit manual seat-sync Activity is not wrapped here.
 *
 * Navigation identity includes the canonical id query parameter when present.
 * This is required because current BlaBlaCar trip pages commonly share the same
 * /rides/offer path while the strong trip identity lives in ?id=... . Treating
 * path alone as identity lets a late callback from trip N leak into trip N+1.
 *
 * The existing activity keeps its conservative delayed read as a fallback. This
 * wrapper asks the same private state machine to probe earlier after a verified
 * page completion. If the DOM is not ready, the activity's existing bounded
 * retries remain authoritative; no evidence is fabricated and no identity check
 * is bypassed.
 *
 * No route, city, account, passenger, capacity or locale is hardcoded here.
 * The wrapper does not open external dialers and preserves the existing tel:
 * interception implemented by BlaBlaMhtmlHarvestActivity.
 */
class BlaBlaHarvestNavigationWatchdogProvider : ContentProvider() {
    private val attached = WeakHashMap<Activity, WatchdogWebViewClient>()

    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity !is BlaBlaMhtmlHarvestActivity) return
                activity.window.decorView.post { attach(activity) }
            }

            override fun onActivityDestroyed(activity: Activity) {
                attached.remove(activity)?.dispose()
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
        return true
    }

    private fun attach(activity: BlaBlaMhtmlHarvestActivity) {
        if (activity.isFinishing || activity.isDestroyed || attached.containsKey(activity)) return
        val webView = findWebView(activity.window.decorView) ?: return
        val delegate = webView.webViewClient ?: WebViewClient()
        if (delegate is WatchdogWebViewClient) return
        val wrapper = WatchdogWebViewClient(
            appContext = activity.applicationContext,
            activity = activity,
            webView = webView,
            delegate = delegate,
        )
        attached[activity] = wrapper
        webView.webViewClient = wrapper
        wrapper.armInitialNavigation()
        UnifiedDebugEventStore.record(
            "HARVEST_NAVIGATION_WATCHDOG_ATTACHED",
            activity.packageName,
            "timeoutMs=$NAVIGATION_TIMEOUT_MS directSource=true syntheticPageFinished=true automaticSeatLookup=${BlaBlaHarvestPolicy.AUTOMATIC_PUBLISHED_SEAT_LOOKUP} strongQueryIdentity=true fastProbeMs=${BlaBlaHarvestPolicy.AUTOMATIC_PAGE_SETTLE_MS}",
        )
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private class WatchdogWebViewClient(
        private val appContext: Context,
        private val activity: BlaBlaMhtmlHarvestActivity,
        private val webView: WebView,
        private val delegate: WebViewClient,
    ) : WebViewClient() {
        private val handler = Handler(Looper.getMainLooper())
        private var generation = 0L
        private var expectedUrl: String? = null
        private var disposed = false
        private val handlePageMethod: Method? by lazy { privateMethod("handlePage") }
        private val finishHarvestMethod: Method? by lazy { privateMethod("finishHarvest") }

        fun armInitialNavigation() {
            arm(webView.url ?: RIDES_URL)
        }

        fun dispose() {
            disposed = true
            generation++
            expectedUrl = null
            handler.removeCallbacksAndMessages(null)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (
                !BlaBlaHarvestPolicy.AUTOMATIC_PUBLISHED_SEAT_LOOKUP &&
                BlaBlaHarvestNavigationIdentity.isEditOrOptionsHref(url)
            ) {
                handler.removeCallbacksAndMessages(null)
                generation++
                expectedUrl = null
                view.stopLoading()
                val completed = invokePrivate(finishHarvestMethod, "finishHarvest")
                UnifiedDebugEventStore.record(
                    "HARVEST_PUBLISHED_SEAT_PHASE_SHORT_CIRCUITED",
                    appContext.packageName,
                    "automatic=true path=${safePath(url)} publishedSeatLookup=false completed=$completed externalWrite=false",
                )
                if (completed) return
            }
            arm(url)
            delegate.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (disposed) return
            val current = view.url
            if (isBlaBlaUrl(current) && isBlaBlaUrl(url) && !sameNavigationUrl(current, url)) {
                UnifiedDebugEventStore.record(
                    "HARVEST_STALE_PAGE_FINISHED_IGNORED",
                    appContext.packageName,
                    "currentPath=${safePath(current)} stalePath=${safePath(url)} strongQueryIdentity=true",
                )
                return
            }
            generation++
            expectedUrl = null
            handler.removeCallbacksAndMessages(null)
            deliverPageFinished(view, url, synthetic = false)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
            delegate.shouldOverrideUrlLoading(view, request)

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
            delegate.shouldOverrideUrlLoading(view, url)

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            delegate.onReceivedError(view, request, error)
        }

        private fun deliverPageFinished(view: WebView, url: String, synthetic: Boolean) {
            if (
                BlaBlaHarvestPolicy.AUTOMATIC_PUBLISHED_SEAT_LOOKUP ||
                !isTripDetailUrl(url)
            ) {
                deliverDelegateAndAccelerate(view, url)
                return
            }
            val deliveredGeneration = generation
            view.evaluateJavascript(SUPPRESS_EDIT_LINKS_JS) {
                if (disposed || activity.isFinishing || activity.isDestroyed) return@evaluateJavascript
                if (generation != deliveredGeneration || !sameNavigationUrl(view.url, url)) {
                    UnifiedDebugEventStore.record(
                        "HARVEST_STALE_PAGE_FINISHED_IGNORED",
                        appContext.packageName,
                        "currentPath=${safePath(view.url)} stalePath=${safePath(url)} seatLookupSuppression=true strongQueryIdentity=true",
                    )
                    return@evaluateJavascript
                }
                UnifiedDebugEventStore.record(
                    "HARVEST_SEAT_OPTIONS_SKIPPED",
                    appContext.packageName,
                    "automatic=true tripDetail=true editLinkSuppressed=true publishedSeatLookup=false capacityAuthority=rota_certa_config syntheticPageFinished=$synthetic strongQueryIdentity=true",
                )
                deliverDelegateAndAccelerate(view, url)
            }
        }

        private fun deliverDelegateAndAccelerate(view: WebView, url: String) {
            delegate.onPageFinished(view, url)
            scheduleFastProbe(view, url)
        }

        private fun scheduleFastProbe(view: WebView, url: String) {
            val expectedGeneration = generation
            handler.postDelayed({
                if (disposed || activity.isFinishing || activity.isDestroyed) return@postDelayed
                if (generation != expectedGeneration || !sameNavigationUrl(view.url, url)) return@postDelayed
                val invoked = invokePrivate(handlePageMethod, "handlePage")
                UnifiedDebugEventStore.record(
                    "HARVEST_FAST_DOM_PROBE",
                    appContext.packageName,
                    "delayMs=${BlaBlaHarvestPolicy.AUTOMATIC_PAGE_SETTLE_MS} path=${safePath(url)} invoked=$invoked fallbackPreserved=true",
                )
            }, BlaBlaHarvestPolicy.AUTOMATIC_PAGE_SETTLE_MS)
        }

        private fun arm(url: String?) {
            if (disposed || activity.isFinishing || activity.isDestroyed) return
            val normalized = url?.takeIf(::isBlaBlaUrl) ?: return
            generation++
            val armedGeneration = generation
            expectedUrl = normalized
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                if (disposed || activity.isFinishing || activity.isDestroyed) return@postDelayed
                if (generation != armedGeneration || expectedUrl == null) return@postDelayed
                val current = webView.url?.takeIf(::isBlaBlaUrl) ?: expectedUrl ?: return@postDelayed
                generation++
                expectedUrl = null
                UnifiedDebugEventStore.record(
                    "HARVEST_NAVIGATION_TIMEOUT",
                    appContext.packageName,
                    "timeoutMs=$NAVIGATION_TIMEOUT_MS path=${safePath(current)} syntheticPageFinished=true externalNavigation=false",
                )
                webView.stopLoading()
                deliverPageFinished(webView, current, synthetic = true)
            }, NAVIGATION_TIMEOUT_MS)
        }

        private fun privateMethod(name: String): Method? = runCatching {
            activity.javaClass.getDeclaredMethod(name).apply { isAccessible = true }
        }.getOrElse {
            UnifiedDebugEventStore.record(
                "HARVEST_FAST_PATH_REFLECTION_UNAVAILABLE",
                appContext.packageName,
                "method=$name fallbackPreserved=true",
            )
            null
        }

        private fun invokePrivate(method: Method?, name: String): Boolean {
            if (method == null) return false
            return runCatching {
                method.invoke(activity)
                true
            }.getOrElse {
                UnifiedDebugEventStore.record(
                    "HARVEST_FAST_PATH_REFLECTION_FAILED",
                    appContext.packageName,
                    "method=$name fallbackPreserved=true error=${it.javaClass.simpleName}",
                )
                false
            }
        }
    }

    companion object {
        private const val NAVIGATION_TIMEOUT_MS = 12_000L
        private const val RIDES_URL = "https://www.blablacar.com.br/rides"
        private val SUPPRESS_EDIT_LINKS_JS = """
            (function() {
              const clean = (value) => (value || '').replace(/\s+/g, ' ').trim();
              Array.from(document.querySelectorAll('a[href]')).forEach((anchor) => {
                const rawHref = anchor.getAttribute('href') || '';
                let path = '';
                try { path = new URL(rawHref, location.href).pathname.replace(/\/+$/, ''); } catch (_) {}
                const text = clean(anchor.innerText || anchor.textContent);
                const editPath = /^\/rides\/offer\/edit(?:\/|$)/i.test(path);
                const editAction = /editar sua carona|lugares e op[cç][õo]es|op[cç][õo]es de passageiros/i.test(text);
                if (editPath || editAction) {
                  anchor.setAttribute('data-rotacerta-automatic-seat-lookup', 'disabled');
                  anchor.removeAttribute('href');
                }
              });
              return 'ok';
            })();
        """.trimIndent()

        private fun isBlaBlaUrl(value: String?): Boolean = BlaBlaCollectorUrlModule.isAllowed(value)

        private fun isTripDetailUrl(value: String?): Boolean {
            if (!isBlaBlaUrl(value)) return false
            val parsed = runCatching { Uri.parse(value) }.getOrNull() ?: return false
            val path = parsed.path.orEmpty().trimEnd('/')
            if (path.equals("/rides/offer/edit", ignoreCase = true) || path.startsWith("/rides/offer/edit/", ignoreCase = true)) return false
            if (path.contains("/passenger/", ignoreCase = true) || path.contains("/booking/", ignoreCase = true)) return false
            if (path.equals("/rides/offer", ignoreCase = true)) return true
            return Regex("/rides/offer/(?!edit(?:/|$)|passenger(?:/|$))[^/?#]+", RegexOption.IGNORE_CASE).containsMatchIn(path) ||
                Regex("/trip/[^/?#]+", RegexOption.IGNORE_CASE).containsMatchIn(path)
        }

        private fun sameNavigationUrl(left: String?, right: String?): Boolean =
            BlaBlaHarvestNavigationIdentity.same(left, right)

        private fun safePath(value: String?): String = runCatching {
            val parsed = Uri.parse(value)
            val id = parsed.getQueryParameter("id")?.takeIf(String::isNotBlank)
            if (id == null) {
                parsed.path.orEmpty().take(160)
            } else {
                "${parsed.path.orEmpty().take(100)}?id=${id.take(54)}"
            }
        }.getOrDefault("")
    }
}
