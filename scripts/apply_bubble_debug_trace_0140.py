from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
GRADLE = ROOT / "app/build.gradle.kts"
TEST = ROOT / "app/src/test/java/br/com/mapeiaia/rotacerta/BubbleDebugTrace140Test.kt"


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Substituição insegura em {path}: esperado 1, encontrado {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# Registra por que um evento foi descartado antes de chegar à leitura.
replace_once(
    SERVICE,
    "        if (!AccessibilityEventFloodGate.isRelevantEventType(event.eventType)) return\n",
    "        if (!AccessibilityEventFloodGate.isRelevantEventType(event.eventType)) {\n"
    "            UnifiedDebugEventStore.record(\"BUBBLE_EVENT_IGNORED\", event.packageName?.toString(), \"motivo=tipo_irrelevante; type=${event.eventType}; window=${event.windowId}\")\n"
    "            return\n"
    "        }\n",
)

replace_once(
    SERVICE,
    "        val candidatePackage = eventPackage ?: rootPackage\n",
    "        val candidatePackage = eventPackage ?: rootPackage\n"
    "        UnifiedDebugEventStore.record(\n"
    "            \"BUBBLE_EVENT_RESOLVED\",\n"
    "            candidatePackage,\n"
    "            \"eventPackage=${eventPackage ?: \"nao informado\"}; rootPackage=${rootPackage ?: \"nao informado\"}; window=${event.windowId}\",\n"
    "        )\n",
)

replace_once(
    SERVICE,
    "        if (resolvedPackage !in savedPackages || !shouldScanPackage(resolvedPackage)) {\n",
    "        if (resolvedPackage !in savedPackages || !shouldScanPackage(resolvedPackage)) {\n"
    "            UnifiedDebugEventStore.record(\n"
    "                \"BUBBLE_PACKAGE_BLOCKED\",\n"
    "                resolvedPackage,\n"
    "                \"selecionado=${resolvedPackage in savedPackages}; shouldScan=${shouldScanPackage(resolvedPackage)}; motivo=${scanBlockReason(resolvedPackage)}\",\n"
    "            )\n",
)

replace_once(
    SERVICE,
    "        val screenChangedChecklist13 = lastImmediateScreenPackageChecklist13 != null &&\n",
    "        UnifiedDebugEventStore.record(\n"
    "            \"BUBBLE_TEXT_COLLECTED\",\n"
    "            resolvedPackage,\n"
    "            \"fonte=acessibilidade_imediata; tamanho=${immediateTextChecklist13.length}; hash=${immediateTextChecklist13.hashCode()}; window=${event.windowId}; fingerprint=$fingerprintChecklist13\",\n"
    "        )\n"
    "        val screenChangedChecklist13 = lastImmediateScreenPackageChecklist13 != null &&\n",
)

replace_once(
    SERVICE,
    "        if (screenChangedChecklist13) {\n",
    "        if (screenChangedChecklist13) {\n"
    "            UnifiedDebugEventStore.record(\"BUBBLE_SCREEN_CHANGED\", resolvedPackage, \"fingerprintAnterior=$lastImmediateScreenFingerprintChecklist13; fingerprintAtual=$fingerprintChecklist13; window=${event.windowId}\")\n",
)

replace_once(
    SERVICE,
    "        if (immediateTextChecklist13.isBlank()) {\n",
    "        if (immediateTextChecklist13.isBlank()) {\n"
    "            UnifiedDebugEventStore.record(\"BUBBLE_TEXT_EMPTY\", resolvedPackage, \"coleta imediata vazia; OCR fallback agendado\")\n",
)

replace_once(
    SERVICE,
    "        analyzeJob?.cancel()\n        analyzeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {\n",
    "        if (analyzeJob?.isActive == true) {\n"
    "            UnifiedDebugEventStore.record(\"BUBBLE_ANALYSIS_CANCELLED\", resolvedPackage, \"análise anterior cancelada por evento mais recente\")\n"
    "        }\n"
    "        analyzeJob?.cancel()\n"
    "        UnifiedDebugEventStore.record(\"BUBBLE_ANALYSIS_STARTED\", resolvedPackage, \"fonte=Accessibility; tamanho=${immediateTextChecklist13.length}; hash=${immediateTextChecklist13.hashCode()}\")\n"
    "        analyzeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {\n",
)

# Instrumenta toda a cadeia de leitura e decisão.
replace_once(
    SERVICE,
    "        @Suppress(\"UNUSED_VARIABLE\") val ignoredPopupCandidateChecklist13 = allowPopupCandidate\n",
    "        @Suppress(\"UNUSED_VARIABLE\") val ignoredPopupCandidateChecklist13 = allowPopupCandidate\n"
    "        UnifiedDebugEventStore.record(\n"
    "            \"BUBBLE_PROCESS_ENTER\",\n"
    "            universalResolvedForegroundPackage(),\n"
    "            \"fonte=${source.name}; tamanho=${text.length}; hash=${text.hashCode()}; gesture=$bubbleGestureActive; ready=$serviceReady; appEnabled=${currentSettings.appEnabled}; live=${currentSettings.liveReadingEnabled}\",\n"
    "        )\n",
)

replace_once(
    SERVICE,
    "        if (!evaluationChecklist13.active) {\n",
    "        UnifiedDebugEventStore.record(\n"
    "            \"BUBBLE_ADDRESS_EVALUATION\",\n"
    "            selectedPackageChecklist13,\n"
    "            \"ativo=${evaluationChecklist13.active}; pickup=${evaluationChecklist13.pickup.orEmpty()}; destination=${evaluationChecklist13.destination.orEmpty()}; assinatura=${evaluationChecklist13.addressSignature}; screenHash=${evaluationChecklist13.screenHash}\",\n"
    "        )\n"
    "        if (!evaluationChecklist13.active) {\n",
)

replace_once(
    SERVICE,
    "        if (cardChangedChecklist13 && (\n",
    "        UnifiedDebugEventStore.record(\n"
    "            \"BUBBLE_CARD_STATE\",\n"
    "            selectedPackageChecklist13,\n"
    "            \"mudou=$cardChangedChecklist13; assinaturaAnterior=${universalActiveAddressSignature ?: \"nenhuma\"}; assinaturaAtual=${evaluationChecklist13.addressSignature}; hashAnterior=${lastSnapshotHash ?: 0}; hashAtual=${evaluationChecklist13.screenHash}\",\n"
    "        )\n"
    "        if (cardChangedChecklist13 && (\n",
)

replace_once(
    SERVICE,
    "        } else if (lastAnalyzedHash == evaluationChecklist13.screenHash || universalRouteJob?.isActive == true) {\n            return\n        }\n",
    "        } else if (lastAnalyzedHash == evaluationChecklist13.screenHash || universalRouteJob?.isActive == true) {\n"
    "            UnifiedDebugEventStore.record(\n"
    "                \"BUBBLE_DUPLICATE_SKIPPED\",\n"
    "                selectedPackageChecklist13,\n"
    "                \"lastAnalyzedHash=$lastAnalyzedHash; screenHash=${evaluationChecklist13.screenHash}; routeActive=${universalRouteJob?.isActive == true}\",\n"
    "            )\n"
    "            return\n"
    "        }\n",
)

replace_once(
    SERVICE,
    "        if (cachedDistancesChecklist13 != null) {\n",
    "        if (cachedDistancesChecklist13 != null) {\n"
    "            UnifiedDebugEventStore.record(\"BUBBLE_CACHE_HIT\", selectedPackageChecklist13, \"destino=${fieldsChecklist13.destination.orEmpty()}; distancias=$cachedDistancesChecklist13\")\n",
)

replace_once(
    SERVICE,
    "        rememberBubbleReason(\"universal_waiting\", \"Dois enderecos identificados; calculando o ultimo destino.\")\n",
    "        UnifiedDebugEventStore.record(\"BUBBLE_ROUTE_REQUESTED\", selectedPackageChecklist13, \"destino=${fieldsChecklist13.destination.orEmpty()}; alvos=${targetsChecklist13.destinations.size}; generation=$generationChecklist13\")\n"
    "        rememberBubbleReason(\"universal_waiting\", \"Dois enderecos identificados; calculando o ultimo destino.\")\n",
)

replace_once(
    SERVICE,
    "        val routeDistancesChecklist13 = googleMapsService.drivingDistancesFromAddressKm(\n",
    "        UnifiedDebugEventStore.record(\"BUBBLE_ROUTE_CALL_START\", universalActiveRidePackageName, \"destino=${fields.destination.orEmpty()}; alvos=${targetsChecklist13.destinations.size}; generation=$generation\")\n"
    "        val routeDistancesChecklist13 = googleMapsService.drivingDistancesFromAddressKm(\n",
)

replace_once(
    SERVICE,
    "        val resultChecklist13 = decideFastWorkRegionChecklist13(\n",
    "        UnifiedDebugEventStore.record(\"BUBBLE_ROUTE_CALL_END\", universalActiveRidePackageName, \"distancias=$routeDistancesChecklist13; fresh=${isUniversalResultFresh(generation, screenHash, addressSignature)}\")\n"
    "        val resultChecklist13 = decideFastWorkRegionChecklist13(\n",
)

replace_once(
    SERVICE,
    "        rememberBubbleReason(\"universal_result\", result.reason)\n        showOverlay(colorChecklist13, distanceChecklist13)\n",
    "        UnifiedDebugEventStore.record(\n"
    "            \"BUBBLE_DECISION_READY\",\n"
    "            universalActiveRidePackageName,\n"
    "            \"recomendacao=${result.recommendation}; cor=$colorChecklist13; distancia=$distanceChecklist13; destino=${result.fields.destination.orEmpty()}; generation=$generation; screenHash=$screenHash\",\n"
    "        )\n"
    "        rememberBubbleReason(\"universal_result\", result.reason)\n"
    "        showOverlay(colorChecklist13, distanceChecklist13)\n"
    "        UnifiedDebugEventStore.record(\"BUBBLE_DECISION_PAINTED\", universalActiveRidePackageName, \"cor=$colorChecklist13; distancia=$distanceChecklist13; stage=$lastBubbleStateStage; motivo=$lastBubbleStateReason\")\n",
)

replace_once(
    SERVICE,
    "    ) {\n        partialReadConfirmationJobChecklist14?.cancel()\n",
    "    ) {\n"
    "        UnifiedDebugEventStore.record(\n"
    "            \"BUBBLE_CLEAR_REQUEST\",\n"
    "            universalResolvedForegroundPackage(),\n"
    "            \"reason=$reason; keepWaitingYellow=$keepWaitingYellow; corAtual=$currentRadarColor; distanciaAtual=$currentDistanceKm; assinatura=${universalActiveAddressSignature ?: \"nenhuma\"}; hash=${lastSnapshotHash ?: 0}\",\n"
    "        )\n"
    "        partialReadConfirmationJobChecklist14?.cancel()\n",
)

replace_once(
    SERVICE,
    "            showOverlay(targetColor127, distanceKm = null) // atomic_hard_clear_single_paint_0_1_127\n",
    "            showOverlay(targetColor127, distanceKm = null) // atomic_hard_clear_single_paint_0_1_127\n"
    "            UnifiedDebugEventStore.record(\"BUBBLE_CLEAR_PAINTED\", universalResolvedForegroundPackage(), \"cor=$targetColor127; stage=$targetStage127; motivo=$targetReason127\")\n",
)

# Versão 0.1.140.
text = GRADLE.read_text(encoding="utf-8")
text = text.replace('versionName = "0.1.139"', 'versionName = "0.1.140"')
text = text.replace('val ciVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 4_000 + it }', 'val ciVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 5_000 + it }')
text = text.replace('val appVersionCode = ciVersionCode ?: 4_001', 'val appVersionCode = ciVersionCode ?: 5_001')
GRADLE.write_text(text, encoding="utf-8")

TEST.write_text(r'''package br.com.mapeiaia.rotacerta

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class BubbleDebugTrace140Test {
    @Test
    fun captures_complete_bubble_pipeline() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()
        listOf(
            "BUBBLE_EVENT_RESOLVED",
            "BUBBLE_PACKAGE_BLOCKED",
            "BUBBLE_TEXT_COLLECTED",
            "BUBBLE_SCREEN_CHANGED",
            "BUBBLE_ANALYSIS_STARTED",
            "BUBBLE_PROCESS_ENTER",
            "BUBBLE_ADDRESS_EVALUATION",
            "BUBBLE_CARD_STATE",
            "BUBBLE_DUPLICATE_SKIPPED",
            "BUBBLE_CACHE_HIT",
            "BUBBLE_ROUTE_REQUESTED",
            "BUBBLE_ROUTE_CALL_START",
            "BUBBLE_ROUTE_CALL_END",
            "BUBBLE_DECISION_READY",
            "BUBBLE_DECISION_PAINTED",
            "BUBBLE_CLEAR_REQUEST",
            "BUBBLE_CLEAR_PAINTED",
        ).forEach { assertContains(source, it) }
    }
}
''', encoding="utf-8")

print("Instrumentação integral da bolinha 0.1.140 aplicada")
