package br.com.mapeiaia.rotacerta.trips

/**
 * 0.1.578 — the external provider id and the exact navigable trip href are one binding.
 *
 * A provider id alone remains sufficient for canonical identity, but it is not sufficient to
 * advertise that the trip can be opened. The href must come from an official BlaBlaCar surface
 * and resolve back to the same administrative trip id.
 */
internal fun canonicalSpecificTripHref0578(
    rawHref: String?,
    expectedTripId: String?,
): String? {
    val expected = expectedTripId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val raw = rawHref?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val canonical = BlaBlaCollectorUrlModule.canonical(raw).trim()
    if (canonical.isBlank()) return null
    if (!BlaBlaCollectorUrlModule.isAllowed(canonical)) return null
    if (!BlaBlaCollectorUrlModule.isSpecificTrip(canonical)) return null
    return canonical.takeIf { BlaBlaCollectorUrlModule.tripId(it) == expected }
}

/**
 * Rehydrates a partial collector observation only from a previously proven href belonging to the
 * same strong external identity. It never synthesizes a BlaBlaCar URL from the trip id.
 */
internal fun collectorSourceWithSpecificTripHref0578(
    incoming: BlaBlaCollectorTrip,
    existing: Trip?,
): BlaBlaCollectorTrip? {
    val tripId = incoming.trip_id?.trim()?.takeIf(String::isNotEmpty) ?: return null
    canonicalSpecificTripHref0578(incoming.trip_href, tripId)?.let { href ->
        return if (incoming.trip_href == href) incoming else incoming.copy(trip_href = href)
    }

    val sameCanonicalIdentity =
        existing != null &&
            resolvedTripRecordOrigin(existing) == TripRecordOrigin.EXTERNAL_BACKING &&
            existing.blablaProfileUuid?.trim()?.equals(incoming.profile_uuid.trim(), ignoreCase = true) == true &&
            existing.blablaTripId?.trim() == tripId
    if (!sameCanonicalIdentity) return null

    val preservedHref = canonicalSpecificTripHref0578(existing?.blablaManageUrl, tripId)
        ?: canonicalSpecificTripHref0578(existing?.externalSnapshot?.trip_href, tripId)
        ?: return null
    return incoming.copy(trip_href = preservedHref)
}

internal data class SpecificTripHrefSnapshotRepair0578(
    val snapshot: BlaBlaDynamicSessionSnapshot,
    val repairedTrips: Int,
)

/**
 * Cheap RIDE_LIST repair for legacy snapshots.
 *
 * The ride list is the authoritative page that materializes the driver's exact trip cards. We use
 * only candidates whose href maps back to the exact stored trip id and only inside the already
 * verified account/profile snapshot. No route/date similarity and no fabricated URL are accepted.
 */
internal fun repairSpecificTripHrefsInSnapshot0578(
    snapshot: BlaBlaDynamicSessionSnapshot,
    expectedProfileUuid: String?,
    candidates: List<BlaBlaDomRideCandidate>,
): SpecificTripHrefSnapshotRepair0578 {
    val profileUuid = expectedProfileUuid?.trim()?.takeIf(String::isNotEmpty)
        ?: return SpecificTripHrefSnapshotRepair0578(snapshot, 0)
    if (!snapshot.identityVerified || snapshot.profileUuid?.trim()?.equals(profileUuid, ignoreCase = true) != true) {
        return SpecificTripHrefSnapshotRepair0578(snapshot, 0)
    }

    val hrefsByTripId = candidates
        .mapNotNull { candidate ->
            val tripId = BlaBlaCollectorUrlModule.tripId(candidate.href)?.trim()?.takeIf(String::isNotEmpty)
                ?: return@mapNotNull null
            canonicalSpecificTripHref0578(candidate.href, tripId)?.let { href -> tripId to href }
        }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (tripId, hrefs) ->
            hrefs.distinct().singleOrNull()?.let { href -> tripId to href }
        }
        .toMap()
    if (hrefsByTripId.isEmpty()) return SpecificTripHrefSnapshotRepair0578(snapshot, 0)

    var repaired = 0
    val trips = snapshot.trips.map { trip ->
        if (!trip.profile_uuid.trim().equals(profileUuid, ignoreCase = true)) return@map trip
        val tripId = trip.trip_id?.trim()?.takeIf(String::isNotEmpty) ?: return@map trip
        val authoritativeHref = hrefsByTripId[tripId] ?: return@map trip
        if (canonicalSpecificTripHref0578(trip.trip_href, tripId) == authoritativeHref) return@map trip
        repaired += 1
        trip.copy(trip_href = authoritativeHref)
    }
    return SpecificTripHrefSnapshotRepair0578(
        snapshot = if (repaired > 0) snapshot.copy(trips = trips) else snapshot,
        repairedTrips = repaired,
    )
}
