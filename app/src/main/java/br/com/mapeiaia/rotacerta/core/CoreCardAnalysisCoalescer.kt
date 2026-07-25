package br.com.mapeiaia.rotacerta.core

/**
 * Impede que pequenas variacoes de OCR do mesmo card reiniciem a rota sem parar.
 *
 * A identidade vem da assinatura estavel do card (pacote + modelo + destino + valor),
 * nunca do hash bruto da tela. Um card realmente diferente ainda cancela a analise
 * anterior imediatamente.
 */
class CoreCardAnalysisCoalescer {
    private var activeSignature: String? = null
    private var completedSignature: String? = null

    @Synchronized
    fun beforeStart(
        signature: String?,
        activeJob: Boolean,
        hasAppliedDecision: Boolean,
    ): CoreCardAnalysisAction {
        val normalized = signature.normalizedSignature()
        if (normalized != null && activeJob && normalized == activeSignature) {
            return CoreCardAnalysisAction.CoalesceActive
        }
        if (normalized != null && hasAppliedDecision && normalized == completedSignature) {
            return CoreCardAnalysisAction.ReuseCompleted
        }
        activeSignature = normalized
        return CoreCardAnalysisAction.Start
    }

    @Synchronized
    fun complete(signature: String?) {
        val normalized = signature.normalizedSignature() ?: return
        completedSignature = normalized
        if (activeSignature == normalized) activeSignature = null
    }

    @Synchronized
    fun finish(signature: String?) {
        val normalized = signature.normalizedSignature()
        if (normalized == null || activeSignature == normalized) activeSignature = null
    }

    @Synchronized
    fun invalidate() {
        activeSignature = null
        completedSignature = null
    }

    @Synchronized
    fun isCurrent(signature: String?): Boolean {
        val normalized = signature.normalizedSignature()
        return normalized == null || activeSignature == null || activeSignature == normalized
    }

    private fun String?.normalizedSignature(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }
}

enum class CoreCardAnalysisAction {
    Start,
    CoalesceActive,
    ReuseCompleted,
}
