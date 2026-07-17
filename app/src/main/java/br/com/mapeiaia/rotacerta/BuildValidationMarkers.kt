package br.com.mapeiaia.rotacerta

/**
 * Marcadores mantidos intencionalmente no APK para a inspeção do GitHub Actions.
 * Eles não executam lógica nem geram logs; apenas comprovam que o binário contém
 * os contratos críticos validados no código e nos testes.
 */
object BuildValidationMarkers {
    const val PASSIVE_EVENT_PRESERVES_MONITORED_ROOT = "passive_event_preserves_monitored_root_0_1_88"
    const val REAL_FAROL_ROUTE_GATE = "real_farol_route_gate_0_1_88"

    const val IN_APP_CENTER = "Central de bolinhas"
    const val ACCESSIBILITY_OFF = "Acessibilidade OFF: toque em Acesso OFF ou Leitura OFF"
    const val LESS_THAN_TWO_CLEAR = "Menos de dois enderecos visiveis; dado anterior removido."
    const val UNIVERSAL_TRIGGER = "universal.trigger source="
    const val SCREEN_CHANGED_YELLOW = "universal.screen.changed hash="
    const val ROUTE_CACHE_HIT = "universal.route.cache hit=true"
    const val ROUTE_CACHE_STORED = "universal.route.cache stored=true"
    const val STALE_RESULT_DISCARDED = "universal.result discarded_stale=true"
    const val IMMEDIATE_CLEAR = "universal.clear immediate=true reason="
    const val OVERLAY_WINDOW_ROUTE_FIX = "universal_overlay_self_window_fix_0_1_106"

    const val LIVE_READING_DISABLED = "Leitura ao vivo desligada pela bolinha"
    const val TARGETS_DISABLED = "Casa e Alfinete estao desligados"
    const val MAIN_TAP_MENU = "bubble.tap.menu_contract_0_1_96"
    const val GRID_OPENED = "bubble.menu.opened grid=true"
}
