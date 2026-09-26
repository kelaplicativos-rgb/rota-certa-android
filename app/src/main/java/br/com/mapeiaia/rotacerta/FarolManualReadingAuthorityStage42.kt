package br.com.mapeiaia.rotacerta

/**
 * Stage42: the user is the only ON/OFF authority for FAROL screen reading.
 * Selected ride apps remain legacy metadata/diagnostic input only; they never arm or disarm reading.
 */
object FarolManualReadingAuthorityStage42 {
    const val CONTRACT_MARKER = "FAROL_MANUAL_READING_AUTHORITY_STAGE42"
    const val USER_ONLY_MARKER = "USER_TOGGLE_ONLY_ARMS_READING_STAGE42"
    const val UNIVERSAL_VISUAL_MARKER = "ANY_VISIBLE_APP_WINDOW_POPUP_TWO_ADDRESS_STAGE42"
    const val NO_PRESENCE_GATE_MARKER = "NO_SELECTED_APP_PRESENCE_IN_CRITICAL_PATH_STAGE42"
    const val HOME_MODULE_MARKER = "HOME_READING_MODULE_AND_FLOATING_SHORTCUT_STAGE42"

    fun isEnabled(settings: AppSettings): Boolean = WorkModePolicy0162.isEnabled(settings)

    fun setEnabled(settings: AppSettings, enabled: Boolean): AppSettings =
        WorkModePolicy0162.setEnabled(settings, enabled)
}
