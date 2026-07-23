package br.com.mapeiaia.rotacerta

/** Mantem um card valido durante vazios transitórios da acessibilidade. */
class CardExitConfirmationGate(
    private val requiredMisses: Int = 2,
    private val minimumGapMillis: Long = 70L,
    private val maximumGraceMillis: Long = 420L,
) {
    private var activeSignature: String? = null
    private var firstMissAtMillis: Long = 0L
    private var lastMissAtMillis: Long = 0L
    private var misses: Int = 0

    @Synchronized
    fun observeActive(signature: String) {
        activeSignature = signature
        firstMissAtMillis = 0L
        lastMissAtMillis = 0L
        misses = 0
    }

    @Synchronized
    fun shouldClear(nowMillis: Long): Boolean {
        if (activeSignature == null) return true
        if (firstMissAtMillis == 0L) {
            firstMissAtMillis = nowMillis
            lastMissAtMillis = nowMillis
            misses = 1
            return false
        }
        if (nowMillis - lastMissAtMillis >= minimumGapMillis) {
            misses += 1
            lastMissAtMillis = nowMillis
        }
        return misses >= requiredMisses || nowMillis - firstMissAtMillis >= maximumGraceMillis
    }

    @Synchronized
    fun reset() {
        activeSignature = null
        firstMissAtMillis = 0L
        lastMissAtMillis = 0L
        misses = 0
    }
}

/**
 * Trava o foco no mesmo card quando uma lista de ofertas sofre pequenas
 * reordenações. Só libera o card anterior depois de duas ausências confirmadas.
 */
class PrimaryCardFocusLock(
    private val releaseMisses: Int = 2,
    private val releaseGraceMillis: Long = 360L,
) {
    private var lockedSignature: String? = null
    private var missingSinceMillis: Long = 0L
    private var misses: Int = 0

    @Synchronized
    fun select(text: String, nowMillis: Long = System.currentTimeMillis()): FocusedRideCardDecision {
        val candidates = PrimaryVisibleRideCardSelector.completeCards(text)
        if (candidates.isEmpty()) {
            return onMissing(nowMillis, "nenhum_card_completo")
        }

        val locked = lockedSignature?.let { signature -> candidates.firstOrNull { it.cardSignature == signature } }
        if (locked != null) {
            missingSinceMillis = 0L
            misses = 0
            return FocusedRideCardDecision(locked, holdPrevious = false, reason = "card_bloqueado_ainda_visivel")
        }

        if (lockedSignature != null && !releaseConfirmed(nowMillis)) {
            return FocusedRideCardDecision(null, holdPrevious = true, reason = "aguardando_confirmar_saida_do_card_bloqueado")
        }

        val selected = candidates.first()
        lockedSignature = selected.cardSignature
        missingSinceMillis = 0L
        misses = 0
        return FocusedRideCardDecision(selected, holdPrevious = false, reason = "novo_card_focado")
    }

    @Synchronized
    fun reset() {
        lockedSignature = null
        missingSinceMillis = 0L
        misses = 0
    }

    private fun onMissing(nowMillis: Long, reason: String): FocusedRideCardDecision {
        if (lockedSignature == null) return FocusedRideCardDecision(null, holdPrevious = false, reason = reason)
        return if (releaseConfirmed(nowMillis)) {
            lockedSignature = null
            missingSinceMillis = 0L
            misses = 0
            FocusedRideCardDecision(null, holdPrevious = false, reason = "saida_do_card_confirmada")
        } else {
            FocusedRideCardDecision(null, holdPrevious = true, reason = "vazio_transitorio_card_preservado")
        }
    }

    private fun releaseConfirmed(nowMillis: Long): Boolean {
        if (missingSinceMillis == 0L) missingSinceMillis = nowMillis
        misses += 1
        return misses >= releaseMisses || nowMillis - missingSinceMillis >= releaseGraceMillis
    }
}

data class FocusedRideCardDecision(
    val selection: PrimaryVisibleRideCardSelection?,
    val holdPrevious: Boolean,
    val reason: String,
)
