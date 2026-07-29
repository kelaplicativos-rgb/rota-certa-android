from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
GRADLE = ROOT / "app/build.gradle.kts"

service = SERVICE.read_text(encoding="utf-8")

# A valid decision may survive short rendering gaps, but it must not remain for
# tens of seconds after the card disappears. Five seconds covers slow devices
# and Flutter/Compose transitions without leaving stale green/red indefinitely.
old_event = '''                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                     universalForegroundPackageName == resolvedPackage
'''
new_event = '''                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                     universalForegroundPackageName == resolvedPackage &&
                     decisionAge141 in 0L..5_000L
'''
if old_event not in service:
    raise SystemExit("event empty-read preservation block not found")
service = service.replace(old_event, new_event, 1)

old_process = '''                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                     universalForegroundPackageName == selectedPackageChecklist13
'''
new_process = '''                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                     universalForegroundPackageName == selectedPackageChecklist13 &&
                     decisionAge141 in 0L..5_000L
'''
if old_process not in service:
    raise SystemExit("process invalid-read preservation block not found")
service = service.replace(old_process, new_process, 1)

old_recent = '''            val preserveRecentValidatedCard144 =
                 universalActiveRidePackageName == selectedPackageChecklist13 &&
                     universalActiveAddressSignature != null &&
                     universalForegroundPackageName == selectedPackageChecklist13
'''
new_recent = '''            val preserveRecentValidatedCard144 =
                 universalActiveRidePackageName == selectedPackageChecklist13 &&
                     universalActiveAddressSignature != null &&
                     universalForegroundPackageName == selectedPackageChecklist13 &&
                     decisionAge141 in 0L..5_000L
'''
if old_recent in service:
    service = service.replace(old_recent, new_recent, 1)

SERVICE.write_text(service, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.1.155"', gradle, count=1)
gradle = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 5160', gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Applied bounded empty-read preservation for Rota Certa 0.1.155")
