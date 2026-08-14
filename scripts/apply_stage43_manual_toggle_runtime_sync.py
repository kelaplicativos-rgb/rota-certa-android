#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'
HELPER = PKG / 'FarolManualToggleRuntimeSyncStage43.kt'


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)


HELPER.write_text(r'''package br.com.mapeiaia.rotacerta

/**
 * Stage43: one persisted/manual state must drive the live AccessibilityService runtime.
 *
 * Stage42 established manual universal authority. Stage43 closes the synchronization gap:
 * Home -> DataStore -> live service and floating shortcut -> live service -> DataStore use the
 * same boolean contract. There is no second visual-only toggle state.
 */
object FarolManualToggleRuntimeSyncStage43 {
    const val CONTRACT_MARKER = "FAROL_MANUAL_TOGGLE_RUNTIME_SYNC_STAGE43"
    const val FLOW_MARKER = "DATASTORE_EMISSION_APPLIES_LIVE_RUNTIME_STAGE43"
    const val GRID_MARKER = "GRID_TAP_APPLIES_RUNTIME_BEFORE_PERSIST_STAGE43"
    const val OFF_MARKER = "MANUAL_OFF_IMMEDIATELY_INVALIDATES_AND_PAINTS_GRAY_STAGE43"
    const val SINGLE_STATE_MARKER = "HOME_GRID_SERVICE_ONE_MANUAL_READING_STATE_STAGE43"

    fun enabled(settings: AppSettings): Boolean = FarolManualReadingAuthorityStage42.isEnabled(settings)

    fun withEnabled(settings: AppSettings, enabled: Boolean): AppSettings =
        FarolManualReadingAuthorityStage42.setEnabled(settings, enabled)
}
''', encoding='utf-8')

service = SERVICE.read_text(encoding='utf-8')

# Runtime bookkeeping is intentionally service-local. Stage42 guarantees the refresh function,
# so anchor fields there rather than relying on an old declaration layout that prior stages can move.
field_anchor = '    private fun refreshReadingActivationStage26(\n'
field_block = '''    private var stage43LastAppliedManualReading: Boolean? = null
    private var stage43ManualTransitionSerial: Long = 0L

'''
service = once(service, field_anchor, field_block + field_anchor, 'Stage43 runtime state fields')

# Stage32+ already had a settings collector, but it kept a second runtime state and only called the
# inherited transition on a boolean delta. Stage43 makes every ready DataStore emission enter the
# single Stage43 transition; identical emissions are deduplicated inside that transition.
collector_old = '''        scope.launch {
            repository.settings.collect { updated0162 ->
                if (!workModeSettingsReady0162) return@collect
                val previousEnabled0162 = WorkModePolicy0162.isEnabled(currentSettings)
                currentSettings = updated0162
                val currentEnabled0162 = WorkModePolicy0162.isEnabled(updated0162)
                if (previousEnabled0162 != currentEnabled0162) {
                    applyWorkModeRuntime0162(currentEnabled0162)
                }
            }
        }
'''
collector_new = '''        scope.launch {
            repository.settings.collect { updatedStage43 ->
                if (!workModeSettingsReady0162) return@collect
                applyPersistedManualReadingStage43(updatedStage43, "settings_flow")
            }
        }
'''
service = once(service, collector_old, collector_new, 'settings collector runtime sync')

# Bootstrap remains after migrations/default-off handling. Do not arm the live service from the raw
# first() value before those migrations finish; once workModeSettingsReady0162 becomes true, apply
# the exact same Stage43 transition used by Home and grid.
bootstrap_old = '            applyWorkModeRuntime0162(WorkModePolicy0162.isEnabled(currentSettings), force0162 = true)\n'
bootstrap_new = '            applyPersistedManualReadingStage43(currentSettings, "service_bootstrap", forceStage43 = true)\n'
service = once(service, bootstrap_old, bootstrap_new, 'service bootstrap runtime sync')

# One central service transition. currentSettings is assigned BEFORE runtime side effects so every
# subsequent event/tap sees the new authority. applyWorkModeRuntime0162 already owns cancellation,
# freshness invalidation and Stage42 gray/yellow behavior; force makes a user command immediate.
work_anchor = '    private fun applyWorkModeRuntime0162(enabled0162: Boolean, force0162: Boolean = false) {\n'
helper_block = r'''    private fun applyPersistedManualReadingStage43(
        updatedStage43: AppSettings,
        sourceStage43: String,
        forceStage43: Boolean = false,
    ) {
        currentSettings = updatedStage43
        val enabledStage43 = FarolManualToggleRuntimeSyncStage43.enabled(updatedStage43)
        if (!forceStage43 && stage43LastAppliedManualReading == enabledStage43) return
        stage43LastAppliedManualReading = enabledStage43
        stage43ManualTransitionSerial += 1L
        traceEvent(
            "stage43.manual_runtime source=$sourceStage43 enabled=$enabledStage43 serial=$stage43ManualTransitionSerial",
        )
        applyWorkModeRuntime0162(enabledStage43, force0162 = true)
    }

    private fun applyManualReadingCommandStage43(enabledStage43: Boolean, sourceStage43: String) {
        val updatedStage43 = FarolManualToggleRuntimeSyncStage43.withEnabled(currentSettings, enabledStage43)
        // The live Farol changes synchronously; DataStore persistence follows. Its returning emission
        // is safely deduplicated by stage43LastAppliedManualReading.
        applyPersistedManualReadingStage43(updatedStage43, sourceStage43, forceStage43 = true)
        scope.launch { runCatching { repository.saveSettings(updatedStage43) } }
    }

'''
service = once(service, work_anchor, helper_block + work_anchor, 'insert Stage43 central runtime transition')

# Replace the inherited grid core rather than adding a second command after it. This guarantees one
# runtime transition and one persistence request per tap, while currentSettings is changed before a
# possible second tap can calculate the next state.
grid_old = '''        val enabled0162 = !WorkModePolicy0162.isEnabled(currentSettings)
        val updated0162 = WorkModePolicy0162.setEnabled(currentSettings, enabled0162)
        currentSettings = updated0162
        applyWorkModeRuntime0162(enabled0162)
        scope.launch { runCatching { repository.saveSettings(updated0162) } }
'''
grid_new = '''        val enabled0162 = !FarolManualToggleRuntimeSyncStage43.enabled(currentSettings)
        applyManualReadingCommandStage43(enabled0162, "grid_shortcut")
'''
service = once(service, grid_old, grid_new, 'grid shortcut single immediate command')

SERVICE.write_text(service, encoding='utf-8')
print('stage43_manual_toggle_runtime_sync=PASS')
