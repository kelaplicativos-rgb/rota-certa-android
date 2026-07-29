from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
GRADLE = ROOT / "app/build.gradle.kts"

service = SERVICE.read_text(encoding="utf-8")

# A valid decision may survive short rendering gaps, but it must not remain for
# tens of seconds after the card disappears. Five seconds covers slow devices
# and Flutter/Compose transitions without leaving stale green/red indefinitely.

def add_bound_after_foreground(source: str, foreground_expr: str) -> tuple[str, int]:
    pattern = re.compile(
        r'(\(currentRadarColor == RadarColor\.Green \|\| currentRadarColor == RadarColor\.Red\)\s*&&\s*'
        + re.escape(foreground_expr)
        + r')(?!\s*&&\s*decisionAge141 in 0L\.\.5_000L)'
    )
    return pattern.subn(r'\1 &&\n                    decisionAge141 in 0L..5_000L', source, count=1)

service, event_count = add_bound_after_foreground(
    service,
    "universalForegroundPackageName == resolvedPackage",
)
if event_count == 0 and not re.search(
    r'universalForegroundPackageName == resolvedPackage\s*&&\s*decisionAge141 in 0L\.\.5_000L',
    service,
):
    raise SystemExit("event empty-read preservation block not found")

service, process_count = add_bound_after_foreground(
    service,
    "universalForegroundPackageName == selectedPackageChecklist13",
)
if process_count == 0 and not re.search(
    r'universalForegroundPackageName == selectedPackageChecklist13\s*&&\s*decisionAge141 in 0L\.\.5_000L',
    service,
):
    raise SystemExit("process invalid-read preservation block not found")

recent_pattern = re.compile(
    r'(val preserveRecentValidatedCard144\s*=\s*'
    r'universalActiveRidePackageName == selectedPackageChecklist13\s*&&\s*'
    r'universalActiveAddressSignature != null\s*&&\s*'
    r'universalForegroundPackageName == selectedPackageChecklist13)'
    r'(?!\s*&&\s*decisionAge141 in 0L\.\.5_000L)'
)
service, _ = recent_pattern.subn(
    r'\1 &&\n                    decisionAge141 in 0L..5_000L',
    service,
    count=1,
)

SERVICE.write_text(service, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.1.155"', gradle, count=1)
gradle = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 5160', gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Applied bounded empty-read preservation for Rota Certa 0.1.155")
