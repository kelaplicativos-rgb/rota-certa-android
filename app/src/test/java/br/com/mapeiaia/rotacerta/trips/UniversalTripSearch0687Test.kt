package br.com.mapeiaia.rotacerta.trips

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UniversalTripSearch0687Test {
    private val zone = ZoneId.of("America/Sao_Paulo")

    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun accentAndCaseAreNormalized() {
        assertEquals(
            "tres coracoes",
            UniversalSearchEngine0687.normalize("TRÊS Corações"),
        )
    }

    @Test
    fun querySeventeenFindsEveryDocumentContainingSeventeen() {
        val tripOnDay17 = Trip(
            id = "trip-day-17",
            title = "São Paulo para Três Corações",
            departureAtMillis = millis(2026, 10, 17, 11, 30),
            stops = listOf(
                TripStop(id = "a", order = 0, name = "São Paulo"),
                TripStop(id = "b", order = 1, name = "Três Corações"),
            ),
        )
        val tripWithLiteral17 = Trip(
            id = "trip-code",
            title = "Referência A17B",
            departureAtMillis = millis(2026, 10, 18, 9, 0),
            stops = listOf(
                TripStop(id = "c", order = 0, name = "Santo André"),
                TripStop(id = "d", order = 1, name = "Extrema"),
            ),
        )
        val bookingWith17 = Booking(
            id = "booking-phone",
            tripId = tripOnDay17.id,
            passengerName = "Gabriela",
            passengerContact = "(35) 99999-1717",
            boardingStopId = "a",
            dropoffStopId = "b",
            fareMinorUnits = 21_700L,
        )

        val documents = UniversalSearchEngine0687.buildDocuments(
            trips = listOf(tripOnDay17, tripWithLiteral17),
            bookings = listOf(bookingWith17),
        )
        val keys = UniversalSearchEngine0687.search(documents, "17")
            .map { it.document.key }
            .toSet()

        assertTrue("trip:" + tripOnDay17.id in keys)
        assertTrue("trip:" + tripWithLiteral17.id in keys)
        assertTrue("booking:" + bookingWith17.id in keys)
    }

    @Test
    fun phoneDigitsAndFormattedMoneyAreSearchable() {
        val trip = Trip(
            id = "trip-1",
            title = "Rota",
            departureAtMillis = millis(2026, 10, 1, 11, 30),
            stops = listOf(
                TripStop(id = "a", order = 0, name = "São Paulo"),
                TripStop(id = "b", order = 1, name = "Três Corações"),
            ),
        )
        val booking = Booking(
            id = "booking-1",
            tripId = trip.id,
            passengerName = "Gabriela",
            passengerContact = "(35) 99999-1717",
            boardingStopId = "a",
            dropoffStopId = "b",
            fareMinorUnits = 21_700L,
        )
        val documents = UniversalSearchEngine0687.buildDocuments(
            trips = listOf(trip),
            bookings = listOf(booking),
        )

        assertTrue(
            UniversalSearchEngine0687.search(documents, "35999991717")
                .any { it.document.bookingId == booking.id },
        )
        assertTrue(
            UniversalSearchEngine0687.search(documents, "217,00")
                .any { it.document.bookingId == booking.id },
        )
    }

    @Test
    fun multipleTermsBehaveLikeAnAllTermsSearch() {
        val trip = Trip(
            id = "trip-terms",
            title = "São Paulo para Três Corações",
            departureAtMillis = millis(2026, 10, 1, 11, 30),
            stops = listOf(
                TripStop(id = "a", order = 0, name = "Metrô Penha"),
                TripStop(id = "b", order = 1, name = "Três Corações"),
            ),
        )
        val documents = UniversalSearchEngine0687.buildDocuments(
            trips = listOf(trip),
            bookings = emptyList(),
        )

        val result = UniversalSearchEngine0687.search(documents, "penha coracoes")
        assertEquals(1, result.size)
        assertEquals("trip:" + trip.id, result.single().document.key)
    }

    @Test
    fun aSingleCharacterHasNoMinimumLengthBlock() {
        val document = UniversalSearchDocument0687(
            key = "manual",
            kind = UniversalSearchKind0687.TRIP,
            title = "Teste",
            subtitle = "",
            searchableRaw = "abc7xyz",
            searchableNormalized = UniversalSearchEngine0687.normalize("abc7xyz"),
        )

        assertEquals(1, UniversalSearchEngine0687.search(listOf(document), "7").size)
    }
}
