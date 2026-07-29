from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
GRADLE = ROOT / "app/build.gradle.kts"

service = SERVICE.read_text(encoding="utf-8")

# A stale launcher event can arrive while the selected ride app is already the
# actual root window. Prefer the selected root in that case. This is generic and
# works with Samsung, Motorola, Xiaomi, Oppo, Realme and other launchers.
old_candidate = '''        val candidatePackage = if (transientOverlayEvent151) rootPackage else (eventPackage ?: rootPackage)
'''
new_candidate = '''        val selectedPackages156 = SelectedRideAppStore.read(applicationContext)
        val staleLauncherEvent156 =
            eventPackage?.contains("launcher", ignoreCase = true) == true &&
                rootPackage != null &&
                rootPackage in selectedPackages156
        val candidatePackage = when {
            transientOverlayEvent151 -> rootPackage
            staleLauncherEvent156 -> rootPackage
            else -> eventPackage ?: rootPackage
        } // selected_root_beats_stale_launcher_0_1_156
'''
if old_candidate in service:
    service = service.replace(old_candidate, new_candidate, 1)
elif "selected_root_beats_stale_launcher_0_1_156" not in service:
    raise SystemExit("candidate package block not found")

# Avoid hundreds of identical clear operations while the app is already idle.
# The service still clears immediately on the first real transition, but repeated
# passive ticks become no-ops, saving CPU, memory pressure and UI invalidations.
pattern = re.compile(r'(\n\s*private fun hardClearUniversalTwoAddress\([^\)]*\)\s*\{\n)')
match = pattern.search(service)
if match and "passive_clear_noop_0_1_156" not in service:
    guard = '''        val passiveClear156 = reason.contains("Pacote passivo", ignoreCase = true) ||
            reason.contains("Aplicativo não selecionado", ignoreCase = true)
        if (passiveClear156 &&
            currentRadarColor == RadarColor.Idle &&
            currentDistanceKm == null &&
            universalActiveAddressSignature == null
        ) {
            return // passive_clear_noop_0_1_156
        }
'''
    service = service[:match.end()] + guard + service[match.end():]
elif "passive_clear_noop_0_1_156" not in service:
    raise SystemExit("hardClearUniversalTwoAddress function not found")

SERVICE.write_text(service, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.1.156"', gradle, count=1)
gradle = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 5170', gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Applied passive churn reduction and stale launcher race fix for 0.1.156")
