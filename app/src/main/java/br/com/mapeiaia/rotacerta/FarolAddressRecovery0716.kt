package br.com.mapeiaia.rotacerta

/**
 * 0.1.716 — coordena a escalada paga somente depois de falha local persistente
 * e preserva o mesmo card durante uma recuperação em andamento.
 */
class FarolAddressRecovery0716 {
    enum class CoordinateDecision {
        WAIT_LOCAL_RETRY,
        ESCALATE_PAID_ADDRESS,
        ALREADY_ESCALATED,
    }

    private var binding: String? = null
    private var firstFailureElapsed: Long = 0L
    private var attempts: Int = 0
    private var escalated: Boolean = false

    @Synchronized
    fun onCoordinateFailure(addressSignature: String, nowElapsedMillis: Long): CoordinateDecision {
        val normalized = addressSignature.trim()
        if (normalized.isBlank()) return CoordinateDecision.WAIT_LOCAL_RETRY

        if (binding != normalized) {
            binding = normalized
            firstFailureElapsed = nowElapsedMillis
            attempts = 1
            escalated = false
            return CoordinateDecision.WAIT_LOCAL_RETRY
        }

        attempts += 1
        if (escalated) return CoordinateDecision.ALREADY_ESCALATED

        val age = (nowElapsedMillis - firstFailureElapsed).coerceAtLeast(0L)
        if (attempts >= MIN_FAILURE_ATTEMPTS && age >= LOCAL_RETRY_GRACE_MILLIS) {
            escalated = true
            return CoordinateDecision.ESCALATE_PAID_ADDRESS
        }
        return CoordinateDecision.WAIT_LOCAL_RETRY
    }

    @Synchronized
    fun onCoordinateResolved(addressSignature: String) {
        if (binding == addressSignature) resetLocked()
    }

    @Synchronized
    fun hasRecoveryLease(addressSignature: String?, nowElapsedMillis: Long): Boolean {
        val expected = binding ?: return false
        if (addressSignature.isNullOrBlank() || expected != addressSignature) return false
        val age = (nowElapsedMillis - firstFailureElapsed).coerceAtLeast(0L)
        return age <= RECOVERY_LEASE_MILLIS
    }

    @Synchronized
    fun reset() = resetLocked()

    private fun resetLocked() {
        binding = null
        firstFailureElapsed = 0L
        attempts = 0
        escalated = false
    }

    companion object {
        const val CONTRACT_MARKER = "FAROL_ADDRESS_RECOVERY_0716"
        const val WAITING_MARKER = "FAROL_ADDRESS_RECOVERY_WAITING_LOCAL_RETRY_0716"
        const val STARTED_MARKER = "FAROL_PAID_ADDRESS_RECOVERY_STARTED_0716"
        const val HEARTBEAT_PRESERVED_MARKER = "FAROL_HEARTBEAT_SAME_CARD_PRESERVED_0716"
        const val LOCAL_RETRY_GRACE_MILLIS = 1_200L
        const val RECOVERY_LEASE_MILLIS = 10_000L
        private const val MIN_FAILURE_ATTEMPTS = 2

        fun shouldPreserveHeartbeat(
            activeAddressSignature: String?,
            observedAddressSignature: String?,
            activeWindowId: Int?,
            observedWindowId: Int,
            samePackage: Boolean,
            routeInFlight: Boolean,
            recoveryLeaseActive: Boolean,
        ): Boolean {
            if (!samePackage) return false
            if (activeAddressSignature.isNullOrBlank() || observedAddressSignature.isNullOrBlank()) return false
            if (!DestinationAddressIdentityPolicy.sameDestinationSignatures(activeAddressSignature, observedAddressSignature)) return false
            if (activeWindowId != null && activeWindowId != observedWindowId) return false
            return routeInFlight || recoveryLeaseActive
        }
    }
}
