package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AgendaCanonicalIntegrity0406Test {
    private val tenant = "tenant-test"
    private val profile = "11111111-1111-4111-8111-111111111111"
    private val tripId = "provider-trip-1"

    @Test
    fun strongTripKeyIsStableTenantScopedAndProviderScoped() {
        val a = canonicalTripKey0406(tenant, "BLABLACAR", profile, tripId)
        val b = canonicalTripKey0406(tenant, "blablacar", profile.uppercase(), tripId)
        assertEquals(a, b)
        assertNotEquals(a, canonicalTripKey0406("other-tenant", "BLABLACAR", profile, tripId))
        assertNotEquals(a, canonicalTripKey0406(tenant, "OTHER", profile, tripId))
        assertNotEquals(a, canonicalTripKey0406(tenant, "BLABLACAR", profile, "other-trip"))
    }

    @Test
    fun canonicalHashIsDeterministicAndSensitiveToState() {
        val trip = trip()
        val bookingA = booking("a", "a", "b")
        val bookingB = booking("b", "b", "c")
        val first = canonicalTripStateHash0406(trip, listOf(bookingA, bookingB))
        assertEquals(first, canonicalTripStateHash0406(trip, listOf(bookingB, bookingA)))
        assertNotEquals(first, canonicalTripStateHash0406(trip.copy(capacity = 4), listOf(bookingA, bookingB)))
        assertNotEquals(first, canonicalTripStateHash0406(trip, listOf(bookingA.copy(status = BookingStatus.CANCELLED), bookingB)))
        assertNotEquals(first, canonicalTripStateHash0406(trip.copy(deleted = true), listOf(bookingA, bookingB)))
    }

    @Test
    fun staleSyncWatchdogOnlyExpiresActiveRunsPastLease() {
        val lease = 45L * 60L * 1000L
        val now = 10L * lease
        assertTrue(syncRunIsStalled0406("RUNNING", "COLLECTING", now - lease - 1L, now - lease - 1L, now, lease))
        assertFalse(syncRunIsStalled0406("RUNNING", "COLLECTING", now - 1_000L, now - lease, now, lease))
        assertFalse(syncRunIsStalled0406("VERIFIED", "COMPLETE", now - lease - 1L, now - lease - 1L, now, lease))
    }

    @Test
    fun tombstonesRequireCompleteGlobalProfileMonthCoverage() {
        val complete = response("success", complete = true, global = true)
        assertTrue(externalCollectorAllowsTombstones0406(complete))
        assertFalse(externalCollectorAllowsTombstones0406(response("failed", complete = false, global = false)))
        assertFalse(externalCollectorAllowsTombstones0406(response("partial", complete = true, global = false)))
        assertFalse(externalCollectorAllowsTombstones0406(response("success", complete = true, global = false)))
        assertTrue(externalCanonicalTripWithinCompleteScope0406(trip(), complete))
        assertFalse(
            externalCanonicalTripWithinCompleteScope0406(
                trip().copy(blablaProfileUuid = "22222222-2222-4222-8222-222222222222"),
                complete,
            ),
        )
    }

    @Test
    fun timelineCapacityIsProjectedFromCanonicalInsteadOfRecalculated() {
        val canonical = trip().copy(capacity = 7, rotaCertaSeatAllocation = 4)
        val entry = TripTimelineEntry(
            tripId = "timeline",
            profileId = profile,
            profileLabel = "Profile",
            departureAtMillis = canonical.departureAtMillis,
            arrivalAtMillis = null,
            origin = "A",
            destination = "C",
            status = TripStatus.PUBLISHED,
            capacity = 1,
            minimumOccupiedSeats = 0,
            maximumOccupiedSeats = 0,
            sourcePassengerSeats = emptyMap(),
            blablaProfileUuid = profile,
            blablaTripId = tripId,
            blablaPublishedSeats = 1,
        )
        val projected = applyCanonicalTripCapacity0406(
            listOf(entry),
            listOf(canonical),
            fallbackRotaCertaSeatAllocation = 0,
        ).single()
        assertEquals(7, projected.capacity)
        assertEquals(4, projected.rotaCertaSeatAllocation)
        assertEquals(canonical.id, projected.localTripId)
    }

    @Test
    fun segmentCapacityCancellationAndOverbookingRemainExplicit() {
        val canonical = trip().copy(capacity = 2)
        val aToB = booking("ab", "a", "b", seats = 2)
        val bToC = booking("bc", "b", "c", seats = 1)
        val loads = SeatAvailabilityEngine.segmentLoads(canonical, listOf(aToB, bToC))
        assertEquals(listOf(2, 1), loads.map(SegmentLoad::occupiedSeats))
        assertEquals(listOf(0, 1), loads.map(SegmentLoad::availableSeats))
        val cancelled = SeatAvailabilityEngine.segmentLoads(
            canonical,
            listOf(aToB.copy(status = BookingStatus.CANCELLED), bToC),
        )
        assertEquals(listOf(0, 1), cancelled.map(SegmentLoad::occupiedSeats))
        val overbooked = SeatAvailabilityEngine.segmentLoads(canonical.copy(capacity = 1), listOf(aToB))
        assertEquals(1, overbooked.first().overbookingSeats)
    }

    @Test
    fun architectureContractsRemoveCollectorToAgendaShortcutAndKeepCrashRecovery() {
        val publicSync = source("PublicAgendaAutoSync0300.kt")
        val background = source("AgendaBackgroundSync0392.kt")
        val store = source("TripStore.kt")
        val outbox = source("TripPublicationOutbox0387.kt")
        assertFalse(publicSync.contains("BlaBlaCollectorStateStore(context).lastResponseRecoveringDynamicSessions()"))
        assertTrue(publicSync.contains("PUBLIC_AGENDA_CANONICAL_SOURCE_0406"))
        assertTrue(background.contains("PROJECTION_RECONCILER_0406"))
        assertTrue(background.contains("BLABLACAR_COMPLETE_SCOPE_DELETE"))
        assertTrue(background.contains("EXTERNAL_CANONICAL_STALE_RESULT_REJECTED_0406"))
        assertTrue(store.contains("reconcileCanonicalIntegrity0406"))
        assertTrue(store.contains("tombstoneExternalTrip0406"))
        assertTrue(outbox.contains("processing_lease_expired"))
        assertTrue(outbox.contains("canonicalTripSnapshot = event.snapshot.trip"))
    }

    @Test
    fun monotonicRevisionRejectsOlderConcurrentResult() {
        assertEquals(
            CanonicalTripRevisionDecision0395.SKIP_STALE_REVISION,
            canonicalTripRevisionDecision0395(51, 50, semanticChanged = true),
        )
        assertEquals(52, nextCanonicalTripRevision0395(51, 51, semanticChanged = true))
    }

    private fun response(status: String, complete: Boolean, global: Boolean) =
        BlaBlaCollectorMonthResponse(
            status = status,
            month = "2030-09",
            profiles = listOf(BlaBlaCollectorProfile(uuid = profile)),
            coverage = BlaBlaCollectorCoverage(
                complete_for_scope = complete,
                global_profile_month_complete = global,
            ),
        )

    private fun trip() = Trip(
        id = "canonical-trip",
        title = "A → C",
        departureAtMillis = 1_915_000_000_000L,
        capacity = 3,
        status = TripStatus.PUBLISHED,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
            TripStop(id = "c", order = 2, name = "C"),
        ),
        blablaProfileUuid = profile,
        blablaTripId = tripId,
        recordOrigin = TripRecordOrigin.EXTERNAL_BACKING,
        tripKey = canonicalTripKey0406(tenant, "BLABLACAR", profile, tripId).orEmpty(),
        externalSnapshot = BlaBlaCollectorTrip(
            profile_uuid = profile,
            date = "2030-09-06",
            departure_time = "10:30",
            actual_departure = "A",
            actual_arrival = "C",
            trip_id = tripId,
            trip_href = "https://example.invalid/rides/offer/" + tripId,
            published_seats = 3,
            passenger_roster_complete = true,
        ),
        externalSnapshotComplete = true,
    )

    private fun booking(id: String, from: String, to: String, seats: Int = 1) = Booking(
        id = id,
        tripId = "canonical-trip",
        passengerName = "Passenger",
        boardingStopId = from,
        dropoffStopId = to,
        seats = seats,
        status = BookingStatus.CONFIRMED,
        source = BookingSource.ROTA_CERTA,
    )

    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/" + name).readText()
}
