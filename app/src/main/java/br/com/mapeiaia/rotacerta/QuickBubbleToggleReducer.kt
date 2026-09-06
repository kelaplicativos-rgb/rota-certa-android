package br.com.mapeiaia.rotacerta

/**
 * Fonte unica para o comportamento liga/desliga das bolinhas funcionais.
 * O primeiro toque inverte o estado atual e o segundo toque restaura o estado anterior.
 */
enum class QuickBubbleToggle {
    Rota,
    LiveReading,
    Alerts,
    Appearance,
    HomeTarget,
    AlternativeTarget,
}

object QuickBubbleToggleReducer {
    fun toggle(settings: AppSettings, toggle: QuickBubbleToggle): AppSettings = when (toggle) {
        QuickBubbleToggle.Rota -> settings.copy(appEnabled = !settings.appEnabled)
        QuickBubbleToggle.LiveReading -> settings.copy(liveReadingEnabled = !settings.liveReadingEnabled)
        QuickBubbleToggle.Alerts -> settings.copy(proximityAlertsEnabled = !settings.proximityAlertsEnabled)
        QuickBubbleToggle.Appearance -> settings.copy(bubbleDarkMode = !settings.bubbleDarkMode)
        QuickBubbleToggle.HomeTarget -> settings.copy(homeTargetEnabled = !settings.homeTargetEnabled)
        QuickBubbleToggle.AlternativeTarget -> settings.copy(alternativeTargetEnabled = !settings.alternativeTargetEnabled)
    }
}
