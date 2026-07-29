from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
GRADLE = ROOT / "app/build.gradle.kts"

service = SERVICE.read_text(encoding="utf-8")

# Quando a acessibilidade já confirmou dois endereços, o OCR agendado deixa de ser
# necessário. Cancelá-lo antes da chamada de rota evita disputa de CPU, bitmap e
# memória justamente no caminho crítico do farol, sobretudo em aparelhos fracos.
anchor = '        UnifiedDebugEventStore.record("BUBBLE_ROUTE_REQUESTED", selectedPackageChecklist13, "destino=${fieldsChecklist13.destination.orEmpty()}; alvos=${targetsChecklist13.destinations.size}; generation=$generationChecklist13")\n'
insert = '''        screenshotFallbackJob127?.cancel()\n        screenshotFallbackJob127 = null\n        lastAccessibilityAcceptedAtMillis127 = System.currentTimeMillis()\n        // accessibility_card_cancels_ocr_0_1_157\n        UnifiedDebugEventStore.record("BUBBLE_ROUTE_REQUESTED", selectedPackageChecklist13, "destino=${fieldsChecklist13.destination.orEmpty()}; alvos=${targetsChecklist13.destinations.size}; generation=$generationChecklist13")\n'''
if anchor in service:
    service = service.replace(anchor, insert, 1)
elif "accessibility_card_cancels_ocr_0_1_157" not in service:
    raise SystemExit("route request anchor not found")

# Não redesenha amarelo se ele já está na tela. Isso remove uma invalidação visual
# do caminho de decisão sem alterar o contrato de cores.
old_wait = '''        rememberBubbleReason("universal_waiting", "Dois enderecos identificados; calculando o ultimo destino.")\n        showOverlay(RadarColor.Default, distanceKm = null)\n'''
new_wait = '''        rememberBubbleReason("universal_waiting", "Dois enderecos identificados; calculando o ultimo destino.")\n        if (currentRadarColor != RadarColor.Default || currentDistanceKm != null) {\n            showOverlay(RadarColor.Default, distanceKm = null)\n        } // waiting_render_noop_0_1_157\n'''
if old_wait in service:
    service = service.replace(old_wait, new_wait, 1)
elif "waiting_render_noop_0_1_157" not in service:
    raise SystemExit("waiting render anchor not found")

SERVICE.write_text(service, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.1.157"', gradle, count=1)
gradle = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 5180', gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Applied subsecond farol critical-path optimization for 0.1.157")
