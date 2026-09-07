package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineCanonicalBackend0494Test {
    private fun stops(
        origin: String = "Origem",
        destination: String = "Destino",
        departure: Long = 1_000L,
        arrival: Long = 2_000L,
    ) = listOf(
        TripStop(
            id = "stop-origin",
            order = 0,
            name = origin,
            plannedDepartureMillis = departure,
        ),
        TripStop(
            id = "stop-destination",
            order = 1,
            name = destination,
            plannedArrivalMillis = arrival,
        ),
    )

    private fun state(
        canonicalId: String,
        revision: Long,
        remoteId: String = "remote-$revision",
        departure: Long = 1_000L,
        blablaTripId: String = "",
    ) = DriverTripSyncState0402(
        remoteTripId = remoteId,
        status = TripStatus.PUBLISHED.name,
        departureAtMillis = departure,
        arrivalAtMillis = departure + 1_000L,
        stops = stops(departure = departure, arrival = departure + 1_000L),
        canonicalTripId = canonicalId,
        canonicalRevision = revision,
        canonicalStateHash = "hash-$revision",
        title = "Origem → Destino",
        capacity = 4,
        publishedSeats = if (blablaTripId.isBlank()) null else 4,
        rotaCertaSeatAllocation = 0,
        availableSeatsMinimum = 2,
        availableSeatsMaximum = 3,
        minimumOccupiedSeats = 1,
        maximumOccupiedSeats = 2,
        segmentLoads = listOf(2),
        segmentPassengerLoads = listOf(2),
        segmentBlockedLoads = listOf(0),
        segmentAvailableSeats = listOf(2),
        blablaTripId = blablaTripId,
        sourceSeatCounts = mapOf(BookingSource.PRIVATE.name to 2),
    )

    @Test
    fun testA_collectorOffCanonicalBackendSnapshotStillBuildsTimeline() {
        val projection = canonicalTimelineProjection0494(
            DriverTripSyncStateResponse0402(
                source = "CANONICAL_BACKEND",
                snapshotAtMillis = 9_000L,
                trips = listOf(state("canonical-a", 7L)),
            ),
        )

        assertEquals(listOf("canonical-a"), projection.entries.map(TripTimelineEntry::tripId))
        assertEquals(listOf("canonical-a"), projection.trips.map(Trip::id))
        assertTrue(projection.entries.single().canonicalBackendAuthoritative0494)
    }

    @Test
    fun testB_and_G_sameCanonicalRevisionOwnsIdentityAndSharedCapacity() {
        val projection = canonicalTimelineProjection0494(
            DriverTripSyncStateResponse0402(
                source = "CANONICAL_BACKEND",
                snapshotAtMillis = 9_000L,
                trips = listOf(state("canonical-g", 11L)),
            ),
        )
        val entry = projection.entries.single()
        val trip = projection.trips.single()

        assertEquals("canonical-g", entry.tripId)
        assertEquals("canonical-g", trip.id)
        assertEquals(11L, entry.canonicalRevision0494)
        assertEquals("hash-11", entry.canonicalStateHash0494)
        assertEquals(2, entry.minimumAvailableSeats)
        assertEquals(3, entry.maximumAvailableSeats)
        assertEquals(4, entry.capacity)
    }

    @Test
    fun testD_manualTripNeedsNoBlaBlaIdentity() {
        val projection = canonicalTimelineProjection0494(
            DriverTripSyncStateResponse0402(
                source = "CANONICAL_BACKEND",
                trips = listOf(state("manual-canonical", 1L, blablaTripId = "")),
            ),
        )

        assertEquals(1, projection.entries.size)
        assertNull(projection.entries.single().blablaTripId)
        assertNull(projection.trips.single().blablaTripId)
    }

    @Test
    fun testH_duplicateTransportRowsCollapseToNewestCanonicalRevision() {
        val projection = canonicalTimelineProjection0494(
            DriverTripSyncStateResponse0402(
                source = "CANONICAL_BACKEND",
                trips = listOf(
                    state("same-canonical", 3L, remoteId = "old"),
                    state("same-canonical", 8L, remoteId = "new"),
                ),
            ),
        )

        assertEquals(1, projection.entries.size)
        assertEquals(8L, projection.entries.single().canonicalRevision0494)
        assertEquals("new", projection.entries.single().remoteTripId0494)
    }

    @Test
    fun testF_nonCanonicalFallbackIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            canonicalTimelineProjection0494(
                DriverTripSyncStateResponse0402(
                    source = "AUTOMATIC_COLLECTOR",
                    trips = listOf(state("forbidden", 1L)),
                ),
            )
        }
    }

    @Test
    fun testC_E_I_sourceContractForbidsCollectorAsTimelineDatasourceAndKeepsCommandsBackendFirst() {
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        val remoteApi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()
        val passenger = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        val quick = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripQuickPassengerUi.kt").readText()
        val backend = File("../trip-platform/functions/index.js").readText()

        assertTrue(timeline.contains("loadCanonicalTimelineState0494"))
        assertTrue(timeline.contains("collectorFallback=false"))
        assertFalse(timeline.contains("BlaBlaTimelineAdapter.merge("))
        assertFalse(timeline.contains("TripPhysicalRideConsolidator.consolidate("))
        assertFalse(timeline.contains("timeline-ext-"))
        assertFalse(timeline.contains("externalSnapshot"))

        val canonicalLoaderStart = remoteApi.indexOf("suspend fun loadCanonicalTimelineState0494")
        val canonicalLoaderEnd = remoteApi.indexOf("suspend fun updateDriverTripPublicVisibility0491", canonicalLoaderStart)
        assertTrue(canonicalLoaderStart >= 0 && canonicalLoaderEnd > canonicalLoaderStart)
        val canonicalLoader = remoteApi.substring(canonicalLoaderStart, canonicalLoaderEnd)
        assertTrue(canonicalLoader.contains("timelineProjection0494 = true"))
        assertTrue(canonicalLoader.contains("CANONICAL_BACKEND"))
        assertFalse(canonicalLoader.contains("collector", ignoreCase = true))
        assertFalse(canonicalLoader.contains("BlaBlaCollector"))

        assertTrue(passenger.contains("TIMELINE_CANONICAL_PASSENGER_MUTATION_0494"))
        assertTrue(passenger.contains("authority=CANONICAL_BACKEND"))
        assertTrue(passenger.contains("localBusinessWrite=false"))
        assertTrue(quick.contains("TIMELINE_CANONICAL_PASSENGER_ADD_0494"))
        assertTrue(quick.contains("collectorWrite=false"))

        assertTrue(backend.contains("timelineProjection0494"))
        assertTrue(backend.contains("source: \"CANONICAL_BACKEND\""))
        assertTrue(backend.contains("applyCanonicalTimelinePhysicalIssues0494"))
    }

    @Test
    fun testF_cacheIsExplicitlySnapshotOnlyAndRejectsRevisionRegression() {
        val store = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripStore.kt").readText()

        assertTrue(store.contains("last backend-canonical Timeline snapshot"))
        assertTrue(store.contains("TIMELINE_CANONICAL_STALE_REJECTED_0494"))
        assertTrue(store.contains("old.canonicalRevision > state.canonicalRevision"))
        assertFalse(store.substringAfter("fun saveTimelineCanonicalCache0494").substringBefore("fun getTrip").contains("collector", ignoreCase = true))
    }

    @Test
    fun testI_downloadEvidenceUsesCanonicalIdentityNotLegacyTimelineIdentity() {
        val download = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaTimelineDownload0398.kt").readText()

        assertTrue(download.contains("put(\"source\", \"CANONICAL_BACKEND\")"))
        assertTrue(download.contains("put(\"collectorFallback\", false)"))
        assertTrue(download.contains("put(\"canonicalTripId\", entry.tripId)"))
        assertFalse(download.contains("automaticSyncLastTrigger"))
        assertFalse(download.contains("put(\"timelineTripId\""))
        assertFalse(download.contains("timeline-ext-"))
    }
}
