package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaPublishedCapacity0366Test {
    private val profileA = "7371f028-9c55-4903-8444-308015823efd"
    private val profileB = "175a7068-50d8-40c3-a27a-214b9c6e0461"

    @Test
    fun physicalFourRemoteThreeUsesThreeAsPublicCapacity() {
        val resolved = timelinePublicCapacityResolution(entry(profileA, "trip-a", published = 3, blablaOccupied = 0))
        assertEquals(4, resolved.physicalVehicleCapacity)
        assertEquals(3, resolved.remotePublishedCapacity)
        assertEquals(3, resolved.effectiveCapacity)
        assertEquals("blablacar_remote_published", resolved.capacitySource)
    }

    @Test
    fun threePublishedThreeOccupiedMeansZeroFree() {
        val resolved = timelinePublicCapacityResolution(entry(profileA, "trip-a", published = 3, blablaOccupied = 3))
        assertEquals(3, resolved.effectiveCapacity)
        assertEquals(0, resolved.availableSeats)
    }

    @Test
    fun threePublishedTwoOccupiedMeansOneFree() {
        assertEquals(1, timelinePublicCapacityResolution(entry(profileA, "trip-a", 3, 2)).availableSeats)
    }

    @Test
    fun denominatorVariesPerTrip() {
        assertEquals(0, timelinePublicCapacityResolution(entry(profileA, "trip-two", 2, 2)).availableSeats)
        assertEquals(1, timelinePublicCapacityResolution(entry(profileA, "trip-four", 4, 3)).availableSeats)
    }

    @Test
    fun remoteChangeFourToThreeRecalculatesImmediately() {
        val before = entry(profileA, "trip-a", 4, 3)
        val after = before.copy(blablaPublishedSeats = 3)
        assertEquals(4, timelinePublicCapacityResolution(before).effectiveCapacity)
        assertEquals(1, timelinePublicCapacityResolution(before).availableSeats)
        assertEquals(3, timelinePublicCapacityResolution(after).effectiveCapacity)
        assertEquals(0, timelinePublicCapacityResolution(after).availableSeats)
    }

    @Test
    fun lastConfirmedRemoteCapacitySurvivesPartialReload() {
        val previous = collectorTrip(profileA, "trip-a", 3, 3, true)
        val partial = collectorTrip(profileA, "trip-a", null, 2, false)
        val merged = BlaBlaCollectorPassengerModule.mergeMonotonic(previous, partial)
        assertEquals(3, merged.published_seats)
        assertEquals(3, merged.booked_seats)
    }

    @Test
    fun timelineAndAgendaResolveSameZeroAvailability() {
        val timelineFree = timelinePublicCapacityResolution(entry(profileA, "trip-a", 3, 3)).availableSeats
        val trip = trip(4)
        val claims = listOf(
            booking("p1", trip, 1, BookingSource.BLABLACAR, CapacityClaimType.PASSENGER),
            booking("p2", trip, 1, BookingSource.BLABLACAR, CapacityClaimType.PASSENGER),
            booking("p3", trip, 1, BookingSource.BLABLACAR, CapacityClaimType.PASSENGER),
            booking("family-gap", trip, 1, BookingSource.BLABLACAR, CapacityClaimType.RESERVED_SEAT),
        )
        val agendaFree = SeatAvailabilityEngine.remainingSeatsForWholeTrip(trip, claims)
        assertEquals(0, timelineFree)
        assertEquals(timelineFree, agendaFree)
    }

    @Test
    fun noSegmentCanAdvertiseMoreThanRemotePublicCapacity() {
        val e = entry(profileA, "trip-a", 3, 0)
        val stops = trip().stops
        val physicalLoads = listOf(
            SegmentLoad(stops[0], stops[1], 0, 4),
            SegmentLoad(stops[1], stops[2], 2, 2),
        )
        val publicLoads = timelinePublicSegmentLoads(e, physicalLoads)
        assertEquals(listOf(3, 1), publicLoads.map(SegmentLoad::availableSeats))
        assertTrue(publicLoads.all { it.availableSeats <= 3 })
    }

    @Test
    fun zeroPublicSeatsBlocksNewReservation() {
        val trip = trip(4)
        val claims = listOf(
            booking("three-passengers", trip, 3, BookingSource.BLABLACAR, CapacityClaimType.PASSENGER),
            booking("family-gap", trip, 1, BookingSource.BLABLACAR, CapacityClaimType.RESERVED_SEAT),
        )
        val result = SeatAvailabilityEngine.availability(trip, claims, "a", "c", 1)
        assertEquals(0, result.availableSeats)
        assertFalse(result.canBook)
    }

    @Test
    fun differentTripsKeepIndependentRemoteCapacities() {
        assertEquals(2, timelinePublicCapacityResolution(entry(profileA, "trip-a", 2, 1)).effectiveCapacity)
        assertEquals(4, timelinePublicCapacityResolution(entry(profileA, "trip-b", 4, 1)).effectiveCapacity)
    }

    @Test
    fun ezequielAndBarbosaDoNotShareCapacityEvenWithSameTripId() {
        val ezequiel = timelinePublicCapacityResolution(entry(profileA, "same-trip", 3, 3))
        val barbosa = timelinePublicCapacityResolution(entry(profileB, "same-trip", 4, 3))
        assertEquals(0, ezequiel.availableSeats)
        assertEquals(1, barbosa.availableSeats)
    }

    @Test
    fun rotaCertaSeatAlreadyRemovedFromBlaBlaIsNotDoubleDecremented() {
        val resolved = timelinePublicCapacityResolution(entry(profileA, "trip-a", 3, 2, rotaOccupied = 1))
        assertEquals(4, resolved.effectiveCapacity)
        assertEquals(1, resolved.availableSeats)
    }

    @Test
    fun publicAgendaDoesNotInventFourSeatsWhenEvidenceIsMissing() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PublicAgendaAutoSync0300.kt").readText()
        assertFalse(source.contains("configuredVehicleCapacity.takeIf { it in 1..999 } ?: 4"))
        assertTrue(source.contains("CAPACITY_PUBLIC_SYNC_SKIPPED"))
        assertTrue(source.contains("blablaAvailableSeats="))
        assertTrue(source.contains("rotaCertaAvailableSeats="))
        assertTrue(source.contains("combinedAgendaCapacity="))
        assertTrue(source.contains("capacitySource=additive_pools"))
    }

    private fun entry(
        profile: String,
        tripId: String,
        published: Int?,
        blablaOccupied: Int,
        rotaOccupied: Int = 0,
    ): TripTimelineEntry {
        val sources = buildMap {
            if (blablaOccupied > 0) put(BookingSource.BLABLACAR, blablaOccupied)
            if (rotaOccupied > 0) put(BookingSource.ROTA_CERTA, rotaOccupied)
        }
        return TripTimelineEntry(
            tripId = "timeline:$profile:$tripId",
            profileId = profile,
            profileLabel = "Perfil",
            departureAtMillis = 1_800_000_000_000L,
            arrivalAtMillis = null,
            origin = "A",
            destination = "C",
            status = TripStatus.PUBLISHED,
            capacity = 4,
            minimumOccupiedSeats = blablaOccupied + rotaOccupied,
            maximumOccupiedSeats = blablaOccupied + rotaOccupied,
            sourcePassengerSeats = sources,
            blablaTripId = tripId,
            blablaProfileUuid = profile,
            blablaPublishedSeats = published,
            blablaPassengerRosterComplete = true,
        )
    }

    private fun collectorTrip(
        profile: String,
        tripId: String,
        published: Int?,
        occupied: Int,
        complete: Boolean,
    ) = BlaBlaCollectorTrip(
        profile_uuid = profile,
        date = "2026-09-04",
        departure_time = "10:30",
        actual_departure = "A",
        actual_arrival = "C",
        trip_id = tripId,
        trip_href = "https://www.blablacar.com.br/rides/offer/$tripId",
        passengers = (1..occupied).map { BlaBlaCollectorPassenger(name = "P$it") },
        booked_seats = occupied,
        published_seats = published,
        passenger_roster_complete = complete,
    )

    private fun trip(capacity: Int = 4) = Trip(
        id = "trip",
        title = "A → C",
        departureAtMillis = 1_800_000_000_000L,
        capacity = capacity,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
            TripStop(id = "c", order = 2, name = "C"),
        ),
    )

    private fun booking(
        id: String,
        trip: Trip,
        seats: Int,
        source: BookingSource,
        claimType: CapacityClaimType,
    ) = Booking(
        id = id,
        tripId = trip.id,
        passengerName = id,
        boardingStopId = "a",
        dropoffStopId = "c",
        seats = seats,
        status = BookingStatus.CONFIRMED,
        source = source,
        capacityClaimType = claimType,
    )
}
