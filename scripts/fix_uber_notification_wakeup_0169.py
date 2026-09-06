#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
APP = ROOT / 'app'
MAIN = APP / 'src/main/java/br/com/mapeiaia/rotacerta'
TEST = APP / 'src/test/java/br/com/mapeiaia/rotacerta'
SERVICE = MAIN / 'LiveRideAccessibilityService.kt'
FLOOD = MAIN / 'AccessibilityEventFloodGate.kt'
XML = APP / 'src/main/res/xml/rota_certa_accessibility.xml'
GRADLE = APP / 'build.gradle.kts'
POLICY = MAIN / 'FarolNotificationWakeup0169.kt'
POLICY_TEST = TEST / 'FarolNotificationWakeup0169Test.kt'
CONTRACT_TEST = TEST / 'FarolNotificationWakeupContract0169Test.kt'
MARKER = 'farol_notification_wakeup_0_1_169'


def fail(message: str) -> None:
    raise SystemExit(message)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        fail(f'{label}: esperado 1 ocorrência, encontrado {count}')
    return text.replace(old, new, 1)


KOTLIN = r'''package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * Porta de entrada limitada para cards que aparecem como sobreposição de notificação.
 *
 * Não autoriza verde/vermelho. Apenas permite um pequeno conjunto de capturas OCR
 * quando o Android informa uma notificação de um pacote escolhido pelo usuário.
 */
data class FarolNotificationWakeToken0169(
    val packageName: String,
    val generation: Long,
    val startedAtElapsedMillis: Long,
)

class FarolNotificationWakeGate0169(
    private val duplicateWindowMillis: Long = 250L,
    private val tokenTtlMillis: Long = 12_000L,
    private val maxCaptures: Int = 4,
) {
    private var generation: Long = 0L
    private var activeToken: FarolNotificationWakeToken0169? = null
    private var captureCount: Int = 0
    private var lastAcceptedPackage: String? = null
    private var lastAcceptedAtElapsedMillis: Long = Long.MIN_VALUE

    fun begin(
        eventType: Int,
        eventPackageName: String?,
        selectedPackages: Set<String>,
        ownPackageName: String,
        workModeEnabled: Boolean,
        liveReadingEnabled: Boolean,
        serviceReady: Boolean,
        bubbleGestureActive: Boolean,
        nowElapsedMillis: Long,
    ): FarolNotificationWakeToken0169? {
        if (eventType != TYPE_NOTIFICATION_STATE_CHANGED || !workModeEnabled ||
            !liveReadingEnabled || !serviceReady || bubbleGestureActive
        ) return null
        val normalized = normalize(eventPackageName) ?: return null
        val own = normalize(ownPackageName)
        val selected = selectedPackages.mapNotNull(::normalize).toSet()
        if (normalized == own || normalized !in selected) return null
        val duplicate = normalized == lastAcceptedPackage &&
            nowElapsedMillis >= lastAcceptedAtElapsedMillis &&
            nowElapsedMillis - lastAcceptedAtElapsedMillis < duplicateWindowMillis
        if (duplicate) return null

        generation += 1L
        captureCount = 0
        lastAcceptedPackage = normalized
        lastAcceptedAtElapsedMillis = nowElapsedMillis
        return FarolNotificationWakeToken0169(
            packageName = normalized,
            generation = generation,
            startedAtElapsedMillis = nowElapsedMillis,
        ).also { activeToken = it }
    }

    fun reserveCapture(
        token: FarolNotificationWakeToken0169,
        nowElapsedMillis: Long,
    ): Int? {
        if (!isCurrent(token, nowElapsedMillis) || captureCount >= maxCaptures) return null
        return captureCount++
    }

    fun isCurrent(
        token: FarolNotificationWakeToken0169,
        nowElapsedMillis: Long,
    ): Boolean {
        val current = activeToken ?: return false
        if (current != token) return false
        val age = nowElapsedMillis - token.startedAtElapsedMillis
        if (age < 0L || age > tokenTtlMillis) {
            invalidate(token)
            return false
        }
        return true
    }

    fun shouldDeferPassiveRejection(
        eventPackageName: String?,
        rootPackageName: String?,
        ownPackageName: String,
        nowElapsedMillis: Long,
    ): Boolean {
        val token = activeToken ?: return false
        if (!isCurrent(token, nowElapsedMillis)) return false
        val eventPackage = normalize(eventPackageName)
        val rootPackage = normalize(rootPackageName)
        val ownPackage = normalize(ownPackageName)
        return listOf(eventPackage, rootPackage).all { value ->
            value == null || value == ownPackage || isPassiveOrSystem(value)
        }
    }

    fun invalidate(token: FarolNotificationWakeToken0169? = null) {
        if (token != null && activeToken != token) return
        activeToken = null
        captureCount = 0
    }

    private fun isPassiveOrSystem(packageName: String): Boolean =
        packageName.contains("launcher") ||
            packageName == "com.android.systemui" ||
            packageName == "com.samsung.android.app.smartcapture" ||
            packageName == "com.google.android.packageinstaller" ||
            packageName == "com.google.android.apps.nbu.files"

    private fun normalize(value: String?): String? = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)

    companion object {
        const val TYPE_NOTIFICATION_STATE_CHANGED: Int = 64
    }
}
'''

TEST_KOTLIN = r'''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolNotificationWakeup0169Test {
    private val selected = setOf("com.ubercab.driver", "com.app99.driver")

    @Test
    fun selectedNotificationStartsBoundedWakeup() {
        val gate = FarolNotificationWakeGate0169(maxCaptures = 2)
        val token = gate.begin(
            eventType = 64,
            eventPackageName = "com.ubercab.driver",
            selectedPackages = selected,
            ownPackageName = "br.com.mapeiaia.rotacerta",
            workModeEnabled = true,
            liveReadingEnabled = true,
            serviceReady = true,
            bubbleGestureActive = false,
            nowElapsedMillis = 1_000L,
        )
        assertNotNull(token)
        assertEquals(0, gate.reserveCapture(token!!, 1_000L))
        assertEquals(1, gate.reserveCapture(token, 1_100L))
        assertNull(gate.reserveCapture(token, 1_200L))
    }

    @Test
    fun unselectedOrDisabledNotificationNeverWakesOcr() {
        val gate = FarolNotificationWakeGate0169()
        assertNull(gate.begin(64, "com.example.other", selected, "br.com.mapeiaia.rotacerta", true, true, true, false, 1_000L))
        assertNull(gate.begin(64, "com.ubercab.driver", selected, "br.com.mapeiaia.rotacerta", false, true, true, false, 2_000L))
        assertNull(gate.begin(2_048, "com.ubercab.driver", selected, "br.com.mapeiaia.rotacerta", true, true, true, false, 3_000L))
    }

    @Test
    fun duplicateNotificationDoesNotCreateParallelWakeup() {
        val gate = FarolNotificationWakeGate0169(duplicateWindowMillis = 300L)
        val first = gate.begin(64, "com.ubercab.driver", selected, "br.com.mapeiaia.rotacerta", true, true, true, false, 1_000L)
        val duplicate = gate.begin(64, "com.ubercab.driver", selected, "br.com.mapeiaia.rotacerta", true, true, true, false, 1_100L)
        val later = gate.begin(64, "com.ubercab.driver", selected, "br.com.mapeiaia.rotacerta", true, true, true, false, 1_400L)
        assertNotNull(first)
        assertNull(duplicate)
        assertNotNull(later)
        assertFalse(gate.isCurrent(first!!, 1_401L))
        assertTrue(gate.isCurrent(later!!, 1_401L))
    }

    @Test
    fun passiveLauncherEventsAreDeferredOnlyInsideTokenTtl() {
        val gate = FarolNotificationWakeGate0169(tokenTtlMillis = 1_000L)
        val token = gate.begin(64, "com.ubercab.driver", selected, "br.com.mapeiaia.rotacerta", true, true, true, false, 1_000L)!!
        assertTrue(gate.shouldDeferPassiveRejection("com.android.systemui", "com.sec.android.app.launcher", "br.com.mapeiaia.rotacerta", 1_500L))
        assertFalse(gate.shouldDeferPassiveRejection("sinet.startup.indriver", "sinet.startup.indriver", "br.com.mapeiaia.rotacerta", 1_500L))
        assertFalse(gate.isCurrent(token, 2_001L))
    }
}
'''

CONTRACT_KOTLIN = r'''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolNotificationWakeupContract0169Test {
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
    private val xml = File("src/main/res/xml/rota_certa_accessibility.xml").readText()

    @Test
    fun notificationEventIsSubscribedAndHandledBeforeStrictRootResolution() {
        assertTrue(xml.contains("typeNotificationStateChanged"))
        val handler = service.indexOf("handleNotificationWakeup0169")
        val resolver = service.indexOf("DriverCardEventResolver0162.resolve")
        assertTrue(handler >= 0)
        assertTrue(resolver > handler)
    }

    @Test
    fun notificationWakeupIsBoundedAndHasNoPollingLoop() {
        assertTrue(service.contains("NOTIFICATION_INITIAL_RETRY_DELAY_MILLIS_0169"))
        assertTrue(service.contains("NOTIFICATION_VERIFY_DELAY_MILLIS_0169"))
        assertFalse(service.contains("while (notificationWake"))
        assertFalse(service.contains("Timer("))
    }
}
'''

if MARKER in SERVICE.read_text(encoding='utf-8') if SERVICE.exists() else False:
    if 'versionName = "0.1.169"' not in GRADLE.read_text(encoding='utf-8'):
        fail('marcador 0.1.169 existe, mas versão diverge')
    print('Rota Certa 0.1.169 já aplicada')
    raise SystemExit(0)

for required in (SERVICE, FLOOD, XML, GRADLE):
    if not required.exists():
        fail(f'arquivo obrigatório ausente: {required}')

MAIN.mkdir(parents=True, exist_ok=True)
TEST.mkdir(parents=True, exist_ok=True)
POLICY.write_text(KOTLIN, encoding='utf-8')
POLICY_TEST.write_text(TEST_KOTLIN, encoding='utf-8')
CONTRACT_TEST.write_text(CONTRACT_KOTLIN, encoding='utf-8')

gradle = GRADLE.read_text(encoding='utf-8')
gradle = replace_once(gradle, 'versionName = "0.1.168"', 'versionName = "0.1.169"', 'versionName')
gradle = replace_once(gradle, 'versionCode = 5290', 'versionCode = 5300', 'versionCode')
GRADLE.write_text(gradle, encoding='utf-8')

xml = XML.read_text(encoding='utf-8')
xml = replace_once(
    xml,
    'android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeWindowsChanged"',
    'android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeWindowsChanged|typeNotificationStateChanged"',
    'evento de notificação na configuração da acessibilidade',
)
XML.write_text(xml, encoding='utf-8')

flood = FLOOD.read_text(encoding='utf-8')
flood = replace_once(
    flood,
    '        const val TYPE_WINDOW_STATE_CHANGED = 32\n',
    '        const val TYPE_WINDOW_STATE_CHANGED = 32\n        const val TYPE_NOTIFICATION_STATE_CHANGED = 64\n',
    'constante de notificação',
)
flood = replace_once(
    flood,
    '        fun isRelevantEventType(eventType: Int): Boolean = eventType == TYPE_WINDOW_STATE_CHANGED ||\n            eventType == TYPE_WINDOW_CONTENT_CHANGED ||',
    '        fun isRelevantEventType(eventType: Int): Boolean = eventType == TYPE_WINDOW_STATE_CHANGED ||\n            eventType == TYPE_NOTIFICATION_STATE_CHANGED ||\n            eventType == TYPE_WINDOW_CONTENT_CHANGED ||',
    'filtro de tipo relevante',
)
FLOOD.write_text(flood, encoding='utf-8')

service = SERVICE.read_text(encoding='utf-8')
service = replace_once(
    service,
    'import kotlinx.coroutines.CoroutineStart\n',
    'import kotlinx.coroutines.CompletableDeferred\nimport kotlinx.coroutines.CoroutineStart\n',
    'import CompletableDeferred',
)
service = replace_once(
    service,
    'import kotlinx.coroutines.withContext\n',
    'import kotlinx.coroutines.withContext\nimport kotlinx.coroutines.withTimeoutOrNull\n',
    'import withTimeoutOrNull',
)
service = replace_once(
    service,
    '    private val screenshotInProgress = AtomicBoolean(false)\n',
    '    private val screenshotInProgress = AtomicBoolean(false)\n'
    '    private val notificationWakeGate0169 = FarolNotificationWakeGate0169()\n'
    '    private var notificationWakeJob0169: Job? = null\n'
    f'    // {MARKER}\n',
    'campos do despertar por notificação',
)

old_event_head = '''        if (!AccessibilityEventFloodGate.isRelevantEventType(event.eventType)) return

        val eventPackage = normalizePackageName(event.packageName?.toString())
        val rootPackage = currentRootPackageName()
        val selectedPackages156 = SelectedRideAppStore.read(applicationContext)
'''
new_event_head = '''        if (!AccessibilityEventFloodGate.isRelevantEventType(event.eventType)) return

        val eventPackage = normalizePackageName(event.packageName?.toString())
        val selectedPackages156 = SelectedRideAppStore.read(applicationContext)
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            handleNotificationWakeup0169(event, eventPackage, selectedPackages156)
            return
        }
        val rootPackage = currentRootPackageName()
        if (rootPackage in selectedPackages156 || rootPackage == packageName) {
            cancelNotificationWakeup0169()
        }
'''
service = replace_once(service, old_event_head, new_event_head, 'entrada do evento de acessibilidade')

old_rejected_anchor = '''        val root0162 = DriverAppPackagePolicy0162.normalize(rootPackage0162)
        val eventPackageNormalized0162 = DriverAppPackagePolicy0162.normalize(eventPackage0162)
        if (root0162 == packageName) {
'''
new_rejected_anchor = '''        val root0162 = DriverAppPackagePolicy0162.normalize(rootPackage0162)
        val eventPackageNormalized0162 = DriverAppPackagePolicy0162.normalize(eventPackage0162)
        if (notificationWakeGate0169.shouldDeferPassiveRejection(
                eventPackageName = eventPackageNormalized0162,
                rootPackageName = root0162,
                ownPackageName = packageName,
                nowElapsedMillis = SystemClock.elapsedRealtime(),
            )
        ) {
            UnifiedDebugEventStore.record(
                "NOTIFICATION_PASSIVE_REJECTION_DEFERRED_0169",
                universalResolvedForegroundPackage(),
                "event=${eventPackageNormalized0162 ?: "none"}; root=${root0162 ?: "none"}",
            )
            return
        }
        if (root0162 == packageName) {
'''
service = replace_once(service, old_rejected_anchor, new_rejected_anchor, 'proteção transitória do launcher')

old_work_off = '''        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null
        partialReadConfirmationJobChecklist14?.cancel()
'''
new_work_off = '''        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null
        cancelNotificationWakeup0169()
        partialReadConfirmationJobChecklist14?.cancel()
'''
service = replace_once(service, old_work_off, new_work_off, 'cancelamento no Modo Trabalho desligado')

old_destroy = '''        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null // deferred_ocr_destroy_cancel_0_1_127
        liveAnalysisJob?.cancel() // latest_card_wins_destroy_0_1_91
'''
new_destroy = '''        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null // deferred_ocr_destroy_cancel_0_1_127
        cancelNotificationWakeup0169()
        liveAnalysisJob?.cancel() // latest_card_wins_destroy_0_1_91
'''
service = replace_once(service, old_destroy, new_destroy, 'cancelamento no destroy')

notification_methods = r'''
    private fun handleNotificationWakeup0169(
        event0169: AccessibilityEvent,
        eventPackage0169: String?,
        selectedPackages0169: Set<String>,
    ) {
        val token0169 = notificationWakeGate0169.begin(
            eventType = event0169.eventType,
            eventPackageName = eventPackage0169,
            selectedPackages = selectedPackages0169,
            ownPackageName = packageName,
            workModeEnabled = WorkModePolicy0162.isEnabled(currentSettings),
            liveReadingEnabled = currentSettings.liveReadingEnabled,
            serviceReady = serviceReady,
            bubbleGestureActive = bubbleGestureActive,
            nowElapsedMillis = SystemClock.elapsedRealtime(),
        ) ?: return

        notificationWakeJob0169?.cancel()
        universalScreenGeneration += 1L
        universalWindowGeneration += 1L
        universalRouteJob?.cancel()
        universalRouteJob = null
        analyzeJob?.cancel()
        analyzeJob = null
        screenshotFallbackJob127?.cancel()
        screenshotFallbackJob127 = null
        failedCardAutoCaptureGate0161.reset()
        lastFailedCardNodes0161 = emptyList()
        lastFailedCardSignature0161 = null
        lastFailedCardAccessibilityHash0161 = null
        lastAccessibilityText = ""
        lastAccessibilityTextAtMillis = 0L
        lastOcrText = ""
        lastOcrTextAtMillis = 0L

        recentSelectedRidePackageChecklist11 = token0169.packageName
        recentSelectedRidePackageAtMillisChecklist11 = System.currentTimeMillis()
        universalForegroundPackageName = token0169.packageName
        activePackageName = token0169.packageName
        lastExternalWindowPackageName = token0169.packageName
        val wakeWindow0169 = event0169.windowId.takeIf { it >= 0 } ?: 0
        ensureDriverCardSession0162(token0169.packageName, wakeWindow0169)
        if (currentRadarColor == RadarColor.Idle) {
            rememberBubbleReason(
                "notification_waiting_0169",
                "Aplicativo selecionado notificou uma oferta; confirmando o card visual.",
            )
            showOverlay(RadarColor.Default, null)
        }
        UnifiedDebugEventStore.record(
            "NOTIFICATION_WAKE_ACCEPTED_0169",
            token0169.packageName,
            "window=$wakeWindow0169; generation=${token0169.generation}; text=${event0169.text.joinToString(" ").take(240)}",
        )

        notificationWakeJob0169 = scope.launch {
            var recognized0169 = captureNotificationOverlay0169(token0169)
            if (!recognized0169) {
                delay(NOTIFICATION_INITIAL_RETRY_DELAY_MILLIS_0169)
                recognized0169 = captureNotificationOverlay0169(token0169)
            }
            if (!recognized0169) {
                if (notificationWakeGate0169.isCurrent(token0169, SystemClock.elapsedRealtime())) {
                    hardClearUniversalTwoAddress(
                        reason = "Notificacao do aplicativo selecionado sem card confirmado.",
                        keepWaitingYellow = false,
                    )
                }
                notificationWakeGate0169.invalidate(token0169)
                return@launch
            }

            delay(NOTIFICATION_VERIFY_DELAY_MILLIS_0169)
            if (!notificationWakeGate0169.isCurrent(token0169, SystemClock.elapsedRealtime())) return@launch
            recognized0169 = captureNotificationOverlay0169(token0169)
            if (!recognized0169) {
                notificationWakeGate0169.invalidate(token0169)
                return@launch
            }

            delay(NOTIFICATION_FINAL_VERIFY_DELAY_MILLIS_0169)
            if (notificationWakeGate0169.isCurrent(token0169, SystemClock.elapsedRealtime())) {
                captureNotificationOverlay0169(token0169)
            }
            notificationWakeGate0169.invalidate(token0169)
        }
    }

    private suspend fun captureNotificationOverlay0169(
        token0169: FarolNotificationWakeToken0169,
    ): Boolean {
        val attempt0169 = notificationWakeGate0169.reserveCapture(
            token0169,
            SystemClock.elapsedRealtime(),
        ) ?: return false
        if (token0169.packageName !in SelectedRideAppStore.read(applicationContext) ||
            !shouldScanPackage(token0169.packageName) || Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        ) return false
        if (!screenshotInProgress.compareAndSet(false, true)) return false

        val completion0169 = CompletableDeferred<Boolean>()
        UnifiedDebugEventStore.record(
            "NOTIFICATION_CAPTURE_STARTED_0169",
            token0169.packageName,
            "attempt=$attempt0169; generation=${token0169.generation}",
        )
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch {
                            var bitmap0169: Bitmap? = null
                            var recognized0169 = false
                            try {
                                if (!notificationWakeGate0169.isCurrent(token0169, SystemClock.elapsedRealtime()) ||
                                    token0169.packageName !in SelectedRideAppStore.read(applicationContext)
                                ) return@launch
                                val session0169 = driverCardSessionGate0162.current()
                                    ?.takeIf { it.packageName == token0169.packageName }
                                    ?: return@launch
                                if (!driverCardSessionGate0162.isCurrent(session0169)) return@launch
                                bitmap0169 = screenshot.toSoftwareBitmap() ?: return@launch
                                val ocrText0169 = withContext(Dispatchers.Default) {
                                    ocrService.extractText(bitmap0169)
                                }
                                rememberSourceText(token0169.packageName, TextSource.Ocr, ocrText0169)
                                processRideText(
                                    ocrText0169,
                                    TextSource.Ocr,
                                    allowPopupCandidate = true,
                                    packageHint152 = token0169.packageName,
                                )
                                recognized0169 = universalActiveRidePackageName == token0169.packageName &&
                                    universalActiveAddressSignature != null
                                UnifiedDebugEventStore.record(
                                    "NOTIFICATION_CAPTURE_FINISHED_0169",
                                    token0169.packageName,
                                    "attempt=$attempt0169; text=${ocrText0169.length}; recognized=$recognized0169",
                                )
                            } catch (error0169: Throwable) {
                                recordDiagnostic(
                                    stage = "notification_capture_error_0169",
                                    reason = "Falha isolada ao confirmar visualmente a oferta notificada.",
                                    error = error0169,
                                )
                            } finally {
                                bitmap0169?.takeUnless(Bitmap::isRecycled)?.recycle()
                                screenshotInProgress.set(false)
                                completion0169.complete(recognized0169)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotInProgress.set(false)
                        UnifiedDebugEventStore.record(
                            "NOTIFICATION_CAPTURE_FAILED_0169",
                            token0169.packageName,
                            "attempt=$attempt0169; code=$errorCode",
                        )
                        completion0169.complete(false)
                    }
                },
            )
        }.onFailure { error0169 ->
            screenshotInProgress.set(false)
            completion0169.complete(false)
            recordDiagnostic(
                stage = "notification_capture_request_error_0169",
                reason = "Android nao iniciou a captura pontual da oferta notificada.",
                error = error0169,
            )
        }
        return withTimeoutOrNull(NOTIFICATION_CAPTURE_TIMEOUT_MILLIS_0169) {
            completion0169.await()
        } ?: false
    }

    private fun cancelNotificationWakeup0169() {
        notificationWakeJob0169?.cancel()
        notificationWakeJob0169 = null
        notificationWakeGate0169.invalidate()
    }

'''
anchor = '    private fun startContinuousScan() {\n'
if anchor not in service:
    fail('âncora startContinuousScan não encontrada')
service = service.replace(anchor, notification_methods + anchor, 1)

constant_anchor = '''        const val BUBBLE_PREFS = "rota_certa_bubble"
'''
if constant_anchor not in service:
    fail('âncora de constantes não encontrada')
constant_block = (
    constant_anchor +
    '        private const val NOTIFICATION_INITIAL_RETRY_DELAY_MILLIS_0169 = 180L\n'
    '        private const val NOTIFICATION_VERIFY_DELAY_MILLIS_0169 = 5_000L\n'
    '        private const val NOTIFICATION_FINAL_VERIFY_DELAY_MILLIS_0169 = 4_500L\n'
    '        private const val NOTIFICATION_CAPTURE_TIMEOUT_MILLIS_0169 = 2_500L\n'
)
service = service.replace(constant_anchor, constant_block, 1)
SERVICE.write_text(service, encoding='utf-8')

print('Rota Certa 0.1.169 aplicada: despertar pontual por notificação para overlays sem evento de janela')
