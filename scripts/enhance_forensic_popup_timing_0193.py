#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
overlay = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt'
test = root / 'app/src/test/java/br/com/mapeiaia/rotacerta/AlertPopupTimingTelemetry0193ContractTest.kt'

if not overlay.is_file():
    raise SystemExit(f'Arquivo obrigatório ausente: {overlay.relative_to(root)}')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: esperado exatamente 1 trecho, encontrado {count}')
    return text.replace(old, new, 1)


text = overlay.read_text(encoding='utf-8')

text = replace_once(
    text,
    '    private var pendingClose: Runnable? = null\n',
    '    private var pendingClose: Runnable? = null\n    private var pendingCloseScheduledAtNanos0193: Long? = null\n',
    'estado monotônico do fechamento pós-passagem',
)

old_close = '''            val closeTarget = visual.targetId
            val close = Runnable {
                if (activeTargetId == closeTarget) hide()
            }
            pendingClose = close
            FarolFlightRecorder0163.record(
                stage = "ALERT_OVERLAY_POST_PASS_SCHEDULED_0193",
                packageName = null,
                details = "delay_ms=$PASSED_CLOSE_DELAY_MILLIS; target_hash=${visual.targetId.hashCode()}",
            )
            handler.postDelayed(close, PASSED_CLOSE_DELAY_MILLIS)'''

new_close = '''            val closeTarget = visual.targetId
            val scheduledAtNanos0193 = android.os.SystemClock.elapsedRealtimeNanos()
            val close = Runnable {
                val elapsedMs0193 = (android.os.SystemClock.elapsedRealtimeNanos() - scheduledAtNanos0193) / 1_000_000L
                if (activeTargetId == closeTarget) {
                    pendingClose = null
                    pendingCloseScheduledAtNanos0193 = null
                    FarolFlightRecorder0163.record(
                        stage = "ALERT_OVERLAY_POST_PASS_TIMEOUT_FIRED_0193",
                        packageName = null,
                        details = "elapsed_ms=$elapsedMs0193; expected_ms=$PASSED_CLOSE_DELAY_MILLIS; target_hash=${closeTarget.hashCode()}",
                    )
                    if (elapsedMs0193 < PASSED_CLOSE_DELAY_MILLIS - EARLY_TIMEOUT_TOLERANCE_MILLIS_0193) {
                        FarolFlightRecorder0163.record(
                            stage = "FORENSIC_ALERT_POPUP_EARLY_TIMEOUT_0193",
                            packageName = null,
                            details = "elapsed_ms=$elapsedMs0193; expected_ms=$PASSED_CLOSE_DELAY_MILLIS; target_hash=${closeTarget.hashCode()}",
                        )
                    }
                    hide()
                } else {
                    FarolFlightRecorder0163.record(
                        stage = "ALERT_OVERLAY_POST_PASS_STALE_CALLBACK_0193",
                        packageName = null,
                        details = "elapsed_ms=$elapsedMs0193; target_hash=${closeTarget.hashCode()}; active_hash=${activeTargetId?.hashCode()}",
                    )
                }
            }
            pendingClose = close
            pendingCloseScheduledAtNanos0193 = scheduledAtNanos0193
            FarolFlightRecorder0163.record(
                stage = "ALERT_OVERLAY_POST_PASS_SCHEDULED_0193",
                packageName = null,
                details = "delay_ms=$PASSED_CLOSE_DELAY_MILLIS; target_hash=${visual.targetId.hashCode()}",
            )
            handler.postDelayed(close, PASSED_CLOSE_DELAY_MILLIS)'''

text = replace_once(text, old_close, new_close, 'medição monotônica do callback de 3 segundos')

old_cancel = '''    private fun cancelPendingClose() {
        pendingClose?.let(handler::removeCallbacks)
        pendingClose = null
    }'''
new_cancel = '''    private fun cancelPendingClose() {
        val scheduledAtNanos0193 = pendingCloseScheduledAtNanos0193
        if (pendingClose != null && scheduledAtNanos0193 != null) {
            val elapsedMs0193 = (android.os.SystemClock.elapsedRealtimeNanos() - scheduledAtNanos0193) / 1_000_000L
            FarolFlightRecorder0163.record(
                stage = "ALERT_OVERLAY_PENDING_CLOSE_CANCELLED_0193",
                packageName = null,
                details = "elapsed_ms=$elapsedMs0193; expected_ms=$PASSED_CLOSE_DELAY_MILLIS; active_hash=${activeTargetId?.hashCode()}",
            )
        }
        pendingClose?.let(handler::removeCallbacks)
        pendingClose = null
        pendingCloseScheduledAtNanos0193 = null
    }'''
text = replace_once(text, old_cancel, new_cancel, 'telemetria de cancelamento antecipado')

text = replace_once(
    text,
    '        const val PASSED_CLOSE_DELAY_MILLIS = 3_000L\n',
    '        const val PASSED_CLOSE_DELAY_MILLIS = 3_000L\n        const val EARLY_TIMEOUT_TOLERANCE_MILLIS_0193 = 150L\n',
    'tolerância de medição do callback',
)

overlay.write_text(text, encoding='utf-8')

test.write_text(r'''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPopupTimingTelemetry0193ContractTest {
    @Test
    fun `popup mede tempo real agenda callback e cancelamento com relogio monotonico`() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt").readText()
        assertTrue(source.contains("android.os.SystemClock.elapsedRealtimeNanos()"))
        assertTrue(source.contains("ALERT_OVERLAY_POST_PASS_SCHEDULED_0193"))
        assertTrue(source.contains("ALERT_OVERLAY_POST_PASS_TIMEOUT_FIRED_0193"))
        assertTrue(source.contains("ALERT_OVERLAY_PENDING_CLOSE_CANCELLED_0193"))
        assertTrue(source.contains("FORENSIC_ALERT_POPUP_EARLY_TIMEOUT_0193"))
        assertTrue(source.contains("expected_ms=$PASSED_CLOSE_DELAY_MILLIS"))
        assertTrue(source.contains("EARLY_TIMEOUT_TOLERANCE_MILLIS_0193 = 150L"))
    }
}
''', encoding='utf-8')

final = overlay.read_text(encoding='utf-8')
for marker in (
    'ALERT_OVERLAY_POST_PASS_TIMEOUT_FIRED_0193',
    'ALERT_OVERLAY_PENDING_CLOSE_CANCELLED_0193',
    'FORENSIC_ALERT_POPUP_EARLY_TIMEOUT_0193',
):
    if final.count(marker) != 1:
        raise SystemExit(f'Overlay: marcador {marker} deve existir exatamente uma vez')
if final.count('const val PASSED_CLOSE_DELAY_MILLIS = 3_000L') != 1:
    raise SystemExit('Overlay: contrato de 3.000 ms deixou de ser único')

print('forensic_popup_timing_0193=applied')
print('monotonic_real_hold_measurement=true')
print('early_cancel_trace=true')
print('early_timeout_anomaly=true')
