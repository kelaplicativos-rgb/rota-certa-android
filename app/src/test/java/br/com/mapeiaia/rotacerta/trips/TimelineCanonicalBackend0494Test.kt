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
        bookings: List<RemoteBooking> = emptyList(),
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
        bookings = bookings,
    )

    @Test
    fun testA_collectorOffCanonicalBackendSnapshotStillBuildsTimeline() {
        val projection = canonicalTimelineProjection0494(
            DriverTripSyncStateResponse0402(
                source = "CANONICAL_NATIVE_FIREWALL",
                provenancePolicy0500 = "AGENDA_CANONICAL_ONLY_0503",
                collectorRead = false,
                collectorFallback = false,
                collectorDerivedData = false,
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
                source = "CANONICAL_NATIVE_FIREWALL",
                provenancePolicy0500 = "AGENDA_CANONICAL_ONLY_0503",
                collectorRead = false,
                collectorFallback = false,
                collectorDerivedData = false,
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
                source = "CANONICAL_NATIVE_FIREWALL",
                provenancePolicy0500 = "AGENDA_CANONICAL_ONLY_0503",
                collectorRead = false,
                collectorFallback = false,
                collectorDerivedData = false,
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
                source = "CANONICAL_NATIVE_FIREWALL",
                provenancePolicy0500 = "AGENDA_CANONICAL_ONLY_0503",
                collectorRead = false,
                collectorFallback = false,
                collectorDerivedData = false,
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
        assertTrue(canonicalLoader.contains("validateCanonicalTimelineResponse0512"))
        assertTrue(canonicalLoader.contains("CANONICAL_NATIVE_FIREWALL"))
        assertTrue(canonicalLoader.contains("AGENDA_CANONICAL_ONLY_0503"))
        val canonicalValidationStart = remoteApi.indexOf("internal fun validateCanonicalTimelineResponse0512")
        val canonicalValidationEnd = remoteApi.indexOf("@Serializable\ndata class DriverOperationalStatusRequest", canonicalValidationStart)
        assertTrue(canonicalValidationStart >= 0 && canonicalValidationEnd > canonicalValidationStart)
        val canonicalValidation = remoteApi.substring(canonicalValidationStart, canonicalValidationEnd)
        assertTrue(canonicalValidation.contains("response.collectorRead"))
        assertTrue(canonicalValidation.contains("response.collectorFallback"))
        assertTrue(canonicalValidation.contains("response.collectorDerivedData"))
        assertFalse(canonicalLoader.contains("BlaBlaCollector"))

        assertTrue(passenger.contains("TIMELINE_CANONICAL_PASSENGER_MUTATION_0494"))
        assertTrue(passenger.contains("authority=CANONICAL_BACKEND"))
        assertTrue(passenger.contains("localBusinessWrite=false"))
        assertTrue(quick.contains("TIMELINE_CANONICAL_PASSENGER_ADD_0494"))
        assertTrue(quick.contains("collectorWrite=false"))

        assertTrue(backend.contains("timelineProjection0494"))
        assertTrue(backend.contains("source: timelineProjection0494 ? \"CANONICAL_NATIVE_FIREWALL\" : \"CANONICAL_BACKEND\""))
        assertTrue(backend.contains("provenancePolicy0500: timelineProjection0494 ? \"AGENDA_CANONICAL_ONLY_0503\" : \"\""))
        assertTrue(backend.contains("applyCanonicalTimelinePhysicalIssues0494"))
    }

    @Test
    fun testF_cacheIsExplicitlySnapshotOnlyAndRejectsRevisionRegression() {
        val store = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripStore.kt").readText()

        assertTrue(store.contains("last backend-canonical Timeline snapshot"))
        assertTrue(store.contains("TIMELINE_CANONICAL_STALE_REJECTED_0494"))
        assertTrue(store.contains("old.canonicalRevision > state.canonicalRevision"))
        val cacheWriter = store.substringAfter("fun saveTimelineCanonicalCache0494").substringBefore("fun getTrip")
        assertTrue(cacheWriter.contains("CANONICAL_NATIVE_FIREWALL"))
        assertTrue(cacheWriter.contains("AGENDA_CANONICAL_ONLY_0503"))
        assertTrue(cacheWriter.contains("!incoming.collectorRead"))
        assertTrue(cacheWriter.contains("!incoming.collectorFallback"))
        assertTrue(cacheWriter.contains("!incoming.collectorDerivedData"))
        assertFalse(cacheWriter.contains("BlaBlaCollector"))
    }

    @Test
    fun testI_downloadEvidenceUsesCanonicalIdentityNotLegacyTimelineIdentity() {
        val download = File("src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaTimelineDownload0398.kt").readText()

        assertTrue(download.contains("put(\"schemaVersion\", \"3.0\")"))
        assertTrue(download.contains("put(\"source\", \"CANONICAL_NATIVE_FIREWALL\")"))
        assertTrue(download.contains("put(\"collectorRead\", false)"))
        assertTrue(download.contains("put(\"collectorFallback\", false)"))
        assertTrue(download.contains("put(\"collectorDerivedData\", false)"))
        assertTrue(download.contains("DriverTripSyncState0402.serializer()"))
        assertTrue(download.contains("put(\"localMetadata\""))
        assertFalse(download.contains("automaticSyncLastTrigger"))
        assertFalse(download.contains("put(\"timelineTripId\""))
        assertFalse(download.contains("timeline-ext-"))
    }

    @Test
    fun testJ_canonicalBookingDrivesPassengerUiEvenWhenLegacyRosterIsEmpty() {
        val remoteBooking = RemoteBooking(
            id = "booking-canonical",
            tripId = "remote-1",
            passengerId = "passenger-1",
            passengerName = "Passageiro Fixture",
            passengerContact = "contact-fixture",
            boardingStopId = "stop-origin",
            dropoffStopId = "stop-destination",
            seats = 1,
            status = BookingStatus.CONFIRMED.name,
            operationalStatus = PassengerOperationalStatus.IN_CAR,
            paymentStatus = PassengerPaymentStatus.PAID,
            source = BookingSource.BLABLACAR,
            sourceReference = "BLABLACAR_SYNC:fixture",
            occupancyGroupId = "occupancy-fixture",
        )
        val projection = canonicalTimelineProjection0494(
            DriverTripSyncStateResponse0402(
                source = "CANONICAL_NATIVE_FIREWALL",
                provenancePolicy0500 = "AGENDA_CANONICAL_ONLY_0503",
                collectorRead = false,
                collectorFallback = false,
                collectorDerivedData = false,
                trips = listOf(state("canonical-booking", 4L, bookings = listOf(remoteBooking))),
            ),
        )

        assertTrue(projection.entries.single().blablaPassengers.isEmpty())
        assertEquals(1, projection.bookings.size)
        assertEquals("booking-canonical", projection.bookings.single().id)
        assertEquals(PassengerOperationalStatus.IN_CAR, projection.bookings.single().operationalStatus)
        assertEquals(PassengerPaymentStatus.PAID, projection.bookings.single().paymentStatus)
    }

    @Test
    fun testK_serverAuthorityPreservesOnlyExplicitLocalBookingMetadata() {
        val remoteBooking = RemoteBooking(
            id = "booking-local-meta",
            passengerId = "server-passenger",
            passengerName = "Passenger",
            passengerContact = "server-contact",
            boardingStopId = "stop-origin",
            dropoffStopId = "stop-destination",
            seats = 1,
            status = BookingStatus.CONFIRMED.name,
            operationalStatus = PassengerOperationalStatus.IN_CAR,
            paymentStatus = PassengerPaymentStatus.PAID,
            source = BookingSource.ROTA_CERTA,
        )
        val local = Booking(
            id = "booking-local-meta",
            tripId = "old-local-trip",
            passengerId = "local-passenger",
            passengerName = "Old",
            passengerContact = "old-contact",
            boardingStopId = "stop-origin",
            dropoffStopId = "stop-destination",
            fareMinorUnits = 12_345L,
            fareCurrencyCode = "BRL",
            boardingAddress = "Endereço local de embarque",
            dropoffAddress = "Endereço local de desembarque",
            localMetadataTouched = true,
            operationalStatus = PassengerOperationalStatus.CONFIRMED,
            paymentStatus = PassengerPaymentStatus.UNPAID,
        )
        val projection = canonicalTimelineProjection0494(
            response = DriverTripSyncStateResponse0402(
                source = "CANONICAL_NATIVE_FIREWALL",
                provenancePolicy0500 = "AGENDA_CANONICAL_ONLY_0503",
                collectorRead = false,
                collectorFallback = false,
                collectorDerivedData = false,
                trips = listOf(state("canonical-local-meta", 5L, bookings = listOf(remoteBooking))),
            ),
            existingLocalBookings = listOf(local),
        )
        val booking = projection.bookings.single()

        assertEquals("canonical-local-meta", booking.tripId)
        assertEquals(PassengerOperationalStatus.IN_CAR, booking.operationalStatus)
        assertEquals(PassengerPaymentStatus.PAID, booking.paymentStatus)
        assertEquals(12_345L, booking.fareMinorUnits)
        assertEquals("BRL", booking.fareCurrencyCode)
        assertEquals("Endereço local de embarque", booking.boardingAddress)
        assertEquals("Endereço local de desembarque", booking.dropoffAddress)
        assertTrue(booking.localMetadataTouched)
    }

    @Test
    fun testL_downloadSchema3ContainsExactCanonicalOperationalStructures() {
        val remoteBooking = RemoteBooking(
            id = "booking-export",
            passengerId = "passenger-export",
            passengerName = "Passenger",
            boardingStopId = "stop-origin",
            dropoffStopId = "stop-destination",
            seats = 1,
            status = BookingStatus.CONFIRMED.name,
            operationalStatus = PassengerOperationalStatus.AT_LOCATION,
            paymentStatus = PassengerPaymentStatus.UNPAID,
            source = BookingSource.ROTA_CERTA,
        )
        val response = DriverTripSyncStateResponse0402(
            source = "CANONICAL_NATIVE_FIREWALL",
                provenancePolicy0500 = "AGENDA_CANONICAL_ONLY_0503",
                collectorRead = false,
                collectorFallback = false,
                collectorDerivedData = false,
            snapshotAtMillis = 55_000L,
            trips = listOf(state("canonical-export", 6L, bookings = listOf(remoteBooking))),
        )
        val projected = canonicalTimelineProjection0494(response)
        val json = agendaTimelineDownloadJson0398(
            response = response,
            projectedBookings = projected.bookings,
            selectedCanonicalTripIds = setOf("canonical-export"),
            generatedAtMillis = 77_000L,
        )

        assertTrue(json.contains("\"schemaVersion\":\"3.0\""))
        assertTrue(json.contains("\"canonicalTripId\":\"canonical-export\""))
        assertTrue(json.contains("\"segmentLoads\":[2]"))
        assertTrue(json.contains("\"bookings\":[{"))
        assertTrue(json.contains("\"operationalStatus\":\"AT_LOCATION\""))
        assertTrue(json.contains("\"localMetadata\":{"))
        assertFalse(json.contains("\"issues\":\""))
        assertFalse(json.contains("\"sourceSeatCounts\":\""))
    }

    @Test
    fun testM_eventDrivenRefreshIsPrimaryAndPollingIsRecoveryOnly() {
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()

        assertTrue(timeline.contains("BookingRealtimeEvents0356.changes.collect"))
        assertTrue(timeline.contains("TIMELINE_INVALIDATED"))
        assertTrue(timeline.contains("TIMELINE_REFRESH_STARTED"))
        assertTrue(timeline.contains("TIMELINE_REFRESH_APPLIED"))
        assertTrue(timeline.contains("POLL_RECOVERY"))
        assertTrue(timeline.contains("FOREGROUND"))
        assertTrue(timeline.contains("NETWORK_AVAILABLE"))
        assertFalse(timeline.contains("BlaBlaTimelineAdapter.merge("))
    }

    @Test
    fun testN_canonicalPassengerConsumersDoNotUseLegacyRosterAsAuthority() {
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimeline.kt").readText()
        val timelineUi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        val passengerUi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()

        assertTrue(timeline.contains("if (entry.canonicalBackendAuthoritative0494)"))
        assertTrue(timelineUi.contains("if (enriched.canonicalBackendAuthoritative0494)"))
        assertTrue(timelineUi.contains("sourcePassengerSeats[BookingSource.BLABLACAR]"))
        assertTrue(passengerUi.contains("if (entry.canonicalBackendAuthoritative0494) emptyList() else entry.blablaPassengers"))
        assertTrue(passengerUi.contains("canonicalBookings0494"))
    }


    @Test
    fun testO_pullToRefreshIsCanonicalNetworkRefreshNotLocalVisualReload() {
        val activity = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt").readText()
        val timelineUi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()

        assertTrue(activity.contains("AGENDA_TIMELINE_CANONICAL_PULL_REFRESH_0499"))
        assertTrue(activity.contains("networkSync=true source=CANONICAL_NATIVE_FIREWALL collectorRead=false collectorFallback=false collectorDerivedData=false"))
        assertTrue(activity.contains("manualRefreshToken0499 = timelinePullRefreshToken0499"))
        assertFalse(activity.contains("requestTimelineVisualReload"))
        assertTrue(timelineUi.contains("USER_PULL_REFRESH"))
        assertTrue(timelineUi.contains("loadCanonicalTimelineState0494"))
        assertFalse(timelineUi.contains("BlaBlaTimelineAdapter.merge("))
    }

    @Test
    fun testP_driverPrivateAgendaMirrorHydratesTimelineWithoutCollectorAuthority() {
        val remote = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt").readText()
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimeline.kt").readText()

        assertTrue(remote.contains("privateMirrorCurrent0499"))
        assertTrue(remote.contains("fareMinorUnits = fareMinorUnits ?: existingLocal?.fareMinorUnits"))
        assertTrue(remote.contains("boardingAddress = boardingAddress.ifBlank"))
        assertTrue(remote.contains("dropoffAddress = dropoffAddress.ifBlank"))
        assertTrue(timeline.contains("notes = state.notes0499"))
        assertTrue(timeline.contains("publicTimezoneId0411 = state.timezoneId0499"))
    }

    @Test
    fun testQ_canonicalAgendaSnapshotKeepsBlaBlaTripAndPassengerDataWithoutDirectCollectorRead() {
        val remoteBooking = RemoteBooking(
            id = "booking-bla-canonical",
            tripId = "remote-bla",
            passengerId = "passenger-bla",
            passengerName = "Passageiro Canônico",
            passengerContact = "contact-canonical",
            boardingStopId = "stop-origin",
            dropoffStopId = "stop-destination",
            seats = 1,
            status = BookingStatus.CONFIRMED.name,
            operationalStatus = PassengerOperationalStatus.CONFIRMED,
            paymentStatus = PassengerPaymentStatus.UNPAID,
            source = BookingSource.BLABLACAR,
            capacityClaimType = CapacityClaimType.EXTERNAL_OCCUPANCY,
            sourceReference = "BLABLACAR_SYNC:canonical-fixture",
        )
        val projection = canonicalTimelineProjection0494(
            DriverTripSyncStateResponse0402(
                source = "CANONICAL_NATIVE_FIREWALL",
                provenancePolicy0500 = "AGENDA_CANONICAL_ONLY_0503",
                collectorRead = false,
                collectorFallback = false,
                collectorDerivedData = false,
                trips = listOf(
                    state(
                        canonicalId = "canonical-bla",
                        revision = 9L,
                        blablaTripId = "provider-trip-9",
                        bookings = listOf(remoteBooking),
                    ),
                ),
            ),
        )

        assertEquals(1, projection.trips.size)
        assertEquals("provider-trip-9", projection.trips.single().blablaTripId)
        assertEquals(1, projection.bookings.size)
        assertEquals(BookingSource.BLABLACAR, projection.bookings.single().source)
        assertEquals("booking-bla-canonical", projection.bookings.single().id)
    }
    @Test
    fun testQ2_canonicalExternalBookingsDrivePassengerCountAndExistingShortcutRows() {
        val remoteBooking = RemoteBooking(
            id = "external-count",
            tripId = "remote-count",
            passengerName = "Norma",
            passengerContact = "+5511999999999",
            boardingStopId = "stop-origin",
            dropoffStopId = "stop-destination",
            seats = 1,
            status = BookingStatus.CONFIRMED.name,
            source = BookingSource.BLABLACAR,
            capacityClaimType = CapacityClaimType.EXTERNAL_OCCUPANCY,
            sourceReference = "BLABLACAR_SYNC:external-count",
            fareMinorUnits = 9_300L,
            fareCurrencyCode = "BRL",
            boardingAddress = "Terminal Rodoviário do Tietê",
            dropoffAddress = "Rodoviária de São Thomé das Letras",
        )
        val base = state(
            canonicalId = "canonical-count",
            revision = 15L,
            blablaTripId = "provider-count",
            bookings = listOf(remoteBooking),
        ).copy(sourceSeatCounts = emptyMap())
        val projection = canonicalTimelineProjection0494(
            DriverTripSyncStateResponse0402(
                source = "CANONICAL_NATIVE_FIREWALL",
                provenancePolicy0500 = "AGENDA_CANONICAL_ONLY_0503",
                collectorRead = false,
                collectorFallback = false,
                collectorDerivedData = false,
                trips = listOf(base),
            ),
        )

        assertEquals(1, projection.entries.single().sourcePassengerSeats[BookingSource.BLABLACAR])
        assertEquals("+5511999999999", projection.bookings.single().passengerContact)
        assertEquals(9_300L, projection.bookings.single().fareMinorUnits)

        val passengerUi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        assertTrue(passengerUi.contains("it.capacityClaimType == CapacityClaimType.EXTERNAL_OCCUPANCY"))
        assertTrue(passengerUi.contains("ic_whatsapp_action"))
        assertTrue(passengerUi.contains("Text(\"📍\""))
        assertTrue(passengerUi.contains("Text(\"🏁\""))
        assertTrue(passengerUi.contains("Text(\"💬\""))
    }

    @Test
    fun testQ_timelineZeroAvailabilityIsNumericWithoutLotadoSuffix() {
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()

        assertFalse(timeline.contains("\"LOTADO\""))
        assertEquals(3, timeline.lines().count { it.contains("val availabilityLabel = statusMark(entry)") })
        assertTrue(timeline.contains("🪑 Vagas disponíveis: \${free ?: 0} \$availabilityLabel"))
        assertTrue(timeline.contains("🪑 Vagas disponíveis: \$emptyFree \$availabilityLabel"))
    }


    @Test
    fun testR_0512ManualCanonicalIdentityRemainsOperationalWithoutExternalIdentity() {
        val booking = RemoteBooking(
            id = "manual-booking",
            tripId = "manual-canonical-0512",
            passengerId = "passenger-internal",
            passengerName = "Passageiro interno",
            boardingStopId = "stop-origin",
            dropoffStopId = "stop-destination",
            seats = 1,
            status = BookingStatus.CONFIRMED.name,
            source = BookingSource.PRIVATE,
            capacityClaimType = CapacityClaimType.PASSENGER,
        )
        val response = DriverTripSyncStateResponse0402(
            source = "CANONICAL_NATIVE_FIREWALL",
            provenancePolicy0500 = "AGENDA_CANONICAL_ONLY_0503",
            collectorRead = false,
            collectorFallback = false,
            collectorDerivedData = false,
            trips = listOf(
                state(
                    canonicalId = "manual-canonical-0512",
                    revision = 21L,
                    blablaTripId = "",
                    bookings = listOf(booking),
                ).copy(
                    bookingsCount = 1,
                    blablaProfileUuid = "",
                ),
            ),
        )

        val validated = validateCanonicalTimelineResponse0512(response)
        val projection = canonicalTimelineProjection0494(validated)

        assertEquals("manual-canonical-0512", projection.entries.single().tripId)
        assertEquals(1, projection.bookings.size)
        assertNull(projection.entries.single().blablaTripId)
        assertFalse(TripTimelineIssue.EXTERNAL_IDENTITY_INCOMPLETE in projection.entries.single().issues)
    }

    @Test
    fun testS_0512PassengerProjectionMismatchRejectsNewSnapshotBeforeCacheReplacement() {
        val booking = RemoteBooking(
            id = "booking-one",
            tripId = "canonical-mismatch",
            passengerName = "Passageiro",
            boardingStopId = "stop-origin",
            dropoffStopId = "stop-destination",
            seats = 1,
            status = BookingStatus.CONFIRMED.name,
            source = BookingSource.PRIVATE,
            capacityClaimType = CapacityClaimType.PASSENGER,
        )
        val response = DriverTripSyncStateResponse0402(
            source = "CANONICAL_NATIVE_FIREWALL",
            provenancePolicy0500 = "AGENDA_CANONICAL_ONLY_0503",
            collectorRead = false,
            collectorFallback = false,
            collectorDerivedData = false,
            trips = listOf(
                state("canonical-mismatch", 22L, bookings = listOf(booking))
                    .copy(bookingsCount = 2),
            ),
        )

        val error = assertFailsWith<CanonicalTimelineProjectionException0512> {
            validateCanonicalTimelineResponse0512(response)
        }
        assertEquals("PASSENGER_PROJECTION_FAILED", error.reasonCode)
    }

    @Test
    fun testT_0512RevisionRaceIsProjectionFailureNotBackendOffline() {
        val response = DriverTripSyncStateResponse0402(
            source = "CANONICAL_NATIVE_FIREWALL",
            provenancePolicy0500 = "AGENDA_CANONICAL_ONLY_0503",
            collectorRead = false,
            collectorFallback = false,
            collectorDerivedData = false,
            trips = listOf(
                state("canonical-race", 23L).copy(
                    canonicalIssues = listOf("REVISION_INCOMPATIBLE"),
                ),
            ),
        )

        val error = assertFailsWith<CanonicalTimelineProjectionException0512> {
            validateCanonicalTimelineResponse0512(response)
        }
        assertEquals("REVISION_INVALID", error.reasonCode)
        assertEquals("REVISION_INVALID", canonicalTimelineFailureCode0512(error))
    }

    @Test
    fun testU_0512CompositionCancellationNeverBecomesBackendUnavailable() {
        val cancellation = kotlinx.coroutines.CancellationException("The coroutine scope left the composition")
        assertEquals("CANCELLED", canonicalTimelineFailureCode0512(cancellation))

        val timelineUi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        assertTrue(timelineUi.contains("catch (cancelled: kotlinx.coroutines.CancellationException)"))
        assertTrue(timelineUi.contains("throw cancelled"))
        assertTrue(timelineUi.contains("TIMELINE_CANONICAL_PROJECTION_REJECTED_0512"))
        assertTrue(timelineUi.contains("TIMELINE_REFRESH_FAILED"))
    }

    @Test
    fun testV_0512PassengerLoadingHasTerminalFailureAndRefreshesWithCanonicalBookings() {
        val passengerUi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()

        assertTrue(passengerUi.contains("LaunchedEffect(entry, trip, canonicalBookings0494, identityRevision, completionRevision)"))
        assertTrue(passengerUi.contains("PASSENGER_PROJECTION_FAILED"))
        assertTrue(passengerUi.contains("renderFailure0512"))
        assertTrue(passengerUi.contains("catch (cancelled: kotlinx.coroutines.CancellationException)"))
        assertTrue(passengerUi.contains("Não foi possível resolver os passageiros desta viagem"))
    }

    @Test
    fun testW_0512ExternalIdentityIncompleteOnlyLimitsExternalActions() {
        val projection = canonicalTimelineProjection0494(
            DriverTripSyncStateResponse0402(
                source = "CANONICAL_NATIVE_FIREWALL",
                provenancePolicy0500 = "AGENDA_CANONICAL_ONLY_0503",
                collectorRead = false,
                collectorFallback = false,
                collectorDerivedData = false,
                trips = listOf(
                    state("canonical-partial-external", 24L).copy(
                        blablaProfileUuid = "profile-only",
                        blablaTripId = "",
                        canonicalIssues = listOf("EXTERNAL_IDENTITY_INCOMPLETE"),
                    ),
                ),
            ),
        )

        val entry = projection.entries.single()
        assertEquals("canonical-partial-external", entry.tripId)
        assertTrue(TripTimelineIssue.EXTERNAL_IDENTITY_INCOMPLETE in entry.issues)
        val timelineUi = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        assertTrue(timelineUi.contains("a viagem continua operacional pela identidade canônica"))
    }


    @Test
    fun testX_0512StalePrivateMirrorRejectsNewSnapshotAsProjectionIncomplete() {
        val response = DriverTripSyncStateResponse0402(
            source = "CANONICAL_NATIVE_FIREWALL",
            provenancePolicy0500 = "AGENDA_CANONICAL_ONLY_0503",
            collectorRead = false,
            collectorFallback = false,
            collectorDerivedData = false,
            trips = listOf(
                state("canonical-private-stale", 25L).copy(
                    canonicalIssues = listOf("PRIVATE_PROJECTION_STALE"),
                    privateMirrorAvailable0499 = true,
                    privateMirrorCurrent0499 = false,
                    privateMirrorRevision0499 = 24L,
                ),
            ),
        )

        val error = assertFailsWith<CanonicalTimelineProjectionException0512> {
            validateCanonicalTimelineResponse0512(response)
        }
        assertEquals("PROJECTION_INCOMPLETE", error.reasonCode)
    }

    @Test
    fun testW_0513CanonicalPassengerPrivateFieldsBeatLegacyCacheAndUseTrustedCoordinates() {
        val source = java.io.File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt",
        ).readText()
        assertTrue(source.contains("fareMinorUnits = booking.fareMinorUnits ?: privateMetadata0494?.fareMinorUnits"))
        assertTrue(source.contains("boardingAddress = booking.boardingAddress.takeIf(String::isNotBlank)"))
        assertTrue(source.contains("dropoffAddress = booking.dropoffAddress.takeIf(String::isNotBlank)"))
        assertTrue(source.contains("persistCanonicalPassengerPrivateMetadata0513"))
        assertTrue(source.contains("updateProtectedDriverBooking(remoteTripId, updated)"))
        assertTrue(source.contains("upsertDriverBooking(remoteTripId, updated)"))
        assertTrue(source.contains("TIMELINE_CANONICAL_PASSENGER_PRIVATE_MUTATION_0513"))
        assertFalse(source.contains("@Suppress(\"UNUSED_PARAMETER\") store: TripStore"))

        val row = EnhancedPassengerCardRow(
            name = "Passageiro",
            phone = null,
            seats = 1,
            boarding = "Origem",
            dropoff = "Destino",
            sources = setOf(BookingSource.PRIVATE),
            boardingAddress = "Embarque privado",
            dropoffAddress = "Destino privado",
            boardingLatitude = -23.6639,
            boardingLongitude = -46.5383,
            dropoffLatitude = -21.7218,
            dropoffLongitude = -44.9849,
        )
        val pickup = passengerPickupMapTarget(row)
        val dropoff = passengerDropoffMapTarget(row)

        assertEquals(-23.6639, pickup?.latitude)
        assertEquals(-46.5383, pickup?.longitude)
        assertEquals(-21.7218, dropoff?.latitude)
        assertEquals(-44.9849, dropoff?.longitude)
        assertEquals("Embarque privado", pickup?.query)
        assertEquals("Destino privado", dropoff?.query)
    }

    @Test
    fun testX_0513CanonicalPassengerIdentityWinsOverStaleLocalIdentity() {
        val remote = RemoteBooking(
            id = "booking-0513",
            tripId = "remote-0513",
            passengerId = "canonical-passenger-0513",
            passengerName = "Passageiro",
            boardingStopId = "a",
            dropoffStopId = "b",
            seats = 1,
            status = BookingStatus.CONFIRMED.name,
            source = BookingSource.PRIVATE,
            capacityClaimType = CapacityClaimType.PASSENGER,
        )
        val staleLocal = Booking(
            id = "booking-0513",
            tripId = "local-0513",
            passengerId = "stale-local-passenger",
            passengerName = "Passageiro",
            boardingStopId = "a",
            dropoffStopId = "b",
            seats = 1,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.PRIVATE,
            capacityClaimType = CapacityClaimType.PASSENGER,
        )

        assertEquals(
            "canonical-passenger-0513",
            remote.toLocalBooking("canonical-trip-0513", staleLocal).passengerId,
        )
    }

}
