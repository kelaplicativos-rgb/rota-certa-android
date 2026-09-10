package br.com.mapeiaia.rotacerta.trips

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Source-neutral timeline model. Local Agenda trips and collector snapshots
 * share this model so conflict/continuity rules remain centralized.
 */
data class TripTimelineEntry(
    val tripId: String,
    val profileId: String,
    val profileLabel: String,
    val departureAtMillis: Long,
    val arrivalAtMillis: Long?,
    val origin: String,
    val destination: String,
    val status: TripStatus,
    /** Derived simultaneous operational inventory ceiling. */
    val capacity: Int,
    val minimumOccupiedSeats: Int,
    val maximumOccupiedSeats: Int,
    val sourcePassengerSeats: Map<BookingSource, Int>,
    val localTripId: String? = null,
    val blablaTripId: String? = null,
    val blablaTripHref: String? = null,
    /** Exact passenger-facing public BlaBlaCar URL captured for this same card. */
    val blablaPublicHref: String? = null,
    val blablaProfileUuid: String? = null,
    val blablaPrice: String? = null,
    val blablaAvailability: String? = null,
    /** Ordered stops observed on the exact BlaBlaCar publication. */
    val blablaItineraryStops: List<String> = emptyList(),
    /** Synchronized BlaBlaCar quota. Legacy field name retained for collector/persistence compatibility. */
    val blablaPublishedSeats: Int? = null,
    val blablaPassengers: List<BlaBlaCollectorPassenger> = emptyList(),
    val blablaPassengerRosterComplete: Boolean? = null,
    val issues: Set<TripTimelineIssue> = emptySet(),
    /** Configured Rota Certa quota contributing to the operational inventory. */
    val rotaCertaSeatAllocation: Int? = null,
    /** Whole-trip operational blocks/holds that are not confirmed passengers. */
    val operationalBlockedSeats: Int = 0,
    /** 0.1.494: true only for a projection read from the authenticated canonical backend. */
    val canonicalBackendAuthoritative0494: Boolean = false,
    val canonicalRevision0494: Long = 0L,
    val canonicalStateHash0494: String = "",
    val canonicalAvailableSeatsMinimum0494: Int? = null,
    val canonicalAvailableSeatsMaximum0494: Int? = null,
    val canonicalOverbookingSeats0494: Int = 0,
    val canonicalCapacityReliable0494: Boolean? = null,
    val canonicalUpdatedAtMillis0494: Long = 0L,
    val canonicalSegmentLoads0494: List<Int> = emptyList(),
    val canonicalSegmentPassengerLoads0494: List<Int> = emptyList(),
    val canonicalSegmentBlockedLoads0494: List<Int> = emptyList(),
    val canonicalSegmentAvailableSeats0494: List<Int> = emptyList(),
    val canonicalOccupancyRevision0494: Long? = null,
    val remoteTripId0494: String = "",
    val publicUrl0494: String = "",
) {
    val minimumAvailableSeats: Int
        get() = canonicalAvailableSeatsMinimum0494
            ?: (capacity - maximumOccupiedSeats).coerceAtLeast(0)

    val maximumAvailableSeats: Int
        get() = canonicalAvailableSeatsMaximum0494
            ?: (capacity - minimumOccupiedSeats).coerceAtLeast(0)
}

/**
 * 0.1.490 single URL projection for every Timeline action that opens the public
 * BlaBlaCar publication. The canonical Trip.blablaPublicUrl reaches this model as
 * blablaPublicHref; the administrative/manage href is deliberately excluded.
 */
internal fun canonicalTimelineBlaBlaPublicHref0490(entry: TripTimelineEntry): String? =
    canonicalBoundBlaBlaPublicUrl0423(entry.blablaPublicHref, entry.blablaTripId)


/**
 * Resolves public availability from the canonical operational inventory.
 * The synchronized BlaBlaCar quota contributes to that inventory exactly once;
 * confirmed occupancy is subtracted separately.
 */
internal data class TimelinePublicCapacityResolution(
    val operationalInventory: Int?,
    val blablaQuota: Int?,
    val passengerSeats: Int,
    val blockedSeats: Int,
    val effectiveCapacity: Int?,
    val availableSeats: Int?,
    val overbookingSeats: Int,
    val capacitySource: String,
)

internal fun resolveTimelinePublicCapacity(
    operationalInventory: Int?,
    blablaQuota: Int?,
    passengerSeats: Int,
    blockedSeats: Int = 0,
): TimelinePublicCapacityResolution {
    val inventory = operationalInventory?.takeIf { it in 0..999 }
    val quota = blablaQuota?.takeIf { it in 0..999 }
    val passengers = passengerSeats.coerceAtLeast(0)
    val blocked = blockedSeats.coerceAtLeast(0)
    val consumed = passengers + blocked
    val overbooking = inventory?.let { (consumed - it).coerceAtLeast(0) } ?: 0
    return TimelinePublicCapacityResolution(
        operationalInventory = inventory,
        blablaQuota = quota,
        passengerSeats = passengers,
        blockedSeats = blocked,
        effectiveCapacity = inventory,
        availableSeats = inventory?.let { (it - consumed).coerceAtLeast(0) },
        overbookingSeats = overbooking,
        capacitySource = if (inventory != null) "trip_operational_inventory" else "unavailable",
    )
}

internal fun timelinePublicCapacityResolution(
    entry: TripTimelineEntry,
    occupiedSeats: Int = entry.maximumOccupiedSeats,
): TimelinePublicCapacityResolution {
    if (entry.canonicalBackendAuthoritative0494) {
        val available = entry.canonicalAvailableSeatsMinimum0494
        return TimelinePublicCapacityResolution(
            operationalInventory = entry.capacity.takeIf { it >= 0 },
            blablaQuota = entry.blablaPublishedSeats,
            passengerSeats = entry.minimumOccupiedSeats.coerceAtLeast(0),
            blockedSeats = entry.operationalBlockedSeats.coerceAtLeast(0),
            effectiveCapacity = entry.capacity.takeIf { it >= 0 },
            availableSeats = available,
            overbookingSeats = entry.canonicalOverbookingSeats0494.coerceAtLeast(0),
            capacitySource = "CANONICAL_BACKEND",
        )
    }
    val confirmedWholeTrip = entry.sourcePassengerSeats.values.sumOf { it.coerceAtLeast(0) }
    val passengers = minOf(occupiedSeats.coerceAtLeast(0), confirmedWholeTrip)
    val blocked = (occupiedSeats.coerceAtLeast(0) - passengers).coerceAtLeast(0)
    return resolveTimelinePublicCapacity(
        operationalInventory = entry.capacity,
        blablaQuota = entry.blablaPublishedSeats,
        passengerSeats = passengers,
        blockedSeats = blocked,
    )
}

internal fun canonicalTimelineSegmentLoads0494(
    entry: TripTimelineEntry,
    trip: Trip?,
): List<SegmentLoad> {
    if (!entry.canonicalBackendAuthoritative0494 || trip == null) return emptyList()
    val stops = trip.stops.sortedBy(TripStop::order)
    if (stops.size < 2 || entry.canonicalSegmentLoads0494.isEmpty()) return emptyList()
    return (0 until stops.lastIndex).mapNotNull { index ->
        val occupied = entry.canonicalSegmentLoads0494.getOrNull(index) ?: return@mapNotNull null
        SegmentLoad(
            from = stops[index],
            to = stops[index + 1],
            occupiedSeats = occupied.coerceAtLeast(0),
            availableSeats = entry.canonicalSegmentAvailableSeats0494.getOrNull(index)
                ?.coerceAtLeast(0)
                ?: return@mapNotNull null,
            passengerSeats = entry.canonicalSegmentPassengerLoads0494.getOrNull(index)
                ?.coerceAtLeast(0)
                ?: occupied.coerceAtLeast(0),
            blockedSeats = entry.canonicalSegmentBlockedLoads0494.getOrNull(index)?.coerceAtLeast(0) ?: 0,
            overbookingSeats = (occupied - entry.capacity).coerceAtLeast(0),
        )
    }
}

internal fun timelinePublicSegmentLoads(
    entry: TripTimelineEntry,
    physicalLoads: List<SegmentLoad>,
): List<SegmentLoad> {
    if (!entry.canonicalBackendAuthoritative0494 || entry.canonicalSegmentLoads0494.isEmpty()) return physicalLoads
    return physicalLoads.mapIndexed { index, load ->
        load.copy(
            occupiedSeats = entry.canonicalSegmentLoads0494.getOrNull(index) ?: load.occupiedSeats,
            passengerSeats = entry.canonicalSegmentPassengerLoads0494.getOrNull(index) ?: load.passengerSeats,
            blockedSeats = entry.canonicalSegmentBlockedLoads0494.getOrNull(index) ?: load.blockedSeats,
            availableSeats = entry.canonicalSegmentAvailableSeats0494.getOrNull(index) ?: load.availableSeats,
        )
    }
}

internal data class TripChannelAllocationBreakdown(
    val operationalInventory: Int?,
    val blablaQuota: Int?,
    val rotaCertaQuota: Int?,
)

/**
 * Channel quotas form the whole-trip operational inventory. Confirmed occupancy
 * is deliberately excluded here and is subtracted later by the per-segment engine.
 */
internal fun tripChannelAllocationBreakdown(
    physicalPassengerCapacity: Int?,
    blablaPublishedSeats: Int?,
    rotaCertaSeatAllocation: Int?,
): TripChannelAllocationBreakdown {
    val physical = physicalPassengerCapacity?.takeIf { it in 1..999 }
    val blabla = blablaPublishedSeats?.takeIf { it in 0..999 }
    val rotaCerta = rotaCertaSeatAllocation?.takeIf { it in 0..999 }
    val total = if (blabla != null || rotaCerta != null) {
        ((blabla ?: 0) + (rotaCerta ?: 0)).coerceAtMost(999)
    } else {
        null
    }
    return TripChannelAllocationBreakdown(
        operationalInventory = total ?: physical,
        blablaQuota = blabla,
        rotaCertaQuota = rotaCerta,
    )
}

enum class TripTimelineIssue {
    DUPLICATE,
    PHYSICAL_CONFLICT,
    PROFILE_CONTINUITY,
    OVERBOOKING,
    VALIDATION_PENDING,
    EXTERNAL_IDENTITY_CONFLICT,
    EXTERNAL_IDENTITY_INCOMPLETE,
    PASSENGER_PROJECTION_INCOMPLETE,
    PRIVATE_PROJECTION_STALE,
    REVISION_INCOMPATIBLE,
}

internal data class CanonicalTimelineProjection0494(
    val trips: List<Trip>,
    val bookings: List<Booking>,
    val entries: List<TripTimelineEntry>,
    val snapshotAtMillis: Long,
)

internal fun passengerSeatsBySource0494(bookings: List<Booking>): Map<BookingSource, Int> {
    val activePassengers = bookings.filter { booking ->
        booking.capacityClaimType in setOf(CapacityClaimType.PASSENGER, CapacityClaimType.EXTERNAL_OCCUPANCY) &&
            booking.status == BookingStatus.CONFIRMED
    }
    val result = mutableMapOf<BookingSource, Int>()
    activePassengers.groupBy(::bookingOccupancyIdentityKey).values.forEach { group ->
        val external = group
            .filter { it.capacityClaimType == CapacityClaimType.EXTERNAL_OCCUPANCY || it.source == BookingSource.BLABLACAR }
            .maxByOrNull(Booking::seats)
        val local = group
            .filterNot { it.capacityClaimType == CapacityClaimType.EXTERNAL_OCCUPANCY || it.source == BookingSource.BLABLACAR }
            .maxByOrNull(Booking::seats)
        val externalSeats = external?.seats?.coerceAtLeast(0) ?: 0
        if (externalSeats > 0) {
            result[BookingSource.BLABLACAR] = (result[BookingSource.BLABLACAR] ?: 0) + externalSeats
        }
        val localSeats = local?.seats?.coerceAtLeast(0) ?: 0
        val localExtra = (localSeats - externalSeats).coerceAtLeast(0)
        if (localExtra > 0 && local != null) {
            result[local.source] = (result[local.source] ?: 0) + localExtra
        }
    }
    return result.filterValues { it > 0 }
}

internal fun canonicalTimelineProjection0494(
    response: DriverTripSyncStateResponse0402?,
    fallbackProfileLabel: String = "Rota Certa",
    existingLocalBookings: List<Booking> = emptyList(),
): CanonicalTimelineProjection0494 {
    if (response == null) return CanonicalTimelineProjection0494(emptyList(), emptyList(), emptyList(), 0L)
    require(
        response.source == "CANONICAL_NATIVE_FIREWALL" &&
            response.provenancePolicy0500 == "AGENDA_CANONICAL_ONLY_0503" &&
            !response.collectorRead &&
            !response.collectorFallback &&
            !response.collectorDerivedData,
    ) { "Timeline recusou datasource fora da Agenda canônica: ${response.source}" }

    val winners = response.trips
        .filter { state -> state.canonicalTripId.isNotBlank() || state.remoteTripId.isNotBlank() }
        .groupBy { state -> state.canonicalTripId.ifBlank { state.remoteTripId } }
        .mapValues { (_, candidates) ->
            candidates.maxWithOrNull(
                compareBy<DriverTripSyncState0402> { it.canonicalRevision }
                    .thenBy { it.publicationRevision }
                    .thenBy { it.updatedAtMillis },
            ) ?: candidates.first()
        }
        .values
        .sortedBy(DriverTripSyncState0402::departureAtMillis)

    val projectedTrips = mutableListOf<Trip>()
    val projectedBookings = mutableListOf<Booking>()
    val projectedEntries = mutableListOf<TripTimelineEntry>()

    winners.forEach { state ->
        val canonicalId = state.canonicalTripId.ifBlank { state.remoteTripId }
        val stops = state.stops.sortedBy(TripStop::order)
        if (canonicalId.isBlank() || stops.size < 2) return@forEach
        val tripStatus = runCatching { TripStatus.valueOf(state.status.trim().uppercase()) }
            .getOrDefault(TripStatus.DRAFT)
        val serverSourceCounts = state.sourceSeatCounts.mapNotNull { (source, seats) ->
            runCatching { BookingSource.valueOf(source.trim().uppercase()) }
                .getOrNull()
                ?.let { it to seats.coerceAtLeast(0) }
        }.toMap()
        val canonicalIssues = state.canonicalIssues.mapNotNull { issue ->
            runCatching { TripTimelineIssue.valueOf(issue.trim().uppercase()) }.getOrNull()
        }.toSet()
        val origin = stops.first()
        val destination = stops.last()
        val strongExternal = state.blablaProfileUuid.isNotBlank() && state.blablaTripId.isNotBlank()
        val trip = Trip(
            id = canonicalId,
            title = state.title.ifBlank { "${origin.name} → ${destination.name}" },
            departureAtMillis = state.departureAtMillis,
            capacity = state.capacity.coerceAtLeast(0),
            status = tripStatus,
            stops = stops,
            publicToken = state.remoteTripId.ifBlank { canonicalId },
            remoteId = state.remoteTripId.takeIf(String::isNotBlank),
            publicUrl = state.publicUrl.takeIf(String::isNotBlank),
            blablaProfileUuid = state.blablaProfileUuid.takeIf(String::isNotBlank),
            blablaTripId = state.blablaTripId.takeIf(String::isNotBlank),
            blablaManageUrl = state.blablaManageUrl.takeIf(String::isNotBlank),
            blablaPublicUrl = state.blablaPublicUrl.takeIf(String::isNotBlank),
            publicBookingEnabled = state.publicBookingEnabled,
            itineraryAuthoritative = state.itineraryAuthoritative,
            notes = state.notes0499,
            publicTimezoneId0411 = state.timezoneId0499,
            publishedSeats = state.publishedSeats,
            capacityReliable = state.capacityReliable,
            rotaCertaSeatAllocation = state.rotaCertaSeatAllocation,
            recordOrigin = if (strongExternal) TripRecordOrigin.EXTERNAL_BACKING else TripRecordOrigin.LOCAL,
            canonicalRevision = state.canonicalRevision,
            publicationRevision = state.publicationRevision,
            tripKey = state.tripKey,
            canonicalStateHash = state.canonicalStateHash,
            updatedAtMillis = state.updatedAtMillis.takeIf { it > 0L } ?: response.snapshotAtMillis,
        )
        val bookings = state.bookings.map { remote ->
            val existingLocal = existingLocalBookings.firstOrNull { local -> local.id == remote.id }
            remote.toLocalBooking(
                localTripId = canonicalId,
                existingLocal = existingLocal,
            )
        }
        val sourceCounts = passengerSeatsBySource0494(bookings).ifEmpty { serverSourceCounts }
        projectedTrips += trip
        projectedBookings += bookings
        projectedEntries += TripTimelineEntry(
            tripId = canonicalId,
            localTripId = canonicalId,
            profileId = state.blablaProfileUuid.ifBlank { canonicalId },
            profileLabel = state.driverDisplayName.ifBlank { fallbackProfileLabel },
            departureAtMillis = state.departureAtMillis,
            arrivalAtMillis = state.arrivalAtMillis.takeIf { it > 0L } ?: destination.plannedArrivalMillis,
            origin = origin.name,
            destination = destination.name,
            status = tripStatus,
            capacity = state.capacity.coerceAtLeast(0),
            minimumOccupiedSeats = state.minimumOccupiedSeats.coerceAtLeast(0),
            maximumOccupiedSeats = state.maximumOccupiedSeats.coerceAtLeast(0),
            sourcePassengerSeats = sourceCounts,
            blablaTripId = state.blablaTripId.takeIf(String::isNotBlank),
            blablaTripHref = state.blablaManageUrl.takeIf(String::isNotBlank),
            blablaPublicHref = state.blablaPublicUrl.takeIf(String::isNotBlank),
            blablaProfileUuid = state.blablaProfileUuid.takeIf(String::isNotBlank),
            blablaItineraryStops = stops.map(TripStop::name),
            blablaPublishedSeats = state.publishedSeats,
            blablaPassengers = emptyList(),
            blablaPassengerRosterComplete = true,
            issues = canonicalIssues,
            rotaCertaSeatAllocation = state.rotaCertaSeatAllocation,
            operationalBlockedSeats = state.operationalBlockedSeats.coerceAtLeast(0),
            canonicalBackendAuthoritative0494 = true,
            canonicalRevision0494 = state.canonicalRevision,
            canonicalStateHash0494 = state.canonicalStateHash,
            canonicalAvailableSeatsMinimum0494 = state.availableSeatsMinimum ?: state.operationalAvailableSeats,
            canonicalAvailableSeatsMaximum0494 = state.availableSeatsMaximum ?: state.operationalAvailableSeats,
            canonicalOverbookingSeats0494 = state.operationalOverbookingSeats.coerceAtLeast(0),
            canonicalCapacityReliable0494 = state.capacityReliable,
            canonicalUpdatedAtMillis0494 = state.updatedAtMillis,
            canonicalSegmentLoads0494 = state.segmentLoads,
            canonicalSegmentPassengerLoads0494 = state.segmentPassengerLoads,
            canonicalSegmentBlockedLoads0494 = state.segmentBlockedLoads,
            canonicalSegmentAvailableSeats0494 = state.segmentAvailableSeats,
            canonicalOccupancyRevision0494 = state.occupancyRevision,
            remoteTripId0494 = state.remoteTripId,
            publicUrl0494 = state.publicUrl,
        )
    }

    return CanonicalTimelineProjection0494(
        trips = projectedTrips,
        bookings = projectedBookings.distinctBy { booking -> "${booking.tripId}|${booking.id}" },
        entries = projectedEntries,
        snapshotAtMillis = response.snapshotAtMillis,
    )
}

internal fun localAgendaTimelineProjection0515(
    trips: List<Trip>,
    bookings: List<Booking>,
    localProfileLabel: String = "Agenda",
    nowMillis: Long = System.currentTimeMillis(),
): CanonicalTimelineProjection0494 {
    val activeTrips = trips.filterNot(Trip::deleted)
    val activeTripIds = activeTrips.map(Trip::id).toSet()
    val activeBookings = bookings.filter { it.tripId in activeTripIds }
    return CanonicalTimelineProjection0494(
        trips = activeTrips,
        bookings = activeBookings,
        entries = TripTimelineEngine.fromLocalAgenda(
            trips = activeTrips,
            bookings = activeBookings,
            localProfileLabel = localProfileLabel,
            nowMillis = nowMillis,
        ),
        snapshotAtMillis = nowMillis,
    )
}

/**
 * 0.1.525 local-first Timeline contract.
 *
 * The persisted Android Agenda is the operational authority. A validated remote
 * canonical projection may fill missing fields or advance a newer revision, but
 * it can never erase a valid local field or silently replace a strong external
 * identity. Matching is by canonicalTripId/strong identity and passenger
 * identity only; list position is never an identity.
 */
internal data class CanonicalTimelineMergeResult0525(
    val projection: CanonicalTimelineProjection0494,
    val mergedTrips: Int = 0,
    val remoteRecoveryTrips: Int = 0,
    val incompleteRemoteIgnored: Int = 0,
    val conflictsRejected: Int = 0,
) {
    val changed: Boolean
        get() = mergedTrips > 0 || remoteRecoveryTrips > 0
}

private data class TimelineStrongIdentity0525(
    val profileUuid: String,
    val tripId: String,
)

private fun Trip.timelineStrongIdentity0525(): TimelineStrongIdentity0525? {
    val profile = blablaProfileUuid?.trim()?.lowercase().orEmpty()
    val providerTripId = blablaTripId?.trim().orEmpty()
    return if (profile.isNotBlank() && providerTripId.isNotBlank()) {
        TimelineStrongIdentity0525(profile, providerTripId)
    } else {
        null
    }
}

private fun TripTimelineEntry.timelineStrongIdentity0525(): TimelineStrongIdentity0525? {
    val profile = blablaProfileUuid?.trim()?.lowercase().orEmpty()
    val providerTripId = blablaTripId?.trim().orEmpty()
    return if (profile.isNotBlank() && providerTripId.isNotBlank()) {
        TimelineStrongIdentity0525(profile, providerTripId)
    } else {
        null
    }
}

private fun timelineExternalIdentityIncomplete0525(trip: Trip): Boolean {
    val hasProfile = !trip.blablaProfileUuid.isNullOrBlank()
    val hasTrip = !trip.blablaTripId.isNullOrBlank()
    return hasProfile != hasTrip
}

private fun timelineExternalIdentityConflict0525(local: Trip, remote: Trip): Boolean {
    val localIdentity = local.timelineStrongIdentity0525()
    val remoteIdentity = remote.timelineStrongIdentity0525()
    if (localIdentity != null && remoteIdentity != null && localIdentity != remoteIdentity) return true
    val localKey = local.tripKey.trim()
    val remoteKey = remote.tripKey.trim()
    return localKey.isNotBlank() && remoteKey.isNotBlank() && localKey != remoteKey
}

private fun timelineMergePlaceKey0525(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

private fun timelineStopsCompatible0525(left: TripStop, right: TripStop): Boolean {
    if (left.id.isNotBlank() && right.id.isNotBlank() && left.id == right.id) return true
    if (left.order != right.order) return false
    val leftKey = timelineMergePlaceKey0525(left.name.ifBlank { left.address })
    val rightKey = timelineMergePlaceKey0525(right.name.ifBlank { right.address })
    return leftKey.isNotBlank() && leftKey == rightKey
}

private fun mergeTimelineStops0525(local: List<TripStop>, remote: List<TripStop>): List<TripStop> {
    if (remote.size < 2) return local
    return remote.sortedBy(TripStop::order).map { incoming ->
        val existing = local.firstOrNull { timelineStopsCompatible0525(it, incoming) }
        if (existing == null) {
            incoming
        } else {
            incoming.copy(
                id = incoming.id.ifBlank { existing.id },
                name = incoming.name.ifBlank { existing.name },
                address = incoming.address.ifBlank { existing.address },
                latitude = incoming.latitude ?: existing.latitude,
                longitude = incoming.longitude ?: existing.longitude,
                plannedArrivalMillis = incoming.plannedArrivalMillis ?: existing.plannedArrivalMillis,
                plannedDepartureMillis = incoming.plannedDepartureMillis ?: existing.plannedDepartureMillis,
                priceToNextCents = if (incoming.priceToNextCents > 0L || existing.priceToNextCents == 0L) {
                    incoming.priceToNextCents
                } else {
                    existing.priceToNextCents
                },
            )
        }
    }
}

private fun timelineValidManageUrl0525(url: String?, tripId: String): String? {
    val value = url?.trim().orEmpty()
    if (value.isBlank() || tripId.isBlank()) return null
    return value.takeIf { BlaBlaCollectorUrlModule.tripId(it) == tripId }
        ?.let(BlaBlaCollectorUrlModule::canonical)
}

private fun timelineDerivedManageUrl0525(tripId: String): String? {
    if (tripId.isBlank()) return null
    val candidate = "${BlaBlaCollectorUrlModule.ORIGIN}/rides/offer/$tripId"
    return BlaBlaCollectorUrlModule.canonical(candidate)
        .takeIf { BlaBlaCollectorUrlModule.tripId(it) == tripId }
}

private fun timelineValidPublicUrl0525(url: String?, tripId: String): String? =
    canonicalBoundBlaBlaPublicUrl0423(url, tripId)

private fun timelineBookingIdentity0525(booking: Booking): String =
    booking.passengerId.trim().takeIf(String::isNotBlank)?.let { "passenger:$it" }
        ?: booking.sourceReference.trim().takeIf(String::isNotBlank)?.let { "reference:$it" }
        ?: booking.occupancyGroupId?.trim()?.takeIf(String::isNotBlank)?.let { "occupancy:$it" }
        ?: "booking:${booking.id}"

private fun mergeTimelineBooking0525(
    local: Booking,
    remote: Booking,
    remoteTripNewer: Boolean,
): Booking {
    val remoteOperationalNewer = remoteTripNewer || remote.updatedAtMillis > local.updatedAtMillis
    fun newerText(remoteValue: String, localValue: String): String =
        if (remoteOperationalNewer && remoteValue.isNotBlank()) remoteValue else localValue.ifBlank { remoteValue }

    return local.copy(
        passengerId = local.passengerId.ifBlank { remote.passengerId },
        passengerName = newerText(remote.passengerName, local.passengerName),
        passengerContact = newerText(remote.passengerContact, local.passengerContact),
        boardingStopId = newerText(remote.boardingStopId, local.boardingStopId),
        dropoffStopId = newerText(remote.dropoffStopId, local.dropoffStopId),
        seats = if (remoteOperationalNewer && remote.seats > 0) remote.seats else local.seats,
        status = if (remoteOperationalNewer) remote.status else local.status,
        operationalStatus = if (remoteOperationalNewer) remote.operationalStatus else local.operationalStatus,
        paymentStatus = if (remoteOperationalNewer) remote.paymentStatus else local.paymentStatus,
        lastDriverSelection = newerText(remote.lastDriverSelection, local.lastDriverSelection),
        holdExpiresAtMillis = if (remoteOperationalNewer) remote.holdExpiresAtMillis ?: local.holdExpiresAtMillis else local.holdExpiresAtMillis,
        createdAtMillis = listOf(local.createdAtMillis, remote.createdAtMillis).filter { it > 0L }.minOrNull() ?: 0L,
        updatedAtMillis = maxOf(local.updatedAtMillis, remote.updatedAtMillis),
        source = if (remoteOperationalNewer) remote.source else local.source,
        capacityClaimType = if (remoteOperationalNewer) remote.capacityClaimType else local.capacityClaimType,
        sourceReference = local.sourceReference.ifBlank { remote.sourceReference },
        occupancyGroupId = local.occupancyGroupId?.takeIf(String::isNotBlank)
            ?: remote.occupancyGroupId?.takeIf(String::isNotBlank),
        fareMinorUnits = if (remoteOperationalNewer) remote.fareMinorUnits ?: local.fareMinorUnits else local.fareMinorUnits,
        fareCurrencyCode = newerText(remote.fareCurrencyCode, local.fareCurrencyCode),
        boardingAddress = newerText(remote.boardingAddress, local.boardingAddress),
        dropoffAddress = newerText(remote.dropoffAddress, local.dropoffAddress),
        boardingLatitude = if (remoteOperationalNewer) remote.boardingLatitude ?: local.boardingLatitude else local.boardingLatitude,
        boardingLongitude = if (remoteOperationalNewer) remote.boardingLongitude ?: local.boardingLongitude else local.boardingLongitude,
        dropoffLatitude = if (remoteOperationalNewer) remote.dropoffLatitude ?: local.dropoffLatitude else local.dropoffLatitude,
        dropoffLongitude = if (remoteOperationalNewer) remote.dropoffLongitude ?: local.dropoffLongitude else local.dropoffLongitude,
        cancellationToken = local.cancellationToken,
        localMetadataTouched = local.localMetadataTouched || remote.localMetadataTouched,
    )
}

private fun mergeTimelineTrip0525(local: Trip, remote: Trip): Trip {
    val remoteNewer = remote.canonicalRevision > local.canonicalRevision
    val remoteIdentityComplete = remote.timelineStrongIdentity0525() != null
    val localIdentity = local.timelineStrongIdentity0525()
    val remoteIdentity = remote.timelineStrongIdentity0525()
    val mergedIdentity = when {
        remoteIdentityComplete && localIdentity == null -> remoteIdentity
        remoteIdentityComplete && localIdentity == remoteIdentity -> localIdentity
        else -> localIdentity
    }
    val mergedTripId = mergedIdentity?.tripId.orEmpty()
    val mergedProfileUuid = mergedIdentity?.profileUuid.orEmpty()

    fun newerText(remoteValue: String?, localValue: String?): String? {
        val incoming = remoteValue?.trim().orEmpty()
        val current = localValue?.trim().orEmpty()
        return when {
            remoteNewer && incoming.isNotBlank() -> incoming
            current.isNotBlank() -> current
            incoming.isNotBlank() -> incoming
            else -> null
        }
    }

    val localManage = timelineValidManageUrl0525(local.blablaManageUrl, mergedTripId)
    val remoteManage = timelineValidManageUrl0525(remote.blablaManageUrl, mergedTripId)
    val localPublicPersisted = local.blablaPublicUrl?.trim()?.takeIf(String::isNotBlank)
    val remotePublic = timelineValidPublicUrl0525(remote.blablaPublicUrl, mergedTripId)

    return local.copy(
        title = if (remoteNewer && remote.title.isNotBlank()) remote.title else local.title.ifBlank { remote.title },
        departureAtMillis = if (remoteNewer && remote.departureAtMillis > 0L) remote.departureAtMillis else local.departureAtMillis,
        capacity = if (remoteNewer && remote.capacityReliable && remote.capacity >= 0) remote.capacity else local.capacity,
        status = if (remoteNewer) remote.status else local.status,
        stops = if (remoteNewer && remote.stops.size >= 2) mergeTimelineStops0525(local.stops, remote.stops) else local.stops,
        publicToken = newerText(remote.publicToken, local.publicToken).orEmpty(),
        notes = newerText(remote.notes, local.notes).orEmpty(),
        remoteId = newerText(remote.remoteId, local.remoteId),
        publicUrl = newerText(remote.publicUrl, local.publicUrl),
        blablaProfileUuid = mergedProfileUuid.takeIf(String::isNotBlank)
            ?: local.blablaProfileUuid?.takeIf(String::isNotBlank),
        blablaTripId = mergedTripId.takeIf(String::isNotBlank)
            ?: local.blablaTripId?.takeIf(String::isNotBlank),
        blablaManageUrl = when {
            remoteNewer && remoteManage != null -> remoteManage
            localManage != null -> localManage
            remoteManage != null -> remoteManage
            else -> timelineDerivedManageUrl0525(mergedTripId)
        },
        blablaPublicUrl = when {
            remoteNewer && remotePublic != null -> remotePublic
            localPublicPersisted != null -> localPublicPersisted
            remotePublic != null -> remotePublic
            else -> null
        },
        publicBookingEnabled = if (remoteNewer) remote.publicBookingEnabled else local.publicBookingEnabled,
        itineraryAuthoritative = if (remoteNewer && remote.stops.size >= 2) remote.itineraryAuthoritative else local.itineraryAuthoritative,
        publishedSeats = if (remoteNewer) remote.publishedSeats ?: local.publishedSeats else local.publishedSeats ?: remote.publishedSeats,
        capacityReliable = if (remoteNewer) remote.capacityReliable else local.capacityReliable,
        updatedAtMillis = maxOf(local.updatedAtMillis, remote.updatedAtMillis),
        rotaCertaSeatAllocation = if (remoteNewer) remote.rotaCertaSeatAllocation ?: local.rotaCertaSeatAllocation
            else local.rotaCertaSeatAllocation ?: remote.rotaCertaSeatAllocation,
        recordOrigin = when {
            local.recordOrigin == TripRecordOrigin.EXTERNAL_BACKING -> local.recordOrigin
            remoteIdentityComplete -> TripRecordOrigin.EXTERNAL_BACKING
            else -> local.recordOrigin
        },
        canonicalRevision = maxOf(local.canonicalRevision, remote.canonicalRevision),
        seatAllocationVersionUsed = maxOf(local.seatAllocationVersionUsed, remote.seatAllocationVersionUsed),
        publicationRevision = maxOf(local.publicationRevision, remote.publicationRevision),
        tripKey = if (local.tripKey.isNotBlank()) local.tripKey else remote.tripKey,
        canonicalStateHash = if (remoteNewer && remote.canonicalStateHash.isNotBlank()) remote.canonicalStateHash
            else local.canonicalStateHash.ifBlank { remote.canonicalStateHash },
        publicTimezoneId0411 = newerText(remote.publicTimezoneId0411, local.publicTimezoneId0411).orEmpty(),
        lastObservedAtMillis = maxOf(local.lastObservedAtMillis, remote.lastObservedAtMillis),
    )
}

internal fun mergeCanonicalTimelineProjections0525(
    localPrimary: CanonicalTimelineProjection0494,
    remoteSecondary: CanonicalTimelineProjection0494,
    localProfileLabel: String = "Agenda",
    nowMillis: Long = System.currentTimeMillis(),
): CanonicalTimelineMergeResult0525 {
    if (remoteSecondary.trips.isEmpty()) {
        return CanonicalTimelineMergeResult0525(projection = localPrimary)
    }

    val localTrips = localPrimary.trips.toMutableList()
    val localById = localTrips.associateBy(Trip::id)
    val localByStrong = localTrips.mapNotNull { trip -> trip.timelineStrongIdentity0525()?.let { it to trip } }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (key, values) -> values.singleOrNull()?.let { key to it } }
        .toMap()
    val mergedTrips = LinkedHashMap<String, Trip>()
    localTrips.forEach { mergedTrips[it.id] = it }

    val remoteAcceptedByLocalId = mutableMapOf<String, Trip>()
    val conflicts = mutableSetOf<String>()
    var mergedCount = 0
    var recoveryCount = 0
    var incompleteIgnored = 0

    remoteSecondary.trips.forEach { remote ->
        val remoteStrong = remote.timelineStrongIdentity0525()
        val local = localById[remote.id] ?: remoteStrong?.let(localByStrong::get)
        if (local == null) {
            // Whole-trip recovery is accepted only when there is no local operational
            // Agenda at all. This prevents a stale server row from reviving a locally
            // tombstoned trip while still allowing disaster recovery on an empty store.
            if (localPrimary.trips.isEmpty() && remote.id.isNotBlank() && remote.stops.size >= 2) {
                mergedTrips[remote.id] = remote
                remoteAcceptedByLocalId[remote.id] = remote
                recoveryCount++
            }
            return@forEach
        }

        if (timelineExternalIdentityConflict0525(local, remote)) {
            conflicts += local.id
            return@forEach
        }

        if (timelineExternalIdentityIncomplete0525(remote)) {
            incompleteIgnored++
        }

        val merged = mergeTimelineTrip0525(local, remote)
        if (merged != local) {
            mergedTrips[local.id] = merged
            mergedCount++
        }
        remoteAcceptedByLocalId[local.id] = remote
    }

    val localBookingsByTrip = localPrimary.bookings.groupBy(Booking::tripId)
    val remoteBookingsByTrip = remoteSecondary.bookings.groupBy(Booking::tripId)
    val mergedBookings = mutableListOf<Booking>()

    mergedTrips.values.forEach { trip ->
        val localBookings = localBookingsByTrip[trip.id].orEmpty()
        val acceptedRemoteTrip = remoteAcceptedByLocalId[trip.id]
        val remoteTripId = acceptedRemoteTrip?.id
        val remoteBookings = remoteTripId?.let { remoteBookingsByTrip[it].orEmpty() }.orEmpty()
        if (acceptedRemoteTrip == null || conflicts.contains(trip.id)) {
            mergedBookings += localBookings
            return@forEach
        }

        val remoteTripNewer = acceptedRemoteTrip.canonicalRevision > (localById[trip.id]?.canonicalRevision ?: 0L)
        val remoteByIdentity = remoteBookings.groupBy(::timelineBookingIdentity0525)
        val consumedRemote = mutableSetOf<String>()
        localBookings.forEach { localBooking ->
            val key = timelineBookingIdentity0525(localBooking)
            val remoteBooking = remoteByIdentity[key]?.singleOrNull()
                ?: remoteBookings.firstOrNull { it.id == localBooking.id }
            if (remoteBooking == null) {
                mergedBookings += localBooking
            } else {
                consumedRemote += remoteBooking.id
                mergedBookings += mergeTimelineBooking0525(localBooking, remoteBooking, remoteTripNewer)
            }
        }
        if (remoteTripNewer) {
            remoteBookings.filterNot { it.id in consumedRemote }.forEach { remoteOnly ->
                mergedBookings += remoteOnly.copy(tripId = trip.id)
            }
        }
    }

    // Remote recovery on an empty local store carries its canonical bookings too.
    if (localPrimary.trips.isEmpty()) {
        mergedTrips.values.filter { it.id !in localById }.forEach { recovered ->
            remoteSecondary.bookings.filter { it.tripId == recovered.id }.forEach { remoteOnly ->
                if (mergedBookings.none { it.id == remoteOnly.id }) mergedBookings += remoteOnly.copy(tripId = recovered.id)
            }
        }
    }

    val mergedTripList = mergedTrips.values.sortedBy(Trip::departureAtMillis)
    val mergedBookingList = mergedBookings
        .distinctBy { booking -> "${booking.tripId}|${timelineBookingIdentity0525(booking)}" }

    val baseEntries = TripTimelineEngine.fromLocalAgenda(
        trips = mergedTripList,
        bookings = mergedBookingList,
        localProfileLabel = localProfileLabel,
        nowMillis = nowMillis,
    )
    val remoteEntriesById = remoteSecondary.entries.associateBy(TripTimelineEntry::tripId)
    val remoteEntriesByStrong = remoteSecondary.entries.mapNotNull { entry ->
        entry.timelineStrongIdentity0525()?.let { it to entry }
    }.groupBy({ it.first }, { it.second })
        .mapNotNull { (key, values) -> values.singleOrNull()?.let { key to it } }
        .toMap()

    val entries = baseEntries.map { base ->
        val trip = mergedTrips[base.tripId] ?: return@map base
        val remoteTrip = remoteAcceptedByLocalId[base.tripId]
        val remoteEntry = remoteTrip?.let {
            remoteEntriesById[it.id] ?: it.timelineStrongIdentity0525()?.let(remoteEntriesByStrong::get)
        }
        val localOriginal = localById[base.tripId]
        val remoteNewer = remoteTrip != null && remoteTrip.canonicalRevision > (localOriginal?.canonicalRevision ?: 0L)
        val mergedStrongComplete = trip.timelineStrongIdentity0525() != null
        val inheritedIssues = remoteEntry?.issues.orEmpty()
            .filterNot { it == TripTimelineIssue.EXTERNAL_IDENTITY_INCOMPLETE && mergedStrongComplete }
            .toSet()
        val issues = buildSet {
            addAll(base.issues)
            if (remoteNewer) addAll(inheritedIssues)
            if (base.tripId in conflicts) add(TripTimelineIssue.EXTERNAL_IDENTITY_CONFLICT)
        }

        base.copy(
            issues = issues,
            canonicalBackendAuthoritative0494 = remoteNewer && remoteEntry?.canonicalBackendAuthoritative0494 == true,
            canonicalRevision0494 = trip.canonicalRevision,
            canonicalStateHash0494 = trip.canonicalStateHash,
            canonicalAvailableSeatsMinimum0494 = if (remoteNewer) remoteEntry?.canonicalAvailableSeatsMinimum0494 else null,
            canonicalAvailableSeatsMaximum0494 = if (remoteNewer) remoteEntry?.canonicalAvailableSeatsMaximum0494 else null,
            canonicalOverbookingSeats0494 = if (remoteNewer) remoteEntry?.canonicalOverbookingSeats0494 ?: 0 else 0,
            canonicalCapacityReliable0494 = trip.capacityReliable,
            canonicalUpdatedAtMillis0494 = maxOf(trip.updatedAtMillis, remoteEntry?.canonicalUpdatedAtMillis0494 ?: 0L),
            canonicalSegmentLoads0494 = if (remoteNewer) remoteEntry?.canonicalSegmentLoads0494.orEmpty() else emptyList(),
            canonicalSegmentPassengerLoads0494 = if (remoteNewer) remoteEntry?.canonicalSegmentPassengerLoads0494.orEmpty() else emptyList(),
            canonicalSegmentBlockedLoads0494 = if (remoteNewer) remoteEntry?.canonicalSegmentBlockedLoads0494.orEmpty() else emptyList(),
            canonicalSegmentAvailableSeats0494 = if (remoteNewer) remoteEntry?.canonicalSegmentAvailableSeats0494.orEmpty() else emptyList(),
            canonicalOccupancyRevision0494 = if (remoteNewer) remoteEntry?.canonicalOccupancyRevision0494 else null,
            remoteTripId0494 = remoteEntry?.remoteTripId0494?.takeIf(String::isNotBlank)
                ?: base.remoteTripId0494,
            publicUrl0494 = remoteEntry?.publicUrl0494?.takeIf(String::isNotBlank)
                ?: base.publicUrl0494,
        )
    }

    return CanonicalTimelineMergeResult0525(
        projection = CanonicalTimelineProjection0494(
            trips = mergedTripList,
            bookings = mergedBookingList,
            entries = entries,
            snapshotAtMillis = maxOf(localPrimary.snapshotAtMillis, remoteSecondary.snapshotAtMillis),
        ),
        mergedTrips = mergedCount,
        remoteRecoveryTrips = recoveryCount,
        incompleteRemoteIgnored = incompleteIgnored,
        conflictsRejected = conflicts.size,
    )
}

object TripTimelineEngine {
    fun fromLocalAgenda(
        trips: List<Trip>,
        bookings: List<Booking>,
        localProfileId: String = "local",
        localProfileLabel: String = "Agenda",
        nowMillis: Long = System.currentTimeMillis(),
    ): List<TripTimelineEntry> {
        val base = trips
            .filterNot { it.status == TripStatus.CANCELLED }
            .mapNotNull { trip ->
                val stops = trip.stops.sortedBy(TripStop::order)
                if (stops.size < 2) return@mapNotNull null
                val tripBookings = bookings.filter { it.tripId == trip.id }
                val loads = SeatAvailabilityEngine.segmentLoads(trip, tripBookings, nowMillis)
                val occupied = loads.map(SegmentLoad::occupiedSeats)
                TripTimelineEntry(
                    tripId = trip.id,
                    profileId = localProfileId,
                    profileLabel = localProfileLabel,
                    departureAtMillis = trip.departureAtMillis,
                    arrivalAtMillis = stops.last().plannedArrivalMillis,
                    origin = stops.first().name,
                    destination = stops.last().name,
                    status = trip.status,
                    capacity = trip.capacity,
                    rotaCertaSeatAllocation = trip.rotaCertaSeatAllocation,
                    minimumOccupiedSeats = occupied.minOrNull() ?: 0,
                    maximumOccupiedSeats = occupied.maxOrNull() ?: 0,
                    sourcePassengerSeats = passengerSeatsBySource0494(tripBookings),
                    operationalBlockedSeats = operationalSeatSummary(trip, tripBookings, nowMillis).blockedSeats,
                    localTripId = trip.id,
                    blablaTripId = trip.blablaTripId,
                    blablaTripHref = trip.blablaManageUrl,
                    blablaPublicHref = trip.blablaPublicUrl,
                    blablaProfileUuid = trip.blablaProfileUuid,
                    blablaItineraryStops = stops.map(TripStop::name),
                    blablaPublishedSeats = trip.publishedSeats,
                    canonicalRevision0494 = trip.canonicalRevision,
                    canonicalStateHash0494 = trip.canonicalStateHash,
                    canonicalCapacityReliable0494 = trip.capacityReliable,
                    canonicalUpdatedAtMillis0494 = trip.updatedAtMillis,
                )
            }
            .sortedBy(TripTimelineEntry::departureAtMillis)
        return annotate(base)
    }

    fun annotate(entries: List<TripTimelineEntry>): List<TripTimelineEntry> {
        if (entries.isEmpty()) return emptyList()
        val issues = entries.associate { it.tripId to it.issues.toMutableSet() }.toMutableMap()

        entries.forEach { entry ->
            if (entry.capacity > 0 && entry.maximumOccupiedSeats > entry.capacity) {
                issues.getValue(entry.tripId) += TripTimelineIssue.OVERBOOKING
            }
        }

        entries.groupBy { duplicateKey(it) }.values
            .filter { it.size > 1 }
            .forEach { group -> group.forEach { issues.getValue(it.tripId) += TripTimelineIssue.DUPLICATE } }

        val chronological = entries.sortedBy(TripTimelineEntry::departureAtMillis)
        chronological.zipWithNext().forEach { (previous, next) ->
            if (normalizePlace(previous.destination) != normalizePlace(next.origin)) {
                issues.getValue(next.tripId) += TripTimelineIssue.PROFILE_CONTINUITY
            }
        }

        val ordered = entries.sortedBy(TripTimelineEntry::departureAtMillis)
        for (leftIndex in ordered.indices) {
            val left = ordered[leftIndex]
            val leftEnd = left.arrivalAtMillis
            for (rightIndex in leftIndex + 1 until ordered.size) {
                val right = ordered[rightIndex]
                if (left.profileId == right.profileId && left.tripId == right.tripId) continue
                if (leftEnd != null) {
                    if (right.departureAtMillis >= leftEnd) break
                    issues.getValue(left.tripId) += TripTimelineIssue.PHYSICAL_CONFLICT
                    issues.getValue(right.tripId) += TripTimelineIssue.PHYSICAL_CONFLICT
                } else if (left.departureAtMillis == right.departureAtMillis && left.tripId != right.tripId) {
                    issues.getValue(left.tripId) += TripTimelineIssue.PHYSICAL_CONFLICT
                    issues.getValue(right.tripId) += TripTimelineIssue.PHYSICAL_CONFLICT
                }
            }
        }

        return entries.sortedBy(TripTimelineEntry::departureAtMillis).map { entry ->
            entry.copy(issues = issues.getValue(entry.tripId).toSet())
        }
    }

    private fun duplicateKey(entry: TripTimelineEntry): String = listOf(
        entry.departureAtMillis.toString(),
        normalizePlace(entry.origin),
        normalizePlace(entry.destination),
    ).joinToString("|")

    private fun normalizePlace(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}

/**
 * Pure presentation filter for the Timeline. It must be called only after the
 * physical agenda has already been merged/consolidated/validated.
 */
internal fun filterTimelineEntries(
    entries: List<TripTimelineEntry>,
    trips: List<Trip>,
    bookings: List<Booking>,
    query: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): List<TripTimelineEntry> {
    val terms = timelineSearchTerms(query)
    if (terms.isEmpty()) return entries

    val tripsById = trips.associateBy(Trip::id)
    val bookingsByTrip = bookings
        .asSequence()
        .filter { booking ->
            booking.capacityClaimType == CapacityClaimType.PASSENGER && booking.seats > 0 && when (booking.status) {
                BookingStatus.REQUESTED,
                BookingStatus.CONFIRMED,
                -> true
                BookingStatus.HELD -> booking.holdExpiresAtMillis == null || booking.holdExpiresAtMillis > nowMillis
                BookingStatus.REJECTED,
                BookingStatus.CANCELLED,
                BookingStatus.EXPIRED,
                -> false
            }
        }
        .groupBy(Booking::tripId)

    return entries.filter { entry ->
        val localTrip = entry.localTripId?.let(tripsById::get) ?: tripsById[entry.tripId]
        val stopsById = localTrip?.stops.orEmpty().associateBy(TripStop::id)
        val localPassengerParts = localTrip?.let { trip ->
            bookingsByTrip[trip.id].orEmpty().flatMap { booking ->
                listOfNotNull(
                    booking.passengerName,
                    booking.passengerContact,
                    stopsById[booking.boardingStopId]?.name,
                    stopsById[booking.dropoffStopId]?.name,
                    timelineSourceLabel(booking.source),
                    booking.source.name,
                )
            }
        }.orEmpty()
        val externalPassengerParts = if (entry.canonicalBackendAuthoritative0494) {
            emptyList()
        } else {
            entry.blablaPassengers.flatMap { passenger ->
                listOfNotNull(
                    passenger.name,
                    passenger.phone,
                    passenger.boarding,
                    passenger.dropoff,
                    "BlaBlaCar",
                )
            }
        }
        val dateParts = timelineDateSearchParts(entry.departureAtMillis, zoneId, locale)
        val haystack = normalizeTimelineSearchText(
            buildList {
                add(entry.profileLabel)
                add(entry.profileId)
                entry.blablaProfileUuid?.let(::add)
                add(entry.origin)
                add(entry.destination)
                addAll(dateParts)
                addAll(externalPassengerParts)
                addAll(localPassengerParts)
            }.joinToString(" ")
        )
        terms.all(haystack::contains)
    }
}

internal fun timelineSearchTerms(query: String): List<String> = normalizeTimelineSearchText(query)
    .split(' ')
    .filter(String::isNotBlank)
    .distinct()

internal fun normalizeTimelineSearchText(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun timelineDateSearchParts(epochMillis: Long, zoneId: ZoneId, locale: Locale): List<String> {
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
    return listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd", locale).format(dateTime),
        DateTimeFormatter.ofPattern("dd/MM/yyyy", locale).format(dateTime),
        DateTimeFormatter.ofPattern("dd/MM", locale).format(dateTime),
        DateTimeFormatter.ofPattern("HH:mm", locale).format(dateTime),
        DateTimeFormatter.ofPattern("EEE dd MMM yyyy HH:mm", locale).format(dateTime),
        DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy HH:mm", locale).format(dateTime),
    )
}

private fun timelineSourceLabel(source: BookingSource): String = when (source) {
    BookingSource.BLABLACAR -> "BlaBlaCar"
    BookingSource.PRIVATE -> "Particular"
    BookingSource.ROTA_CERTA -> "Rota Certa"
    BookingSource.OTHER -> "Outro"
}
