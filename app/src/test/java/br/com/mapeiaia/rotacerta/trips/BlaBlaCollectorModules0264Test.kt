package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
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
    fun duplicateWeakAndStrongDomEvidenceBecomesOnePassenger() {
        val weak = passenger.copy(phone = null, booking_href = null)
        val strong = passenger.copy(name = "Passenger A", seats = 1)

        val merged = BlaBlaCollectorPassengerModule.coalesceDuplicateEvidence(listOf(weak, strong))

        assertEquals(1, merged.size)
        assertEquals(passenger.phone, merged.single().phone)
        assertEquals(passenger.booking_href, merged.single().booking_href)
        assertEquals(1, merged.single().seats)
    }

    @Test
    fun sameVisibleNameWithDifferentStrongPassengerPagesStaysDistinct() {
        val other = passenger.copy(
            phone = null,
            booking_href = "https://www.blablacar.com.br/rides/offer/passenger/reservation-b",
        )

        val merged = BlaBlaCollectorPassengerModule.coalesceDuplicateEvidence(
            listOf(passenger.copy(phone = null), other),
        )

        assertEquals(2, merged.size)
    }

    @Test
    fun normalizedTripDoesNotCountDuplicateDomNodesAsOccupiedSeats() {
        val weak = passenger.copy(phone = null, booking_href = null)
        val account = BlaBlaAccountDefinition(
            slot = "slot",
            label = "Driver",
            uuid = "7371f028-9c55-4903-8444-308015823efd",
            dataDirectorySuffix = "profile",
        )
        val candidate = BlaBlaDomRideCandidate(
            href = "https://www.blablacar.com.br/rides/offer?id=trip-a",
            departureTime = "10:30",
            origin = "Origin",
            destination = "Destination",
            dateText = "2026-08-23",
            passengers = listOf(weak),
        )
        val detail = BlaBlaDomTripDetail(
            url = candidate.href,
            passengers = listOf(passenger),
            passengerRosterComplete = true,
        )

        val normalized = BlaBlaDomNormalizer.toTrip(
            account = account,
            candidate = candidate,
            detail = detail,
            today = LocalDate.of(2026, 8, 23),
            authenticatedProfileSessionVerified = true,
        )!!

        assertEquals(1, normalized.passengers.size)
        assertEquals(1, normalized.booked_seats)
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
    fun videoRegressionPartialMhtmlPassCannotRemoveDirectPassengerActions() {
        val directSnapshot = trip()
        val incompleteMhtmlRead = trip(
            passengers = emptyList(),
            bookedSeats = 0,
            rosterComplete = false,
        )

        val afterMhtml = BlaBlaCollectorTimelineModule.mergeSnapshotTrips(
            previous = listOf(directSnapshot),
            current = listOf(incompleteMhtmlRead),
            authoritativeComplete = false,
        ).trips.single()

        assertEquals(passenger.name, afterMhtml.passengers.single().name)
        assertEquals(passenger.phone, afterMhtml.passengers.single().phone)
        assertEquals(passenger.booking_href, afterMhtml.passengers.single().booking_href)
        assertEquals(1, afterMhtml.booked_seats)
    }

    @Test
    fun partialFieldEnrichmentPreservesUnobservedConfirmedMetadata() {
        val previous = trip().copy(
            profile_name = "Driver",
            arrival_time = "13:00",
            search_from = "Origin",
            search_to = "Destination",
            actual_departure = "Origin terminal",
            actual_arrival = "Destination terminal",
            price = "R$ 70,00",
            availability = "available",
            uuid_validation = "verified_from_authenticated_profile_session",
            itinerary_stops = listOf("Origin terminal", "Middle", "Destination terminal"),
            itinerary_authoritative = true,
            published_seats = 3,
        )
        val partial = previous.copy(
            profile_name = "",
            arrival_time = null,
            search_from = null,
            search_to = null,
            actual_departure = null,
            actual_arrival = null,
            price = "R$ 75,00",
            availability = "unknown",
            trip_href = null,
            uuid_validation = "unknown",
            passengers = emptyList(),
            itinerary_stops = emptyList(),
            itinerary_authoritative = false,
            booked_seats = 0,
            published_seats = null,
            passenger_roster_complete = false,
        )

        val merged = BlaBlaCollectorTimelineModule.mergeSnapshotTrips(
            previous = listOf(previous),
            current = listOf(partial),
            authoritativeComplete = false,
        ).trips.single()

        assertEquals("R$ 75,00", merged.price)
        assertEquals(previous.actual_departure, merged.actual_departure)
        assertEquals(previous.actual_arrival, merged.actual_arrival)
        assertEquals(previous.search_from, merged.search_from)
        assertEquals(previous.search_to, merged.search_to)
        assertEquals(previous.arrival_time, merged.arrival_time)
        assertEquals(previous.trip_href, merged.trip_href)
        assertEquals(previous.availability, merged.availability)
        assertEquals(previous.uuid_validation, merged.uuid_validation)
        assertEquals(previous.itinerary_stops, merged.itinerary_stops)
        assertTrue(merged.itinerary_authoritative)
        assertEquals(previous.published_seats, merged.published_seats)
        assertEquals(previous.passengers, merged.passengers)
        assertEquals(previous.booked_seats, merged.booked_seats)
        assertTrue(merged.passenger_roster_complete)
    }

    @Test
    fun partialNewOccupancyAdvancesWithoutPretendingRosterIsComplete() {
        val previous = trip()
        val second = passenger.copy(
            name = "Passenger B",
            phone = "5511888888888",
            booking_href = "https://www.blablacar.com.br/rides/offer/passenger/reservation-b",
        )
        val partial = previous.copy(
            passengers = listOf(passenger, second),
            booked_seats = 2,
            passenger_roster_complete = false,
        )

        val merged = BlaBlaCollectorTimelineModule.mergeSnapshotTrips(
            previous = listOf(previous),
            current = listOf(partial),
            authoritativeComplete = false,
        ).trips.single()

        assertEquals(2, merged.passengers.size)
        assertEquals(2, merged.booked_seats)
        assertFalse(merged.passenger_roster_complete)
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
    fun passengerNavigationPrioritizesCanonicalReservationUrlOverVisibleCard() {
        val step = BlaBlaCollectorPassengerNavigationModule.nextStep(
            passengerPresent = true,
            hasBookingHref = true,
            needsReservationPage = true,
            hasPassengerCard = true,
        )

        assertEquals(BlaBlaDirectPassengerStep.RESERVATION_URL, step)
    }

    @Test
    fun automaticPublishedSeatLookupRunsInsideNormalReadOnlySync() {
        assertTrue(BlaBlaHarvestPolicy.AUTOMATIC_PUBLISHED_SEAT_LOOKUP)
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
