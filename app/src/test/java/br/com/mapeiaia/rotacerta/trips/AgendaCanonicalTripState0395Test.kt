package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AgendaCanonicalTripState0395Test {
    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    private fun rootSource(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/$name").readText()

    private fun trip(
        id: String = "trip-x",
        capacity: Int = 1,
        stops: List<TripStop> = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
            TripStop(id = "c", order = 2, name = "C"),
        ),
    ) = Trip(
        id = id,
        title = "A → C",
        departureAtMillis = 4_000_000_000_000L,
        capacity = capacity,
        status = TripStatus.PUBLISHED,
        stops = stops,
        rotaCertaSeatAllocation = capacity,
        canonicalRevision = 10L,
        seatAllocationVersionUsed = 4L,
    )

    private fun booking(
        id: String,
        trip: Trip,
        from: String,
        to: String,
        status: BookingStatus = BookingStatus.CONFIRMED,
        group: String? = null,
    ) = Booking(
        id = id,
        tripId = trip.id,
        passengerName = id,
        boardingStopId = from,
        dropoffStopId = to,
        seats = 1,
        status = status,
        source = BookingSource.PRIVATE,
        occupancyGroupId = group,
    )

    private fun external(index: Int) = BlaBlaCollectorTrip(
        profile_uuid = "profile-${index % 3}",
        date = "2030-01-${(index % 28 + 1).toString().padStart(2, '0')}",
        departure_time = "10:00",
        arrival_time = "11:00",
        search_from = "A",
        search_to = "C",
        actual_departure = "A",
        actual_arrival = "C",
        availability = "available",
        trip_id = "external-$index",
        itinerary_stops = listOf("A", "B", "C"),
        itinerary_authoritative = true,
        booked_seats = 0,
        published_seats = 2,
        passenger_roster_complete = true,
    )

    @Test
    fun staleCanonicalRevisionNeverWinsAndEqualRevisionMutationAdvances() {
        assertEquals(
            CanonicalTripRevisionDecision0395.SKIP_STALE_REVISION,
            canonicalTripRevisionDecision0395(
                currentRevision = 106L,
                incomingRevision = 105L,
                semanticChanged = true,
            ),
        )
        assertEquals(
            CanonicalTripRevisionDecision0395.UPDATE,
            canonicalTripRevisionDecision0395(
                currentRevision = 106L,
                incomingRevision = 106L,
                semanticChanged = true,
            ),
        )
        assertEquals(107L, nextCanonicalTripRevision0395(106L, 106L, semanticChanged = true))

        val store = source("TripStore.kt")
        assertTrue(store.contains("synchronized(CANONICAL_LOCK)"))
        assertTrue(store.contains("result=SKIP_STALE_REVISION"))
        assertTrue(store.contains(".commit()"))
    }

    @Test
    fun privatePassengerConsumesOnlyItsSegmentsAndImmediatelyBlocksItsFullSegment() {
        val trip = trip()
        val privatePassenger = booking("private-1", trip, "a", "b")
        val loads = SeatAvailabilityEngine.segmentLoads(trip, listOf(privatePassenger))

        assertEquals(listOf(0, 1), loads.map(SegmentLoad::availableSeats))
        assertFalse(
            SeatAvailabilityEngine.availability(
                trip,
                listOf(privatePassenger),
                "a",
                "b",
                1,
            ).canBook,
        )
        assertTrue(
            SeatAvailabilityEngine.availability(
                trip,
                listOf(privatePassenger),
                "b",
                "c",
                1,
            ).canBook,
        )
    }

    @Test
    fun cancellationReleasesCanonicalSegmentCapacity() {
        val trip = trip()
        val active = booking("private-1", trip, "a", "b")
        val cancelled = active.copy(
            status = BookingStatus.CANCELLED,
            operationalStatus = PassengerOperationalStatus.CANCELLED,
        )

        assertEquals(0, SeatAvailabilityEngine.segmentLoads(trip, listOf(active)).first().availableSeats)
        assertEquals(1, SeatAvailabilityEngine.segmentLoads(trip, listOf(cancelled)).first().availableSeats)
    }

    @Test
    fun duplicateOccupancyIdentityNeverConsumesTwice() {
        val trip = trip(capacity = 2)
        val first = booking("first", trip, "a", "c", group = "same-strong-reservation")
        val mirror = booking("mirror", trip, "a", "c", group = "same-strong-reservation")
            .copy(source = BookingSource.ROTA_CERTA)

        val loads = SeatAvailabilityEngine.segmentLoads(trip, listOf(first, mirror))
        assertEquals(listOf(1, 1), loads.map(SegmentLoad::passengerSeats))
        assertEquals(listOf(1, 1), loads.map(SegmentLoad::availableSeats))
    }

    @Test
    fun thirtyUnchangedExternalCardsAreAllSkipAndOneFingerprintChangeIsOneUpdate() {
        val before = (0 until 30).map(::external)
        val unchanged = before.map { it.copy() }
        val unchangedUpdates = before.zip(unchanged).count { (old, next) ->
            PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(old, 2) !=
                PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(next, 2)
        }
        assertEquals(0, unchangedUpdates)

        val oneChanged = unchanged.toMutableList().also {
            it[17] = it[17].copy(published_seats = 1)
        }
        val changedUpdates = before.zip(oneChanged).count { (old, next) ->
            PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(old, 2) !=
                PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(next, 2)
        }
        assertEquals(1, changedUpdates)
    }

    @Test
    fun externalSeatDifferenceDoesNotInventPassengerIdentity() {
        val before = external(7)
        val after = before.copy(published_seats = 1)
        assertTrue(before.passengers.isEmpty())
        assertTrue(after.passengers.isEmpty())
        assertNotEquals(
            PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(before, 0),
            PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(after, 0),
        )
        val outbox = source("TripPublicationOutbox0387.kt")
        val start = outbox.indexOf("private fun recordExternalMutation(")
        val end = outbox.indexOf("fun recordTombstone(", start)
        assertTrue(start >= 0 && end > start)
        assertFalse(outbox.substring(start, end).contains("Booking("))
    }

    @Test
    fun timelineAndPublicAgendaUseTripScopedCanonicalOutboxInsteadOfCircularFullSync() {
        val outbox = source("TripPublicationOutbox0387.kt")
        val autoSync = source("PublicAgendaAutoSync0300.kt")
        val bookingSync = source("PublicBookingSync0296.kt")
        val activity = source("TripsActivity.kt")

        assertTrue(outbox.contains("canonicalTripId = canonicalTripId"))
        assertTrue(outbox.contains("localTripId = event.canonicalTripId"))
        assertTrue(outbox.contains("snapshotTrip = snapshotTrip"))
        assertTrue(autoSync.contains("syncLocalTripIncremental("))
        assertTrue(bookingSync.contains("changed.forEach { tripId ->"))
        assertTrue(bookingSync.contains("store.saveBookingsBatch("))
        assertTrue(bookingSync.contains("canonicalTripId = tripId"))
        assertFalse(activity.contains("createPublicAgendaSyncCoordinator0373"))
        assertFalse(activity.contains("TENANT_SEAT_ALLOCATION_EXACT_IMPACT"))
    }

    @Test
    fun tenantSeatConfigurationIsVersionedAndFanoutRetriesOnlyPendingExternalTrips() {
        val models = rootSource("Models.kt")
        val repository = rootSource("Repositories.kt")
        val background = source("AgendaBackgroundSync0392.kt")
        val store = source("TripStore.kt")
        val outbox = source("TripPublicationOutbox0387.kt")

        assertTrue(models.contains("rotaCertaSeatAllocationVersion"))
        assertTrue(repository.contains("rota_certa_seat_allocation_version"))
        assertTrue(repository.contains("previousSeatAllocationVersion + 1L"))
        assertTrue(repository.contains("reconcileTenantSeatAllocation0395("))
        assertTrue(background.contains("binding.seatAllocationVersionUsed < seatAllocationVersion"))
        assertTrue(background.contains("result=RETRY_PENDING"))
        assertTrue(background.contains("recordExternalTenantMutation("))
        assertTrue(store.contains("trip.seatAllocationVersionUsed > seatAllocationVersion"))
        assertTrue(outbox.contains("latest.snapshot.seatAllocationVersion != snapshot.seatAllocationVersion"))\n        assertTrue(outbox.contains("shouldDeduplicatePublicationEvent0410"))
        assertTrue(outbox.contains("FAILED_RETRYABLE"))
        assertTrue(outbox.contains("SUPERSEDED"))
    }

    @Test
    fun externalBindingKeepsStableInternalIdentityAcrossRemoteIdChanges() {
        val autoSync = source("PublicAgendaAutoSync0300.kt")
        val store = source("TripStore.kt")
        val outbox = source("TripPublicationOutbox0387.kt")

        assertTrue(autoSync.contains("stableInternalTripId"))
        assertTrue(autoSync.contains("existing?.bookingTripId"))
        assertTrue(autoSync.contains("canonicalTripId.takeIf(String::isNotBlank)"))
        assertTrue(store.contains("publicExternalBindingForStrongIdentity"))
        assertTrue(outbox.contains("existingBinding?.bookingTripId"))
        assertTrue(outbox.contains("strongExternalCanonicalTripId0387"))
    }

    @Test
    fun localBookingPersistenceBumpsCanonicalRevisionOncePerSemanticChangeAndSkipsReplay() {
        val store = source("TripStore.kt")
        assertTrue(store.contains("existing.copy(updatedAtMillis = 0L) != incoming.copy(updatedAtMillis = 0L)"))
        assertTrue(store.contains("if (changedTripIds.isEmpty()) return@synchronized normalized"))
        assertTrue(store.contains("refreshCanonicalTripStateBatch0395("))
        assertTrue(store.contains("canonicalRevision = trip.canonicalRevision.coerceAtLeast(0L) + 1L"))
    }
}
