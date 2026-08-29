package br.com.mapeiaia.rotacerta.trips

import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaCalendarDayLines0294Test {
    @Test
    fun timelineShowsEveryDayOfRepresentedMonthAndKeepsEmptyDays() {
        val items = listOf(
            LocalDate.of(2026, 9, 4),
            LocalDate.of(2026, 9, 7),
        )
        val days = agendaCalendarDaysForItems(items) { it }

        assertEquals(30, days.size)
        assertEquals(LocalDate.of(2026, 9, 1), days.first().date)
        assertEquals(LocalDate.of(2026, 9, 30), days.last().date)
        assertTrue(days.first { it.date == LocalDate.of(2026, 9, 5) }.items.isEmpty())
        assertEquals(listOf(LocalDate.of(2026, 9, 7)), days.first { it.date == LocalDate.of(2026, 9, 7) }.items)
    }

    @Test
    fun timelineKeepsCompletelyEmptyMonthBetweenDistantCards() {
        val items = listOf(
            LocalDate.of(2026, 9, 30),
            LocalDate.of(2026, 11, 1),
        )
        val days = agendaCalendarDaysForItems(items) { it }

        assertEquals(LocalDate.of(2026, 9, 1), days.first().date)
        assertEquals(LocalDate.of(2026, 11, 30), days.last().date)
        assertTrue(days.first { it.date == LocalDate.of(2026, 10, 15) }.items.isEmpty())
    }

    @Test
    fun monthlyPeriodHelperStillEnumeratesSearchMonthWithoutControllingTimelineVisual() {
        val days = agendaCalendarDaysForPeriod<LocalDate>("2026-09", emptyList()) { it }
        assertEquals(30, days.size)
        assertEquals(LocalDate.of(2026, 9, 1), days.first().date)
        assertEquals(LocalDate.of(2026, 9, 30), days.last().date)
        assertTrue(days.all { it.items.isEmpty() })
    }

    @Test
    fun exactPublicSearchDateShowsOnlyThatDay() {
        val days = agendaCalendarDaysForPeriod<LocalDate>("2026-09-04", emptyList()) { it }
        assertEquals(listOf(LocalDate.of(2026, 9, 4)), days.map { it.date })
    }

    @Test
    fun publicSearchPeriodDoesNotInjectEmptyDaysIntoOperationalTimeline() {
        val zone = ZoneId.systemDefault()
        fun entry(id: String, date: LocalDate): TripTimelineEntry {
            val departure = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            return TripTimelineEntry(
                id,
                "profile-$id",
                "Perfil $id",
                departure,
                departure + 60L * 60L * 1000L,
                "Origem",
                "Destino",
                TripStatus.PUBLISHED,
                4,
                0,
                0,
                emptyMap(),
            )
        }
        val entries = listOf(
            entry("a", LocalDate.of(2026, 9, 4)),
            entry("b", LocalDate.of(2026, 9, 7)),
        )
        val response = BlaBlaPublicSearchResponse(
            status = "validated",
            request = BlaBlaPublicSearchRequest(
                targetNames = listOf("Perfil"),
                from = "Origem",
                to = "Destino",
                period = "2026-08",
            ),
            cards = emptyList(),
        )

        val days = combinedTimelineCalendarDays(entries, response)

        assertEquals(LocalDate.of(2026, 9, 1), days.first().date)
        assertEquals(LocalDate.of(2026, 9, 30), days.last().date)
        assertTrue(days.none { it.date.monthValue == 8 })
        assertTrue(days.first { it.date == LocalDate.of(2026, 9, 5) }.items.isEmpty())
    }

    @Test
    fun publicSearchWithoutOperationalTripsAddsOnlyDatesThatHaveRealCards() {
        val response = BlaBlaPublicSearchResponse(
            status = "validated",
            request = BlaBlaPublicSearchRequest(
                targetNames = listOf("Perfil"),
                from = "Origem",
                to = "Destino",
                period = "2026-08",
            ),
            cards = listOf(
                BlaBlaPublicSearchCard("Perfil", "2026-08-05", "Origem", "Destino"),
                BlaBlaPublicSearchCard("Perfil", "2026-08-20", "Origem", "Destino"),
            ),
        )

        val days = combinedTimelineCalendarDays(emptyList(), response)

        assertEquals(
            listOf(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 20)),
            days.map { it.date },
        )
        assertTrue(days.all { it.items.isEmpty() })
    }

    @Test
    fun timelineDayVisualIsBorderedCardWithoutLooseHorizontalDivider() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaCalendarDayLines.kt",
        ).readText()
        assertTrue(source.contains("Card("))
        assertTrue(source.contains("BorderStroke(1.dp"))
        assertTrue(source.contains("RoundedCornerShape(12.dp)"))
        assertTrue(!source.contains("HorizontalDivider"))
    }

    @Test
    fun dateCardAppearsOnlyWhenThatCalendarDayHasNoTravelCard() {
        assertTrue(
            shouldRenderTimelineEmptyDayCard(
                isOperationalCalendarDate = true,
                operationalCardCount = 0,
                publicCardCount = 0,
            ),
        )
        assertFalse(
            shouldRenderTimelineEmptyDayCard(
                isOperationalCalendarDate = true,
                operationalCardCount = 1,
                publicCardCount = 0,
            ),
        )
        assertFalse(
            shouldRenderTimelineEmptyDayCard(
                isOperationalCalendarDate = true,
                operationalCardCount = 0,
                publicCardCount = 1,
            ),
        )
        assertFalse(
            shouldRenderTimelineEmptyDayCard(
                isOperationalCalendarDate = false,
                operationalCardCount = 0,
                publicCardCount = 0,
            ),
        )
    }

    @Test
    fun publicSearchDoesNotRenderStandaloneDayHeaders() {
        val source = File(
            "src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt",
        ).readText()
        assertTrue(source.contains("shouldRenderTimelineEmptyDayCard("))
        assertTrue(source.contains("operationalCardCount = day.items.size"))
        assertTrue(source.contains("publicCardCount = dayPublicCards.size"))
        assertTrue(source.contains("Consulta pública • \$publicDateLabel"))
        assertTrue(!source.contains("agendaCalendarDaysForPeriod<TripTimelineEntry>"))
    }

    @Test
    fun portugueseDayLineMatchesRequestedVisualExample() {
        assertEquals(
            "sexta-feira, 4 de setembro de 2026",
            agendaCalendarDayLabel(LocalDate.of(2026, 9, 4)),
        )
    }
}
