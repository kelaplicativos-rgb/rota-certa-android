package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlaBlaCollectorModules0264Test {
    private val passenger = BlaBlaCollectorPassenger(
        name = "Passenger A",
        seats = 1,
        boarding = "Origin",
        dropoff = "Destination",
        phone = "5511999999999",
        booking_href = "https://www.blablacar.com.br/rides/offer/passenger/reservation-a",
    )

    private fun trip(
        passengers: List<BlaBlaCollectorPassenger> = listOf(passenger),
        bookedSeats: Int = passengers.sumOf { it.seats },
        rosterComplete: Boolean = true,
    ): BlaBlaCollectorTrip = BlaBlaCollectorTrip(
        profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
        date = "2026-08-23",
        departure_time = "10:30",
        actual_departure = "Origin",
        actual_arrival = "Destination",
        trip_href = "https://www.blablacar.com.br/trip?id=trip-a",
        trip_id = "trip-a",
        passengers = passengers,
        booked_seats = bookedSeats,
        passenger_roster_complete = rosterComplete,
    )

    @Test
    fun partialTraversalCannotEraseLastConfirmedTimelineCard() {
        val previous = trip()

        val result = BlaBlaCollectorTimelineModule.mergeSnapshotTrips(
            previous = listOf(previous),
            current = emptyList(),
            authoritativeComplete = false,
        )

        assertEquals(1, result.trips.size)
        assertEquals(1, result.preservedMissingTrips)
        assertEquals(listOf(passenger), result.trips.single().passengers)
        assertEquals(1, result.trips.single().booked_seats)
    }

    @Test
    fun zeroVisibleCardsWithoutExplicitEmptyEvidenceIsNeverAuthoritative() {
        assertFalse(BlaBlaCollectorCardModule.emptyListIsAuthoritative(explicitEmptyList = false))
        assertTrue(BlaBlaCollectorCardModule.emptyListIsAuthoritative(explicitEmptyList = true))
    }

    @Test
    fun completeVerifiedEmptyTraversalMayRemoveOldCard() {
        val result = BlaBlaCollectorTimelineModule.mergeSnapshotTrips(
            previous = listOf(trip()),
            current = emptyList(),
            authoritativeComplete = true,
        )

        assertTrue(result.trips.isEmpty())
        assertEquals(0, result.preservedMissingTrips)
    }

    @Test
    fun incompletePassengerReadEnrichesWithoutClearingConfirmedRows() {
        val incoming = trip(passengers = emptyList(), bookedSeats = 0, rosterComplete = false)

        val result = BlaBlaCollectorTimelineModule.mergeSnapshotTrips(
            previous = listOf(trip()),
            current = listOf(incoming),
            authoritativeComplete = false,
        )

        val merged = result.trips.single()
        assertEquals(listOf(passenger), merged.passengers)
        assertEquals(1, merged.booked_seats)
        assertFalse(merged.passenger_roster_complete)
        assertEquals(1, result.preservedIncompleteRosters)
    }

    @Test
    fun completeRosterStillPreservesConfirmedPhoneForMatchingPassenger() {
        val currentPassenger = passenger.copy(phone = null)
        val merged = BlaBlaCollectorPassengerModule.mergeMonotonic(
            previous = trip(),
            current = trip(passengers = listOf(currentPassenger), rosterComplete = true),
        )

        assertEquals(passenger.phone, merged.passengers.single().phone)
        assertEquals(passenger.booking_href, merged.passengers.single().booking_href)
        assertTrue(merged.passenger_roster_complete)
    }

    @Test
    fun partialPublishedResponseKeepsLastConfirmedCardAndPassengerActions() {
        val previous = BlaBlaCollectorMonthResponse(
            status = "validated",
            trips = listOf(trip()),
            coverage = BlaBlaCollectorCoverage(complete_for_scope = true),
        )
        val partial = BlaBlaCollectorMonthResponse(
            status = "partial",
            trips = emptyList(),
            coverage = BlaBlaCollectorCoverage(complete_for_scope = false, unresolved_target_cards = 1),
        )

        val published = BlaBlaCollectorTimelineModule.mergePublishedResponse(
            previous = previous,
            incoming = partial,
            preserveOnPartial = true,
        )

        assertEquals(1, published.trips.size)
        assertEquals(passenger.booking_href, published.trips.single().passengers.single().booking_href)
        assertEquals("partial", published.status)
    }

    @Test
    fun explicitEmptyRegistryResponseDoesNotKeepRemovedAccountTrips() {
        val previous = BlaBlaCollectorMonthResponse(
            status = "validated",
            trips = listOf(trip()),
            coverage = BlaBlaCollectorCoverage(complete_for_scope = true),
        )
        val empty = BlaBlaCollectorMonthResponse(
            status = "empty",
            trips = emptyList(),
            coverage = BlaBlaCollectorCoverage(complete_for_scope = false),
        )

        val published = BlaBlaCollectorTimelineModule.mergePublishedResponse(
            previous = previous,
            incoming = empty,
            preserveOnPartial = true,
        )

        assertTrue(published.trips.isEmpty())
    }

    @Test
    fun valueModuleKeepsFareAndRouteIndependentFromPhoneAvailability() {
        val complete = BlaBlaPassengerValueEvidence(
            namePresent = true,
            routePresent = true,
            farePresent = true,
            htmlPresent = true,
        )
        val missingFare = complete.copy(farePresent = false)

        assertTrue(BlaBlaCollectorValueModule.complete(complete))
        assertFalse(BlaBlaCollectorValueModule.complete(missingFare))
        assertEquals(setOf("fare"), BlaBlaCollectorValueModule.missing(missingFare))
    }

    @Test
    fun passengerNavigationPrioritizesVisibleCardBeforeReservationUrl() {
        val step = BlaBlaCollectorPassengerNavigationModule.nextStep(
            passengerPresent = true,
            hasBookingHref = true,
            needsReservationPage = true,
            hasPassengerCard = true,
        )

        assertEquals(BlaBlaDirectPassengerStep.PASSENGER_CARD, step)
    }

    @Test
    fun seatModuleRequiresEditOptionsAndSeatCountFromSameTrip() {
        val complete = BlaBlaCollectorSeatModule.state(
            tripId = "trip-a",
            editHref = "https://www.blablacar.com.br/rides/offer/edit/trip-a",
            optionsHref = "https://www.blablacar.com.br/rides/offer/edit/trip-a/options",
            publishedSeats = 3,
        )
        val mismatched = BlaBlaCollectorSeatModule.state(
            tripId = "trip-a",
            editHref = "https://www.blablacar.com.br/rides/offer/edit/trip-a",
            optionsHref = "https://www.blablacar.com.br/rides/offer/edit/trip-b/options",
            publishedSeats = 3,
        )

        assertTrue(BlaBlaCollectorSeatModule.complete(complete))
        assertFalse(BlaBlaCollectorSeatModule.complete(mismatched))
    }
}
