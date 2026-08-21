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
            "timeoutMs=$NAVIGATION_TIMEOUT_MS directSource=true syntheticPageFinished=true",
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
                    "currentPath=${safePath(current)} stalePath=${safePath(url)}",
                )
                return
            }
            generation++
            expectedUrl = null
            handler.removeCallbacksAndMessages(null)
            delegate.onPageFinished(view, url)
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
                delegate.onPageFinished(webView, current)
            }, NAVIGATION_TIMEOUT_MS)
        }
    }

    companion object {
        private const val NAVIGATION_TIMEOUT_MS = 12_000L
        private const val RIDES_URL = "https://www.blablacar.com.br/rides"

        private fun isBlaBlaUrl(value: String?): Boolean =
            value?.startsWith("https://www.blablacar.com.br/", ignoreCase = true) == true

        private fun sameNavigationUrl(left: String?, right: String?): Boolean {
            if (left.isNullOrBlank() || right.isNullOrBlank()) return false
            val a = runCatching { Uri.parse(left) }.getOrNull()
            val b = runCatching { Uri.parse(right) }.getOrNull()
            return a?.scheme.equals(b?.scheme, ignoreCase = true) &&
                a?.host.equals(b?.host, ignoreCase = true) &&
                a?.path.orEmpty().trimEnd('/') == b?.path.orEmpty().trimEnd('/')
        }

        private fun safePath(value: String?): String = runCatching {
            Uri.parse(value).path.orEmpty().take(160)
        }.getOrDefault("")
    }
}
