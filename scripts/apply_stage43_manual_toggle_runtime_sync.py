#!/usr/bin/env python3
from pathlib import Path
import re
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

# The inherited collector only copied settings into currentSettings. That allowed Compose/DataStore
# to show OFF while the already-running AccessibilityService stayed ON. Every emission must now
# execute the runtime transition.
collector_exact = '        scope.launch { repository.settings.collect { currentSettings = it } }\n'
collector_new = '''        scope.launch {
            repository.settings.collect { updatedStage43 ->
                applyPersistedManualReadingStage43(updatedStage43, "settings_flow")
            }
        }
'''
if collector_exact in service:
    service = once(service, collector_exact, collector_new, 'settings collector runtime sync')
else:
    pattern = re.compile(
        r'        scope\.launch\s*\{\s*repository\.settings\.collect\s*\{\s*currentSettings\s*=\s*it\s*\}\s*\}\s*\n',
        re.MULTILINE,
    )
    service, n = pattern.subn(collector_new, service, count=1)
    if n != 1:
        raise SystemExit(f'settings collector runtime sync: expected 1 collector, got {n}')

# Bootstrap must use the exact same path and force the actual runtime state even if the collector
# emitted the same value milliseconds earlier.
bootstrap_old = '            currentSettings = repository.settings.first()\n'
bootstrap_new = '            applyPersistedManualReadingStage43(repository.settings.first(), "service_bootstrap", forceStage43 = true)\n'
service = once(service, bootstrap_old, bootstrap_new, 'service bootstrap runtime sync')

# One central service transition. currentSettings is assigned BEFORE runtime side effects so every
# subsequent event/tap sees the new authority. applyWorkModeRuntime0162 already owns cancellation,
# freshness invalidation and Stage42 gray/yellow behavior; force makes the user command immediate.
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
        // Apply synchronously before the persistence coroutine: one tap must change the live Farol now.
        applyPersistedManualReadingStage43(updatedStage43, sourceStage43, forceStage43 = true)
        scope.launch { repository.saveSettings(updatedStage43) }
    }

'''
service = once(service, work_anchor, helper_block + work_anchor, 'insert Stage43 central runtime transition')

# The Stage42 grid handler already computes enabled0162 and owns the user-facing toast. Make that
# tap synchronously execute the same runtime transition; persistence happens in the shared command.
grid_toast = '            if (enabled0162) "Leitura do Farol ATIVADA" else "Leitura do Farol DESLIGADA",\n'
grid_with_command = '            applyManualReadingCommandStage43(enabled0162, "grid_shortcut")\n' + grid_toast
service = once(service, grid_toast, grid_with_command, 'grid shortcut immediate runtime command')

SERVICE.write_text(service, encoding='utf-8')
print('stage43_manual_toggle_runtime_sync=PASS')
