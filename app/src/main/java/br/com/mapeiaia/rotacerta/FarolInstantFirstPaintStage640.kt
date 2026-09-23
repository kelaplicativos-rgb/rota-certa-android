package br.com.mapeiaia.rotacerta

/**
 * Stage640 — assinatura como porta de entrada, lease semântico como autoridade de continuidade.
 *
 * O Stage639 corretamente fechou o pipeline para apps sem modelo ou snapshots nunca admitidos,
 * porém reaplicava o matcher estrutural em TODO evento. Durante animações do card um frame parcial
 * podia falhar no matcher, executar master reset e matar geocode/rota que já pertenciam ao card
 * confirmado. O resultado era exatamente o atraso físico observado: bolinha amarela por segundos
 * apesar de a decisão já estar em andamento.
 *
 * Stage640 mantém o fail-closed somente na entrada:
 *  - sem modelo => bloqueia;
 *  - sem assinatura e sem lease semântico => bloqueia;
 *  - assinatura exata => admite a primeira leitura;
 *  - depois que Stage19 vinculou um destino à MESMA superfície, eventos dessa superfície continuam
 *    chegando ao Stage46/Stage47 sem repetir o matcher. Esses estágios continuam responsáveis por
 *    provar troca, feed, desaparecimento e por invalidar o lease.
 *
 * Assim uma mutação transitória não cancela trabalho válido, mas um card novo ainda precisa passar
 * pela assinatura antes de criar um novo lease.
 */
object FarolInstantFirstPaintStage640 {
    const val CONTRACT_MARKER = "FAROL_INSTANT_FIRST_PAINT_STAGE640"
    const val ENTRY_FAIL_CLOSED_MARKER = "SIGNATURE_FAIL_CLOSED_ONLY_BEFORE_SEMANTIC_LEASE_STAGE640"
    const val LEASE_CONTINUITY_MARKER = "ACTIVE_SEMANTIC_LEASE_BYPASSES_REPEAT_SIGNATURE_MATCH_STAGE640"
    const val FIRST_FEEDBACK_MARKER = "EXACT_SIGNATURE_PAINTS_WAITING_BEFORE_HEAVY_PIPELINE_STAGE640"
    const val COLOR_BEFORE_KM_MARKER = "GREEN_RED_BEFORE_EXACT_ROAD_KM_STAGE640"

    enum class Outcome {
        ALLOW_EXACT_SIGNATURE,
        ALLOW_ACTIVE_SEMANTIC_LEASE,
        BLOCK_UNTRAINED,
        BLOCK_SIGNATURE_MISS,
    }

    data class Decision(
        val outcome: Outcome,
        val allowHeavyPipeline: Boolean,
        val hardReset: Boolean,
        val paintWaitingImmediately: Boolean,
    )

    fun decide(
        hasModels: Boolean,
        signatureMatched: Boolean,
        activeSemanticLeaseSameSurface: Boolean,
        hasFinalPublicDecision: Boolean,
    ): Decision = when {
        !hasModels -> Decision(
            outcome = Outcome.BLOCK_UNTRAINED,
            allowHeavyPipeline = false,
            hardReset = true,
            paintWaitingImmediately = false,
        )
        signatureMatched -> Decision(
            outcome = Outcome.ALLOW_EXACT_SIGNATURE,
            allowHeavyPipeline = true,
            hardReset = false,
            // A Green/Red já pertencente a outro card não deve piscar amarelo antes de Stage46
            // provar a substituição. Em estado neutro, porém, o motorista recebe feedback no mesmo
            // evento que confirmou a assinatura.
            paintWaitingImmediately = !hasFinalPublicDecision,
        )
        activeSemanticLeaseSameSurface -> Decision(
            outcome = Outcome.ALLOW_ACTIVE_SEMANTIC_LEASE,
            allowHeavyPipeline = true,
            hardReset = false,
            paintWaitingImmediately = false,
        )
        else -> Decision(
            outcome = Outcome.BLOCK_SIGNATURE_MISS,
            allowHeavyPipeline = false,
            hardReset = true,
            paintWaitingImmediately = false,
        )
    }
}
