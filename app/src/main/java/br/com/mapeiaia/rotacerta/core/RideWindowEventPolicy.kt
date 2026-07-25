package br.com.mapeiaia.rotacerta.core

/**
 * Decide o que fazer quando um evento de acessibilidade vem de SystemUI,
 * teclado, launcher ou outra sobreposicao enquanto o app de corrida continua
 * sendo a janela raiz real.
 *
 * Regra principal: um evento passivo nunca pode apagar a leitura em andamento
 * de uma janela monitorada. O card so deve morrer quando a propria janela raiz
 * deixa de ser o app de corrida.
 */
object RideWindowEventPolicy {
    fun decide(
        eventPackageIsMonitored: Boolean,
        rootPackageIsMonitored: Boolean,
        eventPackageIsPassive: Boolean,
        hasActiveRegisteredDecision: Boolean,
    ): RideWindowEventAction = when {
        eventPackageIsMonitored -> RideWindowEventAction.AnalyzeRideWindow
        rootPackageIsMonitored -> RideWindowEventAction.PreserveMonitoredRoot
        eventPackageIsPassive && hasActiveRegisteredDecision -> RideWindowEventAction.PreserveMonitoredRoot
        eventPackageIsPassive -> RideWindowEventAction.ResetIdle
        else -> RideWindowEventAction.IgnoreBlockedEvent
    }
}

enum class RideWindowEventAction {
    AnalyzeRideWindow,
    PreserveMonitoredRoot,
    ResetIdle,
    IgnoreBlockedEvent,
}
