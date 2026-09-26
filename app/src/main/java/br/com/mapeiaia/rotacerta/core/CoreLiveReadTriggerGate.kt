package br.com.mapeiaia.rotacerta.core

/**
 * Controla a tempestade de eventos de acessibilidade sem atrasar a primeira leitura.
 * Eventos de troca de janela entram imediatamente; atualizacoes repetidas da mesma
 * janela sao agrupadas e o ciclo continuo assume as leituras seguintes.
 */
class CoreLiveReadTriggerGate(
    private val duplicateWindowMs: Long = 120L,
) {
    private var lastEffectivePackage: String? = null
    private var lastAcceptedAtMillis: Long = Long.MIN_VALUE

    @Synchronized
    fun decide(
        eventPackageName: String?,
        rootPackageName: String?,
        eventType: Int,
        eventPackageIsMonitored: Boolean,
        rootPackageIsMonitored: Boolean,
        nowMillis: Long,
    ): CoreLiveReadTriggerDecision {
        if (!eventPackageIsMonitored && !rootPackageIsMonitored) {
            return CoreLiveReadTriggerDecision(
                action = CoreLiveReadTriggerAction.LetPassiveFlow,
                effectivePackageName = eventPackageName ?: rootPackageName,
                reason = "Evento fora de app monitorado deve seguir para limpeza normal.",
            )
        }

        val effectivePackage = when {
            eventPackageIsMonitored -> eventPackageName
            rootPackageIsMonitored -> rootPackageName
            else -> eventPackageName ?: rootPackageName
        }
        val isWindowBoundary = eventType == TYPE_WINDOW_STATE_CHANGED || eventType == TYPE_WINDOWS_CHANGED
        val packageChanged = effectivePackage != lastEffectivePackage
        val elapsed = if (lastAcceptedAtMillis == Long.MIN_VALUE) Long.MAX_VALUE else nowMillis - lastAcceptedAtMillis
        val shouldAnalyze = isWindowBoundary || packageChanged || elapsed >= duplicateWindowMs

        return if (shouldAnalyze) {
            lastEffectivePackage = effectivePackage
            lastAcceptedAtMillis = nowMillis
            CoreLiveReadTriggerDecision(
                action = CoreLiveReadTriggerAction.Analyze,
                effectivePackageName = effectivePackage,
                reason = when {
                    isWindowBoundary -> "Troca de janela exige leitura imediata."
                    packageChanged -> "Pacote monitorado mudou; leitura liberada."
                    else -> "Janela de agrupamento encerrada; leitura liberada."
                },
            )
        } else {
            CoreLiveReadTriggerDecision(
                action = CoreLiveReadTriggerAction.IgnoreDuplicate,
                effectivePackageName = effectivePackage,
                reason = "Evento repetido agrupado; o ciclo continuo fara a proxima leitura.",
            )
        }
    }

    @Synchronized
    fun reset() {
        lastEffectivePackage = null
        lastAcceptedAtMillis = Long.MIN_VALUE
    }

    companion object {
        const val TYPE_WINDOW_STATE_CHANGED = 0x20
        const val TYPE_WINDOWS_CHANGED = 0x400000
    }
}

data class CoreLiveReadTriggerDecision(
    val action: CoreLiveReadTriggerAction,
    val effectivePackageName: String?,
    val reason: String,
)

enum class CoreLiveReadTriggerAction {
    Analyze,
    IgnoreDuplicate,
    LetPassiveFlow,
}
