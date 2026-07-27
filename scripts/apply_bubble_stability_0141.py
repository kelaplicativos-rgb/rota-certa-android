from pathlib import Path

SERVICE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt')
GRADLE = Path('app/build.gradle.kts')

source = SERVICE.read_text(encoding='utf-8')

old_screen = '''        if (screenChangedChecklist13) {
            UnifiedDebugEventStore.record("BUBBLE_SCREEN_CHANGED", resolvedPackage, "fingerprintAnterior=$lastImmediateScreenFingerprintChecklist13; fingerprintAtual=$fingerprintChecklist13; window=${event.windowId}")
            hardClearUniversalTwoAddress(
                reason = "A tela mudou; cor e quilometros anteriores removidos imediatamente.",
                keepWaitingYellow = true,
            ) // immediate_screen_change_clear_checklist_13
            universalForegroundPackageName = resolvedPackage
            activePackageName = resolvedPackage
            lastExternalWindowPackageName = resolvedPackage
        }
'''
new_screen = '''        if (screenChangedChecklist13) {
            UnifiedDebugEventStore.record("BUBBLE_SCREEN_CHANGED", resolvedPackage, "fingerprintAnterior=$lastImmediateScreenFingerprintChecklist13; fingerprintAtual=$fingerprintChecklist13; window=${event.windowId}")
            val preserveStableDecision141 =
                universalActiveRidePackageName == resolvedPackage &&
                    universalActiveAddressSignature != null &&
                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red)
            if (preserveStableDecision141) {
                UnifiedDebugEventStore.record(
                    "BUBBLE_SCREEN_CHANGE_DEFERRED",
                    resolvedPackage,
                    "decisao valida preservada; OCR confirmara mudanca real do destino",
                )
                scheduleScreenshotFallback127(resolvedPackage)
            } else {
                hardClearUniversalTwoAddress(
                    reason = "A tela mudou; cor e quilometros anteriores removidos imediatamente.",
                    keepWaitingYellow = true,
                )
            } // stable_decision_survives_visual_noise_0_1_141
            universalForegroundPackageName = resolvedPackage
            activePackageName = resolvedPackage
            lastExternalWindowPackageName = resolvedPackage
        }
'''
if old_screen not in source:
    raise SystemExit('screen-change block not found')
source = source.replace(old_screen, new_screen, 1)

old_blank = '''        if (immediateTextChecklist13.isBlank()) {
            UnifiedDebugEventStore.record("BUBBLE_TEXT_EMPTY", resolvedPackage, "coleta imediata vazia; OCR fallback agendado")
            hardClearUniversalTwoAddress(
                reason = "Tela alterada sem dois enderecos visiveis; resultado removido imediatamente.",
                keepWaitingYellow = true,
            )
            scheduleScreenshotFallback127(resolvedPackage)
            return
        }
'''
new_blank = '''        if (immediateTextChecklist13.isBlank()) {
            UnifiedDebugEventStore.record("BUBBLE_TEXT_EMPTY", resolvedPackage, "coleta imediata vazia; OCR fallback agendado")
            val decisionAge141 = System.currentTimeMillis() - universalLastActiveReadAtMillis
            val preserveStableDecision141 =
                universalActiveRidePackageName == resolvedPackage &&
                    universalActiveAddressSignature != null &&
                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                    decisionAge141 in 0L..STABLE_DECISION_ABSENCE_GRACE_MILLIS_141
            if (preserveStableDecision141) {
                UnifiedDebugEventStore.record(
                    "BUBBLE_EMPTY_READ_DEFERRED",
                    resolvedPackage,
                    "decisao valida preservada; idade=${decisionAge141}ms",
                )
            } else {
                hardClearUniversalTwoAddress(
                    reason = "Tela alterada sem dois enderecos visiveis; resultado removido apos confirmacao.",
                    keepWaitingYellow = true,
                )
            }
            scheduleScreenshotFallback127(resolvedPackage)
            return
        }
'''
if old_blank not in source:
    raise SystemExit('blank block not found')
source = source.replace(old_blank, new_blank, 1)

old_inactive = '''        if (!evaluationChecklist13.active) {
            hardClearUniversalTwoAddress(
                reason = "Tela sem dois enderecos validos; cor e quilometros removidos imediatamente.",
                keepWaitingYellow = true,
            ) // simple_two_address_clear_checklist_13
            return
        }
'''
new_inactive = '''        if (!evaluationChecklist13.active) {
            val decisionAge141 = System.currentTimeMillis() - universalLastActiveReadAtMillis
            val preserveStableDecision141 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                    decisionAge141 in 0L..STABLE_DECISION_ABSENCE_GRACE_MILLIS_141
            if (preserveStableDecision141) {
                UnifiedDebugEventStore.record(
                    "BUBBLE_INVALID_READ_DEFERRED",
                    selectedPackageChecklist13,
                    "fonte=${source.name}; decisao valida preservada; idade=${decisionAge141}ms",
                )
                if (source == TextSource.Accessibility) scheduleScreenshotFallback127(selectedPackageChecklist13)
                return
            }
            hardClearUniversalTwoAddress(
                reason = "Tela sem dois enderecos validos por tempo suficiente; cor e quilometros removidos.",
                keepWaitingYellow = true,
            ) // confirmed_absence_clear_0_1_141
            return
        }
'''
if old_inactive not in source:
    raise SystemExit('inactive block not found')
source = source.replace(old_inactive, new_inactive, 1)

marker = '    private var lastVisibleCardSignature: String? = null\n'
if marker not in source:
    raise SystemExit('field marker not found')
source = source.replace(marker, marker + '    private val STABLE_DECISION_ABSENCE_GRACE_MILLIS_141 = 3_000L\n', 1)
SERVICE.write_text(source, encoding='utf-8')

gradle = GRADLE.read_text(encoding='utf-8')
if 'versionName = "0.1.140"' not in gradle:
    raise SystemExit('expected 0.1.140 version not found')
gradle = gradle.replace('versionName = "0.1.140"', 'versionName = "0.1.141"', 1)
old_version_logic = '''val ciVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 5_000 + it }
val appVersionCode = ciVersionCode ?: 5_001
'''
new_version_logic = '''val minimumVersionCode = 5_020
val ciVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { maxOf(minimumVersionCode, 5_000 + it) }
val appVersionCode = ciVersionCode ?: minimumVersionCode
'''
if old_version_logic not in gradle:
    raise SystemExit('versionCode calculation block not found')
gradle = gradle.replace(old_version_logic, new_version_logic, 1)
GRADLE.write_text(gradle, encoding='utf-8')

print('Applied bubble stability fix 0.1.141 with minimum versionCode 5020')
