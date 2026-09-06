package br.com.mapeiaia.rotacerta.trips

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PublicBookingRealtime0304Test {
    @Test
    fun externalBindingMatchesStrongTimelineIdentityAndCarriesPublicBooking() {
        val binding = PublicExternalTripBinding(
            remoteTripId = "remote-public-1234567890",
            publicToken = "remote-public-1234567890",
            bookingTripId = "public-external:remote-public-1234567890",
            profileUuid = "7371f028-9c55-4903-8444-308015823efd",
            blablaTripId = "blabla-trip-1",
            title = "Três Corações → Santo André",
            departureAtMillis = 4_000_000_000_000L,
            capacity = 4,
            stops = listOf(
                TripStop(id = "from", order = 0, name = "Três Corações"),
                TripStop(id = "to", order = 1, name = "Santo André"),
            ),
        )
        val entry = TripTimelineEntry(
            tripId = "blablacar:x",
            profileId = binding.profileUuid,
            profileLabel = "Ezequiel S",
            departureAtMillis = binding.departureAtMillis,
            arrivalAtMillis = null,
            origin = "Três Corações",
            destination = "Santo André",
            status = TripStatus.PUBLISHED,
            capacity = 4,
            minimumOccupiedSeats = 0,
            maximumOccupiedSeats = 0,
            sourcePassengerSeats = emptyMap(),
            blablaTripId = binding.blablaTripId,
            blablaProfileUuid = binding.profileUuid,
        )
        val booking = Booking(
            id = "public_1",
            tripId = binding.bookingTripId,
            passengerName = "Passageiro do link",
            boardingStopId = "from",
            dropoffStopId = "to",
            seats = 1,
            status = BookingStatus.CONFIRMED,
            source = BookingSource.ROTA_CERTA,
            sourceReference = "PUBLIC_LINK:public_1",
        )

        assertTrue(binding.matches(entry))
        assertEquals(binding.bookingTripId, binding.asTrip().id)

        val enriched = applyPublicExternalBookingsToTimeline(
            entries = listOf(entry),
            bindings = listOf(binding),
            bookings = listOf(booking),
        ).single()
        assertEquals(1, enriched.maximumOccupiedSeats)
        assertEquals(1, enriched.sourcePassengerSeats[BookingSource.ROTA_CERTA])
        assertNotNull(enriched)
    }
}
