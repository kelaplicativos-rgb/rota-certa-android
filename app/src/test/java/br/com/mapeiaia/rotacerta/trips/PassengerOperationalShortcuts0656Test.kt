package br.com.mapeiaia.rotacerta.trips

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PassengerOperationalShortcuts0656Test {
    private fun row(
        fare: Long? = 10_000L,
        boarding: String? = "São Paulo",
        dropoff: String? = "Santo André",
        boardingAddress: String = "",
        dropoffAddress: String = "",
        boardingLatitude: Double? = null,
        boardingLongitude: Double? = null,
        dropoffLatitude: Double? = null,
        dropoffLongitude: Double? = null,
        phone: String? = "+5511999999999",
    ) = EnhancedPassengerCardRow(
        name = "Isabela",
        phone = phone,
        seats = 1,
        boarding = boarding,
        dropoff = dropoff,
        sources = setOf(BookingSource.BLABLACAR),
        fareMinorUnits = fare,
        fareCurrencyCode = "BRL",
        boardingAddress = boardingAddress,
        dropoffAddress = dropoffAddress,
        boardingLatitude = boardingLatitude,
        boardingLongitude = boardingLongitude,
        dropoffLatitude = dropoffLatitude,
        dropoffLongitude = dropoffLongitude,
    )

    private fun entry() = TripTimelineEntry(
        tripId = "trip-0656",
        profileId = "profile",
        profileLabel = "Barbosa",
        departureAtMillis = 1_800_000_000_000L,
        arrivalAtMillis = 1_800_010_800_000L,
        origin = "São Paulo",
        destination = "Santo André",
        status = TripStatus.PUBLISHED,
        capacity = 4,
        minimumOccupiedSeats = 1,
        maximumOccupiedSeats = 1,
        sourcePassengerSeats = mapOf(BookingSource.BLABLACAR to 1),
    )

    @Test
    fun cityOnlyLabelIsDisplayEvidenceNotExactMapTarget() {
        val cityOnly = row()
        assertNull(passengerPickupMapTarget(cityOnly))
        assertNull(passengerDropoffMapTarget(cityOnly))
        assertContains(passengerOperationalAddressLabel0656(cityOnly, boarding = true), "definir endereço")
        assertContains(passengerOperationalAddressLabel0656(cityOnly, boarding = false), "definir endereço")
    }

    @Test
    fun exactAddressOrTrustedCoordinateCanOpenMap() {
        val exact = row(
            boardingAddress = "Rua Júlio Colaço, 73, São Paulo - SP",
            dropoffAddress = "R. Itambé, 87, Santo André - SP",
        )
        assertTrue(passengerPickupMapTarget(exact)?.query?.contains("Júlio Colaço") == true)
        assertTrue(passengerDropoffMapTarget(exact)?.query?.contains("Itambé") == true)

        val coordinate = row(
            boardingLatitude = -23.53,
            boardingLongitude = -46.54,
            dropoffLatitude = -23.66,
            dropoffLongitude = -46.53,
        )
        assertTrue(passengerPickupMapTarget(coordinate) != null)
        assertTrue(passengerDropoffMapTarget(coordinate) != null)
    }

    @Test
    fun missingPrivateMetadataRequestsTargetedRefresh() {
        assertTrue(passengerPrivateMetadataIncomplete0656(row(fare = null)))
        assertTrue(passengerPrivateMetadataIncomplete0656(row(phone = null)))
        assertFalse(
            passengerPrivateMetadataIncomplete0656(
                row(
                    boardingAddress = "Embarque exato",
                    dropoffAddress = "Destino exato",
                ),
            ),
        )
    }

    @Test
    fun quickMessagesCoverOperationalTemplates() {
        val row = row(
            boardingAddress = "Embarque exato",
            dropoffAddress = "Destino exato",
        )
        val confirm = passengerQuickMessageText0656(
            entry(),
            row,
            PassengerQuickMessageType0656.CONFIRM_NOW,
            vehicleMakeModel = "Hyundai HB20 TBJ4F74",
            vehicleColor = "cinza",
        )
        assertContains(confirm, "Isabela")
        assertContains(confirm, "São Paulo → Santo André")
        assertContains(confirm, "Está tudo certo?")

        val tomorrow = passengerQuickMessageText0656(
            entry(),
            row,
            PassengerQuickMessageType0656.CONFIRM_TOMORROW,
            vehicleMakeModel = "Hyundai HB20 TBJ4F74",
            vehicleColor = "cinza",
        )
        assertContains(tomorrow, "amanhã")
        assertContains(tomorrow, "Hyundai HB20 TBJ4F74")
        assertContains(tomorrow, "CINZA")

        val fare = passengerQuickMessageText0656(
            entry(),
            row,
            PassengerQuickMessageType0656.FARE,
        )
        assertContains(fare, "1 lugar")
        assertContains(fare, "R$")
    }

    @Test
    fun sameSharedOperatorDrivesViagensCentralAndTimeline() {
        val passengerSource = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt",
        ).readText()
        val tripsSource = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/OperationalAllTripsBrowserUi0563.kt",
        ).readText()
        val centralSource = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/CentralDoDia0552.kt",
        ).readText()

        assertTrue(passengerSource.contains("passengerOperationalAddressLabel0656"))
        assertTrue(passengerSource.contains("quickMessageRow0656 = passenger"))
        assertTrue(passengerSource.contains("PASSENGER_PRIVATE_REFRESH_REQUEST_0656"))
        assertTrue(passengerSource.contains("CentralDayCommandBridge0552.refreshTrip"))
        assertTrue(passengerSource.contains("READY_MESSAGE_SELECTOR"))
        assertTrue(tripsSource.contains("EnhancedPassengerTimelineSection("))
        assertTrue(tripsSource.contains("compactEmbeddedControls0593 = true"))
        assertTrue(centralSource.contains("EnhancedPassengerTimelineSection("))
        assertTrue(centralSource.contains("compactEmbeddedControls0593 = true"))
        assertTrue(centralSource.contains("if (!passengersExpanded0591)"))
    }
}
