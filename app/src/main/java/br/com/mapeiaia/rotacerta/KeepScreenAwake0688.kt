package br.com.mapeiaia.rotacerta

import android.content.Context

object KeepScreenAwakeContract0688 {
    const val CONTRACT_MARKER = "KEEP_SCREEN_AWAKE_TOGGLE_0688"
    const val SHORTCUT_ID = "action_keep_screen_awake"

    fun displayLabel(enabled: Boolean): String = if (enabled) "Tela ON" else "Tela OFF"

    fun statusMessage(enabled: Boolean): String = if (enabled) {
        "Tela ON: bloqueio automático desativado."
    } else {
        "Tela OFF: bloqueio automático normal."
    }
}

class KeepScreenAwakePreferenceStore0688(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun toggle(): Boolean {
        val enabled = !isEnabled()
        setEnabled(enabled)
        return enabled
    }

    private companion object {
        const val PREFS_NAME = "rota_certa_keep_screen_awake_0688"
        const val KEY_ENABLED = "enabled"
    }
}
