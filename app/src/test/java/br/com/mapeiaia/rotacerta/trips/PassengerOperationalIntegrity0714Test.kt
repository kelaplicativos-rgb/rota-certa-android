package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassengerOperationalIntegrity0714Test {
    private fun trip0714() = Trip(
        id = "trip-0714",
        title = "A → D",
        departureAtMillis = 1_800_000_000_000L,
        capacity = 4,
        status = TripStatus.PUBLISHED,
        publishedSeats = 1,
        rotaCertaSeatAllocation = 0,
        stops = listOf(
            TripStop(id = "a", order = 0, name = "A"),
            TripStop(id = "b", order = 1, name = "B"),
            TripStop(id = "c", order = 2, name = "C"),
            TripStop(id = "d", order = 3, name = "D"),
        ),
    )

    private fun booking(
        id: String,
        from: String,
        to: String,
        seats: Int = 1,
        group: String = id,
    ) = Booking(
        id = id,
        tripId = "trip-0714",
        passengerName = id,
        boardingStopId = from,
        dropoffStopId = to,
        seats = seats,
        status = BookingStatus.CONFIRMED,
        source = BookingSource.BLABLACAR,
        capacityClaimType = CapacityClaimType.EXTERNAL_OCCUPANCY,
        occupancyGroupId = group,
    )

    @Test
    fun remainingExternalSeatPlusPeakConfirmedReconstructsFourSeatCeiling() {
        val trip = trip0714()
        val bookings = listOf(
            booking("Erick", "a", "d"),
            booking("Gabriela", "b", "c"),
            booking("Sidinei", "b", "d"),
        )
        assertEquals(3, peakConfirmedPassengerSeats0714(trip, bookings))
        assertEquals(4, operationalInventoryCapacity(trip, bookings))

        val loads = SeatAvailabilityEngine.segmentLoads(trip.copy(capacity = 4), bookings)
        assertEquals(listOf(1, 3, 2), loads.map(SegmentLoad::passengerSeats))
        assertEquals(listOf(3, 1, 2), loads.map(SegmentLoad::availableSeats))
        assertEquals(listOf(0, 0, 0), loads.map(SegmentLoad::overbookingSeats))
    }

    @Test
    fun localOnlyPassengerDoesNotIncreaseBlaBlaCapacity() {
        val trip = trip0714()
        val local = booking("local", "a", "d").copy(
            source = BookingSource.ROTA_CERTA,
            capacityClaimType = CapacityClaimType.PASSENGER,
        )
        assertEquals(0, peakConfirmedPassengerSeats0714(trip, listOf(local)))
        assertEquals(1, operationalInventoryCapacity(trip, listOf(local)))
    }

    @Test
    fun mirroredReservationIsAddedBackOnlyOnce() {
        val trip = trip0714()
        val bookings = listOf(
            booking("external", "a", "d", group = "same"),
            booking("mirror", "a", "d", group = "same").copy(source = BookingSource.ROTA_CERTA),
        )
        assertEquals(1, peakConfirmedPassengerSeats0714(trip, bookings))
        assertEquals(2, operationalInventoryCapacity(trip, bookings))
    }

    @Test
    fun passengerCopyUsesFirstNameAndIsolatesTrackingLink() {
        assertEquals("Gabriela", passengerFirstName0714("  Gabriela Souza  "))
        val message = passengerTrackingMessage0714("Gabriela Souza")
        assertContains(message, "🚗 Gabriela,")
        assertFalse(message.contains("Gabriela Souza"))
        val payload = passengerTrackingPayload0714(message, "https://example.test/t")
        assertContains(payload, "desembarque.\n\n👇 Toque somente no link abaixo:\n\nhttps://example.test/t")
    }

    @Test
    fun sourceContractsPersistExternalStatusAndInvalidateEditedAddressCoordinates() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        assertContains(source, "saveExternalPassengerOperationalStatus0714")
        assertContains(source, "PASSENGER_EXTERNAL_STATUS_READBACK_OK_0714")
        assertContains(source, "boardingLatitude = null")
        assertContains(source, "boardingLongitude = null")
        assertContains(source, "dropoffLatitude = null")
        assertContains(source, "dropoffLongitude = null")
        assertContains(source, "PASSENGER_ADDRESS_READBACK_OK_0714")
        assertContains(source, "passengerOperationalAddressLabel0656(passenger, boarding = true)")
        assertContains(source, "passengerOperationalAddressLabel0656(passenger, boarding = false)")
    }

    @Test
    fun mainCardSurfaceOpensExactBlaBlaAndShortcutsRemainIndependent() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        assertContains(source, "val openTimelineCard0714: () -> Unit")
        assertContains(source, "openBlaBlaHref(context, entry, target0714.tripHref)")
        assertContains(source, "queueTargetHtmlRefresh0607()")
        assertContains(source, "TIMELINE_CARD_OPEN_BLABLACAR_0714")
        assertContains(source, "onClick = openTimelineCard0714")
        assertContains(source, "onClick = onToggleExpanded")
    }

    @Test
    fun userFacingFullLabelIsUnambiguous() {
        val timeline = File("src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt").readText()
        val passengers = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        assertFalse(timeline.contains("\"LOTADO\""))
        assertFalse(passengers.contains("\"LOTADO\""))
        assertContains(timeline, "CHEIO")
        assertContains(timeline, "• EXCESSO")
        assertContains(passengers, "CHEIO")
        assertContains(passengers, "• EXCESSO")
    }

    @Test
    fun vehicleCopyUsesPerProfileRotaCertaSnapshotForBlaBlaTrips() {
        val source = File("src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt").readText()
        val resolver = source
            .substringAfter("internal fun resolvePassengerMessageVehicle0714(")
            .substringBefore("internal fun passengerTrackingMessage0714")
        assertContains(resolver, "BlaBlaDynamicAccountRegistry")
        assertContains(resolver, "BlaBlaPublicProfileStore")
        assertContains(resolver, "identityVerified")
        assertTrue(resolver.indexOf("return PassengerMessageVehicle0714()") < resolver.indexOf("val settings = store.onlineSettings()"))
    }

    @Test
    fun backendTreatsExplicitNullAsCoordinateInvalidationAndUsesConfirmedPeak() {
        val backend = File("../trip-platform/functions/index.js").readText()
        assertContains(backend, "hasBoardingLatitude")
        assertContains(backend, "hasDropoffLongitude")
        assertContains(backend, "operationalSeatLimit(trip, records = [], now = Date.now())")
        assertContains(backend, "confirmedPeak")
        assertContains(backend, "blablaAvailable + confirmedPeak + rotaCertaAllocated")
    }
}
