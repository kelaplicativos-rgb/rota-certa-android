package br.com.mapeiaia.rotacerta.trips

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
    /** A real passenger/reservation that occupies a physical seat. */
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
    /** Physical simultaneous passenger capacity of the vehicle. */
    val capacity: Int = 3,
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
    /** BlaBlaCar seat-editor observation only. It is channel metadata, never physical vehicle capacity. */
    val publishedSeats: Int? = null,
    /** External trips stay fail-closed until physical capacity and occupancy claims are reconciled. */
    val capacityReliable: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    /** Operational Rota Certa allocation for this trip; never a physical capacity. */
    val rotaCertaSeatAllocation: Int? = null,
)

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

data class SegmentLoad(
    val from: TripStop,
    val to: TripStop,
    /** Total physical capacity consumed on this segment: confirmed passengers + blocked/held seats. */
    val occupiedSeats: Int,
    val availableSeats: Int,
    /** Confirmed real passengers on this segment, after occupancyGroupId deduplication. */
    val passengerSeats: Int = occupiedSeats,
    /** Seats unavailable but not yet a confirmed passenger: explicit blocks, holds and pending requests. */
    val blockedSeats: Int = 0,
    /** Positive only when confirmed + blocked exceeds the physical passenger capacity. */
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
    val blablaPublishedSeats: Int,
    val rotaCertaAllocatedSeats: Int,
    val totalConsideredSeats: Int,
    val confirmedPassengerSeats: Int,
    val blockedSeats: Int,
    val availableSeats: Int,
    val overbookingSeats: Int,
)

/**
 * Whole-trip operational inventory. This is deliberately separate from physical
 * per-segment capacity. A trip may serve more unique passengers across different
 * segments than the simultaneous physical capacity, but no segment may exceed it.
 */
fun operationalSeatSummary(
    trip: Trip,
    bookings: List<Booking>,
    nowMillis: Long = System.currentTimeMillis(),
): TripOperationalSeatSummary {
    val blablaConfigured = trip.publishedSeats?.takeIf { it in 0..999 }
    val rotaCertaConfigured = trip.rotaCertaSeatAllocation?.takeIf { it in 0..999 }
    val operationalLimitConfigured = blablaConfigured != null || rotaCertaConfigured != null
    val blabla = blablaConfigured ?: 0
    val rotaCerta = rotaCertaConfigured ?: 0
    val total = if (operationalLimitConfigured) (blabla + rotaCerta).coerceAtMost(999) else 0

    data class Group(var confirmed: Int = 0, var blocked: Int = 0)
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
            val key = booking.occupancyGroupId?.trim()?.takeIf(String::isNotEmpty)
                ?.let { "group:$it" }
                ?: "booking:${booking.id}"
            val group = groups.getOrPut(key) { Group() }
            when (booking.capacityClaimType) {
                CapacityClaimType.PASSENGER,
                CapacityClaimType.EXTERNAL_OCCUPANCY,
                -> when (booking.status) {
                    BookingStatus.CONFIRMED -> group.confirmed = maxOf(group.confirmed, booking.seats)
                    BookingStatus.REQUESTED,
                    BookingStatus.HELD,
                    -> group.blocked = maxOf(group.blocked, booking.seats)
                    BookingStatus.REJECTED,
                    BookingStatus.CANCELLED,
                    BookingStatus.EXPIRED,
                    -> Unit
                }
                CapacityClaimType.RESERVED_SEAT -> group.blocked = maxOf(group.blocked, booking.seats)
            }
        }

    var confirmed = 0
    var blocked = 0
    groups.values.forEach { group ->
        confirmed += group.confirmed.coerceAtLeast(0)
        blocked += (maxOf(group.confirmed, group.blocked) - group.confirmed).coerceAtLeast(0)
    }
    val consumed = confirmed + blocked
    return TripOperationalSeatSummary(
        operationalLimitConfigured = operationalLimitConfigured,
        blablaPublishedSeats = blabla,
        rotaCertaAllocatedSeats = rotaCerta,
        totalConsideredSeats = total,
        confirmedPassengerSeats = confirmed,
        blockedSeats = blocked,
        availableSeats = if (operationalLimitConfigured) (total - consumed).coerceAtLeast(0) else Int.MAX_VALUE,
        overbookingSeats = if (operationalLimitConfigured) (consumed - total).coerceAtLeast(0) else 0,
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
        require(trip.capacity > 0) { "Trip capacity must be positive" }
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
        val physicalAvailable = loads.minOfOrNull(SegmentLoad::availableSeats) ?: trip.capacity
        val operational = operationalSeatSummary(trip, bookings, nowMillis)
        val available = if (operational.operationalLimitConfigured) {
            minOf(physicalAvailable, operational.availableSeats)
        } else {
            physicalAvailable
        }
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
        val physical = segmentLoads(trip, bookings, nowMillis)
            .minOfOrNull(SegmentLoad::availableSeats)
            ?: trip.capacity
        val operational = operationalSeatSummary(trip, bookings, nowMillis)
        return if (operational.operationalLimitConfigured) minOf(physical, operational.availableSeats) else physical
    }

    fun availableSeatRange(
        trip: Trip,
        bookings: List<Booking>,
        nowMillis: Long = System.currentTimeMillis(),
    ): SeatAvailabilityRange {
        val physical = segmentLoads(trip, bookings, nowMillis).map(SegmentLoad::availableSeats)
        val operational = operationalSeatSummary(trip, bookings, nowMillis)
        val available = if (operational.operationalLimitConfigured) {
            physical.map { minOf(it, operational.availableSeats) }
        } else {
            physical
        }
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
                    val explicitGroup = booking.occupancyGroupId?.trim()?.takeIf { it.isNotEmpty() }
                    val claimKey = explicitGroup?.let { "group:$it" } ?: "booking:${booking.id}"
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
