package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlaBlaPublicSearchTest {
    @Test
    fun septemberWholeMonthWithReverseCreatesSixtyQueries() {
        val tasks = BlaBlaPublicSearchPlanner.tasks(
            BlaBlaPublicSearchRequest(
                targetNames = listOf("Barbosa", "Ezequiel S"),
                from = "Santo André, SP, Brasil",
                to = "São Thomé das Letras, MG, Brasil",
                period = "2026-09",
                includeReverse = true,
            ),
        )
        assertEquals(60, tasks.size)
        assertEquals(LocalDate.of(2026, 9, 1), tasks.first().date)
        assertEquals(LocalDate.of(2026, 9, 30), tasks.last().date)
    }

    @Test
    fun exactDateCreatesOnlyRequestedDirections() {
        val oneWay = BlaBlaPublicSearchPlanner.tasks(
            BlaBlaPublicSearchRequest(listOf("Barbosa"), "Santo André", "São Thomé das Letras", "2026-09-04", false),
        )
        val twoWay = BlaBlaPublicSearchPlanner.tasks(
            BlaBlaPublicSearchRequest(listOf("Barbosa"), "Santo André", "São Thomé das Letras", "2026-09-04", true),
        )
        assertEquals(1, oneWay.size)
        assertEquals(2, twoWay.size)
    }

    @Test
    fun driverMatchingIsAccentAndCaseInsensitiveButNotSubstringBased() {
        assertTrue(BlaBlaPublicSearchPlanner.matchesTarget("EZEQUIEL S", listOf("Ezequiel S")))
        assertTrue(BlaBlaPublicSearchPlanner.matchesTarget("Barbosa", listOf("BARBOSA")))
        assertFalse(BlaBlaPublicSearchPlanner.matchesTarget("João Barbosa", listOf("Barbosa")))
    }

    @Test
    fun knownCorridorCitiesResolveWithoutRailwayOrLogin() {
        assertTrue(BlaBlaPublicPlaceDirectory.supported("Santo André, SP, Brasil"))
        assertTrue(BlaBlaPublicPlaceDirectory.supported("São Thomé das Letras, MG, Brasil"))
        assertTrue(BlaBlaPublicPlaceDirectory.searchUrl(
            BlaBlaPublicSearchTask(LocalDate.of(2026, 9, 4), "Santo André, SP, Brasil", "São Thomé das Letras, MG, Brasil"),
        )!!.contains("db=2026-09-04"))
    }

    @Test
    fun publicCardMatchesAgendaByDateTimeAndDirection() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val departure = LocalDate.of(2026, 9, 4).atTime(11, 0).atZone(zone).toInstant().toEpochMilli()
        val trip = Trip(
            title = "Santo André → São Thomé",
            departureAtMillis = departure,
            status = TripStatus.PUBLISHED,
            stops = listOf(
                TripStop(order = 0, name = "Santo André"),
                TripStop(order = 1, name = "São Thomé das Letras"),
            ),
        )
        val card = BlaBlaPublicSearchCard(
            driverName = "Ezequiel S",
            date = "2026-09-04",
            searchFrom = "Santo André, SP, Brasil",
            searchTo = "São Thomé das Letras, MG, Brasil",
            departureTime = "11:00",
        )
        assertTrue(publicCardMatchesAgendaTrip(card, trip, zone))
        assertFalse(publicCardMatchesAgendaTrip(card.copy(departureTime = "13:00"), trip, zone))
    }
}
