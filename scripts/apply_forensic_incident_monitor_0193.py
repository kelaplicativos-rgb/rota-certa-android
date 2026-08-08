#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
gradle = root / 'app/build.gradle.kts'
recorder = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/FarolFlightRecorder0163.kt'
report = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt'
overlay = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt'
monitor = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt'
test = root / 'app/src/test/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193Test.kt'
contract = root / 'app/src/test/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193ContractTest.kt'

for path in (gradle, recorder, report, overlay):
    if not path.is_file():
        raise SystemExit(f'Arquivo obrigatório ausente: {path.relative_to(root)}')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: esperado exatamente 1 trecho, encontrado {count}')
    return text.replace(old, new, 1)


def inject_after_function_open(text: str, function_name: str, insertion: str, required_tokens: tuple[str, ...]) -> str:
    marker = insertion.strip()
    if marker in text:
        return text
    matches = list(re.finditer(rf'\bfun\s+{re.escape(function_name)}\s*\(', text))
    if len(matches) != 1:
        raise SystemExit(f'{function_name}: esperado exatamente 1 método, encontrado {len(matches)}')
    start = matches[0].start()
    next_fun = text.find('\n    fun ', matches[0].end())
    search_end = next_fun if next_fun >= 0 else min(len(text), matches[0].end() + 6000)
    signature_and_body = text[start:search_end]
    for token in required_tokens:
        if token not in signature_and_body[:2500]:
            raise SystemExit(f'{function_name}: token obrigatório ausente na assinatura/cabeçalho: {token}')
    brace = text.find('{', matches[0].end(), search_end)
    if brace < 0:
        raise SystemExit(f'{function_name}: abertura de corpo não encontrada')
    return text[:brace + 1] + '\n' + insertion + text[brace + 1:]


gradle_text = gradle.read_text(encoding='utf-8')
gradle_text = replace_once(gradle_text, 'versionCode = 5476', 'versionCode = 5477', 'versionCode')
gradle_text = replace_once(gradle_text, 'versionName = "0.1.192"', 'versionName = "0.1.193"', 'versionName')
gradle.write_text(gradle_text, encoding='utf-8')

monitor.write_text(r'''package br.com.mapeiaia.rotacerta

import android.os.SystemClock
import java.util.Locale

/**
 * Observa o gravador de voo existente sem criar um segundo logger.
 * Não usa timer, coroutine, screenshot, OCR, rede ou escrita contínua em disco.
 * Apenas mantém estado mínimo para detectar violações de contrato e injeta
 * marcadores seguros no próprio FarolFlightRecorder0163.
 */
object ForensicIncidentMonitor0193 {
    private const val EVENT_STORM_WINDOW_NANOS = 500_000_000L
    private const val OCR_STORM_WINDOW_NANOS = 500_000_000L
    private const val EVENT_STORM_THRESHOLD = 8
    private const val OCR_STORM_THRESHOLD = 4

    private var lastFingerprint: Int = 0
    private var lastFingerprintAt: Long = 0L
    private var repeatCount: Int = 0
    private var lastStormReportedAt: Long = 0L
    private var highestGeneration: Long = -1L
    private var highestWindowGeneration: Long = -1L
    private var anomalyCount: Long = 0L

    @Synchronized
    fun observe(stage: String, packageName: String?, details: String) {
        if (stage.startsWith("FORENSIC_")) return
        val now = SystemClock.elapsedRealtimeNanos()
        val fingerprint = 31 * stage.hashCode() + details.hashCode()
        if (fingerprint == lastFingerprint && now - lastFingerprintAt <= EVENT_STORM_WINDOW_NANOS) {
            repeatCount += 1
        } else {
            lastFingerprint = fingerprint
            repeatCount = 1
        }
        lastFingerprintAt = now

        val generation = numericToken(details, "generation")
        val windowGeneration = numericToken(details, "windowGeneration")
        if (generation != null) highestGeneration = maxOf(highestGeneration, generation)
        if (windowGeneration != null) highestWindowGeneration = maxOf(highestWindowGeneration, windowGeneration)

        if (repeatCount >= EVENT_STORM_THRESHOLD && now - lastStormReportedAt > EVENT_STORM_WINDOW_NANOS) {
            lastStormReportedAt = now
            anomaly(
                packageName,
                "FORENSIC_EVENT_STORM_0193",
                "stage=${safeStage(stage)}; repeats=$repeatCount; window_ms=500",
            )
        }

        if (stage.contains("OCR", ignoreCase = true) && repeatCount >= OCR_STORM_THRESHOLD && now - lastStormReportedAt > OCR_STORM_WINDOW_NANOS) {
            lastStormReportedAt = now
            anomaly(
                packageName,
                "FORENSIC_OCR_STORM_0193",
                "stage=${safeStage(stage)}; repeats=$repeatCount; window_ms=500",
            )
        }

        if (generation != null && generation < highestGeneration && isResultStage(stage)) {
            anomaly(
                packageName,
                "FORENSIC_STALE_GENERATION_RESULT_0193",
                "stage=${safeStage(stage)}; result_generation=$generation; latest_generation=$highestGeneration",
            )
        }
        if (windowGeneration != null && windowGeneration < highestWindowGeneration && isResultStage(stage)) {
            anomaly(
                packageName,
                "FORENSIC_STALE_WINDOW_RESULT_0193",
                "stage=${safeStage(stage)}; result_window_generation=$windowGeneration; latest_window_generation=$highestWindowGeneration",
            )
        }

        if (stage == "OVERLAY_RENDER_APPLIED") {
            val normalized = details.lowercase(Locale.ROOT)
            val finalColor = normalized.contains("green") || normalized.contains("verde") || normalized.contains("red") || normalized.contains("vermelh")
            val missingDistance = normalized.contains("distance=null") || normalized.contains("distance=none") || normalized.contains("km=null")
            if (finalColor && missingDistance) {
                anomaly(packageName, "FORENSIC_FINAL_COLOR_WITHOUT_DISTANCE_0193", "stage=OVERLAY_RENDER_APPLIED")
            }
        }
    }

    fun markManualReport() {
        FarolFlightRecorder0163.record(
            stage = "FORENSIC_MANUAL_INCIDENT_MARK_0193",
            packageName = null,
            details = "manual_report_requested=true; anomalies=$anomalyCount; latest_generation=$highestGeneration; latest_window_generation=$highestWindowGeneration",
        )
    }

    @Synchronized
    internal fun resetForTest() {
        lastFingerprint = 0
        lastFingerprintAt = 0L
        repeatCount = 0
        lastStormReportedAt = 0L
        highestGeneration = -1L
        highestWindowGeneration = -1L
        anomalyCount = 0L
    }

    private fun isResultStage(stage: String): Boolean {
        val value = stage.uppercase(Locale.ROOT)
        return value.contains("DECISION") || value.contains("RESULT") || value.contains("OVERLAY_RENDER_APPLIED") || value.contains("ROUTE_APPLIED") || value.contains("CACHE_APPLIED")
    }

    private fun numericToken(details: String, key: String): Long? {
        val match = Regex("(?:^|[; ,])${Regex.escape(key)}=(-?\\d+)").find(details) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private fun safeStage(stage: String): String = stage.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(96)

    private fun anomaly(packageName: String?, stage: String, details: String) {
        anomalyCount += 1L
        FarolFlightRecorder0163.record(stage = stage, packageName = packageName, details = details)
    }
}
''', encoding='utf-8')

recorder_text = recorder.read_text(encoding='utf-8')
recorder_text = inject_after_function_open(
    recorder_text,
    'record',
    '        ForensicIncidentMonitor0193.observe(stage, packageName, details)\n',
    ('stage', 'packageName', 'details'),
)
recorder.write_text(recorder_text, encoding='utf-8')

report_text = report.read_text(encoding='utf-8')
if 'ForensicIncidentMonitor0193.markManualReport()' not in report_text:
    report_text = inject_after_function_open(
        report_text,
        'build',
        '        ForensicIncidentMonitor0193.markManualReport()\n',
        ('FarolFlightRecorder0163.exportReport',),
    )
report.write_text(report_text, encoding='utf-8')

overlay_text = overlay.read_text(encoding='utf-8')
if 'ALERT_OVERLAY_POST_PASS_SCHEDULED_0193' not in overlay_text:
    overlay_text = replace_once(
        overlay_text,
        '        handler.postDelayed(close, PASSED_CLOSE_DELAY_MILLIS)',
        '''        FarolFlightRecorder0163.record(
            stage = "ALERT_OVERLAY_POST_PASS_SCHEDULED_0193",
            packageName = null,
            details = "delay_ms=$PASSED_CLOSE_DELAY_MILLIS; target_hash=${visual.targetId.hashCode()}",
        )
        handler.postDelayed(close, PASSED_CLOSE_DELAY_MILLIS)''',
        'telemetria do agendamento pós-passagem',
    )

old_idle = '''    fun hideFromEngineIdle() {
        if (pendingClose != null) return
        hide()
    }'''
new_idle = '''    fun hideFromEngineIdle() {
        if (pendingClose != null) {
            FarolFlightRecorder0163.record(
                stage = "ALERT_OVERLAY_ENGINE_IDLE_PRESERVED_0193",
                packageName = null,
                details = "pending_close=true",
            )
            return
        }
        hide()
    }'''
if old_idle in overlay_text:
    overlay_text = overlay_text.replace(old_idle, new_idle, 1)
elif 'ALERT_OVERLAY_ENGINE_IDLE_PRESERVED_0193' not in overlay_text:
    raise SystemExit('hideFromEngineIdle: contrato 0.1.192 não encontrado')

overlay.write_text(overlay_text, encoding='utf-8')

test.write_text(r'''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertTrue
import org.junit.Test

class ForensicIncidentMonitor0193Test {
    @Test
    fun `monitor nao armazena texto bruto nem cria loop artificial`() {
        val source = java.io.File("src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt").readText()
        assertTrue(source.contains("EVENT_STORM_THRESHOLD = 8"))
        assertTrue(!source.contains("Timer("))
        assertTrue(!source.contains("delay("))
        assertTrue(!source.contains("takeScreenshot"))
        assertTrue(!source.contains("writeText("))
    }

    @Test
    fun `resultado antigo e tempestade de eventos possuem detectores dedicados`() {
        val source = java.io.File("src/main/java/br/com/mapeiaia/rotacerta/ForensicIncidentMonitor0193.kt").readText()
        assertTrue(source.contains("FORENSIC_EVENT_STORM_0193"))
        assertTrue(source.contains("FORENSIC_OCR_STORM_0193"))
        assertTrue(source.contains("FORENSIC_STALE_GENERATION_RESULT_0193"))
        assertTrue(source.contains("FORENSIC_STALE_WINDOW_RESULT_0193"))
        assertTrue(source.contains("FORENSIC_FINAL_COLOR_WITHOUT_DISTANCE_0193"))
    }
}
''', encoding='utf-8')

contract.write_text(r'''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForensicIncidentMonitor0193ContractTest {
    private val recorder = File("src/main/java/br/com/mapeiaia/rotacerta/FarolFlightRecorder0163.kt").readText()
    private val report = File("src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt").readText()
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt").readText()

    @Test
    fun `gravador existente alimenta monitor sem segundo logger`() {
        assertTrue(recorder.contains("ForensicIncidentMonitor0193.observe(stage, packageName, details)"))
        assertFalse(recorder.contains("ForensicIncidentMonitor0193.write"))
    }

    @Test
    fun `relatorio manual marca momento exato da reclamacao`() {
        assertTrue(report.contains("ForensicIncidentMonitor0193.markManualReport()"))
        assertTrue(report.contains("FarolFlightRecorder0163.exportReport"))
    }

    @Test
    fun `popup registra agendamento e preservacao do fechamento de tres segundos`() {
        assertTrue(overlay.contains("ALERT_OVERLAY_POST_PASS_SCHEDULED_0193"))
        assertTrue(overlay.contains("ALERT_OVERLAY_ENGINE_IDLE_PRESERVED_0193"))
        assertTrue(overlay.contains("PASSED_CLOSE_DELAY_MILLIS = 3_000L"))
    }
}
''', encoding='utf-8')

# Guardas finais fail-closed.
if recorder.read_text(encoding='utf-8').count('ForensicIncidentMonitor0193.observe(stage, packageName, details)') != 1:
    raise SystemExit('Gravador: integração forense deve existir exatamente uma vez')
if report.read_text(encoding='utf-8').count('ForensicIncidentMonitor0193.markManualReport()') != 1:
    raise SystemExit('Relatório: marcador manual deve existir exatamente uma vez')
if overlay.read_text(encoding='utf-8').count('ALERT_OVERLAY_POST_PASS_SCHEDULED_0193') != 1:
    raise SystemExit('Overlay: telemetria de fechamento deve existir exatamente uma vez')

print('forensic_incident_monitor_0193=applied')
print('uses_existing_flight_recorder=true')
print('extra_polling=false')
print('extra_screenshot=false')
print('extra_continuous_disk_log=false')
