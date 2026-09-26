#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'
STAGE26 = PKG / 'FarolReadingActivationStage26.kt'
STAGE36 = PKG / 'FarolRuntimeAuthorityStage36.kt'
BUBBLE = PKG / 'BubbleShortcutModule.kt'
READING_MODULE = PKG / 'ReadingBubbleShortcutModule.kt'
SHORTCUT_POLICY = PKG / 'ShortcutLongPressPolicy0171.kt'
MAIN = PKG / 'MainActivity.kt'
HELPER = PKG / 'FarolManualReadingAuthorityStage42.kt'


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)


def between(text: str, start: str, end: str, replacement: str, label: str) -> str:
    try:
        a = text.index(start)
        b = text.index(end, a)
    except ValueError as exc:
        raise SystemExit(f'{label}: anchor missing: {exc}')
    return text[:a] + replacement + text[b:]


HELPER.write_text(r'''package br.com.mapeiaia.rotacerta

/**
 * Stage42: the user is the only ON/OFF authority for FAROL screen reading.
 * Selected ride apps remain legacy metadata/diagnostic input only; they never arm or disarm reading.
 */
object FarolManualReadingAuthorityStage42 {
    const val CONTRACT_MARKER = "FAROL_MANUAL_READING_AUTHORITY_STAGE42"
    const val USER_ONLY_MARKER = "USER_TOGGLE_ONLY_ARMS_READING_STAGE42"
    const val UNIVERSAL_VISUAL_MARKER = "ANY_VISIBLE_APP_WINDOW_POPUP_TWO_ADDRESS_STAGE42"
    const val NO_PRESENCE_GATE_MARKER = "NO_SELECTED_APP_PRESENCE_IN_CRITICAL_PATH_STAGE42"
    const val HOME_MODULE_MARKER = "HOME_READING_MODULE_AND_FLOATING_SHORTCUT_STAGE42"

    fun isEnabled(settings: AppSettings): Boolean = WorkModePolicy0162.isEnabled(settings)

    fun setEnabled(settings: AppSettings, enabled: Boolean): AppSettings =
        WorkModePolicy0162.setEnabled(settings, enabled)
}
''')

# Stage26 activation machine keeps its historical policy for regression tests, but runtime can now
# drive it directly from the user's manual work toggle without selected packages or Usage Access.
s26 = STAGE26.read_text()
anchor26 = '''        @Synchronized
        fun updateSelection(packages: Set<String>): ActivationSnapshot {
'''
manual26 = '''        @Synchronized
        fun setManualAuthority(enabledNow: Boolean): ActivationSnapshot {
            selected = emptySet()
            resumed.clear()
            foregroundServices.clear()
            usageAccessGranted = enabledNow
            if (enabledNow != enabled) {
                generation += 1L
                enabled = enabledNow
                Metrics.increment(if (enabled) "activationOn" else "activationOff")
            }
            Metrics.setGauge("selectedAppsActiveCount", 0L)
            return snapshotLocked()
        }

'''
s26 = once(s26, anchor26, manual26 + anchor26, 'Stage26 manual authority entry')
STAGE26.write_text(s26)

# Stage36 remains the single async epoch/lease owner, but its ON/OFF input is now manual.
s36 = STAGE36.read_text()
usage_block = '''        @Synchronized
        fun setUsageAccess(granted: Boolean): Snapshot {
            usageAccessGranted = granted
            if (!granted) hardOffLocked("usage_access_revoked")
            return snapshotLocked()
        }

'''
manual36 = usage_block + '''        /** Stage42 functional authority: explicit user ON/OFF, no package-presence prerequisite. */
        @Synchronized
        fun setManualAuthority(enabledNow: Boolean): Snapshot {
            selected = emptySet()
            armed.clear()
            resumed.clear()
            foregroundServices.clear()
            seenForegroundServicePositive.clear()
            activityStoppedAfterPositive.clear()
            foregroundServiceStoppedAfterPositive.clear()
            usageAccessGranted = enabledNow
            if (enabledNow) {
                if (!enabled) {
                    enabled = true
                    readingEpoch += 1L
                    Metrics.increment("activationOn")
                }
                reason = "stage42_manual_user_on"
                Metrics.increment("stage42ManualOnRefresh")
            } else {
                hardOffLocked("stage42_manual_user_off")
                Metrics.increment("stage42ManualOffRefresh")
            }
            return snapshotLocked()
        }

'''
s36 = once(s36, usage_block, manual36, 'Stage36 manual authority method')
STAGE36.write_text(s36)

service = SERVICE.read_text()
manual_refresh = r'''    private fun refreshReadingActivationStage26(
        eventPackageStage26: String?,
        eventTypeStage26: Int,
    ): FarolReadingActivationStage26.ActivationSnapshot {
        val startedStage42 = SystemClock.elapsedRealtimeNanos()
        @Suppress("UNUSED_VARIABLE") val provenanceOnlyStage42 = eventPackageStage26 to eventTypeStage26
        val manualEnabledStage42 = FarolManualReadingAuthorityStage42.isEnabled(currentSettings)

        // Stage42: no SelectedRideAppStore, UsageEvents, running processes or selected-window scan
        // is allowed to participate in functional ON/OFF. Stage30/40 presence remains shadow only.
        if (::stage36RuntimeAuthority.isInitialized) {
            stage36RuntimeAuthority.setManualAuthority(manualEnabledStage42)
        }
        val snapshotStage42 = stage26ReadingActivation.setManualAuthority(manualEnabledStage42)

        if (snapshotStage42.enabled != stage28LastActivationEnabled) {
            FarolCausalLatencyStage28.Metrics.increment(if (snapshotStage42.enabled) "activationOn" else "activationOff")
            stage28LastActivationEnabled = snapshotStage42.enabled
        }
        FarolCausalLatencyStage28.Metrics.setGauge("selectedAppsActiveCount", 0L)
        FarolCausalLatencyStage28.Metrics.setGauge("activationGeneration", snapshotStage42.generation)
        FarolCausalLatencyStage28.Metrics.sample(
            "eventToActivationState",
            SystemClock.elapsedRealtimeNanos() - startedStage42,
        )
        return snapshotStage42
    }

'''
service = between(
    service,
    '    private fun currentSelectedWindowPackagesStage40(',
    '    private fun applyReadingOffStage26(',
    manual_refresh,
    'replace selected-app activation with manual authority',
)

# Sync the manual authority immediately when Home/grid changes the setting, before Stage40 decides color.
work_start = '''    private fun applyWorkModeRuntime0162(enabled0162: Boolean, force0162: Boolean = false) {
        if (!force0162 && workModeRuntimeActive0162 == enabled0162) return
'''
work_start_new = '''    private fun applyWorkModeRuntime0162(enabled0162: Boolean, force0162: Boolean = false) {
        if (::stage36RuntimeAuthority.isInitialized) stage36RuntimeAuthority.setManualAuthority(enabled0162)
        stage26ReadingActivation.setManualAuthority(enabled0162)
        if (!force0162 && workModeRuntimeActive0162 == enabled0162) return
'''
service = once(service, work_start, work_start_new, 'work mode manual authority sync')

# OFF must keep the public FAROL visible as gray/no-km instead of removing the overlay.
work_a = service.index('    private fun applyWorkModeRuntime0162(')
work_b = service.index('    private fun ensureDriverCardSession0162(', work_a)
work_block = service[work_a:work_b]
if work_block.count('        removeOverlay()\n') != 1:
    raise SystemExit(f'work mode OFF overlay: expected 1 removeOverlay, got {work_block.count("        removeOverlay()\\n")}')
work_block = work_block.replace('        removeOverlay()\n', '        showOverlay(RadarColor.Idle, null)\n', 1)
service = service[:work_a] + work_block + service[work_b:]

# User-facing floating-grid action uses the same authority and naming.
service = once(
    service,
    '            if (enabled0162) "Modo Trabalho ATIVADO" else "Modo Trabalho DESLIGADO",\n',
    '            if (enabled0162) "Leitura do Farol ATIVADA" else "Leitura do Farol DESLIGADA",\n',
    'grid reading toggle toast',
)
service = once(
    service,
    '        toast("Modo Trabalho desligado. Abra o Rota Certa para ligar novamente.")\n',
    '        toast("Leitura do Farol desligada. Use a Home ou a grade para ligar novamente.")\n',
    'stop action user text',
)
SERVICE.write_text(service)

# Promote the existing reading module into the Home catalog; its own executable ToggleReading spec
# then automatically becomes addable/removable in the floating shortcut grid.
bubble = BUBBLE.read_text()
bubble = once(
    bubble,
    '        RouteBubbleShortcutModule,\n        DestinationBubbleShortcutModule,\n',
    '        RouteBubbleShortcutModule,\n        ReadingBubbleShortcutModule,\n        DestinationBubbleShortcutModule,\n',
    'reading module catalog',
)
bubble = once(
    bubble,
    '        require(modules.size >= 21) { "A Home deve conter o catálogo completo de módulos." }\n',
    '        require(modules.size >= 22) { "A Home deve conter o catálogo completo de módulos, incluindo Leitura." }\n',
    'home module count',
)
BUBBLE.write_text(bubble)

reading = READING_MODULE.read_text()
reading = once(reading, '        emoji = "👁",\n        label = "Leitura",\n', '        emoji = "👁",\n        label = "Leitura do Farol",\n        displayLabel = "Leitura",\n', 'reading module label')
reading = once(reading, '        targetGroup = "access",\n', '        targetGroup = "general",\n', 'reading module group')
READING_MODULE.write_text(reading)

policy = SHORTCUT_POLICY.read_text()
policy = once(
    policy,
    '        "route" -> "Liga, pausa e acompanha o funcionamento geral do Rota Certa."\n',
    '        "route" -> "Configura a rota e acompanha o funcionamento geral do Rota Certa."\n'
    '        "reading" -> "Liga ou desliga manualmente a leitura universal de qualquer tela, janela ou pop-up."\n',
    'reading shortcut description',
)
policy = once(
    policy,
    '        "manual_capture" -> "Gerencia aplicativos autorizados, cards e captura manual."\n',
    '        "manual_capture" -> "Gerencia referências de aplicativos/cards e captura manual; não liga nem bloqueia a leitura do Farol."\n',
    'legacy selected apps no authority description',
)
policy = once(
    policy,
    '        "stop_app" -> "Abre o controle para desligar o Modo Trabalho com segurança."\n',
    '        "stop_app" -> "Abre o controle para desligar a Leitura do Farol com segurança."\n',
    'stop action manual reading description',
)
SHORTCUT_POLICY.write_text(policy)

main = MAIN.read_text()
case_anchor = '''                                BubbleShortcutAction.OpenScreenWhatsApp -> InlineModuleAction0174(
'''
manual_case = '''                                BubbleShortcutAction.ToggleReading -> ManualReadingHomeModuleStage42(
                                    settings = settings,
                                    accessibilityGranted = liveEnabled,
                                    onChange = { updated -> scope.launch { repository.saveSettings(updated) } },
                                    onOpenAccessibilitySettings = {
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    },
                                )

'''
main = once(main, case_anchor, manual_case + case_anchor, 'Home reading module content')

# Rename the existing master switch: it already persists both appEnabled/liveReadingEnabled atomically.
main = once(
    main,
    '    BUBBLE_GROUP_GENERAL -> "Use o Modo Trabalho para ligar ou pausar todo o processamento."\n',
    '    BUBBLE_GROUP_GENERAL -> "Use Leitura do Farol para ligar ou pausar manualmente o processamento visual."\n',
    'general group manual reading text',
)
main = once(main, '            label = "Modo Trabalho",\n', '            label = "Leitura do Farol",\n', 'master switch label')
main = once(
    main,
    '            "Ligue somente ao iniciar o trabalho. Desligado, o farol, OCR, captura automática, rotas e avisos ficam em espera.",\n',
    '            "Ligue ao iniciar o trabalho. ON lê qualquer tela, janela ou pop-up por eventos; OFF mantém a bolinha cinza e não calcula km.",\n',
    'master switch explanation',
)
main = once(
    main,
    '                "Modo Trabalho ATIVO: leitura por eventos e recuperação automática prontas."\n',
    '                "Leitura ATIVA: qualquer aplicativo visível pode fornecer dois ou mais endereços ao Farol."\n',
    'active status text',
)
main = once(
    main,
    '                "Modo Trabalho DESLIGADO: nenhum card, screenshot ou rota é processado."\n',
    '                "Leitura DESLIGADA: bolinha cinza, sem OCR, screenshot automático, rota ou km."\n',
    'off status text',
)

# Dedicated Home module content. Keep accessibility permission separate from the manual functional toggle.
insert_before = '@Composable\nprivate fun BubbleSettingsCard(settings: AppSettings, onChange: (AppSettings) -> Unit) {'
manual_composable = r'''@Composable
private fun ManualReadingHomeModuleStage42(
    settings: AppSettings,
    accessibilityGranted: Boolean,
    onChange: (AppSettings) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val enabledStage42 = FarolManualReadingAuthorityStage42.isEnabled(settings)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSwitchRow(
            label = "Leitura do Farol",
            checked = enabledStage42,
            onCheckedChange = { enabled ->
                onChange(FarolManualReadingAuthorityStage42.setEnabled(settings, enabled))
            },
        )
        Text(
            if (enabledStage42) {
                "ON: a bolinha fica armada e procura dois ou mais endereços em qualquer tela, janela ou pop-up, sem depender de Uber, 99 ou inDrive estarem abertos."
            } else {
                "OFF: a bolinha permanece cinza, sem km e sem processamento visual automático."
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "O atalho Leitura deste módulo pode ser adicionado à grade flutuante e executa o mesmo liga/desliga com um toque.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (!accessibilityGranted) {
            Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
                Text("Conceder permissão de acessibilidade")
            }
            Text(
                "O toggle pode ficar ON, mas a leitura só acontece enquanto o serviço de acessibilidade do Rota Certa estiver autorizado pelo Android.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

'''
main = once(main, insert_before, manual_composable + insert_before, 'manual reading Home composable')
MAIN.write_text(main)

print('stage42_manual_universal_reading=PASS')
