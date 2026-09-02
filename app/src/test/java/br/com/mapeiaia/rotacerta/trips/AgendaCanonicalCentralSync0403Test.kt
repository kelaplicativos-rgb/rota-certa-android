package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AgendaCanonicalCentralSync0403Test {
    private fun source(name: String): String =
        File("src/main/java/br/com/mapeiaia/rotacerta/trips/$name").readText()

    @Test
    fun unchangedFingerprintSkipsAndPartialCannotReplaceCompleteSnapshot() {
        assertEquals(
            ExternalCollectorDeltaDecision0403.SKIP_UNCHANGED,
            externalCollectorDeltaDecision0403(
                existingFingerprint = "same",
                incomingFingerprint = "same",
                existingComplete = true,
                incomingComplete = true,
            ),
        )
        assertEquals(
            ExternalCollectorDeltaDecision0403.PRESERVE_PARTIAL,
            externalCollectorDeltaDecision0403(
                existingFingerprint = "old",
                incomingFingerprint = "partial-new",
                existingComplete = true,
                incomingComplete = false,
            ),
        )
        assertEquals(
            ExternalCollectorDeltaDecision0403.UPDATE_CANONICAL,
            externalCollectorDeltaDecision0403(
                existingFingerprint = "old",
                incomingFingerprint = "complete-new",
                existingComplete = true,
                incomingComplete = true,
            ),
        )
    }

    @Test
    fun externalFingerprintIncludesOperationalPassengerAndTimingChanges() {
        val base = BlaBlaCollectorTrip(
            profile_uuid = "7371f028-9c55-4903-8444-308015823efd",
            profile_name = "Driver",
            date = "2030-09-02",
            departure_time = "10:00",
            arrival_time = "12:00",
            search_from = "A",
            search_to = "C",
            actual_departure = "A",
            actual_arrival = "C",
            trip_href = "https://www.blablacar.com.br/rides/offer/trip-x",
            public_trip_href = "https://www.blablacar.com.br/trip/trip-x",
            trip_id = "trip-x",
            availability = "available",
            published_seats = 3,
            booked_seats = 1,
            passenger_roster_complete = true,
            itinerary_authoritative = true,
            itinerary_stops = listOf("A", "B", "C"),
            passengers = listOf(
                BlaBlaCollectorPassenger(
                    name = "Passenger",
                    phone = "+000000",
                    booking_href = "https://www.blablacar.com.br/booking/booking-x",
                    boarding = "A",
                    dropoff = "B",
                    seats = 1,
                ),
            ),
        )
        val fingerprint = PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(base, 1)

        assertNotEquals(fingerprint, PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(base.copy(arrival_time = "12:15"), 1))
        assertNotEquals(
            fingerprint,
            PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(
                base.copy(passengers = base.passengers.map { it.copy(phone = "+111111") }),
                1,
            ),
        )
        assertNotEquals(
            fingerprint,
            PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(
                base.copy(passengers = base.passengers.map { it.copy(name = "Passenger Updated") }),
                1,
            ),
        )

        val secondPassenger = BlaBlaCollectorPassenger(
            name = "Second Passenger",
            phone = "+222222",
            booking_href = "https://www.blablacar.com.br/booking/booking-y",
            boarding = "B",
            dropoff = "C",
            seats = 1,
        )
        val ordered = base.copy(passengers = base.passengers + secondPassenger, booked_seats = 2)
        val reordered = ordered.copy(passengers = ordered.passengers.reversed())
        assertEquals(
            PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(ordered, 1),
            PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(reordered, 1),
        )
        assertEquals(
            fingerprint,
            PublicAgendaAutoSync0300.externalCapacitySnapshotRevision(
                base.copy(
                    trip_href = base.trip_href + "?search_uuid=volatile-a",
                    public_trip_href = base.public_trip_href + "?search_uuid=volatile-b",
                    passengers = base.passengers.map {
                        it.copy(booking_href = it.booking_href + "?search_uuid=volatile-c")
                    },
                ),
                1,
            ),
        )
    }

    @Test
    fun correlatedInternalAndExternalOccupancyConsumesOnePhysicalSeat() {
        val trip = Trip(
            id = "trip",
            title = "A → C",
            departureAtMillis = 4_000_000_000_000L,
            capacity = 4,
            publishedSeats = 3,
            rotaCertaSeatAllocation = 1,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(id = "a", order = 0, name = "A"),
                TripStop(id = "b", order = 1, name = "B"),
                TripStop(id = "c", order = 2, name = "C"),
            ),
        )
        val external = Booking(
            id = "external",
            tripId = trip.id,
            passengerName = "Passenger",
            boardingStopId = "a",
            dropoffStopId = "b",
            seats = 1,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.BLABLACAR,
            capacityClaimType = CapacityClaimType.EXTERNAL_OCCUPANCY,
            occupancyGroupId = "same-occupancy",
        )
        val rotaCerta = external.copy(
            id = "internal",
            source = BookingSource.ROTA_CERTA,
            capacityClaimType = CapacityClaimType.PASSENGER,
        )

        val loads = SeatAvailabilityEngine.segmentLoads(trip, listOf(external, rotaCerta))
        assertEquals(listOf(1, 0), loads.map { it.passengerSeats })
        assertEquals(listOf(3, 4), loads.map { it.availableSeats })
    }

    @Test
    fun timelineReadsCanonicalExternalBackingsAndCollectorOnlyFeedsCentralSynchronizer() {
        val timeline = source("TripTimelineUi.kt")
        val background = source("AgendaBackgroundSync0392.kt")
        val domain = source("TripDomain.kt")
        val outbox = source("TripPublicationOutbox0387.kt")

        assertTrue(domain.contains("val externalSnapshot: BlaBlaCollectorTrip?"))
        assertTrue(domain.contains("val externalSnapshotFingerprint: String"))
        assertTrue(background.contains("reconcileCollectedExternalTrips0403("))
        assertTrue(background.contains("EXTERNAL_CANONICAL_MISSING_PRESERVED_0403"))
        assertTrue(background.contains("recordExternalCollectionMutation("))
        assertTrue(outbox.contains("eventSource = \"EXTERNAL_COLLECTION\""))
        assertTrue(timeline.contains("resolvedTripRecordOrigin(it) == TripRecordOrigin.EXTERNAL_BACKING"))
        assertTrue(timeline.contains("canonicalCollectorResponse0403"))
        assertTrue(timeline.contains("localTripId = binding.bookingTripId.takeIf(String::isNotBlank)"))
        assertFalse(timeline.contains("collectorStore.lastResponseRecoveringDynamicSessions()"))
        assertFalse(timeline.contains("BlaBlaCollectorTimelineEvents0400.revision.collectAsState()"))
    }

    @Test
    fun collectorResultIsTripDeltaAndDoesNotTriggerWholeAgendaPublication() {
        val background = source("AgendaBackgroundSync0392.kt")
        assertEquals(AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE, agendaBackgroundSyncMode0392("periodic"))
        assertEquals(AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE, agendaBackgroundSyncMode0392("blablacar_collection_result"))
        assertTrue(background.contains("val reconcileAllCanonicalTrips = mode == AgendaBackgroundSyncMode0392.FULL_RECONCILE"))
        assertTrue(background.contains("binding?.externalFingerprint != incomingFingerprint"))
        assertTrue(background.contains("EXTERNAL_CANONICAL_BOOKING_ID_MIGRATED_0403"))
        assertTrue(background.contains("store.bookingsFor(previousBookingTripId)"))
        assertTrue(background.contains("binding.copy("))
        assertTrue(background.contains("bookingTripId = canonicalTripId"))
    }

    @Test
    fun successfulPeriodicCoverageRefreshesRecoveryCheckpointWithoutFullPublication() {
        assertTrue(agendaBackgroundSyncRefreshesCoverageCheckpoint0403("periodic"))
        assertTrue(agendaBackgroundSyncRefreshesCoverageCheckpoint0403("blablacar_collection_result"))
        assertTrue(agendaBackgroundSyncRefreshesCoverageCheckpoint0403("recovery"))
        assertFalse(agendaBackgroundSyncRefreshesCoverageCheckpoint0403("trip_mutation"))
        assertEquals(
            AgendaBackgroundSyncMode0392.COLLECTOR_RECONCILE,
            agendaBackgroundSyncMode0392("periodic"),
        )
    }
}
