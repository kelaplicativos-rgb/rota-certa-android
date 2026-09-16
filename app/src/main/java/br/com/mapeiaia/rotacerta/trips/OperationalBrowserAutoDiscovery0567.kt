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
 * 0.1.567 — automatically asks the existing canonical collector to discover newly
 * published BlaBlaCar trips whenever the trips workspace becomes foreground again.
 *
 * This does not create a second source of truth and does not scrape from the UI layer.
 * It only schedules the already-authoritative collector/reconcile pipeline. When that
 * pipeline materializes a new external trip it emits BookingRealtimeEvents0356, and the
 * existing TripsActivity observer reloads TripStore so "Todas as viagens" updates without
 * a manual refresh button.
 *
 * The trigger is deliberately lifecycle-based instead of tight polling: opening the
 * trips workspace or returning from any BlaBlaCar/app/browser publication flow requests
 * discovery immediately, while the configured periodic sync remains the fallback for
 * changes made elsewhere while Rota Certa stays continuously foregrounded.
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

            val connectedProfiles = BlaBlaDynamicAccountRegistry(app)
                .list()
                .count { account -> !account.profileUuid.isNullOrBlank() }
            val now = System.currentTimeMillis()
            val prefs = app.getSharedPreferences(PREFS_NAME, 0)
            val lastRequestedAt = prefs.getLong(KEY_LAST_REQUESTED_AT, 0L)

            if (!operationalAutoDiscoveryShouldRequest0567(
                    connectedProfileCount = connectedProfiles,
                    nowMillis = now,
                    lastRequestedAtMillis = lastRequestedAt,
                )
            ) {
                return
            }

            prefs.edit().putLong(KEY_LAST_REQUESTED_AT, now).apply()
            AgendaBackgroundSync0392.enqueueImmediate(
                app,
                "admin_update_now:operational_browser_auto_discovery_0567",
            )
            UnifiedDebugEventStore.recordAlways(
                "OPERATIONAL_BROWSER_AUTO_DISCOVERY_REQUESTED_0567",
                app.packageName,
                "trigger=TRIPS_ACTIVITY_RESUMED connectedProfiles=$connectedProfiles " +
                    "collectorReconcile=true manualRefreshRequired=false piiLogged=false",
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
        private const val PREFS_NAME = "operational_browser_auto_discovery_0567"
        private const val KEY_LAST_REQUESTED_AT = "last_requested_at"
    }
}

internal const val OPERATIONAL_AUTO_DISCOVERY_MIN_INTERVAL_MS_0567 = 5_000L

internal fun operationalAutoDiscoveryShouldRequest0567(
    connectedProfileCount: Int,
    nowMillis: Long,
    lastRequestedAtMillis: Long,
    minIntervalMillis: Long = OPERATIONAL_AUTO_DISCOVERY_MIN_INTERVAL_MS_0567,
): Boolean {
    if (connectedProfileCount <= 0) return false
    if (lastRequestedAtMillis <= 0L) return true
    if (nowMillis < lastRequestedAtMillis) return true
    return nowMillis - lastRequestedAtMillis >= minIntervalMillis.coerceAtLeast(0L)
}
