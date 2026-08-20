package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Stage47Step7Post236StabilityTest {
    @Test
    fun tenCandidatesNeverAdvanceToEleven() {
        var index = 0
        repeat(20) { index = nextBlaBlaCandidateIndex(index, 10) }
        assertEquals(10, index)
    }

    @Test
    fun snapshotAndCompletionAreOneShotPerGeneration() {
        val gate = BlaBlaSyncCompletionGate()
        assertTrue(gate.claimSnapshot(1))
        assertFalse(gate.claimSnapshot(1))
        assertTrue(gate.claimCompletion(1))
        assertFalse(gate.claimCompletion(1))
        assertTrue(gate.claimSnapshot(2))
        assertTrue(gate.claimCompletion(2))
    }

    @Test
    fun genericOfferHrefFallsBackAndDoesNotCollapseDistinctTrips() {
        val first = trip(date = "2026-08-21", time = "10:00")
        val second = trip(date = "2026-08-21", time = "14:00")
        val a = BlaBlaTripIdentity.evidence(first)
        val b = BlaBlaTripIdentity.evidence(second)
        assertTrue(a.fallbackIdentityUsed)
        assertFalse(a.specificHrefPresent)
        assertNotEquals(a.key, b.key)
        assertNotEquals(a.identityHash, b.identityHash)
    }

    @Test
    fun specificPathIsStrongButSearchTokenIsNotPartOfIdentity() {
        val first = trip(href = "https://provider.example/rides/offer/ride-123?search_uuid=aaa")
        val second = first.copy(trip_href = "https://provider.example/rides/offer/ride-123?search_uuid=bbb")
        val a = BlaBlaTripIdentity.evidence(first)
        val b = BlaBlaTripIdentity.evidence(second)
        assertTrue(a.specificHrefPresent)
        assertFalse(a.fallbackIdentityUsed)
        assertEquals(a.key, b.key)
    }

    @Test
    fun externalProfilesRemainDistinctWithoutHardcodedDriverIdentity() {
        val first = BlaBlaTripIdentity.evidence(trip(profile = "profile-a"))
        val second = BlaBlaTripIdentity.evidence(trip(profile = "profile-b"))
        assertNotEquals(first.key, second.key)
    }

    @Test
    fun fourExternalPassengersReachTimelineWithoutInventedPhones() {
        val passengers = (1..4).map { index ->
            BlaBlaCollectorPassenger(name = "Passenger $index", seats = 1, phone = null)
        }
        val response = BlaBlaCollectorMonthResponse(
            status = "validated",
            trips = listOf(
                trip().copy(
                    passengers = passengers,
                    booked_seats = 4,
                    uuid_validation = "verified_from_authenticated_profile_session",
                ),
            ),
        )
        val entries = BlaBlaTimelineAdapter.merge(emptyList(), response)
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(4, entry.minimumOccupiedSeats)
        assertEquals(4, entry.maximumOccupiedSeats)
        assertEquals(4, entry.sourcePassengerSeats[BookingSource.BLABLACAR])
        assertEquals(4, entry.blablaPassengers.size)
        assertTrue(entry.blablaPassengers.all { it.phone == null })
        assertFalse(TripTimelineIssue.OVERBOOKING in entry.issues)
    }

    @Test
    fun missingCoordinatesDoNotBecomeFactualContinuityConflict() {
        val first = entry("a", "Start A", "End A", 1_000L, 2_000L, "profile-a")
        val second = entry("b", "Start B", "End B", 3_000L, 4_000L, "profile-b")
        val result = TripPhysicalRideConsolidator.consolidate(listOf(first, second), emptyMap())
        assertTrue(result.all { TripTimelineIssue.PROFILE_CONTINUITY !in it.issues })
        assertTrue(result.all { TripTimelineIssue.PHYSICAL_CONFLICT !in it.issues })
    }

    @Test
    fun phoneEvidenceNeverGetsAutomaticCountryPrefix() {
        assertEquals("local:11987654321", normalizePhone("11 98765-4321"))
        assertEquals("+14155552671", normalizePhone("+1 415 555 2671"))
        assertEquals("11987654321", whatsappRecipient("11 98765-4321"))
        assertNull(whatsappRecipient("123"))
    }

    private fun trip(
        profile: String = "profile-a",
        date: String = "2026-08-21",
        time: String = "10:00",
        href: String = "https://provider.example/rides/offer",
    ) = BlaBlaCollectorTrip(
        profile_uuid = profile,
        profile_name = "Account",
        date = date,
        departure_time = time,
        arrival_time = "11:00",
        actual_departure = "Origin",
        actual_arrival = "Destination",
        trip_href = href,
        trip_id = null,
    )

    private fun entry(
        id: String,
        origin: String,
        destination: String,
        departure: Long,
        arrival: Long,
        profile: String,
    ) = TripTimelineEntry(
        tripId = id,
        profileId = profile,
        profileLabel = profile,
        departureAtMillis = departure,
        arrivalAtMillis = arrival,
        origin = origin,
        destination = destination,
        status = TripStatus.PUBLISHED,
        capacity = 0,
        minimumOccupiedSeats = 0,
        maximumOccupiedSeats = 0,
        sourcePassengerSeats = emptyMap(),
    )
}
