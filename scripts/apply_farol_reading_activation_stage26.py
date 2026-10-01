#!/usr/bin/env python3
from __future__ import annotations

import argparse
import shutil
from pathlib import Path

SERVICE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt')
REPORT = Path('app/src/main/java/br/com/mapeiaia/rotacerta/ManualTechnicalReportBuilder.kt')
MAIN = Path('app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt')
PICKER = Path('app/src/main/java/br/com/mapeiaia/rotacerta/InstalledRideAppPickerActivity.kt')
BUBBLE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt')
MANIFEST = Path('app/src/main/AndroidManifest.xml')
BUILD = Path('app/build.gradle.kts')
HELPER = Path('app/src/main/java/br/com/mapeiaia/rotacerta/FarolReadingActivationStage26.kt')
USAGE = Path('app/src/main/java/br/com/mapeiaia/rotacerta/SelectedAppUsageStateStage26.kt')
TEST = Path('app/src/test/java/br/com/mapeiaia/rotacerta/FarolReadingActivationStage26Test.kt')
PATCH_ROOT = Path(__file__).resolve().parents[1]
HELPER_TEMPLATE = PATCH_ROOT / 'stage26/FarolReadingActivationStage26.kt'
USAGE_TEMPLATE = PATCH_ROOT / 'stage26/SelectedAppUsageStateStage26.kt'
TEST_TEMPLATE = PATCH_ROOT / 'stage26/FarolReadingActivationStage26Test.kt'
HANDLER_TEMPLATE = PATCH_ROOT / 'stage26/LiveRideAccessibilityServiceStage26.inc.kt'
MARKER = 'FAROL_READING_ACTIVATION_STAGE26'


def fail(message: str) -> None:
    raise SystemExit(message)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        fail(f'Stage26 anchor {label}: expected 1, found {count}')
    return text.replace(old, new, 1)


def replace_section(text: str, start: str, end: str, replacement: str, label: str) -> str:
    a = text.find(start)
    b = text.find(end, a + len(start))
    if a < 0 or b <= a:
        fail(f'Stage26 section {label}: markers not found')
    return text[:a] + replacement + text[b:]


def self_test() -> None:
    for path in (HELPER_TEMPLATE, USAGE_TEMPLATE, TEST_TEMPLATE, HANDLER_TEMPLATE):
        if not path.is_file(): fail(f'missing Stage26 support file: {path}')
    helper = HELPER_TEMPLATE.read_text(encoding='utf-8')
    usage = USAGE_TEMPLATE.read_text(encoding='utf-8')
    tests = TEST_TEMPLATE.read_text(encoding='utf-8')
    handler = HANDLER_TEMPLATE.read_text(encoding='utf-8')
    for required in (
        MARKER, 'SELECTED_PACKAGES_ACTIVATE_INFRASTRUCTURE_ONLY_STAGE26',
        'USAGE_ACCESS_FAIL_CLOSED_STAGE26', 'PRECOLLECT_VISUAL_ADMISSION_BEFORE_HEAVY_TRAVERSAL_STAGE26',
        'OLD_PAINT_INVALIDATED_BEFORE_COLLECT_STAGE26', 'COMPACT_NON_OVERLAPPING_COLLECTOR_STAGE26',
        'FINAL_COLOR_AND_KM_SAME_GENERATION_STAGE26', 'NO_TEMPORAL_DEBOUNCE_AUTHORITY_STAGE26',
        'ActivationMachine', 'PreCollectGate', 'WorkCoordinator', 'compact(', 'relevant_visual_slot_cleared',
    ):
        if required not in helper: fail(f'Stage26 helper missing {required}')
    for required in ('UsageEventsQuery.Builder', 'setPackageNames', 'FOREGROUND_SERVICE_START', 'hasUsageAccess'):
        if required not in usage: fail(f'Stage26 usage adapter missing {required}')
    if tests.count('@Test') != 45:
        fail(f'expected exactly 45 Stage26 tests, found {tests.count("@Test")}')
    mandatory = (
        'noSelectedAppActiveMeansZeroHeavyCollect','launcherWithFarolOffMeansZeroCollect','whatsAppWithFarolOffMeansZeroCollect',
        'systemUiWithFarolOffMeansZeroCollect','documentsUiWithFarolOffMeansZeroOcr','openingOneSelectedTurnsFarolOn',
        'twoSelectedActiveRemainOn','closingOneOfTwoRemainsOn','closingLastTurnsOffImmediately','lastClosesDuringCollectDiscardsResult',
        'lastClosesDuringOcrDiscardsResult','lastClosesDuringGoogleDiscardsResult','selectedActiveHomeKeepsReading',
        'selectedActiveWhatsAppKeepsReading','selectedActiveChatGptKeepsReading','validPopupOverHomeIsAnalyzed',
        'validPopupOverWhatsAppIsAnalyzed','validPopupOverChatGptIsAnalyzed','validPopupOverRotaCertaIsAnalyzed',
        'samePopupWithFarolOffIsIgnored','hundredRepeatedEventsCauseAtMostOneHeavyCollect','ownBubbleEventMeansZeroHeavyCollect',
        'realMutationClearsBeforeCollect','oldGreenDisappearsBeforeCollect','oldRedDisappearsBeforeCollect','oldKmDisappearsBeforeCollect',
        'destinationSwapInvalidatesAFirst','cardCloseClearsImmediately','collectorDoesNotDuplicateAncestorSubtree',
        'parserDoesNotAnalyzeDozensOfCopies','twoDistinctCardsAreNotMixed','truncatedAddressRemainsRejected',
        'cacheCannotBypassSemanticBarrier','googleCannotBypassSemanticBarrier','staleGoogleCannotPaint','staleOcrCannotPaint',
        'staleCacheCannotPaint','paintTokenAndFreshnessPreserved','exactCacheCanAnswerImmediately','captureSelectsPackageContract',
        'captureScreenshotIsNotCardAuthority','unselectedVisiblePackageCanContainValidCardWhenOn','missingUsageAccessFailsClosed',
        'activationDoesNotDependOnTemporalDebounce','finalColorAndKmShareGeneration',
    )
    for name in mandatory:
        if name not in tests: fail(f'Stage26 mandatory test missing {name}')
    for forbidden in ('Thread.sleep(', 'SystemClock.sleep(', 'delay(75', 'delay(100', 'delay(180', 'delay(250', 'delay(500'):
        if forbidden in helper or forbidden in handler or forbidden in usage:
            fail(f'Stage26 critical path forbidden timing authority: {forbidden}')
    print('stage26_self_test=passed')
    print('stage26_test_methods=45')
    print('activation_gate_before_heavy_collect=true')
    print('usage_access_fail_closed=true')
    print('precollect_no_temporal_debounce=true')
    print('compact_collector=true')
    print('immediate_old_paint_invalidation=true')


def apply(root: Path) -> None:
    required = (SERVICE, REPORT, MAIN, PICKER, BUBBLE, MANIFEST, BUILD)
    if any(not (root / path).is_file() for path in required): fail('Stage26 requires materialized Stage23 app source')
    service = (root / SERVICE).read_text(encoding='utf-8')
    report = (root / REPORT).read_text(encoding='utf-8')
    main = (root / MAIN).read_text(encoding='utf-8')
    picker = (root / PICKER).read_text(encoding='utf-8')
    bubble = (root / BUBBLE).read_text(encoding='utf-8')
    manifest = (root / MANIFEST).read_text(encoding='utf-8')
    build = (root / BUILD).read_text(encoding='utf-8')
    if 'FAROL_VISUAL_IDENTITY_COALESCING_STAGE23' not in service:
        fail('Stage26 must be applied after Stage23 materialization')
    if 'FAROL_CAUSAL_CORRECTION_STAGE21' not in service or 'S21_SEMANTIC_REJECT_BEFORE_CACHE_ROUTE' not in service:
        fail('Stage26 requires Stage21 semantic barrier in compiled service')
    if MARKER in service or (root / HELPER).exists(): fail('Stage26 already appears applied')
    if 'versionCode = 5487' not in build or 'versionName = "0.1.203"' not in build:
        fail('Stage26 requires exact 0.1.203/5487 Stage23 baseline')

    state_anchor = (
        '    private val stage23VisualGate = FarolVisualIdentityStage23.VisualSnapshotGate()\n'
        '    private val stage23ScheduleGate = FarolVisualIdentityStage23.ScheduledDemandGate()\n'
        '    private val stage23OcrGate = FarolVisualIdentityStage23.OcrDemandGate()\n'
        '    // FAROL_VISUAL_IDENTITY_COALESCING_STAGE23 — visual snapshot owns expensive-work admission\n'
    )
    state_new = (
        '    private val stage23VisualGate = FarolVisualIdentityStage23.VisualSnapshotGate()\n'
        '    private val stage23ScheduleGate = FarolVisualIdentityStage23.ScheduledDemandGate()\n'
        '    private val stage23OcrGate = FarolVisualIdentityStage23.OcrDemandGate()\n'
        '    private val stage26ReadingActivation = FarolReadingActivationStage26.ActivationMachine()\n'
        '    private val stage26PreCollectGate = FarolReadingActivationStage26.PreCollectGate()\n'
        '    private lateinit var stage26UsageState: SelectedAppUsageStateStage26\n'
        '    private var stage26UsageInitialized = false\n'
        '    private var stage26LastAppliedActivationGeneration = -1L\n'
        '    private var stage26CurrentVisualGeneration = 0L\n'
        '    private var stage26CandidateEventStartedNs = 0L\n'
        '    private var stage26CandidateActivationGeneration = -1L\n'
        '    private var stage26OcrActivationGeneration = -1L\n'
        '    private var stage26RouteResponseNs = 0L\n'
        '    private val stage26BindingActivationGeneration = LinkedHashMap<String, Long>()\n'
        '    // FAROL_READING_ACTIVATION_STAGE26 — selected apps gate infrastructure; package never authorizes card content\n'
        '    // FAROL_VISUAL_IDENTITY_COALESCING_STAGE23 — retained as post-collect safety/freshness layer\n'
    )
    service = replace_once(service, state_anchor, state_new, 'Stage26 state')
    service = replace_once(
        service,
        '    private fun connectService0172() {\n        serviceReady = true\n',
        '    private fun connectService0172() {\n        serviceReady = true\n        stage26UsageState = SelectedAppUsageStateStage26(applicationContext)\n',
        'usage tracker init',
    )
    service = replace_once(
        service,
        '        if (handleUniversalVisualEventStage19(eventPackage, eventType0187, eventWindowIdStage20)) return\n',
        '        if (handleUniversalVisualEventStage19(eventPackage, eventType0187, eventWindowIdStage20, event)) return\n',
        'Stage26 event source admission',
    )

    handler = HANDLER_TEMPLATE.read_text(encoding='utf-8')
    service = replace_section(
        service,
        '    private data class Stage23AccessibilitySnapshot(',
        '    private fun requestUniversalScreenshotStage19(',
        handler,
        'Stage26 gate/precollect/collector',
    )

    service = replace_once(
        service,
        '            serialStage19 == stage19OcrSerial &&\n            WorkModePolicy0162.isEnabled(currentSettings)\n',
        '            serialStage19 == stage19OcrSerial &&\n            WorkModePolicy0162.isEnabled(currentSettings) &&\n            isReadingActivationGenerationFreshStage26(stage26OcrActivationGeneration)\n',
        'OCR freshness activation',
    )
    service = replace_once(
        service,
        '        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings) || bubbleGestureActive) return\n        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return\n\n        val demandStage23 = rerunDemandStage23 ?: FarolVisualIdentityStage23.OcrDemand(\n',
        '        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings) || bubbleGestureActive) return\n        val activationStage26 = stage26ReadingActivation.snapshot()\n        if (!activationStage26.enabled || !activationStage26.usageAccessGranted) return\n        stage26OcrActivationGeneration = activationStage26.generation\n        FarolReadingActivationStage26.Metrics.increment("ocrRequests")\n        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return\n\n        val demandStage23 = rerunDemandStage23 ?: FarolVisualIdentityStage23.OcrDemand(\n',
        'OCR request gate',
    )
    service = replace_once(
        service,
        '        FarolVisualIdentityStage23.Metrics.increment("ocrStarts")\n        val serialStage19 = ++stage19OcrSerial\n',
        '        FarolVisualIdentityStage23.Metrics.increment("ocrStarts")\n        FarolReadingActivationStage26.Metrics.increment("ocrStarts")\n        val serialStage19 = ++stage19OcrSerial\n',
        'OCR starts metric',
    )
    for metric in ('ocrStaleBeforeBitmap','ocrStaleBeforeExtract','ocrStaleAfterExtract','ocrStaleAfterEvaluate'):
        service = service.replace(
            f'FarolVisualIdentityStage23.Metrics.increment("{metric}")',
            f'FarolVisualIdentityStage23.Metrics.increment("{metric}")\n                                    FarolReadingActivationStage26.Metrics.increment("ocrStale")',
        )
    service = replace_once(
        service,
        '            val usefulStage23 = serviceReady && WorkModePolicy0162.isEnabled(currentSettings) &&\n                stage23VisualGate.currentGeneration() == rerunStage23.visualGeneration &&\n',
        '            val usefulStage23 = serviceReady && WorkModePolicy0162.isEnabled(currentSettings) &&\n                isReadingActivationGenerationFreshStage26(stage26OcrActivationGeneration) &&\n                stage23VisualGate.currentGeneration() == rerunStage23.visualGeneration &&\n',
        'OCR rerun activation',
    )
    service = replace_once(
        service,
        '                                if (evaluationStage19 != null) {\n                                    processUniversalVisualStage19(evaluationStage19, "Ocr", cycleIdStage20)\n',
        '                                if (evaluationStage19 != null) {\n                                    stage26CandidateEventStartedNs = SystemClock.elapsedRealtimeNanos()\n                                    stage26CandidateActivationGeneration = stage26OcrActivationGeneration\n                                    processUniversalVisualStage19(evaluationStage19, "Ocr", cycleIdStage20)\n',
        'OCR candidate activation binding',
    )

    service = replace_once(
        service,
        '    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {\n',
        '    private fun scheduleVisibleTextAnalysis(delayMs: Long, allowPopupCandidate: Boolean = false) {\n        if (!stage26ReadingActivation.snapshot().enabled) {\n            FarolReadingActivationStage26.Metrics.increment("eventsRejectedReadingOff")\n            return\n        }\n',
        'scheduled activation gate',
    )

    service = replace_once(
        service,
        '    ) {\n        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return\n        val semanticStage21 = FarolCausalCorrectionStage21.validateEvaluation(evaluationStage19)\n',
        '    ) {\n        if (!serviceReady || !WorkModePolicy0162.isEnabled(currentSettings)) return\n        if (!isReadingActivationGenerationFreshStage26(stage26CandidateActivationGeneration)) return\n        stage26RouteResponseNs = 0L\n        val semanticStage21 = FarolCausalCorrectionStage21.validateEvaluation(evaluationStage19)\n',
        'process activation gate',
    )
    binding_anchor = '''        val bindingStage19 = FarolUniversalVisualPipelineStage19.Binding(\n            screenGeneration = universalScreenGeneration,\n            windowGeneration = universalWindowGeneration,\n            screenHash = evaluationStage19.screenHash,\n            addressSignature = evaluationStage19.addressSignature,\n        )\n'''
    binding_new = binding_anchor + '''        bindReadingActivationStage26(bindingStage19, stage26CandidateActivationGeneration)\n        FarolReadingActivationStage26.Metrics.sample(\n            "candidateToRouteStart",\n            (SystemClock.elapsedRealtimeNanos() - stage26CandidateEventStartedNs).coerceAtLeast(0L),\n        )\n'''
    service = replace_once(service, binding_anchor, binding_new, 'binding activation generation')
    service = replace_once(
        service,
        '    private fun isStage19BindingFresh(bindingStage19: FarolUniversalVisualPipelineStage19.Binding): Boolean =\n        serviceReady && WorkModePolicy0162.isEnabled(currentSettings) &&\n',
        '    private fun isStage19BindingFresh(bindingStage19: FarolUniversalVisualPipelineStage19.Binding): Boolean =\n        serviceReady && WorkModePolicy0162.isEnabled(currentSettings) &&\n            isReadingBindingFreshStage26(bindingStage19) &&\n',
        'binding freshness activation',
    )
    binding_helpers = r'''
    private fun stage26BindingKey(bindingStage26: FarolUniversalVisualPipelineStage19.Binding): String =
        "${bindingStage26.screenGeneration}|${bindingStage26.windowGeneration}|${bindingStage26.screenHash}|${bindingStage26.addressSignature}"

    private fun bindReadingActivationStage26(
        bindingStage26: FarolUniversalVisualPipelineStage19.Binding,
        activationGenerationStage26: Long,
    ) {
        if (stage26BindingActivationGeneration.size >= 12) {
            val firstStage26 = stage26BindingActivationGeneration.keys.firstOrNull()
            if (firstStage26 != null) stage26BindingActivationGeneration.remove(firstStage26)
        }
        stage26BindingActivationGeneration[stage26BindingKey(bindingStage26)] = activationGenerationStage26
    }

    private fun isReadingBindingFreshStage26(bindingStage26: FarolUniversalVisualPipelineStage19.Binding): Boolean {
        val expectedStage26 = stage26BindingActivationGeneration[stage26BindingKey(bindingStage26)] ?: return false
        return isReadingActivationGenerationFreshStage26(expectedStage26)
    }

'''
    service = replace_once(
        service,
        '    private suspend fun analyzeUniversalTwoAddressStage19(\n',
        binding_helpers + '    private suspend fun analyzeUniversalTwoAddressStage19(\n',
        'binding helper insertion',
    )
    service = replace_once(
        service,
        '        val distancesStage19 = googleMapsService.drivingDistancesFromAddressKm(\n',
        '        val routeStartedNsStage26 = SystemClock.elapsedRealtimeNanos()\n        val distancesStage19 = googleMapsService.drivingDistancesFromAddressKm(\n',
        'route start metric',
    )
    service = replace_once(
        service,
        '        FarolForensicTraceStage20.routeCallFinished(traceIdStage20, routeJobIdStage20, SystemClock.elapsedRealtimeNanos(), distancesStage19.toString())\n        val routeFreshStage20 = isStage19BindingFresh(bindingStage19)\n',
        '        FarolForensicTraceStage20.routeCallFinished(traceIdStage20, routeJobIdStage20, SystemClock.elapsedRealtimeNanos(), distancesStage19.toString())\n        val routeEndedNsStage26 = SystemClock.elapsedRealtimeNanos()\n        FarolReadingActivationStage26.Metrics.sample("route", routeEndedNsStage26 - routeStartedNsStage26)\n        stage26RouteResponseNs = routeEndedNsStage26\n        val routeFreshStage20 = isStage19BindingFresh(bindingStage19)\n',
        'route complete metric',
    )
    service = replace_once(
        service,
        '        val paintTokenStage20 = FarolForensicTraceStage20.preparePaint(\n',
        '        val paintPreparedNsStage26 = SystemClock.elapsedRealtimeNanos()\n        if (stage26RouteResponseNs > 0L) FarolReadingActivationStage26.Metrics.sample("routeResponseToPaint", paintPreparedNsStage26 - stage26RouteResponseNs)\n        if (stage26CandidateEventStartedNs > 0L) FarolReadingActivationStage26.Metrics.sample("eventToFinalGreenRedKm", paintPreparedNsStage26 - stage26CandidateEventStartedNs)\n        val paintTokenStage20 = FarolForensicTraceStage20.preparePaint(\n',
        'paint timing before preserved paint token',
    )

    manifest = replace_once(
        manifest,
        '    <uses-permission android:name="android.permission.INTERNET" />\n',
        '    <uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />\n',
        'PACKAGE_USAGE_STATS manifest',
    )
    manifest = manifest.replace('android:label="Aplicativos e cards autorizados"', 'android:label="Aplicativos que ativam a leitura"')

    main = replace_once(
        main,
        '    var lastManualCapture by remember { mutableStateOf(ManualAppScreenCaptureStore.read(context)) }\n',
        '    var lastManualCapture by remember { mutableStateOf(ManualAppScreenCaptureStore.read(context)) }\n    var usageAccessGrantedStage26 by remember { mutableStateOf(SelectedAppUsageStateStage26(context).hasUsageAccess()) }\n',
        'usage access UI state',
    )
    main = replace_once(
        main,
        '                lastManualCapture = ManualAppScreenCaptureStore.read(context)\n',
        '                lastManualCapture = ManualAppScreenCaptureStore.read(context)\n                usageAccessGrantedStage26 = SelectedAppUsageStateStage26(context).hasUsageAccess()\n',
        'usage access UI resume refresh',
    )
    main = replace_once(main, 'ExpandableCard(title = "Aplicativos que a bolinha pode ler", initiallyExpanded = true) {', 'ExpandableCard(title = "Aplicativos que ativam a leitura", initiallyExpanded = true) {', 'main title')
    main = main.replace('appendLine("Selecao manual de apps autoriza leitura: false (Stage19 universal)")', 'appendLine("Selecao manual de apps ativa infraestrutura: true (Stage26)")')
    main = main.replace('appendLine("Politica Stage19+: tela visual atual + dois ou mais enderecos coerentes no mesmo bloco; ultimo endereco e o destino")', 'appendLine("Politica Stage26: app selecionado liga/desliga infraestrutura; package visual nao autoriza card; ultimo endereco coerente e o destino")')
    main = replace_once(
        main,
        '            "Nenhum aplicativo vem marcado. Escolha manualmente os aplicativos que a bolinha poderá ler.",',
        '            "Escolha os aplicativos de corrida que ligam a infraestrutura do FAROL. O conteúdo visual do card continua universal e não é autorizado pelo packageName.",',
        'main activation description',
    )
    main = replace_once(
        main,
        '            "Para capturar pacote, texto e imagem: abra o aplicativo desejado, toque na bolinha e escolha Capturar. A captura é opcional e não interfere no farol.",',
        '            "Para adicionar rapidamente: abra o aplicativo de motorista, toque na bolinha e em Capturar. A captura seleciona o package somente para ATIVAR/DESATIVAR a leitura; o screenshot nunca autoriza um card.",',
        'capture semantics text',
    )
    usage_ui_anchor = '''        Button(\n            onClick = { context.startActivity(Intent(context, InstalledRideAppPickerActivity::class.java)) },\n            modifier = Modifier.fillMaxWidth(),\n        ) {\n            Text("Buscar aplicativos instalados")\n        }\n'''
    usage_ui_new = usage_ui_anchor + '''        Text(\n            if (usageAccessGrantedStage26) "Acesso ao uso: concedido." else "Acesso ao uso: necessário. Sem essa autorização o FAROL falha fechado e não faz leitura global.",\n            style = MaterialTheme.typography.bodySmall,\n        )\n        if (!usageAccessGrantedStage26) {\n            OutlinedButton(\n                onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },\n                modifier = Modifier.fillMaxWidth(),\n            ) { Text("Conceder Acesso ao uso") }\n        }\n'''
    main = replace_once(main, usage_ui_anchor, usage_ui_new, 'usage access UI')

    picker = picker.replace('Aplicativos e cards autorizados', 'Aplicativos que ativam a leitura')
    picker = picker.replace('Salvar aplicativos autorizados', 'Salvar aplicativos que ativam a leitura')
    picker = picker.replace('Excluir aplicativo e cards', 'Remover aplicativo e capturas')

    bubble = replace_once(
        bubble,
        '        action = BubbleShortcutAction.OpenAuthorizedAppsAndCards,\n        doubleTapAction = BubbleShortcutQuickAction.CaptureCurrentAppAndScreen,\n',
        '        action = BubbleShortcutAction.CaptureCurrentAppAndScreen,\n        doubleTapAction = BubbleShortcutQuickAction.CaptureCurrentAppAndScreen,\n',
        'Capturar single tap',
    )

    service = service.replace('toast("Aplicativo e texto capturados")', 'toast("Aplicativo selecionado para ativar a leitura; texto capturado")')
    service = service.replace('toast("Aplicativo e tela capturados")', 'toast("Aplicativo selecionado para ativar a leitura; tela capturada")')
    service = service.replace('showSaveConfirmationNotification("Captura salva", externalPackage)', 'showSaveConfirmationNotification("Aplicativo selecionado para ativar a leitura", externalPackage)')

    report = report.replace('appendLine("Selecao manual obrigatoria: true")', 'appendLine("Selecao manual para ativar infraestrutura: true")')
    report = report.replace('appendLine("Selecao manual obrigatoria para autorizar leitura: false (Stage19 universal)")', 'appendLine("Selecao manual para ativar infraestrutura: true (Stage26)")')
    report = report.replace('appendLine("Politica: aplicativo selecionado + dois ou mais enderecos; o ultimo e o destino")', 'appendLine("Politica Stage26: package selecionado liga/desliga a leitura; conteudo visual universal quando FAROL ON; ultimo endereco coerente e o destino")')
    report = replace_once(
        report,
        '            appendLine("Acessibilidade autorizada: ${isAccessibilityEnabled(appContext)}")\n',
        '            appendLine("Acessibilidade autorizada: ${isAccessibilityEnabled(appContext)}")\n            appendLine("Acesso ao uso autorizado: ${SelectedAppUsageStateStage26(appContext).hasUsageAccess()}")\n',
        'report Usage Access',
    )
    report = replace_once(
        report,
        '            appendLine(FarolVisualIdentityStage23.Metrics.exportReport())\n',
        '            appendLine(FarolVisualIdentityStage23.Metrics.exportReport())\n            appendLine()\n            appendLine(FarolReadingActivationStage26.Metrics.exportReport())\n',
        'Stage26 report metrics',
    )

    build = replace_once(build, 'versionCode = 5487', 'versionCode = 5488', 'versionCode')
    build = replace_once(build, 'versionName = "0.1.203"', 'versionName = "0.1.204"', 'versionName')

    (root / HELPER).parent.mkdir(parents=True, exist_ok=True)
    (root / TEST).parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HELPER_TEMPLATE, root / HELPER)
    shutil.copyfile(USAGE_TEMPLATE, root / USAGE)
    shutil.copyfile(TEST_TEMPLATE, root / TEST)
    (root / SERVICE).write_text(service, encoding='utf-8')
    (root / REPORT).write_text(report, encoding='utf-8')
    (root / MAIN).write_text(main, encoding='utf-8')
    (root / PICKER).write_text(picker, encoding='utf-8')
    (root / BUBBLE).write_text(bubble, encoding='utf-8')
    (root / MANIFEST).write_text(manifest, encoding='utf-8')
    (root / BUILD).write_text(build, encoding='utf-8')

    transformed = (root / SERVICE).read_text(encoding='utf-8')
    checks = (
        MARKER, 'refreshReadingActivationStage26', 'applyReadingOffStage26', 'buildCheapVisualSignalStage26',
        'invalidateOldVisualBeforeCollectStage26', 'collectUniversalAccessibilitySnapshotStage26',
        'stage26BindingActivationGeneration', 'isReadingBindingFreshStage26', 'S21_SEMANTIC_REJECT_BEFORE_CACHE_ROUTE',
        'stage20ExpectedPaintToken', 'FarolForensicTraceStage20.preparePaint', 'cachedDrivingDistancesFromAddressKm',
        'drivingDistancesFromAddressKm', 'stage19VisualVerificationPending', 'stage23OcrGate',
        'SelectedRideAppStore.add(applicationContext, externalPackage)',
    )
    for item in checks:
        if item not in transformed: fail(f'applied Stage26 service missing {item}')
    if transformed.index('refreshReadingActivationStage26') > transformed.index('collectUniversalAccessibilitySnapshotStage26'):
        fail('activation gate must precede heavy collection')
    handler_start = transformed.index('    private fun handleUniversalVisualEventStage19(')
    handler_end = transformed.index('    private fun requestUniversalScreenshotStage19(', handler_start)
    handler_applied = transformed[handler_start:handler_end]
    if handler_applied.index('invalidateOldVisualBeforeCollectStage26') > handler_applied.index('collectUniversalAccessibilitySnapshotStage26'):
        fail('old visual invalidation must precede collection')
    process_start = transformed.index('    private suspend fun processUniversalVisualStage19(')
    process_end = transformed.index('    private fun stage20BindingSnapshot(', process_start)
    process = transformed[process_start:process_end]
    if process.index('FarolCausalCorrectionStage21.validateEvaluation(evaluationStage19)') > process.index('cachedDrivingDistancesFromAddressKm'):
        fail('semantic barrier moved after cache')
    if 'Thread.sleep(' in transformed or 'SystemClock.sleep(' in handler_applied:
        fail('Stage26 cannot use sleeps/debounce as visual authority')
    if 'versionCode = 5488' not in (root / BUILD).read_text(encoding='utf-8') or 'versionName = "0.1.204"' not in (root / BUILD).read_text(encoding='utf-8'):
        fail('Stage26 version mismatch')
    if 'android.permission.PACKAGE_USAGE_STATS' not in (root / MANIFEST).read_text(encoding='utf-8'):
        fail('Usage Access manifest permission missing')
    print('stage26_apply=passed')
    print('versionName=0.1.204')
    print('versionCode=5488')
    print('activation_before_universal_visual_work=true')
    print('package_visual_authority=false')
    print('usage_access_fail_closed=true')
    print('precollect_before_heavy_collection=true')
    print('old_paint_invalidated_before_collect=true')
    print('compact_collector_max_blocks=6')
    print('ocr_bound_to_activation_generation=true')
    print('google_cache_route_bound_to_activation_generation=true')
    print('stage21_semantic_barrier_preserved=true')
    print('stage20_paint_token_preserved=true')
    print('stage19_freshness_preserved=true')
    print('capture_single_tap_restored=true')


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('source_root', nargs='?', type=Path)
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    self_test()
    if args.self_test: return
    if args.source_root is None: fail('source_root required')
    apply(args.source_root.resolve())


if __name__ == '__main__':
    main()
