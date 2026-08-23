package br.com.mapeiaia.rotacerta.trips

internal data class BlaBlaSnapshotMergeResult(
    val trips: List<BlaBlaCollectorTrip>,
    val preservedMissingTrips: Int,
    val preservedIncompleteRosters: Int,
)

/**
 * Timeline publication policy. A complete, UUID-verified traversal is
 * authoritative. A partial traversal may enrich known trips, but it cannot
 * erase cards or passenger rows confirmed by the last good snapshot.
 */
internal object BlaBlaCollectorTimelineModule {
    fun mergeSnapshotTrips(
        previous: List<BlaBlaCollectorTrip>,
        current: List<BlaBlaCollectorTrip>,
        authoritativeComplete: Boolean,
    ): BlaBlaSnapshotMergeResult {
        val previousByIdentity = previous.associateBy { BlaBlaTripIdentity.evidence(it).key }
        var preservedIncompleteRosters = 0
        val reconciled = current.map { incoming ->
            val key = BlaBlaTripIdentity.evidence(incoming).key
            val prior = previousByIdentity[key]
            val merged = BlaBlaCollectorPassengerModule.mergeMonotonic(prior, incoming)
            if (
                !incoming.passenger_roster_complete &&
                prior != null &&
                (merged.passengers.size > incoming.passengers.size || merged.booked_seats > incoming.booked_seats)
            ) {
                preservedIncompleteRosters++
            }
            merged
        }
        val currentKeys = current.mapTo(mutableSetOf()) { BlaBlaTripIdentity.evidence(it).key }
        val preservedMissing = if (authoritativeComplete) {
            emptyList()
        } else {
            previous.filter { BlaBlaTripIdentity.evidence(it).key !in currentKeys }
        }
        val resolved = BlaBlaTripIdentity.resolveDistinct(reconciled + preservedMissing).trips
        return BlaBlaSnapshotMergeResult(
            trips = resolved,
            preservedMissingTrips = preservedMissing.size,
            preservedIncompleteRosters = preservedIncompleteRosters,
        )
    }

    fun mergePublishedResponse(
        previous: BlaBlaCollectorMonthResponse?,
        incoming: BlaBlaCollectorMonthResponse,
        preserveOnPartial: Boolean,
    ): BlaBlaCollectorMonthResponse {
        if (!preserveOnPartial || previous == null) return incoming
        if (incoming.status !in setOf("partial", "blocked")) return incoming
        val merged = mergeSnapshotTrips(previous.trips, incoming.trips, authoritativeComplete = false)
        return incoming.copy(trips = merged.trips)
    }
}
