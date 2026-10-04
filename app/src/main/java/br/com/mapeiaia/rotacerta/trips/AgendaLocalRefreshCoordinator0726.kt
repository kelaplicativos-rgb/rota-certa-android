package br.com.mapeiaia.rotacerta.trips

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/**
 * Serializes local Timeline/Agenda refresh requests.
 *
 * Multiple producers may request refreshes (initial load, lifecycle resume, booking events and
 * UI callbacks), but only this coordinator is allowed to execute the snapshot consumer. Requests
 * arriving in the same short burst are coalesced into one execution; requests arriving while an
 * execution is running are preserved for one follow-up pass.
 */
internal class AgendaLocalRefreshCoordinator0726(
    private val coalesceMillis: Long = 40L,
) {
    private val requests = Channel<String>(capacity = Channel.BUFFERED)

    fun request(reason: String): Boolean =
        requests.trySend(reason.trim().ifBlank { "unspecified" }).isSuccess

    suspend fun run(consumer: suspend (String) -> Unit) {
        for (first in requests) {
            if (coalesceMillis > 0L) delay(coalesceMillis)

            val reasons = linkedSetOf(first)
            while (true) {
                val next = requests.tryReceive().getOrNull() ?: break
                reasons += next
            }
            consumer(reasons.joinToString("+"))
        }
    }
}
