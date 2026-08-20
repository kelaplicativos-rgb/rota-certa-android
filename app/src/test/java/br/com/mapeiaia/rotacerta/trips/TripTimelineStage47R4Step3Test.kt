package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTimelineStage47R4Step3Test {
    private fun trip(
        id: String,
        departure: Long,
        origin: String,
        destination: String,
        capacity: Int = 4,
        arrival: Long? = null,
    ) = Trip(
        id = id,
        title = "$origin → $destination",
        departureAtMillis = departure,
        capacity = capacity,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "$id-a", order = 0, name = origin),
            TripStop(id = "$id-b", order = 1, name = destination, plannedArrivalMillis = arrival),
        ),
    )

    @Test
    fun sourceSummaryDoesNotCountReservedSeatMirrorAsPassenger() {
        val trip = trip("t", 1_000L, "A", "B")
        val bookings = listOf(
            Booking(
                id = "blabla-real",
                tripId = trip.id,
                passengerName = "BlaBlaCar",
                boardingStopId = "t-a",
                dropoffStopId = "t-b",
                seats = 3,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
            ),
            Booking(
                id = "private-real",
                tripId = trip.id,
                passengerName = "Particular",
                boardingStopId = "t-a",
                dropoffStopId = "t-b",
                seats = 1,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.PRIVATE,
                occupancyGroupId = "private-seat",
            ),
            Booking(
                id = "platform-mirror",
                tripId = trip.id,
                passengerName = "Vaga espelho",
                boardingStopId = "t-a",
                dropoffStopId = "t-b",
                seats = 1,
                status = BookingStatus.CONFIRMED,
                source = BookingSource.BLABLACAR,
                capacityClaimType = CapacityClaimType.RESERVED_SEAT,
                occupancyGroupId = "private-seat",
            ),
        )

        val entry = TripTimelineEngine.fromLocalAgenda(listOf(trip), bookings, nowMillis = 0L).single()

        assertEquals(4, entry.maximumOccupiedSeats)
        assertEquals(0, entry.minimumAvailableSeats)
        assertEquals(3, entry.sourcePassengerSeats[BookingSource.BLABLACAR])
        assertEquals(1, entry.sourcePassengerSeats[BookingSource.PRIVATE])
    }

    @Test
    fun duplicateSameRouteAndDepartureIsMarkedWithoutRequiringSameProfile() {
        val entries = listOf(
            TripTimelineEntry("a", "profile-a", "A", 10L, 20L, "Origem", "Destino", TripStatus.PUBLISHED, 4, 0, 0, emptyMap()),
            TripTimelineEntry("b", "profile-b", "B", 10L, 20L, "origem", "destino", TripStatus.PUBLISHED, 4, 0, 0, emptyMap()),
        )

        val result = TripTimelineEngine.annotate(entries)

        assertTrue(result.all { TripTimelineIssue.DUPLICATE in it.issues })
    }

    @Test
    fun sameProfileMustContinueFromItsPreviousDestination() {
        val entries = listOf(
            TripTimelineEntry("a", "profile-a", "A", 10L, 20L, "X", "Y", TripStatus.PUBLISHED, 4, 0, 0, emptyMap()),
            TripTimelineEntry("b", "profile-a", "A", 30L, 40L, "Z", "X", TripStatus.PUBLISHED, 4, 0, 0, emptyMap()),
        )

        val result = TripTimelineEngine.annotate(entries)

        assertFalse(TripTimelineIssue.PROFILE_CONTINUITY in result.first().issues)
        assertTrue(TripTimelineIssue.PROFILE_CONTINUITY in result.last().issues)
    }

    @Test
    fun overlappingTripsAcrossProfilesArePhysicalConflictWhenArrivalKnown() {
        val entries = listOf(
            TripTimelineEntry("a", "profile-a", "A", 10L, 50L, "X", "Y", TripStatus.PUBLISHED, 4, 0, 0, emptyMap()),
            TripTimelineEntry("b", "profile-b", "B", 30L, 60L, "Y", "X", TripStatus.PUBLISHED, 4, 0, 0, emptyMap()),
        )

        val result = TripTimelineEngine.annotate(entries)

        assertTrue(result.all { TripTimelineIssue.PHYSICAL_CONFLICT in it.issues })
    }

    @Test
    fun noArrivalTimeDoesNotInventConflictUnlessDepartureIsSimultaneous() {
        val entries = listOf(
            TripTimelineEntry("a", "profile-a", "A", 10L, null, "X", "Y", TripStatus.PUBLISHED, 4, 0, 0, emptyMap()),
            TripTimelineEntry("b", "profile-b", "B", 30L, null, "Y", "X", TripStatus.PUBLISHED, 4, 0, 0, emptyMap()),
        )

        val result = TripTimelineEngine.annotate(entries)

        assertTrue(result.none { TripTimelineIssue.PHYSICAL_CONFLICT in it.issues })
    }
}
