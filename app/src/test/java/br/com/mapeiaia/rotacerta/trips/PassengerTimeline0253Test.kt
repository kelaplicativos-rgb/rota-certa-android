package br.com.mapeiaia.rotacerta.trips

import br.com.mapeiaia.rotacerta.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerTimeline0253Test {
    private fun trip() = Trip(
        id = "trip-253",
        title = "A → D",
        departureAtMillis = 1_800_000_000_000L,
        capacity = 4,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "A", latitude = 0.0, longitude = 0.0),
            TripStop(id = "b", order = 1, name = "B", latitude = 0.0, longitude = 1.0),
            TripStop(id = "c", order = 2, name = "C", latitude = 0.0, longitude = 2.0),
            TripStop(id = "d", order = 3, name = "D", latitude = 0.0, longitude = 3.0),
        ),
    )

    @Test
    fun bookingIdentityIsSeparateAndTripSpecificFieldsArePreserved() {
        val trip = trip()
        var counter = 0
        val plan = QuickPassengerEngine.build(
            trip = trip,
            existingBookings = emptyList(),
            request = QuickPassengerRequest(
                passengerName = "Passenger",
                passengerContact = "+12025550123",
                boardingStopId = "b",
                dropoffStopId = "d",
                seats = 2,
                fareMinorUnits = 12_345L,
                fareCurrencyCode = "USD",
                boardingAddress = "Pickup 123",
                dropoffAddress = "Dropoff 456",
            ),
            idFactory = { "generated-${counter++}" },
        )
        val booking = plan.passenger
        assertNotEquals(booking.id, booking.passengerId)
        assertEquals(12_345L, booking.fareMinorUnits)
        assertEquals("USD", booking.fareCurrencyCode)
        assertEquals("Pickup 123", booking.boardingAddress)
        assertEquals("Dropoff 456", booking.dropoffAddress)
        assertEquals(listOf(2, 2), SeatAvailabilityEngine.segmentLoads(trip, plan.writes()).drop(1).map { it.occupiedSeats })
    }

    @Test
    fun routeOrderUsesTrustedTripStopsAndRejectsUnknownLabels() {
        val trip = trip()
        assertEquals(2, TripPassengerRouteOrder.stopIndexForLabel(trip, "C"))
        assertNull(TripPassengerRouteOrder.stopIndexForLabel(trip, "Unknown place"))
        assertEquals(1, TripPassengerRouteOrder.stopIndexForId(trip, "b"))
    }

    @Test
    fun gpsProgressProjectsAlongStopOrderInsteadOfChangingMacroOrder() {
        val progress = TripPassengerRouteOrder.progress(trip(), Coordinate(latitude = 0.01, longitude = 1.6))
        assertNotNull(progress)
        assertTrue(progress!!.stopIndexProgress in 1.4..1.8)
        assertTrue(TripPassengerRouteOrder.isNextBoarding(2, progress))
        assertTrue(!TripPassengerRouteOrder.isNextBoarding(0, progress))
    }

    @Test
    fun directionRequiresReferenceAndClassifiesOnlyReferenceBoundary() {
        val trip = trip()
        val outbound = TripTimelineEntry(
            tripId = trip.id,
            profileId = "local",
            profileLabel = "Agenda",
            departureAtMillis = trip.departureAtMillis,
            arrivalAtMillis = null,
            origin = "A",
            destination = "D",
            status = TripStatus.PUBLISHED,
            capacity = 4,
            minimumOccupiedSeats = 0,
            maximumOccupiedSeats = 0,
            sourcePassengerSeats = emptyMap(),
            localTripId = trip.id,
        )
        assertEquals(
            TimelineDirectionState.OUTBOUND,
            timelineDirectionState(outbound, trip, emptyMap(), Coordinate(0.0, 0.0), radiusKm = 20.0),
        )
        assertEquals(
            TimelineDirectionState.UNKNOWN,
            timelineDirectionState(outbound, trip, emptyMap(), null, radiusKm = 20.0),
        )
        val inboundTrip = trip.copy(stops = trip.stops.reversed().mapIndexed { index, stop -> stop.copy(order = index) })
        val inbound = outbound.copy(
            tripId = "inbound",
            origin = "D",
            destination = "A",
            localTripId = "inbound",
        )
        assertEquals(
            TimelineDirectionState.INBOUND,
            timelineDirectionState(inbound, inboundTrip, emptyMap(), Coordinate(0.0, 0.0), radiusKm = 20.0),
        )
    }
}
