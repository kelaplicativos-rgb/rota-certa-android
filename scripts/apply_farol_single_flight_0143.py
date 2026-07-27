from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt'
GRADLE = ROOT / 'app/build.gradle.kts'

# Materializa a sequência anterior quando necessário.
gradle_before = GRADLE.read_text(encoding='utf-8')
if 'versionName = "0.1.141"' in gradle_before:
    subprocess.run(['python', str(ROOT / 'scripts/apply_unified_manual_report_0142.py')], check=True)

service = SERVICE.read_text(encoding='utf-8')

fields_anchor = '''    private var partialReadConfirmationJobChecklist14: Job? = null
    private val accessibilityEventFloodGate = AccessibilityEventFloodGate()
'''
fields_new = '''    private var partialReadConfirmationJobChecklist14: Job? = null
    private var activeAnalysisPackage143: String? = null
    private var activeAnalysisHash143: Int? = null
    private var activeAnalysisStartedAt143: Long = 0L
    private val accessibilityEventFloodGate = AccessibilityEventFloodGate()
'''
if fields_anchor not in service:
    raise SystemExit('single-flight fields anchor not found')
service = service.replace(fields_anchor, fields_new, 1)

old_schedule = '''        val quickEvaluationChecklist13 = SimpleSavedAppFarolPolicy.evaluate(
            packageName = resolvedPackage,
            savedPackages = savedPackages,
            text = immediateTextChecklist13,
        )
        if (analyzeJob?.isActive == true) {
            UnifiedDebugEventStore.record("BUBBLE_ANALYSIS_CANCELLED", resolvedPackage, "análise anterior cancelada por evento mais recente")
        }
        analyzeJob?.cancel()
        UnifiedDebugEventStore.record("BUBBLE_ANALYSIS_STARTED", resolvedPackage, "fonte=Accessibility; tamanho=${immediateTextChecklist13.length}; hash=${immediateTextChecklist13.hashCode()}")
        analyzeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            processRideText(immediateTextChecklist13, TextSource.Accessibility, allowPopupCandidate = true)
        } // immediate_accessibility_process_checklist_13
'''
new_schedule = '''        val quickEvaluationChecklist13 = SimpleSavedAppFarolPolicy.evaluate(
            packageName = resolvedPackage,
            savedPackages = savedPackages,
            text = immediateTextChecklist13,
        )
        val analysisHash143 = immediateTextChecklist13.hashCode()
        val sameAnalysisInFlight143 = analyzeJob?.isActive == true &&
            activeAnalysisPackage143 == resolvedPackage &&
            activeAnalysisHash143 == analysisHash143
        if (sameAnalysisInFlight143) {
            UnifiedDebugEventStore.record(
                "BUBBLE_DUPLICATE_EVENT_IGNORED",
                resolvedPackage,
                "mesmo texto já está em análise; hash=$analysisHash143; idade=${System.currentTimeMillis() - activeAnalysisStartedAt143}ms",
            )
            if (quickEvaluationChecklist13.active) {
                screenshotFallbackJob127?.cancel()
                screenshotFallbackJob127 = null
            }
            return
        }
        if (analyzeJob?.isActive == true) {
            UnifiedDebugEventStore.record(
                "BUBBLE_ANALYSIS_REPLACED",
                resolvedPackage,
                "conteúdo realmente mudou; hashAnterior=${activeAnalysisHash143 ?: 0}; hashAtual=$analysisHash143",
            )
            analyzeJob?.cancel()
        }
        activeAnalysisPackage143 = resolvedPackage
        activeAnalysisHash143 = analysisHash143
        activeAnalysisStartedAt143 = System.currentTimeMillis()
        UnifiedDebugEventStore.record("BUBBLE_ANALYSIS_STARTED", resolvedPackage, "fonte=Accessibility; tamanho=${immediateTextChecklist13.length}; hash=$analysisHash143")
        analyzeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                processRideText(immediateTextChecklist13, TextSource.Accessibility, allowPopupCandidate = true)
            } finally {
                if (activeAnalysisPackage143 == resolvedPackage && activeAnalysisHash143 == analysisHash143) {
                    activeAnalysisPackage143 = null
                    activeAnalysisHash143 = null
                    activeAnalysisStartedAt143 = 0L
                }
            }
        } // single_flight_accessibility_analysis_0_1_143
'''
if old_schedule not in service:
    raise SystemExit('analysis scheduling block not found')
service = service.replace(old_schedule, new_schedule, 1)

old_invalid = '''            val preserveStableDecision141 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                    decisionAge141 in 0L..STABLE_DECISION_ABSENCE_GRACE_MILLIS_141
            if (preserveStableDecision141) {
'''
new_invalid = '''            val preserveStableDecision141 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
                    decisionAge141 in 0L..STABLE_DECISION_ABSENCE_GRACE_MILLIS_141
            val preserveRouteInFlight143 =
                universalActiveRidePackageName == selectedPackageChecklist13 &&
                    universalActiveAddressSignature != null &&
                    universalRouteJob?.isActive == true &&
                    decisionAge141 in 0L..8_000L
            if (preserveStableDecision141 || preserveRouteInFlight143) {
'''
if old_invalid not in service:
    raise SystemExit('invalid-read preservation block not found')
service = service.replace(old_invalid, new_invalid, 1)
service = service.replace(
    '"fonte=${source.name}; decisao valida preservada; idade=${decisionAge141}ms",',
    '"fonte=${source.name}; decisao/rota em andamento preservada; idade=${decisionAge141}ms; rotaAtiva=${universalRouteJob?.isActive == true}",',
    1,
)

old_screen = '''            val preserveStableDecision141 =
                universalActiveRidePackageName == resolvedPackage &&
                    universalActiveAddressSignature != null &&
                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red)
            if (preserveStableDecision141) {
'''
new_screen = '''            val preserveStableDecision141 =
                universalActiveRidePackageName == resolvedPackage &&
                    universalActiveAddressSignature != null &&
                    (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red)
            val preserveRouteInFlight143 =
                universalActiveRidePackageName == resolvedPackage &&
                    universalActiveAddressSignature != null &&
                    universalRouteJob?.isActive == true
            if (preserveStableDecision141 || preserveRouteInFlight143) {
'''
if old_screen not in service:
    raise SystemExit('screen-change preservation block not found')
service = service.replace(old_screen, new_screen, 1)
service = service.replace(
    '"decisao valida preservada; OCR confirmara mudanca real do destino",',
    '"decisao/rota em andamento preservada; OCR confirmara mudanca real do destino",',
    1,
)

SERVICE.write_text(service, encoding='utf-8')

gradle = GRADLE.read_text(encoding='utf-8')
if 'versionName = "0.1.142"' not in gradle:
    raise SystemExit('expected 0.1.142 version not found')
gradle = gradle.replace('versionName = "0.1.142"', 'versionName = "0.1.143"', 1)
if 'versionCode = 5030' in gradle:
    gradle = gradle.replace('versionCode = 5030', 'versionCode = 5040', 1)
else:
    gradle = gradle.replace('versionCode = appVersionCode', 'versionCode = 5040', 1)
GRADLE.write_text(gradle, encoding='utf-8')

print('Applied farol single-flight and route protection 0.1.143')
