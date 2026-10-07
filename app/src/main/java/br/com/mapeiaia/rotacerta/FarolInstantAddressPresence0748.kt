package br.com.mapeiaia.rotacerta

/**
 * 0.1.748 — detector ultrarrápido de presença de endereço.
 *
 * A presença positiva arma o pipeline; uma observação negativa isolada nunca revoga
 * uma presença já confirmada. A decisão final de destino/cor continua pertencendo
 * ao pipeline visual, geocodificação local e rota rodoviária existentes.
 */
object FarolInstantAddressPresence0748 {
    const val CONTRACT_MARKER = "FAROL_INSTANT_ADDRESS_PRESENCE_0748"
    const val EVENT_DRIVEN_MARKER = "ADDRESS_PRESENCE_EVENT_DRIVEN_NO_POLLING_0748"
    const val MONOTONIC_MARKER = "NEGATIVE_OBSERVATION_CANNOT_REVOKE_POSITIVE_PRESENCE_0748"
    const val OPENAI_ARBITER_MARKER = "OPENAI_ONLY_AFTER_LOCAL_AMBIGUITY_0748"

    data class Decision(
        val observedNow: Boolean,
        val armPipeline: Boolean,
        val retainedPositive: Boolean,
        val addressSignature: String?,
        val addressCount: Int,
        val reason: String,
    )

    class Gate {
        private var latchedKey: String? = null

        fun observe(
            evaluation: FarolUniversalVisualPipelineStage19.Evaluation?,
            windowId: Int,
        ): Decision {
            if (evaluation == null) {
                return Decision(
                    observedNow = false,
                    armPipeline = false,
                    retainedPositive = latchedKey != null,
                    addressSignature = latchedKey?.substringAfter('|'),
                    addressCount = 0,
                    reason = if (latchedKey == null) "no_positive_address" else "negative_observation_preserves_positive",
                )
            }
            val signature = evaluation.addressSignature.trim()
            if (signature.isBlank()) {
                return Decision(false, false, latchedKey != null, null, 0, "blank_signature_preserves_positive")
            }
            val key = "$windowId|$signature"
            val changed = key != latchedKey
            latchedKey = key
            return Decision(
                observedNow = true,
                armPipeline = changed,
                retainedPositive = true,
                addressSignature = signature,
                addressCount = evaluation.addresses.size,
                reason = if (changed) "positive_address_arms_pipeline" else "same_positive_address_coalesced",
            )
        }

        fun clear() {
            latchedKey = null
        }
    }

    fun detect(
        text: String,
        windowId: Int,
        source: FarolUniversalVisualPipelineStage19.Source = FarolUniversalVisualPipelineStage19.Source.Accessibility,
    ): FarolUniversalVisualPipelineStage19.Evaluation? {
        if (text.isBlank()) return null
        val block = FarolUniversalVisualPipelineStage19.VisualBlock(
            id = "stage748-presence:$windowId",
            windowId = windowId,
            windowLayer = Int.MAX_VALUE,
            depth = 1,
            text = text.take(2400),
            source = source,
            syntheticRoot = false,
        )
        return FarolRouteLocationEvidenceStage46R8.evaluate(listOf(block))
    }
}
