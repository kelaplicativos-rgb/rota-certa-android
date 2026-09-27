package br.com.mapeiaia.rotacerta.trips

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PassengerSegmentTime0672Test {
    private val zone = ZoneId.of("America/Sao_Paulo")

    private fun millis(hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 9, 27, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun entry() = TripTimelineEntry(
        tripId = "trip-0672",
        profileId = "profile",
        profileLabel = "Motorista",
        departureAtMillis = millis(10, 30),
        arrivalAtMillis = millis(15, 10),
        origin = "Santo André",
        destination = "Três Corações",
        status = TripStatus.PUBLISHED,
        capacity = 4,
        minimumOccupiedSeats = 1,
        maximumOccupiedSeats = 2,
        sourcePassengerSeats = mapOf(BookingSource.BLABLACAR to 1),
    )

    private fun jean() = EnhancedPassengerCardRow(
        name = "Jean",
        phone = "+5535999999999",
        seats = 1,
        boarding = "Pouso Alegre",
        dropoff = "Três Corações",
        sources = setOf(BookingSource.BLABLACAR),
        boardingStopIndex = 2,
        dropoffStopIndex = 3,
    )

    private fun trip(pousoTime: Long?) = Trip(
        id = "trip-0672",
        title = "Santo André → Três Corações",
        departureAtMillis = millis(10, 30),
        capacity = 4,
        status = TripStatus.PUBLISHED,
        publicTimezoneId0411 = "America/Sao_Paulo",
        stops = listOf(
            TripStop(id = "sa", order = 0, name = "Santo André", plannedDepartureMillis = millis(10, 30)),
            TripStop(id = "sp", order = 1, name = "São Paulo", plannedDepartureMillis = millis(10, 50)),
            TripStop(id = "pa", order = 2, name = "Pouso Alegre", plannedArrivalMillis = pousoTime, plannedDepartureMillis = pousoTime),
            TripStop(id = "tc", order = 3, name = "Três Corações", plannedArrivalMillis = millis(15, 10)),
        ),
    )

    @Test
    fun passengerBoardingUsesTheExactIntermediateStopTime() {
        val resolved = passengerBoardingTimeMillis0672(entry(), trip(millis(13, 50)), jean())
        assertEquals(millis(13, 50), resolved)

        val message = passengerQuickMessageText0656(
            entry = entry(),
            trip = trip(millis(13, 50)),
            row = jean(),
            type = PassengerQuickMessageType0656.CONFIRM_NOW,
            localeTag = "pt-BR",
        )
        assertContains(message, "13h50")
        assertFalse(message.contains("10h30"))
    }

    @Test
    fun everyConfirmationTemplateUsesTheSamePassengerSchedule() {
        val tomorrow = passengerQuickMessageText0656(
            entry = entry(),
            trip = trip(millis(13, 50)),
            row = jean(),
            type = PassengerQuickMessageType0656.CONFIRM_TOMORROW,
            localeTag = "pt-BR",
        )
        val oneHour = passengerQuickMessageText0656(
            entry = entry(),
            trip = trip(millis(13, 50)),
            row = jean(),
            type = PassengerQuickMessageType0656.CONFIRM_ONE_HOUR,
            localeTag = "pt-BR",
        )
        assertContains(tomorrow, "13h50")
        assertContains(oneHour, "13h50")
        assertFalse(tomorrow.contains("10h30"))
        assertFalse(oneHour.contains("10h30"))
    }

    @Test
    fun missingIntermediateTimeNeverFallsBackToTripDeparture() {
        assertNull(passengerBoardingTimeMillis0672(entry(), trip(null), jean()))
        val message = passengerQuickMessageText0656(
            entry = entry(),
            trip = trip(null),
            row = jean(),
            type = PassengerQuickMessageType0656.CONFIRM_NOW,
            localeTag = "pt-BR",
        )
        assertContains(message, "Horário previsto do embarque ainda não disponível")
        assertFalse(message.contains("10h30"))
    }
}
