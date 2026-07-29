from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
GRADLE = ROOT / "app/build.gradle.kts"

service = SERVICE.read_text(encoding="utf-8")

old = '''        val eventPackage = normalizePackageName(event.packageName?.toString())
        val rootPackage = currentRootPackageName()
        val candidatePackage = eventPackage ?: rootPackage
'''
new = '''        val eventPackage = normalizePackageName(event.packageName?.toString())
        val rootPackage = currentRootPackageName()
        val transientOverlayPackages151 = setOf(
            packageName,
            "com.android.systemui",
            "com.samsung.android.app.smartcapture",
        )
        val transientOverlayEvent151 = eventPackage in transientOverlayPackages151 &&
            rootPackage != null && rootPackage != eventPackage
        val candidatePackage = if (transientOverlayEvent151) rootPackage else (eventPackage ?: rootPackage)
'''
if old not in service:
    raise SystemExit("event resolution block not found")
service = service.replace(old, new, 1)

old = '''        val immediateTextChecklist13 = collectImmediateVisibleTextChecklist13()
        val fingerprintChecklist13 = SimpleSavedAppFarolPolicy.screenFingerprint(
            packageName = resolvedPackage,
            text = immediateTextChecklist13,
            windowId = event.windowId,
        )
'''
new = '''        val immediateTextChecklist13 = collectImmediateVisibleTextChecklist13()
        val stableWindowId151 = when {
            transientOverlayEvent151 -> lastStableFarolWindowIdChecklist14 ?: 0
            eventPackage == resolvedPackage || rootPackage == resolvedPackage -> event.windowId
            else -> lastStableFarolWindowIdChecklist14 ?: event.windowId
        }
        if (!transientOverlayEvent151 && (eventPackage == resolvedPackage || rootPackage == resolvedPackage)) {
            lastStableFarolPackageChecklist14 = resolvedPackage
            lastStableFarolWindowIdChecklist14 = event.windowId
        }
        val fingerprintChecklist13 = SimpleSavedAppFarolPolicy.screenFingerprint(
            packageName = resolvedPackage,
            text = immediateTextChecklist13,
            windowId = stableWindowId151,
        )
'''
if old not in service:
    raise SystemExit("fingerprint block not found")
service = service.replace(old, new, 1)

old = '''        val screenChangedChecklist13 = lastImmediateScreenPackageChecklist13 != null &&
            (lastImmediateScreenPackageChecklist13 != resolvedPackage ||
                SimpleSavedAppFarolPolicy.changed(lastImmediateScreenFingerprintChecklist13, fingerprintChecklist13))
'''
new = '''        val screenChangedChecklist13 = !transientOverlayEvent151 &&
            lastImmediateScreenPackageChecklist13 != null &&
            (lastImmediateScreenPackageChecklist13 != resolvedPackage ||
                SimpleSavedAppFarolPolicy.changed(lastImmediateScreenFingerprintChecklist13, fingerprintChecklist13))
'''
if old not in service:
    raise SystemExit("screen change block not found")
service = service.replace(old, new, 1)

old = '''                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                    decisionAge141 in 0L..STABLE_DECISION_ABSENCE_GRACE_MILLIS_141
'''
new = '''                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                    universalForegroundPackageName == resolvedPackage
'''
if old not in service:
    raise SystemExit("stable empty-read preservation block not found")
service = service.replace(old, new, 1)

old = '''            if (preserveStableDecision141) {
'''
new = '''            if (preserveStableDecision141 || transientOverlayEvent151) {
'''
# Replace only the empty-read branch occurrence after BUBBLE_TEXT_EMPTY.
marker = 'UnifiedDebugEventStore.record("BUBBLE_TEXT_EMPTY"'
pos = service.find(marker)
if pos < 0:
    raise SystemExit("empty read marker not found")
head, tail = service[:pos], service[pos:]
if old not in tail:
    raise SystemExit("empty read if block not found")
tail = tail.replace(old, new, 1)
service = head + tail

# Own overlay updates must never invalidate the ride state.
old = '''        if (candidatePackage == this.packageName) {
            if (ownMainActivityEvent) {
                universalForegroundPackageName = this.packageName
                activePackageName = this.packageName
                hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
            }
            return
        }
'''
new = '''        if (candidatePackage == this.packageName) {
            if (ownMainActivityEvent) {
                universalForegroundPackageName = this.packageName
                activePackageName = this.packageName
                hardClearUniversalTwoAddress("Tela do proprio Rota Certa.")
            }
            return
        }
        if (eventPackage == this.packageName && !ownMainActivityEvent) return
'''
if old not in service:
    raise SystemExit("own package guard not found")
service = service.replace(old, new, 1)

SERVICE.write_text(service, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
import re
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.1.151"', gradle, count=1)
gradle = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 5120', gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Applied 99/SystemUI overlay stability fix for Rota Certa 0.1.151")
