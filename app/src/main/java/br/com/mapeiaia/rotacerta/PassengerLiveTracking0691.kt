package br.com.mapeiaia.rotacerta

/**
 * 0.1.691 — contrato do acompanhamento temporário do passageiro.
 *
 * O link público do passageiro é live-only: não recebe coleção de pontos,
 * encerra no backend após confirmação robusta de chegada e pode ser encaminhado
 * pelo passageiro a familiares de confiança durante a viagem.
 */
internal object PassengerLiveTracking0691 {
    const val MARKER = "PASSENGER_LIVE_ONLY_ARRIVAL_0691"
    const val PUBLIC_MODE = "LIVE_ONLY"
    const val ARRIVAL_REQUIRED_FIXES = 3
    const val ARRIVAL_RADIUS_METERS = 140
    const val FAMILY_FORWARDING_ALLOWED = true
    const val PUBLIC_TRACE_ENABLED = false
}
