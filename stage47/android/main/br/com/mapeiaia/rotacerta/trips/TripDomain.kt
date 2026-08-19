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
    CANCELLED,
    EXPIRED,
}

@Serializable
enum class BookingSource {
    BLABLACAR,
    PRIVATE,
    OTHER,
}

@Serializable
enum class CapacityClaimType {
    PASSENGER,
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
)

@Serializable
data class Trip(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val departureAtMillis: Long,
    val capacity: Int = 3,
    val status: TripStatus = TripStatus.DRAFT,
    val stops: List<TripStop>,
    val publicToken: String = UUID.randomUUID().toString().replace("-", ""),
    val notes: String = "",
    val remoteId: String? = null,
    val publicUrl: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
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
    val holdExpiresAtMillis: Long? = null,
    val cancellationToken: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val source: BookingSource = BookingSource.OTHER,
    val capacityClaimType: CapacityClaimType = CapacityClaimType.PASSENGER,
    val sourceReference: String = "",
    val occupancyGroupId: String? = null,
)

data class SegmentLoad(
    val from: TripStop,
    val to: TripStop,
    val occupiedSeats: Int,
    val availableSeats: Int,
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
            SegmentLoad(
                from = orderedStops[index],
                to = orderedStops[index + 1],
                occupiedSeats = occupancy[index],
                availableSeats = (trip.capacity - occupancy[index]).coerceAtLeast(0),
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
            SegmentLoad(
                from = orderedStops[index],
                to = orderedStops[index + 1],
                occupiedSeats = occupancy[index],
                availableSeats = (trip.capacity - occupancy[index]).coerceAtLeast(0),
            )
        }
    }

    fun remainingSeatsForWholeTrip(
        trip: Trip,
        bookings: List<Booking>,
        nowMillis: Long = System.currentTimeMillis(),
    ): Int = segmentLoads(trip, bookings, nowMillis)
        .minOfOrNull(SegmentLoad::availableSeats)
        ?: trip.capacity

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
        return if (remainingSeatsForWholeTrip(trip, bookings, nowMillis) == 0) {
            TripStatus.FULL
        } else {
            TripStatus.PUBLISHED
        }
    }

    private fun reconciledOccupancy(
        trip: Trip,
        bookings: List<Booking>,
        orderedStops: List<TripStop>,
        nowMillis: Long,
    ): IntArray {
        val claimsBySegment = Array(orderedStops.size - 1) { mutableMapOf<String, Int>() }
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
                        val previous = claimsBySegment[segment][claimKey] ?: 0
                        if (booking.seats > previous) {
                            claimsBySegment[segment][claimKey] = booking.seats
                        }
                    }
                }
            }
        return IntArray(claimsBySegment.size) { index -> claimsBySegment[index].values.sum() }
    }

    private fun occupiesCapacity(booking: Booking, nowMillis: Long): Boolean = when (booking.status) {
        BookingStatus.CONFIRMED -> true
        BookingStatus.HELD -> booking.holdExpiresAtMillis == null || booking.holdExpiresAtMillis > nowMillis
        BookingStatus.REQUESTED,
        BookingStatus.CANCELLED,
        BookingStatus.EXPIRED,
        -> false
    }
}
