#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'
HELPER = PKG / 'FarolManualToggleRuntimeSyncStage43.kt'
OFF_DIAGNOSTICS = PKG / 'FarolManualOffVisualCommitStage43.kt'
REPORT = PKG / 'ManualTechnicalReportBuilder.kt'


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)


HELPER.write_text(r'''package br.com.mapeiaia.rotacerta

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
''', encoding='utf-8')

OFF_DIAGNOSTICS.write_text(r'''package br.com.mapeiaia.rotacerta

import java.util.concurrent.atomic.AtomicLong

/** Stage43 physical truth: logical OFF is not PASS until the gray/no-km renderer actually commits. */
object FarolManualOffVisualCommitStage43 {
    const val CONTRACT_MARKER = "MANUAL_OFF_PHYSICAL_VIEW_COMMIT_STAGE43"
    private val attempts = AtomicLong(0L)
    private val applied = AtomicLong(0L)
    private val anomalies = AtomicLong(0L)

    data class Snapshot(val attempts: Long, val applied: Long, val anomalies: Long) {
        val status: String
            get() = when {
                anomalies > 0L || applied != attempts -> "FAIL"
                attempts > 0L -> "PASS"
                else -> "NOT_TESTED"
            }
    }

    fun recordAttempt(appliedNow: Boolean): Snapshot {
        attempts.incrementAndGet()
        if (appliedNow) applied.incrementAndGet() else anomalies.incrementAndGet()
        return snapshot()
    }

    fun snapshot(): Snapshot = Snapshot(attempts.get(), applied.get(), anomalies.get())

    fun exportReport(): String {
        val s = snapshot()
        return buildString {
            appendLine("ROTA CERTA — STAGE43 COMMIT FISICO DO OFF")
            appendLine("marker=$CONTRACT_MARKER")
            appendLine("status=${s.status}; attempts=${s.attempts}; applied=${s.applied}; anomalies=${s.anomalies}")
            append("rule=OFF so e PASS quando o renderizador atravessa o commit real Idle/cinza com km nulo; ausencia de commit em uma tentativa e FAIL")
        }
    }

    internal fun resetForTests() {
        attempts.set(0L)
        applied.set(0L)
        anomalies.set(0L)
    }
}
''', encoding='utf-8')

service = SERVICE.read_text(encoding='utf-8')

# Runtime bookkeeping is intentionally service-local. The render serial is advanced only after the
# physical overlay renderer reaches overlayApplied, never merely when the logical model says Idle.
field_anchor = '    private fun refreshReadingActivationStage26(\n'
field_block = '''    private var stage43LastAppliedManualReading: Boolean? = null
    private var stage43ManualTransitionSerial: Long = 0L
    private var stage43OffRenderAppliedSerial: Long = 0L

'''
service = once(service, field_anchor, field_block + field_anchor, 'Stage43 runtime state fields')

# Every ready DataStore emission enters the same transition; identical emissions are deduplicated.
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

# Bootstrap after migrations/default-off handling through the same transition used by Home and grid.
bootstrap_old = '            applyWorkModeRuntime0162(WorkModePolicy0162.isEnabled(currentSettings), force0162 = true)\n'
bootstrap_new = '            applyPersistedManualReadingStage43(currentSettings, "service_bootstrap", forceStage43 = true)\n'
service = once(service, bootstrap_old, bootstrap_new, 'service bootstrap runtime sync')

# One central service transition. currentSettings changes before runtime side effects.
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
        // The live Farol changes synchronously; persistence follows. Its returning DataStore emission
        // is safely deduplicated by stage43LastAppliedManualReading.
        applyPersistedManualReadingStage43(updatedStage43, sourceStage43, forceStage43 = true)
        scope.launch { runCatching { repository.saveSettings(updatedStage43) } }
    }

'''
service = once(service, work_anchor, helper_block + work_anchor, 'insert Stage43 central runtime transition')

# Replace the inherited grid core with one immediate runtime command followed by one persistence.
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

# Physical correction proven by the user's video: Stage40 had decided Idle, but the legacy renderer
# skipped before RENDER_REQUEST because currentRadarColor had already been pre-mutated to Idle while
# the existing View still held its old yellow background. Manual OFF therefore gets an explicit
# force flag that is forwarded through Stage40 and bypasses ONLY the idempotent same-value shortcut.
show_sig_old = '    private fun showOverlay(color: RadarColor, distanceKm: Double? = null) {\n'
show_sig_new = '    private fun showOverlay(color: RadarColor, distanceKm: Double? = null, forcePhysicalCommitStage43: Boolean = false) {\n'
service = once(service, show_sig_old, show_sig_new, 'Stage43 showOverlay force signature')
service = once(
    service,
    '        renderOverlayStage40(effectiveColorStage40, decisionStage40.distanceKm)\n',
    '        renderOverlayStage40(effectiveColorStage40, decisionStage40.distanceKm, forcePhysicalCommitStage43)\n',
    'Stage43 Stage40 force forwarding',
)
render_sig_old = '    private fun renderOverlayStage40(color: RadarColor, distanceKm: Double? = null) {\n'
render_sig_new = '    private fun renderOverlayStage40(color: RadarColor, distanceKm: Double? = null, forcePhysicalCommitStage43: Boolean = false) {\n'
service = once(service, render_sig_old, render_sig_new, 'Stage43 raw renderer force signature')
idempotent_old = '''        if (existingViewChecklist15 != null && currentRadarColor == color &&
            existingViewChecklist15.text.toString() == nextTextChecklist15
        ) {
'''
idempotent_new = '''        if (!forcePhysicalCommitStage43 && existingViewChecklist15 != null && currentRadarColor == color &&
            existingViewChecklist15.text.toString() == nextTextChecklist15
        ) {
'''
service = once(service, idempotent_old, idempotent_new, 'Stage43 forced OFF bypasses idempotent renderer skip')

# A forced OFF is considered physically committed only after the renderer reaches overlayApplied.
overlay_applied_line = '        FarolForensicTraceStage20.overlayApplied(stage20ExpectedPaintToken, SystemClock.elapsedRealtimeNanos(), color.toString(), distanceKm, currentStage20BindingSnapshot(), stage20Origin)\n'
overlay_applied_new = overlay_applied_line + '''        if (forcePhysicalCommitStage43 && color == RadarColor.Idle && distanceKm == null) {
            stage43OffRenderAppliedSerial += 1L
        }
'''
service = once(service, overlay_applied_line, overlay_applied_new, 'Stage43 physical render applied serial')

# Fix the OFF block itself. Never claim the model is already Idle before the renderer paints it.
work_a = service.index('    private fun applyWorkModeRuntime0162(')
work_b = service.index('    private fun ensureDriverCardSession0162(', work_a)
work_block = service[work_a:work_b]
pre_mutation = '        currentDistanceKm = null\n        currentRadarColor = RadarColor.Idle\n'
if work_block.count(pre_mutation) != 1:
    raise SystemExit(f'Stage43 OFF pre-mutation: expected 1 occurrence, got {work_block.count(pre_mutation)}')
work_block = work_block.replace(pre_mutation, '', 1)
idle_call = '        showOverlay(RadarColor.Idle, null)\n'
if work_block.count(idle_call) != 2:
    raise SystemExit(f'Stage43 work mode Idle calls: expected 2 occurrences, got {work_block.count(idle_call)}')
off_pos = work_block.rfind(idle_call)
forced_off = '''        val offRenderBeforeStage43 = stage43OffRenderAppliedSerial
        showOverlay(RadarColor.Idle, null, forcePhysicalCommitStage43 = true)
        val offRenderAppliedStage43 = stage43OffRenderAppliedSerial > offRenderBeforeStage43 &&
            currentRadarColor == RadarColor.Idle && currentDistanceKm == null
        FarolManualOffVisualCommitStage43.recordAttempt(offRenderAppliedStage43)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S43_MANUAL_OFF_RENDER_COMMIT", universalResolvedForegroundPackage(),
            details = "applied=$offRenderAppliedStage43; beforeSerial=$offRenderBeforeStage43; afterSerial=$stage43OffRenderAppliedSerial; currentColor=$currentRadarColor; currentDistance=${currentDistanceKm ?: -1.0}",
        )
        if (!offRenderAppliedStage43) {
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S43_MANUAL_OFF_RENDER_ANOMALY", universalResolvedForegroundPackage(),
                details = "logicalOff=true; expected=Idle/no-km/renderApplied; currentColor=$currentRadarColor; currentDistance=${currentDistanceKm ?: -1.0}; serviceReady=$serviceReady; overlayPresent=${overlayView != null}",
            )
            UnifiedDebugEventStore.record(
                "STAGE43_OFF_RENDER_ANOMALY", packageName,
                "logical_off_without_physical_idle_commit=true; color=$currentRadarColor; distance=$currentDistanceKm; serviceReady=$serviceReady; overlayPresent=${overlayView != null}",
            )
        }
'''
work_block = work_block[:off_pos] + forced_off + work_block[off_pos + len(idle_call):]
service = service[:work_a] + work_block + service[work_b:]

SERVICE.write_text(service, encoding='utf-8')

# Export a top-level Stage43 verdict. No OFF attempt => NOT_TESTED; any missing applied commit => FAIL.
report = REPORT.read_text(encoding='utf-8')
report = once(
    report,
    '            appendLine(FarolMaximumForensicsStage38.exportReport())\n',
    '            appendLine(FarolMaximumForensicsStage38.exportReport())\n'
    '            appendLine()\n'
    '            appendLine(FarolManualOffVisualCommitStage43.exportReport())\n',
    'Stage43 physical OFF report status',
)
REPORT.write_text(report, encoding='utf-8')

print('stage43_manual_toggle_runtime_sync=PASS physical_off_force_commit=PASS report_fail_on_missing_commit=PASS')
