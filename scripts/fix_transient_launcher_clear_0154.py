from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
GRADLE = ROOT / "app/build.gradle.kts"

service = SERVICE.read_text(encoding="utf-8")

old = '''        if (resolvedPackage !in savedPackages || !shouldScanPackage(resolvedPackage)) {
            UnifiedDebugEventStore.record(
                "BUBBLE_PACKAGE_BLOCKED",
                resolvedPackage,
                "selecionado=${resolvedPackage in savedPackages}; shouldScan=${shouldScanPackage(resolvedPackage)}; motivo=${scanBlockReason(resolvedPackage)}",
            )
            universalForegroundPackageName = resolvedPackage
            activePackageName = resolvedPackage
            lastExternalWindowPackageName = resolvedPackage
            lastImmediateScreenFingerprintChecklist13 = null
            lastImmediateScreenPackageChecklist13 = null
            hardClearUniversalTwoAddress(scanBlockReason(resolvedPackage))
            return
        }
'''
new = '''        if (resolvedPackage !in savedPackages || !shouldScanPackage(resolvedPackage)) {
            val now154 = System.currentTimeMillis()
            val stableSelectedPackage154 = universalActiveRidePackageName
                ?.takeIf { it in savedPackages }
                ?: recentSelectedRidePackageChecklist11?.takeIf { it in savedPackages }
            val transientOverlayOrLauncher154 =
                stableSelectedPackage154 != null &&
                    universalActiveAddressSignature != null &&
                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                    now154 - recentSelectedRidePackageAtMillisChecklist11 in 0L..2_000L &&
                    (eventPackage == null ||
                        eventPackage == this.packageName ||
                        eventPackage == "com.android.systemui" ||
                        eventPackage == "com.samsung.android.app.smartcapture" ||
                        resolvedPackage.contains("launcher", ignoreCase = true))
            if (transientOverlayOrLauncher154) {
                UnifiedDebugEventStore.record(
                    "BUBBLE_PASSIVE_TRANSITION_DEFERRED",
                    stableSelectedPackage154,
                    "evento transitório=$eventPackage; raiz=$rootPackage; resolvido=$resolvedPackage; decisão válida preservada",
                )
                universalForegroundPackageName = stableSelectedPackage154
                activePackageName = stableSelectedPackage154
                lastExternalWindowPackageName = stableSelectedPackage154
                return
            }
            UnifiedDebugEventStore.record(
                "BUBBLE_PACKAGE_BLOCKED",
                resolvedPackage,
                "selecionado=${resolvedPackage in savedPackages}; shouldScan=${shouldScanPackage(resolvedPackage)}; motivo=${scanBlockReason(resolvedPackage)}",
            )
            universalForegroundPackageName = resolvedPackage
            activePackageName = resolvedPackage
            lastExternalWindowPackageName = resolvedPackage
            lastImmediateScreenFingerprintChecklist13 = null
            lastImmediateScreenPackageChecklist13 = null
            hardClearUniversalTwoAddress(scanBlockReason(resolvedPackage))
            return
        }
'''
if old not in service:
    raise SystemExit("blocked package branch not found")
service = service.replace(old, new, 1)
SERVICE.write_text(service, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.1.154"', gradle, count=1)
gradle = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 5150', gradle, count=1)
GRADLE.write_text(gradle, encoding="utf-8")

print("Applied transient launcher/System UI decision preservation fix for 0.1.154")
