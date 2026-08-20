package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripUnifiedOccupancySyncStage47R4Step2Test {
    @Test
    fun remoteCapacityClaimPreservesSourceAndPhysicalSeatLink() {
        val remote = RemoteBooking(
            id = "private-seat",
            tripId = "remote-trip",
            passengerName = "Passageiro particular",
            boardingStopId = "a",
            dropoffStopId = "c",
            seats = 1,
            status = "CONFIRMED",
            source = BookingSource.PRIVATE,
            capacityClaimType = CapacityClaimType.PASSENGER,
            sourceReference = "whatsapp-123",
            occupancyGroupId = "physical-seat-1",
        )

        val local = remote.toLocalBooking("local-trip")

        assertEquals("local-trip", local.tripId)
        assertEquals(BookingSource.PRIVATE, local.source)
        assertEquals(CapacityClaimType.PASSENGER, local.capacityClaimType)
        assertEquals("whatsapp-123", local.sourceReference)
        assertEquals("physical-seat-1", local.occupancyGroupId)
    }

    @Test
    fun legacyRemoteBookingRemainsCompatible() {
        val remote = RemoteBooking(
            id = "legacy",
            passengerName = "Legado",
            boardingStopId = "a",
            dropoffStopId = "b",
        )

        val local = remote.toLocalBooking("trip")

        assertEquals(BookingSource.OTHER, local.source)
        assertEquals(CapacityClaimType.PASSENGER, local.capacityClaimType)
        assertNull(local.occupancyGroupId)
    }

    @Test
    fun rotaCertaSourceIsDistinctFromExternalSources() {
        assertEquals("ROTA_CERTA", BookingSource.ROTA_CERTA.name)
        assertEquals("BLABLACAR", BookingSource.BLABLACAR.name)
        assertEquals("PRIVATE", BookingSource.PRIVATE.name)
    }
}
