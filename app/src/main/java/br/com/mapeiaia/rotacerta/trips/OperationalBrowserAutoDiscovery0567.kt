package br.com.mapeiaia.rotacerta.trips

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 0.1.616 — lifecycle entry into "Todas as viagens" is read-only.
 *
 * Opening or resuming TripsActivity must never start a BlaBlaCar/canonical synchronization.
 * Fresh acquisition remains explicit through the dedicated HTML capture/manual refresh paths
 * and through configured periodic work. This provider is kept only as a compatibility shell
 * for already-installed manifests and records that the former lifecycle trigger is disabled.
 */
class OperationalBrowserAutoDiscoveryProvider0567 : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        if (registered.compareAndSet(false, true)) {
            app.registerActivityLifecycleCallbacks(AutoDiscoveryCallbacks0567(app))
        }
        return true
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
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private class AutoDiscoveryCallbacks0567(
        private val app: Application,
    ) : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (activity !is TripsActivity) return
            UnifiedDebugEventStore.recordAlways(
                "OPERATIONAL_BROWSER_AUTO_DISCOVERY_DISABLED_0616",
                app.packageName,
                "trigger=TRIPS_ACTIVITY_RESUMED syncRequested=false " +
                    "policy=READ_ONLY_ON_ENTRY explicitHtmlRefreshOnly=true piiLogged=false",
            )
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    companion object {
        private val registered = AtomicBoolean(false)
    }
}

internal const val OPERATIONAL_AUTO_DISCOVERY_MIN_INTERVAL_MS_0567 = 5_000L

@Suppress("UNUSED_PARAMETER")
internal fun operationalAutoDiscoveryShouldRequest0567(
    connectedProfileCount: Int,
    nowMillis: Long,
    lastRequestedAtMillis: Long,
    minIntervalMillis: Long = OPERATIONAL_AUTO_DISCOVERY_MIN_INTERVAL_MS_0567,
): Boolean = false
