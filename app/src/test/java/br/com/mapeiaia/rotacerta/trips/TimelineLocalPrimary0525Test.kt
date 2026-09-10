package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineLocalPrimary0525Test {
    private fun stops(
        originAddress: String = "Rua Origem, 10",
        destinationAddress: String = "Rua Destino, 20",
    ) = listOf(
        TripStop(
            id = "stop-origin",
            order = 0,
            name = "Origem",
            address = originAddress,
            latitude = -23.50,
            longitude = -46.60,
            plannedDepartureMillis = 1_000L,
        ),
        TripStop(
            id = "stop-destination",
            order = 1,
            name = "Destino",
            address = destinationAddress,
            latitude = -22.20,
            longitude = -45.90,
            plannedArrivalMillis = 2_000L,
        ),
    )

    private fun trip(
        revision: Long = 10L,
        profileUuid: String? = "profile-a",
        providerTripId: String? = "trip-b",
        manageUrl: String? = "https://www.blablacar.com.br/rides/offer/trip-b",
        publicUrl: String? = "https://www.blablacar.com.br/trip/public-b",
        capacity: Int = 4,
        status: TripStatus = TripStatus.PUBLISHED,
        stops: List<TripStop> = stops(),
        tripKey: String = "tripkey-stable",
    ) = Trip(
        id = "canonical-a",
        title = "Origem → Destino",
        departureAtMillis = 1_000L,
        capacity = capacity,
        status = status,
        stops = stops,
        blablaProfileUuid = profileUuid,
        blablaTripId = providerTripId,
        blablaManageUrl = manageUrl,
        blablaPublicUrl = publicUrl,
        publishedSeats = capacity,
        capacityReliable = true,
        recordOrigin = if (!profileUuid.isNullOrBlank() && !providerTripId.isNullOrBlank()) {
            TripRecordOrigin.EXTERNAL_BACKING
        } else {
            TripRecordOrigin.LOCAL
        },
        canonicalRevision = revision,
        publicationRevision = revision,
        tripKey = tripKey,
        canonicalStateHash = "hash-$revision",
        updatedAtMillis = revision * 1_000L,
    )

    private fun booking(
        id: String = "booking-local",
        tripId: String = "canonical-a",
        passengerId: String = "passenger-1",
        contact: String = "5511999999999",
        boardingAddress: String = "Embarque exato, 123",
        dropoffAddress: String = "Desembarque exato, 456",
        boardingLatitude: Double? = -23.501,
        boardingLongitude: Double? = -46.601,
        updatedAtMillis: Long = 10_000L,
    ) = Booking(
        id = id,
        tripId = tripId,
        passengerId = passengerId,
        passengerName = "Passageiro",
        passengerContact = contact,
        boardingStopId = "stop-origin",
        dropoffStopId = "stop-destination",
        seats = 1,
        status = BookingStatus.CONFIRMED,
        operationalStatus = PassengerOperationalStatus.CONFIRMED,
        paymentStatus = PassengerPaymentStatus.PAID,
        source = BookingSource.BLABLACAR,
        sourceReference = "BLABLACAR_SYNC:passenger-1",
        fareMinorUnits = 4_200L,
        fareCurrencyCode = "BRL",
        boardingAddress = boardingAddress,
        dropoffAddress = dropoffAddress,
        boardingLatitude = boardingLatitude,
        boardingLongitude = boardingLongitude,
        dropoffLatitude = -22.201,
        dropoffLongitude = -45.901,
        localMetadataTouched = true,
        updatedAtMillis = updatedAtMillis,
    )

    private fun projection(
        trip: Trip,
        bookings: List<Booking> = emptyList(),
        remote: Boolean = false,
        issues: Set<TripTimelineIssue> = emptySet(),
        occupancyRevision: Long? = null,
    ): CanonicalTimelineProjection0494 {
        val base = localAgendaTimelineProjection0515(
            trips = listOf(trip),
            bookings = bookings,
            localProfileLabel = "Agenda",
            nowMillis = 50_000L,
        )
        if (!remote) return base
        val entry = base.entries.single().copy(
            canonicalBackendAuthoritative0494 = true,
            canonicalRevision0494 = trip.canonicalRevision,
            canonicalStateHash0494 = trip.canonicalStateHash,
            canonicalUpdatedAtMillis0494 = trip.updatedAtMillis,
            canonicalOccupancyRevision0494 = occupancyRevision,
            issues = issues,
        )
        return base.copy(entries = listOf(entry), snapshotAtMillis = 60_000L)
    }

    @Test
    fun scenarioA_backendOfflineLocalProjectionKeepsOperationalCardAndPrivatePassengerData() {
        val localTrip = trip()
        val localBooking = booking()
        val local = projection(localTrip, listOf(localBooking))

        assertEquals(1, local.entries.size)
        val entry = local.entries.single()
        assertEquals("profile-a", entry.blablaProfileUuid)
        assertEquals("trip-b", entry.blablaTripId)
        assertEquals("https://www.blablacar.com.br/rides/offer/trip-b", canonicalTimelineManageHref0524(entry))
        assertEquals("https://www.blablacar.com.br/trip/public-b", entry.blablaPublicHref)
        assertEquals("5511999999999", local.bookings.single().passengerContact)
        assertEquals("Embarque exato, 123", local.bookings.single().boardingAddress)
        assertEquals(-23.501, local.bookings.single().boardingLatitude)
        assertEquals(PassengerPaymentStatus.PAID, local.bookings.single().paymentStatus)
    }

    @Test
    fun scenarioB_incompleteRemoteCannotEraseCompleteLocalIdentityOrDisableRadar() {
        val local = projection(trip())
        val remoteTrip = trip(
            revision = 11L,
            profileUuid = "profile-a",
            providerTripId = null,
            manageUrl = null,
            publicUrl = null,
            tripKey = "",
        )
        val remote = projection(
            trip = remoteTrip,
            remote = true,
            issues = setOf(TripTimelineIssue.EXTERNAL_IDENTITY_INCOMPLETE),
        )

        val result = mergeCanonicalTimelineProjections0525(local, remote, nowMillis = 70_000L)
        val mergedTrip = result.projection.trips.single()
        val entry = result.projection.entries.single()

        assertEquals("profile-a", mergedTrip.blablaProfileUuid)
        assertEquals("trip-b", mergedTrip.blablaTripId)
        assertEquals("https://www.blablacar.com.br/rides/offer/trip-b", mergedTrip.blablaManageUrl)
        assertFalse(TripTimelineIssue.EXTERNAL_IDENTITY_INCOMPLETE in entry.issues)
        assertEquals(1, result.incompleteRemoteIgnored)
        assertTrue(timelineBlaBlaTargetUnambiguous0525(entry))
        assertNotNull(canonicalTimelineManageHref0524(entry))
    }

    @Test
    fun scenarioC_newerCompleteRemoteAdvancesRevisionWithoutBlankingProtectedLocalFields() {
        val local = projection(
            trip(
                revision = 10L,
                capacity = 4,
                status = TripStatus.PUBLISHED,
            ),
        )
        val remote = projection(
            trip(
                revision = 11L,
                manageUrl = null,
                publicUrl = null,
                capacity = 6,
                status = TripStatus.ACTIVE,
            ),
            remote = true,
            occupancyRevision = 11L,
        )

        val result = mergeCanonicalTimelineProjections0525(local, remote, nowMillis = 70_000L)
        val merged = result.projection.trips.single()
        assertEquals(11L, merged.canonicalRevision)
        assertEquals(6, merged.capacity)
        assertEquals(TripStatus.ACTIVE, merged.status)
        assertEquals("https://www.blablacar.com.br/rides/offer/trip-b", merged.blablaManageUrl)
        assertEquals("https://www.blablacar.com.br/trip/public-b", merged.blablaPublicUrl)
        assertTrue(result.changed)
    }

    @Test
    fun scenarioD_remoteEmptyPrivateFieldsNeverEraseLocalWhatsappAddressGpsOrFare() {
        val localBooking = booking()
        val local = projection(trip(), listOf(localBooking))
        val remoteBooking = booking(
            id = "remote-booking-id",
            contact = "",
            boardingAddress = "",
            dropoffAddress = "",
            boardingLatitude = null,
            boardingLongitude = null,
            updatedAtMillis = 20_000L,
        ).copy(
            tripId = "canonical-a",
            fareMinorUnits = null,
            fareCurrencyCode = "",
            dropoffLatitude = null,
            dropoffLongitude = null,
            localMetadataTouched = false,
        )
        val remote = projection(
            trip = trip(revision = 11L),
            bookings = listOf(remoteBooking),
            remote = true,
            occupancyRevision = 11L,
        )

        val result = mergeCanonicalTimelineProjections0525(local, remote, nowMillis = 70_000L)
        val merged = result.projection.bookings.single()

        assertEquals("booking-local", merged.id)
        assertEquals("5511999999999", merged.passengerContact)
        assertEquals("Embarque exato, 123", merged.boardingAddress)
        assertEquals("Desembarque exato, 456", merged.dropoffAddress)
        assertEquals(-23.501, merged.boardingLatitude)
        assertEquals(-46.601, merged.boardingLongitude)
        assertEquals(4_200L, merged.fareMinorUnits)
        assertEquals("BRL", merged.fareCurrencyCode)
    }

    @Test
    fun scenarioE_realExternalIdentityConflictPreservesLocalAndBlocksAmbiguousAction() {
        val local = projection(trip())
        val remote = projection(
            trip(
                revision = 11L,
                profileUuid = "profile-a",
                providerTripId = "trip-c",
                manageUrl = "https://www.blablacar.com.br/rides/offer/trip-c",
                publicUrl = null,
                tripKey = "tripkey-conflicting",
            ),
            remote = true,
        )

        val result = mergeCanonicalTimelineProjections0525(local, remote, nowMillis = 70_000L)
        val merged = result.projection.trips.single()
        val entry = result.projection.entries.single()

        assertEquals("trip-b", merged.blablaTripId)
        assertEquals("https://www.blablacar.com.br/rides/offer/trip-b", merged.blablaManageUrl)
        assertEquals(1, result.conflictsRejected)
        assertTrue(TripTimelineIssue.EXTERNAL_IDENTITY_CONFLICT in entry.issues)
        assertFalse(timelineBlaBlaTargetUnambiguous0525(entry))
    }

    @Test
    fun scenarioF_radarUsesAgendaCommandAndDoesNotDependOnRemoteBackendDatasource() {
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        val start = timeline.indexOf("val queueTargetCollectorRefresh0517")
        val end = timeline.indexOf("if (showMirrorDiagnostic0417)", start)
        assertTrue(start >= 0 && end > start)
        val radar = timeline.substring(start, end)

        assertTrue(radar.contains("AgendaBackgroundSync0392.enqueueTripCollectorRefresh0517"))
        assertTrue(radar.contains("TIMELINE_RADAR_LOCAL_IDENTITY_USED"))
        assertFalse(radar.contains("TripRemoteApi"))
        assertFalse(timeline.contains("localAgendaProjection0515 = null"))
        assertTrue(timeline.contains("mergeCanonicalTimelineProjections0525"))
        assertTrue(timeline.contains("TIMELINE_LOCAL_PRIMARY_SELECTED"))
        assertTrue(timeline.contains("TIMELINE_REMOTE_SYNC_STARTED"))
        assertTrue(timeline.contains("TIMELINE_REMOTE_MERGED"))
        assertTrue(timeline.contains("TIMELINE_REMOTE_NOOP"))
        assertTrue(timeline.contains("TIMELINE_REMOTE_INCOMPLETE_IGNORED"))
        assertTrue(timeline.contains("TIMELINE_REMOTE_CONFLICT_REJECTED"))
        assertTrue(timeline.contains("TIMELINE_LOCAL_PRESERVED"))
        assertTrue(timeline.contains("collectorRead=false"))
        assertTrue(timeline.contains("collectorFallback=false"))
    }
}
