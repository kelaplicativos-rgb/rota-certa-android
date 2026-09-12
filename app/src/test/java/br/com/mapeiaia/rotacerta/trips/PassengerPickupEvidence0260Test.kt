package br.com.mapeiaia.rotacerta.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerPickupEvidence0260Test {
    @Test
    fun validCoordinatePairIsAccepted() {
        val metadata = ExternalPassengerMetadata(
            reservationKey = "stable-reservation",
            boardingLatitude = -22.123,
            boardingLongitude = -45.456,
        )

        assertTrue(metadata.hasBoardingCoordinates)
    }

    @Test
    fun incompleteOrOutOfBoundsCoordinatesFailClosed() {
        assertFalse(
            ExternalPassengerMetadata(
                reservationKey = "missing-longitude",
                boardingLatitude = -22.0,
            ).hasBoardingCoordinates,
        )
        assertFalse(
            ExternalPassengerMetadata(
                reservationKey = "invalid-latitude",
                boardingLatitude = 91.0,
                boardingLongitude = -45.0,
            ).hasBoardingCoordinates,
        )
        assertFalse(
            ExternalPassengerMetadata(
                reservationKey = "invalid-longitude",
                boardingLatitude = -22.0,
                boardingLongitude = 181.0,
            ).hasBoardingCoordinates,
        )
        assertNull(validLatitude(Double.NaN))
        assertNull(validLongitude(Double.POSITIVE_INFINITY))
    }

    @Test
    fun reservationMetadataKeyIsStableAndProfileScoped() {
        val href = "https://provider.invalid/booking/stable-a"
        val first = externalPassengerReservationKey("driver-a", href)

        assertEquals(first, externalPassengerReservationKey("DRIVER-A", href))
        assertNotEquals(first, externalPassengerReservationKey("driver-b", href))
    }
}
