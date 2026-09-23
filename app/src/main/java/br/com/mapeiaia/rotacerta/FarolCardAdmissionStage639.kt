package br.com.mapeiaia.rotacerta

/**
 * Autoridade fail-closed para o Farol Card Signature.
 *
 * Stage638 mantinha compatibilidade com o pipeline legado quando ainda não havia
 * modelo treinado. A evidência física 0.1.638 mostrou que isso permite o OCR
 * reconstruir uma decisão logo após o reset ao ler endereços do feed.
 *
 * Stage639 remove essa exceção: o pipeline pesado só pode iniciar quando existe
 * ao menos um modelo local e o snapshot estrutural atual corresponde ao modelo.
 */
object FarolCardAdmissionStage639 {
    enum class Outcome {
        ALLOW_MATCHED,
        BLOCK_UNTRAINED,
        BLOCK_SIGNATURE_MISS,
    }

    data class Decision(
        val outcome: Outcome,
        val allowHeavyPipeline: Boolean,
    )

    fun decide(
        hasModels: Boolean,
        signatureMatched: Boolean,
    ): Decision = when {
        !hasModels -> Decision(Outcome.BLOCK_UNTRAINED, false)
        !signatureMatched -> Decision(Outcome.BLOCK_SIGNATURE_MISS, false)
        else -> Decision(Outcome.ALLOW_MATCHED, true)
    }
}
