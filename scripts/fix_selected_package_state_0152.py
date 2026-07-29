from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
GRADLE = ROOT / "app/build.gradle.kts"

service = SERVICE.read_text(encoding="utf-8")

# Carry the package already resolved from the accessibility event into processing.
old = '''    private suspend fun processRideText(
        text: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
    ) {
'''
new = '''    private suspend fun processRideText(
        text: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
        packageHint152: String? = null,
    ) {
'''
if old not in service:
    raise SystemExit("processRideText signature not found")
service = service.replace(old, new, 1)

old = '''        val savedPackagesChecklist13 = SelectedRideAppStore.read(applicationContext)
        val selectedPackageChecklist13 = strictSelectedRootPackageChecklist1()
            ?: normalizePackageName(universalResolvedForegroundPackage())
                ?.takeIf { it in savedPackagesChecklist13 }
'''
new = '''        val savedPackagesChecklist13 = SelectedRideAppStore.read(applicationContext)
        val selectedPackageChecklist13 = normalizePackageName(packageHint152)
            ?.takeIf { it in savedPackagesChecklist13 }
            ?: universalActiveRidePackageName?.takeIf { it in savedPackagesChecklist13 }
            ?: normalizePackageName(universalForegroundPackageName)?.takeIf { it in savedPackagesChecklist13 }
            ?: strictSelectedRootPackageChecklist1()
            ?: normalizePackageName(universalResolvedForegroundPackage())
                ?.takeIf { it in savedPackagesChecklist13 }
'''
if old not in service:
    raise SystemExit("selected package resolution block not found")
service = service.replace(old, new, 1)

# Event-driven analysis must not fall back to a stale launcher root.
old = '''                processRideText(immediateTextChecklist13, TextSource.Accessibility, allowPopupCandidate = true)
'''
new = '''                processRideText(
                    immediateTextChecklist13,
                    TextSource.Accessibility,
                    allowPopupCandidate = true,
                    packageHint152 = resolvedPackage,
                )
'''
if old not in service:
    raise SystemExit("event processRideText call not found")
service = service.replace(old, new, 1)

# Confirmation job also knows the exact selected package.
old = '''                processRideText(confirmedTextChecklist14, TextSource.Accessibility, allowPopupCandidate = true)
'''
new = '''                processRideText(
                    confirmedTextChecklist14,
                    TextSource.Accessibility,
                    allowPopupCandidate = true,
                    packageHint152 = packageName,
                )
'''
if old in service:
    service = service.replace(old, new, 1)

# A validated green/red decision survives empty accessibility/OCR reads while the
# same selected package remains foreground. Only a confirmed new destination or
# a real package change may replace it.
old = '''            val preserveStableDecision141 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                    decisionAge141 in 0L..STABLE_DECISION_ABSENCE_GRACE_MILLIS_141
'''
new = '''            val preserveStableDecision141 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                    universalForegroundPackageName == selectedPackageChecklist13
'''
if old not in service:
    raise SystemExit("stable decision preservation block not found")
service = service.replace(old, new, 1)

old = '''            val preserveRecentValidatedCard144 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    decisionAge141 in 0L..8_000L
'''
new = '''            val preserveRecentValidatedCard144 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    universalForegroundPackageName == selectedPackageChecklist13
'''
if old not in service:
    raise SystemExit("recent validated card preservation block not found")
service = service.replace(old, new, 1)

SERVICE.write_text(service, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.1.152"', gradle, count=1)
gradle = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 5130', gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Applied selected-package propagation and persistent decision fix for 0.1.152")
