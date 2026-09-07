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
}

internal data class CanonicalTimelineProjection0494(
    val trips: List<Trip>,
    val bookings: List<Booking>,
    val entries: List<TripTimelineEntry>,
    val snapshotAtMillis: Long,
)

internal fun canonicalTimelineProjection0494(
    response: DriverTripSyncStateResponse0402?,
    fallbackProfileLabel: String = "Rota Certa",
): CanonicalTimelineProjection0494 {
    if (response == null) return CanonicalTimelineProjection0494(emptyList(), emptyList(), emptyList(), 0L)
    require(response.source.isBlank() || response.source == "CANONICAL_BACKEND") {
        "Timeline recusou datasource não canônico: ${response.source}"
    }

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
        val sourceCounts = state.sourceSeatCounts.mapNotNull { (source, seats) ->
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
            blablaPublicUrl = state.blablaPublicUrl.takeIf(String::isNotBlank),
            publicBookingEnabled = state.publicBookingEnabled,
            itineraryAuthoritative = state.itineraryAuthoritative,
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
            Booking(
                id = remote.id,
                tripId = canonicalId,
                passengerId = remote.passengerId,
                passengerName = remote.passengerName,
                passengerContact = remote.passengerContact,
                boardingStopId = remote.boardingStopId,
                dropoffStopId = remote.dropoffStopId,
                seats = remote.seats.coerceAtLeast(1),
                status = runCatching { BookingStatus.valueOf(remote.status.trim().uppercase()) }
                    .getOrDefault(BookingStatus.CONFIRMED),
                operationalStatus = remote.operationalStatus,
                paymentStatus = remote.paymentStatus,
                lastDriverSelection = remote.lastDriverSelection,
                holdExpiresAtMillis = remote.holdExpiresAtMillis,
                createdAtMillis = remote.createdAtMillis,
                updatedAtMillis = remote.updatedAtMillis,
                source = remote.source,
                capacityClaimType = remote.capacityClaimType,
                sourceReference = remote.sourceReference,
                occupancyGroupId = remote.occupancyGroupId,
            )
        }
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
                    sourcePassengerSeats = passengerSeatsBySource(tripBookings, nowMillis),
                    operationalBlockedSeats = operationalSeatSummary(trip, tripBookings, nowMillis).blockedSeats,
                    localTripId = trip.id,
                    blablaTripId = trip.blablaTripId,
                    blablaTripHref = trip.blablaManageUrl,
                    blablaPublicHref = trip.blablaPublicUrl,
                    blablaProfileUuid = trip.blablaProfileUuid,
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

    private fun passengerSeatsBySource(bookings: List<Booking>, @Suppress("UNUSED_PARAMETER") nowMillis: Long): Map<BookingSource, Int> {
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
        val externalPassengerParts = entry.blablaPassengers.flatMap { passenger ->
            listOfNotNull(
                passenger.name,
                passenger.phone,
                passenger.boarding,
                passenger.dropoff,
                "BlaBlaCar",
            )
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
