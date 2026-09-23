package br.com.mapeiaia.rotacerta

/**
 * Stage644: alertas de proximidade e radares possuem autoridade de runtime própria.
 *
 * O estado da leitura/Farol (appEnabled/liveReadingEnabled) nunca pode armar, desarmar,
 * iniciar, interromper ou ocultar o monitor de alertas. A única chave funcional é
 * proximityAlertsEnabled; presença de alvos decide apenas se há trabalho de localização.
 */
object AlertRuntimePolicy0644 {
    const val CONTRACT_MARKER = "ALERT_RUNTIME_INDEPENDENT_FROM_FAROL_STAGE644"
    const val SOLE_AUTHORITY_MARKER = "PROXIMITY_ALERTS_ENABLED_IS_SOLE_ALERT_AUTHORITY_STAGE644"
    const val READING_OFF_PRESERVES_MARKER = "READING_OFF_PRESERVES_ALERTS_AND_RADARS_STAGE644"

    fun isEnabled(settings: AppSettings): Boolean = settings.proximityAlertsEnabled

    fun shouldTrack(settings: AppSettings, hasTargets: Boolean): Boolean =
        isEnabled(settings) && hasTargets
}
