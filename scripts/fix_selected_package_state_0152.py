from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
GRADLE = ROOT / "app/build.gradle.kts"

service = SERVICE.read_text(encoding="utf-8")

# Add an explicit package hint to the processing boundary. This is idempotent so
# the script can be reused by GitHub Actions and local builds.
old_signature = '''    private suspend fun processRideText(
        text: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
    ) {
'''
new_signature = '''    private suspend fun processRideText(
        text: String,
        source: TextSource,
        allowPopupCandidate: Boolean = false,
        packageHint152: String? = null,
    ) {
'''
if old_signature in service:
    service = service.replace(old_signature, new_signature, 1)
elif "packageHint152: String? = null" not in service:
    raise SystemExit("processRideText signature not found")

old_resolution = '''        val savedPackagesChecklist13 = SelectedRideAppStore.read(applicationContext)
        val selectedPackageChecklist13 = strictSelectedRootPackageChecklist1()
            ?: normalizePackageName(universalResolvedForegroundPackage())
                ?.takeIf { it in savedPackagesChecklist13 }
'''
new_resolution = '''        val savedPackagesChecklist13 = SelectedRideAppStore.read(applicationContext)
        val selectedPackageChecklist13 = normalizePackageName(packageHint152)
            ?.takeIf { it in savedPackagesChecklist13 }
            ?: universalActiveRidePackageName?.takeIf { it in savedPackagesChecklist13 }
            ?: normalizePackageName(universalForegroundPackageName)?.takeIf { it in savedPackagesChecklist13 }
            ?: strictSelectedRootPackageChecklist1()
            ?: normalizePackageName(universalResolvedForegroundPackage())
                ?.takeIf { it in savedPackagesChecklist13 }
'''
if old_resolution in service:
    service = service.replace(old_resolution, new_resolution, 1)
elif "normalizePackageName(packageHint152)" not in service:
    raise SystemExit("selected package resolution block not found")

# The event handler already resolved the correct selected package. Pass it into
# the coroutine instead of allowing a later launcher/System UI root to replace it.
event_old = '''                processRideText(immediateTextChecklist13, TextSource.Accessibility, allowPopupCandidate = true)
'''
event_new = '''                processRideText(
                    immediateTextChecklist13,
                    TextSource.Accessibility,
                    allowPopupCandidate = true,
                    packageHint152 = resolvedPackage,
                )
'''
if event_old in service:
    service = service.replace(event_old, event_new, 1)
elif "packageHint152 = resolvedPackage" not in service:
    raise SystemExit("event processRideText call not found")

# Confirmation job knows its package too.
confirmation_old = '''                processRideText(confirmedTextChecklist14, TextSource.Accessibility, allowPopupCandidate = true)
'''
confirmation_new = '''                processRideText(
                    confirmedTextChecklist14,
                    TextSource.Accessibility,
                    allowPopupCandidate = true,
                    packageHint152 = packageName,
                )
'''
if confirmation_old in service:
    service = service.replace(confirmation_old, confirmation_new, 1)

# Continuous scanning must use the package captured before text collection. This
# closes the race where the Samsung launcher becomes root between collection and
# processing.
continuous_old = '''                                processRideText(
                                    visibleText,
                                    TextSource.Accessibility,
                                    allowPopupCandidate = true,
                                ) // global_continuous_empty_clear_0_1_124
'''
continuous_new = '''                                processRideText(
                                    visibleText,
                                    TextSource.Accessibility,
                                    allowPopupCandidate = true,
                                    packageHint152 = expectedPackage,
                                ) // global_continuous_empty_clear_0_1_124
'''
if continuous_old in service:
    service = service.replace(continuous_old, continuous_new, 1)
elif "packageHint152 = expectedPackage" not in service:
    raise SystemExit("continuous processRideText call not found")

# Preserve a validated decision while the same selected app remains foreground.
old_stable = '''            val preserveStableDecision141 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                    decisionAge141 in 0L..STABLE_DECISION_ABSENCE_GRACE_MILLIS_141
'''
new_stable = '''            val preserveStableDecision141 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                    universalForegroundPackageName == selectedPackageChecklist13
'''
if old_stable in service:
    service = service.replace(old_stable, new_stable, 1)

old_recent = '''            val preserveRecentValidatedCard144 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    decisionAge141 in 0L..8_000L
'''
new_recent = '''            val preserveRecentValidatedCard144 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    universalForegroundPackageName == selectedPackageChecklist13
'''
if old_recent in service:
    service = service.replace(old_recent, new_recent, 1)

SERVICE.write_text(service, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.1.153"', gradle, count=1)
gradle = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 5140', gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Applied complete selected-package propagation and stability fix for 0.1.153")
