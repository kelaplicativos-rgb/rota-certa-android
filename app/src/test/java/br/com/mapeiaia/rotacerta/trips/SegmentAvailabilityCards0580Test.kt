package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SegmentAvailabilityCards0580Test {
    private fun trip(capacity: Int = 4, stopNames: List<String> = listOf("A", "B", "C", "D")) = Trip(
        id = "trip-0580",
        title = stopNames.first() + " → " + stopNames.last(),
        departureAtMillis = 2_000_000_000_000L,
        capacity = capacity,
        status = TripStatus.PUBLISHED,
        stops = stopNames.mapIndexed { index, name ->
            TripStop(id = "s$index", order = index, name = name)
        },
    )

    private fun booking(
        trip: Trip,
        id: String,
        from: Int,
        to: Int,
        seats: Int = 1,
        status: BookingStatus = BookingStatus.CONFIRMED,
    ) = Booking(
        id = id,
        tripId = trip.id,
        passengerName = "P$id",
        boardingStopId = trip.stops[from].id,
        dropoffStopId = trip.stops[to].id,
        seats = seats,
        status = status,
        source = BookingSource.PRIVATE,
        capacityClaimType = CapacityClaimType.PASSENGER,
    )

    @Test
    fun passengersConsumeEveryAndOnlyTraversedSegment() {
        val trip = trip()
        val firstOnly = booking(trip, "first", 0, 1)
        val middleOnly = booking(trip, "middle", 1, 2)
        val crossing = booking(trip, "cross", 1, 3)
        val loads = SeatAvailabilityEngine.segmentLoads(trip, listOf(firstOnly, middleOnly, crossing))

        assertEquals(listOf("A → B", "B → C", "C → D"), loads.map { "${it.from.name} → ${it.to.name}" })
        assertEquals(listOf(1, 2, 1), loads.map(SegmentLoad::occupiedSeats))
        assertEquals(listOf(3, 2, 3), loads.map(SegmentLoad::availableSeats))
    }

    @Test
    fun multiSeatReservationConsumesRealSeatCountAndNeverGoesNegative() {
        val trip = trip()
        val group = booking(trip, "group", 0, 3, seats = 3)
        val extra = booking(trip, "extra", 0, 3, seats = 2)
        val loads = SeatAvailabilityEngine.segmentLoads(trip, listOf(group, extra))

        assertEquals(listOf(5, 5, 5), loads.map(SegmentLoad::occupiedSeats))
        assertEquals(listOf(0, 0, 0), loads.map(SegmentLoad::availableSeats))
        assertEquals(listOf(1, 1, 1), loads.map(SegmentLoad::overbookingSeats))
    }

    @Test
    fun cancelledAndRejectedReservationsReleaseCapacityImmediately() {
        val trip = trip()
        val active = booking(trip, "active", 0, 2, seats = 2)
        val cancelled = booking(trip, "cancelled", 0, 2, seats = 1, status = BookingStatus.CANCELLED)
        val rejected = booking(trip, "rejected", 1, 3, seats = 1, status = BookingStatus.REJECTED)

        assertEquals(
            listOf(2, 2, 4),
            SeatAvailabilityEngine.segmentLoads(trip, listOf(active, cancelled, rejected)).map(SegmentLoad::availableSeats),
        )

        val allCancelled = active.copy(status = BookingStatus.CANCELLED)
        assertEquals(
            listOf(4, 4, 4),
            SeatAvailabilityEngine.segmentLoads(trip, listOf(allCancelled, cancelled, rejected)).map(SegmentLoad::availableSeats),
        )
    }

    @Test
    fun newReservationImmediatelyConsumesOnlyItsSegments() {
        val trip = trip()
        val before = SeatAvailabilityEngine.segmentLoads(trip, emptyList()).map(SegmentLoad::availableSeats)
        val after = SeatAvailabilityEngine.segmentLoads(
            trip,
            listOf(booking(trip, "new", 1, 3, seats = 2)),
        ).map(SegmentLoad::availableSeats)

        assertEquals(listOf(4, 4, 4), before)
        assertEquals(listOf(4, 2, 2), after)
    }

    @Test
    fun arbitraryCapacityDirectTripAndManyStopsStayCanonical() {
        val sixSeatTrip = trip(capacity = 6, stopNames = listOf("A", "B"))
        val directLoads = SeatAvailabilityEngine.segmentLoads(
            sixSeatTrip,
            listOf(booking(sixSeatTrip, "direct", 0, 1, seats = 2)),
        )
        assertEquals(1, directLoads.size)
        assertEquals(6, directLoads.single().occupiedSeats + directLoads.single().availableSeats)
        assertEquals(4, directLoads.single().availableSeats)

        val longTrip = trip(capacity = 5, stopNames = listOf("A", "B", "C", "D", "E", "F"))
        val longLoads = SeatAvailabilityEngine.segmentLoads(
            longTrip,
            listOf(
                booking(longTrip, "left", 0, 2, seats = 2),
                booking(longTrip, "right", 3, 5, seats = 1),
            ),
        )
        assertEquals(listOf("A", "B", "C", "D", "E", "F"), listOf(longLoads.first().from.name) + longLoads.map { it.to.name })
        assertEquals(listOf(3, 3, 5, 4, 4), longLoads.map(SegmentLoad::availableSeats))
    }

    @Test
    fun collapsedAndroidCardRendersSegmentAvailabilityBeforeExpansionOnlyContent() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        val start = source.indexOf("private fun TimelineEntryCard(")
        val end = source.indexOf("if (showSeatDetails)", start)
        assertTrue(start >= 0 && end > start)
        val card = source.substring(start, end)

        val title = card.indexOf("Text(\"Vagas por trecho\"")
        val expandedAfterAvailability = card.indexOf("if (expanded) {", title)
        val passengerSection = card.indexOf("EnhancedPassengerTimelineSection", title)
        assertTrue(title >= 0)
        assertTrue(expandedAfterAvailability > title)
        assertTrue(passengerSection > expandedAfterAvailability)
        assertTrue(card.contains("0 -> \"LOTADO\""))
        assertTrue(card.contains("load0580.occupiedSeats.coerceIn(0, cap)"))
        assertTrue(card.contains("\"●\".repeat(occupiedDots) + \"○\".repeat((cap - occupiedDots).coerceAtLeast(0))"))
        assertTrue(card.contains("load0580.passengerSeats"))
        assertTrue(card.contains("load0580.blockedSeats"))
        assertTrue(card.contains("load0580.overbookingSeats"))
        assertTrue(!card.contains("coerceAtMost(8)"))
    }
}
