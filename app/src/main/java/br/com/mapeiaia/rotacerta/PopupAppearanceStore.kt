package br.com.mapeiaia.rotacerta

import android.content.Context

/** Preferência leve e síncrona usada somente ao abrir janelas da bolinha. */
class PopupAppearanceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun scale(): Double = prefs.getFloat(KEY_SCALE, DEFAULT_SCALE.toFloat())
        .toDouble()
        .coerceIn(MIN_SCALE, MAX_SCALE)

    fun setScale(value: Double) {
        prefs.edit().putFloat(KEY_SCALE, value.coerceIn(MIN_SCALE, MAX_SCALE).toFloat()).apply()
    }

    companion object {
        const val MIN_SCALE = 0.90
        const val MAX_SCALE = 1.60
        const val DEFAULT_SCALE = 1.00

        private const val PREFS_NAME = "popup_appearance"
        private const val KEY_SCALE = "popup_scale"
    }
}
