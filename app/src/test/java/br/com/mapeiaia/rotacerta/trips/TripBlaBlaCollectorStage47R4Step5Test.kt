package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripBlaBlaCollectorStage47R4Step5Test {
    private val utc = ZoneId.of("UTC")

    private fun millis(value: String): Long = LocalDateTime.parse(value).atZone(utc).toInstant().toEpochMilli()

    private fun trip(
        id: String,
        departure: String,
        origin: String,
        destination: String,
        capacity: Int = 4,
    ) = Trip(
        id = id,
        title = "$origin → $destination",
        departureAtMillis = millis(departure),
        capacity = capacity,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "$id-a", order = 0, name = origin),
            TripStop(id = "$id-b", order = 1, name = destination),
        ),
    )

    @Test
    fun scopeComesFromAgendaAndIsNotHardcodedToOldCities() {
        val trips = listOf(
            trip("one", "2026-09-02T08:00:00", "Cidade Nova/AA", "Destino Novo/BB"),
        )

        val routes = BlaBlaCollectorScope.fromAgenda(trips, "2026-09", maxRoutes = 3, zoneId = utc)

        assertTrue(routes.any { it.from == "Cidade Nova/AA" && it.to == "Destino Novo/BB" })
        assertTrue(routes.any { it.from == "Destino Novo/BB" && it.to == "Cidade Nova/AA" })
        assertFalse(routes.any { it.from.contains("Santo André") || it.to.contains("Três Corações") })
    }

    @Test
    fun scopePrefersTripsInsideRequestedMonth() {
        val trips = listOf(
            trip("old", "2026-08-10T08:00:00", "Antiga", "Antiga 2"),
            trip("current", "2026-09-10T08:00:00", "Atual", "Atual 2"),
        )

        val routes = BlaBlaCollectorScope.fromAgenda(trips, "2026-09", maxRoutes = 2, zoneId = utc)

        assertTrue(routes.all { it.from.startsWith("Atual") || it.to.startsWith("Atual") })
    }

    @Test
    fun verifiedPublicTripMergesWithLocalAgendaAndKeepsPhysicalOccupancy() {
        val localTrip = trip("local-1", "2026-09-12T10:00:00", "Origem X", "Destino Y")
        val bookings = listOf(
            Booking(
                id = "private",
                tripId = localTrip.id,
                passengerName = "Particular",
                boardingStopId = "local-1-a",
                dropoffStopId = "local-1-b",
                seats = 1,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.PRIVATE,
            ),
        )
        val local = TripTimelineEngine.fromLocalAgenda(listOf(localTrip), bookings, nowMillis = 0L).single()
        val response = BlaBlaCollectorMonthResponse(
            status = "validated",
            month = "2026-09",
            trips = listOf(
                BlaBlaCollectorTrip(
                    profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
                    profile_name = "Perfil público",
                    date = "2026-09-12",
                    departure_time = "10:05",
                    arrival_time = "14:00",
                    actual_departure = "origem x",
                    actual_arrival = "destino y",
                    availability = "available_or_unspecified",
                    trip_id = "public-trip",
                    uuid_validation = "verified_from_trip_detail_profile_link",
                ),
            ),
        )

        val merged = BlaBlaTimelineAdapter.merge(listOf(local), response, utc).single()

        assertEquals("local-1", merged.tripId)
        assertEquals("7371f028-9c55-4903-8444-308015823efd", merged.profileId)
        assertEquals(4, merged.capacity)
        assertEquals(1, merged.maximumOccupiedSeats)
        assertEquals(1, merged.sourcePassengerSeats[BookingSource.PRIVATE])
        assertFalse(TripTimelineIssue.VALIDATION_PENDING in merged.issues)
    }

    @Test
    fun unresolvedUuidIsNeverShownAsFullyValidated() {
        val response = BlaBlaCollectorMonthResponse(
            status = "partial",
            month = "2026-09",
            trips = listOf(
                BlaBlaCollectorTrip(
                    profile_uuid = "175a7068-50d8-40c3-a27a-214b9c6e0461",
                    profile_name = "Perfil B",
                    date = "2026-09-15",
                    departure_time = "09:00",
                    actual_departure = "A",
                    actual_arrival = "B",
                    uuid_validation = "unresolved_no_trip_link",
                ),
            ),
        )

        val entry = BlaBlaTimelineAdapter.merge(emptyList(), response, utc).single()

        assertTrue(TripTimelineIssue.VALIDATION_PENDING in entry.issues)
    }

    @Test
    fun unmatchedPublicTripKeepsUnknownCapacityInsteadOfInventingSeats() {
        val response = BlaBlaCollectorMonthResponse(
            status = "validated",
            month = "2026-09",
            trips = listOf(
                BlaBlaCollectorTrip(
                    profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
                    profile_name = "Perfil",
                    date = "2026-09-20",
                    departure_time = "11:00",
                    actual_departure = "Cidade A",
                    actual_arrival = "Cidade B",
                    uuid_validation = "verified_from_trip_detail_profile_link",
                ),
            ),
        )

        val entry = BlaBlaTimelineAdapter.merge(emptyList(), response, utc).single()

        assertEquals(0, entry.capacity)
        assertEquals(0, entry.maximumOccupiedSeats)
        assertFalse(TripTimelineIssue.OVERBOOKING in entry.issues)
    }

    @Test
    fun explicitYearWinsOverEarlierYearlessDateEvidence() {
        val today = LocalDate.of(2026, 8, 20)
        assertEquals(
            LocalDate.of(2027, 6, 27),
            BlaBlaDomNormalizer.parseDate("27 de junho | Domingo, 27 de junho de 2027", today),
        )
        assertEquals(LocalDate.of(2027, 6, 29), BlaBlaDomNormalizer.parseDate("29 de junho de 2027", today))
        assertEquals(LocalDate.of(2027, 6, 30), BlaBlaDomNormalizer.parseDate("30 de junho de 2027", today))
    }

    @Test
    fun explicitPastYearIsNeverShiftedByInference() {
        assertEquals(
            LocalDate.of(2026, 1, 10),
            BlaBlaDomNormalizer.parseDate("10 de janeiro de 2026", LocalDate.of(2026, 8, 20)),
        )
    }

    @Test
    fun incompletePassengerRosterCannotReleasePreviouslyObservedSeats() {
        val previous = BlaBlaCollectorTrip(
            profile_uuid = "profile",
            date = "2026-08-21",
            departure_time = "10:00",
            actual_departure = "A",
            actual_arrival = "B",
            trip_id = "trip-1",
            passengers = listOf(BlaBlaCollectorPassenger(name = "Passageiro A", seats = 2, booking_href = "https://example.invalid/booking/a")),
            booked_seats = 2,
            passenger_roster_complete = true,
        )
        val incomplete = previous.copy(
            passengers = emptyList(),
            booked_seats = 0,
            passenger_roster_complete = false,
        )

        val reconciled = BlaBlaPassengerRosterReconciler.reconcile(previous, incomplete)

        assertEquals(1, reconciled.passengers.size)
        assertEquals(2, reconciled.booked_seats)
        assertFalse(reconciled.passenger_roster_complete)
    }

    @Test
    fun completeEmptyPassengerRosterCanConfirmCancellation() {
        val previous = BlaBlaCollectorTrip(
            profile_uuid = "profile",
            date = "2026-08-21",
            departure_time = "10:00",
            trip_id = "trip-1",
            passengers = listOf(BlaBlaCollectorPassenger(name = "Passageiro A", seats = 1)),
            booked_seats = 1,
            passenger_roster_complete = true,
        )
        val completeEmpty = previous.copy(
            passengers = emptyList(),
            booked_seats = 0,
            passenger_roster_complete = true,
        )

        val reconciled = BlaBlaPassengerRosterReconciler.reconcile(previous, completeEmpty)

        assertTrue(reconciled.passengers.isEmpty())
        assertEquals(0, reconciled.booked_seats)
        assertTrue(reconciled.passenger_roster_complete)
    }
}
