package br.com.mapeiaia.rotacerta.trips

/**
 * User-visible synchronization scope.
 *
 * This state is deliberately separate from network/canonical/background activity. A request can
 * be running internally without owning a user-facing manual synchronization scope.
 */
internal sealed interface AgendaSyncUiScope0562 {
    data object None : AgendaSyncUiScope0562

    data class SingleTrip(
        val tripIdentity: BlaBlaTripTarget0407,
        val operationId: String,
    ) : AgendaSyncUiScope0562

    data class AllTrips(
        val operationId: String,
        val commandIds: Set<String>,
        val trigger: String,
        val commandRevisionAtStart: Long,
    ) : AgendaSyncUiScope0562
}

/**
 * A global visual operation is busy only while one of the commands explicitly owned by that
 * global operation is pending. An unrelated card/background command can never promote the UI to
 * ALL_TRIPS or extend a completed global operation.
 */
internal fun agendaGlobalSyncBusy0562(
    scope: AgendaSyncUiScope0562,
    audits: Iterable<BlaBlaCommandAuditSnapshot0407?>,
): Boolean {
    val global = scope as? AgendaSyncUiScope0562.AllTrips ?: return false
    if (global.commandIds.isEmpty()) return false
    return audits.any { audit ->
        audit?.pending == true && audit.commandId in global.commandIds
    }
}

internal fun agendaSingleTripScope0562(
    target: BlaBlaTripTarget0407?,
    audit: BlaBlaCommandAuditSnapshot0407?,
    activeGlobalCommandIds: Set<String> = emptySet(),
): AgendaSyncUiScope0562 = when {
    target == null || audit?.pending != true -> AgendaSyncUiScope0562.None
    audit.commandId in activeGlobalCommandIds -> AgendaSyncUiScope0562.None
    else -> AgendaSyncUiScope0562.SingleTrip(
        tripIdentity = target,
        operationId = audit.commandId,
    )
}
