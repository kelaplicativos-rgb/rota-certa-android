package br.com.mapeiaia.rotacerta

object BubbleLifecycleGuard {
    fun shouldIgnoreOcrText(text: String): Boolean =
        RideTextSanitizer.containsRotaCertaOverlay(text)

    fun screenChangeReason(activeSession: BubbleCardSession?, newSnapshotHash: Int?): String = when {
        activeSession == null -> "Nova tela detectada; aguardando card cadastrado."
        newSnapshotHash == null -> "Card cadastrado saiu da tela; bolinha limpa imediatamente."
        activeSession.snapshotHash != newSnapshotHash -> "Card mudou; limpei a bolinha antes de ler o proximo card."
        else -> "Mesmo card ainda ativo."
    }

    fun shouldResetOnEmptyText(hasActiveSession: Boolean, allowPopupCandidate: Boolean): Boolean =
        hasActiveSession && !allowPopupCandidate
}
