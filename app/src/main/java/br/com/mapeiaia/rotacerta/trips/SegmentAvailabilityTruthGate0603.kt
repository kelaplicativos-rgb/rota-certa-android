package br.com.mapeiaia.rotacerta.trips

/**
 * 0.1.603 — fail-closed gate for any user-visible "vagas por trecho" claim.
 *
 * A seat calculation can be internally well-formed while still being semantically
 * unsafe to present as per-segment truth when the canonical itinerary is incomplete
 * or the external passenger roster cannot be proven complete/resolved. In that case
 * the correct UI behavior is to show no per-segment availability at all.
 */
internal enum class SegmentAvailabilityTruthReason0603 {
    VERIFIED,
    ROUTE_TOO_SHORT,
    CAPACITY_UNRELIABLE,
    ITINERARY_NOT_AUTHORITATIVE,
    EXTERNAL_SNAPSHOT_MISSING,
    EXTERNAL_SNAPSHOT_PARTIAL,
    EXTERNAL_ITINERARY_NOT_AUTHORITATIVE,
    EXTERNAL_PASSENGER_ROSTER_INCOMPLETE,
    EXTERNAL_PASSENGER_SEGMENTS_UNRESOLVED,
}

internal data class SegmentAvailabilityTruth0603(
    val verified: Boolean,
    val reason: SegmentAvailabilityTruthReason0603,
)

internal fun segmentAvailabilityTruth0603(trip: Trip): SegmentAvailabilityTruth0603 {
    val stops = trip.stops.sortedBy(TripStop::order)
    if (stops.size < 2) {
        return SegmentAvailabilityTruth0603(false, SegmentAvailabilityTruthReason0603.ROUTE_TOO_SHORT)
    }
    if (!trip.capacityReliable || trip.capacity <= 0) {
        return SegmentAvailabilityTruth0603(false, SegmentAvailabilityTruthReason0603.CAPACITY_UNRELIABLE)
    }
    if (!trip.itineraryAuthoritative) {
        return SegmentAvailabilityTruth0603(false, SegmentAvailabilityTruthReason0603.ITINERARY_NOT_AUTHORITATIVE)
    }

    val hasExternalIdentity =
        !trip.blablaProfileUuid.isNullOrBlank() ||
            !trip.blablaTripId.isNullOrBlank() ||
            trip.externalSnapshot != null

    if (!hasExternalIdentity) {
        return SegmentAvailabilityTruth0603(true, SegmentAvailabilityTruthReason0603.VERIFIED)
    }

    val external = trip.externalSnapshot
        ?: return SegmentAvailabilityTruth0603(false, SegmentAvailabilityTruthReason0603.EXTERNAL_SNAPSHOT_MISSING)

    if (!trip.externalSnapshotComplete) {
        return SegmentAvailabilityTruth0603(false, SegmentAvailabilityTruthReason0603.EXTERNAL_SNAPSHOT_PARTIAL)
    }
    if (!external.itinerary_authoritative) {
        return SegmentAvailabilityTruth0603(
            false,
            SegmentAvailabilityTruthReason0603.EXTERNAL_ITINERARY_NOT_AUTHORITATIVE,
        )
    }
    if (!external.passenger_roster_complete) {
        return SegmentAvailabilityTruth0603(
            false,
            SegmentAvailabilityTruthReason0603.EXTERNAL_PASSENGER_ROSTER_INCOMPLETE,
        )
    }
    if (!PublicAgendaAutoSync0300.externalPassengerSegmentsResolved(external, trip)) {
        return SegmentAvailabilityTruth0603(
            false,
            SegmentAvailabilityTruthReason0603.EXTERNAL_PASSENGER_SEGMENTS_UNRESOLVED,
        )
    }

    return SegmentAvailabilityTruth0603(true, SegmentAvailabilityTruthReason0603.VERIFIED)
}
