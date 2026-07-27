from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt'
GRADLE = ROOT / 'app/build.gradle.kts'

# Materializa toda a sequência anterior quando a branch ainda guarda a fonte-base.
gradle_before = GRADLE.read_text(encoding='utf-8')
if 'versionName = "0.1.141"' in gradle_before or 'versionName = "0.1.142"' in gradle_before:
    subprocess.run(['python', str(ROOT / 'scripts/apply_farol_single_flight_0143.py')], check=True)

service = SERVICE.read_text(encoding='utf-8')

# Um OCR válido deve permanecer autoritativo durante a janela de cálculo,
# mesmo quando a rota termina cedo por falta de chave ou chega um evento parcial.
old_invalid = '''            val preserveRouteInFlight143 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    universalRouteJob?.isActive == true &&
                    decisionAge141 in 0L..8_000L
            if (preserveStableDecision141 || preserveRouteInFlight143) {
'''
new_invalid = '''            val preserveRouteInFlight143 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    universalRouteJob?.isActive == true &&
                    decisionAge141 in 0L..8_000L
            val preserveRecentValidatedCard144 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    decisionAge141 in 0L..8_000L
            if (preserveStableDecision141 || preserveRouteInFlight143 || preserveRecentValidatedCard144) {
'''
if old_invalid not in service:
    raise SystemExit('invalid-read authority block not found')
service = service.replace(old_invalid, new_invalid, 1)
service = service.replace(
    '"fonte=${source.name}; decisao/rota em andamento preservada; idade=${decisionAge141}ms; rotaAtiva=${universalRouteJob?.isActive == true}",',
    '"fonte=${source.name}; OCR/card validado preservado; idade=${decisionAge141}ms; rotaAtiva=${universalRouteJob?.isActive == true}",',
    1,
)

# Alterações de windowId/fingerprint produzidas por Smart Capture, SystemUI ou
# overlays não podem apagar um card recentemente validado.
old_screen = '''            val preserveRouteInFlight143 =
                universalActiveRidePackageName == resolvedPackage &&
                    universalActiveAddressSignature != null &&
                    universalRouteJob?.isActive == true
            if (preserveStableDecision141 || preserveRouteInFlight143) {
'''
new_screen = '''            val preserveRouteInFlight143 =
                universalActiveRidePackageName == resolvedPackage &&
                    universalActiveAddressSignature != null &&
                    universalRouteJob?.isActive == true
            val recentValidatedCardAge144 = System.currentTimeMillis() - universalLastActiveReadAtMillis
            val preserveRecentValidatedCard144 =
                universalActiveRidePackageName == resolvedPackage &&
                    universalActiveAddressSignature != null &&
                    recentValidatedCardAge144 in 0L..8_000L
            if (preserveStableDecision141 || preserveRouteInFlight143 || preserveRecentValidatedCard144) {
'''
if old_screen not in service:
    raise SystemExit('screen-change authority block not found')
service = service.replace(old_screen, new_screen, 1)
service = service.replace(
    '"decisao/rota em andamento preservada; OCR confirmara mudanca real do destino",',
    '"OCR/card recente preservado; apenas novo destino confirmado pode substituir o estado",',
    1,
)

SERVICE.write_text(service, encoding='utf-8')

gradle = GRADLE.read_text(encoding='utf-8')
if 'versionName = "0.1.143"' not in gradle:
    raise SystemExit('expected 0.1.143 version not found')
gradle = gradle.replace('versionName = "0.1.143"', 'versionName = "0.1.144"', 1)
if 'versionCode = 5040' in gradle:
    gradle = gradle.replace('versionCode = 5040', 'versionCode = 5050', 1)
else:
    gradle = gradle.replace('versionCode = appVersionCode', 'versionCode = 5050', 1)
GRADLE.write_text(gradle, encoding='utf-8')

print('Applied OCR authority and partial accessibility protection 0.1.144')
