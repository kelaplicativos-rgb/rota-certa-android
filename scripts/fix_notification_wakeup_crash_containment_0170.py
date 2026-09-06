#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
APP = ROOT / 'app'
MAIN = APP / 'src/main/java/br/com/mapeiaia/rotacerta'
TEST = APP / 'src/test/java/br/com/mapeiaia/rotacerta'
SERVICE = MAIN / 'LiveRideAccessibilityService.kt'
GRADLE = APP / 'build.gradle.kts'
GUARD = MAIN / 'FarolNotificationFailureCircuit0170.kt'
GUARD_TEST = TEST / 'FarolNotificationFailureCircuit0170Test.kt'
CONTRACT_TEST = TEST / 'FarolNotificationCrashContainmentContract0170Test.kt'
MARKER = 'farol_notification_crash_containment_0_1_170'


def fail(message: str) -> None:
    raise SystemExit(message)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        fail(f'{label}: esperado 1 ocorrência, encontrado {count}')
    return text.replace(old, new, 1)


GUARD_KOTLIN = r'''package br.com.mapeiaia.rotacerta

/**
 * Circuito fail-closed para impedir que uma exceção no despertar por notificação
 * derrube repetidamente o serviço de acessibilidade.
 */
class FarolNotificationFailureCircuit0170(
    private val cooldownMillis: Long = 60_000L,
) {
    private var blockedUntilElapsedMillis: Long = Long.MIN_VALUE

    fun canAttempt(nowElapsedMillis: Long): Boolean = nowElapsedMillis >= blockedUntilElapsedMillis

    fun onFailure(nowElapsedMillis: Long) {
        blockedUntilElapsedMillis = if (nowElapsedMillis > Long.MAX_VALUE - cooldownMillis) {
            Long.MAX_VALUE
        } else {
            nowElapsedMillis + cooldownMillis
        }
    }

    fun reset() {
        blockedUntilElapsedMillis = Long.MIN_VALUE
    }
}
'''

GUARD_TEST_KOTLIN = r'''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolNotificationFailureCircuit0170Test {
    @Test
    fun failureBlocksOnlyTheNotificationWakePathForBoundedTime() {
        val circuit = FarolNotificationFailureCircuit0170(cooldownMillis = 1_000L)
        assertTrue(circuit.canAttempt(10_000L))
        circuit.onFailure(10_000L)
        assertFalse(circuit.canAttempt(10_999L))
        assertTrue(circuit.canAttempt(11_000L))
    }

    @Test
    fun resetReleasesCircuitImmediately() {
        val circuit = FarolNotificationFailureCircuit0170(cooldownMillis = 60_000L)
        circuit.onFailure(1_000L)
        assertFalse(circuit.canAttempt(1_001L))
        circuit.reset()
        assertTrue(circuit.canAttempt(1_001L))
    }
}
'''

CONTRACT_TEST_KOTLIN = r'''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolNotificationCrashContainmentContract0170Test {
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun notificationEntryIsFailClosedAndDoesNotEscapeToAndroid() {
        val branch = service.substringAfter("AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED").substringBefore("val rootPackage")
        assertTrue(branch.contains("notificationFailureCircuit0170.canAttempt"))
        assertTrue(branch.contains("try {"))
        assertTrue(branch.contains("catch (error0170: Exception)"))
        assertTrue(branch.contains("containNotificationWakeupFailure0170"))
    }

    @Test
    fun asynchronousWakeJobAlsoContainsUnexpectedExceptions() {
        val job = service.substringAfter("notificationWakeJob0169 = scope.launch").substringBefore("private suspend fun captureNotificationOverlay0169")
        assertTrue(job.contains("catch (cancelled0170: kotlinx.coroutines.CancellationException)"))
        assertTrue(job.contains("catch (error0170: Exception)"))
        assertTrue(job.contains("containNotificationWakeupFailure0170"))
    }

    @Test
    fun containmentClearsOnlyTransientWakeState() {
        val containment = service.substringAfter("private fun containNotificationWakeupFailure0170").substringBefore("private fun cancelNotificationWakeup0169")
        assertTrue(containment.contains("notificationWakeGate0169.invalidate"))
        assertTrue(containment.contains("screenshotInProgress.set(false)"))
        assertTrue(containment.contains("recordDiagnostic"))
        assertTrue(containment.contains("keepWaitingYellow = false"))
    }
}
'''

if SERVICE.exists() and MARKER in SERVICE.read_text(encoding='utf-8'):
    if 'versionName = "0.1.170"' not in GRADLE.read_text(encoding='utf-8'):
        fail('marcador 0.1.170 existe, mas versão diverge')
    print('Rota Certa 0.1.170 já aplicada')
    raise SystemExit(0)

for required in (SERVICE, GRADLE):
    if not required.exists():
        fail(f'arquivo obrigatório ausente: {required}')

MAIN.mkdir(parents=True, exist_ok=True)
TEST.mkdir(parents=True, exist_ok=True)
GUARD.write_text(GUARD_KOTLIN, encoding='utf-8')
GUARD_TEST.write_text(GUARD_TEST_KOTLIN, encoding='utf-8')
CONTRACT_TEST.write_text(CONTRACT_TEST_KOTLIN, encoding='utf-8')

gradle = GRADLE.read_text(encoding='utf-8')
gradle = replace_once(gradle, 'versionName = "0.1.169"', 'versionName = "0.1.170"', 'versionName')
gradle = replace_once(gradle, 'versionCode = 5300', 'versionCode = 5310', 'versionCode')
GRADLE.write_text(gradle, encoding='utf-8')

service = SERVICE.read_text(encoding='utf-8')
service = replace_once(
    service,
    '    private var notificationWakeJob0169: Job? = null\n    // farol_notification_wakeup_0_1_169\n',
    '    private var notificationWakeJob0169: Job? = null\n'
    '    private val notificationFailureCircuit0170 = FarolNotificationFailureCircuit0170()\n'
    f'    // {MARKER}\n'
    '    // farol_notification_wakeup_0_1_169\n',
    'campo do circuito de falha',
)

old_notification_entry = '''        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            handleNotificationWakeup0169(event, eventPackage, selectedPackages156)
            return
        }
'''
new_notification_entry = '''        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val now0170 = SystemClock.elapsedRealtime()
            if (!notificationFailureCircuit0170.canAttempt(now0170)) return
            try {
                handleNotificationWakeup0169(event, eventPackage, selectedPackages156)
            } catch (error0170: Exception) {
                notificationFailureCircuit0170.onFailure(now0170)
                containNotificationWakeupFailure0170(
                    stage0170 = "notification_event_entry_0170",
                    packageName0170 = eventPackage,
                    error0170 = error0170,
                )
            }
            return
        }
'''
service = replace_once(service, old_notification_entry, new_notification_entry, 'contenção na entrada da notificação')

old_job_start = '''        notificationWakeJob0169 = scope.launch {
            var recognized0169 = captureNotificationOverlay0169(token0169)
'''
new_job_start = '''        notificationWakeJob0169 = scope.launch {
            try {
                var recognized0169 = captureNotificationOverlay0169(token0169)
'''
service = replace_once(service, old_job_start, new_job_start, 'abertura da contenção assíncrona')

old_job_end = '''            notificationWakeGate0169.invalidate(token0169)
        }
    }

    private suspend fun captureNotificationOverlay0169(
'''
new_job_end = '''                notificationWakeGate0169.invalidate(token0169)
            } catch (cancelled0170: kotlinx.coroutines.CancellationException) {
                throw cancelled0170
            } catch (error0170: Exception) {
                notificationFailureCircuit0170.onFailure(SystemClock.elapsedRealtime())
                containNotificationWakeupFailure0170(
                    stage0170 = "notification_wake_job_0170",
                    packageName0170 = token0169.packageName,
                    error0170 = error0170,
                )
            }
        }
    }

    private suspend fun captureNotificationOverlay0169(
'''
service = replace_once(service, old_job_end, new_job_end, 'fechamento da contenção assíncrona')

old_cancel = '''    private fun cancelNotificationWakeup0169() {
        notificationWakeJob0169?.cancel()
        notificationWakeJob0169 = null
        notificationWakeGate0169.invalidate()
    }
'''
new_cancel = '''    private fun containNotificationWakeupFailure0170(
        stage0170: String,
        packageName0170: String?,
        error0170: Exception,
    ) {
        notificationWakeJob0169?.cancel()
        notificationWakeJob0169 = null
        notificationWakeGate0169.invalidate()
        screenshotInProgress.set(false)
        runCatching {
            recordDiagnostic(
                stage = stage0170,
                reason = "Falha contida no despertar por notificacao; leitura ao vivo preservada.",
                error = error0170,
            )
        }
        runCatching {
            UnifiedDebugEventStore.record(
                "NOTIFICATION_WAKE_FAILURE_CONTAINED_0170",
                packageName0170,
                "stage=$stage0170; type=${error0170::class.java.simpleName}",
            )
        }
        runCatching {
            hardClearUniversalTwoAddress(
                reason = "Falha isolada ao confirmar oferta notificada; estado visual limpo.",
                keepWaitingYellow = false,
            )
        }
    }

    private fun cancelNotificationWakeup0169() {
        notificationWakeJob0169?.cancel()
        notificationWakeJob0169 = null
        notificationWakeGate0169.invalidate()
    }
'''
service = replace_once(service, old_cancel, new_cancel, 'método de contenção')

old_destroy_reset = '''        driverCardSessionGate0162.invalidate()
        failedCardAutoCaptureGate0161.reset()
        lastFailedCardNodes0161 = emptyList()
'''
new_destroy_reset = '''        driverCardSessionGate0162.invalidate()
        failedCardAutoCaptureGate0161.reset()
        notificationFailureCircuit0170.reset()
        lastFailedCardNodes0161 = emptyList()
'''
service = replace_once(service, old_destroy_reset, new_destroy_reset, 'reset do circuito no destroy')

SERVICE.write_text(service, encoding='utf-8')
print('Rota Certa 0.1.170 aplicada: contenção fail-closed do despertar por notificação')
