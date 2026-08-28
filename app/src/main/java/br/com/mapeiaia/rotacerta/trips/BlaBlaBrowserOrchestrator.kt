package br.com.mapeiaia.rotacerta.trips

/**
 * The single browser execution authority.
 *
 * Scripts never invoke another script. Navigation/capture code asks this
 * orchestrator to start exactly one request and every asynchronous callback is
 * checked against the token that was current when the request started.
 */
internal class BlaBlaBrowserOrchestrator {
    private var generation = 0L
    private var active: BlaBlaBrowserRequestToken? = null

    fun start(
        request: BlaBlaBrowserRequest,
        context: BlaBlaBrowserExecutionContext,
        reason: String = "",
    ): BlaBlaBrowserRequestToken {
        generation += 1L
        return BlaBlaBrowserRequestToken(
            generation = generation,
            request = request,
            context = context,
            reason = reason,
        ).also { active = it }
    }

    fun startOrReuse(
        request: BlaBlaBrowserRequest,
        context: BlaBlaBrowserExecutionContext,
        reason: String = "",
    ): BlaBlaBrowserRequestToken {
        val current = active
        if (current != null && current.request == request && contextsCompatible(current.context, context)) {
            return current
        }
        return start(request, context, reason)
    }

    fun current(): BlaBlaBrowserRequestToken? = active

    fun isCurrent(
        token: BlaBlaBrowserRequestToken,
        currentContext: BlaBlaBrowserExecutionContext,
    ): Boolean {
        val current = active ?: return false
        return current.generation == token.generation &&
            current.request == token.request &&
            contextsCompatible(token.context, currentContext)
    }

    fun finish(token: BlaBlaBrowserRequestToken): Boolean {
        val current = active ?: return false
        if (current.generation != token.generation || current.request != token.request) return false
        active = null
        return true
    }

    fun cancel() {
        generation += 1L
        active = null
    }

    private fun contextsCompatible(
        expected: BlaBlaBrowserExecutionContext,
        actual: BlaBlaBrowserExecutionContext,
    ): Boolean {
        if (expected.accountId != actual.accountId) return false
        if (expected.syncGeneration != actual.syncGeneration) return false
        if (expected.navigationGeneration != actual.navigationGeneration) return false
        if (expected.cardKey.isNotBlank() && actual.cardKey.isNotBlank() && expected.cardKey != actual.cardKey) return false
        if (expected.tripId.isNotBlank() && actual.tripId.isNotBlank() && !expected.tripId.equals(actual.tripId, true)) return false
        if (expected.passengerKey.isNotBlank() && actual.passengerKey.isNotBlank() && expected.passengerKey != actual.passengerKey) return false
        return true
    }
}
