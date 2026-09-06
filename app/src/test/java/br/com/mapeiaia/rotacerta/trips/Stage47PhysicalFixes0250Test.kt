package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Stage47PhysicalFixes0250Test {
@Test
fun canonicalPassengerUrlWinsOverSemanticPresentationDifference() {
val url = "https://www.blablacar.com.br/rides/offer/passenger/booking-a"
assertTrue(BlaBlaHarvestAssociation.passengerCanonicalIdentityProven("booking-a", url, url))
assertTrue(BlaBlaHarvestAssociation.passengerEvidenceAccepted(canonicalIdentityProven = true))
}

@Test
fun unprovenPassengerUrlFailsClosedEvenIfSemanticTextCouldLookCompatible() {
val expected = "https://www.blablacar.com.br/rides/offer/passenger/booking-a"
val wrong = "https://www.blablacar.com.br/rides/offer/passenger/booking-b"
assertFalse(BlaBlaHarvestAssociation.passengerCanonicalIdentityProven("booking-a", expected, wrong))
assertFalse(BlaBlaHarvestAssociation.passengerEvidenceAccepted(canonicalIdentityProven = false))
}

@Test
fun candidateTripIdRemainsManifestAuthorityWhenDetailUrlIsGeneric() {
val account = BlaBlaAccountDefinition("slot", "Driver", "profile-a", "profile")
val candidate = BlaBlaDomRideCandidate(
  href = "https://www.blablacar.com.br/rides/offer?id=trip-a",
  departureTime = "11:00",
  origin = "Origin",
  destination = "Destination",
  dateText = "2026-08-28",
)
val detail = BlaBlaDomTripDetail(url = "https://www.blablacar.com.br/rides/offer")
val trip = BlaBlaDomNormalizer.toTrip(account, candidate, detail, LocalDate.of(2026, 8, 21), true)
assertEquals("trip-a", trip?.trip_id)
assertEquals(candidate.href, trip?.trip_href)
}

@Test
fun mismatchedCandidateAndDetailTripIdsAreRejected() {
val account = BlaBlaAccountDefinition("slot", "Driver", "profile-a", "profile")
val candidate = BlaBlaDomRideCandidate(
  href = "https://www.blablacar.com.br/rides/offer?id=trip-a",
  departureTime = "11:00",
  origin = "Origin",
  destination = "Destination",
  dateText = "2026-08-28",
)
val detail = BlaBlaDomTripDetail(url = "https://www.blablacar.com.br/rides/offer?id=trip-b")
assertNull(BlaBlaDomNormalizer.toTrip(account, candidate, detail, LocalDate.of(2026, 8, 21), true))
}

@Test
fun sameStrongTripIdWithDifferentPhysicalCoresIsPreservedAndFlagged() {
val a = externalTrip("2026-08-28", "11:00", "Origin A", "Destination A", "shared-id")
val b = externalTrip("2026-06-27", "10:00", "Origin B", "Destination B", "shared-id")
val resolved = BlaBlaTripIdentity.resolveDistinct(listOf(a, b))
assertEquals(2, resolved.trips.size)
assertEquals(0, resolved.dedupedCount)
assertEquals(1, resolved.conflicts.size)
assertTrue(resolved.trips.all { it.identity_conflict })
assertNotEquals(BlaBlaTripIdentity.evidence(resolved.trips[0]).key, BlaBlaTripIdentity.evidence(resolved.trips[1]).key)
}

@Test
fun trueDuplicateStrongTripIdSameCoreStillDedupes() {
val a = externalTrip("2026-08-28", "11:00", "Origin", "Destination", "same-id")
val b = a.copy(passengers = listOf(BlaBlaCollectorPassenger(name = "Passenger")))
val resolved = BlaBlaTripIdentity.resolveDistinct(listOf(a, b))
assertEquals(1, resolved.trips.size)
assertEquals(1, resolved.dedupedCount)
assertTrue(resolved.conflicts.isEmpty())
}

@Test
fun completeExternalZeroReservationIsNotPending() {
val complete = timelineEntry(occupied = 0, rosterComplete = true)
val pending = timelineEntry(occupied = 0, rosterComplete = null)
val reserved = timelineEntry(occupied = 1, rosterComplete = true)
assertEquals(TimelineOccupancyReadState.COMPLETE_EMPTY, timelineOccupancyReadState(complete))
assertEquals(TimelineOccupancyReadState.PENDING, timelineOccupancyReadState(pending))
assertEquals(TimelineOccupancyReadState.RESERVED, timelineOccupancyReadState(reserved))
}

private fun externalTrip(date: String, time: String, from: String, to: String, tripId: String) = BlaBlaCollectorTrip(
profile_uuid = "profile-a",
date = date,
departure_time = time,
actual_departure = from,
actual_arrival = to,
trip_id = tripId,
uuid_validation = "verified_from_authenticated_profile_session",
)

private fun timelineEntry(occupied: Int, rosterComplete: Boolean?) = TripTimelineEntry(
tripId = "entry-$occupied-$rosterComplete",
profileId = "profile-a",
profileLabel = "Driver",
departureAtMillis = 1L,
arrivalAtMillis = null,
origin = "Origin",
destination = "Destination",
status = TripStatus.PUBLISHED,
capacity = 0,
minimumOccupiedSeats = occupied,
maximumOccupiedSeats = occupied,
sourcePassengerSeats = if (occupied > 0) mapOf(BookingSource.BLABLACAR to occupied) else emptyMap(),
blablaPassengerRosterComplete = rosterComplete,
)
}
