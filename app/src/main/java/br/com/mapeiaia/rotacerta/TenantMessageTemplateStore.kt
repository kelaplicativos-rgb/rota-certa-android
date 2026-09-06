package br.com.mapeiaia.rotacerta

import android.content.Context

/**
 * Tenant-safe facade for the editable Stage172 message templates. The provisional
 * local tenant intentionally keeps the legacy keys so upgrades retain the user's
 * existing phrases; additional tenants receive isolated keys.
 */
object TenantMessageTemplateStore {
    private const val PREFS = "rota_certa_message_templates_0172"
    private const val KEY_TRIP = "trip"
    private const val KEY_VALUE = "value"

    fun readTrip(context: Context): String = prefs(context)
        .getString(key(context, KEY_TRIP), MessageTemplateStore0172.DEFAULT_TRIP)
        ?.takeIf { it.isNotBlank() }
        ?: MessageTemplateStore0172.DEFAULT_TRIP

    fun readValue(context: Context): String = prefs(context)
        .getString(key(context, KEY_VALUE), MessageTemplateStore0172.DEFAULT_VALUE)
        ?.takeIf { it.isNotBlank() }
        ?: MessageTemplateStore0172.DEFAULT_VALUE

    fun saveTrip(context: Context, value: String) {
        prefs(context).edit()
            .putString(
                key(context, KEY_TRIP),
                value.trim().take(4_000).ifBlank { MessageTemplateStore0172.DEFAULT_TRIP },
            )
            .apply()
    }

    fun saveValue(context: Context, value: String) {
        prefs(context).edit()
            .putString(
                key(context, KEY_VALUE),
                value.trim().take(4_000).ifBlank { MessageTemplateStore0172.DEFAULT_VALUE },
            )
            .apply()
    }

    fun restoreDefaults(context: Context) {
        prefs(context).edit()
            .putString(key(context, KEY_TRIP), MessageTemplateStore0172.DEFAULT_TRIP)
            .putString(key(context, KEY_VALUE), MessageTemplateStore0172.DEFAULT_VALUE)
            .apply()
    }

    private fun key(context: Context, base: String): String =
        RotaCertaTenantRegistry(context.applicationContext).activeScope().key(base)

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
