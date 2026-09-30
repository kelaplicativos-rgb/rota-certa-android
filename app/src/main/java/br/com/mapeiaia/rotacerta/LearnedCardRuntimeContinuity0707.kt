package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * 0.1.707 — continuidade curta da identidade runtime já confirmada.
 *
 * A lease só impede um hard-clear transitório enquanto o mesmo package aprendido
 * continua visualmente ativo. Ela NÃO autoriza rota, destino, cor, raio ou quilometragem.
 */
object LearnedCardRuntimeContinuity0707 {
    const val CONTRACT_MARKER = "LEARNED_CARD_RUNTIME_CONTINUITY_0707"
    const val HOLD_MARKER = "LEARNED_CARD_RUNTIME_HOLD_0707"
    const val EXPIRED_MARKER = "LEARNED_CARD_RUNTIME_HOLD_EXPIRED_0707"
    const val LEASE_MS = 1_800L

    data class Lease(
        val packageName: String,
        val matchedAtElapsedMillis: Long,
    )

    fun renew(
        packageName: String?,
        nowElapsedMillis: Long,
    ): Lease? {
        val normalized = normalize(packageName) ?: return null
        return Lease(normalized, nowElapsedMillis)
    }

    fun shouldHold(
        lease: Lease?,
        packageName: String?,
        nowElapsedMillis: Long,
        surfaceActive: Boolean,
    ): Boolean {
        if (!surfaceActive || lease == null) return false
        val normalized = normalize(packageName) ?: return false
        if (lease.packageName != normalized) return false
        val age = nowElapsedMillis - lease.matchedAtElapsedMillis
        return age in 0L..LEASE_MS
    }

    private fun normalize(value: String?): String? =
        value?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)
}
