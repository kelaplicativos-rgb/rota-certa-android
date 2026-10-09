package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import androidx.work.WorkManager
import br.com.mapeiaia.rotacerta.RotaCertaTenantRegistry
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore

/**
 * Explicit device-local, tenant-scoped opt-in for automatic READ-ONLY remote collection.
 * Disabled on all fresh installations and when switching to another tenant.
 * This is not an Android screen capture permission and never bypasses OS prompts.
 */
internal object RemoteSupportAutoAccess0763 {
    const val WORK_TAG = "rota-certa-remote-consult-0764"
    private const val PREFS = "rota_certa_remote_support_auto_access_0763"
    private const val KEY_ENABLED = "read_only_auto_access_enabled"

    private val allowedEvents = setOf(
        "standalone_covers_collect",
        "blablacar_trip_query_collect",
        "remote_health_collect",
    )

    fun enabled(context: Context, event: String): Boolean {
        if (event !in allowedEvents) return false
        val app = context.applicationContext
        val tenantScope = RotaCertaTenantRegistry(app).activeScope()
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(tenantScope.key(KEY_ENABLED), false)
    }

    fun setEnabled(context: Context, value: Boolean) {
        val app = context.applicationContext
        val tenantScope = RotaCertaTenantRegistry(app).activeScope()
        check(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(tenantScope.key(KEY_ENABLED), value).commit()) {
            "Nao foi possivel salvar a autorizacao remota."
        }
        if (!value) {
            // Cancel already queued or active remote reads when OFF is selected.
            WorkManager.getInstance(app).cancelAllWorkByTag(WORK_TAG)
        }
        UnifiedDebugEventStore.recordAlways(
            if (value) "REMOTE_AUTO_ACCESS_ENABLED_0763" else "REMOTE_AUTO_ACCESS_REVOKED_0763",
            app.packageName,
            "tenantScoped=true readOnly=true deviceAuthorized=$value",
        )
    }
}
