package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalPassengerFlow0256Test {
    private fun externalEntry(
        passengers: List<BlaBlaCollectorPassenger> = emptyList(),
        bookedSeats: Int = passengers.sumOf { it.seats },
    ) = TripTimelineEntry(
        tripId = "blablacar:test",
        profileId = "11111111-1111-4111-8111-111111111111",
        profileLabel = "Conta externa",
        departureAtMillis = 1_800_000_000_000L,
        arrivalAtMillis = 1_800_010_800_000L,
        origin = "Cidade A",
        destination = "Cidade D",
        status = TripStatus.PUBLISHED,
        capacity = 14,
        minimumOccupiedSeats = bookedSeats,
        maximumOccupiedSeats = bookedSeats,
        sourcePassengerSeats = if (bookedSeats > 0) mapOf(BookingSource.BLABLACAR to bookedSeats) else emptyMap(),
        blablaTripId = "external-trip-256",
        blablaTripHref = "https://www.blablacar.com.br/rides/offer/external-trip-256",
        blablaProfileUuid = "11111111-1111-4111-8111-111111111111",
        blablaPassengers = passengers,
        blablaPassengerRosterComplete = true,
    )

    @Test
    fun externalBackingIdIsStableOnlyWithStrongIdentity() {
        val entry = externalEntry()
        val first = timelineExternalBackingTripId(entry)
        val second = timelineExternalBackingTripId(entry.copy(tripId = "different-ui-id"))
        assertNotNull(first)
        assertEquals(first, second)
        assertNull(timelineExternalBackingTripId(entry.copy(blablaProfileUuid = null)))
        assertNull(timelineExternalBackingTripId(entry.copy(blablaTripId = null, blablaTripHref = null)))
    }

    @Test
    fun backingTripIsBookableAndRoutePointsUseOnlyUniquelyOrderedEvidence() {
        val entry = externalEntry(
            passengers = listOf(
                BlaBlaCollectorPassenger(
                    name = "P1",
                    seats = 1,
                    boarding = "Cidade A",
                    dropoff = "Cidade B",
                    booking_href = "https://www.blablacar.com.br/rides/offer/passenger/p1",
                ),
                BlaBlaCollectorPassenger(
                    name = "P2",
                    seats = 1,
                    boarding = "Cidade B",
                    dropoff = "Cidade D",
                    booking_href = "https://www.blablacar.com.br/rides/offer/passenger/p2",
                ),
            ),
        )
        val trip = buildTimelineExternalBackingTrip(entry, 14)
        assertEquals(TripStatus.PUBLISHED, trip.status)
        assertEquals(listOf("Cidade A", "Cidade B", "Cidade D"), trip.stops.sortedBy(TripStop::order).map(TripStop::name))
    }

    @Test
    fun ambiguousIntermediateOrderFallsBackToEndpointsInsteadOfGuessing() {
        val entry = externalEntry(
            passengers = listOf(
                BlaBlaCollectorPassenger(name = "P1", boarding = "Cidade B", dropoff = "Cidade D"),
                BlaBlaCollectorPassenger(name = "P2", boarding = "Cidade C", dropoff = "Cidade D"),
            ),
        )
        assertEquals(listOf("Cidade A", "Cidade D"), timelineExternalRoutePointLabels(entry))
    }

    @Test
    fun sixExternalSeatsInFourteenLeaveEightAndRemainReservedSeatClaims() {
        val passengers = listOf(
            BlaBlaCollectorPassenger(
                name = "P1",
                seats = 3,
                boarding = "Cidade A",
                dropoff = "Cidade D",
                booking_href = "https://www.blablacar.com.br/rides/offer/passenger/p1",
            ),
            BlaBlaCollectorPassenger(
                name = "P2",
                seats = 3,
                boarding = "Cidade A",
                dropoff = "Cidade D",
                booking_href = "https://www.blablacar.com.br/rides/offer/passenger/p2",
            ),
        )
        val entry = externalEntry(passengers, bookedSeats = 6)
        val trip = buildTimelineExternalBackingTrip(entry, 14)
        val claims = planTimelineExternalCapacityClaims(entry, trip, emptyList())
        assertEquals(6, claims.sumOf(Booking::seats))
        assertTrue(claims.all { it.capacityClaimType == CapacityClaimType.RESERVED_SEAT })
        assertTrue(claims.all { it.source == BookingSource.BLABLACAR })
        val stops = trip.stops.sortedBy(TripStop::order)
        val availability = SeatAvailabilityEngine.availability(
            trip = trip,
            bookings = claims,
            boardingStopId = stops.first().id,
            dropoffStopId = stops.last().id,
            requestedSeats = 1,
        )
        assertTrue(availability.canBook)
        assertEquals(8, availability.availableSeats)
    }

    @Test
    fun unknownExternalSegmentFallsBackToWholeTripCapacityClaim() {
        val passenger = BlaBlaCollectorPassenger(
            name = "P",
            seats = 2,
            boarding = "Ponto desconhecido",
            dropoff = "Outro ponto desconhecido",
            booking_href = "https://www.blablacar.com.br/rides/offer/passenger/unknown",
        )
        val entry = externalEntry(listOf(passenger), bookedSeats = 2)
        val trip = buildTimelineExternalBackingTrip(entry.copy(blablaPassengers = emptyList()), 14)
        val claims = planTimelineExternalCapacityClaims(entry, trip, emptyList())
        assertEquals(1, claims.size)
        val claim = claims.single()
        val stops = trip.stops.sortedBy(TripStop::order)
        assertEquals(stops.first().id, claim.boardingStopId)
        assertEquals(stops.last().id, claim.dropoffStopId)
    }

    @Test
    fun externalCapacityClaimsDoNotCreatePassengerIdentityRows() {
        val entry = externalEntry(
            passengers = listOf(
                BlaBlaCollectorPassenger(
                    name = "P",
                    seats = 1,
                    boarding = "Cidade A",
                    dropoff = "Cidade D",
                    booking_href = "https://www.blablacar.com.br/rides/offer/passenger/p",
                ),
            ),
            bookedSeats = 1,
        )
        val trip = buildTimelineExternalBackingTrip(entry, 14)
        val claim = planTimelineExternalCapacityClaims(entry, trip, emptyList()).single()
        assertEquals(CapacityClaimType.RESERVED_SEAT, claim.capacityClaimType)
        assertFalse(claim.passengerName.isBlank())
    }
}
