#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
gradle = root / 'app/build.gradle.kts'
overlay = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt'
service = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt'
test = root / 'app/src/test/java/br/com/mapeiaia/rotacerta/AlertPopupPostPassHold0192ContractTest.kt'

for path in (gradle, overlay, service):
    if not path.is_file():
        raise SystemExit(f'Arquivo obrigatório ausente: {path.relative_to(root)}')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: esperado exatamente 1 ocorrência, encontrado {count}: {old!r}')
    return text.replace(old, new, 1)


gradle_text = gradle.read_text(encoding='utf-8')
gradle_text = replace_once(gradle_text, 'versionCode = 5475', 'versionCode = 5476', 'versionCode')
gradle_text = replace_once(gradle_text, 'versionName = "0.1.191"', 'versionName = "0.1.192"', 'versionName')
gradle.write_text(gradle_text, encoding='utf-8')

overlay_text = overlay.read_text(encoding='utf-8')
if overlay_text.count('const val PASSED_CLOSE_DELAY_MILLIS = 3_000L') != 1:
    raise SystemExit('Overlay: contrato de 3.000 ms ausente ou ambíguo antes da correção')

old_hide_entry = '''    fun hide() {\n        cancelPendingClose()\n'''
new_hide_entry = '''    /**\n     * Ausência normal de visual na avaliação do motor não pode cancelar o período\n     * pós-passagem já agendado. Fechamentos explícitos continuam usando hide().\n     */\n    fun hideFromEngineIdle() {\n        if (pendingClose != null) return\n        hide()\n    }\n\n    fun hide() {\n        cancelPendingClose()\n'''
overlay_text = replace_once(
    overlay_text,
    old_hide_entry,
    new_hide_entry,
    'overlay preserva fechamento pós-passagem diante de visual nulo',
)
overlay.write_text(overlay_text, encoding='utf-8')

service_text = service.read_text(encoding='utf-8')
old_null_visual = '''                if (visual == null) {\n                    directionalAlertOverlayChecklist5.hide()\n                } else {\n'''
new_null_visual = '''                if (visual == null) {\n                    directionalAlertOverlayChecklist5.hideFromEngineIdle()\n                } else {\n'''
service_text = replace_once(
    service_text,
    old_null_visual,
    new_null_visual,
    'visual nulo do motor não derruba temporizador pós-passagem',
)
service.write_text(service_text, encoding='utf-8')

# Regressão estrutural: o controlador Android depende de WindowManager/Looper, então
# protegemos diretamente o contrato de integração que causou a falha física.
test.write_text(r'''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPopupPostPassHold0192ContractTest {
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt").readText()
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun `visual nulo depois da passagem nao cancela fechamento de tres segundos`() {
        val idleHide = overlay
            .substringAfter("fun hideFromEngineIdle()")
            .substringBefore("fun hide()")
        assertTrue(idleHide.contains("if (pendingClose != null) return"))
        assertTrue(idleHide.contains("hide()"))

        val visualCallback = service
            .substringAfter("onVisual = { visual ->")
            .substringBefore("} else {")
        assertTrue(visualCallback.contains("directionalAlertOverlayChecklist5.hideFromEngineIdle()"))
        assertFalse(visualCallback.contains("directionalAlertOverlayChecklist5.hide()"))
    }

    @Test
    fun `temporizador pos passagem continua em tres segundos`() {
        assertTrue(overlay.contains("handler.postDelayed(close, PASSED_CLOSE_DELAY_MILLIS)"))
        assertTrue(overlay.contains("const val PASSED_CLOSE_DELAY_MILLIS = 3_000L"))
    }

    @Test
    fun `fechamentos explicitos continuam imediatos`() {
        assertTrue(overlay.contains("fun hide()"))
        assertTrue(overlay.contains("cancelPendingClose()"))
        assertTrue(service.contains("if (!currentSettings.appEnabled || !currentSettings.proximityAlertsEnabled)"))
        assertTrue(service.contains("directionalAlertOverlayChecklist5.hide()"))
    }

    @Test
    fun `novo visual ainda substitui o fechamento pendente anterior`() {
        val show = overlay
            .substringAfter("fun showOrUpdate(")
            .substringBefore("fun hideFromEngineIdle()")
        assertTrue(show.contains("cancelPendingClose()"))
        assertTrue(show.contains("activeTargetId = visual.targetId"))
    }
}
''', encoding='utf-8')

# Guardas finais fail-closed.
overlay_final = overlay.read_text(encoding='utf-8')
service_final = service.read_text(encoding='utf-8')
if overlay_final.count('fun hideFromEngineIdle()') != 1:
    raise SystemExit('Overlay: esperado exatamente um hideFromEngineIdle()')
if overlay_final.count('const val PASSED_CLOSE_DELAY_MILLIS = 3_000L') != 1:
    raise SystemExit('Overlay: atraso pós-passagem deixou de ser exatamente 3.000 ms')
if service_final.count('directionalAlertOverlayChecklist5.hideFromEngineIdle()') != 1:
    raise SystemExit('Serviço: esperado exatamente um uso de hideFromEngineIdle()')
if old_null_visual in service_final:
    raise SystemExit('Serviço: fluxo antigo de visual nulo ainda está presente')

print('alert_popup_post_pass_hold_0192=applied')
print('post_pass_hold_ms=3000')
print('engine_idle_preserves_pending_close=true')
print('explicit_hide_remains_immediate=true')
