package br.com.mapeiaia.rotacerta.trips

import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class TripStatus {
    DRAFT,
    PUBLISHED,
    FULL,
    STARTING,
    ACTIVE,
    COMPLETED,
    CANCELLED,
}

@Serializable
enum class BookingStatus {
    REQUESTED,
    HELD,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    EXPIRED,
}

@Serializable
enum class PassengerOperationalStatus {
    PENDING,
    CONFIRMED,
    AT_LOCATION,
    IN_CAR,
    COMPLETED,
    CANCELLED,
}

@Serializable
enum class PassengerPaymentStatus {
    UNPAID,
    PAID,
}

@Serializable
enum class BookingSource {
    ROTA_CERTA,
    BLABLACAR,
    PRIVATE,
    OTHER,
}

@Serializable
enum class CapacityClaimType {
    /** A real passenger/reservation that occupies the trip inventory on its traveled segments. */
    PASSENGER,
    /** Real external occupancy captured without creating a duplicate local passenger identity. */
    EXTERNAL_OCCUPANCY,
    /** A seat deliberately unavailable for booking. If linked to a passenger by occupancyGroupId it is a mirror, not an extra seat. */
    RESERVED_SEAT,
}

@Serializable
data class TripStop(
    val id: String = UUID.randomUUID().toString(),
    val order: Int,
    val name: String,
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val plannedArrivalMillis: Long? = null,
    val plannedDepartureMillis: Long? = null,
    val priceToNextCents: Long = 0L,
)

@Serializable
data class Trip(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val departureAtMillis: Long,
    /** Derived simultaneous operational inventory ceiling for this trip. Never sourced from legacy vehicleCapacity. */
    val capacity: Int = 0,
    val status: TripStatus = TripStatus.DRAFT,
    val stops: List<TripStop>,
    val publicToken: String = UUID.randomUUID().toString().replace("-", ""),
    val notes: String = "",
    val remoteId: String? = null,
    val publicUrl: String? = null,
    /** Strong BlaBlaCar identity captured from the exact external card. */
    val blablaProfileUuid: String? = null,
    val blablaTripId: String? = null,
    /** Authenticated driver/admin target. Never expose this URL to passengers. */
    val blablaManageUrl: String? = null,
    /** Exact passenger-facing /trip URL observed for this same BlaBlaCar trip. */
    val blablaPublicUrl: String? = null,
    /** Public passenger portal is opt-in. A synchronized BlaBlaCar card is never exposed automatically. */
    val publicBookingEnabled: Boolean = false,
    /** True only when intermediate stops are complete/authoritative for this published trip. */
    val itineraryAuthoritative: Boolean = true,
    /** Synchronized BlaBlaCar seat quota for this trip. Legacy serialized field name kept for persistence compatibility. */
    val publishedSeats: Int? = null,
    /** External trips stay fail-closed until the channel inventory and occupancy claims are reconciled. */
    val capacityReliable: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    /** Seats allocated to the Rota Certa channel for this trip. Explicit zero is valid. */
    val rotaCertaSeatAllocation: Int? = null,
    /** Canonical persisted origin. Old 0.1.372 external backings are resolved by strong-identity migration. */
    val recordOrigin: TripRecordOrigin = TripRecordOrigin.LOCAL,
    /** Monotonic local canonical-state revision. tenantId + id is the stable internal trip identity. */
    val canonicalRevision: Long = 0L,
    /** Tenant seat-allocation configuration version used by this canonical snapshot. */
    val seatAllocationVersionUsed: Long = 0L,
    /** Monotonic public-entity revision. Zero keeps compatibility with legacy/full-sync payloads. */
    val publicationRevision: Long = 0L,
    /** Versioned public deletion marker. Local history/bookings remain preserved. */
    val publicationTombstone: Boolean = false,
    /** Durable outbox event id used only for publication idempotency/audit. */
    val publicationEventId: String = "",
    /**
     * Last normalized BlaBlaCar observation accepted into the canonical TripStore.
     * The collector is only an input; Timeline/Agenda projections must not read its volatile cache directly.
     */
    val externalSnapshot: BlaBlaCollectorTrip? = null,
    /** Semantic fingerprint of [externalSnapshot], excluding volatile browser/UI attributes. */
    val externalSnapshotFingerprint: String = "",
    /** False means the observation was partial and must not replace a previously complete canonical snapshot. */
    val externalSnapshotComplete: Boolean = false,
    /** Stable tenant-scoped identity. External trips are tenant + provider + profile UUID + provider trip id. */
    val tripKey: String = "",
    /** Deterministic hash of the canonical operational state. */
    val canonicalStateHash: String = "",
    /** Explicit public timezone when known. Empty legacy values are normalized at projection time. */
    val publicTimezoneId0411: String = "",
    /** Evidence about the current canonical/public projection revision. Never a source of truth. */
    val publicMirrorAttestationState0411: PublicMirrorAttestationState0411 = PublicMirrorAttestationState0411.UNPROVEN,
    val publicMirrorAttestedCanonicalRevision0411: Long = 0L,
    val publicMirrorAttestedPublicationRevision0411: Long = 0L,
    val publicMirrorExpectedHash0411: String = "",
    val publicMirrorReadbackHash0411: String = "",
    val publicMirrorAttestedAtMillis0411: Long = 0L,
    val publicMirrorReadbackLatencyMillis0411: Long = 0L,
    val publicMirrorAttestationReason0411: String = "",
    val publicMirrorMismatchFields0411: List<String> = emptyList(),
    /** Collector execution that last observed this trip. Metadata only; it never defines identity. */
    val lastCollectionRunId: String = "",
    /** Monotonic collector generation used to reject delayed results from older executions. */
    val lastCollectionGeneration: Long = 0L,
    val lastObservedAtMillis: Long = 0L,
    /** Canonical tombstone. Deleted trips remain durable so projections can be repaired after crashes. */
    val deleted: Boolean = false,
    val deletedAtMillis: Long = 0L,
)

internal enum class CanonicalTripRevisionDecision0395 {
    UPDATE,
    SKIP_NO_CHANGE,
    SKIP_STALE_REVISION,
}

internal fun canonicalTripRevisionDecision0395(
    currentRevision: Long,
    incomingRevision: Long,
    semanticChanged: Boolean,
): CanonicalTripRevisionDecision0395 = when {
    currentRevision > 0L && incomingRevision < currentRevision -> CanonicalTripRevisionDecision0395.SKIP_STALE_REVISION
    semanticChanged || incomingRevision > currentRevision -> CanonicalTripRevisionDecision0395.UPDATE
    else -> CanonicalTripRevisionDecision0395.SKIP_NO_CHANGE
}

internal fun nextCanonicalTripRevision0395(
    currentRevision: Long,
    incomingRevision: Long,
    semanticChanged: Boolean,
): Long = if (semanticChanged) {
    maxOf(currentRevision, incomingRevision) + 1L
} else {
    maxOf(currentRevision, incomingRevision)
}

internal fun canonicalTripStateHash0406(
    trip: Trip,
    bookings: List<Booking>,
): String {
    val relevantBookings = bookings.asSequence()
        .filter { it.tripId == trip.id }
        .sortedWith(
            compareBy<Booking>(
                { bookingOccupancyIdentityKey(it) },
                { it.id },
            ),
        )
        .toList()
    val semantic = buildString {
        append(trip.tripKey).append('|')
        append(trip.recordOrigin.name).append('|')
        append(trip.blablaProfileUuid.orEmpty().trim().lowercase()).append('|')
        append(trip.blablaTripId.orEmpty().trim()).append('|')
        append(trip.departureAtMillis).append('|')
        append(trip.status.name).append('|')
        append(trip.capacity).append('|')
        append(trip.publishedSeats ?: -1).append('|')
        append(trip.rotaCertaSeatAllocation ?: -1).append('|')
        append(trip.capacityReliable).append('|')
        append(trip.publicBookingEnabled).append('|')
        append(trip.itineraryAuthoritative).append('|')
        append(trip.deleted).append('|')
        trip.stops.sortedBy(TripStop::order).forEach { stop ->
            append(stop.order).append('~')
            append(stop.id).append('~')
            append(stop.name.trim()).append('~')
            append(stop.address.trim()).append('~')
            append(stop.plannedArrivalMillis ?: -1L).append('~')
            append(stop.plannedDepartureMillis ?: -1L).append(',')
        }
        append('|')
        relevantBookings.forEach { booking ->
            append(bookingOccupancyIdentityKey(booking)).append('~')
            append(booking.boardingStopId).append('~')
            append(booking.dropoffStopId).append('~')
            append(booking.seats).append('~')
            append(booking.status.name).append('~')
            append(booking.operationalStatus.name).append('~')
            append(booking.paymentStatus.name).append('~')
            append(booking.source.name).append('~')
            append(booking.capacityClaimType.name).append(',')
        }
    }
    return "tripstate-v1:" + MessageDigest.getInstance("SHA-256")
        .digest(semantic.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

@Serializable
data class Booking(
    val id: String = UUID.randomUUID().toString(),
    val tripId: String,
    val passengerName: String,
    val passengerContact: String = "",
    val boardingStopId: String,
    val dropoffStopId: String,
    val seats: Int = 1,
    val status: BookingStatus = BookingStatus.REQUESTED,
    /**
     * Journey phase for this exact passenger occurrence. Separate from BookingStatus so
     * operational updates never distort the segment-capacity engine.
     */
    val operationalStatus: PassengerOperationalStatus = PassengerOperationalStatus.CONFIRMED,
    /** Payment acknowledgement is orthogonal to the journey phase. */
    val paymentStatus: PassengerPaymentStatus = PassengerPaymentStatus.UNPAID,
    /** Last explicit Timeline choice; used only for the compact driver status label. */
    val lastDriverSelection: String = "",
    val holdExpiresAtMillis: Long? = null,
    val cancellationToken: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val source: BookingSource = BookingSource.OTHER,
    val capacityClaimType: CapacityClaimType = CapacityClaimType.PASSENGER,
    val sourceReference: String = "",
    val occupancyGroupId: String? = null,
    /** Canonical Rota Certa person identity. This is deliberately distinct from Booking.id. */
    val passengerId: String = "",
    /** Fare for this reservation/segment, never a permanent property of the passenger. */
    val fareMinorUnits: Long? = null,
    /** ISO 4217 code when known. Empty means the original source did not provide a reliable currency. */
    val fareCurrencyCode: String = "",
    /** Exact reservation pickup address; the shared TripStop remains the route-order authority. */
    val boardingAddress: String = "",
    /** Exact reservation dropoff address; the shared TripStop remains the route-order authority. */
    val dropoffAddress: String = "",
    /** True only after Rota Certa deliberately wrote the local-only identity/fare/address metadata. */
    val localMetadataTouched: Boolean = false,
)

internal fun bookingOccupancyIdentityKey(booking: Booking): String =
    booking.occupancyGroupId?.trim()?.takeIf(String::isNotEmpty)?.let { "group:$it" }
        ?: booking.sourceReference.trim().takeIf(String::isNotEmpty)?.let { "reference:$it" }
        ?: booking.passengerId.trim().takeIf(String::isNotEmpty)?.let { "passenger:$it" }
        ?: "booking:${booking.id}"

/**
 * Canonical operational inventory for a trip.
 *
 * Quota is capacity. Confirmed passengers/reservations are occupancy and must
 * never be added to this ceiling. The segment engine subtracts each unique
 * occupancy exactly once on the segments it actually travels.
 */
fun operationalInventoryCapacity(
    trip: Trip,
    @Suppress("UNUSED_PARAMETER") bookings: List<Booking>,
): Int {
    val blablaQuota = trip.publishedSeats?.takeIf { it in 0..999 } ?: 0
    val rotaCertaQuota = trip.rotaCertaSeatAllocation?.takeIf { it in 0..999 } ?: 0
    return (blablaQuota + rotaCertaQuota).coerceIn(0, 999)
}

data class SegmentLoad(
    val from: TripStop,
    val to: TripStop,
    /** Total inventory consumed on this segment: confirmed passengers + blocked/held seats. */
    val occupiedSeats: Int,
    val availableSeats: Int,
    /** Confirmed real passengers on this segment, after occupancyGroupId deduplication. */
    val passengerSeats: Int = occupiedSeats,
    /** Seats unavailable but not yet a confirmed passenger: explicit blocks, holds and pending requests. */
    val blockedSeats: Int = 0,
    /** Positive only when confirmed + blocked exceeds this trip's derived operational ceiling. */
    val overbookingSeats: Int = 0,
)

data class SeatAvailability(
    val boardingStop: TripStop,
    val dropoffStop: TripStop,
    val requestedSeats: Int,
    val availableSeats: Int,
    val canBook: Boolean,
    val segmentLoads: List<SegmentLoad>,
)

data class SeatAvailabilityRange(
    val minimum: Int,
    val maximum: Int,
) {
    val variesBySegment: Boolean
        get() = minimum != maximum
}
data class TripOperationalSeatSummary(
    val operationalLimitConfigured: Boolean,
    /** Synchronized BlaBlaCar quota before occupancy. */
    val blablaQuotaSeats: Int,
    /** Configured Rota Certa quota before occupancy. */
    val rotaCertaQuotaSeats: Int,
    /** blablaQuotaSeats + rotaCertaQuotaSeats. */
    val operationalInventorySeats: Int,
    /** Minimum remaining seats across all trip segments. */
    val totalAvailableSeats: Int,
    val confirmedPassengerSeats: Int,
    val blockedSeats: Int,
    val availableSeats: Int,
    val overbookingSeats: Int,
)

/**
 * Canonical whole-trip inventory/occupancy summary.
 *
 * Quotas create the operational inventory. Confirmed passengers and blocked
 * seats consume that inventory through the shared per-segment engine.
 */
fun operationalSeatSummary(
    trip: Trip,
    bookings: List<Booking>,
    nowMillis: Long = System.currentTimeMillis(),
): TripOperationalSeatSummary {
    val blablaQuota = trip.publishedSeats?.takeIf { it in 0..999 }
    val rotaCertaQuota = trip.rotaCertaSeatAllocation?.takeIf { it in 0..999 }
    val operationalLimitConfigured = blablaQuota != null || rotaCertaQuota != null
    val normalizedBlablaQuota = blablaQuota ?: 0
    val normalizedRotaCertaQuota = rotaCertaQuota ?: 0

    data class Group(
        var externalConfirmed: Int = 0,
        var localConfirmed: Int = 0,
        var localBlocked: Int = 0,
    )

    val groups = mutableMapOf<String, Group>()
    bookings.asSequence()
        .filter { it.tripId == trip.id && it.seats > 0 }
        .filter { booking ->
            when (booking.status) {
                BookingStatus.CONFIRMED,
                BookingStatus.REQUESTED,
                -> true
                BookingStatus.HELD -> booking.holdExpiresAtMillis == null || booking.holdExpiresAtMillis > nowMillis
                BookingStatus.REJECTED,
                BookingStatus.CANCELLED,
                BookingStatus.EXPIRED,
                -> false
            }
        }
        .forEach { booking ->
            val group = groups.getOrPut(bookingOccupancyIdentityKey(booking)) { Group() }
            val external = booking.capacityClaimType == CapacityClaimType.EXTERNAL_OCCUPANCY ||
                booking.source == BookingSource.BLABLACAR

            when (booking.capacityClaimType) {
                CapacityClaimType.PASSENGER,
                CapacityClaimType.EXTERNAL_OCCUPANCY,
                -> when (booking.status) {
                    BookingStatus.CONFIRMED -> {
                        if (external) group.externalConfirmed = maxOf(group.externalConfirmed, booking.seats)
                        else group.localConfirmed = maxOf(group.localConfirmed, booking.seats)
                    }
                    BookingStatus.REQUESTED,
                    BookingStatus.HELD,
                    -> if (!external) group.localBlocked = maxOf(group.localBlocked, booking.seats)
                    BookingStatus.REJECTED,
                    BookingStatus.CANCELLED,
                    BookingStatus.EXPIRED,
                    -> Unit
                }
                CapacityClaimType.RESERVED_SEAT -> if (!external) {
                    group.localBlocked = maxOf(group.localBlocked, booking.seats)
                }
            }
        }

    var confirmedPassengers = 0
    var blockedSeats = 0

    groups.values.forEach { group ->
        val confirmed = maxOf(group.externalConfirmed, group.localConfirmed).coerceAtLeast(0)
        confirmedPassengers += confirmed
        blockedSeats += (group.localBlocked - maxOf(group.externalConfirmed, group.localConfirmed)).coerceAtLeast(0)
    }

    val operationalInventory = operationalInventoryCapacity(trip, bookings)
    val inventoryTrip = trip.copy(capacity = operationalInventory)
    val canonicalLoads = SeatAvailabilityEngine.segmentLoads(inventoryTrip, bookings, nowMillis)
    val totalAvailable = canonicalLoads.minOfOrNull(SegmentLoad::availableSeats)
        ?: operationalInventory
    val overbooking = canonicalLoads.maxOfOrNull(SegmentLoad::overbookingSeats) ?: 0

    return TripOperationalSeatSummary(
        operationalLimitConfigured = operationalLimitConfigured,
        blablaQuotaSeats = normalizedBlablaQuota,
        rotaCertaQuotaSeats = normalizedRotaCertaQuota,
        operationalInventorySeats = operationalInventory,
        totalAvailableSeats = totalAvailable,
        confirmedPassengerSeats = confirmedPassengers,
        blockedSeats = blockedSeats,
        availableSeats = if (operationalLimitConfigured) totalAvailable else 0,
        overbookingSeats = if (operationalLimitConfigured) overbooking else 0,
    )
}

object DriverIdentityRules {
    // Reserve only paths that are actually owned by Firebase/API routing.
    // Human-facing words such as "agenda", "admin" or a driver's preferred name
    // remain available as public slugs when they are not already taken.
    private val reservedPublicUsernames = setOf(
        "v1",
        "calendar",
    )

    fun normalizeUsername(value: String): String {
        val ascii = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
        return ascii.replace(Regex("[^a-z0-9]+"), "-").trim('-').take(32)
    }

    fun isValidUsername(value: String): Boolean =
        value.length in 3..32 && value.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"))

    fun isReservedUsername(value: String): Boolean =
        normalizeUsername(value) in reservedPublicUsernames

    fun isValidPublicUsername(value: String): Boolean =
        isValidUsername(value) && !isReservedUsername(value)
}

object TripFareEngine {
    fun farePerSeatCents(trip: Trip, boardingStopId: String, dropoffStopId: String): Long {
        val stops = trip.stops.sortedBy(TripStop::order)
        val fromIndex = stops.indexOfFirst { it.id == boardingStopId }
        val toIndex = stops.indexOfFirst { it.id == dropoffStopId }
        require(fromIndex >= 0) { "Unknown boarding stop" }
        require(toIndex > fromIndex) { "Dropoff must be after boarding" }
        return (fromIndex until toIndex).sumOf { stops[it].priceToNextCents.coerceAtLeast(0L) }
    }

    fun totalFareCents(trip: Trip, boardingStopId: String, dropoffStopId: String, seats: Int): Long {
        require(seats > 0) { "Seats must be positive" }
        return farePerSeatCents(trip, boardingStopId, dropoffStopId) * seats.toLong()
    }
}

object SeatAvailabilityEngine {
    fun availability(
        trip: Trip,
        bookings: List<Booking>,
        boardingStopId: String,
        dropoffStopId: String,
        requestedSeats: Int = 1,
        nowMillis: Long = System.currentTimeMillis(),
    ): SeatAvailability {
        require(trip.capacity >= 0) { "Trip inventory cannot be negative" }
        require(requestedSeats > 0) { "Requested seats must be positive" }
        val orderedStops = trip.stops.sortedBy(TripStop::order)
        require(orderedStops.size >= 2) { "Trip must have at least two stops" }
        require(orderedStops.map(TripStop::order).distinct().size == orderedStops.size) {
            "Trip stop order must be unique"
        }
        val boardingIndex = orderedStops.indexOfFirst { it.id == boardingStopId }
        val dropoffIndex = orderedStops.indexOfFirst { it.id == dropoffStopId }
        require(boardingIndex >= 0) { "Unknown boarding stop" }
        require(dropoffIndex >= 0) { "Unknown dropoff stop" }
        require(boardingIndex < dropoffIndex) { "Dropoff must be after boarding" }

        val occupancy = reconciledOccupancy(trip, bookings, orderedStops, nowMillis)
        val loads = (boardingIndex until dropoffIndex).map { index ->
            val state = occupancy[index]
            SegmentLoad(
                from = orderedStops[index],
                to = orderedStops[index + 1],
                occupiedSeats = state.consumedSeats,
                availableSeats = (trip.capacity - state.consumedSeats).coerceAtLeast(0),
                passengerSeats = state.passengerSeats,
                blockedSeats = state.blockedSeats,
                overbookingSeats = (state.consumedSeats - trip.capacity).coerceAtLeast(0),
            )
        }
        val available = loads.minOfOrNull(SegmentLoad::availableSeats) ?: trip.capacity
        return SeatAvailability(
            boardingStop = orderedStops[boardingIndex],
            dropoffStop = orderedStops[dropoffIndex],
            requestedSeats = requestedSeats,
            availableSeats = available,
            canBook = trip.status in setOf(TripStatus.PUBLISHED, TripStatus.FULL) &&
                requestedSeats <= available,
            segmentLoads = loads,
        )
    }

    fun segmentLoads(
        trip: Trip,
        bookings: List<Booking>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<SegmentLoad> {
        val orderedStops = trip.stops.sortedBy(TripStop::order)
        if (orderedStops.size < 2) return emptyList()
        val occupancy = reconciledOccupancy(trip, bookings, orderedStops, nowMillis)
        return occupancy.indices.map { index ->
            val state = occupancy[index]
            SegmentLoad(
                from = orderedStops[index],
                to = orderedStops[index + 1],
                occupiedSeats = state.consumedSeats,
                availableSeats = (trip.capacity - state.consumedSeats).coerceAtLeast(0),
                passengerSeats = state.passengerSeats,
                blockedSeats = state.blockedSeats,
                overbookingSeats = (state.consumedSeats - trip.capacity).coerceAtLeast(0),
            )
        }
    }

    fun remainingSeatsForWholeTrip(
        trip: Trip,
        bookings: List<Booking>,
        nowMillis: Long = System.currentTimeMillis(),
    ): Int {
        return segmentLoads(trip, bookings, nowMillis)
            .minOfOrNull(SegmentLoad::availableSeats)
            ?: trip.capacity
    }

    fun availableSeatRange(
        trip: Trip,
        bookings: List<Booking>,
        nowMillis: Long = System.currentTimeMillis(),
    ): SeatAvailabilityRange {
        val available = segmentLoads(trip, bookings, nowMillis).map(SegmentLoad::availableSeats)
        return SeatAvailabilityRange(
            minimum = available.minOrNull() ?: trip.capacity,
            maximum = available.maxOrNull() ?: trip.capacity,
        )
    }

    fun suggestedStatus(
        trip: Trip,
        bookings: List<Booking>,
        nowMillis: Long = System.currentTimeMillis(),
    ): TripStatus {
        if (trip.status in setOf(TripStatus.DRAFT, TripStatus.CANCELLED, TripStatus.COMPLETED, TripStatus.ACTIVE, TripStatus.STARTING)) {
            return trip.status
        }
        val loads = segmentLoads(trip, bookings, nowMillis)
        val operational = operationalSeatSummary(trip, bookings, nowMillis)
        return if ((operational.operationalLimitConfigured && operational.availableSeats == 0) ||
            (loads.isNotEmpty() && loads.all { it.availableSeats == 0 })
        ) {
            TripStatus.FULL
        } else {
            TripStatus.PUBLISHED
        }
    }

    private data class SegmentOccupancyState(
        val passengerSeats: Int,
        val blockedSeats: Int,
    ) {
        val consumedSeats: Int
            get() = passengerSeats + blockedSeats
    }

    private data class OccupancyGroupClaims(
        var passengerSeats: Int = 0,
        var reservedSeats: Int = 0,
    )

    private fun reconciledOccupancy(
        trip: Trip,
        bookings: List<Booking>,
        orderedStops: List<TripStop>,
        nowMillis: Long,
    ): List<SegmentOccupancyState> {
        val claimsBySegment = Array(orderedStops.size - 1) { mutableMapOf<String, OccupancyGroupClaims>() }
        bookings.asSequence()
            .filter { it.tripId == trip.id }
            .filter { it.seats > 0 }
            .filter { occupiesCapacity(it, nowMillis) }
            .forEach { booking ->
                val fromIndex = orderedStops.indexOfFirst { it.id == booking.boardingStopId }
                val toIndex = orderedStops.indexOfFirst { it.id == booking.dropoffStopId }
                if (fromIndex >= 0 && toIndex > fromIndex) {
                    val claimKey = bookingOccupancyIdentityKey(booking)
                    for (segment in fromIndex until toIndex) {
                        val group = claimsBySegment[segment].getOrPut(claimKey) { OccupancyGroupClaims() }
                        when (booking.capacityClaimType) {
                            CapacityClaimType.PASSENGER,
                            CapacityClaimType.EXTERNAL_OCCUPANCY,
                            -> when (booking.status) {
                                BookingStatus.CONFIRMED ->
                                    if (booking.seats > group.passengerSeats) group.passengerSeats = booking.seats
                                BookingStatus.REQUESTED,
                                BookingStatus.HELD,
                                -> if (booking.seats > group.reservedSeats) group.reservedSeats = booking.seats
                                BookingStatus.REJECTED,
                                BookingStatus.CANCELLED,
                                BookingStatus.EXPIRED,
                                -> Unit
                            }
                            CapacityClaimType.RESERVED_SEAT ->
                                if (booking.seats > group.reservedSeats) group.reservedSeats = booking.seats
                        }
                    }
                }
            }

        return claimsBySegment.map { groups ->
            var passengers = 0
            var blocked = 0
            groups.values.forEach { group ->
                val passenger = group.passengerSeats.coerceAtLeast(0)
                val consumedByGroup = maxOf(passenger, group.reservedSeats.coerceAtLeast(0))
                passengers += passenger
                blocked += (consumedByGroup - passenger).coerceAtLeast(0)
            }
            SegmentOccupancyState(passengerSeats = passengers, blockedSeats = blocked)
        }
    }
    private fun occupiesCapacity(booking: Booking, nowMillis: Long): Boolean = when (booking.status) {
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
