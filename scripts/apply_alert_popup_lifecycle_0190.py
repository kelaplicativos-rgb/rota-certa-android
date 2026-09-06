#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
gradle = root / 'app/build.gradle.kts'
overlay = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt'
service = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt'
engine = root / 'app/src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt'
test = root / 'app/src/test/java/br/com/mapeiaia/rotacerta/AlertPopupLifecycle0190ContractTest.kt'

for path in (gradle, overlay, service, engine):
    if not path.is_file():
        raise SystemExit(f'Arquivo obrigatório ausente: {path.relative_to(root)}')

def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: esperado exatamente 1 ocorrência de {old!r}, encontrado {count}')
    return text.replace(old, new, 1)

gradle_text = gradle.read_text(encoding='utf-8')
gradle_text = replace_once(gradle_text, 'versionCode = 5473', 'versionCode = 5474', 'versionCode')
gradle_text = replace_once(gradle_text, 'versionName = "0.1.189"', 'versionName = "0.1.190"', 'versionName')
gradle.write_text(gradle_text, encoding='utf-8')

overlay_text = overlay.read_text(encoding='utf-8')
pattern = re.compile(r'const val PASSED_CLOSE_DELAY_MILLIS\s*=\s*([0-9_]+)L')
matches = list(pattern.finditer(overlay_text))
if len(matches) != 1:
    raise SystemExit(f'Overlay: esperado exatamente 1 PASSED_CLOSE_DELAY_MILLIS, encontrado {len(matches)}')
current_ms = int(matches[0].group(1).replace('_', ''))
if current_ms != 750:
    raise SystemExit(f'Overlay: atraso-base inesperado {current_ms} ms; recusar alteração automática')
overlay_text = pattern.sub('const val PASSED_CLOSE_DELAY_MILLIS = 3_000L', overlay_text, count=1)
overlay.write_text(overlay_text, encoding='utf-8')

service_text = service.read_text(encoding='utf-8')
engine_text = engine.read_text(encoding='utf-8')
required_contracts = {
    'fechamento direcional silencia alvo até saída': 'onDismiss = { directionalAlertEngineChecklist5.dismissUntilExit(visual.targetId) }',
    'alerta salvo legado silencia até saída': 'onDismiss = { proximityAlertEngine.dismissSavedPlaceUntilExit(alert.id) }',
    'motor direcional expõe dismissUntilExit': 'fun dismissUntilExit(',
    'motor mantém estado mutedUntilExit': 'mutedUntilExit',
    'motor libera estado ao sair da zona': 'resetAfterExit',
}
for label, needle in required_contracts.items():
    haystack = engine_text if label.startswith('motor') else service_text
    if needle not in haystack:
        raise SystemExit(f'Contrato existente ausente ({label}): {needle}')

# Teste de regressão deliberadamente estrutural: protege o contrato real já validado
# sem recriar Android WindowManager/Looper em JVM local.
test.write_text(r'''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPopupLifecycle0190ContractTest {
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalAlertOverlayController.kt").readText()
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
    private val directionalEngine = File("src/main/java/br/com/mapeiaia/rotacerta/DirectionalProximityAlertEngine.kt").readText()

    @Test
    fun `popup permanece tres segundos depois que o ponto foi ultrapassado`() {
        assertTrue(overlay.contains("if (visual.shouldClose)"))
        assertTrue(overlay.contains("handler.postDelayed(close, PASSED_CLOSE_DELAY_MILLIS)"))
        assertTrue(overlay.contains("const val PASSED_CLOSE_DELAY_MILLIS = 3_000L"))
        assertFalse(overlay.contains("const val PASSED_CLOSE_DELAY_MILLIS = 750L"))
    }

    @Test
    fun `fechar manualmente silencia radar ou alerta somente na aproximacao atual`() {
        assertTrue(
            service.contains(
                "onDismiss = { directionalAlertEngineChecklist5.dismissUntilExit(visual.targetId) }",
            ),
        )
        assertTrue(directionalEngine.contains("fun dismissUntilExit("))
        assertTrue(directionalEngine.contains("mutedUntilExit"))
        assertTrue(directionalEngine.contains("resetAfterExit"))
        assertTrue(directionalEngine.contains("RESET_BUFFER_METERS"))
    }

    @Test
    fun `alerta salvo do fluxo legado tambem respeita fechar ate sair da zona`() {
        assertTrue(
            service.contains(
                "onDismiss = { proximityAlertEngine.dismissSavedPlaceUntilExit(alert.id) }",
            ),
        )
    }
}
''', encoding='utf-8')

print('alert_popup_lifecycle_0190=applied')
print('auto_close_after_pass_ms=3000')
print('manual_dismiss_until_exit=preserved')
