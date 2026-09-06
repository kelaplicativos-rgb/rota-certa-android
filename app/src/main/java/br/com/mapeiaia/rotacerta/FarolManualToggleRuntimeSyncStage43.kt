package br.com.mapeiaia.rotacerta

/**
 * Stage43: one persisted/manual state must drive the live AccessibilityService runtime.
 *
 * Stage42 established manual universal authority. Stage43 closes both synchronization gaps:
 * Home -> DataStore -> live service and floating shortcut -> live service -> DataStore use the
 * same boolean contract; manual OFF additionally requires a real forced gray/no-km View commit.
 */
object FarolManualToggleRuntimeSyncStage43 {
    const val CONTRACT_MARKER = "FAROL_MANUAL_TOGGLE_RUNTIME_SYNC_STAGE43"
    const val FLOW_MARKER = "DATASTORE_EMISSION_APPLIES_LIVE_RUNTIME_STAGE43"
    const val GRID_MARKER = "GRID_TAP_APPLIES_RUNTIME_BEFORE_PERSIST_STAGE43"
    const val OFF_MARKER = "MANUAL_OFF_IMMEDIATELY_INVALIDATES_AND_PAINTS_GRAY_STAGE43"
    const val SINGLE_STATE_MARKER = "HOME_GRID_SERVICE_ONE_MANUAL_READING_STATE_STAGE43"
    const val FORCE_RENDER_MARKER = "FORCED_OFF_BYPASSES_IDEMPOTENT_RENDER_SKIP_STAGE43"
    const val APPLIED_REQUIRED_MARKER = "OFF_RENDER_APPLIED_REQUIRED_STAGE43"
    const val REPORT_FAIL_MARKER = "OFF_RENDER_MISMATCH_IS_REPORT_FAIL_STAGE43"

    fun enabled(settings: AppSettings): Boolean = FarolManualReadingAuthorityStage42.isEnabled(settings)

    fun withEnabled(settings: AppSettings, enabled: Boolean): AppSettings =
        FarolManualReadingAuthorityStage42.setEnabled(settings, enabled)
}
