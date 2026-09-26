from pathlib import Path

ROOT = Path('.')


def read(path):
    return (ROOT / path).read_text()


def write(path, text):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 anchor, found {count}')
    return text.replace(old, new, 1)

# Version: 0.1.293 / 5586 -> 0.1.294 / 5587.
build_path = 'app/build.gradle.kts'
build = read(build_path)
build = replace_once(build, 'versionCode = 5586', 'versionCode = 5587', 'versionCode')
build = replace_once(build, 'versionName = "0.1.293"', 'versionName = "0.1.294"', 'versionName')
write(build_path, build)

# One shared calendar/day-line implementation for both Timeline and Public Search.
calendar_path = 'app/src/main/java/br/com/mapeiaia/rotacerta/trips/AgendaCalendarDayLines.kt'
calendar_source = r'''package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class AgendaCalendarDay<T>(
    val date: LocalDate,
    val items: List<T>,
)

internal fun <T> agendaCalendarDaysForItems(
    items: List<T>,
    dateOf: (T) -> LocalDate?,
): List<AgendaCalendarDay<T>> {
    val byDate = items.mapNotNull { item -> dateOf(item)?.let { it to item } }
        .groupBy({ it.first }, { it.second })
    if (byDate.isEmpty()) return emptyList()

    val firstObserved = byDate.keys.minOrNull() ?: return emptyList()
    val lastObserved = byDate.keys.maxOrNull() ?: return emptyList()
    val start = firstObserved.withDayOfMonth(1)
    val end = lastObserved.withDayOfMonth(lastObserved.lengthOfMonth())
    return inclusiveCalendarDates(start, end).map { date ->
        AgendaCalendarDay(date = date, items = byDate[date].orEmpty())
    }
}

internal fun <T> agendaCalendarDaysForPeriod(
    period: String,
    items: List<T>,
    dateOf: (T) -> LocalDate?,
): List<AgendaCalendarDay<T>> {
    val byDate = items.mapNotNull { item -> dateOf(item)?.let { it to item } }
        .groupBy({ it.first }, { it.second })

    val exactDate = runCatching { LocalDate.parse(period.trim()) }.getOrNull()
    if (exactDate != null) {
        return listOf(AgendaCalendarDay(exactDate, byDate[exactDate].orEmpty()))
    }

    val month = runCatching { YearMonth.parse(period.trim()) }.getOrNull()
    if (month != null) {
        return inclusiveCalendarDates(month.atDay(1), month.atEndOfMonth()).map { date ->
            AgendaCalendarDay(date = date, items = byDate[date].orEmpty())
        }
    }

    return agendaCalendarDaysForItems(items, dateOf)
}

private fun inclusiveCalendarDates(start: LocalDate, end: LocalDate): List<LocalDate> {
    if (start.isAfter(end)) return emptyList()
    return generateSequence(start) { previous ->
        previous.plusDays(1).takeIf { next -> !next.isAfter(end) }
    }.toList()
}

private val agendaCalendarDayFormatter = DateTimeFormatter.ofPattern(
    "EEEE, d 'de' MMMM 'de' yyyy",
    Locale.forLanguageTag("pt-BR"),
)

internal fun agendaCalendarDayLabel(date: LocalDate): String =
    agendaCalendarDayFormatter.format(date)

@Composable
internal fun AgendaCalendarDayLine(date: LocalDate) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = agendaCalendarDayLabel(date),
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}
'''
write(calendar_path, calendar_source)

# Timeline: replace flat cards with complete, sequential calendar days spanning
# from the first represented month through the last represented month. Empty days
# render only the shared date line; cards remain unchanged under their date.
timeline_path = 'app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripTimelineUi.kt'
timeline = read(timeline_path)
old_visible = '''    val visibleEntries = remember(entries, trips, bookings, searchQuery) {
        filterTimelineEntries(entries, trips, bookings, searchQuery)
    }
'''
new_visible = '''    val visibleEntries = remember(entries, trips, bookings, searchQuery) {
        filterTimelineEntries(entries, trips, bookings, searchQuery)
    }
    val timelineCalendarDays = remember(visibleEntries) {
        agendaCalendarDaysForItems(visibleEntries) { entry ->
            Instant.ofEpochMilli(entry.departureAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }
'''
timeline = replace_once(timeline, old_visible, new_visible, 'timeline calendar model')
loop_start = timeline.index('    visibleEntries.forEach { entry ->')
loop_end = timeline.index('\n}\n\n@Composable\nprivate fun TripDriverDefaultsCard', loop_start)
loop = timeline[loop_start:loop_end]
loop = replace_once(
    loop,
    '    visibleEntries.forEach { entry ->',
    '    timelineCalendarDays.forEach { day ->\n        AgendaCalendarDayLine(day.date)\n        day.items.forEach { entry ->',
    'timeline render loop',
)
# The original closing brace now closes day.items.forEach; add one for calendar days.
loop = loop + '\n    }'
timeline = timeline[:loop_start] + loop + timeline[loop_end:]
write(timeline_path, timeline)

# Public search: use the exact requested day/month as calendar authority. A monthly
# search renders every day even with zero cards. Date lines live outside result-card
# borders; days with no cards contain only the date line.
public_path = 'app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaPublicSearchUi.kt'
public = read(public_path)
public = replace_once(
    public,
    '    val cardsByDate = remember(cards) { cards.groupBy(BlaBlaPublicSearchCard::date).toSortedMap() }',
    '''    val calendarDays = remember(cards, response.request.period) {
        agendaCalendarDaysForPeriod(
            period = response.request.period,
            items = cards,
            dateOf = { card -> runCatching { LocalDate.parse(card.date) }.getOrNull() },
        )
    }''',
    'public search calendar model',
)
section_start = public.index('            cardsByDate.forEach { (rawDate, dayCards) ->')
section_end = public.index('\n            if (missingLocal.isNotEmpty()) {', section_start)
section = public[section_start:section_end]
section = replace_once(
    section,
    '            cardsByDate.forEach { (rawDate, dayCards) ->',
    '''            calendarDays.forEach { day ->
                AgendaCalendarDayLine(day.date)
                val dayCards = day.items
                if (dayCards.isNotEmpty()) {''',
    'public search render loop',
)
section = replace_once(
    section,
    '                        Text(publicSearchDateLabel(rawDate), style = MaterialTheme.typography.titleMedium)\n',
    '',
    'public search in-card date label',
)
# The old final brace closes the new if(dayCards); add one to close calendarDays.
section = section + '\n            }'
public = public[:section_start] + section + public[section_end:]
write(public_path, public)

# Pure regression tests: full month, empty days, missing intermediate month,
# exact public-search day, and the requested pt-BR label.
test_path = 'app/src/test/java/br/com/mapeiaia/rotacerta/trips/AgendaCalendarDayLines0294Test.kt'
test_source = r'''package br.com.mapeiaia.rotacerta.trips

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun monthlyPublicSearchShowsWholeMonthEvenWithoutCards() {
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
    fun portugueseDayLineMatchesRequestedVisualExample() {
        assertEquals(
            "sexta-feira, 4 de setembro de 2026",
            agendaCalendarDayLabel(LocalDate.of(2026, 9, 4)),
        )
    }
}
'''
write(test_path, test_source)
