package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate

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
    private const val DATE_SCOPE_PREFIX = "date_scope:"

    fun mergeSnapshotTrips(
        previous: List<BlaBlaCollectorTrip>,
        current: List<BlaBlaCollectorTrip>,
        authoritativeComplete: Boolean,
        authoritativeDateScope: Set<String>? = null,
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
        val preservedMissing = previous.filter { prior ->
            val missingFromCurrent = BlaBlaTripIdentity.evidence(prior).key !in currentKeys
            when {
                !missingFromCurrent -> false
                !authoritativeComplete -> true
                authoritativeDateScope == null -> false
                else -> prior.date !in authoritativeDateScope
            }
        }
        val resolved = BlaBlaTripIdentity.resolveDistinct(reconciled + preservedMissing).trips
        return BlaBlaSnapshotMergeResult(
            trips = resolved,
            preservedMissingTrips = preservedMissing.size,
            preservedIncompleteRosters = preservedIncompleteRosters,
        )
    }

    /**
     * 0.1.615 — an exact targeted HTML refresh is a replacement, not an enrichment.
     * The refreshed strong identity is taken byte-for-semantic-field from the new HTML
     * model while sibling cards remain untouched. This deliberately bypasses the legacy
     * monotonic passenger merge so removed passengers/seats cannot survive a fresh card.
     */
    fun replaceAuthoritativeHtmlTarget0615(
        previous: List<BlaBlaCollectorTrip>,
        current: List<BlaBlaCollectorTrip>,
        targetTripId: String,
    ): BlaBlaSnapshotMergeResult {
        val wanted = targetTripId.trim()
        val incoming = current.singleOrNull { trip ->
            trip.trip_id?.trim() == wanted &&
                BlaBlaCollectorUrlModule.tripId(trip.trip_href.orEmpty()) == wanted &&
                trip.passenger_roster_complete &&
                trip.itinerary_authoritative &&
                trip.published_seats != null &&
                !trip.public_trip_href.isNullOrBlank()
        } ?: return BlaBlaSnapshotMergeResult(
            trips = previous,
            preservedMissingTrips = 0,
            preservedIncompleteRosters = 0,
        )
        val incomingKey = BlaBlaTripIdentity.evidence(incoming).key
        val siblings = previous.filter { prior ->
            BlaBlaTripIdentity.evidence(prior).key != incomingKey
        }
        return BlaBlaSnapshotMergeResult(
            trips = BlaBlaTripIdentity.resolveDistinct(siblings + incoming).trips,
            preservedMissingTrips = 0,
            preservedIncompleteRosters = 0,
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


    fun scopeResponseToDate(
        response: BlaBlaCollectorMonthResponse,
        date: LocalDate,
    ): BlaBlaCollectorMonthResponse = scopeResponseToDates(response, listOf(date))

    fun scopeResponseToDates(
        response: BlaBlaCollectorMonthResponse,
        dates: Collection<LocalDate>,
    ): BlaBlaCollectorMonthResponse {
        val isoDates = dates.map(LocalDate::toString).distinct().sorted()
        return response.copy(
            trips = response.trips.filter { trip -> trip.date in isoDates },
            coverage = response.coverage.copy(reason = "$DATE_SCOPE_PREFIX${isoDates.joinToString(",")}"),
        )
    }

    fun isDateScoped(response: BlaBlaCollectorMonthResponse?): Boolean =
        response?.coverage?.reason?.startsWith(DATE_SCOPE_PREFIX) == true

    fun recoverStartupResponse(
        persisted: BlaBlaCollectorMonthResponse?,
        dynamic: BlaBlaCollectorMonthResponse?,
    ): BlaBlaCollectorMonthResponse? {
        if (persisted?.status == "cleared") return persisted
        if (isDateScoped(persisted)) return persisted
        if (persisted?.trips?.isNotEmpty() == true) return persisted
        if (dynamic == null || dynamic.trips.isEmpty()) return persisted
        return dynamic
    }

}
