package br.com.mapeiaia.rotacerta

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.serialization.SerializationException

/**
 * 0.1.699: provider/network failures are operationally recoverable and can never become
 * card-authority failures by themselves.
 *
 * Late network work is tagged with the Stage36 reading/card lease so diagnostics can prove
 * which logical destination owned a request. Freshness is still decided by Stage36 in the
 * accessibility service after the blocking provider returns.
 */
object FarolNetworkFailureIsolation0699 {
    const val CONTRACT_MARKER = "FAROL_NETWORK_FAILURE_ISOLATION_0699"
    const val GOOGLE_GEOCODE_STARTED_MARKER = "FAROL_GOOGLE_GEOCODE_STARTED_0699"
    const val GOOGLE_GEOCODE_TRANSPORT_FAILED_MARKER = "FAROL_GOOGLE_GEOCODE_TRANSPORT_FAILED_0699"
    const val GOOGLE_GEOCODE_UNAVAILABLE_MARKER = "FAROL_GOOGLE_GEOCODE_UNAVAILABLE_0699"
    const val LATE_RESULT_DROPPED_MARKER = "FAROL_NETWORK_LATE_RESULT_DROPPED_0699"
    const val REMOTE_REFINEMENT_FAILED_SOFT_MARKER = "FAROL_REMOTE_REFINEMENT_FAILED_SOFT_0699"
    const val NETWORK_FAILURE_STATE_PRESERVED_MARKER = "FAROL_NETWORK_FAILURE_STATE_PRESERVED_0699"

    data class RequestContext(
        val traceId: String?,
        val operationId: String?,
        val readingEpoch: Long,
        val leaseId: Long,
        val destinationKey: String?,
        val destinationAddress: String?,
    ) {
        fun diagnostic(provider: String, extra: String = ""): String = buildString {
            append("provider=").append(provider)
            append("; trace=").append(traceId.orEmpty())
            append("; operation=").append(operationId.orEmpty())
            append("; epoch=").append(readingEpoch)
            append("; lease=").append(leaseId)
            append("; destinationKey=").append(destinationKey.orEmpty().take(220))
            append("; destination=").append(destinationAddress.orEmpty().take(220))
            if (extra.isNotBlank()) append("; ").append(extra)
        }
    }

    fun requestContext(
        token: FarolRuntimeAuthorityStage36.WorkToken?,
        traceId: String?,
        operationId: String?,
        destinationAddress: String?,
    ): RequestContext = RequestContext(
        traceId = traceId,
        operationId = operationId,
        readingEpoch = token?.readingEpoch ?: -1L,
        leaseId = token?.leaseId ?: -1L,
        destinationKey = token?.destinationKey,
        destinationAddress = destinationAddress,
    )

    /**
     * Cancellation remains structural control flow and must propagate. IOException represents
     * transport/provider unavailability. Malformed provider payloads are also provider failures.
     */
    fun isRecoverableProviderFailure(error: Throwable): Boolean {
        if (error is CancellationException) return false
        return generateSequence<Throwable>(error) { it.cause }
            .any { cause ->
                cause is IOException || cause is SerializationException
            }
    }

    fun failureChain(error: Throwable): String =
        generateSequence<Throwable>(error) { it.cause }
            .take(4)
            .joinToString(" -> ") { it::class.java.simpleName }
}
