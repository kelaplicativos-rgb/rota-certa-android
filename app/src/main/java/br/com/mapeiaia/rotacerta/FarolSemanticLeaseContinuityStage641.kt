package br.com.mapeiaia.rotacerta

/**
 * Stage641 — continuidade semântica não pode depender de windowId bruto.
 *
 * Accessibility windows podem ser recriadas durante animações e recomposição do mesmo card.
 * O Stage640 ainda exigia igualdade de windowId para preservar o lease e, com isso, voltava ao
 * matcher estrutural durante o mesmo card. Um frame transitório podia falhar no matcher e executar
 * master reset enquanto rota/geocode válidos ainda estavam em andamento.
 *
 * Stage641 separa três responsabilidades:
 * 1) assinatura estrutural continua sendo a porta de entrada de um card sem lease;
 * 2) lease semântico ativo da mesma aplicação mantém continuidade por pacote, não por windowId;
 * 3) signature miss sem nenhum estado destrutivo vira no-op idempotente, evitando tempestade de
 *    hard-clears na main thread enquanto o usuário permanece em feed/tela sem card admitido.
 */
object FarolSemanticLeaseContinuityStage641 {
    const val CONTRACT_MARKER = "FAROL_SEMANTIC_LEASE_CONTINUITY_STAGE641"
    const val WINDOW_ID_NOT_AUTHORITY = "RAW_WINDOW_ID_NEVER_REVOKES_ACTIVE_SEMANTIC_LEASE_STAGE641"
    const val ENTRY_SIGNATURE_ONLY = "SIGNATURE_REQUIRED_ONLY_BEFORE_SEMANTIC_LEASE_STAGE641"
    const val IDLE_MISS_NOOP = "IDLE_SIGNATURE_MISS_NO_HARD_CLEAR_STAGE641"
    const val NOTIFICATION_NO_RESET = "NOTIFICATION_MISS_NEVER_RESETS_ACTIVE_LEASE_STAGE641"

    fun activeLeaseForSameApp(
        hasModels: Boolean,
        admittedPackage: String?,
        authorityPackage: String?,
        activeAddressSignature: String?,
    ): Boolean {
        if (!hasModels || activeAddressSignature.isNullOrBlank()) return false
        val admitted = SelectedRideAppStore.normalize(admittedPackage) ?: return false
        val authority = SelectedRideAppStore.normalize(authorityPackage) ?: return false
        return admitted == authority
    }

    enum class BlockAction {
        HARD_RESET,
        PAINT_WAITING_ONLY,
        NOOP,
    }

    fun blockAction(
        hasDestructiveState: Boolean,
        alreadyWaitingYellow: Boolean,
    ): BlockAction = when {
        hasDestructiveState -> BlockAction.HARD_RESET
        !alreadyWaitingYellow -> BlockAction.PAINT_WAITING_ONLY
        else -> BlockAction.NOOP
    }
}
